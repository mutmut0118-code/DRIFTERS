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

import com.google.gson.JsonObject;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
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

	/**
	 * The powers this stack's enchantments give it (flame or fire aspect: fire; multishot; channeling: lightning, in a storm),
	 * plain to anyone who holds it. None of them is ruled (R27; lightning has no rule), so they never hold its fire.
	 */
	public static Set<Power> enchanted(ItemStack stack) {
		EnumSet<Power> out = null;
		for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
			Power power = enchantment.is(Enchantments.FLAME) || enchantment.is(Enchantments.FIRE_ASPECT) ? Power.FIRE
				: enchantment.is(Enchantments.MULTISHOT) ? Power.MULTISHOT : enchantment.is(Enchantments.CHANNELING) ? Power.LIGHTNING : null;
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

	/**
	 * A vanilla item: no hidden powers to believe in (what an enchantment on one does is read from the stack). No watch, text
	 * guess, tip or saved belief is ever about one: nothing in play could correct a false one.
	 */
	public static boolean vanilla(Item item) {
		return "minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace());
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

	// Using powers sensibly (spec goal 6, third layer; anti-goal: "powers used recklessly in ways that hurt its friends or
	// home once it knows better"). Only what it believes counts ({@link #believed}): a newbie that does not know its bow
	// explodes may still learn the hard way.

	/** An explosive power is never used on a target this close to itself (blocks). */
	public static final double SAFE_BLAST = 5.0;
	/** Nor with a friend this close to the target. */
	public static final double BLAST_RADIUS = 4.0;
	/** Neither explosive nor fire where it would land this close to its own home's center (mostly wood, until it isn't). */
	public static final double HOME_GUARD = 16.0;
	/** Fire is not used with a friend this close to the target. */
	public static final double FIRE_FRIEND = 2.0;
	/** An area power is for a crowd: this many other hostiles within {@link #CROWD_RADIUS} of the target. */
	public static final int CROWD_COUNT = 2;
	public static final double CROWD_RADIUS = 4.0;
	public static final double AREA_CROWD_BONUS = 0.3;
	public static final double AREA_LONE_PENALTY = 0.2;
	/** The choosers' multiplier for something it may not use here, and the range of {@link #fit}. */
	public static final double UNSAFE_FIT = 0.2;
	public static final double MAX_FIT = 1.3;
	/** A friend last seen this long ago (ticks) is still taken to be where it was. */
	private static final long FRIEND_SEEN_TICKS = 60L;
	/** A belief this sure, of an item it owns or has used, is worth passing on in talk. */
	public static final float TIP_CONFIDENCE = 0.6F;

	/**
	 * It may use this stack's power on this target (null: no target in mind) as far as it knows: explosive not on a target
	 * within {@link #SAFE_BLAST} of itself, not with a friend within {@link #BLAST_RADIUS} of it, not near its home; fire not
	 * near its home nor with a friend within {@link #FIRE_FRIEND} of the target (not for fire that only comes from a Flame or
	 * Fire Aspect enchantment, R27). With no target in mind there is no rule (R26). Cheap: nothing is looked up for a stack
	 * it believes nothing of.
	 */
	public static boolean mayUse(DrifterEntity d, ItemStack s, @Nullable LivingEntity target) {
		return whyNot(d, s, target) == null;
	}

	/** Why it may not ({@link #mayUse}): the friend's name, "itself" or "home"; null when it may. */
	@Nullable
	public static String whyNot(DrifterEntity d, ItemStack s, @Nullable LivingEntity target) {
		// (No target in mind, nothing lands anywhere: no rule. Ruling R26: "how strong is it" asks for gear stay unchanged.)
		if (target == null || !mayHavePower(d, s)) {
			return null;
		}

		return whyNot(d, target, ruled(d, s));
	}

	private static final int RULE_EXPLOSIVE = 1;
	private static final int RULE_FIRE = 2;
	private static final int RULE_AREA = 4;

	/**
	 * The powers the rules look at, in one pass: from what it believes of the item (at {@link GearLore#ACT_ON}) and from what
	 * this stack's lore or name says (for one who reads). Not an enchantment's: Flame and Fire Aspect set no blocks alight
	 * and spread to no bystander (ruling R27), and the other enchantments carry no ruled power. Fire believed of the item or
	 * written on the stack keeps the rules even when an enchantment gives fire too.
	 */
	private static int ruled(DrifterEntity d, ItemStack s) {
		int bits = 0;
		for (Map.Entry<Power, GearLore.Belief> entry : d.getGearLore().beliefs(s.getItem()).entrySet()) {
			if (entry.getValue().confidence() >= GearLore.ACT_ON) {
				bits |= bit(entry.getKey());
			}
		}

		if (hasWriting(s) && readsText(d)) {
			for (Power power : written(s)) {
				bits |= bit(power);
			}
		}

		return bits;
	}

	private static int bit(Power power) {
		return switch (power) {
			case EXPLOSIVE -> RULE_EXPLOSIVE;
			case FIRE -> RULE_FIRE;
			case AREA -> RULE_AREA;
			default -> 0;
		};
	}

	@Nullable
	private static String whyNot(DrifterEntity d, LivingEntity target, int rules) {
		boolean explosive = (rules & RULE_EXPLOSIVE) != 0;
		boolean fire = (rules & RULE_FIRE) != 0;
		if (!explosive && !fire) {
			return null;
		}

		if (explosive && target.distanceToSqr(d) < SAFE_BLAST * SAFE_BLAST) {
			return "itself";
		}

		String friend = friendNear(d, target, explosive ? BLAST_RADIUS : FIRE_FRIEND);
		if (friend != null) {
			return friend;
		}

		// Where it would go off: at the target.
		com.dinglebob.drifter.brain.Home home = d.getDrifterBrain().home();
		if (home != null && home.dimension.equals(d.level().dimension().location()) && home.center.closerToCenterThan(target.position(), HOME_GUARD)) {
			return "home";
		}

		return null;
	}

	/** "would blow up Pal", "would burn home": the end of its "held fire" thought for this stack. */
	public static String heldFireWords(DrifterEntity d, ItemStack s, String why) {
		return ((ruled(d, s) & RULE_EXPLOSIVE) != 0 ? "would blow up " : "would burn ") + why;
	}

	/**
	 * How well this stack's powers suit this target, as it believes (multiplies a chooser's score, {@link #UNSAFE_FIT} to
	 * {@link #MAX_FIT}): one it may not use here {@link #UNSAFE_FIT}; an area power +{@link #AREA_CROWD_BONUS} with a crowd
	 * round the target, -{@link #AREA_LONE_PENALTY} against a lone one; otherwise 1.
	 */
	public static double fit(DrifterEntity d, ItemStack s, @Nullable LivingEntity target) {
		// (No target in mind: ruling R26, 1.)
		if (target == null || !mayHavePower(d, s)) {
			return 1.0;
		}

		int rules = ruled(d, s);
		if (rules == 0) {
			return 1.0;
		}

		if (whyNot(d, target, rules) != null) {
			return UNSAFE_FIT;
		}

		double fit = 1.0;
		if ((rules & RULE_AREA) != 0) {
			int crowd = hostilesNear(d, target, CROWD_RADIUS);
			fit += crowd >= CROWD_COUNT ? AREA_CROWD_BONUS : crowd == 0 ? -AREA_LONE_PENALTY : 0.0;
		}

		return Math.max(UNSAFE_FIT, Math.min(MAX_FIT, fit));
	}

	/**
	 * A rule could apply to this stack: it believes something of its item, or the stack has lore or a name of its own. (An
	 * enchantment alone never does: the only powers enchantments give, enchanted fire and multishot, have no rule, R27; so a
	 * plain enchanted tool returns here at once.)
	 */
	private static boolean mayHavePower(DrifterEntity d, ItemStack s) {
		return !s.isEmpty() && (!d.getGearLore().beliefs(s.getItem()).isEmpty() || hasWriting(s));
	}

	/** The stack has a custom name or lore lines. */
	private static boolean hasWriting(ItemStack s) {
		if (s.get(DataComponents.CUSTOM_NAME) != null) {
			return true;
		}

		ItemLore lore = s.get(DataComponents.LORE);
		return lore != null && !lore.lines().isEmpty();
	}

	/** A friend it knows to be within {@code radius} of the target (from those it watches: no look round the world), or null. */
	@Nullable
	private static String friendNear(DrifterEntity d, LivingEntity target, double radius) {
		long now = d.level().getGameTime();
		double most = radius * radius;
		for (com.dinglebob.drifter.perception.Awareness seen : d.getSenses().trackedView()) {
			if (seen.entity == d || seen.entity == target || !seen.entity.isAlive() || !(seen.visibleNow || seen.seenWithin(now, FRIEND_SEEN_TICKS))) {
				continue;
			}

			net.minecraft.world.phys.Vec3 at = seen.visibleNow || seen.lastSeenPos == null ? seen.entity.position() : seen.lastSeenPos;
			if (at.distanceToSqr(target.position()) <= most && isFriend(d, seen.entity)) {
				return seen.entity.getName().getString();
			}
		}

		// One it saw there a little while ago and has not seen leave (its memory of who stood where, written by its senses:
		// a friend standing still is slow to be noticed again once it looked away).
		if (d.level() instanceof net.minecraft.server.level.ServerLevel level) {
			net.minecraft.resources.ResourceLocation dimension = level.dimension().location();
			for (com.dinglebob.drifter.memory.MemoryKind kind : FRIEND_KINDS) {
				com.dinglebob.drifter.memory.MemoryEntry entry = d.getMemory().nearest(kind, dimension, target.position(), radius, remembered -> {
					if (remembered.entityId == null || now - remembered.confirmedAt > FRIEND_REMEMBER_TICKS || remembered.entityId.equals(d.getUUID())
						|| remembered.entityId.equals(target.getUUID())) {
						return false;
					}

					net.minecraft.world.entity.Entity who = level.getEntity(remembered.entityId);
					return who != null && who.isAlive() && isFriend(d, who);
				});
				if (entry != null) {
					net.minecraft.world.entity.Entity friend = level.getEntity(entry.entityId);
					return friend == null ? entry.label : friend.getName().getString();
				}
			}
		}

		return null;
	}

	/** Who it remembers standing somewhere: a player, or another mob (a Drifter is one). */
	private static final com.dinglebob.drifter.memory.MemoryKind[] FRIEND_KINDS = {com.dinglebob.drifter.memory.MemoryKind.PLAYER,
		com.dinglebob.drifter.memory.MemoryKind.MOB};
	/** A friend it saw by the target this long ago (ticks) is still taken to be there. */
	private static final long FRIEND_REMEMBER_TICKS = 20L * 20;

	/** On its side: its group or battle side, a Drifter it trusts as a friend, or a player it likes. */
	private static boolean isFriend(DrifterEntity d, net.minecraft.world.entity.Entity other) {
		if (other instanceof DrifterEntity drifter) {
			if (com.dinglebob.drifter.hands.Archery.friends(d, drifter)) {
				return true;
			}

			com.dinglebob.drifter.social.Relationship bond = d.getBonds().get(drifter.getUUID());
			return bond != null && bond.trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND;
		}

		if (other instanceof net.minecraft.world.entity.player.Player player) {
			com.dinglebob.drifter.social.Relationship r = d.getSocial().get(player.getUUID());
			return r != null && r.trust >= com.dinglebob.drifter.social.Mode.FRIENDLY_TRUST;
		}

		return false;
	}

	/** Other hostiles it sees within {@code radius} of the target. */
	private static int hostilesNear(DrifterEntity d, LivingEntity target, double radius) {
		int count = 0;
		double most = radius * radius;
		for (com.dinglebob.drifter.perception.Awareness seen : d.getSenses().trackedView()) {
			if (seen.visibleNow && seen.entity != target && seen.entity.isAlive() && seen.entity instanceof LivingEntity living
				&& (living instanceof net.minecraft.world.entity.monster.Enemy || living instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == d)
				&& living.distanceToSqr(target) <= most) {
				count++;
			}
		}

		return count;
	}

	// Simply Swords items no weapon file lists: when to use the ability, from what it believes the item does.
	private static final JsonObject WHEN_HURT = when("hurt", "health", 0.5);
	private static final JsonObject WHEN_CROWD = when("crowd", "count", 2);
	private static final JsonObject WHEN_FIGHT = when("fight", "max", 6.0);

	private static JsonObject when(String when, String key, Number value) {
		JsonObject entry = new JsonObject();
		entry.addProperty("when", when);
		entry.addProperty(key, value);
		return entry;
	}

	/**
	 * A generic ability entry (the weapon file's {@code ability} shape, read only) for a Simply Swords item no file lists:
	 * healing when hurt (health at half); a cooldown or area power saved for a crowd of two; otherwise any time in a fight.
	 */
	@Nullable
	public static JsonObject abilityFor(DrifterEntity d, ItemStack s) {
		if (s.isEmpty()) {
			return null;
		}

		Set<Power> powers = believed(d, s);
		if (powers.contains(Power.HEAL)) {
			return WHEN_HURT;
		}

		if (powers.contains(Power.COOLDOWN) || powers.contains(Power.AREA)) {
			return WHEN_CROWD;
		}

		return WHEN_FIGHT;
	}

	/** A power it could pass on in talk: an item, a power, as it would say them. */
	public record Tip(Item item, Power power, float confidence, GearLore.Source source) {
		/** "winterfang": the item's name in chat (no id). */
		public String name() {
			return GearLore.nameOf(this.item).toLowerCase(Locale.ROOT);
		}

		/** "explodes", "sets things on fire". */
		public String words() {
			return switch (this.power) {
				case EXPLOSIVE -> "explodes";
				case FIRE -> "sets things on fire";
				case LIGHTNING -> "calls down lightning";
				case POISON -> "poisons whatever it hits";
				case WITHER -> "withers whatever it hits";
				case FREEZE -> "freezes things";
				case HEAL -> "heals you";
				case MULTISHOT -> "shoots a few at once";
				case AREA -> "hits everything around";
				case PULL -> "pulls them in";
				case TELEPORT -> "teleports you";
				case COOLDOWN -> "needs a while to recharge";
			};
		}
	}

	/**
	 * What it could tell the other of what an item does: a belief about an item (never a stack's own enchantment or lore: a
	 * tip "the bow explodes" from one lore'd bow would be about every bow) at {@link #TIP_CONFIDENCE} or more, read, seen or
	 * told, of an item it owns or has used, that the other does not believe yet. The surest one; null if none.
	 */
	@Nullable
	public static Tip tip(DrifterEntity me, @Nullable DrifterEntity other) {
		GearLore lore = me.getGearLore();
		Tip best = null;
		for (Map.Entry<Item, Map<Power, GearLore.Belief>> entry : lore.allBeliefs().entrySet()) {
			Item item = entry.getKey();
			if (vanilla(item)) {
				continue;
			}

			Boolean has = null;
			for (Map.Entry<Power, GearLore.Belief> held : entry.getValue().entrySet()) {
				GearLore.Belief belief = held.getValue();
				if (belief.confidence() < TIP_CONFIDENCE || belief.source() == GearLore.Source.DATA || best != null && belief.confidence() <= best.confidence()
					|| other != null && other.getGearLore().believes(item, held.getKey())) {
					continue;
				}

				if (belief.source() != GearLore.Source.SEEN) {
					// (Seen: only ever in its own use.) Read or told: of something it owns.
					if (has == null) {
						has = lore.how(me, item) == GearLore.How.OWNED || me.getInventory().count(stack -> stack.is(item)) > 0;
					}

					if (!has) {
						continue;
					}
				}

				best = new Tip(item, held.getKey(), belief.confidence(), belief.source());
			}
		}

		return best;
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
