package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who each player is (the start-of-world menu, docs/superpowers/specs/2026-11-11-start-of-world-menu-design.md; the user,
 * 2026-11-11: "we get a menu, picking our name and pronouns, and a nation we want to be a citizen of, or no nation at
 * all"): asked once, the first time they enter a world, on a menu the addon's client shows ({@link Menu}); while a new
 * world's towns are built behind it, a home town for each nation first. A citizen starts in their nation's home town (an
 * ordinary town, never the capital: "for the capital for us to be discovered"), welcome in its towns from the first day
 * and looked at warily by towns at war with them ({@link #bonus}); nothing else is given. The name is what Drifters call
 * them and their pronouns how they're spoken of (told to Drifter: {@link DrifterApi#callPlayer}); both can be changed
 * later, the nation can't. They belong to the nation, not the town: a home town taken or fallen changes nothing; a
 * nation gone leaves them of none ("once of").
 */
public final class Citizens {
	private Citizens() {
	}

	private static final int SAVE_VERSION = 1;
	/** A town's opinion of one of its nation's own, and of one of a nation it's at war with (welcome is over 20, wary under -5). */
	private static final double OWN = 30.0;
	private static final double ENEMY = -10.0;
	/** A home town not made this long after it was chosen: they start where they stand. */
	private static final long PLACE_WITHIN_MS = 150_000L;

	/** A nation on the menu: its id, name, colour (a dye's id), and what's known of it, in words. */
	public record Card(String id, String name, int colour, List<String> lines) {
		static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Card::id, ByteBufCodecs.STRING_UTF8,
			Card::name, ByteBufCodecs.VAR_INT, Card::colour, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Card::lines, Card::new);
	}

	/**
	 * The menu: the first time (a nation to pick; it can't be closed) or a change (name and pronouns only); whether the
	 * world's being built behind it; whether its nations are still being planned; what they're called now; the nations.
	 */
	public record Menu(boolean first, boolean building, boolean drawing, String name, String pronouns, List<Card> cards) implements CustomPacketPayload {
		public static final Type<Menu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "who"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Menu> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Menu::first, ByteBufCodecs.BOOL,
			Menu::building, ByteBufCodecs.BOOL, Menu::drawing, ByteBufCodecs.STRING_UTF8, Menu::name, ByteBufCodecs.STRING_UTF8, Menu::pronouns,
			Card.CODEC.apply(ByteBufCodecs.list()), Menu::cards, Menu::new);

		@Override
		public Type<Menu> type() {
			return TYPE;
		}
	}

	/** What they chose: a name (empty: their Minecraft name), pronouns, a nation's id (empty: none). */
	public record Choice(String name, String pronouns, String nation) implements CustomPacketPayload {
		public static final Type<Choice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "who_choice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choice> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Choice::name,
			ByteBufCodecs.STRING_UTF8, Choice::pronouns, ByteBufCodecs.STRING_UTF8, Choice::nation, Choice::new);

		@Override
		public Type<Choice> type() {
			return TYPE;
		}
	}

	/** The answer to a choice: taken, or why not. */
	public record Answer(boolean ok, String why) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "who_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Answer::ok,
			ByteBufCodecs.STRING_UTF8, Answer::why, Answer::new);

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	/** They ask to change their name and pronouns (from the journal). */
	public record Change() implements CustomPacketPayload {
		public static final Type<Change> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("drifter_civ", "who_change"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = StreamCodec.unit(new Change());

		@Override
		public Type<Change> type() {
			return TYPE;
		}
	}

	/** A player as they chose to be. */
	private static final class Who {
		String minecraftName = "";
		String name = "";
		String pronouns = "they";
		@Nullable
		UUID nation;
		String nationName = "";
		@Nullable
		UUID home;
		String homeName = "";
		boolean chosen;
		/** Put in their home town (or never to be: an old world, no nation). */
		boolean placed = true;
		/** Not saved: when they chose, to give up on a home town that's never made. */
		long chosenAt;
	}

	private static final Map<UUID, Who> people = new HashMap<>();
	/** Online, the menu up, not chosen yet. */
	private static final Set<UUID> choosing = new HashSet<>();
	/** Of those: sent the menu while the nations were still being planned (sent again when they're ready). */
	private static final Set<UUID> drawing = new HashSet<>();

	static void register() {
		PayloadTypeRegistry.playS2C().register(Menu.TYPE, Menu.CODEC);
		PayloadTypeRegistry.playS2C().register(Answer.TYPE, Answer.CODEC);
		PayloadTypeRegistry.playC2S().register(Choice.TYPE, Choice.CODEC);
		PayloadTypeRegistry.playC2S().register(Change.TYPE, Change.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choice.TYPE, (choice, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> chose(player, choice));
		});
		ServerPlayNetworking.registerGlobalReceiver(Change.TYPE, (change, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				Who who = people.get(player.getUUID());
				if (who != null && who.chosen) {
					ServerPlayNetworking.send(player, new Menu(false, false, false, who.name, who.pronouns, List.of()));
				}
			});
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// (Names from the world before this one, in the same game, forgotten.)
			DrifterApi.forgetPlayersCalled();
			state(server);
			people.forEach((id, who) -> DrifterApi.callPlayer(id, who.minecraftName, who.name, who.pronouns));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			people.clear();
			choosing.clear();
			drawing.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(server, handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			choosing.remove(id);
			drawing.remove(id);
		});
		ServerTickEvents.END_SERVER_TICK.register(Citizens::tick);
		// `/drifterciv who`: who they chose to be (and in the log, for tests); `again` (testing): asked again as if new.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("drifterciv").then(net.minecraft.commands.Commands.literal("who").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Who who = people.get(player.getUUID());
				String text = who == null || !who.chosen ? "Not chosen yet"
					: called(player) + " (" + who.pronouns + "): " + String.join(", ", line(player.server, player.getUUID()))
						+ (who.home != null && !who.placed ? " (not at home yet)" : "");
				DrifterCivilizations.LOGGER.info("[who] {}: {}", player.getGameProfile().getName(), text);
				ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(text), false);
				return 1;
			}).then(net.minecraft.commands.Commands.literal("again").requires(source -> source.hasPermission(2)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				people.remove(player.getUUID());
				DrifterApi.callPlayer(player.getUUID(), player.getGameProfile().getName(), "", "they");
				state(player.server).setDirty();
				joined(player.server, player);
				ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Asked again"), false);
				return 1;
			})))));
	}

	// ----- Asking -----

	private static void joined(MinecraftServer server, ServerPlayer player) {
		UUID id = player.getUUID();
		String minecraftName = player.getGameProfile().getName();
		Who who = people.get(id);
		if (who != null && who.chosen) {
			if (!who.minecraftName.equals(minecraftName)) {
				who.minecraftName = minecraftName;
				state(server).setDirty();
			}

			DrifterApi.callPlayer(id, minecraftName, who.name, who.pronouns);
			if (!who.placed) {
				who.chosenAt = System.currentTimeMillis();
			}

			return;
		}

		// (A client without the addon can't be asked: of no nation, their own name, and never held waiting.)
		if (!ServerPlayNetworking.canSend(player, Menu.TYPE)) {
			Who plain = new Who();
			plain.minecraftName = minecraftName;
			plain.chosen = true;
			people.put(id, plain);
			state(server).setDirty();
			return;
		}

		choosing.add(id);
		send(server, player);
	}

	private static void send(MinecraftServer server, ServerPlayer player) {
		// (A plan that couldn't be made isn't waited for: no nations to pick from.)
		boolean planning = WorldPlan.planned && !WorldPlan.ready && WorldPlan.making();
		if (planning) {
			drawing.add(player.getUUID());
		} else {
			drawing.remove(player.getUUID());
		}

		ServerPlayNetworking.send(player, new Menu(true, WorldBuild.building, planning, "", "they", planning ? List.of() : cards(server)));
	}

	private static void tick(MinecraftServer server) {
		if (!choosing.isEmpty()) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!choosing.contains(player.getUUID())) {
					continue;
				}

				// (A world being built holds everyone already.)
				if (!WorldBuild.building) {
					WorldBuild.holdOne(player);
				}

				if (drawing.contains(player.getUUID()) && (!WorldPlan.planned || WorldPlan.ready || !WorldPlan.making())) {
					send(server, player);
				}
			}
		}

		if (server.getTickCount() % 10 != 0) {
			return;
		}

		// Those who chose a nation as the world was built: to their home town once it's made.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Who who = people.get(player.getUUID());
			if (who == null || who.placed || who.home == null) {
				continue;
			}

			Town town = town(who.home);
			// (Only as the world's built before it's played: one who left then and comes back later stays where they are.)
			if (WorldBuild.building && town != null && town.dimension.equals(Level.OVERWORLD.location().toString())) {
				ServerLevel level = server.overworld();
				BlockPos at = WorldBuild.standNear(level, town.center.getX(), town.center.getZ());
				WorldBuild.putAt(player, at, player.getYRot());
				player.setRespawnPosition(Level.OVERWORLD, at, player.getYRot(), true, false);
				who.placed = true;
				state(server).setDirty();
				DrifterCivilizations.LOGGER.info("[who] {} starts in {} at {}", who.minecraftName, town.name, at.toShortString());
			} else if (!WorldBuild.building || System.currentTimeMillis() - who.chosenAt > PLACE_WITHIN_MS) {
				who.placed = true;
				state(server).setDirty();
				DrifterCivilizations.LOGGER.warn("[who] {}'s home town {} wasn't made in time: they start where they stand", who.minecraftName, who.homeName);
			}
		}
	}

	private static void chose(ServerPlayer player, Choice choice) {
		MinecraftServer server = player.server;
		UUID id = player.getUUID();
		Who was = people.get(id);
		boolean first = was == null || !was.chosen;
		if (first && !choosing.contains(id)) {
			return;
		}

		String name = choice.name().trim().replaceAll(" +", " ");
		if (!name.isEmpty() && !name.matches("[A-Za-z0-9_ ]{3,16}")) {
			ServerPlayNetworking.send(player, new Answer(false, "A name is 3 to 16 letters, numbers, spaces or _"));
			return;
		}

		if (!name.isEmpty() && taken(server, id, name)) {
			ServerPlayNetworking.send(player, new Answer(false, "Someone here is called that already"));
			return;
		}

		String pronouns = switch (choice.pronouns()) {
			case "he" -> "he";
			case "she" -> "she";
			default -> "they";
		};
		Who who = first ? new Who() : was;
		if (first) {
			Nations.Nation nation = null;
			if (!choice.nation().isEmpty()) {
				for (Nations.Nation one : Nations.all()) {
					if (one.id.toString().equals(choice.nation()) && living(one) > 0) {
						nation = one;
					}
				}

				if (nation == null) {
					ServerPlayNetworking.send(player, new Answer(false, "That nation is no more. Pick another"));
					send(server, player);
					return;
				}
			}

			if (nation != null) {
				who.nation = nation.id;
				who.nationName = nation.name;
				who.home = home(nation);
				who.homeName = name(who.home);
				// (A world already played: they stay where they are, counted as from there.)
				who.placed = !WorldBuild.building;
				who.chosenAt = System.currentTimeMillis();
			}
		}

		who.minecraftName = player.getGameProfile().getName();
		who.name = name;
		who.pronouns = pronouns;
		who.chosen = true;
		people.put(id, who);
		state(server).setDirty();
		DrifterApi.callPlayer(id, who.minecraftName, who.name, who.pronouns);
		if (choosing.remove(id) && !WorldBuild.building) {
			WorldBuild.release(player);
		}

		drawing.remove(id);
		ServerPlayNetworking.send(player, new Answer(true, ""));
		DrifterCivilizations.LOGGER.info("[who] {} is {} ({}), {}", who.minecraftName, called(player), who.pronouns, String.join(", ", line(server, id)));
	}

	/** Another player's Minecraft name or chosen name. */
	private static boolean taken(MinecraftServer server, UUID asking, String name) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (!other.getUUID().equals(asking) && other.getGameProfile().getName().equalsIgnoreCase(name)) {
				return true;
			}
		}

		for (Map.Entry<UUID, Who> entry : people.entrySet()) {
			if (!entry.getKey().equals(asking) && (entry.getValue().name.equalsIgnoreCase(name) || entry.getValue().minecraftName.equalsIgnoreCase(name))) {
				return true;
			}
		}

		return false;
	}

	// ----- What the rest of the addon asks -----

	/** Someone online hasn't chosen yet, or chose a home town they aren't in yet (a world being built waits for them). */
	static boolean waiting(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Who who = people.get(player.getUUID());
			if (choosing.contains(player.getUUID()) || who != null && !who.placed) {
				return true;
			}
		}

		return false;
	}

	/** Not to be moved with the world's spawn: they have a home town (they're there, or on their way). */
	static boolean hasHome(UUID player) {
		Who who = people.get(player);
		return who != null && who.home != null;
	}

	/**
	 * The home towns to build before anything else while a new world's built: every nation's while someone's choosing (or
	 * nobody's here yet), then only those chosen and not reached.
	 */
	static Set<UUID> wantedHomes(MinecraftServer server) {
		Set<UUID> wanted = new HashSet<>();
		boolean all = server.getPlayerList().getPlayers().isEmpty();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Who who = people.get(player.getUUID());
			if (choosing.contains(player.getUUID())) {
				all = true;
			} else if (who != null && !who.placed && who.home != null) {
				wanted.add(who.home);
			}
		}

		if (all) {
			for (Nations.Nation nation : Nations.all()) {
				UUID home = home(nation);
				if (home != null) {
					wanted.add(home);
				}
			}
		}

		return wanted;
	}

	/** What they're called: the name they chose, or their Minecraft name. */
	static String called(ServerPlayer player) {
		Who who = people.get(player.getUUID());
		return who == null || who.name.isEmpty() ? player.getGameProfile().getName() : who.name;
	}

	/** Their nation, if they have one and it still stands. */
	@Nullable
	static Nations.Nation nation(UUID player) {
		Who who = people.get(player);
		if (who == null || who.nation == null) {
			return null;
		}

		for (Nations.Nation nation : Nations.all()) {
			if (nation.id.equals(who.nation)) {
				return nation;
			}
		}

		return null;
	}

	/** Their home town's id (where they're from), or null. */
	@Nullable
	static UUID homeOf(UUID player) {
		Who who = people.get(player);
		return who == null ? null : who.home;
	}

	/**
	 * What being a citizen adds to a town's opinion of them: one of its nation's own is welcome from the first day; one of
	 * a nation it's at war with is looked at warily. A place to start from: what they do still moves it.
	 */
	static double bonus(MinecraftServer server, Town town, UUID player) {
		Nations.Nation mine = nation(player);
		if (mine == null) {
			return 0;
		}

		if (mine.has(town.id)) {
			return OWN;
		}

		for (UUID id : mine.towns()) {
			Town fellow = town(id);
			Diplomacy.Relation relation = fellow == null ? null : Diplomacy.relation(town, fellow);
			if (relation != null && relation.war) {
				return ENEMY;
			}
		}

		return 0;
	}

	/** One of this town's nation's own. */
	static boolean ownTown(Town town, UUID player) {
		Nations.Nation mine = nation(player);
		return mine != null && mine.has(town.id);
	}

	/** For the journal's left page: "Citizen of Varnholt, from Oakhold", "Of no nation", "Of no nation (once of Varnholt)". */
	static List<String> line(MinecraftServer server, UUID player) {
		Who who = people.get(player);
		// (A world with no nations and nobody of one: nothing to say.)
		if (who == null || !who.chosen || who.nation == null && Nations.all().isEmpty()) {
			return List.of();
		}

		if (who.nation == null) {
			return List.of("Of no nation");
		}

		Nations.Nation nation = nation(player);
		if (nation == null) {
			return List.of("Of no nation", "Once of " + who.nationName + (who.homeName.isEmpty() ? "" : ", from " + who.homeName));
		}

		return List.of("Citizen of " + nation.name, who.homeName.isEmpty() ? "" : "From " + who.homeName);
	}

	// ----- The nations, as the menu tells of them -----

	private static List<Card> cards(MinecraftServer server) {
		List<Card> cards = new ArrayList<>();
		List<Nations.Nation> nations = Nations.all();
		for (Nations.Nation nation : nations) {
			int towns = living(nation);
			if (towns == 0) {
				continue;
			}

			List<String> lines = new ArrayList<>();
			WorldPlan.Place seat = WorldPlan.place(nation.capital);
			String size = towns == 1 ? "a single town on its own" : towns == 2 ? "a small realm of two towns" : towns == 3 ? "a small realm of three towns"
				: towns <= 6 ? "a fair-sized nation of several towns" : "a great nation of many towns";
			lines.add((seat == null ? "" : character(seat) + ". ") + Character.toUpperCase(size.charAt(0)) + size.substring(1) + ".");
			List<String> wars = new ArrayList<>();
			List<String> allies = new ArrayList<>();
			for (Nations.Nation other : nations) {
				if (other == nation || living(other) == 0) {
					continue;
				}

				String tie = tie(nation, other);
				if (tie.equals("war")) {
					wars.add(other.name);
				} else if (tie.equals("allied")) {
					allies.add(other.name);
				}
			}

			lines.add(wars.isEmpty() && allies.isEmpty() ? "At peace with its neighbours."
				: (wars.isEmpty() ? "" : "At war with " + list(wars) + ". ") + (allies.isEmpty() ? "" : "Allied with " + list(allies) + "."));
			String past = past(nation);
			if (past != null) {
				lines.add(past);
			}

			cards.add(new Card(nation.id.toString(), Character.toUpperCase(nation.name.charAt(0)) + nation.name.substring(1), nation.colour.getId(), lines));
		}

		return cards;
	}

	/** A people by their ruler's nature. */
	private static String character(WorldPlan.Place seat) {
		if (seat.aggression > 0.65) {
			return seat.warmth < 0.4 ? "A hard, warlike people" : "A proud people, quick to fight";
		}

		if (seat.aggression < 0.35) {
			return seat.warmth > 0.6 ? "Gentle folk who keep the peace" : "Careful people who keep to themselves";
		}

		return seat.ambition > 0.65 ? "Ambitious traders, always reaching further" : seat.warmth > 0.6 ? "Open-handed folk, easy to get along with"
			: "Steady, ordinary people";
	}

	/** Between two nations: "war" (any of their towns at war), "allied", or "". By their towns made, else by their past. */
	private static String tie(Nations.Nation a, Nations.Nation b) {
		boolean allied = false;
		boolean met = false;
		for (UUID x : a.towns()) {
			Town tx = town(x);
			for (UUID y : tx == null ? List.<UUID>of() : b.towns()) {
				Town ty = town(y);
				Diplomacy.Relation relation = ty == null ? null : Diplomacy.relation(tx, ty);
				if (relation != null) {
					met = true;
					if (relation.war) {
						return "war";
					}

					allied |= relation.pacts.contains(Diplomacy.Pact.ALLIANCE);
				}
			}
		}

		if (!met && WorldPlan.history != null && WorldPlan.nation(a.id) != null && WorldPlan.nation(b.id) != null) {
			History.Ties ties = WorldPlan.history.ties(a.id, b.id);
			return ties.war ? "war" : ties.alliance ? "allied" : "";
		}

		return allied ? "allied" : "";
	}

	/** The biggest thing in its written past (a nation formed in play has none). */
	@Nullable
	private static String past(Nations.Nation nation) {
		String[] biggest = {" conquered ", " was burned ", " took ", " rose against ", " won its freedom ", " declared war ", " became allies", " was founded"};
		String best = null;
		int rank = biggest.length;
		for (Chronicle.Entry entry : Chronicle.all()) {
			if (entry.day() >= 1 || !entry.text().contains(nation.name)) {
				continue;
			}

			// (The latest of the biggest kind.)
			for (int i = 0; i <= Math.min(rank, biggest.length - 1); i++) {
				if (entry.text().contains(biggest[i])) {
					rank = i;
					best = entry.text();
					break;
				}
			}
		}

		if (best == null) {
			return null;
		}

		// (A founding is told short: where, not who.)
		if (best.contains(" was founded") && best.contains(",")) {
			best = best.substring(0, best.indexOf(','));
		}

		return "Its past: " + best + (best.endsWith(".") ? "" : ".");
	}

	private static String list(List<String> names) {
		return names.size() == 1 ? names.get(0) : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	/** Its towns still standing (planned ones not ruins; ones made in play). */
	private static int living(Nations.Nation nation) {
		int count = 0;
		for (UUID id : nation.towns()) {
			WorldPlan.Place place = WorldPlan.place(id);
			if (place != null ? !place.ruin() : town(id) != null) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Where a nation's citizens start: an ordinary town of it, the furthest from its capital (a town before a hamlet),
	 * never a ruin; none: its capital.
	 */
	@Nullable
	static UUID home(Nations.Nation nation) {
		int[] seat = at(nation.capital);
		List<UUID> towns = new ArrayList<>();
		for (UUID id : nation.towns()) {
			WorldPlan.Place place = WorldPlan.place(id);
			if (!id.equals(nation.capital) && (place != null ? !place.ruin() : town(id) != null)) {
				towns.add(id);
			}
		}

		if (towns.isEmpty()) {
			return nation.capital;
		}

		Comparator<UUID> order = Comparator.comparing((UUID id) -> {
			WorldPlan.Place place = WorldPlan.place(id);
			return place != null && place.kind.equals("hamlet") ? 1 : 0;
		}).thenComparingDouble(id -> {
			int[] there = at(id);
			return seat == null || there == null ? 0 : -Math.hypot(there[0] - seat[0], there[1] - seat[1]);
		}).thenComparing(UUID::toString);
		towns.sort(order);
		return towns.get(0);
	}

	@Nullable
	private static int[] at(UUID id) {
		WorldPlan.Place place = WorldPlan.place(id);
		if (place != null) {
			return new int[] {place.x, place.z};
		}

		Town town = town(id);
		return town == null ? null : new int[] {town.center.getX(), town.center.getZ()};
	}

	private static String name(@Nullable UUID id) {
		WorldPlan.Place place = WorldPlan.place(id);
		Town town = place == null && id != null ? town(id) : null;
		return place != null ? place.name : town != null ? town.name : "";
	}

	@Nullable
	private static Town town(UUID id) {
		for (Town town : Towns.all()) {
			if (town.id.equals(id)) {
				return town;
			}
		}

		return null;
	}

	// ----- Saved with the world -----

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			// Version 1 is the first format.
			people.clear();
			CompoundTag players = tag.getCompound("Players");
			for (String key : players.getAllKeys()) {
				try {
					CompoundTag one = players.getCompound(key);
					Who who = new Who();
					who.minecraftName = one.getString("MinecraftName");
					who.name = one.getString("Name");
					who.pronouns = one.getString("Pronouns").isEmpty() ? "they" : one.getString("Pronouns").toLowerCase(Locale.ROOT);
					who.nation = one.hasUUID("Nation") ? one.getUUID("Nation") : null;
					who.nationName = one.getString("NationName");
					who.home = one.hasUUID("Home") ? one.getUUID("Home") : null;
					who.homeName = one.getString("HomeName");
					who.chosen = one.getBoolean("Chosen");
					who.placed = !one.contains("Placed") || one.getBoolean("Placed");
					people.put(UUID.fromString(key), who);
				} catch (Exception e) {
					DrifterCivilizations.LOGGER.warn("A player's choices that couldn't be read were left out", e);
				}
			}

			DrifterCivilizations.LOGGER.info("[who] {} players' choices read", people.size());
			return new Saved();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", SAVE_VERSION);
			CompoundTag players = new CompoundTag();
			for (Map.Entry<UUID, Who> entry : people.entrySet()) {
				Who who = entry.getValue();
				CompoundTag one = new CompoundTag();
				one.putString("MinecraftName", who.minecraftName);
				one.putString("Name", who.name);
				one.putString("Pronouns", who.pronouns);
				if (who.nation != null) {
					one.putUUID("Nation", who.nation);
					one.putString("NationName", who.nationName);
				}

				if (who.home != null) {
					one.putUUID("Home", who.home);
					one.putString("HomeName", who.homeName);
				}

				one.putBoolean("Chosen", who.chosen);
				one.putBoolean("Placed", who.placed);
				players.put(entry.getKey().toString(), one);
			}

			tag.put("Players", players);
			return tag;
		}
	}

	private static Saved state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_citizens");
	}
}
