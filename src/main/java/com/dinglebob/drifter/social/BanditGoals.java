package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.brain.activity.BuildPlanActivity;
import com.dinglebob.drifter.brain.activity.LootChestActivity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.world.DrifterGroups;
import com.dinglebob.drifter.world.DrifterWars;
import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Outpost Bandits (user's decision, 2026-10-17: "automatically hostile, as griefer as possible", to players and to other
 * Drifters; a setting, on by default). A gang that holds a pillager outpost:
 * <ul>
 *   <li>attacks players and other Drifters on sight (no waiting for an opening);</li>
 *   <li>remembers the bases it finds: a player's build it has seen (a cluster of placed blocks), where it saw someone go
 *       to bed, another Drifter's home;</li>
 *   <li>about once a day, goes there as a gang and griefs it: breaks the doors, empties the chests, sets fire to the
 *       wood, smashes the torches, crops, stations and walls; then goes home.</li>
 * </ul>
 * Still never: an ender chest, anything right by a bed (no bed traps), or inside the server's spawn protection.
 */
public final class BanditGoals {
	private BanditGoals() {
	}

	public static List<Goals.Goal> all() {
		return List.of(new GriefRaid(), new Rampage());
	}

	// ---------------------------------------------------------------------------------------------
	// Kill on sight (user, 2026-10-17: "can they be on sight kill anything that isnt their group? im talking worse than
	// pillagers"; a setting, on by default; overrides the telegraph-first rule for Bandits)
	// ---------------------------------------------------------------------------------------------

	/** A Bandit that kills on sight (the setting is on). */
	public static boolean ruthless(DrifterEntity drifter) {
		return DrifterConfig.banditsKillOnSight() && drifter.getSocial().disposition() == Disposition.BANDIT;
	}

	/** How far off it goes for something it sees. */
	private static final double RAMPAGE_RANGE = 32.0;

	/**
	 * Fair game for a ruthless Bandit: a player (not creative or spectating), a Drifter outside its group, villagers and
	 * wandering traders, golems, and other people's tamed animals. Not its own group, not wild animals, not other hostiles
	 * (those it fights as anyone does when they come at it).
	 */
	public static boolean prey(DrifterEntity drifter, net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive() || living == drifter) {
			return false;
		}

		if (living instanceof net.minecraft.world.entity.player.Player player) {
			return SocialWatcher.isRealPlayer(player) && !player.isCreative() && !player.isSpectator();
		}

		var mine = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
		if (living instanceof DrifterEntity other) {
			return mine == null || com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID()) != mine;
		}

		if (living instanceof net.minecraft.world.entity.npc.AbstractVillager || living instanceof net.minecraft.world.entity.animal.AbstractGolem) {
			return true;
		}

		if (living instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()) {
			java.util.UUID owner = pet.getOwnerUUID();
			return owner != null && !owner.equals(drifter.getUUID()) && (mine == null || com.dinglebob.drifter.world.DrifterGroups.of(owner) != mine);
		}

		return false;
	}

	/** The nearest prey it can see now, players first; null if none. */
	@Nullable
	static net.minecraft.world.entity.LivingEntity preyInSight(DrifterEntity drifter) {
		net.minecraft.world.entity.LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (var awareness : drifter.getSenses().tracked()) {
			if (!awareness.visibleNow || !prey(drifter, awareness.entity)) {
				continue;
			}

			double distance = awareness.entity.distanceTo(drifter);
			if (distance > RAMPAGE_RANGE) {
				continue;
			}

			// Players first, then the closest.
			double score = distance - (awareness.entity instanceof net.minecraft.world.entity.player.Player ? 16.0 : 0.0);
			if (score < bestScore) {
				bestScore = score;
				best = (net.minecraft.world.entity.LivingEntity)awareness.entity;
			}
		}

		return best;
	}

	/** Kill on sight: under the fights it's already in and running away, over everything else. */
	public static final class Rampage extends Goals.Goal {
		@Override
		public String id() {
			return "rampage";
		}

		@Override
		public String name(Goals.Context context) {
			return "kill on sight";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			if (!ruthless(drifter) || drifter.getHealth() < drifter.getMaxHealth() * 0.35F) {
				return 0.0;
			}

			return preyInSight(drifter) != null ? 1.0 : 0.0;
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
		public com.dinglebob.drifter.brain.activity.Activity start(Goals.Context context) {
			net.minecraft.world.entity.LivingEntity target = preyInSight(context.drifter());
			return target == null ? null : new RampageFight(context.drifter(), target);
		}
	}

	/** At it until it's dead, gone, or it's too hurt to go on; then the drops. */
	private static final class RampageFight extends com.dinglebob.drifter.brain.activity.Activity {
		private final net.minecraft.world.entity.LivingEntity target;
		private final String label;
		private boolean looted;

		RampageFight(DrifterEntity drifter, net.minecraft.world.entity.LivingEntity target) {
			super(drifter);
			this.target = target;
			this.label = target instanceof net.minecraft.world.entity.player.Player player ? player.getGameProfile().getName()
				: target.getName().getString();
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "kill on sight: going for " + this.label);
		}

		@Override
		public String describe() {
			return "kill " + this.label;
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 2;
		}

		@Override
		public boolean atCheckpoint() {
			return true;
		}

		@Override
		protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
			return false;
		}

		@Override
		protected com.dinglebob.drifter.brain.activity.Activity.Status next() {
			if (!this.target.isAlive() || this.target.isRemoved()) {
				if (!this.looted) {
					this.looted = true;
					this.think("killed " + this.label + ": taking what it dropped");
					return this.begin(new com.dinglebob.drifter.executor.task.CollectItemsTask(this.drifter, 8.0));
				}

				return com.dinglebob.drifter.brain.activity.Activity.Status.SUCCESS;
			}

			if (this.target.distanceTo(this.drifter) > RAMPAGE_RANGE * 2.0 || this.drifter.getHealth() < this.drifter.getMaxHealth() * 0.35F) {
				return com.dinglebob.drifter.brain.activity.Activity.Status.SUCCESS;
			}

			return this.begin(new com.dinglebob.drifter.executor.task.AttackTask(this.drifter, this.target, this.label));
		}
	}

	/** An outpost Bandit with griefing on. */
	public static boolean griefer(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return DrifterConfig.banditGriefing() && home != null && home.outpost && drifter.getSocial().disposition() == Disposition.BANDIT;
	}

	/** Least time between two of a gang's raids (plus up to half again). */
	private static final long RAID_GAP = 24000L;
	/** How far from its outpost it goes raiding. */
	private static final double RAID_RANGE = 320.0;
	private static final double MUSTER_RANGE = 64.0;
	private static final double AT_BASE = 20.0;
	/** How long it griefs once there. */
	private static final long GRIEF_TICKS = 20L * 60 * 4;
	private static final long GATHER_TICKS = 20L * 60;
	/** How close to a bed it never touches anything. */
	private static final double NEAR_BED = 5.0;
	/** Player-placed blocks it must have seen close together to call it someone's base. */
	private static final int BASE_BLOCKS = 8;
	private static final int MAX_FIRES = 3;
	private static final int MAX_BREAKS = 14;

	// ---------------------------------------------------------------------------------------------
	// Raids (the gang's, kept while the server runs)
	// ---------------------------------------------------------------------------------------------

	enum Phase {
		GATHER, TRAVEL, GRIEF, RETREAT
	}

	static final class Raid {
		final UUID gang;
		final BlockPos home;
		final BlockPos target;
		final String whose;
		Phase phase = Phase.GATHER;
		long since;

		Raid(UUID gang, BlockPos home, BlockPos target, String whose, long now) {
			this.gang = gang;
			this.home = home;
			this.target = target;
			this.whose = whose;
			this.since = now;
		}

		void enter(Phase phase, long now) {
			this.phase = phase;
			this.since = now;
		}
	}

	private static final Map<UUID, Raid> RAIDS = new HashMap<>();
	private static final Map<UUID, Long> LAST_RAID = new HashMap<>();
	private static final Map<UUID, Long> LAST_SCOUT_TRIP = new HashMap<>();
	/** Least time between two scouting trips when it knows no base. */
	private static final long SCOUT_GAP = 20L * 60 * 4;
	private static final Map<DrifterEntity, Long> LAST_SCOUT = new WeakHashMap<>();

	/** A base it knows of and could raid, and whose it is. */
	record Target(BlockPos pos, String whose) {
	}

	/**
	 * Looks round for bases (every 10 s): player-placed blocks it has seen, close together, are someone's base; it
	 * tells the rest of the gang. Only what it has seen counts (section 1: knowledge only through perception).
	 */
	static void scout(DrifterEntity drifter, Home home) {
		long now = drifter.level().getGameTime();
		Long last = LAST_SCOUT.get(drifter);
		if (last != null && now - last < 200) {
			return;
		}

		LAST_SCOUT.put(drifter, now);
		Level level = drifter.level();
		var terrain = drifter.getSenses().terrain();
		DrifterGroups.Group mine = DrifterGroups.of(drifter.getUUID());
		// Another Drifter seen at its own home (its hut in view): that's a base.
		for (var awareness : drifter.getSenses().tracked()) {
			if (awareness.visibleNow && awareness.entity instanceof DrifterEntity other && (mine == null || DrifterGroups.of(other.getUUID()) != mine)) {
				Home theirs = other.getDrifterBrain().home();
				if (theirs != null && !theirs.outpost && theirs.dimension.equals(level.dimension().location()) && theirs.center.closerThan(other.blockPosition(), 24.0)
					&& terrain.isKnown(theirs.center.below())) {
					mark(drifter, home, theirs.center, now, other.getName().getString() + "'s");
				}
			}
		}

		ChunkPos here = new ChunkPos(drifter.blockPosition());
		List<BlockPos> seen = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (!level.hasChunk(here.x + dx, here.z + dz)) {
					continue;
				}

				List<Long> placed = level.getChunk(here.x + dx, here.z + dz).getAttached(PlayerPlacedBlocks.PLACED);
				if (placed == null) {
					continue;
				}

				for (long key : placed) {
					BlockPos pos = BlockPos.of(key);
					if (terrain.isKnown(pos) && !level.getBlockState(pos).isAir()) {
						seen.add(pos);
					}
				}
			}
		}

		if (seen.size() < BASE_BLOCKS) {
			return;
		}

		long x = 0;
		long y = 0;
		long z = 0;
		for (BlockPos pos : seen) {
			x += pos.getX();
			y += pos.getY();
			z += pos.getZ();
		}

		BlockPos middle = new BlockPos((int)(x / seen.size()), (int)(y / seen.size()), (int)(z / seen.size()));
		mark(drifter, home, middle, now, "someone's");
	}

	/** A base found: remembered, and the rest of the gang told. */
	private static void mark(DrifterEntity drifter, Home home, BlockPos middle, long now, String whose) {
		if (home.marks.stream().anyMatch(m -> m.closerThan(middle, 32.0)) || middle.closerThan(home.center, 24.0)) {
			return;
		}

		home.marks.add(middle.immutable());
		drifter.getMemory().thoughts().add(now, "found " + whose + " base at " + middle.toShortString() + ": one for the gang");
		DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
		if (gang != null && drifter.getServer() != null) {
			DrifterWars.forMembers(drifter.getServer(), gang.id, member -> {
				Home theirs = member.getDrifterBrain().home();
				if (member != drifter && theirs != null && theirs.marks.stream().noneMatch(m -> m.closerThan(middle, 32.0))) {
					theirs.marks.add(middle);
				}
			});
		}
	}

	/** Debug: its gang knows of a base here, and its next raid is due now. */
	public static void debugMark(DrifterEntity drifter, BlockPos pos) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null) {
			return;
		}

		mark(drifter, home, pos, drifter.level().getGameTime(), "a debug");
		DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
		LAST_RAID.put(gang == null ? drifter.getUUID() : gang.id, drifter.level().getGameTime() - RAID_GAP * 2);
	}

	/** The bases it knows within reach of its outpost: builds it found, and where players and Drifters live. */
	static List<Target> targets(DrifterEntity drifter, Home home) {
		List<Target> targets = new ArrayList<>();
		for (BlockPos mark : home.marks) {
			targets.add(new Target(mark, "someone"));
		}

		DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
		for (Relationship r : drifter.getSocial().all()) {
			if (r.knownBase != null) {
				targets.add(new Target(r.knownBase, r.name == null ? "a player" : r.name));
			}
		}

		for (var bond : drifter.getBonds().all()) {
			if (bond.knownBase != null && (gang == null || DrifterGroups.of(bond.player) != gang)) {
				targets.add(new Target(bond.knownBase, bond.name == null ? "a Drifter" : bond.name));
			}
		}

		targets.removeIf(t -> !t.pos().closerThan(home.center, RAID_RANGE) || t.pos().closerThan(home.center, 24.0));
		return targets;
	}

	@Nullable
	static Raid raidFor(DrifterEntity drifter) {
		DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
		Raid raid = gang == null ? RAIDS.get(drifter.getUUID()) : RAIDS.get(gang.id);
		if (raid == null) {
			return null;
		}

		if (raid.phase == Phase.GATHER && !drifter.blockPosition().closerThan(raid.home, MUSTER_RANGE)) {
			return null;
		}

		return raid;
	}

	// ---------------------------------------------------------------------------------------------
	// The goal
	// ---------------------------------------------------------------------------------------------

	public static final class GriefRaid extends Goals.Goal {
		@Override
		public String id() {
			return "griefraid";
		}

		@Override
		public String name(Goals.Context context) {
			Raid raid = raidFor(context.drifter());
			return raid == null ? "raid someone's base" : "raid " + raid.whose + "'s base";
		}

		@Override
		public double urgency(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			if (!griefer(drifter)) {
				return 0.0;
			}

			Home home = drifter.getDrifterBrain().home();
			scout(drifter, home);
			if (drifter.getHealth() < 10.0F || drifter.getFoodData().getFoodLevel() < 8) {
				return 0.0;
			}

			Raid raid = raidFor(drifter);
			if (raid != null) {
				return 1.0;
			}

			// Under way (out scouting): it keeps at it.
			if (drifter.getDrifterBrain().currentGoal() == this && drifter.getDrifterBrain().activity() != null) {
				return 0.8;
			}

			// Time for another: the leader (or a loner) picks a base and calls the gang.
			DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
			UUID key = gang == null ? drifter.getUUID() : gang.id;
			if (gang != null && !gang.leader.equals(drifter.getUUID()) || RAIDS.containsKey(key)) {
				return 0.0;
			}

			long now = drifter.level().getGameTime();
			// (A gang new to the server, or one that's just found its first base, goes within a couple of minutes.)
			long last = LAST_RAID.computeIfAbsent(key, k -> now - RAID_GAP + 20 * 120);
			if (now - last < RAID_GAP + (key.getLeastSignificantBits() & 0xFF) * 10L) {
				return 0.0;
			}

			// No base known yet: off looking for one round the outpost (a gang's scouting), now and then.
			return targets(drifter, home).isEmpty() ? (now - LAST_SCOUT_TRIP.getOrDefault(key, 0L) > SCOUT_GAP ? 0.6 : 0.0) : 0.85;
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
		public int minCommitTicks() {
			return 20 * 30;
		}

		@Override
		public Activity start(Goals.Context context) {
			DrifterEntity drifter = context.drifter();
			Raid raid = raidFor(drifter);
			if (raid == null) {
				Home home = drifter.getDrifterBrain().home();
				List<Target> targets = home == null ? List.of() : targets(drifter, home);
				if (targets.isEmpty()) {
					if (home == null) {
						return null;
					}

					DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
					LAST_SCOUT_TRIP.put(gang == null ? drifter.getUUID() : gang.id, drifter.level().getGameTime());
					// Toward where it last saw someone (a player or a Drifter not of the gang): people live somewhere near where
					// they're about. Otherwise any way out from the outpost.
					BlockPos lead = null;
					long leadAt = Long.MIN_VALUE;
					for (Relationship r : drifter.getSocial().all()) {
						if (r.lastSeenPos != null && r.lastSeenAt > leadAt && r.lastSeenPos.closerThan(home.center, RAID_RANGE)) {
							lead = r.lastSeenPos;
							leadAt = r.lastSeenAt;
						}
					}

					for (Relationship r : drifter.getBonds().all()) {
						if (r.lastSeenPos != null && r.lastSeenAt > leadAt && r.lastSeenPos.closerThan(home.center, RAID_RANGE)
							&& (gang == null || DrifterGroups.of(r.player) != gang)) {
							lead = r.lastSeenPos;
							leadAt = r.lastSeenAt;
						}
					}

					double angle = drifter.getRandom().nextDouble() * Math.PI * 2.0;
					double far = 60.0 + drifter.getRandom().nextDouble() * 90.0;
					Vec3 toward = lead != null && drifter.getRandom().nextFloat() < 0.75 ? Vec3.atBottomCenterOf(lead)
						: Vec3.atBottomCenterOf(home.center).add(Math.cos(angle) * far, 0.0, Math.sin(angle) * far);
					drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "out looking for a base to raid");
					return new com.dinglebob.drifter.brain.activity.ExploreActivity(drifter, "look for a base to raid", toward,
						d -> d.getDrifterBrain().home() != null && !targets(d, d.getDrifterBrain().home()).isEmpty());
				}

				Target target = targets.get(drifter.getRandom().nextInt(targets.size()));
				DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
				UUID key = gang == null ? drifter.getUUID() : gang.id;
				raid = new Raid(key, home.center, target.pos(), target.whose(), drifter.level().getGameTime());
				RAIDS.put(key, raid);
				LAST_RAID.put(key, drifter.level().getGameTime());
				drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "rounding up the gang to raid " + target.whose() + "'s base at "
					+ target.pos().toShortString());
			}

			return new GriefRaidActivity(drifter, raid);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// One member's part
	// ---------------------------------------------------------------------------------------------

	static final class GriefRaidActivity extends Activity {
		private final Raid raid;
		private final boolean leader;
		private final Set<BlockPos> failed = new HashSet<>();
		private final Map<BlockPos, Integer> tries = new HashMap<>();
		@Nullable
		private BlockPos working;
		private int fires;
		private int breaks;
		private int walks;

		GriefRaidActivity(DrifterEntity drifter, Raid raid) {
			super(drifter);
			this.raid = raid;
			DrifterGroups.Group gang = DrifterGroups.of(drifter.getUUID());
			this.leader = gang == null || gang.leader.equals(drifter.getUUID());
		}

		@Override
		public String describe() {
			return switch (this.raid.phase) {
				case GATHER -> this.leader ? "round up the gang to raid " + this.raid.whose : "join the raid on " + this.raid.whose;
				case TRAVEL -> "on the way to raid " + this.raid.whose + "'s base";
				case GRIEF -> "wreck " + this.raid.whose + "'s base";
				case RETREAT -> "head back to the outpost";
			};
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 8;
		}

		@Override
		protected boolean countsAsFailure(Task step) {
			return false;
		}

		@Override
		protected void stepDone(Task step, Status status) {
			if (this.working != null) {
				if (status != Status.SUCCESS && this.tries.merge(this.working, 1, Integer::sum) >= 2) {
					this.failed.add(this.working);
				}

				this.working = null;
			}
		}

		@Override
		protected Status next() {
			if (RAIDS.get(this.raid.gang) != this.raid || !griefer(this.drifter)) {
				return Status.SUCCESS;
			}

			long now = this.now();
			return switch (this.raid.phase) {
				case GATHER -> this.gather(now);
				case TRAVEL -> this.travel(now);
				case GRIEF -> this.grief(now);
				case RETREAT -> this.retreat();
			};
		}

		private Status gather(long now) {
			if (this.leader) {
				if (now - this.raid.since > GATHER_TICKS || this.musterReady()) {
					this.raid.enter(Phase.TRAVEL, now);
					this.think("off to raid " + this.raid.whose + "'s base");
					return Status.RUNNING;
				}

				return this.begin(new LookAroundTask(this.drifter));
			}

			DrifterEntity leader = this.leaderEntity();
			if (leader != null && this.drifter.distanceTo(leader) > 5.0) {
				return this.begin(new NavigateTask(this.drifter, leader.position(), 3.0, "go to " + leader.getName().getString()));
			}

			return this.begin(new LookAroundTask(this.drifter));
		}

		private boolean musterReady() {
			DrifterGroups.Group gang = DrifterGroups.byId(this.raid.gang);
			if (gang == null || this.drifter.getServer() == null) {
				return true;
			}

			for (UUID id : gang.members) {
				DrifterEntity member = DrifterWars.loaded(this.drifter.getServer(), id);
				if (member != null && member != this.drifter && member.blockPosition().closerThan(this.raid.home, MUSTER_RANGE)
					&& member.distanceTo(this.drifter) > 8.0) {
					return false;
				}
			}

			return true;
		}

		private Status travel(long now) {
			if (this.drifter.blockPosition().closerThan(this.raid.target, AT_BASE)) {
				if (this.raid.phase == Phase.TRAVEL) {
					ChatLines.say(this.drifter, ChatLines.Kind.WAR_CRY);
					this.think("at " + this.raid.whose + "'s base: wreck it!");
					this.raid.enter(Phase.GRIEF, now);
				}

				return Status.RUNNING;
			}

			if (++this.walks > 40) {
				return this.fail("can't get to " + this.raid.whose + "'s base");
			}

			return this.begin(leg(this.drifter, this.raid.target, "on the way to raid " + this.raid.whose + "'s base"));
		}

		private Status grief(long now) {
			if (now - this.raid.since > GRIEF_TICKS) {
				if (this.leader) {
					this.raid.enter(Phase.RETREAT, now);
					this.think("done wrecking " + this.raid.whose + "'s base: back to the outpost");
				}

				return this.retreat();
			}

			if (!this.drifter.blockPosition().closerThan(this.raid.target, AT_BASE + 8)) {
				return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(this.raid.target), 4.0, "into " + this.raid.whose + "'s base"));
			}

			Level level = this.drifter.level();
			List<BlockPos> beds = this.beds(level);

			// 1. The doors, to get in.
			BlockPos door = this.find(level, beds, state -> state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock);
			if (door != null) {
				return this.at(door, () -> this.digging(door), "break the door");
			}

			// 2. The chests (the loot activity finds and empties them; it waits if their owner's watching).
			MemoryEntry chest = this.drifter.getMemory().nearest(MemoryKind.STORAGE, level.dimension().location(), Vec3.atCenterOf(this.raid.target), 16.0,
				e -> !this.drifter.getDrifterBrain().looted.contains(e.blockPos()) && !this.failed.contains(e.blockPos())
					&& !level.getBlockState(e.blockPos()).is(Blocks.ENDER_CHEST) && beds.stream().noneMatch(b -> b.closerThan(e.blockPos(), NEAR_BED)));
			if (chest != null) {
				this.working = chest.blockPos();
				return this.begin(new LootChestActivity(this.drifter, chest.blockPos()));
			}

			// 3. Fire in the woodwork.
			if (this.fires < MAX_FIRES && this.drifter.getInventory().count(stack -> stack.is(Items.FLINT_AND_STEEL)) > 0) {
				// On the ground right by a wooden wall (or on top of the wood itself): it catches from there.
				BlockPos wood = this.find(level, beds, state -> state.ignitedByLava() && !state.canBeReplaced(), true);
				BlockPos ground = wood != null ? wood : this.fireSpot(level, beds);
				if (ground != null) {
					return this.at(ground, () -> {
						this.fires++;
						this.working = ground;
						this.think("setting fire to " + this.raid.whose + "'s base");
						return this.begin(new SequenceTask(this.drifter, "set fire to the base", List.of(
							new SelectItemTask(this.drifter, stack -> stack.is(Items.FLINT_AND_STEEL), "flint and steel"),
							new UseOnBlockTask(this.drifter, ground, Direction.UP, "light it"))));
					}, "set fire to the base");
				}
			}

			// 4. Smash things: torches first (the dark lets mobs in), then crops, glass, stations, then the walls.
			if (this.breaks < MAX_BREAKS) {
				for (java.util.function.Predicate<BlockState> kind : List.<java.util.function.Predicate<BlockState>>of(
					state -> state.is(BlockTags.WALL_POST_OVERRIDE) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN),
					state -> state.getBlock() instanceof CropBlock,
					state -> state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(net.minecraft.tags.BlockTags.IMPERMEABLE),
					state -> state.is(Blocks.CRAFTING_TABLE) || state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock,
					state -> !state.canBeReplaced())) {
					BlockPos block = this.find(level, beds, kind);
					if (block != null) {
						return this.at(block, () -> {
							this.breaks++;
							return this.digging(block);
						}, "wreck the base");
					}
				}
			}

			return this.begin(new LookAroundTask(this.drifter));
		}

		private Status retreat() {
			if (this.drifter.blockPosition().closerThan(this.raid.home, 12.0)) {
				if (this.leader) {
					RAIDS.remove(this.raid.gang);
					this.think("back from raiding " + this.raid.whose + "'s base");
				}

				return Status.SUCCESS;
			}

			if (++this.walks > 80) {
				return Status.SUCCESS;
			}

			return this.begin(leg(this.drifter, this.raid.home, "head back to the outpost"));
		}

		private Status digging(BlockPos pos) {
			this.working = pos;
			return this.begin(this.dig(pos));
		}

		/** Do it from here if it can reach, else walk over first. */
		private Status at(BlockPos pos, java.util.function.Supplier<Status> work, String what) {
			if (Reach.canReach(this.drifter, pos)) {
				return work.get();
			}

			BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, pos, Set.of(pos));
			if (spot == null || this.tries.merge(pos, 1, Integer::sum) > 3) {
				this.failed.add(pos);
				return Status.RUNNING;
			}

			this.working = null;
			return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(spot), 0.5, "walk over to " + what));
		}

		/** Solid ground with open air on it right beside a wooden part of the base (a fire there spreads into the wood). */
		@Nullable
		private BlockPos fireSpot(Level level, List<BlockPos> beds) {
			BlockPos wood = this.find(level, beds, state -> state.ignitedByLava() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES), false);
			if (wood == null) {
				return null;
			}

			var terrain = this.drifter.getSenses().terrain();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos air = wood.relative(side);
				BlockPos floor = air.below();
				if (level.getBlockState(air).isAir() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && terrain.isKnown(floor)
					&& !this.failed.contains(floor)) {
					return floor.immutable();
				}
			}

			this.failed.add(wood);
			return null;
		}

		/** Beds round the base (it never touches anything near one). */
		private List<BlockPos> beds(Level level) {
			List<BlockPos> beds = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(this.raid.target.offset(-14, -6, -14), this.raid.target.offset(14, 6, 14))) {
				if (level.getBlockState(pos).getBlock() instanceof BedBlock) {
					beds.add(pos.immutable());
				}
			}

			return beds;
		}

		/**
		 * The nearest block of the base of this kind it has seen: a player's placed block, or (another Drifter's base)
		 * anything built rather than natural; never a bed or anything by one, never an ender chest or a chest.
		 */
		@Nullable
		private BlockPos find(Level level, List<BlockPos> beds, java.util.function.Predicate<BlockState> kind) {
			return this.find(level, beds, kind, false);
		}

		/** The same, only with open air on top of it (somewhere to light a fire). */
		@Nullable
		private BlockPos find(Level level, List<BlockPos> beds, java.util.function.Predicate<BlockState> kind, boolean airAbove) {
			var terrain = this.drifter.getSenses().terrain();
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			BlockPos spawn = level.getSharedSpawnPos();
			int protection = this.drifter.getServer() == null ? 0 : this.drifter.getServer().getSpawnProtectionRadius();
			for (BlockPos pos : BlockPos.betweenClosed(this.raid.target.offset(-12, -5, -12), this.raid.target.offset(12, 6, 12))) {
				if (this.failed.contains(pos) || !terrain.isKnown(pos)) {
					continue;
				}

				BlockState state = level.getBlockState(pos);
				if (state.isAir() || !kind.test(state) || airAbove && !level.getBlockState(pos.above()).isAir() || state.getBlock() instanceof BedBlock || state.is(Blocks.ENDER_CHEST)
					|| com.dinglebob.drifter.brain.Home.isStorageBlock(state) || state.getDestroySpeed(level, pos) < 0.0F) {
					continue;
				}

				boolean ours = PlayerPlacedBlocks.isPlayerPlaced(level, pos) || !BuildPlanActivity.natural(state) && !state.is(BlockTags.LEAVES);
				if (!ours || beds.stream().anyMatch(b -> b.closerThan(pos, NEAR_BED))
					|| level.dimension() == Level.OVERWORLD && Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= protection) {
					continue;
				}

				double distance = pos.distSqr(this.drifter.blockPosition());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}

			return best;
		}

		@Nullable
		private DrifterEntity leaderEntity() {
			DrifterGroups.Group gang = DrifterGroups.byId(this.raid.gang);
			return gang == null || this.drifter.getServer() == null ? null : DrifterWars.loaded(this.drifter.getServer(), gang.leader);
		}
	}

	/** A stretch of up to 32 blocks toward somewhere far (the way players cross country, a bit at a time). */
	static Task leg(DrifterEntity drifter, BlockPos to, String what) {
		Vec3 from = drifter.position();
		Vec3 toward = Vec3.atBottomCenterOf(to).subtract(from);
		double distance = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
		if (distance <= 32.0) {
			return new NavigateTask(drifter, Vec3.atBottomCenterOf(to), 3.0, what);
		}

		Vec3 step = from.add(toward.x / distance * 32.0, 0.0, toward.z / distance * 32.0);
		BlockPos ground = drifter.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(step));
		return new NavigateTask(drifter, Vec3.atBottomCenterOf(ground), 4.0, what);
	}
}
