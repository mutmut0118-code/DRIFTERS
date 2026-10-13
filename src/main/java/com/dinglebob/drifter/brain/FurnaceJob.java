package com.dinglebob.drifter.brain;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * A batch it left cooking in a furnace (DESIGN.md section 10.4: "load it, leave for a nearby chore, come back"): where,
 * what comes out, how many, and when it should be done (a furnace smelts one item per 10 s).
 */
public final class FurnaceJob {
	public final BlockPos furnace;
	public final Item output;
	public int count;
	public long readyAt;

	public FurnaceJob(BlockPos furnace, Item output, int count, long readyAt) {
		this.furnace = furnace.immutable();
		this.output = output;
		this.count = count;
		this.readyAt = readyAt;
	}

	public boolean ready(long now) {
		return now >= this.readyAt;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("Furnace", this.furnace.asLong());
		tag.putString("Output", BuiltInRegistries.ITEM.getKey(this.output).toString());
		tag.putInt("Count", this.count);
		tag.putLong("ReadyAt", this.readyAt);
		return tag;
	}

	@Nullable
	public static FurnaceJob load(CompoundTag tag) {
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("Output"));
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return null;
		}

		return new FurnaceJob(BlockPos.of(tag.getLong("Furnace")), BuiltInRegistries.ITEM.get(id), tag.getInt("Count"), tag.getLong("ReadyAt"));
	}
}
