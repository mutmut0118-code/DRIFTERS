package com.dinglebob.drifter.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.common.collect.ImmutableMap;

import com.dinglebob.drifter.executor.TaskRunner;
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
	/** Ticks until the debug command's held keys are released; -1 for "hold until changed". */
	private int keysReleaseTimer = -1;
	/** Mirrors LocalPlayer#crouching: true while the sneak key is held (or there is no room to stand). */
	private boolean crouching;

	public DrifterEntity(EntityType<? extends DrifterEntity> entityType, Level level) {
		super(entityType, level);
		this.camera = new CameraModel(this.random);
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

	public TaskRunner getTasks() {
		return this.tasks;
	}

	/** Holds keys for a number of ticks, then releases them all. Ticks of -1 hold until changed. */
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

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide) {
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
		this.tasks.tick();
		float[] look = this.camera.tick(this.getEyePosition(), this.getYRot(), this.getXRot());
		this.setYRot(look[0]);
		this.setXRot(look[1]);
		this.setYHeadRot(look[0]);
		this.applyMovementKeys();
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
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// Version 1 is the first format; future versions migrate older tags here.
		this.inventory.load(this.registryAccess(), tag.getList("DrifterInventory", Tag.TAG_COMPOUND));
		this.inventory.setSelectedImmediately(tag.getInt("SelectedItemSlot"));
		this.foodData.readAdditionalSaveData(tag);
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
