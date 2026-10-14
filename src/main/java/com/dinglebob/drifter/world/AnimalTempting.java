package com.dinglebob.drifter.world;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.mixin.MobAccessor;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Farm animals follow a Drifter holding their food, the way vanilla tempting works for players (TemptGoal only looks
 * at players). This is what lets a Drifter lead animals into its pen with wheat (milestone 8 step 5, section 10.12).
 */
public final class AnimalTempting {
	/** How far an animal notices the food (TemptGoal uses 10). */
	private static final double RANGE = 10.0;
	private static final double STOP_DISTANCE = 2.5;

	private AnimalTempting() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Animal animal) {
				((MobAccessor)animal).drifter$getGoalSelector().addGoal(3, new FollowDrifterFood(animal));
			}
		});
	}

	private static final class FollowDrifterFood extends Goal {
		private final Animal animal;
		@Nullable
		private DrifterEntity drifter;

		FollowDrifterFood(Animal animal) {
			this.animal = animal;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Nullable
		private DrifterEntity find() {
			List<DrifterEntity> near = this.animal.level().getEntitiesOfClass(DrifterEntity.class, this.animal.getBoundingBox().inflate(RANGE),
				d -> d.isAlive() && this.animal.isFood(d.getMainHandItem()));
			DrifterEntity best = null;
			double bestDistance = Double.MAX_VALUE;
			for (DrifterEntity d : near) {
				double distance = d.distanceToSqr(this.animal);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = d;
				}
			}

			return best;
		}

		@Override
		public boolean canUse() {
			this.drifter = this.find();
			return this.drifter != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.drifter != null && this.drifter.isAlive() && this.animal.isFood(this.drifter.getMainHandItem())
				&& this.drifter.distanceToSqr(this.animal) < RANGE * RANGE * 1.5;
		}

		@Override
		public void stop() {
			this.drifter = null;
			this.animal.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (this.drifter == null) {
				return;
			}

			this.animal.getLookControl().setLookAt(this.drifter, this.animal.getMaxHeadYRot() + 20.0F, this.animal.getMaxHeadXRot());
			if (this.drifter.distanceToSqr(this.animal) < STOP_DISTANCE * STOP_DISTANCE) {
				this.animal.getNavigation().stop();
			} else {
				this.animal.getNavigation().moveTo(this.drifter, 1.15);
			}
		}
	}
}
