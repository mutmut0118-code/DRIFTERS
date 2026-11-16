package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterWars;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * The raiders answer their leader's war speech (the user, 2026-11-16: "what do you think about replies during the war rally?
 * ex: we're not losing anyone, got it? and twelve people say got it!/yea!/right!!", then "every idea sounds great, we gonna
 * make it dynamic"). Not every line, or the speech drowns: a question that wants an answer gets two to four ("got it!",
 * "yeah!!"), a battle cry is cheered and echoed, a heartfelt line now and then gets a murmur ("we remember"), the last line
 * a roar from half of them. Each as its kind would (a newbie's "y-yeah!!", a sweat's "first kill's mine"). Once a speech,
 * maybe, one of them gets nervous ("what if they have more people than us") and a neighbour or the leader steadies them;
 * maybe a joker heckles ("can we loot first") and someone shushes them. Staggered a moment apart, as typed; the same one
 * seldom twice running.
 */
public final class WarCrowd {
	private WarCrowd() {
	}

	/** Who's listening: this near the leader, on its raid. */
	private static final double NEAR = 16.0;
	/** How likely a heartfelt line is murmured to; the nervous one and the joker, once a speech each. */
	private static final double MURMUR = 0.3;
	private static final double NERVOUS = 0.2;
	private static final double JOKER = 0.15;
	/** The most who roar at the end. */
	private static final int MOST_ROAR = 8;

	/** What kind of line the leader said. */
	public enum Kind {
		OPEN, PLAIN, ASK, CRY, CLOSE
	}

	/** A line to say later: who, its key, when, its words. */
	private record Later(DrifterEntity drifter, String key, long at, Object[] vars) {
	}

	/** A speech's own: who answered last line, and whether its nervous one and joker have spoken. */
	private static final class Speech {
		final Set<UUID> lastAnswered = new HashSet<>();
		boolean nervous;
		boolean joker;
	}

	private static final List<Later> later = new ArrayList<>();
	private static final Map<UUID, Speech> speeches = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WarCrowd::tick);
	}

	/** Asking them to answer, at a plain line's end ("we stick together. got it?"); not "remember the raid?". */
	private static final List<String> ASKING = List.of("got it?", "understood?", "with me?", "ready?", "clear?", "yeah?", "right?", "deal?",
		"agreed?", "you hear me?", "can you do that?", "all of you?", "everyone?");

	/** A plain speech line's kind by its words: one that asks them to answer, else plain. */
	public static Kind kindOf(String line) {
		String t = line.trim().toLowerCase(java.util.Locale.ROOT);
		return ASKING.stream().anyMatch(t::endsWith) ? Kind.ASK : Kind.PLAIN;
	}

	/** The leader said this line of its speech: the crowd's answer, if any. Returns ticks the speech should wait for it. */
	public static long heard(DrifterEntity leader, DrifterWars.Raid raid, String line, Kind kind, long now) {
		RandomSource random = leader.getRandom();
		Speech speech = speeches.computeIfAbsent(leader.getUUID(), id -> new Speech());
		if (kind == Kind.OPEN) {
			speech.lastAnswered.clear();
			speech.nervous = false;
			speech.joker = false;
		}

		List<DrifterEntity> crowd = crowd(leader, raid);
		if (crowd.isEmpty()) {
			return 0;
		}

		String leaderName = leader.getName().getString();
		Object[] vars = {"town", DrifterWars.name(raid.attackers), "enemy", DrifterWars.name(raid.defenders), "leader", leaderName};
		// (After its line's typed: a moment to read it.)
		long at = now + 15 + Math.min(40, line.length());
		List<DrifterEntity> fresh = new ArrayList<>(crowd.stream().filter(d -> !speech.lastAnswered.contains(d.getUUID())).toList());
		if (fresh.size() < 2) {
			fresh = new ArrayList<>(crowd);
		}

		java.util.Collections.shuffle(fresh, new java.util.Random(random.nextLong()));
		int n = switch (kind) {
			case ASK -> 2 + random.nextInt(3);
			case CRY -> 2 + random.nextInt(4);
			case PLAIN -> heartfelt(line) && random.nextDouble() < MURMUR ? 1 + random.nextInt(2) : 0;
			case CLOSE -> Math.min(MOST_ROAR, Math.max(2, (crowd.size() + 1) / 2));
			case OPEN -> 0;
		};
		String key = switch (kind) {
			case ASK -> "war.crowd.answer";
			case CRY -> "war.crowd.cheer";
			case CLOSE -> "war.crowd.roar";
			default -> "war.crowd.agree";
		};
		List<DrifterEntity> answering = kind == Kind.CLOSE ? new ArrayList<>(crowd) : fresh;
		java.util.Collections.shuffle(answering, new java.util.Random(random.nextLong()));
		speech.lastAnswered.clear();
		for (int i = 0; i < Math.min(n, answering.size()); i++) {
			// (A roar all at once, near enough; answers one after another.)
			at += kind == Kind.CLOSE ? 3 + random.nextInt(8) : 10 + random.nextInt(25);
			later.add(new Later(answering.get(i), key, at, vars));
			speech.lastAnswered.add(answering.get(i).getUUID());
		}

		long wait = 0;
		// Once a speech, maybe: a nervous one, and a neighbour (or the leader) steadying them; a joker, and someone shushing.
		if (kind == Kind.PLAIN && raid.speech >= 3 && crowd.size() >= 2) {
			if (!speech.nervous && random.nextDouble() < NERVOUS) {
				speech.nervous = true;
				DrifterEntity nervous = fresh.get(fresh.size() - 1);
				at += 25 + random.nextInt(20);
				later.add(new Later(nervous, "war.crowd.nervous", at, vars));
				Object[] about = with(vars, "who", nervous.getName().getString());
				DrifterEntity steady = random.nextBoolean() ? leader : crowd.stream().filter(d -> d != nervous).findAny().orElse(leader);
				at += 30 + random.nextInt(20);
				later.add(new Later(steady, steady == leader ? "war.crowd.leader_reassure" : "war.crowd.reassure", at, about));
				wait = 60;
			} else if (!speech.joker && random.nextDouble() < JOKER) {
				speech.joker = true;
				DrifterEntity joker = fresh.get(0);
				at += 20 + random.nextInt(20);
				later.add(new Later(joker, "war.crowd.joke", at, vars));
				if (random.nextDouble() < 0.6) {
					DrifterEntity shush = crowd.stream().filter(d -> d != joker).findAny().orElse(null);
					if (shush != null) {
						at += 15 + random.nextInt(20);
						later.add(new Later(shush, "war.crowd.hush", at, with(vars, "who", joker.getName().getString())));
					}
				}

				wait = 40;
			}
		}

		if (kind == Kind.CLOSE) {
			speeches.remove(leader.getUUID());
		}

		return wait;
	}

	/** Words of a heartfelt line (murmured to: "we remember"), not a joke or an order ("keep the pumpkin on your head"). */
	private static final List<String> HEARTFELT = List.of("remember", "proud", "home", "together", "family", "never", "lost", "dead", "fell", "forget",
		"built", "our people", "for everyone", "for them", "honor", "deserve", "grave", "miss", "friends", "brother", "sister", "kids", "back home",
		"trust", "love", "believe");

	private static boolean heartfelt(String line) {
		String lower = line.toLowerCase(java.util.Locale.ROOT);
		return HEARTFELT.stream().anyMatch(lower::contains);
	}

	/** Those listening: near the leader, alive, on the same raid. */
	private static List<DrifterEntity> crowd(DrifterEntity leader, DrifterWars.Raid raid) {
		return leader.level().getEntitiesOfClass(DrifterEntity.class, leader.getBoundingBox().inflate(NEAR),
			d -> d != leader && d.isAlive() && !d.isSleeping() && WarGoals.raidFor(d) == raid);
	}

	private static Object[] with(Object[] vars, String key, Object value) {
		Object[] more = java.util.Arrays.copyOf(vars, vars.length + 2);
		more[vars.length] = key;
		more[vars.length + 1] = value;
		return more;
	}

	private static void tick(MinecraftServer server) {
		if (later.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		List<Later> due = new ArrayList<>();
		for (Iterator<Later> it = later.iterator(); it.hasNext();) {
			Later l = it.next();
			if (now >= l.at()) {
				it.remove();
				due.add(l);
			}
		}

		for (Later l : due) {
			if (l.drifter().isAlive() && !l.drifter().isRemoved() && Lines.has(l.key())) {
				ChatLines.toEveryone(l.drifter());
				ChatLines.speak(l.drifter(), Lines.pick(l.drifter(), l.key(), l.vars()));
			}
		}
	}
}
