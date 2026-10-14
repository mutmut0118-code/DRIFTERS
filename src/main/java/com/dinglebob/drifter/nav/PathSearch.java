package com.dinglebob.drifter.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.memory.DrifterMemory;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * A* over blocks where breaking and placing are moves with costs (DESIGN.md section 12), in the style of Baritone.
 * Costs are seconds of player time, plus risk x Caution, plus resources spent x Thrift. Runs a limited number of
 * nodes per tick ({@link PathBudget}) inside a window around the start; a target outside the window gets a partial
 * path toward it and the follower plans again from there (rolling replanning).
 * TODO(milestone 10+): long-range planning over mental-map tiles; parkour jumps; boats and horses.
 */
public class PathSearch {
	public enum Status {
		RUNNING, FOUND, PARTIAL, FAILED
	}

	// ----- Tunables (section 12 cost model; seconds unless noted) -----
	/** Defaults; the live values come from DrifterConfig (`/drifter config path`). */
	public static final int WINDOW = 48;
	public static final int MAX_NODES = 8000;
	public static final double WALK = 1.0 / 4.317;
	/** Heuristic uses sprint speed so it never overestimates. */
	public static final double SPRINT = 1.0 / 5.612;
	public static final double DIAGONAL = WALK * Math.sqrt(2.0);
	public static final double JUMP_EXTRA = 0.3;
	public static final double STEP_DOWN_EXTRA = 0.1;
	public static final double SWIM = 0.5;
	/** Extra for swimming with its head under water (air risk, section 12): it keeps to the surface. */
	/** Extra for each move with its head under water: a player swims across the top, not along the bottom (found in
	 * testing: routes along lake floors, and near-drownings on the way). */
	public static final double SWIM_UNDER = 3.0;
	/** Most moves in a row under water on a planned route (about a third of a held breath's swim). */
	public static final int MAX_UNDERWATER = 6;
	public static final double CLIMB_UP = 0.45;
	public static final double CLIMB_DOWN = 0.35;
	public static final double BRIDGE = 1.4;
	public static final double PILLAR = 1.0;
	public static final double DOOR = 0.6;
	public static final double AVOID = 4.0;
	/** Unknown cells are assumed passable "at a moderate cost". */
	public static final double UNKNOWN = 0.4;
	/**
	 * Players dig to get somewhere only when it saves real time: every block dug on the way costs this much extra on
	 * top of the time to break it (found in testing: without it, breaking a window looked cheaper than using the door).
	 */
	public static final double DIG_PENALTY = 2.0;
	/** Blocks that plainly belong to a build (glass, planks, doors...) cost far more to dig: go around, use the door. */
	public static final double BUILT_PENALTY = 12.0;
	/** Walking right next to a drop of 4+ blocks it can see (section 12 risk: "height and exposed edges"). */
	public static final double EDGE_RISK = 1.0;
	public static final double FALLING_ABOVE_DIG = 1.0;
	public static final double LIQUID_BESIDE_DIG = 2.0;
	public static final double DANGER_NEAR = 3.0;
	public static final double DANGER_RADIUS = 3.0;
	/** Placing a block, and wearing a tool by digging, in seconds-equivalent before the Thrift weight. */
	public static final double BLOCK_RESOURCE = 0.5;
	public static final double DIG_RESOURCE = 0.05;
	/** Known roads (its own trails) cost less (section 12, "Discounts"). */
	public static final double TRAIL_DISCOUNT = 0.8;
	/** Drops deeper than this count as a deep edge (edge risk, sneaking). */
	public static final int SAFE_DROP = 3;
	public static final int WATER_DROP = 8;
	/** The highest drop into water the bravest will plan. */
	public static final int BRAVE_WATER_DROP = 32;
	/**
	 * It takes drops onto ground that cost up to this much fall damage (2 = one heart), like a player who doesn't bother
	 * walking round (user's rule). Deeper drops only into water.
	 */
	public static final float MAX_DROP_DAMAGE = 2.0F;
	/** ...and only with at least this much health left afterwards (3 hearts). */
	public static final float MIN_HEALTH_AFTER_DROP = 6.0F;
	/** Seconds-equivalent per point of fall damage, before the Caution weight. */
	public static final double FALL_DAMAGE_COST = 1.5;

	private static final class Node {
		final BlockPos pos;
		double g;
		double f;
		@Nullable
		Node parent;
		MoveType move = MoveType.START;
		List<BlockPos> digs = List.of();
		int unknown;
		int blocksUsed;
		/** Moves in a row with its head under water up to here (a held breath). */
		int underwater;
		boolean closed;

		Node(BlockPos pos) {
			this.pos = pos;
		}
	}

	private final DrifterEntity drifter;
	private final NavView view;
	private final BlockPos start;
	private final Vec3 target;
	private final double goalRadius;
	private final int blocksAvailable;
	private final double caution;
	private final double thrift;
	private final List<Vec3> dangers;
	private final DrifterMemory memory;
	/** Deepest drop onto ground it takes, from the player's own fall-damage attributes and its health. */
	private final int maxDrop;
	/** Moves in a row under water it plans: none for a while after it nearly drowned, unless it's under water now. */
	private final int maxUnderwater;
	/** Highest drop into water it plans (its bravery). */
	private final int waterDrop;
	private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
	private final Map<Long, Node> nodes = new HashMap<>();
	private Node best;
	private double bestH = Double.MAX_VALUE;
	private Status status = Status.RUNNING;
	private List<PathStep> result = List.of();
	private int expanded;

	public PathSearch(DrifterEntity drifter, BlockPos start, Vec3 target, double goalRadius) {
		this.drifter = drifter;
		this.view = new NavView(drifter);
		this.start = start.immutable();
		this.target = target;
		this.goalRadius = goalRadius;
		this.blocksAvailable = Tools.buildingBlockCount(drifter);
		// Section 12 cost model: risk x Caution, resources x Thrift, from its personality (mood included).
		this.caution = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.CAUTION);
		this.thrift = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.THRIFT);
		this.memory = drifter.getMemory();
		int maxDrop = SAFE_DROP;
		while (maxDrop < WATER_DROP && fallDamage(drifter, maxDrop + 1) <= MAX_DROP_DAMAGE
			&& drifter.getHealth() - fallDamage(drifter, maxDrop + 1) >= MIN_HEALTH_AFTER_DROP) {
			maxDrop++;
		}

		this.maxDrop = maxDrop;
		this.maxUnderwater = drifter.recentlyOutOfAir() && !drifter.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) ? 0 : MAX_UNDERWATER;
		// Water stops all fall damage, from any height: brave ones jump into water they can see from high up (a cave
		// with a pool at the bottom), careful ones only short drops (user's question in session 2).
		double bravery = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.BRAVERY);
		this.waterDrop = WATER_DROP + (int)Math.round(Math.max(0.0, bravery - 0.4) / 0.6 * (BRAVE_WATER_DROP - WATER_DROP));
		var dimension = drifter.level().dimension().location();
		this.dangers = this.memory.find(MemoryKind.DANGER, entry -> entry.dimension.equals(dimension)
			&& entry.pos.distanceTo(Vec3.atCenterOf(start)) <= com.dinglebob.drifter.DrifterConfig.pathRange() + DANGER_RADIUS).stream().map(entry -> entry.pos).toList();
		Node first = new Node(this.start);
		first.f = this.heuristic(this.start);
		this.nodes.put(this.start.asLong(), first);
		this.open.add(first);
		this.best = first;
		this.bestH = first.f;
	}

	public Status status() {
		return this.status;
	}

	public List<PathStep> result() {
		return this.result;
	}

	public int expanded() {
		return this.expanded;
	}

	/** LivingEntity#calculateFallDamage for a drop of {@code blocks}, with the player's own attributes. */
	public static int fallDamage(DrifterEntity drifter, int blocks) {
		double safe = drifter.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		return Math.max(0, Mth.ceil((blocks - safe) * drifter.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
	}

	private double heuristic(BlockPos pos) {
		return Math.max(0.0, Vec3.atBottomCenterOf(pos).distanceTo(this.target) - this.goalRadius) * SPRINT;
	}

	private boolean isGoal(BlockPos pos) {
		return Vec3.atBottomCenterOf(pos).distanceTo(this.target) <= this.goalRadius;
	}

	public Status tick(int budget) {
		if (this.status != Status.RUNNING) {
			return this.status;
		}

		for (int i = 0; i < budget; i++) {
			Node node = this.open.poll();
			if (node == null) {
				return this.finish(this.best, true);
			}

			if (node.closed) {
				continue;
			}

			node.closed = true;
			if (this.isGoal(node.pos)) {
				return this.finish(node, false);
			}

			if (++this.expanded >= com.dinglebob.drifter.DrifterConfig.pathNodes()) {
				return this.finish(this.best, true);
			}

			double h = this.heuristic(node.pos);
			if (h < this.bestH) {
				this.bestH = h;
				this.best = node;
			}

			this.expand(node);
		}

		return Status.RUNNING;
	}

	private Status finish(Node end, boolean partial) {
		if (end == null || end.parent == null) {
			this.status = Status.FAILED;
			return this.status;
		}

		List<PathStep> steps = new ArrayList<>();
		for (Node node = end; node.parent != null; node = node.parent) {
			steps.add(new PathStep(node.move, node.parent.pos, node.pos, node.digs, node.unknown));
		}

		Collections.reverse(steps);
		this.result = steps;
		this.status = partial ? Status.PARTIAL : Status.FOUND;
		return this.status;
	}

	// ---------------------------------------------------------------------------------------------
	// Moves
	// ---------------------------------------------------------------------------------------------

	/** Running total while checking cells for a move. Cost -1 means impossible. */
	private static final class Clearance {
		double cost;
		final List<BlockPos> digs = new ArrayList<>();
		int unknown;
	}

	/** Cells that must be passable (digging allowed if {@code mayDig}), adding up their cost. */
	@Nullable
	private Clearance clear(boolean mayDig, BlockPos... cells) {
		Clearance clearance = new Clearance();
		for (BlockPos cell : cells) {
			NavView.Cell info = this.view.cell(cell);
			switch (info.kind()) {
				case OPEN, LOW, WATER, CLIMB -> {
				}
				case UNKNOWN -> {
					clearance.cost += UNKNOWN;
					clearance.unknown++;
				}
				case DOOR -> clearance.cost += DOOR;
				case AVOID -> clearance.cost += AVOID * this.caution * 2.0;
				case SOLID -> {
					if (!mayDig || !info.diggable()) {
						return null;
					}

					clearance.cost += info.digSeconds() + DIG_PENALTY + DIG_RESOURCE * this.thrift * 2.0;
					if (info.built()) {
						clearance.cost += BUILT_PENALTY;
					}

					if (info.fallingAbove()) {
						clearance.cost += FALLING_ABOVE_DIG * this.caution * 2.0;
					}

					if (info.liquidBeside()) {
						clearance.cost += LIQUID_BESIDE_DIG * this.caution * 2.0;
					}

					clearance.digs.add(cell.immutable());
				}
				default -> {
					return null;
				}
			}
		}

		return clearance;
	}

	private boolean inWindow(BlockPos pos) {
		int window = com.dinglebob.drifter.DrifterConfig.pathRange();
		return Math.abs(pos.getX() - this.start.getX()) <= window && Math.abs(pos.getZ() - this.start.getZ()) <= window
			&& Math.abs(pos.getY() - this.start.getY()) <= window;
	}

	private void expand(Node node) {
		BlockPos n = node.pos;
		boolean inWater = this.view.isWater(n);
		// A block it plans to place (bridge or pillar) will be solid footing for the next move too. Ground it hasn't seen
		// counts as well, like everywhere else in the plan (found in testing: the near edge of a ravine was often unseen
		// from where it started, so it never planned a bridge at all); it checks the ground when it gets there.
		NavView.Kind floor = this.view.cell(n.below()).kind();
		boolean onSolidFloor = node.move == MoveType.BRIDGE || node.move == MoveType.PILLAR
			|| (floor == NavView.Kind.SOLID || floor == NavView.Kind.UNKNOWN) && this.view.standable(n.below());

		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos f = n.relative(d);
			this.tryLevel(node, f, inWater, onSolidFloor);
			this.tryJumpUp(node, f, onSolidFloor);
			this.tryDown(node, f);
		}

		this.tryDiagonals(node);
		this.tryVertical(node, inWater, onSolidFloor);
	}

	/** Same level: walk, swim, or bridge (place a block under the next step, sneaking). */
	private void tryLevel(Node node, BlockPos f, boolean inWater, boolean onSolidFloor) {
		Clearance c = this.clear(true, f, f.above());
		if (c == null) {
			return;
		}

		if (this.view.isWater(f) || inWater && !this.view.standable(f.below())) {
			if (this.view.isWater(f) || this.view.isWater(f.below())) {
				this.add(node, f, MoveType.SWIM, SWIM + (this.headUnder(f) ? SWIM_UNDER : 0.0) + c.cost, c, 0);
			}

			return;
		}

		// From the water, only onto ground it has seen (found in testing: unseen ground counted as a floor, so it planned to
		// walk out across a pond and couldn't get out of it for hours).
		boolean floor = inWater ? this.view.cell(f.below()).kind() == NavView.Kind.SOLID && this.view.standable(f.below())
			: this.view.standable(f.below());
		if (floor) {
			this.add(node, f, MoveType.WALK, WALK + c.cost, c, 0);
			return;
		}

		NavView.Kind below = this.view.cell(f.below()).kind();
		boolean placeable = below == NavView.Kind.OPEN || below == NavView.Kind.WATER;
		if (placeable && onSolidFloor && !inWater && node.blocksUsed < this.blocksAvailable && !this.drifter.bridgeFailedRecently(f)) {
			double cost = BRIDGE + c.cost + BLOCK_RESOURCE * this.thrift * 2.0;
			this.add(node, f, MoveType.BRIDGE, cost, c, 1);
		}
	}

	/** Jump up one block onto the block in front. */
	private void tryJumpUp(Node node, BlockPos f, boolean onSolidFloor) {
		// A jump needs a floor, a ladder, or still water to float in: water running down a staircase doesn't hold you up
		// (found in testing: it hopped for minutes at a ledge two blocks above its feet in a flooded stair).
		if (!onSolidFloor && this.view.cell(node.pos).kind() != NavView.Kind.CLIMB && !this.view.isStillWater(node.pos)) {
			return;
		}

		NavView.Cell front = this.view.cell(f);
		if (front.passable() && front.kind() != NavView.Kind.UNKNOWN || !this.view.standable(f)) {
			return;
		}

		// Out of the water only onto a block it has seen (unseen ones at the surface are usually more water).
		if (this.view.isWater(node.pos) && front.kind() != NavView.Kind.SOLID) {
			return;
		}

		BlockPos t = f.above();
		Clearance c = this.clear(true, node.pos.above(2), t, t.above());
		if (c != null) {
			this.add(node, t, MoveType.JUMP_UP, WALK + JUMP_EXTRA + c.cost, c, 0);
		}
	}

	/**
	 * Step or drop down: onto ground as far as costs at most one heart of fall damage (4-5 blocks for a player at
	 * healthy HP; damage is part of the cost), deeper only into water. One block down may be dug out as a stair.
	 */
	private void tryDown(Node node, BlockPos f) {
		for (int k = 1; k <= this.waterDrop; k++) {
			BlockPos t = f.below(k);
			Clearance c;
			if (k == 1) {
				c = this.clear(true, f.above(), f, t);
			} else {
				c = this.clear(false, f.above(), f);
				if (c != null) {
					for (int i = 1; i <= k && c != null; i++) {
						NavView.Kind kind = this.view.cell(f.below(i)).kind();
						if (kind != NavView.Kind.OPEN && kind != NavView.Kind.WATER && !(i == k && kind == NavView.Kind.CLIMB)) {
							c = null;
						}
					}
				}
			}

			if (c == null) {
				return;
			}

			boolean intoWater = this.view.isWater(t);
			if (k > this.maxDrop && !intoWater) {
				// Too far to drop onto ground; keep looking deeper for water.
				if (this.view.cell(t.below()).kind() != NavView.Kind.OPEN && this.view.cell(t.below()).kind() != NavView.Kind.WATER) {
					return;
				}

				continue;
			}

			// It never drops 2+ blocks onto ground it hasn't seen (found in testing: it walked into a dark ravine). Nor does
			// it step down into a hole it can see is open air but whose bottom it can't see: that is a ravine, not a step
			// (found in testing: "step down onto assumed ground" was always cheaper than bridging, so it never bridged).
			// Unseen ground under unseen or solid blocks (digging a stair down) is still assumed, as everywhere else, and so is
			// a step down a hillside: seen ground right next to it at the lower level (found in testing: without that, it
			// planned bridges down hills).
			boolean floorSeen = this.view.cell(t.below()).kind() != NavView.Kind.UNKNOWN;
			boolean seenHole = this.view.cell(t).kind() == NavView.Kind.OPEN;
			if (intoWater || this.view.standable(t.below()) && (floorSeen || k == 1 && (!seenHole || this.groundBeside(t)))) {
				double damage = intoWater ? 0.0 : fallDamage(this.drifter, k);
				double cost = WALK + STEP_DOWN_EXTRA * k + c.cost + damage * FALL_DAMAGE_COST * this.caution * 2.0;
				this.add(node, t, intoWater ? MoveType.SWIM_DOWN : MoveType.STEP_DOWN,
					cost + (intoWater && this.headUnder(t) ? SWIM_UNDER : 0.0), c, 0);
				return;
			}

			if (this.view.cell(t.below()).kind() != NavView.Kind.OPEN) {
				return;
			}
		}
	}

	/** Diagonal steps on the same level: no digging, no cutting through wall corners. */
	private void tryDiagonals(Node node) {
		BlockPos n = node.pos;
		int[][] diagonals = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		for (int[] diagonal : diagonals) {
			BlockPos sideX = n.offset(diagonal[0], 0, 0);
			BlockPos sideZ = n.offset(0, 0, diagonal[1]);
			BlockPos t = n.offset(diagonal[0], 0, diagonal[1]);
			Clearance c = this.clear(false, sideX, sideX.above(), sideZ, sideZ.above(), t, t.above());
			if (c == null || c.unknown > 0 || this.view.isWater(t) || !this.view.standable(t.below())
				|| this.view.cell(t.below()).kind() == NavView.Kind.UNKNOWN) {
				continue;
			}

			this.add(node, t, MoveType.DIAGONAL, DIAGONAL + c.cost, c, 0);
		}
	}

	/** Pillar up (jump and place a block underneath), climb ladders, swim up and down. */
	private void tryVertical(Node node, boolean inWater, boolean onSolidFloor) {
		BlockPos n = node.pos;
		NavView.Kind here = this.view.cell(n).kind();
		if (inWater) {
			Clearance up = this.clear(false, n.above(), n.above(2));
			if (up != null) {
				this.add(node, n.above(), MoveType.SWIM_UP, SWIM + up.cost, up, 0);
			}

			if (this.view.isWater(n.below())) {
				this.add(node, n.below(), MoveType.SWIM_DOWN, SWIM + (this.headUnder(n.below()) ? SWIM_UNDER : 0.0), new Clearance(), 0);
			}

			return;
		}

		if (here == NavView.Kind.CLIMB) {
			Clearance up = this.clear(false, n.above(), n.above(2));
			if (up != null) {
				this.add(node, n.above(), MoveType.CLIMB_UP, CLIMB_UP + up.cost, up, 0);
			}
		}

		if (this.view.cell(n.below()).kind() == NavView.Kind.CLIMB) {
			this.add(node, n.below(), MoveType.CLIMB_DOWN, CLIMB_DOWN, new Clearance(), 0);
		}

		// Not from water: floating, there's nothing to jump off and the block can't go under its feet (found in testing: in
		// a pool with high sides it planned pillars from the water and bobbed there; it digs a step into the side instead).
		if (onSolidFloor && !inWater && !this.view.isWater(n.below()) && node.blocksUsed < this.blocksAvailable
			&& !this.drifter.pillarFailedRecently(n)) {
			Clearance head = this.clear(true, n.above(2));
			if (head != null) {
				this.add(node, n.above(), MoveType.PILLAR, PILLAR + head.cost + BLOCK_RESOURCE * this.thrift * 2.0, head, 1);
			}
		}
	}

	/** Seen ground to stand on right beside this spot, at its level: the lower step of a slope, not a ravine. */
	private boolean groundBeside(BlockPos feet) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(d);
			NavView.Kind kind = this.view.cell(side).kind();
			NavView.Kind floor = this.view.cell(side.below()).kind();
			if ((kind == NavView.Kind.OPEN || kind == NavView.Kind.LOW) && floor == NavView.Kind.SOLID && this.view.standable(side.below())) {
				return true;
			}
		}

		return false;
	}

	/** True if a seen drop of 4+ blocks is right beside this spot. */
	private boolean besideDeepDrop(BlockPos feet) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(d);
			boolean deep = true;
			for (int i = 0; i <= SAFE_DROP && deep; i++) {
				NavView.Kind kind = this.view.cell(side.below(i)).kind();
				deep = kind == NavView.Kind.OPEN || kind == NavView.Kind.LOW && i == 0;
			}

			if (deep) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Its head would be under water standing in {@code feet}: water at head height, or water at its feet with the block
	 * above unseen (found in testing: unseen blocks over a flooded tunnel counted as air, so the underwater limit never
	 * started counting and it drowned under the ceiling).
	 */
	private boolean headUnder(BlockPos feet) {
		return this.view.isWater(feet.above())
			|| this.view.isWater(feet) && this.view.cell(feet.above()).kind() == NavView.Kind.UNKNOWN;
	}

	private void add(Node from, BlockPos to, MoveType move, double cost, Clearance clearance, int blocks) {
		if (!this.inWindow(to)) {
			return;
		}

		double stepCost = cost;
		if (this.memory.isOnTrail(to)) {
			stepCost *= TRAIL_DISCOUNT;
		}

		Vec3 center = Vec3.atBottomCenterOf(to);
		for (Vec3 danger : this.dangers) {
			if (danger.distanceTo(center) <= DANGER_RADIUS) {
				stepCost += DANGER_NEAR * this.caution * 2.0;
				break;
			}
		}

		if (this.besideDeepDrop(to)) {
			stepCost += EDGE_RISK * this.caution * 2.0;
		}

		// A held breath lasts about 15 s: no route with a longer swim under water than that, with a margin (found in
		// testing: it drowned under a ceiling at night).
		int underwater = this.headUnder(to) ? from.underwater + 1 : 0;
		if (underwater > this.maxUnderwater) {
			return;
		}

		double g = from.g + stepCost;
		Node node = this.nodes.computeIfAbsent(to.asLong(), key -> new Node(to.immutable()));
		if (node.closed || node.parent != null && g >= node.g || node == this.nodes.get(this.start.asLong())) {
			return;
		}

		node.g = g;
		node.f = g + this.heuristic(to);
		node.parent = from;
		node.underwater = underwater;
		node.move = move;
		node.digs = clearance.digs.isEmpty() ? List.of() : List.copyOf(clearance.digs);
		node.unknown = clearance.unknown;
		node.blocksUsed = from.blocksUsed + blocks;
		this.open.add(node);
	}
}
