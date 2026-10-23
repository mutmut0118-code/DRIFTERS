package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.ItemValues;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Mode;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.social.SocialEvent;
import com.dinglebob.drifter.social.SocialWatcher;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Players talking to a Drifter in chat (the dynamic chat, phase 2; user, 2026-10-23: "greatly expand ... player
 * interaction"; requests "trust-gated"). What a message asks is matched as answers are ({@link Lines#answer}: the longest
 * phrase found, however it's typed), from {@code drifter_chat/requests.json}: what are you doing, how are you, your name,
 * coords, where's your base, seen a village (diamonds, iron, a dungeon, a portal, a fortress), follow me, come here, stop
 * following, help, team up, can i have, trade, 1v1, nice things, insults, a joke, are you a bot.
 *
 * <p>Who it's for: the Drifter named (or a good part of its name), else the one they've been talking with this last
 * minute, else the one they're looking at, else the only one close by. It turns to them and answers in its voice, a
 * moment later as it's typed. Only from what it knows (its memory, its pockets, its home), and how much it tells or does
 * follows how it feels about them: its coords to a friend, a direction to someone it knows, "nice try" to a stranger;
 * follows a friend; gives only from what it can spare, to someone it likes, about what they're worth to it. It remembers
 * what they said: nice things warm it a little, insults cool it (and it brings them up when asked for something).
 */
public final class PlayerTalk {
	private PlayerTalk() {
	}

	private static final String[] INTENTS = {"wyd", "hru", "name", "coords", "base", "seen_village", "seen_diamonds", "seen_iron", "seen_dungeon",
		"seen_portal", "seen_fortress", "follow", "come", "stop", "help", "team", "give", "trade", "duel", "compliment", "insult", "joke", "bot"};

	/** How far it hears someone talking to it without its name: looking at it, or the only one this close. */
	private static final double LOOKED_AT = 16.0;
	private static final double ONLY_ONE = 10.0;
	/** Who they're talking with stays so this long after the last word between them. */
	private static final long FOCUS = 20L * 60;
	/** A "can i have" with no item: the next message naming one is what they want, this long after. */
	private static final long WAITING_FOR_ITEM = 20L * 30;
	/** Trust from which it tells a direction, and from which it gives things or comes when asked. */
	private static final double KNOWS_THEM = 10.0;
	/** Familiarity with which an acquaintance (trust over {@link #KNOWS_THEM}) comes along too. */
	private static final double FAMILIAR = 3.0;
	/** Insults lately (a day): it brings them up when asked for something. */
	private static final long INSULT_REMEMBERED = 24000L;
	/** How long "follow me" stands while they finish typing and walk off; how long "team up" follows. */
	private static final int INVITE_STANDS = 20 * 30;
	private static final int TEAM_FOLLOWS = 20 * 60 * 15;

	private record Focus(UUID drifter, long until, long wantsItemUntil) {
	}

	private record Reply(DrifterEntity drifter, String text, long at, @Nullable Runnable then) {
	}

	private static final Map<UUID, Focus> focus = new HashMap<>();
	private static final List<Reply> replies = new ArrayList<>();
	/** When each Drifter last answered a player (so it doesn't also say "?"). */
	private static final Map<UUID, Long> answeredAt = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PlayerTalk::tick);
	}

	/** It's answering a player now (a reply on its way or just said). */
	public static boolean answering(DrifterEntity drifter) {
		return drifter.level().getGameTime() - answeredAt.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < 20 * 6;
	}

	/**
	 * A player said this in chat: if it's something a Drifter near them takes as said to it, it answers (and true: the
	 * chat rituals, "hi" back and so on, leave it be).
	 */
	public static boolean heard(ServerPlayer player, String text) {
		long now = player.level().getGameTime();
		Focus focused = focus.get(player.getUUID());
		String intent = Lines.answer(text, INTENTS);
		DrifterEntity drifter = addressee(player, text, focused, now);
		if (drifter == null) {
			return false;
		}

		// "can i have something" ... "bread": the item, a moment later.
		if (intent == null && focused != null && focused.drifter().equals(drifter.getUUID()) && now < focused.wantsItemUntil()) {
			intent = "give";
		}

		if (intent == null) {
			return false;
		}

		// (In an encounter, the encounter does the talking.)
		if (drifter.encounter() != null) {
			return false;
		}

		focus.put(player.getUUID(), new Focus(drifter.getUUID(), now + FOCUS, -1));
		drifter.getSocial().watcher().spokenTo(player);
		answer(drifter, player, intent, text, now);
		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// Who it's said to
	// ---------------------------------------------------------------------------------------------

	@Nullable
	private static DrifterEntity addressee(ServerPlayer player, String text, @Nullable Focus focused, long now) {
		ServerLevel level = player.serverLevel();
		List<DrifterEntity> near = level.getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(SocialWatcher.NAME_RANGE),
			drifter -> drifter.isAlive() && !drifter.isTemporary() || drifter.isAlive() && drifter.encounter() != null);
		if (near.isEmpty()) {
			return null;
		}

		// Named.
		String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", " ") + " ";
		DrifterEntity named = null;
		for (DrifterEntity drifter : near) {
			if (SocialWatcher.called(words, drifter.getName().getString()) && (named == null || drifter.distanceTo(player) < named.distanceTo(player))) {
				named = drifter;
			}
		}

		if (named != null) {
			return named;
		}

		// The one they've been talking with.
		if (focused != null && now < focused.until()) {
			for (DrifterEntity drifter : near) {
				if (drifter.getUUID().equals(focused.drifter())) {
					return drifter;
				}
			}
		}

		// The one they're looking at.
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		DrifterEntity lookedAt = null;
		double best = 0.96;
		for (DrifterEntity drifter : near) {
			Vec3 to = drifter.getBoundingBox().getCenter().subtract(eye);
			double dot = to.normalize().dot(look);
			if (to.length() <= LOOKED_AT && dot > best && player.hasLineOfSight(drifter)) {
				best = dot;
				lookedAt = drifter;
			}
		}

		if (lookedAt != null) {
			return lookedAt;
		}

		// The only one close by.
		List<DrifterEntity> close = near.stream().filter(drifter -> drifter.distanceTo(player) <= ONLY_ONE).toList();
		return close.size() == 1 ? close.get(0) : null;
	}

	// ---------------------------------------------------------------------------------------------
	// What it says and does
	// ---------------------------------------------------------------------------------------------

	private static void answer(DrifterEntity drifter, ServerPlayer player, String intent, String text, long now) {
		String name = player.getGameProfile().getName();
		Relationship r = drifter.getSocial().getOrMeet(player.getUUID(), name);
		Mode mode = Mode.toward(drifter, player);
		// (A 1v1 they asked for is a game, not a grudge: it still talks to them.)
		boolean hostile = (mode.hostile() || mode == Mode.WARNING) && !Spar.on(drifter, player);
		boolean insultedLately = r.lastInsultAt >= 0 && now - r.lastInsultAt < INSULT_REMEMBERED && !r.lastInsult.isEmpty();
		Object[] who = {"player", name, "name", drifter.getName().getString()};

		// Nice things and insults: what they said goes into how it feels about them.
		if (intent.equals("compliment")) {
			drifter.getSocial().record(player.getUUID(), name, SocialEvent.COMPLIMENT, 0.0);
			reply(drifter, hostile ? "ask.hostile" : "ask.compliment", null, who);
			return;
		}

		if (intent.equals("insult")) {
			boolean again = insultedLately;
			r.lastInsult = insultWord(text);
			drifter.getSocial().record(player.getUUID(), name, SocialEvent.INSULT, 0.0);
			reply(drifter, again ? "ask.insult_again" : "ask.insult", null, who);
			return;
		}

		if (hostile) {
			reply(drifter, "ask.hostile", null, who);
			return;
		}

		boolean asking = switch (intent) {
			case "coords", "base", "follow", "come", "help", "team", "give", "duel" -> true;
			default -> intent.startsWith("seen_");
		};
		if (asking && insultedLately && r.trust < Mode.FRIENDLY_TRUST) {
			reply(drifter, "ask.refuse_insulted", null, "player", name, "insult", r.lastInsult);
			return;
		}

		boolean friend = r.trust >= Mode.FRIENDLY_TRUST;
		boolean knows = r.trust >= KNOWS_THEM;
		boolean helper = drifter.getPersonality().archetype() == Archetype.HELPER;
		switch (intent) {
			case "wyd" -> {
				String doing = doing(drifter);
				reply(drifter, doing == null ? "ask.wyd_idle" : "ask.wyd", null, "player", name, "doing", doing == null ? "" : doing);
			}
			case "hru" -> reply(drifter, feeling(drifter), null, who);
			case "name" -> reply(drifter, "ask.name", null, who);
			case "joke" -> reply(drifter, "ask.joke", null, who);
			case "bot" -> reply(drifter, "ask.bot", null, who);
			case "coords" -> {
				BlockPos at = drifter.blockPosition();
				reply(drifter, friend ? "ask.coords_yes" : knows ? "ask.coords_vague" : "ask.coords_no", null, "player", name, "coords", coords(at));
			}
			case "base" -> {
				Home home = drifter.getDrifterBrain().home();
				if (home == null) {
					reply(drifter, "ask.base_none", null, who);
				} else if (friend || helper && knows) {
					reply(drifter, "ask.base_yes", null, "player", name, "coords", coords(home.center));
				} else if (knows) {
					reply(drifter, "ask.base_way", null, "player", name, "way", way(player.position(), Vec3.atCenterOf(home.center)), "dist",
						distance(player.position(), Vec3.atCenterOf(home.center)));
				} else {
					reply(drifter, "ask.base_no", null, who);
				}
			}
			case "seen_village", "seen_diamonds", "seen_iron", "seen_dungeon", "seen_portal", "seen_fortress" -> seen(drifter, player, intent, friend, knows);
			case "follow", "team" -> {
				boolean team = intent.equals("team");
				boolean yes = team ? friend : friend || knows && r.familiarity >= FAMILIAR;
				if (yes) {
					reply(drifter, team ? "ask.team_yes" : "ask.follow_yes", () -> drifter.getSocial().watcher().invite(player, INVITE_STANDS, team ? TEAM_FOLLOWS : 0), who);
				} else {
					reply(drifter, team ? "ask.team_no" : "ask.follow_no", null, who);
				}
			}
			case "come" -> {
				if (friend || knows && r.familiarity >= FAMILIAR) {
					reply(drifter, "ask.come_yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.COME, player, null, 0), who);
				} else {
					reply(drifter, "ask.follow_no", null, who);
				}
			}
			case "stop" -> reply(drifter, "ask.stop", () -> drifter.getSocial().watcher().toldToStop(player), who);
			case "help" -> {
				// A friend comes running; so does a helper for anyone it doesn't dislike, and a brave one it knows.
				boolean comes = friend || helper && r.trust >= 0.0 || knows && drifter.getPersonality().get(Trait.BRAVERY) >= 0.6;
				if (comes && drifter.getHealth() > drifter.getMaxHealth() * 0.4F) {
					reply(drifter, "ask.help_yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.HELP, player, null, 0), who);
				} else {
					reply(drifter, "ask.help_no", null, who);
				}
			}
			case "give" -> give(drifter, player, r, text, now);
			case "trade" -> {
				if (drifter.getShop() != null) {
					reply(drifter, "ask.shop", null, who);
				} else {
					reply(drifter, "ask.trade", null, who);
					focus.put(player.getUUID(), new Focus(drifter.getUUID(), now + FOCUS, now + WAITING_FOR_ITEM));
				}
			}
			case "duel" -> duel(drifter, player, r);
			default -> {
			}
		}
	}

	/** What it's doing, in a few words (its job, without the progress: "gather birch log"), or null when it isn't. */
	@Nullable
	private static String doing(DrifterEntity drifter) {
		var activity = drifter.getDrifterBrain().activity();
		if (activity == null) {
			return null;
		}

		// (Without its progress or counts: "gather birch log (0/3)", "craft oak planks x3".)
		String doing = activity.describe().replaceAll("\\s*\\(.*\\)$", "").replaceAll("\\s+x\\d+$", "").toLowerCase(Locale.ROOT);
		return doing.isBlank() || doing.equals("idle") ? null : doing;
	}

	/** How it is: hurt, hungry, or its mood. */
	private static String feeling(DrifterEntity drifter) {
		if (drifter.getHealth() <= drifter.getMaxHealth() * 0.4F) {
			return "ask.hru_hurt";
		}

		if (drifter.getFoodData().getFoodLevel() <= 8) {
			return "ask.hru_hungry";
		}

		Mood mood = drifter.getPersonality().mood().current();
		return switch (mood) {
			case BORED -> "ask.hru_bored";
			case ANXIOUS, PANICKED -> "ask.hru_scared";
			case ANGRY, FRUSTRATED -> "ask.hru_angry";
			case GRIEVING -> "ask.hru_sad";
			case TRIUMPHANT -> "ask.hru_triumphant";
			default -> "ask.hru_good";
		};
	}

	/** Have you seen one: from its memory only (what it saw), told as its trust in them allows. */
	private static void seen(DrifterEntity drifter, ServerPlayer player, String intent, boolean friend, boolean knows) {
		String name = player.getGameProfile().getName();
		record Sought(MemoryKind kind, String what, Predicate<MemoryEntry> is) {
		}

		Sought sought = switch (intent) {
			case "seen_village" -> new Sought(MemoryKind.LANDMARK, "a village", entry -> entry.label.equals("village"));
			case "seen_fortress" -> new Sought(MemoryKind.LANDMARK, "a fortress", entry -> entry.label.equals("fortress"));
			case "seen_diamonds" -> new Sought(MemoryKind.ORE, "diamonds", entry -> entry.label.contains("diamond_ore"));
			case "seen_iron" -> new Sought(MemoryKind.ORE, "iron", entry -> entry.label.contains("iron_ore"));
			case "seen_dungeon" -> new Sought(MemoryKind.STRUCTURE, "a spawner", entry -> entry.label.contains("spawner"));
			default -> new Sought(MemoryKind.STRUCTURE, "a portal", entry -> entry.label.contains("nether_portal"));
		};
		MemoryEntry entry = drifter.getMemory().nearest(sought.kind(), drifter.level().dimension().location(), drifter.position(), Double.MAX_VALUE, sought.is());
		if (entry == null) {
			reply(drifter, "ask.seen_none", null, "player", name, "what", sought.what());
			return;
		}

		boolean hoards = drifter.getPersonality().get(Trait.GREED) >= 0.8 && sought.kind() == MemoryKind.ORE;
		if ((friend && !hoards) || drifter.getPersonality().archetype() == Archetype.HELPER && knows) {
			reply(drifter, "ask.seen_yes", null, "player", name, "what", sought.what(), "coords", coords(BlockPos.containing(entry.pos)));
		} else if (knows && !hoards) {
			reply(drifter, "ask.seen_way", null, "player", name, "what", sought.what(), "way", way(player.position(), entry.pos), "dist",
				distance(player.position(), entry.pos));
		} else {
			reply(drifter, "ask.seen_no", null, "player", name, "what", sought.what());
		}
	}

	/** "Can i have some bread?": from what it can spare, to someone it likes (or owes), about what they're worth to it. */
	private static void give(DrifterEntity drifter, ServerPlayer player, Relationship r, String text, long now) {
		String name = player.getGameProfile().getName();
		Item item = wanted(drifter, text);
		if (item == null) {
			reply(drifter, "ask.give_what", null, "player", name);
			focus.put(player.getUUID(), new Focus(drifter.getUUID(), now + FOCUS, now + WAITING_FOR_ITEM));
			return;
		}

		String itemName = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		int owed = r.debts.stream().mapToInt(Relationship.Debt::value).sum();
		boolean owes = owed > 0;
		if (r.trust < Mode.FRIENDLY_TRUST && !(owes && r.trust >= 0.0)) {
			reply(drifter, "ask.give_no", null, "player", name, "item", itemName);
			return;
		}

		int spare = 0;
		for (Map.Entry<Integer, Integer> entry : com.dinglebob.drifter.brain.Storage.spare(drifter).entrySet()) {
			if (drifter.getInventory().getItem(entry.getKey()).is(item)) {
				spare += entry.getValue();
			}
		}

		if (spare <= 0) {
			boolean has = drifter.getInventory().count(stack -> stack.is(item)) > 0;
			reply(drifter, has ? "ask.give_need" : "ask.give_none", null, "player", name, "item", itemName);
			return;
		}

		// As many as they said, or a handful; no more than they're worth to it (a friend a little, an ally more, what it
		// owes on top).
		double each = Math.max(0.1, ItemValues.each(new ItemStack(item)));
		Integer said = number(text);
		int want = said != null ? said : each >= 5.0 ? 1 : each >= 2.0 ? 4 : 8;
		double worth = 4.0 + Math.max(0.0, r.trust) * 0.5 + owed;
		int count = Math.min(Math.min(want, spare), (int)Math.floor(worth / each));
		if (count <= 0) {
			reply(drifter, "ask.give_much", null, "player", name, "item", itemName);
			return;
		}

		reply(drifter, owes ? "ask.give_back" : "ask.give_yes", () -> RequestGoal.ask(drifter, RequestGoal.Kind.GIVE, player, item, count), "player", name,
			"count", count, "item", itemName);
	}

	/** "1v1?": a fighter takes it (if it doesn't think they'd crush it), the rest don't. */
	private static void duel(DrifterEntity drifter, ServerPlayer player, Relationship r) {
		String name = player.getGameProfile().getName();
		Archetype archetype = drifter.getPersonality().archetype();
		double bravery = drifter.getPersonality().get(Trait.BRAVERY);
		double aggression = drifter.getPersonality().get(Trait.AGGRESSION);
		boolean fighter = archetype == Archetype.PVP_SWEAT || archetype == Archetype.BANDIT || bravery + aggression >= 1.1;
		// (On Peaceful it couldn't land a hit on them anyway.)
		boolean peaceful = drifter.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
		if (!fighter || peaceful || drifter.getHealth() < drifter.getMaxHealth() * 0.8F || Spar.sparring(drifter)) {
			reply(drifter, "ask.duel_no", null, "player", name);
			return;
		}

		// Much better gear than its own: it knows it'd lose (unless it's a sweat, who never backs down).
		if (r.strength > 1.6 && archetype != Archetype.PVP_SWEAT) {
			reply(drifter, "ask.duel_scared", null, "player", name);
			return;
		}

		// (Face to face: from across the valley, "come here then".)
		if (drifter.distanceTo(player) > Spar.START_WITHIN) {
			reply(drifter, "ask.duel_far", null, "player", name);
			return;
		}

		reply(drifter, "ask.duel_yes", () -> Spar.start(drifter, player), "player", name);
	}

	// ---------------------------------------------------------------------------------------------
	// Reading what they said
	// ---------------------------------------------------------------------------------------------

	private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,3})\\b");
	/** Words in an item's name that don't say which item it is. */
	private static final java.util.Set<String> GENERIC = java.util.Set.of("block", "of", "the", "a", "raw", "item", "ingot", "minecraft", "and", "with",
		"some", "any", "me", "you", "have", "give", "can", "get", "spare", "need", "pls", "please", "i", "do");

	/** A number they said (1 to 64), or null. */
	@Nullable
	private static Integer number(String text) {
		Matcher matcher = NUMBER.matcher(text);
		while (matcher.find()) {
			int n = Integer.parseInt(matcher.group(1));
			// (Not the "1" of a "1v1".)
			if (n >= 1 && n <= 64 && !text.toLowerCase(Locale.ROOT).contains(matcher.group(1) + "v")) {
				return n;
			}
		}

		return null;
	}

	/**
	 * The item they mean, of what it carries ("bread", "logs", "iron", "food", "wood"), or null. Only what it has: it can't
	 * give anything else anyway.
	 */
	@Nullable
	private static Item wanted(DrifterEntity drifter, String text) {
		java.util.Set<String> said = new java.util.HashSet<>();
		for (String word : Lines.normalize(text).trim().split(" ")) {
			if (!word.isBlank()) {
				said.add(singular(word));
			}
		}

		Item best = null;
		int bestScore = 0;
		int bestCount = 0;
		for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = drifter.getInventory().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			int score = 0;
			String names = stack.getHoverName().getString() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			for (String word : Lines.normalize(names.replace('_', ' ')).trim().split(" ")) {
				if (!word.isBlank() && !GENERIC.contains(word) && said.contains(singular(word))) {
					score += 2;
				}
			}

			if (said.contains("food") && stack.has(DataComponents.FOOD)) {
				score += 1;
			}

			if (said.contains("wood") && (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS))) {
				score += 1;
			}

			int count = drifter.getInventory().count(other -> other.is(stack.getItem()));
			if (score > bestScore || score == bestScore && score > 0 && count > bestCount) {
				best = stack.getItem();
				bestScore = score;
				bestCount = count;
			}
		}

		return best;
	}

	private static String singular(String word) {
		if (word.length() > 4 && word.endsWith("ies")) {
			return word.substring(0, word.length() - 3) + "y";
		}

		if (word.length() > 3 && (word.endsWith("ches") || word.endsWith("shes"))) {
			return word.substring(0, word.length() - 2);
		}

		return word.length() > 3 && word.endsWith("s") && !word.endsWith("ss") ? word.substring(0, word.length() - 1) : word;
	}

	/** The insult they used, as they'd say it ("noob", "trash"), for bringing it up later. */
	private static String insultWord(String text) {
		// (Matched as chat is, "noob" and "nooob" alike; said back as written.)
		String said = Lines.normalize(text);
		for (String word : new String[] {"noob", "nub", "trash", "stupid", "idiot", "dumb", "loser", "bot", "clown", "garbage", "useless", "bozo", "ugly",
			"annoying", "cringe", "weirdo", "nerd", "bad", "mid"}) {
			if (said.contains(Lines.normalize(word))) {
				return switch (word) {
					case "bad", "mid", "stupid", "dumb", "trash", "garbage", "useless", "ugly", "annoying", "cringe" -> word;
					default -> "a " + word;
				};
			}
		}

		return "that";
	}

	private static String coords(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static String way(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		String[] ways = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		double angle = Math.toDegrees(Math.atan2(-dx, dz));
		int index = Math.floorMod((int)Math.round(angle / 45.0), 8);
		return ways[index];
	}

	private static int distance(Vec3 from, Vec3 to) {
		int d = (int)Math.round(Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z)));
		// (Players round: "about 120", not "about 117".)
		return d < 20 ? d : (int)(Math.round(d / 10.0) * 10);
	}

	// ---------------------------------------------------------------------------------------------
	// Saying it, a moment later
	// ---------------------------------------------------------------------------------------------

	/** Its answer, said once it's "typed" (longer lines later); then what it said it'd do, if anything. */
	private static void reply(DrifterEntity drifter, String key, @Nullable Runnable then, Object... vars) {
		String line = Lines.pick(drifter, key, vars);
		long now = drifter.level().getGameTime();
		answeredAt.put(drifter.getUUID(), now);
		if (line == null) {
			if (then != null) {
				then.run();
			}

			return;
		}

		long at = now + 15 + Math.min(80, line.length() * 2) + drifter.getRandom().nextInt(15);
		replies.add(new Reply(drifter, line, at, then));
	}

	private static void tick(MinecraftServer server) {
		if (replies.isEmpty()) {
			return;
		}

		for (Iterator<Reply> it = replies.iterator(); it.hasNext();) {
			Reply reply = it.next();
			if (!reply.drifter().isAlive() || reply.drifter().isRemoved()) {
				it.remove();
				continue;
			}

			if (reply.drifter().level().getGameTime() >= reply.at()) {
				it.remove();
				answeredAt.put(reply.drifter().getUUID(), reply.drifter().level().getGameTime());
				ChatLines.speak(reply.drifter(), reply.text());
				if (reply.then() != null) {
					reply.then().run();
				}
			}
		}
	}
}
