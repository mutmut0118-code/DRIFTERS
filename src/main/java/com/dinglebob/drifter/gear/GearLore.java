package com.dinglebob.drifter.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.perception.Senses;
import com.dinglebob.drifter.social.DrifterBonds;
import com.dinglebob.drifter.social.Relationship;
import com.dinglebob.drifter.world.DrifterGroups;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which modded items a Drifter knows exist, and how it came to know (spec goal 2): it saw one (in a chest it opened, on the
 * ground, held or worn by someone, offered in a trade), owns one, holds what its recipe is mostly made of (the recipe book's
 * rule), or was told. Veterans know the craftable gear of the installed mods from the start, newbies none. Saved and
 * versioned. The "know every recipe" setting (goal 11) makes everything known; it never touches where things are.
 */
public final class GearLore {
	public enum How {
		INNATE, OWNED, SEEN, UNLOCKED, TOLD;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static How byId(String id) {
			for (How how : values()) {
				if (how.id().equals(id)) {
					return how;
				}
			}

			return null;
		}
	}

	public static final int SAVE_VERSION = 1;
	/** At or above this KNOWLEDGE, a Drifter knows the installed mods' craftable gear from the start. */
	public static final double VETERAN_KNOWLEDGE = 0.7;
	/** The check runs this often per Drifter (ticks); offset from {@code Lore.check} so they do not share a tick. */
	public static final int CHECK_INTERVAL = 200;
	public static final int CHECK_OFFSET = 100;

	private final Map<Item, How> learned = new LinkedHashMap<>();
	/** No saved knowledge was loaded: the next check seeds it from what the Drifter carries, wears and stores. */
	private boolean seedPending = true;

	// Taste (spec goal 4). Only the loyal kind is saved.
	/** The kind of weapon it has stuck with for {@link #LOYAL_FIGHTS} fights (saved). */
	@Nullable
	private String loyalKind;
	private final Map<String, Integer> fightsByKind = new HashMap<>();
	private long lastTargetTick = Long.MIN_VALUE / 2;
	private final Tally friendKinds = new Tally();
	private final Tally friendSets = new Tally();
	private long friendStamp;
	/** The cached style (not saved) with the gear-data version it was drawn under, and the debug override. */
	@Nullable
	private GearTaste.Style style;
	private int styleVersion = -1;
	@Nullable
	private GearTaste.Style styleOverride;
	@Nullable
	private Weapons.Family favouriteFamily;
	@Nullable
	private String favouriteKind;
	@Nullable
	private String wornSet;
	private long wornSetTick = Long.MIN_VALUE / 2;

	/** Fights with one kind of weapon before it is loyal to it. */
	public static final int LOYAL_FIGHTS = 3;
	/** A fight is a swing after at least this many ticks without one. */
	private static final int FIGHT_GAP = 200;
	private static final long DAY_TICKS = 24000L;
	/** Friends' gear is weighed anew this often (ticks), halving each in-game day. */
	private static final long DECAY_STEP = 1200L;

	/** How much of what friends were seen holding or wearing was each kind (or armor set). */
	private static final class Tally {
		final Map<String, Double> weight = new HashMap<>();
		double total;

		void add(String key) {
			this.weight.merge(key, 1.0, Double::sum);
			this.total += 1.0;
		}

		void decay(double factor) {
			this.total = 0.0;
			this.weight.replaceAll((key, value) -> value * factor);
			this.weight.values().removeIf(value -> value < 0.01);
			for (double value : this.weight.values()) {
				this.total += value;
			}
		}

		double share(String key) {
			Double value = this.weight.get(key);
			return value == null || this.total <= 0.0 ? 0.0 : value / this.total;
		}
	}

	/** True: it knows this from the start (the setting, vanilla, a weapon family, a veteran with a craftable recipe). */
	public static boolean innate(DrifterEntity d, Item item) {
		// Goal 11: items and recipes only; Lore areas and where things are stay as they are.
		if (DrifterConfig.knowEveryRecipe()) {
			return true;
		}

		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		if ("minecraft".equals(id.getNamespace())) {
			return true;
		}

		ItemStack stack = new ItemStack(item);
		if (Weapons.family(stack) != null) {
			return true;
		}

		if (Weapons.unique(stack) != null) {
			return false;
		}

		Archetype archetype = d.getPersonality().archetype();
		if (archetype == Archetype.NEWBIE) {
			return false;
		}

		boolean veteran = d.getPersonality().get(Trait.KNOWLEDGE) >= VETERAN_KNOWLEDGE
			|| archetype == Archetype.PVP_SWEAT || archetype == Archetype.SPEEDRUNNER;
		// It knows the installed mods' craftable gear (cached per reload), not their loot.
		return veteran && RecipeIndex.key(d.level(), item) != null;
	}

	public boolean knows(DrifterEntity d, Item item) {
		return this.learned.containsKey(item) || innate(d, item);
	}

	/** How it knows this (null: it does not). */
	public How how(DrifterEntity d, Item item) {
		How how = this.learned.get(item);
		if (how != null) {
			return how;
		}

		return innate(d, item) ? How.INNATE : null;
	}

	/** It has learned of this (true if new to it), with a thought about it. */
	public boolean learn(DrifterEntity d, Item item, How how, String where) {
		return this.learn(d, item, how, where, true);
	}

	private boolean learn(DrifterEntity d, Item item, How how, String where, boolean thought) {
		if (this.knows(d, item)) {
			return false;
		}

		this.learned.put(item, how);
		if (thought) {
			d.getMemory().thoughts().add(d.level().getGameTime(), "learned of " + nameOf(item) + " (" + where + ")");
		}

		return true;
	}

	/** It has seen this (gear only: others are not its business). */
	public void see(DrifterEntity d, ItemStack stack, String where) {
		if (stack.isEmpty() || !GearValue.isGear(stack) || this.knows(d, stack.getItem())) {
			return;
		}

		this.learn(d, stack.getItem(), How.SEEN, where);
	}

	/**
	 * What it has and sees teaches it. Never scans the item registry or all recipes: only lookups for items it has, and for
	 * the entities it is watching.
	 */
	public void check(DrifterEntity d) {
		Home home = d.getDrifterBrain().home();
		Map<Item, Integer> stored = home == null ? Map.of() : home.stored();
		if (this.seedPending) {
			this.seedPending = false;
			// An older save (or none): what it carries, wears and stores, and its favourite family's items, it knows.
			for (int slot = 0; slot < d.getInventory().getContainerSize(); slot++) {
				this.seed(d, d.getInventory().getItem(slot).getItem());
			}

			for (Item item : stored.keySet()) {
				this.seed(d, item);
			}

			Weapons.Family favourite = Weapons.favorite(d);
			if (favourite != null) {
				for (Item item : favourite.tiers().values()) {
					this.seed(d, item);
				}
			}
		}

		List<Item> has = new ArrayList<>();
		for (int slot = 0; slot < d.getInventory().getContainerSize(); slot++) {
			ItemStack stack = d.getInventory().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			if (!has.contains(stack.getItem())) {
				has.add(stack.getItem());
			}

			if (GearValue.isGear(stack)) {
				this.learn(d, stack.getItem(), How.OWNED, "owned");
			}
		}

		for (Item item : stored.keySet()) {
			if (!has.contains(item)) {
				has.add(item);
			}
		}

		for (Item item : has) {
			for (Item unlocked : RecipeIndex.unlockedBy(d.level(), item)) {
				if (!this.knows(d, unlocked)) {
					this.learn(d, unlocked, How.UNLOCKED, "unlocked: has " + nameOf(item));
				}
			}
		}

		this.decayFriends(d.level().getGameTime());
		DrifterGroups.Group group = DrifterGroups.of(d.getUUID());
		for (Awareness seen : d.getSenses().tracked()) {
			if (!seen.visibleNow || !(seen.entity instanceof LivingEntity other) || other == d
				|| other.distanceToSqr(d) > Senses.DETAIL_RANGE * Senses.DETAIL_RANGE) {
				continue;
			}

			String name = other.getName().getString();
			this.see(d, other.getMainHandItem(), "held by " + name);
			this.see(d, other.getOffhandItem(), "held by " + name);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot.isArmor()) {
					this.see(d, other.getItemBySlot(slot), "worn by " + name);
				}
			}

			// What its friends and group wear and wield (only what it sees them in) shapes a follower's taste.
			if (this.isFriend(d, group, other.getUUID())) {
				this.noteFriend(other.getMainHandItem());
				this.noteFriend(other.getOffhandItem());
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					if (slot.isArmor()) {
						this.noteFriend(other.getItemBySlot(slot));
					}
				}
			}
		}
	}

	private boolean isFriend(DrifterEntity d, @Nullable DrifterGroups.Group group, UUID other) {
		if (group != null && group.members.contains(other)) {
			return true;
		}

		Relationship bond = d.getBonds().get(other);
		return bond != null && bond.trust >= DrifterBonds.FRIEND;
	}

	private void noteFriend(ItemStack stack) {
		if (!stack.isEmpty()) {
			String key = GearTaste.followKey(stack);
			if (key != null) {
				this.noteFriendGear(key);
			}
		}
	}

	/** A friend was seen with this kind of weapon, or this armor set ({@code namespace:set_}, has a colon). */
	public void noteFriendGear(String kindOrSet) {
		(kindOrSet.indexOf(':') >= 0 ? this.friendSets : this.friendKinds).add(kindOrSet);
	}

	/** The share of the weapons (or armor sets) its friends were seen with that are this one, 0 to 1 (0: none noted). */
	public double friendShare(String kindOrSet) {
		return (kindOrSet.indexOf(':') >= 0 ? this.friendSets : this.friendKinds).share(kindOrSet);
	}

	private void decayFriends(long now) {
		long gap = now - this.friendStamp;
		if (this.friendStamp == 0L || gap < 0L) {
			this.friendStamp = now;
		} else if (gap >= DECAY_STEP) {
			double factor = Math.pow(0.5, (double)gap / DAY_TICKS);
			this.friendKinds.decay(factor);
			this.friendSets.decay(factor);
			this.friendStamp = now;
		}
	}

	/**
	 * Called when it swings at a foe in reach (Combat.attack): the first swing of a fight (none for FIGHT_GAP ticks before)
	 * counts the fight for the usable weapon in its hand, so a weapon drawn after it saw the foe still counts. The kind it keeps
	 * fighting with becomes its loyal kind.
	 */
	public void noteSwing(DrifterEntity d) {
		long tick = d.tickCount;
		boolean fresh = tick - this.lastTargetTick > FIGHT_GAP;
		this.lastTargetTick = tick;
		if (!fresh || this.loyalKind != null) {
			return;
		}

		GearKinds.Kind kind = GearKinds.of(d.getMainHandItem());
		if (kind == null || !kind.use().usable()) {
			// A fight with a tool or bare hands: it does not count for any weapon; the next swing may (if it is still the same fight)
			this.lastTargetTick = Long.MIN_VALUE / 2;
			return;
		}

		if (this.fightsByKind.merge(kind.id(), 1, Integer::sum) >= LOYAL_FIGHTS) {
			this.loyalKind = kind.id();
			d.getMemory().thoughts().add(d.level().getGameTime(), "i keep reaching for the " + kind.id() + "; it feels like mine");
		}
	}

	@Nullable
	public String loyalKind() {
		return this.loyalKind;
	}

	@Nullable
	public GearTaste.Style styleOverride() {
		return this.styleOverride;
	}

	/** Debug: pretend this style (null: back to the seeded draw). Not saved. */
	public void setStyleOverride(@Nullable GearTaste.Style style) {
		this.styleOverride = style;
	}

	@Nullable
	GearTaste.Style cachedStyle(int version) {
		return this.styleVersion == version ? this.style : null;
	}

	void cacheStyle(GearTaste.Style style, int version) {
		this.style = style;
		this.styleVersion = version;
	}

	/** The kind of its favourite weapon family (cached until the favourite changes): what a loyal Drifter with no habit yet likes. */
	@Nullable
	String favouriteKind(DrifterEntity d) {
		Weapons.Family favourite = Weapons.favorite(d);
		if (favourite != this.favouriteFamily) {
			this.favouriteFamily = favourite;
			this.favouriteKind = null;
			if (favourite != null && !favourite.tiers().isEmpty()) {
				GearKinds.Kind kind = GearKinds.of(new ItemStack(favourite.tiers().values().iterator().next()));
				this.favouriteKind = kind == null ? null : kind.id();
			}
		}

		return this.favouriteKind;
	}

	/** The armor set it wears most of (at least two pieces), or null; looked at once a second at most. */
	@Nullable
	String wornSet(DrifterEntity d) {
		if (d.tickCount - this.wornSetTick < 20L) {
			return this.wornSet;
		}

		this.wornSetTick = d.tickCount;
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		String[] keys = new String[slots.length];
		for (int i = 0; i < slots.length; i++) {
			ItemStack worn = d.getItemBySlot(slots[i]);
			keys[i] = !worn.isEmpty() && GearValue.armorSlot(worn) != null ? GearTaste.setKey(worn) : null;
		}

		String best = null;
		int bestCount = 0;
		for (String key : keys) {
			if (key == null) {
				continue;
			}

			int count = 0;
			for (String other : keys) {
				if (key.equals(other)) {
					count++;
				}
			}

			if (count > bestCount) {
				best = key;
				bestCount = count;
			}
		}

		this.wornSet = bestCount >= 2 ? best : null;
		return this.wornSet;
	}

	private void seed(DrifterEntity d, Item item) {
		if (item != net.minecraft.world.item.Items.AIR && GearValue.isGear(new ItemStack(item))) {
			this.learn(d, item, How.OWNED, "owned", false);
		}
	}

	public List<Item> learnedItems() {
		return List.copyOf(this.learned.keySet());
	}

	/** Debug: it forgets what it learned (and seeds again from what it has on the next check). */
	public void forget() {
		this.learned.clear();
		this.seedPending = false;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		// Saved before its first check, it must still seed after the next load.
		tag.putBoolean("Seeded", !this.seedPending);
		if (this.loyalKind != null) {
			tag.putString("LoyalKind", this.loyalKind);
		}

		ListTag known = new ListTag();
		this.learned.forEach((item, how) -> {
			CompoundTag entry = new CompoundTag();
			entry.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString());
			entry.putString("How", how.id());
			known.add(entry);
		});
		tag.put("Known", known);
		return tag;
	}

	/** Items whose mod is gone, and kinds of learning this version does not know, are skipped quietly. */
	public void load(CompoundTag tag) {
		this.learned.clear();
		this.seedPending = !tag.getBoolean("Seeded");
		this.loyalKind = tag.contains("LoyalKind", Tag.TAG_STRING) ? tag.getString("LoyalKind") : null;
		if (!tag.contains("Known", Tag.TAG_LIST)) {
			this.seedPending = true;
			return;
		}

		ListTag known = tag.getList("Known", Tag.TAG_COMPOUND);
		for (int i = 0; i < known.size(); i++) {
			CompoundTag entry = known.getCompound(i);
			ResourceLocation id = ResourceLocation.tryParse(entry.getString("Item"));
			How how = How.byId(entry.getString("How"));
			if (id == null || how == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				continue;
			}

			this.learned.put(BuiltInRegistries.ITEM.get(id), how);
		}

		if (tag.getInt("Version") > SAVE_VERSION) {
			Drifter.LOGGER.debug("Drifter gear knowledge saved by a newer version; reading what is understood");
		}
	}

	/** "Warrior Chestplate": the item's name, or its id made readable where the server has no translations. */
	public static String nameOf(Item item) {
		try {
			String name = item.getDescription().getString();
			if (!name.equals(item.getDescriptionId())) {
				return name;
			}
		} catch (RuntimeException e) {
			// fall through to the id
		}

		StringBuilder out = new StringBuilder();
		for (String word : BuiltInRegistries.ITEM.getKey(item).getPath().split("[^a-z0-9]+")) {
			if (!word.isEmpty()) {
				out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}

		return out.toString();
	}
}
