package com.dinglebob.drifter.building;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A house blown up (the user, 2026-11-06: "if a creeper explodes a home, the owner (and other people) should help rebuild
 * it with the same materials"): an explosion that takes blocks out of a Drifter's house from a plan sets it mending (the
 * plan's missing blocks built back, in the woods it was built in, from what it has and gathers, as after a raid), and up to
 * two of its group nearby join in on the same plan. The blocks the blast took aren't given back: it gathers them again.
 */
public final class HouseDamage {
	private HouseDamage() {
	}

	/** It mends for this many days. */
	private static final int MENDS_DAYS = 2;
	/** Neighbours this near the house come and help, this many at most. */
	private static final double HELPERS_NEAR = 48.0;
	private static final int HELPERS = 2;

	/** An explosion here took these blocks out (before they go). */
	public static void exploded(ServerLevel level, Vec3 center, List<BlockPos> blown, Entity source) {
		if (blown.isEmpty()) {
			return;
		}

		long now = level.getGameTime();
		List<DrifterEntity> near = level.getEntitiesOfClass(DrifterEntity.class, new AABB(center, center).inflate(96.0), DrifterEntity::isAlive);
		for (DrifterEntity owner : near) {
			Home home = owner.getDrifterBrain().home();
			BoundingBox box = home == null || !home.planDone || home.planId == null ? null : home.planBox();
			if (box == null || home.mendUntil > now) {
				continue;
			}

			int hit = 0;
			for (BlockPos pos : blown) {
				if (box.isInside(pos) && !level.getBlockState(pos).isAir()) {
					hit++;
				}
			}

			if (hit == 0) {
				continue;
			}

			home.mendUntil = now + 24000L * MENDS_DAYS;
			String what = source instanceof net.minecraft.world.entity.monster.Creeper ? "a creeper" : "an explosion";
			owner.getMemory().thoughts().add(now, what + " blew a hole in its house (" + hit + " blocks): building it back");
			owner.getPersonality().feel(com.dinglebob.drifter.personality.Mood.FRUSTRATED, 0.5, what + " blew up part of its house");
			if (owner.distanceToSqr(center) < 64 * 64) {
				ChatLines.speak(owner, Lines.pick(owner, "house.blown_up"));
			}

			// Its neighbours of its group: they help build it back (the same plan, the same woods).
			var group = com.dinglebob.drifter.world.DrifterGroups.of(owner.getUUID());
			List<DrifterEntity> helpers = new ArrayList<>();
			for (DrifterEntity other : near) {
				Home theirs = other.getDrifterBrain().home();
				if (other == owner || helpers.size() >= HELPERS || group == null || com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID()) != group
					|| theirs == null || theirs.project != null && !theirs.project.done || other.distanceToSqr(Vec3.atCenterOf(box.getCenter())) > HELPERS_NEAR * HELPERS_NEAR) {
					continue;
				}

				Home.Build build = new Home.Build();
				build.planId = home.planId;
				build.origin = home.planOrigin.immutable();
				build.rotation = home.planRotation;
				build.woods.putAll(home.planWoods);
				build.deepslate = home.planDeepslate;
				build.label = owner.getName().getString() + "'s house";
				theirs.project = build;
				helpers.add(other);
				other.getMemory().thoughts().add(now, "helping " + owner.getName().getString() + " build their house back after " + what);
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] {} blew {} blocks out of {}'s house: mending it{}", what, hit, owner.getName().getString(),
				helpers.isEmpty() ? "" : ", " + helpers.size() + " neighbours helping");
		}
	}
}
