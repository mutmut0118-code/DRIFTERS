package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * What a quest done earns (the user, 2026-11-14: "i feel like im getting little compensation and just their thanks, it
 * doesnt even raise the relationship ... after i came back i was still a stranger", "thanks rewards should be much rarer,
 * and when you recieve those thanks you get a bunch of XP (nobody is aware that you got xp ...)", "a few silver coins, more
 * or less depending on how far the quest is", "for harder quests maybe a enchantment book, alot of money, a custom
 * enchanted item, some rare mob drops, an artifact (mod), or a bunch of high valued food"; "Yes, build it").
 *
 * <p>Always from what's really there (the fairness rule: nothing out of thin air): money from the giver's pockets, its own
 * chests, and for the town's business the till; short of money, something of worth out of the same places instead; for a
 * big job, the best fitting thing it has (an enchanted book or piece, a rare drop, a mod's rare thing, good food) besides;
 * for the biggest, a piece made by the town's smith, paid for out of the till. Only with nothing at all to give is it
 * thanks alone, and then the player just has a lump of experience, unmentioned.
 */
final class Rewards {
	private Rewards() {
	}

	/** Money points to an emerald. */
	private static final double EMERALD = 6.0;
	/** A job's worth (emeralds) from which it's a big one: a prize besides the money. */
	static final int BIG = 8;
	/** A prize worth up to this many times the job (points), at least this. */
	private static final double PRIZE_OVER = 3.0;
	private static final double PRIZE_LEAST = 6.0;
	/** Something in kind worth up to this many times what's still owed (one piece). */
	private static final double IN_KIND_OVER = 1.5;
	/** Food to a prize: a stack of a few. */
	private static final int FOOD_PRIZE = 6;
	/** Experience for thanks alone: this, plus this for each emerald the job was worth, at most this. */
	private static final int XP_BASE = 40;
	private static final int XP_PER = 12;
	private static final int XP_MOST = 400;
	/** Rare things a Drifter keeps (by worth they'd be missed otherwise). */
	private static final java.util.Set<net.minecraft.world.item.Item> RARE = java.util.Set.of(Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GHAST_TEAR,
		Items.NETHER_STAR, Items.SHULKER_SHELL, Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.NAME_TAG, Items.SADDLE,
		Items.DIAMOND, Items.EXPERIENCE_BOTTLE, Items.HEART_OF_THE_SEA, Items.NAUTILUS_SHELL, Items.PHANTOM_MEMBRANE, Items.RABBIT_FOOT, Items.ENCHANTED_BOOK);

	/** Giver and player: the day its thanks last came with experience (once a day: found in review, a penniless giver's errands
	 * could be done over and over for it). Not saved. */
	private static final java.util.Map<String, Long> thankedOn = new java.util.HashMap<>();

	/** What it gave, for its words and the journal. */
	record Given(int money, List<ItemStack> things, boolean made, int xp) {
		boolean nothing() {
			return this.money <= 0 && this.things.isEmpty() && !this.made;
		}
	}

	/** It can reach into a chest this near (the player's reach). */
	private static final double REACH = 4.5;

	/**
	 * The giver's own chests within its reach now (at home: its house's; never the town's stores, nor the till, which is the
	 * town's business). Found in review: one with no chest of its own had the town's stores counted as its own, and one met
	 * on the plaza handed over what was in a chest sixty blocks off.
	 */
	private static List<Container> chests(MinecraftServer server, DrifterEntity giver) {
		List<Container> out = new ArrayList<>();
		Home home = DrifterApi.home(giver);
		if (home == null) {
			return out;
		}

		for (BlockPos at : home.chests()) {
			if (!home.townChests.contains(at) && giver.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(at)) <= REACH && giver.level().isLoaded(at)
				&& giver.level().getBlockEntity(at) instanceof Container chest) {
				out.add(chest);
			}
		}

		return out;
	}

	/** What it could promise in money, in whole emeralds' worth: its pockets, and (for its town) the till. */
	static int money(MinecraftServer server, Town town, DrifterEntity giver, boolean forTown) {
		int has = DrifterApi.moneyHeld(giver.getInventory());
		Container till = forTown ? TownNotices.till(server, town) : null;
		return has + (till == null ? 0 : DrifterApi.moneyHeld(till));
	}

	/**
	 * It pays them for this quest: {@code pay} promised in money, the job worth {@code worth} (emeralds), a big one or not.
	 * Tossed to them; what's given said back.
	 */
	static Given give(MinecraftServer server, Quests.Quest q, @Nullable Town town, DrifterEntity giver, ServerPlayer player, int pay, int worth, boolean big) {
		List<Container> sources = new ArrayList<>();
		sources.add(giver.getInventory());
		// (The till is never one of its own, even if it's listed among its chests.)
		Container townTill = town == null ? null : TownNotices.till(server, town);
		for (Container chest : chests(server, giver)) {
			if (chest != townTill) {
				sources.add(chest);
			}
		}

		Container till = q.forTown ? townTill : null;
		if (till != null) {
			sources.add(till);
		}

		// Money: as promised, from its pockets first, then its chests, then the till.
		int paid = 0;
		for (Container from : sources) {
			if (paid < pay) {
				paid += DrifterApi.moneyTake(from, pay - paid);
				from.setChanged();
			}
		}

		giver.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
		for (ItemStack stack : DrifterApi.moneyStacks(paid)) {
			giver.tossForward(stack).setTarget(player.getUUID());
		}

		// Short of what the job was worth: something of worth instead, from its pockets and its own chests (not the till's town).
		List<ItemStack> things = new ArrayList<>();
		double owed = (worth - paid) * EMERALD;
		if (owed >= 1.0) {
			for (Container from : sources.subList(0, sources.size() - (till != null ? 1 : 0))) {
				owed = inKind(giver, from, owed, things);
			}
		}

		// A big job: the best fitting thing it has besides (a prize), else (the biggest) a piece made for them by the smith.
		boolean made = false;
		if (big) {
			ItemStack prize = prize(giver, sources.subList(0, sources.size() - (till != null ? 1 : 0)), worth * EMERALD);
			if (!prize.isEmpty()) {
				things.add(prize);
			} else if (q.kind.equals("deal") && town != null) {
				made = Orders.gift(server, town, player);
			}
		}

		for (ItemStack thing : things) {
			giver.tossForward(thing.copy()).setTarget(player.getUUID());
		}

		// Nothing at all: thanks alone, and a lump of experience, unmentioned (the user's).
		int xp = 0;
		long day = Chronicle.day(server);
		String key = giver.getUUID() + ":" + player.getUUID();
		if (paid <= 0 && things.isEmpty() && !made && thankedOn.getOrDefault(key, -1L) != day) {
			thankedOn.put(key, day);
			xp = Math.min(XP_MOST, XP_BASE + XP_PER * Math.max(1, worth));
			player.giveExperiencePoints(xp);
		}

		return new Given(paid, things, made, xp);
	}

	/** Things of worth out of this container, up to {@code owed} points: what's still owed. Not its gear, nor its food. */
	private static double inKind(DrifterEntity giver, Container from, double owed, List<ItemStack> into) {
		for (int slot = 0; slot < from.getContainerSize() && owed >= 1.0; slot++) {
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty() || !spare(giver, from, slot, stack)) {
				continue;
			}

			double each = DrifterApi.worth(stack.copyWithCount(1));
			// (Not plain food: good meals only. Found in testing: "10 apple and 8 baked potato" for a letter carried.)
			if (each < 1.0 || each > owed * IN_KIND_OVER || stack.has(DataComponents.FOOD) && prizeScore(stack) <= 0.0) {
				continue;
			}

			int n = (int)Math.max(1, Math.min(stack.getCount(), Math.floor(owed / each)));
			into.add(stack.split(n));
			owed -= each * n;
			from.setChanged();
		}

		return owed;
	}

	/** It can spare this: not money (counted already), not what it wears or holds, not its food (it eats), not a tool it works with. */
	private static boolean spare(DrifterEntity giver, Container from, int slot, ItemStack stack) {
		if (DrifterApi.moneyWorth(stack) > 0) {
			return false;
		}

		if (from == giver.getInventory()) {
			for (EquipmentSlot worn : EquipmentSlot.values()) {
				if (giver.getItemBySlot(worn) == stack) {
					return false;
				}
			}

			if (stack.has(DataComponents.FOOD) || DrifterApi.isGear(stack) || stack.getItem() instanceof net.minecraft.world.item.TieredItem) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The best prize it has for a job worth {@code points}: an enchanted book or enchanted piece, a rare drop, a mod's rare
	 * thing, or good food (Farmer's Delight's meals that nourish, first), worth no more than a few times the job. Taken out.
	 */
	private static ItemStack prize(DrifterEntity giver, List<Container> from, double points) {
		Container bestFrom = null;
		int bestSlot = -1;
		double bestScore = 0.0;
		for (Container chest : from) {
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				ItemStack stack = chest.getItem(slot);
				// (Out of its pockets only what it can spare, an enchanted book at most: not its enchanted tools. Never money. Both found
				// in review.)
				if (stack.isEmpty() || DrifterApi.moneyWorth(stack) > 0 || chest == giver.getInventory() && !spare(giver, chest, slot, stack)
					&& !stack.is(Items.ENCHANTED_BOOK)) {
					continue;
				}

				// (Not what it's wearing or holding.)
				boolean worn = false;
				for (EquipmentSlot one : EquipmentSlot.values()) {
					worn |= chest == giver.getInventory() && giver.getItemBySlot(one) == stack;
				}

				double score = prizeScore(stack);
				double worth = DrifterApi.worth(stack.copyWithCount(1)) * (stack.has(DataComponents.FOOD) ? Math.min(FOOD_PRIZE, stack.getCount()) : 1);
				if (worn || score <= 0.0 || worth > Math.max(PRIZE_LEAST, points * PRIZE_OVER)) {
					continue;
				}

				if (score > bestScore) {
					bestScore = score;
					bestFrom = chest;
					bestSlot = slot;
				}
			}
		}

		if (bestFrom == null) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = bestFrom.getItem(bestSlot);
		ItemStack prize = stack.split(stack.has(DataComponents.FOOD) ? Math.min(FOOD_PRIZE, stack.getCount()) : 1);
		bestFrom.setChanged();
		return prize;
	}

	/** How much it's a prize (0: not one): enchanted things, rare drops, a mod's rare or epic things, good food. */
	private static double prizeScore(ItemStack stack) {
		String space = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
		Rarity rarity = stack.getRarity();
		double worth = DrifterApi.worth(stack.copyWithCount(1));
		if (stack.is(Items.ENCHANTED_BOOK) || stack.isEnchanted()) {
			return 30.0 + worth;
		}

		if (RARE.contains(stack.getItem())) {
			return 20.0 + worth;
		}

		// (A mod's: an artifact, a relic, anything it marks rare.)
		if (!space.equals("minecraft") && (rarity == Rarity.RARE || rarity == Rarity.EPIC)) {
			return 25.0 + worth;
		}

		var food = stack.get(DataComponents.FOOD);
		if (food != null && stack.getCount() >= 2) {
			boolean nourishes = food.effects().stream().anyMatch(e -> BuiltInRegistries.MOB_EFFECT.getKey(e.effect().getEffect().value()) != null
				&& BuiltInRegistries.MOB_EFFECT.getKey(e.effect().getEffect().value()).getPath().contains("nourish"));
			if (nourishes) {
				return 18.0 + food.nutrition();
			}

			return food.nutrition() >= 8 ? 8.0 + food.nutrition() : 0.0;
		}

		return 0.0;
	}

	/** The words for what it gave ("2 emeralds and a book"), for its thanks and the journal. */
	static String words(Given given) {
		List<String> parts = new ArrayList<>();
		if (given.money() > 0) {
			parts.add(DrifterApi.moneyWords(given.money()));
		}

		for (ItemStack thing : given.things()) {
			String name = thing.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
			parts.add(thing.getCount() > 1 ? thing.getCount() + " " + name : (thing.isEnchanted() && !thing.is(Items.ENCHANTED_BOOK) ? "an enchanted " : article(name)) + name);
		}

		if (given.made()) {
			parts.add("a piece made for you by the smith");
		}

		if (parts.isEmpty()) {
			return "";
		}

		return parts.size() == 1 ? parts.get(0) : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	private static String article(String name) {
		return "aeiou".indexOf(name.isEmpty() ? 'x' : name.charAt(0)) >= 0 ? "an " : "a ";
	}

	/** Trust and closeness for a quest done: more for the harder ones; one makes them an acquaintance (the user: "still a stranger"). */
	static void warmer(DrifterEntity giver, ServerPlayer player, double trust, double familiarity) {
		String name = player.getGameProfile().getName();
		DrifterApi.addTrustInPlayer(giver, player.getUUID(), name, trust);
		TalkApi.addFamiliarity(giver, player.getUUID(), name, familiarity);
	}
}
