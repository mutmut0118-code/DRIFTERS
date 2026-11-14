package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Paper towns: one planned place's sketch, as plain numbers (docs/superpowers/specs/2026-11-14-lod-towns-design.md). Its
 * shape, the lines of its main roads, boxes for its houses, its fields and its lamps, worked out from the land as the
 * generator says it will be. For drawing into a far-view mod only: nothing a Drifter decides reads it.
 *
 * @param plaza {x0, z0, x1, z1}
 * @param streets each {width, x, z, x, z, ...}: a point every few blocks along its middle
 * @param fields each {x0, z0, x1, z1}
 * @param lamps each {x, z}
 */
record Sketch(UUID place, int x, int z, int y0, String style, String shape, double[] headings, long seed, int reach, boolean ruin, int[] plaza,
	List<int[]> streets, List<Box> boxes, List<int[]> fields, List<int[]> lamps) {
	/** The blocks a sketch's houses are of: a box names its walls' and its roof's by their place here. Add to the end only. */
	static final List<Block> PALETTE = List.of(Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.ACACIA_PLANKS,
		Blocks.JUNGLE_PLANKS, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE);

	static byte paletteOf(Block block) {
		int at = PALETTE.indexOf(block);
		return (byte)(at < 0 ? 0 : at);
	}

	/**
	 * A house: its footprint, its ground's height, how high its walls and its roof are (a ruin's: no roof, low walls), what
	 * they're of, which way its ridge runs, and whether its windows are lit at night.
	 */
	record Box(int x0, int z0, int x1, int z1, int base, int wall, int roof, byte wallBlock, byte roofBlock, boolean ridgeAlongX, boolean lit) {
		boolean contains(int x, int z) {
			return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
		}
	}

	private static final int BOX_INTS = 11;

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Place", this.place);
		tag.putInt("X", this.x);
		tag.putInt("Z", this.z);
		tag.putInt("Y", this.y0);
		tag.putString("Style", this.style);
		tag.putString("Shape", this.shape);
		long[] ways = new long[this.headings.length];
		for (int i = 0; i < ways.length; i++) {
			ways[i] = Double.doubleToLongBits(this.headings[i]);
		}

		tag.putLongArray("Headings", ways);
		tag.putLong("Seed", this.seed);
		tag.putInt("Reach", this.reach);
		tag.putBoolean("Ruin", this.ruin);
		tag.putIntArray("Plaza", this.plaza);
		tag.putIntArray("Streets", pack(this.streets));
		int[] boxes = new int[this.boxes.size() * BOX_INTS];
		for (int i = 0; i < this.boxes.size(); i++) {
			Box box = this.boxes.get(i);
			int[] one = {box.x0, box.z0, box.x1, box.z1, box.base, box.wall, box.roof, box.wallBlock, box.roofBlock, box.ridgeAlongX ? 1 : 0, box.lit ? 1 : 0};
			System.arraycopy(one, 0, boxes, i * BOX_INTS, BOX_INTS);
		}

		tag.putIntArray("Boxes", boxes);
		tag.putIntArray("Fields", pack(this.fields));
		tag.putIntArray("Lamps", pack(this.lamps));
		return tag;
	}

	static Sketch load(CompoundTag tag) {
		long[] ways = tag.getLongArray("Headings");
		double[] headings = new double[ways.length];
		for (int i = 0; i < ways.length; i++) {
			headings[i] = Double.longBitsToDouble(ways[i]);
		}

		int[] packed = tag.getIntArray("Boxes");
		List<Box> boxes = new ArrayList<>();
		for (int i = 0; i + BOX_INTS <= packed.length; i += BOX_INTS) {
			boxes.add(new Box(packed[i], packed[i + 1], packed[i + 2], packed[i + 3], packed[i + 4], packed[i + 5], packed[i + 6], (byte)packed[i + 7],
				(byte)packed[i + 8], packed[i + 9] == 1, packed[i + 10] == 1));
		}

		return new Sketch(tag.getUUID("Place"), tag.getInt("X"), tag.getInt("Z"), tag.getInt("Y"), tag.getString("Style"), tag.getString("Shape"), headings,
			tag.getLong("Seed"), tag.getInt("Reach"), tag.getBoolean("Ruin"), tag.getIntArray("Plaza"), unpack(tag.getIntArray("Streets")), boxes,
			unpack(tag.getIntArray("Fields")), unpack(tag.getIntArray("Lamps")));
	}

	/** Lists of int arrays as one: each one's length, then its numbers. */
	static int[] pack(List<int[]> lists) {
		int size = 0;
		for (int[] one : lists) {
			size += 1 + one.length;
		}

		int[] packed = new int[size];
		int at = 0;
		for (int[] one : lists) {
			packed[at++] = one.length;
			System.arraycopy(one, 0, packed, at, one.length);
			at += one.length;
		}

		return packed;
	}

	static List<int[]> unpack(int[] packed) {
		List<int[]> lists = new ArrayList<>();
		int at = 0;
		while (at < packed.length && packed[at] >= 0 && at + 1 + packed[at] <= packed.length) {
			int[] one = new int[packed[at]];
			System.arraycopy(packed, at + 1, one, 0, one.length);
			lists.add(one);
			at += 1 + one.length;
		}

		return lists;
	}
}
