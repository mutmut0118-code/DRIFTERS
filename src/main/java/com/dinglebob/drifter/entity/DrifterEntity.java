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
	/** A real player's skin (Mojang's textures text), or empty: clients download it as they do a player's (RealSkins). */
	private static final EntityDataAccessor<String> DATA_REAL_SKIN = SynchedEntityData.defineId(DrifterEntity.class, EntityDataSerializers.STRING);
	/** Its last roll, for the roll animation: (count << 17) | (duration ticks << 9) | direction in degrees. */
	private static final EntityDataAccessor<Integer> DATA_ROLL = SynchedEntityData.defineId(DrifterEntity.class, EntityDataSerializers.INT);
	/** Its last Better Combat swing, for the swing animation: "count;animation;ticks;mirrored" ("" for none yet). */
	private static final EntityDataAccessor<String> DATA_SWING = SynchedEntityData.defineId(DrifterEntity.class, EntityDataSerializers.STRING);

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
	/** Its standing with each player it has met, and its disposition (section 14, milestone 9). */
	private final com.dinglebob.drifter.social.Relationships social = new com.dinglebob.drifter.social.Relationships(this);
	/** Its standing with other Drifters (milestone 12). */
	private final com.dinglebob.drifter.social.DrifterBonds bonds = new com.dinglebob.drifter.social.DrifterBonds(this);
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
			if (com.dinglebob.drifter.memory.ThoughtLog.echo && this.traceTicks <= 0 && autoTraceAllowed(now)) {
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
	private final java.util.ArrayDeque<TraceFrame> blackBox = new java.util.ArrayDeque<>();
	@org.jetbrains.annotations.Nullable
	private com.dinglebob.drifter.executor.Task blackBoxLeaf;
	private int blackBoxStepAt;
	private String blackBoxStep = "none";
	/** Black boxes written out lately (deaths in a battle come by the hundred: at most one every few seconds). */
	private static long lastBlackBoxDump = Long.MIN_VALUE / 2;

	/** One tick of the black box, raw (formatted only when written out). {@code use}: ticks using an item, negative in the off hand. */
	private record TraceFrame(int tick, double x, double y, double z, double vy, int flags, Pose pose, float yaw, float pitch, int keys, String step,
		net.minecraft.world.item.Item hand, int use, String reflex) {
		String format(String name) {
			return String.format(java.util.Locale.ROOT, "[trace %s] t%d pos %.2f %.2f %.2f vy %.3f %s%s%s%s%s pose %s yaw %.0f pitch %.0f keys %s step %s hand %s%s%s%s",
				name, this.tick, this.x, this.y, this.z, this.vy, (this.flags & 1) != 0 ? "G" : "-", (this.flags & 2) != 0 ? "W" : "-",
				(this.flags & 4) != 0 ? "U" : "-", (this.flags & 8) != 0 ? "S" : "-", (this.flags & 16) != 0 ? "R" : "-", this.pose, this.yaw, this.pitch,
				com.dinglebob.drifter.motor.VirtualKeys.describe(this.keys), this.step,
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.hand).getPath(),
				this.use == 0 ? "" : " using " + (this.use < 0 ? "off" : "main") + " " + (Math.abs(this.use) - 1), (this.flags & 32) != 0 ? " BLOCKING" : "",
				this.reflex == null || this.reflex.isEmpty() ? "" : " reflex " + this.reflex);
		}
	}
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

	/** Blocks a route had it dig that it couldn't (couldn't see or reach them from where it stood), until a game time. */
	private final java.util.Map<BlockPos, Long> failedDigs = new java.util.HashMap<>();

	/**
	 * Remembers that digging this block on the way didn't work, for two minutes: routes go another way (found in a walking
	 * test on Tectonic's jagged peaks: the same snow block planned and failed fifteen times over, then it gave up).
	 */
	/** When a boat crossing last went wrong (NavigateTask leaves the boat in its pack for a minute after). */
	private long boatTroubleAt = Long.MIN_VALUE / 2;

	public void boatTrouble() {
		this.boatTroubleAt = this.level().getGameTime();
	}

	public long boatTroubleAt() {
		return this.boatTroubleAt;
	}

	public void digFailedAt(BlockPos cell) {
		this.failedDigs.put(cell.immutable(), this.level().getGameTime() + 20L * 60 * 2);
	}

	/** Route digs that failed lately (not to be planned again for a while), a copy. */
	public java.util.Set<BlockPos> recentFailedDigs() {
		if (this.failedDigs.isEmpty()) {
			return java.util.Set.of();
		}

		long now = this.level().getGameTime();
		this.failedDigs.values().removeIf(until -> until < now);
		return java.util.Set.copyOf(this.failedDigs.keySet());
	}

	/**
	 * Blocks it dug out itself, the latest few thousand (saved): its own tunnels and stairs aren't caves to go and
	 * explore (user's report: it saw its old strip mine as "a cave" and went back to it).
	 */
	private final java.util.LinkedHashSet<Long> dugCells = new java.util.LinkedHashSet<>();
	/** How many dug blocks it remembers. */
	private static final int MAX_DUG_CELLS = 8192;

	public void dugOut(BlockPos pos) {
		long key = pos.asLong();
		this.dugCells.remove(key);
		this.dugCells.add(key);
		if (this.dugCells.size() > MAX_DUG_CELLS) {
			var oldest = this.dugCells.iterator();
			oldest.next();
			oldest.remove();
		}
	}

	/** It dug this block out itself (part of its own tunnel, not a cave). */
	public boolean dugOut(long pos) {
		return this.dugCells.contains(pos);
	}

	/**
	 * Blocks it put down only to get somewhere (pillars, bridges), the latest couple of thousand (saved): tidy Drifters
	 * take the ones round home down again (user's request).
	 */
	private final java.util.LinkedHashSet<BlockPos> scaffold = new java.util.LinkedHashSet<>();
	private static final int MAX_SCAFFOLD = 2048;

	public void placedScaffold(BlockPos pos) {
		this.scaffold.add(pos.immutable());
		if (this.scaffold.size() > MAX_SCAFFOLD) {
			var oldest = this.scaffold.iterator();
			oldest.next();
			oldest.remove();
		}
	}

	public boolean isScaffold(BlockPos pos) {
		return this.scaffold.contains(pos);
	}

	public void forgetScaffold(BlockPos pos) {
		this.scaffold.remove(pos);
	}

	/** Its scaffold blocks (a copy). */
	public java.util.List<BlockPos> scaffold() {
		return java.util.List.copyOf(this.scaffold);
	}

	/** Where it got burned by lava lately, until a game time: routes keep clear of them and the blocks touching them. */
	private final java.util.Map<BlockPos, Long> lavaBurns = new java.util.HashMap<>();

	/** Remembers a spot where it stepped into lava (for 10 minutes; found in testing: the next route led straight back in). */
	public void burnedByLavaAt(BlockPos pos) {
		this.lavaBurns.put(pos.immutable(), this.level().getGameTime() + 20L * 60 * 10);
	}

	/** At or right beside (diagonals too) a spot where it got into lava lately. */
	/** Where it got into lava lately (still to be kept clear of), a copy. */
	public java.util.List<BlockPos> recentLavaBurns() {
		if (this.lavaBurns.isEmpty()) {
			return java.util.List.of();
		}

		long now = this.level().getGameTime();
		this.lavaBurns.values().removeIf(until -> until < now);
		return java.util.List.copyOf(this.lavaBurns.keySet());
	}

	/** Bridges and pillars that failed lately (not to be planned again for a while), copies. */
	public java.util.Set<BlockPos> recentFailedBridges() {
		long now = this.level().getGameTime();
		this.failedBridges.values().removeIf(until -> until < now);
		return java.util.Set.copyOf(this.failedBridges.keySet());
	}

	public java.util.Set<BlockPos> recentFailedPillars() {
		long now = this.level().getGameTime();
		this.failedPillars.values().removeIf(until -> until < now);
		return java.util.Set.copyOf(this.failedPillars.keySet());
	}

	public boolean nearRecentLava(BlockPos pos) {
		if (this.lavaBurns.isEmpty()) {
			return false;
		}

		long now = this.level().getGameTime();
		this.lavaBurns.values().removeIf(until -> until < now);
		for (BlockPos burn : this.lavaBurns.keySet()) {
			if (burn.distSqr(pos) <= 2) {
				return true;
			}
		}

		return false;
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
	/** Standing on the ground with nothing under the middle of its feet: it's held up by the edge of a block next door
	 * (found in testing: creeping along a cliff edge, it stopped sneaking there and the last of its drift tipped it over). */
	private boolean overhanging() {
		BlockPos under = this.blockPosition().below();
		return this.level().getBlockState(under).getCollisionShape(this.level(), under).isEmpty();
	}

	/** Open air at least 4 blocks deep right beside where it stands, or under it when it's over an edge (not water). */
	private boolean deepDropBeside() {
		BlockPos feet = this.blockPosition();
		for (BlockPos cell : new BlockPos[] {feet.north(), feet.south(), feet.west(), feet.east(), feet}) {
			boolean drop = true;
			for (int down = cell == feet ? 1 : 0; down <= 3 + (cell == feet ? 1 : 0) && drop; down++) {
				BlockPos below = cell.below(down);
				drop = this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty() && this.level().getFluidState(below).isEmpty();
			}

			if (drop) {
				return true;
			}
		}

		return false;
	}

	/** Mirrors LocalPlayer#crouching: true while the sneak key is held (or there is no room to stand). */
	private boolean crouching;

	public DrifterEntity(EntityType<? extends DrifterEntity> entityType, Level level) {
		super(entityType, level);
		this.camera = new CameraModel(this.random);
		this.personality = com.dinglebob.drifter.personality.Personality.roll(this.random, null);
		this.senses = new Senses(this, this.memory);
		this.memory.thoughts().setOwner(() -> this.getName().getString());
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
		builder.define(DATA_REAL_SKIN, "");
		builder.define(DATA_ROLL, 0);
		builder.define(DATA_SWING, "");
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

	public com.dinglebob.drifter.social.DrifterBonds getBonds() {
		return this.bonds;
	}

	public com.dinglebob.drifter.social.Relationships getSocial() {
		return this.social;
	}

	public com.dinglebob.drifter.personality.Personality getPersonality() {
		return this.personality;
	}

	/** Where its movement keys were pressed toward this tick (Steering), to aim them again after the head turns. */
	@org.jetbrains.annotations.Nullable
	private net.minecraft.world.phys.Vec3 steerGoal;
	private int steerTick = -1;

	/** Null: the keys pressed this tick aren't toward a point (a sideways shuffle), so they're left as they are. */
	public void steeringToward(@org.jetbrains.annotations.Nullable net.minecraft.world.phys.Vec3 goal) {
		this.steerGoal = goal;
		this.steerTick = this.tickCount;
	}

	/** When it was last running before it was loaded (from its save), or -1: catch-up runs on its first tick. */
	private long lastActive = -1L;

	/** Days of life it has to catch up on that it never lived (a Drifter made with the world in its camp), in ticks. */
	private long pendingCatchUp;

	/** Passing through (user's request, 2026-10-17): which way it's headed, since when, and whether a player has met it. */
	private boolean passingThrough;
	private float passHeading;
	private long passingSince;
	private boolean metOnItsWay;

	public boolean passingThrough() {
		return this.passingThrough;
	}

	public void startPassingThrough(float heading, long now) {
		this.passingThrough = true;
		this.passHeading = heading;
		this.passingSince = now;
	}

	public float passHeading() {
		return this.passHeading;
	}

	public void turnPassHeading(float by) {
		this.passHeading += by;
	}

	public long passingSince() {
		return this.passingSince;
	}

	public void markMetOnItsWay() {
		this.metOnItsWay = true;
	}

	public boolean wasMetOnItsWay() {
		return this.metOnItsWay;
	}

	/** Delving an underground structure (user's request, 2026-10-17): until when, and where it went in. */
	private long delveUntil;
	@org.jetbrains.annotations.Nullable
	private BlockPos delveFrom;

	public boolean delving() {
		return this.delveFrom != null && this.level().getGameTime() < this.delveUntil;
	}

	@org.jetbrains.annotations.Nullable
	public BlockPos delveFrom() {
		return this.delveFrom;
	}

	public void startDelving(BlockPos from, long until) {
		this.delveFrom = from.immutable();
		this.delveUntil = until;
		this.delver = true;
	}

	/**
	 * A mineshaft delver (user, 2026-10-17: "mineshaft delvers should [despawn] ... its more than a mob than a drifter"): an
	 * encounter, not a resident. Never saved with the world (gone when its area unloads), and gone half a minute after no
	 * player is within 96 blocks.
	 */
	private boolean delver;
	private int aloneSeconds;
	private static final double DELVER_STAYS = 96.0;
	private static final int DELVER_ALONE_SECONDS = 30;

	public boolean isDelver() {
		return this.delver;
	}

	@Override
	public boolean shouldBeSaved() {
		return !this.delver && super.shouldBeSaved();
	}

	private void delverDespawn() {
		if (!this.delver || this.tickCount % 20 != 0) {
			return;
		}

		boolean near = false;
		for (net.minecraft.world.entity.player.Player player : this.level().players()) {
			near |= !player.isSpectator() && com.dinglebob.drifter.social.SocialWatcher.isRealPlayer(player)
				&& player.distanceToSqr(this) < DELVER_STAYS * DELVER_STAYS;
		}

		this.aloneSeconds = near ? 0 : this.aloneSeconds + 1;
		if (this.aloneSeconds >= DELVER_ALONE_SECONDS) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] {} (a delver) went off into the dark", this.getName().getString());
			this.discard();
		}
	}

	public void stopDelving() {
		this.delveUntil = 0L;
	}

	public void addPendingCatchUp(long ticks) {
		this.pendingCatchUp += ticks;
	}

	/** Arrived from abstract travel: catch-up covers the time from its arrival. */
	public void setLastActive(long time) {
		this.lastActive = time;
	}

	/** Set when a route leads outside the running world with nobody near: it leaves as a traveler at the end of the tick. */
	@org.jetbrains.annotations.Nullable
	private BlockPos travelTo;

	public void requestTravel(BlockPos target) {
		this.travelTo = target.immutable();
	}

	/** After going through a portal, as a player: ready to use one again almost at once (mobs wait 15 s). */
	@Override
	public int getDimensionChangingDelay() {
		return 10;
	}

	/** Detail tier (section 22), from the players round it, checked once a second. */
	private DetailTier tier = DetailTier.FULL;
	/** Set by `/drifter tier` (debug); null for automatic. */
	@org.jetbrains.annotations.Nullable
	private DetailTier forcedTier;

	public DetailTier tier() {
		return this.tier;
	}

	public void forceTier(@org.jetbrains.annotations.Nullable DetailTier forced) {
		this.forcedTier = forced;
		this.tier = DetailTier.of(this, forced);
	}

	/** In a fight or running from one: combat stays at full detail in every tier (section 22). */
	/** In a fight of any kind: fighting, running, killing on sight, chasing (for its eyes' tunnel vision; milestone 16). */
	public boolean inCombat() {
		var goal = this.brain.currentGoal();
		if (goal == null) {
			return false;
		}

		String id = goal.id();
		return "fight".equals(id) || "flee".equals(id) || "rampage".equals(id) || "pursue".equals(id);
	}

	private boolean engaged() {
		var goal = this.brain.currentGoal();
		return goal != null && ("fight".equals(goal.id()) || "flee".equals(goal.id()));
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
	/** Client side: the tick its current roll started, its direction and length (for the animation). */
	private int rollStartAge = Integer.MIN_VALUE / 2;
	private float rollYaw;
	private int rollTicks = 1;

	/**
	 * Server side: tells clients it just swung with this Better Combat animation, lasting this long, with a weapon this
	 * long (for the trail) that reaches the top of its swing after {@code upswingTicks}.
	 */
	public void showSwing(String animation, float ticks, boolean mirrored, float range, int upswingTicks) {
		String last = this.entityData.get(DATA_SWING);
		int count = 0;
		int split = last.indexOf(';');
		if (split > 0) {
			try {
				count = Integer.parseInt(last.substring(0, split));
			} catch (NumberFormatException e) {
				count = 0;
			}
		}

		this.entityData.set(DATA_SWING, (count + 1) % 100000 + ";" + animation + ";" + Math.max(1, Math.round(ticks)) + ";" + mirrored + ";"
			+ String.format(java.util.Locale.ROOT, "%.2f", range) + ";" + Math.max(0, upswingTicks));
	}

	/** Its last Better Combat swing (see DATA_SWING). */
	public String swingData() {
		return this.entityData.get(DATA_SWING);
	}

	/**
	 * It steers only a saddled horse (or a boat), as a player does: on a horse being broken in, the horse goes its own way
	 * (and bucks; a mob steering it would switch its movement off, so it would never buck or come round).
	 */
	@Override
	public boolean canControlVehicle() {
		net.minecraft.world.entity.Entity vehicle = this.getVehicle();
		return vehicle instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse ? horse.isSaddled()
			: vehicle instanceof net.minecraft.world.entity.vehicle.Boat;
	}

	/** The tick it last meant to stay on what it's riding (a ride, breaking in a horse). */
	private int rideIntentAt = Integer.MIN_VALUE / 2;

	/** Called every tick by whatever has it riding on purpose. */
	public void keepRiding() {
		this.rideIntentAt = this.tickCount;
	}

	/** Riding a horse or a boat with nothing using the ride any more (another goal took over): it gets off. */
	private void dismountIfIdle() {
		if ((this.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse || this.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat)
			&& this.tickCount - this.rideIntentAt > 20) {
			this.stopRiding();
		}
	}

	/** Its jump key is down (riding, it makes its horse jump). */
	public boolean isJumpingNow() {
		return this.jumping;
	}

	/** How many rolls clients have been told of (a new number: a new roll). */
	public int rollCount() {
		return this.entityData.get(DATA_ROLL) >>> 17;
	}

	/** Client side: the last roll's length in ticks. */
	public int rollTicks() {
		return this.rollTicks;
	}

	/** Server side: tells clients it just rolled this way, for this many ticks. */
	public void showRoll(float yaw, int ticks) {
		int count = (this.entityData.get(DATA_ROLL) >>> 17) + 1;
		int degrees = Math.floorMod(Math.round(yaw), 360);
		this.entityData.set(DATA_ROLL, (count & 0x3FFF) << 17 | Math.max(1, Math.min(255, ticks)) << 9 | degrees);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ROLL.equals(key) && this.level().isClientSide) {
			int roll = this.entityData.get(DATA_ROLL);
			if (roll != 0) {
				this.rollStartAge = this.tickCount;
				this.rollTicks = Math.max(1, roll >>> 9 & 0xFF);
				this.rollYaw = roll & 0x1FF;
			}
		}
	}

	/** Client side: how far through a roll it is (0 to 1), or -1 if it isn't rolling. */
	public float rollProgress(float partialTick) {
		float progress = (this.tickCount - this.rollStartAge + partialTick) / this.rollTicks;
		return progress >= 0.0F && progress < 1.0F ? progress : -1.0F;
	}

	/** Client side: the way its current roll goes (degrees, as an entity's yaw). */
	public float rollYaw() {
		return this.rollYaw;
	}

	/** Its favorite weapon family (picked from its personality; see Weapons), and the weapon data version it was picked from. */
	@org.jetbrains.annotations.Nullable
	private com.dinglebob.drifter.brain.Weapons.Family favoriteWeapon;
	private int favoriteWeaponVersion = -1;

	@org.jetbrains.annotations.Nullable
	public com.dinglebob.drifter.brain.Weapons.Family favoriteWeapon(int version) {
		return this.favoriteWeaponVersion == version ? this.favoriteWeapon : null;
	}

	public void setFavoriteWeapon(com.dinglebob.drifter.brain.Weapons.Family family, int version) {
		this.favoriteWeapon = family;
		this.favoriteWeaponVersion = version;
	}

	/** A unique weapon ability's wind-up (ticks so far; 0 none), the tick it was last wound up, and when to try again. */
	private int abilityHold;
	private long abilityHeldAt;
	private long abilityReadyAt;

	public int abilityHold() {
		return this.level().getGameTime() - this.abilityHeldAt > 2 ? 0 : this.abilityHold;
	}

	public void setAbilityHold(int ticks) {
		this.abilityHold = ticks;
		this.abilityHeldAt = this.level().getGameTime();
	}

	public long abilityReadyAt() {
		return this.abilityReadyAt;
	}

	public void setAbilityReadyAt(long tick) {
		this.abilityReadyAt = tick;
	}

	/** The server tick a Drifter last caught up in (catch-ups take turns). */
	private static long catchUpTick = Long.MIN_VALUE / 2;
	/** Ticks between two Drifters' catch-ups (each can take 20 to 100 ms: spread out, a new area's Drifters don't freeze it). */
	private static final long CATCH_UP_SPACING = 5L;

	/** Combat Roll: rolls ready (-1: not counted yet, all of them), the next one's recharge so far, and when that was worked out. */
	private int rollCharges = -1;
	private float rollProgress;
	private long rollUpdatedAt;

	public int rollCharges() {
		return this.rollCharges;
	}

	public float rollProgress() {
		return this.rollProgress;
	}

	public long rollUpdatedAt() {
		return this.rollUpdatedAt;
	}

	public void setRollState(int charges, float progress, long at) {
		this.rollCharges = charges;
		this.rollProgress = progress;
		this.rollUpdatedAt = at;
	}

	/** Its place in a Better Combat weapon's combo (back to the first attack after a pause, as for a player). */
	private int combo;

	/** The combo step for a swing this many ticks after its last one (a long enough pause starts the combo again). */
	public int comboFor(int ticksSinceAttack) {
		if (ticksSinceAttack > COMBO_RESET_TICKS) {
			this.combo = 0;
		}

		return this.combo;
	}

	public void nextCombo() {
		this.combo++;
	}

	/** A pause this long between swings starts the combo over. */
	private static final int COMBO_RESET_TICKS = 40;

	public int ticksSinceAttack() {
		return (int)Math.min(Integer.MAX_VALUE, this.level().getGameTime() - this.lastAttackTime);
	}

	public void resetAttackTimer() {
		this.lastAttackTime = this.level().getGameTime();
	}

	/** Can it raise its shield now (holding one in the offhand, not disabled by an axe hit)? */
	public boolean canUseShield() {
		return com.dinglebob.drifter.hands.Shields.isShield(this.getOffhandItem()) && this.level().getGameTime() >= this.shieldDisabledUntil;
	}

	/** Player#hurtCurrentlyUsedShield: a blocked hit wears the shield down (and can break it). */
	@Override
	public void hurtCurrentlyUsedShield(float damage) {
		if (com.dinglebob.drifter.hands.Shields.isShield(this.useItem) && damage >= 3.0F) {
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

	/**
	 * Its ears used to be registered with the game's vibration system, like the Allay's and the Warden's; now it listens
	 * through SoundHub, where each sound is put down once for all Drifters (milestone 16: the game handing every step of a
	 * crowd to every listener near it was a tenth of a battle's time).
	 */
	@Override
	public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> registrar) {
	}

	/** Holds keys for a number of ticks, then releases them all. Ticks of -1 hold until changed. */
	/** Debug: with thoughts going to the log, traces the next 10 s after something went wrong (stuck, out of air). */
	public void autoTrace() {
		long now = this.level().getGameTime();
		if (com.dinglebob.drifter.memory.ThoughtLog.echo && this.traceTicks <= 0 && now - this.lastAutoTrace >= AUTO_TRACE_GAP && autoTraceAllowed(now)) {
			this.lastAutoTrace = now;
			// The run-up too: the black box has the last few seconds.
			com.dinglebob.drifter.Drifter.LOGGER.info("[auto trace] the last {} ticks:", Math.min(60, this.blackBox.size()));
			String name = this.getName().getString();
			this.blackBox.stream().skip(Math.max(0, this.blackBox.size() - 60)).forEach(frame -> com.dinglebob.drifter.Drifter.LOGGER.info(frame.format(name)));
			this.traceTicks = 200;
		}
	}

	private long lastAutoTrace = Long.MIN_VALUE / 2;
	/** At most one automatic trace this often (2 minutes), so a loop doesn't fill the log. */
	private static final long AUTO_TRACE_GAP = 20 * 120;

	/** Logs a line per tick for this many ticks (debug: /drifter trace). */
	public void trace(int ticks) {
		this.traceTicks = ticks;
	}

	/** Debug: trace for a while unless it already is (used when its thoughts go to the log and it gets stuck). */
	public void traceIfIdle(int ticks) {
		if (this.traceTicks <= 0 && autoTraceAllowed(this.level().getGameTime())) {
			this.traceTicks = ticks;
		}
	}

	/** The next game time any Drifter may start tracing itself (server-wide). */
	private static long nextAutoTrace = Long.MIN_VALUE;
	/** Least time between two Drifters starting a trace of their own (5 s): with a thousand Drifters and thoughts in the
	 * log, self-started traces wrote 100,000 lines in two minutes, a line a tick each, up to 15 ms of every tick
	 * (milestone 16). `/drifter trace` isn't held back. */
	private static final long AUTO_TRACE_SPACING = 20 * 5;

	private static boolean autoTraceAllowed(long now) {
		if (now < nextAutoTrace && now > nextAutoTrace - AUTO_TRACE_SPACING * 2) {
			return false;
		}

		nextAutoTrace = now + AUTO_TRACE_SPACING;
		return true;
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

	// ----- Real player skins (RealSkins; user, 2026-10-20) -----

	/** Not looked up yet (or due another try), has one, or there's none for it. */
	private static final int REAL_SKIN_WANTED = 0;
	private static final int REAL_SKIN_WORN = 1;
	private static final int REAL_SKIN_NONE = 2;
	private int realSkinState = REAL_SKIN_WANTED;
	private long realSkinRetryAt;
	/** The account whose skin it wears. */
	private String realSkinName = "";

	/** The real skin it wears (Mojang's textures text), or empty. */
	public String getRealSkin() {
		return this.entityData.get(DATA_REAL_SKIN);
	}

	public String realSkinName() {
		return this.realSkinName;
	}

	/** A real player's skin on it; its default skin (shown until the picture has downloaded) matches the arms. */
	public void setRealSkin(RealSkins.Found found) {
		this.realSkinName = found.name();
		this.realSkinState = REAL_SKIN_WORN;
		this.entityData.set(DATA_REAL_SKIN, found.textures());
		if (this.hasSlimArms() != found.slim()) {
			this.setSkin(SkinPool.pick(this.random, found.slim()));
		}
	}

	/** No account among its names: its default skin for good (a command can still give it one). */
	public void noRealSkin() {
		this.realSkinState = REAL_SKIN_NONE;
	}

	/** No answer (no internet): it tries again then. */
	public void realSkinRetryAt(long tick) {
		this.realSkinRetryAt = tick;
	}

	/** Back to its default skin. */
	public void clearRealSkin() {
		this.realSkinName = "";
		this.realSkinState = REAL_SKIN_NONE;
		this.entityData.set(DATA_REAL_SKIN, "");
	}

	/** Now and then: still without a real skin and due a try, it asks for one. */
	private void wantRealSkin() {
		if (this.realSkinState == REAL_SKIN_WANTED && this.level().getGameTime() >= this.realSkinRetryAt) {
			RealSkins.want(this);
		}
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

	// Asleep in bed it doesn't move, like a player: its keys are let go (applyMovementKeys). Not through isImmobile, as
	// Player does: for a mob that also stops the AI step, and its brain never woke it up (found in testing: it slept for
	// three days).

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

		// LocalPlayer#aiStep: a player overlapping blocks is nudged toward the nearest open side (found in testing: spawned on
		// a block corner inside a tree, it dug at one block at a time and suffocated).
		if (!this.level().isClientSide && !this.noPhysics && this.overlapsSuffocating()) {
			double reach = this.getBbWidth() * 0.35;
			this.moveTowardsClosestSpace(this.getX() - reach, this.getZ() + reach);
			this.moveTowardsClosestSpace(this.getX() - reach, this.getZ() - reach);
			this.moveTowardsClosestSpace(this.getX() + reach, this.getZ() - reach);
			this.moveTowardsClosestSpace(this.getX() + reach, this.getZ() + reach);
		}

		super.aiStep();

		if (!this.level().isClientSide && this.isAlive()) {
			this.pickUpNearbyItems();
		}
	}

	/**
	 * Any block its body overlaps could suffocate it (a quick look at the few blocks round it first: almost always it
	 * stands in the open, and the full collision checks were a measurable cost every tick; performance, 2026-10-17).
	 */
	private boolean overlapsSuffocating() {
		AABB box = this.getBoundingBox();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = net.minecraft.util.Mth.floor(box.minX); x <= net.minecraft.util.Mth.floor(box.maxX); x++) {
			for (int y = net.minecraft.util.Mth.floor(box.minY); y <= net.minecraft.util.Mth.floor(box.maxY); y++) {
				for (int z = net.minecraft.util.Mth.floor(box.minZ); z <= net.minecraft.util.Mth.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					var state = this.level().getBlockState(pos);
					if (!state.isAir() && state.isSuffocating(this.level(), pos)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** LocalPlayer#moveTowardsClosestSpace. */
	private void moveTowardsClosestSpace(double x, double z) {
		BlockPos pos = BlockPos.containing(x, this.getY(), z);
		if (!this.suffocatesAt(pos)) {
			return;
		}

		double inX = x - pos.getX();
		double inZ = z - pos.getZ();
		net.minecraft.core.Direction best = null;
		double bestDistance = Double.MAX_VALUE;
		for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST,
			net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH}) {
			double along = side.getAxis().choose(inX, 0.0, inZ);
			double distance = side.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 1.0 - along : along;
			if (distance < bestDistance && !this.suffocatesAt(pos.relative(side))) {
				bestDistance = distance;
				best = side;
			}
		}

		if (best != null) {
			Vec3 motion = this.getDeltaMovement();
			if (best.getAxis() == net.minecraft.core.Direction.Axis.X) {
				this.setDeltaMovement(0.1 * best.getStepX(), motion.y, motion.z);
			} else {
				this.setDeltaMovement(motion.x, motion.y, 0.1 * best.getStepZ());
			}
		}
	}

	/** LocalPlayer#suffocatesAt. */
	private boolean suffocatesAt(BlockPos pos) {
		AABB box = this.getBoundingBox();
		AABB column = new AABB(pos.getX(), box.minY, pos.getZ(), pos.getX() + 1.0, box.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
		return this.level().collidesWithSuffocatingBlock(this, column);
	}

	/** Runs inside Mob#serverAiStep, before the (idle) vanilla controls. This is where the motor drives the body. */
	@Override
protected void customServerAiStep() {
		this.dismountIfIdle();
		if ((this.tickCount + this.getId()) % 100 == 0) {
			this.wantRealSkin();
		}

		this.lastAiTick = this.level().getGameTime();
		long started = System.nanoTime();
		super.customServerAiStep();
		this.delverDespawn();
		if (this.isRemoved()) {
			return;
		}

		// Its home recorded as built, once (huts from before that was recorded: so it uses the door, not the wall).
		if ((this.tickCount + this.getId()) % 200 == 0) {
			com.dinglebob.drifter.brain.Home home = this.brain.home();
			if (home != null && !home.borrowed() && !home.builtRecorded && (home.hutBuilt || home.planDone) && this.level().isLoaded(home.center)) {
				home.builtRecorded = true;
				if (home.hutBuilt) {
					com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(this.level(), com.dinglebob.drifter.building.StarterHut.of(home).box());
				}

				var plan = home.planDone && home.planId != null ? com.dinglebob.drifter.building.plan.HousePlans.get(this.getServer(), home.planId) : null;
				if (plan != null && home.planOrigin != null) {
					com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(this.level(),
						com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home).box());
				}
			}
		}

		// Perception first (it writes memory), then the executor acts, then the motor moves the body.
		// Back after its area was unloaded a while: it catches up on that time first (section 22; config: offline progression).
		if (this.lastActive >= 0L || this.pendingCatchUp > 0L) {
			long gap = (this.lastActive >= 0L ? this.level().getGameTime() - this.lastActive : 0L) + this.pendingCatchUp;
			this.pendingCatchUp = 0L;
			long offline = this.lastActive >= 0L ? com.dinglebob.drifter.world.OfflineClock.offlineTicksFor(this.lastActive) : 0L;
			this.lastActive = -1L;
			if (com.dinglebob.drifter.DrifterConfig.offlineProgression() != com.dinglebob.drifter.DrifterConfig.OfflineProgression.OFF
				&& gap + offline >= com.dinglebob.drifter.world.CatchUp.minGapTicks()) {
				this.catchUpJob = com.dinglebob.drifter.world.CatchUp.start(this, gap, offline);
			}
		}

		// Catching up: a few slices a tick, in a share of the tick all catch-ups split (milestone 16: one used to run whole in
		// one tick, up to a quarter of a second; a burst of them loading at once was a lag spike: user, 2026-10-17, "10 to 60
		// drifters ... making everything slow down"). It waits standing still till it's done, a second or two.
		if (this.catchUpJob != null) {
			long left = com.dinglebob.drifter.world.CatchUp.budgetLeft(this.level().getGameTime());
			if (left <= 0L && this.catchUpWaited++ < 40) {
				return;
			}

			this.catchUpWaited = 0;
			if (!com.dinglebob.drifter.world.CatchUp.step(this.catchUpJob, System.nanoTime() + left)) {
				return;
			}

			var job = this.catchUpJob;
			this.catchUpJob = null;
			com.dinglebob.drifter.Drifter.LOGGER.info("{} catches up ({} ms over {} ticks, at most {} ms in one): {}", this.getName().getString(),
				String.format(java.util.Locale.ROOT, "%.1f", job.nanos / 1.0E6), job.steps, String.format(java.util.Locale.ROOT, "%.1f", job.longest / 1.0E6),
				job.report.describe());
		}

		if ((this.tickCount + this.getId()) % 20 == 0) {
			this.tier = this.forcedTier == null && this.engaged() ? DetailTier.FULL : DetailTier.of(this, this.forcedTier);
		}

		// Far from every player (and not in a fight): it looks about and thinks every other tick, as DESIGN.md section 22's
		// Far tier has it; its reflexes, what it's doing and its body still run every tick, so nothing it does is slower
		// (milestone 16: a thousand Drifters; user, 2026-10-19, after the recommendation).
		boolean thinks = this.tier != DetailTier.FAR || !com.dinglebob.drifter.DrifterConfig.farHalfRate() || this.inCombat()
			|| ((this.tickCount + this.getId()) & 1) == 0;
		long t = System.nanoTime();
		if (thinks) {
			this.senses.tick();
		}

		long bondsStarted = System.nanoTime();
		this.bonds.tick();
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.BONDS, System.nanoTime() - bondsStarted);
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.SENSES, System.nanoTime() - t, this::perfLabel);
		this.personality.tick(this);
		t = System.nanoTime();
		// (Offset by its id: Drifters that turn up together, a camp or a village loading, don't all do this on the same
		// tick; performance, 2026-10-17.)
		if ((this.tickCount + this.getId()) % 100 == 0) {
			this.social.tick();
		}

		if ((this.tickCount + this.getId()) % com.dinglebob.drifter.social.SocialWatcher.TICK_EVERY == 0) {
			this.social.watcher().tick();
		}

		// Who's on its farm (for its guess about who tramples it; milestone 20): it notices, whatever it's doing.
		if ((this.tickCount + this.getId()) % 40 == 0) {
			com.dinglebob.drifter.brain.Hunches.noteFarmVisitors(this);
		}
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.SOCIAL, System.nanoTime() - t);
		// Skill moves the head (section 16.1: lower Skill overshoots more); mood can shake it (Panicked).
		this.camera.setSkill(this.personality.getF(com.dinglebob.drifter.personality.Trait.SKILL));
		// Reflexes first (section 8, layer 5): while one has the body, the brain, the executor and the tics wait.
		if (!this.reflexes.tick()) {
			t = System.nanoTime();
			if (thinks) {
				this.brain.tick();
			}

			com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.BRAIN, System.nanoTime() - t, this::perfLabel);
			t = System.nanoTime();
			this.tasks.tick();
			com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.TASKS, System.nanoTime() - t, this::perfLabel);
			t = System.nanoTime();
			// Nobody near enough to see a fidget: no fidgets, only the glances that are its senses at work (section 22 detail
			// tiers).
			this.mannerisms.tick(!this.tier.mannerisms() && !this.mannerisms.forcedActive());
			com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.TICS, System.nanoTime() - t);
		} else {
			this.mannerisms.interrupt("reflex");
		}
		float[] look = this.camera.tick(this.getEyePosition(), this.getYRot(), this.getXRot());
		this.setYRot(look[0]);
		this.setXRot(look[1]);
		this.setYHeadRot(look[0]);
		if (this.steerGoal != null && this.steerTick == this.tickCount) {
			com.dinglebob.drifter.motor.Steering.reaim(this, this.steerGoal);
		}

		// TODO(milestone 8): deliberate sprint-swimming on long swims (section 10.10).
		com.dinglebob.drifter.motor.Steering.noSprintInWater(this);
		this.applyMovementKeys();
		t = System.nanoTime();
		// (Every tick near a player; every fourth with nobody near, still plenty to see how it died.)
		if (this.traceTicks <= 0 && com.dinglebob.drifter.memory.ThoughtLog.echo && (this.tier == DetailTier.FULL || this.tickCount % 4 == 0)) {
			// Debug black box: with thoughts going to the log, the last few seconds are kept (raw: written out only if it
			// dies, and the step's description only when the step changes; formatting a line every tick for every Drifter was
			// a twentieth of a battle's time; milestone 16).
			com.dinglebob.drifter.executor.Task leaf = this.tasks.current() == null ? null : this.tasks.current().leaf();
			if (leaf != this.blackBoxLeaf || this.tickCount - this.blackBoxStepAt >= 20) {
				this.blackBoxLeaf = leaf;
				this.blackBoxStepAt = this.tickCount;
				this.blackBoxStep = leaf instanceof com.dinglebob.drifter.executor.task.NavigateTask nav ? nav.debugStep() : leaf == null ? "none" : leaf.describe();
			}

			int flags = (this.onGround() ? 1 : 0) | (this.isInWater() ? 2 : 0) | (this.isUnderWater() ? 4 : 0) | (this.isSwimming() ? 8 : 0)
				| (this.isSprinting() ? 16 : 0) | (this.isBlocking() ? 32 : 0);
			this.blackBox.addLast(new TraceFrame(this.tickCount, this.getX(), this.getY(), this.getZ(), this.getDeltaMovement().y, flags, this.getPose(),
				Mth.wrapDegrees(this.getYRot()), this.getXRot(), this.keys.bits(), this.blackBoxStep, this.getMainHandItem().getItem(),
				this.isUsingItem() ? (this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND ? -1 : 1) * (1 + this.getTicksUsingItem()) : 0,
				this.reflexes.describe()));
			if (this.blackBox.size() > BLACK_BOX_TICKS) {
				this.blackBox.removeFirst();
			}
		} else if (this.traceTicks > 0) {
			com.dinglebob.drifter.executor.Task leaf = this.tasks.current() == null ? null : this.tasks.current().leaf();
			String line = String.format(java.util.Locale.ROOT,
				"[trace %s] t%d pos %.2f %.2f %.2f vy %.3f %s%s%s%s%s pose %s yaw %.0f pitch %.0f keys %s step %s", this.getName().getString(), this.tickCount,
				this.getX(), this.getY(), this.getZ(), this.getDeltaMovement().y, this.onGround() ? "G" : "-", this.isInWater() ? "W" : "-",
				this.isUnderWater() ? "U" : "-", this.isSwimming() ? "S" : "-", this.isSprinting() ? "R" : "-", this.getPose(), Mth.wrapDegrees(this.getYRot()), this.getXRot(),
				this.keys.describe(), leaf instanceof com.dinglebob.drifter.executor.task.NavigateTask nav ? nav.debugStep()
					: leaf == null ? "none" : leaf.describe())
				// What's in hand and in use (a bow drawn, the shield up) and the reflex in charge, for fights.
				+ String.format(java.util.Locale.ROOT, " hand %s%s%s", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.getMainHandItem().getItem()).getPath(),
					this.isUsingItem() ? " using " + (this.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND ? "off" : "main") + " " + this.getTicksUsingItem() : "",
					this.isBlocking() ? " BLOCKING" : "")
				+ (this.reflexes.describe() == null || this.reflexes.describe().isEmpty() ? "" : " reflex " + this.reflexes.describe());
			this.traceTicks--;
			com.dinglebob.drifter.Drifter.LOGGER.info(line);
		}

		if (this.keysReleaseTimer > 0 && --this.keysReleaseTimer == 0) {
			this.keys.releaseAll();
			this.keysReleaseTimer = -1;
		}
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.TRACE, System.nanoTime() - t);
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.TOTAL, System.nanoTime() - started, this::perfLabel);
		if (this.travelTo != null && this.isAlive()) {
			BlockPos to = this.travelTo;
			this.travelTo = null;
			com.dinglebob.drifter.world.DrifterTravel.depart(this, to);
		}
	}

	/** Who and what, for the slowest-call lines of `/drifter perf`. */
	private String perfLabel() {
		var activity = this.brain.activity();
		return this.getName().getString() + ": " + (activity == null ? "no activity" : activity.describe());
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
		if (this.isSleeping()) {
			this.keys.releaseAll();
		}

		// A safety net under every task: on the ground at the edge of a deep drop, still drifting with no movement key
		// held (a task let go of everything), it crouches so the drift can't carry it over, as a player does at a cliff
		// (found in testing: a walk ended crouched at a 40-block edge, the next task let go of sneak, and it slipped off).
		if (this.onGround() && !this.isInWater() && !this.onClimbable() && !this.keys.isDown(VirtualKeys.Key.SNEAK) && this.keys.forwardImpulse() == 0.0F && this.keys.leftImpulse() == 0.0F
			&& (this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6 || this.overhanging()) && this.deepDropBeside()) {
			this.keys.set(VirtualKeys.Key.SNEAK, true);
		}

		// On a magma block it crouches, as a player does: magma only burns you when you don't (found in a Nether test: it
		// stood on magma replanning, a heart at a time, until it died; and in a 10-day run: it worked on underwater magma,
		// walking about, a heart every 10 seconds until it died). Moving or not, in water or not: crouch-walking is slower
		// but safe.
		if (this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
			|| this.level().getBlockState(this.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
			this.keys.set(VirtualKeys.Key.SNEAK, true);
		}

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
		// Standing room is room for every lower pose too (each box is the standing one's width from the same feet, no
		// taller): one check a tick where it stands (milestone 16: these checks, four or five a tick, were a twentieth of a
		// battle's time).
		Vec3 here = this.position();
		if (!here.equals(this.fitCheckedAt) || this.tickCount != this.fitCheckedTick) {
			this.fitCheckedAt = here;
			this.fitCheckedTick = this.tickCount;
			// (Blocks only: whether a boat or a shulker box would let it stand up isn't worth an entity search a tick.)
			this.standingFits = this.level().noBlockCollision(this, this.getDimensions(Pose.STANDING).makeBoundingBox(here).deflate(1.0E-7));
		}

		if (this.standingFits && this.getDimensions(pose).height() <= this.getDimensions(Pose.STANDING).height()
			&& this.getDimensions(pose).width() <= this.getDimensions(Pose.STANDING).width()) {
			return true;
		}

		return this.level().noBlockCollision(this, this.getDimensions(pose).makeBoundingBox(here).deflate(1.0E-7));
	}

	/** Its fake-player helper's id and profile, worked out once (FakePlayerBridge). */
	@org.jetbrains.annotations.Nullable
	public java.util.UUID helperId;
	@org.jetbrains.annotations.Nullable
	public java.util.UUID helperIdFor;
	@org.jetbrains.annotations.Nullable
	public com.mojang.authlib.GameProfile helperProfile;
	/** Its fake-player helper itself, kept while it lives (not saved). */
	@org.jetbrains.annotations.Nullable
	public net.fabricmc.fabric.api.entity.FakePlayer helperPlayer;

	/** When its AI last ran (for `/drifter perf`: how many of the loaded are really running). */
	public long lastAiTick = Long.MIN_VALUE;

	/** A catch-up under way (CatchUp.Job), done a few slices a tick. */
	@org.jetbrains.annotations.Nullable
	private com.dinglebob.drifter.world.CatchUp.Job catchUpJob;
	private int catchUpWaited;

	@org.jetbrains.annotations.Nullable
	private Vec3 fitCheckedAt;
	private boolean standingFits;
	private int fitCheckedTick = -1;

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

	/**
	 * A modded bow with its own way of letting go (Simply Bows' unique bows) is let go by its helper player, as a player
	 * would: the arrows come out of its own pack (the mod gives any shooter that isn't a player endless arrows), and the
	 * bow's ability works as it does for a player. Its arrows are then its own (whom they hit, it hit). The vanilla bow
	 * goes its own way (BowItemMixin).
	 */
	@Override
	public void releaseUsingItem() {
		ItemStack bow = this.useItem;
		if (!this.level().isClientSide && com.dinglebob.drifter.hands.Shields.isBow(bow) && ownRelease(bow.getItem().getClass())) {
			int remaining = this.getUseItemRemainingTicks();
			com.dinglebob.drifter.hands.FakePlayerBridge.act(this, fake -> {
				bow.releaseUsing(fake.level(), fake, remaining);
				return null;
			});
			if (this.helperPlayer != null) {
				for (net.minecraft.world.entity.projectile.Projectile shot : this.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
					this.getBoundingBox().inflate(4.0), shot -> shot.getOwner() == this.helperPlayer)) {
					shot.setOwner(this);
				}
			}

			this.stopUsingItem();
			return;
		}

		super.releaseUsingItem();
	}

	/** This bow class (or one between it and the vanilla bow) has its own releaseUsing. */
	private static boolean ownRelease(Class<?> type) {
		for (Class<?> c = type; c != null && c != net.minecraft.world.item.BowItem.class; c = c.getSuperclass()) {
			for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
				Class<?>[] params = method.getParameterTypes();
				if (method.getReturnType() == void.class && params.length == 4 && params[0] == ItemStack.class && params[1] == Level.class
					&& params[2] == net.minecraft.world.entity.LivingEntity.class && params[3] == int.class) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * A potion drunk is used up and leaves its bottle, as for a player (PotionItem#finishUsingItem only uses up a player's:
	 * a mob would drink the same one for ever).
	 */
	@Override
	protected void completeUsingItem() {
		ItemStack drinking = this.useItem;
		boolean potion = !this.level().isClientSide && drinking.is(net.minecraft.world.item.Items.POTION) && this.isUsingItem();
		net.minecraft.world.InteractionHand hand = this.getUsedItemHand();
		int before = drinking.getCount();
		super.completeUsingItem();
		if (potion && drinking.getCount() == before && this.getItemInHand(hand) == drinking) {
			drinking.shrink(1);
			ItemStack bottle = new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE);
			if (drinking.isEmpty()) {
				this.setItemInHand(hand, bottle);
			} else if (!this.inventory.add(bottle)) {
				this.spawnAtLocation(bottle);
			}
		}
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

	/** Mob's version reads its own (empty) armor list: piglins checking for gold (PiglinAi#isWearingGold), thorns and the
	 * like must see the armor it really wears (found in testing: in golden boots, piglins still came for it). */
	@Override
	public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
		return this.getArmorSlots();
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

		// One look for both (as Player#aiStep does): items first, then orbs.
		List<net.minecraft.world.entity.Entity> near = this.level().getEntities(this, reach,
			entity -> entity instanceof ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb);
		for (net.minecraft.world.entity.Entity entity : near) {
			if (entity instanceof ItemEntity item) {
				this.tryPickUp(item);
			}
		}

		if (this.takeXpDelay > 0) {
			this.takeXpDelay--;
		}

		for (net.minecraft.world.entity.Entity entity : near) {
			if (entity instanceof net.minecraft.world.entity.ExperienceOrb orb && !orb.isRemoved()) {
				this.touchOrb(orb);
			}
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
		ItemStack before = stack.copy();
		if (this.inventory.add(stack)) {
			// Thrown to it by a player it can see: a gift (section 14).
			if (item.getOwner() instanceof net.minecraft.world.entity.player.Player thrower) {
				ItemStack taken = before.copyWithCount(count - stack.getCount());
				if (!taken.isEmpty()) {
					this.social.watcher().pickedUpFrom(thrower, taken);
				}
			}

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
			// A player's hit (or their arrow): felt, so it always knows who (section 14 events).
			if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
				this.social.watcher().hitBy(player, healthBefore - this.getHealth(), this.getHealth());
			}

			// Hit by a mob while backing off: running isn't working (Threats turns it round to fight).
			com.dinglebob.drifter.brain.Threats.Decision decision = this.brain.lastThreatDecision;
			if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity && (decision == com.dinglebob.drifter.brain.Threats.Decision.FLEE
				|| decision == com.dinglebob.drifter.brain.Threats.Decision.RETREAT || decision == com.dinglebob.drifter.brain.Threats.Decision.AVOID)) {
				long now = this.level().getGameTime();
				this.brain.hitsWhileBackingOff = now - this.brain.lastHitWhileBackingOff > 100 ? 1 : this.brain.hitsWhileBackingOff + 1;
				this.brain.lastHitWhileBackingOff = now;
			}
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
		if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
			this.social.watcher().killedBy(player);
		}

		// Killed by another group's member: a loss in their feud (section 21 team wars).
		DrifterEntity killer = com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
		if (killer != null && killer != this && !this.level().isClientSide()) {
			com.dinglebob.drifter.world.DrifterWars.killed(killer, this);
		}

		// Gone for good: out of its group (found in testing: the dead stayed members forever); the others hear of it.
		if (this.level() instanceof ServerLevel server) {
			var group = com.dinglebob.drifter.world.DrifterGroups.of(this.getUUID());
			if (group != null) {
				String name = this.getName().getString();
				com.dinglebob.drifter.world.DrifterGroups.leave(this.getUUID());
				for (java.util.UUID id : group.members) {
					if (server.getEntity(id) instanceof DrifterEntity mate) {
						mate.getMemory().thoughts().add(server.getGameTime(), name + " died");
					}
				}
			}
		}

		// (Not for a death in a fight with a Drifter or a player: that's no mystery, and a battle has them by the hundred.)
		boolean killed = source.getEntity() instanceof DrifterEntity || source.getEntity() instanceof net.minecraft.world.entity.player.Player;
		long nowMillis = System.currentTimeMillis();
		if (!this.blackBox.isEmpty() && !killed && nowMillis - lastBlackBoxDump > 3000L) {
			lastBlackBoxDump = nowMillis;
			com.dinglebob.drifter.Drifter.LOGGER.info("[black box] last {} ticks before death:", this.blackBox.size());
			String name = this.getName().getString();
			this.blackBox.forEach(frame -> com.dinglebob.drifter.Drifter.LOGGER.info(frame.format(name)));
		}

		this.blackBox.clear();

		// A ragdoll where it falls, with Sable Player Ragdoll (and a corpse with its things, with Ragdoll Corpse).
		if (!this.isRemoved() && !this.dead && this.ragdoll == null) {
			this.ragdoll = com.dinglebob.drifter.compat.RagdollCompat.onDeath(this, source);
		}

		super.die(source);
	}

	/** Its ragdoll, once it has died and become one (Sable Player Ragdoll). */
	@org.jetbrains.annotations.Nullable
	private com.dinglebob.drifter.compat.RagdollCompat.Ragdoll ragdoll;

	@Override
	protected void dropEquipment() {
		super.dropEquipment();
		java.util.List<ItemStack> things = new java.util.ArrayList<>();
		for (ItemStack stack : this.inventory.removeAll()) {
			if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				things.add(stack);
			}
		}

		if (this.ragdoll != null) {
			things = com.dinglebob.drifter.compat.RagdollCompat.fillCorpse(this, this.ragdoll, things);
		}

		things.forEach(this::dropScattered);
	}

	/** Become a ragdoll: the body goes at once (the ragdoll is what falls). */
	@Override
	protected void tickDeath() {
		if (this.ragdoll != null && !this.level().isClientSide()) {
			this.remove(RemovalReason.KILLED);
			return;
		}

		super.tickDeath();
	}

	/** It means to get into something (a whim's seat): rides are only ever its choice. */
	private boolean meansToRide;

	public void meansToRide(boolean yes) {
		this.meansToRide = yes;
	}

	/**
	 * Boats and minecarts pick up any mob that bumps into them, but never a player (found in testing: it walked into a
	 * parked boat and was stuck in it): only rides it chose, or forced ones (commands, portals).
	 */
	@Override
	public boolean startRiding(net.minecraft.world.entity.Entity vehicle, boolean force) {
		if (!force && !this.meansToRide) {
			return false;
		}

		boolean on = super.startRiding(vehicle, force);
		if (on) {
			this.keepRiding();
		}

		return on;
	}

	/** Player#drop(stack, false, true), pressing Q: thrown forward where it looks, with the pickup delay. */
	public ItemEntity tossForward(ItemStack stack) {
		return this.tossForward(stack, true);
	}

	/**
	 * As {@link #tossForward(ItemStack)}; {@code thrownAway} false for a fumble (not marked as thrown by it, so it picks it
	 * back up: what it throws away on purpose it never does).
	 */
	public ItemEntity tossForward(ItemStack stack, boolean thrownAway) {
		ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getEyeY() - 0.3F, this.getZ(), stack);
		item.setPickUpDelay(40);
		if (thrownAway) {
			item.setThrower(this);
		}

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
		return item;
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
		// On a client nothing of its mind is there to save, and texture mods (ETF) read every entity's data every frame to
		// test their rules: just what a client knows (milestone 16: a battle's soldiers were saved in full every frame).
		if (this.level().isClientSide()) {
			tag.putString("Skin", this.getSkinTexture().toString());
			tag.putBoolean("SlimArms", this.hasSlimArms());
			return;
		}

		tag.putInt("DrifterSaveVersion", SAVE_VERSION);
		tag.put("DrifterInventory", this.inventory.save(this.registryAccess()));
		tag.putInt("SelectedItemSlot", this.inventory.getSelected());
		this.foodData.addAdditionalSaveData(tag);
		tag.putString("Skin", this.getSkinTexture().toString());
		tag.putBoolean("SlimArms", this.hasSlimArms());
		tag.putInt("RealSkinState", this.realSkinState);
		if (!this.getRealSkin().isEmpty()) {
			CompoundTag real = new CompoundTag();
			real.putString("Name", this.realSkinName);
			real.putString("Textures", this.getRealSkin());
			tag.put("RealSkin", real);
		}

		tag.put("DrifterMemory", this.memory.save());
		tag.put("DrifterBrain", this.brain.save());
		tag.put("DrifterPersonality", this.personality.save());
		tag.put("DrifterSocial", this.social.save());
		tag.put("DrifterBonds", this.bonds.save());
		tag.putBoolean("DrifterNamed", this.named);
		tag.putFloat("XpP", this.experienceProgress);
		tag.putInt("XpLevel", this.experienceLevel);
		tag.putInt("XpTotal", this.totalExperience);
		tag.putInt("XpSeed", this.enchantmentSeed);
		tag.putLongArray("DugCells", this.dugCells.stream().mapToLong(Long::longValue).toArray());
		tag.putLongArray("Scaffold", this.scaffold.stream().mapToLong(BlockPos::asLong).toArray());
		// When it was last running, for catch-up when it loads again (section 22).
		tag.putLong("LastActive", this.level().getGameTime());
		tag.putLong("PendingCatchUp", this.pendingCatchUp);
		if (this.delveFrom != null && this.delveUntil > 0L) {
			CompoundTag delve = new CompoundTag();
			delve.putLong("From", this.delveFrom.asLong());
			delve.putLong("Until", this.delveUntil);
			tag.put("Delve", delve);
		}

		if (this.passingThrough) {
			CompoundTag pass = new CompoundTag();
			pass.putFloat("Heading", this.passHeading);
			pass.putLong("Since", this.passingSince);
			pass.putBoolean("Met", this.metOnItsWay);
			tag.put("PassingThrough", pass);
		}
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

		this.dugCells.clear();
		for (long cell : tag.getLongArray("DugCells")) {
			this.dugCells.add(cell);
		}

		this.scaffold.clear();
		for (long cell : tag.getLongArray("Scaffold")) {
			this.scaffold.add(BlockPos.of(cell));
		}

		this.lastActive = tag.contains("LastActive", Tag.TAG_LONG) ? tag.getLong("LastActive") : -1L;
		this.pendingCatchUp = tag.getLong("PendingCatchUp");
		if (tag.contains("Delve", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag delve = tag.getCompound("Delve");
			this.delveFrom = BlockPos.of(delve.getLong("From"));
			this.delveUntil = delve.getLong("Until");
		}

		if (tag.contains("PassingThrough", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag pass = tag.getCompound("PassingThrough");
			this.passingThrough = true;
			this.passHeading = pass.getFloat("Heading");
			this.passingSince = pass.getLong("Since");
			this.metOnItsWay = pass.getBoolean("Met");
		}

		if (tag.contains("DrifterBrain", Tag.TAG_COMPOUND)) {
			this.brain.load(tag.getCompound("DrifterBrain"));
		}

		// Drifters saved before milestone 7 have no personality yet: they keep the one rolled at load, which is then saved.
		this.named = tag.getBoolean("DrifterNamed");
		if (tag.contains("DrifterSocial", Tag.TAG_COMPOUND)) {
			this.social.load(tag.getCompound("DrifterSocial"));
			this.bonds.load(tag.getCompound("DrifterBonds"));
		}

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

		// (A Drifter saved before real skins: not looked up yet.)
		this.realSkinState = tag.contains("RealSkinState") ? tag.getInt("RealSkinState") : REAL_SKIN_WANTED;
		if (tag.contains("RealSkin", Tag.TAG_COMPOUND)) {
			CompoundTag real = tag.getCompound("RealSkin");
			this.realSkinName = real.getString("Name");
			this.entityData.set(DATA_REAL_SKIN, real.getString("Textures"));
			// (That account is taken: the user's list gives each of its skins to one Drifter at a time.)
			if (!this.level().isClientSide) {
				RealSkins.worn(real.getString("Textures"));
			}
		} else if (this.realSkinState == REAL_SKIN_WORN) {
			this.realSkinState = REAL_SKIN_WANTED;
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
