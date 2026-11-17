package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Where a town is, asked of anyone (the user, 2026-11-17: "do you know where roseridge is?" in Roseridge got "no roseridge
 * here. wrong town maybe?", the name taken for a person; and the idea: "oh CITY? / i think its.. / north east? / which is /
 * that way (pointing at actual direction ... by spam punching the air in that direction, like a tic)", "with variations of
 * pacing, certainty"). The town they're in: "you're standing in it". Another: what this one knows of it, as it knows it (a
 * neighbour, one its town deals with, one of its own nation: sure of the way; further, and only heard of: unsure, "i think
 * its.. north east?"; never heard of: it says so). Said in a few lines with pauses as a player thinks and types, and on
 * "that way" it turns and punches the air toward it. A nation asked after is its capital.
 */
final class TownWhere {
	private TownWhere() {
	}

	/** Known of: this near its own town (neighbours); sure of the way: this near; heard of at least: this near. */
	private static final double KNOWS = 700.0;
	private static final double SURE = 350.0;
	private static final double HEARD = 1800.0;
	/** Within this of a town's middle you're in it. */
	private static final double IN_IT = 80.0;

	/** Words that ask the way. */
	private static final List<String> ASKS = List.of(" where ", " wheres ", " which way ", " how do i get to ", " how to get to ", " how far ",
		" direction ", " directions ", " way to ", " find ", " get to ", " located ", " is it ");

	/** Asked where a town (or a nation) is? True: answered. */
	static boolean asked(ServerPlayer player, DrifterEntity drifter, String text) {
		String said = " " + text.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9 ]+", " ").replaceAll(" +", " ").trim() + " ";
		if (ASKS.stream().noneMatch(said::contains)) {
			return false;
		}

		Town town = null;
		String name = null;
		for (Town one : Towns.all()) {
			String lower = one.name.toLowerCase(Locale.ROOT);
			if (said.contains(" " + lower + " ") && (name == null || lower.length() > name.length())) {
				town = one;
				name = lower;
			}
		}

		for (Nations.Nation nation : Nations.all()) {
			String lower = nation.name.toLowerCase(Locale.ROOT);
			String head = lower.replaceFirst("^(the )?(kingdom|commonwealth|union|dominion|republic|empire|league|federation|realm|crown) of ", "");
			if ((said.contains(" " + lower + " ") || head.length() >= 4 && said.contains(" " + head + " ")) && (name == null || lower.length() > name.length())) {
				Town capital = Towns.all().stream().filter(one -> one.id.equals(nation.capital)).findFirst().orElse(null);
				if (capital != null) {
					town = capital;
					name = lower;
				}
			}
		}

		if (town == null) {
			return false;
		}

		tell(drifter, player, town);
		return true;
	}

	/** A line of the answer: its key, and the pause before it (as it thinks and types). */
	private record Step(String key, long pause, boolean point) {
	}

	private static void tell(DrifterEntity drifter, ServerPlayer player, Town town) {
		RandomSource random = drifter.getRandom();
		String dimension = drifter.level().dimension().location().toString();
		List<Step> steps = new ArrayList<>();
		// Here already.
		if (town.dimension.equals(dimension) && Math.sqrt(town.center.distSqr(player.blockPosition())) <= IN_IT) {
			steps.add(new Step("town.way.here", 0, false));
			run(drifter, player, town, steps, 0, player.level().getGameTime());
			return;
		}

		// What it knows of it (as its town would: neighbours, the towns it deals with, its own nation's).
		Town home = home(drifter);
		double far = town.dimension.equals(dimension) ? Math.sqrt(town.center.distSqr(drifter.blockPosition())) : Double.MAX_VALUE;
		Nations.Nation ours = home == null ? null : Nations.of(home);
		boolean sameNation = ours != null && ours.has(town.id);
		boolean dealt = home != null && Diplomacy.relation(home, town) != null;
		boolean knows = sameNation || dealt || far <= KNOWS;
		if (!knows) {
			steps.add(new Step(far <= HEARD ? "town.way.heard_only" : "town.way.never", 0, false));
			run(drifter, player, town, steps, 0, player.level().getGameTime());
			return;
		}

		boolean sure = far <= SURE || sameNation && far <= KNOWS * 2 || random.nextDouble() < 0.15;
		if (random.nextDouble() < 0.12) {
			sure = !sure;
		}

		if (random.nextDouble() < 0.8) {
			steps.add(new Step("town.way.start", 0, false));
		}

		if (sure) {
			steps.add(new Step(random.nextBoolean() ? "town.way.sure" : "town.way.sure_far", 15 + random.nextInt(25), false));
			steps.add(new Step("town.way.that_way", 10 + random.nextInt(20), true));
		} else {
			steps.add(new Step("town.way.thinking", 10 + random.nextInt(20), false));
			// (A long think, as typed.)
			steps.add(new Step("town.way.unsure", 30 + random.nextInt(40), false));
			if (random.nextDouble() < 0.5) {
				steps.add(new Step("town.way.which_is", 20 + random.nextInt(25), false));
			}

			steps.add(new Step("town.way.that_way", 15 + random.nextInt(30), true));
		}

		run(drifter, player, town, steps, 0, player.level().getGameTime());
	}

	/** Each line after the last is said and its pause; stopped if they say something else or go. */
	private static void run(DrifterEntity drifter, ServerPlayer player, Town town, List<Step> steps, int i, long began) {
		if (i >= steps.size() || !drifter.isAlive() || player.isRemoved() || drifter.level() != player.level() || drifter.distanceTo(player) > 24.0
			|| i > 0 && TownStory.spokeSince(player, began)) {
			return;
		}

		Step step = steps.get(i);
		Runnable said = () -> {
			if (step.point()) {
				TalkApi.point(drifter, town.center);
			}

			if (i + 1 < steps.size()) {
				TownStory.later(steps.get(i + 1).pause(), () -> run(drifter, player, town, steps, i + 1, began));
			}
		};
		if (!TalkApi.answer(drifter, player, step.key(), said, "town", town.name, "dir", compass(drifter.blockPosition(), town.center),
			"dist", distance(drifter.blockPosition(), town.center))) {
			said.run();
		}
	}

	/** Its own town, or null. */
	@Nullable
	private static Town home(DrifterEntity drifter) {
		String name = Retell.townOf(drifter);
		return name == null ? null : Towns.all().stream().filter(t -> t.name.equals(name)).findFirst().orElse(null);
	}

	/** "north east", "south", as players say it. */
	static String compass(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		// (North is -z in Minecraft.)
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int)Math.floorMod(Math.round(angle / 45.0), 8);
		return new String[] {"north", "north east", "east", "south east", "south", "south west", "west", "north west"}[sector];
	}

	/** How far, as said ("not far", "pretty far, like 1200 blocks"). */
	static String distance(BlockPos from, BlockPos to) {
		double blocks = Math.sqrt(from.distSqr(to));
		if (blocks < 120) {
			return "right over there";
		}

		if (blocks < 300) {
			return "not far";
		}

		if (blocks < 700) {
			return "a bit of a walk, like " + Math.round(blocks / 100.0) * 100 + " blocks";
		}

		return blocks < 1500 ? "pretty far, like " + Math.round(blocks / 100.0) * 100 + " blocks" : "really far, " + Math.round(blocks / 500.0) * 500 + " blocks maybe";
	}
}
