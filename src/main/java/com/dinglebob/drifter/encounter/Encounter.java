package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * One encounter going on (milestone 21, docs/ENCOUNTERS.md): Drifters someone meets once and never again, there for one
 * player. They walk in from out of sight, play their part, then walk off, gone once nobody can see them; never saved.
 * Each kind says who's in it, what each one does (a brain goal: it still eats, fights back and runs like any Drifter)
 * and when it's over. Everything they do follows the usual rules: they see and hear for themselves, reach and act like
 * players, and carry only their own kit.
 */
public abstract class Encounter {
	/** Once it's over, the ones still about are gone this long after nobody can see them (or when nobody's near). */
	private static final double GONE_BEYOND = 48.0;
	private static final double SEEN_WITHIN = 128.0;
	/** However it went, it's over after this long at most (ticks; each kind can say less). */
	private static final long LONGEST = 20L * 60 * 12;
	/** Leaving, they're gone after this long even if someone keeps them in sight (they've walked out of the story). */
	private static final long LEAVE_AT_MOST = 20L * 60 * 3;

	public final Encounters.Kind kind;
	public final UUID player;
	public final String playerName;
	protected final ServerLevel level;
	protected final List<DrifterEntity> members = new ArrayList<>();
	protected final long startedAt;
	private boolean over;
	private long overAt;
	private String ending = "";

	protected Encounter(Encounters.Kind kind, ServerPlayer player) {
		this.kind = kind;
		this.player = player.getUUID();
		this.playerName = player.getGameProfile().getName();
		this.level = player.serverLevel();
		this.startedAt = this.level.getGameTime();
	}

	/** Adds one of its Drifters (made by {@link EncounterSpawner}). */
	protected void join(DrifterEntity drifter, String role) {
		drifter.joinEncounter(this, role);
		this.members.add(drifter);
	}

	public List<DrifterEntity> members() {
		return List.copyOf(this.members);
	}

	public boolean isOver() {
		return this.over;
	}

	/** The player it's for, if they're on and in its world. */
	@Nullable
	protected ServerPlayer player() {
		ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(this.player);
		return player != null && player.level() == this.level ? player : null;
	}

	protected long now() {
		return this.level.getGameTime();
	}

	/** Its longest run, in ticks. */
	protected long longest() {
		return LONGEST;
	}

	/** Once a second; false when it's over and everyone in it is gone. */
	final boolean tick() {
		this.members.removeIf(drifter -> drifter.isRemoved() || !drifter.isAlive());
		if (!this.over) {
			this.update();
			if (!this.over && this.now() - this.startedAt > this.longest()) {
				this.end("time ran out");
			}

			return true;
		}

		// Over: each one heads off and is gone once nobody can see it (or nobody's near).
		long leaving = this.now() - this.overAt;
		this.members.removeIf(drifter -> {
			if (unseen(drifter) || leaving > LEAVE_AT_MOST) {
				drifter.discard();
				return true;
			}

			return false;
		});
		return !this.members.isEmpty();
	}

	/** Nobody near, or nobody who could see it. */
	private static boolean unseen(DrifterEntity drifter) {
		for (Player player : drifter.level().players()) {
			if (player.isSpectator()) {
				continue;
			}

			double distance = player.distanceTo(drifter);
			if (distance < GONE_BEYOND || distance < SEEN_WITHIN && player.hasLineOfSight(drifter)) {
				return false;
			}
		}

		return true;
	}

	/** What's happening, once a second: who's where, who's dead, whether it's over ({@link #end}). */
	protected abstract void update();

	/** How keen this one is on its part right now (0: its own life decides); over, they all just leave. */
	public abstract double urgency(DrifterEntity drifter);

	/** Its part right now, as an activity. */
	@Nullable
	public abstract Activity start(DrifterEntity drifter);

	/** Its part in words (for its thoughts and the overlay). */
	public abstract String describe(DrifterEntity drifter);

	/** Its part can't wait (a goal that switches in at once). */
	public boolean urgent(DrifterEntity drifter) {
		return false;
	}

	/** It came for this player: hostile at once, no warning (bounty hunters, an ambush). */
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return false;
	}

	/** It keeps after them far longer than most (a hunter, a bandit). */
	public boolean relentless(DrifterEntity drifter) {
		return false;
	}

	/** A player right-clicked it (the merchant's trades). */
	public InteractionResult interact(DrifterEntity drifter, Player player) {
		return InteractionResult.PASS;
	}

	/** The player died (while it was on). */
	protected void playerDied() {
	}

	/** It's over: everyone in it heads off. */
	public void end(String why) {
		if (this.over) {
			return;
		}

		this.over = true;
		this.overAt = this.now();
		this.ending = why;
		for (DrifterEntity drifter : this.members) {
			drifter.getDrifterBrain().endPursuit();
		}

		Drifter.LOGGER.info("[Drifter] encounter over: {} for {} ({})", this.kind.label, this.playerName, why);
	}

	public String status() {
		StringBuilder who = new StringBuilder();
		for (DrifterEntity drifter : this.members) {
			who.append(who.length() == 0 ? "" : ", ").append(drifter.getName().getString()).append(" (").append(drifter.encounterRole()).append(")");
		}

		return this.kind.label + " for " + this.playerName + ": " + (who.length() == 0 ? "nobody left" : who) + "; " + (this.now() - this.startedAt) / 20
			+ " s in" + (this.over ? ", over (" + this.ending + ")" : "");
	}

	// ---------------------------------------------------------------------------------------------
	// Shared bits
	// ---------------------------------------------------------------------------------------------

	/**
	 * Both in the same encounter and on the same side (found in a test: two bandits of one ambush, not in a group, killed
	 * each other on sight). Every member is, unless a kind says otherwise (a thief and the one it robbed).
	 */
	public static boolean together(DrifterEntity one, DrifterEntity other) {
		Encounter encounter = one.encounter();
		return encounter != null && encounter == other.encounter() && encounter.sameSide(one, other);
	}

	/** These two of its members are on the same side. */
	protected boolean sameSide(DrifterEntity one, DrifterEntity other) {
		return true;
	}

	/** It sees them now (identified, in view). */
	protected static boolean sees(DrifterEntity drifter, @Nullable Entity entity) {
		if (entity == null) {
			return false;
		}

		Awareness awareness = drifter.getSenses().awarenessOf(entity);
		return awareness != null && awareness.identified && awareness.visibleNow;
	}

	/** It knows where they are (seen in the last few seconds). */
	protected static boolean knowsWhere(DrifterEntity drifter, @Nullable Entity entity) {
		if (entity == null) {
			return false;
		}

		Awareness awareness = drifter.getSenses().awarenessOf(entity);
		return awareness != null && awareness.identified && awareness.seenWithin(drifter.level().getGameTime(), 20 * 5);
	}

	/**
	 * Over to someone, once, to talk or hand something over (a follow never ends; this does, near them): where they are
	 * in sight, else where it last saw them, else where the encounter told it they were. Null if it's there already.
	 */
	@Nullable
	protected static com.dinglebob.drifter.executor.Task walkUpTo(DrifterEntity drifter, Entity entity, double radius) {
		Awareness awareness = drifter.getSenses().awarenessOf(entity);
		Vec3 at = awareness != null && !awareness.visibleNow && awareness.lastSeenPos != null ? awareness.lastSeenPos : entity.position();
		return drifter.position().distanceTo(at) <= radius + 0.5 ? null
			: new com.dinglebob.drifter.executor.task.NavigateTask(drifter, at, radius, "walk up to " + entity.getName().getString(),
				com.dinglebob.drifter.executor.task.NavigateTask.Pace.TRAVEL);
	}

	/** Items this player threw down within {@code radius} of it (what they're giving it, as a player gives: drops it). */
	protected static List<net.minecraft.world.entity.item.ItemEntity> thrownBy(DrifterEntity drifter, Player player, double radius) {
		return drifter.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, drifter.getBoundingBox().inflate(radius),
			item -> item.isAlive() && item.getOwner() == player);
	}

	/** How many it carries of what these test for. */
	protected static int carried(DrifterEntity drifter, java.util.function.Predicate<net.minecraft.world.item.ItemStack> test) {
		return drifter.getInventory().count(test);
	}

	/** A line in chat, talking to someone (the chat-lines setting applies; the chatter limits don't). */
	protected static void say(DrifterEntity drifter, String line) {
		com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
	}

	protected static void think(DrifterEntity drifter, String thought) {
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), thought);
	}

	/** A point {@code distance} on from {@code from} the way from {@code away} to it (on the ground there, roughly). */
	protected static Vec3 beyond(Vec3 away, Vec3 from, double distance) {
		Vec3 dir = new Vec3(from.x - away.x, 0.0, from.z - away.z);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
		return from.add(dir.scale(distance));
	}
}
