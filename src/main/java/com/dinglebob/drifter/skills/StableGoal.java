package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.RideTask;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A horse kept in a stall (milestone 23 C5; the user, 2026-11-06: "bees and stables"): a town's stable has its people's
 * horses tied up in it. Off on a long trip, it walks to its horse first and unties it (the lead comes off with it), and
 * rides ({@code NavigateTask} rides its own saddled horse when it's close); back home of an evening, it rides it into its
 * stall, gets off, and ties it up again with its lead.
 */
public final class StableGoal extends Goals.Goal {
	/** A trip this long or longer is worth fetching the horse for. */
	public static final double RIDE_TRIP = 150.0;
	/** Its horse this far from it at most, to go and get it (or bring it back). */
	private static final double NEAR = 96.0;

	@Override
	public String id() {
		return "stable";
	}

	@Override
	public String name(Goals.Context context) {
		return "put its horse in the stable";
	}

	/** Its horse, alive and loaded, or null. */
	@Nullable
	public static AbstractHorse horse(DrifterEntity drifter) {
		java.util.UUID id = drifter.getDrifterBrain().horse();
		if (id == null || !(drifter.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof AbstractHorse horse) || !horse.isAlive()) {
			return null;
		}

		return horse;
	}

	/**
	 * Before a long trip: to its horse in its stall and untie it (then the walk rides it); null if there's no horse kept,
	 * it's far, or it's out already.
	 */
	@Nullable
	public static Task fetch(DrifterEntity drifter, double trip) {
		BlockPos stall = drifter.getDrifterBrain().stall();
		AbstractHorse horse = trip < RIDE_TRIP || stall == null || drifter.isPassenger() ? null : horse(drifter);
		if (horse == null || !tiedUp(horse) || !horse.isSaddled() || horse.distanceTo(drifter) > NEAR) {
			return null;
		}

		if (horse.distanceTo(drifter) > 3.0) {
			return new NavigateTask(drifter, horse.position(), 2.0, "go and get its horse", NavigateTask.Pace.STROLL);
		}

		return new Task(drifter) {
			@Override
			public String describe() {
				return "untie its horse";
			}

			@Override
			protected Status onTick() {
				this.drifter.getCamera().track(horse.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
				// (The lead off the post, back in its pack: as a player clicks the knot.)
				horse.dropLeash(true, false);
				this.drifter.getSenses().touched(horse);
				this.drifter.getInventory().add(new net.minecraft.world.item.ItemStack(Items.LEAD));
				return Status.SUCCESS;
			}
		};
	}

	/**
	 * Before a long trip with no horse of its own: one for sale at the stable of the town it's in, if it has the money (horse
	 * culture, "a traveller without a horse buying one"): over to it, then the sale (then its walk fetches it as its own).
	 * Null if there's none to buy.
	 */
	@Nullable
	public static Task buy(DrifterEntity drifter, double trip) {
		if (trip < RIDE_TRIP || drifter.isPassenger() || drifter.getDrifterBrain().horse() != null) {
			return null;
		}

		var sale = com.dinglebob.drifter.api.DrifterApi.horseForSale(drifter);
		if (sale == null) {
			return null;
		}

		AbstractHorse horse = sale.horse();
		if (horse.distanceTo(drifter) > 3.0) {
			return new NavigateTask(drifter, horse.position(), 2.0, "go and look at a horse for sale", NavigateTask.Pace.STROLL).onFoot();
		}

		return new Task(drifter) {
			@Override
			public String describe() {
				return "buy a horse";
			}

			@Override
			protected Status onTick() {
				this.drifter.getCamera().track(horse.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
				if (!com.dinglebob.drifter.api.DrifterApi.buyHorse(this.drifter, horse)) {
					return this.fail("couldn't buy the horse");
				}

				this.drifter.getSenses().touched(horse);
				this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "bought " + horse.getName().getString() + " at the stable here");
				return Status.SUCCESS;
			}
		};
	}

	/** An enemy's horse tied up this near is taken (seen, in reach to untie as a player would). */
	private static final double CAPTURE_NEAR = 12.0;

	/**
	 * At an enemy town in a war, with no horse of its own: an enemy's horse tied up near (one it sees), taken (horse culture,
	 * the user: "Only war captures"): over to it, untied, its own (its old owner's loss: "taken in war"). Null if there's none.
	 */
	@Nullable
	public static Task capture(DrifterEntity drifter) {
		if (drifter.getDrifterBrain().horse() != null || drifter.isPassenger() || !(drifter.level() instanceof ServerLevel level)) {
			return null;
		}

		long now = level.getGameTime();
		AbstractHorse found = null;
		DrifterEntity enemy = null;
		for (var awareness : drifter.getSenses().tracked()) {
			if (!(awareness.entity instanceof AbstractHorse horse) || !horse.isAlive() || horse.isVehicle() || !tiedUp(horse) && horse.isLeashed()
				|| !awareness.seenWithin(now, 20 * 5)
				|| horse.distanceTo(drifter) > CAPTURE_NEAR) {
				continue;
			}

			java.util.UUID owner = com.dinglebob.drifter.compat.IcyHorses.owner(horse);
			if (owner == null) {
				owner = horse.getOwnerUUID();
			}

			if (owner != null && level.getEntity(owner) instanceof DrifterEntity theirs && com.dinglebob.drifter.world.DrifterWars.hostile(drifter, theirs)) {
				found = horse;
				enemy = theirs;
				break;
			}
		}

		if (found == null) {
			return null;
		}

		AbstractHorse horse = found;
		DrifterEntity loser = enemy;
		if (horse.distanceTo(drifter) > 2.5) {
			return new NavigateTask(drifter, horse.position(), 2.0, "go for " + loser.getName().getString() + "'s horse", NavigateTask.Pace.TRAVEL).onFoot();
		}

		return new Task(drifter) {
			@Override
			public String describe() {
				return "take " + loser.getName().getString() + "'s horse";
			}

			@Override
			protected Status onTick() {
				if (!horse.isAlive() || horse.isVehicle() || !tiedUp(horse) && horse.isLeashed()) {
					return this.fail("the horse is gone");
				}

				this.drifter.getCamera().track(horse.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
				// (Tied: the lead off the post, into its pack, as a player clicks the knot.)
				if (tiedUp(horse)) {
					horse.dropLeash(true, false);
					this.drifter.getInventory().add(new net.minecraft.world.item.ItemStack(Items.LEAD));
				}
				com.dinglebob.drifter.api.DrifterApi.takeHorse(this.drifter, horse, loser);
				this.drifter.getSenses().touched(horse);
				return Status.SUCCESS;
			}
		};
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos stall = drifter.getDrifterBrain().stall();
		AbstractHorse horse = stall == null || context.threat().urgent() ? null : horse(drifter);
		if (horse == null || tiedUp(horse) || horse.distanceTo(drifter) > NEAR || !drifter.blockPosition().closerThan(stall, NEAR)
			|| horse.blockPosition().closerThan(stall, 2.5) && !drifter.isPassenger()) {
			return 0.0;
		}

		// Back home with it of an evening (or with nowhere far to go): into its stall.
		long time = context.time();
		boolean evening = time >= Schedule.shelterThinking(drifter) || Schedule.isNight(time);
		return evening ? 0.6 : drifter.isPassenger() && drifter.blockPosition().closerThan(stall, 48.0) ? 0.4 : 0.0;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 20;
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		BlockPos stall = drifter.getDrifterBrain().stall();
		AbstractHorse horse = horse(drifter);
		if (stall == null || horse == null) {
			return null;
		}

		return new Activity(drifter) {
			private boolean rode;
			private boolean tied;

			@Override
			public String describe() {
				return "put its horse in the stable";
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 120;
			}

			@Override
			protected Status next() {
				if (!this.rode) {
					this.rode = true;
					return this.begin(new RideTask(this.drifter, horse, Vec3.atBottomCenterOf(stall), 1.0));
				}

				if (this.tied) {
					return Status.SUCCESS;
				}

				this.tied = true;
				if (this.drifter.getVehicle() == horse) {
					this.drifter.stopRiding();
				}

				// Tied to the stall's post with its lead (one it has: the one it untied, or any).
				if (!tie(this.drifter, horse, stall)) {
					this.think("couldn't tie its horse up");
					return Status.SUCCESS;
				}

				this.drifter.getDrifterBrain().setHorse(horse.getUUID(), stall);
				this.think("put its horse in the stable");
				return Status.SUCCESS;
			}
		};
	}

	/**
	 * Its horse tied with a lead it has to the fence nearest this spot (within three blocks), the fence in its reach and the
	 * horse near: true if it was.
	 */
	public static boolean tie(DrifterEntity drifter, AbstractHorse horse, BlockPos at) {
		BlockPos post = post(drifter.level(), at);
		if (post == null || !com.dinglebob.drifter.hands.Reach.canReach(drifter, post) || horse.distanceTo(drifter) > 8.0) {
			return false;
		}

		// (Leading it: that lead, off its hand and onto the post.)
		unlead(drifter, horse);
		int slot = -1;
		for (int s = 0; s < drifter.getInventory().getContainerSize(); s++) {
			if (drifter.getInventory().getItem(s).is(Items.LEAD)) {
				slot = s;
				break;
			}
		}

		if (slot < 0) {
			return false;
		}

		if (drifter.getVehicle() == horse) {
			drifter.stopRiding();
		}

		drifter.getInventory().removeItem(slot, 1);
		horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(drifter.level(), post), true);
		return true;
	}

	/**
	 * Its horse on its lead, led on foot (horse culture: through a town it leads its horse, as a player walks one through a
	 * crowd; it rides out on the road): a lead from its pack onto the horse, in reach as a player clicks a horse with a lead.
	 * True if it's leading it now.
	 */
	public static boolean lead(DrifterEntity drifter, AbstractHorse horse) {
		if (horse.getLeashHolder() == drifter) {
			return true;
		}

		double reach = drifter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
		int slot = drifter.getInventory().findSlot(stack -> stack.is(Items.LEAD), 0, drifter.getInventory().getContainerSize());
		if (slot < 0 || horse.isLeashed() || horse.isVehicle() || horse.getBoundingBox().distanceToSqr(drifter.getEyePosition()) > reach * reach) {
			return false;
		}

		drifter.getInventory().removeItem(slot, 1);
		horse.setLeashedTo(drifter, true);
		drifter.getSenses().touched(horse);
		return true;
	}

	/** It can tie a horse up at this stall now: its post in reach. */
	public static boolean canTieAt(DrifterEntity drifter, BlockPos stall) {
		BlockPos post = post(drifter.level(), stall);
		return post != null && com.dinglebob.drifter.hands.Reach.canReach(drifter, post);
	}

	/** Tied to a post (not led by someone). */
	public static boolean tiedUp(AbstractHorse horse) {
		return horse.getLeashHolder() instanceof LeashFenceKnotEntity;
	}

	/** The lead off its horse, back in its pack (to get on it, or to tie it up). */
	public static void unlead(DrifterEntity drifter, AbstractHorse horse) {
		if (horse.getLeashHolder() == drifter) {
			horse.dropLeash(true, false);
			drifter.getInventory().add(new net.minecraft.world.item.ItemStack(Items.LEAD));
		}
	}

	/** The fence its stall's horse is tied to (the nearest within three blocks of the stall), or null. */
	@Nullable
	private static BlockPos post(net.minecraft.world.level.Level level, BlockPos stall) {
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(stall.offset(-3, -1, -3), stall.offset(3, 2, 3))) {
			if (level.getBlockState(pos).is(BlockTags.FENCES) && (best == null || pos.distSqr(stall) < best.distSqr(stall))) {
				best = pos.immutable();
			}
		}

		return best;
	}
}
