package com.dinglebob.drifter.api;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * What the world keeps about one Drifter (DESIGN.md section 20's world-level registry; milestone 22 groundwork), readable
 * while it's far away and unloaded: who it is, whether it's alive, where it lives and was last, its group, how far along
 * it is, and how it feels about its closest Drifters and the players it knows. A snapshot, brought up to date while it's
 * loaded (its position every few seconds, the rest every minute or so) and kept as it was when it unloaded.
 *
 * <p>For the world's own bookkeeping (commands, an addon's town summaries): never for one Drifter to know about another
 * (DESIGN.md section 1: knowledge only through perception).
 */
public record DrifterRecord(UUID id, String name, Status status, ResourceKey<Level> level, BlockPos lastPos, long lastSeen, long firstSeen,
	@Nullable ResourceLocation homeDimension, @Nullable BlockPos home, @Nullable BlockPos bed, @Nullable UUID group, int stage, int deaths,
	String archetype, String disposition, Map<String, Double> traits, Map<UUID, Double> drifterTrust, Map<UUID, Double> playerTrust) {

	public enum Status {
		/** In the world (loaded or not). */
		ALIVE,
		/** Dead, and coming back (respawn setting). */
		DEAD,
		/** Gone for good: out of lives, permadeath, removed. Kept for the history. */
		GONE
	}

	/** The same Drifter, last seen here and now. */
	public DrifterRecord seenAt(ResourceKey<Level> level, BlockPos pos, long time) {
		return new DrifterRecord(this.id, this.name, Status.ALIVE, level, pos, time, this.firstSeen, this.homeDimension, this.home, this.bed, this.group,
			this.stage, this.deaths, this.archetype, this.disposition, this.traits, this.drifterTrust, this.playerTrust);
	}

	/** The same Drifter, with this status (and death count). */
	public DrifterRecord withStatus(Status status, int deaths) {
		return new DrifterRecord(this.id, this.name, status, this.level, this.lastPos, this.lastSeen, this.firstSeen, this.homeDimension, this.home, this.bed,
			this.group, this.stage, deaths, this.archetype, this.disposition, this.traits, this.drifterTrust, this.playerTrust);
	}
}
