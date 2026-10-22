package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;

/**
 * A town (docs/CIVILIZATION.md part 1; the layout on the town plan the user agreed, 2026-10-21): its name, its group (its
 * people), where its plaza is, when it was founded, and its plots (a house each, its front to a road), its store's chests,
 * its well, fields and pen.
 */
public final class Town {
	/** One household's plot: where its house stands (corner, size once turned, turn, front), which plan, and whose it is. */
	public static final class Plot {
		public final BlockPos origin;
		public final int sizeX;
		public final int sizeZ;
		public final String planId;
		public final Rotation rotation;
		public final Direction front;
		@Nullable
		public UUID owner;
		/** Its house is up (a town founded by its people builds them a block at a time). */
		public boolean built;

		public Plot(BlockPos origin, int sizeX, int sizeZ, String planId, Rotation rotation, Direction front, @Nullable UUID owner) {
			this.origin = origin.immutable();
			this.sizeX = sizeX;
			this.sizeZ = sizeZ;
			this.planId = planId;
			this.rotation = rotation;
			this.front = front;
			this.owner = owner;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("Origin", this.origin.asLong());
			tag.putInt("SizeX", this.sizeX);
			tag.putInt("SizeZ", this.sizeZ);
			tag.putString("Plan", this.planId);
			tag.putString("Rotation", this.rotation.name());
			tag.putString("Front", this.front.getName());
			if (this.owner != null) {
				tag.putUUID("Owner", this.owner);
			}

			tag.putBoolean("Built", this.built);
			return tag;
		}

		static Plot load(CompoundTag tag) {
			Direction front = Direction.byName(tag.getString("Front"));
			Plot plot = new Plot(BlockPos.of(tag.getLong("Origin")), tag.getInt("SizeX"), tag.getInt("SizeZ"), tag.getString("Plan"),
				Rotation.valueOf(tag.getString("Rotation")), front == null ? Direction.SOUTH : front, tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
			// (Before 0.22.8 only made-ready towns had plots, every house up.)
			plot.built = !tag.contains("Built") || tag.getBoolean("Built");
			return plot;
		}
	}

	public final UUID id;
	public String name;
	@Nullable
	public UUID group;
	public final String dimension;
	public final BlockPos center;
	public final long foundedDay;
	/** Made at once for testing (`/drifterciv town spawn`), not founded by its people. */
	public final boolean spawned;
	public final List<Plot> plots = new ArrayList<>();
	public final List<BlockPos> store = new ArrayList<>();
	public final List<BlockPos> fields = new ArrayList<>();
	@Nullable
	public BlockPos well;
	@Nullable
	public BlockPos pen;

	public Town(UUID id, String name, @Nullable UUID group, String dimension, BlockPos center, long foundedDay, boolean spawned) {
		this.id = id;
		this.name = name;
		this.group = group;
		this.dimension = dimension;
		this.center = center.immutable();
		this.foundedDay = foundedDay;
		this.spawned = spawned;
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Id", this.id);
		tag.putString("Name", this.name);
		if (this.group != null) {
			tag.putUUID("Group", this.group);
		}

		tag.putString("Dimension", this.dimension);
		tag.putLong("Center", this.center.asLong());
		tag.putLong("FoundedDay", this.foundedDay);
		tag.putBoolean("Spawned", this.spawned);
		ListTag plots = new ListTag();
		this.plots.forEach(plot -> plots.add(plot.save()));
		tag.put("Plots", plots);
		tag.putLongArray("Store", this.store.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putLongArray("Fields", this.fields.stream().mapToLong(BlockPos::asLong).toArray());
		if (this.well != null) {
			tag.putLong("Well", this.well.asLong());
		}

		if (this.pen != null) {
			tag.putLong("Pen", this.pen.asLong());
		}

		return tag;
	}

	static Town load(CompoundTag tag) {
		Town town = new Town(tag.getUUID("Id"), tag.getString("Name"), tag.hasUUID("Group") ? tag.getUUID("Group") : null, tag.getString("Dimension"),
			BlockPos.of(tag.getLong("Center")), tag.getLong("FoundedDay"), tag.getBoolean("Spawned"));
		for (Tag t : tag.getList("Plots", Tag.TAG_COMPOUND)) {
			town.plots.add(Plot.load((CompoundTag)t));
		}

		for (long pos : tag.getLongArray("Store")) {
			town.store.add(BlockPos.of(pos));
		}

		for (long pos : tag.getLongArray("Fields")) {
			town.fields.add(BlockPos.of(pos));
		}

		town.well = tag.contains("Well") ? BlockPos.of(tag.getLong("Well")) : null;
		town.pen = tag.contains("Pen") ? BlockPos.of(tag.getLong("Pen")) : null;
		return town;
	}
}
