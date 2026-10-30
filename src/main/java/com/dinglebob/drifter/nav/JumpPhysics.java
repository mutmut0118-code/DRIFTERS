package com.dinglebob.drifter.nav;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How far a jump carries (parkour; user's request, 2026-10-17: "lets upgrade pathfinding, starting with parkour jumps").
 * A tick-by-tick run of the player's own movement (the Drifter moves by the same rules: LivingEntity#travel and
 * #jumpFromGround, Player#getFlyingSpeed): on the ground each tick adds its speed (the movement speed attribute, times
 * 1.3 sprinting) and keeps 0.546 of the motion (normal block friction 0.6 x 0.91); a jump sets 0.42 up (plus Jump
 * Boost) and, sprinting, adds 0.2 forward; in the air each tick adds 0.02 (0.026 sprinting) and keeps 0.91 across and
 * 0.98 of (up - 0.08). The answer: how far forward its middle has got when its feet come back down to a given height.
 * Used so it only plans jumps a player could make.
 */
public final class JumpPhysics {
	private JumpPhysics() {
	}

	private static final double FRICTION = 0.6 * 0.91;
	private static final double GRAVITY = 0.08;
	private static final double VERTICAL_DRAG = 0.98;
	private static final double AIR_DRAG = 0.91;
	private static final double JUMP_POWER = 0.42;
	private static final double SPRINT_JUMP_BOOST = 0.2;
	private static final double AIR_SPEED = 0.02;
	private static final double SPRINT_AIR_SPEED = 0.025999999;
	private static final double SPRINT_FACTOR = 1.3;
	/**
	 * How high a standing jump lifts its feet (no Jump Boost): about 1.25 blocks. A block up from a slab is 1.5, out of reach
	 * (Terrain Slabs' hillsides).
	 */
	public static final double JUMP_HEIGHT = jumpHeight();

	private static double jumpHeight() {
		double y = 0.0;
		double up = JUMP_POWER;
		while (up > 0.0) {
			y += up;
			up = (up - GRAVITY) * VERTICAL_DRAG;
		}

		return y;
	}

	/** Ticks of run-up that count as at full speed (it is within 1% of it after 6). */
	private static final int RUN_UP_TICKS = 12;

	private record Key(int speed, boolean sprint, boolean runUp, int dy, int boost) {
	}

	/**
	 * How it takes a jump, gentlest first: a hop from the edge at a walk (it creeps to the edge, stops, jumps), a walking
	 * run-up, a sprint jump from close to the edge, a sprinting run-up. It takes the first that carries far enough
	 * (a player doesn't sprint at a one-block gap: that sails past the landing).
	 */
	public enum Mode {
		WALK_HOP(false, false), WALK_RUN(false, true), SPRINT_HOP(true, false), SPRINT_RUN(true, true);

		public final boolean sprint;
		public final boolean runUp;

		Mode(boolean sprint, boolean runUp) {
			this.sprint = sprint;
			this.runUp = runUp;
		}
	}

	/**
	 * The gentlest way to carry at least {@code need} but at most {@code most} (past that it would sail over the landing),
	 * or null if none does. {@code canSprint}: food over 6; {@code canRunUp}: room for a run (or already running at it).
	 */
	@org.jetbrains.annotations.Nullable
	public static Mode choose(double speed, boolean canSprint, boolean canRunUp, int dy, int boost, double need, double most) {
		for (Mode mode : Mode.values()) {
			if (mode.sprint && !canSprint || mode.runUp && !canRunUp) {
				continue;
			}

			double reach = reach(speed, mode.sprint, mode.runUp, dy, boost);
			if (reach >= need) {
				return reach <= most ? mode : null;
			}
		}

		return null;
	}

	private static final Map<Key, Double> REACH = new ConcurrentHashMap<>();

	/**
	 * Forward distance its middle covers from the moment it jumps until its feet are back down at {@code dy} blocks above
	 * the take-off (negative: below), or 0 if the jump never gets that high. {@code speed} is its movement speed
	 * attribute (0.1 for a player), {@code runUp} whether it was already moving straight at full speed, {@code boost} the
	 * Jump Boost level (0 none).
	 */
	public static double reach(double speed, boolean sprint, boolean runUp, int dy, int boost) {
		Key key = new Key((int)Math.round(speed * 10000.0), sprint, runUp, dy, boost);
		return REACH.computeIfAbsent(key, k -> simulate(speed, sprint, runUp, dy, boost));
	}

	private static double simulate(double speed, boolean sprint, boolean runUp, int dy, int boost) {
		double ground = speed * (sprint ? SPRINT_FACTOR : 1.0);
		// Speed on the ground before the jump: a few ticks of walking (or none, standing).
		double vx = 0.0;
		for (int t = 0; t < (runUp ? RUN_UP_TICKS : 0); t++) {
			vx = (vx + ground) * FRICTION;
		}

		// The jump tick: up-motion set, the sprint boost, and this tick still moves with ground control and friction.
		double vy = JUMP_POWER + 0.1 * boost;
		if (sprint) {
			vx += SPRINT_JUMP_BOOST;
		}

		double x = 0.0;
		double y = 0.0;
		vx += ground;
		x += vx;
		y += vy;
		vx *= FRICTION;
		vy = (vy - GRAVITY) * VERTICAL_DRAG;
		double peak = y;
		double air = sprint ? SPRINT_AIR_SPEED : AIR_SPEED;
		for (int t = 0; t < 80; t++) {
			vx += air;
			double nextY = y + vy;
			double nextX = x + vx;
			if (vy < 0.0 && nextY <= dy) {
				// Coming down through the landing height this tick: it lands there, the tick's move across still made.
				return peak >= dy ? nextX : 0.0;
			}

			x = nextX;
			y = nextY;
			peak = Math.max(peak, y);
			vx *= AIR_DRAG;
			vy = (vy - GRAVITY) * VERTICAL_DRAG;
		}

		return 0.0;
	}
}
