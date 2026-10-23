package com.dinglebob.drifter.social.talk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What it has done lately that's worth talking about (the dynamic chat, phase 1; user, 2026-10-23: "greatly expand small
 * talk conversations between drifters"): ore it mined, mobs it killed, a close call, how it last died, what it built,
 * who it traded with, places it found. Its own doings only (what it knows first-hand). The same thing again the same day
 * adds up ("mined 7 iron"). Kept a few days, saved with it.
 */
public final class Diary {
	public enum Kind {
		ORE, KILLED, CLOSE_CALL, DIED, BUILT, TRADED, SAW
	}

	/** One thing: what kind, about what ("iron", "zombie", "house"), how many, when (game ticks). */
	public static final class Entry {
		public final Kind kind;
		public final String what;
		public int count;
		public long time;

		Entry(Kind kind, String what, int count, long time) {
			this.kind = kind;
			this.what = what;
			this.count = count;
			this.time = time;
		}
	}

	/** How long it remembers (game ticks: three days). */
	private static final long KEEPS = 24000L * 3;
	private static final int MOST = 40;

	private final List<Entry> entries = new ArrayList<>();

	/** Notes this (the same kind and thing within the day adds to it). */
	public void add(Kind kind, String what, int count, long now) {
		this.entries.removeIf(entry -> now - entry.time > KEEPS);
		for (Entry entry : this.entries) {
			if (entry.kind == kind && entry.what.equals(what) && now - entry.time < 24000L) {
				entry.count += count;
				entry.time = now;
				return;
			}
		}

		this.entries.add(new Entry(kind, what, count, now));
		if (this.entries.size() > MOST) {
			this.entries.remove(0);
		}
	}

	/** The most notable of this kind within {@code within} ticks: the biggest count (the latest if equal), or null. */
	@Nullable
	public Entry best(Kind kind, long now, long within) {
		Entry best = null;
		for (Entry entry : this.entries) {
			if (entry.kind == kind && now - entry.time <= within && (best == null || entry.count > best.count || entry.count == best.count && entry.time > best.time)) {
				best = entry;
			}
		}

		return best;
	}

	/** The latest of this kind within {@code within} ticks, or null. */
	@Nullable
	public Entry latest(Kind kind, long now, long within) {
		Entry latest = null;
		for (Entry entry : this.entries) {
			if (entry.kind == kind && now - entry.time <= within && (latest == null || entry.time > latest.time)) {
				latest = entry;
			}
		}

		return latest;
	}

	/** All of this kind within {@code within} ticks, added up. */
	public int total(Kind kind, long now, long within) {
		return this.entries.stream().filter(entry -> entry.kind == kind && now - entry.time <= within).mapToInt(entry -> entry.count).sum();
	}

	public List<Entry> all() {
		return List.copyOf(this.entries);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Entry entry : this.entries) {
			CompoundTag one = new CompoundTag();
			one.putString("Kind", entry.kind.name());
			one.putString("What", entry.what);
			one.putInt("Count", entry.count);
			one.putLong("Time", entry.time);
			list.add(one);
		}

		tag.put("Entries", list);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.entries.clear();
		for (Tag one : tag.getList("Entries", Tag.TAG_COMPOUND)) {
			CompoundTag entry = (CompoundTag)one;
			try {
				this.entries.add(new Entry(Kind.valueOf(entry.getString("Kind")), entry.getString("What"), entry.getInt("Count"), entry.getLong("Time")));
			} catch (IllegalArgumentException ignored) {
				// (A kind from a newer version: skipped.)
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Noting it down
	// ---------------------------------------------------------------------------------------------

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			DrifterEntity killer = killer(source);
			if (killer != null && victim != killer && !(victim instanceof Player)) {
				killer.getDiary().add(Kind.KILLED, name(victim), 1, killer.level().getGameTime());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof DrifterEntity drifter && damageTaken > 0.0F && drifter.isAlive() && drifter.getHealth() <= 5.0F) {
				drifter.getDiary().add(Kind.CLOSE_CALL, cause(source), 1, drifter.level().getGameTime());
			}
		});
		com.dinglebob.drifter.api.DrifterEvents.BUILT.register((drifter, what, where) ->
			drifter.getDiary().add(Kind.BUILT, what.startsWith("town:") ? "the town " + what.substring(5) : what, 1, drifter.level().getGameTime()));
		com.dinglebob.drifter.api.DrifterEvents.TRADED.register((asker, giver, paid, got) -> {
			asker.getDiary().add(Kind.TRADED, giver.getName().getString(), 1, asker.level().getGameTime());
			giver.getDiary().add(Kind.TRADED, asker.getName().getString(), 1, giver.level().getGameTime());
		});
	}

	/** It broke this block: ore is worth talking about. */
	public static void mined(DrifterEntity drifter, BlockState state) {
		String ore = ore(state);
		if (ore != null) {
			drifter.getDiary().add(Kind.ORE, ore, 1, drifter.level().getGameTime());
		}
	}

	/** It died, as its death message has it ("fell from a high place"). */
	public static void died(DrifterEntity drifter, String message) {
		String name = drifter.getName().getString();
		drifter.getDiary().add(Kind.DIED, message.startsWith(name) ? message.substring(name.length()).trim() : "died", 1, drifter.level().getGameTime());
	}

	/** It found a place worth telling of (a village, a temple...). */
	public static void saw(DrifterEntity drifter, String place) {
		drifter.getDiary().add(Kind.SAW, place, 1, drifter.level().getGameTime());
	}

	@Nullable
	private static String ore(BlockState state) {
		if (state.is(BlockTags.DIAMOND_ORES)) {
			return "diamonds";
		} else if (state.is(BlockTags.IRON_ORES)) {
			return "iron";
		} else if (state.is(BlockTags.GOLD_ORES) || state.is(Blocks.NETHER_GOLD_ORE)) {
			return "gold";
		} else if (state.is(BlockTags.EMERALD_ORES)) {
			return "emeralds";
		} else if (state.is(BlockTags.LAPIS_ORES)) {
			return "lapis";
		} else if (state.is(BlockTags.REDSTONE_ORES)) {
			return "redstone";
		} else if (state.is(BlockTags.COAL_ORES)) {
			return "coal";
		} else if (state.is(BlockTags.COPPER_ORES)) {
			return "copper";
		} else if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return "ancient debris";
		}

		return null;
	}

	@Nullable
	private static DrifterEntity killer(DamageSource source) {
		if (source.getEntity() instanceof DrifterEntity drifter) {
			return drifter;
		}

		return com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
	}

	/** A mob's plain name ("zombie", "cave spider"). */
	private static String name(LivingEntity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().replace('_', ' ');
	}

	/** What nearly killed it ("creeper", "a fall", "lava"...). */
	private static String cause(DamageSource source) {
		if (source.getEntity() instanceof LivingEntity attacker) {
			return attacker instanceof Player player ? player.getGameProfile().getName() : name(attacker);
		}

		String type = source.typeHolder().unwrapKey().map(key -> key.location().getPath()).orElse("something");
		return switch (type) {
			case "fall" -> "a fall";
			case "lava" -> "lava";
			case "in_fire", "on_fire" -> "fire";
			case "drown" -> "drowning";
			case "starve" -> "hunger";
			case "explosion", "player_explosion" -> "an explosion";
			case "cactus" -> "a cactus";
			default -> type.replace('_', ' ').toLowerCase(Locale.ROOT);
		};
	}
}
