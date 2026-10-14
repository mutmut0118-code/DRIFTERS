package com.dinglebob.drifter.brain.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.personality.Habits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Digging a {@link Tunnel} the way a player does (DESIGN.md section 10.3): a 1x2 tunnel or a 2-high staircase, one
 * step at a time, mining every ore vein it uncovers (only with the right pickaxe), blocking off caves and water it
 * breaks into, never digging into gravel, sand, lava or someone's build, and a torch every so often, always on its
 * habit's side going in (user's note: torches mark where it is and the way back).
 *
 * <p>It only reads blocks it has seen or that mining has just revealed (each dug block shows it its six neighbours).
 * The owning activity runs the tasks this hands out and reports back through {@link #stepDone}.
 */
public final class TunnelDigger {
	/** Digging a tunnel block slower than this (seconds) isn't worth it: it turns. */
	public static final float MAX_DIG_SECONDS = 6.0F;
	/** A torch every this many steps, if it has any. */
	public static final int TORCH_EVERY = 8;
	/** Turns a staircase may make around bad ground before it gives up. */
	public static final int MAX_TURNS = 6;

	private final DrifterEntity drifter;
	private final Tunnel tunnel;
	@Nullable
	private BlockPos digging;
	@Nullable
	private BlockPos stepping;
	@Nullable
	private BlockPos refill;
	/** A cave or water cell it opened onto: it tries to plug that first, and keeps going if it can. */
	@Nullable
	private BlockPos plug;
	/** The cell it dug that opened onto it (filled back in if the plug can't be placed). */
	@Nullable
	private BlockPos plugFrom;
	private boolean plugging;
	private boolean oreStep;
	private boolean walkingToTip;
	private int walkFailures;
	private final ArrayDeque<BlockPos> ores = new ArrayDeque<>();
	private final Map<BlockPos, Integer> oreTries = new HashMap<>();
	private final List<BlockPos> retryOres = new ArrayList<>();
	private int oresMined;
	@Nullable
	private String lastThought;

	public TunnelDigger(DrifterEntity drifter, Tunnel tunnel) {
		this.drifter = drifter;
		this.tunnel = tunnel;
	}

	public Tunnel tunnel() {
		return this.tunnel;
	}

	public int oresMined() {
		return this.oresMined;
	}

	/** Something worth saying in the thought log since the last call (a cave, a turn), or null. */
	@Nullable
	public String takeThought() {
		String thought = this.lastThought;
		this.lastThought = null;
		return thought;
	}

	/** Adds to what it has to say since the last {@link #takeThought()} (several things can happen in one step). */
	private void say(String thought) {
		this.lastThought = this.lastThought == null ? thought : this.lastThought + "; " + thought;
	}

	/** A missed ore (out of view from here), or a vein it couldn't step over to, isn't a failure of the job. */
	public boolean countsAsFailure(Task step) {
		return !this.oreStep && !this.veinWalk && !(step instanceof com.dinglebob.drifter.executor.task.CollectItemsTask);
	}

	/** The next step, or null when the tunnel is finished ({@link Tunnel#done}) or out of legs. */
	@Nullable
	public Task next() {
		Level level = this.drifter.level();
		BlockPos feet = this.drifter.blockPosition();
		Tunnel t = this.tunnel;

		// Broke into a cave or water: plug the hole itself (a player puts a block in the opening and carries on), section
		// 10.3 "blocks flowing water". If it can't reach the hole, it fills back in the block it dug instead.
		if (this.plug != null) {
			BlockPos hole = this.plug;
			if (solid(level, hole)) {
				this.plug = null;
				return this.next();
			}

			if (!this.plugging && this.drifter.getInventory().count(TunnelDigger::isBlock) > 0) {
				this.plugging = true;
				Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0 ? Tools.BUILDING_BLOCK : TunnelDigger::isBlock;
				return new PlaceBlockTask(this.drifter, hole, block, "a block to plug the opening");
			}

			// Couldn't plug a hole into a cave (air): it leaves it open and carries on, like a player passing a cave (found in
			// testing: every unpluggable cave opening turned the stairs, and near the surface they turned until they gave up).
			if (level.getFluidState(hole).isEmpty()) {
				this.plug = null;
				this.say("left the opening to the cave");
				return this.next();
			}

			// Water it couldn't plug: back to filling in its own block, and this way's done.
			this.plug = null;
			this.refill = this.plugFrom;
			if (this.refill != null) {
				t.opened.remove(this.refill);
			}

			if (t.leg != null && !t.leg.stairs()) {
				t.legSteps = t.leg.length();
			} else if (t.leg != null) {
				this.pendingTurn = true;
				this.turnReason = "an opening it couldn't plug";
			}

			if (++t.breaches >= 4) {
				t.done = true;
			}
		}

		if (this.refill != null) {
			BlockPos cell = this.refill;
			if (solid(level, cell) || this.drifter.getInventory().count(TunnelDigger::isBlock) == 0) {
				this.refill = null;
				if (!solid(level, cell)) {
					t.done = true;
				}

				// The way it was going is closed now: the end of the tunnel is where it stands (found in testing: the end
				// was left inside the block it had just filled back in, and it tried to walk there for minutes).
				t.tip = feet;
				if (this.pendingTurn && t.leg != null && t.leg.stairs()) {
					this.pendingTurn = false;
					return this.turn(feet, t.leg);
				}

				return this.next();
			}

			Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0 ? Tools.BUILDING_BLOCK : TunnelDigger::isBlock;
			return new PlaceBlockTask(this.drifter, cell, block, "a block to close off the opening");
		}

		// Section 10.3 "Veins: always finishes a visible ore vein before moving on". Out of reach, it steps closer (digging
		// its way if it has to), like a player (found by the user: half-mined veins left behind).
		while (!this.ores.isEmpty()) {
			BlockPos ore = this.ores.peek();
			if (!isOre(level.getBlockState(ore)) || !this.canMineOre(level, ore) || ore.distSqr(feet) > VEIN_REACH * VEIN_REACH) {
				this.ores.poll();
				continue;
			}

			if (this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(ore)) <= this.drifter.blockInteractionRange()
				&& com.dinglebob.drifter.hands.Reach.canReach(this.drifter, ore)) {
				this.ores.poll();
				this.digging = ore;
				this.oreStep = true;
				return Activity.digTask(this.drifter, ore);
			}

			if (this.veinWalks.merge(ore.immutable(), 1, Integer::sum) > 2) {
				this.ores.poll();
				continue;
			}

			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, ore, java.util.Set.of());
			Vec3 to = spot != null ? Vec3.atBottomCenterOf(spot) : Vec3.atCenterOf(ore);
			this.say("stepping over to finish the vein");
			this.veinWalk = true;
			return new NavigateTask(this.drifter, to, spot != null ? 0.5 : 2.5, "step over to the ore");
		}

		// Vein done: pick up what it dropped (found by the user: raw iron, coal and copper left lying in the tunnels).
		if (this.uncollected > 0) {
			this.uncollected = 0;
			return new com.dinglebob.drifter.executor.task.CollectItemsTask(this.drifter, COLLECT_RADIUS);
		}

		if (t.done || t.steps >= t.maxSteps) {
			t.done = true;
			return null;
		}

		// The next leg when this one is used up.
		if (t.leg == null || t.legSteps >= t.leg.length()) {
			t.leg = t.legs.poll();
			if (t.leg == null) {
				return null;
			}

			t.tip = t.leg.start();
			t.direction = t.leg.direction();
			t.legSteps = 0;
			this.walkFailures = 0;
		}

		if (!feet.equals(t.tip)) {
			if (this.walkingToTip && ++this.walkFailures > 3) {
				// Can't get back to where this leg starts: skip it.
				this.say("can't get back to the end of the tunnel at " + t.tip.toShortString());
				t.legSteps = t.leg.length();
				this.walkingToTip = false;
				return this.next();
			}

			this.walkingToTip = true;
			this.stepping = null;
			return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(t.tip), 0.3, "walk to the end of the tunnel");
		}

		this.walkingToTip = false;

		// A torch now and then, if it has any, always on its habit's side going in: on the wall beside its head.
		if (t.sinceTorch >= TORCH_EVERY && this.drifter.getInventory().count(stack -> stack.is(Items.TORCH)) > 0) {
			t.sinceTorch = 0;
			BlockPos spot = feet.above();
			Direction wall = this.drifter.getPersonality().habits().torchSide == Habits.Side.RIGHT ? t.direction.getClockWise()
				: t.direction.getCounterClockWise();
			if (level.getBlockState(spot).isAir()) {
				return new PlaceBlockTask(this.drifter, spot, stack -> stack.is(Items.TORCH), "a torch").preferSupport(wall);
			}
		}

		Tunnel.Leg leg = t.leg;
		if (leg.stairs() && feet.getY() <= leg.targetY()) {
			// Deep enough: this staircase is done.
			this.say("down to Y " + feet.getY());
			t.legSteps = leg.length();
			return this.next();
		}

		BlockPos front = feet.relative(t.direction);
		BlockPos[] cells = leg.stairs() ? new BlockPos[]{front.above(), front, front.below()} : new BlockPos[]{front.above(), front};
		BlockPos nextFeet = leg.stairs() ? front.below() : front;
		for (BlockPos cell : cells) {
			String why = this.bad(level, cell);
			if (why != null) {
				this.turnReason = why;
				if (why.equals("a cave")) {
					// A way into a cave right ahead: a caver may go in; otherwise the stairs go straight through it (the cells
					// are open already, the floor is checked below).
					this.caveBreach = cell.immutable();
					this.turnReason = null;
					continue;
				}

				if (!leg.stairs()) {
					this.say("the tunnel ran into " + why);
				}

				return this.turn(feet, leg);
			}
		}

		// The floor it's about to step onto (found in testing: a level tunnel ran out over a cave and it fell for 13 damage).
		// A hole it can see the bottom of nearby gets a block put in it, like a player bridging; otherwise not this way.
		BlockPos floor = nextFeet.below();
		if (!this.tunnel.opened.contains(floor) && level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
			&& this.allOpen(level, cells)) {
			boolean fluid = !level.getFluidState(floor).isEmpty();
			// Only a dip of one block (solid under it): over a real drop it would be building stairs down through the air.
			boolean dip = solid(level, floor.below());
			if (!fluid && dip && this.bridged.add(floor.immutable()) && this.drifter.getInventory().count(TunnelDigger::isBlock) > 0) {
				Predicate<ItemStack> block = this.drifter.getInventory().count(Tools.BUILDING_BLOCK) > 0 ? Tools.BUILDING_BLOCK : TunnelDigger::isBlock;
				this.say("a hole in the floor: filling it");
				return new PlaceBlockTask(this.drifter, floor, block, "a block over the hole");
			}

			// Stairs over a cave floor a couple of blocks down: hop down onto it and carry on from there (going down is the
			// point; found in testing: at a cave's edge every way was "a drop" and the stairs turned until they gave up).
			BlockPos landing = leg.stairs() && !fluid ? this.landing(level, floor) : null;
			if (landing != null) {
				this.stepping = landing;
				return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(landing), 0.4, "hop down into the cave");
			}

			this.turnReason = fluid ? "water under the floor" : "a drop";
			if (!leg.stairs()) {
				this.say("the tunnel ran into " + this.turnReason);
			}

			return this.turn(feet, leg);
		}

		for (BlockPos cell : cells) {
			// Grass and flowers too (they're in the way of clicking the ground behind them).
			if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
				|| com.dinglebob.drifter.executor.task.PlaceBlockTask.isClickableReplaceable(level, cell)) {
				this.digging = cell;
				return Activity.digTask(this.drifter, cell);
			}
		}

		this.stepping = nextFeet;
		return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(nextFeet), 0.3, leg.stairs() ? "step down the stairs" : "step along the tunnel");
	}

	/**
	 * Water, a cave, gravel or sand, lava, rock too hard, someone's build: not this way. Open air under the open sky (the
	 * top of a staircase dug from the surface) is fine.
	 */
	@Nullable
	private String bad(Level level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		boolean open = state.getCollisionShape(level, cell).isEmpty();
		if (!level.getFluidState(cell).isEmpty()) {
			return level.getFluidState(cell).is(net.minecraft.tags.FluidTags.LAVA) ? "lava" : "water";
		}

		if (open) {
			// Air it didn't make, underground: a cave.
			return !this.tunnel.opened.contains(cell) && !level.canSeeSky(cell) ? "a cave" : null;
		}

		String name = state.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
		if (state.getBlock() instanceof FallingBlock) {
			return name + " that would fall";
		}

		// Never through what a player placed (tracked). Above ground, nothing that looks built either (a village's houses);
		// underground that's a mineshaft or a dungeon, which players dig through (found in testing: an old mineshaft by
		// home turned every staircase away, for days).
		if (!KnownBlocks.mayTake(level, cell)
			|| com.dinglebob.drifter.nav.NavView.looksBuilt(state) && !Caver.underground(this.drifter, cell)) {
			return name + " (someone's build)";
		}

		if (state.getDestroySpeed(level, cell) < 0.0F || Tools.digSeconds(this.drifter, state, level, cell) > MAX_DIG_SECONDS) {
			return name + " too hard to dig";
		}

		return null;
	}

	/** A staircase step that way has nothing bad in it, and it doesn't head into its hut. */
	private boolean clearWay(BlockPos feet, Direction direction) {
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		if (home != null && home.hutBuilt) {
			BlockPos toward = feet.relative(direction, 2);
			int reach = com.dinglebob.drifter.building.StarterHut.HALF + 1;
			if (Math.abs(toward.getX() - home.center.getX()) <= reach && Math.abs(toward.getZ() - home.center.getZ()) <= reach
				&& Math.abs(toward.getY() - home.center.getY()) <= 4) {
				return false;
			}
		}

		Level level = this.drifter.level();
		BlockPos front = feet.relative(direction);
		for (BlockPos cell : new BlockPos[]{front.above(), front, front.below()}) {
			String why = this.bad(level, cell);
			// Cave space is fine to go through (same as going straight on).
			if (why != null && !why.equals("a cave")) {
				this.turnReason = why;
				return false;
			}
		}

		return true;
	}

	/** A cave it just broke into (for a caver to go and explore instead of plugging it). */
	@Nullable
	private BlockPos caveBreach;

	/** The cave it just broke into, once (then forgotten). */
	@Nullable
	public BlockPos takeCaveBreach() {
		BlockPos breach = this.caveBreach;
		this.caveBreach = null;
		return breach;
	}

	/** Forget the hole it meant to plug (it's going into the cave instead). */
	public void leaveOpen() {
		this.plug = null;
		this.plugging = false;
		this.refill = null;
		this.pendingTurn = false;
	}

	/** Most blocks it hops down off its stairs onto a cave floor. */
	private static final int HOP_DOWN = 3;

	/** Where it lands dropping through this open floor cell: dry, solid ground at most {@link #HOP_DOWN} down, or null. */
	@Nullable
	private BlockPos landing(Level level, BlockPos floor) {
		BlockPos cell = floor;
		for (int k = 1; k <= HOP_DOWN; k++) {
			if (!level.getFluidState(cell).isEmpty() || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				return null;
			}

			if (solid(level, cell.below())) {
				return cell.immutable();
			}

			cell = cell.below();
		}

		return null;
	}

	/** How far from where it stands it goes on with a vein. */
	private static final int VEIN_REACH = 6;
	/** How far it looks for its drops after a vein. */
	private static final double COLLECT_RADIUS = 6.0;
	private final Map<BlockPos, Integer> veinWalks = new HashMap<>();
	/** Ore blocks mined since it last picked up. */
	private int uncollected;
	private boolean veinWalk;

	/** Floor holes it has tried to fill (once each). */
	private final java.util.Set<BlockPos> bridged = new java.util.HashSet<>();

	/** All these cells are already open (dug, or air): the floor ahead is what it would step onto next. */
	private boolean allOpen(Level level, BlockPos[] cells) {
		for (BlockPos cell : cells) {
			if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/** Why the last turn happened, for its thoughts. */
	@Nullable
	private String turnReason;

	/** A level leg ends here; a staircase turns a quarter and carries on down (a few times at most). */
	@Nullable
	private Task turn(BlockPos feet, Tunnel.Leg leg) {
		Tunnel t = this.tunnel;
		if (!leg.stairs()) {
			t.legSteps = leg.length();
			return this.next();
		}

		if (++t.turns > MAX_TURNS) {
			t.done = true;
			this.say("the way down is no good here");
			return null;
		}

		// A quarter turn to whichever side is clear (it looks before it digs), never back toward its own hut.
		Direction first = this.drifter.getRandom().nextBoolean() ? t.direction.getClockWise() : t.direction.getCounterClockWise();
		Direction turned = null;
		for (Direction side : new Direction[]{first, first.getOpposite()}) {
			if (this.clearWay(feet, side)) {
				turned = side;
				break;
			}
		}

		if (turned == null) {
			t.done = true;
			this.say("the way down is no good here" + (this.turnReason == null ? "" : " (" + this.turnReason + ")"));
			return null;
		}

		t.leg = new Tunnel.Leg(feet, turned, leg.length() - t.legSteps, leg.targetY());
		t.tip = feet;
		t.direction = turned;
		t.legSteps = 0;
		this.say("turning " + turned.getName() + " on the way down" + (this.turnReason == null ? "" : " (" + this.turnReason + ")"));
		this.turnReason = null;
		return this.next();
	}

	public void stepDone(Task step, Task.Status status) {
		Tunnel t = this.tunnel;
		Level level = this.drifter.level();
		if (this.digging != null && status == Task.Status.SUCCESS && level.getBlockState(this.digging).getCollisionShape(level, this.digging).isEmpty()) {
			BlockPos dug = this.digging;
			t.opened.add(dug);
			if (this.oreStep) {
				this.oresMined++;
				this.uncollected++;
			}

			for (Direction direction : Direction.values()) {
				BlockPos neighbor = dug.relative(direction);
				if (t.opened.contains(neighbor)) {
					continue;
				}

				BlockState state = level.getBlockState(neighbor);
				boolean outdoors = state.isAir() && level.canSeeSky(neighbor);
				if (!level.getFluidState(neighbor).isEmpty() || state.isAir() && !outdoors) {
					// Broke into a cave or water: plug the opening (next step).
					if (this.plug == null) {
						this.plug = neighbor;
						this.plugFrom = dug;
						this.plugging = false;
						this.say(state.isAir() ? "broke into a cave: plugging the hole" : "hit water: plugging it");
						if (state.isAir()) {
							this.caveBreach = neighbor.immutable();
						}
					}

					continue;
				}

				if (isOre(state) && !this.ores.contains(neighbor)) {
					this.ores.add(neighbor);
				}
			}
		}

		if (this.oreStep && status == Task.Status.FAILURE && this.digging != null && this.oreTries.merge(this.digging, 1, Integer::sum) < 2) {
			// Out of view from here: it tries again from the next step along.
			this.retryOres.add(this.digging);
		}

		if (this.stepping != null && status == Task.Status.SUCCESS && this.drifter.blockPosition().equals(this.stepping)) {
			this.ores.addAll(this.retryOres);
			this.retryOres.clear();
			t.tip = this.stepping;
			t.legSteps++;
			t.steps++;
			t.sinceTorch++;
		}

		if (step instanceof PlaceBlockTask && this.plugging && this.plug != null && status == Task.Status.SUCCESS) {
			// Plugged: the opening is closed and it carries on.
			this.plug = null;
			this.plugging = false;
		}

		if (this.pendingTurn && this.refill == null && this.plug == null && t.leg != null && t.leg.stairs()) {
			this.pendingTurn = false;
			this.turn(this.drifter.blockPosition(), t.leg);
		}

		this.digging = null;
		this.stepping = null;
		this.oreStep = false;
		this.veinWalk = false;
	}

	private boolean pendingTurn;

	private boolean canMineOre(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		int slot = Tools.bestToolSlot(this.drifter, state, level, pos);
		return slot >= 0 && this.drifter.getInventory().getItem(slot).isCorrectToolForDrops(state) && KnownBlocks.mayTake(level, pos);
	}

	public static boolean isOre(BlockState state) {
		return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
			|| state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.DIAMOND_ORES)
			|| state.is(BlockTags.EMERALD_ORES);
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	/** Something to plug a hole with: a building block, or any full block that doesn't fall. */
	private static boolean isBlock(ItemStack stack) {
		return Tools.BUILDING_BLOCK.test(stack) || stack.getItem() instanceof BlockItem block
			&& block.getBlock().defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
			&& !(block.getBlock() instanceof FallingBlock);
	}
}
