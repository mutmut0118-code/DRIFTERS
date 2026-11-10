package com.dinglebob.drifter.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Trinkets, when it's installed (optional, detected at runtime, never required): the Drifter's trinket slots (declared in
 * {@code data/trinkets/entities/drifter.json}), whether an item fits one, and what it gives there. Read through reflection,
 * not a compile-only dependency: Trinkets' types extend Cardinal Components interfaces, which would have to be on the
 * classpath too. If anything about its API changes in an update, this switches itself off (one warning) and Drifters
 * simply don't wear trinkets. Wearing one and getting its attribute modifiers is all this does: effects a mod only runs for
 * real players may not happen on a Drifter.
 */
public final class TrinketsCompat {
	private TrinketsCompat() {
	}

	/** One trinket slot of an entity: a slot type ({@code group/name}) and which of its places ({@code index}). */
	public record Slot(String group, String name, int index, Container inventory) {
		public String id() {
			return this.group + "/" + this.name;
		}

		public ItemStack worn() {
			return this.inventory.getItem(this.index);
		}
	}

	/** Armor, max health, movement speed and attack damage given by a trinket count this much per point; anything else 1. */
	public static final double ARMOR_WEIGHT = 10.0;
	public static final double MAX_HEALTH_WEIGHT = 4.0;
	public static final double SPEED_WEIGHT = 100.0;
	public static final double DAMAGE_WEIGHT = 3.0;
	public static final double OTHER_WEIGHT = 1.0;
	/** The slots {@code data/trinkets/entities/drifter.json} gives it (used when Trinkets' own list can't be read yet). */
	private static final List<String> DEFAULT_SLOT_IDS = List.of("head/hat", "head/face", "chest/necklace", "chest/back", "chest/cape", "legs/belt",
		"hand/glove", "hand/ring", "offhand/glove", "offhand/ring", "feet/shoes", "feet/aglet");

	private static boolean ready;
	private static boolean failed;
	private static Method getTrinketComponent;
	private static Method getInventory;
	private static Method getSlotType;
	private static Method getValidatorPredicates;
	private static Method evaluatePredicateSet;
	private static Method getTrinket;
	private static Method canEquip;
	private static Method canUnequip;
	private static Method getDropRule;
	/** TrinketEnums.DropRule.DESTROY and DEFAULT. */
	private static Object destroy;
	private static Object useDefault;
	private static Method getModifiers;
	private static Method slotIdentifier;
	private static Method getEntitySlots;
	private static Method groupSlots;
	private static Method slotTypeId;
	private static Constructor<?> slotReference;
	/** dev.emi.trinkets.TrinketModifiers#get, optional (not API): the trinket's modifiers and the stack's own trinket modifier component. */
	@Nullable
	private static Method allModifiers;

	/** Whether each item fits one of the Drifter's slot types by Trinkets' tags (per reload). */
	private static final Map<Item, Boolean> TRINKET_ITEMS = new ConcurrentHashMap<>();
	/** The tags of the Drifter's slot types, plus {@code trinkets:all} (per reload). */
	@Nullable
	private static volatile List<TagKey<Item>> slotTags;
	/** Items whose trinket code threw for a Drifter (logged once each). */
	private static final Set<Item> BROKEN = ConcurrentHashMap.newKeySet();

	/** Trinkets is in the mod list (whether or not its API matched). */
	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("trinkets");
	}

	public static boolean active() {
		if (ready || failed) {
			return ready;
		}

		if (!installed()) {
			failed = true;
			return false;
		}

		try {
			Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
			Class<?> component = Class.forName("dev.emi.trinkets.api.TrinketComponent");
			Class<?> inventory = Class.forName("dev.emi.trinkets.api.TrinketInventory");
			Class<?> slotType = Class.forName("dev.emi.trinkets.api.SlotType");
			Class<?> slotGroup = Class.forName("dev.emi.trinkets.api.SlotGroup");
			Class<?> reference = Class.forName("dev.emi.trinkets.api.SlotReference");
			Class<?> trinket = Class.forName("dev.emi.trinkets.api.Trinket");
			if (!Container.class.isAssignableFrom(inventory)) {
				throw new ClassCastException("TrinketInventory is not a Container");
			}

			getTrinketComponent = api.getMethod("getTrinketComponent", LivingEntity.class);
			getInventory = component.getMethod("getInventory");
			getSlotType = inventory.getMethod("getSlotType");
			getValidatorPredicates = slotType.getMethod("getValidatorPredicates");
			evaluatePredicateSet = api.getMethod("evaluatePredicateSet", Set.class, ItemStack.class, reference, LivingEntity.class);
			getTrinket = api.getMethod("getTrinket", Item.class);
			canEquip = trinket.getMethod("canEquip", ItemStack.class, reference, LivingEntity.class);
			canUnequip = trinket.getMethod("canUnequip", ItemStack.class, reference, LivingEntity.class);
			getModifiers = trinket.getMethod("getModifiers", ItemStack.class, reference, LivingEntity.class, ResourceLocation.class);
			slotIdentifier = Class.forName("dev.emi.trinkets.api.SlotAttributes").getMethod("getIdentifier", reference);
			getEntitySlots = api.getMethod("getEntitySlots", Level.class, EntityType.class);
			groupSlots = slotGroup.getMethod("getSlots");
			slotTypeId = slotType.getMethod("getId");
			slotReference = reference.getConstructor(inventory, int.class);
			try {
				allModifiers = Class.forName("dev.emi.trinkets.TrinketModifiers").getMethod("get", ItemStack.class, reference, LivingEntity.class);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				allModifiers = null;
			}

			ready = true;
			Drifter.LOGGER.info("[Drifter] Trinkets found: Drifters wear trinkets");
			registerDropRule(trinket, reference);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
		}

		return ready;
	}

	/**
	 * Its trinkets drop at death as a player's would (it takes every loss a player does, and no more): Trinkets asks the
	 * trinket's own drop rule, then this callback. A DESTROY the trinket gives only because the wearer is a mob (Artifacts
	 * destroys a mob's worn artifacts when no player killed it, so zombies can't farm them) becomes DEFAULT, so the slot's
	 * rule, Curse of Vanishing and the rest still decide; a DESTROY the trinket would also give a player (asked through the
	 * Drifter's helper player) stays. If this can't be set up, it says so once and trinkets drop by the mods' rules as they are.
	 */
	private static void registerDropRule(Class<?> trinket, Class<?> reference) {
		try {
			Class<?> rule = Class.forName("dev.emi.trinkets.api.TrinketEnums$DropRule");
			Class<?> callback = Class.forName("dev.emi.trinkets.api.event.TrinketDropCallback");
			getDropRule = trinket.getMethod("getDropRule", ItemStack.class, reference, LivingEntity.class);
			destroy = rule.getField("DESTROY").get(null);
			useDefault = rule.getField("DEFAULT").get(null);
			Object listener = java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[] {callback}, (proxy, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return switch (method.getName()) {
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						default -> "Drifter's trinket drop rule";
					};
				}

				return args != null && args.length == 4 ? dropRule(args[0], args[1], args[2], args[3]) : args == null ? null : args[0];
			});
			@SuppressWarnings("unchecked")
			net.fabricmc.fabric.api.event.Event<Object> event = (net.fabricmc.fabric.api.event.Event<Object>)callback.getField("EVENT").get(null);
			event.register(listener);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			getDropRule = null;
			Drifter.LOGGER.warn("[Drifter] Trinkets' drop callback didn't match ({}); a Drifter's trinkets drop by the mods' own rules", e.toString());
		}
	}

	/** The callback itself: {@code rule} so far for this stack in this slot of this entity. Never throws. */
	private static Object dropRule(Object rule, Object stack, Object reference, Object entity) {
		if (rule != destroy || getDropRule == null || !(entity instanceof com.dinglebob.drifter.entity.DrifterEntity drifter) || !(stack instanceof ItemStack item)) {
			return rule;
		}

		try {
			Object trinket = getTrinket.invoke(null, item.getItem());
			// Not the trinket's own rule (another mod's callback, which a player gets too): stays.
			if (getDropRule.invoke(trinket, item, reference, drifter) != destroy) {
				return rule;
			}

			net.minecraft.world.entity.player.Player player = com.dinglebob.drifter.hands.FakePlayerBridge.helper(drifter);
			if (player == null || getDropRule.invoke(trinket, item, reference, player) == destroy) {
				return rule;
			}

			return useDefault;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return rule;
		}
	}

	private static void fail(Throwable e) {
		ready = false;
		failed = true;
		Drifter.LOGGER.warn("[Drifter] Trinkets' API didn't match ({}); Drifters don't wear trinkets", e.toString());
	}

	/** Forget what was worked out from tags (a data reload). */
	public static void clear() {
		TRINKET_ITEMS.clear();
		slotTags = null;
	}

	/**
	 * A trinket that fits one of the Drifter's slot types (by Trinkets' tags; no entity needed). Cached per item. Tags only:
	 * a trinket that some mod lets in only through its own slot predicate (no tag) isn't seen, so it isn't worn.
	 */
	public static boolean isTrinket(ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return false;
		}

		Item item = stack.getItem();
		Boolean known = TRINKET_ITEMS.get(item);
		if (known != null) {
			return known;
		}

		boolean fits = false;
		for (TagKey<Item> tag : tags()) {
			if (stack.is(tag)) {
				fits = true;
				break;
			}
		}

		// (Only once a world is up: before that the tags may not be bound yet.)
		if (slotTags != null) {
			TRINKET_ITEMS.put(item, fits);
		}

		return fits;
	}

	private static List<TagKey<Item>> tags() {
		List<TagKey<Item>> tags = slotTags;
		if (tags != null) {
			return tags;
		}

		List<String> ids = new ArrayList<>();
		Level level = com.dinglebob.drifter.gear.GearData.level();
		if (level != null && ready) {
			try {
				Map<?, ?> groups = (Map<?, ?>)getEntitySlots.invoke(null, level, com.dinglebob.drifter.registry.ModEntities.DRIFTER);
				for (Object group : groups.values()) {
					for (Object type : ((Map<?, ?>)groupSlots.invoke(group)).values()) {
						ids.add((String)slotTypeId.invoke(type));
					}
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				fail(e);
				return List.of();
			}
		}

		if (ids.isEmpty()) {
			ids.addAll(DEFAULT_SLOT_IDS);
		}

		List<TagKey<Item>> built = new ArrayList<>();
		built.add(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("trinkets", "all")));
		for (String id : ids) {
			ResourceLocation location = ResourceLocation.tryBuild("trinkets", id);
			if (location != null) {
				built.add(TagKey.create(Registries.ITEM, location));
			}
		}

		tags = List.copyOf(built);
		if (level != null) {
			slotTags = tags;
		}

		return tags;
	}

	/** Every trinket slot this entity has, in Trinkets' order; empty without Trinkets. */
	public static List<Slot> slots(LivingEntity entity) {
		if (!active()) {
			return List.of();
		}

		try {
			Optional<?> component = (Optional<?>)getTrinketComponent.invoke(null, entity);
			if (component.isEmpty()) {
				return List.of();
			}

			List<Slot> out = new ArrayList<>();
			Map<?, ?> groups = (Map<?, ?>)getInventory.invoke(component.get());
			for (Map.Entry<?, ?> group : groups.entrySet()) {
				for (Map.Entry<?, ?> type : ((Map<?, ?>)group.getValue()).entrySet()) {
					Container inventory = (Container)type.getValue();
					for (int i = 0; i < inventory.getContainerSize(); i++) {
						out.add(new Slot(String.valueOf(group.getKey()), String.valueOf(type.getKey()), i, inventory));
					}
				}
			}

			return out;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
			return List.of();
		}
	}

	/** How many trinkets it wears. */
	public static int wornCount(LivingEntity entity) {
		int count = 0;
		for (Slot slot : slots(entity)) {
			if (!slot.worn().isEmpty()) {
				count++;
			}
		}

		return count;
	}

	/** Trinkets would let this go in that slot (its slot type's rules and the trinket's own canEquip, as its screen asks). */
	public static boolean fits(LivingEntity entity, Slot slot, ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return false;
		}

		try {
			Object reference = slotReference.newInstance(slot.inventory(), slot.index());
			Object predicates = getValidatorPredicates.invoke(getSlotType.invoke(slot.inventory()));
			if (!(boolean)evaluatePredicateSet.invoke(null, predicates, stack, reference, entity)) {
				return false;
			}

			return (boolean)canEquip.invoke(getTrinket.invoke(null, stack.getItem()), stack, reference, entity);
		} catch (InvocationTargetException e) {
			broken(stack, e);
			return false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
			return false;
		}
	}

	/** Trinkets would let this come out of that slot (the trinket's own canUnequip: Curse of Binding, or a mod's rule). */
	public static boolean canUnequip(LivingEntity entity, Slot slot, ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return true;
		}

		try {
			Object reference = slotReference.newInstance(slot.inventory(), slot.index());
			return (boolean)canUnequip.invoke(getTrinket.invoke(null, stack.getItem()), stack, reference, entity);
		} catch (InvocationTargetException e) {
			broken(stack, e);
			return false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
			return false;
		}
	}

	/**
	 * What its attribute modifiers are worth worn in that slot: each positive plain addition, weighted (armor 10, max health 4,
	 * speed 100, attack damage 3, anything else 1); a multiplier counts as the addition it makes to this entity's base value.
	 */
	public static double modifierValue(LivingEntity entity, Slot slot, ItemStack stack) {
		if (!active() || stack.isEmpty()) {
			return 0.0;
		}

		try {
			Object reference = slotReference.newInstance(slot.inventory(), slot.index());
			Object modifiers = allModifiers != null ? allModifiers.invoke(null, stack, reference, entity)
				: getModifiers.invoke(getTrinket.invoke(null, stack.getItem()), stack, reference, entity, slotIdentifier.invoke(null, reference));
			if (!(modifiers instanceof com.google.common.collect.Multimap<?, ?> map)) {
				return 0.0;
			}

			double sum = 0.0;
			for (Map.Entry<?, ?> entry : map.entries()) {
				if (!(entry.getKey() instanceof Holder<?> key) || !(key.value() instanceof Attribute) || !(entry.getValue() instanceof AttributeModifier modifier)) {
					continue;
				}

				@SuppressWarnings("unchecked")
				Holder<Attribute> attribute = (Holder<Attribute>)key;
				double amount = modifier.amount();
				if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
					amount *= entity.getAttributes().hasAttribute(attribute) ? entity.getAttributeBaseValue(attribute) : 0.0;
				}

				if (!(amount > 0.0)) {
					continue;
				}

				sum += amount * weight(attribute);
			}

			return sum;
		} catch (InvocationTargetException e) {
			broken(stack, e);
			return 0.0;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail(e);
			return 0.0;
		}
	}

	private static double weight(Holder<Attribute> attribute) {
		if (attribute.is(Attributes.ARMOR)) {
			return ARMOR_WEIGHT;
		} else if (attribute.is(Attributes.MAX_HEALTH)) {
			return MAX_HEALTH_WEIGHT;
		} else if (attribute.is(Attributes.MOVEMENT_SPEED)) {
			return SPEED_WEIGHT;
		} else if (attribute.is(Attributes.ATTACK_DAMAGE)) {
			return DAMAGE_WEIGHT;
		}

		return OTHER_WEIGHT;
	}

	/** The item's own trinket code threw (made for players only, say): it just doesn't count; once in the log per item. */
	private static void broken(ItemStack stack, InvocationTargetException e) {
		if (BROKEN.add(stack.getItem())) {
			Drifter.LOGGER.info("[Drifter] Trinkets: {} can't be read for a Drifter ({}); it isn't worn", stack.getItem(), String.valueOf(e.getCause()));
		}
	}
}
