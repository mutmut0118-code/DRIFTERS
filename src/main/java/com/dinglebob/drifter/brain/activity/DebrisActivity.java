package com.dinglebob.drifter.brain.activity;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.NetherTrip;
import com.dinglebob.drifter.brain.mining.Tunnel;
import com.dinglebob.drifter.brain.mining.TunnelDigger;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.skills.Netherite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Digging for ancient debris on a Nether trip (DESIGN.md section 19 "Goals there": "Ancient debris: around Y 15; strip
 * mines"): a staircase down from near its portal to about Y 15, then a strip mine there (a long tunnel and side
 * tunnels), taking any debris a tunnel wall shows (it can't see through netherrack any more than a player can). It stops
 * with enough for its next upgrades, or when the tunnel is done or it has been at it a good while; the trip takes it home.
 */
public class DebrisActivity extends Activity {
	private static final int MAX_STEPS = 220;
	private static final int MAIN_LENGTH = 40;
	private static final int SPACING = 4;
	private static final int BRANCH_LENGTH = 12;
	/** The longest it digs on one trip. */
	private static final int MAX_TICKS = 20 * 60 * 6;

	private final int wanted;
	private final int debrisBefore;
	@Nullable
	private Tunnel tunnel;
	@Nullable
	private TunnelDigger digger;
	private boolean branched;
	private int targetY;

	public DebrisActivity(DrifterEntity drifter) {
		this(drifter, Netherite.debrisWanted(drifter));
	}

	/** Digging until it has found this many (the debug command sets it). */
	public DebrisActivity(DrifterEntity drifter, int wanted) {
		super(drifter);
		this.wanted = wanted;
		this.debrisBefore = this.debris();
	}

	@Override
	public String describe() {
		return "dig for ancient debris";
	}

	@Override
	protected int timeoutTicks() {
		return MAX_TICKS;
	}

	/** Debris it went for already (got it, or couldn't). */
	private final java.util.Set<BlockPos> wentFor = new java.util.HashSet<>();
	/** How near seen debris must be for it to go for it. */
	private static final double SPOTTED_RANGE = 12.0;

	/** Ancient debris it remembers seeing close by, not gone for yet. */
	@Nullable
	private BlockPos spottedDebris() {
		var entry = this.drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.ORE, this.drifter.level().dimension().location(),
			this.drifter.position(), SPOTTED_RANGE, e -> e.label.equals("minecraft:ancient_debris") && !this.wentFor.contains(e.blockPos()));
		return entry == null || !this.drifter.level().getBlockState(entry.blockPos()).is(Blocks.ANCIENT_DEBRIS) ? null : entry.blockPos();
	}

	private int debris() {
		return this.drifter.getInventory().count(stack -> stack.is(Items.ANCIENT_DEBRIS));
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return this.digger == null || this.digger.countsAsFailure(step);
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (this.digger != null) {
			this.digger.stepDone(step, status);
			this.relayThought();
		}
	}

	private void relayThought() {
		String thought = this.digger == null ? null : this.digger.takeThought();
		if (thought != null) {
			this.think(thought);
		}
	}

	@Override
	protected Status next() {
		int found = this.debris() - this.debrisBefore;
		if (found >= this.wanted || this.ticksRunning() > MAX_TICKS - 20 * 10) {
			return this.finish(found);
		}

		if (this.tunnel == null) {
			this.start();
			return Status.RUNNING;
		}

		// Debris it has just seen close by (a cave wall, a breach): it goes and gets it before digging on, as for ore on a
		// mining trip.
		BlockPos spotted = this.spottedDebris();
		if (spotted != null) {
			this.wentFor.add(spotted);
			this.think("spotted ancient debris at " + spotted.toShortString() + ": going to get it");
			int total = this.debris() + 1;
			return this.begin(new GatherActivity(this.drifter, "ancient debris", state -> state.is(Blocks.ANCIENT_DEBRIS),
				stack -> stack.is(Items.ANCIENT_DEBRIS), total, false, false) {
				private boolean walked;

				@Override
				protected Status next() {
					if (!this.walked && this.drifter.blockPosition().distSqr(spotted) > 4 * 4) {
						this.walked = true;
						return this.begin(new com.dinglebob.drifter.executor.task.NavigateTask(this.drifter, net.minecraft.world.phys.Vec3.atCenterOf(spotted), 3.0,
							"walk to the ancient debris"));
					}

					return super.next();
				}
			});
		}

		Task step = this.digger.next();
		this.relayThought();
		if (step != null) {
			return this.begin(step);
		}

		// At the bottom of the stairs: the strip mine from there.
		if (!this.branched && this.drifter.blockPosition().getY() <= this.targetY + 6) {
			this.branched = true;
			Tunnel t = this.tunnel;
			t.done = false;
			t.turns = 0;
			t.breaches = 0;
			t.leg = null;
			BlockPos bottom = this.drifter.blockPosition();
			t.branchMine(bottom, t.direction, MAIN_LENGTH, SPACING, BRANCH_LENGTH);
			this.think("down at Y " + bottom.getY() + ": strip mining for ancient debris");
			return Status.RUNNING;
		}

		return this.finish(found);
	}

	/** Stairs down from here, away from the portal it came through. */
	private void start() {
		BlockPos here = this.drifter.blockPosition();
		Direction way = this.drifter.getDirection();
		NetherTrip trip = this.drifter.getDrifterBrain().netherTrip();
		if (trip != null && trip.netherPortal != null && here.distSqr(trip.netherPortal) < 36.0) {
			way = Direction.getNearest(here.getX() - trip.netherPortal.getX(), 0, here.getZ() - trip.netherPortal.getZ());
			if (way.getAxis().isVertical()) {
				way = this.drifter.getDirection();
			}
		}

		this.targetY = Netherite.DEBRIS_Y + Mth.nextInt(this.drifter.getRandom(), -2, 2);
		this.tunnel = new Tunnel(here, way, MAX_STEPS);
		if (here.getY() > this.targetY + 6) {
			this.tunnel.legs.add(new Tunnel.Leg(here, way, 400, this.targetY));
			this.think(String.format(Locale.ROOT, "digging down toward Y %d for ancient debris", this.targetY));
		} else {
			this.branched = true;
			this.tunnel.branchMine(here, way, MAIN_LENGTH, SPACING, BRANCH_LENGTH);
			this.think("strip mining for ancient debris");
		}

		this.digger = new TunnelDigger(this.drifter, this.tunnel);
	}

	private Status finish(int found) {
		NetherTrip trip = this.drifter.getDrifterBrain().netherTrip();
		if (trip != null) {
			trip.debrisDug = true;
		}

		this.think(found > 0 ? "dug up " + found + " ancient debris" : "no ancient debris this time");
		return Status.SUCCESS;
	}
}
