package com.dinglebob.drifter.brain;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.CollectSmeltActivity;
import com.dinglebob.drifter.brain.activity.CombatActivity;
import com.dinglebob.drifter.brain.activity.CraftActivity;
import com.dinglebob.drifter.brain.activity.FleeActivity;
import com.dinglebob.drifter.brain.activity.EatActivity;
import com.dinglebob.drifter.brain.activity.ExploreActivity;
import com.dinglebob.drifter.brain.activity.GatherActivity;
import com.dinglebob.drifter.brain.activity.HuntActivity;
import com.dinglebob.drifter.brain.activity.PlaceStationActivity;
import com.dinglebob.drifter.brain.activity.ShelterActivity;
import com.dinglebob.drifter.brain.activity.SmeltActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.WearArmorTask;

import net.minecraft.world.entity.LivingEntity;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The goals the selector chooses between for milestone 5 (DESIGN.md section 7, "Goal catalog"): eat, get food, shelter
 * for the night, progress (the strategist's next target), explore, and idle.
 * TODO(milestone 6+): survive/flee, fight, sleep, and the rest of the catalog.
 */
public final class Goals {
	private Goals() {
	}

	/** Everything a goal needs to score itself, computed once per decision. */
	public record Context(DrifterEntity drifter, KnownWorld world, Needs needs, int stage, @Nullable Strategist.Target target, long time,
		Threats.Assessment threat, boolean sealedIn) {
	}

	public abstract static class Goal {
		public abstract String id();

		/** Plain-words name for the overlay and thought log, e.g. "get a stone pickaxe". */
		public abstract String name(Context context);

		public abstract double urgency(Context context);

		/** 0 to 1: can it actually do this now (section 7, "Feasibility")? */
		public abstract double feasibility(Context context);

		/** Lower when the goal is risky (a long trip near dusk); see {@link #lateInTheDay} for the Caution scaling. */
		public double risk(Context context) {
			return 1.0;
		}

		/** Urgent goals switch in at once (section 7, "Interrupt classes"); others wait for the minimum commit time. */
		public boolean urgent(Context context) {
			return false;
		}

		public int minCommitTicks() {
			return 20 * 20;
		}

		/** The activity for this goal right now, or null if there's nothing to do. */
		@Nullable
		public abstract Activity start(Context context);

		/** Plan steps for the overlay. */
		public List<String> steps(Context context) {
			return List.of();
		}

		public double score(Context context) {
			return this.urgency(context) * this.feasibility(context) * this.risk(context);
		}
	}

	/**
	 * The risk factor for starting something late in the day (section 7: "risk lowers the score in proportion to the
	 * Caution trait"): {@code factor} for a middling Drifter, lower for a cautious one, closer to 1 for a reckless one.
	 */
	static double lateInTheDay(Context context, double factor) {
		if (!(context.time() >= Schedule.shelterThinking(context.drifter()) && context.time() < Schedule.NIGHT_END)) {
			return 1.0;
		}

		double caution = context.drifter().getPersonality().get(com.dinglebob.drifter.personality.Trait.CAUTION);
		return net.minecraft.util.Mth.clamp(factor + (0.5 - caution) * 0.6, 0.1, 1.0);
	}

	/** Turns a planner action into the activity that carries it out. */
	public static Activity activityFor(DrifterEntity drifter, Planner.Action action) {
		return switch (action) {
			case Planner.Gather gather -> new GatherActivity(drifter, gather.label(), gather.blocks(), gather.items(), gather.total(), gather.digDown(),
				new ItemStack(gather.item()).is(ItemTags.LOGS));
			case Planner.Hunt hunt -> new HuntActivity(drifter, hunt.prey(), hunt.items(), hunt.total(), hunt.label(), hunt.variant());
			case Planner.Craft craft -> new CraftActivity(drifter, craft.item(), craft.times());
			case Planner.Place place -> new PlaceStationActivity(drifter, place.station());
			case Planner.Smelt smelt -> new SmeltActivity(drifter, smelt.output(), smelt.input(), smelt.inputLabel(), smelt.count(), smelt.fuel(),
				smelt.fuelLabel(), smelt.fuelCount());
			case Planner.Collect collect -> new CollectSmeltActivity(drifter, collect.job());
			case Planner.Explore explore -> new ExploreActivity(drifter, explore.reason(), explore.toward(), explore.found());
			case Planner.Mine mine -> new com.dinglebob.drifter.brain.activity.MineTripActivity(drifter, mine.item(), mine.label(), mine.blocks(), mine.total(),
				mine.depth(), mine.mountain());
			case Planner.Fetch fetch -> new com.dinglebob.drifter.brain.activity.FetchActivity(drifter, fetch.chest(), fetch.item(), fetch.count());
			case Planner.MakeObsidian obsidian -> new com.dinglebob.drifter.brain.activity.ObsidianActivity(drifter, obsidian.total());
		};
	}

	/** A goal that works through a plan, one action at a time, planning again after each. */
	private abstract static class PlannedGoal extends Goal {
		private Planner.Plan plan = Planner.INFEASIBLE;
		private long plannedAt = -1;

		protected abstract List<Item> items(Context context);

		protected abstract int count(Context context);

		protected Planner.Plan plan(Context context) {
			long now = context.drifter().level().getGameTime();
			if (now != this.plannedAt) {
				this.plannedAt = now;
				List<Item> items = this.items(context);
				this.plan = items.isEmpty() ? Planner.INFEASIBLE : Planner.plan(context.drifter(), context.world(), items, this.count(context));
			}

			return this.plan;
		}

		@Override
		public double feasibility(Context context) {
			Planner.Plan plan = this.plan(context);
			if (!plan.feasible() || plan.done()) {
				return 0.0;
			}

			// Waiting on a furnace: do something else meanwhile (it comes back when the batch is done).
			if (plan.next() instanceof Planner.Collect collect && !collect.job().ready(context.drifter().level().getGameTime())) {
				return 0.0;
			}

			// Too late in the day to set off on a mining trip (found in testing: it set off, turned round at once, every tick).
			// Only for setting off: a trip already underground heads home on its own (found in testing: zeroing it then
			// left it wandering deep in a cave, where it walked into lava).
			if (plan.next() instanceof Planner.Mine && com.dinglebob.drifter.brain.activity.MineTripActivity.tooLateToGo(context.drifter())
				&& !com.dinglebob.drifter.brain.mining.Caver.underground(context.drifter(), context.drifter().blockPosition())) {
				return 0.0;
			}

			// Wandering off to look for something is worth less than doing something it knows how to do.
			return plan.next() instanceof Planner.Explore ? 0.6 : 1.0;
		}

		@Override
		public double risk(Context context) {
			// Don't start long jobs late in the day (more so for careful types: section 7, risk scales with Caution).
			return lateInTheDay(context, 0.5);
		}

		@Nullable
		@Override
		public Activity start(Context context) {
			Planner.Action next = this.plan(context).next();
			return next == null ? null : activityFor(context.drifter(), next);
		}

		@Override
		public List<String> steps(Context context) {
			return this.plan(context).actions().stream().map(Planner.Action::describe).toList();
		}
	}

	// ---------------------------------------------------------------------------------------------

	public static final class Eat extends Goal {
		@Override
		public String id() {
			return "eat";
		}

		@Override
		public String name(Context context) {
			return "eat";
		}

		@Override
		public double urgency(Context context) {
			// Hungry, or hurt and able to heal by eating (natural regeneration needs food 18+), or down to its habit's
			// eating point (some top up at 17, others let it run down to 12).
			int food = context.drifter().getFoodData().getFoodLevel();
			double hurt = context.needs().health() > 0.05 && food < 18 ? 0.5 : 0.0;
			double habit = food <= Food.eatThreshold(context.drifter()) ? Food.AT_THRESHOLD_URGENCY : 0.0;
			return Math.max(context.needs().hunger(), Math.max(hurt, habit));
		}

		@Override
		public double feasibility(Context context) {
			return Food.choose(context.drifter()) != null ? 1.0 : 0.0;
		}

		@Override
		public boolean urgent(Context context) {
			return context.drifter().getFoodData().getFoodLevel() <= 6;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new EatActivity(context.drifter());
		}
	}

	/** Hunt and cook: keep enough food on it (section 10.5; stage 3 "2 to 4 animals of food"). Raw chicken isn't on the
	 * "raw meat" list: it makes you sick, so it doesn't eat it (only cooked). */
	public static final class GetFood extends PlannedGoal {
		private static final List<Item> COOKED = List.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN);
		private static final List<Item> RAW = List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON);
		/** A cooked plan dearer than this (seconds) when it's getting hungry: just get raw meat. */
		private static final double TOO_SLOW = 400.0;

		@Override
		public String id() {
			return "food";
		}

		@Override
		public String name(Context context) {
			return "get food";
		}

		@Override
		protected List<Item> items(Context context) {
			return COOKED;
		}

		@Override
		protected int count(Context context) {
			int carried = 0;
			for (Item item : COOKED) {
				carried += context.drifter().getInventory().count(stack -> stack.is(item));
			}

			int missingPoints = Math.max(0, Needs.FOOD_STOCK_TARGET - Food.carriedNutrition(context.drifter()));
			return carried + Math.max(1, Math.min(4, (int)Math.ceil(missingPoints / 6.0)));
		}

		@Override
		protected Planner.Plan plan(Context context) {
			Planner.Plan cooked = super.plan(context);
			if (cooked.feasible() && (cooked.cost() <= TOO_SLOW || context.drifter().getFoodData().getFoodLevel() >= 14)) {
				return cooked;
			}

			Planner.Plan raw = Planner.plan(context.drifter(), context.world(), RAW, 2);
			return raw.feasible() ? raw : cooked;
		}

		@Override
		public double urgency(Context context) {
			return context.needs().foodSupply();
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}
	}

	/**
	 * Cook the raw food it carries (section 10.5: raw meat "only when it can't cook"; user's rule: cook it, ideally before
	 * it gets hungry). One kind at a time, the kind it has most of: loads a furnace (or a smoker it knows) and goes off
	 * to do something else until it's done.
	 */
	public static final class Cook extends PlannedGoal {
		/** Enough raw food to be worth a trip to the furnace before it's hungry. */
		private static final int BATCH = 3;

		@Nullable
		private static Item raw(Context context) {
			java.util.Map<Item, Integer> counts = new java.util.HashMap<>();
			var inventory = context.drifter().getInventory();
			for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (Food.isCookable(context.drifter().level(), stack)) {
					counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}

			return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
		}

		private static int rawCount(Context context) {
			Item raw = raw(context);
			return raw == null ? 0 : context.drifter().getInventory().count(stack -> stack.is(raw));
		}

		@Override
		public String id() {
			return "cook";
		}

		@Override
		public String name(Context context) {
			Item raw = raw(context);
			return raw == null ? "cook food" : "cook " + Planner.name(raw);
		}

		@Override
		protected List<Item> items(Context context) {
			Item raw = raw(context);
			Item cooked = raw == null ? null : Planner.cookedForm(context.drifter().level(), raw);
			return cooked == null ? List.of() : List.of(cooked);
		}

		@Override
		protected int count(Context context) {
			Item raw = raw(context);
			Item cooked = raw == null ? null : Planner.cookedForm(context.drifter().level(), raw);
			if (cooked == null) {
				return 0;
			}

			return context.drifter().getInventory().count(stack -> stack.is(cooked)) + context.drifter().getInventory().count(stack -> stack.is(raw));
		}

		@Override
		public double urgency(Context context) {
			int raw = rawCount(context);
			if (raw == 0) {
				return 0.0;
			}

			// Hungry with nothing it would eat yet: cooking is how it eats.
			int food = context.drifter().getFoodData().getFoodLevel();
			if (food < Food.eatThreshold(context.drifter()) && Food.choose(context.drifter()) == null) {
				return Math.max(0.6, context.needs().hunger() + 0.1);
			}

			// Ahead of time: a batch is worth a short detour to a furnace it knows (a bit above routine progress).
			if (raw >= BATCH) {
				return context.world().furnaceNear || context.world().smokerNear ? 0.55 : 0.4;
			}

			return 0.25;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 20;
		}
	}

	/** Dig in for the night (section 11 shelter tier 1), and climb out in the morning. */
	public static final class TakeShelter extends Goal {
		@Override
		public String id() {
			return "shelter";
		}

		@Override
		public String name(Context context) {
			return Shelter.isSheltered(context.drifter()) && !Schedule.isNight(context.time()) ? "leave the shelter" : "shelter for the night";
		}

		@Override
		public double urgency(Context context) {
			return context.needs().shelter();
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Context context) {
			return context.needs().shelter() >= 0.9;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}

		@Override
		public Activity start(Context context) {
			return new ShelterActivity(context.drifter());
		}
	}

	/** The strategist's next progression target (sections 8 and 9): wooden pickaxe, stone tools, furnace, torches. */
	public static final class Progress extends PlannedGoal {
		@Override
		public String id() {
			return "progress";
		}

		@Override
		public String name(Context context) {
			return context.target() == null ? "progress (nothing left for now)" : "get " + context.target().label();
		}

		@Override
		protected List<Item> items(Context context) {
			return context.target() == null ? List.of() : context.target().items();
		}

		@Override
		protected int count(Context context) {
			return context.target() == null ? 0 : context.target().count();
		}

		@Override
		public double urgency(Context context) {
			return context.needs().gear();
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}
	}

	/** Curiosity: look around and wander toward ground it hasn't seen (section 10.8). */
	public static final class Wander extends Goal {
		@Override
		public String id() {
			return "explore";
		}

		@Override
		public String name(Context context) {
			return "explore";
		}

		@Override
		public double urgency(Context context) {
			// Not deep underground: a player doesn't wander about a dark cave for the sake of it (found in testing: it
			// wandered onto unseen ground in a cave and into lava).
			if (com.dinglebob.drifter.brain.mining.Caver.underground(context.drifter(), context.drifter().blockPosition())
				&& context.drifter().getY() < context.drifter().level().getSeaLevel() - 8) {
				return 0.0;
			}

			return context.needs().curiosity();
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.3);
		}

		@Override
		public Activity start(Context context) {
			return new ExploreActivity(context.drifter(), "wandering", null, null);
		}
	}

	/** Nothing better to do: stand and look around. */
	public static final class Idle extends Goal {
		@Override
		public String id() {
			return "idle";
		}

		@Override
		public String name(Context context) {
			return "idle";
		}

		@Override
		public double urgency(Context context) {
			return 0.05;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new Activity(context.drifter()) {
				private boolean looked;

				@Override
				public String describe() {
					return "idle";
				}

				@Override
				protected Status next() {
					if (this.looked) {
						return Status.SUCCESS;
					}

					this.looked = true;
					return this.begin(new LookAroundTask(this.drifter));
				}
			};
		}
	}

	/** Go back for a batch that should be done cooking (section 10.4). */
	public static final class CollectSmelting extends Goal {
		@Nullable
		private FurnaceJob ready(Context context) {
			long now = context.drifter().level().getGameTime();
			for (FurnaceJob job : context.drifter().getDrifterBrain().furnaceJobs()) {
				if (job.ready(now)) {
					return job;
				}
			}

			return null;
		}

		@Override
		public String id() {
			return "collect";
		}

		@Override
		public String name(Context context) {
			return "collect from the furnace";
		}

		@Override
		public double urgency(Context context) {
			FurnaceJob job = this.ready(context);
			if (job == null) {
				return 0.0;
			}

			// Its meal is in there and it's hungry with nothing else it would eat: go get it.
			if (new ItemStack(job.output).has(net.minecraft.core.component.DataComponents.FOOD)
				&& context.drifter().getFoodData().getFoodLevel() < Food.eatThreshold(context.drifter()) && Food.choose(context.drifter()) == null) {
				return Math.max(0.65, context.needs().hunger() + 0.15);
			}

			return 0.55;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return context.time() >= Schedule.shelterUrgent(context.drifter()) && context.time() < Schedule.NIGHT_END ? 0.3 : 1.0;
		}

		@Nullable
		@Override
		public Activity start(Context context) {
			FurnaceJob job = this.ready(context);
			return job == null ? null : new CollectSmeltActivity(context.drifter(), job);
		}
	}

	/** Fight hostiles it can beat (section 13 ratio above 1.2, or cornered). */
	public static final class Fight extends Goal {
		@Override
		public String id() {
			return "fight";
		}

		@Override
		public String name(Context context) {
			LivingEntity main = context.threat().main();
			return main == null ? "fight" : "fight the " + main.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		}

		@Override
		public double urgency(Context context) {
			Threats.Assessment threat = context.threat();
			// Sheltered, it lets them howl outside; only something that got in and hit it gets a fight.
			if (context.sealedIn() && !threat.recentlyHit() || !threat.decision().fights()) {
				return 0.0;
			}

			// Above everything else, shelter included (its 0.95 plus commitment): a mob on it comes first.
			if (threat.urgent()) {
				return 1.25;
			}

			// Out hunting at night (section 18): this is what it came out for.
			if (NightHunt.hunting(context)) {
				return 1.0;
			}

			// Something it could beat, further off: worth it if it's coming (or for the drops, if it likes a fight).
			double aggression = context.drifter().getPersonality().get(com.dinglebob.drifter.personality.Trait.AGGRESSION);
			return (threat.decision() == Threats.Decision.ENGAGE ? 0.25 : 0.1) + 0.3 * aggression;
		}

		@Override
		public double feasibility(Context context) {
			// Only something it can see (one it only heard, it looks for first).
			return context.threat().main() != null ? 1.0 : 0.0;
		}

		@Override
		public boolean urgent(Context context) {
			return context.threat().urgent() && context.threat().decision().fights() && (!context.sealedIn() || context.threat().recentlyHit())
				&& context.threat().main() != null;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new CombatActivity(context.drifter());
		}
	}

	/**
	 * The wheat farm (section 9 stage 6 "Settlement"; milestone 8 step 5): once it's settled and has a bucket (for the
	 * water in the middle), it makes one near home and comes back to harvest and replant.
	 */
	public static final class Farm extends Goal {
		@Override
		public String id() {
			return "farm";
		}

		@Override
		public String name(Context context) {
			com.dinglebob.drifter.brain.Home home = context.drifter().getDrifterBrain().home();
			return home != null && home.farm != null ? "tend the farm" : "make a farm";
		}

		@Override
		public double urgency(Context context) {
			com.dinglebob.drifter.brain.Home home = context.drifter().getDrifterBrain().home();
			if (home == null || !home.hasChest() || !home.near(context.drifter(), Schedule.GO_HOME_RANGE)
				|| !Strategist.has(context.drifter(), stack -> stack.is(net.minecraft.world.item.Items.BUCKET) || stack.is(net.minecraft.world.item.Items.WATER_BUCKET))
				|| context.drifter().getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD) {
				return 0.0;
			}

			return com.dinglebob.drifter.brain.activity.FarmActivity.workToDo(context.drifter(), home) ? 0.5 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.3);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.FarmActivity(context.drifter());
		}
	}

	/**
	 * The animal pen (section 9 stage 6 "Settlement"; milestone 8 step 5): once it has a farm (wheat), a fenced pen near
	 * home, two animals of a kind led in, and breeding every few minutes while there's room.
	 */
	public static final class Pen extends Goal {
		@Override
		public String id() {
			return "pen";
		}

		@Override
		public String name(Context context) {
			return "look after the animal pen";
		}

		@Override
		public double urgency(Context context) {
			com.dinglebob.drifter.brain.Home home = context.drifter().getDrifterBrain().home();
			if (home == null || home.farm == null || !home.near(context.drifter(), Schedule.GO_HOME_RANGE)
				|| context.drifter().getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD
				|| context.drifter().getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.WHEAT)
					|| stack.is(net.minecraft.world.item.Items.WHEAT_SEEDS)) < 2) {
				return 0.0;
			}

			return com.dinglebob.drifter.brain.activity.PenActivity.workToDo(context.drifter(), home) ? 0.42 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.3);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.PenActivity(context.drifter());
		}
	}

	/**
	 * Enchanting (section 10.13; milestone 8 step 7): set up the enchanting table near home once it has one, then enchant
	 * its best gear when it has lapis and the levels its bookshelves make worth waiting for.
	 */
	public static final class Enchant extends Goal {
		@Override
		public String id() {
			return "enchant";
		}

		@Override
		public String name(Context context) {
			return "enchant its gear";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			com.dinglebob.drifter.brain.Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.near(drifter, Schedule.GO_HOME_RANGE)) {
				return 0.0;
			}

			net.minecraft.core.BlockPos table = home.enchantingTable(drifter.level());
			if (table == null) {
				return drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.ENCHANTING_TABLE)) > 0 ? 0.45 : 0.0;
			}

			if (com.dinglebob.drifter.brain.activity.EnchantActivity.nextToEnchant(drifter) == null
				|| drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.LAPIS_LAZULI)) == 0) {
				return 0.0;
			}

			int wanted = com.dinglebob.drifter.brain.activity.EnchantActivity.levelsWanted(
				com.dinglebob.drifter.brain.activity.EnchantActivity.bookshelves(drifter.level(), table));
			return drifter.experienceLevel() >= wanted ? 0.45 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.2);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 20;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.EnchantActivity(context.drifter());
		}
	}

	/** Out of the water after running out of air twice in a minute (found in testing: it drowned going back under). */
	public static final class GetOutOfWater extends Goal {
		@Override
		public String id() {
			return "outofwater";
		}

		@Override
		public String name(Context context) {
			return "get out of the water";
		}

		@Override
		public double urgency(Context context) {
			return context.drifter().keepsRunningOutOfAir() ? 1.4 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Context context) {
			return context.drifter().keepsRunningOutOfAir();
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.GetOutOfWaterActivity(context.drifter());
		}
	}

	/** Get away from hostiles it can't beat (section 13 ratio below 1.2), or that it's too hurt to fight. */
	public static final class Flee extends Goal {
		@Override
		public String id() {
			return "flee";
		}

		@Override
		public String name(Context context) {
			LivingEntity main = context.threat().main();
			return main == null ? "get away" : "get away from the " + main.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		}

		@Override
		public double urgency(Context context) {
			Threats.Assessment threat = context.threat();
			if (context.sealedIn() || threat.decision() == Threats.Decision.NONE || threat.decision().fights()) {
				return 0.0;
			}

			if (threat.urgent() || threat.nearest() <= 10.0) {
				return threat.decision() == Threats.Decision.FLEE ? 1.3 : 1.2;
			}

			return 0.5;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Context context) {
			return context.threat().urgent() && !context.threat().decision().fights() && !context.sealedIn();
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new FleeActivity(context.drifter());
		}
	}

	/**
	 * Out after mobs at night for their drops (section 18 "Night variations by personality": brave or confident
	 * Drifters go out to fight mobs for bones, string, gunpowder, ender pearls and XP). It prowls near where it was at
	 * dusk; the fight goal takes over when it sees something it can take, and picks up the drops afterwards. If it gets
	 * hurt or hungry it stops being fit for it and digs in like everyone else.
	 */
	public static final class NightHunt extends Goal {
		@Nullable
		private net.minecraft.world.phys.Vec3 anchor;

		static boolean hunting(Context context) {
			boolean evening = context.time() >= Schedule.shelterUrgent(context.drifter()) && context.time() < Schedule.NIGHT_END;
			return evening && !context.sealedIn() && context.drifter().getDrifterBrain().nightStyle() == Schedule.NightStyle.HUNT
				&& Schedule.fitToHunt(context.drifter());
		}

		@Override
		public String id() {
			return "nighthunt";
		}

		@Override
		public String name(Context context) {
			return "hunt mobs in the dark";
		}

		@Override
		public double urgency(Context context) {
			if (!hunting(context)) {
				this.anchor = null;
				return 0.0;
			}

			return 0.8;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 20;
		}

		@Override
		public Activity start(Context context) {
			if (this.anchor == null) {
				this.anchor = context.drifter().position();
			}

			// Prowl around where it was at dusk: a point 8 to 20 blocks off it.
			var random = context.drifter().getRandom();
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 8.0 + random.nextDouble() * 12.0;
			net.minecraft.world.phys.Vec3 point = this.anchor.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
			return new ExploreActivity(context.drifter(), "hunting mobs", point, drifter -> Threats.assess(drifter).main() != null);
		}
	}

	/**
	 * Settle down (section 9 stage 4: "a home site chosen", "a chest"): once it has stone tools and a furnace and has
	 * survived its first night, it picks a home and puts a chest there; another chest when they're full. Nomads hardly
	 * bother (section 9 pacing: "never really settles").
	 */
	public static final class Settle extends Goal {
		@Override
		public String id() {
			return "settle";
		}

		@Override
		public String name(Context context) {
			Home home = context.drifter().getDrifterBrain().home();
			return home == null ? "find a place to settle" : home.hasChest() ? "put down another chest" : "set up home";
		}

		@Override
		public double urgency(Context context) {
			Home home = context.drifter().getDrifterBrain().home();
			boolean wants = home == null || !home.hasChest() || context.drifter().getDrifterBrain().storageFull();
			if (!wants || context.stage() < 3) {
				return 0.0;
			}

			return context.drifter().getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD ? 0.15 : 0.55;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.4);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.SettleActivity(context.drifter());
		}
	}

	/**
	 * Put things away at home (section 10.18, "Going home: when full, when carrying something precious, or before dark"):
	 * urgent-ish when its inventory is nearly full, worth a detour when it's near home with a load, and on the way in at
	 * the end of the day.
	 */
	public static final class Store extends Goal {
		@Override
		public String id() {
			return "store";
		}

		@Override
		public String name(Context context) {
			return "put things away at home";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hasChest() || drifter.getDrifterBrain().storageFull() || !home.near(drifter, Home.STORAGE_RANGE)) {
				return 0.0;
			}

			int load = Storage.depositCount(drifter);
			if (load == 0) {
				return 0.0;
			}

			if (Storage.freeSlots(drifter) <= Storage.FULL_FREE_SLOTS) {
				return 0.75;
			}

			boolean evening = context.time() >= Schedule.shelterThinking(drifter) && context.time() < Schedule.NIGHT_START;
			if (evening && home.near(drifter, 48.0)) {
				return 0.5;
			}

			return load >= 32 && home.near(drifter, 24.0) ? 0.35 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 20;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.StoreActivity(context.drifter());
		}
	}

	/**
	 * Make room (section 10.18 junk policy): a full inventory with junk in it means throwing the junk out, first thing
	 * (found in testing: a full Drifter crafted a chest that fell on the ground and couldn't pick it up).
	 */
	public static final class MakeRoom extends Goal {
		@Override
		public String id() {
			return "makeroom";
		}

		@Override
		public String name(Context context) {
			return "throw out junk";
		}

		@Override
		public double urgency(Context context) {
			int free = Storage.freeSlots(context.drifter());
			if (free > Storage.FULL_FREE_SLOTS || Storage.junk(context.drifter()).isEmpty()) {
				return 0.0;
			}

			return free <= 1 ? 0.85 : 0.45;
		}

		@Override
		public boolean urgent(Context context) {
			return Storage.freeSlots(context.drifter()) == 0 && !Storage.junk(context.drifter()).isEmpty();
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new Activity(context.drifter()) {
				private boolean done;

				@Override
				public String describe() {
					return "throw out junk";
				}

				@Override
				protected Status next() {
					if (this.done) {
						return Status.SUCCESS;
					}

					this.done = true;
					return this.begin(new com.dinglebob.drifter.executor.task.TossJunkTask(this.drifter));
				}
			};
		}
	}

	/**
	 * Build the starter hut at home (section 11, tier 3: "days 1 to 3"), and patch it up when something knocks a hole in
	 * it (section 11 "Repair").
	 */
	public static final class BuildHut extends Goal {
		@Override
		public String id() {
			return "hut";
		}

		@Override
		public String name(Context context) {
			Home home = context.drifter().getDrifterBrain().home();
			return home != null && home.hutBuilt ? "repair the hut" : "build a hut";
		}

		@Override
		public double urgency(Context context) {
			com.dinglebob.drifter.brain.Home house = context.drifter().getDrifterBrain().home();
			if (house != null && house.houseBed != null) {
				// It lives in a village house: no hut.
				return 0.0;
			}

			Home home = context.drifter().getDrifterBrain().home();
			if (home == null || !home.hasChest() || context.stage() < 4 || !home.near(context.drifter(), Home.STORAGE_RANGE)) {
				return 0.0;
			}

			if (home.hutBuilt) {
				// Living in a village house, or its own house is up: the hut is left as it is.
				if (home.houseBed != null || home.houseBuilt) {
					return 0.0;
				}

				com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
				// (Only if there's somewhere for it: found in testing, a bed it had no room for brought it back every tick.)
				boolean bedToPlace = context.drifter().getInventory().count(stack -> stack.is(ItemTags.BEDS)) > 0 && hut.bed(context.drifter().level()) == null
					&& hut.bedSpot(context.drifter().level()) != null && !home.bedFailedRecently(context.drifter().level().getGameTime());
				return hut.shellBuilt(context.drifter().level()) && !bedToPlace ? 0.0 : 0.45;
			}

			return context.drifter().getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD ? 0.1 : 0.55;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.3);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.BuildHutActivity(context.drifter());
		}
	}

	/**
	 * The house (section 11 shelter tier 4, "stage 5 to 6"; milestone 8 step 8): once the iron age is under way (Builders
	 * as soon as they're settled), a 7x7 house in its archetype's style near the hut; then it moves its bed in. Nomads,
	 * Hermits and Speedrunners don't build one, nor does a Drifter living in a village house.
	 */
	public static final class House extends Goal {
		@Override
		public String id() {
			return "house";
		}

		@Override
		public String name(Context context) {
			Home home = context.drifter().getDrifterBrain().home();
			return home != null && home.houseBuilt ? "work on the house" : "build a house";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hutBuilt || home.houseBed != null || !home.near(drifter, Home.STORAGE_RANGE)
				|| !com.dinglebob.drifter.brain.activity.BuildHouseActivity.buildsHouses(drifter)) {
				return 0.0;
			}

			boolean builder = drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.BUILDER;
			if (context.stage() < (builder ? 4 : 5)) {
				return 0.0;
			}

			com.dinglebob.drifter.building.StarterHut house = home.house();
			if (!home.houseBuilt || house == null) {
				return builder ? 0.52 : drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.HOMEBODY ? 0.46 : 0.38;
			}

			net.minecraft.world.level.Level level = drifter.level();
			if (!house.shellBuilt(level)) {
				return 0.45;
			}

			// The bed to move in (carried, or still in the hut), with room for it.
			boolean bedToMove = house.bed(level) == null && house.bedSpot(level) != null && !home.bedFailedRecently(level.getGameTime())
				&& (drifter.getInventory().count(stack -> stack.is(ItemTags.BEDS)) > 0 || com.dinglebob.drifter.building.StarterHut.of(home).bed(level) != null);
			return bedToMove ? 0.45 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.3);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.BuildHouseActivity(context.drifter());
		}
	}

	/** Put on better armor or a shield the moment it has one (section 10.17, "Upgrades"). */
	public static final class Equip extends Goal {
		@Override
		public String id() {
			return "equip";
		}

		@Override
		public String name(Context context) {
			return "put on better gear";
		}

		@Override
		public double urgency(Context context) {
			return WearArmorTask.hasUpgrade(context.drifter()) ? 0.75 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Context context) {
			return new Activity(context.drifter()) {
				private boolean done;

				@Override
				public String describe() {
					return "put on better gear";
				}

				@Override
				protected Status next() {
					if (this.done) {
						return Status.SUCCESS;
					}

					this.done = true;
					return this.begin(new WearArmorTask(this.drifter));
				}
			};
		}
	}

	public static List<Goal> all() {
		return List.of(new GetOutOfWater(), new Farm(), new Pen(), new Enchant(), new House(), new Fight(), new Flee(), new Equip(), new Eat(), new GetFood(), new Cook(), new CollectSmelting(), new TakeShelter(), new NightHunt(),
			new MakeRoom(), new Settle(), new BuildHut(), new Store(), new Progress(), new Wander(), new Idle());
	}
}
