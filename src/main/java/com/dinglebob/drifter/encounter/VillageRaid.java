package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.Disposition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A village raid (docs/ENCOUNTERS.md 9): the player is in a village and Drifter raiders come for it, three to five,
 * armed as well as the player, going for the villagers and for anyone who stands in their way. Half of them down and the
 * rest fall back. If the player helped (brought one down) and the raiders are beaten, the village is grateful as after
 * any raid (Hero of the Village). Bringing a raider down is no crime.
 */
final class VillageRaid extends Encounter {
	private static final String RAIDER = "raider";
	/** They go for villagers they see within this far, and the player if they're this close (or hit them). */
	private static final double VILLAGER_RANGE = 24.0;
	private static final double PLAYER_RANGE = 10.0;

	private final Vec3 village;
	private final List<DrifterEntity> raiders = new ArrayList<>();
	private final Set<UUID> counted = new HashSet<>();
	private int downedByPlayer;
	private boolean shouted;

	private VillageRaid(ServerPlayer player, Vec3 village) {
		super(Encounters.Kind.RAID, player);
		this.village = village;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(40.0, 16.0, 40.0), Villager::isAlive);
		if (villagers.size() < 3) {
			return null;
		}

		Vec3 center = Vec3.ZERO;
		for (Villager villager : villagers) {
			center = center.add(villager.position());
		}

		center = center.scale(1.0 / villagers.size());
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, center, 45.0, 65.0, null, random);
		if (spot == null) {
			return null;
		}

		VillageRaid raid = new VillageRaid(player, center);
		int tier = EncounterSpawner.gearTier(player);
		// One band, in one set of colours.
		var band = EncounterSpawner.band(level, tier, random);
		int count = 3 + random.nextInt(2) + (tier >= 1 ? 1 : 0);
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + random.nextInt(7) - 3, spot.getZ() + random.nextInt(7) - 3);
			boolean archer = i == count - 1;
			DrifterEntity raider = EncounterSpawner.spawn(level, at == null ? spot : at, Archetype.BANDIT, drifter -> {
				drifter.getSocial().setDisposition(Disposition.BANDIT);
				EncounterSpawner.fighter(drifter, band, archer, random);
				EncounterSpawner.traits(drifter, 0.85, 0.9, 0.2, 0.6);
				drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName()).trust = -50.0;
			});
			if (raider != null) {
				raid.join(raider, RAIDER);
				raid.raiders.add(raider);
				EncounterSpawner.add(level, raider);
				think(raider, "raiding the village by " + BlockPos.containing(center).toShortString());
			}
		}

		return raid;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 8;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		for (DrifterEntity raider : this.raiders) {
			if (!raider.isAlive() && this.counted.add(raider.getUUID()) && player != null && raider.getLastHurtByMob() == player) {
				this.downedByPlayer++;
			}
		}

		// Half of them down (or all): the rest fall back.
		if (this.members.size() * 2 <= this.raiders.size()) {
			if (!this.members.isEmpty()) {
				say(this.members.get(0), "fall back! fall back!");
			}

			boolean helped = this.downedByPlayer > 0 && player != null && player.position().distanceTo(this.village) < 80.0;
			if (helped) {
				player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, 0, false, false, true));
			}

			this.end(helped ? "beaten off, with the player's help" : "the raiders fell back");
			return;
		}

		for (DrifterEntity raider : this.members) {
			// Anyone standing in their way first (close, or who hit them); else the nearest villager it sees.
			boolean hit = player != null && raider.getLastHurtByMob() == player && raider.tickCount - raider.getLastHurtByMobTimestamp() < 100;
			if (player != null && sees(raider, player) && (raider.distanceTo(player) < PLAYER_RANGE || hit)) {
				raider.getDrifterBrain().pursue(raider, player, this.now());
				continue;
			}

			Villager nearest = null;
			double best = VILLAGER_RANGE;
			for (Villager villager : raider.level().getEntitiesOfClass(Villager.class, raider.getBoundingBox().inflate(VILLAGER_RANGE), Villager::isAlive)) {
				double distance = raider.distanceTo(villager);
				if (distance < best && sees(raider, villager)) {
					best = distance;
					nearest = villager;
				}
			}

			if (nearest != null) {
				if (!this.shouted) {
					this.shouted = true;
					say(raider, raider.getRandom().nextBoolean() ? "take everything!" : "burn it down!");
				}

				var pursuit = raider.getDrifterBrain().pursuit();
				if (pursuit == null || !pursuit.active(raider, this.now())) {
					raider.getDrifterBrain().pursue(raider, nearest, this.now());
				}
			}
		}
	}

	@Override
	protected void playerDied() {
		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		var pursuit = drifter.getDrifterBrain().pursuit();
		return pursuit != null && pursuit.active(drifter, this.now()) ? 0.0 : 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "raid the village";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return player.getUUID().equals(this.player);
	}

	@Override
	public boolean relentless(DrifterEntity drifter) {
		return true;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		return search(drifter, "raid the village", "villagers", () -> this.village, 20.0);
	}

	@Override
	public String status() {
		return super.status() + ", " + this.members.size() + " of " + this.raiders.size() + " raiders left, " + this.downedByPlayer + " brought down by the player";
	}
}
