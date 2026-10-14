package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.KnownWorld;
import com.dinglebob.drifter.brain.Planner;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.executor.task.UseOnEntityTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The animal pen (DESIGN.md section 9 stage 6 "Settlement", section 10.12; milestone 8 step 5): a fenced 5x5 near home
 * with a gate facing home. Like a player it leads two animals of a kind in by holding their food (they follow a Drifter's
 * food like a player's, see world/AnimalTempting), puts the food away, and breeds them; then it steps out and shuts the
 * gate.
 */
public class PenActivity extends Activity {
	/** Inside reaches this far from the middle (5x5); the fence is one further out. */
	public static final int INSIDE = 2;
	private static final int[] DISTANCES = {11, 14, 17};
	/** How far it goes to fetch animals it has seen. */
	private static final double LURE_RANGE = 40.0;
	/** Animals it keeps in the pen at most before it stops breeding. */
	public static final int ENOUGH = 6;
	private static final int MAX_STEPS = 80;

	private int steps;
	private int lureTrips;
	private boolean leading;
	private final Set<BlockPos> failedCells = new java.util.HashSet<>();
	private final Set<Integer> fed = new java.util.HashSet<>();

	public PenActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return this.leading ? "lead animals to the pen" : "work on the animal pen";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 6;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return false;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof PlaceBlockTask place && status == Status.FAILURE) {
			this.failedCells.add(place.target());
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("has no home");
		}

		if (++this.steps > MAX_STEPS) {
			return Status.SUCCESS;
		}

		if (home.pen == null) {
			BlockPos spot = chooseSpot(this.drifter, home);
			if (spot == null) {
				home.penFailedAt = this.drifter.level().getGameTime();
				return this.fail("no flat ground near home for a pen");
			}

			home.pen = spot;
			this.think("making an animal pen at " + spot.toShortString());
		}

		BlockPos pen = home.pen;
		BlockPos gate = gate(home);
		// 1. The fence (it crafts what it needs from its wood).
		List<BlockPos> missing = new ArrayList<>();
		for (BlockPos cell : ring(pen)) {
			if (!cell.equals(gate) && !isFence(level.getBlockState(cell)) && !this.failedCells.contains(cell)) {
				missing.add(cell);
			}
		}

		if (!missing.isEmpty()) {
			int fences = this.drifter.getInventory().count(stack -> stack.is(ItemTags.FENCES));
			if (fences == 0) {
				return this.planFor(fenceItems(), Math.min(missing.size(), 24), "fences");
			}

			BlockPos cell = this.nearest(missing);
			return this.placeAt(cell, stack -> stack.is(ItemTags.FENCES) && !(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
				&& bi.getBlock() instanceof FenceGateBlock), "a fence");
		}

		// 2. The gate.
		if (!(level.getBlockState(gate).getBlock() instanceof FenceGateBlock) && !this.failedCells.contains(gate)) {
			if (this.drifter.getInventory().count(stack -> stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
				&& bi.getBlock() instanceof FenceGateBlock) == 0) {
				return this.planFor(gateItems(), 1, "a fence gate");
			}

			return this.placeAt(gate, stack -> stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
				&& bi.getBlock() instanceof FenceGateBlock, "a fence gate");
		}

		// 3. Animals: two of a kind inside, then breed them.
		List<Animal> inside = animalsInside(level, pen);
		Animal[] pair = pairToBreed(inside);
		if (pair != null) {
			Item food = foodFor(pair[0]);
			if (food != null && this.drifter.getInventory().count(stack -> stack.is(food)) >= 2) {
				for (Animal animal : pair) {
					if (!this.fed.contains(animal.getId()) && animal.canFallInLove()) {
						this.fed.add(animal.getId());
						return this.feed(animal, food);
					}
				}

				this.think("bred the " + pair[0].getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT) + "s in the pen");
				home.penBredAt = this.drifter.level().getGameTime();
			}
		}

		if (inside.size() < 2 && this.lureTrips < 3) {
			return this.lure(level, home, pen, gate);
		}

		// 4. Out, and the gate shut behind it.
		return this.leave(level, pen, gate);
	}

	private Status lure(Level level, Home home, BlockPos pen, BlockPos gate) {
		Vec3 middle = Vec3.atBottomCenterOf(pen);
		if (this.leading) {
			// Back in the pen with whatever follows; then the food goes away so they stop following.
			if (this.drifter.position().distanceTo(middle) > 1.5) {
				return this.begin(new NavigateTask(this.drifter, middle, 1.0, "lead the animals into the pen", NavigateTask.Pace.STROLL));
			}

			this.leading = false;
			this.lureTrips++;
			return this.begin(new WaitTask(this.drifter, "the animals to come in", () -> false, 40));
		}

		for (Item food : List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO)) {
			if (this.drifter.getInventory().count(stack -> stack.is(food)) == 0) {
				continue;
			}

			MemoryEntry animal = this.drifter.getMemory().nearest(MemoryKind.MOB, level.dimension().location(), this.drifter.position(), LURE_RANGE,
				e -> eats(e.label, food) && (e.detail == null || !e.detail.contains("baby")) && !insidePen(e.pos, pen));
			if (animal == null) {
				continue;
			}

			this.leading = true;
			this.think("leading animals to the pen with " + food.getDescription().getString().toLowerCase(java.util.Locale.ROOT));
			return this.begin(new com.dinglebob.drifter.executor.task.SequenceTask(this.drifter, "go get the animals", List.of(
				new SelectItemTask(this.drifter, stack -> stack.is(food), food.getDescription().getString()),
				new NavigateTask(this.drifter, animal.pos, 3.0, "walk to the animals", NavigateTask.Pace.STROLL))));
		}

		this.lureTrips = 99;
		return Status.RUNNING;
	}

	private Status feed(Animal animal, Item food) {
		if (animal.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > 2.8 * 2.8) {
			return this.begin(new NavigateTask(this.drifter, animal.position(), 1.5, "walk to the animal", NavigateTask.Pace.STROLL));
		}

		return this.begin(new com.dinglebob.drifter.executor.task.SequenceTask(this.drifter, "feed the animal", List.of(
			new SelectItemTask(this.drifter, stack -> stack.is(food), food.getDescription().getString()),
			new UseOnEntityTask(this.drifter, animal, "feed the " + animal.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT)))));
	}

	private Status leave(Level level, BlockPos pen, BlockPos gate) {
		BlockState state = level.getBlockState(gate);
		if (!(state.getBlock() instanceof FenceGateBlock)) {
			return Status.SUCCESS;
		}

		// Food away first, so nothing follows it out.
		if (this.drifter.getMainHandItem().is(Items.WHEAT) || this.drifter.getMainHandItem().is(Items.WHEAT_SEEDS)
			|| this.drifter.getMainHandItem().is(Items.CARROT)) {
			return this.begin(new SelectItemTask(this.drifter, stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.PICKAXES), "something else"));
		}

		Direction out = Direction.getNearest(gate.getX() - pen.getX(), 0, gate.getZ() - pen.getZ());
		BlockPos outside = gate.relative(out);
		if (insidePen(this.drifter.position(), pen) || this.drifter.blockPosition().equals(gate)) {
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(outside), 0.6, "step out of the pen", NavigateTask.Pace.STROLL));
		}

		if (state.getValue(FenceGateBlock.OPEN)) {
			if (!Reach.canReach(this.drifter, gate)) {
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(outside), 0.6, "go to the gate", NavigateTask.Pace.STROLL));
			}

			return this.begin(UseOnBlockTask.useBlock(this.drifter, gate, out, "shut the gate"));
		}

		return Status.SUCCESS;
	}

	private Status placeAt(BlockPos cell, Predicate<ItemStack> item, String label) {
		if (!Reach.canReach(this.drifter, cell.below()) && !Reach.canReach(this.drifter, cell)) {
			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, cell.below(), Set.of(cell));
			if (spot == null || !this.walked.add(cell)) {
				this.failedCells.add(cell);
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to the pen", NavigateTask.Pace.STROLL));
		}

		return this.begin(new PlaceBlockTask(this.drifter, cell, item, label));
	}

	private final Set<BlockPos> walked = new java.util.HashSet<>();

	private Status planFor(List<Item> items, int count, String label) {
		Planner.Plan plan = Planner.plan(this.drifter, KnownWorld.scan(this.drifter), items, count);
		if (!plan.feasible() || plan.next() == null) {
			return this.fail("can't work out how to make " + label);
		}

		return this.begin(Goals.activityFor(this.drifter, plan.next()));
	}

	private BlockPos nearest(List<BlockPos> cells) {
		BlockPos best = cells.get(0);
		for (BlockPos cell : cells) {
			if (cell.distToCenterSqr(this.drifter.position()) < best.distToCenterSqr(this.drifter.position())) {
				best = cell;
			}
		}

		return best;
	}

	// ---------------------------------------------------------------------------------------------------------------

	private static List<Item> fenceItems() {
		return List.of(Items.OAK_FENCE, Items.SPRUCE_FENCE, Items.BIRCH_FENCE, Items.JUNGLE_FENCE, Items.ACACIA_FENCE, Items.DARK_OAK_FENCE,
			Items.MANGROVE_FENCE, Items.CHERRY_FENCE, Items.BAMBOO_FENCE);
	}

	private static List<Item> gateItems() {
		return List.of(Items.OAK_FENCE_GATE, Items.SPRUCE_FENCE_GATE, Items.BIRCH_FENCE_GATE, Items.JUNGLE_FENCE_GATE, Items.ACACIA_FENCE_GATE,
			Items.DARK_OAK_FENCE_GATE, Items.MANGROVE_FENCE_GATE, Items.CHERRY_FENCE_GATE, Items.BAMBOO_FENCE_GATE);
	}

	private static boolean isFence(BlockState state) {
		return state.is(BlockTags.FENCES) || state.getBlock() instanceof FenceGateBlock;
	}

	/** The fence ring around the pen's middle (at the level it stands on). */
	public static List<BlockPos> ring(BlockPos pen) {
		List<BlockPos> cells = new ArrayList<>();
		int r = INSIDE + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
					cells.add(pen.offset(dx, 0, dz));
				}
			}
		}

		return cells;
	}

	/** The gate: the middle of the side facing home. */
	public static BlockPos gate(Home home) {
		Direction toHome = Direction.getNearest(home.center.getX() - home.pen.getX(), 0, home.center.getZ() - home.pen.getZ());
		return home.pen.relative(toHome, INSIDE + 1);
	}

	public static boolean insidePen(Vec3 pos, BlockPos pen) {
		return Math.abs(pos.x - (pen.getX() + 0.5)) <= INSIDE + 0.5 && Math.abs(pos.z - (pen.getZ() + 0.5)) <= INSIDE + 0.5
			&& Math.abs(pos.y - pen.getY()) <= 2;
	}

	public static List<Animal> animalsInside(Level level, BlockPos pen) {
		return level.getEntitiesOfClass(Animal.class, new AABB(pen).inflate(INSIDE + 0.5, 2, INSIDE + 0.5),
			a -> a.isAlive() && (a instanceof Cow || a instanceof Sheep || a instanceof Pig || a instanceof Chicken));
	}

	/** Two adults of a kind that can breed now, or null. */
	@Nullable
	private static Animal[] pairToBreed(List<Animal> animals) {
		if (animals.size() >= ENOUGH) {
			return null;
		}

		for (Animal a : animals) {
			for (Animal b : animals) {
				if (a != b && a.getType() == b.getType() && a.getAge() == 0 && b.getAge() == 0) {
					return new Animal[]{a, b};
				}
			}
		}

		return null;
	}

	@Nullable
	private static Item foodFor(Animal animal) {
		for (Item food : List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT)) {
			if (animal.isFood(new ItemStack(food))) {
				return food;
			}
		}

		return null;
	}

	/** Does an animal of this kind (its entity id) follow this food? */
	private static boolean eats(String id, Item food) {
		return switch (id) {
			case "minecraft:cow", "minecraft:sheep" -> food == Items.WHEAT;
			case "minecraft:pig" -> food == Items.CARROT || food == Items.POTATO;
			case "minecraft:chicken" -> food == Items.WHEAT_SEEDS;
			default -> false;
		};
	}

	/** A flat open 7x7 near home for the pen, away from the farm and the hut. */
	@Nullable
	public static BlockPos chooseSpot(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		BlockPos best = null;
		int bestScore = 0;
		for (int distance : DISTANCES) {
			for (int turn = 0; turn < 8; turn++) {
				double angle = Math.PI / 4.0 * turn + Math.PI / 8.0;
				BlockPos around = home.center.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
				if (home.farm != null && Math.abs(around.getX() - home.farm.getX()) <= FarmActivity.RADIUS + INSIDE + 2
					&& Math.abs(around.getZ() - home.farm.getZ()) <= FarmActivity.RADIUS + INSIDE + 2) {
					continue;
				}

				// Not on its house or its enchanting corner either.
				if (home.inOwnBuilding(around, INSIDE + 2) || home.enchantingTable != null
					&& Math.abs(around.getX() - home.enchantingTable.getX()) <= INSIDE + 4 && Math.abs(around.getZ() - home.enchantingTable.getZ()) <= INSIDE + 4) {
					continue;
				}

				for (int dy = -2; dy <= 2; dy++) {
					BlockPos middle = around.above(dy);
					int score = 0;
					for (int dx = -INSIDE - 1; dx <= INSIDE + 1; dx++) {
						for (int dz = -INSIDE - 1; dz <= INSIDE + 1; dz++) {
							BlockPos cell = middle.offset(dx, 0, dz);
							if (drifter.getSenses().terrain().isKnown(cell.below()) && KnownBlocks.isStandable(level, cell) && level.canSeeSky(cell)) {
								score++;
							}
						}
					}

					if (score > bestScore) {
						bestScore = score;
						best = middle.immutable();
					}
				}
			}

			if (bestScore >= 44) {
				break;
			}
		}

		// (32 of 49 is enough: hillsides rarely give more, and it was turned down 11 times running in testing.)
		return bestScore >= 32 ? best : null;
	}

	/** Anything to do at the pen (for the goal). */
	public static boolean workToDo(DrifterEntity drifter, Home home) {
		if (home.pen == null) {
			return true;
		}

		Level level = drifter.level();
		for (BlockPos cell : ring(home.pen)) {
			if (!isFence(level.getBlockState(cell))) {
				return true;
			}
		}

		List<Animal> inside = animalsInside(level, home.pen);
		if (inside.size() < 2) {
			return true;
		}

		return pairToBreed(inside) != null && level.getGameTime() - home.penBredAt > 20L * 60 * 5;
	}
}
