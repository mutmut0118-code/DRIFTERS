package com.dinglebob.drifterciv;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;

/**
 * A town's culture (milestone 23, docs/LIVED_WORLD.md C6, CIVILIZATION.md part 5 folded in):
 * <ul>
 *   <li><b>Its calendar:</b> a town year of {@link #YEAR} days from the day it was founded (its past: W6); <b>Founding
 *       Day</b> on each anniversary, a <b>harvest festival</b> half a year after. On the day its leader calls it out, the
 *       chronicle has it, work's put down a little, people gather at the plaza (the {@code festival} whims: celebrating,
 *       ringing the bell, a feast), and talk of it.</li>
 *   <li><b>A shrine</b> in some towns (a little build of stone, candles and flowers, made with the town): a few of its
 *       people keep to it, now and then going to sit by it a while. Never preaching at anyone.</li>
 *   <li><b>Its character</b> from its people (their natures, on average) and its past: a friendly market town, a rough
 *       town, a hard-working town, a pious town...: in {@code /drifterciv town} and its people's talk.</li>
 * </ul>
 */
final class TownCulture {
	private TownCulture() {
	}

	/** A town's year, in game days. */
	static final int YEAR = 60;
	private static final Set<String> WORK = Set.of("farm", "pen", "wares", "roads", "town_water", "clear_trees", "ease_ground", "light", "townbuild",
		"trade_run", "checkchest");

	/** Its festival today ("founding", "harvest"), or null. */
	@Nullable
	static String festival(Town town, long day) {
		long age = day - town.bornDay;
		if (age <= 0) {
			return null;
		}

		long of = Math.floorMod(age, YEAR);
		return of == 0 ? "founding" : of == YEAR / 2 ? "harvest" : null;
	}

	/** What it's called. */
	static String name(String festival) {
		return festival.equals("founding") ? "Founding Day" : "the harvest festival";
	}

	/** On a festival day, work counts less, ease more. */
	static double leaning(Town town, DrifterEntity drifter, String goal, long day) {
		if (festival(town, day) == null) {
			return 0.0;
		}

		return WORK.contains(goal) ? -0.15 : goal.equals("whim") ? 0.12 : goal.equals("converse") ? 0.1 : 0.0;
	}

	/** Each check: a festival's morning, called out; a monument due, begun, built. */
	static void check(MinecraftServer server, Town town) {
		if (!town.spawned || town.group == null || server.overworld() == null) {
			return;
		}

		monuments(server, town);

		long day = Chronicle.day(server);
		long time = server.overworld().getDayTime() % 24000L;
		String festival = festival(town, day);
		if (festival == null || time < 1000L || time > 4000L || town.festivalDay >= day) {
			return;
		}

		town.festivalDay = day;
		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		long years = (day - town.bornDay) / YEAR;
		String text = festival.equals("founding") ? "It's Founding Day in " + town.name + ": " + years + (years == 1 ? " year" : " years")
			+ " since it was founded" : "The harvest festival in " + town.name;
		Chronicle.add(server, "festival", text);
		UUID leader = town.leader != null ? town.leader : group == null ? null : group.leader;
		DrifterEntity speaker = leader == null ? null : TownFounding.loaded(server, leader);
		if (speaker != null) {
			String[] lines = festival.equals("founding") ? new String[] {"happy founding day everyone!", "its founding day! " + years + " years of " + town.name,
				"founding day, everyone to the plaza!", "happy founding day " + town.name} : new String[] {"harvest festival today!",
				"happy harvest everyone", "harvest festival, food at the plaza!", "good harvest this year, lets celebrate"};
			com.dinglebob.drifter.social.ChatLines.speak(speaker, lines[speaker.getRandom().nextInt(lines.length)]);
		}

		DrifterCivilizations.LOGGER.info("[culture] {}", text);
		Towns.save(server);
	}

	/** Where its festival is (the plaza), on a festival day; else null. */
	@Nullable
	static BlockPos festivalSpot(Town town, DrifterEntity drifter) {
		long day = drifter.getServer() == null ? 0 : Chronicle.day(drifter.getServer());
		return festival(town, day) != null ? TownDay.place(town, drifter, "plaza") : null;
	}

	/** Where its shrine is (a spot by it), or null if it has none. */
	@Nullable
	static BlockPos shrineSpot(Town town, DrifterEntity drifter) {
		if (town.shrine == null) {
			return null;
		}

		Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(drifter.getRandom());
		BlockPos spot = town.shrine.relative(side, 2);
		var level = drifter.level();
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
		return Math.abs(y - spot.getY()) <= 2 ? new BlockPos(spot.getX(), y, spot.getZ()) : null;
	}

	// ---------------------------------------------------------------------------------------------
	// Monuments
	// ---------------------------------------------------------------------------------------------

	/** Days at least between a town's monuments. */
	private static final int MONUMENT_EVERY = 30;
	/** The monument build's name. */
	static final String MONUMENT = "monument";

	/** It won a war (Diplomacy's peace, the other paying): a monument to it, if none's due. */
	static void won(Town town, Town over) {
		if (town.spawned && town.monumentFor == null) {
			town.monumentFor = "Victory|over|" + over.name + "|";
		}
	}

	/** Its leader gone for good, a monument due; one due begun, its builders kept at it, finished. */
	private static void monuments(MinecraftServer server, Town town) {
		long day = Chronicle.day(server);
		// A leader it knew, gone for good: in their memory.
		if (town.leader != null && !town.leader.equals(town.knownLeader)) {
			var was = town.knownLeader == null ? null : DrifterApi.record(town.knownLeader);
			if (was != null && was.status() == com.dinglebob.drifter.api.DrifterRecord.Status.GONE && town.monumentFor == null) {
				town.monumentFor = "In memory of|" + was.name() + "|who led|" + town.name;
			}

			town.knownLeader = town.leader;
		}

		DrifterGroups.Group group = DrifterApi.groupById(town.group);
		if (group == null) {
			return;
		}

		net.minecraft.server.level.ServerLevel level = server.overworld();
		if (town.monument == null) {
			if (town.monumentFor == null || day - town.monumentDay < MONUMENT_EVERY || !level.isLoaded(town.center)) {
				return;
			}

			Town.Plot spot = monumentSpot(level, town);
			if (spot == null) {
				return;
			}

			java.util.List<DrifterEntity> builders = new java.util.ArrayList<>();
			for (UUID id : group.members) {
				DrifterEntity one = TownFounding.loaded(server, id);
				if (one != null && DrifterApi.townBuildOf(one) == null) {
					builders.add(one);
				}
			}

			if (builders.isEmpty()) {
				return;
			}

			town.monument = spot;
			DrifterApi.townBuild(builders, spot.planId, spot.origin, spot.rotation, MONUMENT);
			Chronicle.add(server, "town", "The people of " + town.name + " began a monument on the plaza: " + town.monumentFor.replace('|', ' ').trim());
			DrifterCivilizations.LOGGER.info("[culture] {}: a monument begun at {} ({} building)", town.name, spot.origin.toShortString(), builders.size());
			Towns.save(server);
			return;
		}

		Town.Plot spot = town.monument;
		if (!level.isLoaded(spot.origin)) {
			return;
		}

		int[] missing = DrifterApi.missingByLayer(level, spot.planId, spot.origin, spot.rotation);
		int left = missing == null ? 0 : java.util.Arrays.stream(missing).sum();
		if (left > 0) {
			// Those not on it yet join in.
			for (UUID id : group.members) {
				DrifterEntity one = TownFounding.loaded(server, id);
				if (one != null && DrifterApi.townBuildOf(one) == null) {
					DrifterApi.joinTownBuild(one, spot.planId, spot.origin, spot.rotation, MONUMENT, java.util.Map.of(), false);
				}
			}

			return;
		}

		// Up: its sign says what it's for; its builders done.
		BlockPos signAt = DrifterApi.monumentSign(spot.origin, spot.rotation);
		String[] lines = (town.monumentFor == null ? "||" : town.monumentFor).split("\\|", -1);
		if (level.getBlockEntity(signAt) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
			var text = new net.minecraft.world.level.block.entity.SignText();
			for (int line = 0; line < 4; line++) {
				text = text.setMessage(line, net.minecraft.network.chat.Component.literal(line < lines.length ? lines[line] : ""));
			}

			sign.setText(text, true);
			sign.setChanged();
			level.sendBlockUpdated(signAt, sign.getBlockState(), sign.getBlockState(), 3);
		}

		for (UUID id : group.members) {
			DrifterEntity one = TownFounding.loaded(server, id);
			var build = one == null ? null : DrifterApi.townBuildOf(one);
			if (build != null && build.label.equals(MONUMENT)) {
				DrifterApi.endTownBuild(one);
			}
		}

		Chronicle.add(server, "town", "The monument on " + town.name + "'s plaza was finished: " + (town.monumentFor == null ? "" : town.monumentFor.replace('|', ' ')
			.trim()));
		DrifterCivilizations.LOGGER.info("[culture] {}: its monument finished", town.name);
		town.monument = null;
		town.monumentFor = null;
		town.monumentDay = day;
		Towns.save(server);
	}

	/**
	 * A spot for a monument: on the plaza or by it (rings out from its middle, the nearest first), 3 by 3 with a block clear
	 * round it, all of it level, within two of the plaza's height, nothing built on it, no road on it; turned with its sign
	 * to the plaza's middle. Null if there's none (a crowded plaza in a valley).
	 */
	@Nullable
	private static Town.Plot monumentSpot(net.minecraft.server.level.ServerLevel level, Town town) {
		BlockPos c = town.center;
		java.util.Set<Long> roads = new java.util.HashSet<>();
		for (long column : town.roadColumns) {
			roads.add(column);
		}

		for (int ring = 4; ring <= 16; ring++) {
			for (int k = 0; k < ring * 8; k++) {
				double angle = 2 * Math.PI * k / (ring * 8);
				int mx = c.getX() + (int)Math.round(Math.cos(angle) * ring);
				int mz = c.getZ() + (int)Math.round(Math.sin(angle) * ring);
				Integer at = level(level, roads, mx, mz, c.getY());
				if (at == null) {
					continue;
				}

				// (Its sign to the plaza's middle: the plan's sign faces north before it's turned.)
				Direction toward = Direction.getNearest(c.getX() - mx, 0, c.getZ() - mz);
				net.minecraft.world.level.block.Rotation turn = switch (toward) {
					case SOUTH -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
					case EAST -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
					case WEST -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
					default -> net.minecraft.world.level.block.Rotation.NONE;
				};
				return new Town.Plot(new BlockPos(mx - 1, at, mz - 1), 3, 3, DrifterApi.monumentPlan(), turn, Direction.NORTH, null);
			}
		}

		return null;
	}

	/** The height to stand a monument at, its middle here (its 5 by 5 level, near the plaza's height, clear), or null. */
	@Nullable
	private static Integer level(net.minecraft.server.level.ServerLevel level, java.util.Set<Long> roads, int mx, int mz, int plaza) {
		Integer height = null;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int x = mx + dx;
				int z = mz + dz;
				if (!level.hasChunkAt(new BlockPos(x, 0, z)) || roads.contains(BlockPos.asLong(x, 0, z)) || TownBuilder.built(level, x, z) && !paving(level, x,
					z)) {
					return null;
				}

				int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos under = new BlockPos(x, top - 1, z);
				if (height == null) {
					height = top;
				}

				if (top != height || Math.abs(top - plaza) > 2 || !level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)
					|| !level.getFluidState(under).isEmpty() || !level.getBlockState(under.above()).isAir()) {
					return null;
				}
			}
		}

		return height;
	}

	/** The plaza's own paving here (counts as built, but a monument may stand on it). */
	private static boolean paving(net.minecraft.server.level.ServerLevel level, int x, int z) {
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		var state = level.getBlockState(new BlockPos(x, top - 1, z));
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.contains("stone_bricks") || id.contains("cobblestone") || id.equals("gravel") || id.contains("sandstone") || id.contains("path")
			|| id.contains("bricks");
	}

	// ---------------------------------------------------------------------------------------------
	// Its character
	// ---------------------------------------------------------------------------------------------

	/** Its character, from its people's natures on average and what it has (a stall, a shrine): a few words. */
	static String character(MinecraftServer server, Town town) {
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return "a quiet town";
		}

		double social = 0;
		double aggression = 0;
		double industry = 0;
		double greed = 0;
		double patience = 0;
		int n = 0;
		// (Everyone's, loaded or not, as last seen: what's loaded changes as people come and go, and the town with it.)
		for (UUID id : group.members) {
			var record = DrifterApi.record(id);
			if (record != null && record.traits() != null && !record.traits().isEmpty()) {
				var t = record.traits();
				social += t.getOrDefault("sociability", 0.5);
				aggression += t.getOrDefault("aggression", 0.5);
				industry += t.getOrDefault("industriousness", 0.5);
				greed += t.getOrDefault("greed", 0.5);
				patience += t.getOrDefault("patience", 0.5) + t.getOrDefault("forgiveness", 0.5);
				n++;
			}
		}

		if (n == 0) {
			return town.character != null ? town.character : "a quiet town";
		}

		social /= n;
		aggression /= n;
		industry /= n;
		greed /= n;
		patience /= 2 * n;
		String kind = aggression > 0.56 ? "a rough town" : town.shrine != null && patience > 0.53 ? "a pious town" : social > 0.55 && town.stallBuilt
			? "a friendly market town" : social > 0.55 ? "a friendly town" : industry > 0.55 ? "a hard-working town" : greed > 0.56 ? "a town that likes its money"
			: town.byWater ? "a fishing town" : "a quiet town";
		town.character = kind;
		return kind;
	}

	/** Its people's talk: a festival today, its character. */
	static void talk(Town town, long day, Map<String, Object> words, Set<String> flags) {
		String festival = festival(town, day);
		if (festival != null) {
			flags.add("festival_day");
			words.put("festival", name(festival));
		}

		if (town.character != null) {
			flags.add("character_" + town.character.replace("a ", "").replace(' ', '_'));
		}
	}
}
