package com.dinglebob.drifter.api;

import java.util.List;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;

/**
 * The work a town's people share (set by Drifter: Civilizations with {@link DrifterApi#setTownWork}; not saved, set again
 * while it's in a town): its roads made into path with a shovel, its ground kept lit, the ground round each one's house
 * eased into the land. Each block of it done by a Drifter, with what it has, at a player's pace.
 */
public interface TownWork {
	/** The columns (x and z; the y is ignored) of the town's roads, the ones near the middle first. */
	List<BlockPos> roads(DrifterEntity drifter);

	/** This column is the town's own ground (kept lit at night): its roads and round its houses, not the woods round it. */
	boolean townGround(int x, int z);

	/** The town's middle (its plaza). */
	BlockPos center();

	/** How far from the middle its ground goes. */
	int radius();

	/**
	 * Cells this one is to fill with water (a well's basin; a Drifter with a bucket fetches it from water it knows and pours
	 * it in), or none. Asked now and then.
	 */
	default List<BlockPos> water(DrifterEntity drifter) {
		return List.of();
	}

	/**
	 * This column is ground the town wants clear of trees (its plaza): its people fell the ones on it they know of, whole,
	 * with an axe. False everywhere by default.
	 */
	default boolean clearOfTrees(int x, int z) {
		return false;
	}

	/**
	 * It did some of the town's work (milestone 22 step 4, wages): {@code job} is "roads" (a block made path), "trees" (a log
	 * of a tree felled on the plaza), "water" (a bucket poured into the well) or "build" (a block of a town build placed);
	 * {@code units} how many. The town may owe it wages for it.
	 */
	default void worked(DrifterEntity drifter, String job, int units) {
	}

	/** What the town owes it in wages (money points: an emerald is 6). */
	default double owed(DrifterEntity drifter) {
		return 0.0;
	}

	/** Where the town keeps its money (a chest in its store), or null if it has nowhere yet. */
	@org.jetbrains.annotations.Nullable
	default BlockPos till() {
		return null;
	}

	/** It took this much (points) of its wages out of the till. */
	default void paid(DrifterEntity drifter, double points) {
	}

	/** Tax it owes the town (emeralds; step 5: a leader's law), paid into the till. */
	default int taxOwed(DrifterEntity drifter) {
		return 0;
	}

	/** It paid this much tax (emeralds) into the till. */
	default void taxPaid(DrifterEntity drifter, int emeralds) {
	}

	/**
	 * What it knows of its town's affairs to talk about (step 5: its leader, an election coming, the candidates, the laws,
	 * the tax): words for lines ({@code {a.leader}}...) and flags for topics ({@code election_soon}, {@code candidate}...).
	 */
	default void talk(DrifterEntity drifter, java.util.Map<String, Object> words, java.util.Set<String> flags) {
	}

	/** A fine a guard is to collect from a player (step 5, part 2): whom, what it's for, how much (emeralds). */
	record Arrest(java.util.UUID player, String name, int fine, String crime) {
	}

	/** The fine this one (a guard of its town) is to collect now, or null. */
	@org.jetbrains.annotations.Nullable
	default Arrest arrest(DrifterEntity guard) {
		return null;
	}

	/** How it went: {@code paid} emeralds of it handed over, or they {@code refused} (walked off, wouldn't pay). */
	default void arrested(DrifterEntity guard, java.util.UUID player, int paid, boolean refused) {
	}

	/** How its town stands with this player: "exiled" (its people drive them out of the town), or null. */
	@org.jetbrains.annotations.Nullable
	default String standing(DrifterEntity drifter, net.minecraft.world.entity.player.Player player) {
		return null;
	}
}
