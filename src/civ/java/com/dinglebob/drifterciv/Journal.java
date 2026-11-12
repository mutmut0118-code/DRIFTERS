package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A player's journal (docs/superpowers/specs/2026-11-11-player-journal-design.md; the user, 2026-11-11: "all the data
 * about my player with other drifters/myself that is only accessible doing /drifterciv commands easily displayed in a ui
 * thats immersive and faithful to vanilla"): who they are to the world, worked out here as plain lines of words and sent
 * to their client when it asks (the J key; the addon's client only draws it). Only what they'd know: people they've met,
 * towns they've been to or heard of, feelings in words, never numbers. Two things are kept for it with the world: the
 * Drifters each player has met, and the towns they've been to with how they stood there when last about.
 */
public final class Journal {
	private Journal() {
	}

	/** Inks: plain, good (green), bad (red), faint. */
	public static final int PLAIN = 0;
	public static final int GOOD = 1;
	public static final int BAD = 2;
	public static final int FAINT = 3;

	/** One line of a page: what, how it stands (to the right), its ink, and more about it (empty: nothing to open). */
	public record Line(String left, String right, int ink, List<String> detail) {
		static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Line::left,
			ByteBufCodecs.STRING_UTF8, Line::right, ByteBufCodecs.VAR_INT, Line::ink, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Line::detail,
			Line::new);
	}

	/** One bookmark: its name, its lines, and what the page says when there are none. */
	public record Tab(String title, List<Line> lines, String empty) {
		static final StreamCodec<RegistryFriendlyByteBuf, Tab> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Tab::title,
			Line.CODEC.apply(ByteBufCodecs.list()), Tab::lines, ByteBufCodecs.STRING_UTF8, Tab::empty, Tab::new);
	}

	/** The whole journal: their name, the left page (them), and the bookmarks. */
	public record Book(String name, List<Line> you, List<Tab> tabs) implements CustomPacketPayload {
		public static final Type<Book> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "journal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Book> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Book::name,
			Line.CODEC.apply(ByteBufCodecs.list()), Book::you, Tab.CODEC.apply(ByteBufCodecs.list()), Book::tabs, Book::new);

		@Override
		public Type<Book> type() {
			return TYPE;
		}
	}

	/** What a page of the Chronicle is made of (the chronicle revamp): see {@link El}. */
	public static final int HEADLINE = 0;
	public static final int DATELINE = 1;
	public static final int RULE = 2;
	public static final int THIN = 3;
	public static final int TEXT = 4;
	public static final int SUB = 5;
	public static final int BANNERS = 6;
	public static final int HEADS = 7;
	public static final int PERSON = 8;
	public static final int QUOTE = 9;
	public static final int LINK = 10;
	public static final int BREAK = 11;

	/**
	 * One thing on a page, top to bottom: a headline ({@code a}), a dateline ({@code a} left, {@code b} right), a double
	 * rule, a thin one, text ({@code a}, its ink), a small heading, banners ({@code more}: each "picture, tab, caption, tab,
	 * page"), heads ({@code more}: each "skin, tab, name, tab, page, tab, caption"), a person ({@code a} skin, {@code b}
	 * name, {@code more}: their power's picture, then a line about them), a quote ({@code a} the words, {@code b} who,
	 * {@code more}: their skin), a link ({@code a} the words, {@code b} the page), a break (the rest on the right page).
	 * A picture: "b" and a dye's id (a banner), "s" and a skin (a head), "-" (none). A skin: a Drifter's skin texture and
	 * the real player's skin it wears, as the world's record keeps them ("": none known).
	 */
	public record El(int type, String a, String b, int ink, List<String> more) {
		static final StreamCodec<RegistryFriendlyByteBuf, El> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, El::type, ByteBufCodecs.STRING_UTF8, El::a,
			ByteBufCodecs.STRING_UTF8, El::b, ByteBufCodecs.VAR_INT, El::ink, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), El::more, El::new);
	}

	/** A page of the Chronicle, sent when it's turned to. A line of a bookmark opens one by a detail of "@page:" and its id. */
	public record Page(String id, List<El> els) implements CustomPacketPayload {
		public static final Type<Page> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "journal_page"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Page::id,
			El.CODEC.apply(ByteBufCodecs.list()), Page::els, Page::new);

		@Override
		public Type<Page> type() {
			return TYPE;
		}
	}

	/** A client turning to a page. */
	public record Open(String id) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "journal_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Open::id, Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** A page as plain lines (for tests). */
	public static List<String> text(Page page) {
		List<String> out = new ArrayList<>();
		for (El el : page.els()) {
			switch (el.type()) {
				case HEADLINE -> out.add("== " + el.a() + " ==");
				case DATELINE -> out.add(el.a() + " / " + el.b());
				case RULE, THIN -> {
				}
				case BANNERS -> out.add("[banners] " + String.join("; ", el.more().stream().map(one -> one.replace('\t', ',')).toList()));
				case HEADS -> out.add("[heads] " + String.join("; ", el.more().stream().map(one -> one.split("\t", -1).length > 1 ? one.split("\t", -1)[1]
					+ (one.split("\t", -1)[0].isEmpty() ? " (no skin)" : "") : one).toList()));
				case PERSON -> out.add("[person] " + el.b() + (el.a().isEmpty() ? " (no skin)" : "") + ": " + String.join(" / ", el.more()));
				case QUOTE -> out.add("\"" + el.a() + "\" - " + el.b());
				case LINK -> out.add("-> " + el.a() + " [" + el.b() + "]");
				case BREAK -> out.add("--- right page ---");
				default -> out.add(el.a());
			}
		}

		return out;
	}

	/** Has this player met this Drifter (by their journal)? */
	static boolean met(UUID player, UUID drifter) {
		Known mine = known.get(player);
		return mine != null && mine.met.containsKey(drifter);
	}

	/** Has this player been to this town (by their journal)? */
	static boolean been(UUID player, UUID town) {
		Known mine = known.get(player);
		return mine != null && mine.been.containsKey(town);
	}

	/** A client asking for its player's journal. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "journal_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** A town a player has been to: how they stood there when last about, and the day. */
	private record Been(String standing, long day) {
	}

	/** What a player knows of the world, for their journal. */
	private static final class Known {
		final Map<UUID, String> met = new LinkedHashMap<>();
		final Map<UUID, Been> been = new LinkedHashMap<>();
	}

	private static final int SAVE_VERSION = 1;
	/** A Drifter this near that has just seen them has met them (blocks; talking distance). */
	private static final double MEET = 6.0;
	private static final long SEEN_NOW = 40L;
	/** Trust (-100 to 100) under which someone met holds a grudge, is wary; over which it likes them. */
	private static final double GRUDGE = -20.0;
	private static final double WARY = -5.0;
	private static final double LIKES = 10.0;
	/** At most this many lines to a page (the nearest or closest first). */
	private static final int MOST = 200;

	private static final Map<UUID, Known> known = new HashMap<>();
	/** The tick each player last turned to a page of the Chronicle. */
	private static final Map<UUID, Integer> turned = new HashMap<>();
	private static boolean dirty;

	static void register() {
		PayloadTypeRegistry.playS2C().register(Book.TYPE, Book.CODEC);
		PayloadTypeRegistry.playC2S().register(Ask.TYPE, Ask.CODEC);
		PayloadTypeRegistry.playS2C().register(Page.TYPE, Page.CODEC);
		PayloadTypeRegistry.playC2S().register(Open.TYPE, Open.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, (open, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				// (A page at a time: one who asks faster than a hand turns them waits.)
				int tick = context.server().getTickCount();
				Integer last = turned.put(player.getUUID(), tick);
				if (last == null || tick != last) {
					ServerPlayNetworking.send(player, Annals.page(player, open.id().length() > 80 ? "" : open.id()));
				}
			});
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> turned.clear());
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (ask, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> ServerPlayNetworking.send(player, of(player)));
		});
		ServerLifecycleEvents.SERVER_STARTED.register(Journal::state);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> known.clear());
		ServerTickEvents.END_SERVER_TICK.register(Journal::tick);
		// `/drifterciv journal`: the journal as chat lines (and in the log, for tests); `note` (testing): the nearest town
		// counts as been to and every Drifter near as met.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("drifterciv").then(net.minecraft.commands.Commands.literal("journal").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				List<String> lines = text(of(player));
				DrifterCivilizations.LOGGER.info("[journal] {}", String.join(" | ", lines));
				ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(String.join("\n", lines)), false);
				return 1;
			}).then(net.minecraft.commands.Commands.literal("note").requires(source -> source.hasPermission(2)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
				List<DrifterEntity> near = player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(48.0), Friends::eligible);
				note(player, town, null);
				for (DrifterEntity drifter : near) {
					note(player, null, drifter);
				}

				ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Noted: been to " + (town == null ? "no town" : town.name)
					+ ", met " + near.size() + " Drifters"), false);
				return 1;
			})))));
	}

	// ----- What they come to know -----

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		long now = server.overworld().getGameTime();
		long day = Chronicle.day(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}

			Known mine = known.computeIfAbsent(player.getUUID(), k -> new Known());
			for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(MEET), Friends::eligible)) {
				long seen = TalkApi.lastSeen(drifter, player.getUUID());
				String name = drifter.getName().getString();
				if (seen >= 0 && now - seen <= SEEN_NOW && !name.equals(mine.met.put(drifter.getUUID(), name))) {
					dirty = true;
				}
			}

			String dimension = player.level().dimension().location().toString();
			for (Town town : Towns.all()) {
				if (town.dimension.equals(dimension)
					&& Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) < town.land()) {
					// (Wanted and exiled come from their record there, always current; what's kept is how its people took them.)
					String standing = TownReputation.standing(server, town, player.getUUID()).word;
					standing = standing.equals("wanted") ? "unwelcome" : standing;
					Been before = mine.been.get(town.id);
					if (before == null || !before.standing.equals(standing)) {
						mine.been.put(town.id, new Been(standing, day));
						dirty = true;
					}
				}
			}
		}

		if (dirty) {
			state(server).setDirty();
			dirty = false;
		}
	}

	// ----- The journal -----

	/** A player's journal now. */
	public static Book of(ServerPlayer player) {
		MinecraftServer server = player.server;
		UUID id = player.getUUID();
		Known mine = known.computeIfAbsent(id, k -> new Known());
		long day = Chronicle.day(server);
		long now = server.overworld().getGameTime();
		boolean friendsOn = CivConfig.friends().on;
		List<Friends.Bond> bonds = friendsOn ? Friends.of(id) : List.of();
		String dimension = player.level().dimension().location().toString();

		// Towns: been to (written down, or a friend's, or where they broke its law), and only heard of (wanted by word).
		Map<Town, String> standings = new LinkedHashMap<>();
		List<Town> heardOf = new ArrayList<>();
		List<Town> friendTowns = new ArrayList<>();
		List<Town> towns = Towns.all();
		towns.sort(Comparator.comparingDouble(town -> town.dimension.equals(dimension) ? town.center.distSqr(player.blockPosition()) : Double.MAX_VALUE));
		for (Town town : towns) {
			Been been = mine.been.get(town.id);
			Town.Criminal criminal = town.crimes.get(id);
			boolean friendThere = bonds.stream().anyMatch(b -> b.stage != Friends.Stage.STRANGER && town.name.equals(b.town));
			String live = criminal == null ? null : criminal.wantedUntil > 0 ? "wanted" : criminal.exiledUntil > 0 ? "exiled" : null;
			if (been != null || criminal != null && criminal.offences > 0) {
				standings.put(town, live != null ? live : been == null ? "neutral" : been.standing.equals("wanted") ? "unwelcome" : been.standing);
			} else if (live != null) {
				heardOf.add(town);
			} else if (friendThere) {
				friendTowns.add(town);
			}
		}

		DrifterApi.PlayerBounty bounty = DrifterApi.bounty(id);

		// The left page: them.
		List<Line> you = new ArrayList<>();
		// (Who they chose to be: their nation and where they're from. Citizens.)
		for (String line : Citizens.line(server, id)) {
			if (!line.isEmpty()) {
				you.add(new Line(line, "", you.isEmpty() ? PLAIN : FAINT, List.of()));
			}
		}

		int welcome = count(standings, "welcome");
		int neutral = count(standings, "neutral");
		int wary = count(standings, "wary");
		int unwelcome = count(standings, "unwelcome");
		int exiled = count(standings, "exiled");
		int wanted = count(standings, "wanted") + heardOf.size();
		String reputation = wanted > 0 || bounty != null ? "An outlaw" : standings.isEmpty() ? "Nobody knows you yet"
			: welcome + wary + unwelcome + exiled == 0 ? "A new face"
			: unwelcome + exiled > 0 || wary > welcome ? "Not trusted" : welcome * 2 > standings.size() ? "Well liked" : "Known here and there";
		you.add(new Line(reputation, "", wanted > 0 || bounty != null || reputation.equals("Not trusted") ? BAD : reputation.equals("Well liked") ? GOOD : PLAIN,
			List.of()));
		List<String> counts = new ArrayList<>();
		addCount(counts, "welcome in", welcome);
		addCount(counts, "wary of you in", wary);
		addCount(counts, "unwelcome in", unwelcome);
		addCount(counts, "exiled from", exiled);
		if (counts.isEmpty()) {
			addCount(counts, "a stranger in", neutral);
		}

		if (!counts.isEmpty()) {
			you.add(new Line(capital(String.join(", ", counts)), "", FAINT, List.of()));
		}

		for (Town town : standings.keySet()) {
			if (standings.get(town).equals("wanted")) {
				you.add(new Line("Wanted in " + town.name, "", BAD, List.of()));
			}
		}

		for (Town town : heardOf) {
			you.add(new Line("Wanted in " + town.name, "", BAD, List.of()));
		}

		if (bounty != null) {
			you.add(new Line("A price on your head: " + bounty.issuer() + ", for " + bounty.reason(), "", BAD, List.of()));
		}

		return new Book(Citizens.called(player), you, List.of(
			people(server, player, mine, bonds, friendsOn, now),
			towns(server, player, standings, heardOf, friendTowns, bonds, day, bounty),
			law(player, towns, day, bounty),
			favors(bonds, friendsOn, now),
			Annals.tab(player)));
	}

	private static Tab people(MinecraftServer server, ServerPlayer player, Known mine, List<Friends.Bond> bonds, boolean friendsOn, long now) {
		if (!friendsOn) {
			return new Tab("People", List.of(), "Friendships are switched off in the settings.");
		}

		UUID id = player.getUUID();
		List<Line> lines = new ArrayList<>();
		List<Friends.Bond> sorted = new ArrayList<>(bonds);
		sorted.removeIf(b -> b.stage == Friends.Stage.STRANGER && b.state == Friends.State.ACTIVE);
		// (Friends first, the closest first; then those it cooled with, fell out with, or lost.)
		sorted.sort(Comparator.comparingInt((Friends.Bond b) -> b.state.ordinal()).thenComparingInt(b -> -b.stage.ordinal()));
		java.util.Set<UUID> listed = new java.util.HashSet<>();
		for (Friends.Bond b : sorted) {
			listed.add(b.drifter);
			String standing = switch (b.state) {
				case ACTIVE -> capital(b.stage.word);
				case COOLED -> "Used to be friends";
				case ENDED -> "Fell out";
				case GONE -> "Gone";
			};
			int ink = switch (b.state) {
				case ACTIVE -> b.friend() ? GOOD : PLAIN;
				case COOLED, GONE -> FAINT;
				case ENDED -> BAD;
			};
			List<String> detail = new ArrayList<>();
			detail.add(b.drifterName + ": " + switch (b.state) {
				case ACTIVE -> "your " + b.stage.word + ((now - b.since) / 24000L < 1 ? " since today" : " for " + days((now - b.since) / 24000L) + " now");
				case COOLED -> "you used to be friends";
				case ENDED -> "you fell out";
				case GONE -> "gone for good";
			});
			if (!b.why.isEmpty() && b.state != Friends.State.ACTIVE) {
				detail.add("Why: " + b.why);
			}

			if (!b.town.isEmpty()) {
				detail.add("Lives in " + b.town);
			}

			if (b.where != null && b.state != Friends.State.GONE) {
				long apart = (now - b.lastTogether) / 24000L;
				detail.add("Last seen " + (apart < 1 ? "today" : days(apart) + " ago") + " at " + b.where.getX() + ", " + b.where.getZ());
			}

			if (b.saved) {
				detail.add("You saved their life once.");
			}

			if (b.agreed() > 0) {
				detail.add("You've agreed on " + b.agreed() + (b.agreed() == 1 ? " thing" : " things") + " in talk.");
			}

			if (b.favor != null && b.active()) {
				detail.add("A favor: " + favorWords(b.favor, now));
			}

			lines.add(new Line(b.drifterName, standing, ink, detail));
		}

		// Those met with no friendship to speak of: how they feel, as far as it shows.
		for (Map.Entry<UUID, String> met : mine.met.entrySet()) {
			if (listed.contains(met.getKey()) || lines.size() >= MOST) {
				continue;
			}

			DrifterRecord record = DrifterApi.record(met.getKey());
			if (record == null) {
				continue;
			}

			DrifterEntity loaded = TownFounding.loaded(server, met.getKey());
			double trust = loaded != null ? DrifterApi.trustInPlayer(loaded, id) : record.playerTrust().getOrDefault(id, 0.0);
			boolean gone = record.status() == DrifterRecord.Status.GONE;
			String standing = gone ? "Gone" : trust <= GRUDGE ? "Holds a grudge" : trust <= WARY ? "Wary of you" : trust >= LIKES ? "Likes you" : "Stranger";
			int ink = gone ? FAINT : trust <= WARY ? BAD : trust >= LIKES ? GOOD : PLAIN;
			lines.add(new Line(record.name(), standing, ink, List.of(record.name() + ": you've met, and that's about it so far.",
				gone ? "Gone for good." : trust <= GRUDGE ? "They hold something against you." : trust <= WARY ? "They don't trust you much."
					: trust >= LIKES ? "They think well of you. Keep talking and it could be more." : "Talk with them and help them out to get closer.")));
		}

		return new Tab("People", lines, "Nobody yet. Go say hi to someone.");
	}

	private static Tab towns(MinecraftServer server, ServerPlayer player, Map<Town, String> standings, List<Town> heardOf, List<Town> friendTowns,
		List<Friends.Bond> bonds,
		long day, @Nullable DrifterApi.PlayerBounty bounty) {
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<Town, String> entry : standings.entrySet()) {
			if (lines.size() >= MOST) {
				break;
			}

			Town town = entry.getKey();
			String standing = entry.getValue();
			List<String> detail = new ArrayList<>();
			Nations.Nation nation = Nations.of(town);
			detail.add(town.name + ": a " + kind(town) + (nation == null ? "" : " of " + nation.name));
			if (town.id.equals(Citizens.homeOf(player.getUUID()))) {
				detail.add("Your home town.");
			}

			detail.add("At " + town.center.getX() + ", " + town.center.getZ());
			detail.add("You're " + standing + " here" + (standing.equals("wanted") || standing.equals("exiled") ? "." : ", as far as you last saw."));
			List<String> friends = new ArrayList<>();
			for (Friends.Bond b : bonds) {
				if (b.active() && b.stage != Friends.Stage.STRANGER && town.name.equals(b.town)) {
					friends.add(b.drifterName + " (" + b.stage.word + ")");
				}
			}

			if (!friends.isEmpty()) {
				detail.add("Your people here: " + String.join(", ", friends));
			}

			String record = record(town, player.getUUID(), day, bounty);
			if (record != null) {
				detail.add("Your record: " + record);
			}

			lines.add(new Line(town.name, capital(standing), ink(standing), detail));
		}

		for (Town town : heardOf) {
			if (lines.size() >= MOST) {
				break;
			}

			lines.add(new Line(town.name, "Wanted (heard of)", BAD, List.of(town.name + ": you've never been, but word of what you did got there.",
				"You're wanted there.")));
		}

		for (Town town : friendTowns) {
			if (lines.size() >= MOST) {
				break;
			}

			List<String> friends = new ArrayList<>();
			for (Friends.Bond b : bonds) {
				if (b.stage != Friends.Stage.STRANGER && town.name.equals(b.town)) {
					friends.add(b.drifterName);
				}
			}

			lines.add(new Line(town.name, "Heard of", FAINT, List.of(town.name + ": you've never been, but you know someone from there.",
				"From there: " + String.join(", ", friends))));
		}

		return new Tab("Towns", lines, "You haven't been to a town yet. Follow a road.");
	}

	private static Tab law(ServerPlayer player, List<Town> towns, long day, @Nullable DrifterApi.PlayerBounty bounty) {
		List<Line> lines = new ArrayList<>();
		boolean bountyTied = false;
		for (Town town : towns) {
			Town.Criminal criminal = town.crimes.get(player.getUUID());
			if (criminal == null || lines.size() >= MOST) {
				continue;
			}

			String state = criminal.wantedUntil > 0 ? "Wanted" : criminal.exiledUntil > 0 ? "Exiled" : criminal.fine > 0 ? "Fine owed" : "On record";
			List<String> detail = new ArrayList<>();
			detail.add(town.name + ": " + record(town, player.getUUID(), day, null));
			if (bounty != null && bounty.issuer().contains(town.name)) {
				bountyTied = true;
				detail.add("A price on your head from here, for " + bounty.reason() + ". Hunters will come.");
			}

			lines.add(new Line(town.name, state, criminal.wantedUntil > 0 || criminal.exiledUntil > 0 ? BAD : PLAIN, detail));
		}

		if (bounty != null && !bountyTied) {
			lines.add(new Line("A price on your head", "Hunted", BAD, List.of("Put up by " + bounty.issuer() + ", for " + bounty.reason() + ".",
				"Hunters will come.")));
		}

		return new Tab("Law", lines, "A clean record. Nobody's after you.");
	}

	private static Tab favors(List<Friends.Bond> bonds, boolean friendsOn, long now) {
		if (!friendsOn) {
			return new Tab("Favors", List.of(), "Friendships are switched off in the settings.");
		}

		List<Line> lines = new ArrayList<>();
		for (Friends.Bond b : bonds) {
			if (b.favor == null || !b.active()) {
				continue;
			}

			int needed = FriendPerks.needed(b.favor.kind());
			long left = Math.max(0, b.favor.due() - now);
			List<String> detail = new ArrayList<>();
			detail.add(b.drifterName + " asked you: " + favorWords(b.favor, now));
			detail.add(b.favor.promised() ? "You said you would." : "You haven't said yes yet. Talk to them.");
			if (b.favor.at() != null && b.favor.kind().equals("zombies")) {
				detail.add("Their place is at " + b.favor.at().getX() + ", " + b.favor.at().getZ());
			}

			lines.add(new Line(b.drifterName, Math.min(b.favor.done(), needed) + " of " + needed + ", " + timeLeft(left), b.favor.promised() ? PLAIN : FAINT, detail));
		}

		return new Tab("Favors", lines, "Nobody's asked you for anything. Friends will.");
	}

	// ----- Words -----

	private static String favorWords(Friends.Favor favor, long now) {
		int needed = FriendPerks.needed(favor.kind());
		String what = switch (favor.kind()) {
			case "iron" -> "bring them " + needed + " iron ingots";
			case "food" -> "bring them " + needed + " food";
			default -> "kill " + needed + " monsters round their place tonight";
		};
		return what + " (" + Math.min(favor.done(), needed) + " of " + needed + " done, " + timeLeft(Math.max(0, favor.due() - now)) + ")";
	}

	private static String timeLeft(long ticks) {
		return ticks >= 24000L ? days(ticks / 24000L) + " left" : ticks >= 1000L ? ticks / 1000L + (ticks / 1000L == 1 ? " hour left" : " hours left")
			: ticks > 0 ? "almost out of time" : "out of time";
	}

	/** Their record in a town as it knows it, or null. */
	@Nullable
	private static String record(Town town, UUID player, long day, @Nullable DrifterApi.PlayerBounty bounty) {
		Town.Criminal criminal = town.crimes.get(player);
		if (criminal == null) {
			return null;
		}

		List<String> parts = new ArrayList<>();
		if (!criminal.crime.isEmpty()) {
			parts.add(criminal.crime);
		}

		if (criminal.offences > 0) {
			parts.add(criminal.offences + (criminal.offences == 1 ? " offence" : " offences"));
		}

		if (criminal.kills > 0) {
			parts.add(criminal.kills + (criminal.kills == 1 ? " killing seen" : " killings seen"));
		}

		if (criminal.fine > 0) {
			parts.add("a fine of " + DrifterApi.moneyWords(criminal.fine) + " owed");
		}

		if (criminal.wantedUntil > 0) {
			parts.add("wanted for " + Math.max(1, criminal.wantedUntil - day) + " more " + (criminal.wantedUntil - day <= 1 ? "day" : "days"));
		}

		if (criminal.exiledUntil > 0) {
			parts.add("exiled for " + Math.max(1, criminal.exiledUntil - day) + " more " + (criminal.exiledUntil - day <= 1 ? "day" : "days"));
		}

		if (bounty != null && bounty.issuer().contains(town.name)) {
			parts.add("a price on your head");
		}

		return parts.isEmpty() ? "word of you got here" : String.join(", ", parts);
	}

	private static String kind(Town town) {
		int houses = town.plots.size();
		return Towns.city(town) ? "city" : houses <= 2 ? "homestead" : houses < 8 ? "hamlet" : "town";
	}

	private static int ink(String standing) {
		return switch (standing) {
			case "welcome" -> GOOD;
			case "wary", "unwelcome", "wanted", "exiled" -> BAD;
			default -> PLAIN;
		};
	}

	private static int count(Map<Town, String> standings, String word) {
		return (int)standings.values().stream().filter(word::equals).count();
	}

	private static void addCount(List<String> counts, String what, int n) {
		if (n > 0) {
			counts.add(what + " " + n + (n == 1 ? " town" : " towns"));
		}
	}

	private static String days(long n) {
		return n + (n == 1 ? " day" : " days");
	}

	private static String capital(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** The journal as plain lines (`/drifterciv journal`, and for tests). */
	public static List<String> text(Book book) {
		List<String> out = new ArrayList<>();
		out.add(book.name() + "'s journal");
		for (Line line : book.you()) {
			out.add("  " + line.left());
		}

		for (Tab tab : book.tabs()) {
			out.add(tab.title() + ":" + (tab.lines().isEmpty() ? " " + tab.empty() : ""));
			for (Line line : tab.lines()) {
				out.add("  " + line.left() + (line.right().isEmpty() ? "" : ": " + line.right()));
				for (String more : line.detail()) {
					out.add("    " + more);
				}
			}
		}

		return out;
	}

	/** Debug: this player has been to this town and met this Drifter (for tests). */
	static void note(ServerPlayer player, @Nullable Town town, @Nullable DrifterEntity drifter) {
		Known mine = known.computeIfAbsent(player.getUUID(), k -> new Known());
		if (town != null) {
			mine.been.put(town.id, new Been(TownReputation.standing(player.server, town, player.getUUID()).word, Chronicle.day(player.server)));
		}

		if (drifter != null) {
			mine.met.put(drifter.getUUID(), drifter.getName().getString());
		}

		state(player.server).setDirty();
	}

	// ----- Saved with the world -----

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			// Version 1 is the first format.
			known.clear();
			CompoundTag players = tag.getCompound("Players");
			for (String key : players.getAllKeys()) {
				try {
					Known mine = new Known();
					CompoundTag one = players.getCompound(key);
					CompoundTag met = one.getCompound("Met");
					for (String who : met.getAllKeys()) {
						mine.met.put(UUID.fromString(who), met.getString(who));
					}

					CompoundTag been = one.getCompound("Been");
					for (String where : been.getAllKeys()) {
						CompoundTag town = been.getCompound(where);
						mine.been.put(UUID.fromString(where), new Been(town.getString("Standing"), town.getLong("Day")));
					}

					known.put(UUID.fromString(key), mine);
				} catch (Exception e) {
					DrifterCivilizations.LOGGER.warn("A journal that couldn't be read was left out", e);
				}
			}

			DrifterCivilizations.LOGGER.info("[journal] {} players' journals read", known.size());
			return new Saved();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", SAVE_VERSION);
			CompoundTag players = new CompoundTag();
			for (Map.Entry<UUID, Known> entry : known.entrySet()) {
				CompoundTag one = new CompoundTag();
				CompoundTag met = new CompoundTag();
				entry.getValue().met.forEach((who, name) -> met.putString(who.toString(), name));
				one.put("Met", met);
				CompoundTag been = new CompoundTag();
				entry.getValue().been.forEach((where, b) -> {
					CompoundTag town = new CompoundTag();
					town.putString("Standing", b.standing);
					town.putLong("Day", b.day);
					been.put(where.toString(), town);
				});
				one.put("Been", been);
				players.put(entry.getKey().toString(), one);
			}

			tag.put("Players", players);
			return tag;
		}
	}

	private static Saved state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_journal");
	}
}
