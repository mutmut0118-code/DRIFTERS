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
			// Nothing to want: no need to work out whether it could (planning is the dear part).
			double urgency = this.urgency(context);
			return urgency <= 0.0 ? 0.0 : urgency * this.feasibility(context) * this.risk(context);
		}
	}

	/**
	 * The risk factor for starting something late in the day (section 7: "risk lowers the score in proportion to the
	 * Caution trait"): {@code factor} for a middling Drifter, lower for a cautious one, closer to 1 for a reckless one.
	 */
	/** {@link #lateInTheDay} for goals outside this class. */
	public static double lateInTheDayRisk(Context context, double factor) {
		return lateInTheDay(context, factor);
	}

	static double lateInTheDay(Context context, double factor) {
		if (!(context.time() >= Schedule.shelterThinking(context.drifter()) && context.time() < Schedule.NIGHT_END)) {
			return 1.0;
		}

		// Carrying on through the night: the hour barely matters.
		if (context.drifter().getDrifterBrain().nightStyle() == Schedule.NightStyle.WORK && Schedule.fitToWork(context.drifter())) {
			return Math.max(factor, 0.85);
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
			case Planner.TakeBed take -> new com.dinglebob.drifter.brain.activity.PickUpBlockActivity(drifter, take.bed(), "the villager's bed");
		};
	}

	/** A goal that works through a plan, one action at a time, planning again after each. */
	private abstract static class PlannedGoal extends Goal {
		/** The last plan is reused this long while nothing it plans from has changed (section 22 budget: planning every route
		 * from scratch once a second was most of a decision's time). */
		private static final int PLAN_REUSE_TICKS = 20 * 5;

		private Planner.Plan plan = Planner.INFEASIBLE;
		private long checkedAt = -1;
		private long plannedAt = Long.MIN_VALUE / 2;
		private long planKey;

		protected abstract List<Item> items(Context context);

		protected abstract int count(Context context);

		protected Planner.Plan plan(Context context) {
			long now = context.drifter().level().getGameTime();
			if (now != this.checkedAt) {
				this.checkedAt = now;
				List<Item> items = this.items(context);
				int count = items.isEmpty() ? 0 : this.count(context);
				long key = planInputs(context, items, count);
				if (key != this.planKey || now - this.plannedAt >= PLAN_REUSE_TICKS) {
					this.planKey = key;
					this.plannedAt = now;
					this.plan = items.isEmpty() ? Planner.INFEASIBLE : Planner.plan(context.drifter(), context.world(), items, count);
				}
			}

			return this.plan;
		}

		/** A fingerprint of what a plan comes from: the goal's items, what it carries, what it knows round it, its furnaces. */
		private static long planInputs(Context context, List<Item> items, int count) {
			long key = items.hashCode() * 31L + count;
			var inventory = context.drifter().getInventory();
			for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				key = key * 31L + (stack.isEmpty() ? 0 : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem()) * 100L + stack.getCount());
			}

			key = key * 31L + context.world().signature();
			key = key * 31L + context.drifter().getDrifterBrain().furnaceJobs().size();
			Home home = context.drifter().getDrifterBrain().home();
			return key * 31L + (home == null ? 0 : home.storedSignature() * 2L + (home.near(context.drifter(), Home.STORAGE_RANGE) ? 1 : 0));
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
			// Nor too hurt or too hungry to go (found in the user's world: a hurt one set off and turned straight back 206 times;
			// with the trip off the table it eats or rests first).
			if (plan.next() instanceof Planner.Mine && com.dinglebob.drifter.brain.activity.MineTripActivity.unfitToGo(context.drifter())
				&& !com.dinglebob.drifter.brain.mining.Caver.underground(context.drifter(), context.drifter().blockPosition())) {
				return 0.0;
			}

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
			// Never standing in lava: getting out comes first (found in testing: it stopped to eat in a lava pool and died).
			if (context.drifter().isInLava()) {
				return 0.0;
			}

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
		// Rabbit too (found in testing: in a snowy taiga with only rabbits about it went hungry for days), and bread from
		// its farm's wheat.
		private static final List<Item> COOKED = List.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN,
			Items.COOKED_RABBIT, Items.BREAD);
		private static final List<Item> RAW = List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.RABBIT);
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

	/**
	 * The group's banner (section 21 team wars: "a group gets a name and a banner it hangs at its base, so players can tell
	 * sides apart"): the leader makes one (six wool of a colour and a stick) and stands it by its hut door.
	 */
	public static final class GroupBanner extends PlannedGoal {
		private static final List<Item> BANNERS = java.util.Arrays.stream(net.minecraft.world.item.DyeColor.values())
			.map(color -> net.minecraft.world.level.block.BannerBlock.byColor(color).asItem()).toList();

		@Override
		public String id() {
			return "banner";
		}

		@Override
		public String name(Context context) {
			return carrying(context.drifter()) ? "put up the group's banner" : "make a banner for the group";
		}

		private static boolean carrying(DrifterEntity drifter) {
			return drifter.getInventory().count(stack -> stack.is(ItemTags.BANNERS)) > 0;
		}

		@Override
		protected List<Item> items(Context context) {
			return BANNERS;
		}

		@Override
		protected int count(Context context) {
			return 1;
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			com.dinglebob.drifter.world.DrifterGroups.Group group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
			Home home = drifter.getDrifterBrain().home();
			if (group == null || !group.leader.equals(drifter.getUUID()) || home == null || !home.hutBuilt || home.borrowed() || context.stage() < 4
				|| !home.near(drifter, Home.STORAGE_RANGE) || Schedule.isNight(drifter)) {
				return 0.0;
			}

			if (home.banner != null && drifter.level().isLoaded(home.banner) && drifter.level().getBlockState(home.banner).is(net.minecraft.tags.BlockTags.BANNERS)) {
				return 0.0;
			}

			return 0.5;
		}

		@Override
		public double feasibility(Context context) {
			return carrying(context.drifter()) ? 1.0 : super.feasibility(context);
		}

		@Nullable
		@Override
		public Activity start(Context context) {
			if (carrying(context.drifter())) {
				return new com.dinglebob.drifter.brain.activity.PlaceBannerActivity(context.drifter());
			}

			return super.start(context);
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

	/**
	 * A look in a cave it comes across, on a hunch (user's idea, 2026-10-16: players make assumptions and mistakes; "if
	 * they don't see any ore when they run into a cave, they still go just in case"). Curious ones more; once a day at most;
	 * only by day, healthy, with a pickaxe and a few torches. It may well be a waste of time.
	 */
	public static final class CaveHunch extends Goal {
		private static final long EVERY = 24000L;
		private static final int SCAN_EVERY = 20 * 20;
		private static final int RANGE = 24;
		private long lastHunch = Long.MIN_VALUE / 2;
		private long lastScan = Long.MIN_VALUE / 2;
		@org.jetbrains.annotations.Nullable
		private net.minecraft.core.BlockPos cave;

		@Override
		public String id() {
			return "cavehunch";
		}

		@Override
		public String name(Context context) {
			return "look round a cave, just in case";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			long now = drifter.level().getGameTime();
			// Under way: it keeps at it (the once-a-day clock starts when it sets off).
			if (drifter.getDrifterBrain().currentGoal() == this
				&& drifter.getDrifterBrain().activity() instanceof com.dinglebob.drifter.brain.activity.CaveHunchActivity activity && !activity.finished()) {
				return this.score;
			}

			// Not in the middle of a mining trip (that's caving already), nor from underground.
			if (drifter.getDrifterBrain().activity() instanceof com.dinglebob.drifter.brain.activity.MineTripActivity
				|| com.dinglebob.drifter.brain.mining.Caver.underground(drifter, drifter.blockPosition())) {
				return 0.0;
			}

			if (drifter.level().dimension() != net.minecraft.world.level.Level.OVERWORLD || now - this.lastHunch < EVERY || Schedule.isNight(drifter)
				|| drifter.getHealth() < 16.0F || context.stage() < 2 || drifter.getInventory().count(stack -> stack.is(Items.TORCH)) < 4
				|| drifter.getInventory().count(stack -> stack.is(ItemTags.PICKAXES)) == 0) {
				return 0.0;
			}

			if (now - this.lastScan >= SCAN_EVERY) {
				this.scan(drifter);
			}

			if (this.cave == null || !this.cave.closerThan(drifter.blockPosition(), RANGE + 8)) {
				return 0.0;
			}

			this.score = 0.45 + 0.4 * drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.CURIOSITY);
			return this.score;
		}

		private double score;

		/** A cave it has seen round here: the nearest frontier underground with room behind it. */
		private void scan(DrifterEntity drifter) {
			this.lastScan = drifter.level().getGameTime();
			this.cave = null;
			java.util.Set<net.minecraft.core.BlockPos> notCaves = new java.util.HashSet<>();
			for (int tries = 0; tries < 3 && this.cave == null; tries++) {
				net.minecraft.core.BlockPos candidate = com.dinglebob.drifter.brain.mining.Caver.findFrontier(drifter, drifter.blockPosition(), RANGE,
					drifter.blockPosition().getY() - 16, java.util.Set.of(), notCaves);
				if (candidate == null) {
					break;
				}

				if (com.dinglebob.drifter.brain.mining.Caver.isCave(drifter, candidate)) {
					this.cave = candidate;
				} else {
					notCaves.add(candidate);
				}
			}
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.5);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 20;
		}

		@Override
		public Activity start(Context context) {
			if (this.cave == null) {
				this.scan(context.drifter());
			}

			if (this.cave == null) {
				return null;
			}

			this.lastHunch = context.drifter().level().getGameTime();
			this.score = 0.45 + 0.4 * context.drifter().getPersonality().get(com.dinglebob.drifter.personality.Trait.CURIOSITY);
			return new com.dinglebob.drifter.brain.activity.CaveHunchActivity(context.drifter(), this.cave);
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

			// Something coming at it that a bow can stop before it arrives (section 13 "Archer: opens with arrows").
			if (Threats.worthShooting(context.drifter(), threat.main())) {
				return 1.1;
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
			return (context.threat().urgent() || Threats.worthShooting(context.drifter(), context.threat().main())) && context.threat().decision().fights()
				&& (!context.sealedIn() || context.threat().recentlyHit())
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
	 * How keen it is on farming and animals, by archetype (section 9 "Pacing by archetype"; user agreed, session 2):
	 * Nomads, Speedrunners, Bandits and PvP Sweats skip it (they live off hunting and what they find); Miners and Hermits
	 * only when food runs short (or to keep up one they already have); Homebodies and Builders are keen; the rest as usual.
	 */
	static double farmingKeenness(Context context, boolean alreadyHasOne) {
		var archetype = context.drifter().getPersonality().archetype();
		return switch (archetype) {
			case NOMAD, SPEEDRUNNER, BANDIT, PVP_SWEAT -> 0.0;
			case MINER, HERMIT -> alreadyHasOne ? 0.8 : context.needs().foodSupply() >= FOOD_SHORT ? 1.0 : 0.0;
			case HOMEBODY, BUILDER -> 1.2;
			default -> 1.0;
		};
	}

	/** Food supply need from which a Miner or Hermit turns to farming (it's short of food). */
	private static final double FOOD_SHORT = 0.3;

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
			// With a bucket, anywhere; without one, by water near home (a pond or a river), and not again for a while if there
			// was nowhere.
			boolean bucket = home != null && Strategist.has(context.drifter(), stack -> stack.is(net.minecraft.world.item.Items.BUCKET)
				|| stack.is(net.minecraft.world.item.Items.WATER_BUCKET));
			boolean byWater = home != null && home.farm == null && !bucket
				&& context.drifter().level().getGameTime() - home.farmFailedAt > 20L * 60 * 10 && context.stage() >= 4
				&& com.dinglebob.drifter.brain.activity.FarmActivity.nearestWater(context.drifter(), 24) != null;
			if (home == null || !home.hasChest() || !home.near(context.drifter(), Schedule.GO_HOME_RANGE)
				|| !bucket && home.farm == null && !byWater
				|| context.drifter().level().getGameTime() - home.farmFailedAt < 20L * 60 * 10
				|| context.drifter().getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.NOMAD) {
				return 0.0;
			}

			double keen = farmingKeenness(context, home.farm != null);
			return keen > 0.0 && com.dinglebob.drifter.brain.activity.FarmActivity.workToDo(context.drifter(), home) ? 0.5 * keen : 0.0;
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

			if (home.pen == null && context.drifter().level().getGameTime() - home.penFailedAt < 20L * 60 * 10) {
				return 0.0;
			}

			double keen = farmingKeenness(context, home.pen != null);
			return keen > 0.0 && com.dinglebob.drifter.brain.activity.PenActivity.workToDo(context.drifter(), home) ? 0.42 * keen : 0.0;
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
	 * In the Nether, low on building blocks (bridges and walls eat them): netherrack, which is everywhere and quick to mine
	 * (found in testing: it bridged and pillared toward a fortress until it had none left and was stuck up there).
	 */
	public static final class NetherBlocks extends PlannedGoal {
		/** Below this many building blocks it tops up. */
		public static final int LOW = 24;
		private static final int TOP_UP = 40;

		@Override
		public String id() {
			return "nether_blocks";
		}

		@Override
		public String name(Context context) {
			return "dig netherrack for blocks";
		}

		@Override
		protected List<Item> items(Context context) {
			return List.of(Items.NETHERRACK);
		}

		@Override
		protected int count(Context context) {
			return context.drifter().getInventory().count(stack -> stack.is(Items.NETHERRACK)) + TOP_UP;
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			return NetherGoals.inNether(drifter) && com.dinglebob.drifter.hands.Tools.buildingBlockCount(drifter) < LOW ? 0.7 : 0.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}
	}

	/**
	 * Nether goods taken on the way (section 19 "Goals there"): quartz, nether wart (for brewing later) and glowstone, when
	 * some it has seen is close by and it's short of it. Never a trip of its own: only what's within reach of where it is.
	 */
	public static final class NetherGood extends PlannedGoal {
		/** How close the block must be for it to bother. */
		private static final int NEAR = 16;
		private final String id;
		private final String name;
		private final Item item;
		private final net.minecraft.world.level.block.Block block;
		private final int want;

		public NetherGood(String id, String name, Item item, net.minecraft.world.level.block.Block block, int want) {
			this.id = id;
			this.name = name;
			this.item = item;
			this.block = block;
			this.want = want;
		}

		@Override
		public String id() {
			return this.id;
		}

		@Override
		public String name(Context context) {
			return this.name;
		}

		@Override
		protected List<Item> items(Context context) {
			return List.of(this.item);
		}

		@Override
		protected int count(Context context) {
			return this.want;
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			if (!NetherGoals.inNether(drifter) || drifter.getInventory().count(stack -> stack.is(this.item)) >= this.want) {
				return 0.0;
			}

			// (A ripe wart only: an unripe one gives one wart and wastes the plant.)
			boolean[] near = {false};
			var level = drifter.level();
			drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), NEAR, pos -> {
				if (!near[0] && level.isLoaded(pos)) {
					var state = level.getBlockState(pos);
					near[0] = state.is(this.block) && (!state.hasProperty(net.minecraft.world.level.block.NetherWartBlock.AGE)
						|| state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) >= 3);
				}
			});
			// (Above the blaze hunt it's in the middle of, and allowed to break into it: it's right there, a minute's work.)
			return near[0] ? 0.8 : 0.0;
		}

		@Override
		public boolean urgent(Context context) {
			return this.urgency(context) > 0.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 20;
		}
	}

	/** Get an item someone asked for (`/drifter get <item> [count]`, debug): planned like any target. */
	public static final class GetRequested extends PlannedGoal {
		@Override
		public String id() {
			return "get";
		}

		@Override
		public String name(Context context) {
			var item = context.drifter().getDrifterBrain().requested;
			return item == null ? "get what it was asked for" : "get " + Planner.name(item) + " (asked)";
		}

		@Override
		protected List<Item> items(Context context) {
			var item = context.drifter().getDrifterBrain().requested;
			return item == null ? List.of() : List.of(item);
		}

		@Override
		protected int count(Context context) {
			return context.drifter().getDrifterBrain().requestedCount;
		}

		@Override
		public double urgency(Context context) {
			Brain brain = context.drifter().getDrifterBrain();
			if (brain.requested == null) {
				return 0.0;
			}

			net.minecraft.world.item.Item item = brain.requested;
			if (context.drifter().getInventory().count(stack -> stack.is(item)) >= brain.requestedCount) {
				context.drifter().getMemory().thoughts().add(context.drifter().level().getGameTime(), "got the " + Planner.name(item) + " it was asked for");
				brain.requested = null;
				return 0.0;
			}

			return 0.7;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}
	}

	/**
	 * Build and light a Nether portal near home (section 19, milestone 11 step 1), once it has the materials.
	 */
	public static final class BuildPortal extends Goal {
		@Override
		public String id() {
			return "portal";
		}

		@Override
		public String name(Context context) {
			return "build a Nether portal";
		}

		/** Near home with the obsidian (counting what's already in the frame), flint and steel and a block or two. */
		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			com.dinglebob.drifter.brain.Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.near(drifter, Schedule.GO_HOME_RANGE) || context.stage() < 8 || Strategist.portalLit(drifter)) {
				return 0.0;
			}

			boolean materials = Strategist.PORTAL_TARGETS.get(0).satisfied().test(drifter)
				&& drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)) > 0
				&& drifter.getInventory().count(com.dinglebob.drifter.hands.Tools.BUILDING_BLOCK) >= 2;
			return materials ? 0.5 : 0.0;
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
			return 20 * 60;
		}

		@Nullable
		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.PortalActivity(context.drifter());
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

			// A bookshelf to put up round the table.
			if (drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.BOOKSHELF)) > 0
				&& com.dinglebob.drifter.brain.activity.EnchantActivity.nextShelfSpot(drifter.level(), table, home) != null) {
				return 0.45;
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

	/**
	 * Tidy habit (section 17, "picks up crafting tables"): a crafting table it put down away from home comes back with it
	 * once it's done crafting there, instead of being left littered around.
	 */
	public static final class PickUpTable extends Goal {
		@Override
		public String id() {
			return "tidy";
		}

		@Override
		public String name(Context context) {
			return "pick its crafting table back up";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			net.minecraft.core.BlockPos table = drifter.getDrifterBrain().strayTable;
			if (table == null || !drifter.getPersonality().habits().picksUpTables) {
				return 0.0;
			}

			if (!drifter.level().getBlockState(table).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
				|| table.distToCenterSqr(drifter.position()) > 12 * 12) {
				// Gone, or left behind: it's not going back for it.
				drifter.getDrifterBrain().strayTable = null;
				return 0.0;
			}

			return 0.36;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			// (A factor: 1 is no risk.)
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 5;
		}

		@Override
		public Activity start(Context context) {
			DrifterEntity drifter = context.drifter();
			net.minecraft.core.BlockPos table = drifter.getDrifterBrain().strayTable;
			drifter.getDrifterBrain().strayTable = null;
			return new com.dinglebob.drifter.brain.activity.PickUpBlockActivity(drifter, table, "its crafting table");
		}
	}

	/**
	 * Light up the base (section 10.15; tier 5 compound): spare torches go on the ground round home where it's dark, in
	 * the daytime, once it's settled in the iron age.
	 */
	public static final class LightBase extends Goal {
		@Override
		public String id() {
			return "light";
		}

		@Override
		public String name(Context context) {
			return "light up the base";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hutBuilt || context.stage() < 5 || !home.near(drifter, Home.STORAGE_RANGE)
				|| Schedule.isNight(drifter)
				|| drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.TORCH))
					<= com.dinglebob.drifter.brain.activity.LightBaseActivity.KEEP_TORCHES + 4) {
				return 0.0;
			}

			long now = drifter.level().getGameTime();
			if (now < this.checkAfter) {
				return this.dark ? 0.32 : 0.0;
			}

			this.checkAfter = now + 20 * 30;
			this.dark = com.dinglebob.drifter.brain.activity.LightBaseActivity.darkSpot(drifter, home, java.util.Set.of()) != null;
			return this.dark ? 0.32 : 0.0;
		}

		private long checkAfter;
		private boolean dark;

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
			return 20 * 15;
		}

		@Override
		public Activity start(Context context) {
			this.checkAfter = 0;
			return new com.dinglebob.drifter.brain.activity.LightBaseActivity(context.drifter());
		}
	}

	/**
	 * Take down the pillars and bridges it left round home (user's request), if it's tidy: in the daytime, now and then.
	 */
	public static final class TidyScaffold extends Goal {
		@Override
		public String id() {
			return "scaffold";
		}

		@Override
		public String name(Context context) {
			return "take down its old pillars and bridges";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hutBuilt || !home.near(drifter, Home.STORAGE_RANGE) || Schedule.isNight(drifter)
				|| com.dinglebob.drifter.brain.activity.TidyScaffoldActivity.radius(drifter) == 0) {
				return 0.0;
			}

			long now = drifter.level().getGameTime();
			if (now < this.checkAfter) {
				return this.some ? this.urgency : 0.0;
			}

			this.checkAfter = now + 20 * 60;
			this.some = com.dinglebob.drifter.brain.activity.TidyScaffoldActivity.next(drifter, home, java.util.Set.of()) != null;
			// Keener the more there is: a few blocks can wait, a forest of pillars gets seen to (found in testing: 164 left
			// standing, the job always lost to something else).
			this.urgency = URGENCY + Math.min(0.2, com.dinglebob.drifter.brain.activity.TidyScaffoldActivity.countLeft(drifter, home) / 200.0);
			return this.some ? this.urgency : 0.0;
		}

		private static final double URGENCY = 0.3;
		private double urgency = URGENCY;
		private long checkAfter;
		private boolean some;

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
			return 20 * 15;
		}

		@Override
		public Activity start(Context context) {
			// Not straight back to blocks it couldn't get to.
			this.checkAfter = context.drifter().level().getGameTime() + 20 * 60 * 3;
			this.some = false;
			return new com.dinglebob.drifter.brain.activity.TidyScaffoldActivity(context.drifter());
		}
	}

	/** Stuck up on its own pillars and bridges with no blocks left: get down first (found by the user: rubydoodle). */
	public static final class GetDown extends Goal {
		@Override
		public String id() {
			return "getdown";
		}

		@Override
		public String name(Context context) {
			return "get down off its pillars";
		}

		@Override
		public double urgency(Context context) {
			return com.dinglebob.drifter.brain.activity.GetDownActivity.stuckUp(context.drifter()) ? 0.95 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 10;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.GetDownActivity(context.drifter());
		}
	}

	/**
	 * Loot a structure's chest it has seen (section 10.11, "Opportunistic jumps": a ruined portal's chest, a shipwreck's, a
	 * temple's). Greedy and curious ones go at once (user's request: a ruined portal's chest in sight gets looted).
	 */
	public static final class LootChest extends Goal {
		@Override
		public String id() {
			return "loot";
		}

		@Override
		public String name(Context context) {
			return "loot a chest it saw";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			if (drifter.getHealth() < 10.0F || com.dinglebob.drifter.brain.activity.LootChestActivity.target(drifter) == null) {
				return 0.0;
			}

			// Never with a piglin looking: opening a chest in their sight turns them all on you, gold or not (section 19 piglin
			// etiquette; found in the user's game: looting a fortress chest, the piglins came for it and knocked it off a ledge).
			// Unless it's strong and bold enough to take them all on: then it goes loud, loots anyway and fights whoever comes
			// (user's idea, 2026-10-16), the way a well-geared player sometimes does.
			long piglins = drifter.getSenses().tracked().stream().filter(a -> a.entity instanceof net.minecraft.world.entity.monster.piglin.Piglin
				&& a.entity.isAlive() && a.seenWithin(drifter.level().getGameTime(), 20 * 5) && a.entity.distanceTo(drifter) < 24.0).count();
			if (piglins > 0 && !goesLoud(drifter, piglins)) {
				return 0.0;
			}

			var p = drifter.getPersonality();
			return 0.45 + 0.3 * p.get(com.dinglebob.drifter.personality.Trait.GREED) + 0.1 * p.get(com.dinglebob.drifter.personality.Trait.CURIOSITY);
		}

		/** Brave enough (Bravery 0.5 or more) and strong enough to win against all of them at once, as Threats judges a fight. */
		private static boolean goesLoud(DrifterEntity drifter, long piglins) {
			double danger = piglins * Threats.dangerWhenAngry(net.minecraft.world.entity.EntityType.PIGLIN);
			return drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.BRAVERY) >= 0.5
				&& Threats.power(drifter) * Threats.courage(drifter) >= danger * LOUD_MARGIN;
		}

		/** How many times the piglins' strength it wants before it goes loud. */
		private static final double LOUD_MARGIN = 2.0;

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.5);
		}

		/** A chest close by, or a greedy Drifter: it goes now, not when it's done with what it's doing (user's request). */
		@Override
		public boolean urgent(Context context) {
			net.minecraft.core.BlockPos chest = com.dinglebob.drifter.brain.activity.LootChestActivity.target(context.drifter());
			return chest != null && (chest.closerThan(context.drifter().blockPosition(), 24.0)
				|| context.drifter().getPersonality().get(com.dinglebob.drifter.personality.Trait.GREED) >= 0.6);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 10;
		}

		@Override
		public Activity start(Context context) {
			net.minecraft.core.BlockPos chest = com.dinglebob.drifter.brain.activity.LootChestActivity.target(context.drifter());
			return chest == null ? null : new com.dinglebob.drifter.brain.activity.LootChestActivity(context.drifter(), chest);
		}
	}

	/**
	 * Checking a chest of its it saw (or heard) someone open, once they step away (user's idea, 2026-10-16): stolen
	 * things are found out at once, and the one it watched is caught in the act.
	 */
	public static final class CheckChest extends Goal {
		@Override
		public String id() {
			return "checkchest";
		}

		@Override
		public String name(Context context) {
			return "check its chest";
		}

		@Override
		public double urgency(Context context) {
			net.minecraft.core.BlockPos chest = context.drifter().getSocial().watcher().chestToCheck();
			return chest != null && chest.closerThan(context.drifter().blockPosition(), 48.0) ? 0.9 : 0.0;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			// (A factor: 1 is no risk.)
			return 1.0;
		}

		@Override
		public boolean urgent(Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 10;
		}

		@Override
		public Activity start(Context context) {
			net.minecraft.core.BlockPos chest = context.drifter().getSocial().watcher().chestToCheck();
			return chest == null ? null : new com.dinglebob.drifter.brain.activity.CheckChestActivity(context.drifter(), chest);
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
			// From blazes or a ghast in the Nether, running only gets it shot in the back: it walls itself in where it is, which
			// stops the fireballs and breaks their sight, and heals (found in testing: running from blazes, it burned).
			Threats.Assessment threat = context.threat();
			// (Only when it's hurt: at full health that's done at once and it came straight back here, every tick; found in
			// the user's game.)
			if (NetherGoals.inNether(context.drifter()) && threat.main() != null && Threats.shoots(threat.main())
				&& context.drifter().getHealth() < com.dinglebob.drifter.brain.activity.HoleUpActivity.HEALED
				&& com.dinglebob.drifter.hands.Tools.buildingBlockCount(context.drifter()) >= 5) {
				return new com.dinglebob.drifter.brain.activity.HoleUpActivity(context.drifter());
			}

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
			// On the surface: deep in a mine the night doesn't matter (user's rule), and a night hunt down there sent it
			// wandering the flooded caves until it drowned (found in testing).
			return evening && !context.sealedIn() && !com.dinglebob.drifter.social.Manhunt.isHunter(context.drifter())
				&& context.drifter().getDrifterBrain().nightStyle() == Schedule.NightStyle.HUNT
				&& Schedule.fitToHunt(context.drifter())
				&& !com.dinglebob.drifter.brain.mining.Caver.underground(context.drifter(), context.drifter().blockPosition());
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
			// Full pockets send it home from further out too (found in testing: 120 blocks out, full, it started a mining
			// trip that ended "pockets full" at once, 307 times).
			boolean full = Storage.freeSlots(drifter) <= Storage.FULL_FREE_SLOTS;
			if (home == null || !home.hasChest() || drifter.getDrifterBrain().storageFull()
				|| !home.near(drifter, full ? FULL_STORE_RANGE : Home.STORAGE_RANGE)
				|| drifter.level().getGameTime() - home.storeFailedAt < 20 * 120) {
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

		/** How far it walks home to empty full pockets. */
		private static final double FULL_STORE_RANGE = 512.0;
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
			if (house != null && house.borrowed()) {
				// It lives in a village house or an outpost: no hut.
				return 0.0;
			}

			Home home = context.drifter().getDrifterBrain().home();
			if (home == null || !home.hasChest() || context.stage() < 4 || !home.near(context.drifter(), Home.STORAGE_RANGE)) {
				return 0.0;
			}

			if (home.hutBuilt) {
				// Living in a village house, or its own house is up: the hut is left as it is.
				if (home.houseBed != null || home.houseBuilt || home.planDone) {
					return 0.0;
				}

				com.dinglebob.drifter.building.StarterHut hut = com.dinglebob.drifter.building.StarterHut.of(home);
				// (Only if there's somewhere for it: found in testing, a bed it had no room for brought it back every tick.)
				boolean bedToPlace = context.drifter().getInventory().count(stack -> stack.is(ItemTags.BEDS)) > 0 && hut.bed(context.drifter().level()) == null
					&& hut.bedSpot(context.drifter().level()) != null && !home.bedFailedRecently(context.drifter().level().getGameTime());
				// A blast furnace or smoker it made for the hut (user's request, 2026-10-16).
				boolean stationToPlace = hut.stationToPlace(context.drifter().level(), item -> context.drifter().getInventory().count(stack -> stack.is(item)));
				return hut.missingBlocks(context.drifter().level()) <= home.allowedGaps("hut") && !bedToPlace && !stationToPlace ? 0.0 : 0.45;
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
	/**
	 * Its house from a plan (house plans, user's decisions 2026-10-16): when it's settled (hut up) and far enough along, it
	 * picks a plan and a spot, levels the ground and builds it; the hut stays as its shed.
	 */
	public static final class PlanHouse extends Goal {
		@Override
		public String id() {
			return "planhouse";
		}

		@Override
		public String name(Context context) {
			Home home = context.drifter().getDrifterBrain().home();
			return home != null && home.planId != null ? "work on its house" : "build a house";
		}

		@Override
		public double urgency(Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (home == null || !home.hutBuilt || home.borrowed() || home.planDone || home.houseBuilt || !home.near(drifter, Home.STORAGE_RANGE)
				|| drifter.getServer() == null || !com.dinglebob.drifter.brain.activity.BuildHouseActivity.buildsHouses(drifter)
				|| com.dinglebob.drifter.building.plan.HousePlans.cached(drifter.getServer()).isEmpty()) {
				return 0.0;
			}

			boolean builder = drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.BUILDER;
			if (context.stage() < (builder ? 4 : 5)) {
				return 0.0;
			}

			double base = builder ? 0.55 : drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.HOMEBODY ? 0.5 : 0.44;
			return context.stage() >= 6 ? base + 0.08 : base;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return lateInTheDay(context, 0.6);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}

		@Override
		public Activity start(Context context) {
			return new com.dinglebob.drifter.brain.activity.BuildPlanActivity(context.drifter());
		}
	}

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
			if (home == null || !home.hutBuilt || home.borrowed() || !home.near(drifter, Home.STORAGE_RANGE)
				|| !com.dinglebob.drifter.brain.activity.BuildHouseActivity.buildsHouses(drifter)) {
				return 0.0;
			}

			boolean builder = drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.BUILDER;
			if (context.stage() < (builder ? 4 : 5)) {
				return 0.0;
			}

			// House plans (2026-10-16): with any plan to build, its house comes from a plan instead (PlanHouse), unless it
			// already has this kind of house.
			if (!home.houseBuilt && home.houseCenter == null && drifter.getServer() != null
				&& !com.dinglebob.drifter.building.plan.HousePlans.cached(drifter.getServer()).isEmpty()) {
				return 0.0;
			}

			com.dinglebob.drifter.building.StarterHut house = home.house();
			if (house == null && drifter.level().getGameTime() - home.houseSiteFailedAt < 20L * 60 * 10) {
				// No spot for it last time it looked: it gets to know the land round home first.
				return 0.0;
			}

			if (!home.houseBuilt || house == null) {
				// Section 9: the house is stage 5-6 work; once the settlement's in (farm, pen, armor) it matters as much as gear.
				double base = builder ? 0.55 : drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.HOMEBODY ? 0.5 : 0.44;
				return context.stage() >= 6 ? base + 0.08 : base;
			}

			net.minecraft.world.level.Level level = drifter.level();
			// (A gap or two it couldn't reach when it built it isn't a repair job.)
			if (house.missingBlocks(level) > home.allowedGaps("house")) {
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

	/** Blocks a finished hut or house can be missing before it's a repair job (gaps it couldn't reach when building). */

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
			// The gold piece for the Nether goes on before anything else on the trip, before it steps through (found in testing:
			// it put it on after the piglins had already come for it).
			if (WearArmorTask.goldPending(context.drifter())) {
				return 0.97;
			}

			return WearArmorTask.hasUpgrade(context.drifter()) ? 0.75 : 0.0;
		}

		@Override
		public boolean urgent(Context context) {
			return WearArmorTask.goldPending(context.drifter());
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
		List<Goal> goals = new java.util.ArrayList<>(List.of(new GetOutOfWater(), new Farm(), new Pen(), new Enchant(), new BuildPortal(), new GetRequested(), new NetherBlocks(), new NetherGood("nether_quartz", "mine some nether quartz", Items.QUARTZ, net.minecraft.world.level.block.Blocks.NETHER_QUARTZ_ORE, 16), new NetherGood("nether_wart", "pick some nether wart", Items.NETHER_WART, net.minecraft.world.level.block.Blocks.NETHER_WART, 8), new NetherGood("nether_glowstone", "break some glowstone", Items.GLOWSTONE_DUST, net.minecraft.world.level.block.Blocks.GLOWSTONE, 16), new House(), new PickUpTable(), new LightBase(), new TidyScaffold(), new LootChest(), new CheckChest(), new GetDown(), new Fight(), new Flee(), new Equip(), new Eat(), new GetFood(), new Cook(), new CollectSmelting(), new TakeShelter(), new NightHunt(),
			new MakeRoom(), new Settle(), new BuildHut(), new Store(), new Progress(), new CaveHunch(), new Wander(), new Idle()));
		// Toward players (milestone 9, section 14).
		goals.addAll(com.dinglebob.drifter.social.SocialGoals.all());
		goals.addAll(com.dinglebob.drifter.social.HostileGoals.all());
		goals.addAll(com.dinglebob.drifter.social.FriendlyGoals.all());
		// Groups (milestone 12, section 21).
		goals.addAll(com.dinglebob.drifter.social.GroupGoals.all());
		goals.addAll(com.dinglebob.drifter.social.WarGoals.all());
		goals.addAll(com.dinglebob.drifter.social.BanditGoals.all());
		goals.add(new GroupBanner());
		goals.add(new com.dinglebob.drifter.brain.activity.PassThroughActivity.Goal());
		goals.add(new PlanHouse());
		// Whims (milestone 13, docs/WHIMS.md).
		goals.add(new com.dinglebob.drifter.whim.WhimGoal());
		// The Nether (milestone 11, section 19).
		goals.addAll(NetherGoals.all());
		return List.copyOf(goals);
	}
}
