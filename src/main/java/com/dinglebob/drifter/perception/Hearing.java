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
		// Its own sounds, including what its fake-player helper does on its behalf (breaking, placing), aren't news.
		if (maker == this.drifter || !this.drifter.isAlive() || maker != null && maker.getUUID().equals(FakePlayerBridge.helperId(this.drifter))) {
			return false;
		}

		HeardSound.Kind kind = classify(event);
		if (kind == null) {
			return false;
		}

		if (maker != null && maker.isSteppingCarefully() && event.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
			return false;
		}

		Vec3 ear = this.drifter.getEyePosition();
		if (ear.distanceTo(pos) > kind.radius || isOccluded(level, pos, ear)) {
			return false;
		}

		this.senses.onHeard(kind, pos, maker);
		return true;
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

	/** VibrationSystem.Listener#isOccluded (private in vanilla): wool and other vibration-occluding blocks in the way. */
	private static boolean isOccluded(Level level, Vec3 from, Vec3 to) {
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
