package com.dinglebob.drifter.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * A Nether portal frame it builds (DESIGN.md section 19): the ground block its left column stands on, and which way the
 * frame runs. Cells go {@code along} 0 to 3 across and {@code up} 0 (the ground row) to 4 (the top row); the inside is
 * along 1 to 2, up 1 to 3.
 */
public record PortalFrame(BlockPos origin, Direction.Axis axis) {
	public BlockPos cell(int along, int up) {
		return this.axis == Direction.Axis.X ? this.origin.offset(along, up, 0) : this.origin.offset(0, up, along);
	}

	/** A cell moved {@code out} blocks off the frame's plane (to the front or, negative, the back). */
	public BlockPos front(int along, int up, int out) {
		BlockPos cell = this.cell(along, up);
		return this.axis == Direction.Axis.X ? cell.offset(0, 0, out) : cell.offset(out, 0, 0);
	}

	public BlockPos inside() {
		return this.cell(1, 1);
	}

	/** Built and lit: portal blocks inside. */
	public boolean lit(Level level) {
		return level.getBlockState(this.inside()).is(Blocks.NETHER_PORTAL);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("Origin", this.origin.asLong());
		tag.putString("Axis", this.axis.getName());
		return tag;
	}

	public static PortalFrame load(CompoundTag tag) {
		Direction.Axis axis = Direction.Axis.byName(tag.getString("Axis"));
		return new PortalFrame(BlockPos.of(tag.getLong("Origin")), axis == null ? Direction.Axis.X : axis);
	}
}
