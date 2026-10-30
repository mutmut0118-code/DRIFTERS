package com.dinglebob.drifter.brain.activity;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Brain;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.hands.Reach;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Loots a chest it has seen that belongs to a structure (DESIGN.md section 10.11 and "Opportunistic jumps": a ruined
 * portal's chest, a shipwreck's, a temple's, a village's), never one a player or a Drifter put down (that would be
 * griefing, off by default). Takes what isn't junk, once per chest.
 */
public class LootChestActivity extends Activity {
	/** How far it goes for a chest it has seen. */
	public static final double RANGE = 48.0;

	private final BlockPos chest;
	private int walks;

	public LootChestActivity(DrifterEntity drifter, BlockPos chest) {
		super(drifter);
		this.chest = chest;
	}

	@Override
	public String describe() {
		return "loot the chest at " + this.chest.toShortString();
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 90;
	}

	@Override
	protected Status next() {
		Brain brain = this.drifter.getDrifterBrain();
		if (brain.looted.contains(this.chest)) {
			return Status.SUCCESS;
		}

		if (com.dinglebob.drifter.hands.Containers.get(this.drifter.level(), this.chest) == null) {
			brain.looted.add(this.chest);
			return Status.SUCCESS;
		}

		if (Reach.canReach(this.drifter, this.chest)) {
			brain.looted.add(this.chest);
			String victim = com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(this.drifter.level(), this.chest)
				? raidable(this.drifter, this.chest) : null;
			if (com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(this.drifter.level(), this.chest) && victim == null) {
				// Someone turned up (or the grudge passed) on the way: it leaves it.
				brain.looted.remove(this.chest);
				return this.fail("not now: someone's about");
			}

			this.think(victim == null ? "looting the chest at " + this.chest.toShortString()
				: "raiding " + (victim.equals("a player") ? "a player's" : victim + "'s") + " chest at " + this.chest.toShortString());
			return this.begin(new ContainerTask(this.drifter, this.chest, ContainerTask.Mode.TAKE,
				stack -> !com.dinglebob.drifter.brain.Storage.isJunk(this.drifter, stack), "loot", 64 * 27, null));
		}

		BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, this.chest, Set.of());
		if (this.walks++ > 2) {
			brain.looted.add(this.chest);
			return this.fail("can't get to the chest");
		}

		if (this.walks == 1) {
			this.think("spotted a chest at " + this.chest.toShortString() + ": going to loot it");
		}

		Vec3 target = spot != null ? Vec3.atBottomCenterOf(spot) : Vec3.atCenterOf(this.chest);
		return this.begin(new NavigateTask(this.drifter, target, spot != null ? 0.5 : 2.5, "walk to the chest"));
	}

	/**
	 * The nearest chest it has seen that it could loot: a structure's (not placed by a player or a Drifter, not one of its
	 * own), not looted yet, within {@link #RANGE}. Null if none.
	 */
	@Nullable
	public static BlockPos target(DrifterEntity drifter) {
		Brain brain = drifter.getDrifterBrain();
		Home home = brain.home();
		var dimension = drifter.level().dimension().location();
		MemoryEntry entry = drifter.getMemory().nearest(MemoryKind.STORAGE, dimension, drifter.position(), RANGE, e -> {
			BlockPos pos = e.blockPos();
			return !e.label.equals(com.dinglebob.drifter.perception.Senses.CHEST_CART) && !brain.looted.contains(pos)
				&& (home == null || !home.chests().contains(pos))
				&& !drifterBase(drifter, pos)
				&& (!com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(drifter.level(), pos) || raidable(drifter, pos) != null);
		});
		return entry == null ? null : entry.blockPos();
	}

	/**
	 * A player's chest it would raid (section 14 griefing; user's choice: on by default, by personality): a Bandit raids
	 * any; others only the base of a player they hold a real grudge against. Never an ender chest, never inside the
	 * server's spawn protection, never right by a bed, and only when it sees nobody near (it waits for the base to be
	 * empty). Returns who it's raiding ("a player" for a Bandit's find), or null if it wouldn't.
	 */
	@Nullable
	public static String raidable(DrifterEntity drifter, BlockPos pos) {
		var level = drifter.level();
		if (!com.dinglebob.drifter.DrifterConfig.raiding() || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.ENDER_CHEST)
			|| level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return null;
		}

		var server = drifter.getServer();
		BlockPos spawn = level.getSharedSpawnPos();
		if (server != null && level.dimension() == net.minecraft.world.level.Level.OVERWORLD
			&& Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) <= server.getSpawnProtectionRadius()) {
			return null;
		}

		// Someone in sight near it: not now.
		for (var awareness : drifter.getSenses().tracked()) {
			if (awareness.entity instanceof net.minecraft.world.entity.player.Player player && awareness.identified && awareness.visibleNow
				&& com.dinglebob.drifter.social.SocialWatcher.isRealPlayer(player) && player.blockPosition().closerThan(pos, 24.0)) {
				return null;
			}
		}

		String victim = null;
		for (com.dinglebob.drifter.social.Relationship r : drifter.getSocial().all()) {
			// Never right by their bed (no bed ambushes, no griefing the spot they sleep).
			if (r.knownBase != null && r.knownBase.closerThan(pos, NEAR_BED)) {
				return null;
			}

			if (victim == null && r.knownBase != null && r.knownBase.closerThan(pos, 32.0) && r.wronged
				&& r.grudgeWeight() >= com.dinglebob.drifter.social.Mode.VENGEFUL_GRUDGE && r.trust < com.dinglebob.drifter.social.Mode.HOSTILE_TRUST) {
				victim = r.name;
			}
		}

		if (victim != null) {
			return victim;
		}

		return drifter.getSocial().disposition() == com.dinglebob.drifter.social.Disposition.BANDIT ? "a player" : null;
	}

	/** How close to a known bed it never touches anything. */
	private static final double NEAR_BED = 5.0;

	/** Part of another Drifter's base (its chests, or anything round its home): not loot. */
	private static boolean drifterBase(DrifterEntity self, BlockPos pos) {
		// In a house a Drifter built (on its floor, or one of the house's own blocks): a neighbour's, its household's, someone
		// away from home; not loot (found in town tests: townspeople "looting" the chests in the other houses of their town).
		var level = self.level();
		if (com.dinglebob.drifter.world.PlayerPlacedBlocks.isDrifterBuilt(level, pos) || com.dinglebob.drifter.world.PlayerPlacedBlocks.isDrifterBuilt(level, pos.below())) {
			return true;
		}

		for (DrifterEntity other : self.level().getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(160.0))) {
			Home home = other.getDrifterBrain().home();
			if (other != self && home != null && (home.chests().contains(pos) || home.center.closerThan(pos, BASE_RADIUS))) {
				return true;
			}
		}

		return false;
	}

	/** How far round a Drifter's home its base reaches. */
	private static final double BASE_RADIUS = 24.0;
}
