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
	/**
	 * View cone, centered on the head: from the FOV setting (`/drifter config fov`, like the game's FOV slider). At the
	 * default 70 it's about 102 degrees wide and 70 high, a player's view on a 16:9 screen.
	 */
	public static float coneHalfYaw() {
		return com.dinglebob.drifter.DrifterConfig.coneHalfYaw();
	}

	public static float coneHalfPitch() {
		return com.dinglebob.drifter.DrifterConfig.coneHalfPitch();
	}

	/**
	 * The view as a flat screen, like a player's (found in testing: the cone looked "portrait"; it was built from
	 * angles on a sphere, which came out nearly square, and bent badly when looking up or down). {forward, right, up}
	 * for a head yaw and pitch.
	 */
	public static Vec3[] viewBasis(float yaw, float pitch) {
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		return new Vec3[]{forward, right, right.cross(forward)};
	}

	/** The direction through a point on its screen: {@code sx} and {@code sy} from -1 (left, bottom) to 1 (right, top). */
	public static Vec3 screenDirection(float yaw, float pitch, float sx, float sy) {
		Vec3[] basis = viewBasis(yaw, pitch);
		double tanX = Math.tan(Math.toRadians(coneHalfYaw()));
		double tanY = Math.tan(Math.toRadians(coneHalfPitch()));
		return basis[0].add(basis[1].scale(sx * tanX)).add(basis[2].scale(sy * tanY)).normalize();
	}
	/** Peripheral zone: out to 180 degrees horizontal; motion only. */
	public static final float PERIPHERAL_HALF_YAW = 90.0F;
	public static final float PERIPHERAL_HALF_PITCH = 60.0F;
	/** Entity sight range in full light (7 chunks, the user's call: a player sees much farther than a mob), and in full darkness. */
	public static final double DAYLIGHT_RANGE = 112.0;
	public static final double DARK_RANGE = 8.0;
	/** How far above and below it looks for entities (beyond that they're behind terrain anyway). */
	public static final double VERTICAL_RANGE = 48.0;
	/** Small creatures are harder to make out far away: range scales with the square root of size (player-sized 1.0). */
	public static final double SIZE_FACTOR_MIN = 0.5;
	public static final double SIZE_FACTOR_MAX = 1.2;
	/**
	 * Beyond this, an entity is checked with one sight line (to its middle) instead of head and feet, only a few such
	 * checks run per re-rank, and a tracked one is re-checked every third update: far sight stays cheap.
	 */
	public static final double DETAIL_RANGE = 32.0;
	public static final int FAR_CHECKS_PER_RANK = 16;
	public static final int FAR_UPDATE_EVERY = 3;
	public static final double RAIN_FACTOR = 0.75;
	public static final double THUNDER_FACTOR = 0.6;
	public static final double UNDERWATER_RANGE = 12.0;
	public static final double BLINDNESS_RANGE = 5.0;
	public static final double DARKNESS_EFFECT_RANGE = 10.0;
	/**
	 * Awareness gained per tick at the center of view, close, with the target moving and fully visible: about a third
	 * of a second to know what it is (user's rule: common sense, not a mob's slow noticing).
	 */
	public static final float FILL_RATE = 0.15F;
	/** Peripheral motion fills slower and can only produce a glimpse (which turns its head). */
	public static final float PERIPHERAL_FILL_RATE = 0.08F;
	public static final float PERIPHERAL_CAP = 0.35F;
	public static final float DRAIN_RATE = 0.01F;
	public static final float SNEAKING_FACTOR = 0.4F;
	public static final float STILL_FACTOR = 0.8F;
	/** An invisible entity wearing armor can be spotted by the armor, slowly. */
	public static final float INVISIBLE_ARMOR_FACTOR = 0.3F;
	public static final double MOVING_SPEED = 0.02;
	/** Attention budget: entities tracked in detail at once, re-ranked every RANK_INTERVAL ticks. */
	public static final int ATTENTION_BUDGET = 12;
	public static final int RANK_INTERVAL = 10;
	public static final int AWARENESS_INTERVAL = 2;
	public static final int FORCED_TRACK_TICKS = 100;
	/** Items on the ground are noticed within this distance. */
	public static final double ITEM_SIGHT_RANGE = 32.0;

	// ----- Seeing blocks (section 5) -----
	public static final int BLOCK_RAYS_BASE = 6;
	public static final int BLOCK_RAYS_PLAYER_NEAR = 12;
	public static final double BLOCK_SIGHT_RANGE = 48.0;
	public static final double BLOCK_DARK_RANGE = 8.0;
	/**
	 * Far sight (user's rule: see a mountain or a village in the distance, out to 7 chunks): a couple of long rays per
	 * tick near the horizon. They note landmarks and resources, but only the first terrainMarkRange() blocks of each
	 * go into the pathfinder's block knowledge (it plans within 48 blocks), which keeps memory small.
	 */
	public static final int FAR_RAYS = 2;
	public static final double FAR_SIGHT_RANGE = 112.0;
	/** Far rays aim between this far below and above the horizon (degrees). */
	public static final float FAR_PITCH_DOWN = 12.0F;
	public static final float FAR_PITCH_UP = 15.0F;
	/** (Follows the path range, `/drifter config path`: the pathfinder needs to know what it plans across.) */
	public static double terrainMarkRange() {
		return com.dinglebob.drifter.DrifterConfig.pathRange();
	}
	/** Landmarks: high ground this far above sea level, or this far above its feet, seen from at least MIN distance. */
	public static final int MOUNTAIN_ABOVE_SEA = 40;
	public static final int MOUNTAIN_RISE = 30;
	public static final double LANDMARK_MIN_DISTANCE = 24.0;
	/** Bare stone seen farther than this is remembered as a place to get stone (closer, it just sees it). */
	public static final double STONE_HINT_DISTANCE = 24.0;
	/** How many remembered blocks in view are re-checked per second (stations are "verified on arrival"). */
	public static final int VERIFY_PER_SECOND = 6;
	public static final double VERIFY_RANGE = 24.0;

	// ----- Hearing and reactions -----
	public static final double HEARING_POSITION_ERROR = 0.15;
	public static final int SOUND_MEMORY_TICKS = 20 * 30;
	/** A creature heard again this soon isn't news (no new thought). */
	public static final int HEARD_AGAIN_TICKS = 20 * 20;
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

	/**
	 * Where a point is in its view: {degrees across the screen (+ right), degrees up the screen (+ up), degrees above or
	 * below the view's middle plane}. The first two describe a flat screen: inside the cone means on screen, exactly as
	 * for a player at this FOV. The third is for the peripheral zone around it.
	 */
	private float[] screenAngles(Vec3 point) {
		Vec3[] basis = viewBasis(this.drifter.getYHeadRot(), this.drifter.getXRot());
		Vec3 d = point.subtract(this.drifter.getEyePosition());
		double x = d.dot(basis[1]);
		double y = d.dot(basis[2]);
		double z = d.dot(basis[0]);
		float across = (float)(Mth.atan2(x, z) * Mth.RAD_TO_DEG);
		float up = z > 1.0E-6 ? (float)(Math.atan(y / z) * Mth.RAD_TO_DEG) : y >= 0.0 ? 90.0F : -90.0F;
		float elevation = (float)(Mth.atan2(y, Math.sqrt(x * x + z * z)) * Mth.RAD_TO_DEG);
		return new float[]{across, up, elevation};
	}

	private Zone zoneOf(float[] screen) {
		float across = Math.abs(screen[0]);
		if (across < 90.0F && across <= coneHalfYaw() && Math.abs(screen[1]) <= coneHalfPitch()) {
			return Zone.CONE;
		}

		return across <= PERIPHERAL_HALF_YAW && Math.abs(screen[2]) <= PERIPHERAL_HALF_PITCH ? Zone.PERIPHERAL : Zone.NONE;
	}

	/** Sight lines cast so far (for the far-sight budget). */
	private int sightLines;

	private boolean lineOfSight(Vec3 from, Vec3 to) {
		this.sightLines++;
		return this.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.drifter)).getType() == HitResult.Type.MISS;
	}

	/** How far it can see this entity right now: light at the target, weather, water and effects. */
	private double sightRange(Entity target) {
		Level level = this.level();
		int light = level.getRawBrightness(BlockPos.containing(target.getEyePosition()), level.getSkyDarken());
		if (target.isOnFire() || target.isCurrentlyGlowing() || holdsLight(target) || this.drifter.hasEffect(MobEffects.NIGHT_VISION)) {
			light = 15;
		}

		double range = DARK_RANGE + (com.dinglebob.drifter.DrifterConfig.sightRange() - DARK_RANGE) * light / 15.0;
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
		double range = this.sightRange(target) * sizeFactor(target);
		if (distance > range) {
			return Sighting.NOT_SEEN;
		}

		float[] angle = this.screenAngles(target.getBoundingBox().getCenter());
		Zone zone = this.zoneOf(angle);
		// Out of the corner of its eye it only catches movement nearby.
		if (zone == Zone.NONE || zone == Zone.PERIPHERAL && distance > DETAIL_RANGE) {
			return Sighting.NOT_SEEN;
		}

		float visibility;
		if (distance > DETAIL_RANGE) {
			// Far away: one sight line to its middle.
			visibility = this.lineOfSight(eye, target.getBoundingBox().getCenter()) ? 0.8F : 0.0F;
		} else {
			// A ray to the head and one to the feet: either counts, both count more.
			boolean headSeen = this.lineOfSight(eye, head);
			boolean feetSeen = this.lineOfSight(eye, target.position().add(0.0, 0.1, 0.0));
			visibility = headSeen && feetSeen ? 1.0F : headSeen || feetSeen ? 0.6F : 0.0F;
		}

		float centrality = 1.0F - 0.4F * Math.min(1.0F, Math.max(Math.abs(angle[0]) / coneHalfYaw(), Math.abs(angle[1]) / coneHalfPitch()));
		return new Sighting(zone, visibility, distance, range, centrality);
	}

	/** Bigger things are seen from farther (a chicken is a speck long before a horse is). */
	private static double sizeFactor(Entity target) {
		double size = Math.max(target.getBbHeight(), target.getBbWidth()) / 1.8;
		return Mth.clamp(Math.sqrt(size), SIZE_FACTOR_MIN, SIZE_FACTOR_MAX);
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
		AABB area = this.drifter.getBoundingBox().inflate(com.dinglebob.drifter.DrifterConfig.sightRange(), VERTICAL_RANGE, com.dinglebob.drifter.DrifterConfig.sightRange());
		List<Entity> candidates = this.level().getEntities(this.drifter, area,
			entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator());
		// Nearest first, so the far-sight budget goes to the closest of the far ones.
		candidates.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(this.drifter)));
		List<Map.Entry<Entity, Double>> ranked = new ArrayList<>();
		int farChecks = 0;
		for (Entity entity : candidates) {
			Awareness current = this.tracked.get(entity.getId());
			boolean keep = current != null && (current.forcedTicks > 0 || current.value >= Awareness.Level.GLIMPSE.threshold);
			boolean far = entity.distanceToSqr(this.drifter) > DETAIL_RANGE * DETAIL_RANGE;
			if (far && !keep && farChecks >= FAR_CHECKS_PER_RANK) {
				continue;
			}

			int raysBefore = this.sightLines;
			Sighting sighting = this.look(entity);
			if (far && this.sightLines != raysBefore) {
				farChecks++;
			}

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
		int update = this.ticks / AWARENESS_INTERVAL;
		for (Awareness awareness : this.tracked.values()) {
			Entity entity = awareness.entity;
			if (awareness.forcedTicks > 0) {
				awareness.forcedTicks -= AWARENESS_INTERVAL;
			}

			// Far ones are re-checked every third update (and gain three updates' worth): cheaper, hardly different.
			boolean far = !entity.isRemoved() && entity.distanceToSqr(this.drifter) > DETAIL_RANGE * DETAIL_RANGE;
			if (far && Math.floorMod(update + entity.getId(), FAR_UPDATE_EVERY) != 0) {
				continue;
			}

			int ticks = AWARENESS_INTERVAL * (far ? FAR_UPDATE_EVERY : 1);
			Sighting sighting = entity.isRemoved() ? Sighting.NOT_SEEN : this.look(entity);
			float stealth = stealthFactor(entity);
			awareness.visibleNow = sighting.seen() && stealth > 0.0F;
			if (awareness.visibleNow) {
				float closeness = (float)(0.6 + 0.4 * (1.0 - sighting.distance() / sighting.range()));
				float motion = isMoving(entity) ? 1.0F : STILL_FACTOR;
				if (sighting.zone() == Zone.CONE) {
					float gain = FILL_RATE * sighting.visibility() * closeness * sighting.centrality() * motion * stealth;
					awareness.value = Math.min(1.0F, awareness.value + gain * ticks);
				} else if (isMoving(entity) && awareness.value < PERIPHERAL_CAP) {
					float gain = PERIPHERAL_FILL_RATE * sighting.visibility() * closeness * stealth;
					awareness.value = Math.min(PERIPHERAL_CAP, awareness.value + gain * ticks);
				}

				awareness.lastSeenPos = entity.position();
				awareness.lastSeenTick = now;
			} else {
				awareness.value = Math.max(0.0F, awareness.value - DRAIN_RATE * ticks);
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
			// (Something it only heard just fades from mind.)
			if (awareness.seenWithin(this.now(), 20 * 30)) {
				this.think("lost track of " + describe(entity));
			}

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
			if (this.zoneOf(this.screenAngles(center)) == Zone.CONE && eye.distanceTo(center) <= ITEM_SIGHT_RANGE && this.lineOfSight(eye, center)) {
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
			// A random point on its screen, evenly over the whole picture (as pixels are).
			float sx = this.drifter.getRandom().nextFloat() * 2.0F - 1.0F;
			float sy = this.drifter.getRandom().nextFloat() * 2.0F - 1.0F;
			this.castBlockRay(eye, screenDirection(this.drifter.getYHeadRot(), this.drifter.getXRot(), sx, sy), BLOCK_SIGHT_RANGE);
		}

		// Far sight: near the horizon, where mountains, villages and cliffs show up. Only the part of the horizon band
		// that's inside its view (looking at its feet, it doesn't see the skyline).
		float low = Math.max(-FAR_PITCH_UP, this.drifter.getXRot() - coneHalfPitch());
		float high = Math.min(FAR_PITCH_DOWN, this.drifter.getXRot() + coneHalfPitch());
		for (int i = 0; i < FAR_RAYS && low < high; i++) {
			float yaw = this.drifter.getYHeadRot() + (this.drifter.getRandom().nextFloat() * 2.0F - 1.0F) * coneHalfYaw();
			float pitch = Mth.lerp(this.drifter.getRandom().nextFloat(), low, high);
			this.castBlockRay(eye, Vec3.directionFromRotation(pitch, yaw), com.dinglebob.drifter.DrifterConfig.sightRange());
		}
	}

	private void castBlockRay(Vec3 eye, Vec3 direction, double maxRange) {
		// Never look into chunks that aren't loaded: reading them would load (or even generate) them.
		double reach = this.loadedReach(eye, direction, maxRange);
		if (reach <= 1.0) {
			return;
		}

		Vec3 end = eye.add(direction.scale(reach));
		BlockHitResult hit = this.clipThroughGlass(eye, end, ClipContext.Fluid.ANY);
		boolean blockHit = hit.getType() == HitResult.Type.BLOCK;
		double distance = eye.distanceTo(hit.getLocation());
		double range = blockHit ? this.blockSightRange(hit.getBlockPos().relative(hit.getDirection()), maxRange) : 0.0;
		// What it saw across goes into the pathfinder's block knowledge, but only nearby (it plans within 48 blocks).
		if (blockHit && distance <= Math.min(range, terrainMarkRange())) {
			// Nudge into the hit block so the walk includes it.
			this.terrain.markRay(eye, hit.getLocation().add(direction.scale(0.01)));
		} else {
			double seenAcross = Math.min(blockHit ? Math.min(distance, range) : this.blockSightRange(BlockPos.containing(eye), maxRange), terrainMarkRange());
			this.terrain.markRay(eye, eye.add(direction.scale(seenAcross)));
		}

		boolean notable = false;
		if (blockHit && distance <= range) {
			notable = this.see(hit.getBlockPos(), hit.getDirection(), distance, range);
		}

		this.recentRays.addLast(new Ray(eye, hit.getLocation(), blockHit, notable));
		while (this.recentRays.size() > RECENT_RAYS) {
			this.recentRays.removeFirst();
		}
	}

	/** How far along this direction the chunks are loaded (checked every 8 blocks), up to {@code range}. */
	private double loadedReach(Vec3 from, Vec3 direction, double range) {
		for (double along = 0.0; along < range; along += 8.0) {
			Vec3 point = from.add(direction.scale(along));
			if (!this.level().hasChunk(Mth.floor(point.x) >> 4, Mth.floor(point.z) >> 4)) {
				return Math.max(0.0, along - 8.0);
			}
		}

		Vec3 end = from.add(direction.scale(range));
		return this.level().hasChunk(Mth.floor(end.x) >> 4, Mth.floor(end.z) >> 4) ? range : Math.max(0.0, range - 8.0);
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
		return this.blockSightRange(litFrom, BLOCK_SIGHT_RANGE);
	}

	private double blockSightRange(BlockPos litFrom, double fullLightRange) {
		int light = this.drifter.hasEffect(MobEffects.NIGHT_VISION) ? 15 : this.level().getRawBrightness(litFrom, this.level().getSkyDarken());
		double range = BLOCK_DARK_RANGE + (fullLightRange - BLOCK_DARK_RANGE) * light / 15.0;
		BlockPos eyePos = BlockPos.containing(this.drifter.getEyePosition());
		if (fullLightRange > BLOCK_SIGHT_RANGE && this.level().isRainingAt(eyePos)) {
			range *= this.level().isThundering() ? THUNDER_FACTOR : RAIN_FACTOR;
		}

		return range;
	}

	/** It sees a block face. Records the chunk as explored, the block if it's notable, and any landmark. */
	private boolean see(BlockPos pos, Direction face, double distance, double range) {
		if (distance > range) {
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

		boolean landmark = this.noticeLandmark(pos, state, distance);
		return this.rememberBlock(pos, state, MemorySource.SEEN) || landmark;
	}

	/**
	 * Big things seen from afar (user's rule: a player sees a mountain or a village in the distance and knows it):
	 * high ground, village blocks, and bare stone (a cliff, a quarry face, a cave mouth) as a place to get stone.
	 */
	private boolean noticeLandmark(BlockPos pos, BlockState state, double distance) {
		if (BlockNotes.isVillageBlock(state)) {
			return this.rememberLandmark("village", pos, 0.8F, distance);
		}

		if (distance < LANDMARK_MIN_DISTANCE || !BlockNotes.isNaturalGround(state)) {
			return false;
		}

		int seaLevel = this.level().getSeaLevel();
		if (pos.getY() >= seaLevel + MOUNTAIN_ABOVE_SEA || pos.getY() - this.drifter.getBlockY() >= MOUNTAIN_RISE) {
			this.rememberLandmark("mountain", pos, 0.6F, distance);
		}

		if (distance > STONE_HINT_DISTANCE && state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)) {
			this.memory.remember(MemoryKind.RESOURCE, BlockNotes.EXPOSED_STONE, Vec3.atCenterOf(pos), this.dimension(), this.now(), MemorySource.SEEN,
				0.25F, null);
			return true;
		}

		return false;
	}

	private boolean rememberLandmark(String label, BlockPos pos, float importance, double distance) {
		DrifterMemory.Remembered remembered = this.memory.remember(MemoryKind.LANDMARK, label, Vec3.atCenterOf(pos), this.dimension(), this.now(),
			MemorySource.SEEN, importance, null);
		if (remembered.isNew()) {
			this.think(String.format(Locale.ROOT, "spotted a %s %s, about %d blocks away", label, this.direction(Vec3.atCenterOf(pos)),
				(int)Math.round(distance)));
		}

		return true;
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
		// One of its own chests at home: it knows exactly what's in it now (for planning, section 9 "home storage").
		com.dinglebob.drifter.brain.Home home = this.drifter.getDrifterBrain().home();
		if (home != null && home.isChest(pos)) {
			home.record(pos, container);
		}
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
				&& entry.pos.distanceTo(eye) <= VERIFY_RANGE && this.zoneOf(this.screenAngles(entry.pos)) == Zone.CONE)
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
		Vec3 rough = this.roughly(source);
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

	/** Where a sound came from, as well as ears can tell: off by up to 15% of the distance. */
	private Vec3 roughly(Vec3 source) {
		double error = this.drifter.getEyePosition().distanceTo(source) * HEARING_POSITION_ERROR;
		Vec3 offset = new Vec3(this.drifter.getRandom().nextGaussian(), this.drifter.getRandom().nextGaussian() * 0.3, this.drifter.getRandom().nextGaussian())
			.normalize().scale(error * this.drifter.getRandom().nextDouble());
		return source.add(offset);
	}

	/**
	 * A creature's own sound (from {@link CreatureHearing}): like a player, it knows what makes that sound (a moo is a
	 * cow, a rattle is a skeleton, a hiss is a creeper) and roughly where, without looking (user's rule; section 5,
	 * "Mob ambient sounds"). It doesn't know the exact spot until it sees it.
	 */
	public void heardCreature(LivingEntity creature) {
		long now = this.now();
		Vec3 rough = this.roughly(creature.position());
		Awareness awareness = this.tracked.get(creature.getId());
		if (awareness == null) {
			awareness = new Awareness(creature, now);
			// Heard, not seen.
			awareness.lastSeenPos = rough;
			awareness.lastSeenTick = Long.MIN_VALUE / 2;
			this.tracked.put(creature.getId(), awareness);
		}

		boolean news = !awareness.identified || !awareness.heardWithin(now, HEARD_AGAIN_TICKS);
		awareness.identified = true;
		awareness.heardPos = rough;
		awareness.heardTick = now;
		awareness.value = Math.max(awareness.value, Awareness.Level.SUSPICIOUS.threshold);
		awareness.lastLevel = awareness.level();
		boolean hostile = creature instanceof Enemy;
		if (hostile) {
			awareness.forcedTicks = Math.max(awareness.forcedTicks, FORCED_TRACK_TICKS);
		}

		float importance = hostile ? 0.4F : 0.2F;
		MemoryEntry entry = this.memory.remember(MemoryKind.MOB, BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()).toString(), rough,
			this.dimension(), now, MemorySource.HEARD, importance, creature.getUUID()).entry();
		if (entry.source == MemorySource.HEARD) {
			// (A higher voice gives a baby away.)
			entry.detail = creature.isBaby() ? "baby" : "";
		}

		if (news) {
			double distance = this.drifter.getEyePosition().distanceTo(rough);
			this.think(String.format(Locale.ROOT, "heard %s %s, about %d blocks away", describe(creature), this.direction(rough),
				(int)Math.max(1, Math.round(distance))));
			if (hostile || distance < 8.0) {
				this.glanceAt(rough);
			}
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
