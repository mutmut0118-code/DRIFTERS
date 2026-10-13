package com.dinglebob.drifter.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.GameRules;

/**
 * The Drifter's hunger model (DESIGN.md section 3). A line-for-line mirror of vanilla
 * {@link net.minecraft.world.food.FoodData}, which only accepts a Player. Same numbers, same NBT keys.
 */
public class DrifterFoodData {
	public static final float MAX_EXHAUSTION = 40.0F;

	private int foodLevel = FoodConstants.MAX_FOOD;
	private float saturationLevel = FoodConstants.START_SATURATION;
	private float exhaustionLevel;
	private int tickTimer;
	private int lastFoodLevel = FoodConstants.MAX_FOOD;

	private void add(int food, float saturation) {
		this.foodLevel = Mth.clamp(food + this.foodLevel, 0, FoodConstants.MAX_FOOD);
		this.saturationLevel = Mth.clamp(saturation + this.saturationLevel, 0.0F, this.foodLevel);
	}

	public void eat(int nutrition, float saturationModifier) {
		this.add(nutrition, FoodConstants.saturationByModifier(nutrition, saturationModifier));
	}

	public void eat(FoodProperties foodProperties) {
		this.add(foodProperties.nutrition(), foodProperties.saturation());
	}

	public void tick(DrifterEntity drifter) {
		Difficulty difficulty = drifter.level().getDifficulty();
		this.lastFoodLevel = this.foodLevel;
		if (this.exhaustionLevel > FoodConstants.EXHAUSTION_DROP) {
			this.exhaustionLevel -= FoodConstants.EXHAUSTION_DROP;
			if (this.saturationLevel > 0.0F) {
				this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
			} else if (difficulty != Difficulty.PEACEFUL) {
				this.foodLevel = Math.max(this.foodLevel - 1, 0);
			}
		}

		boolean naturalRegen = drifter.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
		if (naturalRegen && this.saturationLevel > 0.0F && drifter.isHurt() && this.foodLevel >= FoodConstants.MAX_FOOD) {
			this.tickTimer++;
			if (this.tickTimer >= FoodConstants.HEALTH_TICK_COUNT_SATURATED) {
				float spent = Math.min(this.saturationLevel, FoodConstants.EXHAUSTION_HEAL);
				drifter.heal(spent / FoodConstants.EXHAUSTION_HEAL);
				this.addExhaustion(spent);
				this.tickTimer = 0;
			}
		} else if (naturalRegen && this.foodLevel >= FoodConstants.HEAL_LEVEL && drifter.isHurt()) {
			this.tickTimer++;
			if (this.tickTimer >= FoodConstants.HEALTH_TICK_COUNT) {
				drifter.heal(1.0F);
				this.addExhaustion(FoodConstants.EXHAUSTION_HEAL);
				this.tickTimer = 0;
			}
		} else if (this.foodLevel <= 0) {
			this.tickTimer++;
			if (this.tickTimer >= FoodConstants.HEALTH_TICK_COUNT) {
				if (drifter.getHealth() > 10.0F || difficulty == Difficulty.HARD || drifter.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
					drifter.hurt(drifter.damageSources().starve(), 1.0F);
				}

				this.tickTimer = 0;
			}
		} else {
			this.tickTimer = 0;
		}
	}

	public void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("foodLevel", 99)) {
			this.foodLevel = tag.getInt("foodLevel");
			this.tickTimer = tag.getInt("foodTickTimer");
			this.saturationLevel = tag.getFloat("foodSaturationLevel");
			this.exhaustionLevel = tag.getFloat("foodExhaustionLevel");
		}
	}

	public void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("foodLevel", this.foodLevel);
		tag.putInt("foodTickTimer", this.tickTimer);
		tag.putFloat("foodSaturationLevel", this.saturationLevel);
		tag.putFloat("foodExhaustionLevel", this.exhaustionLevel);
	}

	public int getFoodLevel() {
		return this.foodLevel;
	}

	public int getLastFoodLevel() {
		return this.lastFoodLevel;
	}

	public boolean needsFood() {
		return this.foodLevel < FoodConstants.MAX_FOOD;
	}

	public void addExhaustion(float amount) {
		this.exhaustionLevel = Math.min(this.exhaustionLevel + amount, MAX_EXHAUSTION);
	}

	public float getExhaustionLevel() {
		return this.exhaustionLevel;
	}

	public float getSaturationLevel() {
		return this.saturationLevel;
	}

	public void setFoodLevel(int foodLevel) {
		this.foodLevel = foodLevel;
	}

	public void setSaturation(float saturation) {
		this.saturationLevel = saturation;
	}

	public void setExhaustion(float exhaustion) {
		this.exhaustionLevel = exhaustion;
	}
}
