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

import net.minecraft.core.BlockPos;
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
	/**
	 * How sharply it prefers the best of the top three (higher is more predictable): from the Efficiency trait (section
	 * 7), from scatterbrained (wanders off to chase a sheep mid-task) to methodical.
	 */
	public static final double PICK_SHARPNESS_MIN = 1.5;
	public static final double PICK_SHARPNESS_MAX = 8.0;
	/** A goal whose activity failed scores less for a while, so it tries something else. */
	public static final int FAILED_COOLDOWN_TICKS = 20 * 15;
	public static final double FAILED_PENALTY = 0.3;
	/** How often (ticks) it checks for a close threat between re-scores. */
	public static final int THREAT_CHECK_TICKS = 5;

	/**
	 * Legs of exploring for stone before it digs down for it, by mining style (user's note for milestone 7, section 10.3
	 * "chosen by personality"): a strip miner digs after at most one look around, a caver and surface explorer keeps
	 * looking for caves and cliffs for up to 5 legs. Frustration makes it dig sooner (section 17: riskier shortcuts).
	 */
	public static final int SEARCH_LEGS_MAX = 5;

	public int searchLegsBeforeDigging() {
		com.dinglebob.drifter.personality.Personality p = this.drifter.getPersonality();
		int legs = (int)Math.round((1.0 - p.habits().miningLean) * SEARCH_LEGS_MAX);
		if (p.is(com.dinglebob.drifter.personality.Mood.FRUSTRATED)) {
			legs /= 2;
		}

		return Math.max(0, Math.min(SEARCH_LEGS_MAX, legs));
	}

	private final Map<String, Integer> explored = new HashMap<>();
	/** Tonight's plan (section 18), chosen once per night, and the night tunnel if it's mining. Not saved: chosen again. */
	private Schedule.NightStyle nightStyle = Schedule.NightStyle.SHELTER;
	private long nightStyleDay = Long.MIN_VALUE;
	@Nullable
	private com.dinglebob.drifter.brain.mining.Tunnel nightTunnel;
	/** Structure loot it has dealt with (chests and spawners by position, chest minecarts by id), so it doesn't go back. */
	public final java.util.Set<net.minecraft.core.BlockPos> looted = new java.util.HashSet<>();
	/** Its last fight-or-flight decision and when it changed (for commitment, Threats#assess). Not saved. */
	@org.jetbrains.annotations.Nullable
	public Threats.Decision lastThreatDecision;
	public long lastThreatDecisionAt;
	/** Where it was at the last stuck check, and when (fleeing in place: it fights back instead). */
	@org.jetbrains.annotations.Nullable
	public net.minecraft.world.phys.Vec3 stuckCheckPos;
	public long stuckCheckAt;
	public boolean stuckInPlace;
	/** Hits taken from mobs while backing off, within 5 s of each other, and when the last one was. */
	public int hitsWhileBackingOff;
	public long lastHitWhileBackingOff;

	/** A crafting table it put down away from home (tidy Drifters pick it back up), or null. Not saved. */
	@org.jetbrains.annotations.Nullable
	public net.minecraft.core.BlockPos strayTable;
	public final java.util.Set<java.util.UUID> lootedCarts = new java.util.HashSet<>();

	/** Game time until which home counts as cut off by mobs (it fled from them on the way): it shelters where it is. */
	private long homeCutOffUntil;

	/** Mobs stood between it and home tonight: it doesn't try to walk back through them until morning. */
	public void cutOffFromHome() {
		long now = this.drifter.level().getGameTime();
		long time = Schedule.believedTime(this.drifter);
		long untilMorning = time < Schedule.NIGHT_END ? Schedule.NIGHT_END - time : 24000 - time + Schedule.NIGHT_END;
		this.homeCutOffUntil = now + Math.max(20L * 60, untilMorning);
	}

	public boolean homeCutOff() {
		return this.drifter.level().getGameTime() < this.homeCutOffUntil;
	}

	/**
	 * How it spends tonight: chosen when it first thinks about shelter each day (late afternoon), then kept for the
	 * night. A hunter that stops being fit for it (hurt, hungry) still digs in: see {@link Schedule#fitToHunt}.
	 */
	public Schedule.NightStyle nightStyle() {
		long time = Schedule.believedTime(this.drifter);
		long day = this.drifter.getSenses().time().believedDay();
		boolean evening = time >= Schedule.shelterThinking(this.drifter);
		if (evening && this.nightStyleDay != day) {
			this.nightStyleDay = day;
			this.nightStyle = Schedule.chooseNightStyle(this.drifter);
			this.nightTunnel = null;
			this.think("tonight it " + this.nightStyle.describe()
				+ (this.nightStyle == Schedule.NightStyle.SHELTER ? " (" + Schedule.shelterReason(this.drifter) + ")" : ""));
		}

		return this.nightStyleDay == day || Schedule.isNight(time) ? this.nightStyle : Schedule.NightStyle.SHELTER;
	}

	@Nullable
	public com.dinglebob.drifter.brain.mining.Tunnel nightTunnel() {
		return this.nightTunnel;
	}

	public void setNightTunnel(@Nullable com.dinglebob.drifter.brain.mining.Tunnel tunnel) {
		this.nightTunnel = tunnel;
	}

	/** Forces tonight's plan (debug command). */
	public void forceNightStyle(Schedule.NightStyle style) {
		this.nightStyle = style;
		this.nightStyleDay = this.drifter.getSenses().time().believedDay();
	}
	/** Where it settled, its chests and what's in them (section 9 stage 4; saved). */
	@Nullable
	private Home home;

	/** A Nether trip under way (section 19), or null. Saved: the portal rebuilds the Drifter on the other side. */
	@Nullable
	private NetherTrip netherTrip;
	/** When its last Nether trip ended (game time), 0 if never. */
	private long lastNetherTrip;

	@Nullable
	public NetherTrip netherTrip() {
		return this.netherTrip;
	}

	public void setNetherTrip(@Nullable NetherTrip trip) {
		this.netherTrip = trip;
	}

	public long lastNetherTrip() {
		return this.lastNetherTrip;
	}

	/** Back in the overworld from a trip (or a trip that never got through): it's over. */
	private void endNetherTripIfBack() {
		if (this.netherTrip == null || NetherGoals.inNether(this.drifter)) {
			return;
		}

		long now = this.drifter.level().getGameTime();
		boolean back = this.netherTrip.phase != NetherTrip.Phase.GOING;
		boolean gaveUp = this.netherTrip.phase == NetherTrip.Phase.GOING && now - this.netherTrip.startedAt > 20 * 120;
		if (back || gaveUp) {
			this.think(back ? "back from the Nether" : "didn't get through the portal");
			this.netherTrip = null;
			this.lastNetherTrip = now;
		}
	}

	@Nullable
	public Home home() {
		return this.home;
	}

	/** Who it's chasing and what it knows of where they went (user, 2026-10-17); null if no one. */
	@Nullable
	private Pursuit pursuit;
	/** The last scan of what it knows round it (KnownWorld#scan: one a tick). */
	@org.jetbrains.annotations.Nullable
	KnownWorld knownWorldCache;

	/**
	 * What it knows round it changed by its own hand (it placed or broke a block): the next decision scans afresh, not the
	 * scan held for a few seconds (found in a test of 50 Drifters' whims: having placed its crafting table, the held scan
	 * still had none, so it made another and placed that too, again and again).
	 */
	public void forgetKnownWorld() {
		this.knownWorldCache = null;
	}

	@Nullable
	public Pursuit pursuit() {
		return this.pursuit;
	}

	/** It's fighting this one and sees them now: the chase starts, or goes on (renewed). */
	public void pursue(com.dinglebob.drifter.entity.DrifterEntity drifter, net.minecraft.world.entity.LivingEntity target, long now) {
		long lasts = Pursuit.persistence(drifter);
		if (this.pursuit == null || this.pursuit.target != target) {
			this.pursuit = new Pursuit(target, now, lasts);
		}

		this.pursuit.sighted(now, lasts);
	}

	public void endPursuit() {
		this.pursuit = null;
	}

	public void setHome(@Nullable Home home) {
		this.home = home;
		if (!this.drifter.level().isClientSide()) {
			com.dinglebob.drifter.api.DrifterEvents.HOME_SET.invoker().onHomeSet(this.drifter, home);
		}
	}

	/** Its chests were all full last time it tried to put things away: it wants another chest. */
	private boolean storageFull;

	public boolean storageFull() {
		return this.storageFull;
	}

	public void noteStorageFull() {
		this.storageFull = true;
	}

	public void clearStorageFull() {
		this.storageFull = false;
	}

	/** Batches it left cooking (saved). */
	private final List<FurnaceJob> furnaceJobs = new ArrayList<>();

	public List<FurnaceJob> furnaceJobs() {
		return this.furnaceJobs;
	}

	public void addFurnaceJob(FurnaceJob job) {
		this.furnaceJobs.removeIf(old -> old.furnace.equals(job.furnace) && old.output == job.output);
		this.furnaceJobs.add(job);
	}

	public void removeFurnaceJob(FurnaceJob job) {
		this.furnaceJobs.remove(job);
	}

	/** The last meal it cooked in a pot (Farmer's Delight; not saved: something to talk about for a while), and when. */
	@org.jetbrains.annotations.Nullable
	private net.minecraft.world.item.Item cookedMeal;
	private long cookedAt = Long.MIN_VALUE / 2;

	/**
	 * A meal it left cooking in a pot (Farmer's Delight): where, what, and when it's done. It comes back for it, as for a
	 * furnace's batch. Not saved.
	 */
	public record PotBatch(BlockPos pot, net.minecraft.world.item.Item meal, long readyAt) {
	}

	@org.jetbrains.annotations.Nullable
	private PotBatch potBatch;

	@org.jetbrains.annotations.Nullable
	public PotBatch potBatch() {
		return this.potBatch;
	}

	public void setPotBatch(@org.jetbrains.annotations.Nullable PotBatch batch) {
		this.potBatch = batch;
	}

	public void noteCooked(net.minecraft.world.item.Item meal, long now) {
		this.cookedMeal = meal;
		this.cookedAt = now;
	}

	/** The meal it cooked within this many ticks, or null. */
	@org.jetbrains.annotations.Nullable
	public net.minecraft.world.item.Item cookedLately(long now, long within) {
		return now - this.cookedAt <= within ? this.cookedMeal : null;
	}

	/** An exploring leg for this reason ended. */
	public void noteExplored(String reason) {
		this.explored.merge(reason, 1, Integer::sum);
	}

	public int timesExplored(String reason) {
		return this.explored.getOrDefault(reason, 0);
	}

	/** The heading (radians) it keeps on a long search for something, per reason. Not saved. */
	public final java.util.Map<String, Double> exploreHeading = new java.util.HashMap<>();

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
	/** Failures of a goal in a row (reset when it gets done): each one makes the next cool-down longer. */
	private final Map<String, Integer> failedInARow = new HashMap<>();
	/** From this many failures in a row, a goal waits out its cool-down completely instead of only scoring lower. */
	private static final int FAILURES_BEFORE_REST = 3;
	/** Longest cool-down, however often it failed (4 minutes). */
	private static final int MAX_FAILED_COOLDOWN_TICKS = FAILED_COOLDOWN_TICKS * 16;

	/** An item someone asked it to get (`/drifter get`, debug), and how many to end up with; not saved. */
	@Nullable
	public net.minecraft.world.item.Item requested;
	public int requestedCount;

	/** This goal failed lately and is in its cool-down. */
	public boolean failedRecently(String goalId) {
		return this.failedUntil.getOrDefault(goalId, 0L) > this.drifter.level().getGameTime();
	}
	private boolean enabled = true;
	@Nullable
	private Goals.Goal current;
	@Nullable
	private Activity activity;
	private long goalStarted;
	private int sinceRescore = RESCORE_TICKS;
	private boolean phased;
	/** The tick (its own count) of its first decision, or -1 before its first brain tick. */
	private int firstDecisionAt = -1;
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

		this.watchForStall();

		// Danger doesn't wait for the once-a-second re-score: a close or hitting hostile is checked every 5 ticks.
		boolean danger = (this.drifter.tickCount + this.drifter.getId()) % THREAT_CHECK_TICKS == 0 && this.current != null && !"fight".equals(this.current.id())
			&& !"flee".equals(this.current.id()) && Threats.assess(this.drifter).urgent();
		if (++this.sinceRescore < this.drifter.tier().rescoreTicks && !finished && this.activity != null && !danger) {
			return;
		}

		// Its very first decision too waits for its own point in the second: 300 soldiers made by one command all decided on
		// their first tick together, half a second in one tick (milestone 16). Counted from its first tick here, not one exact
		// tick of every second: a Drifter far from players thinks every other tick, and for half of them that tick never came
		// (found in a 50v50 in a village: a quarter of the soldiers stood the whole battle with no goal at all).
		if (!this.phased && this.activity == null && !danger) {
			if (this.firstDecisionAt < 0) {
				this.firstDecisionAt = this.drifter.tickCount + Math.floorMod(this.drifter.getUUID().hashCode(), RESCORE_TICKS);
			}

			if (this.drifter.tickCount < this.firstDecisionAt) {
				return;
			}
		}

		// The first time, each at its own point in the second, so Drifters loaded together don't all decide on the same tick
		// (section 22 budget: that made the worst ticks several times the average).
		this.sinceRescore = this.phased ? 0 : Math.floorMod(this.drifter.getUUID().hashCode(), RESCORE_TICKS);
		this.phased = true;
		this.decide();
	}

	private void think(String text) {
		this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), text);
	}

	private Goals.Context context() {
		long t = System.nanoTime();
		int stage = Strategist.stage(this.drifter);
		KnownWorld world = KnownWorld.scan(this.drifter);
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.BRAIN_WORLD, System.nanoTime() - t);
		t = System.nanoTime();
		// While one target waits on a furnace, it works on the next one (multitasking, section 10.4).
		long now = this.drifter.level().getGameTime();
		Strategist.Target target = Strategist.nextTarget(this.drifter, candidate -> {
			if (this.furnaceJobs.isEmpty()) {
				return true;
			}

			Planner.Plan plan = Planner.plan(this.drifter, world, candidate.items(), candidate.count());
			return !(plan.next() instanceof Planner.Collect collect && !collect.job().ready(now));
		});
		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.BRAIN_TARGET, System.nanoTime() - t, () -> this.drifter.getName().getString() + ": " + (target == null ? "no target" : "target " + target.label()));
		Needs needs = Needs.compute(this.drifter, target != null, stage);
		return new Goals.Context(this.drifter, world, needs, stage, target, Schedule.believedTime(this.drifter), Threats.assess(this.drifter),
			Shelter.isSheltered(this.drifter));
	}

	private void decide() {
		this.endNetherTripIfBack();
		Goals.Context context = this.context();
		this.lastContext = context;
		long now = this.drifter.level().getGameTime();
		List<Scored> scores = new ArrayList<>();
		boolean nether = NetherGoals.inNether(this.drifter);
		// Survival first (milestone 16): fights, running, kill on sight, chases, air. When one of those must happen now and
		// comes out on top by a clear margin over anything else ever scores, nothing else could change the choice (an urgent
		// best is taken as it is), so the rest aren't scored at all (in a battle every soldier worked out crafting plans and
		// whims every second; the first decisions of 300 soldiers took half a second in one tick).
		boolean survivalDecided = false;
		List<Goals.Goal> rest = new ArrayList<>();
		for (Goals.Goal goal : this.orderedGoals()) {
			if (survivalDecided && !FIRST_GOALS.contains(goal.id()) && goal != this.current) {
				continue;
			}

			if (!survivalDecided && !FIRST_GOALS.contains(goal.id()) && !scores.isEmpty()) {
				Scored top = scores.stream().max(Comparator.comparingDouble(Scored::score)).orElse(null);
				if (top != null && top.urgent() && top.base() > 0.0
					&& (top.score() >= DECIDED_SCORE || top.goal() == this.current && top.score() >= DECIDED_CONTINUING)) {
					survivalDecided = true;
					if (goal != this.current) {
						continue;
					}
				}
			}

			if (FIRST_GOALS.contains(goal.id())) {
				scores.add(this.scoreOne(goal, context, now, nether, Double.NaN));
			} else {
				rest.add(goal);
			}
		}

		// The rest by branch and bound (milestone 16: scoring every goal of every Drifter was a decision's dearest part, with
		// a thousand of them). Each one's urgency first (how much it would like to: cheap), then, the most wanted first,
		// whether it can and at what risk (the planning: dear) only while it could still make the top three. Feasibility and
		// risk are at most 1, so its urgency, plus the jitter and (for the one it's on) the commitment bonus, is the most it
		// could score. The one it's on is always scored in full (switching away from it weighs its score).
		double[] urgencies = new double[rest.size()];
		Integer[] order = new Integer[rest.size()];
		for (int i = 0; i < urgencies.length; i++) {
			Goals.Goal goal = rest.get(i);
			long t = System.nanoTime();
			urgencies[i] = nether && !NetherGoals.IN_THE_NETHER.contains(goal.id()) ? 0.0 : goal.urgency(context);
			// (Its line of work in its town: the things of its line count a little more. Milestone 23 B1.)
			var work = this.drifter.getTownWork();
			if (urgencies[i] > 0.0 && work != null) {
				urgencies[i] += work.leaning(this.drifter, goal.id());
			}
			com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.BRAIN_SCORE, System.nanoTime() - t,
				() -> this.drifter.getName().getString() + ": urgency of " + goal.id());
			order[i] = i;
		}

		java.util.Arrays.sort(order, (a, b) -> Double.compare(urgencies[b], urgencies[a]));
		double[] top3 = {-1.0, -1.0, -1.0};
		for (Scored scored : scores) {
			keepTop3(top3, scored.score());
		}

		for (Integer i : order) {
			Goals.Goal goal = rest.get(i);
			double urgency = urgencies[i];
			double bound = urgency <= 0.0 ? 0.0 : urgency + JITTER + (goal == this.current ? COMMITMENT_BONUS : 0.0);
			if (goal != this.current && (urgency <= 0.0 || bound < top3[2])) {
				scores.add(new Scored(goal, goal.name(context), 0.0, false, 0.0));
				continue;
			}

			Scored scored = this.scoreOne(goal, context, now, nether, urgency);
			keepTop3(top3, scored.score());
			scores.add(scored);
		}

		scores.sort(Comparator.comparingDouble(Scored::score).reversed());
		this.lastScores = scores;
		Scored best = scores.get(0);

		if (this.activity != null) {
			Scored currentScore = scores.stream().filter(s -> s.goal() == this.current).findFirst().orElse(null);
			double currentValue = currentScore == null ? 0.0 : currentScore.score();
			boolean committed = now - this.goalStarted < (this.current == null ? 0 : this.current.minCommitTicks());
			// Fighting or fleeing a mob that's on it doesn't wait for a checkpoint (it drops a half-dug shelter block).
			boolean survival = best.urgent() && ("fight".equals(best.goal().id()) || "flee".equals(best.goal().id()));
			boolean takeOver = best.goal() != this.current && best.score() > currentValue + SWITCH_MARGIN
				&& (best.urgent() || !committed) && (this.activity.atCheckpoint() || survival);
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

	/** One goal's score now ({@code urgency} if already worked out, else NaN). */
	private Scored scoreOne(Goals.Goal goal, Goals.Context context, long now, boolean nether, double urgency) {
		long t = System.nanoTime();
		// In the Nether, only what makes sense there (no settling, farming or night shelters).
		double base;
		if (nether && !NetherGoals.IN_THE_NETHER.contains(goal.id())) {
			base = 0.0;
		} else if (Double.isNaN(urgency)) {
			base = goal.score(context);
		} else {
			base = urgency <= 0.0 ? 0.0 : urgency * goal.feasibility(context) * goal.risk(context);
		}

		com.dinglebob.drifter.debug.DrifterPerf.add(com.dinglebob.drifter.debug.DrifterPerf.Part.BRAIN_SCORE, System.nanoTime() - t, () -> this.drifter.getName().getString() + ": goal " + goal.id());
		if (base > 0.0 && this.failedUntil.getOrDefault(goal.id(), 0L) > now) {
			// Failing again and again (found in testing: "gather cobblestone" on ground with no stone failed and was picked
			// again every few ticks, 1,851 times): it leaves it alone for a while. Not fights and escapes: those it keeps at.
			base *= this.failedInARow.getOrDefault(goal.id(), 0) >= FAILURES_BEFORE_REST && !goal.urgent(context) ? 0.0 : FAILED_PENALTY;
		}

		double score = base;
		if (goal == this.current && base > 0.0) {
			score += COMMITMENT_BONUS;
		}

		if (base > 0.0) {
			score += (this.drifter.getRandom().nextDouble() * 2.0 - 1.0) * JITTER;
		}

		return new Scored(goal, goal.name(context), Math.max(0.0, score), goal.urgent(context), base);
	}

	/** Keeps the three highest scores, highest first. */
	private static void keepTop3(double[] top3, double score) {
		if (score > top3[0]) {
			top3[2] = top3[1];
			top3[1] = top3[0];
			top3[0] = score;
		} else if (score > top3[1]) {
			top3[2] = top3[1];
			top3[1] = score;
		} else if (score > top3[2]) {
			top3[2] = score;
		}
	}

	/** Goals that can't wait (scored first). */
	private static final java.util.Set<String> FIRST_GOALS = java.util.Set.of("fight", "flee", "rampage", "pursue", "outofwater");
	/** An urgent survival goal this high: no other goal scores near it (the most any other reaches in testing is about 1.1). */
	private static final double DECIDED_SCORE = 1.15;
	/** ...or the one it's already on, still on top this high (keeping at a kill-on-sight or a chase). */
	private static final double DECIDED_CONTINUING = 0.95;
	@Nullable
	private List<Goals.Goal> ordered;

	/** Its goals, survival first. */
	private List<Goals.Goal> orderedGoals() {
		if (this.ordered == null || this.ordered.size() != this.goals.size()) {
			List<Goals.Goal> first = new ArrayList<>();
			List<Goals.Goal> rest = new ArrayList<>();
			for (Goals.Goal goal : this.goals) {
				(FIRST_GOALS.contains(goal.id()) ? first : rest).add(goal);
			}

			first.addAll(rest);
			this.ordered = first;
		}

		return this.ordered;
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

		double sharpness = net.minecraft.util.Mth.lerp(this.drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.EFFICIENCY),
			PICK_SHARPNESS_MIN, PICK_SHARPNESS_MAX);
		double total = top.stream().mapToDouble(s -> Math.pow(s.score(), sharpness)).sum();
		double roll = this.drifter.getRandom().nextDouble() * total;
		for (Scored scored : top) {
			roll -= Math.pow(scored.score(), sharpness);
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
		if (text.contains("Done:")) {
			this.failedInARow.remove(goal.id());
		}

		if (text.contains("Failed:")) {
			int inARow = this.failedInARow.merge(goal.id(), 1, Integer::sum);
			long cooldown = Math.min(MAX_FAILED_COOLDOWN_TICKS, (long)FAILED_COOLDOWN_TICKS << Math.min(4, Math.max(0, inARow - FAILURES_BEFORE_REST + 1)));
			this.failedUntil.put(goal.id(), this.drifter.level().getGameTime() + cooldown);
			// Repeated failures wear on it (section 17, Frustrated).
			this.drifter.getPersonality().feel(com.dinglebob.drifter.personality.Mood.FRUSTRATED, 0.18,
				"couldn't " + (this.lastContext == null ? goal.id() : goal.name(this.lastContext)));
			// The commitment ends with the failure: something else gets a turn.
			this.goalStarted = Long.MIN_VALUE / 2;
		}

		this.think(text.replaceFirst("^\\[[^]]*] ", ""));
	}

	/** Goals that should get it somewhere: two minutes on one without moving 2 blocks is a stall. */
	private static final java.util.Set<String> MOVING_GOALS = java.util.Set.of("nether_blaze", "nether_return", "nether_blocks", "explore");
	private static final int STALL_TICKS = 20 * 60;
	private net.minecraft.world.phys.Vec3 stallFrom = net.minecraft.world.phys.Vec3.ZERO;
	private long stallSince;

	/**
	 * A watchdog for stalls nothing else catches (found in testing: on its way to a fortress, a Drifter stood still for
	 * five minutes with no failure at all, and it couldn't be made to happen on demand): it writes what it's doing to the
	 * log with a short trace, and drops that goal for a while so it does something else.
	 */
	private void watchForStall() {
		long now = this.drifter.level().getGameTime();
		if (this.current == null || !MOVING_GOALS.contains(this.current.id()) || this.drifter.position().distanceTo(this.stallFrom) > 2.0) {
			this.stallFrom = this.drifter.position();
			this.stallSince = now;
			return;
		}

		// Standing to fight, shoot or wait (for blazes by a spawner, say) is still, not stuck.
		Task leaf = this.drifter.getTasks().current() == null ? null : this.drifter.getTasks().current().leaf();
		if (leaf instanceof com.dinglebob.drifter.executor.task.ShootTask || leaf instanceof com.dinglebob.drifter.executor.task.AttackTask
			|| leaf instanceof com.dinglebob.drifter.executor.task.WaitTask) {
			this.stallSince = now;
			return;
		}

		if (now - this.stallSince < STALL_TICKS) {
			return;
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] {} stalled for {} s on {}: {} -> {} at {}", this.drifter.getName().getString(), STALL_TICKS / 20,
			this.current.id(), this.activity == null ? "no activity" : this.activity.describe(), leaf == null ? "no task" : leaf.describe(),
			this.drifter.blockPosition().toShortString());
		this.drifter.autoTrace();
		this.think("not getting anywhere: trying something else");
		this.failedUntil.put(this.current.id(), now + FAILED_COOLDOWN_TICKS * 4);
		this.restart("stalled");
		this.stallFrom = this.drifter.position();
		this.stallSince = now;
	}

	/** Drops whatever it was doing and decides afresh next tick (after catch-up moved it on). */
	public void restart(String why) {
		this.stopActivity(why);
		this.current = null;
		this.sinceRescore = Integer.MAX_VALUE / 2;
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

	/** Its whims: what it did when (milestone 13). */
	private final com.dinglebob.drifter.whim.WhimState whims = new com.dinglebob.drifter.whim.WhimState();

	public com.dinglebob.drifter.whim.WhimState whims() {
		return this.whims;
	}

	/** Its horse (tamed to it) and where it last left it (milestone 18, section 10.7). */
	@Nullable
	private java.util.UUID horse;
	@Nullable
	private BlockPos horseAt;

	@Nullable
	public java.util.UUID horse() {
		return this.horse;
	}

	@Nullable
	public BlockPos horseAt() {
		return this.horseAt;
	}

	public void setHorse(@Nullable java.util.UUID horse, @Nullable BlockPos at) {
		this.horse = horse;
		this.horseAt = at == null ? null : at.immutable();
	}

	/** The villagers' trades it has seen (milestone 18, section 10.12). */
	private final com.dinglebob.drifter.trade.TradeBook trades = new com.dinglebob.drifter.trade.TradeBook();

	public com.dinglebob.drifter.trade.TradeBook trades() {
		return this.trades;
	}

	/** One of its goals by id, or null. */
	@Nullable
	public Goals.Goal goal(String id) {
		return this.goals.stream().filter(goal -> goal.id().equals(id)).findFirst().orElse(null);
	}

	public List<String> goalIds() {
		return this.goals.stream().map(Goals.Goal::id).toList();
	}

	/**
	 * Where it keeps to (another mod's call, DrifterApi.setRoam: "one island"): it explores no further than this from the
	 * middle, in this dimension. Null: anywhere.
	 */
	@org.jetbrains.annotations.Nullable
	private BlockPos roamCenter;
	private int roamRadius;
	private String roamDimension = "minecraft:overworld";

	public void setRoam(@org.jetbrains.annotations.Nullable BlockPos center, int radius) {
		this.roamCenter = center == null ? null : center.immutable();
		this.roamRadius = Math.max(16, radius);
		this.roamDimension = this.drifter.level().dimension().location().toString();
	}

	/** The middle of where it keeps to, if it's in that dimension (else null). */
	@org.jetbrains.annotations.Nullable
	public BlockPos roamCenter() {
		return this.roamCenter != null && this.roamDimension.equals(this.drifter.level().dimension().location().toString()) ? this.roamCenter : null;
	}

	public int roamRadius() {
		return this.roamRadius;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		if (this.roamCenter != null) {
			tag.putLong("RoamCenter", this.roamCenter.asLong());
			tag.putInt("RoamRadius", this.roamRadius);
			tag.putString("RoamDimension", this.roamDimension);
		}

		tag.putInt("Version", SAVE_VERSION);
		tag.putBoolean("Enabled", this.enabled);
		net.minecraft.nbt.ListTag jobs = new net.minecraft.nbt.ListTag();
		this.furnaceJobs.forEach(job -> jobs.add(job.save()));
		tag.put("FurnaceJobs", jobs);
		if (this.home != null) {
			tag.put("Home", this.home.save());
		}

		if (this.netherTrip != null) {
			tag.put("NetherTrip", this.netherTrip.save());
		}

		tag.putLong("LastNetherTrip", this.lastNetherTrip);
		tag.put("Whims", this.whims.save());
		tag.put("Trades", this.trades.save());
		if (this.horse != null) {
			tag.putUUID("Horse", this.horse);
			if (this.horseAt != null) {
				tag.putLong("HorseAt", this.horseAt.asLong());
			}
		}
		return tag;
	}

	public void load(CompoundTag tag) {
		// Version 1 is the first format.
		this.enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
		this.home = tag.contains("Home", net.minecraft.nbt.Tag.TAG_COMPOUND) ? Home.load(tag.getCompound("Home")) : null;
		this.netherTrip = tag.contains("NetherTrip", net.minecraft.nbt.Tag.TAG_COMPOUND) ? NetherTrip.load(tag.getCompound("NetherTrip")) : null;
		this.lastNetherTrip = tag.getLong("LastNetherTrip");
		this.whims.load(tag.getCompound("Whims"));
		this.trades.load(tag.getCompound("Trades"));
		this.horse = tag.hasUUID("Horse") ? tag.getUUID("Horse") : null;
		this.horseAt = tag.contains("HorseAt") ? BlockPos.of(tag.getLong("HorseAt")) : null;
		this.roamCenter = tag.contains("RoamCenter") ? BlockPos.of(tag.getLong("RoamCenter")) : null;
		this.roamRadius = tag.getInt("RoamRadius");
		this.roamDimension = tag.contains("RoamDimension") ? tag.getString("RoamDimension") : "minecraft:overworld";
		this.furnaceJobs.clear();
		net.minecraft.nbt.ListTag jobs = tag.getList("FurnaceJobs", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < jobs.size(); i++) {
			FurnaceJob job = FurnaceJob.load(jobs.getCompound(i));
			if (job != null) {
				this.furnaceJobs.add(job);
			}
		}
	}
}
