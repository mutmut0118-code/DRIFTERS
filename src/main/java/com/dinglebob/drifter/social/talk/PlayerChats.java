package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Mode;
import com.dinglebob.drifter.social.Relationship;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drifters starting talks with players (the dynamic chat, phase 3; user, 2026-10-23): one near a player, with nothing
 * pressing, now and then brings something up with them ({@code drifter_talk/players.json}): hi to a stranger, a friend's
 * name, their gear, how hurt they look, the night coming, news, what it found, a running joke about them, and questions
 * they can answer in chat ("wanna team up?"). Also the rounds: the first time a player turns up in the morning near
 * Drifters who know them, a few say gm one after another; going to bed near them, gn. And bystanders: when one answers a
 * player or finishes a talk, another standing by may add its bit ("lmao", "ooh fight", "never forget").
 */
public final class PlayerChats {
	private PlayerChats() {
	}

	/** How close it has to be (and see them) to bring something up. */
	private static final double START = 8.0;
	private static final int LOOK_EVERY = 40;
	/** Least time between any Drifter starting a talk with one player, and the same Drifter with the same player. */
	private static final long PER_PLAYER = 20L * 90;
	private static final long PER_PAIR = 20L * 60 * 8;
	/** Least time between one Drifter's talks with players, for the chattiest and the quietest. */
	private static final long CHATTY_GAP = 20L * 60 * 3;
	private static final long QUIET_GAP = 20L * 60 * 15;
	/** The rounds: who joins in (knows them, this close), at most how many. */
	private static final double ROUND_RANGE = 32.0;
	private static final int ROUND_MOST = 4;
	/** A bystander this close to the one talking may chime in. */
	private static final double BYSTANDER = 10.0;

	private static final class Opening {
		final DrifterEntity drifter;
		final ServerPlayer player;
		final Topics.Topic topic;
		final Facts a;
		final Facts b;
		final Set<String> world;
		int turn;
		long nextAt;
		/** It said something (a topic whose lines didn't fit says nothing, and asks nothing). */
		boolean said;

		Opening(DrifterEntity drifter, ServerPlayer player, Topics.Topic topic, long now) {
			this.drifter = drifter;
			this.player = player;
			this.topic = topic;
			this.a = Facts.of(drifter, null);
			this.b = Facts.player(drifter, player);
			Heard.addPlayerFacts(drifter, player, this.a, this.b);
			this.world = Facts.world(drifter, player);
			this.nextAt = now + 5;
		}
	}

	private static final List<Opening> openings = new ArrayList<>();
	private static final Map<UUID, Long> lastPlayer = new HashMap<>();
	private static final Map<UUID, Long> lastDrifter = new HashMap<>();
	private static final Map<String, Long> lastPair = new HashMap<>();
	/** The day each player last got a gm round, and whether they were asleep last we looked. */
	private static final Map<UUID, Long> gmDay = new HashMap<>();
	private static final Map<UUID, Boolean> sleeping = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PlayerChats::tick);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Opening> it = openings.iterator(); it.hasNext();) {
			Opening opening = it.next();
			if (!opening.drifter.isAlive() || opening.player.isRemoved() || opening.drifter.level() != opening.player.level()
				|| opening.drifter.distanceTo(opening.player) > 24.0) {
				it.remove();
				continue;
			}

			if (now >= opening.nextAt && !step(opening, now)) {
				it.remove();
				end(opening, now);
			}
		}

		if (now % 20 != 0 || !DrifterConfig.chatLines()) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				if (!com.dinglebob.drifter.social.SocialWatcher.isRealPlayer(player)) {
					continue;
				}

				rounds(level, player, now);
				if (now % LOOK_EVERY == 0) {
					lookForOpening(level, player, now);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// It starts a talk with them
	// ---------------------------------------------------------------------------------------------

	private static void lookForOpening(ServerLevel level, ServerPlayer player, long now) {
		if (now - lastPlayer.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < DrifterConfig.chattyGap(PER_PLAYER)
			|| openings.stream().anyMatch(opening -> opening.player == player)
			|| com.dinglebob.drifter.encounter.Encounters.active(player.getUUID()) != null) {
			return;
		}

		List<DrifterEntity> near = level.getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(START), drifter -> sees(drifter, player)
			&& Conversations.idle(drifter) && !PlayerTalk.answering(drifter) && !Spar.sparring(drifter));
		near.sort(Comparator.comparingDouble(drifter -> drifter.distanceToSqr(player)));
		for (DrifterEntity drifter : near) {
			Mode mode = Mode.toward(drifter, player);
			if (mode.hostile() || mode == Mode.WARNING || mode == Mode.AVOIDING || mode == Mode.FLEEING
				|| now - lastDrifter.getOrDefault(drifter.getUUID(), Long.MIN_VALUE / 2) < gap(drifter)
				|| now - lastPair.getOrDefault(drifter.getUUID() + ":" + player.getUUID(), Long.MIN_VALUE / 2) < DrifterConfig.chattyGap(PER_PAIR)) {
				continue;
			}

			double sociability = drifter.getPersonality().get(Trait.SOCIABILITY);
			if (drifter.getRandom().nextDouble() < DrifterConfig.chatty(0.06 + 0.12 * sociability) && start(drifter, player, null, now) != null) {
				return;
			}
		}
	}

	/** It brings up this topic with them (or one that suits); the topic, or null if none does. */
	@Nullable
	public static Topics.Topic start(DrifterEntity drifter, ServerPlayer player, @Nullable String topicId, long now) {
		Facts a = Facts.of(drifter, null);
		Facts b = Facts.player(drifter, player);
		Heard.addPlayerFacts(drifter, player, a, b);
		Set<String> world = Facts.world(drifter, player);
		Topics.Topic topic = topicId != null ? Topics.playerTopic(topicId) : pick(drifter, a, b, world);
		if (topic == null) {
			return null;
		}

		Opening opening = new Opening(drifter, player, topic, now);
		openings.add(opening);
		lastPlayer.put(player.getUUID(), now);
		lastDrifter.put(drifter.getUUID(), now);
		lastPair.put(drifter.getUUID() + ":" + player.getUUID(), now);
		PlayerTalk.focusOn(player, drifter, now);
		drifter.getHeard().topicSaid(player, topic.id(), now);
		drifter.getMemory().thoughts().add(now, "started talking with " + player.getGameProfile().getName() + " (" + topic.id().replace('_', ' ') + ")");
		return topic;
	}

	@Nullable
	private static Topics.Topic pick(DrifterEntity drifter, Facts a, Facts b, Set<String> world) {
		List<Topics.Topic> fitting = new ArrayList<>();
		double total = 0.0;
		for (Topics.Topic topic : Topics.forPlayers()) {
			if (Topics.holds(topic.needs(), a, b, world)) {
				fitting.add(topic);
				total += topic.weight();
			}
		}

		double roll = drifter.getRandom().nextDouble() * total;
		for (Topics.Topic topic : fitting) {
			roll -= topic.weight();
			if (roll <= 0.0) {
				return topic;
			}
		}

		return fitting.isEmpty() ? null : fitting.get(fitting.size() - 1);
	}

	/** Its next line (turns that don't come up skipped); false when it has said its piece. */
	private static boolean step(Opening opening, long now) {
		while (opening.turn < opening.topic.turns().size()) {
			Topics.Turn turn = opening.topic.turns().get(opening.turn++);
			if (!turn.by().equals("a") || opening.drifter.getRandom().nextDouble() > turn.chance()
				|| !Topics.holds(turn.conditions(), opening.a, opening.b, opening.world)) {
				continue;
			}

			for (Topics.Option option : turn.options()) {
				if (!Topics.holds(option.conditions(), opening.a, opening.b, opening.world)) {
					continue;
				}

				String line = Lines.pickFrom(opening.drifter, option.lines(), vars(opening));
				if (line == null) {
					break;
				}

				opening.drifter.getCamera().lookAt(opening.player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
				ChatLines.speak(opening.drifter, line);
				opening.said = true;
				opening.nextAt = now + Math.min(120, 30 + line.length() * 2) + opening.drifter.getRandom().nextInt(20);
				return true;
			}
		}

		return false;
	}

	/** Said its piece: it waits for an answer to its question, or a bystander may add something. */
	private static void end(Opening opening, long now) {
		if (opening.topic.question() != null && opening.said) {
			PlayerTalk.ask(opening.drifter, opening.player, opening.topic.question(), vars(opening), now);
		}

		chime(opening.drifter, opening.player, opening.topic.chime(), now, "player", opening.player.getGameProfile().getName(), "name",
			opening.drifter.getName().getString());
	}

	private static Object[] vars(Opening opening) {
		// (They're called what they asked to be.)
		String called = Heard.callName(opening.drifter, opening.player);
		List<Object> vars = new ArrayList<>(List.of("a", opening.drifter.getName().getString(), "b", called, "player", called));
		opening.a.words.forEach((key, value) -> {
			vars.add("a." + key);
			vars.add(value);
		});
		opening.b.words.forEach((key, value) -> {
			vars.add("b." + key);
			vars.add(value);
		});
		return vars.toArray();
	}

	private static boolean sees(DrifterEntity drifter, ServerPlayer player) {
		Awareness awareness = drifter.getSenses().awarenessOf(player);
		return drifter.isAlive() && !drifter.isTemporary() && drifter.encounter() == null && awareness != null && awareness.identified && awareness.visibleNow;
	}

	private static long gap(DrifterEntity drifter) {
		return DrifterConfig.chattyGap((long)(QUIET_GAP - (QUIET_GAP - CHATTY_GAP) * drifter.getPersonality().get(Trait.SOCIABILITY)));
	}

	// ---------------------------------------------------------------------------------------------
	// The rounds: gm and gn
	// ---------------------------------------------------------------------------------------------

	private static void rounds(ServerLevel level, ServerPlayer player, long now) {
		long time = level.getDayTime() % 24000L;
		long day = level.getDayTime() / 24000L;
		boolean asleep = player.isSleeping();
		boolean wasAsleep = sleeping.getOrDefault(player.getUUID(), false);
		sleeping.put(player.getUUID(), asleep);
		// Off to bed: those who know them say gn.
		if (asleep && !wasAsleep) {
			round(level, player, "round.gn", 3, now);
			return;
		}

		// The first time they're about in the morning (or just up): a gm round.
		if (time < 2500L && !asleep && gmDay.getOrDefault(player.getUUID(), -1L) != day && round(level, player, "round.gm", ROUND_MOST, now)) {
			gmDay.put(player.getUUID(), day);
		}
	}

	/** Up to {@code most} who know them and see them say this, one after another; false when fewer than two would. */
	private static boolean round(ServerLevel level, ServerPlayer player, String key, int most, long now) {
		List<DrifterEntity> round = new ArrayList<>(level.getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(ROUND_RANGE), drifter -> {
			if (!drifter.isAlive() || drifter.isTemporary() || drifter.encounter() != null) {
				return false;
			}

			Relationship r = drifter.getSocial().get(player.getUUID());
			Mode mode = Mode.toward(drifter, player);
			return r != null && r.familiarity >= 3.0 && r.trust >= 0.0 && !mode.hostile() && mode != Mode.WARNING && mode != Mode.AVOIDING;
		}));
		boolean gm = key.equals("round.gm");
		if (round.size() < (gm ? 2 : 1)) {
			return false;
		}

		round.sort(Comparator.comparingDouble(drifter -> drifter.distanceToSqr(player)));
		long at = now + 20;
		int said = 0;
		for (DrifterEntity drifter : round) {
			if (said >= most) {
				break;
			}

			// The chatty mostly join in, the quiet sometimes (the first always: someone starts it).
			double sociability = drifter.getPersonality().get(Trait.SOCIABILITY);
			if (said > 0 && drifter.getRandom().nextDouble() > DrifterConfig.chatty(0.3 + 0.6 * sociability)) {
				continue;
			}

			String line = Lines.pick(drifter, key, "player", player.getGameProfile().getName());
			if (line != null) {
				at += 15 + drifter.getRandom().nextInt(35);
				PlayerTalk.sayAt(drifter, line, at);
				said++;
			}
		}

		return said > 0;
	}

	// ---------------------------------------------------------------------------------------------
	// Bystanders
	// ---------------------------------------------------------------------------------------------

	/**
	 * Another Drifter standing by (not the one talking, nor the one it talked to) may add one of these, a moment after
	 * ("lmao", "ooh fight", "never forget"); only one that's free and doesn't mind the player there.
	 */
	public static void chime(DrifterEntity speaker, @Nullable LivingEntity other, Map<String, List<String>> lines, long now, Object... vars) {
		if (lines.isEmpty() || !(speaker.level() instanceof ServerLevel level)) {
			return;
		}

		List<DrifterEntity> by = level.getEntitiesOfClass(DrifterEntity.class, speaker.getBoundingBox().inflate(BYSTANDER), drifter -> drifter != speaker
			&& drifter != other && drifter.isAlive() && !drifter.isTemporary() && drifter.encounter() == null && Conversations.of(drifter) == null
			&& !(other instanceof ServerPlayer player && Mode.toward(drifter, player).hostile()));
		if (by.isEmpty()) {
			return;
		}

		DrifterEntity bystander = by.get(speaker.getRandom().nextInt(by.size()));
		if (bystander.getRandom().nextDouble() > DrifterConfig.chatty(0.2 + 0.5 * bystander.getPersonality().get(Trait.SOCIABILITY))) {
			return;
		}

		String line = Lines.pickFrom(bystander, lines, vars);
		if (line != null) {
			PlayerTalk.sayAt(bystander, line, now + 30 + bystander.getRandom().nextInt(40));
		}
	}
}
