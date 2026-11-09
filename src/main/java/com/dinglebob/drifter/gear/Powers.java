package com.dinglebob.drifter.gear;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Item powers (spec goal 6, first layer): what an item's hover text hints it does, read the way a player reads a tooltip,
 * matched against the power words in {@code drifter_gear/powers.json}. Only knowledgeable Drifters read it
 * ({@link GearLore}); everyone learns by use ({@link PowerWatch}).
 *
 * <p>Many mods build their hover text with client-only code (shift-to-show lines, {@code I18n}); on a dedicated server, or
 * the integrated server's thread, that throws (even {@link NoClassDefFoundError}). Such an item just gets no text guess:
 * the failure is caught, logged once per item at debug level, and the item is still valued and used (Review Focus 1).
 */
public final class Powers {
	public enum Power {
		EXPLOSIVE, FIRE, LIGHTNING, POISON, WITHER, FREEZE, HEAL, MULTISHOT, AREA, PULL, TELEPORT, COOLDOWN;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Power byId(String id) {
			for (Power power : values()) {
				if (power.id().equals(id)) {
					return power;
				}
			}

			return null;
		}
	}

	/** Translation keys a mod may keep an item's description under, after its description id. */
	private static final List<String> DESCRIPTION_KEYS = List.of(".tooltip", ".desc", ".description", ".tooltip.ability", ".tooltip.0", ".tooltip.1",
		".tooltip.2", ".tooltip.3");

	/** Each item's guess from its plain stack's text, per reload. */
	private static final Map<Item, Set<Power>> CACHE = new ConcurrentHashMap<>();
	/** Items whose hover text could not be read, already logged (once each). */
	private static final Set<Item> FAILED = ConcurrentHashMap.newKeySet();

	private Powers() {
	}

	static void clear() {
		CACHE.clear();
		FAILED.clear();
	}

	/** Debug: how many items' hover text could not be read since the last reload. */
	public static int unreadable() {
		return FAILED.size();
	}

	/**
	 * The powers this stack's text hints at: the item's own text (cached per item per reload), plus, read fresh, this stack's
	 * lore and custom name, plus its enchantments (flame or fire aspect: fire; multishot).
	 */
	public static Set<Power> guess(@Nullable Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return Set.of();
		}

		Item item = stack.getItem();
		Set<Power> base = CACHE.get(item);
		if (base == null) {
			base = Collections.unmodifiableSet(match(itemText(level, item), item));
			CACHE.put(item, base);
		}

		EnumSet<Power> out = null;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null && lore.lines().isEmpty()) {
			lore = null;
		}

		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		if (lore != null || custom != null) {
			StringBuilder text = new StringBuilder();
			try {
				if (custom != null) {
					text.append(custom.getString()).append('\n');
				}

				if (lore != null) {
					for (Component line : lore.lines()) {
						text.append(line.getString()).append('\n');
					}
				}
			} catch (RuntimeException e) {
				// a broken component: what was read so far is used
			}

			out = base.isEmpty() ? EnumSet.noneOf(Power.class) : EnumSet.copyOf(base);
			out.addAll(match(text.toString(), item));
		}

		for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
			Power power = enchantment.is(Enchantments.FLAME) || enchantment.is(Enchantments.FIRE_ASPECT) ? Power.FIRE
				: enchantment.is(Enchantments.MULTISHOT) ? Power.MULTISHOT : null;
			if (power != null && !base.contains(power)) {
				if (out == null) {
					out = base.isEmpty() ? EnumSet.noneOf(Power.class) : EnumSet.copyOf(base);
				}

				out.add(power);
			}
		}

		return out == null ? base : Collections.unmodifiableSet(out);
	}

	/** The plain item's hover text (its name and description, attribute lines hidden), and its description translations. */
	private static String itemText(@Nullable Level level, Item item) {
		StringBuilder text = new StringBuilder();
		try {
			ItemStack plain = new ItemStack(item);
			// "+4 Max Health" and the like are numbers, not powers
			plain.set(DataComponents.ATTRIBUTE_MODIFIERS, plain.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).withTooltip(false));
			for (Component line : plain.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL)) {
				text.append(line.getString()).append('\n');
			}
		} catch (Throwable t) {
			// Client-only hover text (Review Focus 1): no guess from it, and no spam.
			if (FAILED.add(item)) {
				Drifter.LOGGER.debug("[Drifter] couldn't read the hover text of {} ({}); no text guess for it", BuiltInRegistries.ITEM.getKey(item), t.toString());
			}
		}

		try {
			Language language = Language.getInstance();
			String descriptionId = item.getDescriptionId();
			for (String suffix : DESCRIPTION_KEYS) {
				String key = descriptionId + suffix;
				if (language.has(key)) {
					text.append(language.getOrDefault(key)).append('\n');
				}
			}
		} catch (Throwable t) {
			if (FAILED.add(item)) {
				Drifter.LOGGER.debug("[Drifter] couldn't read the description of {} ({})", BuiltInRegistries.ITEM.getKey(item), t.toString());
			}
		}

		return text.toString();
	}

	/**
	 * The powers whose words start a word in the text ("explod" in "Explodes"; "ice" not in "price"). The item's namespace in
	 * a key is left out first (a server without the mod's translations shows keys such as "item.iceandfire.sword"), and so
	 * are the {@code except} phrases.
	 */
	static EnumSet<Power> match(String raw, Item item) {
		EnumSet<Power> found = EnumSet.noneOf(Power.class);
		if (raw.isEmpty()) {
			return found;
		}

		GearData.PowerRules rules = GearData.tables().powers();
		String text = raw.toLowerCase(Locale.ROOT);
		String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
		if (!"minecraft".equals(namespace)) {
			// only as part of a key ("item.iceandfire.sword"): a mod called "storm" may still say "storm" in its text
			text = text.replace("." + namespace + ".", " ").replace(namespace + ".", " ").replace(namespace + ":", " ");
		}

		for (String phrase : rules.except()) {
			text = text.replace(phrase, " ");
		}

		for (Map.Entry<Power, List<String>> entry : rules.words().entrySet()) {
			for (String word : entry.getValue()) {
				if (startsWord(text, word)) {
					found.add(entry.getKey());
					break;
				}
			}
		}

		return found;
	}

	private static boolean startsWord(String text, String word) {
		if (word.isEmpty()) {
			return false;
		}

		for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
			if (at == 0 || !Character.isLetter(text.charAt(at - 1))) {
				return true;
			}
		}

		return false;
	}
}
