package com.dinglebob.drifter.perception;

import java.util.Locale;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * How aware the Drifter is of one entity (DESIGN.md section 5, "Awareness meter"): 0 to 1, filling while it can
 * see the entity and draining when it can't. Part of working memory: never saved.
 */
public class Awareness {
	public enum Level {
		/** Below the glimpse threshold: not consciously noticed. */
		NONE(0.0F),
		/** Head turns toward it; doesn't know what it is. */
		GLIMPSE(0.3F),
		/** Watches it; still doesn't know what it is. */
		SUSPICIOUS(0.6F),
		/** Identified: knows what (or who) it is. */
		DETECTED(1.0F);

		public final float threshold;

		Level(float threshold) {
			this.threshold = threshold;
		}

		public static Level of(float value) {
			if (value >= DETECTED.threshold) {
				return DETECTED;
			} else if (value >= SUSPICIOUS.threshold) {
				return SUSPICIOUS;
			}

			return value >= GLIMPSE.threshold ? GLIMPSE : NONE;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public final Entity entity;
	public float value;
	/** Reached DETECTED at some point while tracked: it knows what this is. */
	public boolean identified;
	public boolean visibleNow;
	public Vec3 lastSeenPos;
	public long lastSeenTick;
	/** Ticks this entity stays in the tracked set regardless of the attention budget (it hit us, a fuse hissed). */
	public int forcedTicks;
	Level lastLevel = Level.NONE;

	public Awareness(Entity entity, long now) {
		this.entity = entity;
		this.lastSeenPos = entity.position();
		this.lastSeenTick = now;
	}

	public Level level() {
		return Level.of(this.value);
	}
}
