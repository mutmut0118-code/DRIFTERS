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
		return switch (part) {
			case DAWN -> goal.equals("eat") || goal.equals("checkchest") || goal.equals("farm") ? 0.05 : 0.0;
			case MORNING -> work ? 0.05 : 0.0;
			case MIDDAY -> ease ? 0.10 : work ? -0.04 : 0.0;
			case AFTERNOON -> goal.equals("trade_run") ? 0.08 : work ? 0.04 : 0.0;
			case DUSK -> ease ? 0.08 : work ? -0.08 : 0.0;
			case NIGHT -> 0.0;
		};
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
