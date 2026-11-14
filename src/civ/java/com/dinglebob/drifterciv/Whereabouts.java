package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.SocialWatcher;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where someone is (the user, 2026-11-14, of a picture of their chat, "do you know where the shopkeeper is?": "would love to
 * be able to do this", and for word to carry, "could be nice to ask around"; "same with asking where important people are
 * aswell, like a smith, or a nearby guard"): asked of anyone in a town, by their work ("the shopkeeper", "the smith", "a
 * guard", "the leader") or their name. What it says is what it knows: where it saw them, if lately (perception), else where
 * they'd be (a town's people know each other's houses and work: the stall, the smithy, their home; at night, asleep at
 * home). "Take me to them" then: it walks them there.
 */
final class Whereabouts {
	private Whereabouts() {
	}

	/** Seen this lately (ticks), it's where it saw them. */
	private static final long LATELY = 20L * 60 * 10;
	private static final long JUST = 20L * 60 * 2;
	/** In plain sight this near (blocks): "right there". */
	private static final double THERE = 32.0;
	/** What it last pointed someone to is theirs to be taken to this long (ticks). */
	private static final long POINTED = 20L * 60 * 2;

	/** The words for each work (Town.lines, and the keeper and the leader). */
	private static final Map<String, List<String>> JOBS = new LinkedHashMap<>();

	static {
		JOBS.put("keeper", List.of("shopkeeper", "shop keeper", "storekeeper", "store keeper", "keeper", "merchant", "trader", "vendor", "seller", "stall keeper"));
		JOBS.put("leader", List.of("leader", "mayor", "chief", "boss", "ruler", "king", "queen", "lord", "head of town", "elder"));
		JOBS.put("guard", List.of("guard", "guards", "soldier", "watchman", "sheriff", "knight"));
		JOBS.put("smith", List.of("blacksmith", "smith", "armorer", "armourer", "weaponsmith", "toolsmith"));
		JOBS.put("enchanter", List.of("enchanter", "wizard", "mage"));
		JOBS.put("farmer", List.of("farmer", "farmers"));
		JOBS.put("fisher", List.of("fisher", "fisherman", "fishermen"));
		JOBS.put("fletcher", List.of("fletcher", "bowyer", "arrow maker"));
		JOBS.put("hunter", List.of("hunter"));
		JOBS.put("miner", List.of("miner", "miners"));
		JOBS.put("builder", List.of("builder", "mason", "carpenter"));
		JOBS.put("woodcutter", List.of("woodcutter", "lumberjack", "logger", "woodsman"));
		JOBS.put("cook", List.of("cook", "baker", "chef"));
	}

	/** What it last pointed a player to: where, who, when, by whom. */
	private record Pointed(UUID drifter, BlockPos at, String who, long when) {
	}

	private static final Map<UUID, Pointed> pointed = new HashMap<>();

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> pointed.clear());
	}

	/** Said to a townsperson: where someone is, or (just after) "take me to them" (true: answered). */
	static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		Town town = Market.townOf(drifter);
		MinecraftServer server = drifter.getServer();
		if (town == null || server == null) {
			return false;
		}

		String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", " ") + " ";
		long now = server.overworld().getGameTime();
		Pointed last = pointed.get(player.getUUID());
		if (last != null && now - last.when() < POINTED && last.drifter().equals(drifter.getUUID()) && takeMe(words)) {
			if (TownReputation.of(town, player) == TownReputation.Standing.WARY) {
				return TalkApi.answer(drifter, player, "town.talk.tour.no", null);
			}

			String who = last.who();
			BlockPos at = last.at();
			pointed.remove(player.getUUID());
			return TalkApi.answer(drifter, player, "town.where.take", () -> TalkApi.leadTo(drifter, player, at, "town.where.arrive", who), "who", who);
		}

		boolean asks = words.contains(" where ") || words.contains(" wheres ") || words.contains(" whereabouts ") || words.contains(" seen ")
			|| words.contains(" find ") || words.contains(" looking for ") || words.contains(" lookin for ") || words.contains(" wher ") || words.contains(" whers ");
		if (!asks) {
			return false;
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		String job = null;
		for (Map.Entry<String, List<String>> entry : JOBS.entrySet()) {
			for (String word : entry.getValue()) {
				if (words.contains(" " + word + " ") || words.contains(" " + word + "s ")) {
					job = entry.getKey();
					break;
				}
			}

			if (job != null) {
				break;
			}
		}

		// Or by name: one of its town (the longest name heard).
		UUID named = null;
		String namedName = null;
		if (group != null) {
			for (Map.Entry<UUID, String> entry : group.names.entrySet()) {
				if (SocialWatcher.called(words, entry.getValue()) && (namedName == null || entry.getValue().length() > namedName.length())) {
					named = entry.getKey();
					namedName = entry.getValue();
				}
			}
		}

		if (job == null && named == null) {
			return false;
		}

		TownReputation.Standing standing = TownReputation.of(town, player);
		if (standing == TownReputation.Standing.WANTED || standing == TownReputation.Standing.UNWELCOME) {
			return TalkApi.answer(drifter, player, "town.talk.unwelcome", null);
		}

		List<UUID> candidates = new ArrayList<>();
		if (named != null) {
			candidates.add(named);
		} else if (job.equals("keeper")) {
			if (town.keeper != null) {
				candidates.add(town.keeper);
			}
		} else if (job.equals("leader")) {
			if (town.leader != null) {
				candidates.add(town.leader);
			}
		} else {
			for (Map.Entry<UUID, String> entry : town.lines.entrySet()) {
				if (entry.getValue().equals(job)) {
					candidates.add(entry.getKey());
				}
			}
		}

		String jobWord = job == null ? "" : JOBS.get(job).get(0).equals("shopkeeper") ? "shopkeeper" : job;
		if (candidates.isEmpty()) {
			return TalkApi.answer(drifter, player, "town.where.nojob", null, "job", jobWord);
		}

		// The one it knows best where they are: in sight, else seen latest.
		UUID best = null;
		long bestSeen = Long.MIN_VALUE;
		for (UUID id : candidates) {
			long seen = seen(drifter, server, id, now);
			if (best == null || seen > bestSeen) {
				best = id;
				bestSeen = seen;
			}
		}

		String who = group != null && group.names.containsKey(best) ? group.names.get(best) : Diplomacy.name(town, best);
		if (best.equals(drifter.getUUID())) {
			return TalkApi.answer(drifter, player, "town.where.me", null, "job", jobWord);
		}

		DrifterEntity one = TownFounding.loaded(server, best);
		Awareness sight = one == null ? null : drifter.getSenses().awarenessOf(one);
		if (one != null && sight != null && sight.visibleNow && one.distanceTo(drifter) <= THERE) {
			remember(player, drifter, one.blockPosition(), who, now);
			return TalkApi.answer(drifter, player, "town.where.here", null, "who", who, "way", way(player, one.blockPosition()), "far",
				far(player, one.blockPosition()));
		}

		Relationship r = drifter.getBonds().get(best);
		if (r != null && r.lastSeenPos != null && r.lastSeenAt >= 0 && now - r.lastSeenAt <= LATELY) {
			remember(player, drifter, r.lastSeenPos, who, now);
			return TalkApi.answer(drifter, player, "town.where.seen", null, "who", who, "way", way(player, r.lastSeenPos), "far", far(player, r.lastSeenPos),
				"when", now - r.lastSeenAt <= JUST ? "just now" : "a few minutes ago");
		}

		// Where they'd be: their work, their home (at night, asleep there).
		String line = town.lines.getOrDefault(best, "");
		boolean night = (server.overworld().getDayTime() % 24000L) >= 13000L;
		BlockPos home = home(town, best);
		BlockPos at = null;
		String place = null;
		if (!night) {
			if (best.equals(town.keeper) && town.stallBuilt && town.stallPlot != null) {
				at = middle(town.stallPlot);
				place = "the stall";
			} else if ((line.equals("smith") || line.equals("enchanter")) && home != null) {
				at = home;
				place = line.equals("smith") ? "the smithy" : "their house";
			} else if (line.equals("miner") && town.mine != null) {
				at = town.mine;
				place = "the mine";
			} else if (line.equals("farmer") && !town.fields.isEmpty()) {
				at = town.fields.get(0);
				place = "the fields";
			} else if (line.equals("guard")) {
				// (Found in testing: a town with no plaza sent them to a guard's house by day.)
				at = town.plaza.isEmpty() ? town.center : middle(town.plaza.get(0));
				place = town.plaza.isEmpty() ? "about the town, on their rounds" : "the plaza, on their rounds";
			}
		}

		if (at == null && home != null) {
			at = home;
			place = night ? "home, asleep most likely" : "their house";
		}

		if (at == null) {
			return TalkApi.answer(drifter, player, "town.where.unknown", null, "who", who);
		}

		remember(player, drifter, at, who, now);
		return TalkApi.answer(drifter, player, night ? "town.where.night" : "town.where.usual", null, "who", who, "place", place, "way", way(player, at), "far",
			far(player, at));
	}

	/** "take me to them", "show me", "lead the way"... */
	private static boolean takeMe(String words) {
		boolean take = words.contains(" take me ") || words.contains(" show me ") || words.contains(" lead me ") || words.contains(" bring me ")
			|| words.contains(" lead the way ") || words.contains(" walk me ") || words.contains(" can you take ") || words.contains(" show the way ");
		return take && (words.contains(" them ") || words.contains(" him ") || words.contains(" her ") || words.contains(" there ") || words.contains(" the way "));
	}

	/** When it last knew where they were (in sight now: now), or very long ago. */
	private static long seen(DrifterEntity drifter, MinecraftServer server, UUID id, long now) {
		DrifterEntity one = TownFounding.loaded(server, id);
		Awareness sight = one == null ? null : drifter.getSenses().awarenessOf(one);
		if (sight != null && sight.visibleNow) {
			return now;
		}

		Relationship r = drifter.getBonds().get(id);
		return r == null || r.lastSeenAt < 0 ? Long.MIN_VALUE / 2 : r.lastSeenAt;
	}

	private static void remember(ServerPlayer player, DrifterEntity drifter, BlockPos at, String who, long now) {
		pointed.put(player.getUUID(), new Pointed(drifter.getUUID(), at, who, now));
	}

	@Nullable
	private static BlockPos home(Town town, UUID id) {
		for (Town.Plot plot : town.plots) {
			if (plot.built && (id.equals(plot.owner) || plot.residents.contains(id))) {
				return middle(plot);
			}
		}

		return null;
	}

	private static BlockPos middle(Town.Plot plot) {
		return plot.origin.offset(plot.sizeX / 2, 0, plot.sizeZ / 2);
	}

	private static String way(ServerPlayer player, BlockPos to) {
		double dx = to.getX() + 0.5 - player.getX();
		double dz = to.getZ() + 0.5 - player.getZ();
		String[] ways = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		return ways[Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0), 8)];
	}

	private static String far(ServerPlayer player, BlockPos to) {
		double d = Math.sqrt(player.blockPosition().distSqr(to));
		return d < 12 ? "close by" : d < 40 ? "not far" : d < 100 ? "a fair walk" : "a long way";
	}
}
