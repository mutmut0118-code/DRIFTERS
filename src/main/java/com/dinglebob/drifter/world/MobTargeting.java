package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.mixin.MobAccessor;
import com.dinglebob.drifter.mixin.NearestAttackableTargetGoalAccessor;
import com.dinglebob.drifter.mixin.TargetGoalAccessor;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Hostile mobs hunt Drifters like players (DESIGN.md sections 1, 2 and 13, "Mobs targeting the Drifter"; on by
 * default). Every mob that has an "attack the nearest player" goal gets a matching "attack the nearest Drifter" goal
 * with the same priority and the same rules: line of sight, reachability, and the mob's own conditions (drowned only
 * in water, zombified piglins only when angry, slimes only near their height, spiders only in the dark).
 *
 * <p>Brain-driven mobs (piglins, hoglins, wardens, breezes) and stare-based ones (endermen) aren't covered yet: an
 * enderman only gets angry if the Drifter hits it. TODO(milestone 11): piglins and hoglins in the Nether.
 */
public final class MobTargeting {
	private MobTargeting() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && !(entity instanceof DrifterEntity)) {
				addDrifterTargets(mob);
				// Mob Control may rebuild its targets after this (dropping ours): looked at again shortly.
				if (MobControlCompat.LOADED) {
					RECHECK.add(new java.util.AbstractMap.SimpleEntry<>(new java.lang.ref.WeakReference<>(mob), level.getGameTime() + 2));
					RECHECK.add(new java.util.AbstractMap.SimpleEntry<>(new java.lang.ref.WeakReference<>(mob), level.getGameTime() + 40));
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (RECHECK.isEmpty()) {
				return;
			}

			long now = server.overworld().getGameTime();
			RECHECK.removeIf(entry -> {
				Mob mob = entry.getKey().get();
				if (mob == null || mob.isRemoved()) {
					return true;
				}

				if (mob.level().getGameTime() < entry.getValue()) {
					return false;
				}

				addDrifterTargets(mob);
				return true;
			});
		});
	}

	/** Mobs to look at again (with Mob Control), and when. */
	private static final java.util.List<java.util.Map.Entry<java.lang.ref.WeakReference<Mob>, Long>> RECHECK = new ArrayList<>();

	private static void addDrifterTargets(Mob mob) {
		GoalSelector targetSelector = ((MobAccessor)mob).drifter$getTargetSelector();
		List<WrappedGoal> goals = new ArrayList<>(targetSelector.getAvailableGoals());
		for (WrappedGoal wrapped : goals) {
			if (wrapped.getGoal() instanceof DrifterTargetGoal) {
				return;
			}
		}

		for (WrappedGoal wrapped : goals) {
			if (!(wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> goal)
				|| ((NearestAttackableTargetGoalAccessor)goal).drifter$getTargetType() != Player.class) {
				continue;
			}

			// The plain goal, the witch's raid-aware one, and the spider's "only in the dark" one; others (the
			// enderman's stare goal) have player-only rules.
			String name = goal.getClass().getSimpleName();
			boolean spider = mob instanceof Spider;
			if (goal.getClass() != NearestAttackableTargetGoal.class && !name.contains("Witch") && !spider) {
				continue;
			}

			TargetGoalAccessor rules = (TargetGoalAccessor)goal;
			TargetingConditions conditions = ((NearestAttackableTargetGoalAccessor)goal).drifter$getTargetConditions();
			// Mob Control's player goal only takes real players: a Drifter counts as one (see MobControlCompat).
			if (MobControlCompat.governs(mob)) {
				MobControlCompat.includeDrifters(mob);
				conditions = TargetingConditions.forCombat().range(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
			}

			targetSelector.addGoal(wrapped.getPriority(),
				new DrifterTargetGoal(mob, rules.drifter$mustSee(), rules.drifter$mustReach(), conditions.copy(), spider));
		}
	}

	/** "Attack the nearest Drifter", mirroring a mob's "attack the nearest player" goal. */
	public static final class DrifterTargetGoal extends NearestAttackableTargetGoal<DrifterEntity> {
		private final boolean onlyInTheDark;

		DrifterTargetGoal(Mob mob, boolean mustSee, boolean mustReach, TargetingConditions conditions, boolean onlyInTheDark) {
			super(mob, DrifterEntity.class, 10, mustSee, mustReach, null);
			this.targetConditions = conditions;
			this.onlyInTheDark = onlyInTheDark;
		}

		/** Like the player version (which has no height box): a skeleton on a cliff sees a Drifter far below. */
		@Override
		protected AABB getTargetSearchArea(double range) {
			return this.mob.getBoundingBox().inflate(range, range, range);
		}

		@Override
		public boolean canUse() {
			// Spider rule (Spider.SpiderTargetGoal): neutral in daylight.
			if (this.onlyInTheDark && this.mob.getLightLevelDependentMagicValue() >= 0.5F) {
				return false;
			}

			return super.canUse();
		}
	}
}
