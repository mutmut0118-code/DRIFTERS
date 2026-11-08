package com.dinglebob.drifter.brain.activity;

import java.util.Locale;
import java.util.Set;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Taking something it stored out of its chest at home (the planner's "fetch" step, section 9: stage and plans count home
 * storage). If the chest turns out not to have it (someone took it), opening it updates what it knows and it plans again.
 */
public class FetchActivity extends Activity {
	private final BlockPos chest;
	private final Item item;
	private final int count;
	private boolean walked;
	private boolean done;

	public FetchActivity(DrifterEntity drifter, BlockPos chest, Item item, int count) {
		super(drifter);
		this.chest = chest;
		this.item = item;
		this.count = count;
	}

	@Override
	protected void onStart() {
		super.onStart();
		USERS.computeIfAbsent(this.chest.immutable(), pos -> new java.util.HashSet<>()).add(this.drifter.getUUID());
	}

	/** Who's on their way to each chest or at it, for it (so others pick another chest that has it, not crowd one). */
	private static final java.util.Map<BlockPos, Set<java.util.UUID>> USERS = new java.util.HashMap<>();

	/**
	 * How many others are fetching from this chest now, or from one beside it (a town's store is one room of chests: a crowd
	 * at any of them is a crowd at all of them).
	 */
	public static int users(BlockPos chest, java.util.UUID besides) {
		int count = 0;
		for (var entry : USERS.entrySet()) {
			if (entry.getKey().closerThan(chest, SAME_ROOM)) {
				count += (int)entry.getValue().stream().filter(id -> !id.equals(besides)).count();
			}
		}

		return count;
	}

	/** Chests this near each other are one place to crowd. */
	private static final double SAME_ROOM = 5.0;
	/** This many others at the chest already: it waits its turn out of their way (a few times, a few seconds each). */
	private static final int CROWD = 2;
	private static final int WAITS = 5;
	private int waits;

	/** Others standing at the chest now (within a step of it). */
	private int crowd() {
		return this.drifter.level().getEntitiesOfClass(com.dinglebob.drifter.entity.DrifterEntity.class,
			new net.minecraft.world.phys.AABB(this.chest).inflate(2.5), other -> other != this.drifter && other.isAlive()).size();
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return !(step instanceof com.dinglebob.drifter.executor.task.WaitTask) && super.countsAsFailure(step);
	}

	@Override
	protected void onStop() {
		Set<java.util.UUID> users = USERS.get(this.chest);
		if (users != null && users.remove(this.drifter.getUUID()) && users.isEmpty()) {
			USERS.remove(this.chest);
		}

		super.onStop();
	}

	@Override
	public String describe() {
		return "fetch " + this.count + " " + this.item.getDescription().getString().toLowerCase(Locale.ROOT) + " from its chest";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 3;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof ContainerTask) {
			this.done = true;
		}
	}

	/** It has walked over toward the chest to see round it. */
	private boolean approached;

	@Override
	protected Status next() {
		if (this.done) {
			return Status.SUCCESS;
		}

		if (!Reach.canReach(this.drifter, this.chest)) {
			// Others at it already, and it's near: it waits its turn a little way off, as a player would (found in the user's
			// game: a town just made, a crowd in its store's narrow aisles, stuck there, "can't get to its chest").
			if (this.waits < WAITS && this.drifter.position().distanceTo(Vec3.atCenterOf(this.chest)) < 12.0 && this.crowd() >= CROWD) {
				if (this.waits++ == 0) {
					this.think("others at the chest: waiting its turn");
				}

				return this.begin(new com.dinglebob.drifter.executor.task.WaitTask(this.drifter, "wait its turn at the chest", () -> this.crowd() < CROWD, 20 * 4));
			}

			BlockPos spot = this.walked ? null : KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
			if (spot == null) {
				// From across town it hasn't seen the floor by the chest yet (found in testing: a town's store it had never been
				// in, "can't get to its chest" from 40 blocks off): over there first, then it sees where to stand.
				if (!this.approached && this.drifter.position().distanceTo(Vec3.atCenterOf(this.chest)) > 4.0) {
					this.approached = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.chest), 3.0, "go over to the chest"));
				}

				return this.fail("can't get to its chest");
			}

			this.walked = true;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk to the chest"));
		}

		return this.begin(new ContainerTask(this.drifter, this.chest, ContainerTask.Mode.TAKE, stack -> stack.is(this.item),
			this.item.getDescription().getString().toLowerCase(Locale.ROOT), this.count, null));
	}
}
