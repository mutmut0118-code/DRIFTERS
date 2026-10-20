package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Encounters (milestone 21, docs/ENCOUNTERS.md; the user's "random events with temporary drifters you will never see
 * again, think of it as like the random event system in the GTA franchise"): now and then, one at a time per player,
 * something happens round them, fitting the moment (the time of day, whether they're out on the road, how well armed
 * they are, who holds a grudge against them). Rare: about one every day or two of game time by default. Each kind can
 * be switched off; so can all of them.
 *
 * <p>Some come from the world rather than the dice: a Drifter with a grudge heavy enough puts a price on a player's head
 * (bounty hunters, days later); a wanted Drifter's death brings the courier with the reward. What's saved: each player's
 * next encounter time, Drifters they've killed, the price on their head and the bounty they hold. The encounters
 * themselves (and their Drifters) never are.
 */
public final class Encounters {
	/** Every kind, its id (settings, commands) and its name. */
	public enum Kind {
		BOUNTY_HUNTERS("bounty_hunters", "bounty hunters"),
		WANTED("wanted", "a wanted poster"),
		REWARD("reward", "a bounty paid"),
		INJURED("injured_traveller", "an injured traveller"),
		THIEF("thief", "a thief"),
		MERCHANT("merchant", "a travelling merchant"),
		AMBUSH("ambush", "an ambush"),
		WATCHER("watcher", "the watcher"),
		DUEL("duel", "a duel challenge"),
		MUGGING("mugging", "a mugging"),
		HUNGRY("hungry_traveller", "a hungry traveller"),
		FEUD("feud", "a feud"),
		PARTY("campfire_party", "a campfire party"),
		MOURNER("mourner", "a mourner"),
		REVENGE("revenge", "a revenge squad");

		public final String id;
		public final String label;

		Kind(String id, String label) {
			this.id = id;
			this.label = label;
		}

		@Nullable
		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}

			return null;
		}

		/** The kinds the dice pick (the rest come from what happened: a bounty, a kill). */
		public boolean random() {
			return this != BOUNTY_HUNTERS && this != REWARD && this != REVENGE;
		}
	}

	/** How often it looks at each player (ticks). */
	private static final int CHECK_TICKS = 20 * 10;
	/** Nothing fits now: it looks again this much later. */
	private static final long RETRY_TICKS = 20L * 60 * 2;
	/** A grudge this heavy (and a Drifter that wronged) is enough to put a price on someone's head. */
	private static final double BOUNTY_GRUDGE = 40.0;
	/** Or this many Drifters killed since the last price on their head (their friends pay). */
	private static final int BOUNTY_KILLS = 3;
	/** The hunters come this long after the price goes up (ticks; 1 to 2.5 days). */
	private static final long BOUNTY_DELAY_MIN = 24000L;
	private static final long BOUNTY_DELAY_SPREAD = 36000L;
	/** A wanted poster's bounty stands this long. */
	static final long WANTED_LASTS = 24000L * 7;
	/** A Drifter they killed that didn't have it coming: its friends come for revenge this often (a murder: always). */
	private static final double REVENGE_CHANCE = 0.25;
	/** They come this long after (ticks; a quarter of a day to a day). */
	private static final long REVENGE_DELAY_MIN = 6000L;
	private static final long REVENGE_DELAY_SPREAD = 18000L;
	/** Never finding them, they give up this long after they set out. */
	private static final long REVENGE_GIVES_UP = 24000L * 3;

	/** The encounter going on for each player. */
	private static final Map<UUID, Encounter> active = new HashMap<>();
	/** What's kept per player (saved). */
	private static final Map<UUID, PlayerState> players = new HashMap<>();
	/** Where each player was at the last few checks (for which way they're going: an ambush lies ahead). */
	private static final Map<UUID, Vec3> lastPos = new HashMap<>();
	private static final Map<UUID, Vec3> heading = new HashMap<>();
	private static boolean dirty;

	private Encounters() {
	}

	/** One player's encounter state. */
	static final class PlayerState {
		long nextAt = -1;
		int drifterKills;
		/** A price on their head: who put it up, why, and when the hunters come. */
		@Nullable
		Bounty bounty;
		/** A wanted poster they hold. */
		@Nullable
		Wanted wanted;
		/** Friends of a Drifter they killed, coming for them. */
		@Nullable
		Revenge revenge;

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("NextAt", this.nextAt);
			tag.putInt("DrifterKills", this.drifterKills);
			if (this.bounty != null) {
				tag.put("Bounty", this.bounty.save());
			}

			if (this.wanted != null) {
				tag.put("Wanted", this.wanted.save());
			}

			if (this.revenge != null) {
				tag.put("Revenge", this.revenge.save());
			}

			return tag;
		}

		static PlayerState load(CompoundTag tag) {
			PlayerState state = new PlayerState();
			state.nextAt = tag.getLong("NextAt");
			state.drifterKills = tag.getInt("DrifterKills");
			state.bounty = tag.contains("Bounty") ? Bounty.load(tag.getCompound("Bounty")) : null;
			state.wanted = tag.contains("Wanted") ? Wanted.load(tag.getCompound("Wanted")) : null;
			state.revenge = tag.contains("Revenge") ? Revenge.load(tag.getCompound("Revenge")) : null;
			return state;
		}
	}

	/**
	 * Revenge (docs/ENCOUNTERS.md 7, the user's "the revenge one too"): the friends of a Drifter a player killed, coming
	 * for them. Who it was, where it died (where they start looking if nobody's seen the player since), whether it was
	 * murder (it had given up: more of them), when they come, and when they set out.
	 */
	record Revenge(String victim, BlockPos where, boolean murder, long dueAt, long since) {
		Revenge later(long dueAt) {
			return new Revenge(this.victim, this.where, this.murder, dueAt, this.since);
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("Victim", this.victim);
			tag.putLong("Where", this.where.asLong());
			tag.putBoolean("Murder", this.murder);
			tag.putLong("DueAt", this.dueAt);
			tag.putLong("Since", this.since);
			return tag;
		}

		static Revenge load(CompoundTag tag) {
			return new Revenge(tag.getString("Victim"), BlockPos.of(tag.getLong("Where")), tag.getBoolean("Murder"), tag.getLong("DueAt"), tag.getLong("Since"));
		}
	}

	/** A price on a player's head (milestone 21, the user's "bounty hunters are looking for me"). */
	record Bounty(String issuer, String reason, long dueAt) {
		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("Issuer", this.issuer);
			tag.putString("Reason", this.reason);
			tag.putLong("DueAt", this.dueAt);
			return tag;
		}

		static Bounty load(CompoundTag tag) {
			return new Bounty(tag.getString("Issuer"), tag.getString("Reason"), tag.getLong("DueAt"));
		}
	}

	/** A bounty on a Drifter a player took (the user's "a map to a drifters house ... the reward is super valuable"). */
	static final class Wanted {
		final UUID target;
		final String name;
		final String crime;
		final String rewardItem;
		final int rewardCount;
		final long expires;
		boolean claimed;

		Wanted(UUID target, String name, String crime, String rewardItem, int rewardCount, long expires) {
			this.target = target;
			this.name = name;
			this.crime = crime;
			this.rewardItem = rewardItem;
			this.rewardCount = rewardCount;
			this.expires = expires;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Target", this.target);
			tag.putString("Name", this.name);
			tag.putString("Crime", this.crime);
			tag.putString("RewardItem", this.rewardItem);
			tag.putInt("RewardCount", this.rewardCount);
			tag.putLong("Expires", this.expires);
			tag.putBoolean("Claimed", this.claimed);
			return tag;
		}

		static Wanted load(CompoundTag tag) {
			Wanted wanted = new Wanted(tag.getUUID("Target"), tag.getString("Name"), tag.getString("Crime"), tag.getString("RewardItem"),
				tag.getInt("RewardCount"), tag.getLong("Expires"));
			wanted.claimed = tag.getBoolean("Claimed");
			return wanted;
		}
	}

	static PlayerState state(UUID player) {
		return players.computeIfAbsent(player, id -> new PlayerState());
	}

	static void changed() {
		dirty = true;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Encounters::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Encounter encounter : active.values()) {
				encounter.members().forEach(DrifterEntity::discard);
			}

			active.clear();
			save(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			players.clear();
			lastPos.clear();
			heading.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				active.values().removeIf(encounter -> {
					try {
						return !encounter.tick();
					} catch (RuntimeException e) {
						Drifter.LOGGER.error("[Drifter] encounter {} broke: ending it", encounter.kind.label, e);
						encounter.members().forEach(DrifterEntity::discard);
						return true;
					}
				});
			}

			if (server.getTickCount() % CHECK_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					check(player);
				}

				if (dirty) {
					save(server);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer dead) {
				Encounter encounter = active.get(dead.getUUID());
				if (encounter != null && !encounter.isOver()) {
					encounter.playerDied();
				}

				return;
			}

			if (entity instanceof DrifterEntity drifter && source.getEntity() instanceof ServerPlayer killer) {
				killed(drifter, killer);
			}
		});
		// Each blow as it lands (a duel's "I give up" can't wait for the next second).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!entity.isAlive()) {
				return;
			}

			if (entity instanceof DrifterEntity drifter && drifter.encounter() != null) {
				drifter.encounter().hurt(drifter, source);
			} else if (entity instanceof ServerPlayer player) {
				Encounter encounter = active.get(player.getUUID());
				if (encounter != null && !encounter.isOver()) {
					encounter.playerHurt(player, source);
				}
			}
		});
	}

	/**
	 * A player killed a Drifter (the user, 2026-10-20: "what will be the punishment for killing in a duel?"). One of the
	 * world's counts towards a price on their head, as does an encounter's that didn't have it coming (a traveller, a
	 * duellist in the fight it asked for); one that had given up is murder: a price on their head at once. Now and then
	 * (a murder: always) its friends come for revenge. Self-defence (one that was after them) and a wanted one they took
	 * the bounty on bring no revenge.
	 */
	private static void killed(DrifterEntity drifter, ServerPlayer killer) {
		Encounter encounter = drifter.encounter();
		Encounter.Killing killing = encounter != null ? encounter.killing(drifter, killer) : drifter.isTemporary() ? Encounter.Killing.NONE : Encounter.Killing.KILL;
		if (killing == Encounter.Killing.NONE) {
			return;
		}

		PlayerState state = state(killer.getUUID());
		String name = drifter.getName().getString();
		long now = killer.level().getGameTime();
		var random = killer.getRandom();
		boolean murder = killing == Encounter.Killing.MURDER;
		if (murder && state.bounty == null && enabled(Kind.BOUNTY_HUNTERS)) {
			state.bounty = new Bounty("the friends of " + name, "killing " + name + " after they gave up", now + BOUNTY_DELAY_MIN + random.nextInt((int)BOUNTY_DELAY_SPREAD));
			state.drifterKills = 0;
			Drifter.LOGGER.info("[Drifter] {} killed {} after it gave up: a price on their head", killer.getGameProfile().getName(), name);
		} else {
			state.drifterKills++;
		}

		// The wanted one, brought down by the one who took the bounty: the courier comes with the reward.
		boolean wanted = state.wanted != null && state.wanted.target.equals(drifter.getUUID());
		if (wanted && !state.wanted.claimed) {
			state.wanted.claimed = true;
			Drifter.LOGGER.info("[Drifter] {} brought down {}, wanted for {}: the reward is on its way", killer.getGameProfile().getName(),
				state.wanted.name, state.wanted.crime);
		}

		var pursuit = drifter.getDrifterBrain().pursuit();
		boolean wasAfterThem = pursuit != null && pursuit.target == killer
			|| drifter.getLastHurtMob() == killer && drifter.tickCount - drifter.getLastHurtMobTimestamp() < 20 * 10;
		boolean revenge = murder || !wanted && !wasAfterThem && random.nextDouble() < REVENGE_CHANCE;
		// (One squad at a time; a murder takes the place of a lesser grudge.)
		if (revenge && enabled(Kind.REVENGE) && (state.revenge == null || murder && !state.revenge.murder())) {
			long delay = REVENGE_DELAY_MIN + random.nextInt((int)REVENGE_DELAY_SPREAD);
			state.revenge = new Revenge(name, drifter.blockPosition(), murder, now + delay, now);
			Drifter.LOGGER.info("[Drifter] {}'s friends will come for {} in about {} min", name, killer.getGameProfile().getName(), delay / 1200);
		}

		changed();
	}

	// ---------------------------------------------------------------------------------------------
	// The director
	// ---------------------------------------------------------------------------------------------

	/** Each player now and then: one due? (A price on their head, a reward to pay, or the dice.) */
	private static void check(ServerPlayer player) {
		UUID id = player.getUUID();
		Vec3 here = player.position();
		Vec3 before = lastPos.put(id, here);
		if (before != null) {
			heading.put(id, here.subtract(before).multiply(1.0, 0.0, 1.0));
		}

		if (!DrifterConfig.encounters() || active.containsKey(id) || player.isSpectator() || player.isCreative() || !player.isAlive()
			|| player.level().dimension() != Level.OVERWORLD) {
			return;
		}

		long now = player.level().getGameTime();
		PlayerState state = state(id);
		if (state.nextAt < 0) {
			// Not the moment they arrive: the first a while in.
			state.nextAt = now + (long)(DrifterConfig.encounterDays() * 24000L * (0.5 + 0.5 * player.getRandom().nextDouble()));
			changed();
		}

		postBounty(player, state, now);
		if (state.wanted != null && !state.wanted.claimed && now > state.wanted.expires) {
			state.wanted = null;
			changed();
		}

		if (!outdoors(player)) {
			return;
		}

		// What happened comes first: the reward for a wanted one they brought down, then hunters for the price on their head.
		if (state.wanted != null && state.wanted.claimed && enabled(Kind.WANTED)) {
			start(player, Kind.REWARD);
			return;
		}

		if (state.bounty != null && now >= state.bounty.dueAt() && enabled(Kind.BOUNTY_HUNTERS)) {
			if (start(player, Kind.BOUNTY_HUNTERS) == null) {
				// Nobody knows where they are (no Drifter has seen them): the hunters wait for word.
				state.bounty = new Bounty(state.bounty.issuer(), state.bounty.reason(), now + RETRY_TICKS * 5);
				changed();
			}

			return;
		}

		if (state.revenge != null && now >= state.revenge.dueAt() && enabled(Kind.REVENGE)) {
			if (now - state.revenge.since() > REVENGE_GIVES_UP) {
				Drifter.LOGGER.info("[Drifter] {}'s friends gave up looking for {}", state.revenge.victim(), player.getGameProfile().getName());
				state.revenge = null;
			} else if (start(player, Kind.REVENGE) == null) {
				// Nobody's seen them near where they're looking: they keep looking.
				state.revenge = state.revenge.later(now + RETRY_TICKS * 5);
			}

			changed();
			return;
		}

		if (now < state.nextAt) {
			return;
		}

		List<Kind> kinds = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double total = 0.0;
		for (Kind kind : Kind.values()) {
			double weight = kind.random() && enabled(kind) ? weight(player, kind, state) : 0.0;
			if (weight > 0.0) {
				kinds.add(kind);
				weights.add(weight);
				total += weight;
			}
		}

		while (!kinds.isEmpty()) {
			double roll = player.getRandom().nextDouble() * total;
			int index = kinds.size() - 1;
			for (int i = 0; i < kinds.size(); i++) {
				roll -= weights.get(i);
				if (roll <= 0.0) {
					index = i;
					break;
				}
			}

			if (start(player, kinds.get(index)) != null) {
				state.nextAt = now + (long)(DrifterConfig.encounterDays() * 24000L * (0.5 + player.getRandom().nextDouble()));
				changed();
				return;
			}

			total -= weights.get(index);
			kinds.remove(index);
			weights.remove(index);
		}

		state.nextAt = now + RETRY_TICKS;
		changed();
	}

	/** How likely each kind is for them now (0: doesn't fit). */
	private static double weight(ServerPlayer player, Kind kind, PlayerState state) {
		long time = player.level().getDayTime() % 24000L;
		boolean day = time < 12000L;
		boolean night = time >= 13000L && time < 23000L;
		return switch (kind) {
			// Out on the road, away from home and spawn, on the move.
			case AMBUSH -> awayFromHome(player) && moving(player) && !night ? 1.0 : 0.0;
			case THIEF, MERCHANT -> day ? 1.0 : 0.0;
			// Mostly at night (it's the monsters it's running from).
			case INJURED -> night ? 1.6 : 0.6;
			case WANTED -> state.wanted == null && WantedPoster.target(player) != null ? 0.8 : 0.0;
			// A figure at the edge of sight, at night: rare.
			case WATCHER -> night ? 0.4 : 0.0;
			// Someone armed: a challenge.
			case DUEL -> day && EncounterSpawner.gearTier(player) + (armed(player) ? 1 : 0) >= 1 ? 0.6 : 0.0;
			case MUGGING -> awayFromHome(player) && !night ? 0.7 : 0.0;
			case HUNGRY, FEUD -> day ? 0.7 : 0.0;
			// An evening party under a dry sky.
			case PARTY -> time >= 13000L && time < 18000L && !player.level().isRaining() ? 0.8 : 0.0;
			case MOURNER -> time < 13000L ? 0.5 : 0.0;
			default -> 0.0;
		};
	}

	/** Starts one of these for them now, if it can (debug commands too); the encounter, or null. */
	@Nullable
	public static Encounter start(ServerPlayer player, Kind kind) {
		Encounter old = active.get(player.getUUID());
		if (old != null) {
			if (!old.isOver()) {
				return null;
			}

			// (The last one's leavers are still walking off: they go now.)
			old.members().forEach(DrifterEntity::discard);
			active.remove(player.getUUID());
		}

		Encounter encounter;
		try {
			encounter = make(player, kind);
		} catch (RuntimeException e) {
			// (A kind that breaks never takes the server down with it.)
			Drifter.LOGGER.error("[Drifter] couldn't start {} for {}", kind.label, player.getGameProfile().getName(), e);
			return null;
		}

		if (encounter == null || encounter.members().isEmpty()) {
			return null;
		}

		active.put(player.getUUID(), encounter);
		Drifter.LOGGER.info("[Drifter] encounter: {} for {} ({} Drifters)", kind.label, player.getGameProfile().getName(), encounter.members().size());
		return encounter;
	}

	@Nullable
	private static Encounter make(ServerPlayer player, Kind kind) {
		return switch (kind) {
			case BOUNTY_HUNTERS -> BountyHunters.start(player, state(player.getUUID()));
			case WANTED -> WantedPoster.start(player);
			case REWARD -> WantedReward.start(player, state(player.getUUID()));
			case INJURED -> InjuredTraveller.start(player);
			case THIEF -> Thief.start(player);
			case MERCHANT -> Merchant.start(player);
			// (Started by command standing still: ahead is the way they're looking.)
			case AMBUSH -> Ambush.start(player, heading.getOrDefault(player.getUUID(), Vec3.ZERO).lengthSqr() >= 1.0 ? heading.get(player.getUUID())
				: player.getLookAngle().multiply(1.0, 0.0, 1.0).scale(20.0));
			case WATCHER -> Watcher.start(player);
			case DUEL -> Duel.start(player);
			case MUGGING -> Mugging.start(player);
			case HUNGRY -> HungryTraveller.start(player);
			case FEUD -> Feud.start(player);
			case PARTY -> CampfireParty.start(player);
			case MOURNER -> Mourner.start(player);
			case REVENGE -> RevengeSquad.start(player, state(player.getUUID()));
		};
	}

	public static boolean enabled(Kind kind) {
		return !DrifterConfig.encounterOff(kind == Kind.REWARD ? Kind.WANTED.id : kind.id);
	}

	/** Holding a weapon (a sword, an axe, anything that hits harder than a fist). */
	private static boolean armed(ServerPlayer player) {
		return player.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS) || player.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES);
	}

	/** Under the open sky or close to it (the encounters happen on the surface). */
	private static boolean outdoors(ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		int top = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
		return feet.getY() >= top - 4;
	}

	/** Well away from their bed and the world spawn. */
	private static boolean awayFromHome(ServerPlayer player) {
		BlockPos bed = player.getRespawnPosition();
		BlockPos spawn = player.serverLevel().getSharedSpawnPos();
		return (bed == null || !bed.closerThan(player.blockPosition(), 100)) && !spawn.closerThan(player.blockPosition(), 100);
	}

	/** Going somewhere: 20 blocks or more since the last check (10 s). */
	private static boolean moving(ServerPlayer player) {
		Vec3 way = heading.get(player.getUUID());
		return way != null && way.length() >= 20.0;
	}

	/**
	 * Now and then, a Drifter with a heavy enough grudge (or the friends of the Drifters they've killed) puts a price on
	 * their head; the hunters come a day or two later. Half a chance each day it holds.
	 */
	private static void postBounty(ServerPlayer player, PlayerState state, long now) {
		if (state.bounty != null || !enabled(Kind.BOUNTY_HUNTERS) || now % 24000L >= CHECK_TICKS) {
			return;
		}

		String issuer = null;
		String reason = null;
		double heaviest = BOUNTY_GRUDGE;
		for (ServerLevel level : player.server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				var r = drifter.getSocial().get(player.getUUID());
				if (drifter.isTemporary() || r == null || !r.wronged || r.trust >= com.dinglebob.drifter.social.Mode.HOSTILE_TRUST || r.grudgeWeight() < heaviest) {
					continue;
				}

				heaviest = r.grudgeWeight();
				issuer = drifter.getName().getString();
				reason = "what you did to " + issuer;
			}
		}

		if (issuer == null && state.drifterKills >= BOUNTY_KILLS) {
			issuer = "the friends of the Drifters you killed";
			reason = state.drifterKills + " Drifters killed";
		}

		if (issuer == null || player.getRandom().nextBoolean()) {
			return;
		}

		state.bounty = new Bounty(issuer, reason, now + BOUNTY_DELAY_MIN + player.getRandom().nextInt((int)BOUNTY_DELAY_SPREAD));
		state.drifterKills = 0;
		changed();
		Drifter.LOGGER.info("[Drifter] {} put a price on {}'s head ({})", issuer, player.getGameProfile().getName(), reason);
	}

	/** A price on their head now (debug: `/drifter encounter bounty`). */
	public static void postBountyNow(ServerPlayer player) {
		state(player.getUUID()).bounty = new Bounty("someone you crossed", "a debug command", player.level().getGameTime());
		changed();
	}

	/** The price is gone (paid, or the hunters gave up or died). */
	static void clearBounty(UUID player) {
		state(player).bounty = null;
		changed();
	}

	// ---------------------------------------------------------------------------------------------
	// Status and saving
	// ---------------------------------------------------------------------------------------------

	@Nullable
	public static Encounter active(UUID player) {
		return active.get(player);
	}

	public static void stop(UUID player) {
		Encounter encounter = active.get(player);
		if (encounter != null) {
			encounter.end("stopped by a command");
		}
	}

	/** Each player's state, a line each (for the command). */
	public static List<String> report(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		long now = server.overworld().getGameTime();
		lines.add("Encounters " + (DrifterConfig.encounters() ? "on" : "off") + ", about one every " + String.format(Locale.ROOT, "%.1f", DrifterConfig.encounterDays())
			+ " days; " + active.size() + " going on");
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerState state = state(player.getUUID());
			Encounter encounter = active.get(player.getUUID());
			StringBuilder line = new StringBuilder(player.getGameProfile().getName() + ": ");
			line.append(encounter != null ? encounter.status() : state.nextAt < 0 ? "none yet" : "next in about " + Math.max(0, (state.nextAt - now) / 1200) + " min");
			if (state.bounty != null) {
				line.append("; a price on their head from ").append(state.bounty.issuer()).append(" (").append(state.bounty.reason()).append("), hunters in ")
					.append(Math.max(0, (state.bounty.dueAt() - now) / 1200)).append(" min");
			}

			if (state.wanted != null) {
				line.append("; holds the bounty on ").append(state.wanted.name).append(state.wanted.claimed ? " (claimed)" : "");
			}

			if (state.revenge != null) {
				line.append("; ").append(state.revenge.victim()).append("'s friends coming for revenge").append(state.revenge.murder() ? " (murder)" : "")
					.append(" in ").append(Math.max(0, (state.revenge.dueAt() - now) / 1200)).append(" min");
			}

			line.append("; ").append(state.drifterKills).append(" Drifters killed since the last price on their head");
			lines.add(line.toString());
		}

		return lines;
	}

	/** Next encounter now (debug). */
	public static void dueNow(ServerPlayer player) {
		state(player.getUUID()).nextAt = player.level().getGameTime();
		changed();
	}

	private static final class State extends SavedData {
		CompoundTag players = new CompoundTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.players = tag.getCompound("Players");
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("Version", 1);
			tag.put("Players", this.players);
			return tag;
		}
	}

	private static State savedState(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_encounters");
	}

	private static void load(MinecraftServer server) {
		players.clear();
		CompoundTag tag = savedState(server).players;
		for (String key : tag.getAllKeys()) {
			try {
				players.put(UUID.fromString(key), PlayerState.load(tag.getCompound(key)));
			} catch (IllegalArgumentException e) {
				Drifter.LOGGER.warn("[Drifter] encounters: skipped a bad entry {}", key);
			}
		}
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		players.forEach((id, state) -> tag.put(id.toString(), state.save()));
		State state = savedState(server);
		state.players = tag;
		state.setDirty();
		dirty = false;
	}
}
