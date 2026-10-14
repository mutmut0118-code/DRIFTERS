package com.dinglebob.drifter.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.google.common.collect.ImmutableMap;

import com.dinglebob.drifter.executor.TaskRunner;
import com.dinglebob.drifter.memory.DrifterMemory;
import com.dinglebob.drifter.perception.Senses;
import com.dinglebob.drifter.mixin.ItemEntityAccessor;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Drifter: a custom mob (DESIGN.md section 2, option C) that uses player numbers and player movement rules.
 *
 * <p>Milestone 1 (Body) gives it: player dimensions and attributes, a player inventory, the player hunger model,
 * virtual-key movement fed through the normal living-entity physics exactly like a client feeds a player's keys,
 * and a camera model for the head. It has no AI yet: it only does what its keys and camera are told
 * (by the debug commands for now, by the motor layer later).
 *
 * <p>Like a player, its yaw <em>is</em> its camera yaw ({@code yHeadRot == yRot}); the body follows the movement
 * direction and turns when standing still only once the head is more than 50 degrees off.
 */
public class DrifterEntity extends PathfinderMob {
	/** Bump when the saved format changes, and migrate old data in readAdditionalSaveData (section 20). */
	public static final int SAVE_VERSION = 1;

	private static final EntityDataAccessor<String> DATA_SKIN_TEXTURE = SynchedEntityData.defineId(DrifterEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_SLIM_ARMS = SynchedEntityData.defineId(DrifterEntity.class, EntityDataSerializers.BOOLEAN);

	/** The player's pose sizes. Player keeps its map private, so this mirrors it using Player's public constants. */
	private static final Map<Pose, EntityDimensions> POSES = ImmutableMap.<Pose, EntityDimensions>builder()
		.put(Pose.STANDING, Player.STANDING_DIMENSIONS)
		.put(Pose.SLEEPING, SLEEPING_DIMENSIONS)
		.put(Pose.FALL_FLYING, EntityDimensions.scalable(Player.SWIMMING_BB_WIDTH, Player.SWIMMING_BB_HEIGHT).withEyeHeight(0.4F))
		.put(Pose.SWIMMING, EntityDimensions.scalable(Player.SWIMMING_BB_WIDTH, Player.SWIMMING_BB_HEIGHT).withEyeHeight(0.4F))
		.put(Pose.SPIN_ATTACK, EntityDimensions.scalable(Player.SWIMMING_BB_WIDTH, Player.SWIMMING_BB_HEIGHT).withEyeHeight(0.4F))
		.put(Pose.CROUCHING, EntityDimensions.scalable(Player.SWIMMING_BB_WIDTH, Player.CROUCH_BB_HEIGHT).withEyeHeight(1.27F))
		.put(Pose.DYING, EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(Player.DEFAULT_EYE_HEIGHT))
		.build();

	/** Values from LocalPlayer: input is cut to 20% while using an item, sprint needs 80% forward input. */
	private static final float USING_ITEM_INPUT_FACTOR = 0.2F;
	private static final float SPRINT_START_MIN_FORWARD = 0.8F;
	/** From Player#getFlyingSpeed: air control when not flying. */
	private static final float AIR_SPEED = 0.02F;
	private static final float SPRINT_AIR_SPEED = 0.025999999F;

	private final DrifterInventory inventory = new DrifterInventory(this);
	private final DrifterFoodData foodData = new DrifterFoodData();
	private final VirtualKeys keys = new VirtualKeys();
	private final CameraModel camera;
	/** The executor (DESIGN.md section 8, layer 4): runs hand and body tasks one at a time. */
	private final TaskRunner tasks = new TaskRunner(this);
	private final com.dinglebob.drifter.brain.Brain brain = new com.dinglebob.drifter.brain.Brain(this);
	private final com.dinglebob.drifter.reflex.Reflexes reflexes = new com.dinglebob.drifter.reflex.Reflexes(this);
	/** Who it is (section 17): archetype, traits, habit fingerprint and mood. Rolled once, saved. */
	private final com.dinglebob.drifter.personality.Personality personality;
	/** The mannerism layer (section 16): tics on top of whatever it's doing, with the body parts its task leaves free. */
	private final com.dinglebob.drifter.mannerism.Mannerisms mannerisms = new com.dinglebob.drifter.mannerism.Mannerisms(this);
	private long lastAttackTime = Long.MIN_VALUE / 2;
	/** True during its own melee swing (see {@link #hurt} and {@link #knockback}). */
	private boolean ownSwing;
	private long shieldDisabledUntil;
	/** Short- and long-term memory (section 6). Everything it knows about the world is in here. */
	private final DrifterMemory memory = new DrifterMemory();
	/** Perception (section 5): the only thing that looks at the world, and it writes into memory. */
	private final Senses senses;
	private final DynamicGameEventListener<?> hearingListener;
	/** The path it's following right now and how far along it is, for the debug overlay only. */
	private List<com.dinglebob.drifter.nav.PathStep> debugPath = List.of();
	private int debugPathIndex;
	/** Ticks until the debug command's held keys are released; -1 for "hold until changed". */
	private int keysReleaseTimer = -1;
	/** It has its username (section 4): generated at spawn, or on first load for Drifters from before milestone 7. */
	private boolean named;
	/** The last spot it stood on dry ground (where it came from, when it's lost in water). Not saved. */
	@org.jetbrains.annotations.Nullable
	private BlockPos lastDryGround;

	/** Game time it last nearly ran out of air: for a while after, it plans no routes under water. */
	private long outOfAirAt = Long.MIN_VALUE / 2;

	/** Times it nearly ran out of air lately (one per scare, not per tick). */
	private final java.util.ArrayDeque<Long> airScares = new java.util.ArrayDeque<>();

	public void ranOutOfAir() {
		long now = this.level().getGameTime();
		if (now - this.outOfAirAt > 60) {
			this.airScares.addLast(now);
			// Debug: with thoughts going to the log, a near-drowning traces itself (to see what went wrong in long tests).
			if (com.dinglebob.drifter.memory.ThoughtLog.echo && this.traceTicks <= 0) {
				this.traceTicks = 200;
			}
		}

		this.outOfAirAt = now;
	}

	/** Twice or more in the last minute: get out of the water, whatever it was doing. */
	public boolean keepsRunningOutOfAir() {
		long now = this.level().getGameTime();
		while (!this.airScares.isEmpty() && now - this.airScares.peekFirst() > 20L * 60) {
			this.airScares.removeFirst();
		}

		return this.airScares.size() >= 2 && this.isInWater();
	}

	/** It nearly drowned in the last two minutes (found in testing: it surfaced, then its job sent it straight back under). */
	public boolean recentlyOutOfAir() {
		return this.level().getGameTime() - this.outOfAirAt < 20L * 120;
	}

	/** Where its head was last out of the water (a player swims back the way they came when a tunnel floods). */
	@org.jetbrains.annotations.Nullable
	private Vec3 lastAir;

	@org.jetbrains.annotations.Nullable
	public Vec3 lastAir() {
		return this.lastAir;
	}

	@org.jetbrains.annotations.Nullable
	public BlockPos lastDryGround() {
		return this.lastDryGround;
	}

	/** Ticks left of the debug trace (/drifter trace): one log line per tick. */
	private int traceTicks;
	/** Debug: how many trace lines the black box keeps. */
	private static final int BLACK_BOX_TICKS = 200;
	private final java.util.ArrayDeque<String> blackBox = new java.util.ArrayDeque<>();
	/** Spots where pillaring up failed (head hit something), until a game time: the pathfinder plans around them. */
	private final java.util.Map<BlockPos, Long> failedPillars = new java.util.HashMap<>();

	/** Remembers that pillaring up from here didn't work, for a few minutes (a player who bumps their head goes another way). */
	public void pillarFailedAt(BlockPos feet) {
		this.failedPillars.put(feet.immutable(), this.level().getGameTime() + 20L * 60 * 5);
	}

	/** Blocks it couldn't bridge out to (couldn't get to the edge), until a game time: the pathfinder plans around them. */
	private final java.util.Map<BlockPos, Long> failedBridges = new java.util.HashMap<>();

	/** Remembers that bridging out to this block didn't work, for a few minutes (found in testing: it re-planned the same bridge all evening). */
	public void bridgeFailedAt(BlockPos to) {
		this.failedBridges.put(to.immutable(), this.level().getGameTime() + 20L * 60 * 5);
	}

	public boolean bridgeFailedRecently(BlockPos to) {
		Long until = this.failedBridges.get(to);
		if (until == null) {
			return false;
		}

		if (until < this.level().getGameTime()) {
			this.failedBridges.remove(to);
			return false;
		}

		return true;
	}

	public boolean pillarFailedRecently(BlockPos feet) {
		Long until = this.failedPillars.get(feet);
		if (until == null) {
			return false;
		}

		if (until < this.level().getGameTime()) {
			this.failedPillars.remove(feet);
			return false;
		}

		return true;
	}
	/** Mirrors LocalPlayer#crouching: true while the sneak key is held (or there is no room to stand). */
	private boolean crouching;

	public DrifterEntity(EntityType<? extends DrifterEntity> entityType, Level level) {
		super(entityType, level);
		this.camera = new CameraModel(this.random);
		this.personality = com.dinglebob.drifter.personality.Personality.roll(this.random, null);
		this.senses = new Senses(this, this.memory);
		this.hearingListener = new DynamicGameEventListener<>(this.senses.hearing());
		// The Drifter is driven by virtual keys and its camera model, not by vanilla mob controls.
		this.moveControl = new IdleMoveControl(this);
		this.lookControl = new IdleLookControl(this);
		this.jumpControl = new IdleJumpControl(this);
		this.setCanPickUpLoot(false);
		if (!level.isClientSide) {
			this.setSkin(SkinPool.pick(this.random));
		}
	}

	/** Player attributes, read from the game's own player definition (section 3), plus what mob navigation needs. */
	public static AttributeSupplier.Builder createAttributes() {
		return Player.createAttributes().add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SKIN_TEXTURE, SkinPool.FALLBACK.texture().toString());
		builder.define(DATA_SLIM_ARMS, SkinPool.FALLBACK.slim());
	}

	// ---------------------------------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------------------------------

	public DrifterInventory getInventory() {
		return this.inventory;
	}

	public DrifterFoodData getFoodData() {
		return this.foodData;
	}

	public VirtualKeys getKeys() {
		return this.keys;
	}

	public CameraModel getCamera() {
		return this.camera;
	}

	public com.dinglebob.drifter.reflex.Reflexes getReflexes() {
		return this.reflexes;
	}

	public com.dinglebob.drifter.personality.Personality getPersonality() {
		return this.personality;
	}

	public com.dinglebob.drifter.mannerism.Mannerisms getMannerisms() {
		return this.mannerisms;
	}

	public com.dinglebob.drifter.brain.Brain getDrifterBrain() {
		return this.brain;
	}

	public TaskRunner getTasks() {
		return this.tasks;
	}

	public DrifterMemory getMemory() {
		return this.memory;
	}

	public Senses getSenses() {
		return this.senses;
	}

	public void setDebugPath(List<com.dinglebob.drifter.nav.PathStep> path, int index) {
		this.debugPath = path;
		this.debugPathIndex = index;
	}

	public List<com.dinglebob.drifter.nav.PathStep> getDebugPath() {
		return this.debugPath;
	}

	public int getDebugPathIndex() {
		return this.debugPathIndex;
	}

	/** Ticks since its last swing at an entity (the player attack cooldown, Player#attackStrengthTicker). */
	public int ticksSinceAttack() {
		return (int)Math.min(Integer.MAX_VALUE, this.level().getGameTime() - this.lastAttackTime);
	}

	public void resetAttackTimer() {
		this.lastAttackTime = this.level().getGameTime();
	}

	/** Can it raise its shield now (holding one in the offhand, not disabled by an axe hit)? */
	public boolean canUseShield() {
		return this.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) && this.level().getGameTime() >= this.shieldDisabledUntil;
	}

	/** Player#hurtCurrentlyUsedShield: a blocked hit wears the shield down (and can break it). */
	@Override
	public void hurtCurrentlyUsedShield(float damage) {
		if (this.useItem.is(net.minecraft.world.item.Items.SHIELD) && damage >= 3.0F) {
			int wear = 1 + Mth.floor(damage);
			net.minecraft.world.InteractionHand hand = this.getUsedItemHand();
			this.useItem.hurtAndBreak(wear, this, getSlotForHand(hand));
			if (this.useItem.isEmpty()) {
				this.setItemSlot(hand == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				this.useItem = ItemStack.EMPTY;
				this.playSound(net.minecraft.sounds.SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
			}
		}
	}

	/** Player#blockUsingShield: an axe hit knocks the shield down for 5 s, as for a player. */
	@Override
	protected void blockUsingShield(LivingEntity attacker) {
		super.blockUsingShield(attacker);
		if (attacker.canDisableShield()) {
			this.shieldDisabledUntil = this.level().getGameTime() + 100;
			this.stopUsingItem();
			this.playSound(net.minecraft.sounds.SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
		}
	}

	public void setOwnSwing(boolean ownSwing) {
		this.ownSwing = ownSwing;
	}

	/** No knockback from its own sword's sweep (knockback is applied before the hit, so {@link #hurt} can't stop it). */
	@Override
	public void knockback(double strength, double x, double z) {
		if (!this.ownSwing) {
			super.knockback(strength, x, z);
		}
	}

	/** Registers its ears with the game's vibration system, like the Allay and the Warden do. */
	@Override
	public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> registrar) {
		if (this.level() instanceof ServerLevel serverLevel && this.hearingListener != null) {
			registrar.accept(this.hearingListener, serverLevel);
		}
	}

	/** Holds keys for a number of ticks, then releases them all. Ticks of -1 hold until changed. */
	/** Logs a line per tick for this many ticks (debug: /drifter trace). */
	public void trace(int ticks) {
		this.traceTicks = ticks;
	}

	public void holdKeys(List<VirtualKeys.Key> pressed, int ticks) {
		this.keys.setOnly(pressed);
		this.keysReleaseTimer = ticks;
	}

	public ResourceLocation getSkinTexture() {
		ResourceLocation texture = ResourceLocation.tryParse(this.entityData.get(DATA_SKIN_TEXTURE));
		return texture != null ? texture : SkinPool.FALLBACK.texture();
	}

	public boolean hasSlimArms() {
		return this.entityData.get(DATA_SLIM_ARMS);
	}

	public void setSkin(SkinPool.Skin skin) {
		this.entityData.set(DATA_SKIN_TEXTURE, skin.texture().toString());
		this.entityData.set(DATA_SLIM_ARMS, skin.slim());
	}

	public boolean isHurt() {
		return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
	}

	public void causeFoodExhaustion(float amount) {
		if (!this.level().isClientSide) {
			this.foodData.addExhaustion(amount);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Ticking
	// ---------------------------------------------------------------------------------------------

	/** Spawn eggs and the spawn command come through here: it gets its username straight away. */
	@Override
	public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
		net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType spawnType,
		@org.jetbrains.annotations.Nullable net.minecraft.world.entity.SpawnGroupData groupData) {
		net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
		this.ensureName();
		return data;
	}

	/**
	 * Section 4 identity: a player-like username shown like a player's nameplate (always over its head within 64
	 * blocks, dimmed through walls, hidden through walls while it sneaks). A name you give it (spawn command, name tag)
	 * is kept.
	 */
	public void ensureName() {
		if (this.named || this.level().isClientSide || this.getServer() == null) {
			return;
		}

		this.named = true;
		if (!this.hasCustomName()) {
			this.setCustomName(net.minecraft.network.chat.Component.literal(
				com.dinglebob.drifter.identity.Usernames.generate(this.random, this.getServer())));
		}

		this.setCustomNameVisible(true);
	}

	/** Asleep in bed it doesn't move, like a player (Player#isImmobile). */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.isSleeping();
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide) {
			if (!this.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
				this.lastAir = this.getEyePosition().subtract(0.0, this.getEyeHeight() - 0.1, 0.0);
			}

			if (this.tickCount % 10 == 0 && this.onGround() && !this.isInWater() && this.level().getFluidState(this.blockPosition().below()).isEmpty()) {
				this.lastDryGround = this.blockPosition();
			}

			// Held item and armor live in its inventory, like a player's: make sure the next equipment check runs (Lithium).
			com.dinglebob.drifter.compat.LithiumCompat.markEquipmentChanged(this);
			this.ensureName();
			this.foodData.tick(this);
			this.checkMovementStatistics(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
			this.updatePlayerPose();
		}
	}

	@Override
	public void aiStep() {
		// Advances the arm swing (attack/mining animation). Vanilla only does this for players and monsters, so
		// without it the arm never moves on clients and the server never sends a second swing.
		this.updateSwingTime();
		if (!this.level().isClientSide) {
			this.tickPeacefulRegeneration();
			this.inventory.tick();
		}

		super.aiStep();

		if (!this.level().isClientSide && this.isAlive()) {
			this.pickUpNearbyItems();
		}
	}

	/** Runs inside Mob#serverAiStep, before the (idle) vanilla controls. This is where the motor drives the body. */
	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		// Perception first (it writes memory), then the executor acts, then the motor moves the body.
		this.senses.tick();
		this.personality.tick(this);
		// Skill moves the head (section 16.1: lower Skill overshoots more); mood can shake it (Panicked).
		this.camera.setSkill(this.personality.getF(com.dinglebob.drifter.personality.Trait.SKILL));
		// Reflexes first (section 8, layer 5): while one has the body, the brain, the executor and the tics wait.
		if (!this.reflexes.tick()) {
			this.brain.tick();
			this.tasks.tick();
			this.mannerisms.tick();
		} else {
			this.mannerisms.interrupt("reflex");
		}
		float[] look = this.camera.tick(this.getEyePosition(), this.getYRot(), this.getXRot());
		this.setYRot(look[0]);
		this.setXRot(look[1]);
		this.setYHeadRot(look[0]);
		// TODO(milestone 8): deliberate sprint-swimming on long swims (section 10.10).
		com.dinglebob.drifter.motor.Steering.noSprintInWater(this);
		this.applyMovementKeys();
		if (this.traceTicks > 0 || com.dinglebob.drifter.memory.ThoughtLog.echo) {
			com.dinglebob.drifter.executor.Task leaf = this.tasks.current() == null ? null : this.tasks.current().leaf();
			String line = String.format(java.util.Locale.ROOT,
				"[trace %s] t%d pos %.2f %.2f %.2f vy %.3f %s%s%s%s%s pose %s pitch %.0f keys %s step %s", this.getName().getString(), this.tickCount,
				this.getX(), this.getY(), this.getZ(), this.getDeltaMovement().y, this.onGround() ? "G" : "-", this.isInWater() ? "W" : "-",
				this.isUnderWater() ? "U" : "-", this.isSwimming() ? "S" : "-", this.isSprinting() ? "R" : "-", this.getPose(), this.getXRot(),
				this.keys.describe(), leaf instanceof com.dinglebob.drifter.executor.task.NavigateTask nav ? nav.debugStep()
					: leaf == null ? "none" : leaf.describe());
			if (this.traceTicks > 0) {
				this.traceTicks--;
				com.dinglebob.drifter.Drifter.LOGGER.info(line);
			} else {
				// Debug black box: with thoughts going to the log, the last few seconds are kept and written out if it dies.
				this.blackBox.addLast(line);
				if (this.blackBox.size() > BLACK_BOX_TICKS) {
					this.blackBox.removeFirst();
				}
			}
		}

		if (this.keysReleaseTimer > 0 && --this.keysReleaseTimer == 0) {
			this.keys.releaseAll();
			this.keysReleaseTimer = -1;
		}
	}

	/** Player#aiStep: on Peaceful with natural regeneration, players regenerate health and food for free. */
	private void tickPeacefulRegeneration() {
		if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
			if (this.getHealth() < this.getMaxHealth() && this.tickCount % 20 == 0) {
				this.heal(1.0F);
			}

			if (this.foodData.getSaturationLevel() < FoodConstants.MAX_SATURATION && this.tickCount % 20 == 0) {
				this.foodData.setSaturation(this.foodData.getSaturationLevel() + 1.0F);
			}

			if (this.foodData.needsFood() && this.tickCount % 10 == 0) {
				this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Virtual-key movement (mirrors LocalPlayer#aiStep and KeyboardInput)
	// ---------------------------------------------------------------------------------------------

	private void applyMovementKeys() {
		boolean sneakKey = this.keys.isDown(VirtualKeys.Key.SNEAK);
		this.setShiftKeyDown(sneakKey);
		this.crouching = !this.isSwimming()
			&& !this.isPassenger()
			&& this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)
			&& (sneakKey || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));

		float forward = this.keys.forwardImpulse();
		float left = this.keys.leftImpulse();
		if (this.isMovingSlowly()) {
			float sneakFactor = (float)this.getAttributeValue(Attributes.SNEAKING_SPEED);
			forward *= sneakFactor;
			left *= sneakFactor;
		}

		if (this.isUsingItem() && !this.isPassenger()) {
			forward *= USING_ITEM_INPUT_FACTOR;
			left *= USING_ITEM_INPUT_FACTOR;
		}

		boolean hasForwardImpulse = forward > 1.0E-5F;
		boolean enoughImpulseToSprint = this.isUnderWater() ? hasForwardImpulse : forward >= SPRINT_START_MIN_FORWARD;
		boolean canStartSprinting = !this.isSprinting()
			&& enoughImpulseToSprint
			&& this.hasEnoughFoodToSprint()
			&& !this.isUsingItem()
			&& !this.hasEffect(MobEffects.BLINDNESS)
			&& !this.isPassenger()
			&& !this.isFallFlying();
		if ((!this.isInWater() || this.isUnderWater()) && canStartSprinting && this.keys.isDown(VirtualKeys.Key.SPRINT)) {
			this.setSprinting(true);
		}

		// Like a player, sprinting continues after the sprint key is released until forward input stops.
		if (this.isSprinting()) {
			boolean lostForwardOrFood = !hasForwardImpulse || !this.hasEnoughFoodToSprint();
			boolean shouldStop = lostForwardOrFood || this.horizontalCollision && !this.minorHorizontalCollision || this.isInWater() && !this.isUnderWater();
			if (this.isSwimming()) {
				if (!this.onGround() && !sneakKey && lostForwardOrFood || !this.isInWater()) {
					this.setSprinting(false);
				}
			} else if (shouldStop) {
				this.setSprinting(false);
			}
		}

		this.xxa = left;
		this.zza = forward;
		this.jumping = this.keys.isDown(VirtualKeys.Key.JUMP);
	}

	private boolean hasEnoughFoodToSprint() {
		return this.isPassenger() || this.foodData.getFoodLevel() > FoodConstants.SPRINT_LEVEL;
	}

	public boolean isMovingSlowly() {
		return this.crouching || this.isVisuallyCrawling();
	}

	/** Player speed: always the movement-speed attribute (sprinting adds its modifier to it). */
	@Override
	public float getSpeed() {
		return (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	protected float getFlyingSpeed() {
		return this.isSprinting() ? SPRINT_AIR_SPEED : AIR_SPEED;
	}

	/** Player#travel: when swimming, vertical motion follows where the camera looks. */
	@Override
	public void travel(Vec3 input) {
		if (this.isSwimming() && !this.isPassenger()) {
			double lookY = this.getLookAngle().y;
			double rate = lookY < -0.2 ? 0.085 : 0.06;
			if (lookY <= 0.0
				|| this.jumping
				|| !this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() + 1.0 - 0.1, this.getZ())).getFluidState().isEmpty()) {
				Vec3 motion = this.getDeltaMovement();
				this.setDeltaMovement(motion.add(0.0, (lookY - motion.y) * rate, 0.0));
			}
		}

		super.travel(input);
	}

	/**
	 * Swimming up: a player rises 0.04 per tick while holding jump in water (LivingEntity#jumpInLiquid). Mob overrides it
	 * with 0.3 per tick for mobs whose navigation can't float, which shot the Drifter out of the water like a cork
	 * (found in testing: "bouncing very high"). Mirror the player's number.
	 */
	@Override
	protected void jumpInLiquid(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluid) {
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.04F, 0.0));
	}

	@Override
	public void jumpFromGround() {
		super.jumpFromGround();
		this.causeFoodExhaustion(this.isSprinting() ? FoodConstants.EXHAUSTION_SPRINT_JUMP : FoodConstants.EXHAUSTION_JUMP);
	}

	/** ServerPlayer#checkMovementStatistics, minus the statistics: the exhaustion part of moving. */
	private void checkMovementStatistics(double dx, double dy, double dz) {
		if (this.isPassenger() || dx == 0.0 && dy == 0.0 && dz == 0.0) {
			return;
		}

		if (this.isSwimming() || this.isEyeInFluid(FluidTags.WATER)) {
			int cm = Math.round((float)Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F);
			if (cm > 0) {
				this.causeFoodExhaustion(FoodConstants.EXHAUSTION_SWIM * cm * 0.01F);
			}
		} else if (this.isInWater()) {
			int cm = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
			if (cm > 0) {
				this.causeFoodExhaustion(FoodConstants.EXHAUSTION_SWIM * cm * 0.01F);
			}
		} else if (!this.onClimbable() && this.onGround()) {
			int cm = Math.round((float)Math.sqrt(dx * dx + dz * dz) * 100.0F);
			if (cm > 0) {
				float perCm = this.isSprinting() ? FoodConstants.EXHAUSTION_SPRINT
					: this.isCrouching() ? FoodConstants.EXHAUSTION_CROUCH : FoodConstants.EXHAUSTION_WALK;
				this.causeFoodExhaustion(perCm * cm * 0.01F);
			}
		}
	}

	/** Player#maybeBackOffFromEdge: while sneaking, never walk off an edge taller than step height. */
	@Override
	protected Vec3 maybeBackOffFromEdge(Vec3 movement, MoverType moverType) {
		float stepHeight = this.maxUpStep();
		if (!(movement.y > 0.0)
			&& (moverType == MoverType.SELF || moverType == MoverType.PLAYER)
			&& this.isShiftKeyDown()
			&& this.isAboveGround(stepHeight)) {
			double x = movement.x;
			double z = movement.z;
			double stepX = Math.signum(x) * 0.05;
			double stepZ = Math.signum(z) * 0.05;

			while (x != 0.0 && this.canFallAtLeast(x, 0.0, stepHeight)) {
				if (Math.abs(x) <= 0.05) {
					x = 0.0;
					break;
				}

				x -= stepX;
			}

			while (z != 0.0 && this.canFallAtLeast(0.0, z, stepHeight)) {
				if (Math.abs(z) <= 0.05) {
					z = 0.0;
					break;
				}

				z -= stepZ;
			}

			while (x != 0.0 && z != 0.0 && this.canFallAtLeast(x, z, stepHeight)) {
				if (Math.abs(x) <= 0.05) {
					x = 0.0;
				} else {
					x -= stepX;
				}

				if (Math.abs(z) <= 0.05) {
					z = 0.0;
				} else {
					z -= stepZ;
				}
			}

			return new Vec3(x, movement.y, z);
		}

		return movement;
	}

	private boolean isAboveGround(float stepHeight) {
		return this.onGround() || this.fallDistance < stepHeight && !this.canFallAtLeast(0.0, 0.0, stepHeight - this.fallDistance);
	}

	private boolean canFallAtLeast(double dx, double dz, float height) {
		AABB box = this.getBoundingBox();
		return this.level().noCollision(this, new AABB(box.minX + dx, box.minY - height - 1.0E-5F, box.minZ + dz, box.maxX + dx, box.minY, box.maxZ + dz));
	}

	// ---------------------------------------------------------------------------------------------
	// Poses and rotation
	// ---------------------------------------------------------------------------------------------

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return POSES.getOrDefault(pose, Player.STANDING_DIMENSIONS);
	}

	/** Player#updatePlayerPose, without flying and spectator. */
	private void updatePlayerPose() {
		// Leave vanilla's DYING pose alone once dead.
		if (this.isDeadOrDying() || !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.SWIMMING)) {
			return;
		}

		Pose wanted;
		if (this.isFallFlying()) {
			wanted = Pose.FALL_FLYING;
		} else if (this.isSleeping()) {
			wanted = Pose.SLEEPING;
		} else if (this.isSwimming()) {
			wanted = Pose.SWIMMING;
		} else if (this.isAutoSpinAttack()) {
			wanted = Pose.SPIN_ATTACK;
		} else if (this.isShiftKeyDown()) {
			wanted = Pose.CROUCHING;
		} else {
			wanted = Pose.STANDING;
		}

		Pose pose;
		if (this.isPassenger() || this.canPlayerFitWithinBlocksAndEntitiesWhen(wanted)) {
			pose = wanted;
		} else if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)) {
			pose = Pose.CROUCHING;
		} else {
			pose = Pose.SWIMMING;
		}

		this.setPose(pose);
	}

	private boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose pose) {
		return this.level().noCollision(this, this.getDimensions(pose).makeBoundingBox(this.position()).deflate(1.0E-7));
	}

	/**
	 * Player body rotation (LivingEntity#tickHeadTurn), replacing the mob BodyRotationControl: the body eases
	 * toward the movement direction and is pulled along once the head is more than 50 degrees off.
	 */
	@Override
	protected float tickHeadTurn(float movementYaw, float animStep) {
		float toMovement = Mth.wrapDegrees(movementYaw - this.yBodyRot);
		this.yBodyRot += toMovement * 0.3F;
		float headOffset = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
		float maxOffset = this.getMaxHeadRotationRelativeToBody();
		if (Math.abs(headOffset) > maxOffset) {
			this.yBodyRot = this.yBodyRot + (headOffset - Mth.sign(headOffset) * maxOffset);
		}

		boolean walkingBackwards = headOffset < -90.0F || headOffset >= 90.0F;
		if (walkingBackwards) {
			animStep *= -1.0F;
		}

		return animStep;
	}

	/** Player#getMaxHeadRotationRelativeToBody: the head stays within 15 degrees of the body while blocking. */
	@Override
	protected float getMaxHeadRotationRelativeToBody() {
		return this.isBlocking() ? 15.0F : super.getMaxHeadRotationRelativeToBody();
	}

	// ---------------------------------------------------------------------------------------------
	// Block breaking speed (mirrors Player#getDestroySpeed, Player#hasCorrectToolForDrops and
	// BlockBehaviour#getDestroyProgress). Computed on the Drifter itself because its attributes and effects
	// (Efficiency, Haste, Mining Fatigue, Aqua Affinity) are live here; the fake player never ticks.
	// ---------------------------------------------------------------------------------------------

	public float getDestroySpeed(BlockState state) {
		float speed = this.inventory.getSelectedItem().getDestroySpeed(state);
		if (speed > 1.0F) {
			speed += (float)this.getAttributeValue(Attributes.MINING_EFFICIENCY);
		}

		if (MobEffectUtil.hasDigSpeed(this)) {
			speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
		}

		if (this.hasEffect(MobEffects.DIG_SLOWDOWN)) {
			speed *= switch (this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
				case 0 -> 0.3F;
				case 1 -> 0.09F;
				case 2 -> 0.0027F;
				default -> 8.1E-4F;
			};
		}

		speed *= (float)this.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
		if (this.isEyeInFluid(FluidTags.WATER)) {
			speed *= (float)this.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
		}

		if (!this.onGround()) {
			speed /= 5.0F;
		}

		return speed;
	}

	public boolean hasCorrectToolForDrops(BlockState state) {
		return !state.requiresCorrectToolForDrops() || this.inventory.getSelectedItem().isCorrectToolForDrops(state);
	}

	/** Fraction of the block broken per tick of holding attack on it; 1.0 or more breaks it this tick. */
	public float getDestroyProgress(BlockState state, BlockGetter level, BlockPos pos) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness == -1.0F) {
			return 0.0F;
		}

		int divisor = this.hasCorrectToolForDrops(state) ? 30 : 100;
		return this.getDestroySpeed(state) / hardness / divisor;
	}

	public double blockInteractionRange() {
		return this.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
	}

	// ---------------------------------------------------------------------------------------------
	// Eating (mirrors Player#canEat and Player#eat)
	// ---------------------------------------------------------------------------------------------

	public boolean canEat(boolean canAlwaysEat) {
		return canAlwaysEat || this.foodData.needsFood();
	}

	@Override
	public ItemStack eat(Level level, ItemStack stack, FoodProperties food) {
		this.foodData.eat(food);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
		ItemStack result = super.eat(level, stack, food);
		Optional<ItemStack> leftover = food.usingConvertsTo();
		if (leftover.isPresent()) {
			if (result.isEmpty()) {
				return leftover.get().copy();
			}

			if (!level.isClientSide()) {
				this.inventory.add(leftover.get().copy());
			}
		}

		return result;
	}

	// ---------------------------------------------------------------------------------------------
	// Inventory and equipment
	// ---------------------------------------------------------------------------------------------

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		if (this.inventory == null) {
			// Called from a superclass constructor, before this class's fields exist.
			return ItemStack.EMPTY;
		}

		return switch (slot.getType()) {
			case HAND -> slot == EquipmentSlot.MAINHAND ? this.inventory.getSelectedItem() : this.inventory.getOffhand();
			case HUMANOID_ARMOR -> this.inventory.getArmor(slot);
			case ANIMAL_ARMOR -> ItemStack.EMPTY;
		};
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
		this.verifyEquippedItem(stack);
		ItemStack previous = this.getItemBySlot(slot);
		switch (slot.getType()) {
			case HAND -> this.inventory.setItem(slot == EquipmentSlot.MAINHAND ? this.inventory.getSelected() : DrifterInventory.OFFHAND_SLOT, stack);
			case HUMANOID_ARMOR -> this.inventory.setItem(DrifterInventory.MAIN_SIZE + slot.getIndex(), stack);
			case ANIMAL_ARMOR -> {
				return;
			}
		}

		this.onEquipItem(slot, previous, stack);
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return this.inventory == null ? List.of() : this.inventory.armorList();
	}

	@Override
	public Iterable<ItemStack> getHandSlots() {
		return List.of(this.getMainHandItem(), this.getOffhandItem());
	}

	/** Player#touch for item entities: picks up any item type into the inventory (section 2). */
	private void pickUpNearbyItems() {
		AABB reach;
		if (this.isPassenger() && !this.getVehicle().isRemoved()) {
			reach = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0);
		} else {
			reach = this.getBoundingBox().inflate(1.0, 0.5, 1.0);
		}

		for (ItemEntity item : this.level().getEntitiesOfClass(ItemEntity.class, reach)) {
			this.tryPickUp(item);
		}

		if (this.takeXpDelay > 0) {
			this.takeXpDelay--;
		}

		for (net.minecraft.world.entity.ExperienceOrb orb : this.level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, reach)) {
			this.touchOrb(orb);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Experience (section 10.13): the player's own rules and numbers
	// ---------------------------------------------------------------------------------------------

	private int experienceLevel;
	private float experienceProgress;
	private int totalExperience;
	private int enchantmentSeed;
	private int takeXpDelay;
	private int lastLevelUpTime;

	public int experienceLevel() {
		return this.experienceLevel;
	}

	public float experienceProgress() {
		return this.experienceProgress;
	}

	public int totalExperience() {
		return this.totalExperience;
	}

	public int enchantmentSeed() {
		return this.enchantmentSeed;
	}

	/** Copies the levels back from the player-hands helper after an interaction (enchanting spends them). */
	public void setExperience(int level, float progress, int total, int seed) {
		this.experienceLevel = level;
		this.experienceProgress = progress;
		this.totalExperience = total;
		this.enchantmentSeed = seed;
	}

	/**
	 * ExperienceOrb#playerTouch for a Drifter: one orb every 2 ticks, and Mending repairs damaged gear with it first.
	 * Orbs fly only to players, so it walks over them (as with items).
	 */
	private void touchOrb(net.minecraft.world.entity.ExperienceOrb orb) {
		if (orb.isRemoved() || this.takeXpDelay > 0 || !(this.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}

		this.takeXpDelay = 2;
		this.take(orb, 1);
		int left = this.repairWithXp(level, orb.getValue());
		if (left > 0) {
			this.giveExperiencePoints(left);
		}

		com.dinglebob.drifter.mixin.ExperienceOrbAccessor counted = (com.dinglebob.drifter.mixin.ExperienceOrbAccessor)orb;
		counted.drifter$setCount(counted.drifter$getCount() - 1);
		if (counted.drifter$getCount() <= 0) {
			orb.discard();
		}
	}

	/** ExperienceOrb#repairPlayerItems: Mending gear soaks up the experience first. */
	private int repairWithXp(net.minecraft.server.level.ServerLevel level, int amount) {
		var mending = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, this, ItemStack::isDamaged);
		if (mending.isEmpty()) {
			return amount;
		}

		ItemStack stack = mending.get().itemStack();
		int repairable = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, amount);
		int repaired = Math.min(repairable, stack.getDamageValue());
		stack.setDamageValue(stack.getDamageValue() - repaired);
		if (repaired > 0) {
			int left = amount - repaired * amount / repairable;
			if (left > 0) {
				return this.repairWithXp(level, left);
			}
		}

		return 0;
	}

	/** Player#giveExperiencePoints. */
	public void giveExperiencePoints(int points) {
		this.experienceProgress += (float)points / this.xpNeededForNextLevel();
		this.totalExperience = Mth.clamp(this.totalExperience + points, 0, Integer.MAX_VALUE);
		while (this.experienceProgress < 0.0F) {
			float f = this.experienceProgress * this.xpNeededForNextLevel();
			if (this.experienceLevel > 0) {
				this.giveExperienceLevels(-1);
				this.experienceProgress = 1.0F + f / this.xpNeededForNextLevel();
			} else {
				this.giveExperienceLevels(-1);
				this.experienceProgress = 0.0F;
			}
		}

		while (this.experienceProgress >= 1.0F) {
			this.experienceProgress = (this.experienceProgress - 1.0F) * this.xpNeededForNextLevel();
			this.giveExperienceLevels(1);
			this.experienceProgress = this.experienceProgress / this.xpNeededForNextLevel();
		}
	}

	/** Player#giveExperienceLevels, with the level-up chime every 5 levels. */
	public void giveExperienceLevels(int levels) {
		this.experienceLevel += levels;
		if (this.experienceLevel < 0) {
			this.experienceLevel = 0;
			this.experienceProgress = 0.0F;
			this.totalExperience = 0;
		}

		if (levels > 0 && this.experienceLevel % 5 == 0 && this.lastLevelUpTime < this.tickCount - 100) {
			float volume = this.experienceLevel > 30 ? 1.0F : this.experienceLevel / 30.0F;
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
				this.getSoundSource(), volume * 0.75F, 1.0F);
			this.lastLevelUpTime = this.tickCount;
		}
	}

	/** Player#getXpNeededForNextLevel. */
	public int xpNeededForNextLevel() {
		if (this.experienceLevel >= 30) {
			return 112 + (this.experienceLevel - 30) * 9;
		}

		return this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2;
	}

	/** Like a player, it always drops some of its experience when it dies (Player#getBaseExperienceReward). */
	@Override
	public boolean isAlwaysExperienceDropper() {
		return true;
	}

	@Override
	protected int getBaseExperienceReward() {
		return Math.min(this.experienceLevel * 7, 100);
	}

	/** ItemEntity#playerTouch, for a Drifter. TODO(milestone 7+): pickup filter and junk list (section 10.18). */
	private void tryPickUp(ItemEntity item) {
		if (item.isRemoved() || item.hasPickUpDelay()) {
			return;
		}

		UUID target = ((ItemEntityAccessor)item).drifter$getTarget();
		if (target != null && !target.equals(this.getUUID())) {
			return;
		}

		ItemStack stack = item.getItem();
		// Section 10.18 pickup filter: nearly full, it leaves junk lying (and never picks back up what it just threw).
		// Never what it threw away itself.
		if (item.getOwner() == this
			|| com.dinglebob.drifter.brain.Storage.freeSlots(this) <= 2 && com.dinglebob.drifter.brain.Storage.isJunk(this, stack)) {
			return;
		}

		int count = stack.getCount();
		if (this.inventory.add(stack)) {
			this.take(item, count);
			if (stack.isEmpty()) {
				item.discard();
				stack.setCount(count);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Damage and death
	// ---------------------------------------------------------------------------------------------

	/** Touch (section 5): it feels every hit, even one its armor fully absorbs. */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		// Its own swings go through its fake-player helper, standing where it stands: a sword's sweep would hit itself.
		if (this.ownSwing || source.getEntity() != null && source.getEntity().getUUID().equals(com.dinglebob.drifter.hands.FakePlayerBridge.helperId(this))) {
			return false;
		}

		// Player#hurt: damage that scales with difficulty hurts it like it hurts a player (section 1, "Player vulnerability").
		if (source.scalesWithDifficulty()) {
			switch (this.level().getDifficulty()) {
				case PEACEFUL -> amount = 0.0F;
				case EASY -> amount = Math.min(amount / 2.0F + 1.0F, amount);
				case HARD -> amount = amount * 3.0F / 2.0F;
				default -> {
				}
			}

			if (amount == 0.0F) {
				return false;
			}
		}

		boolean hit = super.hurt(source, amount);
		if (hit && !this.level().isClientSide) {
			this.senses.onHurt(source, amount);
			this.reflexes.onHurt(source);
			this.brain.poke();
			this.mannerisms.onHurt(amount);
			this.personality.feel(com.dinglebob.drifter.personality.Mood.ANXIOUS, amount / 10.0, "got hurt");
			if (this.getHealth() <= 6.0F) {
				this.personality.feel(com.dinglebob.drifter.personality.Mood.PANICKED, 0.3, "nearly dead");
			}
		}

		return hit;
	}

	/** Taking damage costs food exhaustion, like a player (only when health actually drops). */
	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		float healthBefore = this.getHealth();
		super.actuallyHurt(source, amount);
		if (this.getHealth() < healthBefore) {
			this.causeFoodExhaustion(source.getFoodExhaustion());
		}
	}

	/** Mob equipment drop chances don't apply: the whole inventory drops in dropEquipment, like a player. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
	}

	/**
	 * Player#dropEquipment: drops the entire inventory, armor and offhand, scattered around (Curse of Vanishing items are destroyed).
	 * TODO(respawn milestone): respect keepInventory once the Drifter can respawn; until then items always drop
	 * so they aren't lost.
	 */
	@Override
	public void die(DamageSource source) {
		this.tasks.cancel("died");
		if (!this.blackBox.isEmpty()) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[black box] last {} ticks before death:", this.blackBox.size());
			this.blackBox.forEach(com.dinglebob.drifter.Drifter.LOGGER::info);
			this.blackBox.clear();
		}

		super.die(source);
	}

	@Override
	protected void dropEquipment() {
		super.dropEquipment();
		for (ItemStack stack : this.inventory.removeAll()) {
			if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				this.dropScattered(stack);
			}
		}
	}

	/** Player#drop(stack, false, true), pressing Q: thrown forward where it looks, with the pickup delay. */
	public void tossForward(ItemStack stack) {
		ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getEyeY() - 0.3F, this.getZ(), stack);
		item.setPickUpDelay(40);
		item.setThrower(this);
		float pitch = this.getXRot() * Mth.DEG_TO_RAD;
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		float sinPitch = Mth.sin(pitch);
		float cosPitch = Mth.cos(pitch);
		float sinYaw = Mth.sin(yaw);
		float cosYaw = Mth.cos(yaw);
		float spread = this.random.nextFloat() * (float)(Math.PI * 2);
		float spreadAmount = 0.02F * this.random.nextFloat();
		item.setDeltaMovement(-sinYaw * cosPitch * 0.3F + Math.cos(spread) * spreadAmount,
			-sinPitch * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F,
			cosYaw * cosPitch * 0.3F + Math.sin(spread) * spreadAmount);
		this.level().addFreshEntity(item);
		this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
	}

	/** Player#drop(stack, true, false): from just below the eyes, in a random direction, with the pickup delay. */
	public void dropScattered(ItemStack stack) {
		ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getEyeY() - 0.3F, this.getZ(), stack);
		item.setPickUpDelay(40);
		float speed = this.random.nextFloat() * 0.5F;
		float angle = this.random.nextFloat() * (float)(Math.PI * 2);
		item.setDeltaMovement(-Mth.sin(angle) * speed, 0.2F, Mth.cos(angle) * speed);
		this.level().addFreshEntity(item);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return source.type().effects().sound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	public LivingEntity.Fallsounds getFallSounds() {
		return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
	}

	@Override
	protected SoundEvent getSwimSound() {
		return SoundEvents.PLAYER_SWIM;
	}

	@Override
	protected SoundEvent getSwimSplashSound() {
		return SoundEvents.PLAYER_SPLASH;
	}

	@Override
	protected SoundEvent getSwimHighSpeedSplashSound() {
		return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.PLAYERS;
	}

	// ---------------------------------------------------------------------------------------------
	// Lifecycle and saving
	// ---------------------------------------------------------------------------------------------

	/** Drifters never despawn like ordinary mobs (section 4). TODO(spawning milestone): fresh, unattached Drifters may despawn. */
	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	/** Players can't be put on a lead. */
	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("DrifterSaveVersion", SAVE_VERSION);
		tag.put("DrifterInventory", this.inventory.save(this.registryAccess()));
		tag.putInt("SelectedItemSlot", this.inventory.getSelected());
		this.foodData.addAdditionalSaveData(tag);
		tag.putString("Skin", this.getSkinTexture().toString());
		tag.putBoolean("SlimArms", this.hasSlimArms());
		tag.put("DrifterMemory", this.memory.save());
		tag.put("DrifterBrain", this.brain.save());
		tag.put("DrifterPersonality", this.personality.save());
		tag.putBoolean("DrifterNamed", this.named);
		tag.putFloat("XpP", this.experienceProgress);
		tag.putInt("XpLevel", this.experienceLevel);
		tag.putInt("XpTotal", this.totalExperience);
		tag.putInt("XpSeed", this.enchantmentSeed);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// Version 1 is the first format; future versions migrate older tags here.
		this.inventory.load(this.registryAccess(), tag.getList("DrifterInventory", Tag.TAG_COMPOUND));
		this.inventory.setSelectedImmediately(tag.getInt("SelectedItemSlot"));
		this.foodData.readAdditionalSaveData(tag);
		this.experienceProgress = tag.getFloat("XpP");
		this.experienceLevel = tag.getInt("XpLevel");
		this.totalExperience = tag.getInt("XpTotal");
		this.enchantmentSeed = tag.getInt("XpSeed");
		if (this.enchantmentSeed == 0) {
			this.enchantmentSeed = this.random.nextInt();
		}

		if (tag.contains("DrifterBrain", Tag.TAG_COMPOUND)) {
			this.brain.load(tag.getCompound("DrifterBrain"));
		}

		// Drifters saved before milestone 7 have no personality yet: they keep the one rolled at load, which is then saved.
		this.named = tag.getBoolean("DrifterNamed");
		if (tag.contains("DrifterPersonality", Tag.TAG_COMPOUND)) {
			this.personality.load(tag.getCompound("DrifterPersonality"));
		}

		if (tag.contains("DrifterMemory", Tag.TAG_COMPOUND)) {
			this.memory.load(tag.getCompound("DrifterMemory"));
		}

		if (tag.contains("Skin", Tag.TAG_STRING)) {
			ResourceLocation texture = ResourceLocation.tryParse(tag.getString("Skin"));
			if (texture != null) {
				this.setSkin(new SkinPool.Skin(texture, tag.getBoolean("SlimArms")));
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Vanilla mob controls, disabled: the motor layer owns the body.
	// ---------------------------------------------------------------------------------------------

	private static final class IdleMoveControl extends MoveControl {
		IdleMoveControl(DrifterEntity drifter) {
			super(drifter);
		}

		@Override
		public void tick() {
		}
	}

	private static final class IdleLookControl extends LookControl {
		IdleLookControl(DrifterEntity drifter) {
			super(drifter);
		}

		@Override
		public void tick() {
		}
	}

	private static final class IdleJumpControl extends JumpControl {
		IdleJumpControl(DrifterEntity drifter) {
			super(drifter);
		}

		@Override
		public void tick() {
		}
	}
}
