package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.RideTask;
import com.dinglebob.drifter.skills.StableGoal;
import com.dinglebob.drifter.world.DrifterGroups;
import com.dinglebob.drifter.world.DrifterWars;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * A war band on horseback (the user, 2026-11-16: "half of the war band should have their own horse, with the other half
 * riding on that horse ... 2 people on 1 horse type vibe"): at the muster those with horses bring them from the stable;
 * after the speech each mounts, and one without a horse climbs up behind each (Icy's Better Horses carries two; without it,
 * or with its riding double switched off, those without walk); the band rides behind its leader, at the leader's gait
 * once up with it, faster to catch up; near the town it raids they get off, the horses left there (an enemy may take one),
 * and go in on foot; home again, each back to its horse. Only if its leader has a horse: else the band marches on foot.
 */
final class WarHorses {
	private WarHorses() {
	}

	/** A horse this far off at most is brought to the muster (its stable is in town). */
	private static final double BRING_FROM = 96.0;
	/** A mounted follower rides to about this far behind its leader. */
	private static final double BEHIND = 6.0;

	/** Its own horse, alive, loaded and saddled, or null. */
	@Nullable
	static AbstractHorse own(DrifterEntity drifter) {
		AbstractHorse horse = StableGoal.horse(drifter);
		return horse != null && horse.isAlive() && horse.isSaddled() ? horse : null;
	}

	/** The horse it's sitting on now, or null. */
	@Nullable
	static AbstractHorse riding(DrifterEntity drifter) {
		return drifter.getVehicle() instanceof AbstractHorse horse ? horse : null;
	}

	/** At the muster: its horse brought from the stable (untied, led there on foot), or null if it has none or it's here. */
	@Nullable
	static Task bring(DrifterEntity drifter, BlockPos muster) {
		AbstractHorse horse = own(drifter);
		if (horse == null || riding(drifter) != null || horse.distanceTo(drifter) > BRING_FROM) {
			return null;
		}

		if (StableGoal.tiedUp(horse)) {
			// (Not at the muster already: off to the stable for it, and it unties it.)
			return horse.blockPosition().closerThan(muster, 12.0) ? null : StableGoal.fetch(drifter, Double.MAX_VALUE);
		}

		if (horse.getLeashHolder() != drifter) {
			if (horse.distanceTo(drifter) > 3.0) {
				return horse.blockPosition().closerThan(muster, 12.0) ? null : NavigateTask.follow(drifter, horse, 2.0);
			}

			if (!horse.blockPosition().closerThan(muster, 12.0)) {
				StableGoal.lead(drifter, horse);
			}
		}

		if (horse.getLeashHolder() == drifter) {
			if (!drifter.blockPosition().closerThan(muster, 6.0)) {
				return new NavigateTask(drifter, Vec3.atBottomCenterOf(muster), 6.0, "bring its horse to the muster").leading(horse);
			}

			// (There: it lets go of it, the horse stands by.)
			StableGoal.unlead(drifter, horse);
		}

		return null;
	}

	/**
	 * The band's riders paired once the speech is done: those with a horse ride it, one without up behind each (Icy's two
	 * to a horse), the rest on foot. Only if the leader has its horse here.
	 */
	static void pair(DrifterWars.Raid raid, DrifterEntity leader) {
		if (raid.paired || leader.getServer() == null) {
			return;
		}

		raid.paired = true;
		DrifterGroups.Group group = DrifterGroups.byId(raid.attackers);
		AbstractHorse leaders = own(leader);
		if (group == null || leaders == null || leaders.distanceTo(leader) > BRING_FROM) {
			return;
		}

		raid.mounted = true;
		com.dinglebob.drifter.Drifter.LOGGER.info("[war] {}'s band rides", DrifterWars.name(raid.attackers));
		List<DrifterEntity> owners = new ArrayList<>();
		List<DrifterEntity> walkers = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = DrifterWars.loaded(leader.getServer(), id);
			if (member == null || WarGoals.raidFor(member) != raid) {
				continue;
			}

			AbstractHorse horse = own(member);
			(horse != null && horse.distanceTo(member) <= BRING_FROM ? owners : walkers).add(member);
		}

		if (!com.dinglebob.drifter.compat.IcyHorses.doubleRiding()) {
			return;
		}

		// (Behind those surest to be up: its leader first, then those whose horses are nearest; found in testing, two paired
		// with riders whose horses were across town waited and walked.)
		owners.sort(java.util.Comparator.comparingDouble((DrifterEntity one) -> one == leader ? -1.0 : own(one).distanceTo(one)));

		for (int i = 0; i < walkers.size() && i < owners.size(); i++) {
			raid.behind.put(walkers.get(i).getUUID(), owners.get(i).getUUID());
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[war] {}: {} with horses, {} behind them, {} on foot", DrifterWars.name(raid.attackers), owners.size(),
			raid.behind.size(), Math.max(0, walkers.size() - raid.behind.size()));
	}

	/** Its band is up: everyone with a horse near on it, everyone paired up behind their rider. */
	static boolean bandUp(DrifterWars.Raid raid, DrifterEntity leader) {
		DrifterGroups.Group group = DrifterGroups.byId(raid.attackers);
		if (group == null || leader.getServer() == null) {
			return true;
		}

		for (UUID id : group.members) {
			DrifterEntity member = DrifterWars.loaded(leader.getServer(), id);
			if (member == null || member == leader || WarGoals.raidFor(member) != raid || riding(member) != null) {
				continue;
			}

			AbstractHorse horse = own(member);
			if (horse != null && horse.distanceTo(member) <= BRING_FROM && !horse.isVehicle() || raid.behind.containsKey(id)) {
				return false;
			}
		}

		return true;
	}

	/** The one whose horse it rides behind, or null. */
	@Nullable
	static DrifterEntity rider(DrifterWars.Raid raid, DrifterEntity drifter) {
		UUID id = raid.behind.get(drifter.getUUID());
		return id == null || drifter.getServer() == null ? null : DrifterWars.loaded(drifter.getServer(), id);
	}

	/**
	 * Up on a horse for the ride: its own (over to it, on: it stays on for the way), or behind its rider once that one is up
	 * (in reach, as a player climbs on behind a friend). Null when it's up (or on foot this time).
	 */
	@Nullable
	static Task mount(DrifterWars.Raid raid, DrifterEntity drifter, BlockPos toward) {
		if (!raid.mounted || riding(drifter) != null) {
			return null;
		}

		AbstractHorse horse = own(drifter);
		if (horse != null && horse.distanceTo(drifter) <= BRING_FROM && !horse.isVehicle()) {
			// (Still tied up where it was left: untied first.)
			if (StableGoal.tiedUp(horse)) {
				Task untie = StableGoal.fetch(drifter, Double.MAX_VALUE);
				if (untie != null) {
					return untie;
				}
			}

			return horse.distanceTo(drifter) > 32.0 ? NavigateTask.follow(drifter, horse, 2.0)
				: new RideTask(drifter, horse, horse.position(), 1.0).onTheWayTo(Vec3.atBottomCenterOf(toward));
		}

		DrifterEntity rider = rider(raid, drifter);
		AbstractHorse theirs = rider == null ? null : riding(rider);
		// (Its rider not up yet: it waits by them a while, rather than set off on foot: found in testing, one of two did.)
		if (theirs == null && rider != null && rider.isAlive() && rider.distanceTo(drifter) < 64.0
			&& drifter.level().getGameTime() - raid.phaseSince < 20 * 60) {
			return rider.distanceTo(drifter) > 4.0 ? NavigateTask.follow(drifter, rider, 2.5) : new com.dinglebob.drifter.executor.task.LookAtTask(drifter, rider.getEyePosition(), 20);
		}

		if (theirs == null || theirs.getPassengers().size() >= 2) {
			return null;
		}

		double reach = drifter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
		if (theirs.getBoundingBox().distanceToSqr(drifter.getEyePosition()) > reach * reach) {
			return NavigateTask.follow(drifter, theirs, 1.5);
		}

		drifter.meansToRide(true);
		boolean on = drifter.startRiding(theirs, true);
		drifter.meansToRide(false);
		if (on) {
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "climbed up behind " + rider.getName().getString());
		}

		return sit(drifter, 10);
	}

	/** Its ride behind the leader: to a spot a little behind it, at its gait once there, faster to catch up. */
	static Task follow(DrifterEntity drifter, DrifterEntity leader, BlockPos toward) {
		AbstractHorse horse = riding(drifter);
		Vec3 back = Vec3.directionFromRotation(0.0F, leader.getYRot()).scale(-BEHIND);
		Vec3 spot = leader.position().add(back);
		// (Near its leader already: it sits tight, rather than jostle for its place in the crowd round it.)
		if (horse == null || horse.getFirstPassenger() != drifter || drifter.position().distanceTo(spot) < 4.0 || drifter.distanceTo(leader) < 10.0) {
			return sit(drifter, 10);
		}

		return new RideTask(drifter, horse, spot, 3.0).onTheWayTo(Vec3.atBottomCenterOf(toward)).keepingUpWith(leader).byRoute().keepSeat();
	}

	/** Off its horse near the town it raids (the horse left there, tied if there's a fence by it), to go in on foot. */
	static void dismount(DrifterEntity drifter) {
		AbstractHorse horse = riding(drifter);
		if (horse == null) {
			return;
		}

		boolean mine = horse.getFirstPassenger() == drifter && horse.getUUID().equals(drifter.getDrifterBrain().horse());
		drifter.stopRiding();
		drifter.getKeys().releaseAll();
		if (mine) {
			StableGoal.tie(drifter, horse, horse.blockPosition());
			drifter.getDrifterBrain().setHorse(horse.getUUID(), horse.blockPosition());
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "got off its horse to go in on foot");
		}
	}

	/**
	 * Sitting on a horse a moment (behind its rider, or up with its leader): it means to stay on; off if its rider's got off
	 * (it was riding behind).
	 */
	static Task sit(DrifterEntity drifter, int ticks) {
		return new Task(drifter) {
			@Override
			public String describe() {
				return "ride along";
			}

			@Override
			protected Status onTick() {
				AbstractHorse horse = riding(this.drifter);
				if (horse == null) {
					return Status.SUCCESS;
				}

				// (Behind a rider who's got off: off too.)
				if (horse.getFirstPassenger() == this.drifter && !horse.getUUID().equals(this.drifter.getDrifterBrain().horse())) {
					this.drifter.stopRiding();
					return Status.SUCCESS;
				}

				this.drifter.keepRiding();
				if (horse.getFirstPassenger() == this.drifter) {
					this.drifter.getKeys().releaseAll();
				}

				return this.ticksRunning() >= ticks ? Status.SUCCESS : Status.RUNNING;
			}
		};
	}
}
