package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.BarrelBlock;

/**
 * Its home (DESIGN.md sections 9 stage 4, 10.18 and 11): where it settled, the chests it keeps there, and what it put
 * in them. Its record of the contents is its own knowledge: updated every time it looks inside one of its chests, so
 * if someone takes things it finds out when it next opens the chest (and plans again).
 * Saved with the brain.
 */
public final class Home {
	/** Things in its chests count as owned for planning while it's within this distance of home. */
	public static final double STORAGE_RANGE = 96.0;

	public final BlockPos center;
	public final ResourceLocation dimension;
	public final long settledAt;
	/** Its starter hut (section 11 tier 3): which side the door is on (null until it decides), and whether it's finished. */
	@Nullable
	public net.minecraft.core.Direction hutDoor;
	public boolean hutBuilt;
	/** A village house it moved into instead of building a hut (user's request, session 2): the head of its bed there. */
	@Nullable
	public BlockPos houseBed;

	/** Its bed in the village house, if it's still there. */
	@Nullable
	public BlockPos houseBed(net.minecraft.world.level.Level level) {
		return this.houseBed != null && level.getBlockState(this.houseBed).getBlock() instanceof net.minecraft.world.level.block.BedBlock
			? this.houseBed : null;
	}
	/** Its mine (section 10.3): the bottom of the staircase it dug from home, which way it went, and trips made from it. */
	@Nullable
	public BlockPos mineBottom;
	@Nullable
	public net.minecraft.core.Direction mineDirection;
	public int mineTrips;

	/** Its chests, in the order it placed them, with what it last saw in each. */
	private final Map<BlockPos, Map<Item, Integer>> chests = new LinkedHashMap<>();

	public Home(BlockPos center, ResourceLocation dimension, long settledAt) {
		this.center = center.immutable();
		this.dimension = dimension;
		this.settledAt = settledAt;
	}

	public List<BlockPos> chests() {
		return new ArrayList<>(this.chests.keySet());
	}

	public boolean hasChest() {
		return !this.chests.isEmpty();
	}

	public void addChest(BlockPos pos) {
		this.chests.putIfAbsent(pos.immutable(), new HashMap<>());
	}

	public void removeChest(BlockPos pos) {
		this.chests.remove(pos);
	}

	public boolean isChest(BlockPos pos) {
		return this.chests.containsKey(pos);
	}

	/** It looked inside one of its chests: this is what's in it now. */
	public void record(BlockPos pos, Container container) {
		Map<Item, Integer> contents = this.chests.get(pos);
		if (contents == null) {
			return;
		}

		contents.clear();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
	}

	/** What it believes is stored, all chests together. */
	public Map<Item, Integer> stored() {
		Map<Item, Integer> total = new HashMap<>();
		this.chests.values().forEach(contents -> contents.forEach((item, count) -> total.merge(item, count, Integer::sum)));
		return total;
	}

	public int stored(Item item) {
		return this.stored().getOrDefault(item, 0);
	}

	/** The chest it believes holds the most of this item, or null. */
	@Nullable
	public BlockPos chestWith(Item item) {
		BlockPos best = null;
		int bestCount = 0;
		for (Map.Entry<BlockPos, Map<Item, Integer>> chest : this.chests.entrySet()) {
			int count = chest.getValue().getOrDefault(item, 0);
			if (count > bestCount) {
				best = chest.getKey();
				bestCount = count;
			}
		}

		return best;
	}

	/** Roughly how full its chests are (0 to 1), by stacks of 64 against 27 slots each. */
	public double fullness() {
		if (this.chests.isEmpty()) {
			return 1.0;
		}

		int slotsUsed = 0;
		for (Map<Item, Integer> contents : this.chests.values()) {
			for (Map.Entry<Item, Integer> entry : contents.entrySet()) {
				slotsUsed += (int)Math.ceil(entry.getValue() / (double)new ItemStack(entry.getKey()).getMaxStackSize());
			}
		}

		return slotsUsed / (27.0 * this.chests.size());
	}

	/** In this dimension and close enough that its storage counts for planning. */
	public boolean near(DrifterEntity drifter, double range) {
		return drifter.level().dimension().location().equals(this.dimension) && drifter.blockPosition().distSqr(this.center) <= range * range;
	}

	/** Its chests that are really still chests (it checks the ones it can see; others it trusts). */
	public void forgetMissingChests(DrifterEntity drifter) {
		this.chests.keySet().removeIf(pos -> drifter.getSenses().terrain().isKnown(pos)
			&& !(drifter.level().getBlockState(pos).getBlock() instanceof ChestBlock || drifter.level().getBlockState(pos).getBlock() instanceof BarrelBlock));
	}

	public static boolean isStorageBlock(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.TRAPPED_CHEST);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("Center", NbtUtils.writeBlockPos(this.center));
		tag.putString("Dimension", this.dimension.toString());
		tag.putLong("SettledAt", this.settledAt);
		if (this.hutDoor != null) {
			tag.putString("HutDoor", this.hutDoor.getName());
		}

		tag.putBoolean("HutBuilt", this.hutBuilt);
		if (this.houseBed != null) {
			tag.put("HouseBed", NbtUtils.writeBlockPos(this.houseBed));
		}

		if (this.mineBottom != null && this.mineDirection != null) {
			tag.put("MineBottom", NbtUtils.writeBlockPos(this.mineBottom));
			tag.putString("MineDirection", this.mineDirection.getName());
		}

		tag.putInt("MineTrips", this.mineTrips);
		ListTag chests = new ListTag();
		this.chests.forEach((pos, contents) -> {
			CompoundTag chest = new CompoundTag();
			chest.put("Pos", NbtUtils.writeBlockPos(pos));
			CompoundTag items = new CompoundTag();
			contents.forEach((item, count) -> items.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), count));
			chest.put("Items", items);
			chests.add(chest);
		});
		tag.put("Chests", chests);
		return tag;
	}

	@Nullable
	public static Home load(CompoundTag tag) {
		BlockPos center = NbtUtils.readBlockPos(tag, "Center").orElse(null);
		ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
		if (center == null || dimension == null) {
			return null;
		}

		Home home = new Home(center, dimension, tag.getLong("SettledAt"));
		home.hutDoor = net.minecraft.core.Direction.byName(tag.getString("HutDoor"));
		home.hutBuilt = tag.getBoolean("HutBuilt");
		home.houseBed = NbtUtils.readBlockPos(tag, "HouseBed").orElse(null);
		home.mineBottom = NbtUtils.readBlockPos(tag, "MineBottom").orElse(null);
		home.mineDirection = net.minecraft.core.Direction.byName(tag.getString("MineDirection"));
		home.mineTrips = tag.getInt("MineTrips");
		ListTag chests = tag.getList("Chests", Tag.TAG_COMPOUND);
		for (int i = 0; i < chests.size(); i++) {
			CompoundTag chest = chests.getCompound(i);
			BlockPos pos = NbtUtils.readBlockPos(chest, "Pos").orElse(null);
			if (pos == null) {
				continue;
			}

			Map<Item, Integer> contents = new HashMap<>();
			CompoundTag items = chest.getCompound("Items");
			for (String key : items.getAllKeys()) {
				ResourceLocation id = ResourceLocation.tryParse(key);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					contents.put(BuiltInRegistries.ITEM.get(id), items.getInt(key));
				}
			}

			home.chests.put(pos, contents);
		}

		return home;
	}
}
