package com.dinglebob.drifter.building.plan;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A GrabCraft blueprint turned into a structure file (user's request, 2026-10-16: "send you a blueprint and you follow
 * that"; for the user's own worlds, never shipped with the mod). GrabCraft's page holds its blocks as layers of x and z,
 * each block with its old numeric id and data ("53_3": oak stairs facing north) in its texture name; the game's own table
 * for upgrading old worlds ({@link BlockStateData}) turns those into today's blocks, facings and halves included.
 */
public final class GrabCraftImport {
	private GrabCraftImport() {
	}

	/** Reads a GrabCraft render object (the page's block data, saved as JSON) into a structure tag. */
	public static CompoundTag read(Path json, HolderGetter<Block> blocks, boolean flipZ) throws IOException {
		JsonObject layers;
		try (Reader reader = Files.newBufferedReader(json)) {
			layers = JsonParser.parseReader(reader).getAsJsonObject();
		}

		record Raw(int x, int y, int z, BlockState state) {
		}

		List<Raw> raw = new ArrayList<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Map.Entry<String, JsonElement> layer : layers.entrySet()) {
			int y = Integer.parseInt(layer.getKey());
			for (Map.Entry<String, JsonElement> column : layer.getValue().getAsJsonObject().entrySet()) {
				int x = Integer.parseInt(column.getKey());
				for (Map.Entry<String, JsonElement> cell : column.getValue().getAsJsonObject().entrySet()) {
					int z = Integer.parseInt(cell.getKey());
					String texture = cell.getValue().getAsJsonObject().get("texture").getAsString().replace(".png", "");
					String[] parts = texture.split("_");
					int id = Integer.parseInt(parts[0]);
					int data = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
					BlockState state = upgrade(id, data, blocks);
					if (state.isAir()) {
						continue;
					}

					raw.add(new Raw(x, y, z, state));
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					minZ = Math.min(minZ, z);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
					maxZ = Math.max(maxZ, z);
				}
			}
		}

		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		ListTag blockList = new ListTag();
		for (Raw r : raw) {
			int z = flipZ ? maxZ - r.z() : r.z() - minZ;
			// GrabCraft counts z the other way from the game, but its blocks' facings are the game's own (found on the first
			// import: only with z turned round did its wall torches hang on walls and its roof stairs climb to the ridge).
			BlockState state = r.state();
			int index = palette.computeIfAbsent(state, s -> palette.size());
			CompoundTag block = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(r.x() - minX));
			pos.add(IntTag.valueOf(r.y() - minY));
			pos.add(IntTag.valueOf(z));
			block.put("pos", pos);
			block.putInt("state", index);
			blockList.add(block);
		}

		ListTag paletteTag = new ListTag();
		palette.keySet().forEach(state -> paletteTag.add(NbtUtils.writeBlockState(state)));
		CompoundTag structure = new CompoundTag();
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(maxX - minX + 1));
		size.add(IntTag.valueOf(maxY - minY + 1));
		size.add(IntTag.valueOf(maxZ - minZ + 1));
		structure.put("size", size);
		structure.put("palette", paletteTag);
		structure.put("blocks", blockList);
		structure.put("entities", new ListTag());
		structure.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return structure;
	}

	/** An old id and data value as today's block (what loading a 1.12 world does). */
	static BlockState upgrade(int id, int data, HolderGetter<Block> blocks) {
		var dynamic = BlockStateData.getTag((id << 4) | (data & 15));
		Tag tag = dynamic.convert(NbtOps.INSTANCE).getValue();
		return tag instanceof CompoundTag compound ? NbtUtils.readBlockState(blocks, compound) : Blocks.AIR.defaultBlockState();
	}
}
