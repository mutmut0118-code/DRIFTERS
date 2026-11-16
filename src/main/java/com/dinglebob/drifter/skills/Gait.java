package com.dinglebob.drifter.skills;

import java.util.Map;
import java.util.WeakHashMap;

import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.compat.IcyHorses;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * How fast a Drifter rides (horse culture; the user, 2026-11-16: "trot, all the way to gallop ... a drifter trotting into
 * town ... a group of drifters trotting to another town, no urgency ... a chase between two drifters with the gallop
 * mechanic"): a walk in a town and the last stretch to where it's going, a trot on the road (every horse's trot is the
 * same speed, so riders together keep together), a canter when late in the day, a gallop in a hurry (chased, chasing, a
 * fight, called to war). With Icy's Better Horses: its gears and its speeds for them (it gives them only to players), and
 * its gait animations follow the speed; without: shares of the horse's own speed.
 */
public enum Gait {
	WALK(IcyHorses.WALK, 0.35F), TROT(IcyHorses.TROT, 0.6F), CANTER(IcyHorses.CANTER, 0.8F), GALLOP(IcyHorses.GALLOP, 1.0F);

	/** The last stretch walked in (blocks to where it's going). */
	public static final double WALK_IN = 32.0;

	/** Icy's gear for it. */
	final int gear;
	/** Its share of the horse's speed without Icy's. */
	final float share;

	Gait(int gear, float share) {
		this.gear = gear;
		this.share = share;
	}

	/** Each ridden horse's gait now. */
	private static final Map<AbstractHorse, Gait> NOW = new WeakHashMap<>();

	/** The gait for now: the hurry, the place, the time of day. */
	public static Gait choose(DrifterEntity rider, Vec3 target, boolean hurry) {
		var goal = rider.getDrifterBrain().currentGoal();
		String id = goal == null ? "" : goal.id();
		if (hurry || rider.inCombat() || id.equals("flee") || id.equals("fight") || id.equals("pursuit") || id.equals("bounty")) {
			return GALLOP;
		}

		double away = Math.hypot(target.x - rider.getX(), target.z - rider.getZ());
		if (away < WALK_IN || com.dinglebob.drifter.api.DrifterApi.settled(rider.level(), rider.blockPosition())) {
			return WALK;
		}

		return rider.level().getDayTime() % 24000L >= Schedule.shelterThinking(rider) ? CANTER : TROT;
	}

	/** Its horse at this gait from now (Icy's gear set too, for anything of Icy's that reads it). */
	public static void set(AbstractHorse horse, Gait gait) {
		NOW.put(horse, gait);
		IcyHorses.setGait(horse, gait.gear);
	}

	/** Its horse's gait now (a trot if none's been set). */
	public static Gait of(AbstractHorse horse) {
		return NOW.getOrDefault(horse, TROT);
	}

	/** The horse's speed at its gait now, ridden by a Drifter. */
	public static float speed(AbstractHorse horse, float base) {
		Gait gait = of(horse);
		return IcyHorses.present() ? IcyHorses.gearSpeed(gait.gear, base) : base * gait.share;
	}

	/** In words, for its thoughts. */
	public String words() {
		return switch (this) {
			case WALK -> "walking";
			case TROT -> "trotting";
			case CANTER -> "cantering";
			case GALLOP -> "galloping";
		};
	}
}
