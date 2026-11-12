package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Quests (RPG profile part 4, stage 1; docs/superpowers/specs/2026-11-12-quests-design.md; the user, 2026-11-12: "quests"):
 * what the world asks of a player, from what's happening in it, asked in person and in words, answered with a yes or a no
 * in chat or on the journal's Quests page, paid with real things the giver has. Stage 1's kinds: bring something (a town at
 * war short of iron or food; a town clean out of something), carry word (to a named person in another town: a letter if the
 * giver has paper, else by word of mouth), see to the dead (a flower on a grave). Stage 2's: find someone (a wanderer
 * brought to a town with a house standing empty, who moves in by the town's own rule), deal with someone (a Bandit about the
 * town, killed: no crime there, its leader having asked). A friend's favor ({@link FriendPerks}) and
 * a town's notices ({@link TownNotices}) are shown with them, done as they always were. Each player has their own; five open
 * at most; an offer lapses after a day at no cost; a word given and not kept costs a little trust.
 */
final class Quests {
	private Quests() {
	}

	static final int OFFERED = 0;
	static final int TAKEN = 1;
	/** Done; they've only to go back and say so (and be thanked, or paid). */
	static final int READY = 2;
	static final int DONE = 3;
	static final int FAILED = 4;
	static final int DROPPED = 5;
	/** No longer wanted, through nobody's fault (the war over, the giver gone). */
	static final int LAPSED = 6;
	static final int DECLINED = 7;

	static final class Quest {
		long id;
		UUID player;
		String playerName = "";
		/** "bring", "word", "dead", "find", "deal"; "favor" and "notice" only as a record of one done. */
		String kind = "";
		UUID giver;
		String giverName = "";
		@Nullable
		UUID town;
		String townName = "";
		/** Asked for the town (paid from its till, written in its chronicle), not for the giver themself. */
		boolean forTown;
		int state;
		/** Game time: asked, to be done by (0: no limit), ended. */
		long asked;
		long due;
		long closed;
		/** The words they asked in, and why they ask. */
		String said = "";
		String why = "";
		/** Bring: what, how many, how many handed over. */
		String item = "";
		int count;
		int given;
		/** What they'd pay, in emeralds' worth (0: thanks alone). */
		int pay;
		/** Carry word: to whom, of where; a letter (else by word of mouth). Deal with someone: whom ({@code to}, {@code toName}). */
		@Nullable
		UUID to;
		String toName = "";
		/** The town they're of (a place of the world's plan, made yet or not: nobody's there to name till it is). */
		@Nullable
		UUID toTownId;
		String toTown = "";
		String toRole = "";
		boolean letter;
		/** Where (the other town, the grave). */
		@Nullable
		BlockPos at;
		/** See to the dead: whose grave. Bring, in a war: the enemy. Find someone: who came, once they have. A record: its title. */
		String about = "";
		boolean war;
		/** Trust the giver has yet to lose over it (it wasn't about when it ended). */
		double owed;
		/** How it ended, in words. */
		String end = "";

		boolean open() {
			return this.state == OFFERED || this.state == TAKEN || this.state == READY;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("Id", this.id);
			tag.putUUID("Player", this.player);
			tag.putString("PlayerName", this.playerName);
			tag.putString("Kind", this.kind);
			tag.putUUID("Giver", this.giver);
			tag.putString("GiverName", this.giverName);
			if (this.town != null) {
				tag.putUUID("Town", this.town);
			}

			tag.putString("TownName", this.townName);
			tag.putBoolean("ForTown", this.forTown);
			tag.putInt("State", this.state);
			tag.putLong("Asked", this.asked);
			tag.putLong("Due", this.due);
			tag.putLong("Closed", this.closed);
			tag.putString("Said", this.said);
			tag.putString("Why", this.why);
			tag.putString("Item", this.item);
			tag.putInt("Count", this.count);
			tag.putInt("Given", this.given);
			tag.putInt("Pay", this.pay);
			if (this.to != null) {
				tag.putUUID("To", this.to);
			}

			tag.putString("ToName", this.toName);
			if (this.toTownId != null) {
				tag.putUUID("ToTownId", this.toTownId);
			}

			tag.putString("ToTown", this.toTown);
			tag.putString("ToRole", this.toRole);
			tag.putBoolean("Letter", this.letter);
			if (this.at != null) {
				tag.putLong("At", this.at.asLong());
			}

			tag.putString("About", this.about);
			tag.putBoolean("War", this.war);
			tag.putDouble("Owed", this.owed);
			tag.putString("End", this.end);
			return tag;
		}

		static Quest load(CompoundTag tag) {
			Quest q = new Quest();
			q.id = tag.getLong("Id");
			q.player = tag.getUUID("Player");
			q.playerName = tag.getString("PlayerName");
			q.kind = tag.getString("Kind");
			q.giver = tag.getUUID("Giver");
			q.giverName = tag.getString("GiverName");
			q.town = tag.hasUUID("Town") ? tag.getUUID("Town") : null;
			q.townName = tag.getString("TownName");
			q.forTown = tag.getBoolean("ForTown");
			q.state = tag.getInt("State");
			q.asked = tag.getLong("Asked");
			q.due = tag.getLong("Due");
			q.closed = tag.getLong("Closed");
			q.said = tag.getString("Said");
			q.why = tag.getString("Why");
			q.item = tag.getString("Item");
			q.count = tag.getInt("Count");
			q.given = tag.getInt("Given");
			q.pay = tag.getInt("Pay");
			q.to = tag.hasUUID("To") ? tag.getUUID("To") : null;
			q.toName = tag.getString("ToName");
			q.toTownId = tag.hasUUID("ToTownId") ? tag.getUUID("ToTownId") : null;
			q.toTown = tag.getString("ToTown");
			q.toRole = tag.getString("ToRole");
			q.letter = tag.getBoolean("Letter");
			q.at = tag.contains("At") ? BlockPos.of(tag.getLong("At")) : null;
			q.about = tag.getString("About");
			q.war = tag.getBoolean("War");
			q.owed = tag.getDouble("Owed");
			q.end = tag.getString("End");
			return q;
		}
	}

	private static final int SAVE_VERSION = 1;
	private static final long DAY = 24000L;
	/** Open at once at most (offers, words given, favors); notices on a board don't count. */
	static final int MOST_OPEN = 5;
	/** An offer nobody answered lapses after this long, at no cost. */
	private static final long OFFER_STANDS = DAY;
	/** A player is asked something at most this often; in one town, this often. */
	private static final long PLAYER_GAP = DAY / 2;
	private static final long TOWN_GAP = DAY;
	/** A town's board is read from this near its plaza (blocks). */
	private static final double BOARD_READ = 24.0;
	/** How near someone is to ask, to be handed word, to pay (blocks); how lately they must have seen the player (ticks). */
	private static final double NEAR = 8.0;
	private static final long SEEN_NOW = 60L;
	/** Each look for someone to ask (every five seconds near a townsperson), this often one does. */
	private static final double ASK_CHANCE = 0.25;
	/** How many they ask for: of a stranger, of someone liked, of a friend or one of their own nation. */
	private static final int[] COUNTS = {8, 16, 32};
	/** A war's ask pays this much over the goods' worth; a shortage's, this (as a notice does). */
	private static final double WAR_PREMIUM = 2.0;
	private static final double SHORT_PREMIUM = 1.6;
	/** In a war, a town with this many of something in its leader's stores isn't short of it. */
	private static final int WAR_ENOUGH = 48;
	/** Days: a war's ask at the longest, a shortage's, word going stale, a grave's flower. */
	private static final int WAR_DAYS = 7;
	private static final int SHORT_DAYS = 4;
	private static final int WORD_DAYS = 5;
	private static final int DEAD_DAYS = 10;
	private static final int FIND_DAYS = 10;
	private static final int DEAL_DAYS = 7;
	/** Find someone, deal with someone: what's paid (emeralds' worth), by how close they are (liked; a friend or its nation's own). */
	private static final int[] FIND_PAY = {6, 6, 10};
	private static final int[] DEAL_PAY = {10, 10, 16};
	/** A Bandit this near a town is its to be rid of (as its board's warning has it); one brought to a town is this near them. */
	private static final double BANDIT_NEAR = 300.0;
	private static final double BROUGHT = 16.0;
	/** Find someone: the one brought was following them this lately (ticks). */
	private static final long FOLLOWED_LATELY = 20L * 60 * 2;
	private static final double FIND_TRUST = 10.0;
	private static final double DEAL_TRUST = 12.0;
	/** Carry word: how far a person knows someone (blocks), a leader; its pay (emeralds' worth) and what each 300 blocks adds. */
	private static final double PERSON_KNOWS = 1500.0;
	private static final double LEADER_KNOWS = 3000.0;
	private static final int WORD_PAY = 2;
	private static final double WORD_PAY_EVERY = 300.0;
	private static final int WORD_PAY_MOST = 12;
	/** A grave this old (days) is no longer asked about. */
	private static final int GRIEF_DAYS = 60;
	/** Trust (-100 to 100): what someone who'd ask at all has in them (not wary of them), and who they'd ask a kindness of. */
	private static final double ASKS = -5.0;
	private static final double LIKES = 10.0;
	/** Trust: done (bring, word, the dead), a word not kept, a word given back to their face. */
	private static final double BRING_TRUST = 10.0;
	private static final double WORD_TRUST = 6.0;
	private static final double DEAD_TRUST = 8.0;
	private static final double DEAD_FAMILIARITY = 2.0;
	private static final double FAIL_TRUST = -6.0;
	private static final double DROP_TRUST = -2.0;
	/** What a town's word of them gains when they did something for it. */
	private static final double TOWN_WORD = 5.0;
	/** Done ones stay on the page this long (days: about a year); ones that came to nothing, this long. */
	private static final int DONE_DAYS = TownCulture.YEAR;
	private static final int ENDED_DAYS = 7;
	/** A bring this big (or any in a war) is written into the Chronicle as a deed. */
	private static final int BIG = 24;
	private static final String LETTER = "drifter_civ_letter";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final Set<String> MASS = Set.of("bread", "wool", "leather", "glass", "paper", "string", "coal", "wheat", "iron", "gold");

	private static final Map<Long, Quest> quests = new LinkedHashMap<>();
	/** The notices each player has seen on a board (or been told of). */
	private static final Map<UUID, Set<UUID>> noticed = new HashMap<>();
	/** What's being asked just now, for the words of the talk it's asked in: giver and player. */
	private static final Map<String, Quest> asking = new HashMap<>();
	private static long nextId = 1;

	static void register() {
		DrifterEvents.RECEIVED.register(Quests::received);
		DrifterEvents.PLAYER_ANSWERED.register((drifter, player, topic, does, yes) -> {
			if (does.equals("quest")) {
				Quest q = offered(drifter.getUUID(), player.getUUID());
				if (q != null) {
					if (yes) {
						take(player.server, q, player);
					} else {
						decline(player.server, q);
					}
				}
			}
		});
		// Someone a leader wanted dealt with, dead: by them (or with them near), it's done; otherwise it's no longer needed.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			MinecraftServer server = victim.getServer();
			if (!(victim instanceof DrifterEntity dead) || server == null) {
				return;
			}

			for (Quest q : quests.values()) {
				if (q.kind.equals("deal") && (q.state == OFFERED || q.state == TAKEN) && dead.getUUID().equals(q.to)) {
					ServerPlayer player = server.getPlayerList().getPlayer(q.player);
					// (By their hand: the blow, or the fight that led to it.)
					boolean theirs = player != null && (source.getEntity() == player || dead.getKillCredit() == player);
					if (theirs) {
						q.state = READY;
						q.end = q.toName + " is dead.";
					} else {
						close(q, LAPSED, q.toName + " is dead, and not by your doing. It isn't needed now.", server.overworld().getGameTime());
					}

					state(server).setDirty();
				}
			}
		});
		// (Its leader asked for it: none of the town holds it against them, seen or heard of. Others who liked the dead still do.)
		DrifterApi.setSanctionedKill((witness, killer, victim) -> {
			Town town = Diplomacy.townOf(witness.getUUID());
			return town != null && lawful(town, killer, victim);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(Quests::state);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			quests.clear();
			noticed.clear();
			asking.clear();
			nextId = 1;
		});
		ServerTickEvents.END_SERVER_TICK.register(Quests::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("drifterciv")
			.then(Commands.literal("quest").requires(source -> source.hasPermission(2)).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				List<String> lines = new ArrayList<>();
				for (Quest q : quests.values()) {
					if (q.player.equals(player.getUUID())) {
						lines.add("#" + q.id + " " + q.kind + " " + stateWord(q.state) + ": " + title(q) + " (" + q.giverName + " of " + q.townName + ")"
							+ (q.at == null ? "" : " at " + q.at.toShortString()) + (q.kind.equals("word") ? ", for " + q.toName + (q.to == null ? " (nobody there yet)" : "") : "") + (q.pay > 0 ? ", pays " + DrifterApi.moneyWords(q.pay) : ""));
					}
				}

				String out = lines.isEmpty() ? "No quests." : String.join("\n", lines);
				DrifterCivilizations.LOGGER.info("[quests] {}", String.join(" | ", lines));
				ctx.getSource().sendSuccess(() -> Component.literal(out), false);
				return 1;
			}).then(Commands.literal("offer").executes(ctx -> debugOffer(ctx.getSource(), null))
				.then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("war", "short", "word", "dead", "find", "deal"), builder))
					.executes(ctx -> debugOffer(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind")))))
			.then(Commands.literal("page").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				List<String> lines = Journal.text(open(player, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")));
				DrifterCivilizations.LOGGER.info("[quests] page: {}", String.join(" | ", lines));
				ctx.getSource().sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
				return 1;
			})))
			// (Testing, and it says so: the goods a bring asks for made from nothing and handed to the giver, who must be
			// loaded, as if thrown to it; half of them at a go.)
			.then(Commands.literal("hand").then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg()).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Quest q = quests.get(com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "id"));
				DrifterEntity giver = q == null ? null : TownFounding.loaded(player.server, q.giver);
				int handed = 0;
				if (q != null && giver != null && q.kind.equals("bring") && q.open()) {
					ItemStack goods = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(q.item)), Math.max(1, Math.min(q.count - q.given, (q.count + 1) / 2)));
					handed = goods.getCount();
					giver.getInventory().add(goods.copy());
					received(giver, player, goods);
				}

				int n = handed;
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + n + " made from nothing and handed over"), false);
				return n;
			})))
			// (Testing: off to where a quest is done, or back to who asked.)
			.then(Commands.literal("go").then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg()).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Quest q = quests.get(com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "id"));
				DrifterEntity to = q == null || q.to == null ? null : TownFounding.loaded(player.server, q.to);
				if (q == null || q.at == null && to == null) {
					return 0;
				}

				BlockPos at = to != null ? to.blockPosition() : q.kind.equals("dead") ? q.at.above()
					: player.serverLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.at);
				player.teleportTo(player.serverLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), player.getXRot());
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: at " + at.toShortString() + (to != null ? ", by " + q.toName : "")), false);
				return 1;
			})))
			// (Testing: the nearest Drifter with no town counts as following them along just now, as if asked to and walking behind.)
			.then(Commands.literal("follow").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				DrifterEntity nearest = null;
				for (DrifterEntity one : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(12.0), d -> d.isAlive()
					&& DrifterApi.group(d.getUUID()) == null)) {
					nearest = nearest == null || one.distanceToSqr(player) < nearest.distanceToSqr(player) ? one : nearest;
				}

				if (nearest != null) {
					nearest.getSocial().watcher().following(player);
				}

				String name = nearest == null ? "nobody without a town near" : nearest.getName().getString() + " counts as following you";
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + name), false);
				return nearest == null ? 0 : 1;
			}))
			.then(Commands.literal("back").then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg()).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				Quest q = quests.get(com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "id"));
				DrifterEntity giver = q == null ? null : TownFounding.loaded(player.server, q.giver);
				DrifterRecord record = q == null ? null : DrifterApi.record(q.giver);
				BlockPos at = giver != null ? giver.blockPosition() : record == null ? null : record.lastPos();
				if (at == null) {
					return 0;
				}

				player.teleportTo(player.serverLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), player.getXRot());
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: back by " + q.giverName + " at " + at.toShortString()), false);
				return 1;
			}))))));
	}

	private static String stateWord(int state) {
		return switch (state) {
			case OFFERED -> "offered";
			case TAKEN -> "taken";
			case READY -> "ready";
			case DONE -> "done";
			case FAILED -> "failed";
			case DROPPED -> "dropped";
			case LAPSED -> "lapsed";
			default -> "declined";
		};
	}

	private static String pair(UUID giver, UUID player) {
		return giver + "|" + player;
	}

	@Nullable
	private static Quest offered(UUID giver, UUID player) {
		for (Quest q : quests.values()) {
			if (q.state == OFFERED && q.giver.equals(giver) && q.player.equals(player)) {
				return q;
			}
		}

		return null;
	}

	private static List<Quest> of(UUID player) {
		List<Quest> out = new ArrayList<>();
		for (Quest q : quests.values()) {
			if (q.player.equals(player)) {
				out.add(q);
			}
		}

		return out;
	}

	/** How many things are asked of them now: offers, words given, and friends' favors. */
	private static int open(UUID player) {
		int n = 0;
		for (Quest q : quests.values()) {
			if (q.player.equals(player) && q.open()) {
				n++;
			}
		}

		if (CivConfig.friends().on) {
			for (Friends.Bond b : Friends.of(player)) {
				if (b.favor != null && b.active()) {
					n++;
				}
			}
		}

		return n;
	}

	// ---------------------------------------------------------------------------------------------
	// Each tick
	// ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		long now = server.overworld().getGameTime();
		boolean changed = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}

			String dimension = player.level().dimension().location().toString();
			Town here = null;
			for (Town town : Towns.all()) {
				if (town.dimension.equals(dimension) && Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) < town.land()) {
					here = town;
					break;
				}
			}

			// (By a town's board, on its plaza: its notices are theirs to know.)
			if (here != null && !here.notices.isEmpty() && player.blockPosition().closerThan(here.center, BOARD_READ)) {
				Set<UUID> mine = noticed.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
				for (TownNotices.Notice notice : here.notices) {
					changed |= mine.add(notice.id);
				}
			}

			for (Quest q : of(player.getUUID())) {
				changed |= near(server, q, player, now);
			}

			if (here != null && server.getTickCount() % 100 == 0 && player.getRandom().nextDouble() < ASK_CHANCE) {
				changed |= ask(server, here, player, now, null) != null;
			}
		}

		if (server.getTickCount() % 100 == 0) {
			changed |= lifecycle(server, now);
		}

		if (changed) {
			state(server).setDirty();
		}
	}

	/** A quest of a player who's about: what being where they are does for it. True if it moved on. */
	private static boolean near(MinecraftServer server, Quest q, ServerPlayer player, long now) {
		// (Word by mouth; and a letter too, should it have gone astray: they're told what it said.)
		if (q.state == TAKEN && q.kind.equals("word") && q.to != null) {
			DrifterEntity to = TownFounding.loaded(server, q.to);
			if (to != null && to.level() == player.level() && to.distanceToSqr(player) <= NEAR * NEAR && sees(to, player, now) && !TalkApi.talking(to)) {
				TalkApi.sayTo(to, player, CivLines.pick(to, "quest.word.got", "from " + q.giverName + "? ok, got it. thanks for coming all this way",
					"player", TalkApi.callName(to, player), "who", q.giverName, "town", q.townName));
				DrifterApi.think(to, player.getGameProfile().getName() + " brought word from " + q.giverName);
				q.state = READY;
				return true;
			}
		}

		if (q.state == TAKEN && q.kind.equals("dead") && q.at != null && player.level() == server.overworld()
			&& player.blockPosition().closerThan(q.at, 4.0) && (q.given > 0 || flower(player.serverLevel(), q.at))) {
			q.state = READY;
			return true;
		}

		if (q.state == TAKEN && q.kind.equals("find")) {
			return brought(server, q, player, now);
		}

		if (q.state == READY) {
			DrifterEntity giver = TownFounding.loaded(server, q.giver);
			if (giver != null && giver.level() == player.level() && giver.distanceToSqr(player) <= NEAR * NEAR && sees(giver, player, now) && !TalkApi.talking(giver)) {
				finish(server, q, giver, player);
				return true;
			}
		}

		return false;
	}

	private static boolean sees(DrifterEntity drifter, ServerPlayer player, long now) {
		// (Known for who they are lately; or right in front of it, in plain sight: one busy with something else may be slow to
		// look up.)
		long seen = TalkApi.lastSeen(drifter, player.getUUID());
		return seen >= 0 && now - seen <= SEEN_NOW || drifter.distanceToSqr(player) <= 5.0 * 5.0 && drifter.hasLineOfSight(player);
	}

	/**
	 * Find someone: a wanderer who has followed them into the town (asked to, "follow me", and still at it or only just
	 * stopped; on the town's land, near them; no home, no town, no Bandit) is taken in, unless the town's people who know it
	 * have something against it, while there's still a house or a room ({@link TownLife}). Done then; they've
	 * only to tell whoever asked.
	 */
	private static boolean brought(MinecraftServer server, Quest q, ServerPlayer player, long now) {
		Town town = q.town == null ? null : Diplomacy.byId(q.town.toString());
		com.dinglebob.drifter.world.DrifterGroups.Group group = town == null || town.group == null ? null : DrifterApi.groupById(town.group);
		if (town == null || group == null || !town.dimension.equals(player.level().dimension().location().toString())
			|| Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) > town.land()) {
			return false;
		}

		ServerLevel level = player.serverLevel();
		Town.Plot empty = TownLife.emptyHouse(town);
		Town.Plot spare = empty == null ? TownLife.spareRoom(level, town) : null;
		if (empty == null && spare == null || group.members.size() >= TownLife.MOST_PEOPLE) {
			close(q, LAPSED, q.townName + " has nowhere left to put anyone. It isn't needed now.", now);
			return true;
		}

		for (DrifterEntity one : level.getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(BROUGHT), d -> d.isAlive() && !d.isTemporary())) {
			if (DrifterApi.group(one.getUUID()) != null || DrifterApi.bandit(one) || TalkApi.hostileOnSight(one) || settled(one)
				|| now - TalkApi.lastFollowed(one, q.player) > FOLLOWED_LATELY
				|| Math.hypot(one.getX() - town.center.getX(), one.getZ() - town.center.getZ()) > town.land()) {
				continue;
			}

			// (Brought by someone the town asked: taken in unless its people who know them have something against them. Not the
			// town's rule for one who just turns up, who must be liked already: a stranger they've only glanced at is no reason.)
			double trust = TownLife.trustOf(group, one, server);
			if (!Double.isNaN(trust) && trust < 0.0) {
				continue;
			}

			if (TownLife.movesIn(server, level, town, group, one, empty, spare, "brought by " + q.playerName)) {
				q.about = one.getName().getString();
				q.state = READY;
				q.end = q.about + " has moved in.";
				Towns.save(server);
				return true;
			}
		}

		return false;
	}

	/** It has a place of its own already: a house in a town, or a hut or a house it built (not just a spot it has in mind). */
	private static boolean settled(DrifterEntity one) {
		Home home = DrifterApi.home(one);
		return home != null && (home.noHut || home.hutBuilt || home.houseBuilt);
	}

	/** Did this town's leader ask this player to deal with this one (so that killing them is no crime there)? */
	static boolean lawful(Town town, UUID player, UUID dead) {
		for (Quest q : quests.values()) {
			// (While it's asked and not yet settled up; and never one of the town's own, should they have come to live there since.)
			if (q.kind.equals("deal") && q.open() && q.player.equals(player) && town.id.equals(q.town) && dead.equals(q.to)) {
				com.dinglebob.drifter.world.DrifterGroups.Group group = DrifterApi.group(dead);
				return group == null || !group.id.equals(town.group);
			}
		}

		return false;
	}

	/** A flower growing by this stone. */
	private static boolean flower(ServerLevel level, BlockPos stone) {
		if (!level.isLoaded(stone)) {
			return false;
		}

		for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-1, -1, -1), stone.offset(1, 1, 1))) {
			var state = level.getBlockState(pos);
			if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)) {
				return true;
			}
		}

		return false;
	}

	/** Offers lapsing, words running out, givers gone, wars over, trust owed, old ones dropped. True if anything changed. */
	private static boolean lifecycle(MinecraftServer server, long now) {
		boolean changed = false;
		long today = Chronicle.day(server);
		for (Quest q : List.copyOf(quests.values())) {
			if (q.owed != 0) {
				DrifterEntity giver = TownFounding.loaded(server, q.giver);
				if (giver != null) {
					DrifterApi.addTrustInPlayer(giver, q.player, q.playerName, q.owed);
					q.owed = 0;
					changed = true;
				}
			}

			if (!q.open()) {
				long age = (now - q.closed) / DAY;
				// (Not while its giver has yet to hear how it ended.)
				if (q.owed == 0 && age > (q.state == DONE ? DONE_DAYS : q.state == DECLINED ? 1 : ENDED_DAYS)) {
					quests.remove(q.id);
					changed = true;
				}

				continue;
			}

			if (q.state == OFFERED && now - q.asked > OFFER_STANDS) {
				close(q, DECLINED, "", now);
				changed = true;
				continue;
			}

			// Whoever asked is gone: their town honours what was asked for it; what was their own ends with them.
			DrifterRecord giver = DrifterApi.record(q.giver);
			// (No record yet: one only just come into the world, not one gone from it. None for long, and not about: gone.)
			if (giver != null ? giver.status() == DrifterRecord.Status.GONE : now - q.asked > 1200L && TownFounding.loaded(server, q.giver) == null) {
				Town town = q.town == null ? null : Diplomacy.byId(q.town.toString());
				DrifterRecord next = null;
				if (q.forTown && town != null) {
					// (Its leader now, else its keeper: whoever speaks for it and lives.)
					for (UUID one : new UUID[] {town.leader, town.keeper}) {
						DrifterRecord record = one == null || one.equals(q.giver) ? null : DrifterApi.record(one);
						if (next == null && record != null && record.status() != DrifterRecord.Status.GONE) {
							next = record;
						}
					}
				}

				if (next != null) {
					q.giver = next.id();
					q.end = q.giverName + " is gone. " + next.name() + " speaks for " + q.townName + " now, and stands by it.";
					q.giverName = next.name();
					changed = true;
				} else if (!q.forTown || town == null || town.group == null || q.due > 0 && now > q.due) {
					// (A town's own: it has till the quest's time is up to find someone to speak for it.)
					close(q, LAPSED, q.giverName + " is gone. Nobody's waiting on it now.", now);
					changed = true;
				}

				continue;
			}

			if (q.kind.equals("word") && q.state != READY) {
				// Who it's for: the one named, while they live; else (or once their town is there to have one) its leader now.
				DrifterRecord to = q.to == null ? null : DrifterApi.record(q.to);
				Town known = q.toTownId == null ? null : Diplomacy.byId(q.toTownId.toString());
				// (Its leader, there but not yet on the world's record: fine as they are.)
				if ((to == null || to.status() == DrifterRecord.Status.GONE) && !(to == null && q.to != null && known != null && q.to.equals(known.leader))) {
					Town other = known;
					WorldPlan.Place place = WorldPlan.place(q.toTownId);
					// (Its leader's record may not be written yet in a town only just there: its group knows the name.)
					DrifterRecord leader = other == null || other.leader == null ? null : DrifterApi.record(other.leader);
					if (other != null && other.leader != null && (leader == null || leader.status() != DrifterRecord.Status.GONE)) {
						String name = leader != null ? leader.name() : Diplomacy.name(other, other.leader);
						if (q.to != null || !name.equals(q.toName)) {
							q.end = (q.toName.isEmpty() ? "" : q.toName + " isn't there any more. ") + name + " leads " + q.toTown + " now: it's for them.";
						}

						q.to = other.leader;
						q.toName = name;
						q.toRole = "leader";
						changed = true;
					} else if (q.to != null || place != null && place.ruin() || other == null && place == null) {
						close(q, LAPSED, (q.toName.isEmpty() ? "The leader of " + q.toTown : q.toName) + " is gone. There's nobody to carry it to.", now);
						changed = true;
						continue;
					}
				}
			}

			// (Whoever was to be dealt with, gone for good some other way.)
			if (q.kind.equals("deal") && q.state != READY) {
				DrifterRecord target = q.to == null ? null : DrifterApi.record(q.to);
				// (No record yet: one only just come into the world, not one gone from it.)
				if (target != null && target.status() == DrifterRecord.Status.GONE) {
					close(q, LAPSED, q.toName + " is gone, and not by your doing. It isn't needed now.", now);
					changed = true;
					continue;
				}
			}

			if (q.kind.equals("bring") && q.war) {
				Town town = q.town == null ? null : Diplomacy.byId(q.town.toString());
				if (town == null || WorldPlan.enemyOf(town) == null) {
					close(q, LAPSED, "The war's over. It isn't needed now.", now);
					changed = true;
					continue;
				}
			}

			if (q.state == TAKEN && q.due > 0 && now > q.due) {
				close(q, FAILED, "It ran out of time. " + q.giverName + " thinks a little less of you" + (q.forTown ? ", and so does " + q.townName : "") + ".", now);
				q.owed = FAIL_TRUST;
				// (A town let down hears of it.)
				Town town = q.forTown && q.town != null ? Diplomacy.byId(q.town.toString()) : null;
				if (town != null) {
					town.heard.merge(q.player, -TOWN_WORD, (a, b) -> Math.max(-100.0, a + b));
					Towns.save(server);
				}

				changed = true;
			}
		}

		// (Notices long taken down: forgotten.)
		if (today % 7 == 0 && server.getTickCount() % 2400 == 0) {
			Set<UUID> up = new HashSet<>();
			for (Town town : Towns.all()) {
				for (TownNotices.Notice notice : town.notices) {
					up.add(notice.id);
				}
			}

			for (Set<UUID> mine : noticed.values()) {
				changed |= mine.retainAll(up);
			}
		}

		return changed;
	}

	private static void close(Quest q, int state, String end, long now) {
		q.state = state;
		q.closed = now;
		if (!end.isEmpty()) {
			q.end = end;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Asking
	// ---------------------------------------------------------------------------------------------

	/**
	 * Someone of this town near the player asks them something, if anyone would and has something to ask. {@code only}
	 * (testing): this kind, whatever the hour and however lately they were asked. The quest offered, or null.
	 */
	@Nullable
	private static Quest ask(MinecraftServer server, Town town, ServerPlayer player, long now, @Nullable String only) {
		UUID id = player.getUUID();
		if (open(id) >= MOST_OPEN) {
			return null;
		}

		if (only == null) {
			for (Quest q : quests.values()) {
				// (What was asked in person; not a favor or a notice kept here once done.)
				if (q.player.equals(id) && !q.kind.equals("favor") && !q.kind.equals("notice")
					&& (now - q.asked < PLAYER_GAP || town.id.equals(q.town) && now - q.asked < TOWN_GAP)) {
					return null;
				}
			}
		}

		// (A wary town doesn't ask; nor one they're wanted in, or owe.)
		Town.Criminal criminal = town.crimes.get(id);
		TownReputation.Standing standing = TownReputation.standing(server, town, id);
		if (criminal != null && (criminal.fine > 0 || criminal.wantedUntil > 0 || criminal.exiledUntil > 0)
			|| standing != TownReputation.Standing.WELCOME && standing != TownReputation.Standing.NEUTRAL) {
			return null;
		}

		List<DrifterEntity> about = player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(only == null ? NEAR : 64.0),
			drifter -> drifter.isAlive() && !drifter.isTemporary() && !TalkApi.hostileOnSight(drifter));
		Collections.shuffle(about, new java.util.Random(now));
		for (DrifterEntity giver : about) {
			// (As it would start any talk: not asleep, busy talking or at odds with them, they not mid-talk or with a question
			// of someone else's still to answer.)
			if (only == null && (!sees(giver, player, now) || !TalkApi.freeToTalk(giver, player) || TalkApi.talking(giver))
				|| Diplomacy.townOf(giver.getUUID()) != town || DrifterApi.trustInPlayer(giver, id) < ASKS) {
				if (only != null) {
					DrifterCivilizations.LOGGER.info("[quests] debug: {} wouldn't ask (talking {}, of this town {}, trust {})", giver.getName().getString(),
						TalkApi.talking(giver), Diplomacy.townOf(giver.getUUID()) == town, DrifterApi.trustInPlayer(giver, id));
				}

				continue;
			}

			// (Nobody asks again while they're owed one.)
			Friends.Bond bond = Friends.bond(giver.getUUID(), id);
			boolean owes = bond != null && bond.favor != null;
			for (Quest q : quests.values()) {
				owes |= q.open() && q.player.equals(id) && q.giver.equals(giver.getUUID());
			}

			if (owes) {
				continue;
			}

			// How close: a stranger, someone they like (or a town that does), a friend or one of their own nation.
			Nations.Nation theirs = Citizens.nation(id);
			Nations.Nation its = Nations.of(town);
			int level = bond != null && bond.friend() || theirs != null && its != null && theirs.id.equals(its.id) ? 2
				: DrifterApi.trustInPlayer(giver, id) >= LIKES || standing == TownReputation.Standing.WELCOME ? 1 : 0;
			boolean speaksForTown = giver.getUUID().equals(town.leader) || giver.getUUID().equals(town.keeper);
			List<String> kinds = new ArrayList<>(List.of("word", "dead", "short", "find", "deal"));
			Collections.shuffle(kinds, new java.util.Random(now + giver.getId()));
			// (A town at war asks for what the war needs before anything else.)
			kinds.add(0, "war");
			for (String kind : kinds) {
				if (only != null && !only.equals(kind)) {
					continue;
				}

				Quest q = switch (kind) {
					case "war" -> speaksForTown ? bring(server, town, giver, player, level, true) : null;
					case "short" -> speaksForTown ? bring(server, town, giver, player, level, false) : null;
					case "word" -> word(server, town, giver, player, level);
					// (Big things: asked of someone liked, a friend, one of its own. Testing: of anyone.)
					case "find" -> speaksForTown && (level >= 1 || only != null) ? find(server, town, giver, player, level) : null;
					case "deal" -> giver.getUUID().equals(town.leader) && (level >= 1 || only != null) ? deal(server, town, giver, player, level) : null;
					// (A kindness: asked of someone they like. Testing: of anyone.)
					default -> level >= 1 || only != null ? dead(server, town, giver, player) : null;
				};
				if (q == null) {
					if (only != null) {
						DrifterCivilizations.LOGGER.info("[quests] debug: {} has no {} to ask (speaks for the town {}, closeness {})", giver.getName().getString(),
							kind, speaksForTown, level);
					}

					continue;
				}

				q.id = nextId;
				q.player = id;
				q.playerName = player.getGameProfile().getName();
				q.giver = giver.getUUID();
				q.giverName = giver.getName().getString();
				q.town = town.id;
				q.townName = town.name;
				q.state = OFFERED;
				q.asked = now;
				// (Testing: put by whoever asks, so it's said to their face as it would be.)
				if (only != null && giver.distanceToSqr(player) > 9.0) {
					player.teleportTo(player.serverLevel(), giver.getX() + 1.5, giver.getY(), giver.getZ(), player.getYRot(), player.getXRot());
				}

				asking.put(pair(q.giver, id), q);
				boolean asked = TalkApi.startTalk(giver, player, "quest_ask");
				asking.remove(pair(q.giver, id));
				if (!asked) {
					return null;
				}

				nextId++;
				quests.put(q.id, q);
				DrifterApi.think(giver, "asking " + q.playerName + ": " + title(q).toLowerCase(Locale.ROOT));
				DrifterCivilizations.LOGGER.info("[quests] {} of {} asks {}: {} (#{}, {}) \"{}\"", q.giverName, q.townName, q.playerName, title(q), q.id, q.kind, q.said);
				return q;
			}
		}

		return null;
	}

	/** The words of the talk a quest is asked in (the topic "quest_ask": what it asks, what it says to a yes and to a no). */
	static void facts(DrifterEntity drifter, Player player, Map<String, Object> words) {
		Quest q = asking.get(pair(drifter.getUUID(), player.getUUID()));
		if (q != null) {
			words.put("q_ask", q.said);
			String called = TalkApi.callName(drifter, player);
			words.put("q_yes", CivLines.pick(drifter, "quest.yes." + q.kind, "thank you, really", "player", called));
			words.put("q_no", CivLines.pick(drifter, "quest.no", "ok, no worries", "player", called));
		}
	}

	/** What a town at war is short of, in the order it's asked for. */
	private static final List<Item> WAR_WANTS = List.of(Items.IRON_INGOT, Items.BREAD, Items.ARROW, Items.COOKED_BEEF, Items.LEATHER);

	/** Bring something: a town at war short of what a war takes, or (not at war) clean out of something it uses. */
	@Nullable
	private static Quest bring(MinecraftServer server, Town town, DrifterEntity giver, ServerPlayer player, int level, boolean war) {
		String enemy = WorldPlan.enemyOf(town);
		if (war != (enemy != null)) {
			return null;
		}

		Home home = DrifterApi.home(giver);
		if (home == null) {
			return null;
		}

		List<Item> wants = new ArrayList<>(war ? WAR_WANTS : Market.wanted());
		if (!war) {
			wants.addAll(List.of(Items.GLASS, Items.STONE_BRICKS, Items.WHITE_WOOL, Items.STRING, Items.IRON_INGOT, Items.LEATHER, Items.PAPER));
			Collections.shuffle(wants, new java.util.Random(Chronicle.day(server) * 17 + town.id.hashCode()));
		}

		for (Item item : wants) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			// (Not what a notice of its already asks for. What it holds itself counts as the town's: goods just brought it.)
			int has = home.stored(item) + giver.getInventory().count(stack -> stack.is(item));
			if (has >= (war ? WAR_ENOUGH : 1) || town.notices.stream().anyMatch(n -> n.item.equals(id))) {
				continue;
			}

			Quest q = new Quest();
			q.kind = "bring";
			q.forTown = true;
			q.war = war;
			q.item = id;
			q.count = COUNTS[level];
			q.about = war ? enemy : "";
			// (By what a stall sells it for where Numismatic Overhaul lists a price, else by its worth: never so much over that
			// buying it at a stall to hand in would pay.)
			Double listed = DrifterApi.listedPoints(item, true);
			double each = listed != null ? listed : DrifterApi.worth(new ItemStack(item));
			int wanted = (int)Math.max(1, Math.round(each * q.count * (war ? WAR_PREMIUM : SHORT_PREMIUM) / 6.0));
			q.pay = Math.min(wanted, has(server, town, giver, true));
			q.due = Friends.now() + (war ? WAR_DAYS : SHORT_DAYS) * DAY;
			String what = things(q.count, item);
			q.why = war ? town.name + " is at war with " + enemy + ", and its stores are short of " + plural(name(item)) + "."
				: town.name + " has no " + plural(name(item)) + " left in its stores.";
			q.said = CivLines.pick(giver, war ? "quest.ask.war" : "quest.ask.short", war ? "we're at war with " + enemy + " and short of " + plural(name(item))
				+ ". could you bring " + what + "? " + (q.pay > 0 ? "the town will pay " + DrifterApi.moneyWords(q.pay) : "the till's empty but we won't forget it")
				: "we're clean out of " + plural(name(item)) + ". could you bring " + what + "? "
					+ (q.pay > 0 ? "the town will pay " + DrifterApi.moneyWords(q.pay) : "the till's empty but we won't forget it"),
				"player", TalkApi.callName(giver, player), "what", what, "thing", plural(name(item)), "enemy", enemy == null ? "" : enemy, "town", town.name,
				"pay", q.pay > 0 ? "the town will pay " + DrifterApi.moneyWords(q.pay) : "the till's empty but we won't forget it");
			return q;
		}

		return null;
	}

	/** Carry word: to someone they know in another town (a leader to a leader; anyone else to its keeper, or its leader). */
	@Nullable
	private static Quest word(MinecraftServer server, Town town, DrifterEntity giver, ServerPlayer player, int level) {
		boolean leader = giver.getUUID().equals(town.leader);
		if (!town.dimension.equals(OVERWORLD)) {
			return null;
		}

		// The towns it knows of, by the world's plan: there whether or not anyone has been near enough for its people to be.
		UUID own = WorldPlan.powerOf(town);
		List<WorldPlan.Place> others = new ArrayList<>();
		for (WorldPlan.Place place : WorldPlan.places) {
			double far = Math.hypot(place.x - town.center.getX(), place.z - town.center.getZ());
			if (place.id.equals(town.id) || place.ruin() || far > (leader ? LEADER_KNOWS : PERSON_KNOWS) || far < 200
				|| Annals.atWar(own, WorldPlan.powerOf(place))) {
				continue;
			}

			others.add(place);
		}

		Collections.shuffle(others, new java.util.Random(Friends.now() + giver.getId()));
		for (WorldPlan.Place place : others) {
			// Who it's for: in a town that's there, its leader (a leader's word) or its keeper; in one that isn't yet, its
			// leader, as the world's summary names them.
			Town other = Diplomacy.byId(place.id.toString());
			UUID to = other == null ? null : leader || other.keeper == null ? other.leader : other.keeper;
			DrifterRecord record = to == null ? null : DrifterApi.record(to);
			if (other != null && (other.group == null || record == null || record.status() == DrifterRecord.Status.GONE) || other == null && place.leader.isEmpty()) {
				continue;
			}

			// (Not to the same town twice at once.)
			boolean already = false;
			for (Quest q : quests.values()) {
				already |= q.open() && q.player.equals(player.getUUID()) && place.id.equals(q.toTownId);
			}

			if (already) {
				continue;
			}

			Quest q = new Quest();
			q.kind = "word";
			q.forTown = leader;
			q.to = to;
			q.toTownId = place.id;
			q.toName = record != null ? record.name() : place.leader;
			q.toTown = place.name;
			q.toRole = other == null || to.equals(other.leader) ? "leader" : "shopkeeper";
			q.at = other != null ? other.center : new BlockPos(place.x, 64, place.z);
			double far = Math.hypot(place.x - town.center.getX(), place.z - town.center.getZ());
			// (A stranger is offered half.)
			int wanted = (int)Math.min(WORD_PAY_MOST, WORD_PAY + far / WORD_PAY_EVERY);
			wanted = level == 0 ? Math.max(1, wanted / 2) : wanted;
			q.pay = Math.min(wanted, has(server, town, giver, leader));
			q.due = Friends.now() + WORD_DAYS * DAY;
			q.why = giver.getName().getString() + (leader ? ", who leads " + town.name + "," : " of " + town.name) + " has word for " + q.toName + ", the "
				+ q.toRole + " of " + place.name + ", and can't leave to take it.";
			String pay = q.pay > 0 ? "i'll pay " + DrifterApi.moneyWords(q.pay) + " when you're back" : "i cant pay but id owe you one";
			q.said = CivLines.pick(giver, leader ? "quest.ask.word.leader" : "quest.ask.word", "could you take word to " + q.toName + " in " + place.name + " for me? "
				+ pay, "player", TalkApi.callName(giver, player), "who", q.toName, "town", place.name, "pay", pay);
			return q;
		}

		return null;
	}

	/** See to the dead: a flower for the grave of one of its town's people, lately gone. */
	@Nullable
	private static Quest dead(MinecraftServer server, Town town, DrifterEntity giver, ServerPlayer player) {
		long today = Chronicle.day(server);
		if (!town.dimension.equals(OVERWORLD)) {
			return null;
		}

		List<TownGraves.Grave> graves = new ArrayList<>(town.graves);
		Collections.shuffle(graves, new java.util.Random(today + giver.getId()));
		for (TownGraves.Grave grave : graves) {
			if (grave.stone() == null || today - grave.day() > GRIEF_DAYS || grave.name().equals(giver.getName().getString())) {
				continue;
			}

			boolean already = false;
			for (Quest q : quests.values()) {
				already |= q.player.equals(player.getUUID()) && q.kind.equals("dead") && grave.stone().equals(q.at);
			}

			if (already) {
				continue;
			}

			Quest q = new Quest();
			q.kind = "dead";
			q.about = grave.name();
			q.at = grave.stone();
			q.due = Friends.now() + DEAD_DAYS * DAY;
			q.why = grave.name() + " of " + town.name + " died " + ago(today - grave.day()) + " and lies in its graveyard. " + giver.getName().getString()
				+ " misses them, and hasn't the heart to go.";
			q.said = CivLines.pick(giver, "quest.ask.dead", "i still think about " + grave.name() + ". would you put a flower on their grave for me? i cant go myself",
				"player", TalkApi.callName(giver, player), "dead", grave.name(), "town", town.name);
			return q;
		}

		return null;
	}

	/** Find someone: a town with a house standing empty wants someone to live in it. */
	@Nullable
	private static Quest find(MinecraftServer server, Town town, DrifterEntity giver, ServerPlayer player, int level) {
		if (TownLife.emptyHouse(town) == null) {
			return null;
		}

		for (Quest other : quests.values()) {
			if (other.open() && other.kind.equals("find") && town.id.equals(other.town)) {
				return null;
			}
		}

		Quest q = new Quest();
		q.kind = "find";
		q.forTown = true;
		q.pay = Math.min(FIND_PAY[level], has(server, town, giver, true));
		q.due = Friends.now() + FIND_DAYS * DAY;
		long day = Chronicle.day(server);
		String lost = town.lastLeft != null && day - town.lastLeftDay <= 30 ? town.lastLeft + " left, and their house stands empty. " : "A house in "
			+ town.name + " stands empty. ";
		q.why = lost + town.name + " wants someone to live in it and take up work.";
		String pay = q.pay > 0 ? "the town will pay " + DrifterApi.moneyWords(q.pay) : "the till's empty but we won't forget it";
		q.said = CivLines.pick(giver, "quest.ask.find", "we've a house standing empty and work going. if you meet someone with no home, bring them to us? " + pay,
			"player", TalkApi.callName(giver, player), "town", town.name, "pay", pay);
		return q;
	}

	/**
	 * Deal with someone: a Bandit about the town whom one of its people has seen and knows for who they are (its senses'
	 * own word), of no town and nobody's hidden crook, and not one it has asked about before.
	 */
	@Nullable
	private static Quest deal(MinecraftServer server, Town town, DrifterEntity giver, ServerPlayer player, int level) {
		ServerLevel world = TownLife.level(server, town);
		if (world == null) {
			return null;
		}

		for (DrifterEntity one : world.getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(town.center).inflate(BANDIT_NEAR, 64, BANDIT_NEAR),
			d -> d.isAlive() && !d.isTemporary())) {
			// (Not one who lives in a town, its own or another's: a crook nobody knows for one.)
			if (!DrifterApi.bandit(one) || DrifterApi.group(one.getUUID()) != null) {
				continue;
			}

			boolean crook = false;
			for (Town other : Towns.all()) {
				crook |= other.crooks.contains(one.getUUID());
			}

			// (Nor one this town has asked about before, done or not: back from the dead, they're not paid for twice.)
			boolean already = false;
			for (Quest other : quests.values()) {
				already |= other.kind.equals("deal") && one.getUUID().equals(other.to) && town.id.equals(other.town);
			}

			if (crook || already || !known(server, town, one)) {
				continue;
			}

			Quest q = new Quest();
			q.kind = "deal";
			q.forTown = true;
			q.to = one.getUUID();
			q.toName = one.getName().getString();
			q.pay = Math.min(DEAL_PAY[level], has(server, town, giver, true));
			q.due = Friends.now() + DEAL_DAYS * DAY;
			double dx = one.getX() - town.center.getX();
			double dz = one.getZ() - town.center.getZ();
			String way = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
			q.toRole = way;
			q.why = q.toName + " is a bandit, about to the " + way + " of " + town.name + ", and its people aren't safe on the roads.";
			String pay = q.pay > 0 ? "the town will pay " + DrifterApi.moneyWords(q.pay) : "the till's empty but we won't forget it";
			q.said = CivLines.pick(giver, "quest.ask.deal", "there's a bandit called " + q.toName + " out " + way + " of here. deal with them and " + pay,
				"player", TalkApi.callName(giver, player), "who", q.toName, "way", way, "town", town.name, "pay", pay);
			return q;
		}

		return null;
	}

	/** Has one of this town's people (loaded) seen this one and known them for who they are? */
	private static boolean known(MinecraftServer server, Town town, DrifterEntity one) {
		com.dinglebob.drifter.world.DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return false;
		}

		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			var seen = member == null ? null : member.getSenses().awarenessOf(one);
			if (seen != null && seen.identified) {
				return true;
			}
		}

		return false;
	}

	/** What it could pay with, in whole emeralds' worth: its own money, and (asking for the town) the till's. */
	private static int has(MinecraftServer server, Town town, DrifterEntity giver, boolean till) {
		int has = DrifterApi.moneyHeld(giver.getInventory());
		Container chest = till ? TownNotices.till(server, town) : null;
		return has + (chest == null ? 0 : DrifterApi.moneyHeld(chest));
	}

	// ---------------------------------------------------------------------------------------------
	// Answering, doing, ending
	// ---------------------------------------------------------------------------------------------

	/** Words given and not yet kept: quests taken, favors promised. */
	private static int given(UUID player) {
		int n = 0;
		for (Quest q : quests.values()) {
			if (q.player.equals(player) && (q.state == TAKEN || q.state == READY)) {
				n++;
			}
		}

		if (CivConfig.friends().on) {
			for (Friends.Bond b : Friends.of(player)) {
				if (b.favor != null && b.active() && b.favor.promised()) {
					n++;
				}
			}
		}

		return n;
	}

	/** Yes. False: they've given their word to enough already (the offer stands till it lapses). */
	private static boolean take(MinecraftServer server, Quest q, ServerPlayer player) {
		if (q.state != OFFERED || given(q.player) >= MOST_OPEN) {
			return false;
		}

		q.state = TAKEN;
		DrifterEntity giver = TownFounding.loaded(server, q.giver);
		if (q.kind.equals("word") && giver != null && giver.level() == player.level() && giver.distanceToSqr(player) <= 12.0 * 12.0) {
			// (A letter, if it has paper to write one on: its own paper, handed over. Else by word of mouth.)
			for (int slot = 0; slot < giver.getInventory().getContainerSize(); slot++) {
				ItemStack stack = giver.getInventory().getItem(slot);
				if (stack.is(Items.PAPER) && !stack.has(DataComponents.CUSTOM_DATA)) {
					ItemStack letter = stack.split(1);
					CompoundTag mark = new CompoundTag();
					mark.putLong(LETTER, q.id);
					letter.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
					letter.set(DataComponents.CUSTOM_NAME, Component.literal("Letter for " + q.toName + " of " + q.toTown).withStyle(style -> style.withItalic(false)));
					letter.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
						Component.literal("From " + q.giverName + " of " + q.townName).withStyle(net.minecraft.ChatFormatting.GRAY))));
					giver.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
					// (Theirs to pick up, nobody else's.)
					giver.tossForward(letter).setTarget(player.getUUID());
					q.letter = true;
					break;
				}
			}
		}

		if (q.kind.equals("dead") && q.at != null && flower(server.overworld(), q.at)) {
			// (Tended already: the going there is what's asked.)
			q.given = 1;
		}

		DrifterCivilizations.LOGGER.info("[quests] {} took #{} ({}){}", q.playerName, q.id, title(q), q.letter ? ", with a letter" : "");
		state(server).setDirty();
		return true;
	}

	private static void decline(MinecraftServer server, Quest q) {
		if (q.state == OFFERED) {
			close(q, DECLINED, "", server.overworld().getGameTime());
			state(server).setDirty();
		}
	}

	/** "I can't do it after all": costs a little, less than letting it run out. */
	private static void drop(MinecraftServer server, Quest q) {
		if (q.state == TAKEN) {
			close(q, DROPPED, "You told " + q.giverName + " you couldn't do it after all.", server.overworld().getGameTime());
			q.owed = DROP_TRUST;
			state(server).setDirty();
		}
	}

	/** Would a quest count this thing thrown to this Drifter (so a notice or a favor for the same leaves it be)? */
	static boolean claims(DrifterEntity drifter, Player player, ItemStack stack) {
		return bringFor(drifter, player, stack) != null;
	}

	@Nullable
	private static Quest bringFor(DrifterEntity drifter, Player player, ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		for (Quest q : quests.values()) {
			if (q.kind.equals("bring") && (q.state == OFFERED || q.state == TAKEN) && q.player.equals(player.getUUID()) && q.giver.equals(drifter.getUUID())
				&& q.item.equals(id)) {
				return q;
			}
		}

		return null;
	}

	/** A player threw a Drifter something: a letter it's for, or goods it asked them for. */
	private static void received(DrifterEntity drifter, Player thrower, ItemStack stack) {
		MinecraftServer server = drifter.getServer();
		if (server == null || !(thrower instanceof ServerPlayer player)) {
			return;
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.contains(LETTER)) {
			Quest q = quests.get(data.copyTag().getLong(LETTER));
			if (q != null && q.state == TAKEN && q.kind.equals("word") && drifter.getUUID().equals(q.to)) {
				// (Read: a sheet of paper again.)
				for (int slot = 0; slot < drifter.getInventory().getContainerSize(); slot++) {
					ItemStack held = drifter.getInventory().getItem(slot);
					CustomData mark = held.get(DataComponents.CUSTOM_DATA);
					if (mark != null && mark.contains(LETTER) && mark.copyTag().getLong(LETTER) == q.id) {
						held.remove(DataComponents.CUSTOM_DATA);
						held.remove(DataComponents.CUSTOM_NAME);
						held.remove(DataComponents.LORE);
					}
				}

				TalkApi.sayTo(drifter, player, CivLines.pick(drifter, "quest.word.got", "from " + q.giverName + "? ok, got it. thanks for coming all this way",
					"player", TalkApi.callName(drifter, player), "who", q.giverName, "town", q.townName));
				DrifterApi.think(drifter, player.getGameProfile().getName() + " brought a letter from " + q.giverName);
				q.state = READY;
				state(server).setDirty();
			}

			return;
		}

		Quest q = bringFor(drifter, player, stack);
		if (q == null) {
			return;
		}

		// (Throwing it over is a yes.)
		q.state = TAKEN;
		// (More than was asked: what's over is handed straight back.)
		int over = Math.max(0, q.given + stack.getCount() - q.count);
		for (int slot = 0, left = over; slot < drifter.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack held = drifter.getInventory().getItem(slot);
			if (ItemStack.isSameItemSameComponents(held, stack)) {
				ItemStack back = held.split(Math.min(left, held.getCount()));
				left -= back.getCount();
				drifter.tossForward(back).setTarget(player.getUUID());
			}
		}

		q.given += stack.getCount() - over;
		if (q.given >= q.count) {
			finish(server, q, drifter, player);
		} else {
			com.dinglebob.drifter.social.ChatLines.speak(drifter, CivLines.pick(drifter, "town.notice.progress", q.given + " of " + q.count + ", keep em coming",
				"given", q.given, "count", q.count));
		}

		state(server).setDirty();
	}

	/** Done, and the giver's here: its thanks, its pay (what it promised, of what it and the till have now), and what's thought of them. */
	private static void finish(MinecraftServer server, Quest q, DrifterEntity giver, ServerPlayer player) {
		long now = server.overworld().getGameTime();
		String name = player.getGameProfile().getName();
		Town town = q.town == null ? null : Diplomacy.byId(q.town.toString());
		int paid = 0;
		if (q.pay > 0) {
			paid = DrifterApi.moneyTake(giver.getInventory(), q.pay);
			Container till = q.forTown && town != null ? TownNotices.till(server, town) : null;
			if (paid < q.pay && till != null) {
				paid += DrifterApi.moneyTake(till, q.pay - paid);
			}

			giver.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
			for (ItemStack stack : DrifterApi.moneyStacks(paid)) {
				// (Theirs to pick up, nobody else's.)
				giver.tossForward(stack).setTarget(player.getUUID());
			}
		}

		String thanks = q.pay > 0 && paid < q.pay ? CivLines.pick(giver, "town.notice.short", "thats all i have, sorry")
			: CivLines.pick(giver, q.pay > 0 || q.kind.equals("dead") ? "quest.thanks." + q.kind : "quest.thanks.unpaid", q.pay > 0 ? "thank you! here, as promised"
				: "thank you. really", "player", TalkApi.callName(giver, player),
				"dead", q.about, "who", q.toName);
		TalkApi.sayTo(giver, player, thanks);
		DrifterApi.addTrustInPlayer(giver, q.player, name, switch (q.kind) {
			case "bring" -> BRING_TRUST;
			case "word" -> WORD_TRUST;
			case "find" -> FIND_TRUST;
			case "deal" -> DEAL_TRUST;
			default -> DEAD_TRUST;
		});
		if (q.kind.equals("dead")) {
			TalkApi.addFamiliarity(giver, q.player, name, DEAD_FAMILIARITY);
		}

		String did = switch (q.kind) {
			case "bring" -> "brought " + q.townName + " the " + things(q.count, item(q.item)) + " it needed" + (q.war ? " for its war with " + q.about : "");
			case "word" -> "carried " + (q.letter ? "a letter" : "word") + " from " + q.giverName + " of " + q.townName + " to " + q.toName + " of " + q.toTown;
			case "find" -> "brought " + q.about + " to live in " + q.townName;
			case "deal" -> "dealt with the bandit " + q.toName + " for " + q.townName;
			default -> "laid a flower on " + q.about + "'s grave in " + q.townName + " for " + q.giverName;
		};
		if (q.forTown && town != null) {
			town.heard.merge(q.player, TOWN_WORD, (a, b) -> Math.min(100.0, a + b));
			Chronicle.add(server, "town", name + " " + did);
			Towns.save(server);
		}

		if (q.kind.equals("bring") && (q.war || q.count >= BIG) || q.kind.equals("find") || q.kind.equals("deal")) {
			Annals.deed(server, player, did, giver);
		}

		DrifterApi.think(giver, name + " " + did);
		close(q, DONE, paid > 0 ? "Paid " + DrifterApi.moneyWords(paid) + (paid < q.pay ? ", all there was." : ".") : q.pay > 0 ? "There was nothing left to pay you with."
			: "", now);
		DrifterCivilizations.LOGGER.info("[quests] {} did #{} ({}): paid {}", name, q.id, title(q), paid);
		state(server).setDirty();
	}

	/** A favor done or a notice filled: kept under Done. */
	static void record(MinecraftServer server, Player player, String kind, String title, UUID giver, String giverName, @Nullable Town town, String end) {
		Quest q = new Quest();
		q.id = nextId++;
		q.player = player.getUUID();
		q.playerName = player.getGameProfile().getName();
		q.kind = kind;
		q.about = title;
		q.giver = giver;
		q.giverName = giverName;
		q.town = town == null ? null : town.id;
		q.townName = town == null ? "" : town.name;
		q.asked = server.overworld().getGameTime();
		close(q, DONE, end, q.asked);
		quests.put(q.id, q);
		state(server).setDirty();
	}

	// ---------------------------------------------------------------------------------------------
	// Words
	// ---------------------------------------------------------------------------------------------

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	private static String name(Item item) {
		return new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
	}

	/** "iron ingots", "bread". */
	static String plural(String name) {
		String last = name.substring(name.lastIndexOf(' ') + 1);
		return name.endsWith("s") || MASS.contains(last) || name.startsWith("cooked ") || name.startsWith("raw ") || name.equals("steak") ? name : name + "s";
	}

	static String things(int count, Item item) {
		return count + " " + (count == 1 ? name(item) : plural(name(item)));
	}

	private static String capital(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String ago(long days) {
		return days <= 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
	}

	static String title(Quest q) {
		return switch (q.kind) {
			case "bring" -> capital(plural(name(item(q.item)))) + " for " + q.townName + (q.war ? "'s war" : "");
			case "word" -> (q.letter ? "A letter for " : "Word for ") + q.toName + " in " + q.toTown;
			case "dead" -> "A flower for " + q.about + "'s grave";
			case "find" -> "Someone new for " + q.townName;
			case "deal" -> "Deal with " + q.toName;
			default -> q.about;
		};
	}

	private static String payWords(Quest q) {
		return q.pay > 0 ? capital(DrifterApi.moneyWords(q.pay)) + (q.forTown ? ", from " + q.townName + "'s till. And " + q.townName + " will think the better of you."
			: ", from their own pockets. And they'll think the better of you.")
			: q.kind.equals("dead") ? "Their thanks. It would mean a lot to them." : q.forTown ? "The till's empty: the town's thanks, and it will remember."
				: "Their thanks, and they'd owe you one.";
	}

	// ---------------------------------------------------------------------------------------------
	// The journal
	// ---------------------------------------------------------------------------------------------

	/** The Quests bookmark: offered, your word given, on the boards, done. Each line opens its page. */
	static Journal.Tab tab(ServerPlayer player) {
		MinecraftServer server = player.server;
		long now = server.overworld().getGameTime();
		UUID id = player.getUUID();
		List<Journal.Line> offered = new ArrayList<>();
		List<Journal.Line> given = new ArrayList<>();
		List<Journal.Line> done = new ArrayList<>();
		List<Quest> mine = of(id);
		for (Quest q : mine) {
			List<String> page = List.of("@page:q:" + q.id);
			switch (q.state) {
				case OFFERED -> offered.add(new Journal.Line(title(q), q.giverName, Journal.PLAIN, page));
				case TAKEN -> given.add(new Journal.Line(title(q), q.due > 0 ? Journal.timeLeft(Math.max(0, q.due - now)) : q.giverName, Journal.PLAIN, page));
				case READY -> given.add(new Journal.Line(title(q), "tell " + q.giverName, Journal.GOOD, page));
				case DONE -> done.add(0, new Journal.Line(title(q), "done", Journal.GOOD, page));
				case FAILED -> done.add(0, new Journal.Line(title(q), "ran out", Journal.BAD, page));
				case DROPPED -> done.add(0, new Journal.Line(title(q), "given up", Journal.FAINT, page));
				case LAPSED -> done.add(0, new Journal.Line(title(q), "not needed", Journal.FAINT, page));
				default -> {
				}
			}
		}

		if (CivConfig.friends().on) {
			for (Friends.Bond b : Friends.of(id)) {
				if (b.favor == null || !b.active()) {
					continue;
				}

				List<String> page = List.of("@page:qf:" + b.drifter);
				(b.favor.promised() ? given : offered).add(new Journal.Line(favorTitle(b), b.favor.promised() ? Journal.timeLeft(Math.max(0, b.favor.due() - now))
					: b.drifterName, Journal.PLAIN, page));
			}
		}

		List<Journal.Line> boards = new ArrayList<>();
		Set<UUID> seen = noticed.getOrDefault(id, Set.of());
		for (Town town : Towns.all()) {
			for (TownNotices.Notice notice : town.notices) {
				if (seen.contains(notice.id)) {
					boards.add(new Journal.Line(capital(things(notice.count, item(notice.item))), town.name, Journal.PLAIN, List.of("@page:qn:" + notice.id)));
				}
			}
		}

		List<Journal.Line> lines = new ArrayList<>();
		group(lines, "Offered", offered);
		group(lines, "Your word given", given);
		group(lines, "On the boards", boards);
		group(lines, "Done", done);
		return new Journal.Tab("Quests", lines, "Nobody's asked anything of you yet. Get to know people; they will.");
	}

	private static void group(List<Journal.Line> lines, String title, List<Journal.Line> of) {
		if (!of.isEmpty()) {
			lines.add(new Journal.Line(title, "", Journal.FAINT, List.of()));
			lines.addAll(of);
		}
	}

	private static String favorTitle(Friends.Bond b) {
		int needed = FriendPerks.needed(b.favor.kind());
		return switch (b.favor.kind()) {
			case "iron" -> needed + " iron for " + b.drifterName;
			case "food" -> "Food for " + b.drifterName;
			default -> "Monsters at " + b.drifterName + "'s place";
		};
	}

	private static Journal.El el(int type, String a, String b, int ink, List<String> more) {
		return new Journal.El(type, a, b, ink, more);
	}

	private static Journal.El text(String words, int ink) {
		return el(Journal.TEXT, words, "", ink, List.of());
	}

	private static Journal.El sub(String words) {
		return el(Journal.SUB, words, "", Journal.FAINT, List.of());
	}

	private static Journal.El button(String words, String does, int ink) {
		return el(Journal.BUTTON, words, does, ink, List.of());
	}

	/** Who asked, as a person on the page: their head, their name, their nation's banner, who they are. */
	private static Journal.El person(UUID who, String name, @Nullable Town town) {
		DrifterRecord record = DrifterApi.record(who);
		UUID power = town == null ? null : WorldPlan.powerOf(town);
		String role = town == null ? "A wanderer" : who.equals(town.leader) ? "Leader of " + town.name : who.equals(town.keeper) ? "Shopkeeper of " + town.name
			: "Of " + town.name;
		return el(Journal.PERSON, record == null ? "" : record.skin(), name, Journal.PLAIN, List.of(power == null ? "-" : Annals.picture(power), role));
	}

	/**
	 * A client turning to a page of the Quests bookmark, or pressing one of its buttons ("q:" and a quest's number, then
	 * ":take", ":no" or ":drop"; "qf:" and a friend, a favor; "qn:" and a notice): what the button does, then the page.
	 */
	static Journal.Page open(ServerPlayer player, String id) {
		MinecraftServer server = player.server;
		List<Journal.El> els = new ArrayList<>();
		try {
			String[] parts = id.split(":");
			String does = parts.length > 2 ? parts[2] : "";
			if (parts[0].equals("q") && parts.length >= 2) {
				Quest q = quests.get(Long.parseLong(parts[1]));
				if (q != null && q.player.equals(player.getUUID())) {
					boolean acted = switch (does) {
						case "take" -> {
							take(server, q, player);
							yield true;
						}
						case "no" -> {
							decline(server, q);
							yield true;
						}
						case "drop" -> {
							drop(server, q);
							yield true;
						}
						default -> false;
					};
					if (acted) {
						ServerPlayNetworking.send(player, Journal.of(player));
					}

					quest(els, player, q);
				}
			} else if (parts[0].equals("qf") && parts.length >= 2) {
				UUID friend = UUID.fromString(parts[1]);
				Friends.Bond b = Friends.bond(friend, player.getUUID());
				DrifterEntity drifter = TownFounding.loaded(server, friend);
				if (b != null && b.favor != null && (does.equals("take") || does.equals("no")) && !b.favor.promised() && drifter != null) {
					FriendPerks.favorAnswered(drifter, player, b.favor.kind(), does.equals("take"));
					ServerPlayNetworking.send(player, Journal.of(player));
				}

				if (b != null && b.favor != null) {
					favor(els, player, b);
				}
			} else if (parts[0].equals("qn") && parts.length >= 2) {
				UUID notice = UUID.fromString(parts[1]);
				for (Town town : Towns.all()) {
					for (TownNotices.Notice n : town.notices) {
						if (n.id.equals(notice) && noticed.getOrDefault(player.getUUID(), Set.of()).contains(notice)) {
							notice(els, player, town, n);
						}
					}
				}
			}
		} catch (IllegalArgumentException bad) {
			els.clear();
		}

		if (els.isEmpty()) {
			els.add(el(Journal.HEADLINE, "Nothing here now", "", Journal.PLAIN, List.of()));
			els.add(text("Whatever was asked isn't any more.", Journal.FAINT));
		}

		return new Journal.Page(id, els);
	}

	private static void quest(List<Journal.El> els, ServerPlayer player, Quest q) {
		MinecraftServer server = player.server;
		long now = server.overworld().getGameTime();
		Town town = q.town == null ? null : Diplomacy.byId(q.town.toString());
		els.add(el(Journal.HEADLINE, title(q), "", Journal.PLAIN, List.of()));
		els.add(el(Journal.DATELINE, q.townName.isEmpty() ? "" : "Asked in " + q.townName, capital(ago((now - q.asked) / DAY)), Journal.FAINT, List.of()));
		els.add(el(Journal.RULE, "", "", Journal.PLAIN, List.of()));
		els.add(person(q.giver, q.giverName, town));
		if (!q.said.isEmpty()) {
			DrifterRecord record = DrifterApi.record(q.giver);
			els.add(el(Journal.QUOTE, q.said, q.giverName, Journal.PLAIN, List.of(record == null ? "" : record.skin())));
		}

		if (!q.why.isEmpty()) {
			els.add(sub("Why they ask"));
			els.add(text(q.why, Journal.PLAIN));
		}

		if (!q.end.isEmpty()) {
			els.add(el(Journal.THIN, "", "", Journal.PLAIN, List.of()));
			els.add(text(q.end, q.state == FAILED ? Journal.BAD : q.state == DONE ? Journal.GOOD : Journal.FAINT));
		}

		if (q.kind.equals("favor") || q.kind.equals("notice")) {
			return;
		}

		els.add(el(Journal.BREAK, "", "", Journal.PLAIN, List.of()));
		els.add(sub("What's wanted"));
		switch (q.kind) {
			case "bring" -> {
				Item item = item(q.item);
				List<String> more = new ArrayList<>();
				if (q.open()) {
					if (q.given > 0) {
						more.add(Math.min(q.given, q.count) + " handed over so far");
					}

					more.add("You're carrying " + player.getInventory().countItem(item));
				}

				els.add(el(Journal.ITEM, q.item, capital(things(q.count, item)), Journal.PLAIN, more));
				if (q.open()) {
					els.add(text("Throw them to " + q.giverName + " in " + q.townName + ".", Journal.FAINT));
				}
			}
			case "word" -> {
				List<String> more = new ArrayList<>(List.of("The " + q.toRole + " of " + q.toTown));
				if (q.at != null) {
					more.add(q.toTown + " is at " + q.at.getX() + ", " + q.at.getZ());
				}

				els.add(el(Journal.ITEM, "minecraft:paper", (q.letter ? "A letter for " : "Word for ") + q.toName, Journal.PLAIN, more));
				if (q.state == OFFERED || q.state == TAKEN) {
					els.add(text(q.state == OFFERED ? "Say yes to their face and, if they've paper, they'll write it down for you to carry."
						: q.letter ? "Throw the letter to " + q.toName + " (or, if it's lost, find them and tell them). Then come back to " + q.giverName + "."
							: "Find " + q.toName + " and stand with them a moment. Then come back to " + q.giverName + ".", Journal.FAINT));
					// (Never led into trouble unwarned.)
					for (Town other : Towns.all()) {
						Town.Criminal criminal = other.name.equals(q.toTown) ? other.crimes.get(player.getUUID()) : null;
						if (criminal != null && (criminal.wantedUntil > 0 || criminal.exiledUntil > 0)) {
							els.add(text("You're " + (criminal.wantedUntil > 0 ? "wanted in " : "exiled from ") + q.toTown + ". Its guards will come for you.",
								Journal.BAD));
						}
					}
				}
			}
			case "find" -> {
				els.add(el(Journal.ITEM, "minecraft:oak_door", "Someone to live in " + q.townName, Journal.PLAIN, q.about.isEmpty()
					? List.of("A wanderer with no home and no town", "Not a bandit, nor someone who lives elsewhere") : List.of(q.about + " came")));
				if (q.state == OFFERED || q.state == TAKEN) {
					els.add(text("Get to know one, ask them to come with you (say \"follow me\" to them; they follow a few minutes, so ask again on a long road), and "
						+ "walk them into " + q.townName + ". If its people take to them, they'll be given the house. Then tell " + q.giverName + ".", Journal.FAINT));
				}
			}
			case "deal" -> {
				List<String> more = new ArrayList<>(List.of("A bandit"));
				if (q.open()) {
					more.add("Last seen to the " + q.toRole + " of " + q.townName);
				}

				els.add(el(Journal.ITEM, "minecraft:iron_sword", "Deal with " + q.toName, Journal.PLAIN, more));
				if (q.state == OFFERED || q.state == TAKEN) {
					els.add(text("Kill them. Then tell " + q.giverName + ".", Journal.FAINT));
					// (Never led into trouble unwarned.)
					els.add(text(q.townName + "'s leader has asked it: nobody of " + q.townName + " will hold it against you. Anyone else who sees it and liked them will.",
						Journal.BAD));
				}
			}
			default -> {
				List<String> more = new ArrayList<>(List.of("In " + q.townName + "'s graveyard"));
				if (q.at != null) {
					more.add("At " + q.at.getX() + ", " + q.at.getZ());
				}

				els.add(el(Journal.ITEM, "minecraft:poppy", "A flower on " + q.about + "'s grave", Journal.PLAIN, more));
				if (q.state == OFFERED || q.state == TAKEN) {
					els.add(text("Plant any flower by the stone. Then tell " + q.giverName + ".", Journal.FAINT));
				}
			}
		}

		if (q.state == READY) {
			els.add(text("Done. Go back to " + q.giverName + " in " + q.townName + ".", Journal.GOOD));
		}

		els.add(sub(q.state == DONE ? "What you were promised" : "What you'd get"));
		els.add(text(payWords(q), Journal.PLAIN));
		if (q.state == OFFERED || q.state == TAKEN) {
			els.add(sub("How long"));
			els.add(text(q.war ? "While the war lasts: " + Journal.timeLeft(Math.max(0, q.due - now)) + " at most."
				: capital(Journal.timeLeft(Math.max(0, q.due - now))) + ".", Journal.PLAIN));
		}

		if (q.state == OFFERED) {
			els.add(el(Journal.THIN, "", "", Journal.PLAIN, List.of()));
			if (given(player.getUUID()) >= MOST_OPEN) {
				els.add(text("You've given your word to enough people already. Keep some of it first.", Journal.FAINT));
			} else {
				els.add(button("Take it", "q:" + q.id + ":take", Journal.GOOD));
			}

			els.add(button("Not for me", "q:" + q.id + ":no", Journal.PLAIN));
			els.add(text("Saying no costs nothing. Left unanswered, the offer lapses in a day.", Journal.FAINT));
		} else if (q.state == TAKEN) {
			els.add(el(Journal.THIN, "", "", Journal.PLAIN, List.of()));
			els.add(button("I can't do it after all", "q:" + q.id + ":drop", Journal.BAD));
			els.add(text(q.giverName + " would think a little less of you; less than if you let it run out.", Journal.FAINT));
		}
	}

	private static void favor(List<Journal.El> els, ServerPlayer player, Friends.Bond b) {
		MinecraftServer server = player.server;
		long now = server.overworld().getGameTime();
		Town town = Diplomacy.townOf(b.drifter);
		int needed = FriendPerks.needed(b.favor.kind());
		els.add(el(Journal.HEADLINE, favorTitle(b), "", Journal.PLAIN, List.of()));
		els.add(el(Journal.DATELINE, "A friend's favor", capital(ago((now - b.favor.asked()) / DAY)), Journal.FAINT, List.of()));
		els.add(el(Journal.RULE, "", "", Journal.PLAIN, List.of()));
		els.add(person(b.drifter, b.drifterName, town));
		els.add(sub("Why they ask"));
		els.add(text(b.drifterName + " is your " + b.stage.word + ", and " + switch (b.favor.kind()) {
			case "iron" -> "is short of iron.";
			case "food" -> "has run out of food.";
			default -> "has monsters round the house at night.";
		}, Journal.PLAIN));
		els.add(el(Journal.BREAK, "", "", Journal.PLAIN, List.of()));
		els.add(sub("What's wanted"));
		String done = Math.min(b.favor.done(), needed) + " of " + needed + " so far";
		switch (b.favor.kind()) {
			case "iron" -> els.add(el(Journal.ITEM, "minecraft:iron_ingot", needed + " iron ingots", Journal.PLAIN,
				List.of(done, "You're carrying " + player.getInventory().countItem(Items.IRON_INGOT))));
			case "food" -> els.add(el(Journal.ITEM, "minecraft:bread", needed + " of any food", Journal.PLAIN, List.of(done)));
			default -> els.add(el(Journal.ITEM, "minecraft:iron_sword", "Kill " + needed + " monsters round their place tonight", Journal.PLAIN,
				b.favor.at() == null ? List.of(done) : List.of(done, "Their place is at " + b.favor.at().getX() + ", " + b.favor.at().getZ())));
		}

		if (!b.favor.kind().equals("zombies")) {
			els.add(text("Throw it to " + b.drifterName + ".", Journal.FAINT));
		}

		els.add(sub("What you'd get"));
		els.add(text("A closer friendship. Let down after a yes, they'd trust you a little less.", Journal.PLAIN));
		els.add(sub("How long"));
		els.add(text(capital(Journal.timeLeft(Math.max(0, b.favor.due() - now))) + ".", Journal.PLAIN));
		els.add(el(Journal.THIN, "", "", Journal.PLAIN, List.of()));
		if (b.favor.promised()) {
			els.add(text("You said you would.", Journal.GOOD));
		} else if (TownFounding.loaded(server, b.drifter) != null) {
			els.add(button("Take it", "qf:" + b.drifter + ":take", Journal.GOOD));
			els.add(button("Not for me", "qf:" + b.drifter + ":no", Journal.PLAIN));
		} else {
			els.add(text("They're not about to hear your answer. Find them and tell them.", Journal.FAINT));
		}
	}

	private static void notice(List<Journal.El> els, ServerPlayer player, Town town, TownNotices.Notice n) {
		long today = Chronicle.day(player.server);
		Item item = item(n.item);
		els.add(el(Journal.HEADLINE, "Wanted: " + things(n.count, item), "", Journal.PLAIN, List.of()));
		els.add(el(Journal.DATELINE, "On " + town.name + "'s board", capital(ago(today - n.day)), Journal.FAINT, List.of()));
		els.add(el(Journal.RULE, "", "", Journal.PLAIN, List.of()));
		els.add(person(n.poster, n.posterName, town));
		DrifterRecord record = DrifterApi.record(n.poster);
		String words = n.text.contains(" - ") ? n.text.substring(0, n.text.lastIndexOf(" - ")) : n.text;
		els.add(el(Journal.QUOTE, words, n.posterName, Journal.PLAIN, List.of(record == null ? "" : record.skin())));
		els.add(sub("Why it's up"));
		els.add(text(town.name + "'s stores are short of " + plural(name(item)) + ". Anyone can fill it; nobody's counting on you.", Journal.PLAIN));
		els.add(el(Journal.BREAK, "", "", Journal.PLAIN, List.of()));
		els.add(sub("What's wanted"));
		int given = n.delivered.getOrDefault(player.getUUID(), 0);
		List<String> more = new ArrayList<>();
		if (given > 0) {
			more.add(Math.min(given, n.count) + " handed over so far");
		}

		more.add("You're carrying " + player.getInventory().countItem(item));
		els.add(el(Journal.ITEM, n.item, capital(things(n.count, item)), Journal.PLAIN, more));
		els.add(text("Throw them to " + n.posterName + " in " + town.name + ".", Journal.FAINT));
		els.add(sub("What you'd get"));
		els.add(text(capital(DrifterApi.moneyWords(Math.max(1, Math.round(n.points / 6.0)))) + ", from " + n.posterName + "'s pockets, then the town's till.",
			Journal.PLAIN));
		els.add(sub("How long"));
		long left = TownNotices.STAYS - (today - n.day);
		els.add(text("It comes down " + (left <= 1 ? "tomorrow." : "in " + left + " days."), Journal.PLAIN));
	}

	// ---------------------------------------------------------------------------------------------
	// Testing
	// ---------------------------------------------------------------------------------------------

	private static int debugOffer(net.minecraft.commands.CommandSourceStack source, @Nullable String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
		Quest q = town == null ? null : ask(player.server, town, player, player.server.overworld().getGameTime(), kind == null ? "" : kind);
		if (q == null && kind == null && town != null) {
			for (String one : List.of("war", "short", "word", "dead", "find", "deal")) {
				q = q != null ? q : ask(player.server, town, player, player.server.overworld().getGameTime(), one);
			}
		}

		Quest asked = q;
		if (asked != null) {
			state(player.server).setDirty();
		}

		source.sendSuccess(() -> Component.literal(asked == null ? "Debug: nobody near had that to ask (a townsperson within 64 blocks who doesn't mind you? "
			+ "war: a town at war, its leader or keeper; short: its leader or keeper; word: another town in reach; dead: a grave, and they like you; "
			+ "find: its leader or keeper, a house standing empty; deal: its leader, a Bandit within 300 blocks; "
			+ "the log says who wouldn't and why)"
			: "Debug: #" + asked.id + " " + title(asked) + ", asked by " + asked.giverName), false);
		return asked == null ? 0 : 1;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			// Version 1 is the first format.
			quests.clear();
			noticed.clear();
			nextId = Math.max(1, tag.getLong("Next"));
			ListTag list = tag.getList("Quests", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				try {
					Quest q = Quest.load(list.getCompound(i));
					quests.put(q.id, q);
					nextId = Math.max(nextId, q.id + 1);
				} catch (Exception e) {
					DrifterCivilizations.LOGGER.warn("A quest that couldn't be read was left out", e);
				}
			}

			CompoundTag seen = tag.getCompound("Noticed");
			for (String key : seen.getAllKeys()) {
				try {
					Set<UUID> mine = new HashSet<>();
					ListTag ids = seen.getList(key, Tag.TAG_STRING);
					for (int i = 0; i < ids.size(); i++) {
						mine.add(UUID.fromString(ids.getString(i)));
					}

					noticed.put(UUID.fromString(key), mine);
				} catch (IllegalArgumentException ignored) {
				}
			}

			DrifterCivilizations.LOGGER.info("[quests] {} quests read", quests.size());
			return new Saved();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", SAVE_VERSION);
			tag.putLong("Next", nextId);
			ListTag list = new ListTag();
			for (Quest q : quests.values()) {
				list.add(q.save());
			}

			tag.put("Quests", list);
			CompoundTag seen = new CompoundTag();
			noticed.forEach((player, ids) -> {
				ListTag mine = new ListTag();
				for (UUID id : ids) {
					mine.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
				}

				seen.put(player.toString(), mine);
			});
			tag.put("Noticed", seen);
			return tag;
		}
	}

	private static Saved state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_quests");
	}
}
