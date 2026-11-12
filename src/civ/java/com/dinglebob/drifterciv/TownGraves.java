package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A town's graves (A10 part 2; the user, 2026-11-11: "a grave in the town" for anyone gone for good, "so there's something
 * to stand in front of"): a plain graveyard on open ground by the town's edge, a stone for each of its people gone for
 * good from this version on (in front of a player or away), with a sign naming them and what they died of. Put up when
 * the ground's loaded, the day after at the earliest. Things a player could put there by hand: a mossy stone, a sign
 * on it, turned earth before it.
 */
final class TownGraves {
	/** A grave: whose, when, of what; where its stone stands (null: not put up yet). */
	record Grave(String name, long day, String cause, @Nullable BlockPos stone) {
	}

	/** The yard's first row holds this many; rows go on away from the town. */
	private static final int ROW = 4;
	/** A yard holds this many stones at most (the oldest stay; more go unmarked). */
	private static final int MOST = 40;

	private TownGraves() {
	}

	static void add(Town town, String name, long day, String cause) {
		if (town.graves.stream().noneMatch(grave -> grave.name.equals(name) && grave.day == day)) {
			town.graves.add(new Grave(name, day, cause, null));
		}
	}

	static boolean waiting(Town town) {
		return town.graves.stream().anyMatch(grave -> grave.stone == null);
	}

	/** Stones put up for those still without one, where the yard's ground is loaded. True if any was. */
	static boolean dig(ServerLevel level, Town town) {
		if (!waiting(town)) {
			return false;
		}

		long today = Chronicle.day(level.getServer());
		if (town.yard == null) {
			if (!level.isLoaded(town.center)) {
				return false;
			}

			town.yard = yard(level, town);
			if (town.yard == null) {
				return false;
			}

			DrifterCivilizations.LOGGER.info("[graves] {}: its graveyard at {}", town.name, town.yard.toShortString());
		}

		// Rows away from the town: along the yard's long side, then out.
		Direction out = Direction.getNearest(town.yard.getX() - town.center.getX(), 0, town.yard.getZ() - town.center.getZ());
		Direction along = out.getClockWise();
		boolean any = false;
		int slot = (int)town.graves.stream().filter(grave -> grave.stone != null).count();
		java.util.Set<Long> roads = new java.util.HashSet<>();
		for (long column : town.roadColumns) {
			roads.add(column);
		}

		for (int i = 0; i < town.graves.size() && slot < MOST; i++) {
			Grave grave = town.graves.get(i);
			if (grave.stone != null || grave.day >= today) {
				continue;
			}

			// The next free place in the rows that'll take a stone (skipping any that's turned bad: a tree grown, water).
			BlockPos at = null;
			while (at == null && slot < MOST) {
				BlockPos column = town.yard.relative(along, (slot % ROW) * 2).relative(out, (slot / ROW) * 3);
				slot++;
				if (!level.isLoaded(column) || !level.isLoaded(column.offset(-2, 0, -2)) || !level.isLoaded(column.offset(2, 0, 2))) {
					return any;
				}

				// (Rows past the first two weren't looked at when the yard was found: no stone on a road, a house, a field.)
				if (roads.contains(BlockPos.asLong(column.getX(), 0, column.getZ())) || TownBuilder.built(level, column.getX(), column.getZ())
					|| !inNoPlot(town, column.getX(), column.getZ())) {
					continue;
				}

				at = ground(level, column.getX(), column.getZ(), town.yard.getY());
			}

			if (at == null) {
				break;
			}

			final BlockPos stoneAt = at;
			if (town.graves.stream().anyMatch(other -> stoneAt.equals(other.stone))) {
				continue;
			}

			stone(level, at, out.getOpposite(), grave);
			town.graves.set(i, new Grave(grave.name, grave.day, grave.cause, at));
			any = true;
			DrifterCivilizations.LOGGER.info("[graves] {}: a stone for {} at {}", town.name, grave.name, at.toShortString());
		}

		// (Its yard full: those without a stone go unmarked, their names in the chronicle.)
		if (slot >= MOST) {
			any |= town.graves.removeIf(grave -> grave.stone == null && grave.day < today);
		}

		return any;
	}

	/** The stone, its sign facing the town, turned earth before it. */
	private static void stone(ServerLevel level, BlockPos at, Direction facing, Grave grave) {
		// (Under trees: the leaves over it cut back, so it stands in the open.)
		for (BlockPos leaf : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 7, 2))) {
			if (level.getBlockState(leaf).is(net.minecraft.tags.BlockTags.LEAVES)) {
				level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
			}
		}

		level.setBlock(at, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3);
		BlockPos front = at.relative(facing);
		BlockPos earth = front.below();
		if (level.getBlockState(earth).is(net.minecraft.tags.BlockTags.DIRT) && level.getBlockState(front).canBeReplaced()) {
			level.setBlock(front, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(earth, Blocks.COARSE_DIRT.defaultBlockState(), 3);
		}

		if (!level.getBlockState(front).canBeReplaced()) {
			return;
		}

		BlockState sign = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
		level.setBlock(front, sign, 3);
		if (level.getBlockEntity(front) instanceof SignBlockEntity entity) {
			List<String> words = wrap(grave.cause);
			SignText text = new SignText().setMessage(0, Component.literal("Here lies")).setMessage(1, Component.literal(grave.name))
				.setMessage(2, Component.literal(words.isEmpty() ? "" : words.get(0))).setMessage(3, Component.literal(words.size() < 2 ? "" : words.get(1)));
			entity.setText(text, true);
			entity.setChanged();
			level.sendBlockUpdated(front, sign, sign, 3);
		}
	}

	/** Its cause on two lines of a sign (what doesn't fit: left off). */
	private static List<String> wrap(String cause) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : cause.split(" ")) {
			if (line.length() > 0 && line.length() + 1 + word.length() > 15) {
				lines.add(line.toString());
				line = new StringBuilder();
			}

			line.append(line.length() > 0 ? " " : "").append(word);
		}

		if (line.length() > 0) {
			lines.add(line.toString());
		}

		return lines;
	}

	/**
	 * Open, level ground for a yard by the town's edge: outside its houses, inside its land, nothing built on it, no road,
	 * no water; the corner nearest the town of a patch four stones wide and two rows deep. Null if there's none loaded.
	 */
	@Nullable
	private static BlockPos yard(ServerLevel level, Town town) {
		java.util.Set<Long> roads = new java.util.HashSet<>();
		for (long column : town.roadColumns) {
			roads.add(column);
		}

		BlockPos c = town.center;
		int far = town.land() - 24;
		// (Under open sky if there's such a patch; else under trees.)
		for (int pass = 0; pass < 2; pass++) {
		boolean open = pass == 0;
		for (int ring = far + 4; ring <= far + 30; ring += 2) {
			for (int k = 0; k < 48; k++) {
				double angle = 2 * Math.PI * k / 48;
				int x = c.getX() + (int)Math.round(Math.cos(angle) * ring);
				int z = c.getZ() + (int)Math.round(Math.sin(angle) * ring);
				if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
					continue;
				}

				Direction out = Direction.getNearest(x - c.getX(), 0, z - c.getZ());
				Direction along = out.getClockWise();
				int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				boolean good = true;
				for (int a = -1; a <= ROW * 2 && good; a++) {
					for (int o = -2; o <= 4 && good; o++) {
						int px = x + along.getStepX() * a + out.getStepX() * o;
						int pz = z + along.getStepZ() * a + out.getStepZ() * o;
						good = level.hasChunkAt(new BlockPos(px, 0, pz)) && !roads.contains(BlockPos.asLong(px, 0, pz)) && !TownBuilder.built(level, px, pz)
							&& inNoPlot(town, px, pz) && ground(level, px, pz, height) != null
							&& (!open || level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz) == level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz));
					}
				}

				if (good) {
					return new BlockPos(x, height, z);
				}
			}
		}
		}

		return null;
	}

	private static boolean inNoPlot(Town town, int x, int z) {
		for (Town.Plot plot : town.plots) {
			if (x >= plot.origin.getX() - 2 && x <= plot.origin.getX() + plot.sizeX + 1 && z >= plot.origin.getZ() - 2 && z <= plot.origin.getZ() + plot.sizeZ + 1) {
				return false;
			}
		}

		for (BlockPos field : town.fields) {
			if (Math.abs(field.getX() - x) <= 1 && Math.abs(field.getZ() - z) <= 1) {
				return false;
			}
		}

		return true;
	}

	/** Where a stone would stand in this column (on sturdy dry ground, open above, within one of the yard's height), or null. */
	@Nullable
	private static BlockPos ground(ServerLevel level, int x, int z, int height) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos under = new BlockPos(x, top - 1, z);
		if (Math.abs(top - height) > 1 || !level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) || !level.getFluidState(under).isEmpty()
			|| level.getBlockState(under).is(Blocks.MOSSY_COBBLESTONE) || level.getBlockState(under).is(net.minecraft.tags.BlockTags.LEAVES)
			|| !level.getBlockState(under.above()).canBeReplaced() || !level.getFluidState(under.above()).isEmpty()) {
			return null;
		}

		return under.above();
	}

	static List<String> status(Town town) {
		List<String> lines = new ArrayList<>();
		if (town.yard != null) {
			lines.add("  graveyard at " + town.yard.toShortString());
		}

		town.graves.forEach(grave -> lines.add("  grave: " + grave.name + " (day " + grave.day + ", " + grave.cause + ")"
			+ (grave.stone == null ? ", no stone yet" : " at " + grave.stone.toShortString())));
		return lines;
	}

	static ListTag save(Town town) {
		ListTag list = new ListTag();
		for (Grave grave : town.graves) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Name", grave.name);
			tag.putLong("Day", grave.day);
			tag.putString("Cause", grave.cause);
			if (grave.stone != null) {
				tag.put("Stone", NbtUtils.writeBlockPos(grave.stone));
			}

			list.add(tag);
		}

		return list;
	}

	static void load(Town town, ListTag list) {
		for (Tag t : list) {
			CompoundTag tag = (CompoundTag)t;
			town.graves.add(new Grave(tag.getString("Name"), tag.getLong("Day"), tag.getString("Cause"), NbtUtils.readBlockPos(tag, "Stone").orElse(null)));
		}
	}
}
