package com.dinglebob.drifter.social;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Notices what players do to it, near it and to its things (DESIGN.md section 14 event table), only as far as it can
 * tell (section 1: knowledge through perception): hits it feels, what it sees, sounds at its own chests and blocks while
 * it knows who's there. Each event goes to {@link Relationships#record}.
 */
public class SocialWatcher {
	/** A second hit within this long after the first is "attacks it repeatedly". */
	private static final int ENGAGEMENT_TICKS = 20 * 10;
	/** One hit, then nothing for this long: it stopped (read as an accident). */
	private static final int ACCIDENT_TICKS = 20 * 3;
	/** Health (half-hearts) at or under which a player's hit "nearly killed" it. */
	private static final float NEARLY_DEAD = 4.0F;
	/** Health under which killing its attacker "saved it from death". */
	private static final float SAVED_HEALTH = 6.0F;
	/** How long a mob counts as attacking it after its last hit. */
	private static final int ATTACKER_TICKS = 20 * 10;
	/** Crouches in this many ticks make crouch-spam. */
	private static final int CROUCH_WINDOW = 40;
	private static final int CROUCHES = 3;
	/** Time nearby, peacefully, for each small step of Trust and Familiarity. */
	private static final int PEACEFUL_TICKS = 20 * 60 * 5;
	private static final double PEACEFUL_RANGE = 16.0;
	/** How far round home counts as "near its base" for fire and lava. */
	private static final double BASE_RANGE = 16.0;
	/** How far it can hear a block break or a chest open (section 5 hearing table). */
	private static final double HEARING = 16.0;
	/** How recently it must have seen a player to pin a sound on them. */
	private static final int RECENTLY_SEEN = 20 * 10;

	private final DrifterEntity drifter;
	private final Map<UUID, Watch> watches = new HashMap<>();

	/** What it's keeping track of for one player right now (not saved: it's the moment). */
	private static final class Watch {
		long lastHitAt = Long.MIN_VALUE / 2;
		int hits;
		float damage;
		boolean pendingAccident;
		boolean attackedRecorded;
		boolean nearlyRecorded;
		boolean wasCrouching;
		final ArrayDeque<Long> crouches = new ArrayDeque<>();
		long lastCrouchSpamAt = Long.MIN_VALUE / 2;
		long lastAimAt = Long.MIN_VALUE / 2;
		long lastSneakAt = Long.MIN_VALUE / 2;
		long lastFireAt = Long.MIN_VALUE / 2;
		boolean wasIdentified;
		int peacefulTicks;
	}

	public SocialWatcher(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	private Watch watch(Player player) {
		return this.watches.computeIfAbsent(player.getUUID(), id -> new Watch());
	}

	private long now() {
		return this.drifter.level().getGameTime();
	}

	private double record(Player player, SocialEvent event, double amount) {
		if (event.trust < 0) {
			this.watch(player).peacefulTicks = 0;
		}

		return this.drifter.getSocial().record(player.getUUID(), player.getGameProfile().getName(), event, amount);
	}

	/** A real player (never the helper it uses for its own hands, or another mod's fake player). */
	public static boolean isRealPlayer(@Nullable Object entity) {
		return entity instanceof ServerPlayer player && !(player instanceof FakePlayer) && !player.isSpectator();
	}

	// ---------------------------------------------------------------------------------------------
	// Perception: only what it could tell
	// ---------------------------------------------------------------------------------------------

	/** It sees this player right now and knows who they are. */
	public boolean sees(Player player) {
		Awareness awareness = this.drifter.getSenses().awarenessOf(player);
		return awareness != null && awareness.identified && awareness.visibleNow;
	}

	/**
	 * It can tell this player did something at {@code pos}: it sees them, or it heard it there (in earshot) and saw them
	 * right there a moment ago.
	 */
	public boolean noticed(Player player, BlockPos pos) {
		if (this.sees(player)) {
			return true;
		}

		Awareness awareness = this.drifter.getSenses().awarenessOf(player);
		return awareness != null && awareness.identified && this.now() - awareness.lastSeenTick < RECENTLY_SEEN
			&& awareness.lastSeenPos != null && awareness.lastSeenPos.distanceTo(Vec3.atCenterOf(pos)) < 8.0
			&& this.drifter.position().distanceTo(Vec3.atCenterOf(pos)) < HEARING;
	}

	// ---------------------------------------------------------------------------------------------
	// Its own body: hits (felt, so always known)
	// ---------------------------------------------------------------------------------------------

	/** A player's hit landed (health after it). Called from the Drifter's damage handling. */
	public void hitBy(Player player, float amount, float healthAfter) {
		if (!isRealPlayer(player)) {
			return;
		}

		Watch w = this.watch(player);
		long now = this.now();
		if (now - w.lastHitAt > ENGAGEMENT_TICKS) {
			w.hits = 0;
			w.damage = 0.0F;
			w.attackedRecorded = false;
			w.nearlyRecorded = false;
		}

		w.hits++;
		w.damage += amount;
		w.lastHitAt = now;
		w.peacefulTicks = 0;
		w.pendingAccident = w.hits == 1;
		if (w.hits >= 2 && !w.attackedRecorded) {
			w.attackedRecorded = true;
			this.record(player, SocialEvent.ATTACKED, w.damage);
		}

		if (healthAfter > 0.0F && healthAfter <= NEARLY_DEAD && !w.nearlyRecorded) {
			w.nearlyRecorded = true;
			this.record(player, SocialEvent.NEARLY_KILLED, 0.0);
		}
	}

	/** It died, killed by this player. */
	public void killedBy(Player player) {
		if (isRealPlayer(player)) {
			this.record(player, SocialEvent.KILLED, 0.0);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Every few ticks: what it sees players doing
	// ---------------------------------------------------------------------------------------------

	public void tick() {
		long now = this.now();
		// One hit, then they stopped: an accident.
		for (Map.Entry<UUID, Watch> entry : this.watches.entrySet()) {
			Watch w = entry.getValue();
			if (w.pendingAccident && now - w.lastHitAt > ACCIDENT_TICKS) {
				w.pendingAccident = false;
				Player player = this.drifter.level().getPlayerByUUID(entry.getKey());
				if (player != null) {
					this.record(player, SocialEvent.HIT_ONCE, 0.0);
				}
			}
		}

		for (Awareness awareness : this.drifter.getSenses().tracked()) {
			if (!(awareness.entity instanceof Player player) || !isRealPlayer(player)) {
				continue;
			}

			Watch w = this.watch(player);
			boolean identified = awareness.identified;
			boolean visible = identified && awareness.visibleNow;
			double distance = player.distanceTo(this.drifter);
			// Sneaked up on: it only noticed them once they were right there, crouching.
			if (identified && !w.wasIdentified && distance < 4.0 && player.isShiftKeyDown() && now - w.lastSneakAt > 20 * 60) {
				w.lastSneakAt = now;
				this.record(player, SocialEvent.SNUCK_UP, 0.0);
			}

			w.wasIdentified = identified;
			if (!visible) {
				w.wasCrouching = player.isShiftKeyDown();
				continue;
			}

			Relationship r = this.drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
			r.lastSeenAt = now;
			r.lastSeenPos = player.blockPosition();
			// Where they live: it saw them go to bed.
			if (player.isSleeping()) {
				r.knownBase = player.getSleepingPos().orElse(player.blockPosition());
			}

			this.watchBow(player, w, now, distance);
			this.watchCrouches(player, w, now, distance);
			this.watchGear(player, r);
			if (distance < PEACEFUL_RANGE) {
				w.peacefulTicks += TICK_EVERY;
				if (w.peacefulTicks >= PEACEFUL_TICKS) {
					w.peacefulTicks = 0;
					this.record(player, SocialEvent.PEACEFUL_TIME, 0.0);
				}
			}
		}
	}

	/** How often {@link #tick} runs. */
	public static final int TICK_EVERY = 5;

	private void watchBow(Player player, Watch w, long now, double distance) {
		ItemStack using = player.getUseItem();
		boolean drawn = player.isUsingItem() && using.getItem() instanceof BowItem && player.getTicksUsingItem() >= 10;
		boolean loaded = player.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(player.getMainHandItem());
		if (!(drawn || loaded) || distance > 48.0 || now - w.lastAimAt < 20 * 10) {
			return;
		}

		// Aimed at it: the player's look within a few degrees of its body.
		Vec3 toIt = this.drifter.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
		if (player.getViewVector(1.0F).dot(toIt) > Math.cos(Math.toRadians(6.0))) {
			w.lastAimAt = now;
			this.record(player, SocialEvent.AIMED_BOW, 0.0);
		}
	}

	private void watchCrouches(Player player, Watch w, long now, double distance) {
		boolean crouching = player.isShiftKeyDown();
		if (crouching && !w.wasCrouching) {
			w.crouches.addLast(now);
		}

		w.wasCrouching = crouching;
		while (!w.crouches.isEmpty() && now - w.crouches.peekFirst() > CROUCH_WINDOW) {
			w.crouches.pollFirst();
		}

		// Crouching up and down at it, facing it, close by.
		Vec3 toIt = this.drifter.position().subtract(player.position()).normalize();
		if (w.crouches.size() >= CROUCHES && distance < 8.0 && player.getViewVector(1.0F).dot(toIt) > 0.7 && now - w.lastCrouchSpamAt > 20 * 5) {
			w.crouches.clear();
			w.lastCrouchSpamAt = now;
			this.record(player, SocialEvent.CROUCH_SPAM, 0.0);
		}
	}

	/** Their gear against its own (what it can see: armor, the glint, what's in their hand). */
	private void watchGear(Player player, Relationship r) {
		double theirs = gearScore(player);
		double mine = Math.max(1.0, gearScore(this.drifter));
		r.strength = theirs / mine;
		double gap = theirs - mine;
		int level = gap >= 12.0 ? 30 : gap >= 8.0 ? 20 : gap >= 4.0 ? 10 : 0;
		if (level > r.gearFear) {
			this.record(player, SocialEvent.BETTER_GEAR, level);
		}
	}

	/** Armor tiers worn plus the weapon in hand (each enchanted piece counts a little more). */
	public static double gearScore(LivingEntity entity) {
		double score = 0.0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.getItem() instanceof ArmorItem armor) {
				score += ItemValues.armorTier(armor) + (stack.isEnchanted() ? 0.5 : 0.0);
			}
		}

		ItemStack hand = entity.getMainHandItem();
		if (hand.getItem() instanceof TieredItem tool) {
			score += ItemValues.toolTier(tool) + (hand.isEnchanted() ? 0.5 : 0.0);
		}

		return score;
	}

	// ---------------------------------------------------------------------------------------------
	// Things it picks up and finds missing
	// ---------------------------------------------------------------------------------------------

	/** It's picking up an item a player threw: a gift if they were close and it saw them. */
	public void pickedUpFrom(Player player, ItemStack stack) {
		if (!isRealPlayer(player) || player.distanceTo(this.drifter) > 10.0 || !this.sees(player)) {
			return;
		}

		double value = ItemValues.of(stack);
		Relationship r = this.drifter.getSocial().getOrMeet(player.getUUID(), player.getGameProfile().getName());
		r.debts.add(new Relationship.Debt(stack.getCount() + " " + stack.getHoverName().getString(), (int)Math.round(value), this.now()));
		if (r.debts.size() > 20) {
			r.debts.remove(0);
		}

		this.record(player, SocialEvent.GIFT, SocialEvent.GIFT.trust + value);
	}

	/**
	 * It opened one of its chests and found valuables gone since it last closed it: pinned on the player it last saw
	 * opening that chest (section 14: "detected on its next chest check").
	 */
	public void missingFromChest(BlockPos chest, int valuableStacks) {
		Home home = this.drifter.getDrifterBrain().home();
		if (valuableStacks <= 0 || home == null || home.lastOpenedBy == null || this.now() - home.lastOpenedAt > 24000L * 3) {
			return;
		}

		Player player = this.drifter.level().getPlayerByUUID(home.lastOpenedBy);
		String name = home.lastOpenedByName;
		for (int i = 0; i < valuableStacks; i++) {
			if (player != null) {
				this.record(player, SocialEvent.TOOK_VALUABLES, 0.0);
			} else {
				this.drifter.getSocial().record(home.lastOpenedBy, name, SocialEvent.TOOK_VALUABLES, 0.0);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// World events, passed to the Drifters near enough to notice
	// ---------------------------------------------------------------------------------------------

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!isRealPlayer(player)) {
				return;
			}

			forDriftersNear(level, pos, 32.0, drifter -> {
				Home home = drifter.getDrifterBrain().home();
				if (home != null && home.owns(pos) && drifter.getSocial().watcher().noticed(player, pos)) {
					drifter.getSocial().watcher().record(player, SocialEvent.BROKE_BLOCK, 0.0);
				}
			});
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !isRealPlayer(player)) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hit.getBlockPos();
			ItemStack held = player.getItemInHand(hand);
			forDriftersNear(level, pos, 32.0, drifter -> {
				Home home = drifter.getDrifterBrain().home();
				SocialWatcher watcher = drifter.getSocial().watcher();
				if (home == null) {
					return;
				}

				// One of its chests opened (seen, or heard while it knew who was there).
				if (home.isChest(pos) && !player.isSecondaryUseActive() && watcher.noticed(player, pos)) {
					home.lastOpenedBy = player.getUUID();
					home.lastOpenedByName = player.getGameProfile().getName();
					home.lastOpenedAt = level.getGameTime();
					watcher.record(player, SocialEvent.OPENED_CHEST, 0.0);
				}

				watcher.fireNearBase(player, held, pos, home);
			});
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!level.isClientSide() && isRealPlayer(player) && held.is(Items.LAVA_BUCKET)) {
				forDriftersNear(level, player.blockPosition(), 32.0, drifter -> {
					Home home = drifter.getDrifterBrain().home();
					if (home != null) {
						drifter.getSocial().watcher().fireNearBase(player, held, player.blockPosition(), home);
					}
				});
			}

			return InteractionResultHolder.pass(held);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!isRealPlayer(source.getEntity())) {
				return;
			}

			Player player = (Player)source.getEntity();
			forDriftersNear(victim.level(), victim.blockPosition(), 48.0, drifter -> drifter.getSocial().watcher().sawDeath(player, victim));
		});
	}

	/** A player lit a fire or poured lava close to its home, where it could tell. */
	private void fireNearBase(Player player, ItemStack held, BlockPos pos, Home home) {
		boolean fiery = held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE) || held.is(Items.LAVA_BUCKET);
		Watch w = this.watch(player);
		if (fiery && home.center.closerThan(pos, BASE_RANGE) && this.noticed(player, pos) && this.now() - w.lastFireAt > 20 * 30) {
			w.lastFireAt = this.now();
			this.record(player, SocialEvent.FIRE_NEAR_BASE, 0.0);
		}
	}

	/** Something died at a player's hand near it. */
	private void sawDeath(Player player, LivingEntity victim) {
		if (victim == this.drifter) {
			return;
		}

		Awareness ofVictim = this.drifter.getSenses().awarenessOf(victim);
		boolean sawIt = ofVictim != null && ofVictim.visibleNow || this.sees(player);
		if (!sawIt) {
			return;
		}

		// A Drifter: the news reaches the ones who saw it (friendships come with groups; a witness counts for now).
		if (victim instanceof DrifterEntity) {
			this.record(player, SocialEvent.KILLED_FRIEND, 0.0);
			return;
		}

		// One of its animals, in its pen.
		Home home = this.drifter.getDrifterBrain().home();
		if (victim instanceof Animal && home != null && home.pen != null && home.pen.closerThan(victim.blockPosition(), 5.0)) {
			this.record(player, SocialEvent.KILLED_FARM_ANIMAL, 0.0);
			return;
		}

		// A mob that was attacking it: thanks (and if it was nearly dead, it was saved).
		boolean attacking = victim == this.drifter.getLastHurtByMob() && this.drifter.tickCount - this.drifter.getLastHurtByMobTimestamp() < ATTACKER_TICKS
			|| victim instanceof Mob mob && mob.getTarget() == this.drifter;
		if (victim instanceof Enemy && attacking) {
			this.record(player, this.drifter.getHealth() <= SAVED_HEALTH ? SocialEvent.SAVED_IT : SocialEvent.KILLED_ATTACKER, 0.0);
		}
	}

	private static void forDriftersNear(Level level, BlockPos pos, double radius, java.util.function.Consumer<DrifterEntity> action) {
		for (DrifterEntity drifter : level.getEntitiesOfClass(DrifterEntity.class, new AABB(pos).inflate(radius), DrifterEntity::isAlive)) {
			action.accept(drifter);
		}
	}
}
