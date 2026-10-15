package com.dinglebob.drifter.reflex;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.mixin.AbstractArrowAccessor;
import com.dinglebob.drifter.executor.task.MineTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.Steering;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The reflex layer (DESIGN.md section 15, and section 8 layer 5): hazard reactions that read perception directly,
 * take over the body for a few ticks and then hand it back to whatever task was running (the executor and the brain
 * pause meanwhile). Each reacts after a human delay (150 to 400 ms, longer for a hazard behind it) and sometimes late
 * on purpose ("Misses on purpose").
 *
 * <p>Priority, highest first: in lava, drowning, on fire, a creeper about to blow, lit TNT, lava in the way, a heavy fall,
 * an incoming arrow or fireball, suffocating, falling sand or gravel, stuck in a cobweb, touching a cactus or berry
 * bush. Reaction times and the chance to react late come from the Skill trait (milestone 7).
 */
public class Reflexes {
	// ----- Tuning (section 15; tunable, meant for a data file later) -----
	/** Reaction delay: 150 to 400 ms. */
	public static final int MIN_DELAY_TICKS = 3;
	public static final int MAX_DELAY_TICKS = 8;
	/** Extra delay when the hazard is behind it (it has to turn or hear it first). */
	public static final int BEHIND_EXTRA_TICKS = 3;
	/** Chance to react late (Skill 0.5 stand-in), and how much later. */
	public static final float LATE_CHANCE = 0.15F;
	public static final int LATE_MIN_TICKS = 4;
	public static final int LATE_MAX_TICKS = 8;
	/** After a reflex ends, it can't fire again for this long (no twitching back and forth). */
	public static final int REFRACTORY_TICKS = 10;

	/** One reflex: a hazard it can notice, and what the body does about it. */
	public abstract static class Reflex {
		private long readyAt;

		public abstract String name();

		/** Is the hazard here right now? (May remember details for {@link #act}.) */
		protected abstract boolean detect(DrifterEntity drifter);

		/** Where the hazard is, to tell whether it's behind it; null if it's all around (in lava). */
		@Nullable
		protected Vec3 where(DrifterEntity drifter) {
			return null;
		}

		/** Longest the reflex holds the body. */
		protected int maxTicks() {
			return 40;
		}

		protected void start(DrifterEntity drifter) {
		}

		/** One tick of the reaction; false when it's done. */
		protected abstract boolean act(DrifterEntity drifter, int tick);

		protected void stop(DrifterEntity drifter) {
		}
	}

	private final DrifterEntity drifter;
	private final List<Reflex> reflexes;
	@Nullable
	private Reflex active;
	private int activeTicks;
	@Nullable
	private Reflex pending;
	private int pendingDelay;
	private long contactUntil = -1;
	@Nullable
	private Vec3 contactFrom;
	/** The hotbar slot it held when the reflex started: given back afterwards (a reflex may take out a block). */
	private int heldSlot = -1;

	public Reflexes(DrifterEntity drifter) {
		this.drifter = drifter;
		this.reflexes = List.of(new InLava(), new Drowning(), new OnFire(), new DouseWithBucket(), new CreeperFuse(), new LitTnt(), new LavaInTheWay(), new HeavyFall(),
			new IncomingProjectile(), new Suffocating(), new Freezing(), new FallingBlockAbove(), new InCobweb(), new Contact());
	}

	@Nullable
	public Reflex active() {
		return this.active;
	}

	/** Damage it took: a cactus or berry bush prick triggers the step-away reflex (for the next half second). */
	public void onHurt(DamageSource source) {
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
			this.contactUntil = this.drifter.level().getGameTime() + 10;
			this.contactFrom = source.getSourcePosition();
		}
	}

	/** @return true while a reflex has the body (the brain and the executor wait). */
	public boolean tick() {
		long now = this.drifter.level().getGameTime();
		if (this.active != null) {
			boolean going = ++this.activeTicks <= this.active.maxTicks() && this.active.act(this.drifter, this.activeTicks);
			if (going) {
				return true;
			}

			this.finish(now);
		}

		for (Reflex reflex : this.reflexes) {
			if (now < reflex.readyAt || !reflex.detect(this.drifter)) {
				continue;
			}

			if (this.pending != reflex) {
				this.pending = reflex;
				this.pendingDelay = this.reactionDelay(reflex);
			}

			if (--this.pendingDelay > 0) {
				// Still reacting: the hazard is noticed but the body hasn't moved yet.
				return false;
			}

			this.pending = null;
			this.active = reflex;
			this.activeTicks = 0;
			this.drifter.getKeys().releaseAll();
			this.heldSlot = this.drifter.getInventory().getSelected();
			reflex.start(this.drifter);
			this.drifter.getMemory().thoughts().add(now, "reflex: " + reflex.name());
			return true;
		}

		this.pending = null;
		return false;
	}

	private void finish(long now) {
		Reflex done = this.active;
		this.active = null;
		done.stop(this.drifter);
		done.readyAt = now + REFRACTORY_TICKS;
		this.drifter.getKeys().releaseAll();
		if (this.drifter.isUsingItem() && this.drifter.getUseItem().is(Items.SHIELD)) {
			this.drifter.stopUsingItem();
		}

		// Back to what it was holding (the task it was doing expects its tool or weapon).
		if (this.heldSlot >= 0 && this.drifter.getInventory().getSelected() != this.heldSlot) {
			this.drifter.getInventory().requestSelect(this.heldSlot);
		}

		this.heldSlot = -1;
	}

	/**
	 * Human reaction time (150 to 400 ms), scaled by Skill (section 17: Mechanical Skill covers reaction time and
	 * clutches): a skilled Drifter reacts in 3 to 6 ticks and is rarely late, a clumsy one in 4 to 10 and late more often.
	 * Panic makes it worse (its Skill drops while Panicked).
	 */
	private int reactionDelay(Reflex reflex) {
		float skill = this.drifter.getPersonality().getF(com.dinglebob.drifter.personality.Trait.SKILL);
		int min = Math.round(Mth.lerp(skill, MIN_DELAY_TICKS + 1, MIN_DELAY_TICKS));
		int max = Math.round(Mth.lerp(skill, MAX_DELAY_TICKS + 2, MAX_DELAY_TICKS - 2));
		int delay = Mth.nextInt(this.drifter.getRandom(), min, Math.max(min, max));
		Vec3 where = reflex.where(this.drifter);
		if (where != null) {
			Vec3 toHazard = where.subtract(this.drifter.getEyePosition()).normalize();
			if (toHazard.dot(this.drifter.getViewVector(1.0F)) < -0.2) {
				delay += BEHIND_EXTRA_TICKS;
			}
		}

		if (this.drifter.getRandom().nextFloat() < LATE_CHANCE * Mth.lerp(skill, 1.6F, 0.4F)) {
			delay += Mth.nextInt(this.drifter.getRandom(), LATE_MIN_TICKS, LATE_MAX_TICKS);
		}

		return delay;
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** The nearest block within {@code radius} it could stand on, out of fluids and fire. */
	@Nullable
	private static BlockPos nearestSafeSpot(DrifterEntity drifter, int radius) {
		Level level = drifter.level();
		BlockPos feet = drifter.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, -1, -radius), feet.offset(radius, 2, radius))) {
			if (solid(level, pos.below()) && !solid(level, pos) && !solid(level, pos.above()) && level.getFluidState(pos).isEmpty()
				&& level.getFluidState(pos.above()).isEmpty() && !level.getBlockState(pos).is(Blocks.FIRE)
				&& !level.getFluidState(pos.below()).is(FluidTags.LAVA)) {
				double distance = pos.distToCenterSqr(drifter.position());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}

		return best;
	}

	/** Run (sprinting) toward a point, jumping out of whatever it's in. */
	private static void runTo(DrifterEntity drifter, Vec3 point, boolean jump) {
		VirtualKeys keys = drifter.getKeys();
		drifter.getCamera().track(new Vec3(point.x, drifter.getEyeY(), point.z), CameraModel.TurnSpeed.COMBAT);
		Steering.pressToward(drifter, point);
		keys.set(VirtualKeys.Key.SPRINT, keys.isDown(VirtualKeys.Key.FORWARD));
		keys.set(VirtualKeys.Key.JUMP, jump || drifter.horizontalCollision && drifter.onGround());
		keys.set(VirtualKeys.Key.SNEAK, false);
	}

	/** A point {@code distance} blocks straight away from {@code from}. */
	private static Vec3 awayFrom(DrifterEntity drifter, Vec3 from, double distance) {
		Vec3 away = new Vec3(drifter.getX() - from.x, 0.0, drifter.getZ() - from.z);
		if (away.lengthSqr() < 1.0E-4) {
			away = Vec3.directionFromRotation(0.0F, drifter.getYRot() + 180.0F);
		}

		return drifter.position().add(away.normalize().scale(distance));
	}

	/** Takes out its best tool for the block and mines it (a reflex can run a short task). */
	private static Task dig(DrifterEntity drifter, BlockPos pos) {
		int slot = Tools.bestToolSlot(drifter, drifter.level().getBlockState(pos), drifter.level(), pos);
		if (slot < 0) {
			return new MineTask(drifter, pos);
		}

		ItemStack tool = drifter.getInventory().getItem(slot);
		return new SequenceTask(drifter, "dig free", List.of(new SelectItemTask(drifter, stack -> stack == tool, "a tool"), new MineTask(drifter, pos)));
	}

	/** A reflex that runs a short task (mining out, blocking lava) as its reaction. */
	private abstract static class TaskReflex extends Reflex {
		@Nullable
		private Task task;

		protected abstract Task makeTask(DrifterEntity drifter);

		@Override
		protected void start(DrifterEntity drifter) {
			this.task = this.makeTask(drifter);
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			return this.task != null && this.task.tick() == Task.Status.RUNNING;
		}

		@Override
		protected void stop(DrifterEntity drifter) {
			if (this.task != null) {
				this.task.stop();
				this.task = null;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The catalog (section 15)
	// ---------------------------------------------------------------------------------------------

	/** Standing in lava: jump and get to the nearest safe block. */
	private static final class InLava extends Reflex {
		@Nullable
		private BlockPos safe;
		/** No safe block in sight: a fixed way out (back the way it came), not one that turns with its head. */
		private Vec3 escape = Vec3.ZERO;

		@Override
		public String name() {
			return "in lava! getting out";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			return drifter.isInLava();
		}

		@Override
		protected int maxTicks() {
			return 60;
		}

		@Override
		protected void start(DrifterEntity drifter) {
			drifter.burnedByLavaAt(drifter.blockPosition());
			this.safe = nearestSafeSpot(drifter, 4);
			if (this.safe == null) {
				this.safe = nearestSafeSpot(drifter, 8);
			}

			Vec3 motion = drifter.getDeltaMovement();
			Vec3 back = new Vec3(-motion.x, 0.0, -motion.z);
			if (back.lengthSqr() < 1.0E-4) {
				back = Vec3.directionFromRotation(0.0F, drifter.getYRot() + 180.0F);
			}

			this.escape = drifter.position().add(back.normalize().scale(6.0));
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (!drifter.isInLava() && drifter.onGround()) {
				return false;
			}

			Vec3 point = this.safe != null ? Vec3.atBottomCenterOf(this.safe) : this.escape;
			runTo(drifter, point, true);
			return true;
		}
	}

	/** Low on air under water: swim up, or toward the nearest air. */
	/** (Up to 30 s: mining up through a ceiling while floating takes a player about 10 s a block.) */
	private static final class Drowning extends Reflex {
		@Override
		public String name() {
			return "out of air: swimming up";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			// At half its air: enough left to swim back the way it came (at 30% it ran out on the way, found in testing).
			boolean low = drifter.isEyeInFluid(FluidTags.WATER) && drifter.getAirSupply() < drifter.getMaxAirSupply() * 0.5;
			if (low) {
				drifter.ranOutOfAir();
			}

			return low;
		}

		@Override
		protected int maxTicks() {
			return 600;
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (!drifter.isEyeInFluid(FluidTags.WATER) || drifter.getAirSupply() >= drifter.getMaxAirSupply() * 0.6) {
				return false;
			}

			Level level = drifter.level();
			BlockPos head = BlockPos.containing(drifter.getEyePosition());
			VirtualKeys keys = drifter.getKeys();
			keys.set(VirtualKeys.Key.JUMP, true);
			if (!solid(level, head.above())) {
				// Straight up: look up and swim.
				drifter.getCamera().lookAt(drifter.getYRot(), -70.0F, CameraModel.TurnSpeed.COMBAT);
				keys.set(VirtualKeys.Key.FORWARD, true);
				return true;
			}

			// Blocked above: along a way through the water to the nearest air (found in testing: heading straight for the
			// nearest air, a wall in between held it there until it drowned).
			BlockPos next = wayToAir(level, head);
			if (next != null) {
				if (next.getY() > head.getY()) {
					drifter.getCamera().lookAt(drifter.getYRot(), -70.0F, CameraModel.TurnSpeed.COMBAT);
					Steering.pressToward(drifter, Vec3.atCenterOf(next));
				} else {
					runTo(drifter, Vec3.atCenterOf(next), true);
				}

				return true;
			}

			// Blocked above: toward the nearest air it can get its head into (found in testing: "open water" counted too, so
			// under a ceiling it swam from one drowned spot to the next and died).
			BlockPos open = null;
			double best = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(head.offset(-6, -1, -6), head.offset(6, 3, 6))) {
				// Air it can swim into: right over the water or right beside it, not a pocket sealed off behind stone.
				boolean byTheWater = !level.getFluidState(pos.below()).isEmpty();
				for (Direction side : Direction.Plane.HORIZONTAL) {
					byTheWater |= !level.getFluidState(pos.relative(side)).isEmpty();
				}

				if (byTheWater && !solid(level, pos) && level.getFluidState(pos).isEmpty() && pos.distToCenterSqr(drifter.getEyePosition()) < best) {
					best = pos.distToCenterSqr(drifter.getEyePosition());
					open = pos.immutable();
				}
			}

			if (open != null) {
				runTo(drifter, Vec3.atCenterOf(open), true);
				return true;
			}

			// Sealed in under a ceiling with no way to air it can swim (found in a user's test: it held jump under the rock
			// until it drowned): it mines up through the ceiling, as a player would, with its best tool for the block.
			BlockPos ceiling = head.above();
			net.minecraft.world.level.block.state.BlockState rock = level.getBlockState(ceiling);
			if (this.digUp != null) {
				if (this.digUp.tick() != Task.Status.RUNNING) {
					this.digUp = null;
				}

				keys.set(VirtualKeys.Key.JUMP, true);
				return true;
			}

			if (!rock.isAir() && rock.getDestroySpeed(level, ceiling) >= 0.0F && !level.getFluidState(ceiling.above()).is(FluidTags.LAVA)
				&& !(rock.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)) {
				this.digUp = dig(drifter, ceiling);
				drifter.getMemory().thoughts().add(level.getGameTime(), "no air to swim to: mining up through the ceiling");
				return true;
			}

			if (drifter.lastAir() != null && drifter.lastAir().distanceTo(drifter.position()) < 32.0) {
				// No air in sight (a flooded tunnel): back the way it came, where it was breathing a moment ago.
				runTo(drifter, drifter.lastAir(), true);
			}

			return true;
		}

		@Nullable
		private Task digUp;

		@Override
		protected void stop(DrifterEntity drifter) {
			if (this.digUp != null) {
				this.digUp.stop();
				this.digUp = null;
			}
		}
	}

	/** How far (in blocks swum) the drowning reflex looks for a way up to air. */
	private static final int AIR_SEARCH_STEPS = 20;

	/**
	 * The next block for its head on the shortest swim from {@code head} to air (a breadth-first search through the
	 * water around it, with room for its body below its head on sideways moves), or null if none is close.
	 */
	@Nullable
	private static BlockPos wayToAir(Level level, BlockPos head) {
		java.util.Map<BlockPos, BlockPos> cameFrom = new java.util.HashMap<>();
		java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
		java.util.Map<BlockPos, Integer> steps = new java.util.HashMap<>();
		cameFrom.put(head, head);
		steps.put(head, 0);
		open.add(head);
		while (!open.isEmpty()) {
			BlockPos cell = open.poll();
			if (!cell.equals(head) && level.getFluidState(cell).isEmpty() && !solid(level, cell)) {
				// Back along the way to the first block after where its head is now.
				BlockPos step = cell;
				while (!cameFrom.get(step).equals(head)) {
					step = cameFrom.get(step);
				}

				return step;
			}

			int taken = steps.get(cell);
			if (taken >= AIR_SEARCH_STEPS) {
				continue;
			}

			for (Direction direction : Direction.values()) {
				BlockPos to = cell.relative(direction);
				if (cameFrom.containsKey(to) || solid(level, to) || !level.getFluidState(to).isEmpty() && !level.getFluidState(to).is(FluidTags.WATER)) {
					continue;
				}

				// Sideways, its body needs the block under its head too (unless it's swimming flat, which this doesn't try).
				if (direction.getAxis().isHorizontal() && solid(level, to.below())) {
					continue;
				}

				cameFrom.put(to, cell);
				steps.put(to, taken + 1);
				open.add(to);
			}
		}

		return null;
	}

	/** On fire (not in lava) with water close by: run into it. */
	private static final class OnFire extends Reflex {
		@Nullable
		private BlockPos water;

		@Override
		public String name() {
			return "on fire: running into water";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			if (!drifter.isOnFire() || drifter.isInLava() || drifter.isInWater()) {
				return false;
			}

			this.water = null;
			double best = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(drifter.blockPosition().offset(-4, -2, -4), drifter.blockPosition().offset(4, 1, 4))) {
				if (drifter.level().getFluidState(pos).is(FluidTags.WATER) && pos.distToCenterSqr(drifter.position()) < best) {
					best = pos.distToCenterSqr(drifter.position());
					this.water = pos.immutable();
				}
			}

			return this.water != null;
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (!drifter.isOnFire() || drifter.isInWater() || this.water == null) {
				return false;
			}

			runTo(drifter, Vec3.atCenterOf(this.water), false);
			return true;
		}
	}

	/**
	 * On fire with no water near but a water bucket on it: water at its own feet, then scoop it back up, as players do
	 * (found by the user: one burned to death for 15 s after touching lava, a water bucket would have put it out).
	 */
	private static final class DouseWithBucket extends TaskReflex {
		@Override
		public String name() {
			return "on fire: water bucket at its feet";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			if (!drifter.isOnFire() || drifter.isInLava() || drifter.isInWater() || !drifter.onGround() || drifter.level().dimensionType().ultraWarm()
				|| drifter.getInventory().count(stack -> stack.is(Items.WATER_BUCKET)) == 0) {
				return false;
			}

			BlockPos feet = drifter.blockPosition();
			Level level = drifter.level();
			return level.getBlockState(feet).canBeReplaced() && level.getFluidState(feet).isEmpty()
				&& level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP);
		}

		@Override
		protected Task makeTask(DrifterEntity drifter) {
			Vec3 floor = Vec3.atBottomCenterOf(drifter.blockPosition()).add(0.0, 0.05, 0.0);
			return new com.dinglebob.drifter.executor.task.SequenceTask(drifter, "put the fire out with a water bucket", java.util.List.of(
				new com.dinglebob.drifter.executor.task.SelectItemTask(drifter, stack -> stack.is(Items.WATER_BUCKET), "a water bucket"),
				new com.dinglebob.drifter.executor.task.LookAtTask(drifter, floor, 6),
				new com.dinglebob.drifter.executor.task.UseItemTask(drifter),
				new com.dinglebob.drifter.executor.task.SelectItemTask(drifter, stack -> stack.is(Items.BUCKET), "a bucket"),
				new com.dinglebob.drifter.executor.task.LookAtTask(drifter, floor, 6),
				new com.dinglebob.drifter.executor.task.UseItemTask(drifter)));
		}

		@Override
		protected int maxTicks() {
			return 60;
		}
	}

	/** A creeper hissing close by: get clear (sprint away), or shield up if it can't. */
	private static final class CreeperFuse extends Reflex {
		private static final double DANGER_RANGE = 6.0;
		private static final double CLEAR_RANGE = 7.0;
		@Nullable
		private Creeper creeper;

		@Override
		public String name() {
			return "creeper hissing: getting clear";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			this.creeper = null;
			for (Awareness awareness : drifter.getSenses().tracked()) {
				if (awareness.entity instanceof Creeper creeper && creeper.isAlive() && creeper.distanceTo(drifter) < DANGER_RANGE
					&& (creeper.getSwellDir() > 0 || creeper.getSwelling(1.0F) > 0.1F)
					// It sees it swelling, or hears the hiss when it's close.
					&& (awareness.visibleNow || creeper.distanceTo(drifter) < 4.0)) {
					this.creeper = creeper;
					return true;
				}
			}

			return false;
		}

		@Override
		protected Vec3 where(DrifterEntity drifter) {
			return this.creeper == null ? null : this.creeper.position();
		}

		@Override
		protected int maxTicks() {
			return 50;
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (this.creeper == null || !this.creeper.isAlive() || this.creeper.distanceTo(drifter) >= CLEAR_RANGE) {
				return false;
			}

			boolean cornered = this.creeper.getSwelling(1.0F) > 0.7F && this.creeper.distanceTo(drifter) < 3.0;
			if (cornered && drifter.getOffhandItem().is(Items.SHIELD)) {
				// Can't get clear in time: face it, shield up.
				drifter.getCamera().lookAt(this.creeper, CameraModel.TurnSpeed.COMBAT);
				Steering.stopMoving(drifter);
				if (!drifter.isUsingItem()) {
					drifter.startUsingItem(InteractionHand.OFF_HAND);
				}

				return true;
			}

			runTo(drifter, awayFrom(drifter, this.creeper.position(), 8.0), false);
			return true;
		}
	}

	/** Lava right next to it (it just dug into it, or walked up to it): block it off, or back away. */
	private static final class LavaInTheWay extends Reflex {
		@Nullable
		private BlockPos lava;
		@Nullable
		private Task task;

		@Override
		public String name() {
			return "lava! blocking it off";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			Level level = drifter.level();
			BlockPos feet = drifter.blockPosition();
			boolean hasBlocks = Tools.buildingBlockCount(drifter) > 0;
			this.lava = null;
			for (BlockPos body : List.of(feet, feet.above())) {
				for (Direction direction : Direction.values()) {
					BlockPos side = body.relative(direction);
					if (level.getFluidState(side).is(FluidTags.LAVA) && drifter.getSenses().terrain().isKnown(side)) {
						// Without blocks it only backs off when it's moving toward it: walking along a lava shore is
						// fine (the pathfinder never steps in), and backing off every time it did made it loop (review).
						Vec3 toLava = Vec3.atCenterOf(side).subtract(drifter.position());
						if (!hasBlocks && drifter.getDeltaMovement().dot(new Vec3(toLava.x, 0.0, toLava.z)) <= 0.01) {
							continue;
						}

						this.lava = side.immutable();
						return true;
					}
				}
			}

			return false;
		}

		@Override
		protected Vec3 where(DrifterEntity drifter) {
			return this.lava == null ? null : Vec3.atCenterOf(this.lava);
		}

		@Override
		protected void start(DrifterEntity drifter) {
			this.task = this.lava != null && Tools.buildingBlockCount(drifter) > 0
				? new PlaceBlockTask(drifter, this.lava, Tools.BUILDING_BLOCK, "a block over the lava") : null;
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (this.task != null) {
				Task.Status status = this.task.tick();
				if (status == Task.Status.RUNNING) {
					return true;
				}

				this.task.stop();
				this.task = null;
				if (status == Task.Status.SUCCESS) {
					return false;
				}
			}

			// No block (or it couldn't place it): back away from it.
			if (this.lava == null || tick > 20) {
				return false;
			}

			runTo(drifter, awayFrom(drifter, Vec3.atCenterOf(this.lava), 3.0), false);
			drifter.getKeys().set(VirtualKeys.Key.SPRINT, false);
			return true;
		}

		@Override
		protected void stop(DrifterEntity drifter) {
			if (this.task != null) {
				this.task.stop();
				this.task = null;
			}
		}
	}

	/** Falling far with a water bucket: the clutch (water just before landing, then picking it back up). */
	private static final class HeavyFall extends Reflex {
		private boolean placed;
		private int pickupWait;

		@Override
		public String name() {
			return "falling: water bucket clutch";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			if (drifter.onGround() || drifter.isInWater() || drifter.getDeltaMovement().y > -0.4 || drifter.fallDistance < 3.0F) {
				return false;
			}

			int ground = groundDistance(drifter);
			if (ground < 0) {
				return false;
			}

			float damage = drifter.fallDistance + ground - (float)drifter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
			return damage >= 4.0F && drifter.getInventory().findSlot(stack -> stack.is(Items.WATER_BUCKET), 0, 9) >= 0;
		}

		@Override
		protected int maxTicks() {
			return 80;
		}

		@Override
		protected void start(DrifterEntity drifter) {
			this.placed = false;
			this.pickupWait = 0;
			int slot = drifter.getInventory().findSlot(stack -> stack.is(Items.WATER_BUCKET), 0, 9);
			drifter.getInventory().requestSelect(slot);
			drifter.getCamera().lookAt(drifter.getYRot(), 90.0F, CameraModel.TurnSpeed.COMBAT);
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (!this.placed) {
				if (drifter.onGround() || drifter.isInWater()) {
					return false;
				}

				int ground = groundDistance(drifter);
				if (ground >= 0 && ground <= 3 && drifter.getMainHandItem().is(Items.WATER_BUCKET) && drifter.getXRot() > 75.0F) {
					FakePlayerBridge.act(drifter, fake -> fake.gameMode.useItem(fake, drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND));
					drifter.swing(InteractionHand.MAIN_HAND);
					this.placed = !drifter.getMainHandItem().is(Items.WATER_BUCKET);
				}

				return true;
			}

			// Landed in the water: scoop it back up.
			if (++this.pickupWait < 6 || !drifter.getMainHandItem().is(Items.BUCKET)) {
				return this.pickupWait < 20;
			}

			FakePlayerBridge.act(drifter, fake -> fake.gameMode.useItem(fake, drifter.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND));
			drifter.swing(InteractionHand.MAIN_HAND);
			return false;
		}

		/** Blocks of air below it before the first solid block (or water), or -1 if more than 30. */
		private static int groundDistance(DrifterEntity drifter) {
			Level level = drifter.level();
			BlockPos pos = drifter.blockPosition();
			for (int i = 0; i <= 30; i++) {
				BlockPos below = pos.below(i);
				if (solid(level, below) || !level.getFluidState(below).isEmpty()) {
					return level.getFluidState(below).is(FluidTags.WATER) ? -1 : i;
				}
			}

			return -1;
		}
	}

	/** An arrow or fireball coming straight at it: shield up, or sidestep. */
	private static final class IncomingProjectile extends Reflex {
		private static final double RANGE = 24.0;
		private static final double HIT_RADIUS = 1.2;
		private static final int LOOKAHEAD_TICKS = 20;
		@Nullable
		private Projectile projectile;
		private boolean left;

		@Override
		public String name() {
			return "something flying at it: dodging";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			this.projectile = null;
			Vec3 eye = drifter.getEyePosition();
			List<Projectile> flying = drifter.level().getEntitiesOfClass(Projectile.class, new AABB(eye, eye).inflate(RANGE),
				p -> (p instanceof AbstractArrow arrow && !((AbstractArrowAccessor)arrow).drifter$isInGround() || p instanceof AbstractHurtingProjectile)
					&& p.getOwner() != drifter);
			flying.sort(Comparator.comparingDouble(p -> p.distanceToSqr(drifter)));
			AABB body = drifter.getBoundingBox().inflate(HIT_RADIUS - 1.0 + 0.3);
			for (Projectile candidate : flying) {
				if (candidate.getDeltaMovement().lengthSqr() < 0.25 || !willHit(candidate, body)) {
					continue;
				}

				boolean sees = drifter.level().clip(new ClipContext(eye, candidate.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
					drifter)).getType() == HitResult.Type.MISS;
				if (sees) {
					this.projectile = candidate;
					return true;
				}
			}

			return false;
		}

		/**
		 * Follows its flight for the next second (arrows fall 0.05 a tick and slow 1% a tick; fireballs fly straight) and
		 * checks whether it passes through its body (found in review: a straight line missed skeleton arrows, which are
		 * aimed high and drop onto the target).
		 */
		private static boolean willHit(Projectile projectile, AABB body) {
			boolean arrow = projectile instanceof AbstractArrow;
			Vec3 position = projectile.position();
			Vec3 velocity = projectile.getDeltaMovement();
			for (int tick = 0; tick < LOOKAHEAD_TICKS; tick++) {
				Vec3 next = position.add(velocity);
				if (body.clip(position, next).isPresent() || body.contains(next)) {
					return true;
				}

				position = next;
				if (arrow) {
					velocity = velocity.scale(0.99).subtract(0.0, 0.05, 0.0);
				}
			}

			return false;
		}

		@Override
		protected Vec3 where(DrifterEntity drifter) {
			return this.projectile == null ? null : this.projectile.position();
		}

		@Override
		protected int maxTicks() {
			return 15;
		}

		@Override
		protected void start(DrifterEntity drifter) {
			this.left = drifter.getRandom().nextBoolean();
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (this.projectile == null || this.projectile.isRemoved()) {
				return false;
			}

			if (drifter.getOffhandItem().is(Items.SHIELD)) {
				drifter.getCamera().lookAt(this.projectile.position(), CameraModel.TurnSpeed.COMBAT);
				Steering.stopMoving(drifter);
				if (!drifter.isUsingItem()) {
					drifter.startUsingItem(InteractionHand.OFF_HAND);
				}

				return true;
			}

			// Sidestep across its line (not along it): perpendicular to its flight, on the side picked at the start.
			Vec3 velocity = this.projectile.getDeltaMovement();
			Vec3 across = new Vec3(-velocity.z, 0.0, velocity.x);
			if (across.lengthSqr() < 1.0E-4) {
				return false;
			}

			across = across.normalize().scale(this.left ? 2.0 : -2.0);
			Steering.pressToward(drifter, drifter.position().add(across));
			drifter.getKeys().set(VirtualKeys.Key.SPRINT, false);
			return tick < 8;
		}
	}

	/** Its head is inside a block (sand or gravel fell on it): mine out. */
	private static final class Suffocating extends TaskReflex {
		@Override
		public String name() {
			return "suffocating: digging out";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			return drifter.isInWall();
		}

		/** The block its head is in, found the way Entity#isInWall finds it (the head box is 0.48 wide). */
		@Override
		protected Task makeTask(DrifterEntity drifter) {
			float width = drifter.getBbWidth() * 0.8F;
			AABB head = AABB.ofSize(drifter.getEyePosition(), width, 1.0E-6, width);
			Level level = drifter.level();
			BlockPos found = BlockPos.containing(drifter.getEyePosition());
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(head.minX, head.minY, head.minZ), BlockPos.containing(head.maxX, head.maxY, head.maxZ))) {
				if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).isSuffocating(level, pos)) {
					found = pos.immutable();
					break;
				}
			}

			return dig(drifter, found);
		}

		@Override
		protected int maxTicks() {
			return 60;
		}
	}

	/**
	 * Sunk in powder snow and freezing: toward the nearest firm ground, breaking the powder snow in the way at feet and head
	 * height (it breaks at a touch), as a player digs their way out sideways (found in testing: it jumped on the spot until
	 * it froze to death).
	 */
	private static final class Freezing extends Reflex {
		@Nullable
		private BlockPos out;

		@Override
		public String name() {
			return "freezing in powder snow: getting out";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			// (isInPowderSnow is cleared at the start of every tick and only set again while moving, so at this point in the
			// tick it's last tick's value that tells: found in testing, the reflex never fired and it froze anyway.)
			return (drifter.isInPowderSnow || drifter.wasInPowderSnow) && drifter.getTicksFrozen() > 20;
		}

		@Override
		protected int maxTicks() {
			return 200;
		}

		@Override
		protected void start(DrifterEntity drifter) {
			Level level = drifter.level();
			BlockPos feet = drifter.blockPosition();
			this.out = null;
			this.climb = null;
			double best = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-6, -2, -6), feet.offset(6, 5, 6))) {
				if (solid(level, pos.below()) && !level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
					&& !solid(level, pos) && !solid(level, pos.above()) && !isPowder(level, pos) && !isPowder(level, pos.above())
					&& level.getFluidState(pos).isEmpty() && pos.distSqr(feet) < best) {
					best = pos.distSqr(feet);
					this.out = pos.immutable();
				}
			}
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (!drifter.isInPowderSnow && !drifter.wasInPowderSnow || this.out == null) {
				return false;
			}

			Level level = drifter.level();
			BlockPos feet = drifter.blockPosition();
			// Firm ground above it (sunk into a deep pocket): clear the powder snow over its head and pillar up, as a player
			// climbs out of a hole.
			if (this.out.getY() > feet.getY() && Tools.buildingBlockCount(drifter) > 0) {
				if (tick % POWDER_BREAK_TICKS == 0) {
					for (BlockPos cell : List.of(feet.above(2), feet.above(), feet)) {
						if (isPowder(level, cell)) {
							FakePlayerBridge.act(drifter, fake -> fake.gameMode.destroyBlock(cell));
							drifter.swing(InteractionHand.MAIN_HAND);
							break;
						}
					}
				}

				if (this.climb == null && !isPowder(level, feet) && !isPowder(level, feet.above())) {
					this.climb = new com.dinglebob.drifter.executor.task.PillarUpTask(drifter, Math.min(4, this.out.getY() - feet.getY()));
				}

				if (this.climb != null) {
					if (this.climb.tick() != Task.Status.RUNNING) {
						this.climb = null;
					}

					return true;
				}

				return true;
			}

			Direction toward = Direction.getNearest(this.out.getX() - feet.getX(), 0, this.out.getZ() - feet.getZ());
			// Powder snow in the way (or what it's sunk in): break it, then keep moving. By hand that takes a player about 8
			// ticks a block (hardness 0.25), so one every 8 ticks.
			for (BlockPos cell : tick % POWDER_BREAK_TICKS != 0 ? List.<BlockPos>of() : List.of(feet.above(), feet.relative(toward).above(), feet.relative(toward))) {
				if (isPowder(level, cell)) {
					FakePlayerBridge.act(drifter, fake -> fake.gameMode.destroyBlock(cell));
					drifter.swing(InteractionHand.MAIN_HAND);
					break;
				}
			}

			runTo(drifter, Vec3.atBottomCenterOf(this.out), true);
			return true;
		}

		private static final int POWDER_BREAK_TICKS = 8;
		@Nullable
		private Task climb;

		@Override
		protected void stop(DrifterEntity drifter) {
			if (this.climb != null) {
				this.climb.stop();
				this.climb = null;
			}
		}

		private static boolean isPowder(Level level, BlockPos pos) {
			return level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
		}
	}

	/** Sand or gravel falling onto it: step out of the column. */
	private static final class FallingBlockAbove extends Reflex {
		@Nullable
		private BlockPos escape;

		@Override
		public String name() {
			return "something falling on it: stepping aside";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			AABB column = drifter.getBoundingBox().expandTowards(0.0, 6.0, 0.0);
			return !drifter.level().getEntitiesOfClass(FallingBlockEntity.class, column, block -> block.getY() > drifter.getEyeY()).isEmpty();
		}

		@Override
		protected int maxTicks() {
			return 12;
		}

		@Override
		protected void start(DrifterEntity drifter) {
			this.escape = null;
			Level level = drifter.level();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos side = drifter.blockPosition().relative(direction);
				if (!solid(level, side) && !solid(level, side.above()) && solid(level, side.below())) {
					this.escape = side;
					return;
				}
			}
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (this.escape == null) {
				return false;
			}

			// All the way out of the column (its body is 0.6 wide): close to the middle of the escape block.
			Vec3 middle = Vec3.atBottomCenterOf(this.escape);
			Steering.pressToward(drifter, middle);
			return Math.abs(drifter.getX() - middle.x) > 0.2 || Math.abs(drifter.getZ() - middle.z) > 0.2;
		}
	}

	/** Stuck in a cobweb: cut it (a sword or shears cut fastest). */
	private static final class InCobweb extends TaskReflex {
		@Nullable
		private BlockPos web;

		@Override
		public String name() {
			return "stuck in a cobweb: cutting it";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			BlockPos feet = drifter.blockPosition();
			for (BlockPos pos : List.of(feet, feet.above())) {
				if (drifter.level().getBlockState(pos).is(Blocks.COBWEB)) {
					this.web = pos.immutable();
					return true;
				}
			}

			return false;
		}

		@Override
		protected Task makeTask(DrifterEntity drifter) {
			return dig(drifter, this.web);
		}
	}

	/** Pricked by a cactus or berry bush: step away from it. */
	private final class Contact extends Reflex {
		@Nullable
		private Vec3 from;

		@Override
		public String name() {
			return "ouch: stepping away";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			if (drifter.level().getGameTime() > Reflexes.this.contactUntil) {
				return false;
			}

			Level level = drifter.level();
			this.from = Reflexes.this.contactFrom;
			if (this.from != null) {
				return true;
			}

			for (BlockPos pos : BlockPos.betweenClosed(drifter.blockPosition().offset(-1, 0, -1), drifter.blockPosition().offset(1, 1, 1))) {
				if (level.getBlockState(pos).is(Blocks.CACTUS) || level.getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH)) {
					this.from = Vec3.atCenterOf(pos);
					return true;
				}
			}

			return false;
		}

		@Override
		protected void start(DrifterEntity drifter) {
			Reflexes.this.contactUntil = -1;
		}

		@Override
		protected int maxTicks() {
			return 8;
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (this.from == null) {
				return false;
			}

			Steering.pressToward(drifter, awayFrom(drifter, this.from, 2.0));
			return true;
		}
	}

	/** Lit TNT seen close by: run more than 8 blocks away. */
	private static final class LitTnt extends Reflex {
		private static final double DANGER_RANGE = 8.0;
		@Nullable
		private PrimedTnt tnt;

		@Override
		public String name() {
			return "lit TNT: running";
		}

		@Override
		protected boolean detect(DrifterEntity drifter) {
			Vec3 eye = drifter.getEyePosition();
			this.tnt = drifter.level().getEntitiesOfClass(PrimedTnt.class, drifter.getBoundingBox().inflate(DANGER_RANGE),
				primed -> drifter.level().clip(new ClipContext(eye, primed.position().add(0.0, 0.5, 0.0), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, drifter)).getType() == HitResult.Type.MISS).stream().findFirst().orElse(null);
			return this.tnt != null;
		}

		@Override
		protected Vec3 where(DrifterEntity drifter) {
			return this.tnt == null ? null : this.tnt.position();
		}

		@Override
		protected int maxTicks() {
			return 80;
		}

		@Override
		protected boolean act(DrifterEntity drifter, int tick) {
			if (this.tnt == null || this.tnt.isRemoved() || this.tnt.distanceTo(drifter) > DANGER_RANGE + 1.0) {
				return false;
			}

			runTo(drifter, awayFrom(drifter, this.tnt.position(), 10.0), false);
			return true;
		}
	}

	/** For the overlay. */
	public String describe() {
		if (this.active != null) {
			return "Reflex: " + this.active.name();
		}

		return this.pending != null ? "Reflex: noticed (" + this.pending.name() + ")" : "";
	}
}
