package com.dinglebob.drifter.brain;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
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

		/** Lower when the goal is risky (a long trip near dusk). TODO(milestone 7): scaled by Caution. */
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

	/** Turns a planner action into the activity that carries it out. */
	public static Activity activityFor(DrifterEntity drifter, Planner.Action action) {
		return switch (action) {
			case Planner.Gather gather -> new GatherActivity(drifter, gather.label(), gather.blocks(), gather.items(), gather.total(), gather.digDown(),
				new ItemStack(gather.item()).is(ItemTags.LOGS));
			case Planner.Hunt hunt -> new HuntActivity(drifter, hunt.prey(), hunt.items(), hunt.total(), hunt.label());
			case Planner.Craft craft -> new CraftActivity(drifter, craft.item(), craft.times());
			case Planner.Place place -> new PlaceStationActivity(drifter, place.station());
			case Planner.Smelt smelt -> new SmeltActivity(drifter, smelt.input(), smelt.inputLabel(), smelt.count(), smelt.fuel(), smelt.fuelLabel(),
				smelt.fuelCount());
			case Planner.Explore explore -> new ExploreActivity(drifter, explore.reason(), explore.toward(), explore.found());
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

			// Wandering off to look for something is worth less than doing something it knows how to do.
			return plan.next() instanceof Planner.Explore ? 0.6 : 1.0;
		}

		@Override
		public double risk(Context context) {
			// Don't start long jobs late in the day.
			return context.time() >= Schedule.SHELTER_THINKING && context.time() < Schedule.NIGHT_END ? 0.5 : 1.0;
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
			// Hungry, or hurt and able to heal by eating (natural regeneration needs food 18+).
			double hurt = context.needs().health() > 0.05 && context.drifter().getFoodData().getFoodLevel() < 18 ? 0.5 : 0.0;
			return Math.max(context.needs().hunger(), hurt);
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

	/** Dig in for the night (section 11 shelter tier 1), and climb out in the morning. */
	public static final class TakeShelter extends Goal {
		@Override
		public String id() {
			return "shelter";
		}

		@Override
		public String name(Context context) {
			return Shelter.isSealedIn(context.drifter()) && !Schedule.isNight(context.time()) ? "climb out of the shelter" : "shelter for the night";
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
			return context.needs().curiosity();
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public double risk(Context context) {
			return context.time() >= Schedule.SHELTER_THINKING && context.time() < Schedule.NIGHT_END ? 0.3 : 1.0;
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
			if (context.sealedIn() || !threat.decision().fights()) {
				return 0.0;
			}

			if (threat.urgent()) {
				return 0.95;
			}

			// Something it could beat, further off: worth it if it's coming (or for the drops, if confident).
			return threat.decision() == Threats.Decision.ENGAGE ? 0.4 : 0.2;
		}

		@Override
		public double feasibility(Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Context context) {
			return context.threat().urgent() && context.threat().decision().fights() && !context.sealedIn();
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
				return threat.decision() == Threats.Decision.FLEE ? 0.97 : 0.9;
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
		return List.of(new Fight(), new Flee(), new Equip(), new Eat(), new GetFood(), new TakeShelter(), new Progress(), new Wander(), new Idle());
	}
}
