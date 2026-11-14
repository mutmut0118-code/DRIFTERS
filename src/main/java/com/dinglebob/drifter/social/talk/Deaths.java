package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Deaths as the Drifters near know of them (docs/PLAYER_TALK.md I4.9, C1.10, C3.4, F3.3, B9.3): a Drifter's death (who, how,
 * by whom, where, when), read by those within chat range as the death message, and seen by those who watched; where a
 * player died, by those who saw it. "wheres reed?" then gets "reed's gone. was slain by zombie", "where did i die?" a
 * direction, and a death by a monster seen close by marks the place as dangerous ({@link Dangers}).
 */
public final class Deaths {
	private Deaths() {
	}

	/** A death: who (a Drifter's name), how (the death message after the name, lower case), by whom (a name or null). */
	public record Death(String who, UUID id, String how, @Nullable String by, BlockPos pos, String dim, long at) {
	}

	/** How long a death is talked of (ticks). */
	private static final long REMEMBERED = 24000L * 5;
	/** How far a death is seen from (blocks), and read in chat from. */
	private static final double SEEN = 32.0;
	private static final double READ = 160.0;

	/** Drifters' deaths, by name (lower case); who of the living knew of each. */
	private static final Map<String, Death> byName = new HashMap<>();
	private static final Map<String, java.util.Set<UUID>> knownBy = new HashMap<>();
	/** Where each player last died, as each Drifter that saw it knows ("drifter|player"). */
	private static final Map<String, Death> playerDeaths = new HashMap<>();

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim.level() instanceof ServerLevel level)) {
				return;
			}

			long now = level.getGameTime();
			String dim = level.dimension().location().toString();
			Entity killer = source.getEntity();
			boolean monster = killer instanceof Enemy || source.getDirectEntity() instanceof Enemy;
			String message = source.getLocalizedDeathMessage(victim).getString();
			String name = victim.getName().getString();
			String how = message.startsWith(name) ? message.substring(name.length()).trim() : message;
			how = how.toLowerCase(Locale.ROOT).replaceAll("[.!]+$", "");
			Death death = new Death(name, victim.getUUID(), how, killer == null ? null : killer.getName().getString(), victim.blockPosition(), dim, now);
			boolean drifter = victim instanceof DrifterEntity d && !d.isTemporary();
			boolean player = victim instanceof ServerPlayer;
			if (!drifter && !player) {
				return;
			}

			java.util.Set<UUID> knew = new java.util.HashSet<>();
			for (DrifterEntity reader : level.getEntitiesOfClass(DrifterEntity.class, victim.getBoundingBox().inflate(READ), d -> d.isAlive() && d != victim)) {
				boolean saw = reader.distanceTo(victim) <= SEEN && reader.hasLineOfSight(victim);
				if (drifter) {
					knew.add(reader.getUUID());
				}

				if (saw && player) {
					playerDeaths.put(reader.getUUID() + "|" + victim.getUUID(), death);
				}

				// A monster's kill it saw close by: a dangerous place.
				if (saw && monster && reader.distanceTo(victim) <= 24.0) {
					Dangers.sawKilled(reader, victim.blockPosition());
				}
			}

			if (drifter) {
				// (Its own group, wherever they are, hear of it.)
				var group = com.dinglebob.drifter.world.DrifterGroups.of(victim.getUUID());
				if (group != null) {
					knew.addAll(group.members);
				}

				byName.put(name.toLowerCase(Locale.ROOT), death);
				knownBy.put(name.toLowerCase(Locale.ROOT), knew);
			}

			if (level.getServer().getTickCount() % 200 == 0) {
				byName.values().removeIf(old -> now - old.at() > REMEMBERED);
				knownBy.keySet().retainAll(byName.keySet());
				playerDeaths.values().removeIf(old -> now - old.at() > REMEMBERED);
			}
		});
	}

	/**
	 * A Drifter's death this one knows of, whose name (or a good part of it) is in this message, and who isn't alive near
	 * now (a new one by that name: no); or null.
	 */
	@Nullable
	public static Death named(DrifterEntity knower, String text) {
		String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", " ") + " ";
		long now = knower.level().getGameTime();
		Death best = null;
		for (Map.Entry<String, Death> entry : byName.entrySet()) {
			Death death = entry.getValue();
			java.util.Set<UUID> knew = knownBy.get(entry.getKey());
			if (now - death.at() > REMEMBERED || knew == null || !knew.contains(knower.getUUID())
				|| !com.dinglebob.drifter.social.SocialWatcher.called(words, death.who())) {
				continue;
			}

			if (best == null || death.who().length() > best.who().length()) {
				best = death;
			}
		}

		if (best != null && knower.level() instanceof ServerLevel level && level.getEntity(best.id()) instanceof LivingEntity back && back.isAlive()) {
			// (It came back: respawned, alive again.)
			return null;
		}

		return best;
	}

	/** Where this player last died, if this Drifter saw it (and not long ago); or null. */
	@Nullable
	public static Death sawPlayerDie(DrifterEntity drifter, ServerPlayer player) {
		Death death = playerDeaths.get(drifter.getUUID() + "|" + player.getUUID());
		return death != null && drifter.level().getGameTime() - death.at() <= REMEMBERED ? death : null;
	}

	/** "this morning", "yesterday", "a few days ago". */
	public static String when(long at, long now) {
		long ago = now - at;
		return ago < 20L * 60 * 3 ? "just now" : ago < 24000L / 2 ? "earlier today" : ago < 24000L * 2 ? "yesterday" : "a few days ago";
	}
}
