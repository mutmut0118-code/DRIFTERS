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
		return List.of(new JoinRaid());
	}

	/** How close to the leader a member must be to come along (members off elsewhere stay out of it). */
	private static final double MUSTER_RANGE = 96.0;
	/** Close enough to the rival base for the war cry and the attack. */
	private static final double AT_BASE = 24.0;
	/** Chests it goes for round the rival base. */
	private static final double LOOT_RANGE = 20.0;

	/** Its group's raid, if it's one of the raiders (near enough to the leader when it set off, or already on it). */
	@Nullable
	static DrifterWars.Raid raidFor(DrifterEntity drifter) {
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
			return 20 * 60 * 4;
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

			return switch (this.raid.phase) {
				case GATHER -> this.gather();
				case TRAVEL -> this.travel();
				case ATTACK -> this.attack();
				case RETREAT -> this.retreat();
			};
		}

		private Status gather() {
			DrifterEntity leader = this.leaderEntity();
			if (this.leader || leader == null) {
				// Everyone close: off they go (or the leader stops waiting: see DrifterWars).
				if (this.musterReady()) {
					this.raid.enter(DrifterWars.Phase.TRAVEL, this.now());
					this.think("everyone's here: off to raid " + DrifterWars.name(this.raid.defenders));
					return Status.RUNNING;
				}

				return this.begin(new LookAroundTask(this.drifter));
			}

			if (this.drifter.distanceTo(leader) > 5.0) {
				return this.begin(new NavigateTask(this.drifter, leader.position(), 3.0, "go to " + leader.getName().getString()));
			}

			return this.begin(new LookAroundTask(this.drifter));
		}

		/** Every member that came along is within a few blocks of the leader. */
		private boolean musterReady() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			if (group == null || this.drifter.getServer() == null) {
				return true;
			}

			for (java.util.UUID id : group.members) {
				DrifterEntity member = DrifterWars.loaded(this.drifter.getServer(), id);
				if (member != null && member != this.drifter && member.blockPosition().closerThan(this.raid.home, MUSTER_RANGE)
					&& member.distanceTo(this.drifter) > 8.0) {
					return false;
				}
			}

			return true;
		}

		private Status travel() {
			if (this.drifter.blockPosition().closerThan(this.raid.target, AT_BASE)) {
				if (!this.raid.warCry) {
					this.raid.warCry = true;
					ChatLines.say(this.drifter, ChatLines.Kind.WAR_CRY);
					this.think("at " + DrifterWars.name(this.raid.defenders) + "'s base: attack!");
				}

				this.raid.enter(DrifterWars.Phase.ATTACK, this.now());
				return Status.RUNNING;
			}

			return this.begin(this.leg(this.raid.target, "on the way to raid " + DrifterWars.name(this.raid.defenders)));
		}

		private Status attack() {
			BlockPos chest = this.chestToLoot();
			if (chest != null) {
				return this.begin(new LootChestActivity(this.drifter, chest));
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
					DrifterWars.endRaid(this.raid);
					this.think("back from the raid on " + DrifterWars.name(this.raid.defenders));
				}

				return Status.SUCCESS;
			}

			return this.begin(this.leg(this.raid.home, "head home from the raid"));
		}

		/** A stretch of up to 32 blocks toward somewhere far (the way players cross country, a bit at a time). */
		private Task leg(BlockPos to, String what) {
			Vec3 from = this.drifter.position();
			Vec3 toward = Vec3.atBottomCenterOf(to).subtract(from);
			double distance = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
			if (distance <= 32.0) {
				return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(to), 3.0, what);
			}

			Vec3 step = from.add(toward.x / distance * 32.0, 0.0, toward.z / distance * 32.0);
			BlockPos ground = this.drifter.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(step));
			return new NavigateTask(this.drifter, Vec3.atBottomCenterOf(ground), 4.0, what);
		}

		@Nullable
		private DrifterEntity leaderEntity() {
			DrifterGroups.Group group = DrifterGroups.byId(this.raid.attackers);
			return group == null || this.drifter.getServer() == null ? null : DrifterWars.loaded(this.drifter.getServer(), group.leader);
		}
	}
}
