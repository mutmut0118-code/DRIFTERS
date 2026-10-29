package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The little rituals of Minecraft chat (the user's guidebook "How Minecraft Players Chat", 2026-10-23: "the game is a
 * speaker... a lot of SMP chat is people reacting to a sentence the game wrote"): Drifters about answer a player's "hi"
 * ("o/"), "gn", "ty" ("np") and "cya"; say "wb" when someone they know joins and "welcome" to someone new, "cya" or "gn"
 * when they leave; "rip" (or "lmao" for a silly death) at a player's death message, "rip" or "o7" for a Drifter, harder
 * for a friend; "gg" or "W" for an advancement. A moment later, as someone types it; one or two of them, the chattier
 * more often, under the chat lines' setting and rate limits.
 */
public final class ChatReplies {
	private ChatReplies() {
	}

	/** How far from a player Drifters answer their chat, or their leaving. */
	private static final double HEARS = 64.0;
	/** How far off Drifters react to a death or an advancement: as far as they're heard (only players nearby see it). */
	private static double reads() {
		return DrifterConfig.chatRange();
	}
	/** Most Drifters answering one thing. */
	private static final int MOST = 2;

	/**
	 * A line it's to say from {@code at} on: a key's line (or {@code text} as it is); {@code afterFight}: not while it's in
	 * a fight (it waits, a couple of minutes at most); {@code urgent}: past the chat limits (said to someone's face, or
	 * something that can't wait).
	 */
	private record Pending(UUID drifter, String key, long at, Object[] vars, @org.jetbrains.annotations.Nullable String text, boolean afterFight,
		boolean urgent) {
		Pending(UUID drifter, String key, long at, Object[] vars) {
			this(drifter, key, at, vars, null, false, false);
		}
	}

	/** How long a line waits for a fight to be over before it's not worth saying. */
	private static final long FIGHT_WAIT = 20L * 60 * 2;

	/** It says this once it's out of the fight it's in (now, if it isn't in one): a death acknowledged after the fighting. */
	public static void afterFight(DrifterEntity drifter, @org.jetbrains.annotations.Nullable String text, boolean urgent) {
		if (text != null && !text.isEmpty()) {
			pending.add(new Pending(drifter.getUUID(), "", drifter.level().getGameTime() + 10, new Object[0], text, true, urgent));
		}
	}

	/** On the side of whoever killed it: the killer, or one of its group (no "rip" from them; the user, 2026-10-29). */
	public static boolean killersSide(DrifterEntity reader, @org.jetbrains.annotations.Nullable DrifterEntity killer) {
		if (killer == null) {
			return false;
		}

		var group = com.dinglebob.drifter.world.DrifterGroups.of(reader.getUUID());
		return killer == reader || group != null && com.dinglebob.drifter.world.DrifterGroups.of(killer.getUUID()) == group;
	}

	private static final List<Pending> pending = new ArrayList<>();

	public static void register() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			// (Answering a Drifter that asked them something: that Drifter answers, not the room.)
			if (!SocialWatcher.isRealPlayer(sender) || com.dinglebob.drifter.encounter.Encounters.active(sender.getUUID()) != null) {
				return;
			}

			String said = message.signedContent();
			// Said to a Drifter (asked it something, by name or to its face): it answers that (chat phase 2).
			if (com.dinglebob.drifter.social.talk.PlayerTalk.heard(sender, said)) {
				return;
			}

			String intent = Lines.answer(said, "night", "bye", "thanks", "greeting");
			if (intent == null) {
				return;
			}

			ServerLevel level = sender.serverLevel();
			String name = sender.getGameProfile().getName();
			switch (intent) {
				case "greeting" -> answer(level, sender, near(level, sender, HEARS, true), "greet_back", MOST, 25, name);
				case "night" -> answer(level, sender, near(level, sender, HEARS, true), "night_back", MOST, 25, name);
				case "bye" -> answer(level, sender, near(level, sender, HEARS, true), "bye_back", MOST, 25, name);
				// Thanks: the nearest who doesn't mind them says np (most likely the one who helped).
				case "thanks" -> answer(level, sender, near(level, sender, 16.0, true).stream().limit(1).toList(), "you_welcome", 1, 15, name);
				default -> {
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			// (Once the Drifters round them have loaded.)
			later(server, player.getUUID(), 100);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			ServerLevel level = player.serverLevel();
			long time = level.getDayTime() % 24000L;
			answer(level, player, near(level, player, HEARS, true), time >= 13000L ? "bye_night" : "bye_back", MOST, 20,
				player.getGameProfile().getName());
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim.level() instanceof ServerLevel level)) {
				return;
			}

			if (victim instanceof ServerPlayer player && SocialWatcher.isRealPlayer(player)) {
				// Its own kill says its own "gg"; the rest react to the death message.
				List<DrifterEntity> readers = near(level, player, reads(), false).stream()
					.filter(drifter -> source.getEntity() != drifter && com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity()) != drifter).toList();
				answer(level, player, readers, silly(source) ? "rip_funny" : "rip", MOST, 30, player.getGameProfile().getName());
				if (silly(source)) {
					joke(near(level, player, 32.0, false), player.getGameProfile().getName(), source);
				}
			} else if (victim instanceof DrifterEntity dead && !dead.isTemporary()) {
				String who = dead.getName().getString();
				if (silly(source)) {
					joke(near(level, dead, 32.0, false).stream().filter(drifter -> drifter != dead).toList(), who, source);
				}

				// Who killed it (a Drifter): its side doesn't mourn it (the user, 2026-10-29: "even the enemies mourned the deaths
				// of the people they killed? doesnt make sense"); everyone says what they say once the fight's over.
				DrifterEntity killer = source.getEntity() instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
				if (killer == null) {
					var credit = dead.getKillCredit();
					killer = credit instanceof DrifterEntity d ? d : com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(credit);
				}

				var deadGroup = com.dinglebob.drifter.world.DrifterGroups.of(dead.getUUID());
				if (deadGroup == null && dead.groupAtDeath() != null) {
					deadGroup = com.dinglebob.drifter.world.DrifterGroups.byId(dead.groupAtDeath());
				}

				boolean enemy = killer != null && (deadGroup == null || com.dinglebob.drifter.world.DrifterGroups.of(killer.getUUID()) != deadGroup);
				for (DrifterEntity reader : near(level, dead, reads(), false)) {
					if (reader == dead) {
						continue;
					}

					long at = level.getGameTime() + 20 + reader.getRandom().nextInt(40);
					if (enemy && killersSide(reader, killer)) {
						// Its own kill, or one of its side's: now and then a word for it, after.
						if (reader.getRandom().nextDouble() < (reader == killer ? 0.6 : 0.2) * chattiness(reader)) {
							pending.add(new Pending(reader.getUUID(), reader == killer ? "war.kill" : "war.kill_seen", at, new Object[] {"who", who}, null, true,
								false));
						}

						continue;
					}

					var bond = reader.getBonds().get(dead.getUUID());
					boolean friend = bond != null && bond.trust >= DrifterBonds.FRIEND;
					if (friend || reader.getRandom().nextDouble() < 0.25 * chattiness(reader)) {
						pending.add(new Pending(reader.getUUID(), friend ? "rip_friend" : "rip_drifter", at, new Object[] {"who", who}, null, true, false));
					}
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(ChatReplies::tick);
	}

	/** A player made an advancement that shows in chat: one or two Drifters about say gg (PlayerAdvancementsMixin). */
	public static void advancement(ServerPlayer player) {
		if (SocialWatcher.isRealPlayer(player)) {
			answer(player.serverLevel(), player, near(player.serverLevel(), player, reads(), true), "congrats", MOST, 30, player.getGameProfile().getName());
		}
	}

	/** A death that's funny rather than sad: lava, a fall, drowning, a cactus, a berry bush, a wall, starving, its own fire. */
	private static boolean silly(net.minecraft.world.damagesource.DamageSource source) {
		return SILLY.contains(type(source));
	}

	private static String type(net.minecraft.world.damagesource.DamageSource source) {
		return source.typeHolder().unwrapKey().map(key -> key.location().getPath()).orElse("");
	}

	private static final java.util.Set<String> SILLY = java.util.Set.of("lava", "fall", "drown", "cactus", "sweet_berry_bush", "in_wall", "fly_into_wall", "starve", "hot_floor", "on_fire", "in_fire",
			"freeze", "stalagmite", "falling_stalactite", "falling_block", "falling_anvil", "cramming");

	/** Everyone who saw a silly death has a running joke for a week (chat phase 3). */
	private static void joke(List<DrifterEntity> witnesses, String who, net.minecraft.world.damagesource.DamageSource source) {
		for (DrifterEntity witness : witnesses) {
			com.dinglebob.drifter.social.talk.Diary.joke(witness, who, type(source));
		}
	}

	/** Drifters near this one (or player), the nearest first; {@code friendly}: only those who don't mind a player. */
	private static List<DrifterEntity> near(ServerLevel level, LivingEntity center, double range, boolean friendly) {
		List<DrifterEntity> drifters = new ArrayList<>(level.getEntitiesOfClass(DrifterEntity.class, center.getBoundingBox().inflate(range),
			drifter -> drifter.isAlive() && !drifter.isTemporary() && drifter.encounter() == null));
		if (friendly && center instanceof ServerPlayer player) {
			drifters.removeIf(drifter -> Mode.atOdds(drifter, player));
		}

		drifters.sort(java.util.Comparator.comparingDouble(drifter -> drifter.distanceToSqr(center)));
		return drifters;
	}

	/** How likely it is to bother answering at all: the chatty mostly do, the quiet rarely. */
	private static double chattiness(DrifterEntity drifter) {
		return DrifterConfig.chatty(0.15 + 0.8 * drifter.getPersonality().get(Trait.SOCIABILITY));
	}

	/** Up to {@code most} of these answer, each by its chattiness, after a moment's typing. */
	private static void answer(ServerLevel level, @Nullable ServerPlayer player, List<DrifterEntity> candidates, String key, int most, int delay, String name) {
		if (!DrifterConfig.chatLines()) {
			return;
		}

		int answered = 0;
		long now = level.getGameTime();
		for (DrifterEntity drifter : candidates) {
			if (answered >= most) {
				break;
			}

			if (drifter.getRandom().nextDouble() < chattiness(drifter)) {
				answered++;
				queue(drifter, key, now + delay + answered * 25L + drifter.getRandom().nextInt(30), "player", name);
			}
		}
	}

	private static void queue(DrifterEntity drifter, String key, long at, Object... vars) {
		pending.add(new Pending(drifter.getUUID(), key, at, vars));
	}

	/** A player who joined: the Drifters round them say wb (or welcome, to someone none of them knows). */
	private static void later(MinecraftServer server, UUID player, int ticks) {
		joins.add(new Pending(player, "join", server.overworld().getGameTime() + ticks, new Object[0]));
	}

	private static final List<Pending> joins = new ArrayList<>();

	private static void tick(MinecraftServer server) {
		if (pending.isEmpty() && joins.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		for (Iterator<Pending> it = joins.iterator(); it.hasNext();) {
			Pending join = it.next();
			if (now < join.at()) {
				continue;
			}

			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(join.drifter());
			if (player != null && SocialWatcher.isRealPlayer(player)) {
				List<DrifterEntity> round = near(player.serverLevel(), player, HEARS, true);
				boolean known = round.stream().anyMatch(drifter -> drifter.getSocial().get(player.getUUID()) != null);
				answer(player.serverLevel(), player, round, known ? "welcome_back" : "welcome_new", MOST, 10, player.getGameProfile().getName());
			}
		}

		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending line = it.next();
			if (now < line.at()) {
				continue;
			}

			DrifterEntity drifter = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(line.drifter()) instanceof DrifterEntity one && one.isAlive()) {
					drifter = one;
					break;
				}
			}

			// (Still fighting: later, or not at all once it's been too long.)
			if (drifter != null && line.afterFight() && drifter.inAFight() && now - line.at() < FIGHT_WAIT) {
				continue;
			}

			it.remove();
			if (drifter == null || line.afterFight() && now - line.at() >= FIGHT_WAIT) {
				continue;
			}

			String text = line.text() != null ? line.text() : Lines.pick(drifter, line.key(), line.vars());
			if (line.urgent()) {
				ChatLines.speak(drifter, text);
			} else {
				ChatLines.sayLine(drifter, text);
			}
		}
	}
}
