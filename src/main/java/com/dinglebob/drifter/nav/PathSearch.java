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
 * Parkour: jumps across gaps a player could make (JumpPhysics), only over ground it has seen, never over lava.
 * TODO(milestone 10+): long-range planning over mental-map tiles; boats and horses.
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
	/** On top of a gap jump's time: a little over walking round when that's about as quick (a player doesn't jump for fun). */
	public static final double PARKOUR_EXTRA = 0.6;
	/** Risk of a gap jump over a drop that would hurt, before the Caution weight (plus a little per block of depth). */
	public static final double PARKOUR_DEEP_RISK = 3.0;
	/**
	 * How much farther it needs to carry than the gap's width minus its body: its take-off point varies by up to a tick's
	 * run (it jumps at the edge, somewhere within the last 0.3 of a block), so it plans for the worst of those.
	 */
	public static final double PARKOUR_TAKEOFF_LOSS = 0.45;
	public static final double STEP_DOWN_EXTRA = 0.1;
	public static final double SWIM = 0.5;
	/** Extra for swimming with its head under water (air risk, section 12): it keeps to the surface. */
	/** Extra for each move with its head under water: a player swims across the top, not along the bottom (found in
	 * testing: routes along lake floors, and near-drownings on the way). */
	public static final double SWIM_UNDER = 3.0;
	/**
	 * Extra for each cell of flowing water (a current it can see): swimming against a stream is slow and can wash it back,
	 * so it walks round or bridges over when that's not much longer, like a player (found in 10-day runs: a home by a
	 * river and a flooded mine had it fighting the same current for hours).
	 */
	public static final double CURRENT = 2.0;
	/**
	 * A cell of open water with a boat in its pack (it rows across: a boat goes about four times a swimmer's speed, and
	 * putting it on the water and picking it up again takes a few seconds; DESIGN.md section 12, "Boat").
	 */
	public static final double BOAT = 0.2;
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
	/** In the Nether any block it hasn't seen may be lava: routes keep to what it has seen where they can. */
	public static final double UNKNOWN_LAVA_WORLD = 3.0;
	private final boolean lavaWorld;
	/** It has a boat to cross open water in. */
	private final boolean boat;
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
	/** Building blocks routes always leave in its pockets. */
	public static final int BLOCK_RESERVE = 6;
	/**
	 * In a lava world (the Nether) it keeps more: bridges there go out over the lava sea, and one that runs out of blocks
	 * far from the shore is stranded (found in testing: exploring for a fortress, it bridged out over the lava until it had
	 * none, and stood there for good).
	 */
	public static final int LAVA_WORLD_BLOCK_RESERVE = 16;
	/** Extra cost of each step at the level of a lava sea (feet at most a block above its surface). */
	public static final double LAVA_SEA_SHORE = 4.0;

	/** Building blocks its routes leave in its pockets where it is now. */
	public static int reserve(DrifterEntity drifter) {
		return drifter.level().dimensionType().ultraWarm() ? LAVA_WORLD_BLOCK_RESERVE : BLOCK_RESERVE;
	}
	/** Break time (seconds) at or under which a block counts as clearing at a touch, and its small extra cost. */
	public static final double INSTANT_BREAK = 0.25;
	public static final double INSTANT_PENALTY = 0.3;
	/** ...and only with at least this much health left afterwards (3 hearts). */
	public static final float MIN_HEALTH_AFTER_DROP = 6.0F;
	/** Seconds-equivalent per point of fall damage, before the Caution weight. */
	public static final double FALL_DAMAGE_COST = 1.5;

	/** The open list: a binary heap on f, without a comparator object or boxing (a hot spot in the profile). */
	private static final class NodeHeap {
		private Node[] heap = new Node[1024];
		private int size;

		void add(Node node) {
			if (this.size == this.heap.length) {
				this.heap = java.util.Arrays.copyOf(this.heap, this.size * 2);
			}

			int i = this.size++;
			double f = node.f;
			while (i > 0) {
				int parent = (i - 1) >>> 1;
				Node p = this.heap[parent];
				if (p.f <= f) {
					break;
				}

				this.heap[i] = p;
				i = parent;
			}

			this.heap[i] = node;
		}

		@Nullable
		Node poll() {
			if (this.size == 0) {
				return null;
			}

			Node top = this.heap[0];
			Node last = this.heap[--this.size];
			this.heap[this.size] = null;
			if (this.size > 0) {
				int i = 0;
				double f = last.f;
				int half = this.size >>> 1;
				while (i < half) {
					int child = 2 * i + 1;
					Node c = this.heap[child];
					int right = child + 1;
					if (right < this.size && this.heap[right].f < c.f) {
						child = right;
						c = this.heap[child];
					}

					if (f <= c.f) {
						break;
					}

					this.heap[i] = c;
					i = child;
				}

				this.heap[i] = last;
			}

			return top;
		}
	}

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
		/** What the cell itself adds to any move into it (lava shore, trail, danger, edge), worked out once. */
		boolean costed;
		/** For a gap jump: how it takes it. */
		@Nullable
		JumpPhysics.Mode jump;
		double shoreAdd;
		double trailFactor = 1.0;
		double extraAdd;

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
	/** For gap jumps: its skill (the widest jump and the margin it wants), whether it can sprint, its speed, Jump Boost. */
	private final double skill;
	private final boolean canSprint;
	/** Gap jumps on (the setting). */
	private final boolean parkour;
	private final double walkSpeed;
	private final int jumpBoost;
	private final List<Vec3> dangers;
	private final DrifterMemory memory;
	/** Deepest drop onto ground it takes, from the player's own fall-damage attributes and its health. */
	private final int maxDrop;
	/** Moves in a row under water it plans: none for a while after it nearly drowned, unless it's under water now. */
	private final int maxUnderwater;
	/** Its head is under water already (it may swim under to get out). */
	private final boolean startUnderwater;
	/** Highest drop into water it plans (its bravery). */
	private final int waterDrop;
	private final NodeHeap open = new NodeHeap();
	private final LocalGrid<Node> nodes;
	/** Each cell's "beside a deep drop" answer (asked for every move into it). */
	private final LocalGrid<Boolean> deepDrop;
	@Nullable
	private Node startNode;
	private Node best;
	private double bestH = Double.MAX_VALUE;
	/** (Volatile: a search on a path thread sets it, and the Drifter's tick reads it; the result is set first.) */
	private volatile Status status = Status.RUNNING;
	private volatile List<PathStep> result = List.of();
	private int expanded;
	/**
	 * What it reads of the Drifter while searching, taken at the start (server thread), so the search itself can run on
	 * another thread (milestone 16): bridges and pillars that failed lately, its trails, its fall-damage attributes.
	 */
	private final java.util.Set<BlockPos> failedBridges;
	/** Blocks a route had it dig lately that it couldn't: not planned as digs again for a while. */
	private final java.util.Set<BlockPos> failedDigs;
	private final java.util.Set<BlockPos> failedPillars;
	private final it.unimi.dsi.fastutil.longs.LongOpenHashSet trails;
	private final double safeFall;
	private final double fallMultiplier;
	private final int seaLevel;
	/** Runs on a path thread ({@link PathWorkers}): reads only the world copy and copies of what it knows. */
	private final boolean async;
	private boolean submitted;
	private volatile boolean cancelled;
	/** The Drifter is near a player (its search goes first). */
	private final boolean near;

	public PathSearch(DrifterEntity drifter, BlockPos start, Vec3 target, double goalRadius) {
		this(drifter, start, target, goalRadius, false);
	}

	/** {@code lastBlocks}: a way out of a dead end, with the blocks it keeps back too. */
	public PathSearch(DrifterEntity drifter, BlockPos start, Vec3 target, double goalRadius, boolean lastBlocks) {
		this.drifter = drifter;
		this.start = start.immutable();
		// On a path thread: the world round it copied first (most of it already is), and what it knows round here.
		this.async = PathWorkers.enabled() && drifter.level() instanceof net.minecraft.server.level.ServerLevel;
		if (this.async) {
			int reach = com.dinglebob.drifter.DrifterConfig.pathRange() + 8;
			var level = (net.minecraft.server.level.ServerLevel)drifter.level();
			long copyStarted = System.nanoTime();
			WorldCopy.ensure(level, this.start.getX() - reach, this.start.getY() - reach, this.start.getZ() - reach, this.start.getX() + reach,
				this.start.getY() + reach, this.start.getZ() + reach);
			this.view = new NavView(drifter, WorldCopy.view(level), drifter.getSenses().terrain().copyAround(this.start, reach));
			PathBudget.copied(System.nanoTime() - copyStarted);
		} else {
			this.view = new NavView(drifter);
		}

		this.near = drifter.tier() == com.dinglebob.drifter.entity.DetailTier.FULL;
		this.failedBridges = drifter.recentFailedBridges();
		this.failedDigs = drifter.recentFailedDigs();
		this.failedPillars = drifter.recentFailedPillars();
		this.trails = drifter.getMemory().trailCopy();
		this.safeFall = drifter.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		this.fallMultiplier = drifter.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		this.seaLevel = drifter.level().getSeaLevel();
		this.nodes = new LocalGrid<>(this.start, com.dinglebob.drifter.DrifterConfig.pathRange() + 8);
		this.deepDrop = new LocalGrid<>(this.start, com.dinglebob.drifter.DrifterConfig.pathRange() + 8);
		this.target = target;
		this.goalRadius = goalRadius;
		// Never its last few blocks on a route: a long chain of partial routes (exploring far) once spent them all on pillars
		// and bridges and left it stranded up on them (found by the user: rubydoodle).
		this.blocksAvailable = Math.max(0, Tools.buildingBlockCount(drifter) - (lastBlocks ? 0 : reserve(drifter)));
		this.lavaWorld = drifter.level().dimensionType().ultraWarm();
		this.boat = !this.lavaWorld && com.dinglebob.drifter.executor.task.BoatTask.hasBoat(drifter);
		// Section 12 cost model: risk x Caution, resources x Thrift, from its personality (mood included).
		this.caution = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.CAUTION);
		this.thrift = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.THRIFT);
		this.skill = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.SKILL);
		this.parkour = com.dinglebob.drifter.DrifterConfig.parkour();
		// Sprinting needs food over 6, as for a player.
		this.canSprint = drifter.getFoodData().getFoodLevel() > net.minecraft.world.food.FoodConstants.SPRINT_LEVEL;
		// Its walking speed (the attribute counts a sprint in while it sprints: that comes back out).
		double speed = drifter.getAttributeValue(Attributes.MOVEMENT_SPEED);
		this.walkSpeed = drifter.isSprinting() ? speed / 1.3 : speed;
		var boost = drifter.getEffect(net.minecraft.world.effect.MobEffects.JUMP);
		this.jumpBoost = boost == null ? 0 : boost.getAmplifier() + 1;
		this.memory = drifter.getMemory();
		int maxDrop = SAFE_DROP;
		while (maxDrop < WATER_DROP && fallDamage(drifter, maxDrop + 1) <= MAX_DROP_DAMAGE
			&& drifter.getHealth() - fallDamage(drifter, maxDrop + 1) >= MIN_HEALTH_AFTER_DROP) {
			maxDrop++;
		}

		this.maxDrop = maxDrop;
		// Low on air already: no more moves with its head under (found in testing: at 40% air in a flooded cave its route still
		// went deeper, and it drowned).
		boolean lowOnAir = drifter.getAirSupply() < drifter.getMaxAirSupply() * 0.6;
		this.startUnderwater = drifter.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
		this.maxUnderwater = lowOnAir || drifter.recentlyOutOfAir() && !drifter.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) ? 0 : MAX_UNDERWATER;
		// Water stops all fall damage, from any height: brave ones jump into water they can see from high up (a cave
		// with a pool at the bottom), careful ones only short drops (user's question in session 2).
		double bravery = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.BRAVERY);
		this.waterDrop = WATER_DROP + (int)Math.round(Math.max(0.0, bravery - 0.4) / 0.6 * (BRAVE_WATER_DROP - WATER_DROP));
		var dimension = drifter.level().dimension().location();
		this.dangers = this.memory.find(MemoryKind.DANGER, entry -> entry.dimension.equals(dimension)
			&& entry.pos.distanceTo(Vec3.atCenterOf(start)) <= com.dinglebob.drifter.DrifterConfig.pathRange() + DANGER_RADIUS).stream().map(entry -> entry.pos).toList();
		Node first = new Node(this.start);
		first.f = this.heuristic(this.start);
		this.nodes.put(this.start, first);
		this.startNode = first;
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

	/** {@link #fallDamage(DrifterEntity, int)} with the attributes it had at the start. */
	private int fallDamage(int blocks) {
		return Math.max(0, Mth.ceil((blocks - this.safeFall) * this.fallMultiplier));
	}

	/** LivingEntity#calculateFallDamage for a drop of {@code blocks}, with the player's own attributes. */
	public static int fallDamage(DrifterEntity drifter, int blocks) {
		double safe = drifter.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		return Math.max(0, Mth.ceil((blocks - safe) * drifter.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
	}

	/**
	 * Straight-line distance at walking pace: what every plain move costs (walks are costed at walking pace; it was the
	 * sprint pace, a quarter under, and the search looked at far more cells than it needed to; performance, 2026-10-17).
	 * Only its own trails cost less, and a route along one is a fine route anyway.
	 */
	private double heuristic(BlockPos pos) {
		double dx = pos.getX() + 0.5 - this.target.x;
		double dy = pos.getY() - this.target.y;
		double dz = pos.getZ() + 0.5 - this.target.z;
		return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - this.goalRadius) * WALK;
	}

	private boolean isGoal(BlockPos pos) {
		return Vec3.atBottomCenterOf(pos).distanceTo(this.target) <= this.goalRadius;
	}

	/** Searches on for up to {@code nanos} (at least {@link PathBudget#MIN_NODES} nodes, at most
	 * {@link PathBudget#MAX_NODES_PER_SEARCH_PER_TICK}). */
	private int skipped;

	public Status tick(long nanos) {
		if (this.status != Status.RUNNING) {
			return this.status;
		}

		// On a path thread: handed over the first time, then it's done when it's done (the Drifter waits a tick or two).
		if (this.async) {
			if (!this.submitted) {
				this.submitted = true;
				PathWorkers.submit(this::runToEnd, this.near ? 0 : 1);
			}

			return this.status;
		}

		// The tick's search time is used up: it waits its turn, and only after being skipped for a while takes its few
		// nodes anyway, so the last Drifter in line never starves but a crowd doesn't blow the budget (found in a
		// 100-Drifter test: everyone's few nodes a tick came to 20 ms).
		if (nanos <= 0L && this.skipped++ < PathBudget.MAX_SKIPPED_TICKS) {
			return this.status;
		}

		this.skipped = 0;
		long started = System.nanoTime();
		int before = this.expanded;
		try {
			return this.search(started + nanos);
		} finally {
			PathBudget.spent(System.nanoTime() - started);
			PathBudget.expanded(this.expanded - before);
		}
	}

	/** The whole search at once (on a path thread), unless it's been given up on. */
	private void runToEnd() {
		long started = System.nanoTime();
		int before = this.expanded;
		try {
			while (this.status == Status.RUNNING) {
				if (this.cancelled) {
					this.status = Status.FAILED;
					return;
				}

				this.search(Long.MAX_VALUE);
			}
		} catch (RuntimeException e) {
			// A world copy is only ever a copy: a surprise in one fails this search, not the game.
			if (System.currentTimeMillis() - lastFailureLog > 30_000L) {
				lastFailureLog = System.currentTimeMillis();
				com.dinglebob.drifter.Drifter.LOGGER.warn("[Drifter] a path search failed", e);
			}

			this.status = Status.FAILED;
		} finally {
			PathBudget.workerSpent(System.nanoTime() - started, this.expanded - before);
		}
	}

	private static volatile long lastFailureLog;

	/** No longer wanted (the Drifter went on to something else): a search on a path thread stops. */
	public void cancel() {
		this.cancelled = true;
	}

	private Status search(long deadline) {
		for (int i = 0; i < PathBudget.MAX_NODES_PER_SEARCH_PER_TICK; i++) {
			if (i >= PathBudget.MIN_NODES && (i & 15) == 0 && System.nanoTime() > deadline) {
				return Status.RUNNING;
			}

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
		PathBudget.finished(this.expanded);
		if (end == null || end.parent == null) {
			this.status = Status.FAILED;
			return Status.FAILED;
		}

		List<PathStep> steps = new ArrayList<>();
		for (Node node = end; node.parent != null; node = node.parent) {
			steps.add(new PathStep(node.move, node.parent.pos, node.pos, node.digs, node.unknown, node.jump));
		}

		Collections.reverse(steps);
		this.result = steps;
		Status done = partial ? Status.PARTIAL : Status.FOUND;
		this.status = done;
		return done;
	}

	// ---------------------------------------------------------------------------------------------
	// Moves
	// ---------------------------------------------------------------------------------------------

	/** Running total while checking cells for a move. Cost -1 means impossible. */
	private static final class Clearance {
		double cost;
		/** Made only when a move digs (most don't; a list per move tried was a measurable cost). */
		@Nullable
		List<BlockPos> digs;
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
					clearance.cost += this.lavaWorld ? UNKNOWN_LAVA_WORLD : UNKNOWN;
					clearance.unknown++;
				}
				case DOOR -> clearance.cost += DOOR;
				case AVOID -> clearance.cost += AVOID * this.caution * 2.0;
				case SOLID -> {
					if (!mayDig || !info.diggable() || this.failedDigs.contains(cell)) {
						return null;
					}

					// Something that breaks at a touch (a berry bush) costs next to nothing to clear: a player cuts straight
					// through a patch rather than skirting its pricking edge (found by the user: Drifters died skirting them).
					boolean instant = info.digSeconds() <= INSTANT_BREAK;
					clearance.cost += info.digSeconds() + (instant ? INSTANT_PENALTY : DIG_PENALTY + DIG_RESOURCE * this.thrift * 2.0);
					if (info.built()) {
						clearance.cost += BUILT_PENALTY;
					}

					if (info.fallingAbove()) {
						clearance.cost += FALLING_ABOVE_DIG * this.caution * 2.0;
					}

					if (info.liquidBeside()) {
						clearance.cost += LIQUID_BESIDE_DIG * this.caution * 2.0;
					}

					if (clearance.digs == null) {
						clearance.digs = new ArrayList<>(2);
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
			if (this.parkour) {
				this.tryParkour(node, d, onSolidFloor);
			}
		}

		this.tryDiagonals(node);
		if (this.parkour) {
			this.tryParkourAngled(node, onSolidFloor);
		}
		this.tryVertical(node, inWater, onSolidFloor);
	}

	private double current(BlockPos pos) {
		return this.view.isWater(pos) && !this.view.isStillWater(pos) ? CURRENT : 0.0;
	}

	/** Same level: walk, swim, or bridge (place a block under the next step, sneaking). */
	private void tryLevel(Node node, BlockPos f, boolean inWater, boolean onSolidFloor) {
		Clearance c = this.clear(true, f, f.above());
		if (c == null) {
			return;
		}

		if (this.view.isWater(f) || inWater && !this.view.standable(f.below())) {
			if (this.view.isWater(f) || this.view.isWater(f.below())) {
				boolean rowed = this.boat && this.view.isWater(f) && !this.headUnder(f) && !this.view.isWater(f.above());
				this.add(node, f, MoveType.SWIM, (rowed ? BOAT : SWIM + this.current(f) + (this.headUnder(f) ? SWIM_UNDER : 0.0)) + c.cost, c, 0);
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
		if (placeable && onSolidFloor && !inWater && node.blocksUsed < this.blocksAvailable && !this.failedBridges.contains(f)) {
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
		// (Not a jump that failed it a moment ago; the same memory as bridges.)
		Clearance c = this.failedBridges.contains(t) ? null : this.clear(true, node.pos.above(2), t, t.above());
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
			boolean floorSeen = this.view.isKnown(t.below());
			boolean seenHole = this.view.cell(t).kind() == NavView.Kind.OPEN;
			// (In the Nether, never onto ground or into a space it hasn't seen: either may be lava; found in testing, a step
			// down a slope onto an unseen block landed it in lava.)
			// Digging a stair down through rock it has seen is the exception: once that block is out it sees what's under it
			// before it steps in (the walker looks and replans if there's no floor), as a player digs down (found in testing:
			// up on the rock over its portal with no blocks to build down, it had no way at all to get down).
			boolean stairDig = k == 1 && this.view.isKnown(t) && this.view.cell(t).kind() == NavView.Kind.SOLID;
			boolean seenEnough = !this.lavaWorld || floorSeen && this.view.isKnown(t) || stairDig;
			if (seenEnough && (intoWater || this.view.standable(t.below()) && (floorSeen || k == 1 && (!seenHole || this.groundBeside(t))))) {
				double damage = intoWater ? 0.0 : this.fallDamage(k);
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

	/** Landing offsets for jumps that aren't straight along an axis (diagonals, corners, knight's moves), up to 4 out. */
	private static final int[][] ANGLED = angledOffsets();

	private static int[][] angledOffsets() {
		List<int[]> offsets = new ArrayList<>();
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double distance = Math.sqrt(x * x + z * z);
				if (x != 0 && z != 0 && distance <= 4.3) {
					offsets.add(new int[] {x, z});
				}
			}
		}

		return offsets.toArray(new int[0][]);
	}

	/**
	 * A jump at an angle (diagonals, round a corner, a knight's move): the same rules as a straight one, for any landing up
	 * to about 4 blocks out. Its body (0.6 wide) sweeps a corridor from the take-off to the landing at feet, head and the
	 * top of the jump: all of it open and seen, and somewhere along it no floor (else it would just walk). How far it has
	 * to carry: the distance less what its body can lean out over each edge along that line (0.8 of a block over the axis
	 * it leaves the block by, so more on a diagonal), plus the take-off timing.
	 */
	private void tryParkourAngled(Node node, boolean onSolidFloor) {
		BlockPos n = node.pos;
		if (!onSolidFloor || node.move == MoveType.BRIDGE || node.move == MoveType.PILLAR || this.view.isWater(n)
			|| !this.view.jumpFooting(n.below()) || !this.openSeen(n.above(2))) {
			return;
		}

		// Only from an edge: a neighbour (of the eight) with no floor. Anywhere else a jump starts over ground it can walk
		// (and the jump, if any, starts from the edge further on): this keeps the search quick on open ground.
		boolean edge = false;
		for (int x = -1; x <= 1 && !edge; x++) {
			for (int z = -1; z <= 1 && !edge; z++) {
				edge = (x != 0 || z != 0) && !this.view.standable(n.offset(x, -1, z));
			}
		}

		if (!edge) {
			return;
		}

		double margin = 0.15 + 0.25 * (1.0 - this.skill);
		double maxDistance = this.skill >= 0.35 ? 4.3 : 3.3;
		for (int[] offset : ANGLED) {
			double distance = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
			if (distance > maxDistance) {
				continue;
			}

			double ux = offset[0] / distance;
			double uz = offset[1] / distance;
			double lean = 0.8 / Math.max(Math.abs(ux), Math.abs(uz));
			double need = distance - 2.0 * lean + PARKOUR_TAKEOFF_LOSS;
			// (A corner a body can't lean past isn't a jump: walking covers anything nearer.)
			if (need <= 0.2) {
				continue;
			}

			for (int dy = 1; dy >= -3; dy--) {
				BlockPos t = n.offset(offset[0], dy, offset[1]);
				if (!this.view.jumpFooting(t.below()) || !this.openSeen(t) || !this.openSeen(t.above())) {
					continue;
				}

				if (dy > 0 && !this.headroomUp(n, t)) {
					break;
				}

				if (this.failedBridges.contains(t)) {
					break;
				}

				// Floor all along its middle line: a walk, not a jump.
				if (this.floorAlong(n, ux, uz, distance)) {
					break;
				}

				int depth = this.corridor(n, t, ux, uz, distance, dy);
				if (depth == Integer.MIN_VALUE) {
					break;
				}

				if (depth < 0) {
					break;
				}

				boolean deep = depth > this.maxDrop;
				boolean runUp = this.roomToRunUp(n, ux, uz);
				double needed = need + margin + (deep ? 0.3 : 0.0);
				JumpPhysics.Mode mode = JumpPhysics.choose(this.walkSpeed, this.canSprint, runUp, dy, this.jumpBoost, needed,
					this.stopsPast(t, ux, uz) ? Double.MAX_VALUE : needed + OVERSHOOT);
				if (mode == null) {
					break;
				}

				double risk = deep ? (PARKOUR_DEEP_RISK + 0.2 * Math.min(depth, 30)) * this.caution * 2.0 : 0.0;
				double cost = distance * (mode.sprint ? SPRINT : WALK) + JUMP_EXTRA + PARKOUR_EXTRA + risk;
				this.pendingJump = mode;
				this.add(node, t, MoveType.PARKOUR, cost, new Clearance(), 0);
				break;
			}
		}
	}

	/**
	 * The corridor a jump at an angle sweeps, checked: every cell its body (0.3 either side of its middle) passes through at
	 * feet, head and the top of the jump is open and seen, and at least one has no floor. Returns how deep a fall a miss
	 * would be (as gapDepth), -1 if there's no gap at all or it's over lava, or MIN_VALUE if the way is blocked.
	 */
	private int corridor(BlockPos n, BlockPos t, double ux, double uz, double distance, int dy) {
		java.util.Set<Long> columns = new java.util.HashSet<>();
		double x0 = n.getX() + 0.5;
		double z0 = n.getZ() + 0.5;
		for (double s = 0.0; s <= distance + 1.0E-6; s += 0.25) {
			double cx = x0 + ux * s;
			double cz = z0 + uz * s;
			for (double ox : new double[] {-0.3, 0.3}) {
				for (double oz : new double[] {-0.3, 0.3}) {
					columns.add(net.minecraft.world.level.ChunkPos.asLong(Mth.floor(cx + ox), Mth.floor(cz + oz)));
				}
			}
		}

		boolean gap = false;
		int deepest = 0;
		int low = Math.min(0, dy);
		int high = Math.max(2, dy + 2);
		for (long key : columns) {
			int x = net.minecraft.world.level.ChunkPos.getX(key);
			int z = net.minecraft.world.level.ChunkPos.getZ(key);
			boolean takeoff = x == n.getX() && z == n.getZ();
			boolean landing = x == t.getX() && z == t.getZ();
			for (int y = low; y <= high; y++) {
				BlockPos cell = new BlockPos(x, n.getY() + y, z);
				// (The take-off column only from its feet up; the landing column only from its feet there up.)
				if (takeoff && y < 0 || landing && cell.getY() < t.getY()) {
					continue;
				}

				if (!this.openSeen(cell)) {
					return Integer.MIN_VALUE;
				}
			}

			if (takeoff || landing) {
				continue;
			}

			BlockPos floor = new BlockPos(x, n.getY() - 1, z);
			if (!this.view.standable(floor)) {
				gap = true;
				int depth = this.columnDepth(new BlockPos(x, n.getY(), z));
				if (depth < 0) {
					return -1;
				}

				deepest = Math.max(deepest, depth);
			}
		}

		return gap ? deepest : -1;
	}

	/** Floor under every point of the straight line from the take-off's middle to the landing's (no gap on the way). */
	private boolean floorAlong(BlockPos n, double ux, double uz, double distance) {
		for (double s = 0.5; s < distance; s += 0.5) {
			if (!this.view.standable(BlockPos.containing(n.getX() + 0.5 + ux * s, n.getY() - 1, n.getZ() + 0.5 + uz * s))) {
				return false;
			}
		}

		return true;
	}

	/** Blocks to the floor under this cell (0 for water), UNSEEN_GAP_DEPTH where unseen, -1 for lava (or unseen in the Nether). */
	private int columnDepth(BlockPos column) {
		for (int k = 1; k <= 40; k++) {
			BlockPos cell = column.below(k);
			if (!this.view.isKnown(cell)) {
				return this.lavaWorld ? -1 : UNSEEN_GAP_DEPTH;
			}

			NavView.Kind kind = this.view.cell(cell).kind();
			if (kind == NavView.Kind.WATER) {
				return 0;
			}

			if (kind == NavView.Kind.BANNED) {
				return -1;
			}

			if (kind != NavView.Kind.OPEN) {
				return k - 1;
			}
		}

		return UNSEEN_GAP_DEPTH;
	}

	/** Room to back up for a run along this line: floor and headroom one and two blocks back. */
	private boolean roomToRunUp(BlockPos n, double ux, double uz) {
		for (int i = 1; i <= 2; i++) {
			BlockPos back = BlockPos.containing(n.getX() + 0.5 - ux * i, n.getY(), n.getZ() + 0.5 - uz * i);
			if (!this.openSeen(back) || !this.openSeen(back.above()) || !this.view.isKnown(back.below()) || !this.view.standable(back.below())) {
				return false;
			}
		}

		return true;
	}

	/** Two blocks of seen floor behind it, with room to stand, to back up onto and run from. */
	private boolean roomToRunUp(BlockPos n, Direction d) {
		for (int i = 1; i <= 2; i++) {
			BlockPos back = n.relative(d.getOpposite(), i);
			if (!this.openSeen(back) || !this.openSeen(back.above()) || !this.view.isKnown(back.below()) || !this.view.standable(back.below())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * A jump across a gap onto a ladder or vines (user's request, 2026-10-17: "ladders and vines jumps"): a player catches
	 * a climbable block the moment its body is in it, and holding toward it climbs. So the jump must carry its middle into
	 * the climbable block (0.5 past the gap, less the lean over the take-off edge, plus the timing) while its feet are
	 * still at that height or above. There must be a wall behind it to stop against (a ladder's or vines' own wall), the
	 * cells it comes in through open or climbable, and somewhere to go on it (more of it above, or open air at the top to
	 * climb out). Returns whether it added the jump.
	 */
	private boolean tryCatchClimb(Node node, BlockPos n, BlockPos t, int gap, int dy, double ux, double uz, boolean runUp, double margin) {
		if (this.failedBridges.contains(t)) {
			return false;
		}

		if (!this.view.wallBehindClimb(t, Direction.getNearest(ux, 0.0, uz))) {
			return false;
		}

		// Its body comes in at head height too: that cell open or climbable.
		NavView.Kind head = this.view.cell(t.above()).kind();
		if (head != NavView.Kind.OPEN && head != NavView.Kind.CLIMB) {
			return false;
		}

		if (dy > 0 && !this.headroomUp(n, t)) {
			return false;
		}

		int depth = this.gapDepth(n, Direction.getNearest(ux, 0.0, uz), gap);
		if (depth < 0) {
			return false;
		}

		boolean deep = depth > this.maxDrop;
		double needed = gap + 0.5 - 0.8 + PARKOUR_TAKEOFF_LOSS + margin + (deep ? 0.3 : 0.0);
		JumpPhysics.Mode mode = JumpPhysics.choose(this.walkSpeed, this.canSprint, runUp, dy, this.jumpBoost, needed, Double.MAX_VALUE);
		if (mode == null) {
			return false;
		}

		double risk = deep ? (PARKOUR_DEEP_RISK + 0.2 * Math.min(depth, 30)) * this.caution * 2.0 : 0.0;
		double cost = (gap + 1) * (mode.sprint ? SPRINT : WALK) + JUMP_EXTRA + PARKOUR_EXTRA + CLIMB_UP + risk;
		this.pendingJump = mode;
		this.add(node, t, MoveType.PARKOUR, cost, new Clearance(), 0);
		return true;
	}

	/** How the gap jump being added is taken (set just before adding it). */
	@Nullable
	private JumpPhysics.Mode pendingJump;

	/**
	 * Something past the landing that stops it (more floor at that level, or a wall): without it, a jump that would carry
	 * well past the landing block sails off the far side, so it must be one that carries only just far enough.
	 */
	private boolean stopsPast(BlockPos t, double ux, double uz) {
		BlockPos beyond = BlockPos.containing(t.getX() + 0.5 + ux, t.getY(), t.getZ() + 0.5 + uz);
		NavView.Kind wall = this.view.cell(beyond).kind();
		NavView.Kind head = this.view.cell(beyond.above()).kind();
		return this.view.isKnown(beyond.below()) && this.view.standable(beyond.below()) && this.view.cell(beyond.below()).kind() == NavView.Kind.SOLID
			|| wall == NavView.Kind.SOLID || head == NavView.Kind.SOLID;
	}

	/** Most a jump may carry beyond what it needs, with nothing past the landing to stop it (just past the landing block). */
	private static final double OVERSHOOT = 1.1;

	/** Room over the take-off and the landing for the top of a jump up (its head goes about 3 blocks up): nothing solid seen there. */
	private boolean headroomUp(BlockPos n, BlockPos t) {
		NavView.Kind over = this.view.cell(n.above(3)).kind();
		NavView.Kind overLanding = this.view.cell(t.above(2)).kind();
		return over != NavView.Kind.SOLID && over != NavView.Kind.BANNED && overLanding != NavView.Kind.SOLID && overLanding != NavView.Kind.BANNED;
	}

	/** Open and seen (a jump's way has to be: it won't leap into what it hasn't looked at). */
	private boolean openSeen(BlockPos pos) {
		return this.view.isKnown(pos) && this.view.cell(pos).kind() == NavView.Kind.OPEN;
	}

	/**
	 * A jump across a gap (user's request, 2026-10-17: parkour), straight ahead: 1 to 3 blocks of gap (3 only for the
	 * skilled), landing on the same level, a block up or up to three down, if the jump carries that far by the player's own
	 * physics (JumpPhysics, with its run-up and sprint) with room to spare. Everything on the way is seen: the take-off
	 * ground, the gap and the arc over it, the landing. Never over lava; over a drop that would hurt only with more room,
	 * at a cost Caution weighs. Not from a planned bridge or pillar, nor from water.
	 */
	private void tryParkour(Node node, Direction d, boolean onSolidFloor) {
		BlockPos n = node.pos;
		if (!onSolidFloor || node.move == MoveType.BRIDGE || node.move == MoveType.PILLAR || this.view.isWater(n)
			|| !this.view.jumpFooting(n.below())) {
			return;
		}

		// Only where the next block has no floor (anything else is a walk or a step).
		BlockPos first = n.relative(d);
		if (this.view.standable(first.below()) || !this.openSeen(first.below()) || !this.openSeen(n.above(2))) {
			return;
		}

		// Straight on from a walk the same way, or room to back up two blocks for a run at it (as a player does at an edge):
		// it takes off at full speed.
		boolean runUp = node.parent != null && (node.move == MoveType.WALK || node.move == MoveType.PARKOUR) && node.parent.pos.relative(d).equals(n)
			|| this.roomToRunUp(n, d);
		int maxGap = this.skill >= 0.35 ? 3 : 2;
		double margin = 0.15 + 0.25 * (1.0 - this.skill);
		double ux = d.getStepX();
		double uz = d.getStepZ();
		for (int g = 1; g <= maxGap; g++) {
			BlockPos gap = n.relative(d, g);
			// The gap itself: open (feet, head and the top of the jump), and no floor (that would end the gap a block sooner).
			if (!this.openSeen(gap) || !this.openSeen(gap.above()) || !this.openSeen(gap.above(2)) || this.view.standable(gap.below())) {
				return;
			}

			BlockPos column = n.relative(d, g + 1);
			for (int dy = 1; dy >= -3; dy--) {
				BlockPos t = column.above(dy);
				// A ladder or vines across the gap: it grabs on in mid-air (the highest part it reaches).
				if (this.view.cell(t).kind() == NavView.Kind.CLIMB && this.view.isKnown(t)) {
					if (this.tryCatchClimb(node, n, t, g, dy, ux, uz, runUp, margin)) {
						break;
					}

					continue;
				}

				if (!this.view.jumpFooting(t.below())) {
					continue;
				}

				if (dy > 0 && !this.headroomUp(n, t)) {
					break;
				}

				// (Not one it missed a moment ago.)
				if (this.failedBridges.contains(t)) {
					break;
				}

				// The landing and the arc coming down onto it: open from its feet there up to the top of the jump.
				boolean arc = true;
				for (int k = dy; k <= Math.max(2, dy + 2); k++) {
					arc &= this.openSeen(column.above(k));
				}

				if (!arc) {
					break;
				}

				double need = g - 0.6 + PARKOUR_TAKEOFF_LOSS;
				int depth = this.gapDepth(n, d, g);
				if (depth < 0) {
					return;
				}

				boolean deep = depth > this.maxDrop;
				double needed = need + margin + (deep ? 0.3 : 0.0);
				JumpPhysics.Mode mode = JumpPhysics.choose(this.walkSpeed, this.canSprint, runUp, dy, this.jumpBoost, needed,
					this.stopsPast(t, ux, uz) ? Double.MAX_VALUE : needed + OVERSHOOT);
				if (mode == null) {
					break;
				}

				double risk = deep ? (PARKOUR_DEEP_RISK + 0.2 * Math.min(depth, 30)) * this.caution * 2.0 : 0.0;
				double cost = (g + 1) * (mode.sprint ? SPRINT : WALK) + JUMP_EXTRA + PARKOUR_EXTRA + risk;
				this.pendingJump = mode;
				this.add(node, t, MoveType.PARKOUR, cost, new Clearance(), 0);
				break;
			}
		}
	}

	/** A gap whose bottom it hasn't seen counts as this deep. */
	private static final int UNSEEN_GAP_DEPTH = 24;

	/**
	 * How far down it would fall missing this jump: blocks to the floor under the gap (0 for water), as deep as
	 * UNSEEN_GAP_DEPTH where it hasn't seen the bottom, or -1 if it's lava (or an unseen bottom in the Nether).
	 */
	private int gapDepth(BlockPos takeoff, Direction d, int gap) {
		int deepest = 0;
		for (int i = 1; i <= gap; i++) {
			BlockPos column = takeoff.relative(d, i);
			int depth = -1;
			for (int k = 1; k <= 40; k++) {
				BlockPos cell = column.below(k);
				// A bottom it hasn't seen: a deep fall as far as it knows (players jump gaps without seeing the bottom). Not
				// in the Nether, where an unseen bottom may be lava.
				if (!this.view.isKnown(cell)) {
					if (this.lavaWorld) {
						return -1;
					}

					depth = UNSEEN_GAP_DEPTH;
					break;
				}

				NavView.Kind kind = this.view.cell(cell).kind();
				if (kind == NavView.Kind.WATER) {
					depth = 0;
					break;
				}

				if (kind == NavView.Kind.BANNED) {
					return -1;
				}

				if (kind != NavView.Kind.OPEN) {
					depth = k - 1;
					break;
				}
			}

			if (depth < 0) {
				return -1;
			}

			deepest = Math.max(deepest, depth);
		}

		return deepest;
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

			// Its body sweeps over both corners: floor under them too, or it walks round (found in a Nether test: a diagonal
			// cut across the corner of a hole at a run, and it fell 19 blocks).
			if (!this.view.standable(sideX.below()) || !this.view.standable(sideZ.below())) {
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
				this.add(node, n.above(), MoveType.SWIM_UP, SWIM + this.current(n.above()) + up.cost, up, 0);
			}

			if (this.view.isWater(n.below())) {
				this.add(node, n.below(), MoveType.SWIM_DOWN, SWIM + this.current(n.below()) + (this.headUnder(n.below()) ? SWIM_UNDER : 0.0), new Clearance(), 0);
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
			&& !this.failedPillars.contains(n) && !this.view.lavaWithinTwo(n)) {
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
		Boolean known = this.deepDrop.get(feet);
		if (known != null) {
			return known;
		}

		boolean drop = this.besideDeepDropUncached(feet);
		this.deepDrop.put(feet.immutable(), drop);
		return drop;
	}

	private boolean besideDeepDropUncached(BlockPos feet) {
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

		// Settled already (the commonest case): nothing to work out (performance, 2026-10-17).
		Node node = this.nodes.get(to);
		if (node != null && (node.closed || node == this.startNode || node.parent != null && from.g + cost * TRAIL_DISCOUNT >= node.g)) {
			return;
		}

		if (node == null) {
			node = new Node(to.immutable());
			this.nodes.put(node.pos, node);
		}

		// The cell's own part of the cost, once per search (performance, 2026-10-17: the trail and edge checks ran again for
		// every way into a cell).
		if (!node.costed) {
			node.costed = true;
			// In a lava world, the lava sea's shore (a block or two above it) costs more: one slip there is a swim in lava
			// (found in a Nether test: a route along the shore, a vine climb cut short, and it slid in).
			if (this.lavaWorld && to.getY() <= this.seaLevel + 1) {
				node.shoreAdd = LAVA_SEA_SHORE;
			}

			if (this.trails.contains(to.asLong())) {
				node.trailFactor = TRAIL_DISCOUNT;
			}

			if (!this.dangers.isEmpty()) {
				Vec3 center = Vec3.atBottomCenterOf(to);
				for (Vec3 danger : this.dangers) {
					if (danger.distanceTo(center) <= DANGER_RADIUS) {
						node.extraAdd += DANGER_NEAR * this.caution * 2.0;
						break;
					}
				}
			}

			if (this.besideDeepDrop(to)) {
				node.extraAdd += EDGE_RISK * this.caution * 2.0;
			}
		}

		double stepCost = (cost + node.shoreAdd) * node.trailFactor + node.extraAdd;

		// A held breath lasts about 15 s: no route with a longer swim under water than that, with a margin (found in
		// testing: it drowned under a ceiling at night).
		int underwater = this.headUnder(to) ? from.underwater + 1 : 0;
		if (underwater > this.maxUnderwater) {
			return;
		}

		// Underground (no open sky over the water), never a move with its head under, unless it's under already and getting
		// out: a flooded cave passage can go on further than it can see (found in testing: the main cause of death left).
		if (underwater > 0 && !this.startUnderwater && !this.view.world().openAbove(to.above())) {
			return;
		}

		double g = from.g + stepCost;
		if (node.closed || node.parent != null && g >= node.g || node == this.startNode) {
			return;
		}

		node.g = g;
		node.f = g + this.heuristic(to);
		node.parent = from;
		node.underwater = underwater;
		node.move = move;
		node.jump = move == MoveType.PARKOUR ? this.pendingJump : null;
		node.digs = clearance.digs == null ? List.of() : List.copyOf(clearance.digs);
		node.unknown = clearance.unknown;
		node.blocksUsed = from.blocksUsed + blocks;
		this.open.add(node);
	}
}
