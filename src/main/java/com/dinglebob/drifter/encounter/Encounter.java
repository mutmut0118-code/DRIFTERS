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

		// Over: each one heads off and is gone once nobody can see it (or nobody's near); its animals with it.
		long leaving = this.now() - this.overAt;
		this.members.removeIf(drifter -> {
			if (unseen(drifter) || leaving > LEAVE_AT_MOST) {
				drifter.discard();
				return true;
			}

			return false;
		});
		this.props.removeIf(prop -> {
			if (prop.isRemoved() || !prop.isAlive() || !this.stillTheirs(prop)) {
				// (Not theirs any more: a horse whose rider fell, a dog someone kept. It stays in the world.)
				return true;
			}

			if (unseen(prop) || leaving > LEAVE_AT_MOST) {
				prop.discard();
				return true;
			}

			return false;
		});
		return !this.members.isEmpty() || !this.props.isEmpty();
	}

	/** Everyone and everything in it gone at once (the server stopping, a new one starting, a kind that broke). */
	void discardAll() {
		this.members.forEach(DrifterEntity::discard);
		this.props.forEach(prop -> {
			if (this.stillTheirs(prop)) {
				prop.discard();
			}
		});
	}

	/** Nobody near, or nobody who could see it. */
	private static boolean unseen(Entity entity) {
		for (Player player : entity.level().players()) {
			if (player.isSpectator()) {
				continue;
			}

			double distance = player.distanceTo(entity);
			if (distance < GONE_BEYOND || distance < SEEN_WITHIN && player.hasLineOfSight(entity)) {
				return false;
			}
		}

		return true;
	}

	/** Animals and things that came with it (horses, a dog): gone with it at the end, while they're still its. */
	protected final List<Entity> props = new ArrayList<>();

	/** This one still goes with them (a horse a member rides, a dog one of them has): yes unless a kind says. */
	protected boolean stillTheirs(Entity prop) {
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

	/**
	 * How the player killing one of its Drifters counts (the user, 2026-10-20: "what will be the punishment for killing in
	 * a duel?"). One against them at the time (a bandit, a hunter): self-defence, nothing; anyone else: a kill like any
	 * Drifter's. Each kind can say otherwise (a thief: it had it coming; a duellist that gave up: murder).
	 */
	public Killing killing(DrifterEntity drifter, Player player) {
		return this.hostileTo(drifter, player) ? Killing.NONE : Killing.KILL;
	}

	/** What a player's killing one of them counts as. */
	public enum Killing {
		/** Self-defence, or it had it coming: nothing. */
		NONE,
		/** Like killing any Drifter: towards a price on their head, and now and then its friends come for revenge. */
		KILL,
		/** One that had given up: a price on their head at once, and its friends come for revenge. */
		MURDER
	}

	/** One of its Drifters was hurt (as each blow lands: the once-a-second update can be a blow too late). */
	protected void hurt(DrifterEntity drifter, net.minecraft.world.damagesource.DamageSource source) {
	}

	/** Its player was hurt (as each blow lands). */
	protected void playerHurt(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
	}

	/** Its player said something in chat (lower case, letters and digits only, a space either side of each word). */
	protected void chat(ServerPlayer player, String words) {
	}

	/** They said one of these words (in {@link #chat}'s form). */
	protected static boolean saidAny(String words, String... any) {
		for (String word : any) {
			if (words.contains(" " + word + " ")) {
				return true;
			}
		}

		return false;
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

	/**
	 * Off to where word has it someone is, then round there, one spot then another (it doesn't stand still): a hunt for
	 * them (bounty hunters, a revenge squad). {@code lead} is read as it goes (where one of them last saw them).
	 */
	protected static Script search(DrifterEntity drifter, String what, String name, java.util.function.Supplier<Vec3> lead, double radius) {
		var random = drifter.getRandom();
		Script script = new Script(drifter, what, 20 * 60 * 3);
		if (drifter.position().distanceTo(lead.get()) > 12.0) {
			script.then(() -> new com.dinglebob.drifter.executor.task.NavigateTask(drifter, lead.get(), 6.0, "head for where " + name + " was seen",
				com.dinglebob.drifter.executor.task.NavigateTask.Pace.TRAVEL));
		}

		for (int i = 0; i < 3; i++) {
			script.then(() -> {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double distance = 6.0 + random.nextDouble() * (radius - 6.0);
				Vec3 spot = lead.get().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
				return new com.dinglebob.drifter.executor.task.NavigateTask(drifter, spot, 4.0, "search for " + name,
					com.dinglebob.drifter.executor.task.NavigateTask.Pace.TRAVEL);
			});
			script.then(() -> new com.dinglebob.drifter.executor.task.LookAroundTask(drifter, 2, 6));
		}

		return script;
	}

	/**
	 * Over to somewhere it can put a block down at {@code cell} from (the ground under it in reach and in sight); null if
	 * it's there already. (A block going on the ground; see {@link #toBuild} for one in the air.)
	 */
	@Nullable
	protected static com.dinglebob.drifter.executor.Task toPlace(DrifterEntity drifter, net.minecraft.core.BlockPos cell) {
		if (com.dinglebob.drifter.hands.Reach.canReach(drifter, cell.below())) {
			return null;
		}

		net.minecraft.core.BlockPos stand = com.dinglebob.drifter.brain.KnownBlocks.standingSpotFor(drifter, cell.below(), java.util.Set.of(cell));
		return stand != null ? new com.dinglebob.drifter.executor.task.NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.6, "to where it puts it",
				com.dinglebob.drifter.executor.task.NavigateTask.Pace.STROLL)
			: new com.dinglebob.drifter.executor.task.NavigateTask(drifter, Vec3.atBottomCenterOf(cell), 2.0, "to where it puts it",
				com.dinglebob.drifter.executor.task.NavigateTask.Pace.STROLL);
	}

	/**
	 * Over to a spot to stand while putting a block at {@code cell} (anywhere in the air, beside what's there): standing
	 * ground nearest it, its eyes within reach of the block, not in any of {@code keepClear} (the build); null if it can
	 * reach it from here (or there's no such spot: it tries from where it is).
	 */
	@Nullable
	protected static com.dinglebob.drifter.executor.Task toBuild(DrifterEntity drifter, net.minecraft.core.BlockPos cell, java.util.Set<net.minecraft.core.BlockPos> keepClear) {
		boolean clearHere = !keepClear.contains(drifter.blockPosition()) && !keepClear.contains(drifter.blockPosition().above());
		if (clearHere && !cell.equals(drifter.blockPosition()) && !cell.equals(drifter.blockPosition().above()) && placeableFrom(drifter, drifter.getEyePosition(), cell)) {
			return null;
		}

		// Spots near enough, nearest it first; the first one it could place it from (a face it can see and reach, as the
		// placing itself checks: found in testing, "in reach" alone sent it to spots with every face hidden).
		net.minecraft.world.level.Level level = drifter.level();
		Vec3 center = Vec3.atCenterOf(cell);
		double reach = drifter.blockInteractionRange();
		List<net.minecraft.core.BlockPos> spots = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -4; dy <= 1; dy++) {
					net.minecraft.core.BlockPos pos = cell.offset(dx, dy, dz);
					if (keepClear.contains(pos) || keepClear.contains(pos.above()) || pos.equals(cell) || pos.above().equals(cell)
						|| Vec3.atBottomCenterOf(pos).add(0.0, drifter.getEyeHeight(), 0.0).distanceTo(center) > reach + 0.5
						|| !com.dinglebob.drifter.brain.KnownBlocks.isStandable(level, pos)) {
						continue;
					}

					spots.add(pos.immutable());
				}
			}
		}

		spots.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(drifter.blockPosition())));
		for (net.minecraft.core.BlockPos pos : spots) {
			if (placeableFrom(drifter, Vec3.atBottomCenterOf(pos).add(0.0, drifter.getEyeHeight(), 0.0), cell)) {
				return new com.dinglebob.drifter.executor.task.NavigateTask(drifter, Vec3.atBottomCenterOf(pos), 0.4, "step over to build",
					com.dinglebob.drifter.executor.task.NavigateTask.Pace.STROLL);
			}
		}

		return null;
	}

	/** From an eye there it could put a block at {@code cell}: a face of something solid beside it in sight and reach. */
	protected static boolean placeableFrom(DrifterEntity drifter, Vec3 eye, net.minecraft.core.BlockPos cell) {
		net.minecraft.world.level.Level level = drifter.level();
		for (net.minecraft.core.Direction toSupport : net.minecraft.core.Direction.values()) {
			net.minecraft.core.BlockPos support = cell.relative(toSupport);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(support);
			if (state.isAir() || state.canBeReplaced()) {
				continue;
			}

			if (com.dinglebob.drifter.hands.Reach.aimPointFrom(drifter, eye, support, toSupport.getOpposite()).isPresent()) {
				return true;
			}
		}

		return false;
	}

	/** Its coordinates, rounded, as a player would read them off and type them (x, y, z). */
	protected static String coords(net.minecraft.core.BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	/** Which way {@code to} lies from {@code from}, as a compass word ("north-east"). */
	protected static String direction(Vec3 from, Vec3 to) {
		double angle = Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x));
		// East is 0, south 90 (z grows southward).
		String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
		return names[Math.floorMod((int)Math.round(angle / 45.0), 8)];
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
