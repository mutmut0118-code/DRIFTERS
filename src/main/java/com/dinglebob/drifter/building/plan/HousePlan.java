package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A house plan (user's decision, 2026-10-16: hand-designed plans with variety, and the user's own via structure blocks):
 * every block of a build as it stands in its structure file, relative to its corner. Where its materials come from (wood
 * and stone swapped for what the Drifter has round it) is {@link HousePalette}'s job; this is the shape.
 */
public final class HousePlan {
	/** One block of the plan: where (from the plan's corner) and what. */
	public record Cell(BlockPos pos, BlockState state) {
	}

	public final String id;
	/** Where it came from: "mod", "world" (a structure block save) or "personal" (config/drifter/houses). */
	public final String source;
	public final Vec3i size;
	public final List<Cell> cells;

	private HousePlan(String id, String source, Vec3i size, List<Cell> cells) {
		this.id = id;
		this.source = source;
		this.size = size;
		this.cells = List.copyOf(cells);
	}

	/** A plan the generator made (milestone 15): already unfurnished, cells from its corner. */
	public static HousePlan generated(String id, Vec3i size, List<Cell> cells) {
		return new HousePlan(id, "generated", size, cells);
	}

	/** The plan as a structure file's tag (for pictures of generated plans: `/drifter house export`). */
	public CompoundTag toStructure() {
		CompoundTag tag = new CompoundTag();
		ListTag sizeTag = new ListTag();
		sizeTag.add(net.minecraft.nbt.IntTag.valueOf(this.size.getX()));
		sizeTag.add(net.minecraft.nbt.IntTag.valueOf(this.size.getY()));
		sizeTag.add(net.minecraft.nbt.IntTag.valueOf(this.size.getZ()));
		tag.put("size", sizeTag);
		List<BlockState> palette = new ArrayList<>();
		ListTag blocks = new ListTag();
		for (Cell cell : this.cells) {
			int index = palette.indexOf(cell.state());
			if (index < 0) {
				index = palette.size();
				palette.add(cell.state());
			}

			CompoundTag block = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(net.minecraft.nbt.IntTag.valueOf(cell.pos().getX()));
			pos.add(net.minecraft.nbt.IntTag.valueOf(cell.pos().getY()));
			pos.add(net.minecraft.nbt.IntTag.valueOf(cell.pos().getZ()));
			block.put("pos", pos);
			block.putInt("state", index);
			blocks.add(block);
		}

		ListTag paletteTag = new ListTag();
		palette.forEach(state -> paletteTag.add(NbtUtils.writeBlockState(state)));
		tag.put("palette", paletteTag);
		tag.put("blocks", blocks);
		tag.put("entities", new ListTag());
		tag.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return tag;
	}

	/**
	 * From a structure file's tag (what StructureTemplate#save writes): its first palette and its blocks, leaving out air
	 * and structure voids (the plan leaves those cells as they are).
	 */
	public static HousePlan fromStructure(String id, String source, CompoundTag tag, HolderGetter<Block> blocks) {
		ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
		Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
		ListTag paletteTag = tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND)
			: tag.getList("palettes", Tag.TAG_LIST).getList(0);
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(i)));
		}

		List<Cell> cells = new ArrayList<>();
		ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blockList.size(); i++) {
			CompoundTag block = blockList.getCompound(i);
			ListTag pos = block.getList("pos", Tag.TAG_INT);
			int index = block.getInt("state");
			BlockState state = index >= 0 && index < palette.size() ? palette.get(index) : Blocks.AIR.defaultBlockState();
			if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK) || furniture(state)) {
				continue;
			}

			cells.add(new Cell(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state));
		}

		return new HousePlan(id, source, size, cells);
	}

	/** Furniture and work blocks the Drifter brings itself. */
	private static final java.util.Set<Block> FURNITURE = java.util.Set.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
		Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
		Blocks.ENCHANTING_TABLE, Blocks.BREWING_STAND, Blocks.COMPOSTER, Blocks.LECTERN, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE,
		Blocks.FLETCHING_TABLE, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.JUKEBOX, Blocks.NOTE_BLOCK, Blocks.BELL,
		Blocks.DECORATED_POT, Blocks.CHISELED_BOOKSHELF, Blocks.RESPAWN_ANCHOR, Blocks.BEACON);

	/**
	 * Plans are unfurnished (user's rule, 2026-10-16: "it's up for them to place beds"): beds, chests, work blocks,
	 * cauldrons, flower pots, carpets and shulker boxes in a build are left out; what goes in is the Drifter's own.
	 */
	public static boolean furniture(BlockState state) {
		return FURNITURE.contains(state.getBlock()) || state.is(net.minecraft.tags.BlockTags.BEDS) || state.is(net.minecraft.tags.BlockTags.SHULKER_BOXES)
			|| state.is(net.minecraft.tags.BlockTags.CAULDRONS) || state.is(net.minecraft.tags.BlockTags.FLOWER_POTS)
			|| state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS);
	}

	/** The items its blocks take, as a player would need them (a door counts once for its two halves). */
	public Map<Item, Integer> materials(List<Cell> cells) {
		Map<Item, Integer> needs = new LinkedHashMap<>();
		for (Cell cell : cells) {
			BlockState state = cell.state();
			if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
				== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
				continue;
			}

			if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
				== net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
				continue;
			}

			Item item = state.getBlock().asItem();
			int count = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
				== net.minecraft.world.level.block.state.properties.SlabType.DOUBLE ? 2 : 1;
			if (item != net.minecraft.world.item.Items.AIR) {
				needs.merge(item, count, Integer::sum);
			}
		}

		return needs;
	}

	public int blockCount() {
		return this.cells.size();
	}
}
