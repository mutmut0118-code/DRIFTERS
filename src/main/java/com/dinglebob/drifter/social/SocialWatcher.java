package com.dinglebob.drifter.social;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import net.minecraft.world.item.Item;
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

	/** Caught in the act: valuables gone when it checks within this long of watching them at the chest. */
	private static final long RED_HANDED_TICKS = 20 * 60 * 2;
	/** It goes to check a chest it saw opened within this long (later, it'll find out on its next visit anyway). */
	private static final long CHECK_WITHIN_TICKS = 20 * 60 * 2;
	/** How close it must be to make out what someone's wearing and holding. */
	private static final double GEAR_RANGE = 16.0;

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
		/** It gave in to them in this fight. */
		boolean gaveIn;
		boolean wasCrouching;
		final ArrayDeque<Long> crouches = new ArrayDeque<>();
		long lastCrouchSpamAt = Long.MIN_VALUE / 2;
		long lastAimAt = Long.MIN_VALUE / 2;
		long lastSneakAt = Long.MIN_VALUE / 2;
		long lastFireAt = Long.MIN_VALUE / 2;
		boolean wasIdentified;
		int peacefulTicks;
		/** When it started watching them on noticing them, and when it last did. */
		long watchStartAt = Long.MIN_VALUE / 2;
		long lastWatchedAt = Long.MIN_VALUE / 2;
		/** Its last warning to them, and whether they ignored it (stayed, came closer). */
		long warnedAt = Long.MIN_VALUE / 2;
		boolean warningIgnored;
		/** "Follow me": crouched twice near it, facing it (then walks off); and where they were then. */
		long invitedAt = Long.MIN_VALUE / 2;
		@Nullable
		Vec3 invitedFrom;
		/**
		 * How long the invitation stands (crouching: a few seconds; asked in chat: while they finish typing), and how long it
		 * follows them for (0: the usual).
		 */
		int invitedFor = 20 * 6;
		int followFor;
		/** They told it to stop following ("stay here", "go away"), when. */
		long toldToStopAt = Long.MIN_VALUE / 2;
		/** When it last greeted them. */
		long greetedAt = Long.MIN_VALUE / 2;
		/** They just gave it something: it thanks them (once). */
		long giftAt = Long.MIN_VALUE / 2;
		boolean thanked = true;
		/** What it's answering: a gift, or a hello (crouch-spam). */
		String respondTo = "the gift";
		/** They said its name in chat, when, and where they were. */
		long calledAt = Long.MIN_VALUE / 2;
		@Nullable
		Vec3 calledFrom;
	}

	/** How far it hears its name said in chat (user's choice: about 32 blocks). */
	public static final double NAME_RANGE = 32.0;

	/** Someone who said its name in the last few seconds and where they stood, or null. */
	@Nullable
	public Map.Entry<Player, Vec3> calledBy() {
		for (Map.Entry<UUID, Watch> entry : this.watches.entrySet()) {
			Watch w = entry.getValue();
			if (w.calledFrom != null && this.now() - w.calledAt < 20 * 4) {
				Player player = this.drifter.level().getPlayerByUUID(entry.getKey());
				if (player != null) {
					return Map.entry(player, w.calledFrom);
				}
			}
		}

		return null;
	}

	public void answeredCall(Player player) {
		this.watch(player).calledFrom = null;
	}

	/** What it owes {@link #owedThanks} for ("the gift", "the hello"). */
	public String respondingTo(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w == null ? "the gift" : w.respondTo;
	}

	/** A player who just gave it something and hasn't been thanked yet (in the last few seconds), or null. */
	@Nullable
	public Player owedThanks() {
		for (Map.Entry<UUID, Watch> entry : this.watches.entrySet()) {
			Watch w = entry.getValue();
			if (!w.thanked && this.now() - w.giftAt < 20 * 6) {
				Player player = this.drifter.level().getPlayerByUUID(entry.getKey());
				if (player != null && this.sees(player)) {
					return player;
				}
			}
		}

		return null;
	}

	public void thanked(Player player) {
		this.watch(player).thanked = true;
	}

	/** They asked it to follow (crouched twice near it) in the last few seconds, and where they stood: or null. */
	@Nullable
	public Vec3 invitedFrom(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && this.now() - w.invitedAt < w.invitedFor ? w.invitedFrom : null;
	}

	/**
	 * They asked it in chat to come along (chat phase 2): as the crouch invitation, standing for {@code forTicks} (it follows
	 * once they walk off), for {@code followTicks} (0: the usual).
	 */
	public void invite(Player player, int forTicks, int followTicks) {
		Watch w = this.watch(player);
		w.invitedAt = this.now();
		w.invitedFrom = player.position();
		w.invitedFor = forTicks;
		w.followFor = followTicks;
	}

	/** The invitation standing now was asked in chat (someone's waiting on an answer they got). */
	public boolean askedToFollow(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && w.invitedFor > 20 * 6 && this.invitedFrom(player) != null;
	}

	/** How long it follows them this time ({@code usual} unless they asked for longer). */
	public int followTicks(Player player, int usual) {
		Watch w = this.watches.get(player.getUUID());
		return w == null || w.followFor <= 0 ? usual : w.followFor;
	}

	/** They told it to stop following. */
	public void toldToStop(Player player) {
		this.watch(player).toldToStopAt = this.now();
	}

	/** They told it to stop since this time. */
	public boolean toldToStopSince(Player player, long since) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && w.toldToStopAt >= since;
	}

	/** They talked to it in chat (not by name): it turns to look for them, as when its name is called. */
	public void spokenTo(Player player) {
		Watch w = this.watch(player);
		w.calledAt = this.now();
		w.calledFrom = player.getEyePosition();
	}

	public void followed(Player player) {
		Watch w = this.watch(player);
		w.invitedAt = Long.MIN_VALUE / 2;
		w.invitedFor = 20 * 6;
	}

	/** It greeted them lately. */
	public boolean greetedLately(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && this.now() - w.greetedAt < 20 * 60 * 5;
	}

	public void greeted(Player player) {
		this.watch(player).greetedAt = this.now();
	}

	/** Watches someone again only if it hasn't for this long. */
	private static final int REWATCH_TICKS = 20 * 60 * 2;
	/** An ignored warning stands this long (then a new one is owed). */
	private static final int WARNING_STANDS = 20 * 60 * 10;
	/** After a heeded warning, it doesn't warn the same player again for this long. */
	private static final int WARNING_COOLDOWN = 20 * 30;

	/** When it started watching this player (just noticed them), or -1 if it isn't. */
	public long watchingSince(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && this.now() - w.watchStartAt < Mode.WATCH_TICKS ? w.watchStartAt : -1;
	}

	/** They ignored its warning lately: hostility no longer needs another one. */
	public boolean warningIgnored(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && w.warningIgnored && this.now() - w.warnedAt < WARNING_STANDS;
	}

	/** It warned them a moment ago (and they backed off): not again yet. */
	public boolean warnedLately(Player player) {
		Watch w = this.watches.get(player.getUUID());
		return w != null && this.now() - w.warnedAt < WARNING_COOLDOWN;
	}

	/** It warned them; {@code ignored}: they stayed or came on. */
	public void warned(Player player, boolean ignored) {
		Watch w = this.watch(player);
		w.warnedAt = this.now();
		w.warningIgnored = ignored;
		this.drifter.getMemory().thoughts().add(this.now(), player.getGameProfile().getName()
			+ (ignored ? " ignored its warning" : " backed off at its warning: it backs off too"));
	}

	public SocialWatcher(DrifterEntity drifter) {
		this.drifter = drifter;
	}

	/** A player's deed that moves its trust this much is news it passes on. */
	private static final double NEWSWORTHY = 15.0;

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

		var known = this.drifter.getSocial().get(player.getUUID());
		double before = known == null ? 0.0 : known.trust;
		double result = this.drifter.getSocial().record(player.getUUID(), player.getGameProfile().getName(), event, amount);
		var r = this.drifter.getSocial().get(player.getUUID());
		double after = r == null ? 0.0 : r.trust;
		// Big enough to talk about: news it passes on (a friend's death travels as the death itself).
		if (Math.abs(after - before) >= NEWSWORTHY && event != SocialEvent.KILLED_FRIEND) {
			this.drifter.getNews().saw(NewsBook.deed(this.drifter, player, event, after - before));
		}

		com.dinglebob.drifter.api.DrifterEvents.PLAYER_DEED.invoker().onPlayerDeed(this.drifter, player, event, after);
		return result;
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
		// (A friendly fight they agreed to in chat: no wrong, no "ouch".)
		if (!isRealPlayer(player) || com.dinglebob.drifter.social.talk.Spar.on(this.drifter, player)
			|| com.dinglebob.drifter.social.talk.Spar.calming(this.drifter, player)) {
			return;
		}

		Watch w = this.watch(player);
		long now = this.now();
		if (now - w.lastHitAt > ENGAGEMENT_TICKS) {
			w.hits = 0;
			w.damage = 0.0F;
			w.attackedRecorded = false;
			w.nearlyRecorded = false;
			w.gaveIn = false;
		}

		w.hits++;
		w.damage += amount;
		w.lastHitAt = now;
		w.peacefulTicks = 0;
		w.pendingAccident = w.hits == 1;
		if (w.hits == 1) {
			ChatLines.say(this.drifter, ChatLines.Kind.OUCH, "player", player.getGameProfile().getName());
		}

		if (w.hits >= 2 && !w.attackedRecorded) {
			w.attackedRecorded = true;
			this.record(player, SocialEvent.ATTACKED, w.damage);
		}

		if (healthAfter > 0.0F && healthAfter <= NEARLY_DEAD && !w.nearlyRecorded) {
			w.nearlyRecorded = true;
			this.record(player, SocialEvent.NEARLY_KILLED, 0.0);
		}

		// Losing, and dying would cost a lot (stakes, part 2): it gives in, once.
		if (healthAfter > 0.0F && healthAfter <= GIVES_IN && !w.gaveIn && this.givesIn(player)) {
			w.gaveIn = true;
			this.giveIn(player);
		}
	}

	/** Health (half-hearts) at or under which one that's losing, and would lose a lot by dying, gives in. */
	private static final float GIVES_IN = 8.0F;
	/** How wary (stakes) it has to be to give in rather than fight on or run. */
	private static final double GIVE_IN_WARINESS = 0.5;
	/** What it hands over: its best few stacks. */
	private static final int GIVES = 3;

	/** Wary enough, not the kind to give in to anyone (a Bandit, a hunter), and not winning this. */
	private boolean givesIn(Player player) {
		if (com.dinglebob.drifter.brain.Stakes.wariness(this.drifter) < GIVE_IN_WARINESS || com.dinglebob.drifter.brain.Pursuit.relentless(this.drifter)
			|| this.drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.BRAVERY) >= 0.6) {
			return false;
		}

		var threat = com.dinglebob.drifter.brain.Threats.assess(this.drifter);
		return threat.decision() != com.dinglebob.drifter.brain.Threats.Decision.ENGAGE;
	}

	/**
	 * Gives in to a player who's beating it: faces them, says so, and throws them its best few things (its armour and weapon
	 * stay on it). Then it gets away as it would anyway. It's no gift: they're still the one who did this.
	 */
	private void giveIn(Player player) {
		var inventory = this.drifter.getInventory();
		java.util.List<Integer> slots = new java.util.ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (!inventory.getItem(i).isEmpty() && !com.dinglebob.drifter.brain.Storage.isJunk(this.drifter, inventory.getItem(i))) {
				slots.add(i);
			}
		}

		slots.sort(java.util.Comparator.comparingDouble((Integer i) -> -ItemValues.each(inventory.getItem(i)) * inventory.getItem(i).getCount()));
		Vec3 to = player.getEyePosition().subtract(this.drifter.getEyePosition());
		this.drifter.setYRot((float)(Math.atan2(-to.x, to.z) * (180.0 / Math.PI)));
		this.drifter.setYHeadRot(this.drifter.getYRot());
		this.drifter.setXRot(0.0F);
		ChatLines.speak(this.drifter, Lines.pick(this.drifter, "yield", "player", player.getGameProfile().getName()));
		int given = 0;
		for (int slot : slots) {
			if (given++ >= GIVES) {
				break;
			}

			this.drifter.tossForward(inventory.removeItemNoUpdate(slot), false);
		}

		this.drifter.getMemory().thoughts().add(this.now(), "gave in to " + player.getGameProfile().getName() + ": threw them " + Math.min(given, GIVES)
			+ " stacks of its things");
	}

	/** It died, killed by this player. */
	public void killedBy(Player player) {
		if (isRealPlayer(player) && !com.dinglebob.drifter.social.talk.Spar.on(this.drifter, player)) {
			ChatLines.say(this.drifter, ChatLines.Kind.GG_LOST, "player", player.getGameProfile().getName());
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

			// Just noticed them: it stops and looks them over (unless it did a moment ago).
			if (identified && !w.wasIdentified && now - w.lastWatchedAt > REWATCH_TICKS) {
				w.watchStartAt = now;
				w.lastWatchedAt = now;
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

			this.shareReputation(player, r, now);
			this.watchBow(player, w, now, distance);
			this.watchCrouches(player, w, now, distance);
			this.watchGear(player, r);
			this.watchForItsThings(player, distance);
			if (distance < PEACEFUL_RANGE) {
				w.peacefulTicks += TICK_EVERY;
				if (w.peacefulTicks >= PEACEFUL_TICKS) {
					w.peacefulTicks = 0;
					this.record(player, SocialEvent.PEACEFUL_TIME, 0.0);
				}
			}
		}
	}

	/**
	 * Section 14 "Forgiveness and multiplayer": players seen together share a little of each other's standing ("you're
	 * with them, so I don't trust you either", or the other way round). A fifth of the gap, once a day per pair.
	 */
	private void shareReputation(Player player, Relationship r, long now) {
		if (!com.dinglebob.drifter.DrifterConfig.shareReputation()) {
			return;
		}

		long day = now / 24000L;
		for (Player other : this.drifter.level().players()) {
			if (other == player || !isRealPlayer(other) || other.distanceTo(player) > 6.0 || !this.sees(other)) {
				continue;
			}

			Relationship theirs = this.drifter.getSocial().get(other.getUUID());
			String pair = other.getUUID().toString();
			if (theirs == null || r.sharedDay.getOrDefault(pair, -1L) == day || Math.abs(theirs.trust - r.trust) < 10.0) {
				continue;
			}

			r.sharedDay.put(pair, day);
			double before = r.trust;
			r.addTrust((theirs.trust - r.trust) * SHARE);
			this.drifter.getMemory().thoughts().add(now, String.format(java.util.Locale.ROOT, "%s is with %s: trust %.0f -> %.0f",
				player.getGameProfile().getName(), other.getGameProfile().getName(), before, r.trust));
		}
	}

	/** Share of the gap in standing that rubs off on someone seen with another player. */
	private static final double SHARE = 0.2;

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
		// Two crouches close by, facing it: "follow me" (section 14), once they walk off.
		if (w.crouches.size() == 2 && distance < 6.0 && player.getViewVector(1.0F).dot(toIt) > 0.7 && now - w.invitedAt > 20 * 6) {
			w.invitedAt = now;
			w.invitedFrom = player.position();
		}

		if (w.crouches.size() >= CROUCHES && distance < 8.0 && player.getViewVector(1.0F).dot(toIt) > 0.7 && now - w.lastCrouchSpamAt > 20 * 5) {
			w.crouches.clear();
			w.lastCrouchSpamAt = now;
			this.record(player, SocialEvent.CROUCH_SPAM, 0.0);
			// Crouch-spam is "hello" (section 14 table): it answers in kind, unless it's against them.
			Mode mode = Mode.toward(this.drifter, player);
			if (!mode.hostile() && mode != Mode.AVOIDING && mode != Mode.WARNING) {
				w.giftAt = now;
				w.thanked = false;
				w.respondTo = "the hello";
			}
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
		ChatLines.say(this.drifter, ChatLines.Kind.THANKS, "player", player.getGameProfile().getName());
		Watch w = this.watch(player);
		w.giftAt = this.now();
		w.thanked = false;
		w.respondTo = "the gift";
	}

	/**
	 * It opened one of its chests and found valuables gone since it last closed it: pinned on the player it last saw
	 * opening that chest (section 14: "detected on its next chest check").
	 */
	public void missingFromChest(BlockPos chest, int valuableStacks, List<Item> items) {
		Home home = this.drifter.getDrifterBrain().home();
		if (valuableStacks <= 0 || home == null) {
			return;
		}

		// What's gone, for spotting it on someone later (user's idea, 2026-10-16).
		long now = this.now();
		items.forEach(item -> home.missing.put(item, now));
		this.think(describe(items) + " gone from its chest");
		if (home.lastOpenedBy == null || now - home.lastOpenedAt > 24000L * Home.MISSING_DAYS) {
			return;
		}

		Player player = this.drifter.level().getPlayerByUUID(home.lastOpenedBy);
		String name = home.lastOpenedByName;
		// It watched them at this chest and came straight over to look: caught in the act.
		if (home.lastOpenedSeen && chest.equals(home.lastOpenedChest) && now - home.lastOpenedAt < RED_HANDED_TICKS) {
			home.lastOpenedSeen = false;
			this.think("caught " + name + " stealing from its chest");
			if (player != null) {
				this.record(player, SocialEvent.CAUGHT_STEALING, 0.0);
				this.watch(player).warningIgnored = true;
			} else {
				this.drifter.getSocial().record(home.lastOpenedBy, name, SocialEvent.CAUGHT_STEALING, 0.0);
			}

			ChatLines.say(this.drifter, ChatLines.Kind.THIEF, "player", name);
			return;
		}

		for (int i = 0; i < valuableStacks; i++) {
			if (player != null) {
				this.record(player, SocialEvent.TOOK_VALUABLES, 0.0);
			} else {
				this.drifter.getSocial().record(home.lastOpenedBy, name, SocialEvent.TOOK_VALUABLES, 0.0);
			}
		}
	}

	/**
	 * Someone it can see wearing or holding something that went missing from its chests: it takes them for the thief,
	 * whether they took it or not (user's idea, 2026-10-16: "stole it or not, they are gonna assume"). Close enough to
	 * make out what they have on; once per missing thing.
	 */
	private void watchForItsThings(Player player, double distance) {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || home.missing.isEmpty() || distance > GEAR_RANGE) {
			return;
		}

		home.forgetOldMissing(this.now());
		List<ItemStack> showing = new ArrayList<>();
		player.getArmorSlots().forEach(showing::add);
		showing.add(player.getMainHandItem());
		showing.add(player.getOffhandItem());
		for (ItemStack stack : showing) {
			if (!stack.isEmpty() && home.missing.remove(stack.getItem()) != null) {
				String name = player.getGameProfile().getName();
				this.think(name + " has its " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT) + ": " + name + " stole it");
				this.record(player, SocialEvent.HAD_ITS_THINGS, 0.0);
				this.watch(player).warningIgnored = true;
				ChatLines.say(this.drifter, ChatLines.Kind.THIEF, "player", name);
				return;
			}
		}
	}

	/** A chest of its it saw (or heard) someone open: it goes and checks once they step away (see the CheckChest goal). */
	@Nullable
	public BlockPos chestToCheck() {
		Home home = this.drifter.getDrifterBrain().home();
		if (home == null || home.checkChest == null) {
			return null;
		}

		if (this.now() - home.checkChestSince > CHECK_WITHIN_TICKS || !home.isChest(home.checkChest)) {
			home.checkChest = null;
			return null;
		}

		// Not while they're still standing at it (it waits for them to go: then looks).
		Player player = home.lastOpenedBy == null ? null : this.drifter.level().getPlayerByUUID(home.lastOpenedBy);
		if (player != null && player.blockPosition().closerThan(home.checkChest, 3.0) && this.now() - home.checkChestSince < 20 * 30) {
			return null;
		}

		return home.checkChest;
	}

	private static String describe(List<Item> items) {
		if (items.isEmpty()) {
			return "things";
		}

		String first = "its " + items.get(0).getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		return items.size() == 1 ? first : first + " and " + (items.size() - 1) + " more";
	}

	private void think(String text) {
		this.drifter.getMemory().thoughts().add(this.now(), text);
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
					home.lastOpenedSeen = watcher.sees(player);
					home.lastOpenedChest = pos.immutable();
					home.checkChest = pos.immutable();
					home.checkChestSince = level.getGameTime();
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

		// Section 18: a Drifter hostile to this player, close by, is "danger nearby" (no sleeping); others aren't.
		net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register((player, sleepingPos, vanillaResult) -> {
			if (!isRealPlayer(player)) {
				return InteractionResult.PASS;
			}

			AABB around = new AABB(sleepingPos).inflate(8.0, 5.0, 8.0);
			boolean hostile = player.level().getEntitiesOfClass(DrifterEntity.class, around, DrifterEntity::isAlive).stream()
				.anyMatch(drifter -> Mode.toward(drifter, player).hostile());
			return hostile ? InteractionResult.FAIL : InteractionResult.PASS;
		});

		// Its name said in chat nearby (user's choice, 2026-10-15), or a good part of it (2026-10-23): it hears it and turns to
		// look for whoever said it.
		net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			if (!isRealPlayer(sender)) {
				return;
			}

			String text = " " + message.signedContent().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", " ") + " ";
			forDriftersNear(sender.level(), sender.blockPosition(), NAME_RANGE, drifter -> {
				if (called(text, drifter.getName().getString()) && drifter.distanceTo(sender) <= NAME_RANGE) {
					Watch w = drifter.getSocial().watcher().watch(sender);
					w.calledAt = sender.level().getGameTime();
					w.calledFrom = sender.getEyePosition();
				}
			});
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!isRealPlayer(source.getEntity())) {
				return;
			}

			Player player = (Player)source.getEntity();
			forDriftersNear(victim.level(), victim.blockPosition(), 48.0, drifter -> drifter.getSocial().watcher().sawDeath(player, victim));
		});
	}

	/** The shortest part of a name that calls it ("grumpy" or "baker" for xXGrumpybakerXx). */
	private static final int NAME_PART = 4;
	/** Everyday words that are part of many names ("rock", "over"...) but mean nothing by themselves in chat. */
	private static final java.util.Set<String> EVERYDAY = java.util.Set.of("that", "this", "with", "have", "what", "just", "like", "your", "from", "they",
		"will", "here", "there", "good", "dont", "know", "want", "going", "come", "some", "them", "then", "been", "were", "when", "make", "time",
		"back", "need", "about", "really", "yeah", "okay", "lets", "where", "into", "over", "more", "much", "very", "well", "thanks", "hello", "sure",
		"maybe", "still", "also", "only", "even", "ever", "never", "other", "after", "before", "again", "today", "night", "morning", "home", "base",
		"stuff", "thing", "things", "anyone", "someone", "everyone", "would", "could", "should", "think", "look", "found", "done", "gonna", "wanna");

	/**
	 * Its name said in this chat message (lower case, a space either side of each word): the whole of it, or a part of it at
	 * least {@link #NAME_PART} letters long that isn't an everyday word (user, 2026-10-23: nobody types out
	 * "xXGrumpybakerXx"; "grumpy" or "baker" will do).
	 */
	public static boolean called(String text, String name) {
		String lower = name.toLowerCase(java.util.Locale.ROOT);
		if (lower.isEmpty()) {
			return false;
		}

		if (text.contains(" " + lower + " ")) {
			return true;
		}

		for (String word : text.trim().split("[^a-z0-9]+")) {
			if (word.length() >= NAME_PART && !EVERYDAY.contains(word) && lower.contains(word)) {
				return true;
			}
		}

		return false;
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

		// A Drifter: the news reaches the ones who saw it (friendships come with groups; a witness counts for now). Unless it
		// was the one it was after (a thief it was chasing, found in an encounter test: the owner took the thief's death
		// for a friend's): then the player stopped its attacker.
		if (victim instanceof DrifterEntity) {
			var pursuit = this.drifter.getDrifterBrain().pursuit();
			this.record(player, pursuit != null && pursuit.target == victim ? SocialEvent.KILLED_ATTACKER : SocialEvent.KILLED_FRIEND, 0.0);
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
			ChatLines.say(this.drifter, ChatLines.Kind.THANKS, "player", player.getGameProfile().getName());
		}
	}

	private static void forDriftersNear(Level level, BlockPos pos, double radius, java.util.function.Consumer<DrifterEntity> action) {
		for (DrifterEntity drifter : level.getEntitiesOfClass(DrifterEntity.class, new AABB(pos).inflate(radius), DrifterEntity::isAlive)) {
			action.accept(drifter);
		}
	}
}
