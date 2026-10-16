package com.dinglebob.drifter.brain;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.EnterPortalActivity;
import com.dinglebob.drifter.brain.activity.NetherArrivalActivity;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.level.Level;

/**
 * Nether trips (DESIGN.md section 19, milestone 11): set off through its portal once it's packed, arrive (note the way
 * home, wall the portal in), and come home. What it does there comes in later steps; for now a trip is a look round.
 */
public final class NetherGoals {
	private NetherGoals() {
	}

	/** In the Nether only these goals run: the rest are overworld jobs (settling, farming, sheltering for the night). */
	/** (Looting too: a fortress's chests are what players go through, found by the user.) */
	public static final Set<String> IN_THE_NETHER = Set.of("fight", "flee", "eat", "equip", "getdown", "nether_arrive", "nether_return",
		"nether_blaze", "nether_blocks", "nether_holeup", "loot");

	/** Days between trips while there's nothing in particular to fetch there yet. */
	private static final long DAYS_BETWEEN_LOOKS = 24000L;
	/** The longest it stays on one trip (a Minecraft day is 20 minutes). */
	private static final long MAX_STAY_TICKS = 20 * 60 * 20;

	public static boolean inNether(DrifterEntity drifter) {
		return drifter.level().dimension() == Level.NETHER;
	}

	public static List<Goals.Goal> all() {
		return List.of(new GoToNether(), new Arrive(), new Return(), new BlazeRods(), new HoleUp());
	}

	/** Badly hurt in the Nether and it couldn't get home just now: it walls itself in and heals (HoleUpActivity). */
	public static final class HoleUp extends Goals.Goal {
		/** At or below this health. */
		private static final float HURT = 10.0F;
		/** With blazes or a ghast about it holes up sooner, while it still has the health to build its walls (found in testing:
		 * holing up only when nearly dead, the next fireball finished it before the walls were up). */
		public static final float HURT_UNDER_FIRE = 14.0F;

		@Override
		public String id() {
			return "nether_holeup";
		}

		@Override
		public String name(Goals.Context context) {
			return "hole up to heal";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			if (!inNether(drifter) || drifter.getDrifterBrain().failedRecently("nether_holeup")) {
				return 0.0;
			}

			// Under fire and getting low: before anything else, fighting included. Once holed up it stays until healed (found
			// in testing: a little better, it walked back out into the fireballs).
			boolean holedUp = drifter.getDrifterBrain().currentGoal() instanceof HoleUp
				&& drifter.getHealth() < com.dinglebob.drifter.brain.activity.HoleUpActivity.HEALED;
			if (holedUp || drifter.getHealth() <= HURT_UNDER_FIRE && shootersAbout(drifter)) {
				return 1.35;
			}

			if (drifter.getHealth() > HURT) {
				return 0.0;
			}

			// No way home just now, or shooters about (it heals before it walks home through their fire).
			NetherTrip trip = drifter.getDrifterBrain().netherTrip();
			boolean noWayHome = trip == null || trip.netherPortal == null || drifter.getDrifterBrain().failedRecently("nether_return");
			return noWayHome ? 0.93 : 0.0;
		}

		/** A blaze or ghast seen near here in the last half minute. */
		private static boolean shootersAbout(DrifterEntity drifter) {
			long now = drifter.level().getGameTime();
			return drifter.getMemory().nearest(com.dinglebob.drifter.memory.MemoryKind.MOB, drifter.level().dimension().location(), drifter.position(), 32.0,
				e -> (e.label.equals("minecraft:blaze") || e.label.equals("minecraft:ghast")) && now - e.confirmedAt < 20 * 30) != null;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Goals.Context context) {
			return new com.dinglebob.drifter.brain.activity.HoleUpActivity(context.drifter());
		}
	}

	/** Through its portal at home, when it's packed, fit, and it's daytime. */
	public static final class GoToNether extends Goals.Goal {
		@Override
		public String id() {
			return "nether_go";
		}

		@Override
		public String name(Goals.Context context) {
			return "go to the Nether";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Brain brain = drifter.getDrifterBrain();
			Home home = brain.home();
			// On its way already (the trip begun, not through yet): it sees it through, night falling or someone coming to
			// look at it (found in the user's game: it stepped into its portal, then walked off to put things away at dusk,
			// and the next time to look the player over). The trip ends on its own if it doesn't get through in 2 minutes.
			if (underway(drifter) && drifter.getHealth() >= drifter.getMaxHealth() / 2.0F) {
				return 0.95;
			}

			if (inNether(drifter) || home == null || home.portal == null || !home.near(drifter, Schedule.GO_HOME_RANGE) || !Strategist.portalLit(drifter)
				|| !Strategist.packedForNether(drifter) || drifter.getHealth() < drifter.getMaxHealth() - 4.0F
				|| drifter.getFoodData().getFoodLevel() < 14 || context.time() >= Schedule.shelterThinking(drifter) - 2000) {
				return 0.0;
			}

			if (brain.lastNetherTrip() > 0 && drifter.level().getGameTime() - brain.lastNetherTrip() < DAYS_BETWEEN_LOOKS) {
				return 0.0;
			}

			return 0.55;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return underway(context.drifter());
		}

		/** The trip has begun and it isn't through the portal yet. */
		private static boolean underway(DrifterEntity drifter) {
			NetherTrip trip = drifter.getDrifterBrain().netherTrip();
			return trip != null && trip.phase == NetherTrip.Phase.GOING && !inNether(drifter);
		}

		@Override
		public int minCommitTicks() {
			return 20 * 60;
		}

		@Nullable
		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Home home = drifter.getDrifterBrain().home();
			if (home == null || home.portal == null) {
				return null;
			}

			// Picked up again on the way (after a fight, say): the same trip, so its 2 minutes to get through still count.
			if (!underway(drifter)) {
				drifter.getDrifterBrain().setNetherTrip(new NetherTrip(home.portal.inside(), drifter.level().getGameTime()));
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "packed and ready: off to the Nether");
			}

			return new EnterPortalActivity(drifter, home.portal.inside(), "to the Nether");
		}
	}

	/** Just out of the portal in the Nether: the way home noted and the portal walled in, before anything else. */
	public static final class Arrive extends Goals.Goal {
		@Override
		public String id() {
			return "nether_arrive";
		}

		@Override
		public String name(Goals.Context context) {
			return "get its bearings in the Nether";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			if (!inNether(drifter)) {
				return 0.0;
			}

			NetherTrip trip = drifter.getDrifterBrain().netherTrip();
			if (trip == null) {
				// In the Nether without meaning to be (it fell into a portal): a trip all the same, home by that portal.
				trip = new NetherTrip(drifter.blockPosition(), drifter.level().getGameTime());
				trip.phase = NetherTrip.Phase.THERE;
				drifter.getDrifterBrain().setNetherTrip(trip);
			}

			return trip.netherPortal == null || !trip.walledIn || !trip.lookedAround ? 0.95 : 0.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Nullable
		@Override
		public Activity start(Goals.Context context) {
			return new NetherArrivalActivity(context.drifter());
		}
	}

	/** What a trip is for (section 19 "Goals there"; step 5): blaze rods from a fortress. */
	public static final class BlazeRods extends Goals.Goal {
		@Override
		public String id() {
			return "nether_blaze";
		}

		@Override
		public String name(Goals.Context context) {
			return "get blaze rods";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			NetherTrip trip = drifter.getDrifterBrain().netherTrip();
			if (!inNether(drifter) || trip == null || trip.netherPortal == null || !trip.lookedAround
				|| com.dinglebob.drifter.brain.activity.BlazeRodsActivity.rods(drifter) >= com.dinglebob.drifter.brain.activity.BlazeRodsActivity.WANTED
				// Just failed: not again at once (found in testing: it failed and re-picked it every few ticks).
				|| drifter.getDrifterBrain().failedRecently("nether_blaze")) {
				return 0.0;
			}

			return 0.6;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 120;
		}

		@Nullable
		@Override
		public Activity start(Goals.Context context) {
			return new com.dinglebob.drifter.brain.activity.BlazeRodsActivity(context.drifter());
		}
	}

	/** Back through the portal it came out of: the trip is done, or it's hurt or hungry. */
	public static final class Return extends Goals.Goal {
		@Override
		public String id() {
			return "nether_return";
		}

		@Override
		public String name(Goals.Context context) {
			return "go home from the Nether";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			NetherTrip trip = drifter.getDrifterBrain().netherTrip();
			if (!inNether(drifter) || trip == null || trip.netherPortal == null || !trip.lookedAround) {
				return 0.0;
			}

			// Couldn't get to the portal just now: not again at once (found in testing: it tried four times a second, for
			// minutes, from a spot it had no route out of); other goals (or getting unstuck) get a turn first.
			if (drifter.getDrifterBrain().failedRecently("nether_return")) {
				return 0.0;
			}

			boolean done = com.dinglebob.drifter.brain.activity.BlazeRodsActivity.rods(drifter) >= com.dinglebob.drifter.brain.activity.BlazeRodsActivity.WANTED
				|| drifter.level().getGameTime() - trip.arrivedAt >= MAX_STAY_TICKS
				|| drifter.getDrifterBrain().failedRecently("nether_blaze");
			// Hungry only counts with nothing to eat: with food it eats and carries on (found in the user's game: it arrived
			// hungry, turned round and went home with steak in its pockets).
			boolean hurt = drifter.getHealth() <= drifter.getMaxHealth() / 2.0F
				|| drifter.getFoodData().getFoodLevel() <= 8 && com.dinglebob.drifter.brain.Food.choose(drifter) == null;
			// Once it has set off home it keeps going (found in testing: the reason to leave wore off on the way, a short
			// cool-down ending or a little healing, and it turned back to look for blazes until it burned to death).
			return done || hurt || trip.phase == NetherTrip.Phase.RETURNING ? 0.9 : 0.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Nullable
		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			NetherTrip trip = drifter.getDrifterBrain().netherTrip();
			if (trip == null || trip.netherPortal == null) {
				return null;
			}

			trip.phase = NetherTrip.Phase.RETURNING;
			return new EnterPortalActivity(drifter, trip.netherPortal, "home");
		}
	}
}
