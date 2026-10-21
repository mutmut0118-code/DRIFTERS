package com.dinglebob.drifter.world;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BannerBlock;

/**
 * A fighter's kit, with some variety (the user, 2026-10-20: "can we add more variety into the gear?"; before, every
 * soldier wore the same iron). An army has a colour and a standard of kit; each of its fighters' pieces is picked on its
 * own (leather, chainmail, iron, gold, diamond, now and then none), leather dyed the army's colour, metal trimmed in it,
 * shields painted it, and its captain wears the army's banner on its head. Weapons are its favourite kind (modded ones
 * too) at a grade fitting the standard; veterans carry the odd enchantment; it's all been through a fight or two (worn).
 * The kit is its starting kit (DESIGN.md section 1): what it drops is what it had.
 */
public final class SoldierKit {
	/** How well an army is kitted out. */
	public enum Quality {
		/** Leather and stone, a few iron bits. */
		MILITIA,
		/** Chainmail and iron. */
		REGULARS,
		/** Iron and diamond, some enchanted. */
		VETERANS;

		/** From how well armed a player looks (0 to 2, encounters' gear tier). */
		public static Quality forTier(int tier) {
			return tier <= 0 ? MILITIA : tier == 1 ? REGULARS : VETERANS;
		}
	}

	/** A fighter's part: in the line, at the back with a bow, or leading (the banner, the best blade, the war chest). */
	public enum Role {
		INFANTRY, ARCHER, CAPTAIN
	}

	/** An army: its colour, its standard, and the trim its metal wears (null: a band too rough for trims). */
	public record Army(DyeColor colour, Quality quality, @Nullable Holder<TrimMaterial> trim, @Nullable Holder<TrimPattern> pattern) {
	}

	/** Colours armies take (two at once never the same), and the trim material that goes with each. */
	private static final List<DyeColor> COLOURS = List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW, DyeColor.WHITE,
		DyeColor.PURPLE, DyeColor.BLACK, DyeColor.ORANGE, DyeColor.LIGHT_BLUE, DyeColor.CYAN);
	private static final List<ResourceKey<TrimPattern>> PATTERNS = List.of(TrimPatterns.SENTRY, TrimPatterns.VEX, TrimPatterns.WILD, TrimPatterns.COAST,
		TrimPatterns.DUNE, TrimPatterns.WARD, TrimPatterns.RIB, TrimPatterns.SNOUT, TrimPatterns.TIDE, TrimPatterns.SPIRE, TrimPatterns.FLOW, TrimPatterns.BOLT);

	private SoldierKit() {
	}

	private static ResourceKey<TrimMaterial> trimFor(DyeColor colour) {
		return switch (colour) {
			case RED -> TrimMaterials.REDSTONE;
			case BLUE -> TrimMaterials.LAPIS;
			case GREEN -> TrimMaterials.EMERALD;
			case YELLOW -> TrimMaterials.GOLD;
			case WHITE -> TrimMaterials.QUARTZ;
			case PURPLE -> TrimMaterials.AMETHYST;
			case BLACK -> TrimMaterials.NETHERITE;
			case ORANGE -> TrimMaterials.COPPER;
			case LIGHT_BLUE -> TrimMaterials.DIAMOND;
			default -> TrimMaterials.IRON;
		};
	}

	/** Two armies to face each other: different colours, the same standard. */
	public static Army[] opposed(ServerLevel level, Quality quality, RandomSource random) {
		DyeColor one = COLOURS.get(random.nextInt(COLOURS.size()));
		DyeColor other;
		do {
			other = COLOURS.get(random.nextInt(COLOURS.size()));
		} while (other == one);

		return new Army[] {army(level, one, quality, random), army(level, other, quality, random)};
	}

	/** An army of this colour and standard (its trim pattern picked at random; militia go untrimmed). */
	public static Army army(ServerLevel level, DyeColor colour, Quality quality, RandomSource random) {
		var materials = level.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL);
		var patterns = level.registryAccess().registryOrThrow(Registries.TRIM_PATTERN);
		Holder<TrimMaterial> trim = quality == Quality.MILITIA ? null : materials.getHolder(trimFor(colour)).orElse(null);
		Holder<TrimPattern> pattern = trim == null ? null : patterns.getHolder(PATTERNS.get(random.nextInt(PATTERNS.size()))).orElse(null);
		return new Army(colour, quality, trim, pattern);
	}

	/** Its kit for its part in this army: armour, weapon, shield (its colours), a bow, food, a pickaxe and some blocks. */
	public static void equip(DrifterEntity drifter, ServerLevel level, Army army, Role role, RandomSource random) {
		Quality quality = army.quality();
		// Armour, a piece at a time (archers go lighter).
		Quality armour = role == Role.ARCHER && quality != Quality.MILITIA ? Quality.values()[quality.ordinal() - 1] : quality;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			Item piece = piece(slot, armour, random);
			if (piece != null) {
				drifter.setItemSlot(slot, dress(level, new ItemStack(piece), army, random));
			}
		}

		// The captain: the army's banner on its head, as a raid captain wears one.
		if (role == Role.CAPTAIN) {
			drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BannerBlock.byColor(army.colour()).asItem()));
		}

		// Its weapon: its favourite kind (modded too) at the army's grade, a grade better for the captain.
		Weapons.Tier tier = weaponTier(quality, role, random);
		Weapons.Family family = Weapons.favorite(drifter);
		Item weapon = family.at(tier);
		boolean twoHanded = weapon != null && family.twoHanded();
		if (weapon == null) {
			weapon = random.nextInt(10) < 7 ? sword(tier) : axe(tier);
			twoHanded = false;
		}

		ItemStack blade = wear(new ItemStack(weapon), random);
		if (quality == Quality.VETERANS && random.nextInt(3) == 0 || role == Role.CAPTAIN && quality != Quality.MILITIA) {
			enchant(level, blade, Enchantments.SHARPNESS, 1 + random.nextInt(2));
		}

		var inventory = drifter.getInventory();
		inventory.add(blade);
		if (!twoHanded) {
			drifter.setItemSlot(EquipmentSlot.OFFHAND, shield(army, random));
		}

		// A bow: a quiverful for an archer, a few arrows for anyone else (for when it can't get through).
		inventory.add(wear(new ItemStack(Items.BOW), random));
		inventory.add(new ItemStack(Items.ARROW, role == Role.ARCHER ? 32 + random.nextInt(33) : 8 + random.nextInt(9)));

		// Food from home, a pickaxe and blocks for getting about, and the captain's war chest.
		Item food = List.of(Items.BREAD, Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.BAKED_POTATO, Items.COOKED_MUTTON, Items.APPLE).get(random.nextInt(6));
		inventory.add(new ItemStack(food, 6 + random.nextInt(9)));
		inventory.add(wear(new ItemStack(quality == Quality.MILITIA ? Items.STONE_PICKAXE : Items.IRON_PICKAXE), random));
		inventory.add(new ItemStack(random.nextBoolean() ? Items.COBBLESTONE : Items.DIRT, 16 + random.nextInt(17)));
		if (role == Role.CAPTAIN) {
			inventory.add(new ItemStack(Items.EMERALD, 4 + random.nextInt(7) + 4 * quality.ordinal()));
		}
	}

	/** A piece of armour for this slot at this standard, or none (now and then a bare head or legs). */
	@Nullable
	private static Item piece(EquipmentSlot slot, Quality quality, RandomSource random) {
		int roll = random.nextInt(100);
		String material = switch (quality) {
			case MILITIA -> roll < 12 ? null : roll < 70 ? "leather" : roll < 90 ? "chain" : "iron";
			case REGULARS -> roll < 5 ? null : roll < 20 ? "leather" : roll < 55 ? "chain" : roll < 95 ? "iron" : roll < 98 ? "gold" : "diamond";
			case VETERANS -> roll < 10 ? "chain" : roll < 63 ? "iron" : roll < 68 ? "gold" : "diamond";
		};
		if (material == null) {
			return null;
		}

		return switch (slot) {
			case HEAD -> switch (material) {
				case "leather" -> Items.LEATHER_HELMET;
				case "chain" -> Items.CHAINMAIL_HELMET;
				case "iron" -> Items.IRON_HELMET;
				case "gold" -> Items.GOLDEN_HELMET;
				default -> Items.DIAMOND_HELMET;
			};
			case CHEST -> switch (material) {
				case "leather" -> Items.LEATHER_CHESTPLATE;
				case "chain" -> Items.CHAINMAIL_CHESTPLATE;
				case "iron" -> Items.IRON_CHESTPLATE;
				case "gold" -> Items.GOLDEN_CHESTPLATE;
				default -> Items.DIAMOND_CHESTPLATE;
			};
			case LEGS -> switch (material) {
				case "leather" -> Items.LEATHER_LEGGINGS;
				case "chain" -> Items.CHAINMAIL_LEGGINGS;
				case "iron" -> Items.IRON_LEGGINGS;
				case "gold" -> Items.GOLDEN_LEGGINGS;
				default -> Items.DIAMOND_LEGGINGS;
			};
			default -> switch (material) {
				case "leather" -> Items.LEATHER_BOOTS;
				case "chain" -> Items.CHAINMAIL_BOOTS;
				case "iron" -> Items.IRON_BOOTS;
				case "gold" -> Items.GOLDEN_BOOTS;
				default -> Items.DIAMOND_BOOTS;
			};
		};
	}

	/** In the army's colours: leather dyed, metal trimmed; a veteran's chestplate now and then enchanted; worn. */
	private static ItemStack dress(ServerLevel level, ItemStack stack, Army army, RandomSource random) {
		if (stack.is(net.minecraft.tags.ItemTags.DYEABLE)) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(army.colour().getTextureDiffuseColor(), false));
		} else if (army.trim() != null && army.pattern() != null) {
			stack.set(DataComponents.TRIM, new ArmorTrim(army.trim(), army.pattern(), false));
		}

		if (army.quality() == Quality.VETERANS && random.nextInt(4) == 0) {
			enchant(level, stack, Enchantments.PROTECTION, 1 + random.nextInt(2));
		}

		return wear(stack, random);
	}

	/** A shield in the army's colour (militia: now and then a crude Spartan Shields one, with that mod). */
	private static ItemStack shield(Army army, RandomSource random) {
		if (army.quality() == Quality.MILITIA && !com.dinglebob.drifter.hands.Shields.CRUDE_SHIELD.isEmpty() && random.nextBoolean()) {
			return wear(new ItemStack(com.dinglebob.drifter.hands.Shields.CRUDE_SHIELD.get(0)), random);
		}

		ItemStack shield = new ItemStack(Items.SHIELD);
		shield.set(DataComponents.BASE_COLOR, army.colour());
		return wear(shield, random);
	}

	private static Weapons.Tier weaponTier(Quality quality, Role role, RandomSource random) {
		int grade = switch (quality) {
			case MILITIA -> random.nextInt(5) == 0 ? 0 : 1;
			case REGULARS -> random.nextInt(4) == 0 ? 1 : 2;
			case VETERANS -> random.nextInt(3) == 0 ? 3 : 2;
		};
		if (role == Role.CAPTAIN) {
			grade = Math.min(3, grade + 1);
		}

		return switch (grade) {
			case 0 -> Weapons.Tier.WOOD;
			case 1 -> Weapons.Tier.STONE;
			case 2 -> Weapons.Tier.IRON;
			default -> Weapons.Tier.DIAMOND;
		};
	}

	private static Item sword(Weapons.Tier tier) {
		return switch (tier) {
			case WOOD -> Items.WOODEN_SWORD;
			case STONE -> Items.STONE_SWORD;
			case IRON -> Items.IRON_SWORD;
			default -> Items.DIAMOND_SWORD;
		};
	}

	private static Item axe(Weapons.Tier tier) {
		return switch (tier) {
			case WOOD -> Items.WOODEN_AXE;
			case STONE -> Items.STONE_AXE;
			case IRON -> Items.IRON_AXE;
			default -> Items.DIAMOND_AXE;
		};
	}

	/** Been through a fight or two: a tenth to half of its durability gone. */
	private static ItemStack wear(ItemStack stack, RandomSource random) {
		if (stack.isDamageableItem()) {
			stack.setDamageValue((int)(stack.getMaxDamage() * (0.1F + random.nextFloat() * 0.4F)));
		}

		return stack;
	}

	private static void enchant(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key, int lvl) {
		Optional<Holder.Reference<Enchantment>> enchantment = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key);
		enchantment.ifPresent(holder -> stack.enchant(holder, lvl));
	}
}
