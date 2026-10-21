package com.dinglebob.drifter.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The villagers' trades it has seen (DESIGN.md section 10.12): each villager's offers as they were when it last had its
 * trading screen open, and where that villager was. It only knows a villager's trades by having looked (section 1), as a
 * player remembers who sells what. Saved, versioned.
 */
public final class TradeBook {
	/** One offer as it saw it: what it costs, what it gives, an enchanted book's enchantments, sold out or not. */
	public record Offer(int index, String costA, int countA, String costB, int countB, String result, int resultCount, List<String> enchantments,
		boolean outOfStock) {
		/** It gives money for something (a villager buying): emeralds, or with Numismatic Overhaul coins. */
		public boolean buysForEmeralds() {
			return Money.isMoney(this.result);
		}
	}

	/** A villager it has traded with or looked at: its job, where it was, when, and its offers. */
	public record Seen(UUID villager, String profession, BlockPos where, long seenAt, List<Offer> offers) {
	}

	/** Most villagers it keeps in mind (the longest unseen go first). */
	private static final int MAX_VILLAGERS = 48;
	private static final int VERSION = 1;

	private final Map<UUID, Seen> seen = new LinkedHashMap<>();

	/** It has its trading screen open: it sees all of the villager's offers. */
	public void saw(AbstractVillager villager, long now) {
		List<Offer> offers = new ArrayList<>();
		int index = 0;
		for (MerchantOffer offer : villager.getOffers()) {
			offers.add(note(index++, offer));
		}

		String profession = villager instanceof Villager v ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).getPath()
			: "trader";
		this.seen.remove(villager.getUUID());
		this.seen.put(villager.getUUID(), new Seen(villager.getUUID(), profession, villager.blockPosition(), now, List.copyOf(offers)));
		while (this.seen.size() > MAX_VILLAGERS) {
			this.seen.remove(this.seen.keySet().iterator().next());
		}
	}

	static Offer note(int index, MerchantOffer offer) {
		ItemStack costA = offer.getCostA();
		ItemStack costB = offer.getCostB();
		ItemStack result = offer.getResult();
		List<String> enchantments = new ArrayList<>();
		EnchantmentHelper.getEnchantmentsForCrafting(result).keySet().forEach(holder -> holder.unwrapKey()
			.ifPresent(key -> enchantments.add(key.location().toString())));
		return new Offer(index, id(costA), costA.getCount(), costB.isEmpty() ? "" : id(costB), costB.getCount(), id(result), result.getCount(),
			List.copyOf(enchantments), offer.isOutOfStock());
	}

	private static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	@Nullable
	public Seen get(UUID villager) {
		return this.seen.get(villager);
	}

	public Collection<Seen> all() {
		return this.seen.values();
	}

	public void forget(UUID villager) {
		this.seen.remove(villager);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", VERSION);
		ListTag list = new ListTag();
		for (Seen seen : this.seen.values()) {
			CompoundTag entry = new CompoundTag();
			entry.putUUID("Villager", seen.villager());
			entry.putString("Profession", seen.profession());
			entry.putLong("Where", seen.where().asLong());
			entry.putLong("SeenAt", seen.seenAt());
			ListTag offers = new ListTag();
			for (Offer offer : seen.offers()) {
				CompoundTag o = new CompoundTag();
				o.putInt("Index", offer.index());
				o.putString("CostA", offer.costA());
				o.putInt("CountA", offer.countA());
				o.putString("CostB", offer.costB());
				o.putInt("CountB", offer.countB());
				o.putString("Result", offer.result());
				o.putInt("ResultCount", offer.resultCount());
				ListTag enchantments = new ListTag();
				offer.enchantments().forEach(e -> enchantments.add(StringTag.valueOf(e)));
				o.put("Enchantments", enchantments);
				o.putBoolean("OutOfStock", offer.outOfStock());
				offers.add(o);
			}

			entry.put("Offers", offers);
			list.add(entry);
		}

		tag.put("Seen", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.seen.clear();
		ListTag list = tag.getList("Seen", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			if (!entry.hasUUID("Villager")) {
				continue;
			}

			List<Offer> offers = new ArrayList<>();
			ListTag offerList = entry.getList("Offers", Tag.TAG_COMPOUND);
			for (int j = 0; j < offerList.size(); j++) {
				CompoundTag o = offerList.getCompound(j);
				List<String> enchantments = new ArrayList<>();
				ListTag e = o.getList("Enchantments", Tag.TAG_STRING);
				for (int k = 0; k < e.size(); k++) {
					enchantments.add(e.getString(k));
				}

				offers.add(new Offer(o.getInt("Index"), o.getString("CostA"), o.getInt("CountA"), o.getString("CostB"), o.getInt("CountB"),
					o.getString("Result"), o.getInt("ResultCount"), List.copyOf(enchantments), o.getBoolean("OutOfStock")));
			}

			UUID id = entry.getUUID("Villager");
			this.seen.put(id, new Seen(id, entry.getString("Profession"), BlockPos.of(entry.getLong("Where")), entry.getLong("SeenAt"), List.copyOf(offers)));
		}
	}
}
