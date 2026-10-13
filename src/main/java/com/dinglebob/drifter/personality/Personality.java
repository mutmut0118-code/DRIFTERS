package com.dinglebob.drifter.personality;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Who a Drifter is (DESIGN.md section 17): an archetype, trait values, a habit fingerprint rolled once, and a mood.
 * Systems read traits through {@link #get}, which includes the mood's pull (an anxious Drifter is more cautious for a
 * while) and practice (Skill grows a little with use, section 17 "Learning over time").
 */
public final class Personality {
	public static final int SAVE_VERSION = 1;
	/** Section 17: Skill improves with practice up to about 0.2 above where it started. */
	public static final double MAX_SKILL_GAIN = 0.2;

	/** How each mood pulls on traits at full intensity (section 17 mood effects). */
	private static final Map<Mood, Map<Trait, Double>> MOOD_PULL = Map.of(
		Mood.ANXIOUS, Map.of(Trait.CAUTION, 0.15, Trait.BRAVERY, -0.1, Trait.FIDGET, 0.1),
		Mood.PANICKED, Map.of(Trait.SKILL, -0.2, Trait.BRAVERY, -0.25, Trait.CAUTION, 0.1, Trait.EFFICIENCY, -0.2),
		Mood.CONFIDENT, Map.of(Trait.BRAVERY, 0.15, Trait.CURIOSITY, 0.1, Trait.CAUTION, -0.05),
		Mood.FRUSTRATED, Map.of(Trait.CAUTION, -0.15, Trait.PATIENCE, -0.2),
		Mood.BORED, Map.of(Trait.CURIOSITY, 0.2, Trait.FIDGET, 0.2),
		Mood.TRIUMPHANT, Map.of(Trait.BRAVERY, 0.1, Trait.FIDGET, 0.15),
		Mood.GRIEVING, Map.of(Trait.INDUSTRIOUSNESS, -0.2, Trait.CURIOSITY, -0.2),
		Mood.ANGRY, Map.of(Trait.AGGRESSION, 0.2));

	private Archetype archetype;
	private EnumMap<Trait, Double> traits;
	private Habits habits;
	private final MoodState mood = new MoodState();
	private double skillGained;

	private Personality(Archetype archetype, EnumMap<Trait, Double> traits, Habits habits) {
		this.archetype = archetype;
		this.traits = traits;
		this.habits = habits;
	}

	/** A new personality: the archetype by spawn weight (or the given one), traits from its preset, habits from those. */
	public static Personality roll(RandomSource random, @Nullable Archetype archetype) {
		Archetype picked = archetype != null ? archetype : Archetype.pick(random);
		EnumMap<Trait, Double> traits = picked.rollTraits(random);
		return new Personality(picked, traits, Habits.roll(picked, traits, random));
	}

	/** Replaces everything with a fresh roll (debug command). */
	public void reroll(RandomSource random, @Nullable Archetype archetype) {
		Personality fresh = roll(random, archetype);
		this.archetype = fresh.archetype;
		this.traits = fresh.traits;
		this.habits = fresh.habits;
		this.skillGained = 0.0;
		this.mood.force(Mood.CALM);
	}

	public Archetype archetype() {
		return this.archetype;
	}

	public Habits habits() {
		return this.habits;
	}

	public MoodState mood() {
		return this.mood;
	}

	/** The trait as rolled, without mood or practice. */
	public double base(Trait trait) {
		return this.traits.getOrDefault(trait, 0.5);
	}

	/** The trait as it acts right now: rolled value, plus practice (Skill), plus the current mood's pull. */
	public double get(Trait trait) {
		double value = this.base(trait);
		if (trait == Trait.SKILL) {
			value += this.skillGained;
		}

		Map<Trait, Double> pull = MOOD_PULL.get(this.mood.current());
		if (pull != null) {
			value += pull.getOrDefault(trait, 0.0) * this.mood.intensity();
		}

		return Archetype.clamp(value);
	}

	public float getF(Trait trait) {
		return (float)this.get(trait);
	}

	public boolean is(Mood mood) {
		return this.mood.current() == mood;
	}

	/** Practice (a won fight, a clutch, a long bridge) nudges Skill up, to at most {@link #MAX_SKILL_GAIN}. */
	public void practice(double amount) {
		this.skillGained = Math.min(MAX_SKILL_GAIN, this.skillGained + amount);
	}

	public void feel(Mood mood, double amount, String why) {
		this.mood.add(mood, amount, why);
	}

	public void tick(DrifterEntity drifter) {
		if (drifter.tickCount % 20 != 0) {
			return;
		}

		Mood changed = this.mood.tickSecond(drifter, this);
		if (changed != null) {
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(),
				changed == Mood.CALM ? "calmed down" : "feeling " + changed.id() + (this.mood.reason().isEmpty() ? "" : ": " + this.mood.reason()));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Describing (commands and the overlay)
	// ---------------------------------------------------------------------------------------------

	/** One line for the overlay: archetype, mood, and the traits that matter most to watch. */
	public String summary() {
		return String.format(Locale.ROOT, "%s, mood %s | brave %.2f caution %.2f skill %.2f curious %.2f", this.archetype.displayName(),
			this.mood.describe(), this.get(Trait.BRAVERY), this.get(Trait.CAUTION), this.get(Trait.SKILL), this.get(Trait.CURIOSITY));
	}

	public List<String> describe() {
		List<String> lines = new ArrayList<>();
		lines.add("Archetype: " + this.archetype.displayName() + ", mood: " + this.mood.describe());
		StringBuilder traits = new StringBuilder("Traits:");
		for (Trait trait : Trait.values()) {
			double base = this.base(trait);
			double now = this.get(trait);
			traits.append(String.format(Locale.ROOT, " %s %.2f", trait.id(), base));
			if (Math.abs(now - base) >= 0.01) {
				traits.append(String.format(Locale.ROOT, " (now %.2f)", now));
			}

			traits.append(',');
		}

		lines.add(traits.substring(0, traits.length() - 1));
		Habits h = this.habits;
		lines.add(String.format(Locale.ROOT, "Habits: eats at %d, %s, %s%s, mining: %s (%.2f), tunnels %s every %d, torches on the %s",
			h.eatThreshold, h.scroller ? "scrolls the hotbar" : "number keys", h.autoJump ? "auto-jump" : "jumps by hand",
			h.sprintToggle ? ", sprint toggle" : "", h.miningStyle(), h.miningLean, Habits.words(h.tunnel).replace("one by two", "1x2")
				.replace("two by two", "2x2").replace("three by three", "3x3"), h.branchSpacing, Habits.words(h.torchSide)));
		lines.add(String.format(Locale.ROOT, "More habits: offhand %s, doors: %s, %s crafting tables, celebrates: %s, greets: %s, sleeps %s, trail: %s",
			Habits.words(h.offhand), Habits.words(h.doors), h.picksUpTables ? "picks up" : "leaves", Habits.words(h.celebration), Habits.words(h.greeting),
			Habits.words(h.sleep), Habits.words(h.trail)));
		lines.add(String.format(Locale.ROOT, "Favorites: %s wood, %s, %s wool; hotbar %s%s", h.favoriteWood.replace('_', ' '), h.favoriteStone.replace('_', ' '),
			h.favoriteWool.replace('_', ' '), Habits.words(h.hotbar), h.keepsDirtStack ? ", keeps a dirt stack" : ""));
		return lines;
	}

	// ---------------------------------------------------------------------------------------------
	// Saving (section 20: versioned)
	// ---------------------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", SAVE_VERSION);
		tag.putString("Archetype", this.archetype.id());
		CompoundTag traits = new CompoundTag();
		this.traits.forEach((trait, value) -> traits.putDouble(trait.name(), value));
		tag.put("Traits", traits);
		tag.put("Habits", this.habits.save());
		tag.put("Mood", this.mood.save());
		tag.putDouble("SkillGained", this.skillGained);
		return tag;
	}

	public void load(CompoundTag tag) {
		// Version 1 is the first format. Traits are saved by name: a trait added later keeps its rolled value.
		this.archetype = Archetype.byId(tag.getString("Archetype"));
		CompoundTag traits = tag.getCompound("Traits");
		for (Trait trait : Trait.values()) {
			if (traits.contains(trait.name())) {
				this.traits.put(trait, Archetype.clamp(traits.getDouble(trait.name())));
			}
		}

		if (tag.contains("Habits")) {
			this.habits = Habits.load(tag.getCompound("Habits"));
		}

		this.mood.load(tag.getCompound("Mood"));
		this.skillGained = Math.max(0.0, Math.min(MAX_SKILL_GAIN, tag.getDouble("SkillGained")));
	}
}
