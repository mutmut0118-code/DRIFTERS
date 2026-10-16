package com.dinglebob.drifter.brain.activity;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.NetherTrip;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.hands.Tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Arriving in the Nether (DESIGN.md section 19 "On arrival"): notes where the portal it came out of is (the way home),
 * walls it in with cobblestone against ghasts, leaving a door gap, and has a look round before choosing a direction.
 */
public class NetherArrivalActivity extends Activity {
	private int placed;

	public NetherArrivalActivity(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "arrive in the Nether";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 120;
	}

	@Override
	protected boolean countsAsFailure(com.dinglebob.drifter.executor.Task step) {
		// A wall block it can't place is skipped, not a failed arrival.
		return false;
	}

	@Override
	protected Status next() {
		NetherTrip trip = this.drifter.getDrifterBrain().netherTrip();
		if (trip == null) {
			return Status.SUCCESS;
		}

		Level level = this.drifter.level();
		if (trip.netherPortal == null) {
			BlockPos portal = portalNear(level, this.drifter.blockPosition());
			if (portal == null) {
				return this.fail("can't see the portal it came through");
			}

			trip.netherPortal = portal;
			trip.arrivedAt = level.getGameTime();
			trip.phase = NetherTrip.Phase.THERE;
			this.drifter.getMemory().remember(com.dinglebob.drifter.memory.MemoryKind.STRUCTURE, "minecraft:nether_portal", Vec3.atCenterOf(portal),
				level.dimension().location(), level.getGameTime(), com.dinglebob.drifter.memory.MemorySource.SEEN, 1.0F, null);
			this.think("in the Nether: the portal home is at " + portal.toShortString());
		}

		if (!trip.walledIn) {
			for (BlockPos wall : walls(level, trip.netherPortal)) {
				if (!level.getBlockState(wall).canBeReplaced() || this.skipped.contains(wall)) {
					continue;
				}

				if (this.drifter.getInventory().count(Tools.BUILDING_BLOCK) == 0) {
					break;
				}

				if (this.drifter.getEyePosition().distanceTo(Vec3.atCenterOf(wall)) > 4.0) {
					this.skipped.add(wall);
					continue;
				}

				this.skipped.add(wall);
				this.placed++;
				return this.begin(new PlaceBlockTask(this.drifter, wall, Tools.BUILDING_BLOCK, "a wall block round the portal"));
			}

			trip.walledIn = true;
			if (this.placed > 0) {
				this.think("walled the portal in (" + this.placed + " blocks), with a way out");
			}
		}

		if (!trip.lookedAround) {
			trip.lookedAround = true;
			return this.begin(new LookAroundTask(this.drifter));
		}

		return Status.SUCCESS;
	}

	private final java.util.Set<BlockPos> skipped = new java.util.HashSet<>();

	/** A portal block right round it (it has just come out of one): the bottom one of the nearest column. */
	@Nullable
	public static BlockPos portalNear(Level level, BlockPos feet) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -2, -3), feet.offset(3, 3, 3))) {
			if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
				double distance = pos.distSqr(feet);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}

		if (best == null) {
			return null;
		}

		while (level.getBlockState(best.below()).is(Blocks.NETHER_PORTAL)) {
			best = best.below();
		}

		return best;
	}

	/**
	 * The walls of a small room round the portal's exits, front and back: the cells at the ends and two out from the
	 * portal, two blocks high, plus a roof over the exits, minus a door gap in the front wall.
	 */
	static List<BlockPos> walls(Level level, BlockPos inside) {
		BlockState portal = level.getBlockState(inside);
		Direction.Axis axis = portal.hasProperty(NetherPortalBlock.AXIS) ? portal.getValue(NetherPortalBlock.AXIS) : Direction.Axis.X;
		// The bottom row of the portal, from one end to the other.
		BlockPos start = inside;
		Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		while (level.getBlockState(start.relative(along.getOpposite())).is(Blocks.NETHER_PORTAL)) {
			start = start.relative(along.getOpposite());
		}

		int width = 0;
		while (level.getBlockState(start.relative(along, width)).is(Blocks.NETHER_PORTAL)) {
			width++;
		}

		Direction front = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		List<BlockPos> walls = new ArrayList<>();
		for (Direction out : new Direction[] {front, front.getOpposite()}) {
			for (int up = 0; up <= 1; up++) {
				// The ends of the exit, one out.
				walls.add(start.relative(along, -1).relative(out).above(up));
				walls.add(start.relative(along, width).relative(out).above(up));
				// The far wall, two out, along the whole width and its ends.
				for (int a = -1; a <= width; a++) {
					BlockPos cell = start.relative(along, a).relative(out, 2).above(up);
					// The door: in the middle of the front wall.
					if (out == front && a == 0) {
						continue;
					}

					walls.add(cell);
				}
			}

			// A roof over the exit.
			for (int a = 0; a < width; a++) {
				walls.add(start.relative(along, a).relative(out).above(2));
			}
		}

		return walls;
	}
}
