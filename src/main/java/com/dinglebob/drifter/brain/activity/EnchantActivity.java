package com.dinglebob.drifter.brain.activity;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.EnchantTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The enchanting corner (section 10.13; milestone 8 step 7): places its enchanting table near home with room round it
 * for bookshelves, then enchants its best gear there one piece at a time, taking the strongest offer it can afford.
 */
public class EnchantActivity extends Activity {
	/** What it enchants, most wanted first (section 10.13: pickaxe, sword, armor). */
	public static final List<Item> PRIORITY = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
		Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS, Items.IRON_PICKAXE, Items.IRON_SWORD, Items.BOW);
	/** How far from home the table goes. */
	private static final int[] DISTANCES = {5, 6, 7, 8, 10};

	@Nullable
	private BlockPos placing;
	private int enchanted;

	public EnchantActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "enchant its gear";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 3;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		if (step instanceof EnchantTask enchant && status == Status.SUCCESS) {
			this.enchanted++;
			this.think(enchant.result());
		}

		if (step instanceof PlaceBlockTask && status == Status.SUCCESS && this.placing != null) {
			Home home = this.drifter.getDrifterBrain().home();
			if (home != null) {
				home.enchantingTable = this.placing;
				this.think("set up its enchanting table at " + this.placing.toShortString());
			}
		}
	}

	@Override
	protected Status next() {
		Level level = this.drifter.level();
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null) {
			return this.fail("has no home for an enchanting table");
		}

		BlockPos table = home.enchantingTable(level);
		if (table == null) {
			if (this.drifter.getInventory().count(stack -> stack.is(Items.ENCHANTING_TABLE)) == 0) {
				return this.fail("has no enchanting table");
			}

			if (this.placing == null) {
				this.placing = librarySpot(this.drifter, home);
				if (this.placing == null) {
					return this.fail("can't find a flat spot near home for the enchanting table");
				}
			}

			return this.reachThen(this.placing, () -> new PlaceBlockTask(this.drifter, this.placing, stack -> stack.is(Items.ENCHANTING_TABLE),
				"the enchanting table"));
		}

		Item next = nextToEnchant(this.drifter);
		if (next == null || this.enchanted >= 3 || this.drifter.getInventory().count(stack -> stack.is(Items.LAPIS_LAZULI)) == 0
			|| this.drifter.experienceLevel() < 1) {
			return this.enchanted > 0 || next == null ? Status.SUCCESS : this.fail("no lapis or levels to enchant with");
		}

		Predicate<ItemStack> unenchanted = stack -> stack.is(next) && !stack.isEnchanted();
		return this.reachThen(table, () -> new EnchantTask(this.drifter, table, unenchanted, new ItemStack(next).getHoverName().getString().toLowerCase(java.util.Locale.ROOT)));
	}

	/** The first item on its list it carries without enchantments, or null. */
	@Nullable
	public static Item nextToEnchant(DrifterEntity drifter) {
		for (Item item : PRIORITY) {
			if (drifter.getInventory().findSlot(stack -> stack.is(item) && !stack.isEnchanted(), 0, DrifterInventory.MAIN_SIZE) >= 0) {
				return item;
			}
		}

		return null;
	}

	/** Bookshelves set up round the table (they count only with the gap between clear, as the game counts them). */
	public static int bookshelves(Level level, BlockPos table) {
		int count = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Levels worth waiting for before enchanting: the most the bottom offer can cost with its bookshelves (8 with none,
	 * 30 with all 15; EnchantmentHelper#getEnchantmentCost).
	 */
	public static int levelsWanted(int shelves) {
		return Math.min(30, Math.max(8 + shelves / 2 + shelves, shelves * 2));
	}

	/**
	 * A spot for the table near home: flat, open ground 5x5 round it (room for the ring of bookshelves later), clear of
	 * the hut, the farm and the pen.
	 */
	@Nullable
	public static BlockPos librarySpot(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		for (int distance : DISTANCES) {
			for (int turn = 0; turn < 8; turn++) {
				double angle = Math.PI / 4.0 * turn;
				BlockPos around = home.center.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos spot = around.above(dy);
					if (flatAndClear(drifter, level, home, spot)) {
						return spot.immutable();
					}
				}
			}
		}

		return null;
	}

	private static boolean flatAndClear(DrifterEntity drifter, Level level, Home home, BlockPos spot) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos cell = spot.offset(dx, 0, dz);
				if (!KnownBlocks.isStandable(level, cell) || !drifter.getSenses().terrain().isKnown(cell.below()) || nearOther(home, cell)) {
					return false;
				}
			}
		}

		return true;
	}

	/** In the hut, the farm or the pen (with a block of margin). */
	private static boolean nearOther(Home home, BlockPos cell) {
		if (within(home.center, cell, 3)) {
			return true;
		}

		if (home.farm != null && within(home.farm, cell, FarmActivity.RADIUS + 1)) {
			return true;
		}

		return home.pen != null && within(home.pen, cell, 4);
	}

	private static boolean within(BlockPos middle, BlockPos cell, int radius) {
		return Math.abs(cell.getX() - middle.getX()) <= radius && Math.abs(cell.getZ() - middle.getZ()) <= radius
			&& Math.abs(cell.getY() - middle.getY()) <= 3;
	}

	private Status reachThen(BlockPos pos, java.util.function.Supplier<Task> step) {
		// An empty spot (where the table goes) counts as in reach when it's close enough: the placing finds a face to click.
		boolean empty = this.drifter.level().getBlockState(pos).canBeReplaced();
		if (empty ? this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= this.drifter.blockInteractionRange() - 0.5
			: Reach.canReach(this.drifter, pos)) {
			return this.begin(step.get());
		}

		if (this.failures() > 3) {
			return this.fail("can't get to " + pos.toShortString());
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of());
		if (spot == null) {
			// An empty spot (where the table goes): just next to it.
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(pos), 2.0, "walk over to where the enchanting table goes"));
		}

		return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to the enchanting table"));
	}
}
