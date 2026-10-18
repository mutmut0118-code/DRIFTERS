package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Armor a Drifter picked up over the days it caught up on (user, 2026-10-17: "it spawns in houses with a sword and
 * shield ... nice for it to have armor equipped aswell, matching with whatever tier they're on"; "very mix matchy";
 * "use armor from actual early game loot tables"). A few finds, more for more days, each a piece for one slot from where
 * a player of its stage gets armor: early, leather it made from hides (now and then dyed), gold from a ruined portal's
 * chest (the real loot table, enchanted as those can be) and chainmail off a zombie or skeleton; in the iron age iron (made,
 * or from a village armorer's chest), with chain and gold still about; later diamond and iron, some enchanted. All worn
 * some. It puts on the best piece for each slot, so the set comes out mixed. Only for one that wore next to nothing when
 * the catch-up began.
 */
public final class CatchUpArmor {
	private CatchUpArmor() {
	}

	private enum Source {
		LEATHER, GOLD, CHAIN, IRON, DIAMOND
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Map<Source, Item[]> PIECES = Map.of(
		Source.LEATHER, new Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
		Source.GOLD, new Item[] {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
		Source.CHAIN, new Item[] {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
		Source.IRON, new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		Source.DIAMOND, new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS});

	/** Finds a day it's away (and one more by chance); at most this many in all. */
	private static final double FINDS_PER_DAY = 1.5;
	private static final int MOST_FINDS = 6;
	private static final ResourceLocation RUINED_PORTAL = ResourceLocation.withDefaultNamespace("chests/ruined_portal");
	private static final ResourceLocation ARMORER = ResourceLocation.withDefaultNamespace("chests/village/village_armorer");

	/** How many armor pieces it has on. */
	public static int worn(DrifterEntity drifter) {
		int worn = 0;
		for (EquipmentSlot slot : SLOTS) {
			worn += drifter.getItemBySlot(slot).isEmpty() ? 0 : 1;
		}

		return worn;
	}

	/** The pieces it found over {@code days} at this stage (Strategist stage), into its inventory; names for the report. */
	public static List<Item> find(DrifterEntity drifter, double days, int stage, RandomSource random) {
		List<Item> found = new ArrayList<>();
		if (!(drifter.level() instanceof ServerLevel level) || days < 0.2) {
			return found;
		}

		int finds = Math.min(MOST_FINDS, (int)Math.floor(1.0 + days * FINDS_PER_DAY + random.nextDouble()));
		for (int i = 0; i < finds; i++) {
			ItemStack piece = piece(level, drifter, source(stage, random), random);
			if (piece == null) {
				continue;
			}

			// Worn some: it's had it a while.
			if (piece.isDamageableItem()) {
				piece.setDamageValue((int)(piece.getMaxDamage() * (0.1 + 0.5 * random.nextDouble())));
			}

			found.add(piece.getItem());
			drifter.getInventory().add(piece);
		}

		return found;
	}

	/** Where this find came from, by how far along it is (2 stone, 4 settling, 5 iron, 7 diamond). */
	private static Source source(int stage, RandomSource random) {
		double[] weights = stage >= 7 ? new double[] {0.5, 1.0, 1.0, 4.0, 3.0}
			: stage >= 5 ? new double[] {2.0, 2.0, 3.0, 5.0, 0.0}
			: new double[] {5.0, 3.0, 2.0, 0.5, 0.0};
		double total = 0.0;
		for (double weight : weights) {
			total += weight;
		}

		double roll = random.nextDouble() * total;
		Source[] sources = Source.values();
		for (int i = 0; i < sources.length; i++) {
			if ((roll -= weights[i]) <= 0.0) {
				return sources[i];
			}
		}

		return Source.LEATHER;
	}

	@Nullable
	private static ItemStack piece(ServerLevel level, DrifterEntity drifter, Source source, RandomSource random) {
		Item plain = PIECES.get(source)[random.nextInt(SLOTS.length)];
		switch (source) {
			case GOLD -> {
				// A ruined portal's chest, the real roll: its gold armor (often enchanted), if it had any.
				ItemStack fromLoot = armorFrom(level, drifter, RUINED_PORTAL, random);
				return fromLoot != null ? fromLoot : random.nextDouble() < 0.4 ? new ItemStack(plain) : null;
			}
			case IRON -> {
				// Made from its iron, or out of a village armorer's chest.
				if (random.nextBoolean()) {
					ItemStack fromLoot = armorFrom(level, drifter, ARMORER, random);
					if (fromLoot != null) {
						return fromLoot;
					}
				}

				return new ItemStack(plain);
			}
			case LEATHER -> {
				ItemStack stack = new ItemStack(plain);
				if (random.nextDouble() < 0.25) {
					DyeColor dye = DyeColor.values()[random.nextInt(DyeColor.values().length)];
					stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(dye.getTextureDiffuseColor() & 0xFFFFFF, true));
				}

				return stack;
			}
			case DIAMOND -> {
				ItemStack stack = new ItemStack(plain);
				if (random.nextDouble() < 0.3) {
					enchant(level, stack, random);
				}

				return stack;
			}
			default -> {
				return new ItemStack(plain);
			}
		}
	}

	/** One armor piece from a roll of this loot table, or null if the roll had none. */
	@Nullable
	private static ItemStack armorFrom(ServerLevel level, DrifterEntity drifter, ResourceLocation id, RandomSource random) {
		var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
		var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, drifter.position()).create(LootContextParamSets.CHEST);
		List<ItemStack> armor = table.getRandomItems(params).stream().filter(stack -> stack.getItem() instanceof ArmorItem).toList();
		return armor.isEmpty() ? null : armor.get(random.nextInt(armor.size())).copy();
	}

	/** An enchanting-table enchantment at level 8 to 25. */
	private static void enchant(ServerLevel level, ItemStack stack, RandomSource random) {
		Optional<? extends net.minecraft.core.HolderSet<net.minecraft.world.item.enchantment.Enchantment>> table = level.registryAccess()
			.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
		ItemStack enchanted = EnchantmentHelper.enchantItem(random, stack, 8 + random.nextInt(18), level.registryAccess(), table);
		if (enchanted != stack) {
			stack.applyComponents(enchanted.getComponentsPatch());
		}
	}
}
