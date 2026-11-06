package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A townsperson's day and the places to be (milestone 23, docs/LIVED_WORLD.md C1 and C2: "a town you can watch for a day
 * and see a day go by"). Nobody's day is fixed: what it chooses goes by the same goal scoring as ever, with a light pull
 * by the time of its own day (its habit: an early riser's day runs ahead, a night owl's behind; {@link DrifterApi#ownTime}):
 * <ul>
 *   <li>first light: up, a bite, a look in its chests and at the fields;</li>
 *   <li>the morning and the afternoon: its line of work, trade runs in the afternoon;</li>
 *   <li>the middle of the day: a break, the plaza and the well, a chat (the town's whims: {@code drifter_whims/town.json});</li>
 *   <li>the evening: work put down, the gathering fire, a chat;</li>
 *   <li>the night: in, as before (a few out hunting, the guards on watch).</li>
 * </ul>
 * And where its places are, for those whims ({@link #place}): the plaza, the well, the fire, the board, the bell, the stall,
 * the store, the fields; a spot round each, a different one for each of its people.
 */
final class TownDay {
	private TownDay() {
	}

	/** The town's work, whatever line: these count a little more in the working hours, less in the evening. */
	private static final Set<String> WORK = Set.of("farm", "pen", "wares", "roads", "town_water", "clear_trees", "ease_ground", "light", "store",
		"townbuild", "stall", "trade_run", "fish", "checkchest");
	/** Its ease: a whim, a chat. */
	private static final Set<String> EASE = Set.of("whim", "converse");
	/** Going out: exploring, delving, adventures. */
	private static final Set<String> OUT = Set.of("explore", "delve", "passthrough", "pursuit", "visit", "netherportal", "build_portal");
	/** Cooking. */
	private static final Set<String> COOKING = Set.of("cook", "cookmeal");

	/** A seasoned one: far along (iron at least) and wearing iron or better on its body. */
	static boolean veteran(DrifterEntity drifter) {
		// (An iron chestplate's 6 armor points, or better.)
		var chest = drifter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		boolean armored = chest.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getDefense() >= 6;
		return armored && com.dinglebob.drifter.brain.Strategist.stage(drifter) >= 5;
	}

	/** At (or near) its home. */
	static boolean atHome(DrifterEntity drifter, double near) {
		var home = drifter.getDrifterBrain().home();
		return home != null && home.center.closerThan(drifter.blockPosition(), near);
	}

	/** Parts of its own day. */
	enum Part {
		DAWN, MORNING, MIDDAY, AFTERNOON, DUSK, NIGHT
	}

	static Part part(long own) {
		if (own >= 23000L || own < 1000L) {
			return Part.DAWN;
		}

		if (own < 5000L) {
			return Part.MORNING;
		}

		if (own < 7500L) {
			return Part.MIDDAY;
		}

		if (own < 11000L) {
			return Part.AFTERNOON;
		}

		return own < 13000L ? Part.DUSK : Part.NIGHT;
	}

	/** How much more (or less) this goal counts for it now, by the time of its day. */
	static double leaning(Town town, DrifterEntity drifter, String goal) {
		Part part = part(DrifterApi.ownTime(drifter));
		boolean work = WORK.contains(goal) || Trades.leaning(town, drifter, goal) > 0.0;
		boolean ease = EASE.contains(goal);
		// Who it is (the user, 2026-11-06: "normal citizens ... doing home activities. while the geared up and more experienced
		// drifters set out and explore and run around"): a seasoned one, well geared, is drawn out; the rest keep to the town.
		double out = OUT.contains(goal) ? veteran(drifter) ? 0.12 : -0.08 : 0.0;
		// Its meals at home of a morning and an evening (cooking what it has, by the stove: the user, "would kill to see a
		// drifter cooking in their home").
		double kitchen = COOKING.contains(goal) && (part == Part.DAWN || part == Part.MORNING || part == Part.DUSK) && atHome(drifter, 24.0) ? 0.15 : 0.0;
		return out + kitchen + switch (part) {
			case DAWN -> goal.equals("eat") || goal.equals("checkchest") || goal.equals("farm") ? 0.05 : 0.0;
			case MORNING -> work ? 0.05 : 0.0;
			case MIDDAY -> ease ? 0.10 : work ? -0.04 : 0.0;
			case AFTERNOON -> goal.equals("trade_run") ? 0.08 : work ? 0.04 : 0.0;
			case DUSK -> ease ? 0.08 : work ? -0.08 : 0.0;
			case NIGHT -> 0.0;
		};
	}

	/**
	 * How soon its next whim may come, as a share of the usual wait: a festival day, much sooner (a crowd at the plaza all
	 * day); a market day, sooner; the middle of the day and the evening (the gatherings), a little sooner.
	 */
	static double whimPace(Town town, DrifterEntity drifter, long day) {
		if (TownCulture.festival(town, day) != null) {
			return 0.25;
		}

		// (A townsperson's whims come about twice as often as a wanderer's: the user, 2026-11-06, "would love for normal
		// citizens to have a higher chance of whims"; a seasoned one, out and about, less so.)
		double who = veteran(drifter) || "guard".equals(town.lines.get(drifter.getUUID())) ? 0.9 : 0.5;
		Part part = part(DrifterApi.ownTime(drifter));
		boolean gathering = part == Part.MIDDAY || part == Part.DUSK;
		if (TownEvents.marketDay(town, day)) {
			return who * (gathering ? 0.6 : 0.8);
		}

		return who * (gathering ? 0.7 : 1.0);
	}

	/** What part of its day it's in, for /drifterciv town and its thoughts. */
	static String describe(DrifterEntity drifter) {
		return part(DrifterApi.ownTime(drifter)).name().toLowerCase(java.util.Locale.ROOT);
	}

	// ---------------------------------------------------------------------------------------------
	// Places to be
	// ---------------------------------------------------------------------------------------------

	/** A spot to stand at by one of its town's places (see {@link com.dinglebob.drifter.api.TownWork#place}), or null. */
	@Nullable
	static BlockPos place(Town town, DrifterEntity drifter, String kind) {
		Level level = drifter.level();
		List<BlockPos> spots = new ArrayList<>();
		BlockPos c = town.center;
		switch (kind) {
			case "plaza" -> {
				// Anywhere round the well, a few blocks out.
				for (int dx = -6; dx <= 6; dx += 2) {
					for (int dz = -6; dz <= 6; dz += 2) {
						int d = Math.max(Math.abs(dx), Math.abs(dz));
						if (d >= 3 && d <= 6) {
							spots.add(c.offset(dx, 0, dz));
						}
					}
				}
			}
			case "well" -> {
				BlockPos well = town.well != null ? town.well : c;
				for (Direction side : Direction.Plane.HORIZONTAL) {
					spots.add(well.relative(side, 2));
				}
			}
			case "fire" -> {
				if (town.fire == null) {
					return null;
				}

				// (Round it, off its two logs: they lie 2 out east or west, and 2 out north or south, of it.)
				int sx = town.fire.getX() >= c.getX() ? 1 : -1;
				int sz = town.fire.getZ() >= c.getZ() ? 1 : -1;
				spots.add(town.fire.offset(-2 * sx, 0, 0));
				spots.add(town.fire.offset(0, 0, -2 * sz));
				spots.add(town.fire.offset(-2 * sx, 0, -sz));
				spots.add(town.fire.offset(-sx, 0, -2 * sz));
				spots.add(town.fire.offset(2 * sx, 0, sz));
				spots.add(town.fire.offset(sx, 0, 2 * sz));
			}
			case "market" -> {
				return TownEvents.market(town, drifter);
			}
			case "festival" -> {
				return TownCulture.festivalSpot(town, drifter);
			}
			case "shrine" -> {
				return TownCulture.shrineSpot(town, drifter);
			}
			case "home" -> {
				// Inside its house: by its bed, or its home's middle.
				var home = drifter.getDrifterBrain().home();
				if (home == null || !home.center.closerThan(drifter.blockPosition(), 160.0)) {
					return null;
				}

				BlockPos bed = home.houseBed(level);
				for (BlockPos at : bed != null ? List.of(home.center, bed) : List.of(home.center)) {
					BlockPos in = indoors(level, at);
					if (in != null) {
						return in;
					}
				}

				return null;
			}
			case "kitchen" -> {
				// By its stove, furnace or smoker at home (a campfire, a cooking pot), a spot to stand at in front of it.
				var home = drifter.getDrifterBrain().home();
				if (home == null || !home.center.closerThan(drifter.blockPosition(), 160.0) || !level.isLoaded(home.center)) {
					return null;
				}

				for (BlockPos pos : BlockPos.betweenClosed(home.center.offset(-7, -2, -7), home.center.offset(7, 3, 7))) {
					var state = level.getBlockState(pos);
					String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
					if (KITCHEN.contains(id)) {
						for (Direction side : Direction.Plane.HORIZONTAL) {
							BlockPos in = indoors(level, pos.relative(side));
							if (in != null && in.getY() == pos.getY()) {
								return in;
							}
						}
					}
				}

				return null;
			}
			case "board" -> spots.add(c.offset(-5, 0, -4));
			case "bell" -> spots.add(c.offset(5, 0, -4));
			case "stall" -> {
				if (town.stallPlot == null || !town.stallBuilt) {
					return null;
				}

				Town.Plot plot = town.stallPlot;
				BlockPos middle = plot.origin.offset(plot.sizeX / 2, 0, plot.sizeZ / 2);
				int reach = Math.max(plot.sizeX, plot.sizeZ) / 2 + 2;
				spots.add(middle.relative(plot.front, reach));
				spots.add(middle.relative(plot.front, reach).relative(plot.front.getClockWise()));
				spots.add(middle.relative(plot.front, reach).relative(plot.front.getCounterClockWise()));
			}
			case "store" -> {
				for (BlockPos chest : town.store) {
					for (Direction side : Direction.Plane.HORIZONTAL) {
						spots.add(chest.relative(side));
					}
				}
			}
			case "fields" -> {
				for (BlockPos field : town.fields) {
					for (Direction side : Direction.Plane.HORIZONTAL) {
						spots.add(field.relative(side, 5));
					}
				}
			}
			default -> {
				return null;
			}
		}

		if (spots.isEmpty()) {
			return null;
		}

		// A different one for each of its people (and each day), the first one it can stand at from there.
		int start = Math.floorMod(drifter.getUUID().hashCode() + (int)(level.getDayTime() / 24000L) * 31, spots.size());
		for (int i = 0; i < spots.size(); i++) {
			BlockPos spot = stand(level, spots.get((start + i) % spots.size()));
			if (spot != null) {
				return spot;
			}
		}

		return null;
	}

	/** What a kitchen's cooked on. */
	private static final Set<String> KITCHEN = Set.of("minecraft:furnace", "minecraft:smoker", "minecraft:campfire", "farmersdelight:stove",
		"farmersdelight:cooking_pot", "farmersdelight:skillet");

	/** A spot to stand at here or a step off it, under a roof or not (floor under, room for one), if it's loaded; else null. */
	@Nullable
	private static BlockPos indoors(Level level, BlockPos at) {
		if (!level.isLoaded(at)) {
			return null;
		}

		for (int dy = 0; dy >= -1; dy--) {
			for (Direction side : new Direction[] {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				BlockPos feet = (side == null ? at : at.relative(side)).above(dy);
				var under = level.getBlockState(feet.below());
				if (under.isFaceSturdy(level, feet.below(), Direction.UP) && under.getFluidState().isEmpty() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
					return feet;
				}
			}
		}

		return null;
	}

	/** Where one stands in this column (on its ground, room for one), if it's loaded and open; else null. */
	@Nullable
	private static BlockPos stand(Level level, BlockPos column) {
		if (!level.hasChunkAt(column)) {
			return null;
		}

		BlockPos feet = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
		// (Not on the roof of something: within a few blocks of the place's own height.)
		if (Math.abs(feet.getY() - column.getY()) > 3) {
			return null;
		}

		var under = level.getBlockState(feet.below());
		if (!under.isFaceSturdy(level, feet.below(), Direction.UP) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
			|| !under.getFluidState().isEmpty()) {
			return null;
		}

		return feet;
	}
}
