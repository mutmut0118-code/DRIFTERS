package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.Combat;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Attack (DESIGN.md sections 8 and 13): chases a living target and fights it until it dies, the way a player who
 * knows 1.9+ combat does:
 * <ul>
 *   <li>takes out its best weapon first (most damage per second);</li>
 *   <li>never spam-clicks: swings only at full attack strength (the cooldown comes from the weapon's attack speed);</li>
 *   <li>critical hits (1.5x damage) with slow weapons: jump as the cooldown is about to refill, let go of sprint in
 *       the air (a sprinting hit can't crit), hit on the way down;</li>
 *   <li>a plain full-strength hit when a jump isn't possible or with a fast item (fist, block);</li>
 *   <li>per-mob protocols (section 13): animals are just chased; melee mobs get hit, then it steps back out of reach
 *       while the cooldown refills, strafing, shield up; archers are approached in a zigzag; creepers get one
 *       sprint-knockback hit, then it backs well off before they can blow.</li>
 * </ul>
 * Far away or out of sight it follows with the pathfinder; close up it steers with the keys, camera on the target.
 * Hits only land under the crosshair within 3 blocks (player reach).
 * Fighting style and timing come from the personality (section 13 "Fighting styles", section 17 Skill): a brawler
 * runs in and trades hits, a duelist keeps its spacing, a turtle keeps its shield up; low Skill swings a little early,
 * goes for fewer crits and sometimes whiffs, and panic turns it into spam-clicking. (Cowards are decided in
 * {@link com.dinglebob.drifter.brain.Threats}; archers need bows, a later milestone.)
 */
public class AttackTask extends Task {
	/** Section 13 fighting styles that apply to melee. */
	public enum Style {
		/** Runs in and trades hits; uses sprint-knockback. */
		BRAWLER,
		/** Controls spacing, waits for full cooldown, times crits, strafes. */
		DUELIST,
		/** Shield up most of the time, hits in the gaps. */
		TURTLE;

		public static Style of(DrifterEntity drifter) {
			com.dinglebob.drifter.personality.Personality p = drifter.getPersonality();
			if (p.base(com.dinglebob.drifter.personality.Trait.CAUTION) > 0.62 && drifter.canUseShield()) {
				return TURTLE;
			}

			if (p.base(com.dinglebob.drifter.personality.Trait.AGGRESSION) > 0.62 && p.base(com.dinglebob.drifter.personality.Trait.SKILL) < 0.65) {
				return BRAWLER;
			}

			return DUELIST;
		}
	}

	/** How it fights this target (section 13, "Per-mob protocols"). */
	public enum Protocol {
		/** An animal: chase and hit. */
		PREY,
		/** A melee mob: hit, step back out of reach while the cooldown refills, strafe; shield up while backing off. */
		MELEE,
		/** An archer: zigzag in (shield up while it draws), then melee. */
		RANGED,
		/** A creeper: one sprint-knockback hit, then back well off before it can blow; repeat. */
		CREEPER;

		public static Protocol of(LivingEntity target) {
			if (target instanceof Creeper) {
				return CREEPER;
			}

			// Blazes fight from range too (their fireball bursts): shield up while one glows, as for an archer drawing.
			if (target instanceof net.minecraft.world.entity.monster.Blaze) {
				return RANGED;
			}

			// A drowned only shoots with a trident.
			if (target instanceof RangedAttackMob && !(target instanceof Drowned drowned && !drowned.getMainHandItem().is(Items.TRIDENT))) {
				return RANGED;
			}

			// (Another Drifter fights back like a player: footwork, shield and bow, not a chase after an animal.)
			return target instanceof Enemy || target instanceof com.dinglebob.drifter.entity.DrifterEntity || target instanceof Mob mob && mob.isAggressive()
				? MELEE : PREY;
		}
	}

	/** Blazes glowing this close are worth holding the shield up for. */
	private static final double BLAZE_GUARD_RANGE = 24.0;
	private long blazeGuardUntil;

	/** Closer than this (and in sight) it steers by hand instead of pathfinding. */
	private static final double CLOSE_RANGE = 5.0;
	/** It stops pressing forward this close, so it doesn't run into the target. */
	private static final double STOP_RANGE = 1.8;
	/** It jumps for a crit this close (horizontal, feet to the target's middle), so it's still in reach coming down. */
	private static final double JUMP_RANGE = 2.6;
	/** It jumps this many ticks before the cooldown is full: about the time from take-off to falling past the top. */
	private static final float JUMP_LEAD_TICKS = 7.0F;
	/**
	 * Crits only pay off with slow weapons: a jump takes about 12 ticks, so with a fist or a block (full strength every
	 * 5 ticks) plain hits do more damage per second. A sword (12.5 ticks) or axe (25) crits every jump.
	 */
	private static final float CRIT_MIN_DELAY_TICKS = 10.0F;
	/** Lost from sight this long, it gives up. */
	private static final int LOST_TICKS = 20 * 6;
	/** Vanilla: a hit stronger than 0.9 counts as full strength (crits and sweeps need it). */
	private static final float FULL_STRENGTH = 0.92F;
	/** Melee spacing: it hovers between these distances while the cooldown refills (section 13, "Spacing"). */
	private static final double HOVER_MIN = 2.4;
	private static final double HOVER_MAX = 3.3;
	/** It steps in to hit once the cooldown is this full. */
	private static final float STEP_IN_STRENGTH = 0.6F;
	/** Ticks it backs off after a hit (creepers: until well clear of the blast). */
	private static final int BACKOFF_TICKS = 7;
	private static final int CREEPER_BACKOFF_TICKS = 30;
	private static final double CREEPER_SAFE_DISTANCE = 5.0;
	/** Strafe direction changes every 0.5 to 2 s (section 13, "Strafing"); zigzag legs toward archers. */
	private static final int STRAFE_MIN_TICKS = 10;
	private static final int STRAFE_MAX_TICKS = 40;
	private static final int ZIGZAG_MIN_TICKS = 8;
	private static final int ZIGZAG_MAX_TICKS = 14;

	private final LivingEntity target;
	private final String label;
	private final Protocol protocol;
	private final Style style;
	/** Rolled per swing: does it go for a crit this time (low Skill often doesn't bother). */
	private boolean wantsCrit = true;
	@Nullable
	private NavigateTask follow;
	@Nullable
	private SelectItemTask selectWeapon;
	private boolean weaponChecked;
	private int unseenTicks;
	private boolean closeMode;
	/** A crit jump is under way; if it lands without hitting, the next hit is a plain one. */
	private boolean critJump;
	private boolean missedCrit;
	private int backoff;
	/** Ticks left of letting the pathfinder lead (around a wall it can't jump). */
	private int pathTicks;
	private static final int PATH_AROUND_TICKS = 40;
	private int strafeTicks;
	private boolean strafeLeft;
	/** The camera aim it set; a reflex taking the head elsewhere changes the camera's token, so it aims again. */
	private int lookToken = -1;
	/** The weapon it took out; if something else ends up in its hand (a reflex used a block), it switches back. */
	@Nullable
	private ItemStack weapon;

	public AttackTask(DrifterEntity drifter, LivingEntity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
		this.protocol = Protocol.of(target);
		this.style = Style.of(drifter);
	}

	public Style style() {
		return this.style;
	}

	private float skill() {
		return this.drifter.getPersonality().getF(com.dinglebob.drifter.personality.Trait.SKILL);
	}

	/**
	 * How full the cooldown must be before it swings: about 0.95 for a skilled player, 0.8 for a clumsy one (a little
	 * early, a little weaker, no crit); a panicking one spam-clicks (section 17, Panicked).
	 */
	private float swingStrength() {
		if (this.drifter.getPersonality().is(com.dinglebob.drifter.personality.Mood.PANICKED)) {
			return 0.35F;
		}

		return Math.max(FULL_STRENGTH - 0.12F, Mth.lerp(this.skill(), FULL_STRENGTH - 0.12F, FULL_STRENGTH + 0.03F));
	}

	@Override
	public String describe() {
		return "attack the " + this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60;
	}

	public LivingEntity target() {
		return this.target;
	}

	/** Within this (eyes to its middle) it fights whether it can see it or not; within the other, even with no path. */
	private static final double TURN_AND_FIGHT = 3.5;
	private static final double NO_PATH_FIGHT = 5.0;
	/** After a failed path, it steers by hand this long before looking for a path again. */
	private static final int NO_PATH_RETRY_TICKS = 40;
	private int noPathTicks;

	@Override
	protected Status onTick() {
		if (!this.target.isAlive() || this.target.isRemoved()) {
			return Status.SUCCESS;
		}

		// Weapon first: like a player switching to the sword before a fight (and back to it if a reflex changed the item).
		if (this.weaponChecked && this.weapon != null && this.drifter.getMainHandItem() != this.weapon && !this.weapon.isEmpty()) {
			this.weaponChecked = false;
		}

		if (!this.weaponChecked) {
			Status selecting = this.selectWeapon();
			if (selecting != null) {
				return selecting;
			}
		}

		Awareness awareness = this.drifter.getSenses().awarenessOf(this.target);
		boolean visible = awareness != null && awareness.visibleNow;
		// A player or Drifter it's fighting: it keeps after them when they get out of sight (the pursuit goal).
		if (visible && (this.target instanceof net.minecraft.world.entity.player.Player || this.target instanceof com.dinglebob.drifter.entity.DrifterEntity)) {
			this.drifter.getDrifterBrain().pursue(this.drifter, this.target, this.drifter.level().getGameTime());
		}

		this.unseenTicks = visible ? 0 : this.unseenTicks + 1;
		if (this.unseenTicks > LOST_TICKS) {
			return this.fail("lost sight of it");
		}

		double distance = this.drifter.getEyePosition().distanceTo(this.target.getBoundingBox().getCenter());
		// A unique weapon's ability (Simply Swords; its data entry says when), right-clicked as a player would. While it
		// holds a charge it stands and aims.
		if (visible && com.dinglebob.drifter.hands.UniqueAbility.tick(this.drifter, this.target, distance)) {
			this.aim();
			Steering.stopMoving(this.drifter);
			return Status.RUNNING;
		}
		// Up close it steers by hand; a wall in the way it can't jump (a 2-block step on a hill) means the pathfinder
		// finds the way round for a couple of seconds (found in testing: it died jumping at such a step mid-fight).
		Vec3 toTarget = new Vec3(this.target.getX() - this.drifter.getX(), 0.0, this.target.getZ() - this.drifter.getZ());
		if (this.closeMode && this.backoff == 0 && this.drifter.horizontalCollision && toTarget.lengthSqr() > 1.0E-4
			&& Steering.wallAhead(this.drifter, toTarget.normalize())) {
			this.pathTicks = PATH_AROUND_TICKS;
		}

		if (this.pathTicks > 0) {
			this.pathTicks--;
		}

		// In reach, seen or not (it was just hit from behind or below): it turns round and fights, like a player, instead of
		// pathing (found in a user's test: "can't get to it" with a zombie hitting it from behind, until it died).
		boolean inReach = distance <= TURN_AND_FIGHT;
		if (this.noPathTicks > 0) {
			this.noPathTicks--;
		}

		boolean steerByHand = this.noPathTicks > 0 && distance <= NO_PATH_FIGHT;
		if ((distance > CLOSE_RANGE || !visible && !inReach || this.pathTicks > 0) && this.backoff == 0 && !steerByHand) {
			this.lowerShield();
			if (this.closeMode || this.follow == null) {
				this.closeMode = false;
				this.follow = NavigateTask.follow(this.drifter, this.target, 1.5);
			}

			Status status = this.follow.tick();
			if (status == Status.FAILURE) {
				// No way round, but it's right there: fight from where it stands.
				if (distance <= NO_PATH_FIGHT) {
					this.follow = null;
					this.pathTicks = 0;
					this.noPathTicks = NO_PATH_RETRY_TICKS;
					return Status.RUNNING;
				}

				return this.fail("can't get to it: " + this.follow.failureReason());
			}

			return Status.RUNNING;
		}

		if (!this.closeMode) {
			this.closeMode = true;
			if (this.follow != null) {
				this.follow.stop();
				this.follow = null;
			}

			this.aim();
		} else if (this.drifter.getCamera().targetToken() != this.lookToken) {
			// Something else (a reflex) turned its head: back onto the target.
			this.aim();
		}

		this.fight(distance);
		return Status.RUNNING;
	}

	private void aim() {
		this.drifter.getCamera().lookAt(this.target, CameraModel.TurnSpeed.COMBAT);
		this.lookToken = this.drifter.getCamera().targetToken();
	}

	/** Returns a status while still switching to its weapon, or null once it holds it. */
	@Nullable
	private Status selectWeapon() {
		if (this.selectWeapon == null) {
			int slot = Combat.bestWeaponSlot(this.drifter);
			ItemStack weapon = slot < 0 ? ItemStack.EMPTY : this.drifter.getInventory().getItem(slot);
			this.weapon = weapon;
			if (weapon.isEmpty() || weapon == this.drifter.getMainHandItem()) {
				this.weaponChecked = true;
				return null;
			}

			this.selectWeapon = new SelectItemTask(this.drifter, stack -> stack == weapon, weapon.getHoverName().getString());
		}

		Status status = this.selectWeapon.tick();
		if (status == Status.RUNNING) {
			return Status.RUNNING;
		}

		// Got it out (or couldn't: it fights with what it holds).
		this.selectWeapon.stop();
		this.selectWeapon = null;
		this.weaponChecked = true;
		return null;
	}

	private void fight(double distance) {
		VirtualKeys keys = this.drifter.getKeys();
		Vec3 at = this.target.position();
		double dx = at.x - this.drifter.getX();
		double dz = at.z - this.drifter.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		boolean onGround = this.drifter.onGround();
		float strength = Combat.attackStrength(this.drifter);
		float ticksToFull = (1.0F - strength) * Combat.attackDelay(this.drifter);
		boolean fightingMob = this.protocol != Protocol.PREY;

		// A creeper starting to hiss: get clear first (the reflex layer does the sprint; this keeps it from stepping back in).
		if (this.protocol == Protocol.CREEPER && this.target instanceof Creeper creeper && creeper.getSwellDir() > 0) {
			this.backoff = Math.max(this.backoff, CREEPER_BACKOFF_TICKS);
		}

		// A blaze lit up to fire its burst (and a little after): shield up and held, facing it, no swings (a swing lowers the
		// shield, and it only blocks once it has been up a quarter second; found in testing: it raised it for a tick or two
		// between swings and took every burst). It hits in between bursts.
		long now = this.drifter.level().getGameTime();
		if (this.hasShield() && com.dinglebob.drifter.hands.Archery.glowing(this.drifter, this.target)) {
			this.blazeGuardUntil = now + com.dinglebob.drifter.hands.Archery.BLAZE_GUARD_AFTER_TICKS;
		}

		// (In reach with a full swing ready, it strikes anyway, and the shield goes back up after.)
		boolean strike = strength >= FULL_STRENGTH && Combat.canHit(this.drifter, this.target);
		if (this.hasShield() && now < this.blazeGuardUntil && !strike) {
			Steering.stopMoving(this.drifter);
			keys.set(VirtualKeys.Key.SPRINT, false);
			keys.set(VirtualKeys.Key.JUMP, false);
			this.guardFootwork(at);
			this.shieldIf(true);
			return;
		}

		if (fightingMob && this.backoff > 0) {
			this.backOff(distance);
			return;
		}

		if (--this.strafeTicks <= 0) {
			this.strafeLeft = this.drifter.getRandom().nextBoolean();
			this.strafeTicks = this.protocol == Protocol.RANGED && distance > this.scaled(HOVER_MAX)
				? Mth.nextInt(this.drifter.getRandom(), ZIGZAG_MIN_TICKS, ZIGZAG_MAX_TICKS)
				: Mth.nextInt(this.drifter.getRandom(), STRAFE_MIN_TICKS, STRAFE_MAX_TICKS);
		}

		// A brawler doesn't hover: it stays in and trades hits.
		boolean stepIn = !fightingMob || strength >= STEP_IN_STRENGTH || this.style == Style.BRAWLER;
		if (!stepIn) {
			// Waiting for the cooldown: hover at the edge of reach, circling.
			if (distance < this.scaled(HOVER_MIN)) {
				this.stepBack(at);
			} else if (distance > this.scaled(HOVER_MAX)) {
				Steering.pressToward(this.drifter, at);
			} else {
				Steering.stopMoving(this.drifter);
			}

			this.strafe(keys);
			this.guardFootwork(at);
			keys.set(VirtualKeys.Key.SPRINT, false);
			keys.set(VirtualKeys.Key.JUMP, false);
			// Shield up while an archer draws or a mob is about to swing in reach (a turtle keeps it up all the while).
			this.shieldIf(this.threatening(distance) || this.style == Style.TURTLE);
			return;
		}

		this.lowerShield();
		if (distance > this.scaled(STOP_RANGE)) {
			Steering.pressToward(this.drifter, at);
			if (this.protocol == Protocol.RANGED && distance > this.scaled(HOVER_MAX)) {
				// Zigzag in toward an archer.
				this.strafe(keys);
			}
		} else {
			Steering.stopMoving(this.drifter);
		}

		if (onGround) {
			if (this.critJump) {
				// Landed without getting the crit in: take the next plain hit instead of jumping forever.
				this.critJump = false;
				this.missedCrit = true;
			}

			// Sprint in to close the gap (and, at a creeper, for the knockback hit); it lets go of sprint in the air for crits.
			boolean sprint = keys.isDown(VirtualKeys.Key.FORWARD) && (horizontal > this.scaled(JUMP_RANGE) || this.protocol == Protocol.CREEPER)
				&& this.drifter.getFoodData().getFoodLevel() > FoodConstants.SPRINT_LEVEL;
			// An edge in front (fighting on a bridge or a ledge): it sneaks forward, no sprint, no crit jumps, so it can't walk
			// off (found by the user: imNiftyshield stepped off its bridge after a zombie into the lava below).
			boolean edgeAhead = keys.isDown(VirtualKeys.Key.FORWARD) && !this.safeStep(Vec3.directionFromRotation(0.0F, this.drifter.getYRot()));
			if (edgeAhead) {
				keys.set(VirtualKeys.Key.SNEAK, true);
				sprint = false;
			}

			keys.set(VirtualKeys.Key.SPRINT, sprint);
			boolean jump = !edgeAhead && !this.missedCrit && this.wantsCrit && this.canJump() && horizontal <= this.scaled(JUMP_RANGE) && ticksToFull <= JUMP_LEAD_TICKS;
			keys.set(VirtualKeys.Key.JUMP, jump || this.drifter.horizontalCollision);
			if (jump) {
				this.critJump = true;
				return;
			}

			// A plain hit when it can't (or failed to) crit, at full strength only.
			if ((this.missedCrit || !this.wantsCrit || !this.canJump()) && strength >= this.swingStrength() && Combat.canHit(this.drifter, this.target)) {
				this.hit();
			}

			return;
		}

		// In the air: no sprinting (a sprint hit can't crit). Letting go of forward for a tick ends the sprint, as for a
		// player; it keeps its momentum.
		keys.set(VirtualKeys.Key.JUMP, false);
		keys.set(VirtualKeys.Key.SPRINT, false);
		if (this.drifter.isSprinting()) {
			Steering.stopMoving(this.drifter);
		}

		boolean falling = this.drifter.fallDistance > 0.0F && this.drifter.getDeltaMovement().y < 0.0;
		if (falling && !this.drifter.isSprinting() && strength >= Math.max(FULL_STRENGTH, this.swingStrength()) && Combat.canHit(this.drifter, this.target)) {
			this.hit();
		}
	}

	/** It has thought about rolling back for this back-off already. */
	private boolean rollTried;

	/** After a hit: step back out of reach while the cooldown refills (shield up), strafing; far back from a creeper. */
	private void backOff(double distance) {
		VirtualKeys keys = this.drifter.getKeys();
		this.backoff--;
		if (this.protocol == Protocol.CREEPER && distance < CREEPER_SAFE_DISTANCE) {
			this.backoff = Math.max(this.backoff, 1);
		}

		Vec3 at = this.target.position();
		// With Combat Roll, now and then it rolls back out of reach after a hit instead of stepping (the skilled more
		// often), onto safe ground only.
		Vec3 away = this.drifter.position().subtract(at);
		if (!this.rollTried && com.dinglebob.drifter.compat.CombatRollCompat.canRoll(this.drifter) && away.horizontalDistanceSqr() > 1.0E-4) {
			this.rollTried = true;
			Vec3 back = new Vec3(away.x, 0.0, away.z).normalize();
			double skill = this.drifter.getPersonality().getF(com.dinglebob.drifter.personality.Trait.SKILL);
			if (this.drifter.getRandom().nextDouble() < 0.25 + 0.5 * skill && Steering.safeStep(this.drifter, back) && Steering.safeStep(this.drifter, back, 1.9)) {
				com.dinglebob.drifter.compat.CombatRollCompat.roll(this.drifter, back);
			}
		}

		this.stepBack(at);
		this.strafe(keys);
		this.guardFootwork(at);
		keys.set(VirtualKeys.Key.SPRINT, false);
		keys.set(VirtualKeys.Key.JUMP, this.drifter.horizontalCollision && this.drifter.onGround() && !keys.isDown(VirtualKeys.Key.SNEAK));
		this.shieldIf(distance < 4.0);
	}

	private void stepBack(Vec3 from) {
		Steering.pressToward(this.drifter, this.drifter.position().subtract(from).scale(2.0).add(from));
	}

	/**
	 * Fighting footwork never backs or strafes it off a cliff or into lava (found in review): if the step behind or to
	 * the side isn't safe it doesn't take it (strafing the other way), and sneaks so it can't slip over an edge.
	 */
	private void guardFootwork(Vec3 from) {
		VirtualKeys keys = this.drifter.getKeys();
		Vec3 here = this.drifter.position();
		Vec3 back = new Vec3(here.x - from.x, 0.0, here.z - from.z);
		if (back.lengthSqr() < 1.0E-4) {
			return;
		}

		back = back.normalize();
		Vec3 right = new Vec3(back.z, 0.0, -back.x);
		boolean unsafeBack = !this.safeStep(back);
		boolean pressingBack = keys.isDown(VirtualKeys.Key.BACK) || !keys.isDown(VirtualKeys.Key.FORWARD) && unsafeBack;
		if (unsafeBack && pressingBack) {
			keys.set(VirtualKeys.Key.BACK, false);
			keys.set(VirtualKeys.Key.FORWARD, false);
		}

		// Strafe keys are relative to where it faces (the target).
		if (keys.isDown(VirtualKeys.Key.LEFT) && !this.safeStep(right.scale(-1.0)) || keys.isDown(VirtualKeys.Key.RIGHT) && !this.safeStep(right)) {
			this.strafeLeft = !this.strafeLeft;
			this.strafe(keys);
		}

		keys.set(VirtualKeys.Key.SNEAK, unsafeBack || !this.safeStep(right) || !this.safeStep(right.scale(-1.0)));
	}

	private boolean safeStep(Vec3 direction) {
		return Steering.safeStep(this.drifter, direction);
	}

	private void strafe(VirtualKeys keys) {
		keys.set(VirtualKeys.Key.LEFT, this.strafeLeft);
		keys.set(VirtualKeys.Key.RIGHT, !this.strafeLeft);
	}

	/** An archer drawing its bow, or a mob close enough to swing at it. */
	private boolean threatening(double distance) {
		// (A blaze about to fire glows: Blaze#isOnFire is its "charged" flag, the fire you see on it.)
		return this.protocol == Protocol.RANGED && (this.target.isUsingItem()
			|| this.target instanceof net.minecraft.world.entity.monster.Blaze && this.target.isOnFire()) || distance < 3.0;
	}

	/**
	 * A spacing distance for the weapon in its hand: the player-reach distances scaled to its reach (a Better Combat
	 * spear or claymore lets it stand further off; a dagger brings it in close).
	 */
	private double scaled(double distance) {
		return distance * com.dinglebob.drifter.hands.Combat.reach(this.drifter) / 3.0;
	}

	private boolean hasShield() {
		// (Not with a two-handed Better Combat weapon out: both hands are on it.)
		return this.drifter.canUseShield() && !com.dinglebob.drifter.compat.BetterCombatCompat.twoHanded(this.drifter.getMainHandItem());
	}

	private void shieldIf(boolean raise) {
		if (raise && this.hasShield()) {
			if (!this.drifter.isUsingItem()) {
				this.drifter.startUsingItem(InteractionHand.OFF_HAND);
			}
		} else {
			this.lowerShield();
		}
	}

	private void lowerShield() {
		if (this.drifter.isUsingItem() && this.drifter.getUseItem().is(Items.SHIELD)) {
			this.drifter.stopUsingItem();
		}
	}

	private void hit() {
		// Low Skill sometimes whiffs: a swing at the air beside it, which resets the cooldown like a player's miss.
		float whiff = (1.0F - this.skill()) * 0.12F + (this.drifter.getPersonality().is(com.dinglebob.drifter.personality.Mood.PANICKED) ? 0.15F : 0.0F);
		if (this.drifter.getRandom().nextFloat() < whiff) {
			this.drifter.swing(InteractionHand.MAIN_HAND);
			this.drifter.resetAttackTimer();
			this.critJump = false;
			this.missedCrit = false;
			this.rollCrit();
			return;
		}

		if (Combat.attack(this.drifter, this.target)) {
			this.rollCrit();
			if (!this.target.isAlive()) {
				this.drifter.getMannerisms().onKill(this.target);
			}

			this.critJump = false;
			this.missedCrit = false;
			if (this.protocol != Protocol.PREY) {
				// A brawler stays in and trades hits; a turtle backs off longer behind its shield.
				this.backoff = this.protocol == Protocol.CREEPER ? CREEPER_BACKOFF_TICKS
					: this.style == Style.BRAWLER ? 0
					: BACKOFF_TICKS + this.drifter.getRandom().nextInt(4) + (this.style == Style.TURTLE ? 4 : 0);
				this.strafeLeft = this.drifter.getRandom().nextBoolean();
				this.rollTried = false;
			}
		}
	}

	/** Whether the next swing goes for a crit: always at high Skill, about a third of the time at the lowest. */
	private void rollCrit() {
		this.wantsCrit = this.drifter.getRandom().nextFloat() < Mth.lerp(this.skill(), 0.35F, 1.05F);
	}

	/** Worth jumping for a crit: a slow enough weapon, room to jump, not in water or on a ladder, not at a creeper. */
	private boolean canJump() {
		if (this.protocol == Protocol.CREEPER || Combat.attackDelay(this.drifter) < CRIT_MIN_DELAY_TICKS) {
			return false;
		}

		Level level = this.drifter.level();
		BlockPos overhead = this.drifter.blockPosition().above(2);
		return level.getBlockState(overhead).getCollisionShape(level, overhead).isEmpty() && !this.drifter.isInWater()
			&& !this.drifter.onClimbable();
	}

	@Override
	protected void onStop() {
		if (this.follow != null) {
			this.follow.stop();
			this.follow = null;
		}

		if (this.selectWeapon != null) {
			this.selectWeapon.stop();
			this.selectWeapon = null;
		}

		this.lowerShield();
		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().clearTarget();
	}
}
