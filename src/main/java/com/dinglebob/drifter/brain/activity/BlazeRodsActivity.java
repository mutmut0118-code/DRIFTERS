package com.dinglebob.drifter.brain.activity;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.Threats;
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
	/** How close to the spawner it waits: blazes appear within 4 blocks of it, so from here it reaches them with the sword as
	 * they come, the way players clear a spawner. */
	private static final double SPAWNER_WAIT_DISTANCE = 4.0;
	private static final int MAX_WALKS = 6;

	/** Walks toward each kind of place, counted apart (found in testing: walks to the fortress used them all up, so on
	 * finding the spawner it gave up on it at once). */
	private int walks;
	private int spawnerWalks;
	private int blazeWalks;
	private boolean headingForBlazes;

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
		return step instanceof NavigateTask && (this.walks > MAX_WALKS || this.spawnerWalks > MAX_WALKS);
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

		// A blaze in sight: it takes it on now, as it came for (found in testing: it stood "waiting for blazes" while one
		// shot at it, and only fought back once it had been hit).
		if (blazeNear(this.drifter) && Threats.assess(this.drifter).decision().fights()) {
			return this.begin(new CombatActivity(this.drifter));
		}

		MemoryEntry spawner = spawner(this.drifter);
		if (spawner != null) {
			BlockPos at = spawner.blockPos();
			if (this.drifter.position().distanceTo(Vec3.atCenterOf(at)) > SPAWNER_WAIT_DISTANCE + 2.0) {
				BlockPos spot = KnownBlocks.standingSpotFor(this.drifter, at, java.util.Set.of());
				if (this.spawnerWalks++ > MAX_WALKS) {
					return this.fail("can't get near the blaze spawner");
				}

				if (this.spawnerWalks == 1) {
					this.think("going to the blaze spawner at " + at.toShortString());
				}

				// (Close enough by the same measure as the check above: found in testing, "arrived" by the walk's measure but not by
				// this one, it walked eight times on the spot and gave up.)
				return this.begin(new NavigateTask(this.drifter, spot != null ? Vec3.atBottomCenterOf(spot) : Vec3.atCenterOf(at), SPAWNER_WAIT_DISTANCE - 1.5,
					"walk to the blaze spawner"));
			}

			// Near it: wait for blazes (the fight goal takes over when one comes), looking round.
			return this.begin(new WaitTask(this.drifter, "wait for blazes by the spawner", () -> blazeNear(this.drifter), 20 * 15));
		}

		MemoryEntry fortress = fortress(this.drifter);
		if (fortress != null) {
			// A blaze seen or heard lately: blazes stay round their spawner, so it goes that way (the fight takes over when
			// they come at it, and the spawner turns up on the way).
			MemoryEntry blaze = this.drifter.getMemory().nearest(MemoryKind.MOB, this.drifter.level().dimension().location(), this.drifter.position(),
				64.0, e -> e.label.equals("minecraft:blaze"));
			if (blaze != null && this.drifter.position().distanceTo(blaze.pos) > 10.0 && this.blazeWalks++ <= MAX_WALKS) {
				if (!this.headingForBlazes) {
					this.headingForBlazes = true;
					this.think("blazes over there: heading that way");
				}

				return this.begin(new NavigateTask(this.drifter, blaze.pos, 8.0, "walk toward the blazes"));
			}

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

		return this.begin(new ExploreActivity(this.drifter, SEARCH, this.searchToward(), drifter -> fortress(drifter) != null));
	}

	private static final String SEARCH = "looking for a Nether fortress";
	/** Search legs along one line before it moves across and comes back the other way. */
	private static final int LEGS_PER_LINE = 8;
	private static final double LEG = 48.0;
	private static final double LINE_SPACING = 96.0;

	/**
	 * Where the next search leg heads. One who knows the game (Knowledge) knows fortresses stretch north to south and
	 * searches in long north-south lines, moving across after a while; others just wander.
	 */
	@Nullable
	private Vec3 searchToward() {
		if (this.drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.KNOWLEDGE) < 0.4) {
			return null;
		}

		int legs = this.drifter.getDrifterBrain().timesExplored(SEARCH);
		var trip = this.drifter.getDrifterBrain().netherTrip();
		int first = trip != null && (trip.startedAt / 20) % 2 == 0 ? 1 : -1;
		int line = legs / LEGS_PER_LINE;
		int direction = line % 2 == 0 ? first : -first;
		double across = legs % LEGS_PER_LINE == 0 && legs > 0 ? LINE_SPACING : 0.0;
		return this.drifter.position().add(across, 0.0, direction * LEG);
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
