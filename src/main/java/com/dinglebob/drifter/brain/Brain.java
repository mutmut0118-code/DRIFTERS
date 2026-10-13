package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The brain (DESIGN.md sections 7 and 8): the selector layer that picks the current goal from the needs and the
 * strategist's target, with commitment so it doesn't flip-flop, and runs the goal's activity on the executor.
 * It re-scores once a second, and at once when an activity finishes.
 *
 * <p>Manual task commands take the body over: the brain switches itself off until {@code /drifter brain on}.
 */
public class Brain {
	public static final int SAVE_VERSION = 1;
	/** Re-score cadence (section 7): once a second. */
	public static final int RESCORE_TICKS = 20;
	/** The current goal's flat bonus (about 15% of the max score, section 7). */
	public static final double COMMITMENT_BONUS = 0.15;
	public static final double JITTER = 0.02;
	/** How sharply it prefers the best of the top three (higher is more predictable). TODO(milestone 7): Efficiency. */
	public static final double PICK_SHARPNESS = 4.0;
	/** A goal whose activity failed scores less for a while, so it tries something else. */
	public static final int FAILED_COOLDOWN_TICKS = 20 * 15;
	public static final double FAILED_PENALTY = 0.3;
	/** How often (ticks) it checks for a close threat between re-scores. */
	public static final int THREAT_CHECK_TICKS = 5;

	/** Re-score at the next tick (it got hurt, or something else important happened). */
	public void poke() {
		this.sinceRescore = RESCORE_TICKS;
	}

	/** A better goal must beat the current one by this much to take over at a checkpoint. */
	public static final double SWITCH_MARGIN = 0.05;

	/** {@code base}: the score before the commitment bonus and jitter (0 means it has nothing to do). */
	public record Scored(Goals.Goal goal, String name, double score, boolean urgent, double base) {
	}

	private final DrifterEntity drifter;
	private final Blacklist blacklist = new Blacklist();
	private final List<Goals.Goal> goals = Goals.all();
	private final Map<String, Long> failedUntil = new HashMap<>();
	private boolean enabled = true;
	@Nullable
	private Goals.Goal current;
	@Nullable
	private Activity activity;
	private long goalStarted;
	private int sinceRescore = RESCORE_TICKS;
	private List<Scored> lastScores = List.of();
	private List<String> lastSteps = List.of();
	@Nullable
	private Goals.Context lastContext;

	public Brain(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	public Blacklist blacklist() {
		return this.blacklist;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (!enabled) {
			this.stopActivity("brain switched off");
			this.current = null;
		}
	}

	public void tick() {
		if (!this.enabled || !this.drifter.isAlive()) {
			return;
		}

		Task running = this.drifter.getTasks().current();
		if (running != null && running != this.activity) {
			// Someone gave it a command: the command has the body now.
			this.enabled = false;
			this.activity = null;
			this.current = null;
			this.think("brain off: following a command (/drifter brain on to resume)");
			return;
		}

		boolean finished = this.activity != null && running == null;
		if (finished) {
			this.activity = null;
		}

		// Danger doesn't wait for the once-a-second re-score: a close or hitting hostile is checked every 5 ticks.
		boolean danger = this.drifter.tickCount % THREAT_CHECK_TICKS == 0 && this.current != null && !"fight".equals(this.current.id())
			&& !"flee".equals(this.current.id()) && Threats.assess(this.drifter).urgent();
		if (++this.sinceRescore < RESCORE_TICKS && !finished && this.activity != null && !danger) {
			return;
		}

		this.sinceRescore = 0;
		this.decide();
	}

	private void think(String text) {
		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), text);
	}

	private Goals.Context context() {
		Strategist.Target target = Strategist.nextTarget(this.drifter);
		int stage = Strategist.stage(this.drifter);
		KnownWorld world = KnownWorld.scan(this.drifter);
		Needs needs = Needs.compute(this.drifter, target != null, stage);
		return new Goals.Context(this.drifter, world, needs, stage, target, Schedule.believedTime(this.drifter), Threats.assess(this.drifter),
			Shelter.isSealedIn(this.drifter));
	}

	private void decide() {
		Goals.Context context = this.context();
		this.lastContext = context;
		long now = this.drifter.level().getGameTime();
		List<Scored> scores = new ArrayList<>();
		for (Goals.Goal goal : this.goals) {
			double base = goal.score(context);
			if (base > 0.0 && this.failedUntil.getOrDefault(goal.id(), 0L) > now) {
				base *= FAILED_PENALTY;
			}

			double score = base;
			if (goal == this.current && base > 0.0) {
				score += COMMITMENT_BONUS;
			}

			if (base > 0.0) {
				score += (this.drifter.getRandom().nextDouble() * 2.0 - 1.0) * JITTER;
			}

			scores.add(new Scored(goal, goal.name(context), Math.max(0.0, score), goal.urgent(context), base));
		}

		scores.sort(Comparator.comparingDouble(Scored::score).reversed());
		this.lastScores = scores;
		Scored best = scores.get(0);

		if (this.activity != null) {
			Scored currentScore = scores.stream().filter(s -> s.goal() == this.current).findFirst().orElse(null);
			double currentValue = currentScore == null ? 0.0 : currentScore.score();
			boolean committed = now - this.goalStarted < (this.current == null ? 0 : this.current.minCommitTicks());
			boolean takeOver = best.goal() != this.current && best.score() > currentValue + SWITCH_MARGIN
				&& (best.urgent() || !committed) && this.activity.atCheckpoint();
			if (takeOver) {
				this.stopActivity("switching to " + best.name());
				this.start(best, scores, context, now);
			}

			return;
		}

		// Nothing running: carry on with the current goal while committed (unless it just failed or has nothing left to
		// do), else a weighted pick among the top three.
		Scored chosen = null;
		boolean justFailed = this.current != null && this.failedUntil.getOrDefault(this.current.id(), 0L) > now;
		if (this.current != null && !justFailed && now - this.goalStarted < this.current.minCommitTicks()) {
			int rank = 0;
			for (Scored scored : scores) {
				if (scored.goal() == this.current && rank < 3 && scored.base() > 0.0 && !(best.urgent() && best.base() > 0.0)) {
					chosen = scored;
				}

				rank++;
			}
		}

		if (chosen == null) {
			chosen = this.pickTopThree(scores);
		}

		if (chosen != null) {
			this.start(chosen, scores, context, now);
		}
	}

	@Nullable
	private Scored pickTopThree(List<Scored> scores) {
		if (scores.get(0).urgent() && scores.get(0).base() > 0.0) {
			return scores.get(0);
		}

		List<Scored> top = scores.stream().filter(s -> s.base() > 0.0).limit(3).toList();
		if (top.isEmpty()) {
			return null;
		}

		double total = top.stream().mapToDouble(s -> Math.pow(s.score(), PICK_SHARPNESS)).sum();
		double roll = this.drifter.getRandom().nextDouble() * total;
		for (Scored scored : top) {
			roll -= Math.pow(scored.score(), PICK_SHARPNESS);
			if (roll <= 0.0) {
				return scored;
			}
		}

		return top.get(0);
	}

	private void start(Scored chosen, List<Scored> scores, Goals.Context context, long now) {
		Activity next = chosen.goal().start(context);
		this.lastSteps = chosen.goal().steps(context);
		if (next == null) {
			this.failedUntil.put(chosen.goal().id(), now + FAILED_COOLDOWN_TICKS);
			return;
		}

		if (chosen.goal() != this.current) {
			this.current = chosen.goal();
			this.goalStarted = now;
			StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "picked %s %.2f", chosen.name(), chosen.score()));
			List<Scored> others = scores.stream().filter(s -> s != chosen && s.score() > 0.0).limit(2).toList();
			if (!others.isEmpty()) {
				line.append(" over ");
				for (int i = 0; i < others.size(); i++) {
					line.append(i > 0 ? ", " : "").append(String.format(Locale.ROOT, "%s %.2f", others.get(i).name(), others.get(i).score()));
				}
			}

			this.think(line.toString());
		}

		this.activity = next;
		Goals.Goal goal = chosen.goal();
		this.drifter.getTasks().start(next, message -> this.onActivityMessage(goal, message));
	}

	private void onActivityMessage(Goals.Goal goal, Component message) {
		String text = message.getString();
		if (text.contains("Failed:")) {
			this.failedUntil.put(goal.id(), this.drifter.level().getGameTime() + FAILED_COOLDOWN_TICKS);
			// The commitment ends with the failure: something else gets a turn.
			this.goalStarted = Long.MIN_VALUE / 2;
		}

		this.think(text.replaceFirst("^\\[[^]]*] ", ""));
	}

	private void stopActivity(String why) {
		if (this.activity != null && this.drifter.getTasks().current() == this.activity) {
			this.drifter.getTasks().cancel(why);
		}

		this.activity = null;
	}

	// ---------------------------------------------------------------------------------------------
	// Debug views
	// ---------------------------------------------------------------------------------------------

	@Nullable
	public Goals.Goal currentGoal() {
		return this.current;
	}

	@Nullable
	public Activity activity() {
		return this.activity;
	}

	public List<Scored> lastScores() {
		return this.lastScores;
	}

	public List<String> lastSteps() {
		return this.lastSteps;
	}

	@Nullable
	public Goals.Context lastContext() {
		return this.lastContext;
	}

	/** Forces a goal for its commit time (debug command). */
	public boolean force(String id) {
		for (Goals.Goal goal : this.goals) {
			if (goal.id().equals(id)) {
				Goals.Context context = this.context();
				this.enabled = true;
				this.stopActivity("forced " + id);
				this.current = null;
				this.start(new Scored(goal, goal.name(context), 1.0, true, 1.0), List.of(), context, this.drifter.level().getGameTime());
				return this.activity != null;
			}
		}

		return false;
	}

	public List<String> goalIds() {
		return this.goals.stream().map(Goals.Goal::id).toList();
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		tag.putBoolean("Enabled", this.enabled);
		return tag;
	}

	public void load(CompoundTag tag) {
		// Version 1 is the first format.
		this.enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
	}
}
