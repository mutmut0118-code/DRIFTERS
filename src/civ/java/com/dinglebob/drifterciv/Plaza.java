package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * A founded town's plaza (milestone 22; the user, 2026-10-28: the leader's old hut torn down, then "well with real water",
 * "lamp posts", "notice board"): once the market stall is up and the leader has moved into its town house and taken its
 * hut down, the town's people build, one after another, a well on the clearest spot nearest the plaza's middle, a notice
 * board beside it facing it, and a lamp post at each corner of the plaza. Each a town build (every block made and placed by
 * one of them); the well then gets its water from one of them with a bucket ({@link TownRoads}' town work). The board's
 * signs say the town's name, when it was founded, its households and people, its leader and its latest news, kept up to
 * date each day.
 */
final class Plaza {
	private Plaza() {
	}

	/** The town build's name for the plaza's pieces. */
	static final String LABEL = "plaza";
	/** The lamp posts this far out from the plaza's middle, each way. */
	private static final int LAMPS_AT = TownRoads.PLAZA - 4;
	/** How far round a spot it looks for ground that suits. */
	private static final int LOOK = 4;
	/** Whose turn it is to fetch the well's water, since when; a turn lasts this long (not saved). */
	private record Turn(UUID carrier, long since) {
	}

	private static final java.util.Map<UUID, Turn> turns = new java.util.HashMap<>();
	private static final long TURN = 20L * 60 * 3;

	/** How far above or below the plaza's middle a piece may stand. */
	private static final int HEIGHT = 5;
	/** The longest the plaza waits for its trees to come down before the well goes up anyway (a tree no one can get at). */
	private static final long CLEARING_AT_MOST = 24000L;
	/** When each town's plaza started waiting for its trees (not saved: it waits again after a restart, a day at most). */
	private static final java.util.Map<UUID, Long> clearing = new java.util.HashMap<>();

	/** A tree's log still standing on the plaza (round the plaza's height). */
	private static boolean treesLeft(MinecraftServer server, Town town) {
		ServerLevel level = server.overworld();
		TownRoads.Rect plaza = TownRoads.plaza(town);
		int y = town.center.getY();
		for (int x = plaza.x0(); x <= plaza.x1(); x++) {
			for (int z = plaza.z0(); z <= plaza.z1(); z++) {
				for (int dy = -4; dy <= 24; dy++) {
					BlockPos pos = new BlockPos(x, y + dy, z);
					if (level.isLoaded(pos) && level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)
						&& level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** The plaza's next piece, or keeps them building the one under way. True if anything changed. */
	static boolean build(MinecraftServer server, Town town, DrifterGroups.Group group) {
		if (town.spawned || !town.stallBuilt) {
			return false;
		}

		DrifterEntity leader = TownFounding.loaded(server, group.leader);
		Home home = leader == null ? null : DrifterApi.home(leader);
		// (Its old hut on the plaza comes down first: it does that itself once it lives in its house.)
		if (leader == null || home == null || home.hutBuilt && !home.hutTakenDown) {
			return false;
		}

		Town.Plot under = town.plaza.stream().filter(plot -> !plot.built).findFirst().orElse(null);
		if (under != null) {
			// Those not on it yet join in, in the same woods as the others.
			Home.Build any = null;
			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				var build = member == null ? null : DrifterApi.townBuildOf(member);
				if (build != null && build.label.equals(LABEL) && build.origin.equals(under.origin)) {
					any = build;
				}
			}

			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member != null && DrifterApi.townBuildOf(member) == null) {
					DrifterApi.joinTownBuild(member, under.planId, under.origin, under.rotation, LABEL, any == null ? java.util.Map.of() : any.woods,
						any != null && any.deepslate);
				}
			}

			return false;
		}

		// Builds of a piece finished while they were away: theirs no more.
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			var build = member == null ? null : DrifterApi.townBuildOf(member);
			if (build != null && build.label.equals(LABEL)) {
				DrifterApi.endTownBuild(member);
			}
		}

		// Its trees down first (its people fell them: the town work's clearOfTrees), a day at most.
		if (town.plaza.isEmpty() && treesLeft(server, town)) {
			long now = server.overworld().getGameTime();
			long since = clearing.computeIfAbsent(town.id, id -> now);
			if (now - since < CLEARING_AT_MOST) {
				return false;
			}
		}

		Town.Plot next = next(server, town, leader);
		if (next == null) {
			if (town.plaza.stream().noneMatch(plot -> plot.planId.equals(DrifterApi.wellPlan()))) {
				DrifterCivilizations.LOGGER.info("[town] {}: no spot for the well yet ({})", town.name, DrifterApi.siteRejections());
			}

			return false;
		}

		town.plaza.add(next);
		List<DrifterEntity> builders = new ArrayList<>();
		builders.add(leader);
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member != leader && DrifterApi.townBuildOf(member) == null) {
				builders.add(member);
			}
		}

		DrifterApi.townBuild(builders, next.planId, next.origin, next.rotation, LABEL);
		String what = next.planId.equals(DrifterApi.wellPlan()) ? "a well" : next.planId.equals(DrifterApi.boardPlan()) ? "a notice board" : "a lamp post";
		if (!next.planId.equals(DrifterApi.lampPlan()) || town.plaza.stream().filter(plot -> plot.planId.equals(DrifterApi.lampPlan())).count() == 1) {
			Chronicle.add(server, "town", "The people of " + town.name + " began building " + (what.equals("a lamp post") ? "lamp posts round the plaza"
				: what + " on the plaza"));
		}

		return true;
	}

	/** The next piece and where (on ground the leader knows that suits it), or null when it's all built or nowhere suits. */
	@Nullable
	private static Town.Plot next(MinecraftServer server, Town town, DrifterEntity leader) {
		int cx = town.center.getX();
		int cz = town.center.getZ();
		Town.Plot well = piece(town, DrifterApi.wellPlan());
		if (well == null) {
			// (Anywhere on the plaza, the nearest the middle first: the leader's old farm may be there.)
			return place(server, town, leader, DrifterApi.wellPlan(), 4, 4, cx - 2, cz - 2, Rotation.NONE, Direction.NORTH, TownRoads.PLAZA - 3);
		}

		if (piece(town, DrifterApi.boardPlan()) == null) {
			// Beside the well, facing it (its signs on the side toward the well), on whichever side suits.
			int wx = well.origin.getX();
			int wz = well.origin.getZ();
			Object[][] sides = {
				{wx, wz + 6, Rotation.NONE, Direction.NORTH}, {wx + 1, wz - 4, Rotation.CLOCKWISE_180, Direction.SOUTH},
				{wx + 6, wz, Rotation.COUNTERCLOCKWISE_90, Direction.WEST}, {wx - 4, wz + 1, Rotation.CLOCKWISE_90, Direction.EAST}};
			for (Object[] side : sides) {
				Rotation rotation = (Rotation)side[2];
				boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
				Town.Plot plot = place(server, town, leader, DrifterApi.boardPlan(), turned ? 2 : 3, turned ? 3 : 2, (int)side[0], (int)side[1], rotation,
					(Direction)side[3], 1);
				if (plot != null) {
					return plot;
				}
			}

			// (Nowhere beside it: on to the lamps.)
			town.plaza.add(skipped(DrifterApi.boardPlan(), Direction.NORTH));
		}

		for (int[] corner : new int[][] {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
			int x = cx + corner[0] * LAMPS_AT;
			int z = cz + corner[1] * LAMPS_AT;
			if (town.plaza.stream().anyMatch(plot -> plot.planId.equals(DrifterApi.lampPlan()) && plot.front == lampCorner(corner))) {
				continue;
			}

			Town.Plot lamp = place(server, town, leader, DrifterApi.lampPlan(), 1, 1, x, z, Rotation.NONE, lampCorner(corner));
			if (lamp == null) {
				// (Nowhere there suits: that corner goes without.)
				town.plaza.add(skipped(DrifterApi.lampPlan(), lampCorner(corner)));
				continue;
			}

			return lamp;
		}

		return null;
	}

	/** Which corner a lamp post is for (kept in its plot's front). */
	private static Direction lampCorner(int[] corner) {
		return corner[0] < 0 ? corner[1] < 0 ? Direction.NORTH : Direction.WEST : corner[1] < 0 ? Direction.EAST : Direction.SOUTH;
	}

	@Nullable
	private static Town.Plot place(MinecraftServer server, Town town, DrifterEntity leader, String planId, int sx, int sz, int x0, int z0, Rotation rotation,
		Direction front) {
		return place(server, town, leader, planId, sx, sz, x0, z0, rotation, front, LOOK);
	}

	/** The nearest spot to this corner (within {@code look}) that suits the piece, clear of the others; or null. */
	@Nullable
	private static Town.Plot place(MinecraftServer server, Town town, DrifterEntity leader, String planId, int sx, int sz, int x0, int z0, Rotation rotation,
		Direction front, int look) {
		var finder = DrifterApi.siteFinder(leader, planId);
		for (int ring = 0; ring <= look; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || clashes(town, x0 + dx, z0 + dz, sx, sz)) {
						continue;
					}

					BlockPos origin = finder.apply(new BlockPos(x0 + dx, 0, z0 + dz), rotation);
					// (On the plaza's ground, not down a hole in it: found in testing, a lamp post at the bottom of a ravine.)
					if (origin != null && Math.abs(origin.getY() - town.center.getY()) <= HEIGHT && untouched(server, origin, sx, sz)) {
						return new Town.Plot(origin, sx, sz, planId, rotation, front, null);
					}
				}
			}
		}

		return null;
	}

	/**
	 * Nothing anyone made in its space, from the ground under it to over its roof (found in testing: a well built round the
	 * leader's old furnace, its floor left with a hole the water ran out of): natural ground, plants and trees only.
	 */
	private static boolean untouched(MinecraftServer server, BlockPos origin, int sx, int sz) {
		ServerLevel level = server.overworld();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(sx, 5, sz))) {
			var state = level.getBlockState(pos);
			if (!(state.isAir() || state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)
				|| state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SAND) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(net.minecraft.world.level.block.Blocks.GRAVEL) || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
				|| state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || state.is(net.minecraft.tags.BlockTags.FLOWERS))) {
				return false;
			}
		}

		return true;
	}

	/** Over another piece of the plaza (with a block between), or off the plaza. */
	private static boolean clashes(Town town, int x, int z, int sx, int sz) {
		TownRoads.Rect plaza = TownRoads.plaza(town);
		if (x < plaza.x0() || z < plaza.z0() || x + sx - 1 > plaza.x1() || z + sz - 1 > plaza.z1()) {
			return true;
		}

		for (Town.Plot plot : town.plaza) {
			if (plot.sizeX > 0 && x <= plot.origin.getX() + plot.sizeX && plot.origin.getX() <= x + sx && z <= plot.origin.getZ() + plot.sizeZ
				&& plot.origin.getZ() <= z + sz) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static Town.Plot piece(Town town, String planId) {
		return town.plaza.stream().filter(plot -> plot.planId.equals(planId)).findFirst().orElse(null);
	}

	/** A piece it went without (nowhere suited): marked done, so it's not looked for again. */
	private static Town.Plot skipped(String planId, Direction front) {
		Town.Plot none = new Town.Plot(BlockPos.ZERO, 0, 0, planId, Rotation.NONE, front, null);
		none.built = true;
		return none;
	}

	/** A piece is up. */
	static void built(MinecraftServer server, Town town, Town.Plot plot) {
		plot.built = true;
		if (plot.planId.equals(DrifterApi.wellPlan())) {
			town.well = plot.origin;
			Chronicle.add(server, "town", "The well of " + town.name + " was finished");
		} else if (plot.planId.equals(DrifterApi.boardPlan())) {
			Chronicle.add(server, "town", "The notice board of " + town.name + " was put up");
			town.boardDay = -1;
			board(server, town);
		} else if (town.plaza.stream().filter(one -> one.planId.equals(DrifterApi.lampPlan())).count() == 4
			&& town.plaza.stream().filter(one -> one.planId.equals(DrifterApi.lampPlan())).allMatch(one -> one.built)) {
			Chronicle.add(server, "town", "The plaza of " + town.name + " has its lamp posts");
		}

		Towns.save(server);
	}

	// ---------------------------------------------------------------------------------------------
	// The well's water
	// ---------------------------------------------------------------------------------------------

	/**
	 * The well's cells still wanting water, for the one of its people who fetches it: the leader if it has a bucket, else the
	 * first of them who has one (the others go on with their day). Empty for the rest, or once it has its water.
	 */
	static List<BlockPos> water(MinecraftServer server, Town town, DrifterEntity drifter) {
		Town.Plot well = piece(town, DrifterApi.wellPlan());
		if (well == null || !well.built || well.sizeX == 0 || town.wellFilled) {
			return List.of();
		}

		ServerLevel level = (ServerLevel)drifter.level();
		List<BlockPos> cells = DrifterApi.wellWater(server, well.origin, well.rotation);
		if (cells.stream().allMatch(cell -> level.getFluidState(cell).isSource())) {
			town.wellFilled = true;
			Chronicle.add(server, "town", "The well of " + town.name + " has water");
			Towns.save(server);
			return List.of();
		}

		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group == null) {
			return List.of();
		}

		// Those with a bucket, the nearest the well first; each has a turn of a few minutes, then the next (found in testing: the
		// nearest one couldn't get to the well, again and again, and nobody else was asked).
		List<DrifterEntity> buckets = new ArrayList<>();
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.level() == level && member.getInventory().count(stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)) > 0) {
				buckets.add(member);
			}
		}

		if (buckets.isEmpty()) {
			return List.of();
		}

		buckets.sort(java.util.Comparator.comparingDouble(member -> member.distanceToSqr(well.origin.getCenter())));
		long now = level.getGameTime();
		Turn turn = turns.get(town.id);
		int at = turn == null ? -1 : buckets.stream().map(DrifterEntity::getUUID).toList().indexOf(turn.carrier());
		// (Not while it's at it: found in testing, a turn ran out halfway back from the lake. Water in its bucket for its own
		// farm doesn't count.)
		if (at >= 0) {
			var goal = buckets.get(at).getDrifterBrain().currentGoal();
			if (goal != null && goal.id().equals("town_water")) {
				turn = new Turn(turn.carrier(), now);
				turns.put(town.id, turn);
			}
		}

		if (at < 0 || now - turn.since() > TURN) {
			DrifterEntity next = buckets.get(at < 0 ? 0 : (at + 1) % buckets.size());
			turn = new Turn(next.getUUID(), now);
			turns.put(town.id, turn);
		}

		return turn.carrier().equals(drifter.getUUID()) ? cells.stream().filter(cell -> !level.getFluidState(cell).isSource()).toList() : List.of();
	}

	// ---------------------------------------------------------------------------------------------
	// The notice board
	// ---------------------------------------------------------------------------------------------

	/** Its signs say how the town is (once a day, and when it's put up). */
	static void board(MinecraftServer server, Town town) {
		Town.Plot board = piece(town, DrifterApi.boardPlan());
		long day = Chronicle.day(server);
		if (board == null || !board.built || board.sizeX == 0 || town.boardDay == day) {
			return;
		}

		ServerLevel level = server.overworld();
		List<BlockPos> signs = DrifterApi.boardSigns(server, board.origin, board.rotation);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		int people = group == null ? 0 : group.members.size();
		long households = town.plots.stream().filter(plot -> plot.built).count();
		String leader = group == null ? "" : group.names.getOrDefault(town.leader != null ? town.leader : group.leader, "");
		List<String> news = latest(town);
		String[][] text = {
			{"Welcome to", town.name, "est. day " + town.foundedDay, ""},
			{households + (households == 1 ? " home, " : " homes, ") + people + " ppl", "Leader:", leader, Politics.lawsShort(town)},
			{"News:", news.size() > 0 ? news.get(0) : "all quiet", news.size() > 1 ? news.get(1) : "", news.size() > 2 ? news.get(2) : ""}};
		for (int i = 0; i < signs.size() && i < text.length; i++) {
			if (level.getBlockEntity(signs.get(i)) instanceof SignBlockEntity sign) {
				SignText lines = new SignText();
				for (int line = 0; line < 4; line++) {
					lines = lines.setMessage(line, Component.literal(text[i][line]));
				}

				sign.setText(lines, true);
				sign.setChanged();
				level.sendBlockUpdated(signs.get(i), sign.getBlockState(), sign.getBlockState(), 3);
			}
		}

		town.boardDay = day;
	}

	/** The town's latest news, in sign-sized pieces (15 letters a line, three lines). */
	private static List<String> latest(Town town) {
		List<Chronicle.Entry> all = Chronicle.all();
		for (int i = all.size() - 1; i >= 0; i--) {
			String text = all.get(i).text();
			if (text.contains(town.name)) {
				String said = "Day " + all.get(i).day() + ": " + text.replace("The people of " + town.name + " ", "").replace(" of " + town.name, "")
					.replace(" in " + town.name, "");
				List<String> lines = new ArrayList<>();
				StringBuilder line = new StringBuilder();
				for (String word : said.split(" ")) {
					if (line.length() + word.length() + 1 > 15 && line.length() > 0) {
						lines.add(line.toString());
						line = new StringBuilder();
					}

					line.append(line.length() > 0 ? " " : "").append(word);
				}

				lines.add(line.toString());
				return lines.subList(0, Math.min(3, lines.size()));
			}
		}

		return List.of();
	}
}
