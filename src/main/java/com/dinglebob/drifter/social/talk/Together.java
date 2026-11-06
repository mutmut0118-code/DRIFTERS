package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.Relationship;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What two Drifters agreed in a talk, done (the user, 2026-11-05: "ofc the talking leading to actions"). A topic's
 * {@code effects} ("plan_mine", "plan_hunt", "plan_show_home", "plan_tag_along", "plan_share_food", "plan_visit") make a
 * plan when the other said yes (the topic marked {@code plan_yes}); then:
 * <ul>
 * <li>show home: the one who asked walks home, the other follows, and once there it knows where they live;</li>
 * <li>share food: it walks up to the hungry one and tosses them some of its spare food;</li>
 * <li>tag along: the one who asked follows the other a few minutes, as they go about their day;</li>
 * <li>mine: the next time either sets off on a mining trip (that day) the other comes too ({@code GroupGoals}), and if it's
 * day now it tags along till then;</li>
 * <li>hunt: that night both go out after mobs (even one that'd otherwise stay in: there's two of them), prowling together
 * ({@code Schedule.chooseNightStyle}, {@code Goals.NightHunt});</li>
 * <li>visit: later that day it goes over to the other's place ({@code NeighbourGoals.Visit}), told where it is.</li>
 * </ul>
 * Plans live only while the game runs (minutes to a day): a restart forgets them, as people forget what they said.
 */
public final class Together extends Goals.Goal {
	public enum Kind {
		SHOW_HOME("show_home"), SHARE_FOOD("share_food"), TAG_ALONG("tag_along"), MINE("mine"), HUNT("hunt"), VISIT("visit");

		public final String key;

		Kind(String key) {
			this.key = key;
		}
	}

	/** A plan: who leads it (shows, gives, is followed; the host of a visit) and the other. */
	public static final class Plan {
		public final Kind kind;
		public final UUID lead;
		public final UUID other;
		public final String leadName;
		public final String otherName;
		public final long from;
		public final long until;
		/** When it was done (it lingers a moment so the other sees it's over), or 0. */
		long doneAt;
		/** Said its bit at the start (once a plan, however often its part starts again). */
		boolean leadSaid;
		boolean otherSaid;
		/** Since when the follower's lost sight of the other, or 0. */
		long lostAt;

		Plan(Kind kind, DrifterEntity lead, DrifterEntity other, long from, long until) {
			this.kind = kind;
			this.lead = lead.getUUID();
			this.other = other.getUUID();
			this.leadName = lead.getName().getString();
			this.otherName = other.getName().getString();
			this.from = from;
			this.until = until;
		}

		boolean has(DrifterEntity drifter) {
			return this.lead.equals(drifter.getUUID()) || this.other.equals(drifter.getUUID());
		}

		boolean leads(DrifterEntity drifter) {
			return this.lead.equals(drifter.getUUID());
		}

		public String describe() {
			return this.kind.key + ": " + this.leadName + " with " + this.otherName + (this.doneAt > 0 ? " (done)" : "");
		}
	}

	private static final List<Plan> plans = new ArrayList<>();
	/** How long a plan made now waits to be done before it's forgotten. */
	private static final long NOW = 20L * 60 * 3;
	/** How long a tag-along lasts. */
	private static final long TAG_ALONG = 20L * 60 * 3;
	/** A plan for later that day: up to a day. */
	private static final long LATER = 24000L;
	/** Further than this apart, a plan for now is off (they've gone their ways). */
	private static final double APART = 64.0;
	/** Staying at the home shown, once both are there. */
	private static final int SHOW_STAY = 20 * 30;

	// ---------------------------------------------------------------------------------------------
	// Plans
	// ---------------------------------------------------------------------------------------------

	/** The talk's topic ended with a yes to this: a plan for the two. */
	static void agree(Kind kind, DrifterEntity lead, DrifterEntity other) {
		long now = lead.level().getGameTime();
		plans.removeIf(plan -> plan.kind == kind && plan.has(lead) && plan.has(other));
		switch (kind) {
			case MINE -> {
				plans.add(new Plan(Kind.MINE, lead, other, now, now + LATER));
				// (Daytime: it sticks with them till they set off.)
				if (!Schedule.isNight(other) && Schedule.believedTime(other) < Schedule.shelterThinking(other)) {
					plans.add(new Plan(Kind.TAG_ALONG, lead, other, now, now + TAG_ALONG));
				}
			}
			case HUNT -> plans.add(new Plan(Kind.HUNT, lead, other, now, now + untilMorning(lead)));
			case VISIT -> {
				plans.add(new Plan(Kind.VISIT, lead, other, now + 20L * 60, now + LATER));
				// (Told where it is: "it's just past the river".)
				Home home = lead.getDrifterBrain().home();
				Relationship r = other.getBonds().get(lead.getUUID());
				if (home != null && r != null && r.knownBase == null) {
					r.knownBase = home.center;
				}
			}
			case TAG_ALONG -> plans.add(new Plan(kind, lead, other, now, now + TAG_ALONG));
			default -> plans.add(new Plan(kind, lead, other, now, now + NOW));
		}

		String what = switch (kind) {
			case SHOW_HOME -> "to show " + nameOf(other) + " my place";
			case SHARE_FOOD -> "to give " + nameOf(other) + " some food";
			case TAG_ALONG -> "that " + nameOf(other) + " tags along with me";
			case MINE -> "to go mining with " + nameOf(other);
			case HUNT -> "to hunt with " + nameOf(other) + " tonight";
			case VISIT -> "that " + nameOf(other) + " comes by my place";
		};
		String theirs = switch (kind) {
			case SHOW_HOME -> "to go see " + nameOf(lead) + "'s place";
			case SHARE_FOOD -> nameOf(lead) + " is giving me some food";
			case TAG_ALONG -> "to tag along with " + nameOf(lead);
			case MINE -> "to go mining with " + nameOf(lead);
			case HUNT -> "to hunt with " + nameOf(lead) + " tonight";
			case VISIT -> "to drop by " + nameOf(lead) + "'s place later";
		};
		lead.getMemory().thoughts().add(now, "agreed " + what);
		other.getMemory().thoughts().add(now, "agreed " + theirs);
	}

	private static String nameOf(DrifterEntity drifter) {
		return com.dinglebob.drifter.identity.Nicknames.of(drifter);
	}

	/** Ticks till this morning's end of night (as it believes the time). */
	private static long untilMorning(DrifterEntity drifter) {
		long time = Schedule.believedTime(drifter);
		return Math.floorMod(Schedule.NIGHT_END - time, 24000L);
	}

	/** Its plan of this kind due now (not done), or null. */
	@Nullable
	static Plan due(DrifterEntity drifter, Kind kind) {
		long now = drifter.level().getGameTime();
		for (Iterator<Plan> it = plans.iterator(); it.hasNext();) {
			Plan plan = it.next();
			if (now > plan.until || plan.doneAt > 0 && now - plan.doneAt > 20 * 10) {
				it.remove();
				continue;
			}

			if (plan.kind == kind && plan.has(drifter) && plan.from <= now && plan.doneAt == 0) {
				return plan;
			}
		}

		return null;
	}

	/** Its plan of this kind (done or not) still about, or null. */
	@Nullable
	private static Plan any(DrifterEntity drifter, Kind kind) {
		long now = drifter.level().getGameTime();
		for (Plan plan : plans) {
			if (plan.kind == kind && plan.has(drifter) && now <= plan.until) {
				return plan;
			}
		}

		return null;
	}

	/** Done (it lingers a moment, so the other knows). */
	static void done(Plan plan, DrifterEntity drifter) {
		if (plan.doneAt == 0) {
			plan.doneAt = Math.max(1L, drifter.level().getGameTime());
		}
	}

	/** The other one in it, if loaded and alive. */
	@Nullable
	static DrifterEntity partner(Plan plan, DrifterEntity drifter) {
		UUID id = plan.leads(drifter) ? plan.other : plan.lead;
		return drifter.level() instanceof ServerLevel level && level.getEntity(id) instanceof DrifterEntity other && other.isAlive() ? other : null;
	}

	/** All plans (the debug command). */
	public static List<Plan> all() {
		return plans;
	}

	// The hooks other goals use.

	/** It said it'd hunt with someone tonight (it goes out even if it'd otherwise stay in). */
	public static boolean huntTonight(DrifterEntity drifter) {
		return due(drifter, Kind.HUNT) != null;
	}

	/** Who it's hunting with tonight, if they're about. */
	@Nullable
	public static DrifterEntity huntMate(DrifterEntity drifter) {
		Plan plan = due(drifter, Kind.HUNT);
		return plan == null ? null : partner(plan, drifter);
	}

	/** Who it said it'd go mining with (today), if they're about. */
	@Nullable
	public static DrifterEntity mineMate(DrifterEntity drifter) {
		Plan plan = due(drifter, Kind.MINE);
		return plan == null ? null : partner(plan, drifter);
	}

	/** It's gone mining with them: that plan's done (and the tag-along before it). */
	public static void wentMining(DrifterEntity drifter) {
		Plan plan = due(drifter, Kind.MINE);
		if (plan != null) {
			done(plan, drifter);
		}

		Plan tag = due(drifter, Kind.TAG_ALONG);
		if (tag != null) {
			done(tag, drifter);
		}
	}

	/** The one whose place it said it'd drop by, now it's time (they're about), or null. */
	@Nullable
	public static DrifterEntity visitDue(DrifterEntity drifter) {
		Plan plan = due(drifter, Kind.VISIT);
		return plan == null || plan.leads(drifter) ? null : partner(plan, drifter);
	}

	/** It's gone over: the visit's under way. */
	public static void visiting(DrifterEntity drifter) {
		Plan plan = due(drifter, Kind.VISIT);
		if (plan != null && !plan.leads(drifter)) {
			done(plan, drifter);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The goal: the plans for now (show home, share food, tag along)
	// ---------------------------------------------------------------------------------------------

	@Nullable
	private Plan plan;

	@Override
	public String id() {
		return "together";
	}

	@Override
	public String name(Goals.Context context) {
		Plan plan = this.plan;
		if (plan == null) {
			return "do what it agreed with someone";
		}

		boolean leads = plan.leads(context.drifter());
		String them = leads ? plan.otherName : plan.leadName;
		return switch (plan.kind) {
			case SHOW_HOME -> leads ? "show " + them + " its place" : "go see " + them + "'s place";
			case SHARE_FOOD -> leads ? "give " + them + " some food" : "get some food from " + them;
			default -> "tag along with " + them;
		};
	}

	/** Its plan for now, with the other about: which, and its part in it. */
	@Nullable
	private static Plan current(DrifterEntity drifter) {
		for (Kind kind : new Kind[] {Kind.SHARE_FOOD, Kind.SHOW_HOME, Kind.TAG_ALONG}) {
			Plan plan = due(drifter, kind);
			if (plan == null) {
				continue;
			}

			DrifterEntity other = partner(plan, drifter);
			// (A tag-along is the follower's: the one followed just goes about its day.)
			if (other == null || other.level() != drifter.level() || other.distanceTo(drifter) > APART || kind == Kind.TAG_ALONG && plan.leads(drifter)) {
				continue;
			}

			return plan;
		}

		return null;
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		this.plan = context.threat().urgent() || drifter.inCombat() || drifter.encounter() != null ? null : current(drifter);
		if (this.plan == null) {
			return 0.0;
		}

		// (Still stood talking: that first. In a long talk they talk as they go.)
		Conversations.Talk talk = Conversations.of(drifter);
		if (talk != null && !talk.longTalk()) {
			return 0.0;
		}

		return switch (this.plan.kind) {
			case SHARE_FOOD -> 0.66;
			case SHOW_HOME -> 0.6;
			default -> 0.45 + 0.1 * drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.SOCIABILITY);
		};
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public double risk(Goals.Context context) {
		return Goals.lateInTheDayRisk(context, 0.6);
	}

	@Override
	public int minCommitTicks() {
		return 20 * 15;
	}

	@Override
	@Nullable
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Plan plan = this.plan != null ? this.plan : current(drifter);
		DrifterEntity other = plan == null ? null : partner(plan, drifter);
		if (plan == null || other == null) {
			return null;
		}

		boolean leads = plan.leads(drifter);
		return switch (plan.kind) {
			case SHOW_HOME -> leads ? new ShowHome(drifter, plan, other) : new Follow(drifter, plan, other);
			case SHARE_FOOD -> leads ? new GiveFood(drifter, plan, other) : new Follow(drifter, plan, other);
			default -> new Follow(drifter, plan, other);
		};
	}

	/** A line about it (data/drifter/drifter_chat/together.json), to the other. */
	private static void say(DrifterEntity drifter, DrifterEntity to, String key) {
		String line = Lines.pick(drifter, "together." + key, "b", to.getName().getString());
		if (line != null) {
			ChatLines.to(drifter, to);
			ChatLines.speak(drifter, line);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Its part
	// ---------------------------------------------------------------------------------------------

	/** At this place: within 4 blocks across, and a storey or so up or down (a house's floor, a bed upstairs). */
	private static boolean near(Vec3 at, Vec3 place) {
		return Math.hypot(at.x - place.x, at.z - place.z) <= 4.0 && Math.abs(at.y - place.y) <= 5.0;
	}

	/** The waits and glances in these aren't failures. */
	private abstract static class Part extends Activity {
		final Plan plan;
		final DrifterEntity other;

		Part(DrifterEntity drifter, Plan plan, DrifterEntity other) {
			super(drifter);
			this.plan = plan;
			this.other = other;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return step instanceof NavigateTask;
		}

		/** Off: the other's gone (dead, too far, elsewhere) or the plan's been forgotten. */
		boolean off() {
			long now = this.drifter.level().getGameTime();
			return !this.other.isAlive() || this.other.isRemoved() || this.other.level() != this.drifter.level()
				|| this.other.distanceTo(this.drifter) > APART * 1.5 || now > this.plan.until || !plans.contains(this.plan);
		}

		/** Seen (or right here): it knows where they are only by seeing them. */
		boolean sees() {
			return this.other.distanceTo(this.drifter) < 4.0 || this.drifter.getSenses().tracked().stream()
				.anyMatch(awareness -> awareness.entity == this.other && awareness.visibleNow);
		}
	}

	/** Shows the other its place: home, then waits for them there and stays a bit. */
	private static final class ShowHome extends Part {
		private boolean home;
		private long homeAt = -1;
		private long bothThereAt = -1;

		ShowHome(DrifterEntity drifter, Plan plan, DrifterEntity other) {
			super(drifter, plan, other);
		}

		@Override
		public String describe() {
			return "show " + this.plan.otherName + " its place";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 4;
		}

		@Override
		protected Status next() {
			Home home = this.drifter.getDrifterBrain().home();
			if (home == null || this.off()) {
				done(this.plan, this.drifter);
				return Status.SUCCESS;
			}

			if (!this.plan.leadSaid) {
				this.plan.leadSaid = true;
				say(this.drifter, this.other, "show_home.lead");
			}

			Vec3 center = Vec3.atBottomCenterOf(home.center);
			if (!this.home && !near(this.drifter.position(), center)) {
				return this.begin(new NavigateTask(this.drifter, center, 2.5, "head home to show " + this.plan.otherName));
			}

			long now = this.drifter.level().getGameTime();
			if (!this.home) {
				this.home = true;
				this.homeAt = now;
				say(this.drifter, this.other, "show_home.here");
			}

			if (this.other.distanceTo(this.drifter) < 6.0) {
				if (this.bothThereAt < 0) {
					this.bothThereAt = now;
					// (Been there now: it knows where they live.)
					Relationship r = this.other.getBonds().get(this.drifter.getUUID());
					if (r != null) {
						r.knownBase = home.center;
					}

					this.other.getMemory().thoughts().add(now, "saw " + this.plan.leadName + "'s place");
				}

				if (now - this.bothThereAt >= SHOW_STAY) {
					this.drifter.getMemory().thoughts().add(now, "showed " + this.plan.otherName + " my place");
					com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(this.drifter, this.other, 1.0);
					com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(this.other, this.drifter, 1.0);
					done(this.plan, this.drifter);
					return Status.SUCCESS;
				}

				return this.begin(this.drifter.getRandom().nextBoolean() ? new LookAroundTask(this.drifter, 2, 20)
					: new LookAtTask(this.drifter, this.other.getEyePosition(), 40));
			}

			// Waiting for them (looking their way), a minute at most.
			if (this.bothThereAt < 0 && now - this.homeAt > 20 * 60) {
				done(this.plan, this.drifter);
				return Status.SUCCESS;
			}

			return this.begin(new LookAtTask(this.drifter, this.other.getEyePosition(), 30));
		}
	}

	/** Walks up to them and tosses them some of its spare food. */
	private static final class GiveFood extends Part {
		private boolean gave;

		GiveFood(DrifterEntity drifter, Plan plan, DrifterEntity other) {
			super(drifter, plan, other);
		}

		@Override
		public String describe() {
			return "give " + this.plan.otherName + " some food";
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60;
		}

		@Override
		protected boolean rethink() {
			return !this.gave && this.other.distanceTo(this.drifter) < 2.5;
		}

		@Override
		protected Status next() {
			if (this.gave || this.off()) {
				done(this.plan, this.drifter);
				return Status.SUCCESS;
			}

			if (this.other.distanceTo(this.drifter) >= 2.5) {
				if (!this.sees()) {
					done(this.plan, this.drifter);
					return Status.SUCCESS;
				}

				return this.begin(NavigateTask.follow(this.drifter, this.other, 2.0));
			}

			this.drifter.getCamera().lookAt(this.other.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
			Item food = PlayerTalk.spareFood(this.drifter);
			int given = 0;
			if (food != null) {
				int left = 3;
				for (Map.Entry<Integer, Integer> spare : com.dinglebob.drifter.brain.Storage.spare(this.drifter).entrySet()) {
					ItemStack stack = this.drifter.getInventory().getItem(spare.getKey());
					if (left <= 0 || !stack.is(food)) {
						continue;
					}

					int count = Math.min(left, Math.min(spare.getValue(), stack.getCount()));
					if (count > 0) {
						this.drifter.tossForward(stack.split(count));
						left -= count;
						given += count;
					}
				}
			}

			this.gave = true;
			long now = this.drifter.level().getGameTime();
			if (given > 0) {
				say(this.drifter, this.other, "share_food.give");
				this.drifter.getMemory().thoughts().add(now, "gave " + this.plan.otherName + " " + given + " " + new ItemStack(food).getHoverName().getString());
				com.dinglebob.drifter.api.DrifterApi.addTrustInDrifter(this.other, this.drifter, 2.0);
			} else {
				say(this.drifter, this.other, "share_food.none");
			}

			done(this.plan, this.drifter);
			// (A moment facing them while they pick it up.)
			return this.begin(new LookAtTask(this.drifter, this.other.getEyePosition(), 30));
		}
	}

	/** Goes with the other: to see their place, to get the food, or tagging along as they go about their day. */
	private static final class Follow extends Part {
		private boolean commented;
		private boolean thanked;
		private long doneSeenAt = -1;

		Follow(DrifterEntity drifter, Plan plan, DrifterEntity other) {
			super(drifter, plan, other);
		}

		@Override
		public String describe() {
			return switch (this.plan.kind) {
				case SHOW_HOME -> "go see " + this.plan.leadName + "'s place";
				case SHARE_FOOD -> "get some food from " + this.plan.leadName;
				default -> "tag along with " + this.plan.leadName;
			};
		}

		@Override
		protected int timeoutTicks() {
			return (int) Math.min(20L * 60 * 4, Math.max(20, this.plan.until - this.drifter.level().getGameTime()));
		}

		@Override
		protected boolean finishedEarly() {
			long now = this.drifter.level().getGameTime();
			if (this.plan.doneAt > 0) {
				if (this.doneSeenAt < 0) {
					this.doneSeenAt = now;
				}

				// (Thanks for the food once it's had a moment to pick it up.)
				if (!this.thanked && now - this.doneSeenAt >= 25 && this.plan.kind == Kind.SHARE_FOOD) {
					this.thanked = true;
					say(this.drifter, this.other, "share_food.thanks");
				}

				return now - this.doneSeenAt > 30;
			}

			return this.off();
		}

		@Override
		protected Status next() {
			if (!this.plan.otherSaid) {
				this.plan.otherSaid = true;
				say(this.drifter, this.other, this.plan.kind.key + ".follow");
			}

			if (this.plan.kind == Kind.SHOW_HOME && !this.commented && this.other.distanceTo(this.drifter) < 6.0) {
				Home home = this.other.getDrifterBrain().home();
				if (home != null && near(this.other.position(), Vec3.atBottomCenterOf(home.center))) {
					this.commented = true;
					say(this.drifter, this.other, "show_home.nice");
				}
			}

			if (this.plan.kind == Kind.SHARE_FOOD && this.other.distanceTo(this.drifter) < 3.0) {
				// (Stood facing them for it.)
				return this.begin(new LookAtTask(this.drifter, this.other.getEyePosition(), 20));
			}

			long now = this.drifter.level().getGameTime();
			if (!this.sees()) {
				// Lost them: over to where they were last seen; there, a look round; and after a while, it gives up on them.
				var awareness = this.drifter.getSenses().awarenessOf(this.other);
				boolean somewhere = awareness != null && awareness.lastSeenPos != null && awareness.lastSeenPos.distanceTo(this.drifter.position()) >= 3.0;
				if (this.plan.lostAt == 0) {
					this.plan.lostAt = now;
				}

				if (now - this.plan.lostAt > 20 * 20) {
					this.drifter.getMemory().thoughts().add(now, "lost track of " + this.plan.leadName);
					done(this.plan, this.drifter);
					return Status.SUCCESS;
				}

				if (!somewhere) {
					return this.begin(new LookAroundTask(this.drifter, 1, 10));
				}
			} else {
				this.plan.lostAt = 0;
			}

			return this.begin(NavigateTask.follow(this.drifter, this.other, this.plan.kind == Kind.SHARE_FOOD ? 1.5 : 3.0));
		}

		@Override
		protected boolean rethink() {
			// (Following goes on till stopped: a look at how things are every two seconds.)
			return this.drifter.level().getGameTime() % 40 == 0;
		}
	}
}
