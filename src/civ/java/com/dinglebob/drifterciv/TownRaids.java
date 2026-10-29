package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Raids on Drifter towns (milestone 23, docs/LIVED_WORLD.md A7: "vanilla raids need villagers; the addon's raid: a
 * pillager band that comes for a Drifter town (someone with Bad Omen walks in...); the townspeople fight, hide or flee by
 * their natures; a player who helps is a hero to the town"). Vanilla's raids don't come to a Drifter town (its beds and
 * bell aren't villagers', so to vanilla it's no village), so a player with Bad Omen walking into one brings this instead:
 * after half a minute, a band of pillagers and vindicators from beyond its edge, making for its plaza, in waves (two; a
 * city three), the next when the last is mostly down. All of them down: the town fought it off, and if the player brought
 * some down its people think the better of them. Ten minutes on with raiders still about: they make off.
 */
final class TownRaids {
	private TownRaids() {
	}

	/** One coming for a town, or on it. */
	private static final class Raid {
		final Town town;
		final UUID player;
		final String playerName;
		final long startAt;
		final int waves;
		int wave;
		long waveAt;
		final List<Mob> raiders = new ArrayList<>();
		int downedByPlayer;
		long lastWaveAt;
		int lastWaveSize;
		/** Raiders brought down so far (by anyone). */
		int downed;

		Raid(Town town, ServerPlayer player, long startAt, int waves) {
			this.town = town;
			this.player = player.getUUID();
			this.playerName = player.getGameProfile().getName();
			this.startAt = startAt;
			this.waves = waves;
			this.waveAt = startAt;
		}
	}

	/** In a town: this near its middle. */
	private static final double IN_TOWN = 64.0;
	/** Half a minute from walking in to the first wave (vanilla's raid omen's). */
	private static final long OMEN = 20L * 30;
	/** The next wave once this few of the last are left (or this long after it). */
	private static final double NEXT_WHEN_LEFT = 0.25;
	private static final long NEXT_AFTER = 20L * 180;
	/** Raiders still about this long after the last wave: they make off. */
	private static final long GIVES_UP = 20L * 600;
	/** Trust its people give a player who helped fight it off (and a little more for each raider they brought down). */
	private static final double HERO = 12.0;
	private static final double PER_RAIDER = 2.0;

	private static final Map<UUID, Raid> raids = new HashMap<>();

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownRaids::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && source.getEntity() instanceof ServerPlayer player) {
				for (Raid raid : raids.values()) {
					if (raid.player.equals(player.getUUID()) && raid.raiders.contains(mob)) {
						raid.downedByPlayer++;
					}
				}
			}
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		long now = server.overworld().getGameTime();
		// Bad Omen walked into a town: a raid's coming (the omen spent on it).
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.hasEffect(MobEffects.BAD_OMEN) || player.serverLevel().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
				continue;
			}

			Town town = Towns.nearest(player.serverLevel().dimension().location().toString(), player.blockPosition());
			if (town == null || !town.center.closerThan(player.blockPosition(), IN_TOWN) || raids.containsKey(town.id)) {
				continue;
			}

			player.removeEffect(MobEffects.BAD_OMEN);
			int waves = Nations.size(town) >= TownBuilder.CITY ? 3 : 2;
			raids.put(town.id, new Raid(town, player, now + OMEN, waves));
			player.sendSystemMessage(Component.literal("Something's coming for " + town.name + "...").withStyle(net.minecraft.ChatFormatting.DARK_RED));
			Chronicle.add(server, "town", "A raid was coming for " + town.name);
		}

		for (Raid raid : List.copyOf(raids.values())) {
			ServerLevel level = level(server, raid.town);
			if (level == null) {
				raids.remove(raid.town.id);
				continue;
			}

			int before = raid.raiders.size();
			raid.raiders.removeIf(mob -> !mob.isAlive() && (mob.isRemoved() || mob.isDeadOrDying()));
			raid.downed += before - raid.raiders.size();
			// Each living raider without a target makes for the plaza.
			for (Mob mob : raid.raiders) {
				if (mob.getTarget() == null && mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(raid.town.center.getX() + 0.5, raid.town.center.getY(), raid.town.center.getZ() + 0.5, 1.0);
				}
			}

			boolean waveDown = raid.raiders.size() <= Math.max(0, Math.round(NEXT_WHEN_LEFT * raid.lastWaveSize));
			if (raid.wave < raid.waves && now >= raid.waveAt && (raid.wave == 0 || waveDown || now - raid.lastWaveAt >= NEXT_AFTER)) {
				spawnWave(level, raid, now);
				continue;
			}

			if (raid.wave >= raid.waves && raid.raiders.isEmpty()) {
				won(server, level, raid);
				raids.remove(raid.town.id);
			} else if (raid.wave >= raid.waves && now - raid.lastWaveAt > GIVES_UP) {
				raid.raiders.forEach(Mob::discard);
				Chronicle.add(server, "town", "The raiders made off from " + raid.town.name + " before they could be driven out (" + raid.downed + " brought down, "
					+ raid.raiders.size() + " got away)");
				raids.remove(raid.town.id);
			}
		}
	}

	/** A wave: four or more (more for a bigger town, and each wave bigger), from a spot beyond its edge, making for its plaza. */
	private static void spawnWave(ServerLevel level, Raid raid, long now) {
		raid.wave++;
		raid.lastWaveAt = now;
		var random = level.getRandom();
		// (Its people have to be able to win it: a vindicator's axe kills in a few hits; found in testing, a town of 21 lost 12
		// lives to waves of 7 and 8, a third of them vindicators.)
		int size = Math.min(10, 2 + raid.wave + Nations.size(raid.town) / 8);
		raid.lastWaveSize = size;
		double angle = random.nextDouble() * Math.PI * 2.0;
		int x = raid.town.center.getX() + (int)(Math.cos(angle) * 80);
		int z = raid.town.center.getZ() + (int)(Math.sin(angle) * 80);
		for (int i = 0; i < size; i++) {
			int px = x + random.nextInt(9) - 4;
			int pz = z + random.nextInt(9) - 4;
			if (!level.hasChunk(px >> 4, pz >> 4)) {
				continue;
			}

			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(px, 0, pz));
			EntityType<? extends Mob> type = random.nextFloat() < 0.75F ? EntityType.PILLAGER : EntityType.VINDICATOR;
			if (raid.wave == raid.waves && i == 0 && raid.waves >= 3) {
				type = EntityType.RAVAGER;
			}

			Mob mob = type.spawn(level, at, MobSpawnType.EVENT);
			if (mob != null) {
				mob.setPersistenceRequired();
				raid.raiders.add(mob);
			}
		}

		Chronicle.add(level.getServer(), "town", "Raiders came for " + raid.town.name + (raid.waves > 1 ? " (wave " + raid.wave + " of " + raid.waves + ")" : ""));
	}

	/** Fought off: the chronicle, and its people think better of the player who helped. */
	private static void won(MinecraftServer server, ServerLevel level, Raid raid) {
		Chronicle.add(server, "town", raid.town.name + " fought off a raid (" + raid.downed + " raiders brought down" + (raid.downedByPlayer > 0 ? ", "
			+ raid.downedByPlayer + " of them by " + raid.playerName : "") + ")");
		if (raid.downedByPlayer <= 0) {
			return;
		}

		var group = raid.town.group == null ? null : DrifterApi.groupById(raid.town.group);
		if (group == null) {
			return;
		}

		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null) {
				DrifterApi.addTrustInPlayer(member, raid.player, raid.playerName, HERO + PER_RAIDER * Math.min(10, raid.downedByPlayer));
			}
		}

		ServerPlayer player = server.getPlayerList().getPlayer(raid.player);
		if (player != null) {
			player.sendSystemMessage(Component.literal("The people of " + raid.town.name + " won't forget that.").withStyle(net.minecraft.ChatFormatting.GOLD));
		}
	}

	@Nullable
	private static ServerLevel level(MinecraftServer server, Town town) {
		return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
			net.minecraft.resources.ResourceLocation.parse(town.dimension)));
	}

	/** Debug: a raid on the nearest town now, as if this player had walked in with Bad Omen. */
	static boolean debugRaid(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.BAD_OMEN, 600, 0));
		Town town = Towns.nearest(player.serverLevel().dimension().location().toString(), player.blockPosition());
		return town != null && town.center.closerThan(player.blockPosition(), IN_TOWN);
	}
}
