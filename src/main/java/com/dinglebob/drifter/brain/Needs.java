package com.dinglebob.drifter.brain;

import java.util.Locale;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.util.Mth;

/**
 * Need urgencies, 0 to 1, from response curves (DESIGN.md section 7, "Needs" and "Scoring a goal"). Milestone 5 covers
 * hunger, health, shelter, gear and food supply; curiosity is a small constant.
 * TODO(milestone 7): personality weights reshape these curves (a Cautious Drifter's shelter curve starts earlier).
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
		boolean sealedIn = Shelter.isSealedIn(drifter);
		double shelter;
		boolean dusk = time >= Schedule.SHELTER_URGENT && time < Schedule.NIGHT_START;
		if (Schedule.isNight(time) || dusk) {
			shelter = sealedIn ? 1.0 : 0.95;
		} else if (time >= Schedule.NIGHT_END) {
			// Dawn twilight: stay put until the sun is up.
			shelter = sealedIn ? 0.9 : 0.0;
		} else if (time >= Schedule.SHELTER_THINKING) {
			shelter = Mth.lerp((time - Schedule.SHELTER_THINKING) / (double)(Schedule.SHELTER_URGENT - Schedule.SHELTER_THINKING), 0.2, 0.7);
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

		return new Needs(hunger, health, shelter, gear, foodSupply, 0.15);
	}

	public String describe() {
		return String.format(Locale.ROOT, "hunger %.2f health %.2f shelter %.2f gear %.2f food %.2f", this.hunger, this.health, this.shelter,
			this.gear, this.foodSupply);
	}
}
