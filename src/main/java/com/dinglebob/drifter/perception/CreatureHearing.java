package com.dinglebob.drifter.perception;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hearing creatures by their own sounds (DESIGN.md section 5, "Mob ambient sounds"; user's rule: a player who hears a
 * pig knows it's a pig without looking). A creature's voice, hurt and death sounds, distinctive steps (a skeleton's
 * rattle, a spider's skitter) and a creeper's hiss all name the kind of creature; it gets a rough position, not the
 * exact spot. Generic sounds (splashes, plain block steps) and players' sounds tell it nothing about who.
 *
 * <p>Cheap by design: every creature sound checks a short list of loaded Drifters (usually a handful) by distance,
 * instead of searching the world for listeners.
 */
public final class CreatureHearing {
	private static final List<DrifterEntity> LOADED = new ArrayList<>();

	private CreatureHearing() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof DrifterEntity drifter && !LOADED.contains(drifter)) {
				LOADED.add(drifter);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof DrifterEntity drifter) {
				LOADED.remove(drifter);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	public static void onSound(Entity maker, SoundEvent sound, float volume) {
		if (LOADED.isEmpty() || !(maker instanceof LivingEntity creature) || maker instanceof Player || maker instanceof DrifterEntity) {
			return;
		}

		String path = sound.getLocation().getPath();
		if (!path.startsWith("entity.") || path.startsWith("entity.generic.") || path.startsWith("entity.player.")) {
			return;
		}

		// How far vanilla lets a player hear it (16 blocks at normal volume, more for loud sounds).
		double range = sound.getRange(volume);
		double rangeSqr = range * range;
		for (int i = 0; i < LOADED.size(); i++) {
			DrifterEntity drifter = LOADED.get(i);
			if (drifter.level() == maker.level() && drifter.isAlive() && drifter.distanceToSqr(maker) <= rangeSqr) {
				drifter.getSenses().heardCreature(creature);
			}
		}
	}
}
