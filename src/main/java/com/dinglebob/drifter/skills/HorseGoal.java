package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseOnEntityTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A horse of its own (DESIGN.md section 10.7: "tames by repeated mounting, saddles from loot, uses them for long trips"):
 * with no horse and one in sight, it gets on, gets thrown, and gets on again until the horse comes round (the patient keep
 * at it longer); with its horse and a saddle, it saddles it. Long walks then ride it (NavigateTask, RideTask).
 */
public final class HorseGoal extends Goals.Goal {
	/** A wild horse this close (seen lately), and it has a saddle or will want one: it tries to break it in. */
	private static final double HORSE_RANGE = 24.0;
	private static final long TRY_AGAIN_AFTER = 20L * 60 * 5;

	@Nullable
	private AbstractHorse target;
	private boolean saddling;
	private long gaveUpAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "horse";
	}

	@Override
	public String name(Goals.Context context) {
		return this.saddling ? "saddle its horse" : "break in a horse";
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		long now = drifter.level().getGameTime();
		this.target = null;
		// On a wild horse it's breaking in: it holds on (the score stays, or another goal would take it off mid-buck).
		if (drifter.getVehicle() instanceof AbstractHorse riding && !riding.isTamed() && !context.threat().urgent()) {
			this.saddling = false;
			this.target = riding;
			return 0.6;
		}

		if (context.threat().urgent() || drifter.inCombat() || com.dinglebob.drifter.world.Battle.inBattle(drifter) || Schedule.isNight(drifter)
			|| drifter.level().dimension() != Level.OVERWORLD || drifter.isPassenger()) {
			return 0.0;
		}

		boolean saddle = drifter.getInventory().count(stack -> stack.is(Items.SADDLE)) > 0;
		AbstractHorse own = ownHorse(drifter);
		if (own != null) {
			this.saddling = true;
			this.target = own.isSaddled() || !saddle ? null : own;
			return this.target == null ? 0.0 : 0.45;
		}

		if (now - this.gaveUpAt < TRY_AGAIN_AFTER || drifter.getDrifterBrain().horse() != null) {
			return 0.0;
		}

		this.saddling = false;
		this.target = wildHorse(drifter, now);
		if (this.target == null) {
			return 0.0;
		}

		// With a saddle to put on it, worth an afternoon; without, the curious and patient have a go anyway.
		var personality = drifter.getPersonality();
		return (saddle ? 0.4 : 0.15) + 0.1 * personality.get(Trait.PATIENCE) + 0.1 * personality.get(Trait.CURIOSITY);
	}

	/** Its own horse, if it can see it (or saw it a moment ago). */
	@Nullable
	public static AbstractHorse ownHorse(DrifterEntity drifter) {
		java.util.UUID id = drifter.getDrifterBrain().horse();
		if (id == null) {
			return null;
		}

		long now = drifter.level().getGameTime();
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (awareness.entity instanceof AbstractHorse horse && horse.getUUID().equals(id) && horse.isAlive() && awareness.seenWithin(now, 20 * 10)) {
				return horse;
			}
		}

		return null;
	}

	/** A grown wild horse (a horse, donkey or mule nobody owns) seen lately near here. */
	@Nullable
	private static AbstractHorse wildHorse(DrifterEntity drifter, long now) {
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (awareness.entity instanceof AbstractHorse horse && (horse instanceof Horse || horse instanceof AbstractChestedHorse) && horse.isAlive()
				&& !horse.isBaby() && !horse.isTamed() && !horse.isVehicle() && horse.distanceTo(drifter) < HORSE_RANGE && awareness.seenWithin(now, 20 * 10)) {
				return horse;
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
		return 20 * 20;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		// (Started straight away, by a command: worked out now.)
		if (this.target == null) {
			this.urgency(context);
		}

		if (this.target == null) {
			return null;
		}

		return this.saddling ? new Saddle(context.drifter(), this.target) : new BreakIn(context.drifter(), this.target, this);
	}

	/** On, thrown, on again, until it comes round or patience runs out. */
	private static final class BreakIn extends Activity {
		private final AbstractHorse horse;
		private final HorseGoal goal;
		private final int maxTries;
		private int tries;

		BreakIn(DrifterEntity drifter, AbstractHorse horse, HorseGoal goal) {
			super(drifter);
			this.horse = horse;
			this.goal = goal;
			this.maxTries = 4 + Math.round(8.0F * drifter.getPersonality().getF(Trait.PATIENCE));
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "going to break in a horse");
		}

		@Override
		public String describe() {
			return "break in a horse";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 4;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected Status next() {
			if (!this.horse.isAlive()) {
				return this.fail("the horse is gone");
			}

			if (this.horse.isTamed()) {
				if (this.drifter.getVehicle() == this.horse) {
					this.drifter.stopRiding();
				}

				return Status.SUCCESS;
			}

			if (this.drifter.getVehicle() == this.horse) {
				// Holding on while it bucks.
				this.drifter.getKeys().releaseAll();
				return this.begin(new WaitTask(this.drifter, "hold on", () -> {
					this.drifter.keepRiding();
					return this.drifter.getVehicle() != this.horse || this.horse.isTamed();
				}, 20 * 30));
			}

			if (++this.tries > this.maxTries) {
				this.goal.gaveUpAt = this.drifter.level().getGameTime();
				this.think("gave up on the horse");
				return this.fail("the horse won't be broken in");
			}

			double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.3;
			if (this.horse.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
				this.tries--;
				return this.begin(NavigateTask.follow(this.drifter, this.horse, 1.5));
			}

			return this.begin(new MountTask(this.drifter, this.horse));
		}

		@Override
		protected boolean rethink() {
			if (!(this.current() instanceof NavigateTask)) {
				return false;
			}

			double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.3;
			return this.horse.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) <= reach * reach;
		}
	}

	/** Looking at the horse, then up onto it (a right-click with an empty hand: AbstractHorse#doPlayerRide). */
	private static final class MountTask extends Task {
		private final AbstractHorse horse;
		private int settled;

		MountTask(DrifterEntity drifter, AbstractHorse horse) {
			super(drifter);
			this.horse = horse;
		}

		@Override
		public String describe() {
			return "get on the horse";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 4;
		}

		@Override
		protected Status onTick() {
			this.drifter.getKeys().releaseAll();
			// (A horse moves about: the camera following it never quite settles, so a moment's look is enough.)
			this.drifter.getCamera().track(this.horse.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
			if (++this.settled < 6) {
				return Status.RUNNING;
			}

			if (this.horse.isVehicle()) {
				return this.fail("someone's on it");
			}

			this.horse.setEating(false);
			this.horse.setStanding(false);
			// (Right after being thrown it can't get straight back on: the game's boarding cooldown. It waits it out.)
			this.drifter.meansToRide(true);
			boolean on = this.drifter.startRiding(this.horse);
			this.drifter.meansToRide(false);
			return on ? Status.SUCCESS : Status.RUNNING;
		}
	}

	/** The saddle onto its horse (right-click with the saddle, as a player does). */
	private static final class Saddle extends Activity {
		private final AbstractHorse horse;
		private boolean done;

		Saddle(DrifterEntity drifter, AbstractHorse horse) {
			super(drifter);
			this.horse = horse;
		}

		@Override
		public String describe() {
			return "saddle its horse";
		}

		@Override
		protected Status next() {
			if (this.done || this.horse.isSaddled()) {
				return Status.SUCCESS;
			}

			if (!this.drifter.getMainHandItem().is(Items.SADDLE)) {
				return this.begin(new SelectItemTask(this.drifter, stack -> stack.is(Items.SADDLE), "the saddle"));
			}

			double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.3;
			if (this.horse.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
				return this.begin(NavigateTask.follow(this.drifter, this.horse, 1.5));
			}

			this.done = true;
			return this.begin(new UseOnEntityTask(this.drifter, this.horse, "saddle the horse"));
		}

		@Override
		protected boolean rethink() {
			if (!(this.current() instanceof NavigateTask)) {
				return false;
			}

			double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 0.3;
			return this.horse.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) <= reach * reach;
		}
	}
}
