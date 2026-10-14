package com.dinglebob.drifter.brain.activity;

import java.util.Locale;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Food;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Storage;
import com.dinglebob.drifter.brain.mining.Caver;
import com.dinglebob.drifter.brain.mining.Tunnel;
import com.dinglebob.drifter.brain.mining.TunnelDigger;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A mining trip (DESIGN.md section 10.3) for an ore it hasn't seen: down to where that ore is common and back.
 * <ul>
 *   <li>If it remembers that ore somewhere (seen in a cave or a cliff), it goes there first; cavers and surface
 *       explorers lean on that.</li>
 *   <li>Otherwise it digs a 2-high staircase from home down to the ore's depth (torches on its habit side), then a
 *       branch mine: a main tunnel with side branches at its spacing habit, mining every vein it uncovers. Home remembers
 *       the bottom of the stairs, so later trips walk back down and branch out a new way.</li>
 * </ul>
 * It heads back up when it has enough, its inventory is nearly full, it's getting late by its own clock (which drifts
 * underground), it's hurt or hungry with nothing to eat, or a cautious one runs out of torches.
 * TODO(milestone 8 later steps): following caves (caving), ravine work, deeper trips for diamonds.
 */
public class MineTripActivity extends Activity {
	/** A trip's tunnel, steps at most. */
	public static final int MAX_STEPS = 160;
	private static final int MAIN_LENGTH = 24;
	private static final int BRANCH_LENGTH = 12;
	/** Remembered ore this close is worth going to first. */
	private static final double REMEMBERED_RANGE = 64.0;
	/** Ticks of margin before its evening to start back up (the stairs take a while). */
	private static final long RETURN_MARGIN = 1200;

	private enum Phase {
		START, REMEMBERED, DIG, CAVE, HOME
	}

	private final Item item;
	private final String label;
	private final Predicate<BlockState> blocks;
	private final int total;
	private final int depth;
	private Phase phase = Phase.START;
	@Nullable
	private Tunnel tunnel;
	@Nullable
	private TunnelDigger digger;
	private boolean branched;
	/** Exploring a cave (section 10.3 "Caving"), or null. */
	@Nullable
	private Caver caver;
	/** Caving is over for this trip (the cave ran out); the rest is dug. */
	private boolean cavingDone;
	private boolean torchesChecked;
	@Nullable
	private String why;

	/** Ores also common high in mountains (iron, coal: section 10.3): on high ground it mines into the mountain. */
	public static final int MOUNTAIN_ABOVE = 96;
	public static final int MOUNTAIN_DIG = 24;

	private final boolean mountain;

	public MineTripActivity(DrifterEntity drifter, Item item, String label, Predicate<BlockState> blocks, int total, int depth, boolean mountain) {
		super(drifter);
		this.mountain = mountain;
		this.item = item;
		this.label = label;
		this.blocks = blocks;
		this.total = total;
		this.depth = depth;
	}

	@Override
	public String describe() {
		return switch (this.phase) {
			case HOME -> "head back up from mining";
			default -> "go mining for " + this.label + " (" + this.have() + "/" + this.total + ")";
		};
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 12;
	}

	private int have() {
		return this.drifter.getInventory().count(stack -> stack.is(this.item));
	}

	/** A walk inside the mine that takes this long is going nowhere: drop it and let the digger pick another way. */
	private static final int MAX_WALK_TICKS = 20 * 60;

	@Override
	protected boolean rethink() {
		return (this.phase == Phase.DIG || this.phase == Phase.CAVE) && this.child() instanceof NavigateTask walk && walk.ticksRunning() > MAX_WALK_TICKS;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// Caving: a spot it couldn't get to or a torch that wouldn't go on the wall is part of exploring, not a failure.
		if (this.phase == Phase.CAVE) {
			return false;
		}

		return this.phase != Phase.DIG || this.digger == null || this.digger.countsAsFailure(step);
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (this.phase == Phase.DIG && this.digger != null) {
			this.digger.stepDone(step, status);
			this.relayThought();
			this.enterCave();
		}

		if (this.phase == Phase.CAVE && this.caver != null) {
			this.caver.stepDone(step, status);
			String thought = this.caver.takeThought();
			if (thought != null) {
				this.think(thought);
			}
		}
	}

	/** Broke into a cave, or found one right ahead: a caver goes in and explores it (a strip miner plugs it and carries on). */
	private boolean enterCave() {
		BlockPos breach = this.digger == null ? null : this.digger.takeCaveBreach();
		// Strip miners too, for a quick look (found by the user: stopping right before the cave most of the time).
		if (breach == null || this.cavingDone || this.caver != null || this.cavesEntered >= MAX_CAVES) {
			return false;
		}

		// A glance through the hole: an air pocket of a block or two isn't a cave (found in testing: most "caves" its stairs
		// broke into were pockets, and it gave up caving for the trip on each).
		if (!Caver.isCave(this.drifter, breach)) {
			return false;
		}

		this.cavesEntered++;

		this.digger.leaveOpen();
		this.caver = new Caver(this.drifter, breach, this.depth, this.caveLegs());
		this.phase = Phase.CAVE;
		this.think("broke into a cave: exploring it");
		return true;
	}

	/** Caves it goes into from its tunnel on one trip at most. */
	private static final int MAX_CAVES = 3;
	private int cavesEntered;

	/** Goes caving rather than strip mining (its mining style habit: everyone but strip miners). */
	private boolean caves() {
		return this.drifter.getPersonality().habits().miningLean < STRIP_MINER;
	}

	/** Walks to new spots per cave trip: braver ones go further. */
	private int caveLegs() {
		if (!this.caves()) {
			return STRIP_MINER_CAVE_LEGS;
		}

		return 16 + (int)Math.round((Caver.MAX_LEGS - 16) * this.drifter.getPersonality().get(Trait.BRAVERY));
	}

	/** A strip miner's quick look into a cave its tunnel opens up. */
	private static final int STRIP_MINER_CAVE_LEGS = 8;
	/** Mining style habit at or above this: a strip miner. */
	private static final double STRIP_MINER = 0.65;
	/** How far from home it looks for a cave it has seen. */
	private static final int CAVE_SEARCH = 48;
	/** Torches it wants before going caving. */
	private static final int CAVE_TORCHES = 16;

	/** In the cave: ore it spots first, then the next place to explore; when the cave runs out, back to digging. */
	private Status cave() {
		BlockPos spotted = this.rememberedOre(SPOTTED_RANGE);
		if (spotted != null) {
			return this.goFor(spotted, "spotted " + this.label + " at " + spotted.toShortString() + ": going to get it");
		}

		Task step = this.caver.next();
		String thought = this.caver.takeThought();
		if (thought != null) {
			this.think(thought);
		}

		if (step != null) {
			return this.begin(step);
		}

		// The cave's done: the rest the usual way (stairs and a branch mine), or home if that's enough for one trip.
		this.caver = null;
		this.cavingDone = this.cavesEntered >= MAX_CAVES || this.tunnel == null;
		this.phase = this.have() > 0 ? Phase.HOME : this.tunnel != null ? Phase.DIG : Phase.START;
		this.why = "explored the cave";
		if (this.phase == Phase.HOME) {
			this.think("heading back up: explored the cave");
		}

		return Status.RUNNING;
	}

	private void relayThought() {
		String thought = this.digger == null ? null : this.digger.takeThought();
		if (thought != null) {
			this.think(thought);
		}
	}

	@Override
	protected Status next() {
		if (this.phase != Phase.HOME) {
			String stop = this.reasonToStop();
			if (stop != null) {
				this.why = stop;
				this.phase = Phase.HOME;
				this.think("heading back up: " + stop);
				this.rememberStairs();
			}
		}

		return switch (this.phase) {
			case START -> this.start();
			case REMEMBERED -> {
				// Went to the ore it remembered and gathered what it could; anything still missing means a dig (or back to
				// the tunnel it was digging, for ore it spotted on the way).
				this.phase = this.have() >= this.total ? Phase.HOME : this.caver != null ? Phase.CAVE : this.tunnel != null ? Phase.DIG : Phase.START;
				this.rememberedTried = true;
				yield Status.RUNNING;
			}
			case DIG -> this.dig();
			case CAVE -> this.cave();
			case HOME -> this.goHome();
		};
	}

	private boolean rememberedTried;
	private boolean kitChecked;
	/** Trips to below this height take a crafting table and sticks along. */
	private static final int KIT_DEPTH = 8;
	private static final int KIT_STICKS = 4;
	private boolean spareChecked;

	/** Too late in the day to start a trip (it would turn round at once). */
	public static boolean tooLateToGo(DrifterEntity d) {
		long time = Schedule.believedTime(d);
		return time >= Schedule.shelterThinking(d) - RETURN_MARGIN * 2 && time < Schedule.NIGHT_END;
	}

	/** Pickaxe uses left on everything it carries. */
	private static int pickaxeUsesLeft(DrifterEntity d) {
		int left = 0;
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = d.getInventory().getItem(slot);
			if (stack.is(ItemTags.PICKAXES)) {
				left += stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 10000;
			}
		}

		return left;
	}

	/** Most pickaxes it takes down at once. */
	private static final int MAX_PICKAXES = 4;
	/** Blocks of a branch mine it expects to dig on a trip, on top of any stairs. */
	private static final int BRANCH_DIGS = 100;

	@Nullable
	private String reasonToStop() {
		DrifterEntity d = this.drifter;
		if (this.have() >= this.total) {
			return "got enough " + this.label;
		}

		if (Storage.freeSlots(d) <= 2) {
			return "pockets full";
		}

		if (Schedule.believedTime(d) >= Schedule.shelterThinking(d) - RETURN_MARGIN && Schedule.believedTime(d) < Schedule.NIGHT_END) {
			return "it must be getting late";
		}

		if (d.getHealth() < 10.0F) {
			return "hurt";
		}

		if (d.getFoodData().getFoodLevel() <= 6 && Food.choose(d) == null) {
			return "hungry with nothing to eat";
		}

		if (d.getInventory().count(stack -> stack.is(ItemTags.PICKAXES)) == 0) {
			return "no pickaxe left";
		}

		// Section 10.17: careful ones head back before the last pickaxe breaks.
		if (pickaxes(d) == 1 && wear(bestPickaxe(d)) > 0.85) {
			return "pickaxe nearly worn out";
		}

		boolean noTorches = d.getInventory().count(stack -> stack.is(Items.TORCH)) == 0;
		if (noTorches && this.tunnel != null && this.tunnel.steps > 16 && d.getPersonality().get(Trait.CAUTION) > 0.5) {
			return "out of torches";
		}

		return null;
	}

	/** Pickaxes it carries. */
	private static int pickaxes(DrifterEntity d) {
		return d.getInventory().count(stack -> stack.is(ItemTags.PICKAXES));
	}

	private static ItemStack bestPickaxe(DrifterEntity d) {
		ItemStack best = ItemStack.EMPTY;
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = d.getInventory().getItem(slot);
			if (stack.is(ItemTags.PICKAXES) && (best.isEmpty() || stack.getMaxDamage() - stack.getDamageValue() > best.getMaxDamage() - best.getDamageValue())) {
				best = stack;
			}
		}

		return best;
	}

	/** 0 for new, 1 for broken. */
	private static double wear(ItemStack stack) {
		return stack.isEmpty() || !stack.isDamageableItem() ? 0.0 : stack.getDamageValue() / (double)stack.getMaxDamage();
	}

	private Status start() {
		// Section 10.17 "Spares: keeps a spare pickaxe": with only one, half worn, it makes another before going down.
		if (!this.spareChecked) {
			this.spareChecked = true;
			// Players take a few stone pickaxes down: one lasts 131 blocks, a staircase down 50 is 150 (found in testing:
			// both wore out just short of the bottom). It estimates the digging and makes more if its pickaxes won't last.
			Home home = this.drifter.getDrifterBrain().home();
			int fromY = home != null && home.mineBottom != null ? home.mineBottom.getY() : this.drifter.blockPosition().getY();
			int needed = Math.max(0, fromY - this.depth) * 3 + BRANCH_DIGS;
			boolean shortOfUses = pickaxeUsesLeft(this.drifter) < needed && pickaxes(this.drifter) < MAX_PICKAXES;
			if (shortOfUses || pickaxes(this.drifter) == 1 && wear(bestPickaxe(this.drifter)) > 0.5) {
				com.dinglebob.drifter.brain.Planner.Plan plan = com.dinglebob.drifter.brain.Planner.plan(this.drifter,
					com.dinglebob.drifter.brain.KnownWorld.scan(this.drifter), Items.STONE_PICKAXE,
					this.drifter.getInventory().count(stack -> stack.is(Items.STONE_PICKAXE)) + 1);
				if (plan.feasible() && plan.next() != null) {
					this.think("making a spare pickaxe before going down");
					this.spareChecked = false;
					return this.begin(com.dinglebob.drifter.brain.Goals.activityFor(this.drifter, plan.next()));
				}
			}
		}

		// Deep trips: a crafting table and sticks along, so a pickaxe that wears out down there can be replaced with one made
		// from the rock it mines (found in testing: 150 blocks from home at Y -46, its last pickaxe broke and it was stuck).
		if (!this.kitChecked && this.depth < KIT_DEPTH) {
			this.kitChecked = true;
			for (var need : java.util.List.of(java.util.Map.entry(Items.CRAFTING_TABLE, 1), java.util.Map.entry(Items.STICK, KIT_STICKS))) {
				int carried = this.drifter.getInventory().count(stack -> stack.is(need.getKey()));
				if (carried >= need.getValue()) {
					continue;
				}

				com.dinglebob.drifter.brain.Planner.Plan plan = com.dinglebob.drifter.brain.Planner.plan(this.drifter,
					com.dinglebob.drifter.brain.KnownWorld.scan(this.drifter), need.getKey(), need.getValue());
				if (plan.feasible() && plan.next() != null && !plan.needsExploring()) {
					this.think("taking " + (need.getKey() == Items.STICK ? "sticks" : "a crafting table") + " along, to make a pickaxe down there if one breaks");
					this.kitChecked = false;
					return this.begin(com.dinglebob.drifter.brain.Goals.activityFor(this.drifter, plan.next()));
				}
			}
		}

		// Ore it remembers seeing (a cave, a cliff, a ravine): go and get that first.
		if (!this.rememberedTried) {
			BlockPos remembered = this.rememberedOre(REMEMBERED_RANGE);
			if (remembered != null) {
				return this.goFor(remembered, "going for the " + this.label + " it saw at " + remembered.toShortString());
			}
		}

		// Cavers go to a cave they've seen near home (an opening in a hillside, a ravine, one a tunnel broke into), with
		// torches to light the way (the user's request: caving finds more iron than strip mining, and teaches it the land).
		if (this.caves() && !this.cavingDone) {
			if (!this.torchesChecked) {
				this.torchesChecked = true;
				int torches = this.drifter.getInventory().count(stack -> stack.is(Items.TORCH));
				if (torches < CAVE_TORCHES) {
					com.dinglebob.drifter.brain.Planner.Plan plan = com.dinglebob.drifter.brain.Planner.plan(this.drifter,
						com.dinglebob.drifter.brain.KnownWorld.scan(this.drifter), Items.TORCH, CAVE_TORCHES);
					if (plan.feasible() && plan.next() != null) {
						this.think("making torches before going caving");
						this.torchesChecked = false;
						return this.begin(com.dinglebob.drifter.brain.Goals.activityFor(this.drifter, plan.next()));
					}
				}
			}

			Home caveHome = this.drifter.getDrifterBrain().home();
			BlockPos from = caveHome != null && caveHome.near(this.drifter, Home.STORAGE_RANGE) ? caveHome.center : this.drifter.blockPosition();
			// Only a real cave (not air under an overhang by the surface; found in testing): a few tries.
			java.util.Set<BlockPos> notCaves = new java.util.HashSet<>();
			BlockPos cave = null;
			for (int tries = 0; tries < 5 && cave == null; tries++) {
				BlockPos candidate = Caver.findFrontier(this.drifter, from, CAVE_SEARCH, this.depth, java.util.Set.of(), notCaves);
				if (candidate == null) {
					break;
				}

				if (Caver.isCave(this.drifter, candidate)) {
					cave = candidate;
				} else {
					notCaves.add(candidate);
				}
			}

			if (cave != null) {
				this.caver = new Caver(this.drifter, cave, this.depth, this.caveLegs());
				this.phase = Phase.CAVE;
				this.think("going caving: a cave it saw at " + cave.toShortString());
				return Status.RUNNING;
			}
		}

		Home home = this.drifter.getDrifterBrain().home();
		int targetY = this.depth + Mth.nextInt(this.drifter.getRandom(), -4, 4);
		int startY = home != null ? home.center.getY() : this.drifter.blockPosition().getY();
		if (this.mountain && startY >= MOUNTAIN_ABOVE) {
			targetY = startY - MOUNTAIN_DIG + Mth.nextInt(this.drifter.getRandom(), -4, 4);
		}
		if (home != null && home.mineBottom != null && home.mineDirection != null && home.near(this.drifter, Home.STORAGE_RANGE)
			&& home.mineBottom.getY() - targetY > 12) {
			// A staircase it didn't finish last time: back down it and carry on.
			this.tunnel = new Tunnel(home.mineBottom, home.mineDirection, MAX_STEPS);
			this.tunnel.legs.add(new Tunnel.Leg(home.mineBottom, home.mineDirection, 400, targetY));
			this.think(String.format(Locale.ROOT, "back down its stairs, carrying on toward Y %d for %s", targetY, this.label));
		} else if (home != null && home.mineBottom != null && home.mineDirection != null && home.near(this.drifter, Home.STORAGE_RANGE)
			&& Math.abs(home.mineBottom.getY() - targetY) <= 12) {
			// Its mine: back down the stairs, then branch out a new way (round the compass, trip by trip).
			Direction way = Direction.from2DDataValue(home.mineDirection.get2DDataValue() + home.mineTrips);
			home.mineTrips++;
			this.tunnel = new Tunnel(home.mineBottom, way, MAX_STEPS);
			this.tunnel.branchMine(home.mineBottom, way, MAIN_LENGTH, this.spacing(), BRANCH_LENGTH);
			this.branched = true;
			this.think("back down to its mine, branching " + way.getName());
		} else {
			BlockPos entrance;
			Direction way;
			if (home != null && home.near(this.drifter, Home.STORAGE_RANGE)) {
				// Stairs start just outside the hut's door, heading away from it.
				// A staircase that didn't get down (caves, gravel, water) means trying another side next time.
				Direction door = home.hutDoor != null ? home.hutDoor : Direction.from2DDataValue(this.drifter.getRandom().nextInt(4));
				Direction side = Direction.from2DDataValue(door.get2DDataValue() + home.mineTrips);
				home.mineTrips++;
				entrance = this.ground(home.center.relative(side, StarterHut.HALF + 2));
				way = side;
			} else {
				entrance = this.drifter.blockPosition();
				way = this.drifter.getDirection();
			}

			this.tunnel = new Tunnel(entrance, way, MAX_STEPS);
			this.tunnel.legs.add(new Tunnel.Leg(entrance, way, 400, targetY));
			this.think(String.format(Locale.ROOT, "digging down toward Y %d for %s", targetY, this.label));
		}

		this.digger = new TunnelDigger(this.drifter, this.tunnel);
		this.phase = Phase.DIG;
		return Status.RUNNING;
	}

	private Status dig() {
		// Ore it wants that it has just seen close by (it broke into a cave, or a wall showed it): a player goes and gets
		// it before carrying on, the way most iron is found since caves got big (section 10.3 "Caving").
		if (this.tunnel != null && this.tunnel.steps % 2 == 0) {
			BlockPos spotted = this.rememberedOre(SPOTTED_RANGE);
			if (spotted != null) {
				return this.goFor(spotted, "spotted " + this.label + " at " + spotted.toShortString() + ": going to get it");
			}
		}

		Task step = this.digger.next();
		this.relayThought();
		if (this.enterCave()) {
			return Status.RUNNING;
		}

		if (step != null) {
			return this.begin(step);
		}

		// Out of legs: at the bottom of the stairs for the first time, it starts the branch mine (and home remembers it).
		Tunnel t = this.tunnel;
		int y = this.drifter.blockPosition().getY();
		if (!this.branched && (!t.done && y <= this.depth + 6 || t.done && t.steps < t.maxSteps && y <= this.depth + GOOD_ENOUGH)) {
			// A staircase that had to stop a little short (caves, gravel, water all round) still branches from there: the
			// ore is not only at the one best height (found in testing: cave-riddled ground sent it home empty every trip).
			this.branched = true;
			t.done = false;
			t.turns = 0;
			t.breaches = 0;
			t.leg = null;
			BlockPos bottom = this.drifter.blockPosition();
			Home home = this.drifter.getDrifterBrain().home();
			if (home != null && home.near(this.drifter, Home.STORAGE_RANGE + 64)) {
				home.mineBottom = bottom;
				home.mineDirection = t.direction;
				home.mineTrips = 1;
			}

			t.branchMine(bottom, t.direction, MAIN_LENGTH, this.spacing(), BRANCH_LENGTH);
			this.think("at the bottom (Y " + bottom.getY() + "): branch mining");
			return Status.RUNNING;
		}

		this.why = t.done ? "the tunnel's done" : "dug what it planned";
		this.think("heading back up: " + this.why);
		this.rememberStairs();
		this.phase = Phase.HOME;
		return Status.RUNNING;
	}

	/**
	 * Heading home from a staircase that didn't get all the way down (pickaxes, time): home remembers where it got to,
	 * so the next trip carries on from there instead of starting a new one from the top.
	 */
	private void rememberStairs() {
		Tunnel t = this.tunnel;
		Home home = this.drifter.getDrifterBrain().home();
		if (this.branched || t == null || t.done || home == null || !home.near(this.drifter, Home.STORAGE_RANGE + 64)
			|| t.entrance.getY() - t.tip.getY() < 6) {
			return;
		}

		if (home.mineBottom == null || t.tip.getY() < home.mineBottom.getY()) {
			home.mineBottom = t.tip;
			home.mineDirection = t.direction;
		}
	}

	/** How far above the planned depth a staircase that had to stop still counts as deep enough to branch. */
	private static final int GOOD_ENOUGH = 24;

	/** Blocks between branches: its habit plus one (a 2-block gap with 1-wide branches shows every ore between). */
	private int spacing() {
		return this.drifter.getPersonality().habits().branchSpacing + 1;
	}

	private Status goHome() {
		Home home = this.drifter.getDrifterBrain().home();
		BlockPos target = home != null && home.near(this.drifter, Home.STORAGE_RANGE + 64) ? home.center
			: this.tunnel != null ? this.tunnel.entrance : null;
		if (target == null || this.drifter.blockPosition().distSqr(target) <= 3 * 3) {
			// Back: whatever it found counts (the planner sends it again if it needs more).
			return this.have() > 0 || this.digger != null && this.digger.oresMined() > 0 ? Status.SUCCESS
				: this.fail("came back without any " + this.label + (this.why == null ? "" : " (" + this.why + ")"));
		}

		if (this.failures() > 4) {
			return this.fail("can't find the way back up");
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(target), 2.0, "head back up"));
	}

	/** How close ore it spots while digging must be for it to go and get it. */
	private static final double SPOTTED_RANGE = 16.0;
	/** Ore it already went for on this trip (got it, or couldn't get there). */
	private final java.util.Set<BlockPos> wentFor = new java.util.HashSet<>();

	/** Goes and gathers the ore it saw there (and the rest of the vein), then back to what it was doing. */
	private Status goFor(BlockPos ore, String thought) {
		this.wentFor.add(ore);
		this.phase = Phase.REMEMBERED;
		this.think(thought);
		return this.begin(new GatherActivity(this.drifter, this.label, this.blocks, stack -> stack.is(this.item), this.total, false, false) {
			private boolean walked;

			@Override
			protected Status next() {
				if (!this.walked && this.drifter.blockPosition().distSqr(ore) > 6 * 6) {
					this.walked = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(ore), 4.0, "walk to the " + MineTripActivity.this.label));
				}

				return super.next();
			}
		});
	}

	/** The nearest ore of this kind it remembers seeing within a range (and hasn't gone for yet), or null. */
	@Nullable
	private BlockPos rememberedOre(double range) {
		ResourceLocation dimension = this.drifter.level().dimension().location();
		MemoryEntry entry = this.drifter.getMemory().nearest(MemoryKind.ORE, dimension, this.drifter.position(), range, e -> {
			if (this.wentFor.contains(e.blockPos()) || KnownBlocks.underWater(this.drifter.level(), e.blockPos())) {
				return false;
			}

			ResourceLocation id = ResourceLocation.tryParse(e.label);
			return id != null && BuiltInRegistries.BLOCK.containsKey(id) && this.blocks.test(BuiltInRegistries.BLOCK.get(id).defaultBlockState());
		});
		return entry == null ? null : entry.blockPos();
	}

	/** Standable ground at this x, z near this height (a slope in front of the hut), or the spot itself. */
	private BlockPos ground(BlockPos around) {
		for (int dy = 3; dy >= -4; dy--) {
			BlockPos spot = around.above(dy);
			if (com.dinglebob.drifter.brain.KnownBlocks.isStandable(this.drifter.level(), spot)) {
				return spot;
			}
		}

		return around;
	}
}
