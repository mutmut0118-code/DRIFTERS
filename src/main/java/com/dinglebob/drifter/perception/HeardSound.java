package com.dinglebob.drifter.perception;

import java.util.Locale;

import net.minecraft.world.phys.Vec3;

/**
 * Something the Drifter heard (DESIGN.md section 5, "Hearing"): the kind of sound, a rough position (off by up to
 * 15% of the distance) and when. Never who made it. Part of working memory: never saved.
 */
public record HeardSound(Kind kind, Vec3 roughPos, double roughDistance, long time) {
	/** Sound kinds and how far each carries (section 5 hearing table). */
	public enum Kind {
		MOVEMENT("something moving", 16),
		BLOCKS("building or mining", 16),
		CONTAINER("a container or door", 16),
		EATING("eating or drinking", 8),
		PROJECTILE("a projectile", 16),
		HURT("something getting hurt", 16),
		EXPLOSION("an explosion", 64),
		FUSE("a fuse hissing", 12);

		public final String description;
		public final double radius;

		Kind(String description, double radius) {
			this.description = description;
			this.radius = radius;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}
}
