package com.dinglebob.drifter.building;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.building.plan.HousePalette;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Signs on a house's chests saying what's in them (the user's furnishing guidebook, "label every chest": a sign on the
 * chest's face, one word, readable from across the room). What a chest holds most of, by kind; a chest of this and that
 * is "misc". A Drifter writes it from what it knows is in there (its memory of the chest), and puts it right when that
 * changes.
 */
public final class ChestLabels {
	private ChestLabels() {
	}

	/** The share of a chest's items one kind needs to be its label; less, and it's "misc". */
	private static final double MOSTLY = 0.5;

	/** The label for a chest holding these (item: count), or null if it's empty. */
	@Nullable
	public static String of(Map<Item, Integer> contents) {
		Map<String, Integer> kinds = new LinkedHashMap<>();
		int total = 0;
		for (Map.Entry<Item, Integer> entry : contents.entrySet()) {
			if (entry.getValue() <= 0) {
				continue;
			}

			kinds.merge(kind(new ItemStack(entry.getKey())), entry.getValue(), Integer::sum);
			total += entry.getValue();
		}

		if (total == 0) {
			return null;
		}

		String best = null;
		for (Map.Entry<String, Integer> entry : kinds.entrySet()) {
			if (best == null || entry.getValue() > kinds.get(best)) {
				best = entry.getKey();
			}
		}

		return kinds.get(best) >= total * MOSTLY ? best : "Misc";
	}

	/** What kind of thing it is, as a chest's label says it. */
	static String kind(ItemStack stack) {
		Item item = stack.getItem();
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Items.STICK) || stack.is(ItemTags.WOODEN_SLABS)
			|| stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.SAPLINGS)) {
			return "Wood";
		}

		if (item == Items.COAL || item == Items.CHARCOAL || item == Items.DIAMOND || item == Items.EMERALD || item == Items.LAPIS_LAZULI
			|| item == Items.REDSTONE || item == Items.QUARTZ || item == Items.AMETHYST_SHARD || path.startsWith("raw_") || path.endsWith("_ingot")
			|| path.endsWith("_nugget") || path.endsWith("_ore") || item == Items.NETHERITE_SCRAP) {
			return "Ores";
		}

		if (stack.has(DataComponents.FOOD)) {
			return "Food";
		}

		if (item instanceof TieredItem || item instanceof ArmorItem || item == Items.BOW || item == Items.CROSSBOW || item == Items.SHIELD
			|| item == Items.FISHING_ROD || item == Items.SHEARS || item == Items.FLINT_AND_STEEL || item == Items.ARROW) {
			return "Gear";
		}

		if (item == Items.STRING || item == Items.BONE || item == Items.BONE_MEAL || item == Items.GUNPOWDER || item == Items.ROTTEN_FLESH
			|| item == Items.SPIDER_EYE || item == Items.LEATHER || item == Items.FEATHER || item == Items.ENDER_PEARL || item == Items.SLIME_BALL
			|| path.endsWith("_wool") || item == Items.RABBIT_HIDE) {
			return "Mob drops";
		}

		if (path.endsWith("_seeds") || item == Items.WHEAT || item == Items.SUGAR_CANE || item == Items.BAMBOO || item == Items.CACTUS
			|| item == Items.PUMPKIN || item == Items.MELON) {
			return "Farm";
		}

		if (HousePalette.stoneOf(path) != null || item == Items.STONE || item == Items.DEEPSLATE || item == Items.DIORITE || item == Items.GRANITE
			|| item == Items.CALCITE || item == Items.SMOOTH_STONE || path.contains("brick")) {
			return "Stone";
		}

		if (item == Items.DIRT || item == Items.GRAVEL || item == Items.SAND || item == Items.RED_SAND || item == Items.COARSE_DIRT || item == Items.CLAY_BALL
			|| item == Items.FLINT || item == Items.GLASS) {
			return "Dirt & sand";
		}

		return "Misc";
	}

	/** A chest of its house to label (nothing in front of it yet) or relabel ({@code sign}: its sign says something else). */
	public record Job(BlockPos chest, BlockPos front, Direction facing, String text, boolean sign) {
	}

	/** Its house's chests that want a label, or a new one (from what it remembers is in them). */
	public static java.util.List<Job> wanted(DrifterEntity drifter, Home home) {
		java.util.List<Job> jobs = new java.util.ArrayList<>();
		Level level = drifter.level();
		if (!home.planDone) {
			return jobs;
		}

		for (BlockPos pos : home.chests()) {
			BlockState chest = level.getBlockState(pos);
			if (!home.inOwnBuilding(pos, 0) || !(chest.getBlock() instanceof ChestBlock)) {
				continue;
			}

			String text = of(home.contents(pos));
			Direction facing = chest.getValue(ChestBlock.FACING);
			BlockPos front = pos.relative(facing);
			BlockState there = level.getBlockState(front);
			if (text == null) {
				continue;
			}

			if (there.getBlock() instanceof WallSignBlock && there.getValue(WallSignBlock.FACING) == facing) {
				if (level.getBlockEntity(front) instanceof SignBlockEntity sign && !says(sign, text)) {
					jobs.add(new Job(pos, front, facing, text, true));
				}
			} else if (there.isAir()) {
				jobs.add(new Job(pos, front, facing, text, false));
			}
		}

		return jobs;
	}

	/** The sign it would make: of the wood it has or knows most of (oak if none). */
	public static Item signToMake(DrifterEntity drifter) {
		for (String wood : HousePalette.woodsAround(drifter)) {
			Item sign = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(wood + "_sign"));
			if (sign != Items.AIR) {
				return sign;
			}
		}

		return Items.OAK_SIGN;
	}

	/** A sign it carries, or null. */
	@Nullable
	public static Item carried(DrifterEntity drifter) {
		for (int slot = 0; slot < drifter.getInventory().getContainerSize(); slot++) {
			Item item = drifter.getInventory().getItem(slot).getItem();
			if (wallSign(item) != null) {
				return item;
			}
		}

		return null;
	}

	/** The wall sign its sign item makes ("oak_sign": "oak_wall_sign"), or null for anything else. */
	@Nullable
	public static Block wallSign(Item sign) {
		String path = BuiltInRegistries.ITEM.getKey(sign).getPath();
		if (!path.endsWith("_sign") || path.contains("hanging")) {
			return null;
		}

		Block wall = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path.substring(0, path.length() - "_sign".length()) + "_wall_sign"));
		return wall == Blocks.AIR ? null : wall;
	}

	/** The sign says this already. */
	public static boolean says(SignBlockEntity sign, String label) {
		return sign.getFrontText().getMessage(1, false).getString().equals(label);
	}

	/** Writes the label on its front, on the second line (the middle of the sign). */
	public static void write(SignBlockEntity sign, String label) {
		SignText text = new SignText();
		text = text.setMessage(1, Component.literal(label));
		sign.setText(text, true);
		sign.setChanged();
		if (sign.getLevel() != null) {
			sign.getLevel().sendBlockUpdated(sign.getBlockPos(), sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
		}
	}
}
