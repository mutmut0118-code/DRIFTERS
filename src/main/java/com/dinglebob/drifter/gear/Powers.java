package com.dinglebob.drifter.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Item powers (spec goal 6, first layer): what an item's hover text hints it does, read the way a player reads a tooltip,
 * matched against the power words in {@code drifter_gear/powers.json}. Only knowledgeable Drifters read it
 * ({@link GearLore}); everyone learns by use ({@link PowerWatch}). Two things are kept apart: what is believed of the
 * item (its own text, and what was seen in use: {@link GearLore#beliefs}), and what one stack does of its own (its
 * enchantments; its lore or custom name: {@link #stackOwn}), which is read off the stack each time and never stored.
 * {@link #believed} is both together, for acting on.
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

	/** Runs of spaces and line breaks (a wrapped tooltip), read as one space. */
	private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("[\\s\\u00A0]+");

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

	/** Where a power of the stack itself comes from: an enchantment on it, or what its lore or custom name says. */
	public enum Own {
		ENCHANT, LORE
	}

	/**
	 * The powers the plain item's text hints at (its name and description; cached per item per reload). What this one stack
	 * adds of its own (enchantments, lore, a custom name) is {@link #stackOwn}: never part of what is believed of the item.
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

		return base;
	}

	/** The powers this stack's enchantments give it (flame or fire aspect: fire; multishot), plain to anyone who holds it. */
	public static Set<Power> enchanted(ItemStack stack) {
		EnumSet<Power> out = null;
		for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
			Power power = enchantment.is(Enchantments.FLAME) || enchantment.is(Enchantments.FIRE_ASPECT) ? Power.FIRE
				: enchantment.is(Enchantments.MULTISHOT) ? Power.MULTISHOT : null;
			if (power != null) {
				if (out == null) {
					out = EnumSet.noneOf(Power.class);
				}

				out.add(power);
			}
		}

		return out == null ? Set.of() : out;
	}

	/** The powers this stack's lore and custom name hint at (read fresh: they belong to this one stack). */
	public static Set<Power> written(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null && lore.lines().isEmpty()) {
			lore = null;
		}

		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		if (lore == null && custom == null) {
			return Set.of();
		}

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

		return match(text.toString(), stack.getItem());
	}

	/**
	 * What this stack does of its own, apart from its item: its enchantments' powers always, and what its lore and custom
	 * name hint at when the one holding it reads text ({@link #readsText}). Never stored as a belief about the item: another
	 * stack of the same item may have none of it.
	 */
	public static Map<Power, Own> stackOwn(ItemStack stack, boolean readsText) {
		if (stack.isEmpty()) {
			return Map.of();
		}

		Set<Power> enchanted = enchanted(stack);
		Set<Power> written = readsText ? written(stack) : Set.of();
		if (enchanted.isEmpty() && written.isEmpty()) {
			return Map.of();
		}

		EnumMap<Power, Own> out = new EnumMap<>(Power.class);
		for (Power power : written) {
			out.put(power, Own.LORE);
		}

		for (Power power : enchanted) {
			out.put(power, Own.ENCHANT);
		}

		return out;
	}

	/** It knows enough to read an item's text (a newbie does not). */
	public static boolean readsText(DrifterEntity d) {
		return d.getPersonality().get(Trait.KNOWLEDGE) >= GearData.tables().powers().readTextKnowledge();
	}

	/**
	 * What this Drifter believes this stack does, to act on: the beliefs about its item it is sure enough of
	 * ({@link GearLore#believes}), and the stack's own powers ({@link #stackOwn}).
	 */
	public static Set<Power> believed(DrifterEntity d, ItemStack stack) {
		if (stack.isEmpty()) {
			return Set.of();
		}

		EnumSet<Power> out = EnumSet.noneOf(Power.class);
		for (Map.Entry<Power, GearLore.Belief> entry : d.getGearLore().beliefs(stack.getItem()).entrySet()) {
			if (entry.getValue().confidence() >= GearLore.ACT_ON) {
				out.add(entry.getKey());
			}
		}

		out.addAll(stackOwn(stack, readsText(d)).keySet());
		return out;
	}

	/** It believes this stack has this power ({@link #believed}). */
	public static boolean believes(DrifterEntity d, ItemStack stack, Power power) {
		if (stack.isEmpty()) {
			return false;
		}

		return d.getGearLore().believes(stack.getItem(), power) || stackOwn(stack, readsText(d)).containsKey(power);
	}

	/**
	 * The plain item's own hover text (its name and what its {@code appendHoverText} adds), and its description translations.
	 * Not {@code ItemStack.getTooltipLines}: in a player's own game that also runs every client mod's tooltip callbacks, here
	 * on the server thread.
	 */
	private static String itemText(@Nullable Level level, Item item) {
		StringBuilder text = new StringBuilder();
		try {
			ItemStack plain = new ItemStack(item);
			text.append(plain.getHoverName().getString()).append('\n');
			List<Component> lines = new ArrayList<>();
			item.appendHoverText(plain, Item.TooltipContext.of(level), lines, TooltipFlag.NORMAL);
			for (Component line : lines) {
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
		// One line of it: a mod that wraps its text puts "fire" and "at nearby targets" on different lines.
		String text = WHITESPACE.matcher(raw.toLowerCase(Locale.ROOT)).replaceAll(" ");
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
