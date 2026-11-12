package com.dinglebob.drifter.trade;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * What Numismatic Overhaul's own villagers ask and pay, in bronze apiece (its villager_trades data, version 0.3.5; the
 * user, 2026-11-12: with that mod, a Drifter's stall follows the mod's own price where it has one, so bread stays
 * pennies and a diamond axe about a gold). {@link #SELL}: what one sells for (the cheapest of its villagers); {@link
 * #BUY}: what one is bought for (the best of them). Only read when that mod is here ({@link Money#COINS}); what isn't
 * listed is priced by its worth, an emerald's worth a thousand bronze.
 */
public final class CoinPrices {
	private CoinPrices() {
	}

	private static final Map<String, Double> SELL = sell();
	private static final Map<String, Double> BUY = buy();
	/** A stall never pays more for a thing than this share of what it sells it for (no buying and selling in a ring). */
	private static final double BUY_AT_MOST = 0.5;

	/** What a stall sells this for, in bronze apiece, by the mod's list; null: not listed (or no coins). */
	@Nullable
	public static Double sells(Item item) {
		return Money.COINS ? SELL.get(BuiltInRegistries.ITEM.getKey(item).toString()) : null;
	}

	/** What a stall pays for this, in bronze apiece, by the mod's list; null: not listed (or no coins). */
	@Nullable
	public static Double buys(Item item) {
		if (!Money.COINS) {
			return null;
		}

		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		Double pays = BUY.get(id);
		Double asks = SELL.get(id);
		return pays == null ? null : asks == null ? pays : Double.valueOf(Math.min(pays, asks * BUY_AT_MOST));
	}

	private static Map<String, Double> sell() {
		Map<String, Double> m = new HashMap<>();
		m.put("minecraft:acacia_sapling", 1600D);
		m.put("minecraft:allium", 120D);
		m.put("minecraft:apple", 87.5D);
		m.put("minecraft:arrow", 6.25D);
		m.put("minecraft:axolotl_bucket", 1500D);
		m.put("minecraft:azalea", 2000D);
		m.put("minecraft:azure_bluet", 120D);
		m.put("minecraft:beetroot_seeds", 60D);
		m.put("minecraft:bell", 3000D);
		m.put("minecraft:birch_sapling", 1600D);
		m.put("minecraft:black_dye", 40D);
		m.put("minecraft:blue_dye", 40D);
		m.put("minecraft:blue_ice", 2500D);
		m.put("minecraft:blue_orchid", 160D);
		m.put("minecraft:bookshelf", 800D);
		m.put("minecraft:bow", 2000D);
		m.put("minecraft:brain_coral_block", 849D);
		m.put("minecraft:bread", 18D);
		m.put("minecraft:brick", 8.25D);
		m.put("minecraft:brown_dye", 40D);
		m.put("minecraft:brown_mushroom", 120D);
		m.put("minecraft:bubble_coral_block", 849D);
		m.put("minecraft:bundle", 2999D);
		m.put("minecraft:cactus", 849D);
		m.put("minecraft:cake", 1500D);
		m.put("minecraft:campfire", 500D);
		m.put("minecraft:chainmail_boots", 1100D);
		m.put("minecraft:chainmail_chestplate", 1500D);
		m.put("minecraft:chainmail_helmet", 1200D);
		m.put("minecraft:chainmail_leggings", 1250D);
		m.put("minecraft:clock", 900D);
		m.put("minecraft:cod_bucket", 700D);
		m.put("minecraft:compass", 700D);
		m.put("minecraft:cooked_beef", 75D);
		m.put("minecraft:cooked_chicken", 66.67D);
		m.put("minecraft:cooked_mutton", 83.33D);
		m.put("minecraft:cooked_porkchop", 80D);
		m.put("minecraft:cooked_rabbit", 100D);
		m.put("minecraft:cooked_salmon", 75D);
		m.put("minecraft:cookie", 6D);
		m.put("minecraft:cornflower", 120D);
		m.put("minecraft:crossbow", 1200D);
		m.put("minecraft:cyan_dye", 40D);
		m.put("minecraft:dandelion", 120D);
		m.put("minecraft:dark_oak_sapling", 1600D);
		m.put("minecraft:diamond_axe", 10000D);
		m.put("minecraft:diamond_hoe", 5540D);
		m.put("minecraft:dripstone_block", 75D);
		m.put("minecraft:ender_pearl", 500D);
		m.put("minecraft:experience_bottle", 300D);
		m.put("minecraft:feather", 100D);
		m.put("minecraft:fern", 120D);
		m.put("minecraft:fire_coral_block", 849D);
		m.put("minecraft:fishing_rod", 800D);
		m.put("minecraft:flower_banner_pattern", 20000D);
		m.put("minecraft:flowering_azalea", 2400D);
		m.put("minecraft:glass", 64D);
		m.put("minecraft:glistering_melon_slice", 200D);
		m.put("minecraft:globe_banner_pattern", 20000D);
		m.put("minecraft:glow_item_frame", 400D);
		m.put("minecraft:glowstone", 4000D);
		m.put("minecraft:glowstone_dust", 300D);
		m.put("minecraft:golden_carrot", 200D);
		m.put("minecraft:gray_dye", 40D);
		m.put("minecraft:green_dye", 40D);
		m.put("minecraft:gunpowder", 200D);
		m.put("minecraft:horn_coral_block", 849D);
		m.put("minecraft:iron_axe", 1280D);
		m.put("minecraft:iron_boots", 2250D);
		m.put("minecraft:iron_chestplate", 3200D);
		m.put("minecraft:iron_helmet", 2500D);
		m.put("minecraft:iron_leggings", 2800D);
		m.put("minecraft:item_frame", 250D);
		m.put("minecraft:jungle_sapling", 1600D);
		m.put("minecraft:lantern", 175D);
		m.put("minecraft:lapis_lazuli", 200D);
		m.put("minecraft:leather", 200D);
		m.put("minecraft:leather_horse_armor", 800D);
		m.put("minecraft:light_blue_dye", 40D);
		m.put("minecraft:light_gray_dye", 40D);
		m.put("minecraft:lily_of_the_valley", 160D);
		m.put("minecraft:lily_pad", 100D);
		m.put("minecraft:lime_dye", 40D);
		m.put("minecraft:magenta_dye", 40D);
		m.put("minecraft:map", 500D);
		m.put("minecraft:melon_seeds", 60D);
		m.put("minecraft:mojang_banner_pattern", 100000D);
		m.put("minecraft:moss_block", 100D);
		m.put("minecraft:mutton", 40D);
		m.put("minecraft:name_tag", 400D);
		m.put("minecraft:nautilus_shell", 2000D);
		m.put("minecraft:oak_fence", 125D);
		m.put("minecraft:oak_sapling", 1600D);
		m.put("minecraft:orange_dye", 40D);
		m.put("minecraft:orange_tulip", 120D);
		m.put("minecraft:oxeye_daisy", 120D);
		m.put("minecraft:packed_ice", 500D);
		m.put("minecraft:painting", 250D);
		m.put("minecraft:pink_dye", 40D);
		m.put("minecraft:pink_tulip", 120D);
		m.put("minecraft:podzol", 200D);
		m.put("minecraft:pointed_dripstone", 100D);
		m.put("minecraft:polished_andesite", 75D);
		m.put("minecraft:polished_diorite", 75D);
		m.put("minecraft:polished_granite", 75D);
		m.put("minecraft:poppy", 120D);
		m.put("minecraft:pufferfish_bucket", 1500D);
		m.put("minecraft:pumpkin", 100D);
		m.put("minecraft:pumpkin_pie", 40D);
		m.put("minecraft:pumpkin_seeds", 60D);
		m.put("minecraft:purple_dye", 40D);
		m.put("minecraft:quartz_block", 550D);
		m.put("minecraft:quartz_pillar", 800D);
		m.put("minecraft:rabbit_stew", 500D);
		m.put("minecraft:red_dye", 40D);
		m.put("minecraft:red_mushroom", 120D);
		m.put("minecraft:red_sand", 160D);
		m.put("minecraft:red_tulip", 120D);
		m.put("minecraft:redstone", 20D);
		m.put("minecraft:rooted_dirt", 100D);
		m.put("minecraft:saddle", 2200D);
		m.put("minecraft:sand", 20D);
		m.put("minecraft:sculk_sensor", 10000D);
		m.put("minecraft:sea_pickle", 420D);
		m.put("minecraft:shears", 500D);
		m.put("minecraft:shield", 900D);
		m.put("minecraft:slime_ball", 1200D);
		m.put("minecraft:small_dripleaf", 100D);
		m.put("minecraft:spore_blossom", 100D);
		m.put("minecraft:stone_axe", 200D);
		m.put("minecraft:stone_hoe", 150D);
		m.put("minecraft:stone_pickaxe", 200D);
		m.put("minecraft:stone_shovel", 120D);
		m.put("minecraft:totem_of_undying", 40000D);
		m.put("minecraft:tripwire_hook", 100D);
		m.put("minecraft:tropical_fish", 400D);
		m.put("minecraft:tropical_fish_bucket", 1500D);
		m.put("minecraft:tube_coral_block", 849D);
		m.put("minecraft:turtle_scute", 300D);
		m.put("minecraft:vine", 120D);
		m.put("minecraft:wheat_seeds", 60D);
		m.put("minecraft:white_dye", 40D);
		m.put("minecraft:white_tulip", 120D);
		m.put("minecraft:writable_book", 890D);
		m.put("minecraft:yellow_dye", 40D);
		return m;
	}

	private static Map<String, Double> buy() {
		Map<String, Double> m = new HashMap<>();
		m.put("minecraft:beef", 40D);
		m.put("minecraft:beetroot", 2.67D);
		m.put("minecraft:black_wool", 12.78D);
		m.put("minecraft:book", 20D);
		m.put("minecraft:bookshelf", 10D);
		m.put("minecraft:brown_wool", 12.78D);
		m.put("minecraft:carrot", 1.6D);
		m.put("minecraft:chicken", 30D);
		m.put("minecraft:clay_ball", 2D);
		m.put("minecraft:coal", 10D);
		m.put("minecraft:cod", 20D);
		m.put("minecraft:compass", 500D);
		m.put("minecraft:diamond", 3000D);
		m.put("minecraft:dried_kelp_block", 70D);
		m.put("minecraft:emerald", 1000D);
		m.put("minecraft:flint", 15D);
		m.put("minecraft:glass_bottle", 11.11D);
		m.put("minecraft:glass_pane", 4.17D);
		m.put("minecraft:gold_ingot", 66.67D);
		m.put("minecraft:ink_sac", 50D);
		m.put("minecraft:iron_ingot", 50D);
		m.put("minecraft:leather", 71.43D);
		m.put("minecraft:melon", 50D);
		m.put("minecraft:mutton", 20D);
		m.put("minecraft:nether_wart", 30D);
		m.put("minecraft:paper", 4D);
		m.put("minecraft:porkchop", 27.27D);
		m.put("minecraft:potato", 1.6D);
		m.put("minecraft:pufferfish", 50D);
		m.put("minecraft:pumpkin", 10D);
		m.put("minecraft:quartz", 25D);
		m.put("minecraft:rabbit", 50D);
		m.put("minecraft:rabbit_foot", 100D);
		m.put("minecraft:rabbit_hide", 125D);
		m.put("minecraft:rotten_flesh", 2D);
		m.put("minecraft:salmon", 22.5D);
		m.put("minecraft:sand", 2.4D);
		m.put("minecraft:stick", 1D);
		m.put("minecraft:string", 5D);
		m.put("minecraft:sweet_berries", 60D);
		m.put("minecraft:turtle_scute", 100D);
		m.put("minecraft:wheat", 2.67D);
		m.put("minecraft:white_wool", 12.22D);
		return m;
	}
}
