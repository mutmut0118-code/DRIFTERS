package com.dinglebob.drifter.trade;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.TradeTask;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;

/**
 * Going to trade (DESIGN.md section 10.12): to a villager it knows has something it wants and can pay for (selling its
 * surplus there first if it has to), or, with emeralds or goods to sell, to a villager in sight whose trades it hasn't
 * seen, to have a look. In the daytime, when nothing's pressing.
 */
public final class TradeGoal extends Goals.Goal {
	/** Trades it saw longer ago than this it doesn't go by (they restock and prices move; 5 days). */
	private static final long SEEN_FOR = 24000L * 5;
	/** How far it goes to trade. */
	private static final double TRADE_RANGE = 160.0;
	/** A villager in sight this close whose trades it hasn't seen (or not for 2 days): it has a look. */
	private static final double LOOK_RANGE = 24.0;
	private static final long LOOK_AGAIN_AFTER = 24000L * 2;
	private static final double LOOK_URGENCY = 0.3;
	/** After a visit that came to nothing, not that villager again for a while. */
	private static final long NOTHING_AGAIN_AFTER = 20L * 60 * 5;

	@Nullable
	private Trading.Plan plan;
	@Nullable
	private UUID lookAt;
	private long checkedAt = Long.MIN_VALUE / 2;
	private UUID lastFailed;
	private long lastFailedAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "trade";
	}

	@Override
	public String name(Goals.Context context) {
		return this.plan != null ? this.plan.why() : "see what a villager trades";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		long now = drifter.level().getGameTime();
		if (context.threat().urgent() || drifter.inCombat() || com.dinglebob.drifter.world.Battle.inBattle(drifter) || Schedule.isNight(drifter)
			|| drifter.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return 0.0;
		}

		if (now - this.checkedAt >= 20) {
			this.checkedAt = now;
			this.plan = null;
			this.lookAt = null;
			double best = 0.0;
			for (TradeBook.Seen seen : drifter.getDrifterBrain().trades().all()) {
				if (now - seen.seenAt() > SEEN_FOR || seen.where().distSqr(drifter.blockPosition()) > TRADE_RANGE * TRADE_RANGE
					|| seen.villager().equals(this.lastFailed) && now - this.lastFailedAt < NOTHING_AGAIN_AFTER) {
					continue;
				}

				Trading.Plan plan = Trading.plan(drifter, seen);
				if (plan != null && plan.value() > best) {
					best = plan.value();
					this.plan = plan;
				}
			}

			if (this.plan == null && (Trading.emeralds(drifter) > 0 || hasSurplus(drifter))) {
				this.lookAt = unseenVillager(drifter, now);
			}
		}

		if (this.plan != null) {
			return this.plan.value();
		}

		// The curious and the greedy go and have a look sooner.
		var personality = drifter.getPersonality();
		return this.lookAt != null ? LOOK_URGENCY + 0.15 * personality.get(com.dinglebob.drifter.personality.Trait.CURIOSITY)
			+ 0.15 * personality.get(com.dinglebob.drifter.personality.Trait.GREED) : 0.0;
	}

	/** Anything it would sell. */
	private static boolean hasSurplus(DrifterEntity drifter) {
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			if (!inventory.getItem(slot).isEmpty() && Trading.surplus(drifter, inventory.getItem(slot).getItem()) > 0) {
				return true;
			}
		}

		return false;
	}

	/** A grown villager with a job, seen in the last few seconds close by, whose trades it hasn't seen lately. */
	@Nullable
	private UUID unseenVillager(DrifterEntity drifter, long now) {
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (awareness.identified && awareness.seenWithin(now, 20 * 10) && awareness.entity instanceof AbstractVillager villager && villager.isAlive() && !villager.isBaby()
				&& villager.distanceTo(drifter) < LOOK_RANGE && !villager.isSleeping()
				&& !(villager instanceof Villager v && (v.getVillagerData().getProfession() == VillagerProfession.NONE
					|| v.getVillagerData().getProfession() == VillagerProfession.NITWIT))
				&& !(villager.getUUID().equals(this.lastFailed) && now - this.lastFailedAt < NOTHING_AGAIN_AFTER)) {
				TradeBook.Seen seen = drifter.getDrifterBrain().trades().get(villager.getUUID());
				if (seen == null || now - seen.seenAt() > LOOK_AGAIN_AFTER) {
					return villager.getUUID();
				}
			}
		}

		return null;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 10;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (this.plan != null) {
			return new Visit(drifter, this.plan.villager(), this.plan.where(), false, this.plan.why(), this);
		}

		if (this.lookAt != null) {
			return new Visit(drifter, this.lookAt, drifter.blockPosition(), true, "see what a villager trades", this);
		}

		return null;
	}

	void failed(UUID villager, long now) {
		this.lastFailed = villager;
		this.lastFailedAt = now;
	}

	/** To the villager (where it last was, or following it), then the trade. */
	private static final class Visit extends Activity {
		private final UUID villager;
		private final BlockPos where;
		private final boolean lookOnly;
		private final String why;
		private final TradeGoal goal;
		private boolean walked;
		private boolean lookedAround;
		private boolean traded;

		Visit(DrifterEntity drifter, UUID villager, BlockPos where, boolean lookOnly, String why, TradeGoal goal) {
			super(drifter);
			this.villager = villager;
			this.where = where;
			this.lookOnly = lookOnly;
			this.why = why;
			this.goal = goal;
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "off to trade: " + why);
		}

		@Override
		public String describe() {
			return this.why;
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 3;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return !(step instanceof TradeTask);
		}

		@Override
		protected void stepDone(Task step, Status status) {
			if (step instanceof TradeTask trade) {
				this.traded = true;
				if (status != Status.SUCCESS || trade.traded() == 0 && !this.lookOnly) {
					this.goal.failed(this.villager, this.drifter.level().getGameTime());
				}
			}
		}

		/** Following it (a villager wanders): once it's in reach, on to the trade. */
		@Override
		protected boolean rethink() {
			if (!(this.current() instanceof NavigateTask)) {
				return false;
			}

			AbstractVillager found = this.find();
			double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.3;
			return found != null && found.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) <= reach * reach;
		}

		@Override
		protected Status next() {
			if (this.traded) {
				return Status.SUCCESS;
			}

			AbstractVillager found = this.find();
			if (found != null) {
				double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.3;
				if (found.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) <= reach * reach) {
					return this.begin(new TradeTask(this.drifter, found, this.lookOnly));
				}

				return this.begin(NavigateTask.follow(this.drifter, found, 1.5));
			}

			if (!this.walked && this.drifter.blockPosition().distSqr(this.where) > 9.0) {
				this.walked = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.where), 3.0, "walk over to the villager"));
			}

			if (!this.lookedAround) {
				this.lookedAround = true;
				return this.begin(new LookAroundTask(this.drifter));
			}

			// Not where it was: it has gone off (or died). It doesn't go by those trades any more.
			this.drifter.getDrifterBrain().trades().forget(this.villager);
			this.goal.failed(this.villager, this.drifter.level().getGameTime());
			return this.fail("couldn't find the villager");
		}

		/** The villager, if it can see it (or saw it a moment ago). */
		@Nullable
		private AbstractVillager find() {
			long now = this.drifter.level().getGameTime();
			for (Awareness awareness : this.drifter.getSenses().tracked()) {
				if (awareness.entity instanceof AbstractVillager villager && villager.getUUID().equals(this.villager) && villager.isAlive()
					&& awareness.seenWithin(now, 20 * 5)) {
					return villager;
				}
			}

			return null;
		}
	}
}
