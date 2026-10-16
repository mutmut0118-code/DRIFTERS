package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Blaze rods (DESIGN.md section 19 "Goals there", milestone 11 step 5), the way players get them: find a Nether fortress
 * (it notices one by its nether bricks, from afar), go there and look for its blaze spawner, then hold a spot near the
 * spawner. The blazes that come are fought by its usual fight goal; it picks up the rods they drop. Everything it knows
 * comes from what it has seen.
 */
public class BlazeRodsActivity extends Activity {
	/** Rods it goes for on a trip (a few eyes of ender and a brewing stand's worth, later). */
	public static final int WANTED = 6;
	/** How far it goes looking for a fortress it has seen. */
	public static final double FORTRESS_RANGE = 256.0;
	/** How close to the spawner it waits (blazes spawn round it, within a few blocks). */
	private static final double SPAWNER_WAIT_DISTANCE = 6.0;
	private static final int MAX_WALKS = 6;

	private int walks;

	public BlazeRodsActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "get blaze rods (" + rods(this.drifter) + "/" + WANTED + ")";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 600;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		// Exploring legs that don't find the fortress, walks cut short: it keeps at it until the timeout.
		return step instanceof NavigateTask && this.walks > MAX_WALKS;
	}

	@Override
	protected Status next() {
		if (rods(this.drifter) >= WANTED) {
			this.think("got " + rods(this.drifter) + " blaze rods");
			return Status.SUCCESS;
		}

		// Rods on the ground near it: picked up first.
		if (!this.drifter.level().getEntitiesOfClass(ItemEntity.class, this.drifter.getBoundingBox().inflate(8.0),
			item -> item.getItem().is(Items.BLAZE_ROD)).isEmpty()) {
			return this.begin(new CollectItemsTask(this.drifter, 8.0));
		}

		MemoryEntry spawner = spawner(this.drifter);
		if (spawner != null) {
			BlockPos at = spawner.blockPos();
			if (this.drifter.position().distanceTo(Vec3.atCenterOf(at)) > SPAWNER_WAIT_DISTANCE + 2.0) {
				BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, at, java.util.Set.of());
				if (this.walks++ > MAX_WALKS) {
					return this.fail("can't get near the blaze spawner");
				}

				if (this.walks == 1) {
					this.think("going to the blaze spawner at " + at.toShortString());
				}

				return this.begin(new NavigateTask(this.drifter, spot != null ? Vec3.atBottomCenterOf(spot) : Vec3.atCenterOf(at), SPAWNER_WAIT_DISTANCE,
					"walk to the blaze spawner"));
			}

			// Near it: wait for blazes (the fight goal takes over when one comes), looking round.
			return this.begin(new WaitTask(this.drifter, "wait for blazes by the spawner", () -> blazeNear(this.drifter), 20 * 15));
		}

		MemoryEntry fortress = fortress(this.drifter);
		if (fortress != null) {
			// At the fortress: look round inside it for the spawner.
			if (this.drifter.position().distanceTo(fortress.pos) < 12.0) {
				return this.begin(new ExploreActivity(this.drifter, "looking for the blaze spawner", null, drifter -> spawner(drifter) != null));
			}

			if (this.walks++ > MAX_WALKS) {
				return this.fail("can't get to the fortress");
			}

			if (this.walks == 1) {
				this.think("heading for the fortress it saw at " + fortress.blockPos().toShortString());
			}

			return this.begin(new NavigateTask(this.drifter, fortress.pos, 8.0, "walk to the fortress"));
		}

		if (this.ticksRunning() < 2) {
			this.think("looking for a Nether fortress");
		}

		return this.begin(new ExploreActivity(this.drifter, "looking for a Nether fortress", null, drifter -> fortress(drifter) != null));
	}

	/** Blaze rods it carries. */
	public static int rods(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.BLAZE_ROD));
	}

	/** A fortress it has seen in this dimension (nether bricks, noted as a landmark). */
	@Nullable
	public static MemoryEntry fortress(DrifterEntity drifter) {
		return drifter.getMemory().nearest(MemoryKind.LANDMARK, drifter.level().dimension().location(), drifter.position(), FORTRESS_RANGE,
			e -> e.label.equals("fortress"));
	}

	/** A spawner it has seen in the Nether (in a fortress, that's blazes). */
	@Nullable
	public static MemoryEntry spawner(DrifterEntity drifter) {
		if (drifter.level().dimension() != net.minecraft.world.level.Level.NETHER) {
			return null;
		}

		return drifter.getMemory().nearest(MemoryKind.STRUCTURE, drifter.level().dimension().location(), drifter.position(), FORTRESS_RANGE,
			e -> e.label.equals("minecraft:spawner") && drifter.level().getBlockState(e.blockPos()).is(net.minecraft.world.level.block.Blocks.SPAWNER));
	}

	/** A blaze it can see now. */
	private static boolean blazeNear(DrifterEntity drifter) {
		return drifter.getSenses().tracked().stream().anyMatch(a -> a.entity instanceof Blaze && a.visibleNow && a.entity.isAlive());
	}
}
