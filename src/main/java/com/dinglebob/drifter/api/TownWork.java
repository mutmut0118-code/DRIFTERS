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
	 * How soon it may take its next whim, as a share of the usual wait between them (milestone 23 C2, C6: a festival, the
	 * gatherings at midday and of an evening: sooner, so a crowd gathers). 1 by default.
	 */
	default double whimPace(DrifterEntity drifter) {
		return 1.0;
	}

	/**
	 * A spot to stand at by one of the town's places (milestone 23 C2, places to be): "plaza", "well", "fire" (its gathering
	 * fire), "board" (its notice board), "stall", "store", "fields", "bell"; or null if it has none of them. A little way off
	 * the thing itself, round it, different for each of its people (so a few gather round, not on one block).
	 */
	@org.jetbrains.annotations.Nullable
	default BlockPos place(DrifterEntity drifter, String kind) {
		return null;
	}

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

	/**
	 * A Bandit living in the town (the user, 2026-10-28: "if they are in a town they should be commiting several crimes"):
	 * every so often it steals or picks a fight ({@code CrookGoal}), and tells the town with {@link #crime}.
	 */
	default boolean crook(DrifterEntity drifter) {
		return false;
	}

	/**
	 * Its town's bell to ring now, why, and what to call out after (an event's begun: a festival, market day, a war), for
	 * the one its town picked to ring it; null for everyone else, and when there's nothing to ring for.
	 */
	default Bell bell(DrifterEntity drifter) {
		return null;
	}

	/**
	 * Where to be for its town's gathering now (a festival day: the plaza, a spot of its own there), or null when there's
	 * none on (the user, 2026-11-06: "when festivals happen civs should mainly gather around the plaza").
	 */
	default BlockPos gathering(DrifterEntity drifter) {
		return null;
	}

	/**
	 * The news for its town's crier to call out this morning, and the paper to hand out (a written book), if it's the crier
	 * and hasn't yet today; else null.
	 */
	default News news(DrifterEntity drifter) {
		return null;
	}

	/** It's its town's crier (it keeps a book and quill or two for the paper). */
	default boolean crier(DrifterEntity drifter) {
		return false;
	}

	/** It cried the news for today. */
	default void cried(DrifterEntity drifter) {
	}

	/** The day's headlines, and a copy of the paper. */
	record News(List<String> headlines, net.minecraft.world.item.ItemStack paper) {
	}

	/** It rang the bell. */
	default void rang(DrifterEntity drifter) {
	}

	/**
	 * A bell to ring: where it is, what for ("the festival"), the lines to call out after; and whether it's an alarm (rung
	 * fast, over and over: raiders, a killing, a massacre; 0.23.30) and where the trouble is, if the town knows.
	 */
	record Bell(BlockPos pos, String why, List<String> lines, boolean alarm, @org.jetbrains.annotations.Nullable BlockPos trouble) {
		public Bell(BlockPos pos, String why, List<String> lines) {
			this(pos, why, lines, false, null);
		}
	}

	/** How soon a crook goes at it again, as a share of the usual wait (a city's crook: much sooner). 1 by default. */
	default double crookPace(DrifterEntity drifter) {
		return 1.0;
	}

	/**
	 * One of its town's guards (the user, 2026-11-06: "in a city there should be a higher ratio of guards, patrolling at
	 * night so random civs dont get killed"): it walks the town's rounds ({@code PatrolGoal}), by night most of all.
	 */
	default boolean guard(DrifterEntity drifter) {
		return false;
	}

	/**
	 * A job it's taken on for a player (B5: an enchanting, a repair, a netherite upgrade), or null: what kind ("enchant",
	 * "repair", "netherite"), said ("enchant someone's sword"), the item, what it was given to do it with.
	 */
	default Service service(DrifterEntity drifter) {
		return null;
	}

	/** It's done the job (or couldn't): what goes back to the player (the item, leftovers). */
	default void serviced(DrifterEntity drifter, List<net.minecraft.world.item.ItemStack> back, boolean done) {
	}

	record Service(String kind, String what, net.minecraft.world.item.ItemStack item, List<net.minecraft.world.item.ItemStack> materials) {
	}

	/** It makes enchanted books to sell (its town's enchanter: B6, what villagers gave had now). */
	default boolean enchantsBooks(DrifterEntity drifter) {
		return false;
	}

	/**
	 * (API 14.) A thing of its trade it carries that it would enchant to sell dearer (a smith's piece, now and then), or
	 * null: at an enchanting table it knows, the table's own roll, with its own levels and lapis.
	 */
	@org.jetbrains.annotations.Nullable
	default java.util.function.Predicate<net.minecraft.world.item.ItemStack> wareToEnchant(DrifterEntity drifter) {
		return null;
	}

	/** (API 14.) It went to enchant one ({@code done}: it came off). */
	default void wareEnchanted(DrifterEntity drifter, boolean done) {
	}

	/** The player this guard keeps an eye on now (one its town is wary of, or worse, in the town), or null. */
	default net.minecraft.world.entity.player.Player watched(DrifterEntity guard) {
		return null;
	}

	/** A guard's rounds: the spots it walks between, in order (along its town's roads), or empty. */
	default List<BlockPos> patrol(DrifterEntity drifter) {
		return List.of();
	}

	/** This chest is someone else's in the town (the town's store or till, a neighbour's): a crook's to rob. */
	default boolean othersChest(DrifterEntity drifter, BlockPos chest) {
		return false;
	}

	/**
	 * The crook did it: {@code kind} "theft" (from the chest at {@code where}) or "fight" (it went for {@code victim}). The
	 * town judges by which of its people saw it.
	 */
	default void crime(DrifterEntity drifter, String kind, @org.jetbrains.annotations.Nullable DrifterEntity victim, BlockPos where) {
	}

	/**
	 * How its town stands with this player: "wanted" (its guards and bold people go for them on sight about the town, the
	 * rest keep clear), "exiled" (its people drive them out of the town), or null.
	 */
	@org.jetbrains.annotations.Nullable
	default String standing(DrifterEntity drifter, net.minecraft.world.entity.player.Player player) {
		return null;
	}

	/**
	 * Its town wants this player for something past forgiving (its leader's murder, a massacre; 0.23.35, the user: "if im
	 * wanted after i kill the leader it should be kill on sight for anybody who isnt a coward"): its people who aren't
	 * cowards go for them wherever they see them, not only about the town, and stand and fight.
	 */
	default boolean killOnSight(DrifterEntity drifter, net.minecraft.world.entity.player.Player player) {
		return false;
	}

	/**
	 * Tonight its town keeps it up (0.23.40: a night out, where people gather, till it goes home to bed): the time of day
	 * (the sun's, as {@code level.getDayTime() % 24000}) it goes in at; or -1, in with the sun as ever.
	 */
	default long bedtime(DrifterEntity drifter) {
		return -1L;
	}

	/**
	 * A banner its town wants it to make and stand at a spot (a nation's colour on the plaza: milestone 22 step 6 part 3), or
	 * null. Asked of each of its people (one of them makes it).
	 */
	@org.jetbrains.annotations.Nullable
	default Banner banner(DrifterEntity drifter) {
		return null;
	}

	/**
	 * How much more this goal (by id) counts for it in what it chooses (its line of work: milestone 23 B1; a little, 0 for
	 * most goals).
	 */
	default double leaning(DrifterEntity drifter, String goal) {
		return 0.0;
	}

	/**
	 * What its line of work has it make to sell (milestone 23 B2: a smith's tools, a fletcher's arrows), or null (nothing
	 * now). Asked now and then; it gets them the way it gets anything, and puts them away at home.
	 */
	@org.jetbrains.annotations.Nullable
	default Wares wares(DrifterEntity drifter) {
		return null;
	}

	/** It stood the banner up there. */
	default void bannerUp(DrifterEntity drifter, BlockPos where) {
	}

	/**
	 * Things to make: any of {@code items} till it carries {@code count} of them in all (the cheapest to make first), and
	 * what they are for its thoughts ("tools to sell").
	 */
	record Wares(java.util.List<net.minecraft.world.item.Item> items, int count, String what) {
	}

	/** A banner of this colour (a banner item, e.g. {@code Items.RED_BANNER}) to stand at {@code spot}, and why (its thought). */
	record Banner(net.minecraft.world.item.Item item, BlockPos spot, String what) {
	}
}
