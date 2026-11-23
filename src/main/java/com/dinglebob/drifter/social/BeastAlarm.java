package com.dinglebob.drifter.social;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A shout for help at a dangerous beast (the user, 2026-11-14: tusklins "keep killing off the town"; their pick, guards gang
 * up and the rest keep out of reach). One of a group (a town) hurt by a beast of the threat table shouts; those of its group
 * in earshot who are armed for it (Threats.armed) and fit come running, and fight it together (several at once: one it turns
 * on backs off behind its shield, the others strike from behind, AttackTask.Protocol.BEAST). Those not armed stay away.
 * Knowledge through perception: only those who heard the shout know, and only where it came from.
 */
public final class BeastAlarm {
	private BeastAlarm() {
	}

	/** A shout carries this far (blocks). */
	private static final double EARSHOT = 48.0;
	/** A shout is answered for this long (ticks), and one Drifter shouts at most this often. */
	private static final long HEARD_FOR = 20L * 40;
	private static final long SHOUT_EVERY = 20L * 10;
	/** It answers only above this share of its health. */
	private static final float FIT = 0.6F;
	/** Close enough to the shout: from here its own eyes take over (Threats, the fight goal). */
	private static final double THERE = 8.0;

	/** A shout heard: where it came from, at what, when. */
	public record Call(Vec3 pos, UUID beast, String name, long at, UUID from) {
	}

	private static final Map<UUID, Call> heard = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> shouted = new ConcurrentHashMap<>();

	/** It was hurt by this: a beast of the threat table, and it's of a group: it shouts, and its group in earshot hear. */
	public static void hurtBy(DrifterEntity victim, LivingEntity attacker) {
		if (!Threats.beast(attacker) || attacker instanceof DrifterEntity) {
			return;
		}

		// (Felt, if not seen: hit from behind, it knows what and roughly where.)
		Threats.rememberBeast(victim, attacker);

		DrifterGroups.Group group = DrifterGroups.of(victim.getUUID());
		long now = victim.level().getGameTime();
		if (group == null || now - shouted.getOrDefault(victim.getUUID(), Long.MIN_VALUE / 2) < SHOUT_EVERY) {
			return;
		}

		shouted.put(victim.getUUID(), now);
		String name = attacker.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		ChatLines.speak(victim, List.of(name.toUpperCase(java.util.Locale.ROOT) + "!! help", "help!! " + name, name + " " + name + " HELP", "HELP theres a " + name)
			.get(victim.getRandom().nextInt(4)));
		victim.getMemory().thoughts().add(now, "shouted for help: a " + name);
		warned.put(attacker.getUUID(), now);
		call(victim, attacker, name, now);
	}

	/** Seen coming this close, a beast gets a warning shout before it gores anyone (blocks). */
	private static final double SPOTTED_WITHIN = 24.0;
	/** One warning per beast this often, whoever sees it. */
	private static final long WARN_EVERY = 20L * 30;
	private static final Map<UUID, Long> warned = new ConcurrentHashMap<>();

	/**
	 * It sees a territorial beast close by (one of its town at work with its back turned is gored before anyone knows; found
	 * with tusklins, 2026-11-23): a warning shout, and its group's armed in earshot come before it gores someone.
	 */
	public static void spotted(DrifterEntity drifter, LivingEntity beast) {
		if (Threats.leash(beast) <= 0.0 || drifter.distanceTo(beast) > SPOTTED_WITHIN || DrifterGroups.of(drifter.getUUID()) == null) {
			return;
		}

		long now = drifter.level().getGameTime();
		if (now - warned.getOrDefault(beast.getUUID(), Long.MIN_VALUE / 2) < WARN_EVERY) {
			return;
		}

		warned.put(beast.getUUID(), now);
		warned.entrySet().removeIf(e -> now - e.getValue() > WARN_EVERY * 4);
		String name = beast.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		ChatLines.speak(drifter, List.of(name + "!!", "theres a " + name + " here", "watch out, " + name, name.toUpperCase(java.util.Locale.ROOT) + " guys")
			.get(drifter.getRandom().nextInt(4)));
		drifter.getMemory().thoughts().add(now, "warned the others: a " + name);
		call(drifter, beast, name, now);
	}

	/** Its group in earshot hear it, from where it is. */
	private static void call(DrifterEntity from, LivingEntity beast, String name, long now) {
		DrifterGroups.Group group = DrifterGroups.of(from.getUUID());
		if (group == null || !(from.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}

		Call call = new Call(beast.position(), beast.getUUID(), name, now, from.getUUID());
		for (UUID id : group.members) {
			if (!id.equals(from.getUUID()) && level.getEntity(id) instanceof DrifterEntity mate && mate.isAlive() && mate.distanceTo(from) <= EARSHOT) {
				heard.put(id, call);
			}
		}
	}

	/** The shout it heard and may answer, or null. */
	@Nullable
	public static Call heard(DrifterEntity drifter) {
		Call call = heard.get(drifter.getUUID());
		if (call == null) {
			return null;
		}

		if (drifter.level().getGameTime() - call.at() > HEARD_FOR) {
			heard.remove(drifter.getUUID());
			return null;
		}

		return call;
	}

	/** It's answering a shout at this beast: the fight with it comes first (Goals.Fight). */
	public static boolean answering(DrifterEntity drifter, @Nullable LivingEntity beast) {
		Call call = beast == null ? null : heard(drifter);
		return call != null && call.beast().equals(beast.getUUID());
	}

	/** Fit and armed to answer a shout. */
	static boolean canAnswer(DrifterEntity drifter) {
		return Threats.armed(drifter) && drifter.getHealth() >= drifter.getMaxHealth() * FIT;
	}

	public static List<Goals.Goal> goals() {
		return List.of(new Answer());
	}

	/** Answering a shout for help: there at a run; once it sees the beast, the fight goal takes over. */
	public static final class Answer extends Goals.Goal {
		@Override
		public String id() {
			return "answer_shout";
		}

		@Override
		public String name(Goals.Context context) {
			Call call = heard(context.drifter());
			return call == null ? "answer a shout for help" : "answer a shout for help (a " + call.name() + ")";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Call call = heard(drifter);
			if (call == null || !canAnswer(drifter) || context.threat().decision().fights() || drifter.position().distanceTo(call.pos()) <= THERE) {
				return 0.0;
			}

			// (Above work and most things, below a fight or running from one: it drops what it's doing and comes.)
			return 1.05;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Nullable
		@Override
		public Activity start(Goals.Context context) {
			Call call = heard(context.drifter());
			return call == null ? null : new Run(context.drifter(), call);
		}
	}

	/** At a run to where the shout came from, till it's there or sees what it's for. */
	private static final class Run extends Activity {
		private final Call call;
		private boolean went;

		Run(DrifterEntity drifter, Call call) {
			super(drifter);
			this.call = call;
		}

		@Override
		public String describe() {
			return "run to help (a " + this.call.name() + ")";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 45;
		}

		@Override
		protected Status next() {
			if (this.went || Threats.assess(this.drifter).decision().fights()) {
				return Status.SUCCESS;
			}

			this.went = true;
			this.think("running to help: someone shouted, a " + this.call.name());
			return this.begin(new NavigateTask(this.drifter, this.call.pos(), THERE * 0.75, "run to help", NavigateTask.Pace.TRAVEL).urgent());
		}

		@Override
		public boolean atCheckpoint() {
			return true;
		}

		/** It sees the beast and can take it on: the fight goal (which comes first while it answers) takes over. */
		@Override
		protected boolean rethink() {
			return this.ticksRunning() % 10 == 0 && Threats.assess(this.drifter).decision().fights();
		}
	}

	/** Forgets those gone (so the maps don't grow). */
	public static void forget(UUID id) {
		heard.remove(id);
		shouted.remove(id);
	}
}
