package com.dinglebob.drifter.social;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.LootChestActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.world.DrifterGroups;
import com.dinglebob.drifter.world.DrifterWars;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Raids (DESIGN.md section 21 "Drifter team wars": "the group gears up, travels together, telegraphs with banners or a
 * war-cry chat line, attacks, loots, and retreats"). The raid itself (who, where, which part it's at) is the group's, in
 * {@link DrifterWars}; each member here does its part. Fighting is the ordinary fight goal: at war, the rival group's
 * members are enemies on sight.
 */
public final class WarGoals {
	private WarGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new JoinRaid(), new Defend());
	}

	/** At war: the leader leads the raiders this close to the target before the charge (then they run in together). */
	private static final double CHARGE = 48.0;
	/** The leader's speech before the march: this many lines, this far apart (ticks). */
	private static final int SPEECH_LINES = 12;
	private static final long SPEECH_GAP = 20L * 5;
	/** Leaders giving a speech now: where, and till when (a line's gap and a bit). */
	private static final java.util.Map<java.util.UUID, Speaking> speaking = new java.util.HashMap<>();

	private record Speaking(net.minecraft.world.level.Level level, Vec3 at, long until) {
	}

	/**
	 * A leader near is giving its war speech: this one keeps its own chatter down till it's done (found in play: "everyone stop
	 * talking for one sec", then two of them went on about cows).
	 */
	public static boolean hushedBySpeech(DrifterEntity drifter, long now) {
		if (speaking.isEmpty()) {
			return false;
		}

		speaking.values().removeIf(s -> now >= s.until());
		double range = Math.max(24.0, com.dinglebob.drifter.DrifterConfig.chatRange());
		for (java.util.Map.Entry<java.util.UUID, Speaking> entry : speaking.entrySet()) {
			Speaking s = entry.getValue();
			if (!entry.getKey().equals(drifter.getUUID()) && s.level() == drifter.level() && drifter.position().distanceTo(s.at()) <= range) {
				return true;
			}
		}

		return false;
	}
	/** On the march, the leader waits a while once most of the band has fallen behind, at most every so often. */
	private static final long WAITS_FOR_STRAGGLERS = 20L * 10;
	private static final long HALTS_EVERY = 20L * 90;
	/** Most of the band this far behind the leader: it halts for them (a column strung out behind it is no cause). */
	private static final double FALLEN_BEHIND = 40.0;
	/** This close to the target the band creeps up on it (sneaking), till the charge (the user, 2026-11-16: sneaking "only ... when they get to the enemy city"). */
	private static final double CREEP = 80.0;
	/** A raider this far behind its leader, out of sight of it, goes on by itself along the roads. */
	private static final double LOST_BAND = 32.0;
	/** Fit to fight: a weapon, and this share of its health. */
	private static final float FIT = 0.6F;

	/** A weapon in its things (a sword or an axe; a bow with arrows). */
	static boolean armed(DrifterEntity drifter) {
		return com.dinglebob.drifter.hands.Combat.bestWeaponSlot(drifter) >= 0 || com.dinglebob.drifter.hands.Archery.ready(drifter);
	}

	/** How close to the leader a member must be to come along (members off elsewhere stay out of it). */
	private static final double MUSTER_RANGE = 96.0;
	/** Close enough to the rival base for the war cry and the attack. */
	private static final double AT_BASE = 24.0;
	/** Chests it goes for round the rival base. */
	private static final double LOOT_RANGE = 20.0;

	/** At most this many go on a raid (the leader too). */
	private static final int MOST_MARCHING = 40;

	/** One of those chosen to go: a share of the group (half to seven in ten), by the raid, the same each time it's asked. */
	static boolean chosen(DrifterWars.Raid raid, DrifterGroups.Group group, java.util.UUID member) {
		if (group.members.size() <= 4) {
			return true;
		}

		long seed = raid.startedAt * 31 + member.getLeastSignificantBits();
		seed = (seed ^ seed >>> 29) * 0xBF58476D1CE4E5B9L;
		double roll = ((seed ^ seed >>> 32) >>> 11) / (double)(1L << 53);
		double share = Math.min(0.5 + Math.floorMod(raid.startedAt, 21) / 100.0, (double)MOST_MARCHING / group.members.size());
		return roll < share;
	}

	/** Its group's raid, if it's one of the raiders (near enough to the leader when it set off, or already on it). */
	@Nullable
	public static DrifterWars.Raid raidFor(DrifterEntity drifter) {
		DrifterGroups.Group group = DrifterGroups.of(drifter.getUUID());
		if (group == null) {
			return null;
		}

		DrifterWars.Raid raid = DrifterWars.raidOf(group.id);
		if (raid == null || !drifter.level().dimension().location().toString().equals(group.baseDimension)) {
			return null;
		}

		if (raid.phase == DrifterWars.Phase.GATHER && !drifter.blockPosition().closerThan(raid.home, MUSTER_RANGE)) {
			return null;
		}

		// (Its fighters: armed and well. The rest stay home.)
		if (raid.phase == DrifterWars.Phase.GATHER && !group.leader.equals(drifter.getUUID())
			&& (!armed(drifter) || drifter.getHealth() < drifter.getMaxHealth() * FIT)) {
			return null;
		}

		// (Not all of a town: half to seven in ten of it, the same ones all the raid long, at most MOST_MARCHING; the user,
		// 2026-11-06: "wars should only use 50% - 70% of the civs". A city of a hundred all came, every one of them.)
		if (!group.leader.equals(drifter.getUUID()) && !chosen(raid, group, drifter.getUUID())) {
			return null;
		}

		return raid;
	}

	public static final class JoinRaid extends Goals.Goal {
		@Override
		public String id() {
			return "raid";
		}

		@Override
		public String name(Goals.Context context) {
			DrifterWars.Raid raid = raidFor(context.drifter());
			return raid == null ? "raid a rival group" : "raid " + DrifterWars.name(raid.defenders);
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			DrifterWars.Raid raid = raidFor(drifter);
			if (raid == null) {
				return 0.0;
			}

			// Badly hurt: it drops out and heads home (the others carry on).
			if (drifter.getHealth() < 8.0F && raid.phase != DrifterWars.Phase.RETREAT) {
				return 0.0;
			}

			return 1.05;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public double risk(Goals.Context context) {
			return 1.0;
		}

		@Override
		public boolean urgent(Goals.Context context) {
			return true;
		}

		@Override
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterWars.Raid raid = raidFor(context.drifter());
			return raid == null ? null : new RaidActivity(context.drifter(), raid);
		}
	}

	/** One member's part in a raid, phase by phase; the leader moves the raid on. */
	public static final class RaidActivity extends Activity {
		private final DrifterWars.Raid raid;
		private final boolean leader;
		private int failures;
		private boolean called;
		private long waitingSince = -1;
		private final java.util.Set<BlockPos> tried = new java.util.HashSet<>();
		private final java.util.Map<BlockPos, Integer> walks = new java.util.HashMap<>();
		private long nextLook;
		/** The raid's part when its step began (a new part: a new step at once, not at the end of a long march). */
		@Nullable
		private DrifterWars.Phase stepPhase;
		/** The leader halts for the band at most this often; the next time it may. */
		private long nextHalt;
		/** The leader's march creeping (the last stretch to the target), or a member's pace keeping up; for {@link #rethink}. */
		private boolean marchCreeping;
		@Nullable
		private NavigateTask.Pace keepingUp;

		public RaidActivity(DrifterEntity drifter, DrifterWars.Raid raid) {
			super(drifter);
			this.raid = raid;
			DrifterGroups.Group group = DrifterGroups.of(drifter.getUUID());
			this.leader = group != null && group.leader.equals(drifter.getUUID());
		}

		@Override
		public String describe() {
			String rival = DrifterWars.name(this.raid.defenders);
			return switch (this.raid.phase) {
				case GATHER -> this.leader ? "rally the group to raid " + rival : "join the raid on " + rival;
				case TRAVEL -> "on the way to raid " + rival;
				case ATTACK -> "raid " + rival + "'s base";
				case RETREAT -> "head home from the raid on " + rival;
			};
		}

		@Override
		protected int timeoutTicks() {
			// (The raid's own parts end it, each in its time: a long march gave up every four minutes on the way, the first test.)
			return 20 * 60 * 20;
		}

		@Override
		protected void stepDone(Task step, Status status) {
			if (status == Status.FAILURE) {
				this.failures++;
			}

			if (step instanceof LootChestActivity && status == Status.SUCCESS) {
				DrifterWars.looted(this.raid, this.now());
			}
		}

		@Override
		protected Status next() {
			if (DrifterWars.raidOf(this.raid.attackers) != this.raid) {
				return Status.SUCCESS;
			}

			if (this.failures > 6) {
				return this.fail("can't get anywhere on the raid");
			}

			this.stepPhase = this.raid.phase;
			return switch (this.raid.phase) {
				case GATHER -> this.gather();
				case TRAVEL -> this.travel();
				case ATTACK -> this.attack();
				case RETREAT -> this.retreat();
			};
		}

		/** Its speech so far: questions and cries said, and what its last line was. */
		private int asks;
		private int cries;
		private WarCrowd.Kind lastKind = WarCrowd.Kind.OPEN;

		/** A line between the first and last: a question (at most 3, never two running), a cry (at most 3), else plain. */
		private String middleKey() {
			double roll = this.drifter.getRandom().nextDouble();
			if (roll < 0.22 && this.asks < 3 && this.lastKind != WarCrowd.Kind.ASK && Lines.has("war.speech_ask")) {
				return "war.speech_ask";
			}

			if (roll < 0.42 && this.cries < 3 && this.lastKind != WarCrowd.Kind.CRY && Lines.has("war.speech_cry")) {
				return "war.speech_cry";
			}

			return "war.speech";
		}

		private Status gather() {
			DrifterEntity leader = this.leaderEntity();
			if (this.leader || leader == null) {
				// To the muster point first (its town's plaza), and the call to arms.
				if (this.leader && !this.drifter.blockPosition().closerThan(this.raid.home, 6.0)) {
					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.raid.home), 3.0, "go to where the raid gathers"));
				}

				if (this.leader && !this.called) {
					this.called = true;
					ChatLines.speak(this.drifter, Lines.pick(this.drifter, "war.rally", "enemy", DrifterWars.name(this.raid.defenders)));
				}

				// Everyone close: the leader's speech (the user, 2026-11-06: "after they group up the leader should give a very long
				// speech for morale, and then they travel"), a line every few seconds; then off they go (or the leader stops
				// waiting: see DrifterWars).
				if (this.leader && (this.raid.speech > 0 || this.musterReady())) {
					if (this.raid.speech == 0) {
						this.raid.enter(DrifterWars.Phase.GATHER, this.now());
						this.think("everyone's here: a few words before we go");
					}

					if (this.raid.speech < SPEECH_LINES) {
						if (this.now() - this.raid.lastSpoke >= SPEECH_GAP) {
							this.raid.lastSpoke = this.now();
							this.raid.speech++;
							// (Its kind: the opening, the close; between, plain words mostly, now and then a question for them to answer
							// or a cry for them to take up: the crowd answers those, WarCrowd.)
							String key = this.raid.speech == 1 ? "war.speech_open" : this.raid.speech == SPEECH_LINES ? "war.speech_close" : this.middleKey();
							String line = null;
							for (int tries = 0; tries < 6 && (line == null || this.raid.said.contains(line)); tries++) {
								line = Lines.pick(this.drifter, key, "enemy", DrifterWars.name(this.raid.defenders), "town", DrifterWars.name(this.raid.attackers));
							}

							if (line != null) {
								this.raid.said.add(line);
								ChatLines.toEveryone(this.drifter);
								ChatLines.speak(this.drifter, line);
								WarCrowd.Kind kind = this.raid.speech == 1 ? WarCrowd.Kind.OPEN : this.raid.speech == SPEECH_LINES ? WarCrowd.Kind.CLOSE
									: key.equals("war.speech_ask") ? WarCrowd.Kind.ASK : key.equals("war.speech_cry") ? WarCrowd.Kind.CRY : WarCrowd.kindOf(line);
								if (kind == WarCrowd.Kind.ASK) {
									this.asks++;
								} else if (kind == WarCrowd.Kind.CRY) {
									this.cries++;
								}

								this.lastKind = kind;
								// (The crowd's moment: a nervous one steadied, a joker shushed, before the next line.)
								this.raid.lastSpoke += WarCrowd.heard(this.drifter, this.raid, line, kind, this.now());
							}

							// (Till its next line, and the last a little while after.)
							if (this.raid.speech < SPEECH_LINES) {
								speaking.put(this.drifter.getUUID(), new Speaking(this.drifter.level(), this.drifter.position(), this.now() + SPEECH_GAP + 40));
							} else {
								speaking.put(this.drifter.getUUID(), new Speaking(this.drifter.level(), this.drifter.position(), this.now() + 40));
							}
						}

						return this.begin(new LookAroundTask(this.drifter));
					}

					this.raid.enter(DrifterWars.Phase.TRAVEL, this.now());
					this.heartened();
					this.think("the speech is done: off to raid " + DrifterWars.name(this.raid.defenders));
					return Status.RUNNING;
				}

				return this.begin(new LookAroundTask(this.drifter));
			}

			// (To the leader where it's gathering them; it's on its way there. Listening, once it's speaking: facing it.)
			// (Round the leader wherever it is about the town: stuck getting to the spot itself, the band waited there without it
			// and the speech never came; a test of it.)
			Vec3 to = leader.blockPosition().closerThan(this.raid.home, 40.0) ? leader.position() : Vec3.atBottomCenterOf(this.raid.home);
			if (this.drifter.position().distanceTo(to) > 5.0) {
				return this.begin(new NavigateTask(this.drifter, to, 3.0, "go to where the raid gathers"));
			}

			if (this.raid.speech > 0) {
				return this.begin(new com.dinglebob.drifter.executor.task.LookAtTask(this.drifter, leader.getEyePosition(), 60));
			}

			return this.begin(new LookAroundTask(this.drifter));
		}

		/** Its raiders heartened by the speech (confident for the march and the fight). */
		private void heartened() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			if (group == null || this.drifter.getServer() == null) {
				return;
			}

			for (java.util.UUID id : group.members) {
				DrifterEntity member = DrifterWars.loaded(this.drifter.getServer(), id);
				if (member != null && WarGoals.raidFor(member) == this.raid && member.distanceTo(this.drifter) < 24.0) {
					member.getPersonality().feel(com.dinglebob.drifter.personality.Mood.CONFIDENT, 0.6, "heard the leader's speech before the raid");
				}
			}
		}

		/** Every member that came along is within a few blocks of the leader. */
		private boolean musterReady() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			if (group == null || this.drifter.getServer() == null) {
				return true;
			}

			int raiders = 0;
			int here = 0;
			for (java.util.UUID id : group.members) {
				DrifterEntity member = DrifterWars.loaded(this.drifter.getServer(), id);
				if (member != null && member != this.drifter && WarGoals.raidFor(member) == this.raid) {
					raiders++;
					if (member.distanceTo(this.drifter) <= 10.0) {
						here++;
					}
				}
			}

			// (Everyone; or, after a while, most of them: a few always have something on, and the speech waited for them till the
			// gathering ran out, never given; the first test of it.)
			return here >= raiders || this.now() - this.raid.phaseSince > DrifterWars.GATHER_TICKS / 2 && here * 3 >= raiders * 2
				|| this.now() - this.raid.phaseSince > DrifterWars.GATHER_TICKS * 3 / 4 && here * 2 >= raiders;
		}

		private Status travel() {
			// Together: the leader leads (waiting a little for stragglers), the rest keep with it; near enough, the charge:
			// everyone runs in at once, shouting (the user, 2026-10-29: "they can run in and it can be on sight").
			DrifterEntity leader = this.leaderEntity();
			if (this.leader || leader == null) {
				if (this.drifter.blockPosition().closerThan(this.raid.target, this.leader ? CHARGE : AT_BASE)) {
					if (!this.raid.warCry) {
						this.raid.warCry = true;
						ChatLines.speak(this.drifter, Lines.pick(this.drifter, "war.charge", "enemy", DrifterWars.name(this.raid.defenders)));
						this.think("at " + DrifterWars.name(this.raid.defenders) + ": charge!");
					}

					this.raid.enter(DrifterWars.Phase.ATTACK, this.now());
					return Status.RUNNING;
				}

				// Most of the band fallen behind: a halt for them, now and then (not for every one straggling: it stopped at every
				// stretch of the way in its own town for whoever was still coming from the fields; the user, 2026-11-16: "i dont
				// like the little pauses they do").
				if (this.leader && this.waitingSince < 0 && this.now() >= this.nextHalt && this.bandBehind()) {
					this.waitingSince = this.now();
					this.think("waiting for the others to catch up");
				}

				if (this.waitingSince >= 0) {
					if (this.now() - this.waitingSince < WAITS_FOR_STRAGGLERS && this.bandBehind()) {
						return this.begin(new LookAroundTask(this.drifter));
					}

					this.waitingSince = -1;
					this.nextHalt = this.now() + HALTS_EVERY;
				}

				// (The leader marches, at a walk, the whole way at once along the roads it knows (its town's people have gone
				// between the towns for years): no stop between the stretches of the way. The last stretch, near the town it's
				// raiding, it creeps up on it, the band with it; then the charge.)
				this.marchCreeping = this.creeping(this.drifter);
				String what = "on the way to raid " + DrifterWars.name(this.raid.defenders);
				return this.begin(new com.dinglebob.drifter.nav.March(this.drifter, this.raid.target, this.marchCreeping ? CHARGE - 2.0 : CREEP - 2.0,
					this.marchCreeping ? "creep up on " + DrifterWars.name(this.raid.defenders) : what, this.marchCreeping ? NavigateTask.Pace.SNEAK
						: NavigateTask.Pace.STROLL).onFoot());
			}

			// Lost sight of its leader, well behind: on along the roads to where they're going, at a run, till it has it in sight
			// again (it knows the way as well as its leader does). (Making for where it last saw it, it stood there for good once
			// it got there, waiting for a leader it couldn't see to move on; half the band 100 blocks back, a test of it.)
			if (this.lostTheBand(leader)) {
				this.keepingUp = null;
				if (this.drifter.blockPosition().closerThan(this.raid.target, CREEP)) {
					return this.begin(new LookAroundTask(this.drifter));
				}

				return this.begin(new com.dinglebob.drifter.nav.March(this.drifter, this.raid.target, CREEP - 2.0, "catch up with the band",
					NavigateTask.Pace.TRAVEL).onFoot());
			}

			// The band walks with its leader, at its back (each a few blocks further back than the next: a column, not a crowd in
			// its way), no stop and stare each time it's caught up; at a run to catch up, creeping when it creeps.
			this.keepingUp = this.keepUpPace(leader, null);
			double behind = 3.0 + Math.floorMod(this.drifter.getUUID().getLeastSignificantBits(), 9L);
			return this.begin(NavigateTask.followBehind(this.drifter, leader, 3.0, this.keepingUp, behind));
		}

		/** Well behind its leader and not seeing it: it goes on by itself (and back with the leader once it has it in sight). */
		private boolean lostTheBand(DrifterEntity leader) {
			var seen = this.drifter.getSenses().awarenessOf(leader);
			return this.drifter.distanceTo(leader) > LOST_BAND && (seen == null || !seen.visibleNow);
		}

		/** Near enough the target (but not yet for the charge) to creep up on it, by where that one is. */
		private boolean creeping(DrifterEntity who) {
			return who.blockPosition().closerThan(this.raid.target, CREEP);
		}

		/** How a member keeps up with its leader: at a run when it's fallen behind (a bit nearer once running), else at its pace. */
		private NavigateTask.Pace keepUpPace(DrifterEntity leader, @Nullable NavigateTask.Pace now) {
			double far = this.drifter.distanceTo(leader);
			if (far > (now == NavigateTask.Pace.TRAVEL ? 14.0 : 22.0)) {
				return NavigateTask.Pace.TRAVEL;
			}

			return this.creeping(leader) ? NavigateTask.Pace.SNEAK : NavigateTask.Pace.STROLL;
		}

		@Override
		protected boolean rethink() {
			// A new part of the raid (or the raid's over): straight on to it, not at the end of the march.
			if (DrifterWars.raidOf(this.raid.attackers) != this.raid || this.raid.phase != this.stepPhase) {
				return true;
			}

			if (this.raid.phase != DrifterWars.Phase.TRAVEL) {
				return false;
			}

			if (this.leader) {
				return this.child() instanceof com.dinglebob.drifter.nav.March && (this.creeping(this.drifter) != this.marchCreeping
					|| this.now() >= this.nextHalt && this.now() % 20 == 0 && this.bandBehind());
			}

			DrifterEntity leader = this.leaderEntity();
			if (leader == null) {
				return true;
			}

			// (On by itself and the leader in sight and near again: back with it. Following and lost it: on by itself.)
			if (this.keepingUp == null) {
				var seen = this.drifter.getSenses().awarenessOf(leader);
				return this.now() % 10 == 0 && seen != null && seen.visibleNow && this.drifter.distanceTo(leader) < LOST_BAND;
			}

			return this.child() instanceof NavigateTask && (this.keepUpPace(leader, this.keepingUp) != this.keepingUp
				|| this.now() % 10 == 0 && this.lostTheBand(leader));
		}

		/** Most of the raiders with it fallen well behind the leader. */
		private boolean bandBehind() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			if (group == null || this.drifter.getServer() == null) {
				return false;
			}

			int band = 0;
			int behind = 0;
			for (java.util.UUID id : group.members) {
				DrifterEntity member = DrifterWars.loaded(this.drifter.getServer(), id);
				if (member != null && member != this.drifter && WarGoals.raidFor(member) == this.raid && member.distanceTo(this.drifter) < 160.0) {
					band++;
					if (member.distanceTo(this.drifter) > FALLEN_BEHIND) {
						behind++;
					}
				}
			}

			return behind * 2 > band;
		}

		/** Most of the raiders about the target are worn down (or there's hardly anyone left): time to go. */
		private boolean wornDown() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			if (group == null || this.drifter.getServer() == null) {
				return false;
			}

			int here = 0;
			int fit = 0;
			for (java.util.UUID id : group.members) {
				DrifterEntity member = DrifterWars.loaded(this.drifter.getServer(), id);
				if (member != null && member.isAlive() && member.blockPosition().closerThan(this.raid.target, CHARGE)) {
					here++;
					if (member.getHealth() >= member.getMaxHealth() * com.dinglebob.drifter.brain.Threats.WAR_FALL_BACK) {
						fit++;
					}
				}
			}

			return here == 0 || fit * 2 < here;
		}

		private Status attack() {
			// Worn down: the leader calls it (the user, 2026-10-29: "if everybody gets low they retreat").
			if (this.leader && this.now() - this.raid.phaseSince > 20 * 10 && this.wornDown()) {
				ChatLines.speak(this.drifter, Lines.pick(this.drifter, "war.fall_back", "enemy", DrifterWars.name(this.raid.defenders)));
				this.raid.enter(DrifterWars.Phase.RETREAT, this.now());
				this.think("we're done in: falling back from " + DrifterWars.name(this.raid.defenders));
				return Status.RUNNING;
			}

			// An enemy's horse tied up here, and none of its own: it takes it (horse culture: "only war captures").
			Task capture = com.dinglebob.drifter.skills.StableGoal.capture(this.drifter);
			if (capture != null) {
				return this.begin(capture);
			}

			BlockPos chest = this.chestToLoot();
			if (chest != null) {
				return this.begin(new LootChestActivity(this.drifter, chest));
			}

			// Wrecking, as its leader would have it: doors and fields, and (a ruthless one's raid) fire. Never by a bed.
			Status wreck = this.wreck();
			if (wreck != null) {
				return wreck;
			}

			// Nothing left to take that it knows of: a look round the base for more; the leader calls it once it's had a look.
			if (this.drifter.blockPosition().closerThan(this.raid.target, 8.0)) {
				if (this.leader && this.now() - this.raid.phaseSince > 20 * 30) {
					this.raid.enter(DrifterWars.Phase.RETREAT, this.now());
					this.think("done here: back home (" + this.raid.looted + " chests raided)");
					return Status.RUNNING;
				}

				return this.begin(new LookAroundTask(this.drifter));
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.raid.target), 4.0, "into the rival base"));
		}

		/** Most its side breaks, and fires it lights, on one raid (all the raiders together). */
		private static final int MOST_BREAKS = 10;
		private static final int MOST_FIRES = 4;
		/** Beds are let be (the user: "nobody goes near beds"): nothing broken right by one, no fire this close to one. */
		private static final double BY_BED = 2.5;
		private static final double FIRE_BY_BED = 6.0;
		/** No fire this close to what it came for (the loot's there), nor any wrecking till it's had this long at it. */
		private static final double FIRE_BY_TARGET = 8.0;
		private static final int LOOT_FIRST = 20 * 20;
		/** How far round what it came for it wrecks. */
		private static final int WRECKS = 32;
		/** How often it looks again when there was nothing to wreck. */
		private static final int LOOKS_EVERY = 40;

		/** Its next bit of wrecking, by the raid's grief, or null (nothing more, or nothing to). */
		@Nullable
		private Status wreck() {
			long now = this.drifter.level().getGameTime();
			if (this.raid.grief <= 0 || this.raid.broken >= MOST_BREAKS && (this.raid.grief < 2 || this.raid.fires >= MOST_FIRES) || now < this.nextLook
				|| now - this.raid.phaseSince < LOOT_FIRST) {
				return null;
			}

			// What it has seen of the place round what it came for, the beds among it.
			net.minecraft.world.level.Level level = this.drifter.level();
			java.util.List<BlockPos> beds = new java.util.ArrayList<>();
			java.util.List<BlockPos> breakable = new java.util.ArrayList<>();
			java.util.List<BlockPos> wood = new java.util.ArrayList<>();
			var terrain = this.drifter.getSenses().terrain();
			BlockPos spawn = level.getSharedSpawnPos();
			int protection = this.drifter.getServer() == null ? 0 : this.drifter.getServer().getSpawnProtectionRadius();
			for (BlockPos pos : BlockPos.betweenClosed(this.raid.target.offset(-WRECKS, -4, -WRECKS), this.raid.target.offset(WRECKS, 8, WRECKS))) {
				var state = level.getBlockState(pos);
				var block = state.getBlock();
				if (block instanceof net.minecraft.world.level.block.BedBlock) {
					beds.add(pos.immutable());
				} else if (this.tried.contains(pos) || !terrain.isKnown(pos) || state.getDestroySpeed(level, pos) < 0.0F
					|| com.dinglebob.drifter.brain.Home.isStorageBlock(state)
					|| !com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(level, pos) && com.dinglebob.drifter.brain.activity.BuildPlanActivity.natural(state)
					|| level.dimension() == net.minecraft.world.level.Level.OVERWORLD && Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= protection) {
					// (Only what it has seen, and only what someone built: not trees, nor what spawn protection keeps.)
					continue;
				} else if (block instanceof net.minecraft.world.level.block.DoorBlock && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
					== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER || block instanceof net.minecraft.world.level.block.FenceGateBlock
					|| block instanceof net.minecraft.world.level.block.CropBlock) {
					breakable.add(pos.immutable());
				} else if (state.ignitedByLava() && !state.canBeReplaced() && !(block instanceof net.minecraft.world.level.block.LeavesBlock)
					&& Math.abs(pos.getY() - this.raid.target.getY()) <= 4 && fireFace(level, pos) != null) {
					wood.add(pos.immutable());
				}
			}

			breakable.removeIf(pos -> beds.stream().anyMatch(bed -> bed.closerThan(pos, BY_BED)));
			wood.removeIf(pos -> pos.closerThan(this.raid.target, FIRE_BY_TARGET) || beds.stream().anyMatch(bed -> bed.closerThan(pos, FIRE_BY_BED)));
			String place = DrifterWars.name(this.raid.defenders) + "'s place";
			if (this.raid.broken < MOST_BREAKS && !breakable.isEmpty()) {
				BlockPos pos = breakable.stream().min(java.util.Comparator.comparingDouble(b -> b.distSqr(this.drifter.blockPosition()))).orElseThrow();
				return this.at(pos, () -> {
					this.raid.broken++;
					this.raid.wrecked = true;
					return this.dig(pos);
				}, "wreck " + place);
			}

			if (this.raid.grief >= 2 && this.raid.fires < MOST_FIRES && !wood.isEmpty()
				&& this.drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)) > 0) {
				BlockPos pos = wood.stream().min(java.util.Comparator.comparingDouble(b -> b.distSqr(this.drifter.blockPosition()))).orElseThrow();
				return this.at(pos, () -> {
					this.raid.fires++;
					this.raid.wrecked = true;
					this.think("setting fire to " + place);
					// (The face it lights: the top if it's below its eyes, else a side with air in front, the side toward it.)
					net.minecraft.core.Direction face = level.getBlockState(pos.above()).isAir() && pos.getY() + 1.0 < this.drifter.getEyeY() ? net.minecraft.core.Direction.UP
						: net.minecraft.core.Direction.Plane.HORIZONTAL.stream().filter(d -> level.getBlockState(pos.relative(d)).isAir())
							.min(java.util.Comparator.comparingDouble(d -> Vec3.atCenterOf(pos.relative(d)).distanceToSqr(this.drifter.getEyePosition())))
							.orElse(net.minecraft.core.Direction.UP);
					return new com.dinglebob.drifter.executor.task.SequenceTask(this.drifter, "set fire to it", List.of(
						new com.dinglebob.drifter.executor.task.SelectItemTask(this.drifter, stack -> stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL),
							"flint and steel"),
						new com.dinglebob.drifter.executor.task.UseOnBlockTask(this.drifter, pos, face, "light it")));
				}, "set fire to " + place);
			}

			this.nextLook = now + LOOKS_EVERY;
			return null;
		}

		/** A face of this block with air in front of it, a fire's spot (the top, or a side), or null. */
		@Nullable
		private static net.minecraft.core.Direction fireFace(net.minecraft.world.level.Level level, BlockPos pos) {
			for (net.minecraft.core.Direction face : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.UP, net.minecraft.core.Direction.NORTH,
				net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
				if (level.getBlockState(pos.relative(face)).isAir()) {
					return face;
				}
			}

			return null;
		}

		/** The work once it's in reach of it (counted then), else a walk over first; a spot it can't get to is let be. */
		private Status at(BlockPos pos, java.util.function.Supplier<Task> work, String what) {
			if (com.dinglebob.drifter.hands.Reach.canReach(this.drifter, pos)) {
				this.tried.add(pos);
				return this.begin(work.get());
			}

			BlockPos spot = com.dinglebob.drifter.brain.KnownBlocks.standingSpotFor(this.drifter, pos, java.util.Set.of(pos));
			if (spot == null || this.walks.merge(pos, 1, Integer::sum) > 2) {
				this.tried.add(pos);
				return Status.RUNNING;
			}

			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to " + what));
		}

		/** A chest round the rival base it has seen and not looted yet. */
		@Nullable
		private BlockPos chestToLoot() {
			if (!com.dinglebob.drifter.DrifterConfig.raiding()) {
				return null;
			}

			var brain = this.drifter.getDrifterBrain();
			MemoryEntry entry = this.drifter.getMemory().nearest(MemoryKind.STORAGE, this.drifter.level().dimension().location(),
				Vec3.atCenterOf(this.raid.target), LOOT_RANGE, e -> !brain.looted.contains(e.blockPos())
					&& !com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(this.drifter.level(), e.blockPos()));
			return entry == null ? null : entry.blockPos();
		}

		private Status retreat() {
			if (this.drifter.blockPosition().closerThan(this.raid.home, 12.0)) {
				if (this.leader) {
					DrifterWars.endRaid(this.drifter.getServer(), this.raid);
					this.think("back from the raid on " + DrifterWars.name(this.raid.defenders));
				}

				return Status.SUCCESS;
			}

			// (Home the whole way at once, along the roads.)
			return this.begin(new com.dinglebob.drifter.nav.March(this.drifter, this.raid.home, 11.0, "head home from the raid", NavigateTask.Pace.TRAVEL));
		}

		@Nullable
		private DrifterEntity leaderEntity() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			return group == null || this.drifter.getServer() == null ? null : DrifterWars.loaded(this.drifter.getServer(), group.leader);
		}
	}

	/**
	 * Its group raided (at war): its fit fighters go to stand by what's being raided and fight whoever comes, the first to
	 * see them raising the alarm; the rest keep out of it (the user, 2026-10-29: "few would fight and others would just
	 * hide; should be more fighting").
	 */
	public static final class Defend extends Goals.Goal {
		/** Raiders this near what they came for: its fighters turn out. */
		private static final double COMING = 96.0;
		/** Where it stands, round what's raided. */
		private static final double STANDS = 6.0;
		/** One alarm a raid (by the first to see them). */
		private static final java.util.Set<DrifterWars.Raid> alarmed = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

		@Override
		public String id() {
			return "defend";
		}

		@Override
		public String name(Goals.Context context) {
			DrifterWars.Raid raid = raidOn(context.drifter());
			return raid == null ? "defend its town" : "fight off " + DrifterWars.name(raid.attackers);
		}

		/** A raid on its group under way and near (its raiders on the way in or there). */
		@Nullable
		private static DrifterWars.Raid raidOn(DrifterEntity drifter) {
			DrifterGroups.Group group = DrifterGroups.of(drifter.getUUID());
			DrifterWars.Raid raid = group == null ? null : DrifterWars.raidOn(group.id);
			if (raid == null || raid.phase == DrifterWars.Phase.GATHER || raid.phase == DrifterWars.Phase.RETREAT
				|| !drifter.blockPosition().closerThan(raid.target, COMING)) {
				return null;
			}

			// (It knows they're coming once it's seen one of them, or the alarm's gone up.)
			return alarmed.contains(raid) || sees(drifter, raid) ? raid : null;
		}

		private static boolean sees(DrifterEntity drifter, DrifterWars.Raid raid) {
			for (var awareness : drifter.getSenses().tracked()) {
				if (awareness.visibleNow && awareness.identified && awareness.entity instanceof DrifterEntity other && other.isAlive()) {
					DrifterGroups.Group theirs = DrifterGroups.of(other.getUUID());
					if (theirs != null && theirs.id.equals(raid.attackers)) {
						return true;
					}
				}
			}

			return false;
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			DrifterWars.Raid raid = raidOn(drifter);
			if (raid == null || !armed(drifter) || drifter.getHealth() < drifter.getMaxHealth() * com.dinglebob.drifter.brain.Threats.WAR_FALL_BACK) {
				return 0.0;
			}

			// The alarm: the first to see them.
			if (!alarmed.contains(raid) && sees(drifter, raid)) {
				alarmed.add(raid);
				ChatLines.speak(drifter, Lines.pick(drifter, "war.alarm", "enemy", DrifterWars.name(raid.attackers)));
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), DrifterWars.name(raid.attackers) + " are here: to arms");
			}

			return drifter.blockPosition().closerThan(raid.target, STANDS) ? 0.0 : 1.02;
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
		public Activity start(Goals.Context context) {
			DrifterWars.Raid raid = raidOn(context.drifter());
			if (raid == null) {
				return null;
			}

			return new Activity(context.drifter()) {
				@Override
				public String describe() {
					return "go to fight off " + DrifterWars.name(raid.attackers);
				}

				@Override
				protected int timeoutTicks() {
					return 20 * 60;
				}

				@Override
				protected Status next() {
					if (this.drifter.blockPosition().closerThan(raid.target, STANDS)) {
						return Status.SUCCESS;
					}

					return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(raid.target), STANDS - 1.0, "run to fight off "
						+ DrifterWars.name(raid.attackers)).urgent());
				}
			};
		}
	}
}
