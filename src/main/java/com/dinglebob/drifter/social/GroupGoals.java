package com.dinglebob.drifter.social;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.MineTripActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * What a group does together (DESIGN.md section 21 "Group activities"; milestone 12 step 2): joint mining trips. Team
 * wars (step 3) are in {@link WarGoals}.
 */
public final class GroupGoals {
	private GroupGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new JoinMiningTrip());
	}

	/** A group mate close by (in earshot: "going mining, coming?") setting off on a mining trip; or null. */
	@Nullable
	static DrifterEntity mateSettingOff(DrifterEntity drifter, double range) {
		DrifterGroups.Group group = DrifterGroups.of(drifter.getUUID());
		if (group == null || !(drifter.level() instanceof ServerLevel level)) {
			return null;
		}

		for (java.util.UUID id : group.members) {

			if (!id.equals(drifter.getUUID()) && level.getEntity(id) instanceof DrifterEntity mate && mate.isAlive()
				&& mate.distanceTo(drifter) <= range && mate.getDrifterBrain().activity() instanceof MineTripActivity trip && trip.settingOff()
				) {
				return mate;
			}
		}

		return null;
	}

	/** Who leads a joint trip: the group's leader, otherwise the one with more Skill. */
	static boolean leadsOver(DrifterEntity a, DrifterEntity b) {
		DrifterGroups.Group group = DrifterGroups.of(a.getUUID());
		if (group != null && group.leader.equals(a.getUUID())) {
			return true;
		}

		if (group != null && group.leader.equals(b.getUUID())) {
			return false;
		}

		double skillA = a.getPersonality().get(Trait.SKILL);
		double skillB = b.getPersonality().get(Trait.SKILL);
		return skillA != skillB ? skillA > skillB : a.getUUID().compareTo(b.getUUID()) < 0;
	}

	/**
	 * Going mining with a group mate who's setting off (section 21: "joint mining trips"): the sociable ones more often,
	 * if it's free, healthy, it's day, and it has a pickaxe good enough for iron and some torches. Not every time: a while
	 * after a joint trip it gets on with its own things.
	 */
	public static final class JoinMiningTrip extends Goals.Goal {
		private static final double RANGE = 32.0;
		private static final long AGAIN_AFTER = 20L * 60 * 10;
		private long lastJoined = Long.MIN_VALUE / 2;
		@Nullable
		private DrifterEntity mate;

		@Override
		public String id() {
			return "jointrip";
		}

		@Override
		public String name(Goals.Context context) {
			return this.mate == null ? "go mining with a group mate" : "go mining with " + this.mate.getName().getString();
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			// Both setting off for the same ore at once from the same place: the one not leading goes along with the other (one
			// mine, together), rather than two trips side by side.
			// Under way: it sticks with it (and with the mate).
			if (drifter.getDrifterBrain().currentGoal() == this && drifter.getDrifterBrain().activity() instanceof MineTripActivity trip && trip.joint()) {
				return 0.8;
			}

			if (drifter.getDrifterBrain().activity() instanceof MineTripActivity own) {
				DrifterEntity mate = own.settingOff() && !own.joint() ? mateSettingOff(drifter, RANGE) : null;
				this.mate = mate != null && mate.getDrifterBrain().activity() instanceof MineTripActivity theirs && !theirs.joint() && own.sameOre(theirs)
					&& leadsOver(mate, drifter) ? mate : null;
				return this.mate == null ? 0.0 : 1.0;
			}

			// (A shopkeeper in its open hours keeps its shop: found in testing, the town's keeper off mining all day.)
			if (drifter.level().getGameTime() - this.lastJoined < AGAIN_AFTER || drifter.getShop() != null && com.dinglebob.drifter.trade.StallGoal.open(drifter)
				|| Schedule.isNight(drifter) || drifter.getHealth() < 14.0F || context.stage() < 4
				|| drifter.getInventory().count(stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) == 0
				|| drifter.getInventory().count(stack -> stack.is(Items.TORCH)) < 4) {
				this.mate = null;
				return 0.0;
			}

			this.mate = mateSettingOff(drifter, RANGE);
			return this.mate == null ? 0.0 : 0.7 + 0.25 * drifter.getPersonality().get(Trait.SOCIABILITY);
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			// (A factor: 1 is no risk.)
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity mate = this.mate != null ? this.mate : mateSettingOff(context.drifter(), RANGE);
			if (mate == null || !(mate.getDrifterBrain().activity() instanceof MineTripActivity trip)) {
				return null;
			}

			this.lastJoined = context.drifter().level().getGameTime();
			context.drifter().getMemory().thoughts().add(this.lastJoined, "going mining with " + mate.getName().getString() + " (for " + trip.oreLabel() + ")");
			mate.getMemory().thoughts().add(this.lastJoined, context.drifter().getName().getString() + " is coming mining too");
			return MineTripActivity.alongside(context.drifter(), trip, mate);
		}
	}
}
