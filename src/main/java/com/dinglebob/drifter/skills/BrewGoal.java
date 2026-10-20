package com.dinglebob.drifter.skills;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Stations;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.CraftActivity;
import com.dinglebob.drifter.brain.activity.PlaceStationActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.FillBottlesTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.hands.Containers;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.perception.TerrainKnowledge;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Brewing (DESIGN.md section 10.14, Knowledge-gated): at home, with its brewing stand, nether wart, blaze powder for the
 * fuel and bottles, it brews what it's short of: fire resistance (magma cream) once it goes to the Nether, healing
 * (glistering melon) and strength (blaze powder) for fights, night vision (golden carrot) for caving. As a player does it
 * in the stand's own slots: three water bottles filled at the water, nether wart for awkward potions, then the
 * ingredient; and the skilled add gunpowder to healing potions to throw them (splash).
 */
public final class BrewGoal extends Goals.Goal {
	/** Knows how to brew from this much Knowledge. */
	public static final double KNOWLEDGE = 0.4;
	/** Throws healing potions (splash) from this much Skill (section 10.14). */
	public static final double SPLASH_SKILL = 0.7;
	/** How far from the stand it goes for water. */
	private static final int WATER_RANGE = 24;
	/** One brew: the stand's 20 seconds, with time to spare. */
	private static final int BREW_WAIT = 20 * 30;
	private static final long CHECK_EVERY = 20 * 5;
	/** Its stand goes down within this of the middle of home. */
	private static final int HOME_NEAR = 6;

	@Nullable
	private Potions.Kind kind;
	private long checkedAt = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return "brew";
	}

	@Override
	public String name(Goals.Context context) {
		return this.kind == null ? "brew potions" : "brew potions of " + this.kind.label;
	}

	/** It knows how to brew. */
	public static boolean brews(DrifterEntity drifter) {
		return drifter.getPersonality().get(Trait.KNOWLEDGE) >= KNOWLEDGE;
	}

	/** How many of each it likes to carry. */
	private static int keep(DrifterEntity drifter, Potions.Kind kind) {
		return switch (kind) {
			case FIRE_RESISTANCE, HEALING -> 2;
			case STRENGTH -> 1;
			case NIGHT_VISION -> drifter.getPersonality().archetype() == Archetype.MINER ? 2 : 1;
		};
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		if (!brews(drifter) || context.threat().urgent() || drifter.inCombat() || com.dinglebob.drifter.world.Battle.inBattle(drifter)
			|| drifter.level().dimension() != Level.OVERWORLD) {
			return 0.0;
		}

		Home home = drifter.getDrifterBrain().home();
		if (home == null || !home.near(drifter, Stations.HOME_RANGE)) {
			return 0.0;
		}

		long now = drifter.level().getGameTime();
		if (now - this.checkedAt >= CHECK_EVERY) {
			this.checkedAt = now;
			this.kind = wanted(drifter);
		}

		return this.kind == null ? 0.0 : 0.25 + 0.15 * drifter.getPersonality().get(Trait.KNOWLEDGE);
	}

	/** The first potion it's short of that it has (or can make) the ingredient for, if it has the rest of the kit. */
	@Nullable
	static Potions.Kind wanted(DrifterEntity drifter) {
		return bottles(drifter) == 0 ? null : wantedWithBottles(drifter);
	}

	/** Bottles to brew in: empty, or water in them already. */
	private static int bottles(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.GLASS_BOTTLE) || Potions.isWater(stack) || Potions.isAwkward(stack));
	}

	/** It would brew something if it had bottles (for its progression: when to make more of them). */
	public static boolean wantsBottles(DrifterEntity drifter) {
		return brews(drifter) && bottles(drifter) < 3 && wantedWithBottles(drifter) != null;
	}

	@Nullable
	private static Potions.Kind wantedWithBottles(DrifterEntity drifter) {
		var inventory = drifter.getInventory();
		boolean stand = inventory.count(stack -> stack.is(Items.BREWING_STAND)) > 0 || Stations.usable(drifter, Blocks.BREWING_STAND) != null;
		if (!stand || inventory.count(stack -> stack.is(Items.NETHER_WART)) == 0 || powder(drifter) < 1) {
			return null;
		}

		Home home = drifter.getDrifterBrain().home();
		boolean nether = home != null && home.portal != null;
		for (Potions.Kind kind : Potions.Kind.values()) {
			if (kind == Potions.Kind.FIRE_RESISTANCE && !nether) {
				continue;
			}

			if (Potions.count(drifter, kind) < keep(drifter, kind) && canGetIngredient(drifter, kind)) {
				return kind;
			}
		}

		return null;
	}

	/** Blaze powder it has or can make (a rod makes two). */
	private static int powder(DrifterEntity drifter) {
		return count(drifter, Items.BLAZE_POWDER) + 2 * count(drifter, Items.BLAZE_ROD);
	}

	private static int count(DrifterEntity drifter, Item item) {
		return drifter.getInventory().count(stack -> stack.is(item));
	}

	/** Gold for eight nuggets (a glistering melon slice, a golden carrot). */
	private static boolean gold(DrifterEntity drifter) {
		return count(drifter, Items.GOLD_NUGGET) >= 8 || count(drifter, Items.GOLD_INGOT) > 0;
	}

	private static boolean canGetIngredient(DrifterEntity drifter, Potions.Kind kind) {
		if (count(drifter, kind.ingredient) > 0 && kind != Potions.Kind.STRENGTH) {
			return true;
		}

		return switch (kind) {
			// The fuel and the magma cream (slime and powder) both take powder.
			case FIRE_RESISTANCE -> count(drifter, Items.SLIME_BALL) > 0 && powder(drifter) >= 2;
			case HEALING -> count(drifter, Items.MELON_SLICE) > 0 && gold(drifter);
			case STRENGTH -> powder(drifter) >= 2;
			case NIGHT_VISION -> count(drifter, Items.CARROT) > 0 && gold(drifter);
		};
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Potions.Kind kind = this.kind != null ? this.kind : wanted(drifter);
		return kind == null ? null : new Brew(drifter, kind);
	}

	/** Brewing one batch: the stand, the fuel, the ingredient, the water, then the stand's slots. */
	private static final class Brew extends Activity {
		private final Potions.Kind kind;
		private final boolean splash;
		private boolean placedStand;
		private boolean walkedHome;
		private boolean madePowder;
		private boolean madeNuggets;
		private boolean madeIngredient;
		private boolean filled;
		private boolean walked;
		private boolean cleared;
		private int stage;
		private int bottles;

		Brew(DrifterEntity drifter, Potions.Kind kind) {
			super(drifter);
			this.kind = kind;
			this.splash = kind == Potions.Kind.HEALING && drifter.getPersonality().get(Trait.SKILL) >= SPLASH_SKILL
				&& drifter.getInventory().count(stack -> stack.is(Items.GUNPOWDER)) > 0;
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "going to brew potions of " + kind.label);
		}

		@Override
		public String describe() {
			return "brew potions of " + this.kind.label;
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 4;
		}

		@Nullable
		private BlockPos stand() {
			return Stations.usable(this.drifter, Blocks.BREWING_STAND);
		}

		@Nullable
		private Container standContainer(BlockPos stand) {
			return this.drifter.level().getBlockState(stand).is(Blocks.BREWING_STAND) ? Containers.get(this.drifter.level(), stand) : null;
		}

		@Override
		protected Status next() {
			BlockPos stand = this.stand();
			if (stand == null) {
				if (this.placedStand || count(this.drifter, Items.BREWING_STAND) == 0) {
					return this.fail("no brewing stand");
				}

				// Set up at home, where it brews (not wherever it happens to be).
				Home home = this.drifter.getDrifterBrain().home();
				if (home != null && !this.walkedHome && this.drifter.blockPosition().distSqr(home.center) > HOME_NEAR * HOME_NEAR) {
					this.walkedHome = true;
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(home.center), 3.0, "walk home"));
				}

				this.placedStand = true;
				return this.begin(new PlaceStationActivity(this.drifter, Items.BREWING_STAND));
			}

			if (this.stage == 0) {
				Status ready = this.prepare(stand);
				if (ready != null) {
					return ready;
				}

				this.stage = 1;
			}

			if (!Reach.canReach(this.drifter, stand)) {
				if (this.walked) {
					return this.fail("can't get to the brewing stand");
				}

				this.walked = true;
				return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(stand), 2.5, "walk over to the brewing stand"));
			}

			Container container = this.standContainer(stand);
			if (container == null) {
				return this.fail("the brewing stand is gone");
			}

			// Whatever was left in the stand (an old brew, a stray ingredient) out first.
			if (this.stage == 1 && !this.cleared && (!container.getItem(0).isEmpty() || !container.getItem(1).isEmpty() || !container.getItem(2).isEmpty()
				|| !container.getItem(3).isEmpty())) {
				this.cleared = true;
				return this.begin(new ContainerTask(this.drifter, stand, ContainerTask.Mode.TAKE, stack -> true, "what's left in it", 4, new int[] {0, 1, 2, 3}));
			}

			return switch (this.stage) {
				case 1 -> {
					// Bottles in (one in each of the three places), and blaze powder in the fuel place if it's empty.
					this.stage = container.getItem(4).isEmpty() ? 2 : 3;
					yield this.begin(new ContainerTask(this.drifter, stand, ContainerTask.Mode.PUT, Potions::isWater, "water bottles", this.bottles,
						new int[] {0, 1, 2}));
				}
				case 2 -> {
					this.stage = 3;
					yield this.begin(new ContainerTask(this.drifter, stand, ContainerTask.Mode.PUT, stack -> stack.is(Items.BLAZE_POWDER), "blaze powder", 1,
						new int[] {4}));
				}
				case 3 -> {
					this.stage = 4;
					yield this.brewWith(stand, Items.NETHER_WART, "nether wart");
				}
				case 4 -> {
					this.stage = this.splash ? 5 : 6;
					yield this.brewWith(stand, this.kind.ingredient, this.kind.ingredient.getDescription().getString().toLowerCase(java.util.Locale.ROOT));
				}
				case 5 -> {
					this.stage = 6;
					yield this.brewWith(stand, Items.GUNPOWDER, "gunpowder");
				}
				case 6 -> {
					this.stage = 7;
					yield this.begin(new ContainerTask(this.drifter, stand, ContainerTask.Mode.TAKE, Potions::isAnyPotion, "the potions", 3, new int[] {0, 1, 2}));
				}
				default -> {
					int made = Potions.count(this.drifter, this.kind);
					this.think("brewed potions of " + this.kind.label + " (has " + made + ")");
					yield Status.SUCCESS;
				}
			};
		}

		/** The ingredient into its place, and a wait while the stand brews it in (the ingredient is used up at the end). */
		private Status brewWith(BlockPos stand, Item ingredient, String label) {
			return this.begin(new com.dinglebob.drifter.executor.task.SequenceTask(this.drifter, "brew in " + label, List.of(
				new ContainerTask(this.drifter, stand, ContainerTask.Mode.PUT, stack -> stack.is(ingredient), label, 1, new int[] {3}),
				new WaitTask(this.drifter, "the brewing stand to finish", () -> {
					Container container = this.standContainer(stand);
					return container == null || container.getItem(3).isEmpty();
				}, BREW_WAIT))));
		}

		/** Powder, the ingredient, and water in the bottles; null once it has all it needs. */
		@Nullable
		private Status prepare(BlockPos stand) {
			// Powder: one for the fuel if the stand has none, one for strength or for magma cream.
			int powderNeeded = (this.standContainer(stand) != null && this.standContainer(stand).getItem(4).isEmpty() ? 1 : 0)
				+ (this.kind == Potions.Kind.STRENGTH || this.kind == Potions.Kind.FIRE_RESISTANCE && count(this.drifter, Items.MAGMA_CREAM) == 0 ? 1 : 0);
			if (count(this.drifter, Items.BLAZE_POWDER) < powderNeeded) {
				if (this.madePowder || count(this.drifter, Items.BLAZE_ROD) == 0) {
					return this.fail("no blaze powder");
				}

				this.madePowder = true;
				return this.begin(new CraftActivity(this.drifter, Items.BLAZE_POWDER, 1));
			}

			if (count(this.drifter, this.kind.ingredient) == 0) {
				boolean nuggets = this.kind == Potions.Kind.HEALING || this.kind == Potions.Kind.NIGHT_VISION;
				if (nuggets && count(this.drifter, Items.GOLD_NUGGET) < 8) {
					if (this.madeNuggets) {
						return this.fail("no gold nuggets");
					}

					this.madeNuggets = true;
					return this.begin(new CraftActivity(this.drifter, Items.GOLD_NUGGET, 1));
				}

				if (this.madeIngredient) {
					return this.fail("couldn't make the " + this.kind.ingredient.getDescription().getString());
				}

				this.madeIngredient = true;
				return this.begin(new CraftActivity(this.drifter, this.kind.ingredient, 1));
			}

			// Three bottles of water (or as many as it has bottles for).
			int water = this.drifter.getInventory().count(Potions::isWater);
			int empty = count(this.drifter, Items.GLASS_BOTTLE);
			this.bottles = Math.min(3, water + empty);
			if (water < this.bottles && !this.filled) {
				this.filled = true;
				BlockPos[] spot = findWater(this.drifter, stand);
				if (spot == null) {
					if (water == 0) {
						return this.fail("no water near to fill the bottles");
					}

					this.bottles = water;
					return null;
				}

				return this.begin(new com.dinglebob.drifter.executor.task.SequenceTask(this.drifter, "fill the bottles", List.of(
					new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot[0]), 0.8, "walk to the water"),
					new FillBottlesTask(this.drifter, spot[1], this.bottles - water))));
			}

			this.bottles = Math.min(3, this.drifter.getInventory().count(Potions::isWater));
			return this.bottles == 0 ? this.fail("no water bottles") : null;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			// (Filling the bottles can come up short: it brews what it has.)
			return !(step instanceof com.dinglebob.drifter.executor.task.SequenceTask sequence && sequence.describe().equals("fill the bottles"));
		}
	}

	/** Water it knows near the stand (a source block open above), and dry ground beside it to stand on: {stand, water}. */
	@Nullable
	static BlockPos[] findWater(DrifterEntity drifter, BlockPos near) {
		Level level = drifter.level();
		BlockPos[] best = {null, null};
		double[] bestDistance = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnownState(level, near, WATER_RANGE,
			TerrainKnowledge.SectionFilter.palette(level, state -> state.getFluidState().is(FluidTags.WATER)),
			state -> !state.getFluidState().is(FluidTags.WATER) || !state.getFluidState().isSource(),
			(pos, state) -> {
				double distance = pos.distSqr(near);
				if (distance >= bestDistance[0] || !level.getBlockState(pos.above()).isAir()) {
					return;
				}

				for (Direction side : Direction.Plane.HORIZONTAL) {
					for (int dy = 0; dy <= 1; dy++) {
						BlockPos stand = pos.relative(side).above(dy);
						if (KnownBlocks.isStandable(level, stand) && level.getFluidState(stand).isEmpty() && level.getFluidState(stand.below()).isEmpty()
							&& drifter.getSenses().terrain().isKnown(stand.below())) {
							bestDistance[0] = distance;
							best[0] = stand.immutable();
							best[1] = pos.immutable();
							return;
						}
					}
				}
			});
		return best[0] == null ? null : best;
	}
}
