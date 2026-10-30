package com.dinglebob.drifterciv;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A town made ready lit all over, as a player lights their base (user, 2026-10-23: "the town isnt fully lit up alot,
 * causing a bunch of mobs to spawn and killing half the town"): monsters spawn only where block light is 0, so every spot
 * one could spawn on, on the town's ground and on the floors inside its houses, gets some. The light its lamps, torches and
 * lanterns give is worked out over the area (as the game spreads it: one less a block, less through blocks that dim it,
 * none through solid ones); where it's dark, a lamp post outdoors (not on a road or a field) or a torch on the wall
 * indoors, and the light worked out again round it, till nowhere's dark.
 */
final class TownLights {
	private TownLights() {
	}

	/** Light at least this everywhere (monsters spawn at 0). */
	private static final int ENOUGH = 1;
	/** A lantern's light, and a torch's. */
	private static final int LANTERN = 15;
	private static final int TORCH = 14;

	/**
	 * Lights the area; {@code houses}: their boxes (indoors); {@code town}: the town's own ground outdoors (not the woods
	 * round it); {@code noLamp}: columns a lamp post mustn't stand in; {@code lamps}: puts a lamp post up standing at a spot, and
	 * says where its light is (null: none fits). Lamp posts and torches added.
	 */
	static int[] light(ServerLevel level, BoundingBox area, List<BoundingBox> houses, BiPredicate<Integer, Integer> town,
		BiPredicate<Integer, Integer> noLamp, java.util.function.Function<BlockPos, BlockPos> lampPosts) {
		Light light = new Light(level, area);
		light.spreadAll();
		int lamps = 0;
		int torches = 0;
		for (int x = area.minX(); x <= area.maxX(); x++) {
			for (int z = area.minZ(); z <= area.maxZ(); z++) {
				// Outdoors: the top of the ground.
				BlockPos outdoors = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				boolean underHouse = false;
				for (BoundingBox house : houses) {
					underHouse |= house.isInside(outdoors.atY(house.minY()));
				}

				if (!underHouse && town.test(x, z) && area.isInside(outdoors) && spawnable(level, outdoors) && light.at(outdoors) < ENOUGH) {
					BlockPos lamp = lampSpot(level, outdoors, noLamp, houses);
					BlockPos lit = lamp == null ? null : lampPosts.apply(lamp);
					if (lit != null) {
						light.spreadFrom(lit, Math.max(TORCH, level.getBlockState(lit).getLightEmission()));
						lamps++;
					}
				}

				// Indoors: every floor in a house.
				for (BoundingBox house : houses) {
					if (!house.isInside(new BlockPos(x, house.minY(), z))) {
						continue;
					}

					for (int y = house.minY(); y <= house.maxY(); y++) {
						BlockPos pos = new BlockPos(x, y, z);
						if (!level.canSeeSky(pos) && spawnable(level, pos) && light.at(pos) < ENOUGH) {
							BlockPos torch = torch(level, pos);
							if (torch != null) {
								light.spreadFrom(torch, TORCH);
								torches++;
							}
						}
					}
				}
			}
		}

		return new int[] {lamps, torches};
	}

	/** A monster could spawn here: room for one, on a block they spawn on. */
	private static boolean spawnable(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState under = level.getBlockState(below);
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& under.isValidSpawn(level, below, EntityType.ZOMBIE) && under.isFaceSturdy(level, below, Direction.UP);
	}

	/** Where a lamp post for this dark spot goes: there, or the nearest ground round it off the roads and fields. */
	@org.jetbrains.annotations.Nullable
	private static BlockPos lampSpot(ServerLevel level, BlockPos dark, BiPredicate<Integer, Integer> noLamp, List<BoundingBox> houses) {
		for (BlockPos column : BlockPos.withinManhattan(dark.atY(0), 3, 0, 3)) {
			int x = column.getX();
			int z = column.getZ();
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			boolean byHouse = false;
			for (BoundingBox house : houses) {
				byHouse |= house.inflatedBy(1).isInside(pos.atY(house.minY()));
			}

			if (!byHouse && !noLamp.test(x, z) && spawnable(level, pos) && level.getBlockState(pos.above(2)).isAir()) {
				return pos;
			}
		}

		return null;
	}

	/** A torch for this dark floor: on a wall beside it at head height (a player's way), else standing on it. Where it went. */
	@org.jetbrains.annotations.Nullable
	private static BlockPos torch(ServerLevel level, BlockPos floor) {
		BlockPos head = floor.above();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side.getOpposite());
			if (level.getBlockState(head).isAir() && torch.canSurvive(level, head)) {
				level.setBlock(head, torch, Block.UPDATE_ALL);
				return head;
			}
		}

		if (Blocks.TORCH.defaultBlockState().canSurvive(level, floor)) {
			level.setBlock(floor, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
			return floor;
		}

		return null;
	}

	/** Block light over the area (and a margin: light from outside it reaches in), as the game spreads it. */
	private static final class Light {
		private final ServerLevel level;
		private final int x0;
		private final int y0;
		private final int z0;
		private final int sx;
		private final int sy;
		private final int sz;
		private final byte[] values;

		Light(ServerLevel level, BoundingBox area) {
			this.level = level;
			this.x0 = area.minX() - LANTERN;
			this.y0 = area.minY() - LANTERN;
			this.z0 = area.minZ() - LANTERN;
			this.sx = area.getXSpan() + 2 * LANTERN;
			this.sy = area.getYSpan() + 2 * LANTERN;
			this.sz = area.getZSpan() + 2 * LANTERN;
			this.values = new byte[this.sx * this.sy * this.sz];
		}

		private int index(int x, int y, int z) {
			int dx = x - this.x0;
			int dy = y - this.y0;
			int dz = z - this.z0;
			return dx < 0 || dy < 0 || dz < 0 || dx >= this.sx || dy >= this.sy || dz >= this.sz ? -1 : (dx * this.sy + dy) * this.sz + dz;
		}

		int at(BlockPos pos) {
			int i = this.index(pos.getX(), pos.getY(), pos.getZ());
			return i < 0 ? 0 : this.values[i];
		}

		/** From every block that gives light in the area. */
		void spreadAll() {
			for (BlockPos pos : BlockPos.betweenClosed(this.x0, this.y0, this.z0, this.x0 + this.sx - 1, this.y0 + this.sy - 1, this.z0 + this.sz - 1)) {
				int emits = this.level.getBlockState(pos).getLightEmission();
				if (emits > 0) {
					this.spreadFrom(pos.immutable(), emits);
				}
			}
		}

		void spreadFrom(BlockPos source, int strength) {
			int start = this.index(source.getX(), source.getY(), source.getZ());
			if (start < 0 || this.values[start] >= strength) {
				return;
			}

			this.values[start] = (byte)strength;
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			queue.add(source);
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				int here = this.values[this.index(pos.getX(), pos.getY(), pos.getZ())];
				for (Direction side : Direction.values()) {
					BlockPos next = pos.relative(side);
					int i = this.index(next.getX(), next.getY(), next.getZ());
					if (i < 0) {
						continue;
					}

					BlockState state = this.level.getBlockState(next);
					int block = state.getLightBlock(this.level, next);
					if (block >= 15) {
						continue;
					}

					int there = here - Math.max(1, block);
					if (there > this.values[i]) {
						this.values[i] = (byte)there;
						queue.add(next);
					}
				}
			}
		}
	}
}
