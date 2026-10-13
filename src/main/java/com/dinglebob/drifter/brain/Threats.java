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
 * Power = health x armor x weapon damage per second x shield x food. TODO(milestone 7): Bravery and Caution shift
 * the thresholds.
 */
public final class Threats {
	public static final String DIRECTORY = "drifter_threats";
	/** Hostiles farther than this are ignored. */
	public static final double RANGE = 16.0;
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
	public static double danger(DrifterEntity drifter, LivingEntity entity) {
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
		return entity instanceof Mob mob && mob.getTarget() == drifter || drifter.getLastHurtByMob() == entity;
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
		double threat = 0.0;
		double nearest = Double.MAX_VALUE;
		LivingEntity main = null;
		double mainScore = 0.0;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (!(awareness.entity instanceof LivingEntity living) || !living.isAlive() || living == drifter) {
				continue;
			}

			// Noticed and recently in sight, or the one that just hit it (it knows that much without seeing it).
			boolean noticed = awareness.identified && now - awareness.lastSeenTick < 20 * 4;
			if (!noticed && !(recentlyHit && living == lastAttacker)) {
				continue;
			}

			double distance = living.distanceTo(drifter);
			if (distance > RANGE) {
				continue;
			}

			double danger = danger(drifter, living);
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
			// The one to deal with first: the most dangerous up close.
			if (score > mainScore) {
				mainScore = score;
				main = living;
			}
		}

		if (hostiles.isEmpty()) {
			return NONE;
		}

		hostiles.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(drifter)));
		double power = power(drifter);
		double ratio = power / threat;
		Decision decision = ratio > 2.0 ? Decision.ENGAGE : ratio >= 1.2 ? Decision.ENGAGE_CAREFUL : ratio >= 0.8 ? Decision.AVOID
			: ratio >= 0.5 ? Decision.RETREAT : Decision.FLEE;
		// Cornered: something close just hit it and it isn't hopeless: fight back.
		if ((decision == Decision.AVOID || decision == Decision.RETREAT) && recentlyHit && lastAttacker != null && lastAttacker.distanceTo(drifter) < 3.5) {
			decision = Decision.ENGAGE_CAREFUL;
			main = lastAttacker;
		}

		return new Assessment(threat, power, decision, main, List.copyOf(hostiles), nearest, recentlyHit);
	}
}
