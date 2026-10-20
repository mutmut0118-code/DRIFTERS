package com.dinglebob.drifter.mannerism;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.Task.BodyPart;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Habits;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Personality;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The mannerism layer (DESIGN.md section 16): small human tics on top of whatever the Drifter is doing, using only the
 * body parts its current task leaves free (section 8 body-part claims). Glances while walking, look-backs at night,
 * a sky check at dusk, jump-spam and going AFK while waiting, hotbar fidgeting, celebrating a kill, a rage fit after a
 * string of failures.
 *
 * <p>Section 16.6 rules: at most one tic every 2 s, never the same tic twice in a row (side glances are the camera's
 * habit from 16.1, not a 16.4 tic, so they're exempt), each tic has its own cooldown and a random length, never during
 * reflexes or fights or anything fiddly (the task doesn't free the parts), and rates scale with Fidget and mood.
 */
public final class Mannerisms {
	/** Section 16.6: at most one tic every 2 s. */
	public static final int MIN_GAP_TICKS = 40;
	/** How often it considers starting a tic (ticks). Chances below are per check. */
	public static final int CHECK_TICKS = 5;
	/** An event (a kill, coming up from a mine) waits this long for free body parts before it's forgotten. */
	public static final int EVENT_EXPIRY_TICKS = 100;
	/** After nearly dying it keeps looking back for this long (section 16.5, "Near death escape": 30 s). */
	public static final int NEAR_DEATH_TICKS = 20 * 30;
	/** Below this health it counts as a near-death moment. */
	public static final float NEAR_DEATH_HEALTH = 6.0F;
	/** Underground at least this long before seeing the sky again counts as surfacing (a sky check). */
	public static final int SURFACE_AFTER_TICKS = 20 * 60;
	/** A wait has to run this long before it might go AFK. */
	public static final int AFK_AFTER_WAITING_TICKS = 20 * 30;

	/** The tic catalog this milestone covers (section 16.4), plus the 16.1 side glance. */
	public enum Kind {
		GLANCE(40), LOOK_BACK(60), SKY_CHECK(20 * 30), SWEEP(20 * 60), JUMP_SPAM(60), CROUCH_SPAM(20 * 10), SPIN(20 * 30), SWING_AIR(20 * 15),
		HOTBAR_FIDGET(20 * 15), SHIELD_RAISE(20 * 10), AFK(20 * 120), RAGE(20 * 60 * 5);

		/** Ticks before this tic can fire again. */
		final int cooldown;

		Kind(int cooldown) {
			this.cooldown = cooldown;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}
	}

	/** Things that happened that a tic may answer (section 16.5 signature moments). */
	public enum Event {
		/** It killed a hostile mob: maybe a celebration, by its habit. */
		CELEBRATE,
		/** It came up from underground. */
		SURFACED,
		/** Dusk while it can see the sky. */
		DUSK,
		/** New armor on (section 16.5 "First iron armor: ... then a quick spin"). */
		NEW_GEAR,
		/** A fight just ended: a look around. */
		FIGHT_OVER
	}

	private final DrifterEntity drifter;
	@Nullable
	private Tic active;
	@Nullable
	private Kind last;
	private long lastEnd = Long.MIN_VALUE / 2;
	private final EnumMap<Kind, Long> readyAt = new EnumMap<>(Kind.class);
	private final EnumMap<Event, Long> pending = new EnumMap<>(Event.class);
	private long nearDeathUntil = Long.MIN_VALUE / 2;
	private boolean sawSky = true;
	private long skyLostAt;
	private long duskCheckedDay = -1;
	private String lastDescription = "";
	/** A tic started by command runs whatever the task is doing. */
	private boolean forced;

	public Mannerisms(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	// ---------------------------------------------------------------------------------------------
	// Outside hooks
	// ---------------------------------------------------------------------------------------------

	public void event(Event event) {
		this.pending.put(event, this.now() + EVENT_EXPIRY_TICKS);
	}

	/** It got hurt: whatever it was fidgeting with, it stops. */
	public void onHurt(float amount) {
		this.interrupt("hurt");
	}

	/** It killed something: a hostile kill can set off a celebration (by habit and mood). */
	public void onKill(net.minecraft.world.entity.LivingEntity victim) {
		Personality personality = this.drifter.getPersonality();
		if (victim instanceof Enemy) {
			personality.feel(Mood.TRIUMPHANT, 0.35, "killed a " + victim.getType().getDescription().getString().toLowerCase(Locale.ROOT));
			personality.feel(Mood.CONFIDENT, 0.1, "");
			personality.practice(0.004);
			double chance = 0.25 + 0.35 * personality.get(Trait.FIDGET) + 0.3 * personality.mood().pressure(Mood.TRIUMPHANT);
			if (personality.habits().celebration != Habits.Celebration.QUIET && this.drifter.getRandom().nextDouble() < chance) {
				this.event(Event.CELEBRATE);
			}
		}
	}

	/** Starts a tic now, for testing (debug command). It still stops if its body parts get busy. */
	public boolean force(Kind kind) {
		Tic tic = switch (kind) {
			case GLANCE -> new Glance();
			case LOOK_BACK -> new LookBack(false);
			case SKY_CHECK -> new SkyCheck();
			case SWEEP -> new Sweep();
			case JUMP_SPAM -> new JumpSpam(4);
			case CROUCH_SPAM -> new CrouchSpam(5);
			case SPIN -> new Spin();
			case SWING_AIR -> new SwingAir(3);
			case HOTBAR_FIDGET -> new HotbarFidget();
			case SHIELD_RAISE -> this.drifter.canUseShield() ? new ShieldRaise() : null;
			case AFK -> new Afk();
			case RAGE -> new Rage();
		};
		if (tic == null) {
			return false;
		}

		this.interrupt("forced");
		this.active = tic;
		this.forced = true;
		tic.start();
		return true;
	}

	/** A tic started on purpose (a command) is running. */
	public boolean forcedActive() {
		return this.active != null && this.forced;
	}

	public void interrupt(String why) {
		if (this.active != null) {
			this.stopActive();
		}
	}

	/** The tic going on now, for the overlay. */
	public String describe() {
		return this.active != null ? "tic: " + this.active.kind.id() : this.lastDescription;
	}

	// ---------------------------------------------------------------------------------------------
	// Ticking
	// ---------------------------------------------------------------------------------------------

	public void tick() {
		this.tick(false);
	}

	/**
	 * {@code reactiveOnly}: no one near enough to see a fidget (section 22 detail tiers), so only the glances that are
	 * perception, not show: toward a sound or a creature it noticed (found in testing: with every tic off, a busy
	 * Drifter far from players never turned toward the ghast shooting at it).
	 */
	public void tick(boolean reactiveOnly) {
		this.reactiveOnly = reactiveOnly;
		long now = this.now();
		this.trackSignals(now);
		Set<BodyPart> free = this.freeParts();
		if (this.active != null) {
			if (!this.forced && !free.containsAll(this.active.parts) || !this.active.tick()) {
				this.stopActive();
			}

			return;
		}

		this.pending.entrySet().removeIf(entry -> entry.getValue() < now);
		if (free.isEmpty() || now - this.lastEnd < MIN_GAP_TICKS || this.drifter.tickCount % CHECK_TICKS != 0) {
			return;
		}

		Tic tic = this.choose(free, now);
		if (tic != null && free.containsAll(tic.parts)) {
			this.active = tic;
			tic.start();
		}
	}

	private long now() {
		return this.drifter.level().getGameTime();
	}

	private void stopActive() {
		Tic tic = this.active;
		this.active = null;
		this.forced = false;
		if (tic == null) {
			return;
		}

		tic.stop();
		long now = this.now();
		this.last = tic.kind;
		this.lastEnd = now;
		this.readyAt.put(tic.kind, now + tic.kind.cooldown);
		this.lastDescription = "last tic: " + tic.kind.id();
	}

	/** What the task leaves free, minus what nothing should touch (fights, swimming, eating, a brain switched off). */
	private Set<BodyPart> freeParts() {
		DrifterEntity d = this.drifter;
		if (!d.isAlive() || d.isSleeping() || d.isPassenger() || d.isInWater() || d.isUsingItem() && this.active == null
			|| !d.getDrifterBrain().isEnabled()) {
			return Task.NOTHING_FREE;
		}

		Goals.Goal goal = d.getDrifterBrain().currentGoal();
		if (goal != null && ("fight".equals(goal.id()) || "flee".equals(goal.id()))) {
			return Task.NOTHING_FREE;
		}

		Task task = d.getTasks().current();
		return task == null ? Task.NOTHING_FREE : task.freeParts();
	}

	@Nullable
	private Task leaf() {
		Task task = this.drifter.getTasks().current();
		return task == null ? null : task.leaf();
	}

	private boolean walking() {
		return this.leaf() instanceof NavigateTask;
	}

	@Nullable
	private WaitTask waiting() {
		return this.leaf() instanceof WaitTask wait ? wait : null;
	}

	/** Dusk, surfacing, and near-death, read from its own senses (time sense and sky). */
	private void trackSignals(long now) {
		DrifterEntity d = this.drifter;
		boolean sky = d.getSenses().time().canSeeSky();
		if (sky && !this.sawSky && now - this.skyLostAt >= SURFACE_AFTER_TICKS) {
			this.event(Event.SURFACED);
		}

		if (!sky && this.sawSky) {
			this.skyLostAt = now;
		}

		this.sawSky = sky;
		long time = Schedule.believedTime(d);
		long day = d.getSenses().time().believedDay();
		if (sky && time >= Schedule.SHELTER_URGENT && time < Schedule.NIGHT_START + 500 && this.duskCheckedDay != day) {
			this.duskCheckedDay = day;
			this.event(Event.DUSK);
		}

		if (d.getHealth() <= NEAR_DEATH_HEALTH) {
			this.nearDeathUntil = now + NEAR_DEATH_TICKS;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Choosing a tic
	// ---------------------------------------------------------------------------------------------

	private boolean ready(Kind kind, long now) {
		return this.readyAt.getOrDefault(kind, Long.MIN_VALUE) <= now && (kind != this.last || kind == Kind.GLANCE);
	}

	private boolean roll(double chance) {
		return this.drifter.getRandom().nextDouble() < chance;
	}

	/** Chance per check for something that should happen about once every {@code meanTicks}. */
	private static double every(double meanTicks) {
		return CHECK_TICKS / Math.max(CHECK_TICKS, meanTicks);
	}

	@Nullable
	private boolean reactiveOnly;

	private Tic choose(Set<BodyPart> free, long now) {
		Personality p = this.drifter.getPersonality();
		boolean walking = this.walking();
		if (this.reactiveOnly) {
			return walking && free.contains(BodyPart.HEAD) && this.ready(Kind.GLANCE, now) && this.somethingToLookAt(70.0F) != null
				&& this.roll(every(20 * 5.5)) ? new Glance() : null;
		}

		WaitTask wait = this.waiting();
		boolean head = free.contains(BodyPart.HEAD);
		boolean hands = free.contains(BodyPart.HANDS);
		boolean legs = free.contains(BodyPart.LEGS);
		// Section 16.6: rates scale with Fidget and mood.
		double fidget = 0.5 + p.get(Trait.FIDGET);

		// Events first (signature moments).
		if (this.pending.containsKey(Event.CELEBRATE)) {
			Tic celebration = switch (p.habits().celebration) {
				case CROUCH_SPAM -> legs && this.ready(Kind.CROUCH_SPAM, now) ? new CrouchSpam(4 + this.drifter.getRandom().nextInt(5)) : null;
				case SPIN -> head && !walking && this.ready(Kind.SPIN, now) ? new Spin() : null;
				case SWING -> hands && this.ready(Kind.SWING_AIR, now) ? new SwingAir(2 + this.drifter.getRandom().nextInt(3)) : null;
				case QUIET -> null;
			};
			if (celebration != null) {
				this.pending.remove(Event.CELEBRATE);
				return celebration;
			}
		}

		if (this.pending.containsKey(Event.NEW_GEAR) && head && !walking && this.ready(Kind.SPIN, now)) {
			this.pending.remove(Event.NEW_GEAR);
			return new Spin();
		}

		if ((this.pending.containsKey(Event.DUSK) || this.pending.containsKey(Event.SURFACED)) && head && this.ready(Kind.SKY_CHECK, now)) {
			boolean surfaced = this.pending.remove(Event.SURFACED) != null;
			this.pending.remove(Event.DUSK);
			// Coming up from a mine, a careful one also looks all the way round once the sky check is done.
			if (surfaced && this.roll(0.3 + 0.6 * p.get(Trait.CAUTION))) {
				this.pending.put(Event.FIGHT_OVER, now + EVENT_EXPIRY_TICKS);
			}

			return new SkyCheck();
		}

		if (this.pending.containsKey(Event.FIGHT_OVER) && head && !walking && this.ready(Kind.SWEEP, now)) {
			this.pending.remove(Event.FIGHT_OVER);
			return new Sweep();
		}

		// A rage fit (section 16.5) after a string of failures.
		if (head && hands && legs && !walking && p.is(Mood.FRUSTRATED) && p.mood().intensity() >= 0.7 && this.ready(Kind.RAGE, now)
			&& this.roll(every(20 * 20))) {
			return new Rage();
		}

		// Going AFK in a long wait (section 16.4: "stands still facing one way for 30 s or more").
		if (wait != null && head && hands && legs && wait.ticksRunning() >= AFK_AFTER_WAITING_TICKS && this.ready(Kind.AFK, now)) {
			double bored = p.mood().pressure(Mood.BORED);
			if (this.roll(every(20 * 90) * (0.5 + p.get(Trait.PATIENCE) + bored))) {
				return new Afk();
			}
		}

		boolean nearDeath = now < this.nearDeathUntil;
		boolean night = Schedule.isNight(Schedule.believedTime(this.drifter));
		boolean outside = this.drifter.getSenses().time().canSeeSky();
		if (head) {
			// Look-backs: walking alone at night, anxious, or in the 30 s after nearly dying.
			double lookBack = 0.0;
			if (nearDeath) {
				lookBack = every(20 * 4);
			} else if (p.is(Mood.ANXIOUS) || p.is(Mood.PANICKED)) {
				lookBack = every(20 * 8);
			} else if (night && outside) {
				lookBack = every(20 * 10) * (0.4 + 1.2 * p.get(Trait.CAUTION));
			}

			if (lookBack > 0.0 && this.ready(Kind.LOOK_BACK, now) && this.roll(lookBack)) {
				return new LookBack(nearDeath || p.is(Mood.PANICKED));
			}

			// Side glances while walking (section 16.1: every 3 to 8 s), toward a sound or a creature when there is one.
			if (walking && this.ready(Kind.GLANCE, now) && this.roll(every(20 * 5.5) * (0.6 + 0.4 * fidget))) {
				return new Glance();
			}

			if (!walking && this.ready(Kind.SWEEP, now) && this.roll(every(20 * 60) * (0.3 + p.get(Trait.CAUTION)) * fidget)) {
				return new Sweep();
			}

			if (!walking && (p.is(Mood.BORED) || p.is(Mood.TRIUMPHANT)) && this.ready(Kind.SPIN, now) && this.roll(every(20 * 60) * fidget)) {
				return new Spin();
			}
		}

		if (legs && wait != null && this.ready(Kind.JUMP_SPAM, now) && this.roll(every(20 * 3.5) * (1.4 - p.get(Trait.PATIENCE)) * fidget)) {
			return new JumpSpam(2 + this.drifter.getRandom().nextInt(4));
		}

		if (legs && !walking && p.is(Mood.TRIUMPHANT) && this.ready(Kind.CROUCH_SPAM, now) && this.roll(every(20 * 20) * fidget)) {
			return new CrouchSpam(4 + this.drifter.getRandom().nextInt(4));
		}

		if (hands) {
			if (this.drifter.canUseShield() && this.ready(Kind.SHIELD_RAISE, now) && this.nervous(night, outside, now)
				&& this.roll(every(20 * 12) * (0.4 + p.get(Trait.CAUTION)))) {
				return new ShieldRaise();
			}

			if (this.ready(Kind.HOTBAR_FIDGET, now) && this.roll(every(20 * 40) * fidget)) {
				return new HotbarFidget();
			}

			if (p.is(Mood.BORED) && this.ready(Kind.SWING_AIR, now) && this.roll(every(20 * 45) * fidget)) {
				return new SwingAir(1 + this.drifter.getRandom().nextInt(3));
			}
		}

		return null;
	}

	/** Night sounds (section 16.4 "nervous shield raise"): a monster it heard but can't see, or it's anxious. */
	private boolean nervous(boolean night, boolean outside, long now) {
		Personality p = this.drifter.getPersonality();
		if (p.is(Mood.ANXIOUS)) {
			return true;
		}

		if (!night || Threats.assess(this.drifter).decision() != Threats.Decision.NONE) {
			return false;
		}

		for (Awareness awareness : this.drifter.getSenses().tracked()) {
			if (awareness.entity instanceof Enemy && awareness.heardWithin(now, 60) && !awareness.visibleNow) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// The tics
	// ---------------------------------------------------------------------------------------------

	private abstract class Tic {
		final Kind kind;
		final Set<BodyPart> parts;
		int age;

		Tic(Kind kind, Set<BodyPart> parts) {
			this.kind = kind;
			this.parts = parts;
		}

		void start() {
		}

		/** One tick; false when it's done. */
		final boolean tick() {
			return this.step(this.age++);
		}

		abstract boolean step(int age);

		void stop() {
		}

		CameraModel camera() {
			return Mannerisms.this.drifter.getCamera();
		}

		VirtualKeys keys() {
			return Mannerisms.this.drifter.getKeys();
		}

		int between(int min, int max) {
			return Mth.nextInt(Mannerisms.this.drifter.getRandom(), min, max);
		}

		float yaw() {
			return Mannerisms.this.drifter.getYRot();
		}

		float pitch() {
			return Mannerisms.this.drifter.getXRot();
		}
	}

	/** A head-only tic that holds one look for a while, then hands the head back. */
	private abstract class HeadTic extends Tic {
		int length;

		HeadTic(Kind kind, int length) {
			super(kind, EnumSet.of(BodyPart.HEAD));
			this.length = length;
		}

		@Override
		boolean step(int age) {
			return age < this.length;
		}

		@Override
		void stop() {
			this.camera().releaseOverride();
		}
	}

	/** Section 16.1: a glance to the side while walking, toward a sound or a creature when there is one. */
	private final class Glance extends HeadTic {
		Glance() {
			super(Kind.GLANCE, Mth.nextInt(Mannerisms.this.drifter.getRandom(), 6, 14));
		}

		@Override
		void start() {
			Vec3 interesting = Mannerisms.this.somethingToLookAt(70.0F);
			if (interesting != null) {
				this.camera().override(interesting, CameraModel.TurnSpeed.NORMAL);
				return;
			}

			float side = Mannerisms.this.drifter.getRandom().nextBoolean() ? 1.0F : -1.0F;
			float yaw = this.yaw() + side * this.between(20, 50);
			float pitch = Mth.clamp(this.pitch() + this.between(-8, 10), -30.0F, 40.0F);
			this.camera().override(yaw, pitch, CameraModel.TurnSpeed.NORMAL);
		}
	}

	/** Section 16.4 "look-back glance": over the shoulder at night, when anxious, or after nearly dying. */
	private final class LookBack extends HeadTic {
		private final boolean quick;

		LookBack(boolean quick) {
			super(Kind.LOOK_BACK, Mth.nextInt(Mannerisms.this.drifter.getRandom(), 8, 14));
			this.quick = quick;
		}

		@Override
		void start() {
			float side = Mannerisms.this.drifter.getRandom().nextBoolean() ? 1.0F : -1.0F;
			this.camera().override(this.yaw() + side * this.between(150, 180), this.between(-5, 10),
				this.quick ? CameraModel.TurnSpeed.COMBAT : CameraModel.TurnSpeed.NORMAL);
		}
	}

	/** Section 16.4 "sky check": at dusk, and coming up from a mine. */
	private final class SkyCheck extends HeadTic {
		SkyCheck() {
			super(Kind.SKY_CHECK, Mth.nextInt(Mannerisms.this.drifter.getRandom(), 15, 25));
		}

		@Override
		void start() {
			this.camera().override(this.yaw() + this.between(-25, 25), -this.between(55, 80), CameraModel.TurnSpeed.NORMAL);
		}
	}

	/** Section 16.4 "360 camera sweep": a full turn of the head, standing still. */
	private final class Sweep extends HeadTic {
		private float startYaw;
		private int turn;
		private int hold;
		private final float direction;

		Sweep() {
			super(Kind.SWEEP, 20 * 6);
			this.direction = Mannerisms.this.drifter.getRandom().nextBoolean() ? 1.0F : -1.0F;
		}

		@Override
		void start() {
			this.startYaw = this.yaw();
			this.next();
		}

		private void next() {
			this.turn++;
			float pitch = this.between(0, 12);
			this.camera().override(this.startYaw + this.direction * 90.0F * this.turn, pitch, CameraModel.TurnSpeed.NORMAL);
			this.hold = this.between(6, 12);
		}

		@Override
		boolean step(int age) {
			if (age >= this.length) {
				return false;
			}

			if (this.camera().isSettled() && --this.hold <= 0) {
				if (this.turn >= 4) {
					return false;
				}

				this.next();
			}

			return true;
		}
	}

	/** Section 16.4 "spin in place": a quick full turn (celebrating, bored, new armor). */
	private final class Spin extends HeadTic {
		private float startYaw;
		private int turn;
		private final float direction;

		Spin() {
			super(Kind.SPIN, 40);
			this.direction = Mannerisms.this.drifter.getRandom().nextBoolean() ? 1.0F : -1.0F;
		}

		@Override
		void start() {
			this.startYaw = this.yaw();
			this.next();
		}

		private void next() {
			this.turn++;
			this.camera().override(this.startYaw + this.direction * 120.0F * this.turn, this.pitch(), CameraModel.TurnSpeed.COMBAT);
		}

		@Override
		boolean step(int age) {
			if (age >= this.length) {
				return false;
			}

			// Each third of the turn starts as soon as the head is most of the way through the last one.
			if (Math.abs(Mth.wrapDegrees(this.yaw() - (this.startYaw + this.direction * 120.0F * this.turn))) < 25.0F) {
				if (this.turn >= 3) {
					return false;
				}

				this.next();
			}

			return true;
		}
	}

	/** Section 16.4 "jump-spam": bunny hops while waiting (furnace, morning, a slow player). */
	private final class JumpSpam extends Tic {
		private int jumps;
		private boolean pressed;

		JumpSpam(int jumps) {
			super(Kind.JUMP_SPAM, EnumSet.of(BodyPart.LEGS));
			this.jumps = jumps;
		}

		@Override
		boolean step(int age) {
			DrifterEntity d = Mannerisms.this.drifter;
			BlockPos overhead = d.blockPosition().above(2);
			boolean room = d.level().getBlockState(overhead).getCollisionShape(d.level(), overhead).isEmpty();
			if (!room || age > 20 * 6) {
				return false;
			}

			if (this.pressed) {
				this.keys().set(VirtualKeys.Key.JUMP, false);
				this.pressed = false;
				return true;
			}

			if (d.onGround()) {
				if (this.jumps-- <= 0) {
					return false;
				}

				this.keys().set(VirtualKeys.Key.JUMP, true);
				this.pressed = true;
			}

			return true;
		}

		@Override
		void stop() {
			this.keys().set(VirtualKeys.Key.JUMP, false);
		}
	}

	/** Section 16.4 "crouch-spam": greeting, thanks, celebration. */
	private final class CrouchSpam extends Tic {
		private int toggles;
		private int wait;

		CrouchSpam(int presses) {
			super(Kind.CROUCH_SPAM, EnumSet.of(BodyPart.LEGS));
			this.toggles = presses * 2;
		}

		@Override
		boolean step(int age) {
			if (--this.wait > 0) {
				return true;
			}

			if (this.toggles-- <= 0) {
				return false;
			}

			this.keys().set(VirtualKeys.Key.SNEAK, !this.keys().isDown(VirtualKeys.Key.SNEAK));
			this.wait = this.between(2, 4);
			return true;
		}

		@Override
		void stop() {
			this.keys().set(VirtualKeys.Key.SNEAK, false);
		}
	}

	/** Section 16.4 "swing at air": after a kill, or bored. A swing at nothing resets the attack cooldown, as for a player. */
	private final class SwingAir extends Tic {
		private int swings;
		private int wait;

		SwingAir(int swings) {
			super(Kind.SWING_AIR, EnumSet.of(BodyPart.HANDS));
			this.swings = swings;
		}

		@Override
		boolean step(int age) {
			if (--this.wait > 0) {
				return true;
			}

			if (this.swings-- <= 0) {
				return false;
			}

			Mannerisms.this.drifter.swing(InteractionHand.MAIN_HAND);
			Mannerisms.this.drifter.resetAttackTimer();
			this.wait = this.between(3, 6);
			return true;
		}
	}

	/** Section 16.4 "hotbar scroll fidget": scrolls a few slots away and back. */
	private final class HotbarFidget extends Tic {
		private int original = -1;
		private int set = -1;
		private int notches;
		private int direction;
		private boolean returning;
		private int wait;

		HotbarFidget() {
			super(Kind.HOTBAR_FIDGET, EnumSet.of(BodyPart.HANDS));
		}

		@Override
		void start() {
			this.original = Mannerisms.this.drifter.getInventory().getSelected();
			this.notches = this.between(2, 5);
			this.direction = Mannerisms.this.drifter.getRandom().nextBoolean() ? 1 : -1;
		}

		@Override
		boolean step(int age) {
			DrifterInventory inventory = Mannerisms.this.drifter.getInventory();
			if (age > 20 * 4) {
				return false;
			}

			if (inventory.isSwitching() || --this.wait > 0) {
				return true;
			}

			int selected = inventory.getSelected();
			if (!this.returning && this.notches-- <= 0) {
				this.returning = true;
			}

			if (this.returning && selected == this.original) {
				return false;
			}

			int step = this.returning ? -this.direction : this.direction;
			this.set = Math.floorMod(selected + step, DrifterInventory.HOTBAR_SIZE);
			inventory.requestSelect(this.set);
			this.wait = this.between(1, 3);
			return true;
		}

		@Override
		void stop() {
			// Back to what it was holding, unless something else picked a slot meanwhile.
			DrifterInventory inventory = Mannerisms.this.drifter.getInventory();
			if (this.original >= 0 && (inventory.getSelected() == this.set || inventory.isSwitching()) && inventory.getSelected() != this.original) {
				inventory.requestSelect(this.original);
			}
		}
	}

	/** Section 16.4 "nervous shield raise": night sounds. */
	private final class ShieldRaise extends Tic {
		private final int length;

		ShieldRaise() {
			super(Kind.SHIELD_RAISE, EnumSet.of(BodyPart.HANDS));
			this.length = Mth.nextInt(Mannerisms.this.drifter.getRandom(), 10, 25);
		}

		@Override
		void start() {
			if (!Mannerisms.this.drifter.isUsingItem()) {
				Mannerisms.this.drifter.startUsingItem(InteractionHand.OFF_HAND);
			}
		}

		@Override
		boolean step(int age) {
			return age < this.length && Mannerisms.this.drifter.canUseShield();
		}

		@Override
		void stop() {
			if (Mannerisms.this.drifter.isUsingItem() && com.dinglebob.drifter.hands.Shields.isShield(Mannerisms.this.drifter.getUseItem())) {
				Mannerisms.this.drifter.stopUsingItem();
			}
		}
	}

	/** Section 16.4 "going AFK": long waits, standing still facing one way for 30 s or more. */
	private final class Afk extends Tic {
		private final int length;

		Afk() {
			super(Kind.AFK, EnumSet.allOf(BodyPart.class));
			this.length = Mth.nextInt(Mannerisms.this.drifter.getRandom(), 20 * 30, 20 * 75);
		}

		@Override
		void start() {
			this.camera().override(this.yaw(), this.pitch(), CameraModel.TurnSpeed.IDLE);
		}

		@Override
		boolean step(int age) {
			return age < this.length;
		}

		@Override
		void stop() {
			this.camera().releaseOverride();
		}
	}

	/** Section 16.5 "Rage": a rapid spin, punching the air, angry crouch-spam. */
	private final class Rage extends Tic {
		private float startYaw;
		private int phase;
		private int count;
		private int wait;

		Rage() {
			super(Kind.RAGE, EnumSet.allOf(BodyPart.class));
		}

		@Override
		void start() {
			this.startYaw = this.yaw();
			String why = Mannerisms.this.drifter.getPersonality().mood().reason();
			Mannerisms.this.drifter.getMemory().thoughts().add(Mannerisms.this.now(), why.isEmpty() ? "rage" : "rage: " + why);
		}

		@Override
		boolean step(int age) {
			if (age > 20 * 8) {
				return false;
			}

			if (--this.wait > 0) {
				return true;
			}

			switch (this.phase) {
				case 0 -> {
					// Rapid spin: two full turns in thirds.
					this.count++;
					this.camera().override(this.startYaw + 120.0F * this.count, this.between(-10, 20), CameraModel.TurnSpeed.COMBAT);
					this.wait = 4;
					if (this.count >= 6) {
						this.phase = 1;
						this.count = 0;
					}
				}
				case 1 -> {
					Mannerisms.this.drifter.swing(InteractionHand.MAIN_HAND);
					Mannerisms.this.drifter.resetAttackTimer();
					this.wait = 3;
					if (++this.count >= 5) {
						this.phase = 2;
						this.count = 0;
					}
				}
				default -> {
					this.keys().set(VirtualKeys.Key.SNEAK, !this.keys().isDown(VirtualKeys.Key.SNEAK));
					this.wait = 2;
					if (++this.count >= 10) {
						return false;
					}
				}
			}

			return true;
		}

		@Override
		void stop() {
			this.camera().releaseOverride();
			this.keys().set(VirtualKeys.Key.SNEAK, false);
			// Getting it out of its system takes the edge off.
			Mannerisms.this.drifter.getPersonality().mood().ease(Mood.FRUSTRATED, 0.5);
		}
	}

	/**
	 * Something worth a glance: a creature it heard but can't see, or one it can see off to the side, within
	 * {@code maxTurn} degrees of where it's heading (a bigger turn while walking would throw its steering).
	 */
	@Nullable
	private Vec3 somethingToLookAt(float maxTurn) {
		long now = this.now();
		Vec3 eye = this.drifter.getEyePosition();
		List<Vec3> options = new ArrayList<>();
		for (Awareness awareness : this.drifter.getSenses().tracked()) {
			Vec3 point = null;
			if (awareness.heardWithin(now, 40) && !awareness.visibleNow) {
				point = awareness.heardPos;
			} else if (awareness.visibleNow && awareness.entity.distanceTo(this.drifter) < 24.0) {
				point = awareness.entity.getEyePosition();
			}

			if (point == null) {
				continue;
			}

			float yawTo = (float)(Mth.atan2(point.z - eye.z, point.x - eye.x) * Mth.RAD_TO_DEG) - 90.0F;
			float turn = Math.abs(Mth.wrapDegrees(yawTo - this.drifter.getYRot()));
			// Not something it's already looking straight at.
			if (turn <= maxTurn && turn > 10.0F) {
				options.add(point);
			}
		}

		return options.isEmpty() ? null : options.get(this.drifter.getRandom().nextInt(options.size()));
	}

	/** For tests and the overlay: the cooldowns still running. */
	public Map<Kind, Long> cooldowns() {
		return this.readyAt;
	}
}
