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
	/** How far off Drifters react to a death or an advancement in chat (they read chat, as players do). */
	private static final double READS = 160.0;
	/** Most Drifters answering one thing. */
	private static final int MOST = 2;

	private record Pending(UUID drifter, String key, long at, Object[] vars) {
	}

	private static final List<Pending> pending = new ArrayList<>();

	public static void register() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			// (Answering a Drifter that asked them something: that Drifter answers, not the room.)
			if (!SocialWatcher.isRealPlayer(sender) || com.dinglebob.drifter.encounter.Encounters.active(sender.getUUID()) != null) {
				return;
			}

			String said = message.signedContent();
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
				List<DrifterEntity> readers = near(level, player, READS, false).stream()
					.filter(drifter -> source.getEntity() != drifter && com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity()) != drifter).toList();
				answer(level, player, readers, silly(source) ? "rip_funny" : "rip", MOST, 30, player.getGameProfile().getName());
			} else if (victim instanceof DrifterEntity dead && !dead.isTemporary()) {
				String who = dead.getName().getString();
				for (DrifterEntity reader : near(level, dead, READS, false)) {
					if (reader == dead) {
						continue;
					}

					var bond = reader.getBonds().get(dead.getUUID());
					boolean friend = bond != null && bond.trust >= DrifterBonds.FRIEND;
					if (friend || reader.getRandom().nextDouble() < 0.25 * chattiness(reader)) {
						queue(reader, friend ? "rip_friend" : "rip_drifter", level.getGameTime() + 20 + reader.getRandom().nextInt(40), "who", who);
					}
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(ChatReplies::tick);
	}

	/** A player made an advancement that shows in chat: one or two Drifters about say gg (PlayerAdvancementsMixin). */
	public static void advancement(ServerPlayer player) {
		if (SocialWatcher.isRealPlayer(player)) {
			answer(player.serverLevel(), player, near(player.serverLevel(), player, READS, false), "congrats", MOST, 30, player.getGameProfile().getName());
		}
	}

	/** A death that's funny rather than sad: lava, a fall, drowning, a cactus, a berry bush, a wall, starving, its own fire. */
	private static boolean silly(net.minecraft.world.damagesource.DamageSource source) {
		String type = source.typeHolder().unwrapKey().map(key -> key.location().getPath()).orElse("");
		return java.util.Set.of("lava", "fall", "drown", "cactus", "sweet_berry_bush", "in_wall", "fly_into_wall", "starve", "hot_floor", "on_fire", "in_fire",
			"freeze", "stalagmite", "falling_stalactite", "falling_block", "falling_anvil", "cramming").contains(type);
	}

	/** Drifters near this one (or player), the nearest first; {@code friendly}: only those who don't mind a player. */
	private static List<DrifterEntity> near(ServerLevel level, LivingEntity center, double range, boolean friendly) {
		List<DrifterEntity> drifters = new ArrayList<>(level.getEntitiesOfClass(DrifterEntity.class, center.getBoundingBox().inflate(range),
			drifter -> drifter.isAlive() && !drifter.isTemporary() && drifter.encounter() == null));
		if (friendly && center instanceof ServerPlayer player) {
			drifters.removeIf(drifter -> Mode.toward(drifter, player).hostile());
		}

		drifters.sort(java.util.Comparator.comparingDouble(drifter -> drifter.distanceToSqr(center)));
		return drifters;
	}

	/** How likely it is to bother answering at all: the chatty mostly do, the quiet rarely. */
	private static double chattiness(DrifterEntity drifter) {
		return 0.15 + 0.8 * drifter.getPersonality().get(Trait.SOCIABILITY);
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

			it.remove();
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(line.drifter()) instanceof DrifterEntity drifter && drifter.isAlive()) {
					ChatLines.sayLine(drifter, Lines.pick(drifter, line.key(), line.vars()));
					break;
				}
			}
		}
	}
}
