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
			this.think("looting the chest at " + this.chest.toShortString());
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
				&& !com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(drifter.level(), pos) && !drifterBase(drifter, pos);
		});
		return entry == null ? null : entry.blockPos();
	}

	/** Part of another Drifter's base (its chests, or anything round its home): not loot. */
	private static boolean drifterBase(DrifterEntity self, BlockPos pos) {
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
