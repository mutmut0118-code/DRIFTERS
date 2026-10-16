package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Schedule;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Personality;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.social.SocialWatcher;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The whim goal (milestone 13, docs/WHIMS.md "How whims work"): when nothing urgent is going on, now and then it does
 * something just because, the way players do. How keen it is comes mostly from boredom (and a triumph), less for the
 * industrious; which whim from who it is (archetype and traits, docs/WHIMS.md section 29), its mood, and what fits here
 * and now (time, weather, what it carries, what's about). Each whim has its own cooldown; a few minutes pass between
 * any two.
 */
public final class WhimGoal extends Goals.Goal {
	/** Least time between two whims (plus a random part). */
	private static final long GAP = 20L * 60 * 3;
	/** How long a pick stays chosen before it picks again (so the score and the start agree). */
	private static final long PICK_FOR = 20L * 10;

	@Nullable
	private Whim picked;
	private long pickedAt = Long.MIN_VALUE / 2;
	private long nextGap = GAP;
	@Nullable
	private Whim running;
	private double runningScore;

	@Override
	public String id() {
		return "whim";
	}

	@Override
	public String name(Goals.Context context) {
		Whim whim = this.running != null && context.drifter().getDrifterBrain().currentGoal() == this ? this.running : this.picked;
		return whim == null ? "do something just for fun" : whim.thought();
	}

	@Override
	public double urgency(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		long now = drifter.level().getGameTime();
		// Under way: it keeps at it.
		if (drifter.getDrifterBrain().currentGoal() == this && drifter.getDrifterBrain().activity() != null && this.running != null) {
			return this.runningScore;
		}

		if (!com.dinglebob.drifter.DrifterConfig.whims() || now - state(drifter).lastAny < this.nextGap || !calm(drifter, context)) {
			return 0.0;
		}

		if (this.picked == null || now - this.pickedAt > PICK_FOR && this.pickedAt < Long.MAX_VALUE / 4) {
			this.picked = pick(drifter, context);
			this.pickedAt = now;
		}

		if (this.picked == null) {
			return 0.0;
		}

		Personality p = drifter.getPersonality();
		double keen = 0.25 + 0.35 * Math.min(1.0, p.mood().pressure(Mood.BORED)) + 0.15 * Math.min(1.0, p.mood().pressure(Mood.TRIUMPHANT))
			+ 0.1 * p.get(Trait.FIDGET) + 0.1 * p.get(Trait.CURIOSITY);
		return keen * (1.15 - 0.5 * p.get(Trait.INDUSTRIOUSNESS));
	}

	/** Nothing pressing: not hurt, hungry, in danger, in the Nether or deep underground, and not out at night unless brave. */
	private static boolean calm(DrifterEntity drifter, Goals.Context context) {
		if (drifter.getHealth() < 14.0F || drifter.getFoodData().getFoodLevel() < 10 || context.threat().urgent()
			|| drifter.level().dimension() != Level.OVERWORLD || drifter.getDrifterBrain().netherTrip() != null) {
			return false;
		}

		if (com.dinglebob.drifter.brain.mining.Caver.underground(drifter, drifter.blockPosition()) && drifter.getY() < drifter.level().getSeaLevel() - 8) {
			return false;
		}

		return !Schedule.isNight(drifter) || context.sealedIn() || drifter.getPersonality().get(Trait.BRAVERY) >= 0.7;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public double risk(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 15;
	}

	@Nullable
	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		Whim whim = this.picked != null ? this.picked : pick(drifter, context);
		this.picked = null;
		if (whim == null) {
			return null;
		}

		return this.begin(drifter, whim, this.urgency(context));
	}

	/** Starts this whim now (the goal's own pick, or a debug command's). */
	@Nullable
	public Activity begin(DrifterEntity drifter, Whim whim, double score) {
		WhimAction action = WhimActions.get(whim.action());
		if (action == null) {
			return null;
		}

		boolean missing = whim.needs().stream().anyMatch(need -> count(drifter, need.item()) < need.count());
		Activity activity = missing ? new WhimPrep.MakeThen(drifter, whim, action) : action.start(drifter, whim);
		if (activity == null) {
			return null;
		}

		long now = drifter.level().getGameTime();
		state(drifter).did(whim.id(), now);
		this.nextGap = GAP + drifter.getRandom().nextInt((int)GAP);
		this.running = whim;
		this.runningScore = Math.max(0.3, score);
		drifter.getMemory().thoughts().add(now, whim.thought());
		if (!whim.chat().isEmpty() && drifter.getRandom().nextFloat() < 0.5F) {
			WhimActions.say(drifter, whim.chat().get(drifter.getRandom().nextInt(whim.chat().size())));
		}

		return activity;
	}

	public static WhimState state(DrifterEntity drifter) {
		return drifter.getDrifterBrain().whims();
	}

	// ---------------------------------------------------------------------------------------------
	// Choosing
	// ---------------------------------------------------------------------------------------------

	/** A weighted pick among the whims that fit now (null if none). */
	@Nullable
	static Whim pick(DrifterEntity drifter, Goals.Context context) {
		List<Whim> fits = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double total = 0.0;
		for (Whim whim : Whims.all()) {
			double weight = weight(drifter, whim);
			if (weight <= 0.0 || !fits(drifter, whim, context)) {
				continue;
			}

			fits.add(whim);
			weights.add(weight);
			total += weight;
		}

		if (fits.isEmpty()) {
			return null;
		}

		double roll = drifter.getRandom().nextDouble() * total;
		for (int i = 0; i < fits.size(); i++) {
			roll -= weights.get(i);
			if (roll <= 0.0) {
				return fits.get(i);
			}
		}

		return fits.get(fits.size() - 1);
	}

	/** How much this Drifter goes for this whim: base weight, archetype, traits and mood. 0: never. */
	public static double weight(DrifterEntity drifter, Whim whim) {
		Personality p = drifter.getPersonality();
		if (whim.gates().later()) {
			return 0.0;
		}

		double weight = whim.weight() * whim.archetypes().getOrDefault(p.archetype(), 1.0);
		for (Map.Entry<Trait, Double> trait : whim.traits().entrySet()) {
			weight *= Math.max(0.05, 1.0 + (p.get(trait.getKey()) - 0.5) * 2.0 * trait.getValue());
		}

		for (Map.Entry<Mood, Double> mood : whim.moods().entrySet()) {
			weight *= 1.0 + Math.min(1.0, p.mood().pressure(mood.getKey())) * mood.getValue();
		}

		// [R]: the careful ones rarely.
		if (whim.gates().risky()) {
			double caution = p.get(Trait.CAUTION);
			weight *= caution > 0.8 ? 0.0 : 1.0 - caution;
		}

		return weight;
	}

	/** It fits now: off cooldown, the stage, the gates, the time and weather, what it carries, and the action can start. */
	public static boolean fits(DrifterEntity drifter, Whim whim, @Nullable Goals.Context context) {
		return whyNot(drifter, whim, context) == null;
	}

	/** Why it doesn't fit now (for the command), or null if it does. */
	@Nullable
	public static String whyNot(DrifterEntity drifter, Whim whim, @Nullable Goals.Context context) {
		long now = drifter.level().getGameTime();
		if (now - state(drifter).lastDone(whim.id()) < whim.cooldown()) {
			return "done it too recently";
		}

		if (context != null && context.stage() < whim.stage()) {
			return "not far enough along (stage " + whim.stage() + ")";
		}

		// [G]: only a hostile one, and only with griefing on.
		if (whim.gates().griefing() && (!com.dinglebob.drifter.DrifterConfig.raiding()
			|| drifter.getSocial().disposition() != com.dinglebob.drifter.social.Disposition.BANDIT)) {
			return "not that kind of Drifter";
		}

		if (whim.gates().multiplayer() && !company(drifter)) {
			return "nobody about";
		}

		if (!when(drifter.level(), drifter, whim.when())) {
			return "not the right time or weather (" + whim.when().name().toLowerCase(java.util.Locale.ROOT) + ")";
		}

		// What it needs: carried, or (if the whim says how) something it can make quickly from what it has round here.
		for (Whim.Need need : whim.needs()) {
			if (count(drifter, need.item()) < need.count() && !WhimPrep.canMake(drifter, whim, need, context == null ? null : context.world())) {
				return "doesn't have " + need.item() + " (and can't make it)";
			}
		}

		WhimAction action = WhimActions.get(whim.action());
		return action != null && action.possible(drifter, whim) ? null : "nothing about to do it with";
	}


	/** A player or another Drifter it can see close by. */
	static boolean company(DrifterEntity drifter) {
		for (var awareness : drifter.getSenses().tracked()) {
			if (awareness.identified && awareness.seenWithin(drifter.level().getGameTime(), 20 * 10) && awareness.entity.distanceTo(drifter) < 24.0
				&& (awareness.entity instanceof Player player && SocialWatcher.isRealPlayer(player) || awareness.entity instanceof DrifterEntity)) {
				return true;
			}
		}

		return false;
	}

	/** The time of day and the weather (it can see the sky). */
	static boolean when(Level level, DrifterEntity drifter, Whim.When when) {
		long time = level.getDayTime() % 24000L;
		return switch (when) {
			case ANY -> true;
			case DAY -> time < 12000L;
			case NIGHT -> time >= 13000L && time < 23000L;
			case SUNSET -> time >= 11500L && time < 13000L;
			case SUNRISE -> time >= 22800L || time < 500L;
			case RAIN -> level.isRaining();
			case THUNDER -> level.isThundering();
			case CLEAR -> !level.isRaining() && time < 12000L;
		};
	}

	/** How many it carries of an item id, or of an item tag ("#namespace:tag"). */
	public static int count(DrifterEntity drifter, String item) {
		if (item.startsWith("#")) {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(item.substring(1)));
			return drifter.getInventory().count(stack -> stack.is(tag));
		}

		Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
		return drifter.getInventory().count(stack -> stack.is(it));
	}

	/** Debug: this whim next, whatever else (the command then forces the goal). */
	public void force(Whim whim) {
		this.picked = whim;
		this.pickedAt = Long.MAX_VALUE / 2;
	}
}
