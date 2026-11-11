package com.dinglebob.drifterciv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What a town thinks of a player (milestone 23 D1; the user, 2026-11-06: "only d1 and d5"): from how much its people trust
 * them (those who know them), the law they've broken there, and what it's heard from the towns round it (word spreads,
 * a day at a time). Welcome, neutral, wary, unwelcome, wanted. It shows: its stall's prices (dearer the less they like
 * you; none for the wanted), someone says something as you come into town, a guard keeps an eye on you while you're
 * wary or worse, and `/drifterciv town` says where you stand.
 */
final class TownReputation {
	private TownReputation() {
	}

	enum Standing {
		WELCOME("welcome", 0.9), NEUTRAL("neutral", 1.0), WARY("wary", 1.15), UNWELCOME("unwelcome", 1.4), WANTED("wanted", -1);

		final String word;
		/** What its stall's prices are for them (times; under 0: it won't sell to them). */
		final double prices;

		Standing(String word, double prices) {
			this.word = word;
			this.prices = prices;
		}
	}

	/** How much a town takes of what the towns round it think (word spread). */
	private static final double HEARD_SHARE = 0.3;
	/** Each day, a town's word moves the towns that know it this much of the way. */
	private static final double SPREADS = 0.2;
	/** Towns this near each other hear of each other's word. */
	private static final double WORD_REACH = 1500.0;

	/** Each town's last standing of each player (worked out each check, for players online). */
	private static final Map<UUID, Map<UUID, Standing>> known = new HashMap<>();
	/** Players in each town now (for the word as they come in), and when someone last said something to them there. */
	private static final Map<UUID, java.util.Set<UUID>> inTown = new HashMap<>();
	private static final Map<String, Long> greeted = new HashMap<>();

	/** Its opinion of a player: -100 to 100. */
	static double opinion(MinecraftServer server, Town town, UUID player) {
		return opinion(server, town, player, true);
	}

	/**
	 * {@code citizen}: with what being one of its nation's own (or its enemy's) adds ({@link Citizens#bonus}). Left out of
	 * the word that spreads to other towns: each adds its own, and passed on it grew by itself, day after day.
	 */
	private static double opinion(MinecraftServer server, Town town, UUID player, boolean citizen) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		double sum = 0;
		int knowing = 0;
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				double trust = member == null ? 0.0 : DrifterApi.trustInPlayer(member, player);
				if (trust != 0.0) {
					sum += trust;
					knowing++;
				}
			}
		}

		double heard = town.heard.getOrDefault(player, 0.0);
		double own = knowing == 0 ? heard : (1 - HEARD_SHARE) * sum / knowing + HEARD_SHARE * heard;
		// (One of its nation's own, or of a nation it's at war with: where they start from. Citizens.)
		if (citizen) {
			own += Citizens.bonus(server, town, player);
		}

		Town.Criminal criminal = town.crimes.get(player);
		if (criminal != null) {
			own -= 15.0 * criminal.offences;
			if (criminal.exiledUntil > 0) {
				own = -100;
			}
		}

		return Math.max(-100, Math.min(100, own));
	}

	static Standing standing(MinecraftServer server, Town town, UUID player) {
		Town.Criminal criminal = town.crimes.get(player);
		double opinion = opinion(server, town, player);
		if (criminal != null && criminal.exiledUntil > 0 || opinion <= -60) {
			return Standing.WANTED;
		}

		return opinion < -25 ? Standing.UNWELCOME : opinion < -5 ? Standing.WARY : opinion > 20 ? Standing.WELCOME : Standing.NEUTRAL;
	}

	/** Its standing of this player as last worked out (neutral if not yet). */
	static Standing of(Town town, @Nullable Player player) {
		if (player == null) {
			return Standing.NEUTRAL;
		}

		return known.getOrDefault(town.id, Map.of()).getOrDefault(player.getUUID(), Standing.NEUTRAL);
	}

	/** Each check: its standing of each player online; one coming in, someone says something; the word, once a day. */
	static void check(MinecraftServer server, Town town) {
		Map<UUID, Standing> mine = known.computeIfAbsent(town.id, k -> new HashMap<>());
		java.util.Set<UUID> inside = inTown.computeIfAbsent(town.id, k -> new java.util.HashSet<>());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Standing standing = standing(server, town, player.getUUID());
			mine.put(player.getUUID(), standing);
			// (Not while the world's built before it's played: they can't see or hear yet.)
			boolean here = !WorldBuild.building && player.level().dimension().location().toString().equals(town.dimension)
				&& Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) < town.land();
			if (here && inside.add(player.getUUID())) {
				arrived(server, town, player, standing);
			} else if (!here) {
				inside.remove(player.getUUID());
			}
		}

		long day = Chronicle.day(server);
		if (town.wordDay < day) {
			town.wordDay = day;
			spread(server, town);
		}
	}

	/** A player came into town: someone who can see them says what the town thinks (once in ten minutes at most). */
	private static void arrived(MinecraftServer server, Town town, ServerPlayer player, Standing standing) {
		String key = town.id + "/" + player.getUUID();
		long now = server.overworld().getGameTime();
		if (now - greeted.getOrDefault(key, Long.MIN_VALUE / 2) < 20L * 60 * 10 || standing == Standing.NEUTRAL) {
			return;
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		DrifterEntity speaker = null;
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && member.distanceTo(player) < 24.0 && member.hasLineOfSight(player)
					&& (speaker == null || member.distanceTo(player) < speaker.distanceTo(player))) {
					speaker = member;
				}
			}
		}

		if (speaker == null) {
			return;
		}

		greeted.put(key, now);
		// (Their Minecraft name in: said as what they chose to be called, their pronouns filled in. Lines, Citizens.)
		String name = player.getGameProfile().getName();
		String line = switch (standing) {
			case WELCOME -> Citizens.ownTown(town, player.getUUID())
				? CivLines.pick(speaker, "town.greet.home", "welcome home " + Citizens.called(player), "player", name)
				: CivLines.pick(speaker, "town.greet.welcome", "hey " + name + "! welcome back", "player", name);
			case WARY -> CivLines.pick(speaker, "town.greet.wary", "hm. " + name + " again", "player", name);
			case UNWELCOME -> CivLines.pick(speaker, "town.greet.unwelcome", "you again? we dont want trouble", "player", name);
			case WANTED -> CivLines.pick(speaker, "town.greet.wanted", "its " + name + "! guards!", "player", name);
			default -> "hey";
		};
		com.dinglebob.drifter.social.ChatLines.speak(speaker, line);
	}

	/** The word about each player it has an opinion of, passed on to the towns round it (a fifth of the way). */
	private static void spread(MinecraftServer server, Town town) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			double opinion = opinion(server, town, player.getUUID(), false);
			if (Math.abs(opinion) < 10) {
				continue;
			}

			for (Town other : Towns.all()) {
				if (other != town && other.dimension.equals(town.dimension) && Math.hypot(other.center.getX() - town.center.getX(),
					other.center.getZ() - town.center.getZ()) < WORD_REACH) {
					double was = other.heard.getOrDefault(player.getUUID(), 0.0);
					other.heard.put(player.getUUID(), was + (opinion - was) * SPREADS);
				}
			}
		}
	}

	/** The player its guard should keep an eye on (wary or worse, in the town; the nearest guard to each), or null. */
	@Nullable
	static Player watched(Town town, DrifterEntity guard) {
		MinecraftServer server = guard.getServer();
		java.util.Set<UUID> inside = inTown.get(town.id);
		if (server == null || inside == null || inside.isEmpty()) {
			return null;
		}

		for (UUID id : inside) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || player.isSpectator() || of(town, player).ordinal() < Standing.WARY.ordinal()) {
				continue;
			}

			// (The guard nearest them keeps the eye on them.)
			DrifterEntity nearest = null;
			for (Map.Entry<UUID, String> line : town.lines.entrySet()) {
				DrifterEntity other = line.getValue().equals("guard") ? TownFounding.loaded(server, line.getKey()) : null;
				if (other != null && (nearest == null || other.distanceTo(player) < nearest.distanceTo(player))) {
					nearest = other;
				}
			}

			if (nearest == guard) {
				return player;
			}
		}

		return null;
	}

	/** For /drifterciv town: where this player stands. */
	static String describe(MinecraftServer server, Town town, Player player) {
		Standing standing = standing(server, town, player.getUUID());
		int opinion = (int)Math.round(opinion(server, town, player.getUUID()));
		return "You: " + standing.word + " (" + (opinion >= 0 ? "+" : "") + opinion + ")";
	}
}
