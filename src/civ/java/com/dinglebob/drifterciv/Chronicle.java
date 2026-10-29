package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.SocialEvent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's chronicle (docs/CIVILIZATION.md, "History"): what happened, day by day, as those civilization videos tell
 * it. "Day 3: Ann and Bo teamed up as Ann's Crew. Day 5: Cy was slain by a zombie." Deaths, homes, groups, houses
 * built, feuds and wars, a player's big deeds, and each day's count of trades and news passed on. Written as it happens
 * from Drifter's events; read with `/drifterciv chronicle` or as a book; each morning the day before is told in chat (a
 * setting). The world's narrator: nothing here is anything a Drifter knows.
 */
public final class Chronicle {
	private Chronicle() {
	}

	/** One line of history: the world's day, the game time, what kind of thing, and what happened. */
	public record Entry(long day, long time, String kind, String text) {
	}

	/** Most entries kept (the oldest go first). */
	private static final int MOST = 5000;
	/** Lines of one day told each morning before "and N more". */
	private static final int MORNING_LINES = 8;
	private static final int SAVE_VERSION = 1;

	private static final List<Entry> entries = new ArrayList<>();
	/** Where each Drifter's home was when last noted (a new one, far from it, is news). */
	private static final Map<UUID, BlockPos> homes = new HashMap<>();
	/** Each member's group and its name, to say which one it left. */
	private static final Map<UUID, UUID> groupOf = new HashMap<>();
	private static final Map<UUID, String> groupNames = new HashMap<>();
	/** Today's trades and news passed on (written down as the day ends). */
	private static int trades;
	private static int news;
	private static long today = -1;
	private static boolean summary = true;
	/** While a town is being made at once (testing): its people's homes and joining aren't news, the founding is. */
	static boolean quiet;
	private static boolean dirty;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Chronicle::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Chronicle::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			entries.clear();
			homes.clear();
			groupOf.clear();
			groupNames.clear();
			today = -1;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				newDay(server);
			}

			if (dirty && server.getTickCount() % 1200 == 0) {
				save(server);
			}
		});

		DrifterEvents.DIED.register((drifter, source, comesBack) -> {
			if (counts(drifter)) {
				// (From the damage itself: the game has forgotten the fight by now.)
				String how = source.getLocalizedDeathMessage(drifter).getString();
				add(drifter.getServer(), "death", how + (comesBack ? "" : " (gone for good)"));
			}
		});
		DrifterEvents.HOME_SET.register((drifter, home) -> {
			if (home == null || !counts(drifter) || drifter.getServer() == null) {
				return;
			}

			BlockPos before = homes.put(drifter.getUUID(), home.center);
			if (before == null || !before.closerThan(home.center, 32.0)) {
				add(drifter.getServer(), "home", name(drifter) + " made a home near " + home.center.getX() + ", " + home.center.getZ());
			}
		});
		DrifterEvents.GROUP_CHANGED.register((member, groupId) -> {
			MinecraftServer server = DrifterCivilizations.server();
			if (server == null) {
				return;
			}

			if (groupId == null) {
				UUID was = groupOf.remove(member);
				String group = was == null ? null : groupNames.get(was);
				if (group != null) {
					add(server, "group", name(member) + " left " + group);
				}

				return;
			}

			var group = DrifterApi.groupById(groupId);
			if (group == null) {
				return;
			}

			groupOf.put(member, groupId);
			boolean known = groupNames.containsKey(groupId);
			groupNames.put(groupId, group.name);
			// (A new group comes in two calls, its leader then the other: told once, on the second.)
			// (Names as the group has them: a new Drifter isn't in the world's directory yet.)
			if (!known && group.members.size() == 2) {
				add(server, "group", group.names.getOrDefault(group.members.get(0), name(group.members.get(0))) + " and "
					+ group.names.getOrDefault(group.members.get(1), name(group.members.get(1))) + " teamed up as " + group.name);
			} else if (known && group.members.size() > 2) {
				add(server, "group", group.names.getOrDefault(member, name(member)) + " joined " + group.name + " (" + group.members.size() + " now)");
			} else if (!known) {
				groupNames.remove(groupId);
			}
		});
		DrifterEvents.BUILT.register((drifter, what, where) -> {
			// (A town's buildings have lines of their own: TownFounding.)
			if (counts(drifter) && !what.startsWith("town:")) {
				add(drifter.getServer(), "built", name(drifter) + " finished " + (what.equals("hut") ? "its hut" : "building its " + what));
			}
		});
		DrifterEvents.FEUD.register((a, b, what) -> {
			MinecraftServer server = DrifterCivilizations.server();
			if (server != null) {
				add(server, "feud", groupName(a) + (what.equals("set off to raid") ? " set off to raid " + groupName(b) : " and " + groupName(b) + " " + what));
			}
		});
		DrifterEvents.PLAYER_DEED.register((drifter, player, deed, trustAfter) -> {
			if (counts(drifter) && big(deed)) {
				String who = name(drifter);
				String what = deed.what.replaceAll("\\bits\\b", who + "'s").replaceAll("\\bit\\b", who);
				add(drifter.getServer(), "deed", player.getGameProfile().getName() + " " + what);
			}
		});
		DrifterEvents.TRADED.register((asker, giver, paid, got) -> {
			trades++;
		});
		DrifterEvents.HEARD_NEWS.register((drifter, heard, from) -> {
			news++;
		});
	}

	/** A player's deed worth a line in the history (a kill is told as the death). */
	private static boolean big(SocialEvent deed) {
		return deed == SocialEvent.SAVED_IT || deed == SocialEvent.NEARLY_KILLED || deed == SocialEvent.CAUGHT_STEALING
			|| deed == SocialEvent.KILLED_PET || deed == SocialEvent.FIRE_NEAR_BASE;
	}

	/** Not one met once (an encounter's), nor a soldier in a battle (they fall by the dozen). */
	private static boolean counts(DrifterEntity drifter) {
		return drifter.getServer() != null && !DrifterApi.temporary(drifter) && !DrifterApi.inBattle(drifter);
	}

	// ----- Writing -----

	/** The world's day (from the overworld's clock): day 1 is the first. */
	public static long day(MinecraftServer server) {
		return server.overworld().getDayTime() / 24000L + 1;
	}

	public static void add(@Nullable MinecraftServer server, String kind, String text) {
		if (server == null || quiet && !kind.equals("town")) {
			return;
		}

		entries.add(new Entry(day(server), server.overworld().getGameTime(), kind, text));
		if (entries.size() > MOST) {
			entries.subList(0, entries.size() - MOST).clear();
		}

		dirty = true;
		DrifterCivilizations.LOGGER.info("[chronicle] Day {}: {}", day(server), text);
	}

	/** A new day: yesterday's counts written down, and yesterday told in chat. */
	private static void newDay(MinecraftServer server) {
		long day = day(server);
		if (today < 0) {
			today = day;
			return;
		}

		if (day == today) {
			return;
		}

		long ended = today;
		today = day;
		if (trades > 0 || news > 0) {
			entries.add(new Entry(ended, server.overworld().getGameTime(), "count", plural(trades, "trade") + " between Drifters, "
				+ plural(news, "piece") + " of news passed on"));
			dirty = true;
		}

		trades = 0;
		news = 0;
		if (!summary || server.getPlayerList().getPlayerCount() == 0) {
			return;
		}

		List<Entry> lines = day(ended);
		if (lines.isEmpty()) {
			return;
		}

		server.getPlayerList().broadcastSystemMessage(Component.literal("Day " + ended + ":").withStyle(ChatFormatting.GOLD), false);
		for (Entry entry : lines.subList(0, Math.min(MORNING_LINES, lines.size()))) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("  " + entry.text()).withStyle(ChatFormatting.GRAY), false);
		}

		if (lines.size() > MORNING_LINES) {
			server.getPlayerList().broadcastSystemMessage(Component.literal("  ...and " + (lines.size() - MORNING_LINES)
				+ " more (/drifterciv chronicle day " + ended + ")").withStyle(ChatFormatting.DARK_GRAY), false);
		}
	}

	private static String plural(int count, String what) {
		return count + " " + what + (count == 1 ? "" : "s");
	}

	// ----- Reading -----

	public static List<Entry> all() {
		return List.copyOf(entries);
	}

	public static List<Entry> day(long day) {
		return entries.stream().filter(entry -> entry.day() == day).toList();
	}

	public static boolean summary() {
		return summary;
	}

	public static void setSummary(boolean on) {
		summary = on;
		dirty = true;
	}

	/** Its name from the world's directory (it may be far away), or its own. */
	static String name(DrifterEntity drifter) {
		return drifter.getName().getString();
	}

	static String name(UUID id) {
		DrifterRecord record = DrifterApi.record(id);
		return record != null ? record.name() : "someone";
	}

	private static String groupName(UUID group) {
		var found = DrifterApi.groupById(group);
		return found != null ? found.name : groupNames.getOrDefault(group, "a group");
	}

	// ----- Saved with the world -----

	private static final class State extends SavedData {
		CompoundTag tag = new CompoundTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.tag = tag;
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.merge(this.tag);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_civ_chronicle");
	}

	private static void load(MinecraftServer server) {
		entries.clear();
		homes.clear();
		groupOf.clear();
		groupNames.clear();
		CompoundTag tag = state(server).tag;
		// Version 1 is the first format.
		summary = !tag.contains("Summary") || tag.getBoolean("Summary");
		for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
			CompoundTag e = (CompoundTag)t;
			entries.add(new Entry(e.getLong("Day"), e.getLong("Time"), e.getString("Kind"), e.getString("Text")));
		}

		for (Tag t : tag.getList("Homes", Tag.TAG_COMPOUND)) {
			CompoundTag h = (CompoundTag)t;
			homes.put(h.getUUID("Id"), BlockPos.of(h.getLong("At")));
		}

		for (Tag t : tag.getList("Groups", Tag.TAG_COMPOUND)) {
			CompoundTag g = (CompoundTag)t;
			groupNames.put(g.getUUID("Id"), g.getString("Name"));
		}

		for (Tag t : tag.getList("Members", Tag.TAG_COMPOUND)) {
			CompoundTag m = (CompoundTag)t;
			groupOf.put(m.getUUID("Id"), m.getUUID("Group"));
		}

		trades = tag.getInt("Trades");
		news = tag.getInt("News");
		today = tag.contains("Today") ? tag.getLong("Today") : -1;
		dirty = false;
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		tag.putBoolean("Summary", summary);
		ListTag list = new ListTag();
		for (Entry entry : entries) {
			CompoundTag e = new CompoundTag();
			e.putLong("Day", entry.day());
			e.putLong("Time", entry.time());
			e.putString("Kind", entry.kind());
			e.putString("Text", entry.text());
			list.add(e);
		}

		tag.put("Entries", list);
		ListTag homeList = new ListTag();
		homes.forEach((id, at) -> {
			CompoundTag h = new CompoundTag();
			h.putUUID("Id", id);
			h.putLong("At", at.asLong());
			homeList.add(h);
		});
		tag.put("Homes", homeList);
		ListTag groupList = new ListTag();
		groupNames.forEach((id, name) -> {
			CompoundTag g = new CompoundTag();
			g.putUUID("Id", id);
			g.putString("Name", name);
			groupList.add(g);
		});
		tag.put("Groups", groupList);
		ListTag memberList = new ListTag();
		groupOf.forEach((id, group) -> {
			CompoundTag m = new CompoundTag();
			m.putUUID("Id", id);
			m.putUUID("Group", group);
			memberList.add(m);
		});
		tag.put("Members", memberList);
		tag.putInt("Trades", trades);
		tag.putInt("News", news);
		tag.putLong("Today", today);
		State state = state(server);
		state.tag = tag;
		state.setDirty();
		dirty = false;
	}

	/** A line for a list: "Day 3: Ann made a home near 120, -40". */
	static String line(Entry entry) {
		return dayName(entry.day()) + ": " + entry.text();
	}

	/** "Day 12", or for a day before the world's first (a town's past: milestone 23 A5), "20 days before day 1". */
	static String dayName(long day) {
		return day >= 1 ? "Day " + day : (1 - day) + (1 - day == 1 ? " day" : " days") + " before day 1";
	}

	/**
	 * Something that happened {@code daysAgo} days before today (a town's past, written when it's made: A5), put in among
	 * the rest by its day.
	 */
	static void addPast(@Nullable MinecraftServer server, String kind, String text, long daysAgo) {
		if (server == null) {
			return;
		}

		long day = day(server) - daysAgo;
		Entry entry = new Entry(day, server.overworld().getGameTime() - daysAgo * 24000L, kind, text);
		int at = entries.size();
		while (at > 0 && entries.get(at - 1).day() > day) {
			at--;
		}

		entries.add(at, entry);
		dirty = true;
		DrifterCivilizations.LOGGER.info("[chronicle] {}: {}", dayName(day), text);
	}
}
