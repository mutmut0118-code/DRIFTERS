package com.dinglebob.drifter.brain;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Combat;
import com.dinglebob.drifter.perception.Awareness;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.Items;

/**
 * Threat assessment (DESIGN.md section 13): the danger of the hostiles it has noticed against its own power, and
 * what that ratio says to do. Base danger per mob comes from the data files {@code data/<namespace>/drifter_threats/}.
 *
 * <p>Threat = sum over noticed hostiles of base danger x gear x proximity (x1.3 when it's coming for the Drifter).
 * Power = health x armor x weapon damage per second x shield x food. Bravery and Caution shift the thresholds
 * (section 17): the ratio is judged through {@link #courage}, so a brave Drifter takes fights a careful one avoids.
 * A coward (low Bravery) only fights when cornered (section 13 "Coward").
 */
public final class Threats {
	public static final String DIRECTORY = "drifter_threats";
	/** Hostiles farther than this are ignored. */
	public static final double RANGE = 16.0;
	/** How far it watches threats with a bow ready. */
	public static final double BOW_RANGE = 24.0;

	/** Worth an arrow now: it has a bow, the bow beats the sword here, and the thing is coming at it (or flies). */
	public static boolean worthShooting(DrifterEntity drifter, @org.jetbrains.annotations.Nullable LivingEntity target) {
		return target != null && com.dinglebob.drifter.hands.Archery.shouldShoot(drifter, target)
			&& (isAfter(drifter, target) || com.dinglebob.drifter.hands.Archery.flying(target));
	}
	/** A hostile within this, or one that hit it in the last 2 s, makes the decision urgent. */
	public static final double CLOSE = 6.0;
	public static final int RECENT_HIT_TICKS = 40;

	/** Section 13 ratio bands. */
	public enum Decision {
		/** Nothing to deal with. */
		NONE,
		/** Ratio above 2. */
		ENGAGE,
		/** 1.2 to 2: fight, carefully. */
		ENGAGE_CAREFUL,
		/** 0.8 to 1.2: avoid and go around (no bow yet). */
		AVOID,
		/** 0.5 to 0.8: back off toward safety, fight only if cornered. */
		RETREAT,
		/** Below 0.5: run or hide now. */
		FLEE;

		public boolean fights() {
			return this == ENGAGE || this == ENGAGE_CAREFUL;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}
	}

	public record Assessment(double threat, double power, Decision decision, @Nullable LivingEntity main, List<LivingEntity> hostiles,
		double nearest, boolean recentlyHit) {
		public double ratio() {
			return this.threat <= 0.0 ? Double.POSITIVE_INFINITY : this.power / this.threat;
		}

		/** Something is close or just hit it: deal with it now. */
		public boolean urgent() {
			return this.decision != Decision.NONE && (this.nearest <= CLOSE || this.recentlyHit);
		}

		public String describe() {
			if (this.decision == Decision.NONE) {
				return "no threats";
			}

			return String.format(Locale.ROOT, "threat %.1f vs power %.1f (%.2f): %s%s", this.threat, this.power, this.ratio(), this.decision.id(),
				this.main == null ? "" : " [" + this.main.getType().getDescription().getString().toLowerCase(Locale.ROOT) + "]");
		}
	}

	private record Entry(double danger, boolean perSize, boolean neutral) {
	}

	public static final Assessment NONE = new Assessment(0.0, 1.0, Decision.NONE, null, List.of(), Double.MAX_VALUE, false);

	private static volatile Map<ResourceLocation, Entry> table = Map.of();

	private Threats() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("threats");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	private static void load(ResourceManager manager) {
		Map<ResourceLocation, Entry> loaded = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject threats = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("threats");
				for (Map.Entry<String, JsonElement> entry : threats.entrySet()) {
					JsonObject value = entry.getValue().getAsJsonObject();
					loaded.put(ResourceLocation.parse(entry.getKey()), new Entry(value.get("danger").getAsDouble(),
						value.has("per_size") && value.get("per_size").getAsBoolean(), value.has("neutral") && value.get("neutral").getAsBoolean()));
				}
			} catch (Exception e) {
				Drifter.LOGGER.error("Couldn't read Drifter threats file {}", file.getKey(), e);
			}
		}

		table = Map.copyOf(loaded);
		Drifter.LOGGER.info("Loaded {} Drifter threat entries", loaded.size());
	}

	/** Base danger of this entity right now, or 0 if it's no threat (a passive animal, a calm neutral mob). */
	/** How dangerous one of this kind is once it's after it (its table value, angry): for weighing up a fight in advance. */
	public static double dangerWhenAngry(net.minecraft.world.entity.EntityType<?> type) {
		Entry entry = table.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		return (entry == null ? 1.0 : entry.danger()) * 1.3;
	}

	public static double danger(DrifterEntity drifter, LivingEntity entity) {
		// Another Drifter (milestone 12): never one of its own group; a bitter rival is an enemy on sight (section 21
		// "Rivalry": rivals may fight); anyone else only once it comes for it, as before.
		if (entity instanceof DrifterEntity other) {
			var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
			if (group != null && group == com.dinglebob.drifter.world.DrifterGroups.of(other.getUUID())) {
				return 0.0;
			}

			// The aggression-toward-each-other setting: at 0 never enemies (it still fights back when hit, below); at 100 every
			// Drifter outside its group is one on sight (user's setting).
			int aggression = com.dinglebob.drifter.DrifterConfig.drifterAggression();
			var bond = drifter.getBonds().get(other.getUUID());
			if (aggression > 0 && (aggression >= 100 || bond != null && bond.trust <= BITTER_RIVAL)) {
				return RIVAL_DANGER * (isAfter(drifter, entity) ? 1.3 : 1.0) * (1.0 + entity.getArmorValue() / 20.0);
			}
		}

		Entry entry = table.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
		boolean after = isAfter(drifter, entity);
		double danger;
		if (entry != null) {
			if (entry.neutral() && !after) {
				return 0.0;
			}

			if (entity instanceof Spider && entity.getLightLevelDependentMagicValue() >= 0.5F && !after) {
				// Spiders (and cave spiders) are neutral in daylight, as for a player.
				return 0.0;
			}

			danger = entry.danger();
			if (entry.perSize() && entity instanceof Slime slime) {
				danger *= slime.getSize();
			}
		} else if (entity instanceof Enemy) {
			if (entity instanceof Spider && entity.getLightLevelDependentMagicValue() >= 0.5F && !after) {
				// Spiders (and cave spiders) are neutral in daylight, as for a player.
				return 0.0;
			}

			danger = entity.getMaxHealth() / 20.0;
		} else {
			return after ? entity.getMaxHealth() / 20.0 : 0.0;
		}

		// Gear it can see on the mob: armor and a weapon in hand.
		danger *= 1.0 + entity.getArmorValue() / 20.0;
		if (!entity.getMainHandItem().isEmpty()) {
			danger *= entity.getMainHandItem().isEnchanted() ? 1.4 : 1.2;
		}

		return after ? danger * 1.3 : danger;
	}

	/** It can tell this one is coming for it (it's heading at it, or just hit it). */
	public static boolean isAfter(DrifterEntity drifter, LivingEntity entity) {
		if (entity instanceof Mob mob && mob.getTarget() == drifter || drifter.getLastHurtByMob() == entity) {
			return true;
		}

		// Going for one of its group close by counts too: members stand up for each other (section 21 "defending").
		if (entity instanceof Mob mob && mob.getTarget() instanceof DrifterEntity target && target != drifter && target.distanceTo(drifter) < DEFEND_RANGE) {
			var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
			return group != null && group == com.dinglebob.drifter.world.DrifterGroups.of(target.getUUID());
		}

		return false;
	}

	/** How close a group member must be for it to stand up for them. */
	private static final double DEFEND_RANGE = 16.0;

	/** A switch between fighting and backing off waits this long after the last change. */
	private static final long COMMIT_TICKS = 60;
	/** An archer closer than this: it rushes it rather than backing off in the open. */
	private static final double ARCHER_RUSH = 12.0;

	/** A mob that shoots (skeletons, pillagers, anything holding a bow or crossbow). */
	/** Trust in another Drifter at or below which it's an enemy on sight, and how dangerous one counts. */
	public static final double BITTER_RIVAL = -60.0;
	private static final double RIVAL_DANGER = 1.5;

	/** Above this health, with a shield, it stands and fights blazes rather than running from them. */
	public static final float BLAZE_STAND_HEALTH = NetherGoals.HoleUp.HURT_UNDER_FIRE;

	/** How much a shooter counts against a Drifter with a shield in hand. */
	public static final double SHIELDED_SHOOTER = 0.5;

	/** It attacks from range with projectiles (arrows, fireballs). */
	public static boolean shoots(LivingEntity entity) {
		return isArcher(entity) || entity instanceof net.minecraft.world.entity.monster.Blaze || entity instanceof net.minecraft.world.entity.monster.Ghast;
	}

	public static boolean isArcher(LivingEntity entity) {
		return entity instanceof net.minecraft.world.entity.monster.AbstractSkeleton
			|| entity.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
	}

	/** Below this Bravery it fights like a coward (section 13 fighting styles). */
	public static final double COWARD_BRAVERY = 0.3;

	/**
	 * How the personality reads the odds (section 17): 1.0 for a middling Drifter, up to about 1.3 for a brave, careless
	 * one and down to about 0.7 for a timid, careful one. Moods count (Confident is braver, Panicked much less so).
	 */
	public static double courage(DrifterEntity drifter) {
		com.dinglebob.drifter.personality.Personality p = drifter.getPersonality();
		return 1.0 + 0.6 * (p.get(com.dinglebob.drifter.personality.Trait.BRAVERY) - 0.5) - 0.3 * (p.get(com.dinglebob.drifter.personality.Trait.CAUTION) - 0.5);
	}

	/** Its own fighting power (1.0 is a fresh, unarmed player at full health). */
	public static double power(DrifterEntity drifter) {
		double health = (drifter.getHealth() + drifter.getAbsorptionAmount()) / 20.0;
		double armor = 1.0 + (drifter.getArmorValue() + drifter.getAttributeValue(Attributes.ARMOR_TOUGHNESS) / 2.0) / 12.0;
		int weaponSlot = Combat.bestWeaponSlot(drifter);
		double weapon = Combat.weaponStats(drifter, weaponSlot < 0 ? net.minecraft.world.item.ItemStack.EMPTY
			: drifter.getInventory().getItem(weaponSlot)).perSecond() / 4.0;
		double shield = drifter.getInventory().count(stack -> stack.is(Items.SHIELD)) > 0 ? 1.3 : 1.0;
		double food = Food.carriedNutrition(drifter) >= 6 ? 1.1 : 0.9;
		return health * armor * Math.max(1.0, weapon) * shield * food;
	}

	/** The current assessment, from what it has noticed (perception only). */
	public static Assessment assess(DrifterEntity drifter) {
		long now = drifter.level().getGameTime();
		LivingEntity lastAttacker = drifter.getLastHurtByMob();
		boolean recentlyHit = lastAttacker != null && drifter.tickCount - drifter.getLastHurtByMobTimestamp() < RECENT_HIT_TICKS;
		List<LivingEntity> hostiles = new ArrayList<>();
		// With a bow it watches further out: something coming can be shot before it arrives.
		double range = com.dinglebob.drifter.hands.Archery.ready(drifter) ? BOW_RANGE : RANGE;
		double threat = 0.0;
		double nearest = Double.MAX_VALUE;
		LivingEntity main = null;
		double mainScore = 0.0;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (!(awareness.entity instanceof LivingEntity living) || !living.isAlive() || living == drifter) {
				continue;
			}

			// Noticed and recently in sight, heard (a groan, a rattle: it knows what's there and roughly where), or the one
			// that just hit it (it knows that much without seeing it).
			boolean seen = awareness.identified && awareness.seenWithin(now, 20 * 4);
			boolean heard = awareness.identified && awareness.heardWithin(now, 20 * 4);
			if (!seen && !heard && !(recentlyHit && living == lastAttacker)) {
				continue;
			}

			double distance = seen || recentlyHit && living == lastAttacker ? living.distanceTo(drifter)
				: awareness.believedPos(now, 20 * 4).distanceTo(drifter.position());
			// Fliers (a ghast shoots from 60 blocks off) are watched as far as a bow can answer them.
			if (distance > (com.dinglebob.drifter.hands.Archery.flying(living) && range > RANGE ? com.dinglebob.drifter.hands.Archery.MAX_RANGE : range)) {
				continue;
			}

			double danger = danger(drifter, living);
			// A shield up stops arrows and fireballs dead, which is how a player takes on blazes and skeletons: shooters count
			// for less when it has one ready (found in testing: in iron and diamond, a spawner's three or four blazes had it
			// "outmatched", and it ran from the fortress it came for).
			if (shoots(living) && drifter.getOffhandItem().is(Items.SHIELD)) {
				danger *= SHIELDED_SHOOTER;
			}
			if (danger <= 0.0) {
				continue;
			}

			// One that's chasing it counts in full however far it is, so stepping away doesn't flip the decision back
			// and forth (found in review); others count less with distance.
			double proximity = distance <= 4.0 || isAfter(drifter, living) ? 1.0 : Mth.lerp((distance - 4.0) / (RANGE - 4.0), 1.0, 0.25);
			double score = danger * proximity;
			threat += score;
			hostiles.add(living);
			nearest = Math.min(nearest, distance);
			// The one to deal with first: the most dangerous up close, of the ones it can see (it doesn't go for one it
			// only heard; it's on its guard and looks).
			if (score > mainScore && (seen || recentlyHit && living == lastAttacker)) {
				mainScore = score;
				main = living;
			}
		}

		if (hostiles.isEmpty()) {
			return NONE;
		}

		// Something hitting it from right there comes first, before one further off however dangerous (found in the user's
		// game: aiming its bow at a creeper, it let a zombie hit it from behind five times until it died). A player turns
		// round to what's hitting them.
		if (recentlyHit && lastAttacker != null && lastAttacker.isAlive() && main != lastAttacker && hostiles.contains(lastAttacker)
			&& lastAttacker.distanceTo(drifter) < 3.5 && (main == null || main.distanceTo(drifter) >= 3.5)) {
			main = lastAttacker;
		}

		hostiles.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(drifter)));
		double power = power(drifter);
		double ratio = power / threat * courage(drifter);
		Decision decision = ratio > 2.0 ? Decision.ENGAGE : ratio >= 1.2 ? Decision.ENGAGE_CAREFUL : ratio >= 0.8 ? Decision.AVOID
			: ratio >= 0.5 ? Decision.RETREAT : Decision.FLEE;
		// A coward backs off from fights it could take (section 13 "Coward: disengages early, fights only cornered").
		if (decision == Decision.ENGAGE_CAREFUL && drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.BRAVERY) < COWARD_BRAVERY) {
			decision = Decision.AVOID;
		}

		// Cornered: something close just hit it and it isn't hopeless: fight back.
		if ((decision == Decision.AVOID || decision == Decision.RETREAT) && recentlyHit && lastAttacker != null && lastAttacker.distanceTo(drifter) < 3.5) {
			decision = Decision.ENGAGE_CAREFUL;
			main = lastAttacker;
		}

		// Stuck where it is (wedged in leaves, a corner) while something right there keeps hitting it: running isn't working,
		// so even a hopeless one fights back, as a player would (found by the user: one died "running away" stuck under leaves
		// while a zombie hit it for five seconds).
		// Nor when it can't get away: something faster than it can run (a baby zombie: found by the user, four Drifters killed
		// running from ones that hit them from "below" all the way), or one that has hit it twice while it backed off.
		// Running only gets it hit in the back; a player turns and fights.
		boolean backingOff = decision == Decision.FLEE || decision == Decision.RETREAT || decision == Decision.AVOID;
		if (backingOff && recentlyHit && lastAttacker != null && lastAttacker.distanceTo(drifter) < 3.5
			&& (stuckInPlace(drifter, now) || cantOutrun(lastAttacker)
				|| drifter.getDrifterBrain().hitsWhileBackingOff >= 2 && now - drifter.getDrifterBrain().lastHitWhileBackingOff < 100)) {
			decision = Decision.ENGAGE_CAREFUL;
			main = lastAttacker;
		}

		// An archer close by (a skeleton): backing off in the open only gets it shot in the back, so it rushes it unless it's
		// hopeless (found in a user's test: shot dead switching between fighting and fleeing a skeleton).
		if ((decision == Decision.AVOID || decision == Decision.RETREAT) && main != null && isArcher(main) && main.distanceTo(drifter) < ARCHER_RUSH) {
			decision = Decision.ENGAGE_CAREFUL;
		}

		// The same for blazes, with a shield to block their bursts: they fly after it and shoot it in the back, so running
		// only gets it burned (found in testing: a little hurt, it ran from a spawner's blazes and died, shieldless, in a
		// corner); it stands and fights unless badly hurt.
		if ((decision == Decision.AVOID || decision == Decision.RETREAT) && main instanceof net.minecraft.world.entity.monster.Blaze
			&& drifter.getOffhandItem().is(Items.SHIELD) && drifter.getHealth() > BLAZE_STAND_HEALTH) {
			decision = Decision.ENGAGE_CAREFUL;
		}

		// Commitment: a switch between fighting and backing off within 3 s doesn't happen unless it's become hopeless (each
		// hit tipped the odds and it dithered until it died).
		com.dinglebob.drifter.brain.Brain brain = drifter.getDrifterBrain();
		boolean fightNow = decision == Decision.ENGAGE || decision == Decision.ENGAGE_CAREFUL;
		boolean backOffNow = decision == Decision.AVOID || decision == Decision.RETREAT;
		if (brain.lastThreatDecision != null && now - brain.lastThreatDecisionAt < COMMIT_TICKS) {
			Decision before = brain.lastThreatDecision;
			boolean wasFighting = before == Decision.ENGAGE || before == Decision.ENGAGE_CAREFUL;
			boolean wasBackingOff = before == Decision.AVOID || before == Decision.RETREAT;
			if (wasFighting && backOffNow || wasBackingOff && fightNow) {
				decision = before;
			}
		}

		if (decision != brain.lastThreatDecision) {
			brain.lastThreatDecision = decision;
			brain.lastThreatDecisionAt = now;
		}

		return new Assessment(threat, power, decision, main, List.copyOf(hostiles), nearest, recentlyHit);
	}

	/** Moved less than this in {@link #STUCK_TICKS}: it's stuck where it is. */
	private static final double STUCK_MOVE = 0.75;
	private static final int STUCK_TICKS = 40;

	/** Hasn't got anywhere in the last two seconds or so (checked every couple of seconds). */
	private static boolean stuckInPlace(DrifterEntity drifter, long now) {
		com.dinglebob.drifter.brain.Brain brain = drifter.getDrifterBrain();
		if (brain.stuckCheckPos == null || now - brain.stuckCheckAt > STUCK_TICKS * 2) {
			brain.stuckCheckPos = drifter.position();
			brain.stuckCheckAt = now;
			brain.stuckInPlace = false;
		} else if (now - brain.stuckCheckAt >= STUCK_TICKS) {
			brain.stuckInPlace = drifter.position().distanceTo(brain.stuckCheckPos) < STUCK_MOVE;
			brain.stuckCheckPos = drifter.position();
			brain.stuckCheckAt = now;
		}

		return brain.stuckInPlace;
	}

	/** Faster than a sprinting player: no use running from it. */
	private static boolean cantOutrun(LivingEntity mob) {
		return mob instanceof net.minecraft.world.entity.monster.Zombie zombie && zombie.isBaby();
	}
}
