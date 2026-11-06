package com.dinglebob.drifter.compat;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Farmer's Delight (optional, CLAUDE.md soft dependencies; the user, 2026-10-31: "i want them to actually know how to cook
 * food using the mod farmers delight"): the kitchen's stove, cooking pot, cutting board, skillet and cabinets, used the way a
 * player uses them. Everything here goes by registry id, so it loads without the mod; what needs the mod's own classes (its
 * recipes, the pot's slots) is in {@code compat.farmersdelight.FdBridge}, only touched when the mod is there.
 */
public final class FarmersDelight {
	public static final String MOD_ID = "farmersdelight";
	/** Knives (Farmer's Delight's and other mods'): what cuts on the board. */
	public static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/knife"));
	private static final TagKey<Block> CABINETS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "cabinets"));
	private static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "heat_sources"));
	private static final TagKey<Item> MEALS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "meals"));
	/** Bowls, bottles and pumpkins a pot's meal is served into. */
	private static final TagKey<Item> SERVING = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "serving_containers"));

	private static Boolean loaded;

	private FarmersDelight() {
	}

	/** The mod is installed. */
	public static boolean loaded() {
		Boolean known = loaded;
		if (known == null) {
			known = FabricLoader.getInstance().isModLoaded(MOD_ID);
			loaded = known;
		}

		return known;
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/** One of the mod's blocks, or null without the mod. */
	@Nullable
	public static Block block(String path) {
		if (!loaded()) {
			return null;
		}

		ResourceLocation id = id(path);
		return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
	}

	/** One of the mod's items, or null without the mod. */
	@Nullable
	public static Item item(String path) {
		if (!loaded()) {
			return null;
		}

		ResourceLocation id = id(path);
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
	}

	private static boolean is(BlockState state, String path) {
		return loaded() && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id(path));
	}

	public static boolean isStove(BlockState state) {
		return is(state, "stove");
	}

	public static boolean isPot(BlockState state) {
		return is(state, "cooking_pot");
	}

	public static boolean isBoard(BlockState state) {
		return is(state, "cutting_board");
	}

	public static boolean isSkillet(BlockState state) {
		return is(state, "skillet");
	}

	/** A kitchen cabinet: storage, like a chest (it opens like a barrel). */
	public static boolean isCabinet(BlockState state) {
		return loaded() && state.is(CABINETS);
	}

	/** Any of the kitchen's things (furniture in a house's layout). */
	public static boolean isKitchen(BlockState state) {
		return isStove(state) || isPot(state) || isBoard(state) || isSkillet(state) || isCabinet(state);
	}

	public static boolean isKnife(ItemStack stack) {
		return loaded() && stack.is(KNIVES);
	}

	/** A meal from the mod's list (stews, soups, rice dishes...): the cooking's worth showing off. */
	public static boolean isMeal(ItemStack stack) {
		return loaded() && stack.is(MEALS);
	}

	public static boolean isServing(ItemStack stack) {
		return loaded() && (stack.is(SERVING) || stack.is(Items.BOWL));
	}

	/**
	 * The block under this pot gives heat now (FD's HeatableBlockEntity: a heat source, lit if it can be; a lit stove, a
	 * campfire, magma, lava). What the Drifter sees: flames or not.
	 */
	public static boolean heatedFrom(Level level, BlockPos pot) {
		BlockState below = level.getBlockState(pot.below());
		if (!below.is(HEAT_SOURCES) && !below.is(Blocks.CAMPFIRE) && !below.is(Blocks.SOUL_CAMPFIRE)) {
			return false;
		}

		return !below.hasProperty(BlockStateProperties.LIT) || below.getValue(BlockStateProperties.LIT);
	}

	/** A stove that's out (water, a shovel): flint and steel or a fire charge lights it again. */
	public static boolean unlitStove(BlockState state) {
		return isStove(state) && state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT);
	}

	// ---------------------------------------------------------------------------------------------
	// Recipes, read from the game's recipe manager like crafting and smelting (brain/Planner)
	// ---------------------------------------------------------------------------------------------

	/** A cooking pot recipe: up to six ingredients, one each per batch; the meal, what it's served in, the time it takes. */
	public record PotRecipe(List<Ingredient> inputs, ItemStack result, ItemStack container, int cookTicks) {
	}

	/** A cutting board recipe: what goes on the board, what it's cut with, what comes off it (the sure results). */
	public record CutRecipe(Ingredient input, Ingredient tool, List<ItemStack> results) {
	}

	public static List<PotRecipe> potRecipes(Level level) {
		return loaded() ? com.dinglebob.drifter.compat.farmersdelight.FdBridge.potRecipes(level) : List.of();
	}

	public static List<CutRecipe> cutRecipes(Level level) {
		return loaded() ? com.dinglebob.drifter.compat.farmersdelight.FdBridge.cutRecipes(level) : List.of();
	}

	/**
	 * The cooking pot here as a player sees its window: slots 0 to 5 the ingredients, 6 the meal cooking, 7 the bowls, 8 what's
	 * served. Null if it isn't a pot.
	 */
	@Nullable
	public static Container pot(Level level, BlockPos pos) {
		return loaded() ? com.dinglebob.drifter.compat.farmersdelight.FdBridge.pot(level, pos) : null;
	}

	/** What's on the cutting board here (empty if nothing, or not a board). */
	public static ItemStack onBoard(Level level, BlockPos pos) {
		return loaded() ? com.dinglebob.drifter.compat.farmersdelight.FdBridge.onBoard(level, pos) : ItemStack.EMPTY;
	}

	/** What's cooking in the skillet here (empty if nothing, or not a skillet). */
	public static ItemStack inSkillet(Level level, BlockPos pos) {
		return loaded() ? com.dinglebob.drifter.compat.farmersdelight.FdBridge.inSkillet(level, pos) : ItemStack.EMPTY;
	}

	public static final int POT_INPUTS = 6;
	public static final int POT_MEAL = 6;
	public static final int POT_CONTAINER = 7;
	public static final int POT_OUTPUT = 8;
}
