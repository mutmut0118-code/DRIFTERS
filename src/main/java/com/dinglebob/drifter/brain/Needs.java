package com.dinglebob.drifter.brain;

import java.util.Locale;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.util.Mth;

/**
 * Need urgencies, 0 to 1, from response curves (DESIGN.md section 7, "Needs" and "Scoring a goal"). Milestone 5 covers
 * hunger, health, shelter, gear and food supply, and curiosity. Personality reshapes the curves (section 7 "personality
 * weight"): a cautious Drifter's shelter curve starts earlier, a curious one's exploring is worth more, a bored one's
 * even more.
 */
public record Needs(double hunger, double health, double shelter, double gear, double foodSupply, double curiosity) {
	/** Hunger points of carried food it wants before night (about four cooked meats). */
	public static final int FOOD_STOCK_TARGET = 24;

	public static Needs compute(DrifterEntity drifter, boolean hasGearTarget, int stage) {
		int food = drifter.getFoodData().getFoodLevel();
		double hunger;
		if (food >= 18) {
			hunger = 0.0;
		} else if (food >= 14) {
			hunger = Mth.lerp((17 - food) / 3.0, 0.1, 0.3);
		} else if (food >= 6) {
			hunger = Mth.lerp((14 - food) / 8.0, 0.3, 0.85);
		} else {
			hunger = Mth.lerp((6 - food) / 6.0, 0.9, 1.0);
		}

		double missing = 1.0 - drifter.getHealth() / drifter.getMaxHealth();
		double health = missing * missing;

		long time = Schedule.believedTime(drifter);
		// In its own night tunnel counts as being in its shelter (it mines there, and climbs out in the morning).
		NightTunnel tunnel = drifter.getDrifterBrain().nightTunnel();
		boolean sealedIn = Shelter.isSealedIn(drifter) || tunnel != null && tunnel.contains(drifter.blockPosition());
		// A night hunter (section 18: brave ones go out after mobs) only needs shelter once it isn't fit to be out.
		boolean hunting = !sealedIn && drifter.getDrifterBrain().nightStyle() == Schedule.NightStyle.HUNT && Schedule.fitToHunt(drifter);
		double shelter;
		long thinking = Schedule.shelterThinking(drifter);
		long urgent = Schedule.shelterUrgent(drifter);
		boolean dusk = time >= urgent && time < Schedule.NIGHT_START;
		if (Schedule.isNight(time) || dusk) {
			shelter = sealedIn ? 1.0 : hunting ? 0.15 : 0.95;
		} else if (time >= Schedule.NIGHT_END) {
			// Dawn twilight: stay put until the sun is up.
			shelter = sealedIn ? 0.9 : 0.0;
		} else if (time >= thinking) {
			shelter = Mth.lerp((time - thinking) / (double)Math.max(1L, urgent - thinking), 0.2, 0.7);
		} else {
			// Morning: climb out of the night's hole before anything else.
			shelter = sealedIn ? 0.6 : 0.0;
		}

		double gear = hasGearTarget ? 0.5 : 0.1;
		int carried = Food.carriedNutrition(drifter);
		// Section 9: food is the stage 3 job (after stone tools and a furnace), unless it's getting hungry.
		double stageWeight = stage >= 3 ? 0.55 : stage == 2 ? 0.35 : stage == 1 ? 0.2 : 0.0;
		double foodSupply = Mth.clamp(1.0 - carried / (double)FOOD_STOCK_TARGET, 0.0, 1.0) * stageWeight;
		if (food < 14 && carried == 0) {
			foodSupply = Math.max(foodSupply, 0.8);
		}

		// Curiosity (section 7): 0.05 for an incurious homebody up to 0.25 for a nomad; boredom pushes it up (section 17).
		com.dinglebob.drifter.personality.Personality personality = drifter.getPersonality();
		double curiosity = 0.05 + 0.2 * personality.get(com.dinglebob.drifter.personality.Trait.CURIOSITY)
			+ 0.25 * Math.min(1.0, personality.mood().pressure(com.dinglebob.drifter.personality.Mood.BORED));
		return new Needs(hunger, health, shelter, gear, foodSupply, curiosity);
	}

	public String describe() {
		return String.format(Locale.ROOT, "hunger %.2f health %.2f shelter %.2f gear %.2f food %.2f", this.hunger, this.health, this.shelter,
			this.gear, this.foodSupply);
	}
}
