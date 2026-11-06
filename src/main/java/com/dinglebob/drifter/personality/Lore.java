package com.dinglebob.drifter.personality;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * What it knows of the game beyond the basics (DESIGN.md section 8 "Acquisition rules": "the Knowledge trait limits which
 * entries a Drifter knows ... Players don't know every recipe either"; the user, 2026-11-05: "certain personalities can know
 * a bit more when spawned, with a newbie knowing the least"). Each area is known from the start if its Knowledge is high
 * enough for it (or it's its kind of player's thing: a speedrunner knows the End, a builder redstone); a newbie knows only
 * farming and animals. It learns the rest as players do: by going there or getting the thing (the Nether once it's been,
 * enchanting once it holds an enchanted item or has seen a table), and by being told in a talk ({@code teach_<area>} and
 * {@code learn_<area>} topic effects). It plans only for what it knows (no portal before it knows the Nether exists), and
 * its talk follows: a veteran gives advice, a newbie asks.
 */
public final class Lore {
	public enum Area {
		FARMING(0.05), ANIMALS(0.05), TRADING(0.3), ENCHANTING(0.35), NETHER(0.35), BREWING(0.4), RAIDS(0.45), REDSTONE(0.5),
		ANVILS(0.5), FORTRESSES(0.5), BARTERING(0.55), END(0.55), TRIAL_CHAMBERS(0.6), NETHERITE(0.6), WARDEN(0.65), DRAGON(0.7),
		ELYTRA(0.75), BEACONS(0.75);

		/** The Knowledge it takes to know it from the start. */
		public final double knowledge;
		public final String key;

		Area(double knowledge) {
			this.knowledge = knowledge;
			this.key = this.name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Area of(String key) {
			for (Area area : values()) {
				if (area.key.equals(key)) {
					return area;
				}
			}

			return null;
		}
	}

	/** Each kind of player's own things, known whatever its Knowledge. */
	private static final Map<Archetype, Set<Area>> OWN = Map.ofEntries(
		Map.entry(Archetype.SPEEDRUNNER, EnumSet.of(Area.ENCHANTING, Area.NETHER, Area.FORTRESSES, Area.BARTERING, Area.END, Area.DRAGON)),
		Map.entry(Archetype.PVP_SWEAT, EnumSet.of(Area.ENCHANTING, Area.ANVILS, Area.BREWING, Area.NETHER, Area.NETHERITE)),
		Map.entry(Archetype.BUILDER, EnumSet.of(Area.REDSTONE, Area.TRADING)),
		Map.entry(Archetype.MINER, EnumSet.of(Area.ENCHANTING, Area.NETHERITE)),
		Map.entry(Archetype.HOMEBODY, EnumSet.of(Area.TRADING)),
		Map.entry(Archetype.HOARDER, EnumSet.of(Area.TRADING, Area.ANVILS)),
		Map.entry(Archetype.NOMAD, EnumSet.of(Area.NETHER, Area.RAIDS, Area.TRIAL_CHAMBERS)),
		Map.entry(Archetype.HERMIT, EnumSet.of(Area.BREWING)),
		Map.entry(Archetype.HELPER, EnumSet.of(Area.TRADING)),
		Map.entry(Archetype.BANDIT, EnumSet.of(Area.RAIDS)));

	public static final int SAVE_VERSION = 1;

	/** What it's learned since it came (the rest comes from its personality, so a reroll changes it). */
	private final EnumSet<Area> learned = EnumSet.noneOf(Area.class);

	/** Known, from the start or learned. */
	public boolean knows(DrifterEntity drifter, Area area) {
		return this.learned.contains(area) || innate(drifter.getPersonality(), area);
	}

	/** Known from the start, by its kind of player and Knowledge. */
	public static boolean innate(Personality personality, Area area) {
		Archetype archetype = personality.archetype();
		// (A newbie's first world: it knows to farm and keep animals, the rest it finds out.)
		if (archetype == Archetype.NEWBIE) {
			return area.knowledge <= 0.05;
		}

		Set<Area> own = OWN.get(archetype);
		return own != null && own.contains(area) || personality.get(Trait.KNOWLEDGE) >= area.knowledge;
	}

	/** It's learned this (true if it's new to it), with a thought about it. */
	public boolean learn(DrifterEntity drifter, Area area, String how) {
		if (this.knows(drifter, area)) {
			return false;
		}

		this.learned.add(area);
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "learned about " + area.key.replace('_', ' ') + " (" + how + ")");
		return true;
	}

	/** How many it knows (for the debug command). */
	public String describe(DrifterEntity drifter) {
		StringBuilder known = new StringBuilder();
		StringBuilder unknown = new StringBuilder();
		for (Area area : Area.values()) {
			StringBuilder to = this.knows(drifter, area) ? known : unknown;
			to.append(to.isEmpty() ? "" : ", ").append(area.key).append(this.learned.contains(area) ? " (learned)" : "");
		}

		return "knows: " + known + "; new to: " + (unknown.isEmpty() ? "nothing" : unknown);
	}

	/** Now and then: what it's seen, been and got teaches it (a table it saw, the Nether it's in, a blaze rod in its bag). */
	public void check(DrifterEntity drifter) {
		var level = drifter.level();
		if (level.dimension() == Level.NETHER) {
			this.learn(drifter, Area.NETHER, "been there");
		} else if (level.dimension() == Level.END) {
			this.learn(drifter, Area.END, "been there");
		}

		var inventory = drifter.getInventory();
		if (inventory.count(ItemStack::isEnchanted) > 0) {
			this.learn(drifter, Area.ENCHANTING, "got something enchanted");
		}

		if (inventory.count(stack -> stack.is(Items.BLAZE_ROD) || stack.is(Items.BLAZE_POWDER)) > 0) {
			this.learn(drifter, Area.FORTRESSES, "got a blaze rod");
		}

		if (inventory.count(stack -> stack.is(Items.ENDER_EYE)) > 0) {
			this.learn(drifter, Area.END, "made an eye of ender");
		}

		if (inventory.count(stack -> stack.is(Items.ANCIENT_DEBRIS) || stack.is(Items.NETHERITE_SCRAP) || stack.is(Items.NETHERITE_INGOT)) > 0) {
			this.learn(drifter, Area.NETHERITE, "found ancient debris");
		}

		if (inventory.count(stack -> stack.is(Items.ELYTRA)) > 0) {
			this.learn(drifter, Area.ELYTRA, "got an elytra");
		}

		if (inventory.count(stack -> stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)) > 0) {
			this.learn(drifter, Area.BREWING, "got a potion");
		}

		if (inventory.count(stack -> stack.is(Items.REDSTONE) || stack.is(Items.REPEATER) || stack.is(Items.PISTON)) > 0) {
			this.learn(drifter, Area.REDSTONE, "got some redstone");
		}

		// Stations and structures it remembers seeing: a village library's table, a stand, an anvil, an End portal.
		drifter.getMemory().find(MemoryKind.STATION, entry -> true).forEach(entry -> {
			switch (entry.label) {
				case "minecraft:enchanting_table" -> this.learn(drifter, Area.ENCHANTING, "saw an enchanting table");
				case "minecraft:brewing_stand" -> this.learn(drifter, Area.BREWING, "saw a brewing stand");
				case "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil" -> this.learn(drifter, Area.ANVILS, "saw an anvil");
				default -> {
				}
			}
		});
		drifter.getMemory().find(MemoryKind.STRUCTURE, entry -> true).forEach(entry -> {
			switch (entry.label) {
				case "minecraft:nether_portal" -> this.learn(drifter, Area.NETHER, "saw a Nether portal");
				case "minecraft:end_portal_frame" -> this.learn(drifter, Area.END, "saw an End portal");
				default -> {
				}
			}
		});
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		ListTag list = new ListTag();
		this.learned.forEach(area -> list.add(StringTag.valueOf(area.key)));
		tag.put("Learned", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.learned.clear();
		for (Tag element : tag.getList("Learned", Tag.TAG_STRING)) {
			Area area = Area.of(element.getAsString());
			if (area != null) {
				this.learned.add(area);
			}
		}
	}
}
