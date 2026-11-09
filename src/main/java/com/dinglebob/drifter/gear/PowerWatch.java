package com.dinglebob.drifter.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Learning an item's powers by using it (spec goal 6, second layer): for a couple of seconds after its own hit, shot or
 * ability, it watches what follows, as a player would (only its own target and what it sees around it, and explosions it
 * hears), and each sign firms up a belief: the target set on fire, poisoned, withered, frozen or slowed, struck by
 * lightning, blown up, pulled toward it or sent away in a blink; other foes around it hurt too; its own health going up;
 * two arrows from one draw; an ability going on cooldown. A text guess that several landed uses never show is doubted.
 * Nothing runs while no watch is on (the tick is one field check).
 */
public final class PowerWatch {
	/** What started the watch. */
	public enum Use {
		HIT, SHOT, ABILITY
	}

	/** Explosions further than this from its target and from itself are not its item's doing. */
	private static final double EXPLOSION_NEAR = 4.0;
	/** Foes this near its target at the start may be caught by an area power. */
	private static final double AREA_RANGE = 6.0;
	/** Foes hurt besides the target for an area power. */
	private static final int AREA_COUNT = 2;
	private static final double LIGHTNING_NEAR = 3.0;
	/** A target closing on it this fast in one tick is pulled (a mob walks at about 0.3 a tick). */
	private static final double PULL_STEP = 0.7;
	/** ... and pulled at least this far. */
	private static final double PULL_TOTAL = 2.0;
	/** A target this far from where it was a tick before was teleported. */
	private static final double TELEPORT_STEP = 8.0;
	/** Arrows (or other shots) of its own from one draw for multishot. */
	private static final int MULTISHOT_COUNT = 2;

	/** Watchers that left the world mid-watch (unloaded, dead) are dropped from the list this often (game ticks). */
	private static final long PRUNE_EVERY = 200L;

	/** The Drifters with a watch on, for explosions (usually none). Server thread only. */
	private static final Set<DrifterEntity> WATCHING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static long prunedAt;

	private boolean active;
	private Use use = Use.HIT;
	@Nullable
	private Item item;
	@Nullable
	private LivingEntity target;
	private int age;
	private float targetHealth;
	private Vec3 lastTargetPos = Vec3.ZERO;
	private int targetFire;
	private int lastFrozen;
	private boolean hadPoison;
	private boolean hadWither;
	private boolean hadSlow;
	private float lastHealth;
	/** It was hurt when the watch began (a healing power could show). */
	private boolean wasHurt;
	/** The foes it saw around its target when the watch began. */
	private final List<LivingEntity> others = new ArrayList<>();
	/** Each one's health a tick ago, and whether a hit of its own (not a burn or poison left on it) has hurt it since. */
	private float[] otherHealth = new float[0];
	private boolean[] otherHit = new boolean[0];
	private boolean othersBaselined;
	/** What the enchantments on the stack in use do anyway: no news about the item. */
	private Set<Powers.Power> explained = Set.of();
	private double pulled;
	private final EnumSet<Powers.Power> seen = EnumSet.noneOf(Powers.Power.class);
	/** Landed uses in a row that did not show a text-guessed power, per item (not saved). */
	private final Map<Item, EnumMap<Powers.Power, Integer>> misses = new HashMap<>();

	public static void start(DrifterEntity d, ItemStack used, @Nullable LivingEntity target) {
		start(d, used, target, Use.HIT);
	}

	/**
	 * Starts watching what follows a use of {@code used}: a hit (called before the swing, so the target is seen as it was), a
	 * shot (called just after letting go, with its arrows in the air) or an ability (before it goes off). A watch already on
	 * ends first.
	 */
	public static void start(DrifterEntity d, ItemStack used, @Nullable LivingEntity target, Use use) {
		if (used.isEmpty() || !(d.level() instanceof ServerLevel)) {
			return;
		}

		PowerWatch watch = d.getPowerWatch();
		if (watch.active) {
			watch.finish(d);
		}

		watch.begin(d, used, target, use);
	}

	/** The use did not happen after all (an ability that did not go off): the watch ends without counting. */
	public static void cancel(DrifterEntity d) {
		PowerWatch watch = d.getPowerWatch();
		if (watch.active) {
			watch.end(d);
		}
	}

	/** An explosion (heard by every Drifter near it): a watching Drifter's item may have caused it, or it hurt the Drifter. */
	public static void explosion(ServerLevel level, Vec3 at, @Nullable Entity cause) {
		if (WATCHING.isEmpty()) {
			return;
		}

		for (DrifterEntity d : new ArrayList<>(WATCHING)) {
			if (d.isRemoved()) {
				WATCHING.remove(d);
				continue;
			}

			if (d.level() == level) {
				d.getPowerWatch().heard(d, at, cause);
			}
		}
	}

	/** Its health went up (not by eating or natural regeneration; DrifterEntity#heal). */
	public static void healed(DrifterEntity d) {
		PowerWatch watch = d.getPowerWatch();
		if (!watch.active || d.hasEffect(MobEffects.REGENERATION)) {
			return;
		}

		if (d.isUsingItem()) {
			UseAnim anim = d.getUseItem().getUseAnimation();
			if (anim == UseAnim.EAT || anim == UseAnim.DRINK) {
				return;
			}
		}

		watch.sign(d, Powers.Power.HEAL);
	}

	/** Server stop: no watches carry over. */
	public static void clearAll() {
		WATCHING.clear();
		prunedAt = 0L;
	}

	public boolean active() {
		return this.active;
	}

	private void begin(DrifterEntity d, ItemStack used, @Nullable LivingEntity target, Use use) {
		this.active = true;
		this.use = use;
		this.item = used.getItem();
		this.explained = explainedBy(used);
		this.target = target;
		this.age = 0;
		this.seen.clear();
		this.pulled = 0.0;
		this.lastHealth = d.getHealth();
		this.wasHurt = d.getHealth() < d.getMaxHealth();
		this.others.clear();
		this.othersBaselined = false;
		long now = d.level().getGameTime();
		if (now - prunedAt >= PRUNE_EVERY || now < prunedAt) {
			prunedAt = now;
			WATCHING.removeIf(Entity::isRemoved);
		}

		WATCHING.add(d);
		if (target != null) {
			this.targetHealth = target.getHealth();
			this.lastTargetPos = target.position();
			this.targetFire = target.getRemainingFireTicks();
			this.lastFrozen = target.getTicksFrozen();
			this.hadPoison = target.hasEffect(MobEffects.POISON);
			this.hadWither = target.hasEffect(MobEffects.WITHER);
			this.hadSlow = target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
			// The foes it sees around its target now (from its senses, not the world).
			for (Awareness seen : d.getSenses().trackedView()) {
				if (seen.visibleNow && seen.entity instanceof LivingEntity other && other != target && other != d && other.isAlive()
					&& (other instanceof Enemy || other instanceof Mob mob && mob.getTarget() == d)
					&& other.distanceToSqr(target) <= AREA_RANGE * AREA_RANGE) {
					this.others.add(other);
				}
			}

			// A hit is watched from just after the swing: a sweep or a Better Combat arc is the swing, not a power.
			if (use != Use.HIT) {
				this.baselineOthers();
			}
		}

		if (use == Use.SHOT) {
			// Its own shots, just loosed, still beside it.
			int shots = d.level().getEntitiesOfClass(Projectile.class, d.getBoundingBox().inflate(3.0),
				shot -> shot.tickCount == 0 && ownedBy(d, shot.getOwner())).size();
			if (shots >= MULTISHOT_COUNT) {
				this.sign(d, Powers.Power.MULTISHOT);
			}
		}
	}

	private void baselineOthers() {
		this.othersBaselined = true;
		this.otherHit = new boolean[this.others.size()];
		this.otherHealth = new float[this.others.size()];
		for (int i = 0; i < this.others.size(); i++) {
			this.otherHealth[i] = this.others.get(i).getHealth();
		}
	}

	/** Each tick of the Drifter: one check when no watch is on. */
	public void tick(DrifterEntity d) {
		if (!this.active) {
			return;
		}

		this.age++;
		LivingEntity t = this.target;
		if (t != null && !t.isRemoved()) {
			if (t.isOnFire() && this.targetFire <= 0 && !this.seen.contains(Powers.Power.FIRE) && !burnsAnyway(t)) {
				this.sign(d, Powers.Power.FIRE);
			}

			if (!this.hadPoison && t.hasEffect(MobEffects.POISON)) {
				this.sign(d, Powers.Power.POISON);
			}

			if (!this.hadWither && t.hasEffect(MobEffects.WITHER)) {
				this.sign(d, Powers.Power.WITHER);
			}

			int frozen = t.getTicksFrozen();
			if (frozen > this.lastFrozen && !t.getInBlockState().is(Blocks.POWDER_SNOW) || !this.hadSlow && t.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
				this.sign(d, Powers.Power.FREEZE);
			}

			this.lastFrozen = frozen;
			if (!this.seen.contains(Powers.Power.LIGHTNING)
				&& !d.level().getEntitiesOfClass(LightningBolt.class, t.getBoundingBox().inflate(LIGHTNING_NEAR)).isEmpty()) {
				this.sign(d, Powers.Power.LIGHTNING);
			}

			Vec3 now = t.position();
			Vec3 moved = now.subtract(this.lastTargetPos);
			// What blinks, flies or rides moves like that by itself: an Enderman dodging an arrow is no pull.
			if (!movesByItself(t)) {
				if (moved.length() >= TELEPORT_STEP) {
					this.sign(d, Powers.Power.TELEPORT);
				} else {
					Vec3 toward = new Vec3(d.getX() - this.lastTargetPos.x, 0.0, d.getZ() - this.lastTargetPos.z);
					if (toward.lengthSqr() > 1.0E-4) {
						double closing = new Vec3(moved.x, 0.0, moved.z).dot(toward.normalize());
						if (closing >= PULL_STEP) {
							this.pulled += closing;
							if (this.pulled >= PULL_TOTAL) {
								this.sign(d, Powers.Power.PULL);
							}
						}
					}
				}
			}

			this.lastTargetPos = now;
		}

		if (!this.othersBaselined) {
			this.baselineOthers();
		} else if (!this.seen.contains(Powers.Power.AREA)) {
			// Only a fresh drop this tick from a hit of its own counts: a burn, poison or the sun still working on a foe it
			// swept a moment ago has no attacker, and is not an area power.
			int hit = 0;
			for (int i = 0; i < this.others.size(); i++) {
				LivingEntity other = this.others.get(i);
				float health = other.getHealth();
				if (health < this.otherHealth[i] && !this.otherHit[i]) {
					DamageSource source = other.getLastDamageSource();
					Entity by = source == null ? null : source.getEntity();
					if (by != null && (by == d || by == d.helperPlayer)) {
						this.otherHit[i] = true;
					}
				}

				this.otherHealth[i] = health;
				if (this.otherHit[i]) {
					hit++;
				}
			}

			if (hit >= AREA_COUNT) {
				this.sign(d, Powers.Power.AREA);
			}
		}

		if (this.use == Use.ABILITY && this.item != null && d.helperPlayer != null && d.helperPlayer.getCooldowns().isOnCooldown(this.item)) {
			this.sign(d, Powers.Power.COOLDOWN);
		}

		this.lastHealth = d.getHealth();
		if (this.age >= GearData.tables().powers().watchTicks()) {
			this.finish(d);
		}
	}

	private void heard(DrifterEntity d, Vec3 at, @Nullable Entity cause) {
		if (!this.active || this.item == null || foreign(d, cause)) {
			return;
		}

		DamageSource source = d.getLastDamageSource();
		boolean hurtItself = d.getHealth() < this.lastHealth && source != null && source.is(DamageTypeTags.IS_EXPLOSION)
			&& at.distanceToSqr(d.getBoundingBox().getCenter()) <= 12.0 * 12.0;
		if (hurtItself) {
			GearLore lore = d.getGearLore();
			GearLore.Belief before = lore.beliefs(this.item).get(Powers.Power.EXPLOSIVE);
			boolean known = before != null && before.source().rank() >= GearLore.Source.SEEN.rank();
			this.seen.add(Powers.Power.EXPLOSIVE);
			lore.firm(d, this.item, Powers.Power.EXPLOSIVE, 1.0F, GearLore.Source.SEEN, "the hard way", false);
			if (!known) {
				d.getMemory().thoughts().add(d.level().getGameTime(), "found out the hard way: " + GearLore.nameOf(this.item) + " explodes");
			}

			return;
		}

		LivingEntity t = this.target;
		boolean near = at.distanceToSqr(d.position()) <= EXPLOSION_NEAR * EXPLOSION_NEAR
			|| t != null && at.distanceToSqr(t.getBoundingBox().getCenter()) <= EXPLOSION_NEAR * EXPLOSION_NEAR;
		if (near) {
			this.sign(d, Powers.Power.EXPLOSIVE);
		}
	}

	/** Something else blew up: a creeper, someone else's TNT or arrow. No cause (some mods' blasts) may be its own. */
	private static boolean foreign(DrifterEntity d, @Nullable Entity cause) {
		if (cause == null || cause == d || cause == d.helperPlayer) {
			return false;
		}

		if (cause instanceof Projectile shot) {
			return !ownedBy(d, shot.getOwner());
		}

		if (cause instanceof TraceableEntity traced) {
			return !ownedBy(d, traced.getOwner());
		}

		return true;
	}

	private static boolean ownedBy(DrifterEntity d, @Nullable Entity owner) {
		return owner != null && (owner == d || owner == d.helperPlayer);
	}

	/**
	 * What the enchantments on this stack do anyway: their own powers ({@link Powers#enchanted}), and several foes hurt at
	 * once by a Multishot volley or a Piercing bolt.
	 */
	private static Set<Powers.Power> explainedBy(ItemStack used) {
		Set<Powers.Power> own = Powers.enchanted(used);
		boolean volley = own.contains(Powers.Power.MULTISHOT);
		if (!volley) {
			for (Holder<Enchantment> enchantment : used.getEnchantments().keySet()) {
				if (enchantment.is(Enchantments.PIERCING)) {
					volley = true;
					break;
				}
			}
		}

		if (!volley) {
			return own;
		}

		EnumSet<Powers.Power> out = EnumSet.of(Powers.Power.AREA);
		out.addAll(own);
		return out;
	}

	/** An Enderman or Shulker (they blink), a flier, or a rider or mount: its jumps and dashes are its own. */
	private static boolean movesByItself(LivingEntity t) {
		return t instanceof EnderMan || t instanceof Shulker || t instanceof FlyingMob || t.isPassenger() || t.isVehicle() || t.isFallFlying();
	}

	/** On fire for a reason anyone can see: in lava or fire, or the undead in daylight. No sign of what hit it. */
	private static boolean burnsAnyway(LivingEntity t) {
		return t.isInLava() || t.getInBlockState().is(BlockTags.FIRE)
			|| t.getType().is(EntityTypeTags.UNDEAD) && t.level().isDay() && t.level().canSeeSky(t.blockPosition());
	}

	/** This watch could have shown the power (so not seeing it counts against a text guess). */
	private boolean couldShow(Powers.Power power, @Nullable LivingEntity t) {
		return switch (power) {
			case FIRE -> t != null && this.targetFire <= 0 && !t.fireImmune() && !burnsAnyway(t);
			case POISON -> t != null && !this.hadPoison && t.canBeAffected(new MobEffectInstance(MobEffects.POISON, 1));
			case WITHER -> t != null && !this.hadWither && t.canBeAffected(new MobEffectInstance(MobEffects.WITHER, 1));
			case FREEZE -> t != null && !this.hadSlow;
			case AREA -> this.others.size() >= AREA_COUNT;
			case HEAL -> this.wasHurt;
			case MULTISHOT -> this.use == Use.SHOT;
			case COOLDOWN -> this.use == Use.ABILITY;
			default -> true;
		};
	}

	/**
	 * A sign of a power, once per watch: belief up a step ("found out Bow: explosive" the first time). Not when the
	 * enchantments on the stack in use explain it (Flame, Fire Aspect, Multishot): that says nothing about the item.
	 */
	private void sign(DrifterEntity d, Powers.Power power) {
		if (this.item == null || !this.seen.add(power) || this.explained.contains(power)) {
			return;
		}

		d.getGearLore().firm(d, this.item, power, (float)GearData.tables().powers().seenStep(), GearLore.Source.SEEN, "saw it");
	}

	/**
	 * The watch is over: a landed use that showed none of a text guess counts toward doubting it, when it could have shown
	 * it (no multishot from a sword swing, no area power with nobody else around).
	 */
	private void finish(DrifterEntity d) {
		Item used = this.item;
		LivingEntity t = this.target;
		boolean landed = t != null && (t.getHealth() < this.targetHealth || t.isDeadOrDying() || t.isRemoved()) || this.use == Use.ABILITY;
		if (used != null && landed) {
			GearData.PowerRules rules = GearData.tables().powers();
			GearLore lore = d.getGearLore();
			List<Powers.Power> unseen = new ArrayList<>();
			for (Map.Entry<Powers.Power, GearLore.Belief> entry : lore.beliefs(used).entrySet()) {
				if (entry.getValue().source() == GearLore.Source.TEXT && entry.getValue().confidence() > 0.0F && !this.seen.contains(entry.getKey())
					&& this.couldShow(entry.getKey(), t)) {
					unseen.add(entry.getKey());
				}
			}

			EnumMap<Powers.Power, Integer> counts = this.misses.computeIfAbsent(used, key -> new EnumMap<>(Powers.Power.class));
			for (Powers.Power power : this.seen) {
				counts.remove(power);
			}

			for (Powers.Power power : unseen) {
				if (counts.merge(power, 1, Integer::sum) >= rules.doubtAfterUses()) {
					counts.remove(power);
					lore.doubt(used, power, (float)rules.doubtStep());
				}
			}
		}

		this.end(d);
	}

	private void end(DrifterEntity d) {
		this.active = false;
		this.target = null;
		this.item = null;
		this.explained = Set.of();
		this.others.clear();
		WATCHING.remove(d);
	}
}
