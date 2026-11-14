package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Letters that come to the player (words, part 2; the user, 2026-11-14: "think of ways that a word can be delivered to the
 * player", then "Yes, part 2"). Each from something true: thanks for a quest they did, a leader who likes them with work
 * for them (come and see me), a threat from someone with a grudge (unsigned), a friend's warning (a price on their head,
 * wanted somewhere), news that a friend of theirs died, an invitation to a friend's town's festival, a fine they still owe,
 * a friend missing them. Written on the sender's own paper if it had any (a letter they can read with a right-click), else
 * passed on by word of mouth.
 *
 * <p>How it reaches them: a townsperson of the town holding it who sees them walks up and hands it over (or says it); a
 * friend of theirs they meet passes it on; a note on a town's board, taken when they're at the board; or left in the chest
 * by their bed in their town, by one of its people who walks there while they're away. Nothing on screen but what's handed
 * over, said, or found.
 */
final class Mail {
	private Mail() {
	}

	private static final long DAY = 24000L;
	/** A day's chance of a letter for each player (when there's anything to write about), and at most this many waiting. */
	private static final double DAILY = 0.4;
	private static final int MOST_WAITING = 3;
	/** A letter not delivered in this many days is given up. */
	private static final int LASTS = 12;
	/** Seen this lately by a townsperson: it knows they're about (ticks). */
	private static final long SEES = 100L;
	/** A courier walks up this close to hand it over; a friend passes it on from this near. */
	private static final double HANDS = 3.0;
	private static final double FRIEND_NEAR = 16.0;
	/** At a board this close, a note for them is taken. */
	private static final double AT_BOARD = 5.0;
	/** Left at their door: they're this far off (or away), one of the town this near the chest walks there. */
	private static final double AWAY = 48.0;
	private static final double DOOR_WALK = 64.0;
	/** A chest by their bed: within this of it. */
	private static final int BY_BED = 8;
	/** Trust: a leader that likes them this much sends for them; a grudge this deep writes; a friend unseen this many days misses them. */
	private static final double LIKES = 20.0;
	private static final double GRUDGE = -40.0;
	private static final int MISSES_DAYS = 7;
	/** Ticks a letter takes to be carried a block (a walk, with stops). */
	private static final double CARRIED = 8.0;
	/** A courier that didn't get there: no other for this long. */
	private static final long RETRY = 20L * 60 * 5;
	/** A summons stands this many days. */
	private static final int SUMMONS_DAYS = 7;

	static final String MAIL = "drifter_civ_mail";

	static final class Letter {
		long id;
		UUID player;
		String playerName = "";
		/** "thanks", "summons", "threat", "warning", "death", "invite", "debt", "miss". */
		String kind = "";
		@Nullable
		UUID from;
		String fromName = "";
		String fromTown = "";
		String message = "";
		boolean anon;
		/** Written on paper (else said). */
		boolean paper;
		/** "town" (a townsperson brings it), "friends" (a friend passes it on), "board", "door". */
		String channel = "town";
		@Nullable
		UUID town;
		long day;
		boolean delivered;
		/** Read (a letter opened) or heard. */
		boolean known;
		String how = "";
		/** Their bed when it was written (for a letter to their door), or 0. */
		long bed;
		/** It can't be where it's to be delivered from before this (game time): carried there from where it was written. */
		long after;
		/** Not saved: no new courier before this (a try came to nothing). */
		long retry;
		/** Not saved: who's on its way with it. */
		@Nullable
		UUID courier;
		long courierAt;

		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putLong("Id", this.id);
			t.putUUID("Player", this.player);
			t.putString("PlayerName", this.playerName);
			t.putString("Kind", this.kind);
			if (this.from != null) {
				t.putUUID("From", this.from);
			}

			t.putString("FromName", this.fromName);
			t.putString("FromTown", this.fromTown);
			t.putString("Message", this.message);
			t.putBoolean("Anon", this.anon);
			t.putBoolean("Paper", this.paper);
			t.putString("Channel", this.channel);
			if (this.town != null) {
				t.putUUID("Town", this.town);
			}

			t.putLong("Day", this.day);
			t.putBoolean("Delivered", this.delivered);
			t.putBoolean("Known", this.known);
			t.putString("How", this.how);
			t.putLong("Bed", this.bed);
			t.putLong("After", this.after);
			return t;
		}

		static Letter load(CompoundTag t) {
			Letter l = new Letter();
			l.id = t.getLong("Id");
			l.player = t.getUUID("Player");
			l.playerName = t.getString("PlayerName");
			l.kind = t.getString("Kind");
			l.from = t.hasUUID("From") ? t.getUUID("From") : null;
			l.fromName = t.getString("FromName");
			l.fromTown = t.getString("FromTown");
			l.message = t.getString("Message");
			l.anon = t.getBoolean("Anon");
			l.paper = t.getBoolean("Paper");
			l.channel = t.getString("Channel");
			l.town = t.hasUUID("Town") ? t.getUUID("Town") : null;
			l.day = t.getLong("Day");
			l.delivered = t.getBoolean("Delivered");
			l.known = t.getBoolean("Known");
			l.how = t.getString("How");
			l.bed = t.getLong("Bed");
			l.after = t.getLong("After");
			return l;
		}
	}

	private static final List<Letter> letters = new ArrayList<>();
	private static long nextId = 1;
	/** The day each player was last thought about. Not saved (at worst a second letter a day after a restart). */
	private static final Map<UUID, Long> checked = new HashMap<>();
	/** Quests thanked for, deaths told, friends' festivals asked to (so none twice). */
	private static final Set<String> told = new HashSet<>();
	/** Player: the leader sending for them, its town, until when (day). */
	private record Summons(UUID leader, UUID town, long until) {
	}

	private static final Map<UUID, Summons> summoned = new HashMap<>();
	/** Debug (`/drifterciv mail debug`): why letters wait, in the log. */
	static boolean debug;

	static void register() {
		DrifterApi.registerGoal(Deliver::new);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(Mail::load);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			letters.clear();
			checked.clear();
			told.clear();
			summoned.clear();
			doors.clear();
			debug = false;
			nextId = 1;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 13) {
				tick(server);
			}
		});
		// Debug: `/drifterciv mail` lists their letters; `mail write <kind>` writes one now (if there's anything true to say).
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("drifterciv")
			.then(Commands.literal("mail").requires(source -> source.hasPermission(2)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				List<String> lines = new ArrayList<>();
				for (Letter l : letters) {
					if (l.player.equals(player.getUUID())) {
						lines.add("#" + l.id + " " + l.kind + " from " + (l.anon ? "?" : l.fromName) + " (" + l.fromTown + "), " + l.channel + (l.paper ? ", written" : ", by word")
							+ (l.delivered ? ", delivered (" + l.how + ")" : ", waiting") + ": " + l.message);
					}
				}

				Summons s = summoned.get(player.getUUID());
				if (s != null) {
					lines.add("summoned by " + DrifterApi.record(s.leader()));
				}

				DrifterCivilizations.LOGGER.info("[mail] {}", String.join(" | ", lines));
				ctx.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "No letters." : String.join("\n", lines)), false);
				return 1;
			}).then(Commands.literal("debug").executes(ctx -> {
				debug = !debug;
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: mail reasons in the log " + (debug ? "on" : "off")), false);
				return 1;
			})).then(Commands.literal("write").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
				.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("thanks", "summons", "threat", "warning", "death", "invite",
					"debt", "miss"), builder))
				.executes(ctx -> debugWrite(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"), null))
				// (And how it's to come, for testing: town, friends, board, door; held by the nearest town.)
				.then(Commands.argument("channel", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("town", "friends", "board", "door"), builder))
					.executes(ctx -> debugWrite(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"),
						com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "channel")))))))));
	}

	private static int debugWrite(net.minecraft.commands.CommandSourceStack source, String kind, @Nullable String channel)
		throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Letter l = write(source.getServer(), player, kind);
		if (l != null && channel != null && (l.paper || !channel.equals("board") && !channel.equals("door"))) {
			Town near = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
			l.channel = channel;
			l.town = near == null ? l.town : near.id;
			save(source.getServer());
			// (A board's note: they're put by the board, to take it.)
			if (channel.equals("board") && near != null) {
				BlockPos board = near.bountyBoard != null ? near.bountyBoard : near.center;
				player.teleportTo(player.serverLevel(), board.getX() + 3.5, board.getY(), board.getZ() + 0.5, player.getYRot(), player.getXRot());
			}
		}

		String said = l == null ? "Debug: nothing true to write of that kind" : "Debug: #" + l.id + " " + l.kind + " from " + l.fromName + ", " + l.channel
			+ (l.paper ? ", written" : ", by word") + ": " + l.message;
		DrifterCivilizations.LOGGER.info("[mail] {}", said);
		source.sendSuccess(() -> Component.literal(said), false);
		return l == null ? 0 : 1;
	}

	// ---------------------------------------------------------------------------------------------
	// What there is to write
	// ---------------------------------------------------------------------------------------------

	/** The town they live in (their bed's), or null. */
	@Nullable
	private static Town home(ServerPlayer player) {
		for (Town town : Towns.all()) {
			if (Justice.citizen(player, town)) {
				return town;
			}
		}

		return null;
	}

	/** A loaded Drifter of this town with paper: one sheet out of its pockets for a letter (true), else none. */
	private static boolean paper(@Nullable DrifterEntity writer) {
		if (writer == null) {
			return false;
		}

		var pockets = writer.getInventory();
		for (int slot = 0; slot < pockets.getContainerSize(); slot++) {
			ItemStack stack = pockets.getItem(slot);
			if (stack.is(Items.PAPER) && !stack.has(DataComponents.CUSTOM_DATA)) {
				stack.shrink(1);
				return true;
			}
		}

		return false;
	}

	private static String called(@Nullable DrifterEntity drifter, ServerPlayer player) {
		return drifter == null ? player.getGameProfile().getName() : TalkApi.callName(drifter, player);
	}

	/** A line of this kind, in the writer's voice if it's about (else plainly the fallback with its words). */
	private static String line(@Nullable DrifterEntity writer, String key, String fallback, Object... vars) {
		return writer == null ? fallback : CivLines.pick(writer, key, fallback, vars);
	}

	/**
	 * A letter for them now, of this kind ({@code only}; null: whatever there is, in a shuffled order), from something true;
	 * null if there's nothing.
	 */
	@Nullable
	static Letter write(MinecraftServer server, ServerPlayer player, @Nullable String only) {
		long day = Chronicle.day(server);
		long now = server.overworld().getGameTime();
		UUID id = player.getUUID();
		Town home = home(player);
		Random random = new Random(now + id.hashCode());
		List<String> kinds = new ArrayList<>(List.of("thanks", "summons", "threat", "warning", "death", "invite", "debt", "miss"));
		Collections.shuffle(kinds, random);
		for (String kind : kinds) {
			if (only != null && !only.equals(kind)) {
				continue;
			}

			Letter l = switch (kind) {
				case "thanks" -> thanks(server, player, home, day);
				case "summons" -> summons(server, player, home, day);
				case "threat" -> threat(server, player, home);
				case "warning" -> warning(server, player, home);
				case "death" -> death(server, player, home);
				case "invite" -> invite(server, player, home, day);
				case "debt" -> debt(server, player);
				default -> miss(server, player, home, day, now);
			};
			if (l == null) {
				continue;
			}

			l.id = nextId++;
			l.player = id;
			l.playerName = player.getGameProfile().getName();
			l.kind = kind;
			l.day = day;
			// (It has to get there: from the writer's town to the one keeping it, at a walk. Found in review: letters crossed the
			// world the moment they were written.)
			Town wrote = Towns.all().stream().filter(t -> t.name.equals(l.fromTown)).findFirst().orElse(null);
			Town keeps = l.town == null ? null : Diplomacy.byId(l.town.toString());
			double far = wrote == null || keeps == null || wrote == keeps ? 0.0 : Math.hypot(wrote.center.getX() - keeps.center.getX(), wrote.center.getZ() - keeps.center.getZ());
			l.after = now + (long)(far * CARRIED);
			BlockPos bed = player.getRespawnPosition();
			l.bed = bed == null || player.getRespawnDimension() != net.minecraft.world.level.Level.OVERWORLD ? 0L : bed.asLong();
			// (Board and door need paper: a word of mouth is brought, by its town or a friend.)
			if (!l.paper && (l.channel.equals("board") || l.channel.equals("door"))) {
				l.channel = "town";
			}

			if (l.channel.equals("town") && l.town == null) {
				l.channel = "friends";
			}

			letters.add(l);
			save(server);
			DrifterCivilizations.LOGGER.info("[mail] for {}: {} from {} ({}), {}{}: {}", l.playerName, kind, l.anon ? "someone" : l.fromName, l.fromTown, l.channel,
				l.paper ? ", written" : ", by word", l.message);
			return l;
		}

		return null;
	}

	/** Thanks again, a few days on, for a quest done (from whoever asked it, who likes them). */
	@Nullable
	private static Letter thanks(MinecraftServer server, ServerPlayer player, @Nullable Town home, long day) {
		for (Quests.Quest q : Quests.doneLately(player.getUUID(), 4)) {
			String key = "thanks:" + q.id;
			DrifterRecord record = DrifterApi.record(q.giver);
			Town town = q.town == null ? null : Diplomacy.byId(q.town.toString());
			if (told.contains(key) || record == null || record.status() == DrifterRecord.Status.GONE || town == null) {
				continue;
			}

			told.add(key);
			DrifterEntity writer = TownFounding.loaded(server, q.giver);
			Letter l = from(writer, q.giver, q.giverName, town);
			l.message = line(writer, "mail.msg.thanks", "i keep thinking about what you did for us. " + Quests.title(q).toLowerCase(java.util.Locale.ROOT)
				+ ". thank you, truly. if you're ever this way, there's a place at our fire.", "player", called(writer, player), "what", Quests.title(q)
				.toLowerCase(java.util.Locale.ROOT), "town", town.name);
			// (To their door if they live in a town; else waiting where it was written.)
			l.channel = home != null && home != town ? "door" : "town";
			l.town = home != null && home != town ? home.id : town.id;
			return l;
		}

		return null;
	}

	/** A leader who likes them has work for them: come and see me. */
	@Nullable
	private static Letter summons(MinecraftServer server, ServerPlayer player, @Nullable Town home, long day) {
		if (summoned.containsKey(player.getUUID())) {
			return null;
		}

		for (Town town : Towns.all()) {
			DrifterEntity leader = town.leader == null ? null : TownFounding.loaded(server, town.leader);
			// (Not the same leader twice in a week: found in review.)
			String key = "summons:" + town.leader + ":" + player.getUUID() + ":" + day / 7;
			if (leader == null || told.contains(key) || town == home && player.blockPosition().closerThan(town.center, town.land())
				|| DrifterApi.trustInPlayer(leader, player.getUUID()) < LIKES) {
				continue;
			}

			told.add(key);

			Town.Criminal criminal = town.crimes.get(player.getUUID());
			if (criminal != null && (criminal.fine > 0 || criminal.wantedUntil > 0 || criminal.exiledUntil > 0)) {
				continue;
			}

			summoned.put(player.getUUID(), new Summons(leader.getUUID(), town.id, day + SUMMONS_DAYS));
			Letter l = from(leader, leader.getUUID(), leader.getName().getString(), town);
			l.message = line(leader, "mail.msg.summons", "come and see me in " + town.name + " when you can. i've work for someone i can trust, and it pays.", "player",
				called(leader, player), "town", town.name);
			l.channel = "friends";
			l.town = home != null ? home.id : town.id;
			if (home != null) {
				l.channel = l.paper ? "door" : "town";
			}

			return l;
		}

		return null;
	}

	/** Someone with a grudge: unsigned, on their door, or on the board of a town they'll pass. */
	@Nullable
	private static Letter threat(MinecraftServer server, ServerPlayer player, @Nullable Town home) {
		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				var r = drifter.isTemporary() ? null : drifter.getSocial().get(player.getUUID());
				Town theirs = Market.townOf(drifter);
				String key = "threat:" + drifter.getUUID() + ":" + player.getUUID() + ":" + Chronicle.day(server) / 7;
				if (r == null || !r.wronged || r.trust > GRUDGE || theirs == null || told.contains(key)) {
					continue;
				}

				told.add(key);

				Letter l = from(drifter, drifter.getUUID(), drifter.getName().getString(), theirs);
				l.anon = true;
				l.message = line(drifter, "mail.msg.threat", "we know what you did. we haven't forgotten. watch yourself.", "player", called(drifter, player));
				l.channel = home != null ? "door" : "board";
				l.town = home != null ? home.id : theirs.id;
				return l;
			}
		}

		return null;
	}

	/** A friend warns them: a price on their head, or wanted somewhere. */
	@Nullable
	private static Letter warning(MinecraftServer server, ServerPlayer player, @Nullable Town home) {
		DrifterApi.PlayerBounty bounty = DrifterApi.bounty(player.getUUID());
		String wantedIn = null;
		for (Town town : Towns.all()) {
			Town.Criminal criminal = town.crimes.get(player.getUUID());
			wantedIn = criminal != null && criminal.wantedUntil > 0 ? town.name : wantedIn;
		}

		String key = "warning:" + player.getUUID() + ":" + Chronicle.day(server) / 7;
		if (bounty == null && wantedIn == null || told.contains(key)) {
			return null;
		}

		Friends.Bond friend = friend(player, null);
		if (friend == null) {
			return null;
		}

		told.add(key);

		Town theirs = Diplomacy.townOf(friend.drifter);
		DrifterEntity writer = TownFounding.loaded(server, friend.drifter);
		Letter l = from(writer, friend.drifter, friend.drifterName, theirs);
		String what = bounty != null ? "there's a price on your head, put up by " + bounty.issuer() + ". hunters will come" : "you're wanted in " + wantedIn
			+ ". they'll come for you if you go there";
		l.message = line(writer, "mail.msg.warning", "i had to warn you: " + what + ". be careful, please.", "player", called(writer, player), "what", what);
		l.channel = "friends";
		l.town = home != null ? home.id : theirs == null ? null : theirs.id;
		if (home != null && l.paper) {
			l.channel = "door";
		}

		return l;
	}

	/** A friend of theirs died for good: someone of its town tells them. */
	@Nullable
	private static Letter death(MinecraftServer server, ServerPlayer player, @Nullable Town home) {
		for (Friends.Bond b : Friends.of(player.getUUID())) {
			DrifterRecord record = DrifterApi.record(b.drifter);
			String key = "death:" + b.drifter + ":" + player.getUUID();
			if (!b.friend() || record == null || record.status() != DrifterRecord.Status.GONE || told.contains(key)) {
				continue;
			}

			Town town = b.town.isEmpty() ? null : Towns.all().stream().filter(t -> t.name.equals(b.town)).findFirst().orElse(null);
			if (town == null) {
				continue;
			}

			told.add(key);
			UUID by = town.leader != null ? town.leader : town.keeper;
			DrifterEntity writer = by == null ? null : TownFounding.loaded(server, by);
			Letter l = from(writer, by, by == null ? "someone of " + town.name : Diplomacy.name(town, by), town);
			l.message = line(writer, "mail.msg.death", "i'm sorry to be the one to tell you. " + b.drifterName + " is gone. they spoke of you often.", "player", called(writer,
				player), "dead", b.drifterName);
			l.channel = home != null ? (l.paper ? "door" : "town") : "town";
			l.town = home != null ? home.id : town.id;
			return l;
		}

		return null;
	}

	/** A friend's town has a festival in the days ahead: come. */
	@Nullable
	private static Letter invite(MinecraftServer server, ServerPlayer player, @Nullable Town home, long day) {
		for (Friends.Bond b : Friends.of(player.getUUID())) {
			Town town = b.friend() ? Diplomacy.townOf(b.drifter) : null;
			if (town == null || town == home) {
				continue;
			}

			for (int ahead = 1; ahead <= 3; ahead++) {
				String festival = TownCulture.festival(town, day + ahead);
				String key = "invite:" + town.id + ":" + (day + ahead) + ":" + player.getUUID();
				if (festival == null || told.contains(key)) {
					continue;
				}

				told.add(key);
				DrifterEntity writer = TownFounding.loaded(server, b.drifter);
				Letter l = from(writer, b.drifter, b.drifterName, town);
				String when = ahead == 1 ? "tomorrow" : "in " + ahead + " days";
				l.message = line(writer, "mail.msg.invite", "it's " + TownCulture.name(festival) + " in " + town.name + " " + when + ". come! it won't be the same without you.",
					"player", called(writer, player), "what", TownCulture.name(festival), "town", town.name, "when", when);
				l.channel = home != null && l.paper ? "door" : "friends";
				l.town = home != null ? home.id : town.id;
				return l;
			}
		}

		return null;
	}

	/** A fine they still owe: a note on that town's board. */
	@Nullable
	private static Letter debt(MinecraftServer server, ServerPlayer player) {
		for (Town town : Towns.all()) {
			Town.Criminal criminal = town.crimes.get(player.getUUID());
			String key = "debt:" + town.id + ":" + player.getUUID() + ":" + Chronicle.day(server) / 7;
			if (criminal == null || criminal.fine <= 0 || criminal.exiledUntil > 0 || told.contains(key)) {
				continue;
			}

			told.add(key);

			UUID by = town.leader != null ? town.leader : town.keeper;
			DrifterEntity writer = by == null ? null : TownFounding.loaded(server, by);
			Letter l = from(writer, by, by == null ? "the people of " + town.name : Diplomacy.name(town, by), town);
			l.message = line(writer, "mail.msg.debt", "you still owe " + town.name + " a fine of " + DrifterApi.moneyWords(criminal.fine) + ". pay it to a guard, or don't come back.",
				"player", called(writer, player), "owed", DrifterApi.moneyWords(criminal.fine), "town", town.name);
			l.channel = "board";
			l.town = town.id;
			return l;
		}

		return null;
	}

	/** A friend who hasn't seen them in a long while misses them. */
	@Nullable
	private static Letter miss(MinecraftServer server, ServerPlayer player, @Nullable Town home, long day, long now) {
		for (Friends.Bond b : Friends.of(player.getUUID())) {
			Town town = b.friend() ? Diplomacy.townOf(b.drifter) : null;
			String key = "miss:" + b.drifter + ":" + player.getUUID() + ":" + (now / (DAY * MISSES_DAYS));
			if (town == null || now - b.lastTogether < DAY * MISSES_DAYS || told.contains(key)) {
				continue;
			}

			told.add(key);
			DrifterEntity writer = TownFounding.loaded(server, b.drifter);
			Letter l = from(writer, b.drifter, b.drifterName, town);
			l.message = line(writer, "mail.msg.miss", "it's been ages. we miss you in " + town.name + ". come back and see us.", "player", called(writer, player), "town",
				town.name);
			l.channel = home != null && l.paper ? "door" : "friends";
			l.town = home != null ? home.id : town.id;
			return l;
		}

		return null;
	}

	/** A friend of theirs (an active one), not this one. */
	@Nullable
	private static Friends.Bond friend(ServerPlayer player, @Nullable UUID not) {
		for (Friends.Bond b : Friends.of(player.getUUID())) {
			DrifterRecord record = DrifterApi.record(b.drifter);
			if (b.friend() && b.active() && !b.drifter.equals(not) && record != null && record.status() != DrifterRecord.Status.GONE) {
				return b;
			}
		}

		return null;
	}

	/** A letter from this one of this town (written if it's about with paper). */
	private static Letter from(@Nullable DrifterEntity writer, @Nullable UUID id, String name, @Nullable Town town) {
		Letter l = new Letter();
		l.from = id;
		l.fromName = name;
		l.fromTown = town == null ? "" : town.name;
		l.paper = paper(writer);
		return l;
	}

	// ---------------------------------------------------------------------------------------------
	// What the rest asks
	// ---------------------------------------------------------------------------------------------

	/** A leader sent for them: it asks them something when they're in its town (Quests); spent when asked. */
	@Nullable
	static UUID summonedBy(UUID player, Town town, long day) {
		Summons s = summoned.get(player);
		if (s == null || day > s.until()) {
			summoned.remove(player);
			return null;
		}

		// (Once they've had the letter: found in testing, it asked before its letter reached them.)
		boolean had = letters.stream().anyMatch(l -> l.player.equals(player) && l.kind.equals("summons") && l.delivered && s.leader().equals(l.from));
		return had && s.town().equals(town.id) ? s.leader() : null;
	}

	static void answered(UUID player) {
		summoned.remove(player);
	}

	/** A letter of theirs opened (LetterReading, Words.read): they know what it says now. */
	static void opened(MinecraftServer server, long id) {
		for (Letter l : letters) {
			if (l.id == id && !l.known) {
				l.known = true;
				save(server);
			}
		}
	}

	/** The journal's Quests page: letters that reached them lately. */
	static List<Journal.Line> lines(ServerPlayer player) {
		List<Journal.Line> lines = new ArrayList<>();
		for (int i = letters.size() - 1; i >= 0 && lines.size() < 8; i--) {
			Letter l = letters.get(i);
			if (!l.player.equals(player.getUUID()) || !l.delivered) {
				continue;
			}

			String title = l.anon ? "A note, unsigned" : (l.paper ? "A letter from " : "Word from ") + l.fromName + (l.fromTown.isEmpty() ? "" : " of " + l.fromTown);
			lines.add(new Journal.Line(title, l.how, l.kind.equals("threat") || l.kind.equals("debt") || l.kind.equals("warning") ? Journal.BAD : Journal.PLAIN,
				List.of(l.known ? "\"" + capital(l.message) + "\"" : "You haven't read it yet: right-click the letter.")));
		}

		return lines;
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ---------------------------------------------------------------------------------------------
	// Delivering
	// ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long day = Chronicle.day(server);
		long now = server.overworld().getGameTime();
		long time = server.overworld().getDayTime() % DAY;
		// Something to write, once a day each, in the daytime.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (time < 2000L || time > 10000L || checked.getOrDefault(player.getUUID(), -1L) == day) {
				continue;
			}

			checked.put(player.getUUID(), day);
			long waiting = letters.stream().filter(l -> l.player.equals(player.getUUID()) && !l.delivered).count();
			if (waiting < MOST_WAITING && player.getRandom().nextDouble() < DAILY) {
				write(server, player, null);
			}
		}

		boolean changed = false;
		for (Letter l : List.copyOf(letters)) {
			if (l.delivered) {
				// (Kept a while for the journal.)
				if (day - l.day > LASTS * 3) {
					letters.remove(l);
					changed = true;
				}

				continue;
			}

			if (day - l.day > LASTS) {
				letters.remove(l);
				changed = true;
				DrifterCivilizations.LOGGER.info("[mail] a letter for {} from {} was never delivered: given up", l.playerName, l.fromName);
				continue;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(l.player);
			Town town = l.town == null ? null : Diplomacy.byId(l.town.toString());
			if (l.courier != null && now - l.courierAt > 20L * 120) {
				l.courier = null;
				l.retry = now + RETRY;
			}

			if (now < l.after || now < l.retry) {
				continue;
			}

			switch (l.channel) {
				case "board" -> {
					BlockPos board = town == null ? null : town.bountyBoard != null ? town.bountyBoard : town.center;
					if (player != null && board != null && player.level().dimension().location().toString().equals(town.dimension)
						&& player.position().distanceTo(Vec3.atCenterOf(board)) <= AT_BOARD) {
						ItemStack note = letterItem(l);
						if (!player.getInventory().add(note)) {
							player.drop(note, false);
						}
						player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
						player.displayClientMessage(Component.literal("A note with your name on it, pinned to the board. You take it."), true);
						done(server, l, "pinned to " + town.name + "'s board");
					}
				}
				case "door" -> door(server, l, player, town, now);
				case "friends" -> {
					if (player != null && l.courier == null) {
						DrifterEntity friend = friendNear(server, player, l.from);
						if (friend != null) {
							send(l, friend, now);
						}
					}
				}
				default -> {
					if (player != null && l.courier == null && town != null) {
						DrifterEntity courier = courierFor(server, town, player, now);
						if (courier != null) {
							send(l, courier, now);
						}
					}
				}
			}
		}

		if (changed) {
			save(server);
		}
	}

	private static void send(Letter l, DrifterEntity courier, long now) {
		l.courier = courier.getUUID();
		l.courierAt = now;
		courier.getDrifterBrain().restart("bringing a letter to " + l.playerName);
		DrifterCivilizations.LOGGER.info("[mail] {} takes the letter for {} ({}) to them", courier.getName().getString(), l.playerName, l.channel);
	}

	/** One of the town that sees them now: its keeper first. Not asleep, fighting, or at odds with them. */
	@Nullable
	private static DrifterEntity courierFor(MinecraftServer server, Town town, ServerPlayer player, long now) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null || !player.blockPosition().closerThan(town.center, town.land() + 16)) {
			return null;
		}

		DrifterEntity best = null;
		for (UUID id : group.members) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one == null || one.level() != player.level() || one.isSleeping() || one.inAFight() || TalkApi.hostileOnSight(one) || busy(one) || TalkApi.talking(one)
				|| now - TalkApi.lastSeen(one, player.getUUID()) > SEES || TalkApi.lastSeen(one, player.getUUID()) < 0) {
				continue;
			}

			if (best == null || id.equals(town.keeper) || one.distanceTo(player) < best.distanceTo(player) && !best.getUUID().equals(town.keeper)) {
				best = one;
			}
		}

		return best;
	}

	/** A friend of theirs about, that sees them (not the one it's from: it'd be the one telling). */
	@Nullable
	private static DrifterEntity friendNear(MinecraftServer server, ServerPlayer player, @Nullable UUID from) {
		long now = server.overworld().getGameTime();
		for (Friends.Bond b : Friends.of(player.getUUID())) {
			DrifterEntity one = b.friend() && b.active() ? TownFounding.loaded(server, b.drifter) : null;
			if (one != null && one.level() == player.level() && one.distanceTo(player) <= FRIEND_NEAR && !one.isSleeping() && !one.inAFight() && !busy(one)
				&& !TalkApi.talking(one)
				&& now - TalkApi.lastSeen(one, player.getUUID()) <= SEES && TalkApi.lastSeen(one, player.getUUID()) >= 0) {
				return one;
			}

			if (debug && one != null && server.getTickCount() % 200 == 13) {
				DrifterCivilizations.LOGGER.info("[mail] debug: friend {} won't bring it: {} blocks, asleep {}, fighting {}, busy {}, last saw them {} ticks ago",
					one.getName().getString(), (int)one.distanceTo(player), one.isSleeping(), one.inAFight(), busy(one), now - TalkApi.lastSeen(one, player.getUUID()));
			}
		}

		if (debug && server.getTickCount() % 200 == 13) {
			DrifterCivilizations.LOGGER.info("[mail] debug: {} friends on record for {}", Friends.of(player.getUUID()).size(), player.getGameProfile().getName());
		}

		return null;
	}

	/** It's carrying a letter already. */
	private static boolean busy(DrifterEntity drifter) {
		return letters.stream().anyMatch(l -> !l.delivered && drifter.getUUID().equals(l.courier));
	}

	/** To their door: the chest by their bed in their town, by one of its people, while they're away. */
	private static void door(MinecraftServer server, Letter l, @Nullable ServerPlayer player, @Nullable Town town, long now) {
		if (l.courier != null || town == null) {
			return;
		}

		// (Their bed's house, one of the town's: its people know whose it is. Not loaded: it waits. Found in review: it gave up when
		// they were offline, and went to any chest near the bed, a neighbour's too.)
		BlockPos bed = l.bed == 0L ? null : BlockPos.of(l.bed);
		Town.Plot house = bed == null ? null : town.plots.stream().filter(p -> p.built && within(p, bed)).findFirst().orElse(null);
		if (house == null) {
			l.channel = "town";
			save(server);
			return;
		}

		if (!server.overworld().isLoaded(bed)) {
			return;
		}

		BlockPos chest = doorChest(server, bed, house);
		if (chest == null) {
			l.channel = "town";
			save(server);
			return;
		}

		if (player != null && player.position().distanceTo(Vec3.atCenterOf(chest)) < AWAY) {
			return;
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		for (UUID id : group.members) {
			DrifterEntity one = TownFounding.loaded(server, id);
			if (one != null && one.level() == server.overworld() && one.distanceToSqr(Vec3.atCenterOf(chest)) <= DOOR_WALK * DOOR_WALK && !one.isSleeping() && !one.inAFight()
				&& !busy(one) && !one.isTemporary() && !TalkApi.talking(one)) {
				doors.put(l.id, chest);
				send(l, one, now);
				return;
			}
		}
	}

	/** Where each letter for a door is to go (not saved: found again). */
	private static final Map<Long, BlockPos> doors = new HashMap<>();

	/** On this house's ground (a plot), across. */
	private static boolean within(Town.Plot plot, BlockPos pos) {
		return pos.getX() >= plot.origin.getX() && pos.getX() < plot.origin.getX() + plot.sizeX && pos.getZ() >= plot.origin.getZ() && pos.getZ() < plot.origin.getZ() + plot.sizeZ;
	}

	/** The chest nearest their bed in their own house. */
	@Nullable
	private static BlockPos doorChest(MinecraftServer server, BlockPos bed, Town.Plot house) {
		ServerLevel level = server.overworld();
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(bed.offset(-BY_BED, -2, -BY_BED), bed.offset(BY_BED, 2, BY_BED))) {
			if (within(house, pos) && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
				&& (best == null || pos.distSqr(bed) < best.distSqr(bed))) {
				best = pos.immutable();
			}
		}

		return best;
	}

	/** The letter as a thing: a sheet of the sender's paper, its words on it (opens with a right-click: LetterReading). */
	private static ItemStack letterItem(Letter l) {
		ItemStack letter = new ItemStack(Items.PAPER);
		CompoundTag mark = new CompoundTag();
		// (Marked as a letter for the reader (a negative number: not a quest's), and as mail.)
		mark.putLong(Words.LETTER, -l.id);
		mark.putLong(MAIL, l.id);
		mark.putString(Words.TEXT, "To " + l.playerName + ",\n\n" + capital(l.message) + (l.anon ? "" : "\n\n" + l.fromName));
		letter.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
		letter.set(DataComponents.CUSTOM_NAME, Component.literal(l.anon ? "A note for " + l.playerName : "Letter for " + l.playerName).withStyle(style -> style.withItalic(false)));
		letter.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(l.anon ? "No name on it" : "From " + l.fromName + (l.fromTown.isEmpty() ? "" : " of "
			+ l.fromTown)).withStyle(net.minecraft.ChatFormatting.GRAY), Component.literal("Right-click to read it").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))));
		return letter;
	}

	private static void done(MinecraftServer server, Letter l, String how) {
		l.delivered = true;
		l.courier = null;
		l.how = how;
		l.known = !l.paper;
		doors.remove(l.id);
		save(server);
		DrifterCivilizations.LOGGER.info("[mail] the letter for {} from {} reached them: {}", l.playerName, l.anon ? "someone" : l.fromName, how);
	}

	@Nullable
	private static Letter carried(UUID courier) {
		for (Letter l : letters) {
			if (!l.delivered && courier.equals(l.courier)) {
				return l;
			}
		}

		return null;
	}

	/** It takes the letter it carries where it's going: to them (handed over, or said), or into the chest at their door. */
	static final class Deliver extends Goals.Goal {
		@Override
		public String id() {
			return "deliver_letter";
		}

		@Override
		public String name(Goals.Context context) {
			return "take a letter to someone";
		}

		@Override
		public double urgency(Goals.Context context) {
			return carried(context.drifter().getUUID()) != null ? 1.05 : 0.0;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 5;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			MinecraftServer server = drifter.getServer();
			Letter l = carried(drifter.getUUID());
			if (l == null || server == null) {
				return null;
			}

			BlockPos chest = l.channel.equals("door") ? doors.get(l.id) : null;
			return new Activity(drifter) {
				private boolean spoke;

				@Override
				public String describe() {
					return chest != null ? "leave a letter at " + l.playerName + "'s door" : "bring " + l.playerName + " a letter";
				}

				@Override
				protected int timeoutTicks() {
					return 20 * 110;
				}

				@Override
				protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
					return false;
				}

				/** (A follow goes on till it's stopped: there, it hands it over. Found in testing: it walked beside them for ever.) */
				@Override
				protected boolean rethink() {
					ServerPlayer player = server.getPlayerList().getPlayer(l.player);
					return chest == null && this.current() instanceof NavigateTask && player != null && player.level() == this.drifter.level()
						&& player.distanceTo(this.drifter) <= HANDS && this.drifter.hasLineOfSight(player);
				}

				@Override
				protected Status next() {
					if (l.delivered || !drifter.getUUID().equals(l.courier)) {
						return Status.SUCCESS;
					}

					if (chest != null) {
						if (this.drifter.position().distanceTo(Vec3.atCenterOf(chest)) > 3.5) {
							return this.begin(new NavigateTask(this.drifter, Vec3.atCenterOf(chest), 2.5, "go to " + l.playerName + "'s door"));
						}

						// (Into the chest, as anyone puts something in one: in reach and in sight, not through a wall. Found in review.)
						var hit = this.drifter.level().clip(new net.minecraft.world.level.ClipContext(this.drifter.getEyePosition(), Vec3.atCenterOf(chest),
							net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, this.drifter));
						boolean seen = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(chest);
						if (seen && this.drifter.level().getBlockEntity(chest) instanceof Container box && add(box, letterItem(l))) {
							box.setChanged();
							DrifterApi.think(this.drifter, "left a letter for " + l.playerName + " at their door");
							done(server, l, "left in the chest by your bed");
						} else {
							l.channel = "town";
							l.courier = null;
						}

						return Status.SUCCESS;
					}

					ServerPlayer player = server.getPlayerList().getPlayer(l.player);
					if (player == null || player.level() != this.drifter.level() || player.distanceTo(this.drifter) > 64.0) {
						// (Gone off: not chased again at once. Found in testing: a new courier the next second.)
						l.courier = null;
						l.retry = this.drifter.level().getGameTime() + RETRY;
						return Status.SUCCESS;
					}

					// (Face to face: not through a wall. Found in review.)
					if (player.distanceTo(this.drifter) > HANDS || !this.drifter.hasLineOfSight(player)) {
						if (!this.spoke && player.distanceTo(this.drifter) < 24.0) {
							this.spoke = true;
							TalkApi.sayTo(this.drifter, player, CivLines.pick(this.drifter, l.channel.equals("friends") ? "mail.call.friend" : "mail.call", "hey " + called(this.drifter,
								player) + "! something came for you", "player", called(this.drifter, player)));
						}

						return this.begin(NavigateTask.follow(this.drifter, player, HANDS - 0.5));
					}

					// Handed over: the letter, or the word.
					String name = called(this.drifter, player);
					boolean own = this.drifter.getUUID().equals(l.from);
					if (l.paper) {
						this.drifter.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
						this.drifter.tossForward(letterItem(l)).setTarget(player.getUUID());
						TalkApi.sayTo(this.drifter, player, CivLines.pick(this.drifter, own ? "mail.give.own" : "mail.give", own ? "here, i wrote you this" : (l.anon
							? "someone left this for you. no name on it" : "a letter came for you, from " + l.fromName), "player", name, "who", l.anon ? "someone" : l.fromName));
						done(server, l, own ? "handed to you by " + l.fromName : "brought by " + this.drifter.getName().getString());
					} else {
						TalkApi.sayTo(this.drifter, player, own ? capital(l.message) : CivLines.pick(this.drifter, "mail.tell", (l.anon ? "someone" : l.fromName)
							+ " asked me to tell you: " + l.message, "player", name, "who", l.anon ? "someone" : l.fromName, "what", l.message));
						done(server, l, own ? "from " + l.fromName + " themself" : "told you by " + this.drifter.getName().getString());
					}

					return Status.SUCCESS;
				}
			};
		}
	}

	/** Into this container wherever there's room (true), else nothing. */
	private static boolean add(Container box, ItemStack stack) {
		for (int slot = 0; slot < box.getContainerSize(); slot++) {
			if (box.getItem(slot).isEmpty()) {
				box.setItem(slot, stack);
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved
	// ---------------------------------------------------------------------------------------------

	private static final int VERSION = 1;

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			letters.clear();
			told.clear();
			nextId = Math.max(1, tag.getLong("Next"));
			ListTag list = tag.getList("Letters", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				try {
					Letter l = Letter.load(list.getCompound(i));
					letters.add(l);
					nextId = Math.max(nextId, l.id + 1);
				} catch (Exception e) {
					DrifterCivilizations.LOGGER.warn("A letter that couldn't be read was left out", e);
				}
			}

			ListTag said = tag.getList("Told", Tag.TAG_STRING);
			for (int i = 0; i < said.size(); i++) {
				told.add(said.getString(i));
			}

			summoned.clear();
			ListTag sent = tag.getList("Summoned", Tag.TAG_COMPOUND);
			for (int i = 0; i < sent.size(); i++) {
				CompoundTag s = sent.getCompound(i);
				if (s.hasUUID("Player") && s.hasUUID("Leader") && s.hasUUID("Town")) {
					summoned.put(s.getUUID("Player"), new Summons(s.getUUID("Leader"), s.getUUID("Town"), s.getLong("Until")));
				}
			}

			return new Saved();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", VERSION);
			tag.putLong("Next", nextId);
			ListTag list = new ListTag();
			letters.forEach(l -> list.add(l.save()));
			tag.put("Letters", list);
			ListTag said = new ListTag();
			told.forEach(s -> said.add(net.minecraft.nbt.StringTag.valueOf(s)));
			tag.put("Told", said);
			ListTag sent = new ListTag();
			summoned.forEach((player, s) -> {
				CompoundTag one = new CompoundTag();
				one.putUUID("Player", player);
				one.putUUID("Leader", s.leader());
				one.putUUID("Town", s.town());
				one.putLong("Until", s.until());
				sent.add(one);
			});
			tag.put("Summoned", sent);
			return tag;
		}
	}

	private static Saved saved(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_mail");
	}

	static void load(MinecraftServer server) {
		letters.clear();
		saved(server);
	}

	private static void save(MinecraftServer server) {
		saved(server).setDirty();
	}
}
