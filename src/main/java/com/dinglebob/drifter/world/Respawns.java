package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Death and coming back (DESIGN.md section 20; the user, 2026-10-20: "do the respawn and lives stuff first, make it
 * configurable between the three"). Death works like a player's: everything drops, a death message goes to chat, and,
 * by the setting, it comes back at its bed 5 to 30 seconds later with nothing (bed respawn), does so a few times and then
 * logs off for good (limited lives, the default), or never comes back (permadeath). Its name, skin, memories, friends and
 * grudges come back with it; it knows where it died and may go back for its things (the corpse run).
 *
 * <p>No bed: near its base's middle. No base at all: it's gone (a player with no home in the world). Three deaths in a day
 * and the impatient may rage-quit: they log off for a day, then come back. Not for a Drifter you meet once (an encounter's,
 * a delver), a manhunt hunter (its own respawn), a battle's soldiers, or one removed with /kill.
 */
public final class Respawns {
	/** It comes back this long after dying (ticks): 5 to 30 s. */
	private static final int DELAY_MIN = 20 * 5;
	private static final int DELAY_SPREAD = 20 * 25;
	/** Rage-quitting: it's gone this long (a day to a day and a half). */
	private static final long QUIT_FOR = 24000L;
	private static final int QUIT_SPREAD = 12000;
	/** Three deaths within this long: a bad day. */
	private static final long BAD_DAY = 24000L;

	/** A Drifter on its way back: who, its saved self, where it lives, when it's due, and whether it rage-quit. */
	private record Pending(UUID id, String name, CompoundTag body, ResourceKey<Level> level, BlockPos home, long dueAt, boolean quit) {
		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", this.id);
			tag.putString("Name", this.name);
			tag.put("Body", this.body);
			tag.putString("Level", this.level.location().toString());
			tag.putLong("Home", this.home.asLong());
			tag.putLong("DueAt", this.dueAt);
			tag.putBoolean("Quit", this.quit);
			return tag;
		}

		@Nullable
		static Pending load(CompoundTag tag) {
			ResourceLocation level = ResourceLocation.tryParse(tag.getString("Level"));
			return level == null || !tag.hasUUID("Id") ? null : new Pending(tag.getUUID("Id"), tag.getString("Name"), tag.getCompound("Body"),
				ResourceKey.create(Registries.DIMENSION, level), BlockPos.of(tag.getLong("Home")), tag.getLong("DueAt"), tag.getBoolean("Quit"));
		}
	}

	/**
	 * Where it died and what it left there (for the corpse run): the spot, its world, when, how much worth going back for
	 * (things that aren't junk; 0: nothing, or lost in lava or the void), who killed it (a player or Drifter), and how.
	 */
	public record Corpse(BlockPos pos, ResourceLocation dimension, long at, int worth, @Nullable UUID killer, String cause) {
		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("Pos", this.pos.asLong());
			tag.putString("Dimension", this.dimension.toString());
			tag.putLong("At", this.at);
			tag.putInt("Worth", this.worth);
			if (this.killer != null) {
				tag.putUUID("Killer", this.killer);
			}

			tag.putString("Cause", this.cause);
			return tag;
		}

		@Nullable
		public static Corpse load(CompoundTag tag) {
			ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
			return dimension == null ? null : new Corpse(BlockPos.of(tag.getLong("Pos")), dimension, tag.getLong("At"), tag.getInt("Worth"),
				tag.hasUUID("Killer") ? tag.getUUID("Killer") : null, tag.getString("Cause"));
		}
	}

	private static final List<Pending> pending = new ArrayList<>();
	private static boolean dirty;

	private Respawns() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Respawns::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Respawns::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0 && !pending.isEmpty()) {
				tick(server);
			}

			if (dirty && server.getTickCount() % 200 == 0) {
				save(server);
			}
		});
	}

	/** Does this death have a way back (by the setting, its lives, its home)? Asked as it dies, before anything's dropped. */
	public static boolean comesBack(DrifterEntity drifter, DamageSource source) {
		if (drifter.isTemporary() || com.dinglebob.drifter.social.Manhunt.isHunter(drifter) || Battle.inBattle(drifter)
			|| source.is(DamageTypes.GENERIC_KILL) || drifter.getDrifterBrain().home() == null) {
			return false;
		}

		return switch (DrifterConfig.respawnMode()) {
			case BED -> true;
			case LIVES -> drifter.deaths() + 1 <= DrifterConfig.lives();
			case PERMADEATH -> false;
		};
	}

	/**
	 * It has died (its things already dropped): the death message, what it remembers of it, and, if it comes back, its
	 * saved self put by until it's due.
	 */
	public static void died(DrifterEntity drifter, DamageSource source, boolean comesBack, int worth) {
		if (!(drifter.level() instanceof ServerLevel level)) {
			return;
		}

		MinecraftServer server = level.getServer();
		Component message = drifter.getCombatTracker().getDeathMessage();
		if (DrifterConfig.deathMessages() && !Battle.inBattle(drifter)) {
			server.getPlayerList().broadcastSystemMessage(message, false);
		}

		if (drifter.isTemporary() || com.dinglebob.drifter.social.Manhunt.isHunter(drifter) || Battle.inBattle(drifter)) {
			return;
		}

		// What it remembers: where, how, who, and what it left there (nothing worth it in lava or the void).
		long now = level.getGameTime();
		boolean lost = drifter.isInLava() || source.is(DamageTypes.FELL_OUT_OF_WORLD);
		var killer = source.getEntity();
		UUID killerId = killer instanceof net.minecraft.world.entity.player.Player || killer instanceof DrifterEntity ? killer.getUUID() : null;
		String cause = message.getString().replace(drifter.getName().getString(), "it").trim();
		drifter.recordDeath(now, new Corpse(drifter.blockPosition(), level.dimension().location(), now, lost ? 0 : worth, killerId, cause));
		if (!comesBack) {
			Home home = drifter.getDrifterBrain().home();
			boolean outOfLives = DrifterConfig.respawnMode() == DrifterConfig.RespawnMode.LIVES && home != null && !source.is(DamageTypes.GENERIC_KILL);
			if (outOfLives) {
				// Its last life: it logs off for good, as a player who's had enough.
				server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", drifter.getDisplayName())
					.withStyle(ChatFormatting.YELLOW), false);
				Drifter.LOGGER.info("[Drifter] {} is out of lives ({} deaths): gone for good", drifter.getName().getString(), drifter.deaths());
			}

			return;
		}

		// A bad day (three deaths in one): the impatient may rage-quit for a day.
		boolean quit = drifter.deathsWithin(now, BAD_DAY) >= 3
			&& drifter.getRandom().nextDouble() < 0.6 * (1.0 - drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.PATIENCE));
		long delay = quit ? QUIT_FOR + drifter.getRandom().nextInt(QUIT_SPREAD) : DELAY_MIN + drifter.getRandom().nextInt(DELAY_SPREAD);
		if (quit) {
			drifter.getMemory().thoughts().add(now, "third death today: rage-quit for a while");
			server.getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.left", drifter.getDisplayName())
				.withStyle(ChatFormatting.YELLOW), false);
		}

		Home home = drifter.getDrifterBrain().home();
		CompoundTag body = new CompoundTag();
		drifter.saveWithoutId(body);
		pending.add(new Pending(drifter.getUUID(), drifter.getName().getString(), body, ResourceKey.create(Registries.DIMENSION, home.dimension),
			home.center, now + delay, quit));
		dirty = true;
		Drifter.LOGGER.info("[Drifter] {} died ({}): back in {} s{}", drifter.getName().getString(), cause, delay / 20, quit ? " (rage-quit)" : "");
	}

	/** Who's due back now and can come (its spot's area loaded): back they come. */
	private static void tick(MinecraftServer server) {
		pending.removeIf(entry -> {
			ServerLevel level = server.getLevel(entry.level());
			if (level == null) {
				return true;
			}

			// Due, and its home's area running (nobody near: it comes back when someone is; nothing is loaded for it).
			if (level.getGameTime() < entry.dueAt() || !level.isPositionEntityTicking(entry.home())) {
				return false;
			}

			try {
				return respawn(level, entry);
			} catch (RuntimeException e) {
				Drifter.LOGGER.error("[Drifter] couldn't bring {} back: dropping it", entry.name(), e);
				return true;
			}
		});
	}

	/** Back at its bed (or its base), with nothing; true once it's there (or it never can be). */
	private static boolean respawn(ServerLevel level, Pending entry) {
		DrifterEntity fresh = com.dinglebob.drifter.registry.ModEntities.DRIFTER.create(level);
		if (fresh == null) {
			return true;
		}

		CompoundTag body = entry.body().copy();
		// Its body starts over (what it carried dropped where it died; health, food, air and effects as new, like a player's);
		// who it is stays.
		for (String key : new String[] {"DrifterInventory", "ArmorItems", "HandItems", "body_armor_item", "Health", "DeathTime", "HurtTime",
			"HurtByTimestamp", "Fire", "Pos", "Motion", "FallDistance", "Air", "active_effects", "XpP", "XpLevel", "XpTotal", "foodLevel",
			"foodSaturationLevel", "foodExhaustionLevel", "foodTickTimer", "SleepingX", "SleepingY", "SleepingZ", "PortalCooldown"}) {
			body.remove(key);
		}

		fresh.load(body);
		Home home = fresh.getDrifterBrain().home();
		Vec3 spot = home == null ? null : spot(level, home);
		if (spot == null) {
			Drifter.LOGGER.info("[Drifter] {} has nowhere to come back to: gone", entry.name());
			return true;
		}

		// (Its old body still about, for a moment: next time.)
		BlockPos at = BlockPos.containing(spot);
		if (!level.isPositionEntityTicking(at) || level.getEntity(entry.id()) != null) {
			return false;
		}

		fresh.moveTo(spot.x, spot.y, spot.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		fresh.setHealth(fresh.getMaxHealth());
		fresh.setDeltaMovement(Vec3.ZERO);
		if (!level.addFreshEntity(fresh)) {
			return false;
		}

		int lives = DrifterConfig.lives();
		String life = DrifterConfig.respawnMode() == DrifterConfig.RespawnMode.LIVES ? " (" + Math.max(0, lives - fresh.deaths()) + " of " + lives + " lives left)" : "";
		fresh.getMemory().thoughts().add(level.getGameTime(), "back at " + (home.bedOrNull(level) != null ? "its bed" : "its base") + ", with nothing" + life);
		if (entry.quit()) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("multiplayer.player.joined", fresh.getDisplayName())
				.withStyle(ChatFormatting.YELLOW), false);
		}

		Drifter.LOGGER.info("[Drifter] {} respawned at {}{}", entry.name(), at.toShortString(), life);
		return true;
	}

	/** Where it comes back: beside its bed if it still has one (where a player stands up from it), else near its base's middle. */
	@Nullable
	private static Vec3 spot(ServerLevel level, Home home) {
		if (!home.dimension.equals(level.dimension().location())) {
			return null;
		}

		BlockPos bed = home.bedOrNull(level);
		if (bed != null) {
			BlockState state = level.getBlockState(bed);
			var up = BedBlock.findStandUpPosition(com.dinglebob.drifter.registry.ModEntities.DRIFTER, level, bed, state.getValue(BedBlock.FACING), 0.0F);
			if (up.isPresent()) {
				return up.get();
			}
		}

		BlockPos center = home.center;
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}

					for (int dy = 2; dy >= -3; dy--) {
						BlockPos pos = center.offset(dx, dy, dz);
						if (level.hasChunkAt(pos) && com.dinglebob.drifter.brain.KnownBlocks.isStandable(level, pos)) {
							return Vec3.atBottomCenterOf(pos);
						}
					}
				}
			}
		}

		// Nowhere to stand round its middle (built over, flooded): on top of whatever's there.
		return Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center));
	}

	/** Who's on their way back (for the command). */
	public static List<String> report(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		long now = server.overworld().getGameTime();
		for (Pending entry : pending) {
			long left = entry.dueAt() - now;
			lines.add(entry.name() + (left > 0 ? " back in " + Math.max(1, left / 20) + " s" : " due back once its home's area is loaded")
				+ (entry.quit() ? " (rage-quit)" : ""));
		}

		return lines;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Pending", Tag.TAG_COMPOUND);
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", 1);
			tag.put("Pending", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_respawns");
	}

	private static void load(MinecraftServer server) {
		pending.clear();
		for (Tag tag : state(server).list) {
			Pending entry = tag instanceof CompoundTag compound ? Pending.load(compound) : null;
			if (entry != null) {
				pending.add(entry);
			}
		}
	}

	private static void save(MinecraftServer server) {
		ListTag list = new ListTag();
		pending.forEach(entry -> list.add(entry.save()));
		State state = state(server);
		state.list = list;
		state.setDirty();
		dirty = false;
	}
}
