package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.SocialEvent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Friendships between players and Drifters (D2, docs/superpowers/specs/2026-11-10-d2-friends-design.md; the user,
 * 2026-11-10). Four stages, Sims-like: stranger, acquaintance, friend, best friend, reached by trust and time together
 * (Drifter's own: deeds, gifts, talk) and by opinions agreed in talk ({@link FriendTalk}); kept per Drifter and player
 * with the world, so a friend far away is still known. Once reached a stage holds (its trust doesn't drift down past a
 * floor) until it cools (apart too long) or ends (wronged). Its perks: {@link FriendPerks}.
 */
final class Friends {
	private Friends() {
	}

	enum Stage {
		STRANGER("stranger"), ACQUAINTANCE("acquaintance"), FRIEND("friend"), BEST_FRIEND("best friend");

		final String word;

		Stage(String word) {
			this.word = word;
		}
	}

	enum State {
		ACTIVE, COOLED, ENDED, GONE
	}

	/** A favor a friend asked of a player: what, when, by when, and whether they said they'd do it. */
	record Favor(String kind, long asked, long due, boolean promised, int done, @Nullable BlockPos at) {
	}

	/** One Drifter and one player. */
	static final class Bond {
		final UUID drifter;
		final UUID player;
		String drifterName;
		String playerName;
		Stage stage = Stage.STRANGER;
		State state = State.ACTIVE;
		/** When it reached its stage (game time). */
		long since;
		/** When they were last near each other, it seeing them (game time). */
		long lastTogether;
		/** When they turned up after a day or more apart (not saved: for its welcome). */
		long returned = -1;
		/** Time together since it cooled, toward picking up again (ticks). */
		long warming;
		/** The in-game day of {@link #talksToday}: answers to its questions that day (repeat talks count less). */
		long talkDay = -1;
		int talksToday;
		/** Opinions it shared and how they answered: "topic:yes" or "topic:no", newest last. */
		final List<String> shared = new ArrayList<>();
		/** Places it has told them of (BlockPos longs). */
		final List<Long> tipped = new ArrayList<>();
		long lastTip = -1;
		long lastFood = -1;
		long lastWelcome = -1;
		long lastFavor = -1;
		long lastInvite = -1;
		@Nullable
		Favor favor;
		/** They saved its life once. */
		boolean saved;
		/** Why it cooled or ended (for the list). */
		String why = "";
		/** Its town's name when last seen, and where it was. */
		String town = "";
		@Nullable
		BlockPos where;

		Bond(UUID drifter, UUID player) {
			this.drifter = drifter;
			this.player = player;
		}

		boolean active() {
			return this.state == State.ACTIVE;
		}

		/** A friend now (or best friend), not cooled or ended. */
		boolean friend() {
			return this.active() && this.stage.ordinal() >= Stage.FRIEND.ordinal();
		}

		boolean best() {
			return this.active() && this.stage == Stage.BEST_FRIEND;
		}

		int agreed() {
			return (int)this.shared.stream().filter(s -> s.endsWith(":yes")).count();
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Drifter", this.drifter);
			tag.putUUID("Player", this.player);
			tag.putString("DrifterName", this.drifterName == null ? "" : this.drifterName);
			tag.putString("PlayerName", this.playerName == null ? "" : this.playerName);
			tag.putString("Stage", this.stage.name());
			tag.putString("State", this.state.name());
			tag.putLong("Since", this.since);
			tag.putLong("LastTogether", this.lastTogether);
			tag.putLong("Warming", this.warming);
			tag.putLong("TalkDay", this.talkDay);
			tag.putInt("TalksToday", this.talksToday);
			ListTag shared = new ListTag();
			this.shared.forEach(s -> shared.add(StringTag.valueOf(s)));
			tag.put("Shared", shared);
			ListTag tipped = new ListTag();
			this.tipped.forEach(t -> tipped.add(LongTag.valueOf(t)));
			tag.put("Tipped", tipped);
			tag.putLong("LastTip", this.lastTip);
			tag.putLong("LastFood", this.lastFood);
			tag.putLong("LastWelcome", this.lastWelcome);
			tag.putLong("LastFavor", this.lastFavor);
			tag.putLong("LastInvite", this.lastInvite);
			if (this.favor != null) {
				CompoundTag favor = new CompoundTag();
				favor.putString("Kind", this.favor.kind());
				favor.putLong("Asked", this.favor.asked());
				favor.putLong("Due", this.favor.due());
				favor.putBoolean("Promised", this.favor.promised());
				favor.putInt("Done", this.favor.done());
				if (this.favor.at() != null) {
					favor.putLong("At", this.favor.at().asLong());
				}

				tag.put("Favor", favor);
			}

			tag.putBoolean("Saved", this.saved);
			tag.putString("Why", this.why);
			tag.putString("Town", this.town);
			if (this.where != null) {
				tag.putLong("Where", this.where.asLong());
			}

			return tag;
		}

		static Bond load(CompoundTag tag) {
			Bond b = new Bond(tag.getUUID("Drifter"), tag.getUUID("Player"));
			b.drifterName = tag.getString("DrifterName");
			b.playerName = tag.getString("PlayerName");
			b.stage = Stage.valueOf(tag.getString("Stage"));
			b.state = State.valueOf(tag.getString("State"));
			b.since = tag.getLong("Since");
			b.lastTogether = tag.getLong("LastTogether");
			b.warming = tag.getLong("Warming");
			b.talkDay = tag.getLong("TalkDay");
			b.talksToday = tag.getInt("TalksToday");
			tag.getList("Shared", Tag.TAG_STRING).forEach(t -> b.shared.add(t.getAsString()));
			tag.getList("Tipped", Tag.TAG_LONG).forEach(t -> b.tipped.add(((LongTag)t).getAsLong()));
			b.lastTip = tag.getLong("LastTip");
			b.lastFood = tag.getLong("LastFood");
			b.lastWelcome = tag.getLong("LastWelcome");
			b.lastFavor = tag.getLong("LastFavor");
			b.lastInvite = tag.getLong("LastInvite");
			if (tag.contains("Favor")) {
				CompoundTag f = tag.getCompound("Favor");
				b.favor = new Favor(f.getString("Kind"), f.getLong("Asked"), f.getLong("Due"), f.getBoolean("Promised"), f.getInt("Done"),
					f.contains("At") ? BlockPos.of(f.getLong("At")) : null);
			}

			b.saved = tag.getBoolean("Saved");
			b.why = tag.getString("Why");
			b.town = tag.getString("Town");
			b.where = tag.contains("Where") ? BlockPos.of(tag.getLong("Where")) : null;
			return b;
		}
	}

	/** What a player does to it that ends a friendship there and then. */
	private static final java.util.Set<SocialEvent> ENDS = java.util.EnumSet.of(SocialEvent.ATTACKED, SocialEvent.NEARLY_KILLED, SocialEvent.KILLED,
		SocialEvent.KILLED_PET, SocialEvent.CAUGHT_STEALING, SocialEvent.TOOK_VALUABLES, SocialEvent.HAD_ITS_THINGS, SocialEvent.KILLED_FRIEND,
		SocialEvent.FIRE_NEAR_BASE);

	/** How far round a player the loop looks for Drifters, and how often (ticks). */
	private static final double NEAR = 32.0;
	private static final int EVERY = 20;
	/** Seen this recently counts as together (ticks). */
	private static final long SEEN_NOW = 100L;
	/** Time together after cooling before it picks up again (ticks: five minutes), and the trust that takes. */
	private static final long WARM_AGAIN = 20L * 60 * 5;
	private static final double WARM_TRUST = 25.0;
	/**
	 * Stage thresholds at the Normal pace, for an average nature (see {@link #need}). Familiarity: Drifter gives 1 for each 5
	 * minutes within 16 blocks (about 4 a day spent round someone) and a day's talks about 2 more, so a friend in 3 to 4 such
	 * days, about two or three play sessions; trust needs deeds besides (gifts, help, favors), as it drifts back each day.
	 */
	private static final double ACQ_FAMILIARITY = 8.0;
	private static final double FRIEND_TRUST = 50.0;
	private static final double FRIEND_FAMILIARITY = 20.0;
	private static final int FRIEND_AGREED = 2;
	private static final double BEST_TRUST = 70.0;
	private static final int BEST_AGREED = 6;
	/** Trust it doesn't drift down past, by stage (and cooled). */
	private static final double FLOOR_FRIEND = 30.0;
	private static final double FLOOR_BEST = 50.0;
	private static final double FLOOR_COOLED = 10.0;
	private static final int MOST_SHARED = 24;
	private static final int MOST_TIPPED = 32;

	private static final Map<String, Bond> bonds = new HashMap<>();
	@Nullable
	private static MinecraftServer server;

	private static String key(UUID drifter, UUID player) {
		return drifter + "|" + player;
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;
			load(started);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			bonds.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Friends::tick);
		DrifterEvents.DIED.register((drifter, source, comesBack) -> {
			if (!comesBack) {
				for (Bond b : bonds.values()) {
					if (b.drifter.equals(drifter.getUUID()) && b.state != State.GONE) {
						b.state = State.GONE;
						b.why = "died";
					}
				}

				dirty();
			}
		});
		DrifterEvents.PLAYER_DEED.register((drifter, player, deed, trustAfter) -> {
			if (deed == SocialEvent.SAVED_IT && eligible(drifter)) {
				bondOrNew(drifter, player).saved = true;
				dirty();
			}

			// Wronged: over at once (a single hit then backing off isn't: an accident between friends).
			if (ENDS.contains(deed)) {
				end(drifter.getUUID(), player.getUUID(), deed.what);
			}
		});
		TalkApi.setTrustFloor(Friends::floor);
		// Where something is, told to a friend: whispered.
		TalkApi.setWhisperRule((drifter, player, key) -> CivConfig.friends().whisper && isFriend(drifter, player));
		TalkApi.registerPlayerFacts((drifter, player, words, flags) -> {
			// (What it's asking of them just now, if anything: Quests.)
			Quests.facts(drifter, player, words);
			Bond b = bond(drifter.getUUID(), player.getUUID());
			if (b == null) {
				return;
			}

			if (b.state == State.COOLED) {
				flags.add("cooled");
				return;
			}

			if (!b.active()) {
				return;
			}

			if (b.stage.ordinal() >= Stage.ACQUAINTANCE.ordinal()) {
				flags.add("acq");
			}

			if (b.friend()) {
				flags.add("pal");
			}

			if (b.best()) {
				flags.add("bff");
			}

			String shared = sharedWord(drifter, b);
			if (shared != null) {
				words.put("shared", shared);
				flags.add("shared");
			}

			words.put("since_days", Math.max(1, (now() - b.since) / 24000L));
		});
		TalkApi.setNameNote((drifter, viewer) -> {
			Bond b = bond(drifter.getUUID(), viewer.getUUID());
			if (b == null) {
				return null;
			}

			return b.state == State.COOLED ? "used to be friends" : !b.active() || b.stage == Stage.STRANGER ? null : "your " + b.stage.word;
		});
	}

	// ----- Reading -----

	@Nullable
	static Bond bond(UUID drifter, UUID player) {
		// (Friendships off: none count, kept for when they're on again.)
		return CivConfig.friends().on ? bonds.get(key(drifter, player)) : null;
	}

	@Nullable
	static Bond bond(DrifterEntity drifter, Player player) {
		return bond(drifter.getUUID(), player.getUUID());
	}

	static Bond bondOrNew(DrifterEntity drifter, Player player) {
		Bond b = bonds.computeIfAbsent(key(drifter.getUUID(), player.getUUID()), k -> new Bond(drifter.getUUID(), player.getUUID()));
		b.drifterName = drifter.getName().getString();
		b.playerName = player.getGameProfile().getName();
		if (b.lastTogether == 0) {
			b.lastTogether = now();
		}

		return b;
	}

	/** Its stage with a player now (stranger if none, or cooled or ended). */
	static Stage stage(DrifterEntity drifter, Player player) {
		Bond b = bond(drifter, player);
		return b == null || !b.active() ? Stage.STRANGER : b.stage;
	}

	static boolean isFriend(DrifterEntity drifter, Player player) {
		Bond b = bond(drifter, player);
		return b != null && b.friend();
	}

	static boolean isFriend(UUID drifter, UUID player) {
		Bond b = bond(drifter, player);
		return b != null && b.friend();
	}

	static boolean isBest(UUID drifter, UUID player) {
		Bond b = bond(drifter, player);
		return b != null && b.best();
	}

	/** The price factor at its stall or shop for this player: a friend's price (the setting's discount) when cheaper. */
	static double price(DrifterEntity keeper, @Nullable Player player, double standing) {
		if (player == null || !isFriend(keeper, player)) {
			return standing;
		}

		return Math.min(standing, 1.0 - CivConfig.friends().discount / 100.0);
	}

	/** A friend seeing them take something from its town: let down a little, not more. */
	static final double LET_DOWN = -3.0;
	/** Why it cooled when word of a big crime elsewhere reached its town. */
	private static final String HEARD = "what they did in ";

	/** Its Drifter is one of this town (its group's), or was last seen as one. */
	private static boolean ofTown(Bond b, Town town) {
		var group = DrifterApi.group(b.drifter);
		return group != null && town.group != null ? group.id.equals(town.group) : b.town.equals(town.name);
	}

	/** Its people's friendships with this player over (a murder in their town). */
	static void endTown(Town town, UUID player, String why) {
		for (Bond b : of(player)) {
			if (ofTown(b, town) && (b.active() || b.state == State.COOLED)) {
				end(b.drifter, player, why);
			}
		}
	}

	/** Its people's friendships with this player cooled: word of something big they did in another town. */
	static void coolTown(Town town, UUID player, String where) {
		for (Bond b : of(player)) {
			if (ofTown(b, town) && b.friend()) {
				cool(b, HEARD + where);
			}
		}
	}

	/**
	 * Whether this Drifter of this town leaves them be although its town wants them: a friend, over something small (not
	 * kill on sight); or one cooled only by word from another town.
	 */
	static boolean sparedBy(DrifterEntity drifter, Player player, Town town) {
		Bond b = bond(drifter, player);
		if (b == null) {
			return false;
		}

		if (b.state == State.COOLED && b.why.startsWith(HEARD)) {
			return true;
		}

		return b.friend() && !Justice.killOnSight(town, player);
	}

	/** Every bond of this player's (any stage, any state). */
	static List<Bond> of(UUID player) {
		List<Bond> out = new ArrayList<>();
		for (Bond b : bonds.values()) {
			if (b.player.equals(player)) {
				out.add(b);
			}
		}

		return out;
	}

	/** Every bond. */
	static java.util.Collection<Bond> all() {
		return bonds.values();
	}

	/** Every bond of this Drifter's. */
	static List<Bond> ofDrifter(UUID drifter) {
		List<Bond> out = new ArrayList<>();
		for (Bond b : bonds.values()) {
			if (b.drifter.equals(drifter)) {
				out.add(b);
			}
		}

		return out;
	}

	/** It can be someone's friend at all: not a Bandit or a delver, not one met once, and a townsperson if the setting says. */
	static boolean eligible(DrifterEntity drifter) {
		return CivConfig.friends().on && drifter.isAlive() && !drifter.isTemporary() && !TalkApi.hostileOnSight(drifter)
			&& (!CivConfig.friends().townsOnly || Market.townOf(drifter) != null);
	}

	static long now() {
		return server == null ? 0L : server.overworld().getGameTime();
	}

	static void dirty() {
		if (server != null) {
			state(server).setDirty();
		}
	}

	private static double floor(DrifterEntity drifter, UUID player) {
		Bond b = bond(drifter.getUUID(), player);
		if (b == null) {
			return Double.NEGATIVE_INFINITY;
		}

		return b.state == State.COOLED ? FLOOR_COOLED : b.best() ? FLOOR_BEST : b.friend() ? FLOOR_FRIEND : Double.NEGATIVE_INFINITY;
	}

	/** A shared opinion's words, for its talk ("hating phantoms"), or null. */
	@Nullable
	private static String sharedWord(DrifterEntity drifter, Bond b) {
		List<String> agreed = new ArrayList<>();
		for (String s : b.shared) {
			if (s.endsWith(":yes")) {
				String about = TalkApi.topicAbout(s.substring(0, s.length() - 4));
				if (!about.isEmpty()) {
					agreed.add(about);
				}
			}
		}

		return agreed.isEmpty() ? null : agreed.get(drifter.getRandom().nextInt(agreed.size()));
	}

	/** Remembers what they answered to an opinion (an older answer to the same one replaced). */
	static void share(Bond b, String topic, boolean yes) {
		b.shared.removeIf(s -> s.startsWith(topic + ":"));
		b.shared.add(topic + (yes ? ":yes" : ":no"));
		while (b.shared.size() > MOST_SHARED) {
			b.shared.remove(0);
		}

		dirty();
	}

	static void tipped(Bond b, BlockPos pos) {
		b.tipped.add(pos.asLong());
		while (b.tipped.size() > MOST_TIPPED) {
			b.tipped.remove(0);
		}

		dirty();
	}

	// ----- Changing -----

	/** Over: it's done with them (cooled or ended can't become friends again; trust starts over from Drifter's own). */
	/** A player learned what became of one of its people in its town (died away for good, or left): gone, and why. */
	static void learned(UUID player, UUID drifter, String why) {
		for (Bond b : bonds.values()) {
			if (b.drifter.equals(drifter) && b.player.equals(player) && b.state != State.GONE) {
				b.state = State.GONE;
				b.why = why;
				dirty();
			}
		}
	}

	/** Do these two know each other past strangers (whatever became of it)? */
	static boolean known(UUID player, UUID drifter) {
		for (Bond b : bonds.values()) {
			if (b.drifter.equals(drifter) && b.player.equals(player) && b.stage.ordinal() >= Stage.ACQUAINTANCE.ordinal()) {
				return true;
			}
		}

		return false;
	}

	static void end(UUID drifter, UUID player, String why) {
		Bond b = bond(drifter, player);
		if (b != null && b.state != State.ENDED && b.state != State.GONE && b.stage.ordinal() >= Stage.ACQUAINTANCE.ordinal()) {
			b.state = State.ENDED;
			b.why = why;
			b.favor = null;
			DrifterCivilizations.LOGGER.info("Friendship over: {} and {} ({})", b.drifterName, b.playerName, why);
			dirty();
		}
	}

	/** Cooled to "used to be friends": warm still, no perks; time together brings it back. */
	static void cool(Bond b, String why) {
		if (b.active() && b.stage.ordinal() >= Stage.ACQUAINTANCE.ordinal()) {
			b.state = State.COOLED;
			b.why = why;
			b.warming = 0;
			b.favor = null;
			DrifterCivilizations.LOGGER.info("Friendship cooled: {} and {} ({})", b.drifterName, b.playerName, why);
			dirty();
		}
	}

	/** To this stage now (debug, or reached). */
	static void reach(DrifterEntity drifter, ServerPlayer player, Bond b, Stage stage, boolean announce) {
		b.stage = stage;
		b.state = State.ACTIVE;
		b.since = now();
		b.why = "";
		DrifterCivilizations.LOGGER.info("Friendship: {} and {} are {} now", b.drifterName, b.playerName, stage.word);
		dirty();
		if (announce && CivConfig.friends().announce && (stage == Stage.FRIEND || stage == Stage.BEST_FRIEND)) {
			String line = TalkApi.line(drifter, stage == Stage.FRIEND ? "friends.up.friend" : "friends.up.best", "player", TalkApi.callName(drifter, player));
			if (line != null) {
				TalkApi.sayTo(drifter, player, line);
			}
		}
	}

	// ----- The loop -----

	private static void tick(MinecraftServer s) {
		long now = s.overworld().getGameTime();
		if (now % EVERY != 0 || !CivConfig.friends().on) {
			return;
		}

		for (ServerPlayer player : s.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}

			for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(NEAR), Friends::eligible)) {
				check(drifter, player, now);
			}
		}

		// Apart too long: it cools (once a minute, over every bond, loaded or not).
		if (now % 1200 == 0) {
			// (Times together moved: saved with the world at its next save.)
			dirty();
		}

		if (now % 1200 == 0 && CivConfig.friends().coolDays > 0) {
			long apart = CivConfig.friends().coolDays * 24000L;
			for (Bond b : bonds.values()) {
				if (b.active() && b.stage.ordinal() >= Stage.ACQUAINTANCE.ordinal() && now - b.lastTogether >= apart) {
					cool(b, "apart " + CivConfig.friends().coolDays + " days");
				}
			}
		}

		FriendTalk.tick(s, now);
		FriendPerks.tick(s, now);
	}

	/** One Drifter near one player: together, warming again, or closer by a stage. */
	private static void check(DrifterEntity drifter, ServerPlayer player, long now) {
		long seen = TalkApi.lastSeen(drifter, player.getUUID());
		boolean together = seen >= 0 && now - seen <= SEEN_NOW;
		Bond b = bond(drifter, player);
		if (b != null) {
			if (b.state == State.ENDED || b.state == State.GONE) {
				return;
			}

			if (together) {
				if (now - b.lastTogether >= FriendPerks.WELCOME_APART) {
					b.returned = now;
				}

				b.lastTogether = now;
				b.where = drifter.blockPosition();
				Town town = Market.townOf(drifter);
				b.town = town == null ? "" : town.name;
			}

			if (b.state == State.COOLED) {
				if (together && DrifterApi.trustInPlayer(drifter, player.getUUID()) >= WARM_TRUST) {
					b.warming += EVERY;
					if (b.warming >= WARM_AGAIN) {
						b.state = State.ACTIVE;
						b.why = "";
						DrifterCivilizations.LOGGER.info("Friendship back: {} and {} ({})", b.drifterName, b.playerName, b.stage.word);
						String line = b.stage.ordinal() >= Stage.FRIEND.ordinal() ? TalkApi.line(drifter, "friends.back_from_cool", "player",
							TalkApi.callName(drifter, player)) : null;
						if (line != null) {
							TalkApi.sayTo(drifter, player, line);
						}

						dirty();
					}
				}

				return;
			}
		}

		if (!together) {
			return;
		}

		Stage next = next(drifter, player, b);
		if (next != null) {
			reach(drifter, player, b != null ? b : bondOrNew(drifter, player), next, true);
		}
	}

	/** The stage it's ready to move up to, or null. */
	@Nullable
	private static Stage next(DrifterEntity drifter, ServerPlayer player, @Nullable Bond b) {
		double trust = DrifterApi.trustInPlayer(drifter, player.getUUID());
		double familiarity = TalkApi.familiarity(drifter, player.getUUID());
		Stage stage = b == null ? Stage.STRANGER : b.stage;
		double ease = ease(drifter);
		return switch (stage) {
			case STRANGER -> trust > 0.0 && familiarity >= need(ACQ_FAMILIARITY, ease) ? Stage.ACQUAINTANCE : null;
			case ACQUAINTANCE -> trust >= trustNeed(FRIEND_TRUST, ease) && familiarity >= need(FRIEND_FAMILIARITY, ease)
				&& b != null && (b.agreed() >= FRIEND_AGREED || b.saved) ? Stage.FRIEND : null;
			case FRIEND -> trust >= trustNeed(BEST_TRUST, ease) && familiarity >= need(FRIEND_FAMILIARITY * CivConfig.friends().bestFriendHarder, ease)
				&& b != null && b.agreed() >= BEST_AGREED && bestFriends(drifter) < (DrifterApi.trait(drifter, "sociability") >= 0.8 ? 4 : 2)
				? Stage.BEST_FRIEND : null;
			case BEST_FRIEND -> null;
		};
	}

	/** How easily it lets people close (0.5 to 1.5): sociable and forgiving natures more, vengeful ones less. */
	static double ease(DrifterEntity drifter) {
		double e = 0.5 + DrifterApi.trait(drifter, "sociability") + 0.5 * DrifterApi.trait(drifter, "forgiveness") - 0.5 * DrifterApi.trait(drifter, "vengefulness");
		return Math.max(0.5, Math.min(1.5, e));
	}

	/** Time together needed (familiarity): at the setting's pace and its nature's ease, at most 95 (of 100). */
	private static double need(double normal, double ease) {
		return Math.min(95.0, normal / (CivConfig.friends().pace * ease));
	}

	/** Trust needed: moved less by pace and nature than time is (trust is quick to swing and drifts back each day). */
	private static double trustNeed(double normal, double ease) {
		return Math.max(25.0, Math.min(90.0, normal / Math.sqrt(CivConfig.friends().pace * ease)));
	}

	private static int bestFriends(DrifterEntity drifter) {
		int n = 0;
		for (Bond b : bonds.values()) {
			if (b.drifter.equals(drifter.getUUID()) && b.best()) {
				n++;
			}
		}

		return n;
	}

	// ----- Commands -----

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("friends").executes(Friends::list)
			.then(Commands.literal("set").requires(source -> source.hasPermission(2))
				.then(Commands.argument("stage", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("stranger", "acquaintance", "friend", "best"), builder))
					.then(Commands.argument("who", net.minecraft.commands.arguments.EntityArgument.entity()).executes(ctx -> setStage(ctx,
						net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "who") instanceof DrifterEntity d ? d : null)))
					.executes(ctx -> setStage(ctx, looked(ctx.getSource().getPlayerOrException())))))
			.then(Commands.literal("days").requires(source -> source.hasPermission(2))
				.then(Commands.argument("days", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					int days = IntegerArgumentType.getInteger(ctx, "days");
					for (Bond b : of(player.getUUID())) {
						b.lastTogether -= days * 24000L;
					}

					ctx.getSource().sendSuccess(() -> Component.literal("Debug: your friendships aged " + days + " days apart"), false);
					return 1;
				})))
			.then(Commands.literal("add").requires(source -> source.hasPermission(2))
				.then(Commands.argument("trust", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-100, 100))
					.then(Commands.argument("familiarity", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-100, 100)).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						DrifterEntity drifter = looked(player);
						if (drifter == null) {
							ctx.getSource().sendFailure(Component.literal("No Drifter in front of you"));
							return 0;
						}

						String name = player.getGameProfile().getName();
						DrifterApi.addTrustInPlayer(drifter, player.getUUID(), name, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "trust"));
						TalkApi.addFamiliarity(drifter, player.getUUID(), name, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "familiarity"));
						String shown = String.format(java.util.Locale.ROOT, "Debug: %s: trust %.1f, familiarity %.1f, %s", drifter.getName().getString(),
							DrifterApi.trustInPlayer(drifter, player.getUUID()), TalkApi.familiarity(drifter, player.getUUID()), stage(drifter, player).word);
						ctx.getSource().sendSuccess(() -> Component.literal(shown), false);
						return 1;
					}))))
			.then(Commands.literal("end").requires(source -> source.hasPermission(2)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				DrifterEntity drifter = looked(player);
				if (drifter == null) {
					ctx.getSource().sendFailure(Component.literal("No Drifter in front of you"));
					return 0;
				}

				end(drifter.getUUID(), player.getUUID(), "debug");
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: over with " + drifter.getName().getString()), false);
				return 1;
			}))
			.then(FriendTalk.command())
			.then(FriendPerks.tipCommand())
			.then(FriendPerks.favorCommand())
			.then(FriendPerks.inviteCommand())
			.then(FriendPerks.handCommand())
			.then(FriendPerks.backupCommand());
	}

	private static int setStage(CommandContext<CommandSourceStack> ctx, @Nullable DrifterEntity drifter) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String word = StringArgumentType.getString(ctx, "stage");
		Stage stage = switch (word) {
			case "stranger" -> Stage.STRANGER;
			case "acquaintance" -> Stage.ACQUAINTANCE;
			case "friend" -> Stage.FRIEND;
			case "best", "best_friend" -> Stage.BEST_FRIEND;
			default -> null;
		};
		if (drifter == null || stage == null) {
			ctx.getSource().sendFailure(Component.literal(drifter == null ? "No Drifter in front of you" : "No such stage"));
			return 0;
		}

		Bond b = bondOrNew(drifter, player);
		// (Its trust and time with them made to fit, so Drifter's own behaviour goes with it.)
		double trust = stage == Stage.BEST_FRIEND ? 75.0 : stage == Stage.FRIEND ? 55.0 : stage == Stage.ACQUAINTANCE ? 10.0 : 0.0;
		double familiarity = stage == Stage.BEST_FRIEND ? 60.0 : stage == Stage.FRIEND ? 32.0 : stage == Stage.ACQUAINTANCE ? 12.0 : 0.0;
		double had = DrifterApi.trustInPlayer(drifter, player.getUUID());
		if (had < trust) {
			DrifterApi.addTrustInPlayer(drifter, player.getUUID(), player.getGameProfile().getName(), trust - had);
		}

		double knew = TalkApi.familiarity(drifter, player.getUUID());
		if (knew < familiarity) {
			TalkApi.addFamiliarity(drifter, player.getUUID(), player.getGameProfile().getName(), familiarity - knew);
		}

		reach(drifter, player, b, stage, true);
		ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + drifter.getName().getString() + " is your " + stage.word + " now"), false);
		return 1;
	}

	/** The nearest friend of theirs within 24 blocks (debug), or the one they're looking at. */
	@Nullable
	static DrifterEntity friendNear(ServerPlayer player) {
		DrifterEntity best = null;
		for (DrifterEntity d : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(24.0), d -> d.isAlive() && isFriend(d, player))) {
			if (best == null || d.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = d;
			}
		}

		return best != null ? best : looked(player);
	}

	/** The Drifter a player is looking at, within 16 blocks (or the nearest within 16). */
	@Nullable
	static DrifterEntity looked(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		DrifterEntity best = null;
		double bestDot = 0.9;
		DrifterEntity nearest = null;
		double nearestDistance = 256.0;
		for (DrifterEntity d : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(16.0), DrifterEntity::isAlive)) {
			Vec3 to = d.getEyePosition().subtract(eye);
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = d;
			}

			double distance = d.distanceToSqr(player);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = d;
			}
		}

		return best != null ? best : nearest;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
			ctx.getSource().sendFailure(Component.literal("A player is needed"));
			return 0;
		}

		long now = now();
		List<Bond> mine = of(player.getUUID());
		mine.removeIf(b -> b.stage == Stage.STRANGER);
		mine.sort((x, y) -> y.stage.ordinal() - x.stage.ordinal());
		if (mine.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No friends or acquaintances yet" + (CivConfig.friends().on ? "" : " (friendships are off)")), false);
			return 1;
		}

		StringBuilder out = new StringBuilder("Your friends and acquaintances:");
		for (Bond b : mine) {
			String standing = switch (b.state) {
				case ACTIVE -> b.stage.word + " (" + Math.max(0, (now - b.since) / 24000L) + " days)";
				case COOLED -> "used to be friends (" + b.why + ")";
				case ENDED -> "fell out (" + b.why + ")";
				case GONE -> "gone (" + b.why + ")";
			};
			long apart = (now - b.lastTogether) / 24000L;
			out.append("\n  ").append(b.drifterName).append(": ").append(standing)
				.append(b.town.isEmpty() ? "" : ", of " + b.town)
				.append(b.where == null ? "" : ", last seen at " + b.where.getX() + " " + b.where.getZ())
				.append(b.state == State.ACTIVE && apart >= 1 ? ", " + apart + (apart == 1 ? " day ago" : " days ago") : "");
		}

		String shown = out.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(shown), false);
		return 1;
	}

	/** Its people who are your friends or acquaintances, for {@code /drifterciv town}; null if none. */
	@Nullable
	static String inTown(Town town, Player player) {
		List<String> here = new ArrayList<>();
		for (Bond b : of(player.getUUID())) {
			if (b.active() && b.stage != Stage.STRANGER && ofTown(b, town)) {
				here.add(b.drifterName + " (" + b.stage.word + ")");
			}
		}

		return here.isEmpty() ? null : "Your people here: " + String.join(", ", here);
	}

	// ----- Saved with the world -----

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			Saved saved = new Saved();
			// Version 1 is the first format.
			for (Tag t : tag.getList("Bonds", Tag.TAG_COMPOUND)) {
				try {
					Bond b = Bond.load((CompoundTag)t);
					bonds.put(key(b.drifter, b.player), b);
				} catch (Exception e) {
					DrifterCivilizations.LOGGER.warn("A friendship that couldn't be read was left out", e);
				}
			}

			return saved;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", 1);
			ListTag list = new ListTag();
			for (Bond b : bonds.values()) {
				list.add(b.save());
			}

			tag.put("Bonds", list);
			return tag;
		}
	}

	private static Saved state(MinecraftServer s) {
		return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_friends");
	}

	private static void load(MinecraftServer s) {
		bonds.clear();
		state(s);
	}
}
