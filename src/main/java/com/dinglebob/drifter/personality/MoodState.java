package com.dinglebob.drifter.personality;

import java.util.EnumMap;
import java.util.Locale;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.Threats;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * The Drifter's mood (DESIGN.md section 17, "Mood"). Every mood has a pressure: events push it up (a kill, a bad hit,
 * a failed job), a few things push it every second (low health, danger, being underground at night, good gear, nothing
 * new for a long time), and each one fades with its own half-life back toward Calm. The strongest pressure above
 * {@link #ENTER} is the mood; it holds until it drops below {@link #STAY} or another one clearly beats it.
 */
public final class MoodState {
	public static final double ENTER = 0.4;
	public static final double STAY = 0.25;
	/** Another mood must beat the current one by this much to take over. */
	public static final double SWITCH_MARGIN = 0.15;
	public static final double MAX_PRESSURE = 1.5;
	/** Boredom starts building once nothing new has turned up for this long (ticks). */
	public static final int BORED_AFTER_TICKS = 20 * 120;

	private final EnumMap<Mood, Double> pressure = new EnumMap<>(Mood.class);
	private Mood current = Mood.CALM;
	private long lastNovelty;
	private int lastExplored = -1;
	private int lastLongTerm = -1;
	/** Who or what it was about, for the thought log ("frustrated: couldn't reach the tree"). */
	private String reason = "";

	public Mood current() {
		return this.current;
	}

	/** How strongly it feels the current mood, 0 to 1 (0 when Calm). */
	public double intensity() {
		return this.current == Mood.CALM ? 0.0 : Math.min(1.0, this.pressure(this.current));
	}

	public double pressure(Mood mood) {
		return this.pressure.getOrDefault(mood, 0.0);
	}

	public String reason() {
		return this.reason;
	}

	public void add(Mood mood, double amount, String why) {
		if (mood == Mood.CALM || amount <= 0.0) {
			return;
		}

		this.pressure.put(mood, Math.min(MAX_PRESSURE, this.pressure(mood) + amount));
		if (!why.isEmpty()) {
			this.reasonFor(mood, why);
		}
	}

	/** Takes a share of a mood's pressure away (a rage fit lets some frustration out; a meal calms the nerves). */
	public void ease(Mood mood, double fraction) {
		this.pressure.put(mood, this.pressure(mood) * (1.0 - Mth.clamp(fraction, 0.0, 1.0)));
	}

	/** Forces a mood for testing (debug command). */
	public void force(Mood mood) {
		this.pressure.clear();
		if (mood != Mood.CALM) {
			this.pressure.put(mood, 1.0);
		}

		this.current = mood;
		this.reason = "debug command";
	}

	private void reasonFor(Mood mood, String why) {
		if (mood == this.current || this.pressure(mood) >= ENTER) {
			this.reason = why;
		}
	}

	/** Once a second: the steady inputs, decay, and which mood wins. Returns the new mood if it changed, else null. */
	public Mood tickSecond(DrifterEntity drifter, Personality personality) {
		long now = drifter.level().getGameTime();
		double health = drifter.getHealth() / drifter.getMaxHealth();
		Threats.Assessment threat = Threats.assess(drifter);
		double caution = personality.base(Trait.CAUTION);
		double bravery = personality.base(Trait.BRAVERY);

		if (threat.decision() != Threats.Decision.NONE) {
			if (health < 0.3) {
				this.add(Mood.PANICKED, 0.35 * (1.3 - bravery), "nearly dead with something after it");
			}

			if (threat.decision() == Threats.Decision.FLEE) {
				this.add(Mood.PANICKED, 0.15 * (1.3 - bravery), "outmatched");
			} else if (threat.decision() == Threats.Decision.RETREAT || threat.decision() == Threats.Decision.AVOID) {
				this.add(Mood.ANXIOUS, 0.1 * (0.5 + caution), "danger nearby");
			}
		}

		if (health < 0.5) {
			this.add(Mood.ANXIOUS, 0.04 * (0.5 + caution), "hurt");
		}

		// Underground at night (or thinks so), away from its shelter: uneasy, more so for careful types.
		Goals.Goal goal = drifter.getDrifterBrain().currentGoal();
		boolean sheltering = goal != null && "shelter".equals(goal.id());
		if (Schedule.isNight(Schedule.believedTime(drifter)) && !drifter.getSenses().time().canSeeSky() && !sheltering) {
			this.add(Mood.ANXIOUS, 0.02 * (0.3 + caution), "");
		}

		// Good gear keeps it confident (a floor, not a push).
		double power = Threats.power(drifter);
		double floor = Mth.clamp((power - 1.6) / 2.0, 0.0, 0.8);
		if (this.pressure(Mood.CONFIDENT) < floor) {
			this.pressure.put(Mood.CONFIDENT, floor);
		}

		this.tickBoredom(drifter, personality, now);

		for (Mood mood : Mood.values()) {
			double value = this.pressure(mood);
			if (value > 0.0 && mood != Mood.BORED) {
				value *= Math.pow(0.5, 1.0 / mood.halfLifeSeconds);
				this.pressure.put(mood, value < 0.01 ? 0.0 : value);
			}
		}

		return this.choose();
	}

	private void tickBoredom(DrifterEntity drifter, Personality personality, long now) {
		int explored = drifter.getMemory().map().exploredCount();
		int longTerm = drifter.getMemory().count(com.dinglebob.drifter.memory.MemoryKind.Tier.LONG);
		if (explored > this.lastExplored || longTerm > this.lastLongTerm) {
			if (this.lastExplored >= 0) {
				// Something new: a fresh chunk seen, something worth remembering.
				this.lastNovelty = now;
				this.pressure.put(Mood.BORED, Math.max(0.0, this.pressure(Mood.BORED) - 0.05));
			}

			this.lastExplored = explored;
			this.lastLongTerm = longTerm;
		}

		if (this.lastNovelty == 0L) {
			this.lastNovelty = now;
		}

		Goals.Goal goal = drifter.getDrifterBrain().currentGoal();
		boolean idle = goal == null || "idle".equals(goal.id()) || "shelter".equals(goal.id()) && com.dinglebob.drifter.brain.Shelter.isSealedIn(drifter);
		if (now - this.lastNovelty > BORED_AFTER_TICKS || idle) {
			// Impatient types get bored quicker.
			double rate = (idle ? 0.012 : 0.003) * (1.5 - personality.base(Trait.PATIENCE));
			this.add(Mood.BORED, rate, idle ? "nothing to do" : "nothing new for a while");
		} else {
			this.pressure.put(Mood.BORED, this.pressure(Mood.BORED) * 0.98);
		}
	}

	private Mood choose() {
		Mood best = Mood.CALM;
		double bestValue = 0.0;
		for (Mood mood : Mood.values()) {
			if (mood != Mood.CALM && this.pressure(mood) > bestValue) {
				best = mood;
				bestValue = this.pressure(mood);
			}
		}

		Mood next = this.current;
		if (this.current != Mood.CALM && this.pressure(this.current) < STAY) {
			next = Mood.CALM;
		}

		if (best != Mood.CALM && bestValue >= ENTER && best != next
			&& (next == Mood.CALM || bestValue > this.pressure(next) + SWITCH_MARGIN)) {
			next = best;
		}

		if (next != this.current) {
			this.current = next;
			if (next == Mood.CALM) {
				this.reason = "";
			}

			return next;
		}

		return null;
	}

	public String describe() {
		if (this.current == Mood.CALM) {
			return "calm";
		}

		return String.format(Locale.ROOT, "%s %.0f%%%s", this.current.id(), this.intensity() * 100.0, this.reason.isEmpty() ? "" : " (" + this.reason + ")");
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Current", this.current.name());
		CompoundTag pressures = new CompoundTag();
		this.pressure.forEach((mood, value) -> {
			if (value > 0.0) {
				pressures.putDouble(mood.name(), value);
			}
		});
		tag.put("Pressure", pressures);
		tag.putString("Reason", this.reason);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.pressure.clear();
		CompoundTag pressures = tag.getCompound("Pressure");
		for (Mood mood : Mood.values()) {
			if (pressures.contains(mood.name())) {
				this.pressure.put(mood, pressures.getDouble(mood.name()));
			}
		}

		try {
			this.current = Mood.valueOf(tag.getString("Current"));
		} catch (IllegalArgumentException e) {
			this.current = Mood.CALM;
		}

		this.reason = tag.getString("Reason");
	}
}
