package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * A travelling merchant (docs/ENCOUNTERS.md 21): a player-like trader walks up with a few rare things in its pack and
 * sells them for emeralds, through the game's own trading screen (right-click it). What it sells leaves its pack and
 * the emeralds go in (no free items either way). After a few minutes, or once you walk off, it goes on its way.
 */
final class Merchant extends Encounter {
	private static final String MERCHANT = "merchant";
	/** What it might carry: emeralds for a bundle of so many. Tunable here. */
	private static final Map<Item, int[]> PRICES = new LinkedHashMap<>();

	static {
		PRICES.put(Items.DIAMOND, new int[] {6, 1});
		PRICES.put(Items.GOLDEN_APPLE, new int[] {5, 1});
		PRICES.put(Items.ENDER_PEARL, new int[] {3, 2});
		PRICES.put(Items.NAME_TAG, new int[] {6, 1});
		PRICES.put(Items.SADDLE, new int[] {5, 1});
		PRICES.put(Items.SPYGLASS, new int[] {3, 1});
		PRICES.put(Items.NAUTILUS_SHELL, new int[] {4, 1});
		PRICES.put(Items.BLAZE_ROD, new int[] {4, 2});
		PRICES.put(Items.SLIME_BALL, new int[] {2, 4});
		PRICES.put(Items.GLOW_INK_SAC, new int[] {2, 3});
		PRICES.put(Items.ENCHANTED_BOOK, new int[] {10, 1});
		PRICES.put(Items.MUSIC_DISC_CAT, new int[] {5, 1});
		PRICES.put(Items.EXPERIENCE_BOTTLE, new int[] {3, 4});
	}

	/** How long it hangs about once it has said hello (ticks), and how far off you can walk before it goes. */
	private static final long STAYS = 20L * 60 * 3;
	private static final double WALKED_OFF = 40.0;

	@Nullable
	private DrifterEntity merchant;
	private long greetedAt = -1;
	@Nullable
	private Trades trades;

	private Merchant(ServerPlayer player) {
		super(Encounters.Kind.MERCHANT, player);
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 30.0, 42.0, null, random);
		if (spot == null) {
			return null;
		}

		Merchant encounter = new Merchant(player);
		DrifterEntity merchant = EncounterSpawner.spawn(level, spot, Archetype.HELPER, drifter -> {
			EncounterSpawner.traits(drifter, 0.5, 0.2, 0.6, 0.3);
			List<Item> goods = new ArrayList<>(PRICES.keySet());
			Collections.shuffle(goods, new java.util.Random(random.nextLong()));
			int kinds = 3 + random.nextInt(3);
			for (Item item : goods.subList(0, kinds)) {
				int bundles = 1 + random.nextInt(3);
				ItemStack stack = item == Items.ENCHANTED_BOOK
					? net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), 15 + random.nextInt(15),
						level.registryAccess(), Optional.empty())
					: new ItemStack(item, PRICES.get(item)[1] * bundles);
				drifter.getInventory().add(stack);
			}

			drifter.getInventory().add(new ItemStack(Items.EMERALD, 4 + random.nextInt(8)));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 4));
			drifter.getInventory().add(new ItemStack(Items.IRON_SWORD));
		});
		if (merchant == null) {
			return null;
		}

		encounter.join(merchant, MERCHANT);
		encounter.merchant = merchant;
		EncounterSpawner.add(level, merchant);
		think(merchant, "on the road with a pack of rare things to sell");
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 6;
	}

	@Override
	protected void update() {
		DrifterEntity merchant = this.merchant;
		ServerPlayer player = this.player();
		if (merchant == null || !merchant.isAlive()) {
			this.end("the merchant is gone");
			return;
		}

		boolean trading = this.trades != null && this.trades.customer != null;
		if (!trading && this.greetedAt >= 0 && (player == null || player.distanceTo(merchant) > WALKED_OFF || this.now() - this.greetedAt > STAYS)) {
			say(merchant, "safe travels, " + this.playerName + ".");
			this.end("the merchant moved on");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		return player == null ? 0.0 : 0.9;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.greetedAt < 0 ? "go and offer " + this.playerName + " its wares" : "trade with " + this.playerName;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		Script script = new Script(drifter, this.describe(drifter), 20 * 90);
		if (this.greetedAt < 0) {
			script.then(() -> walkUpTo(drifter, player, 3.0))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					if (drifter.distanceTo(player) < 7.0) {
						this.greetedAt = this.now();
						say(drifter, "hello there! I've a few rare things, if you've got emeralds. Have a look.");
					}
				});
			return script;
		}

		// Waiting on them: near, turned to them (still while they look through its wares).
		boolean trading = this.trades != null && this.trades.customer != null;
		if (!trading && drifter.distanceTo(player) > 5.0) {
			script.then(() -> walkUpTo(drifter, player, 3.0));
		}

		return script.then(() -> new LookAtTask(drifter, player.getEyePosition(), 40));
	}

	@Override
	public InteractionResult interact(DrifterEntity drifter, Player player) {
		if (this.isOver() || drifter != this.merchant || !(player instanceof ServerPlayer)) {
			return InteractionResult.PASS;
		}

		if (this.trades == null) {
			this.trades = new Trades(drifter);
		}

		if (this.trades.customer != null && this.trades.customer != player) {
			return InteractionResult.CONSUME;
		}

		this.trades.refresh();
		if (this.trades.getOffers().isEmpty()) {
			say(drifter, "sold out, I'm afraid.");
			return InteractionResult.CONSUME;
		}

		this.trades.setTradingPlayer(player);
		this.trades.openTradingScreen(player, drifter.getName(), 1);
		if (this.greetedAt < 0) {
			this.greetedAt = this.now();
		}

		return InteractionResult.SUCCESS;
	}

	/** Its wares as the game's trades: what it has, priced; a sale moves the goods out of its pack and the emeralds in. */
	static final class Trades implements net.minecraft.world.item.trading.Merchant {
		private final DrifterEntity drifter;
		@Nullable
		Player customer;
		private MerchantOffers offers = new MerchantOffers();

		Trades(DrifterEntity drifter) {
			this.drifter = drifter;
		}

		/** One offer per kind of thing it carries that it sells (bundles it still has). */
		void refresh() {
			MerchantOffers offers = new MerchantOffers();
			DrifterInventory inventory = this.drifter.getInventory();
			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
				ItemStack stack = inventory.getItem(slot);
				int[] price = stack.isEmpty() ? null : PRICES.get(stack.getItem());
				if (price == null || stack.getCount() < price[1]) {
					continue;
				}

				ItemStack sold = stack.copyWithCount(price[1]);
				offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price[0]), Optional.empty(), sold, 0, stack.getCount() / price[1], 0, 0.0F));
			}

			this.offers = offers;
		}

		@Override
		public void setTradingPlayer(@Nullable Player player) {
			this.customer = player;
		}

		@Nullable
		@Override
		public Player getTradingPlayer() {
			return this.customer;
		}

		@Override
		public MerchantOffers getOffers() {
			return this.offers;
		}

		@Override
		public void overrideOffers(MerchantOffers offers) {
			this.offers = offers;
		}

		@Override
		public void notifyTrade(MerchantOffer offer) {
			// The goods out of its pack (the slot they came from), the emeralds it was paid in.
			ItemStack sold = offer.getResult();
			DrifterInventory inventory = this.drifter.getInventory();
			int left = sold.getCount();
			for (int slot = 0; slot < DrifterInventory.MAIN_SIZE && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (ItemStack.isSameItemSameComponents(stack, sold)) {
					int take = Math.min(left, stack.getCount());
					stack.shrink(take);
					left -= take;
				}
			}

			inventory.add(new ItemStack(Items.EMERALD, offer.getCostA().getCount()));
			think(this.drifter, "sold " + sold.getCount() + " " + sold.getHoverName().getString().toLowerCase(java.util.Locale.ROOT) + " for "
				+ offer.getCostA().getCount() + " emeralds");
		}

		@Override
		public void notifyTradeUpdated(ItemStack stack) {
		}

		@Override
		public int getVillagerXp() {
			return 0;
		}

		@Override
		public void overrideXp(int xp) {
		}

		@Override
		public boolean showProgressBar() {
			return false;
		}

		@Override
		public SoundEvent getNotifyTradeSound() {
			return SoundEvents.ITEM_PICKUP;
		}

		@Override
		public boolean isClientSide() {
			return this.drifter.level().isClientSide;
		}
	}
}
