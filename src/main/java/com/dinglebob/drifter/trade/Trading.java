package com.dinglebob.drifter.trade;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.personality.Trait;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trading with villagers (DESIGN.md section 10.12): it sells what it has more of than it keeps (wheat, potatoes, paper,
 * sticks, coal...; how much it keeps of each is in {@code data/<namespace>/drifter_trading/*.json}) for emeralds, and
 * buys what it needs: better armour, a better weapon or tool, food when it's short, a Mending book if it knows enough to
 * want one. No trading halls (a v1 non-goal). A trade itself is the vanilla one, step for step (MerchantResultSlot).
 */
public final class Trading {
	private Trading() {
	}

	public static final String DIRECTORY = "drifter_trading";
	/** Items it sells when it has more than this many (item -> how many it keeps). */
	private static volatile Map<Item, Integer> goods = Map.of();

	/** How much it wants a thing it could buy (0 to 1). */
	private static final double WANT_ARMOR = 0.55;
	private static final double WANT_WEAPON = 0.5;
	private static final double WANT_TOOL = 0.45;
	private static final double WANT_FOOD = 0.5;
	private static final double WANT_MENDING = 0.35;
	/** Fewer food items than this: it would buy some. */
	private static final int FOOD_SHORT = 8;
	/** A greedy one sells its surplus for emeralds even with nothing to buy. */
	private static final double GREEDY_SELL = 0.25;

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("trading");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		Map<Item, Integer> loaded = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("requires") && !FabricLoader.getInstance().isModLoaded(root.get("requires").getAsString())) {
					continue;
				}

				if (root.has("goods")) {
					for (var entry : root.getAsJsonObject("goods").entrySet()) {
						BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(entry.getKey())).ifPresent(item -> loaded.put(item, entry.getValue().getAsInt()));
					}
				}
			} catch (Exception e) {
				Drifter.LOGGER.warn("[Drifter] Couldn't read trading data {}: {}", file.getKey(), e.toString());
			}
		}

		goods = Map.copyOf(loaded);
		Drifter.LOGGER.info("[Drifter] Trading: {} kinds of goods it sells its surplus of", loaded.size());
	}

	/** How many of this it keeps before it sells any, or null if it isn't one of the goods it sells. */
	@Nullable
	public static Integer keeps(Item item) {
		return goods.get(item);
	}

	/** How many of this it could sell (what it has over what it keeps; 0 if it isn't something it sells). */
	public static int surplus(DrifterEntity drifter, Item item) {
		Integer keep = goods.get(item);
		if (keep == null) {
			return 0;
		}

		return Math.max(0, drifter.getInventory().count(stack -> stack.is(item) && !stack.isDamaged() && !stack.isEnchanted()) - keep);
	}

	public static int emeralds(DrifterEntity drifter) {
		return drifter.getInventory().count(stack -> stack.is(Items.EMERALD));
	}

	/** One trade of a visit: which offer, how many times, buying or selling, in words. */
	public record Step(int offer, int times, boolean buy, String label) {
	}

	/** A visit to a villager: the trades, how much it's worth to it, and why. */
	public record Plan(UUID villager, BlockPos where, List<Step> steps, double value, String why) {
	}

	/**
	 * What it would do at this villager, from its offers: the thing it wants most that it can pay for (with emeralds it has,
	 * and what it can sell this villager first), or, greedy, selling its surplus anyway. Null: nothing worth it.
	 */
	@Nullable
	public static Plan plan(DrifterEntity drifter, TradeBook.Seen seen) {
		int emeralds = emeralds(drifter);
		TradeBook.Offer bestBuy = null;
		double bestWant = 0.0;
		for (TradeBook.Offer offer : seen.offers()) {
			if (offer.outOfStock() || offer.buysForEmeralds() || !offer.costA().equals("minecraft:emerald") && !canPayOther(drifter, offer)) {
				continue;
			}

			double want = want(drifter, offer);
			if (want > bestWant) {
				bestWant = want;
				bestBuy = offer;
			}
		}

		List<Step> sells = new ArrayList<>();
		int fromSelling = 0;
		for (TradeBook.Offer offer : seen.offers()) {
			if (offer.outOfStock() || !offer.buysForEmeralds() || !offer.costB().isEmpty()) {
				continue;
			}

			Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.costA()));
			int times = offer.countA() <= 0 ? 0 : surplus(drifter, item) / offer.countA();
			if (times > 0) {
				times = Math.min(times, MAX_SELLS);
				sells.add(new Step(offer.index(), times, false, "sell " + offer.countA() * times + " " + name(offer.costA()) + " for " + offer.resultCount() * times
					+ " emeralds"));
				fromSelling += offer.resultCount() * times;
			}
		}

		if (bestBuy != null) {
			int price = (bestBuy.costA().equals("minecraft:emerald") ? bestBuy.countA() : 0) + (bestBuy.costB().equals("minecraft:emerald") ? bestBuy.countB() : 0);
			String label = "buy " + (bestBuy.resultCount() > 1 ? bestBuy.resultCount() + " " : "") + name(bestBuy.result()) + " for " + price + " emeralds";
			if (price <= emeralds) {
				return new Plan(seen.villager(), seen.where(), List.of(new Step(bestBuy.index(), 1, true, label)), bestWant, label);
			}

			if (price <= emeralds + fromSelling) {
				List<Step> steps = new ArrayList<>(sells);
				steps.add(new Step(bestBuy.index(), 1, true, label));
				return new Plan(seen.villager(), seen.where(), List.copyOf(steps), bestWant, label + " (selling first)");
			}
		}

		if (!sells.isEmpty() && drifter.getPersonality().get(Trait.GREED) >= 0.6) {
			return new Plan(seen.villager(), seen.where(), List.copyOf(sells), GREEDY_SELL, "sell its surplus for emeralds");
		}

		return null;
	}

	/** Most of one kind it sells in a visit. */
	private static final int MAX_SELLS = 6;

	/** An offer costing something other than emeralds (a librarian's book and emeralds): it has the other part. */
	private static boolean canPayOther(DrifterEntity drifter, TradeBook.Offer offer) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.costA()));
		return item != Items.AIR && drifter.getInventory().count(stack -> stack.is(item)) >= offer.countA() && offer.costB().equals("minecraft:emerald");
	}

	private static String name(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).getDescription().getString().toLowerCase(java.util.Locale.ROOT);
	}

	/** How much it wants what this offer gives (0: not at all). */
	public static double want(DrifterEntity drifter, TradeBook.Offer offer) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.result()));
		if (item == Items.AIR) {
			return 0.0;
		}

		ItemStack stack = new ItemStack(item, offer.resultCount());
		if (item instanceof ArmorItem armor) {
			return armorScore(stack) > bestArmor(drifter, armor.getEquipmentSlot()) ? WANT_ARMOR : 0.0;
		}

		if (item == Items.ENCHANTED_BOOK) {
			boolean mending = offer.enchantments().contains("minecraft:mending");
			return mending && drifter.getPersonality().get(Trait.KNOWLEDGE) >= 0.7 && drifter.getInventory().count(s -> s.is(Items.ENCHANTED_BOOK)) == 0
				? WANT_MENDING : 0.0;
		}

		if (item instanceof DiggerItem && !(item instanceof AxeItem && com.dinglebob.drifter.brain.Weapons.isWeapon(stack))) {
			BlockState reference = reference(item);
			if (reference != null) {
				float speed = stack.getDestroySpeed(reference);
				float best = 1.0F;
				for (ItemStack tool : com.dinglebob.drifter.hands.Tools.toolsOf(drifter)) {
					if (tool.getItem().getClass() == item.getClass() || reference(tool.getItem()) == reference) {
						best = Math.max(best, tool.getDestroySpeed(reference));
					}
				}

				if (speed > best * 1.2F) {
					return WANT_TOOL;
				}
			}
		}

		if (com.dinglebob.drifter.brain.Weapons.isWeapon(stack)) {
			double perSecond = com.dinglebob.drifter.hands.Combat.weaponStats(drifter, stack).perSecond();
			int slot = com.dinglebob.drifter.hands.Combat.bestWeaponSlot(drifter);
			ItemStack current = slot < 0 ? ItemStack.EMPTY : drifter.getInventory().getItem(slot);
			return perSecond > com.dinglebob.drifter.hands.Combat.weaponStats(drifter, current).perSecond() * 1.15 ? WANT_WEAPON : 0.0;
		}

		if (stack.has(DataComponents.FOOD)) {
			int food = drifter.getInventory().count(s -> s.has(DataComponents.FOOD) && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE));
			return food < FOOD_SHORT ? WANT_FOOD * (1.0 - food / (double)FOOD_SHORT) : 0.0;
		}

		return 0.0;
	}

	@Nullable
	private static BlockState reference(Item item) {
		if (item instanceof PickaxeItem) {
			return Blocks.IRON_ORE.defaultBlockState();
		}

		if (item instanceof ShovelItem) {
			return Blocks.DIRT.defaultBlockState();
		}

		if (item instanceof AxeItem) {
			return Blocks.OAK_LOG.defaultBlockState();
		}

		return null;
	}

	private static double armorScore(ItemStack stack) {
		return stack.getItem() instanceof ArmorItem armor ? armor.getDefense() * 10.0 + armor.getToughness() : 0.0;
	}

	/** Its best armour for this slot, worn or carried. */
	private static double bestArmor(DrifterEntity drifter, EquipmentSlot slot) {
		double best = armorScore(drifter.getItemBySlot(slot));
		DrifterInventory inventory = drifter.getInventory();
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot) {
				best = Math.max(best, armorScore(stack));
			}
		}

		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// The trade itself
	// ---------------------------------------------------------------------------------------------

	/**
	 * One trade, as the vanilla trading screen does it (MerchantMenu putting the payment in, MerchantResultSlot#onTake):
	 * the payment from its pockets, the offer's own check and take, the villager told (its XP, its restock count, the
	 * player's trade stat), the item into its pockets. Run as its helper player, the one the villager is trading with.
	 */
	public static boolean trade(FakePlayer fake, AbstractVillager villager, int index) {
		var offers = villager.getOffers();
		if (index < 0 || index >= offers.size() || villager.getTradingPlayer() != fake) {
			return false;
		}

		MerchantOffer offer = offers.get(index);
		if (offer.isOutOfStock()) {
			return false;
		}

		Inventory inventory = fake.getInventory();
		ItemStack payA = gather(inventory, offer.getItemCostA(), offer.getCostA().getCount());
		ItemStack payB = offer.getItemCostB().map(cost -> gather(inventory, cost, offer.getCostB().getCount())).orElse(ItemStack.EMPTY);
		if (!offer.satisfiedBy(payA, payB)) {
			giveBack(fake, payA);
			giveBack(fake, payB);
			return false;
		}

		ItemStack result = offer.assemble();
		result.onCraftedBy(fake.level(), fake, result.getCount());
		if (offer.take(payA, payB)) {
			villager.notifyTrade(offer);
			fake.awardStat(Stats.TRADED_WITH_VILLAGER);
		}

		villager.overrideXp(villager.getVillagerXp() + offer.getXp());
		giveBack(fake, payA);
		giveBack(fake, payB);
		giveBack(fake, result);
		villager.notifyTradeUpdated(result);
		return true;
	}

	/** The payment for one cost, from its pockets (as the menu fills a payment slot): one stack of what matches, up to the count. */
	private static ItemStack gather(Inventory inventory, ItemCost cost, int count) {
		ItemStack pay = ItemStack.EMPTY;
		for (int slot = 0; slot < inventory.items.size() && pay.getCount() < count; slot++) {
			ItemStack stack = inventory.items.get(slot);
			if (stack.isEmpty() || !cost.test(stack) || !pay.isEmpty() && !ItemStack.isSameItemSameComponents(pay, stack)) {
				continue;
			}

			int take = Math.min(count - pay.getCount(), stack.getCount());
			if (pay.isEmpty()) {
				pay = stack.copyWithCount(take);
			} else {
				pay.grow(take);
			}

			stack.shrink(take);
		}

		return pay;
	}

	private static void giveBack(FakePlayer fake, ItemStack stack) {
		if (!stack.isEmpty() && !fake.getInventory().add(stack) && !stack.isEmpty()) {
			fake.drop(stack, false);
		}
	}
}
