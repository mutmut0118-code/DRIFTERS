package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterRecord;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * A made town's people live on while nobody's near (A10 part 2; the user, 2026-11-11: coming back to a town not seen for
 * a hundred days, some faces are gone, some are new, the leader may have changed). Each game day a town no player is
 * near has a day on paper, from what's saved: those it lost in the wars the summary fought ({@link WorldPlan.Place#paperLost},
 * A10 part 1) die, its fighters first; very rarely its leader is killed; now and then someone leaves; an empty house
 * draws a newcomer. A death is a real one, by the world's own rule ({@link DrifterApi#dieAway}: the user, "account for
 * all death types (respawn, lives, permanent)"). What can be done on what's saved is done at once (who's of the town,
 * whose house is whose, who leads, the chronicle); what needs the town itself (a newcomer made, a leaver's home let go,
 * a grave dug) waits in its ledger till its ground is loaded ({@link #arrive}).
 *
 * <p>In front of a player a town's own life decides, as before ({@link TownLife}); "away" is the summary's
 * ({@link WorldPlan#WATCHED}).
 */
final class TownAway {
	/** Of those a town loses in a battle on paper, this share are its fighters while it has any (the rest: anyone but its leader). */
	private static final double FIGHTER_SHARE = 0.85;
	/** A day's chance its leader is killed (an assassination: the user, "very rare"); times this in a revolt, or at war. */
	private static final double ASSASSIN_CHANCE = 1.0 / 2500.0;
	private static final double ASSASSIN_REVOLT = 6.0;
	private static final double ASSASSIN_WAR = 2.0;
	/** A day's chance one of its people leaves; times this for each who's unhappy with its leader. */
	private static final double LEAVE_CHANCE = 1.0 / 150.0;
	private static final double LEAVE_UNHAPPY = 2.0;
	/** A day's chance a newcomer comes to a house standing empty (about one in fifteen days); content, more; unhappy, none. */
	private static final double ARRIVE_CHANCE = 1.0 / 15.0;
	private static final double ARRIVE_CONTENT = 1.5;
	private static final double CONTENT = 60.0;
	private static final double UNHAPPY = 40.0;
	/** A town keeps at least this many of its people from leaving (as {@link TownLife}). */
	private static final int FEWEST = 4;
	/** Days on paper made up in one check, at most (a long time skipped: the summary catches up as fast). */
	private static final int DAYS_A_CHECK = 15;
	/** At most this many die in one day on paper (the rest tomorrow). */
	private static final int DEATHS_A_DAY = 3;
	/** What became of whom is kept this long (for its people to tell of, and a friend's journal). */
	static final int FATES_KEPT = 120;

	/** A newcomer on paper: named, a house theirs, not made yet. */
	record Newcomer(String name, BlockPos house, long day, String line) {
	}

	/** What became of one of its people: "died" (and came back), "gone" (for good), "left", "came". */
	record Fate(UUID id, String name, String kind, long day, String cause, long time) {
		/** Now. */
		Fate(UUID id, String name, String kind, long day, String cause) {
			this(id, name, kind, day, cause, clock());
		}
	}

	/** The world's clock now (ticks), or 0 with no world up. */
	private static long clock() {
		MinecraftServer server = DrifterCivilizations.server();
		return server == null ? 0L : server.overworld().getGameTime();
	}

	/** Something a player in the town at the time would have seen for themselves isn't told them: it happened this long after they were last seen there, at least. */
	private static final long UNSEEN_TICKS = 250L;

	/** What a town keeps of its days on paper. */
	static final class Ledger {
		/** The last day it had (on paper or in front of someone); -1: none yet. */
		long day = -1;
		/** How many of the summary's losses have been made deaths; -1: not looked at yet (a world from before). */
		int lostApplied = -1;
		final List<Newcomer> newcomers = new ArrayList<>();
		/** Those who left on paper, their home and work still to be let go when they're loaded. */
		final List<UUID> left = new ArrayList<>();
		final List<Fate> fates = new ArrayList<>();
		/** One who can't stand at the next vote (a leader killed and back). */
		@Nullable UUID barred;
		/** Lines of work left by those gone, by their house (the newcomer who takes the house takes it up). */
		final java.util.Map<BlockPos, String> vacant = new java.util.HashMap<>();
		/** Whose a house standing empty was (the chronicle: "into X's old house"). */
		final java.util.Map<BlockPos, String> wasOf = new java.util.HashMap<>();
		/** Emeralds its till held when it was last loaded, less what's gone out of it on paper since, plus what's come in. */
		int tillSeen;
		/** What's gone into (over 0) or out of (under 0) its till on paper, still to do to the chest itself. */
		int tillDelta;
		/** Food its stores are up (over 0) or down (under 0) by its days on paper, still to do to its chests. */
		double food;
		/** When each player was last in the town (the world's clock; what became of its people since: told them then). */
		final java.util.Map<UUID, Long> seenBy = new java.util.HashMap<>();

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("Version", 1);
			tag.putLong("Day", this.day);
			tag.putInt("LostApplied", this.lostApplied);
			tag.putInt("TillSeen", this.tillSeen);
			tag.putInt("TillDelta", this.tillDelta);
			tag.putDouble("Food", this.food);
			ListTag coming = new ListTag();
			for (Newcomer one : this.newcomers) {
				CompoundTag t = new CompoundTag();
				t.putString("Name", one.name);
				t.put("House", NbtUtils.writeBlockPos(one.house));
				t.putLong("Day", one.day);
				t.putString("Line", one.line);
				coming.add(t);
			}

			tag.put("Newcomers", coming);
			ListTag gone = new ListTag();
			this.left.forEach(id -> gone.add(NbtUtils.createUUID(id)));
			tag.put("Left", gone);
			ListTag told = new ListTag();
			for (Fate fate : this.fates) {
				CompoundTag t = new CompoundTag();
				t.putUUID("Id", fate.id);
				t.putString("Name", fate.name);
				t.putString("Kind", fate.kind);
				t.putLong("Day", fate.day);
				t.putString("Cause", fate.cause);
				t.putLong("Time", fate.time);
				told.add(t);
			}

			tag.put("Fates", told);
			if (this.barred != null) {
				tag.putUUID("Barred", this.barred);
			}

			ListTag work = new ListTag();
			this.vacant.forEach((house, line) -> {
				CompoundTag t = new CompoundTag();
				t.put("House", NbtUtils.writeBlockPos(house));
				t.putString("Line", line);
				work.add(t);
			});
			tag.put("Vacant", work);
			ListTag whose = new ListTag();
			this.wasOf.forEach((house, name) -> {
				CompoundTag t = new CompoundTag();
				t.put("House", NbtUtils.writeBlockPos(house));
				t.putString("Name", name);
				whose.add(t);
			});
			tag.put("WasOf", whose);
			CompoundTag seen = new CompoundTag();
			this.seenBy.forEach((player, day) -> seen.putLong(player.toString(), day));
			tag.put("SeenBy", seen);
			return tag;
		}

		void load(CompoundTag tag) {
			this.day = tag.contains("Day") ? tag.getLong("Day") : -1;
			this.lostApplied = tag.contains("LostApplied") ? tag.getInt("LostApplied") : -1;
			this.tillSeen = tag.getInt("TillSeen");
			this.tillDelta = tag.getInt("TillDelta");
			this.food = tag.getDouble("Food");
			for (Tag t : tag.getList("Newcomers", Tag.TAG_COMPOUND)) {
				CompoundTag one = (CompoundTag)t;
				NbtUtils.readBlockPos(one, "House").ifPresent(house -> this.newcomers.add(new Newcomer(one.getString("Name"), house, one.getLong("Day"),
					one.getString("Line"))));
			}

			for (Tag t : tag.getList("Left", Tag.TAG_INT_ARRAY)) {
				this.left.add(NbtUtils.loadUUID(t));
			}

			for (Tag t : tag.getList("Fates", Tag.TAG_COMPOUND)) {
				CompoundTag one = (CompoundTag)t;
				if (one.hasUUID("Id")) {
					this.fates.add(new Fate(one.getUUID("Id"), one.getString("Name"), one.getString("Kind"), one.getLong("Day"), one.getString("Cause"),
						one.getLong("Time")));
				}
			}

			this.barred = tag.hasUUID("Barred") ? tag.getUUID("Barred") : null;
			for (Tag t : tag.getList("Vacant", Tag.TAG_COMPOUND)) {
				CompoundTag one = (CompoundTag)t;
				NbtUtils.readBlockPos(one, "House").ifPresent(house -> this.vacant.put(house, one.getString("Line")));
			}

			for (Tag t : tag.getList("WasOf", Tag.TAG_COMPOUND)) {
				CompoundTag one = (CompoundTag)t;
				NbtUtils.readBlockPos(one, "House").ifPresent(house -> this.wasOf.put(house, one.getString("Name")));
			}

			CompoundTag seen = tag.getCompound("SeenBy");
			for (String key : seen.getAllKeys()) {
				try {
					this.seenBy.put(UUID.fromString(key), seen.getLong(key));
				} catch (IllegalArgumentException e) {
					// (Not a player's id: left out.)
				}
			}
		}
	}

	private TownAway() {
	}

	static void register() {
		// A town's people: their deaths while away are the town's to decide, not catch-up's.
		DrifterApi.awayDeathsDecidedBy(drifter -> {
			DrifterGroups.Group group = DrifterApi.group(drifter.getUUID());
			return group != null && Towns.all().stream().anyMatch(town -> town.spawned && group.id.equals(town.group));
		});
		// One of a town's people gone for good in front of someone: a grave for it too, and its people tell of it.
		com.dinglebob.drifter.api.DrifterEvents.DIED.register((drifter, source, comesBack) -> {
			MinecraftServer server = drifter.getServer();
			if (comesBack || server == null) {
				return;
			}

			Town town = Diplomacy.townOf(drifter.getUUID());
			if (town == null && drifter.groupAtDeath() != null) {
				town = Diplomacy.townOfGroup(drifter.groupAtDeath());
			}

			if (town == null || !town.spawned) {
				return;
			}

			long day = Chronicle.day(server);
			String name = drifter.getName().getString();
			String cause = cause(source, drifter);
			// (Killed by one of a power its own is at war with: one of that war's fallen. Its leader: told as that.)
			String told = cause;
			Town theirs = source.getEntity() instanceof DrifterEntity killer ? Diplomacy.townOf(killer.getUUID()) : null;
			if (theirs != null && Annals.atWar(WorldPlan.powerOf(town), WorldPlan.powerOf(theirs))) {
				told = "fighting " + WorldPlan.powerName(WorldPlan.powerOf(theirs));
			}

			Annals.death(server, town, drifter.getUUID(), name, told, drifter.getUUID().equals(town.leader), true);
			vacate(town, drifter.getUUID());
			TownGraves.add(town, name, day, cause);
			town.away.fates.add(new Fate(drifter.getUUID(), name, "gone", day, cause));
			Towns.save(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> telling.clear());
	}

	/** What one died of, in a few words for its stone ("killed by a zombie", "fell"). */
	private static String cause(net.minecraft.world.damagesource.DamageSource source, DrifterEntity drifter) {
		var by = source.getEntity();
		if (by != null) {
			return "killed by " + (by instanceof net.minecraft.world.entity.player.Player || by instanceof DrifterEntity ? by.getName().getString()
				: "a " + by.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT));
		}

		String id = source.getMsgId();
		return switch (id) {
			case "fall" -> "a fall";
			case "drown" -> "drowned";
			case "starve" -> "starved";
			case "lava", "inFire", "onFire", "hotFloor" -> "burned";
			case "explosion", "explosion.player" -> "an explosion";
			default -> "an accident";
		};
	}

	/** What's still to tell each player in a town now (not saved: a line or two as they walk in). */
	private static final java.util.Map<String, java.util.ArrayDeque<Fate>> telling = new java.util.HashMap<>();
	/** At most this many things told on walking in (the latest; the rest is in the chronicle). */
	private static final int TOLD = 3;

	/**
	 * A player in the town: what became of its people since they were last there is theirs to know now (their journal, a
	 * friend gone), and one of its people near says it, a thing at a time (the user: "the town tells you").
	 */
	private static boolean tell(MinecraftServer server, Town town, long today) {
		boolean changed = false;
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.level().dimension().location().toString().equals(town.dimension)
				|| Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) > town.land()) {
				continue;
			}

			// (What the town knows of the world is theirs to know now too: their Chronicle.)
			Annals.visit(server, player, town);
			String key = town.id + "/" + player.getUUID();
			Long last = town.away.seenBy.get(player.getUUID());
			long now = server.overworld().getGameTime();
			town.away.seenBy.put(player.getUUID(), now);
			// (Saved once a day or so: a restart forgets a few minutes of having been there at worst.)
			changed |= last == null || now - last > 24000L;

			if (last != null) {
				java.util.ArrayDeque<Fate> queue = telling.computeIfAbsent(key, k -> new java.util.ArrayDeque<>());
				for (Fate fate : town.away.fates) {
					if (fate.time - last > UNSEEN_TICKS) {
						if (!queue.contains(fate)) {
							queue.add(fate);
						}

						if (fate.kind.equals("gone") || fate.kind.equals("left")) {
							Friends.learned(player.getUUID(), fate.id, fate.kind.equals("left") ? "left " + town.name + " (" + fate.cause + ")" : "died: " + fate.cause);
						}
					}
				}

				// (The latest few; a friend's first.)
				while (queue.size() > TOLD) {
					queue.removeFirst();
				}
			}

			java.util.ArrayDeque<Fate> queue = telling.get(key);
			if (queue == null || queue.isEmpty() || group == null) {
				continue;
			}

			DrifterEntity speaker = null;
			for (UUID id : group.members) {
				DrifterEntity one = TownFounding.loaded(server, id);
				if (one != null && one.isAlive() && one.distanceToSqr(player) < 24 * 24 && one.getTarget() == null
					&& (speaker == null || one.distanceToSqr(player) < speaker.distanceToSqr(player))) {
					speaker = one;
				}
			}

			if (speaker == null) {
				continue;
			}

			Fate fate = queue.removeFirst();
			if (fate.id.equals(speaker.getUUID()) && !fate.kind.equals("died")) {
				// (Its own coming or going: not for it to tell.)
				continue;
			}

			if (fate.id.equals(speaker.getUUID())) {
				// (Its own death: it tells of it itself.)
				com.dinglebob.drifter.social.ChatLines.speak(speaker, CivLines.pick(speaker, "town.lost.me", "i died out there while you were gone. " + fate.cause,
					"player", player.getGameProfile().getName(), "cause", fate.cause));
				continue;
			}

			boolean friend = Friends.known(player.getUUID(), fate.id);
			String name = player.getGameProfile().getName();
			String line = switch (fate.kind) {
				case "gone" -> friend
					? CivLines.pick(speaker, "town.lost.friend", name + ".. " + fate.name + " is gone. " + fate.cause, "player", name, "who", fate.name, "cause", fate.cause)
					: CivLines.pick(speaker, "town.lost", "we lost " + fate.name + ". " + fate.cause, "player", name, "who", fate.name, "cause", fate.cause);
				case "died" -> CivLines.pick(speaker, "town.lost.back", fate.name + " died out there, " + fate.cause + ". back now though", "player", name, "who",
					fate.name, "cause", fate.cause);
				case "left" -> CivLines.pick(speaker, "town.left", fate.name + " left town for good", "player", name, "who", fate.name);
				default -> CivLines.pick(speaker, "town.newcomer", fate.name + " moved in while you were gone", "player", name, "who", fate.name);
			};
			com.dinglebob.drifter.social.ChatLines.speak(speaker, line);
			if (fate.kind.equals("gone") || fate.kind.equals("died")) {
				Annals.quoted(player, fate.id, speaker, line);
			}
		}

		return changed;
	}

	/** Is a player near enough that this town's own life decides (not a spectator; as the summary's {@link WorldPlan#WATCHED})? */
	static boolean watched(MinecraftServer server, Town town) {
		double reach = town.land() + WorldPlan.WATCHED;
		for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && player.level().dimension().location().toString().equals(town.dimension)
				&& Math.hypot(player.getX() - town.center.getX(), player.getZ() - town.center.getZ()) < reach) {
				return true;
			}
		}

		return false;
	}

	/** Each check: its days on paper made up (nobody near), and what they left to do done where its ground is loaded. */
	static void check(MinecraftServer server, Town town) {
		if (!town.spawned || town.group == null) {
			return;
		}

		Ledger ledger = town.away;
		long today = Chronicle.day(server);
		boolean changed = false;
		if (ledger.day < 0 || watched(server, town)) {
			// (In front of someone: its own life decides; the summary's losses there are none, a watched town is never fought over.)
			if (ledger.day != today) {
				ledger.day = today;
				changed = true;
			}

			changed |= settle(town);
		} else {
			Chronicle.unseen = true;
			try {
				for (int n = 0; n < DAYS_A_CHECK && ledger.day < today; n++) {
					ledger.day++;
					changed = true;
					day(server, town, ledger.day);
				}
			} finally {
				Chronicle.unseen = false;
			}
		}

		changed |= arrive(server, town);
		changed |= tell(server, town, today);
		if (changed) {
			Towns.save(server);
		}
	}

	/**
	 * Its till on paper (its chest not loaded): emeralds in (count over 0), or out (under 0) of what it was last seen to
	 * hold and no more. Real emeralds: done to the chest itself when it's loaded ({@link Diplomacy#till}). How many moved.
	 */
	static int paperTill(Town town, int count) {
		Ledger ledger = town.away;
		int moved = count < 0 ? Math.min(-count, Math.max(0, ledger.tillSeen)) : count;
		if (moved == 0) {
			return 0;
		}

		ledger.tillSeen += count < 0 ? -moved : moved;
		ledger.tillDelta += count < 0 ? -moved : moved;
		return moved;
	}

	/** A farmer's day brings in this much food, each of its people eats this much; at war its fields give this share. */
	private static final double FARMER_BRINGS = 2.5;
	private static final double EATEN = 0.6;
	private static final double WAR_FIELDS = 0.3;
	/** Its stores are never up or down on paper by more than this (items). */
	private static final double MOST_FOOD = 256.0;

	/** Its stores, brought up to date (the store chests loaded): bread in for what its days brought, food out for what they ate. */
	private static boolean stores(ServerLevel level, Town town) {
		Ledger ledger = town.away;
		int whole = (int)ledger.food;
		if (whole == 0 || town.store.isEmpty()) {
			return false;
		}

		boolean any = false;
		for (BlockPos pos : town.store) {
			if (whole == 0) {
				break;
			}

			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof net.minecraft.world.Container chest)) {
				continue;
			}

			any = true;

			for (int i = 0; i < chest.getContainerSize() && whole != 0; i++) {
				var stack = chest.getItem(i);
				if (whole > 0 && stack.isEmpty()) {
					int put = Math.min(64, whole);
					chest.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, put));
					whole -= put;
				} else if (whole > 0 && stack.is(net.minecraft.world.item.Items.BREAD) && stack.getCount() < 64) {
					int put = Math.min(64 - stack.getCount(), whole);
					stack.grow(put);
					whole -= put;
				} else if (whole < 0 && stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
					int take = Math.min(stack.getCount(), -whole);
					stack.shrink(take);
					whole += take;
				}
			}

			chest.setChanged();
		}

		// (None of its stores loaded: another time. What there was no room for, or nothing left to eat: let go.)
		if (!any) {
			return false;
		}

		ledger.food = 0;
		return true;
	}

	/** The summary's losses so far are behind it (a world from before this, or a town in front of someone). */
	private static boolean settle(Town town) {
		WorldPlan.Place place = WorldPlan.place(town.id);
		int lost = place == null ? 0 : place.paperLost;
		if (town.away.lostApplied < 0) {
			town.away.lostApplied = lost;
			return true;
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// A day on paper
	// ---------------------------------------------------------------------------------------------

	private static void day(MinecraftServer server, Town town, long day) {
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		if (group == null || group.members.isEmpty()) {
			return;
		}

		Ledger ledger = town.away;
		ledger.fates.removeIf(fate -> day - fate.day > FATES_KEPT);
		RandomSource random = server.overworld().getRandom();
		settle(town);

		// Those it lost in the summary's battles: real deaths now, its fighters first, never its leader.
		WorldPlan.Place place = WorldPlan.place(town.id);
		int lost = place == null ? 0 : place.paperLost - ledger.lostApplied;
		String enemy = WorldPlan.enemyOf(town);
		for (int n = 0; n < Math.min(lost, DEATHS_A_DAY); n++) {
			ledger.lostApplied++;
			UUID one = victim(town, group, random);
			if (one != null) {
				dies(server, town, group, one, enemy == null ? "fighting far from home" : "fighting " + enemy, day, false);
			}
		}

		boolean war = enemy != null || Diplomacy.of(town).stream().anyMatch(r -> r.war);
		Unrest.Mood mood = Unrest.mood(town, group);

		// Its stores: what its farmers brought in, less what its people ate (at war, its fields half tended at best).
		long farmers = group.members.stream().filter(id -> "farmer".equals(town.lines.get(id))).count();
		ledger.food = Math.max(-MOST_FOOD, Math.min(MOST_FOOD, ledger.food + farmers * FARMER_BRINGS * (war ? WAR_FIELDS : 1.0) - group.members.size() * EATEN));

		// Its leader killed: very rarely; less rarely in a town risen against it, or at war.
		double chance = ASSASSIN_CHANCE * (town.revolt || mood.share() >= 0.5 ? ASSASSIN_REVOLT : 1.0) * (war ? ASSASSIN_WAR : 1.0);
		if (town.leader != null && group.members.contains(town.leader) && group.members.size() > 1 && random.nextDouble() < chance) {
			assassinated(server, town, group, day);
		}

		// Someone leaves.
		if (group.members.size() > FEWEST && random.nextDouble() < LEAVE_CHANCE * (1.0 + (LEAVE_UNHAPPY - 1.0) * mood.share())) {
			List<UUID> could = new ArrayList<>();
			for (UUID id : group.members) {
				if (!id.equals(group.leader) && !id.equals(town.leader) && !id.equals(town.keeper) && !town.crooks.contains(id)) {
					could.add(id);
				}
			}

			if (!could.isEmpty()) {
				// (The unhappiest of a few picked: who'd go is who likes it least.)
				UUID one = could.get(random.nextInt(could.size()));
				for (int tries = 0; tries < 2; tries++) {
					UUID other = could.get(random.nextInt(could.size()));
					if (Unrest.approval(town, other) < Unrest.approval(town, one)) {
						one = other;
					}
				}

				leaves(server, town, group, one, Unrest.approval(town, one) <= UNHAPPY ? "had enough of how it's led" : "wanted a change", day);
			}
		}

		// Someone comes: a house standing empty, the town at peace and not unhappy.
		if (!war && !town.revolt && mood.average() >= UNHAPPY && group.members.size() + ledger.newcomers.size() < TownLife.MOST_PEOPLE
			&& random.nextDouble() < ARRIVE_CHANCE * (mood.average() >= CONTENT ? ARRIVE_CONTENT : 1.0)) {
			Town.Plot empty = emptyHouse(town);
			if (empty != null) {
				comes(server, town, empty, day);
			}
		}
	}

	/** Who a battle far away took: one of its fighters, mostly, while it has any; else anyone but its leader. */
	@Nullable
	private static UUID victim(Town town, DrifterGroups.Group group, RandomSource random) {
		List<UUID> fighters = new ArrayList<>();
		List<UUID> others = new ArrayList<>();
		for (UUID id : group.members) {
			if (id.equals(town.leader) || DrifterApi.goneAway(id)) {
				continue;
			}

			DrifterRecord record = DrifterApi.record(id);
			if (record == null || record.status() != DrifterRecord.Status.ALIVE) {
				continue;
			}

			("guard".equals(town.lines.get(id)) ? fighters : others).add(id);
		}

		// (A town is never fought down past its leader and one more on paper: with fewer it's no town to come back to.
		// Its fighters all dead, the battle's losses are mostly nobody's: ordinary folk die only seldom.)
		if (fighters.size() + others.size() < 2 || fighters.isEmpty() && random.nextDouble() >= 1.0 - FIGHTER_SHARE) {
			return null;
		}

		List<UUID> from = !fighters.isEmpty() && (others.isEmpty() || random.nextDouble() < FIGHTER_SHARE) ? fighters : others;
		return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
	}

	/**
	 * One of its people died, away: a real death by the world's rule. Gone for good: out of the town, its house empty, a
	 * grave; back: it's told. True if it died.
	 */
	static boolean dies(MinecraftServer server, Town town, DrifterGroups.Group group, UUID id, String cause, long day, boolean leader) {
		DrifterRecord record = DrifterApi.record(id);
		String name = record == null ? Politics.name(group, id) : record.name();
		// (Out of its group as it goes: the chronicle tells it here, not as someone who left.)
		Chronicle.toldLeaving.add(id);
		DrifterApi.AwayDeath end = DrifterApi.dieAway(server, id, cause);
		if (end == DrifterApi.AwayDeath.UNKNOWN) {
			Chronicle.toldLeaving.remove(id);
			return false;
		}

		String who = leader ? name + ", leader of " + town.name + "," : name + " of " + town.name;
		String how = leader ? " was killed by an unknown hand" : " died " + cause;
		if (end == DrifterApi.AwayDeath.GONE) {
			vacate(town, id);
			town.approval.remove(id);
			town.taxOwed.remove(id);
			town.owed.remove(id);
			town.lineStocked.remove(id);
			if (id.equals(town.keeper)) {
				town.keeper = null;
			}

			DrifterApi.setGroupLimit(group, Math.max(group.members.size(), 2));
			Unrest.killed(town, group);
			TownGraves.add(town, name, day, leader ? "killed by an unknown hand" : cause);
			town.away.fates.add(new Fate(id, name, "gone", day, leader ? "killed by an unknown hand" : cause));
			Annals.death(server, town, id, name, leader ? "killed by an unknown hand" : cause, leader, true);
			Chronicle.add(server, leader ? "town" : "war", who + how);
		} else {
			Chronicle.toldLeaving.remove(id);
			town.away.fates.add(new Fate(id, name, "died", day, leader ? "killed by an unknown hand" : cause));
			Annals.death(server, town, id, name, leader ? "killed by an unknown hand" : cause, leader, false);
			Chronicle.add(server, leader ? "town" : "war", who + how + ", and came back");
		}

		DrifterCivilizations.LOGGER.info("[away] {}: {}{} ({})", town.name, name, how, end == DrifterApi.AwayDeath.GONE ? "gone for good" : "comes back");
		return true;
	}

	/** Its house is nobody's (or the next of those living there's), and its work is left for whoever takes the house. */
	private static void vacate(Town town, UUID id) {
		String line = town.lines.remove(id);
		DrifterRecord record = DrifterApi.record(id);
		for (Town.Plot plot : town.plots) {
			if (id.equals(plot.owner)) {
				plot.owner = plot.residents.isEmpty() ? null : plot.residents.remove(0);
				if (plot.owner == null && line != null && !line.equals("guard")) {
					town.away.vacant.put(plot.origin, line);
				}

				if (plot.owner == null && record != null) {
					town.away.wasOf.put(plot.origin, record.name());
				}
			}

			plot.residents.remove(id);
		}
	}

	/** Its leader killed (the user: "leaders can die as well but very rare (like an assassination)"): out of office, whatever the world's rule. */
	static boolean assassinated(MinecraftServer server, Town town, DrifterGroups.Group group, long day) {
		UUID was = town.leader;
		if (was == null || !dies(server, town, group, was, "killed by an unknown hand", day, true)) {
			return false;
		}

		// Back (respawn, lives left): an ordinary one of its people now, and not standing at the vote it brings.
		if (group.members.contains(was)) {
			UUID next = group.members.stream().filter(id -> !id.equals(was) && !town.crooks.contains(id))
				.max(java.util.Comparator.comparingDouble(Politics::keen)).orElse(null);
			if (next == null) {
				return true;
			}

			town.leader = next;
			town.leaderSince = day;
			Unrest.newLeader(town, null, next);
			town.candidates.clear();
			town.nextElection = day + 1;
			town.away.barred = was;
			Chronicle.add(server, "town", Politics.name(group, next) + " leads " + town.name + " till the vote tomorrow morning");
		}

		return true;
	}

	/** It left, on paper: out of the town now; its home and work let go when it's next loaded (at once, loaded now). */
	static void leaves(MinecraftServer server, Town town, DrifterGroups.Group group, UUID id, String why, long day) {
		DrifterEntity loaded = TownFounding.loaded(server, id);
		if (loaded != null) {
			TownLife.goes(server, town, group, loaded, why);
			town.lines.remove(id);
			town.away.fates.add(new Fate(id, loaded.getName().getString(), "left", day, why));
			return;
		}

		DrifterRecord record = DrifterApi.record(id);
		String name = record == null ? Politics.name(group, id) : record.name();
		vacate(town, id);
		town.approval.remove(id);
		town.taxOwed.remove(id);
		town.owed.remove(id);
		Chronicle.toldLeaving.add(id);
		DrifterGroups.leave(id);
		DrifterApi.setGroupLimit(group, Math.max(group.members.size(), 2));
		town.lastLeft = name;
		town.lastLeftDay = day;
		town.away.left.add(id);
		town.away.fates.add(new Fate(id, name, "left", day, why));
		Chronicle.add(server, "town", name + " left " + town.name + " for good (" + why + ")");
		DrifterCivilizations.LOGGER.info("[away] {}: {} left ({})", town.name, name, why);
	}

	/** A newcomer, on paper: named, the house theirs; made when the town's ground is loaded. */
	static void comes(MinecraftServer server, Town town, Town.Plot house, long day) {
		String name = DrifterApi.newName(server);
		for (int tries = 0; tries < 5 && taken(name); tries++) {
			name = DrifterApi.newName(server);
		}

		String line = town.away.vacant.getOrDefault(house.origin, "");
		town.away.vacant.remove(house.origin);
		town.away.newcomers.add(new Newcomer(name, house.origin, day, line));
		town.away.fates.add(new Fate(new UUID(0L, 0L), name, "came", day, ""));
		town.arrivedDay = day;
		town.newest = name;
		town.newestDay = day;
		String was = town.away.wasOf.remove(house.origin);
		String where = was != null ? was + "'s old house" : "a house standing empty";
		Chronicle.add(server, "town", name + " moved to " + town.name + " (came up the road looking for a place), into " + where);
		DrifterCivilizations.LOGGER.info("[away] {}: {} came, into {}", town.name, name, where);
	}

	/** Is this name a newcomer's already, somewhere? */
	private static boolean taken(String name) {
		for (Town town : Towns.all()) {
			for (Newcomer one : town.away.newcomers) {
				if (one.name.equalsIgnoreCase(name)) {
					return true;
				}
			}
		}

		return false;
	}

	/** A house that's up with nobody in it and no newcomer due, nearest the middle. */
	@Nullable
	private static Town.Plot emptyHouse(Town town) {
		Town.Plot best = null;
		for (Town.Plot plot : town.plots) {
			if (plot.built && plot.owner == null && plot.residents.isEmpty() && town.away.newcomers.stream().noneMatch(one -> one.house.equals(plot.origin))
				&& (best == null || plot.origin.distSqr(town.center) < best.origin.distSqr(town.center))) {
				best = plot;
			}
		}

		return best;
	}

	/** How many it has, counting newcomers not made yet. */
	static int people(Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		return (group == null ? 0 : group.members.size()) + town.away.newcomers.size();
	}

	// ---------------------------------------------------------------------------------------------
	// Its ground loaded: what its days left to do
	// ---------------------------------------------------------------------------------------------

	/** Newcomers made in their houses, leavers' homes let go, graves dug: wherever the ground's loaded. True if anything was done. */
	static boolean arrive(MinecraftServer server, Town town) {
		Ledger ledger = town.away;
		// (Its till, loaded: what moved on paper done to it, and what it holds seen.)
		Diplomacy.till(server, town, 0);
		if (ledger.newcomers.isEmpty() && ledger.left.isEmpty() && !TownGraves.waiting(town) && (int)ledger.food == 0) {
			return false;
		}

		ServerLevel level = TownLife.level(server, town);
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		if (level == null || group == null) {
			return false;
		}

		boolean changed = false;
		for (java.util.Iterator<Newcomer> it = ledger.newcomers.iterator(); it.hasNext();) {
			Newcomer one = it.next();
			Town.Plot plot = town.plots.stream().filter(p -> p.origin.equals(one.house)).findFirst().orElse(null);
			if (plot == null || !plot.built || plot.owner != null) {
				// (Its house gone, or someone's since: it never came.)
				ledger.fates.removeIf(fate -> fate.kind.equals("came") && fate.name.equals(one.name));
				it.remove();
				changed = true;
				continue;
			}

			if (!level.isLoaded(plot.origin) || !level.isPositionEntityTicking(plot.origin)) {
				continue;
			}

			DrifterEntity made = DrifterApi.spawnAs(level, plot.origin.above(), one.name);
			if (made == null) {
				continue;
			}

			it.remove();
			changed = true;
			DrifterApi.notABandit(made);
			if (!TownLife.movesIn(server, level, town, group, made, plot, null, null)) {
				ledger.fates.removeIf(fate -> fate.kind.equals("came") && fate.name.equals(one.name));
				made.discard();
				continue;
			}

			if (!one.line.isEmpty()) {
				town.lines.put(made.getUUID(), one.line);
			}

			// (In its house, not on its roof: where it'd stand up from its bed.)
			var home = DrifterApi.home(made);
			BlockPos at = home == null ? null : standing(level, home.center);
			if (at != null) {
				made.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			}

			for (int i = 0; i < ledger.fates.size(); i++) {
				Fate fate = ledger.fates.get(i);
				if (fate.kind.equals("came") && fate.name.equals(one.name)) {
					ledger.fates.set(i, new Fate(made.getUUID(), fate.name, fate.kind, fate.day, fate.cause, fate.time));
				}
			}

			DrifterCivilizations.LOGGER.info("[away] {}: {} is there now (came on day {})", town.name, one.name, one.day);
		}

		for (java.util.Iterator<UUID> it = ledger.left.iterator(); it.hasNext();) {
			UUID id = it.next();
			DrifterEntity one = TownFounding.loaded(server, id);
			DrifterRecord record = DrifterApi.record(id);
			if (one == null) {
				if (record == null || record.status() == DrifterRecord.Status.GONE) {
					it.remove();
					changed = true;
				}

				continue;
			}

			it.remove();
			changed = true;
			DrifterApi.setHome(one, null);
			DrifterApi.setTownWork(one, null);
			DrifterApi.think(one, "left " + town.name + " for good a while back");
		}

		return TownGraves.dig(level, town) | stores(level, town) || changed;
	}

	/** Somewhere to stand at or by this spot (two blocks of room over something solid), or null. */
	@Nullable
	private static BlockPos standing(ServerLevel level, BlockPos center) {
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}

					for (int dy = 2; dy >= -3; dy--) {
						BlockPos pos = center.offset(dx, dy, dz);
						if (level.isLoaded(pos) && level.getBlockState(pos.below()).isSolidRender(level, pos.below())
							&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
							&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
							return pos;
						}
					}
				}
			}
		}

		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// Debug
	// ---------------------------------------------------------------------------------------------

	/** For the command: its ledger in words. */
	static List<String> status(MinecraftServer server, Town town) {
		List<String> lines = new ArrayList<>();
		Ledger ledger = town.away;
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		WorldPlan.Place place = WorldPlan.place(town.id);
		lines.add(town.name + ": " + (group == null ? 0 : group.members.size()) + " people, " + (watched(server, town) ? "someone near (its own life)" : "nobody near (on paper)")
			+ ", last day " + ledger.day + ", lost on paper " + (place == null ? 0 : place.paperLost) + " (made deaths: " + Math.max(0, ledger.lostApplied) + ")");
		ledger.newcomers.forEach(one -> lines.add("  to come: " + one.name + " (day " + one.day + (one.line.isEmpty() ? "" : ", " + one.line) + ")"));
		ledger.left.forEach(id -> lines.add("  left, home still to let go: " + (DrifterApi.record(id) == null ? id : DrifterApi.record(id).name())));
		ledger.fates.forEach(fate -> lines.add("  day " + fate.day + ": " + fate.name + " " + fate.kind + (fate.cause.isEmpty() ? "" : " (" + fate.cause + ")")));
		lines.addAll(TownGraves.status(town));
		return lines;
	}

	/** Debug: days on paper now, whoever's near. */
	static String debugDays(MinecraftServer server, Town town, int days) {
		for (int n = 0; n < days; n++) {
			day(server, town, Chronicle.day(server));
		}

		arrive(server, town);
		Towns.save(server);
		return town.name + " had " + days + " day" + (days == 1 ? "" : "s") + " on paper";
	}

	/** Debug: one of these now: "kill" (a battle's loss), "leave", "newcomer", "assassinate". */
	static String debug(MinecraftServer server, Town town, String what) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return "it has no people";
		}

		long day = Chronicle.day(server);
		RandomSource random = server.overworld().getRandom();
		String done;
		switch (what) {
			case "kill" -> {
				UUID one = victim(town, group, random);
				String name = one == null ? null : Politics.name(group, one);
				done = one != null && dies(server, town, group, one, "fighting far from home (debug)", day, false) ? name + " died" : "nobody to die";
			}
			case "assassinate" -> {
				String name = Politics.name(group, town.leader);
				done = assassinated(server, town, group, day) ? name + " was killed" : "no leader to kill";
			}
			case "leave" -> {
				UUID one = group.members.stream().filter(id -> !id.equals(group.leader) && !id.equals(town.leader) && !id.equals(town.keeper)).findFirst().orElse(null);
				if (one == null) {
					done = "nobody to leave";
				} else {
					String name = Politics.name(group, one);
					leaves(server, town, group, one, "wanted a change (debug)", day);
					done = name + " left";
				}
			}
			case "newcomer" -> {
				Town.Plot empty = emptyHouse(town);
				if (empty == null) {
					done = "no house standing empty";
				} else {
					comes(server, town, empty, day);
					done = town.away.newcomers.get(town.away.newcomers.size() - 1).name + " came";
				}
			}
			default -> done = "not kill, leave, newcomer or assassinate";
		}

		arrive(server, town);
		Towns.save(server);
		return town.name + ": " + done;
	}
}
