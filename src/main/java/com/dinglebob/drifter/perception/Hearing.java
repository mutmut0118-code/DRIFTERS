package com.dinglebob.drifter.perception;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.FakePlayerBridge;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hearing (DESIGN.md section 5) through the same vibration events sculk sensors listen to. Sneaking makes no step
 * sounds (the vanilla "ignore vibrations while sneaking" tag), and wool between the sound and the Drifter blocks it,
 * like it does for sculk. Vanilla only delivers these events within 16 blocks; explosions are passed in separately
 * (see {@link #hearExplosion}) so they carry 64.
 * TODO: mob ambient sounds (groans, rattles) don't produce game events; they need a sound hook.
 */
public class Hearing implements GameEventListener {
	/** Vanilla delivers game events no farther than this. */
	public static final int LISTEN_RADIUS = 16;

	private final DrifterEntity drifter;
	private final Senses senses;
	private final PositionSource source;
	/**
	 * When it last heard each creature make each kind of sound (id * 8 + kind): the same creature's footsteps again within
	 * {@link #REPEAT_TICKS} are the same ongoing sound, as {@link Senses#onHeard} treats them anyway, and are let go before
	 * any work (milestone 16: in a 300-Drifter battle hearing was a tenth of the server's time).
	 */
	private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap lastFrom = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
	private static final int REPEAT_TICKS = 10;

	Hearing(DrifterEntity drifter, Senses senses) {
		this.drifter = drifter;
		this.senses = senses;
		this.source = new EntityPositionSource(drifter, drifter.getEyeHeight());
	}

	@Override
	public PositionSource getListenerSource() {
		return this.source;
	}

	@Override
	public int getListenerRadius() {
		return LISTEN_RADIUS;
	}

	@Override
	public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 pos) {
		Entity maker = context.sourceEntity();
		if (maker == this.drifter || !this.drifter.isAlive()) {
			return false;
		}

		HeardSound.Kind kind = kindOf(event);
		if (kind == null) {
			return false;
		}

		// What its fake-player helper does on its behalf (breaking, placing) isn't news either.
		if (maker instanceof net.fabricmc.fabric.api.entity.FakePlayer && maker.getUUID().equals(FakePlayerBridge.helperId(this.drifter))) {
			return false;
		}

		// In a crowd it can't pick out more than a few sounds at once (milestone 16: in a battle every footstep reached every
		// Drifter in earshot, tens of thousands a tick). A fuse always gets through.
		long tick = level.getGameTime();
		if (tick != this.heardTick) {
			this.heardTick = tick;
			this.heardThisTick = 0;
		}

		if (this.heardThisTick >= HEARD_PER_TICK && kind != HeardSound.Kind.FUSE) {
			return false;
		}

		if (maker != null && maker.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
			return false;
		}

		if (maker != null && kind != HeardSound.Kind.FUSE) {
			int key = maker.getId() * 8 + kind.ordinal();
			long now = level.getGameTime();
			if (this.lastFrom.containsKey(key) && now - this.lastFrom.get(key) < REPEAT_TICKS) {
				return false;
			}

			if (this.lastFrom.size() > 512) {
				this.lastFrom.clear();
			}

			this.lastFrom.put(key, now);
		}

		Vec3 ear = this.drifter.getEyePosition();
		if (ear.distanceToSqr(pos) > kind.radius * kind.radius || isOccluded(level, pos, ear)) {
			return false;
		}

		this.heardThisTick++;
		this.senses.onHeard(kind, pos, maker);
		return true;
	}

	/** Sounds it takes in a tick at most (the rest in a din are lost). */
	private static final int HEARD_PER_TICK = 6;
	private long heardTick = Long.MIN_VALUE;
	private int heardThisTick;

	/** Each game event's kind of sound, worked out once (null: not something it hears). */
	private static final java.util.Map<GameEvent, java.util.Optional<HeardSound.Kind>> KINDS = new java.util.concurrent.ConcurrentHashMap<>();

	@Nullable
	private static HeardSound.Kind kindOf(Holder<GameEvent> event) {
		return KINDS.computeIfAbsent(event.value(), value -> java.util.Optional.ofNullable(classify(event))).orElse(null);
	}

	/** An explosion within 64 blocks (section 5 hearing table), passed in from the explosion hook. */
	public void hearExplosion(Vec3 center) {
		Vec3 ear = this.drifter.getEyePosition();
		if (this.drifter.isAlive() && ear.distanceTo(center) <= HeardSound.Kind.EXPLOSION.radius) {
			this.senses.onHeard(HeardSound.Kind.EXPLOSION, center, null);
		}
	}

	@Nullable
	static HeardSound.Kind classify(Holder<GameEvent> event) {
		if (event.is(GameEvent.STEP) || event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.SPLASH) || event.is(GameEvent.SWIM)
			|| event.is(GameEvent.FLAP) || event.is(GameEvent.ELYTRA_GLIDE)) {
			return HeardSound.Kind.MOVEMENT;
		} else if (event.is(GameEvent.BLOCK_PLACE) || event.is(GameEvent.BLOCK_DESTROY)) {
			return HeardSound.Kind.BLOCKS;
		} else if (event.is(GameEvent.CONTAINER_OPEN) || event.is(GameEvent.CONTAINER_CLOSE) || event.is(GameEvent.BLOCK_OPEN) || event.is(GameEvent.BLOCK_CLOSE)) {
			return HeardSound.Kind.CONTAINER;
		} else if (event.is(GameEvent.EAT) || event.is(GameEvent.DRINK)) {
			return HeardSound.Kind.EATING;
		} else if (event.is(GameEvent.PROJECTILE_LAND) || event.is(GameEvent.PROJECTILE_SHOOT)) {
			return HeardSound.Kind.PROJECTILE;
		} else if (event.is(GameEvent.ENTITY_DAMAGE)) {
			return HeardSound.Kind.HURT;
		} else if (event.is(GameEvent.PRIME_FUSE)) {
			return HeardSound.Kind.FUSE;
		}

		return null;
	}

	private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> OCCLUDES =
		state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);

	/** VibrationSystem.Listener#isOccluded (private in vanilla): wool and other vibration-occluding blocks in the way. */
	private static boolean isOccluded(Level level, Vec3 from, Vec3 to) {
		// No wool anywhere in the sections between: nothing to stop it (most places; the palettes say so without a ray).
		var palette = TerrainKnowledge.SectionFilter.palette(level, OCCLUDES);
		boolean maybe = false;
		for (int sx = Mth.floor(Math.min(from.x, to.x)) >> 4; sx <= Mth.floor(Math.max(from.x, to.x)) >> 4 && !maybe; sx++) {
			for (int sy = Mth.floor(Math.min(from.y, to.y)) >> 4; sy <= Mth.floor(Math.max(from.y, to.y)) >> 4 && !maybe; sy++) {
				for (int sz = Mth.floor(Math.min(from.z, to.z)) >> 4; sz <= Mth.floor(Math.max(from.z, to.z)) >> 4 && !maybe; sz++) {
					maybe = palette.mayHave(sx, sy, sz);
				}
			}
		}

		if (!maybe) {
			return false;
		}

		Vec3 fromCenter = new Vec3(Mth.floor(from.x) + 0.5, Mth.floor(from.y) + 0.5, Mth.floor(from.z) + 0.5);
		Vec3 toCenter = new Vec3(Mth.floor(to.x) + 0.5, Mth.floor(to.y) + 0.5, Mth.floor(to.z) + 0.5);
		for (Direction direction : Direction.values()) {
			Vec3 nudged = fromCenter.relative(direction, 1.0E-5F);
			if (level.isBlockInLine(new ClipBlockStateContext(nudged, toCenter, state -> state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))).getType()
				!= HitResult.Type.BLOCK) {
				return false;
			}
		}

		return true;
	}
}
