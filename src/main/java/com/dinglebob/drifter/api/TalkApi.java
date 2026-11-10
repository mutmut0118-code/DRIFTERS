package com.dinglebob.drifter.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Talk and closeness with players, for addons (API 5, 0.23.38-side.1: Drifter: Civilizations' friendships, docs/API.md
 * "Friends"). Kept apart from {@link DrifterApi} so the door grows in one place per feature. Every hook is one listener
 * (the addon's), a no-op until set.
 */
public final class TalkApi {
	private TalkApi() {
	}

	/** A player said something a Drifter takes as said to it: true if the addon answered it (Drifter then says nothing of its own). */
	@FunctionalInterface
	public interface ChatHandler {
		boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent);
	}

	/** What a Drifter knows of a player for its talk with them: flags for {@code b:} conditions, words for {@code {b.word}}. */
	@FunctionalInterface
	public interface PlayerFacts {
		void add(DrifterEntity drifter, Player player, Map<String, Object> words, Set<String> flags);
	}

	/** The least its trust in a player drifts down to each day ({@link Double#NEGATIVE_INFINITY}: no floor). */
	@FunctionalInterface
	public interface TrustFloor {
		double floor(DrifterEntity drifter, UUID player);
	}

	/** A note for this player on its name's hover in chat ("your friend"), or null. */
	@FunctionalInterface
	public interface NameNote {
		@Nullable
		String note(DrifterEntity drifter, ServerPlayer viewer);
	}

	/** Whether its answer to this player that says where something is ({@code key}: the chat line's key) is whispered. */
	@FunctionalInterface
	public interface WhisperRule {
		boolean whisper(DrifterEntity drifter, ServerPlayer player, String key);
	}

	private static ChatHandler chatHandler = (player, drifter, text, intent) -> false;
	private static PlayerFacts playerFacts = (drifter, player, words, flags) -> {
	};
	private static TrustFloor trustFloor = (drifter, player) -> Double.NEGATIVE_INFINITY;
	private static NameNote nameNote = (drifter, viewer) -> null;
	private static WhisperRule whisperRule = (drifter, player, key) -> false;

	public static void registerChatHandler(ChatHandler handler) {
		chatHandler = handler;
	}

	public static void registerPlayerFacts(PlayerFacts facts) {
		playerFacts = facts;
	}

	public static void setTrustFloor(TrustFloor floor) {
		trustFloor = floor;
	}

	public static void setNameNote(NameNote note) {
		nameNote = note;
	}

	public static void setWhisperRule(WhisperRule rule) {
		whisperRule = rule;
	}

	// (Drifter's side: where the hooks are asked.)

	public static boolean chatHandled(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		return chatHandler.heard(player, drifter, text, intent);
	}

	public static void playerFacts(DrifterEntity drifter, Player player, Map<String, Object> words, Set<String> flags) {
		playerFacts.add(drifter, player, words, flags);
	}

	public static double trustFloor(DrifterEntity drifter, UUID player) {
		return trustFloor.floor(drifter, player);
	}

	@Nullable
	public static String nameNote(DrifterEntity drifter, ServerPlayer viewer) {
		return nameNote.note(drifter, viewer);
	}

	public static boolean whispers(DrifterEntity drifter, ServerPlayer player, String key) {
		return whisperRule.whisper(drifter, player, key);
	}

	// (What an addon can do and read.)

	/** A line to this player only, shown as a /msg ("X whispers to you: ..."); nobody else sees it, no Drifter overhears. */
	public static void whisper(DrifterEntity drifter, ServerPlayer player, String text) {
		com.dinglebob.drifter.social.ChatLines.whisper(drifter, player, text);
	}

	/** It says this aloud, to this player (its name's hover says so). */
	public static void sayTo(DrifterEntity drifter, ServerPlayer player, String text) {
		com.dinglebob.drifter.social.ChatLines.to(drifter, player);
		com.dinglebob.drifter.social.ChatLines.speak(drifter, text);
	}

	/** A line from the chat files ({@code drifter_chat}) in its voice, filled in from {@code vars} (key, value, ...); null if none. */
	@Nullable
	public static String line(DrifterEntity drifter, String key, Object... vars) {
		return com.dinglebob.drifter.social.Lines.pick(drifter, key, vars);
	}

	/** What it calls a player (the nickname they asked for, or their name). */
	public static String callName(DrifterEntity drifter, Player player) {
		return com.dinglebob.drifter.social.talk.Heard.callName(drifter, player);
	}

	/**
	 * Which of these meanings a message has (the chat files' {@code answers} phrases, matched as Drifter matches its own: the
	 * longest phrase found, slang and typos allowed), or null.
	 */
	@Nullable
	public static String meaning(String text, String... keys) {
		return com.dinglebob.drifter.social.Lines.answer(text, keys);
	}

	/** A player topic's word for what it's about ({@code "about"}; empty if none or no such topic). */
	public static String topicAbout(String topicId) {
		var topic = com.dinglebob.drifter.social.talk.Topics.playerTopic(topicId);
		return topic == null ? "" : topic.web().about();
	}

	/** The player topics' ids that have this tag, or whose id has this word. */
	public static List<String> playerTopics(String tag) {
		List<String> ids = new ArrayList<>();
		for (var topic : com.dinglebob.drifter.social.talk.Topics.forPlayers()) {
			if (topic.web().tags().contains(tag) || java.util.Arrays.asList(topic.id().split("_")).contains(tag)) {
				ids.add(topic.id());
			}
		}

		return ids;
	}

	/** How well it knows a player (0 to 100: time spent around them). */
	public static double familiarity(DrifterEntity drifter, UUID player) {
		var r = drifter.getSocial().get(player);
		return r == null ? 0.0 : r.familiarity;
	}

	/** It knows a player this much better (met now if it hadn't: by name). */
	public static void addFamiliarity(DrifterEntity drifter, UUID player, String name, double change) {
		drifter.getSocial().getOrMeet(player, name).addFamiliarity(change);
	}

	/** When it last saw a player (game time; -1 never). */
	public static long lastSeen(DrifterEntity drifter, UUID player) {
		var r = drifter.getSocial().get(player);
		return r == null ? -1L : r.lastSeenAt;
	}

	/** What it remembers of this kind (ores it has seen a face of, structures, landmarks...): a copy. */
	public static List<MemoryEntry> memories(DrifterEntity drifter, MemoryKind kind) {
		return new ArrayList<>(drifter.getMemory().find(kind, entry -> true));
	}

	/** Food it can spare, or null. */
	@Nullable
	public static Item spareFood(DrifterEntity drifter) {
		return com.dinglebob.drifter.social.talk.PlayerTalk.spareFoodOf(drifter);
	}

	/** It walks over and tosses this player that many of this (from what it has; gives up after a minute). */
	public static void bring(DrifterEntity drifter, ServerPlayer player, Item item, int count) {
		com.dinglebob.drifter.social.talk.RequestGoal.bring(drifter, player, item, count);
	}

	/** It brings up this topic (a player topic's id; null: one that suits) with this player now; false if none would do. */
	public static boolean startTalk(DrifterEntity drifter, ServerPlayer player, @Nullable String topicId) {
		return com.dinglebob.drifter.social.talk.PlayerChats.start(drifter, player, topicId, drifter.level().getGameTime()) != null;
	}

	/** Hostile on sight by nature: a Bandit (now), or a delver. */
	public static boolean hostileOnSight(DrifterEntity drifter) {
		return drifter.getSocial().disposition() == com.dinglebob.drifter.social.Disposition.BANDIT || drifter.isDelver();
	}

	/** It's in a talk now (with a Drifter, or answering a player). */
	public static boolean talking(DrifterEntity drifter) {
		return !com.dinglebob.drifter.social.talk.Conversations.idle(drifter) || com.dinglebob.drifter.social.talk.PlayerTalk.answering(drifter);
	}
}
