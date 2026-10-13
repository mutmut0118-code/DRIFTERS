package com.dinglebob.drifter.perception;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.DrifterMemory;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.memory.MemorySource;
import com.dinglebob.drifter.memory.MentalMap;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Drifter perceives (DESIGN.md section 5). Perception writes into memory; decisions read memory,
 * never the world. This class is the only place (besides debug commands and scripts) that looks at the world on
 * the Drifter's behalf, and it only looks the way a player at its position, looking where it looks, could.
 *
 * <p>All tunable numbers are constants here, to move to a datapack file later (section 25).
 */
public class Senses {
	// ----- Vision (section 5) -----
	/** View cone: about 110 degrees horizontal and 70 vertical, centered on the head. */
	public static final float CONE_HALF_YAW = 55.0F;
	public static final float CONE_HALF_PITCH = 35.0F;
	/** Peripheral zone: out to 180 degrees horizontal; motion only. */
	public static final float PERIPHERAL_HALF_YAW = 90.0F;
	public static final float PERIPHERAL_HALF_PITCH = 60.0F;
	/** Entity sight range in full light, and in full darkness. */
	public static final double DAYLIGHT_RANGE = 48.0;
	public static final double DARK_RANGE = 8.0;
	public static final double RAIN_FACTOR = 0.75;
	public static final double THUNDER_FACTOR = 0.6;
	public static final double UNDERWATER_RANGE = 12.0;
	public static final double BLINDNESS_RANGE = 5.0;
	public static final double DARKNESS_EFFECT_RANGE = 10.0;
	/** Awareness gained per tick at the center of view, close, with the target moving and fully visible. */
	public static final float FILL_RATE = 0.06F;
	/** Peripheral motion fills slower and can only produce a glimpse. */
	public static final float PERIPHERAL_FILL_RATE = 0.03F;
	public static final float PERIPHERAL_CAP = 0.35F;
	public static final float DRAIN_RATE = 0.01F;
	public static final float SNEAKING_FACTOR = 0.4F;
	public static final float STILL_FACTOR = 0.6F;
	/** An invisible entity wearing armor can be spotted by the armor, slowly. */
	public static final float INVISIBLE_ARMOR_FACTOR = 0.3F;
	public static final double MOVING_SPEED = 0.02;
	/** Attention budget: entities tracked in detail at once, re-ranked every RANK_INTERVAL ticks. */
	public static final int ATTENTION_BUDGET = 8;
	public static final int RANK_INTERVAL = 10;
	public static final int AWARENESS_INTERVAL = 2;
	public static final int FORCED_TRACK_TICKS = 100;
	/** Items on the ground are noticed within this distance. */
	public static final double ITEM_SIGHT_RANGE = 16.0;

	// ----- Seeing blocks (section 5) -----
	public static final int BLOCK_RAYS_BASE = 6;
	public static final int BLOCK_RAYS_PLAYER_NEAR = 12;
	public static final double BLOCK_SIGHT_RANGE = 32.0;
	public static final double BLOCK_DARK_RANGE = 8.0;
	/** How many remembered blocks in view are re-checked per second (stations are "verified on arrival"). */
	public static final int VERIFY_PER_SECOND = 6;
	public static final double VERIFY_RANGE = 24.0;

	// ----- Hearing and reactions -----
	public static final double HEARING_POSITION_ERROR = 0.15;
	public static final int SOUND_MEMORY_TICKS = 20 * 30;
	public static final int MAX_SOUNDS = 16;
	/** A sound of the same kind this close to a recent one, this soon after, is the same ongoing sound. */
	public static final double SAME_SOUND_DISTANCE = 4.0;
	public static final int SAME_SOUND_TICKS = 40;
	public static final int GLANCE_HOLD_TICKS = 30;
	public static final int GLANCE_COOLDOWN_TICKS = 40;
	/** It always knows the blocks this close around its feet (touch and the edge of its vision). */
	public static final int BODY_KNOWLEDGE_RADIUS = 1;
	/** Kept for the debug overlay: the most recent block rays. */
	public static final int RECENT_RAYS = 24;

	public record Ray(Vec3 from, Vec3 to, boolean hit, boolean notable) {
	}

	private final DrifterEntity drifter;
	private final DrifterMemory memory;
	private final Hearing hearing;
	private final TimeSense time = new TimeSense();
	private final TerrainKnowledge terrain = new TerrainKnowledge();
	private final Map<Integer, Awareness> tracked = new HashMap<>();
	private final Deque<HeardSound> sounds = new ArrayDeque<>();
	private final Deque<Ray> recentRays = new ArrayDeque<>();
	private int ticks;
	private int glanceToken = -1;
	private int glanceTicks;
	private int glanceCooldown;

	public Senses(DrifterEntity drifter, DrifterMemory memory) {
		this.drifter = drifter;
		this.memory = memory;
		this.hearing = new Hearing(drifter, this);
	}

	public Hearing hearing() {
		return this.hearing;
	}

	public TimeSense time() {
		return this.time;
	}

	/** Which blocks it has seen, for the pathfinder (section 12). */
	public TerrainKnowledge terrain() {
		return this.terrain;
	}

	public List<Awareness> tracked() {
		return new ArrayList<>(this.tracked.values());
	}

	@Nullable
	public Awareness awarenessOf(Entity entity) {
		return this.tracked.get(entity.getId());
	}

	public List<HeardSound> recentSounds() {
		return new ArrayList<>(this.sounds);
	}

	public List<Ray> recentRays() {
		return new ArrayList<>(this.recentRays);
	}

	private Level level() {
		return this.drifter.level();
	}

	private long now() {
		return this.level().getGameTime();
	}

	private ResourceLocation dimension() {
		return this.level().dimension().location();
	}

	private void think(String text) {
		this.memory.thoughts().add(this.now(), text);
	}

	// ---------------------------------------------------------------------------------------------
	// Tick
	// ---------------------------------------------------------------------------------------------

	/** Server only, every tick, before the executor runs. */
	public void tick() {
		this.ticks++;
		if (this.time.tick(this.drifter)) {
			this.consolidate("dusk");
		}

		if (this.ticks % RANK_INTERVAL == 0) {
			this.rankAttention();
		}

		if (this.ticks % AWARENESS_INTERVAL == 0) {
			this.updateAwareness();
		}

		this.sampleBlocks();
		if (this.ticks % 5 == 0) {
			this.terrain.markAround(this.drifter.blockPosition(), BODY_KNOWLEDGE_RADIUS, 2, 3);
		}

		if (this.ticks % 20 == 0) {
			this.verifyRememberedBlocks();
			this.memory.decay(this.now());
			long cutoff = this.now() - SOUND_MEMORY_TICKS;
			this.sounds.removeIf(sound -> sound.time() < cutoff);
		}

		this.tickGlance();
	}

	private void consolidate(String when) {
		int promoted = this.memory.consolidate(this.time.believedDay(), this.now());
		if (promoted >= 0) {
			this.think("thinking over the day (" + when + "): kept " + promoted + " important memories, let the rest go");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Vision: entities
	// ---------------------------------------------------------------------------------------------

	private enum Zone {
		NONE, PERIPHERAL, CONE
	}

	private record Sighting(Zone zone, float visibility, double distance, double range, float centrality) {
		static final Sighting NOT_SEEN = new Sighting(Zone.NONE, 0.0F, 0.0, 0.0, 0.0F);

		boolean seen() {
			return this.zone != Zone.NONE && this.visibility > 0.0F;
		}
	}

	/** Where something is relative to the head: {yaw offset, pitch offset} in degrees. */
	private float[] angleTo(Vec3 point) {
		Vec3 eye = this.drifter.getEyePosition();
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
		return new float[]{Mth.wrapDegrees(yaw - this.drifter.getYHeadRot()), pitch - this.drifter.getXRot()};
	}

	private Zone zoneOf(float[] angle) {
		float yaw = Math.abs(angle[0]);
		float pitch = Math.abs(angle[1]);
		if (yaw <= CONE_HALF_YAW && pitch <= CONE_HALF_PITCH) {
			return Zone.CONE;
		}

		return yaw <= PERIPHERAL_HALF_YAW && pitch <= PERIPHERAL_HALF_PITCH ? Zone.PERIPHERAL : Zone.NONE;
	}

	private boolean lineOfSight(Vec3 from, Vec3 to) {
		return this.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.drifter)).getType() == HitResult.Type.MISS;
	}

	/** How far it can see this entity right now: light at the target, weather, water and effects. */
	private double sightRange(Entity target) {
		Level level = this.level();
		int light = level.getRawBrightness(BlockPos.containing(target.getEyePosition()), level.getSkyDarken());
		if (target.isOnFire() || target.isCurrentlyGlowing() || holdsLight(target) || this.drifter.hasEffect(MobEffects.NIGHT_VISION)) {
			light = 15;
		}

		double range = DARK_RANGE + (DAYLIGHT_RANGE - DARK_RANGE) * light / 15.0;
		BlockPos eyePos = BlockPos.containing(this.drifter.getEyePosition());
		if (level.isThundering() && level.isRainingAt(eyePos)) {
			range *= THUNDER_FACTOR;
		} else if (level.isRainingAt(eyePos)) {
			range *= RAIN_FACTOR;
		}

		if (this.drifter.isEyeInFluid(FluidTags.WATER)) {
			range = Math.min(range, UNDERWATER_RANGE);
		}

		if (this.drifter.hasEffect(MobEffects.BLINDNESS)) {
			range = Math.min(range, BLINDNESS_RANGE);
		} else if (this.drifter.hasEffect(MobEffects.DARKNESS)) {
			range = Math.min(range, DARKNESS_EFFECT_RANGE);
		}

		return range;
	}

	private static boolean holdsLight(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return false;
		}

		return emitsLight(living.getMainHandItem()) || emitsLight(living.getOffhandItem());
	}

	private static boolean emitsLight(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().getLightEmission() > 0;
	}

	private Sighting look(Entity target) {
		Vec3 eye = this.drifter.getEyePosition();
		Vec3 head = target.getEyePosition();
		double distance = eye.distanceTo(head);
		double range = this.sightRange(target);
		if (distance > range) {
			return Sighting.NOT_SEEN;
		}

		float[] angle = this.angleTo(target.getBoundingBox().getCenter());
		Zone zone = this.zoneOf(angle);
		if (zone == Zone.NONE) {
			return Sighting.NOT_SEEN;
		}

		// A ray to the head and one to the feet: either counts, both count more.
		boolean headSeen = this.lineOfSight(eye, head);
		boolean feetSeen = this.lineOfSight(eye, target.position().add(0.0, 0.1, 0.0));
		float visibility = headSeen && feetSeen ? 1.0F : headSeen || feetSeen ? 0.6F : 0.0F;
		float centrality = 1.0F - 0.6F * Math.min(1.0F, Math.max(Math.abs(angle[0]) / CONE_HALF_YAW, Math.abs(angle[1]) / CONE_HALF_PITCH));
		return new Sighting(zone, visibility, distance, range, centrality);
	}

	private static boolean isMoving(Entity entity) {
		return entity.getDeltaMovement().horizontalDistance() > MOVING_SPEED || Math.abs(entity.getY() - entity.yo) > MOVING_SPEED;
	}

	/** Invisible: unseen, except for any armor it wears. Sneaking: harder to spot. */
	private static float stealthFactor(Entity entity) {
		if (entity.isInvisible()) {
			if (entity instanceof LivingEntity living) {
				for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					if (!living.getItemBySlot(slot).isEmpty()) {
						return INVISIBLE_ARMOR_FACTOR;
					}
				}
			}

			return 0.0F;
		}

		return entity.isDiscrete() ? SNEAKING_FACTOR : 1.0F;
	}

	/** Attention budget (section 5): re-rank what's worth tracking in detail. Players and forced ones always stay. */
	private void rankAttention() {
		long now = this.now();
		AABB area = this.drifter.getBoundingBox().inflate(DAYLIGHT_RANGE);
		List<Entity> candidates = this.level().getEntities(this.drifter, area,
			entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator());
		List<Map.Entry<Entity, Double>> ranked = new ArrayList<>();
		for (Entity entity : candidates) {
			Sighting sighting = this.look(entity);
			Awareness current = this.tracked.get(entity.getId());
			boolean keep = current != null && (current.forcedTicks > 0 || current.value >= Awareness.Level.GLIMPSE.threshold);
			if (!sighting.seen() && !keep) {
				continue;
			}

			if (sighting.zone() == Zone.PERIPHERAL && !isMoving(entity) && !keep) {
				continue;
			}

			double salience = entity instanceof Player ? 3.0 : entity instanceof Enemy ? 2.0 : 1.0;
			salience *= (1.0 + (current == null ? 0.0 : current.value)) / (1.0 + entity.distanceTo(this.drifter) / 16.0);
			ranked.add(Map.entry(entity, salience));
		}

		ranked.sort(Map.Entry.<Entity, Double>comparingByValue().reversed());
		Map<Integer, Awareness> next = new HashMap<>();
		for (Map.Entry<Entity, Double> candidate : ranked) {
			Entity entity = candidate.getKey();
			Awareness current = this.tracked.get(entity.getId());
			boolean mustTrack = entity instanceof Player || current != null && current.forcedTicks > 0;
			if (next.size() >= ATTENTION_BUDGET && !mustTrack) {
				continue;
			}

			next.put(entity.getId(), current != null ? current : new Awareness(entity, now));
		}

		this.tracked.clear();
		this.tracked.putAll(next);
		this.noticeItems();
	}

	private void updateAwareness() {
		long now = this.now();
		for (Awareness awareness : this.tracked.values()) {
			Entity entity = awareness.entity;
			if (awareness.forcedTicks > 0) {
				awareness.forcedTicks -= AWARENESS_INTERVAL;
			}

			Sighting sighting = entity.isRemoved() ? Sighting.NOT_SEEN : this.look(entity);
			float stealth = stealthFactor(entity);
			awareness.visibleNow = sighting.seen() && stealth > 0.0F;
			if (awareness.visibleNow) {
				float closeness = (float)(0.35 + 0.65 * (1.0 - sighting.distance() / sighting.range()));
				float motion = isMoving(entity) ? 1.0F : STILL_FACTOR;
				if (sighting.zone() == Zone.CONE) {
					float gain = FILL_RATE * sighting.visibility() * closeness * sighting.centrality() * motion * stealth;
					awareness.value = Math.min(1.0F, awareness.value + gain * AWARENESS_INTERVAL);
				} else if (isMoving(entity) && awareness.value < PERIPHERAL_CAP) {
					float gain = PERIPHERAL_FILL_RATE * sighting.visibility() * closeness * stealth;
					awareness.value = Math.min(PERIPHERAL_CAP, awareness.value + gain * AWARENESS_INTERVAL);
				}

				awareness.lastSeenPos = entity.position();
				awareness.lastSeenTick = now;
			} else {
				awareness.value = Math.max(0.0F, awareness.value - DRAIN_RATE * AWARENESS_INTERVAL);
			}

			this.onAwarenessChanged(awareness);
		}
	}

	private void onAwarenessChanged(Awareness awareness) {
		Awareness.Level level = awareness.level();
		Entity entity = awareness.entity;
		if (level == Awareness.Level.DETECTED && awareness.visibleNow) {
			if (!awareness.identified) {
				awareness.identified = true;
				this.think("detected " + describe(entity) + " " + this.direction(entity.position()));
			}

			this.rememberEntity(entity);
		} else if (level.ordinal() > awareness.lastLevel.ordinal() && level != Awareness.Level.DETECTED && !awareness.identified) {
			this.think((level == Awareness.Level.GLIMPSE ? "glimpsed something " : "something suspicious ") + this.direction(entity.position()));
			this.glanceAt(entity.getEyePosition());
		}

		if (level == Awareness.Level.NONE && awareness.lastLevel != Awareness.Level.NONE && awareness.identified) {
			this.think("lost track of " + describe(entity));
			awareness.identified = false;
		}

		awareness.lastLevel = level;
	}

	/** Once identified, a mob or player goes into memory (with what a player could see of them). */
	private void rememberEntity(Entity entity) {
		long now = this.now();
		if (entity instanceof Player player) {
			MemoryEntry entry = this.memory.remember(MemoryKind.PLAYER, player.getGameProfile().getName(), player.position(), this.dimension(), now,
				MemorySource.SEEN, 0.8F, player.getUUID()).entry();
			entry.detail = appearance(player);
		} else {
			float importance = entity instanceof Enemy ? 0.4F : 0.2F;
			MemoryEntry entry = this.memory.remember(MemoryKind.MOB, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
				entity.position(), this.dimension(), now, MemorySource.SEEN, importance, entity.getUUID()).entry();
			entry.detail = entity instanceof LivingEntity living ? appearance(living) : "";
		}
	}

	/**
	 * What a player could see of someone (section 5, "Seeing players"): held item, armor per slot, enchantment glint,
	 * and what they're doing. Never health or inventory.
	 */
	private static String appearance(LivingEntity living) {
		List<String> parts = new ArrayList<>();
		if (living.isBaby()) {
			parts.add("baby");
		}

		ItemStack held = living.getMainHandItem();
		if (!held.isEmpty()) {
			parts.add("holding " + itemName(held));
		}

		List<String> armor = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack piece = living.getItemBySlot(slot);
			if (!piece.isEmpty()) {
				armor.add(itemName(piece));
			}
		}

		if (!armor.isEmpty()) {
			parts.add("wearing " + String.join(", ", armor));
		}

		if (living.isCrouching()) {
			parts.add("sneaking");
		}

		if (living.isSprinting()) {
			parts.add("sprinting");
		}

		if (living.isBlocking()) {
			parts.add("blocking");
		} else if (living.isUsingItem()) {
			parts.add("using " + itemName(living.getUseItem()));
		}

		if (living.hurtTime > 0) {
			parts.add("just got hurt");
		}

		return String.join("; ", parts);
	}

	private static String itemName(ItemStack stack) {
		String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ');
		return stack.hasFoil() ? name + " (enchanted)" : name;
	}

	/** Dropped items in view go into short-term memory. */
	private void noticeItems() {
		Vec3 eye = this.drifter.getEyePosition();
		for (ItemEntity item : this.level().getEntitiesOfClass(ItemEntity.class, this.drifter.getBoundingBox().inflate(ITEM_SIGHT_RANGE))) {
			Vec3 center = item.getBoundingBox().getCenter();
			if (this.zoneOf(this.angleTo(center)) == Zone.CONE && eye.distanceTo(center) <= ITEM_SIGHT_RANGE && this.lineOfSight(eye, center)) {
				MemoryEntry entry = this.memory.remember(MemoryKind.ITEM, BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
					item.position(), this.dimension(), this.now(), MemorySource.SEEN, 0.3F, item.getUUID()).entry();
				entry.detail = item.getItem().getCount() + "x";
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Vision: blocks
	// ---------------------------------------------------------------------------------------------

	/** Sampled, not scanned (section 5): a small budget of rays across the view cone each tick. */
	private void sampleBlocks() {
		boolean playerNear = this.tracked.values().stream().anyMatch(a -> a.entity instanceof Player && a.visibleNow);
		int rays = playerNear ? BLOCK_RAYS_PLAYER_NEAR : BLOCK_RAYS_BASE;
		Vec3 eye = this.drifter.getEyePosition();
		for (int i = 0; i < rays; i++) {
			float yaw = this.drifter.getYHeadRot() + (this.drifter.getRandom().nextFloat() * 2.0F - 1.0F) * CONE_HALF_YAW;
			float pitch = Mth.clamp(this.drifter.getXRot() + (this.drifter.getRandom().nextFloat() * 2.0F - 1.0F) * CONE_HALF_PITCH, -90.0F, 90.0F);
			Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
			Vec3 end = eye.add(direction.scale(BLOCK_SIGHT_RANGE));
			BlockHitResult hit = this.clipThroughGlass(eye, end, ClipContext.Fluid.ANY);
			boolean notable = false;
			if (hit.getType() == HitResult.Type.BLOCK && eye.distanceTo(hit.getLocation()) <= this.blockSightRange(hit.getBlockPos().relative(hit.getDirection()))) {
				// Nudge into the hit block so the walk includes it.
				this.terrain.markRay(eye, hit.getLocation().add(direction.scale(0.01)));
			} else if (hit.getType() != HitResult.Type.BLOCK) {
				this.terrain.markRay(eye, eye.add(direction.scale(this.blockSightRange(BlockPos.containing(eye)))));
			}

			if (hit.getType() == HitResult.Type.BLOCK) {
				notable = this.see(hit.getBlockPos(), hit.getDirection(), eye.distanceTo(hit.getLocation()));
			}

			this.recentRays.addLast(new Ray(eye, hit.getLocation(), hit.getType() == HitResult.Type.BLOCK, notable));
			while (this.recentRays.size() > RECENT_RAYS) {
				this.recentRays.removeFirst();
			}
		}
	}

	/** Most blocks a ray can pass looking through glass, glass panes and other see-through blocks. */
	private static final int MAX_SEE_THROUGH = 8;

	/**
	 * Like a block ray, but it looks through see-through blocks (glass, glass panes): the ones vanilla gives a solid
	 * body but no visual shape. Without this, a block behind glass could never be checked again (bug found in testing).
	 */
	private BlockHitResult clipThroughGlass(Vec3 from, Vec3 to, ClipContext.Fluid fluids) {
		Vec3 start = from;
		Vec3 direction = to.subtract(from).normalize();
		BlockHitResult hit = null;
		for (int i = 0; i <= MAX_SEE_THROUGH; i++) {
			hit = this.level().clip(new ClipContext(start, to, ClipContext.Block.OUTLINE, fluids, this.drifter));
			if (hit.getType() != HitResult.Type.BLOCK || !isSeeThrough(this.level(), hit.getBlockPos())) {
				return hit;
			}

			start = exitPoint(hit.getLocation(), direction, hit.getBlockPos());
			if (start.distanceToSqr(from) >= to.distanceToSqr(from)) {
				break;
			}
		}

		// Ran out of see-through blocks to pass (or reached the end): nothing solid in the way.
		return BlockHitResult.miss(to, Direction.getNearest(direction.x, direction.y, direction.z), BlockPos.containing(to));
	}

	private static boolean isSeeThrough(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty()
			&& !state.getCollisionShape(level, pos).isEmpty()
			&& state.getVisualShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty();
	}

	/** Where a ray entering this block at {@code point} leaves it again, nudged just past the edge. */
	private static Vec3 exitPoint(Vec3 point, Vec3 direction, BlockPos block) {
		double exit = Double.MAX_VALUE;
		double[] p = {point.x, point.y, point.z};
		double[] d = {direction.x, direction.y, direction.z};
		int[] lo = {block.getX(), block.getY(), block.getZ()};
		for (int axis = 0; axis < 3; axis++) {
			if (d[axis] > 1.0E-9) {
				exit = Math.min(exit, (lo[axis] + 1 - p[axis]) / d[axis]);
			} else if (d[axis] < -1.0E-9) {
				exit = Math.min(exit, (lo[axis] - p[axis]) / d[axis]);
			}
		}

		return point.add(direction.scale(Math.max(exit, 0.0) + 1.0E-4));
	}

	/** How far it can make out a block whose visible face has this light. Night vision lights everything up. */
	private double blockSightRange(BlockPos litFrom) {
		int light = this.drifter.hasEffect(MobEffects.NIGHT_VISION) ? 15 : this.level().getRawBrightness(litFrom, this.level().getSkyDarken());
		return BLOCK_DARK_RANGE + (BLOCK_SIGHT_RANGE - BLOCK_DARK_RANGE) * light / 15.0;
	}

	/** It sees a block face. Records the chunk as explored and the block if it's notable. */
	private boolean see(BlockPos pos, Direction face, double distance) {
		if (distance > this.blockSightRange(pos.relative(face))) {
			return false;
		}

		long now = this.now();
		BlockState state = this.level().getBlockState(pos);
		MentalMap.Tile tile = this.memory.map().explore(new net.minecraft.world.level.ChunkPos(pos), now);
		if (tile.biome.isEmpty()) {
			tile.biome = this.level().getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
		}

		if (state.getFluidState().is(FluidTags.WATER)) {
			tile.water = true;
		} else if (state.getFluidState().is(FluidTags.LAVA)) {
			tile.lava = true;
		}

		return this.rememberBlock(pos, state, MemorySource.SEEN);
	}

	private boolean rememberBlock(BlockPos pos, BlockState state, MemorySource source) {
		BlockNotes.Note note = BlockNotes.classify(state);
		if (note == null) {
			return false;
		}

		DrifterMemory.Remembered remembered = this.memory.remember(note.kind(), note.label(), Vec3.atCenterOf(pos), this.dimension(), this.now(),
			source, note.importance(), null);
		if (remembered.isNew() && note.importance() >= 0.5F) {
			this.think("noticed " + shortLabel(note.label()) + " at " + pos.toShortString());
		}

		return true;
	}

	/**
	 * It broke a block: remembers mining there today, and mining reveals the six neighbors of the broken block
	 * (section 5), which is the only way it learns what's inside rock.
	 */
	public void minedBlock(BlockPos broken, BlockState was) {
		this.memory.remember(MemoryKind.MINED, BuiltInRegistries.BLOCK.getKey(was.getBlock()).toString(), Vec3.atCenterOf(broken), this.dimension(),
			this.now(), MemorySource.SEEN, 0.2F, null);
		this.revealAround(broken);
	}

	public void revealAround(BlockPos broken) {
		this.memory.forgetBlockAt(broken, this.dimension());
		this.terrain.mark(broken);
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = broken.relative(direction);
			this.terrain.mark(neighbor);
			this.rememberBlock(neighbor, this.level().getBlockState(neighbor), MemorySource.SEEN);
		}
	}

	/** It placed this block itself, so it knows it's there (the start of the owned-blocks log, section 6). */
	public void placedBlock(BlockPos pos) {
		this.rememberBlock(pos, this.level().getBlockState(pos), MemorySource.SEEN);
	}

	/** It looked inside a chest or barrel: remembers a rough summary of the contents (section 6, "Storage"). */
	public void sawContents(BlockPos pos, net.minecraft.world.Container container) {
		BlockNotes.Note note = BlockNotes.classify(this.level().getBlockState(pos));
		if (note == null || note.kind() != MemoryKind.STORAGE) {
			return;
		}

		Map<String, Integer> totals = new HashMap<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' '), stack.getCount(), Integer::sum);
			}
		}

		String summary = totals.isEmpty() ? "empty" : totals.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(6)
			.map(entry -> entry.getValue() + " " + entry.getKey())
			.reduce((a, b) -> a + ", " + b)
			.orElse("");
		MemoryEntry entry = this.memory.remember(note.kind(), note.label(), Vec3.atCenterOf(pos), this.dimension(), this.now(), MemorySource.SEEN,
			note.importance(), null).entry();
		entry.detail = summary;
	}

	/**
	 * Re-checks remembered blocks that are in view (stations are "verified on arrival", section 6): still there
	 * refreshes the memory; a clear view of an empty spot means it's gone. Blocked views tell it nothing.
	 */
	private void verifyRememberedBlocks() {
		Vec3 eye = this.drifter.getEyePosition();
		ResourceLocation dimension = this.dimension();
		List<MemoryEntry> inView = this.memory.all()
			.filter(entry -> entry.kind.isBlockKind() && entry.kind.cellSize == 1 && entry.dimension.equals(dimension)
				&& entry.pos.distanceTo(eye) <= VERIFY_RANGE && this.zoneOf(this.angleTo(entry.pos)) == Zone.CONE)
			.sorted(Comparator.comparingLong(entry -> entry.confirmedAt))
			.limit(VERIFY_PER_SECOND)
			.toList();
		for (MemoryEntry entry : inView) {
			BlockPos pos = entry.blockPos();
			BlockHitResult hit = this.clipThroughGlass(eye, entry.pos, ClipContext.Fluid.NONE);
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
				if (eye.distanceTo(hit.getLocation()) > this.blockSightRange(pos.relative(hit.getDirection()))) {
					continue;
				}

				BlockNotes.Note note = BlockNotes.classify(this.level().getBlockState(pos));
				if (note != null && note.label().equals(entry.label)) {
					entry.confirmedAt = this.now();
					entry.baseConfidence = 1.0F;
				} else {
					this.memory.forget(entry);
					this.think(shortLabel(entry.label) + " at " + pos.toShortString() + " has changed");
				}
			} else if (hit.getType() == HitResult.Type.MISS && eye.distanceTo(entry.pos) <= this.blockSightRange(pos)) {
				this.memory.forget(entry);
				this.think(shortLabel(entry.label) + " at " + pos.toShortString() + " is gone");
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Hearing and touch
	// ---------------------------------------------------------------------------------------------

	/** Called by {@link Hearing}. Direction and rough distance only, never who made it. */
	void onHeard(HeardSound.Kind kind, Vec3 source, @Nullable Entity maker) {
		Vec3 eye = this.drifter.getEyePosition();
		double distance = eye.distanceTo(source);
		double error = distance * HEARING_POSITION_ERROR;
		Vec3 offset = new Vec3(this.drifter.getRandom().nextGaussian(), this.drifter.getRandom().nextGaussian() * 0.3, this.drifter.getRandom().nextGaussian())
			.normalize().scale(error * this.drifter.getRandom().nextDouble());
		Vec3 rough = source.add(offset);
		double roughDistance = Math.max(1.0, Math.round(eye.distanceTo(rough)));
		// Footsteps and digging repeat: the same kind of sound from about the same place is one ongoing sound.
		boolean ongoing = this.sounds.removeIf(sound -> sound.kind() == kind && sound.roughPos().distanceTo(rough) < SAME_SOUND_DISTANCE
			&& this.now() - sound.time() < SAME_SOUND_TICKS);
		this.sounds.addLast(new HeardSound(kind, rough, roughDistance, this.now()));
		while (this.sounds.size() > MAX_SOUNDS) {
			this.sounds.removeFirst();
		}

		if (ongoing && kind != HeardSound.Kind.FUSE) {
			return;
		}

		this.think(String.format(Locale.ROOT, "heard %s %s, about %d blocks away", kind.description, this.direction(rough), (int)roughDistance));
		// A creeper fuse always forces its source into attention (section 5), though only sight can identify it.
		if (kind == HeardSound.Kind.FUSE && maker != null) {
			this.force(maker, Awareness.Level.GLIMPSE.threshold);
		}

		if (kind != HeardSound.Kind.MOVEMENT || distance < 8.0) {
			this.glanceAt(rough);
		}
	}

	/** Touch (section 5): it knows which way a hit came from and what kind of thing it was, and turns to look. */
	public void onHurt(DamageSource source, float amount) {
		Vec3 from = source.getSourcePosition();
		String kind = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile ? "a projectile"
			: source.getEntity() != null ? "an attack" : source.type().msgId();
		String where = from == null ? "" : " " + this.direction(from);
		this.think("got hurt by " + kind + where + String.format(Locale.ROOT, " (%.1f damage)", amount));
		float importance = Mth.clamp(amount / 8.0F, 0.1F, 1.0F);
		MemoryEntry entry = this.memory.remember(MemoryKind.HURT, source.type().msgId(), this.drifter.position(), this.dimension(), this.now(),
			MemorySource.INFERRED, importance, null).entry();
		entry.detail = kind;
		if (source.getEntity() != null) {
			this.force(source.getEntity(), Awareness.Level.GLIMPSE.threshold);
		}

		if (from != null) {
			this.glanceAt(from);
		}
	}

	private void force(Entity entity, float minimumAwareness) {
		Awareness awareness = this.tracked.computeIfAbsent(entity.getId(), id -> new Awareness(entity, this.now()));
		awareness.forcedTicks = FORCED_TRACK_TICKS;
		awareness.value = Math.max(awareness.value, minimumAwareness);
		awareness.lastSeenPos = entity.position();
	}

	/** "left", "behind", "ahead and above" relative to where the head points. */
	public String direction(Vec3 point) {
		float[] angle = this.angleTo(point);
		float yaw = angle[0];
		String horizontal;
		if (Math.abs(yaw) <= 30.0F) {
			horizontal = "ahead";
		} else if (Math.abs(yaw) >= 135.0F) {
			horizontal = "behind";
		} else {
			horizontal = yaw < 0 ? "to the left" : "to the right";
		}

		if (angle[1] < -30.0F) {
			return horizontal + " and above";
		} else if (angle[1] > 30.0F) {
			return horizontal + " and below";
		}

		return horizontal;
	}

	private static String describe(Entity entity) {
		if (entity instanceof Player player) {
			return player.getGameProfile().getName() + " (player)";
		}

		return "a " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().replace('_', ' ');
	}

	public static String shortLabel(String label) {
		int colon = label.indexOf(':');
		return (colon >= 0 ? label.substring(colon + 1) : label).replace('_', ' ');
	}

	// ---------------------------------------------------------------------------------------------
	// Glances: the head turns toward a glimpse or a sound (section 5 awareness table)
	// ---------------------------------------------------------------------------------------------

	/**
	 * Turns the head toward something it noticed, but only while idle (no task, nothing else being looked at):
	 * reacting while busy is the brain's job (milestone 5).
	 */
	private void glanceAt(Vec3 point) {
		CameraModel camera = this.drifter.getCamera();
		if (this.glanceCooldown > 0 || this.drifter.getTasks().current() != null || camera.hasTarget() && camera.targetToken() != this.glanceToken) {
			return;
		}

		camera.lookAt(point, CameraModel.TurnSpeed.NORMAL);
		this.glanceToken = camera.targetToken();
		this.glanceTicks = GLANCE_HOLD_TICKS;
		this.glanceCooldown = GLANCE_COOLDOWN_TICKS;
	}

	private void tickGlance() {
		if (this.glanceCooldown > 0) {
			this.glanceCooldown--;
		}

		if (this.glanceTicks > 0 && --this.glanceTicks == 0) {
			CameraModel camera = this.drifter.getCamera();
			if (camera.targetToken() == this.glanceToken) {
				camera.clearTarget();
			}
		}
	}
}
