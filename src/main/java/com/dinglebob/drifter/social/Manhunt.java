package com.dinglebob.drifter.social;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The manhunt test mode (DESIGN.md section 25), hunter role: a 1v1 between a real player (the runner) and a Drifter (the
 * hunter). Off unless started by command; it changes no core rules. Toward the runner only, the Drifter is Vengeful for
 * the game (both sides agreed, so no warning first); it has a tracking compass that tells it the runner's direction when
 * it checks it (never the exact position); it respawns at the world spawn with nothing when it dies; the runner dying
 * ends the game, and the runner wins by reaching the Nether (the simple goal for early tests) or killing the dragon.
 */
public final class Manhunt {
	private Manhunt() {
	}

	public enum Goal {
		NETHER, DRAGON
	}

	@Nullable
	private static UUID hunter;
	@Nullable
	private static UUID runner;
	private static String runnerName = "";
	private static Goal goal = Goal.NETHER;

	public static boolean active() {
		return hunter != null && runner != null;
	}

	/** This Drifter is hunting this player in a manhunt. */
	public static boolean hunting(DrifterEntity drifter, Player player) {
		return active() && drifter.getUUID().equals(hunter) && player.getUUID().equals(runner);
	}

	public static boolean isHunter(DrifterEntity drifter) {
		return active() && drifter.getUUID().equals(hunter);
	}

	@Nullable
	public static UUID runner() {
		return runner;
	}

	public static String status() {
		return active() ? "manhunt: " + runnerName + " runs (goal: " + goal.name().toLowerCase(java.util.Locale.ROOT) + "), a Drifter hunts" : "no manhunt running";
	}

	/** Where the hunter is and how far from the runner (for the person running the test). */
	public static String status(MinecraftServer server) {
		if (!active()) {
			return status();
		}

		DrifterEntity found = null;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(hunter) instanceof DrifterEntity drifter) {
				found = drifter;
			}
		}

		ServerPlayer player = server.getPlayerList().getPlayer(runner);
		if (found == null) {
			return status() + "; the hunter isn't loaded (too far from anyone)";
		}

		String where = found.getName().getString() + " is at " + found.blockPosition().toShortString();
		if (player != null && player.level() == found.level()) {
			where += String.format(java.util.Locale.ROOT, ", %.0f blocks from %s", player.distanceTo(found), runnerName);
		}

		return status() + "; " + where;
	}

	public static void start(DrifterEntity drifter, ServerPlayer player, Goal winGoal) {
		hunter = drifter.getUUID();
		runner = player.getUUID();
		runnerName = player.getGameProfile().getName();
		goal = winGoal;
		lastHunterChunk = drifter.chunkPosition();
		lastHunterLevel = drifter.level().dimension();
		save(drifter.getServer());
		prepare(drifter);
		announce(drifter.getServer(), "Manhunt! " + drifter.getName().getString() + " is hunting " + runnerName + ". "
			+ runnerName + " wins by " + (winGoal == Goal.NETHER ? "reaching the Nether" : "killing the Ender Dragon") + ".");
	}

	public static void stop(@Nullable MinecraftServer server, String why) {
		if (active() && server != null) {
			announce(server, "Manhunt over: " + why);
		}

		hunter = null;
		runner = null;
		lastHunterChunk = null;
		lastHunterLevel = null;
		save(server);
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world (user's request: a manhunt survives a restart)
	// ---------------------------------------------------------------------------------------------

	private static final String DATA_NAME = "drifter_manhunt";

	/** The world's saved manhunt record. */
	private static final class State extends net.minecraft.world.level.saveddata.SavedData {
		CompoundTag game = new CompoundTag();

		static State load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			State state = new State();
			state.game = tag.getCompound("Game");
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
			tag.put("Game", this.game);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(State::new, State::load, null), DATA_NAME);
	}

	/** Writes the current game (or none) into the world's save. */
	private static void save(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}

		State state = state(server);
		CompoundTag game = new CompoundTag();
		if (active()) {
			game.putUUID("Hunter", hunter);
			game.putUUID("Runner", runner);
			game.putString("RunnerName", runnerName);
			game.putString("Goal", goal.name());
			if (lastHunterChunk != null && lastHunterLevel != null) {
				game.putLong("HunterChunk", lastHunterChunk.toLong());
				game.putString("HunterLevel", lastHunterLevel.location().toString());
			}
		}

		state.game = game;
		state.setDirty();
	}

	/** Picks up a game saved with this world (or clears the last world's). */
	private static void load(MinecraftServer server) {
		CompoundTag game = state(server).game;
		if (game.hasUUID("Hunter") && game.hasUUID("Runner")) {
			hunter = game.getUUID("Hunter");
			runner = game.getUUID("Runner");
			runnerName = game.getString("RunnerName");
			try {
				goal = Goal.valueOf(game.getString("Goal"));
			} catch (IllegalArgumentException unknown) {
				goal = Goal.NETHER;
			}

			if (game.contains("HunterChunk")) {
				lastHunterChunk = new net.minecraft.world.level.ChunkPos(game.getLong("HunterChunk"));
				lastHunterLevel = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
					net.minecraft.resources.ResourceLocation.parse(game.getString("HunterLevel")));
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("Manhunt carried on from the save: {} is still being hunted", runnerName);
		} else {
			hunter = null;
			runner = null;
		}
	}

	/** The hunter's standing with the runner for the game, and its tracking compass. */
	private static void prepare(DrifterEntity drifter) {
		Relationship r = drifter.getSocial().getOrMeet(runner, runnerName);
		r.trust = -Relationship.MAX_TRUST;
		r.wronged = true;
		if (r.grudges.stream().noneMatch(Relationship.Grudge::forever)) {
			r.grudges.add(new Relationship.Grudge(SocialEvent.ATTACKED, 100.0, drifter.level().getGameTime(), true));
		}

		if (drifter.getInventory().count(stack -> stack.is(Items.COMPASS)) == 0) {
			ItemStack compass = new ItemStack(Items.COMPASS);
			compass.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Tracking compass"));
			drifter.getInventory().add(compass);
		}
	}

	/**
	 * What the tracking compass shows: the runner's direction, as a point some way along it at its own height (only in the
	 * runner's dimension; elsewhere it spins). Null if it has no compass or it spins.
	 */
	@Nullable
	public static Vec3 compassPoint(DrifterEntity drifter, double distance) {
		if (!isHunter(drifter) || drifter.getInventory().count(stack -> stack.is(Items.COMPASS)) == 0 || drifter.getServer() == null) {
			return null;
		}

		ServerPlayer player = drifter.getServer().getPlayerList().getPlayer(runner);
		if (player == null || player.level() != drifter.level()) {
			return null;
		}

		Vec3 toward = player.position().subtract(drifter.position()).multiply(1.0, 0.0, 1.0);
		if (toward.lengthSqr() < 1.0) {
			return null;
		}

		return drifter.position().add(toward.normalize().scale(distance));
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(Manhunt::load);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			hunter = null;
			runner = null;
		});

		// The hunter dies: it comes back at the world spawn with nothing, like a manhunt hunter.
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!active()) {
				return;
			}

			if (victim instanceof DrifterEntity drifter && drifter.getUUID().equals(hunter)) {
				MinecraftServer server = drifter.getServer();
				if (server != null) {
					CompoundTag saved = new CompoundTag();
					drifter.saveWithoutId(saved);
					server.execute(() -> respawn(server, saved));
				}
			} else if (victim instanceof Player player && player.getUUID().equals(runner)) {
				stop(player.getServer(), "the hunter wins! " + runnerName + " died.");
			} else if (victim instanceof EnderDragon && goal == Goal.DRAGON) {
				stop(victim.getServer(), runnerName + " wins: the dragon is dead!");
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!active() || server.getTickCount() % 20 != 0) {
				return;
			}

			keepHunterLoaded(server);

			ServerPlayer player = server.getPlayerList().getPlayer(runner);
			if (player != null && goal == Goal.NETHER && player.level().dimension() == Level.NETHER) {
				stop(server, runnerName + " wins: they reached the Nether!");
			}
		});
	}

	/**
	 * Keeps the chunks round the hunter loaded and running, like the chunks round a player: otherwise it freezes once the
	 * runner is far away (Minecraft only runs mobs near players). Its last spot is saved so it comes back after a restart.
	 */
	private static void keepHunterLoaded(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(hunter) instanceof DrifterEntity drifter) {
				lastHunterLevel = level.dimension();
				lastHunterChunk = drifter.chunkPosition();
				break;
			}
		}

		if (lastHunterChunk == null || lastHunterLevel == null) {
			return;
		}

		ServerLevel level = server.getLevel(lastHunterLevel);
		if (level != null) {
			level.getChunkSource().addRegionTicket(HUNTER_TICKET, lastHunterChunk, TICKET_RADIUS, lastHunterChunk);
		}

		if (server.getTickCount() % 200 == 0) {
			save(server);
		}
	}

	/** A short-lived ticket renewed every second while the game runs (it lapses on its own once the game ends). */
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> HUNTER_TICKET =
		net.minecraft.server.level.TicketType.create("drifter_hunter", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 60);
	/** Chunks round the hunter kept running (3: its chunk and the ones next to it tick entities, as near a player). */
	private static final int TICKET_RADIUS = 3;
	@Nullable
	private static net.minecraft.world.level.ChunkPos lastHunterChunk;
	@Nullable
	private static net.minecraft.resources.ResourceKey<Level> lastHunterLevel;

	/** The same Drifter (memory, personality, standing) back at the world spawn, with an empty inventory. */
	private static void respawn(MinecraftServer server, CompoundTag saved) {
		ServerLevel level = server.overworld();
		DrifterEntity fresh = com.dinglebob.drifter.registry.ModEntities.DRIFTER.create(level);
		if (fresh == null) {
			return;
		}

		for (String key : new String[]{"DrifterInventory", "ArmorItems", "HandItems", "UUID", "Health", "DeathTime", "HurtTime", "Fire", "Pos",
			"Motion", "XpP", "XpLevel", "XpTotal"}) {
			saved.remove(key);
		}

		fresh.load(saved);
		BlockPos spawn = level.getSharedSpawnPos();
		fresh.moveTo(spawn.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
			spawn.getZ() + 0.5, 0.0F, 0.0F);
		fresh.setHealth(fresh.getMaxHealth());
		level.addFreshEntity(fresh);
		hunter = fresh.getUUID();
		save(server);
		prepare(fresh);
		announce(server, fresh.getName().getString() + " respawned and is back on the hunt.");
	}

	private static void announce(@Nullable MinecraftServer server, String text) {
		if (server != null) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("[Drifter] " + text), false);
		}
	}

	/** A hunter's quarry, or null (for the Hunt goal). */
	@Nullable
	public static Player quarry(DrifterEntity drifter) {
		return isHunter(drifter) && drifter.getServer() != null ? drifter.getServer().getPlayerList().getPlayer(runner) : null;
	}
}
