package com.dinglebob.drifter.perception;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * The Drifter's sense of time and weather (DESIGN.md section 5, "Time and weather sense"). With the sky in view
 * (or a clock in its inventory) it knows the exact time; underground it keeps its own estimate, which drifts by up
 * to about 10% per in-game hour. It only knows the weather while it can see the sky.
 */
public class TimeSense {
	/** One in-game hour. */
	public static final int HOUR_TICKS = 1000;
	public static final double MAX_DRIFT = 0.10;
	public static final double DRIFT_STEP = 0.03;
	public static final int DUSK = 12000;

	private double estimate = -1;
	private double drift;
	private boolean exact;
	private boolean skyVisible;
	private boolean knownRain;
	private boolean knownThunder;
	private long lastDriftChange;

	/** @return true the moment its believed time passes dusk (once per day). */
	public boolean tick(DrifterEntity drifter) {
		Level level = drifter.level();
		long actual = level.getDayTime();
		double before = this.estimate;
		this.skyVisible = level.dimensionType().hasSkyLight() && level.canSeeSky(BlockPos.containing(drifter.getEyePosition()));
		boolean hasClock = level.dimensionType().natural() && drifter.getInventory().count(stack -> stack.is(Items.CLOCK)) > 0;
		if (this.estimate < 0 || this.skyVisible || hasClock) {
			this.estimate = actual;
			this.exact = true;
		} else if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
			this.exact = false;
			this.estimate += 1.0 + this.drift;
			if (level.getGameTime() - this.lastDriftChange >= HOUR_TICKS) {
				this.lastDriftChange = level.getGameTime();
				this.drift = Mth.clamp(this.drift + drifter.getRandom().nextGaussian() * DRIFT_STEP, -MAX_DRIFT, MAX_DRIFT);
			}
		}

		if (this.skyVisible) {
			this.knownRain = level.isRaining();
			this.knownThunder = level.isThundering();
		}

		return before >= 0 && Math.floorMod((long)before, 24000L) < DUSK && Math.floorMod((long)this.estimate, 24000L) >= DUSK;
	}

	public long believedTimeOfDay() {
		return Math.floorMod((long)Math.max(this.estimate, 0), 24000L);
	}

	public long believedDay() {
		return (long)Math.max(this.estimate, 0) / 24000L;
	}

	public boolean isExact() {
		return this.exact;
	}

	public boolean canSeeSky() {
		return this.skyVisible;
	}

	public String describeWeather() {
		if (this.knownThunder) {
			return "thunderstorm";
		}

		return this.knownRain ? "rain" : "clear";
	}

	public static String describeTimeOfDay(long time) {
		if (time < 1000 || time >= 23000) {
			return "dawn";
		} else if (time < 6000) {
			return "morning";
		} else if (time < 11000) {
			return "afternoon";
		} else if (time < 13000) {
			return "dusk";
		}

		return "night";
	}
}
