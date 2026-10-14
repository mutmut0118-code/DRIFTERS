package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.TerrainKnowledge;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Choosing where to settle (DESIGN.md section 11, "Site selection"), from what it has seen only: flat dry ground for a
 * small house, open sky, water within about 16 blocks, trees and stone nearby, not too far from where it is. Personality
 * tilts it: Builders like a view (higher ground), cautious ones like it close to stone they can dig into.
 * TODO(milestone 8 later steps): defensibility (cliffs, hillsides), biome preference, distance from other bases,
 * moving into an empty village house.
 */
public final class HomeSite {
	/** How far around it looks for a site (blocks it has seen). */
	public static final int SEARCH_RADIUS = 32;
	/** The footprint it wants flat: 7x7 (a 5x5 hut plus a ring around it). */
	public static final int HALF_FOOTPRINT = 3;
	/** Below this share of flat, dry, standable cells in the footprint, a spot isn't a site. */
	public static final double MIN_FLATNESS = 0.6;
	/** After looking around a while (mountains, forests), it settles for bumpier ground (it can level it later). */
	public static final double FALLBACK_FLATNESS = 0.4;
	/** Candidates are sampled on a grid this coarse (cheaper, hardly different). */
	private static final int GRID = 2;

	public record Site(BlockPos pos, double score, String why) {
	}

	private HomeSite() {
	}

	@Nullable
	public static Site choose(DrifterEntity drifter) {
		return choose(drifter, MIN_FLATNESS);
	}

	@Nullable
	public static Site choose(DrifterEntity drifter, double minFlatness) {
		Level level = drifter.level();
		TerrainKnowledge terrain = drifter.getSenses().terrain();
		BlockPos origin = drifter.blockPosition();
		List<BlockPos> water = new ArrayList<>();
		List<BlockPos> logs = new ArrayList<>();
		List<BlockPos> stone = new ArrayList<>();
		List<BlockPos> floors = new ArrayList<>();
		terrain.forEachKnown(origin, SEARCH_RADIUS, pos -> {
			if (!level.isLoaded(pos)) {
				return;
			}

			BlockState state = level.getBlockState(pos);
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				water.add(pos.immutable());
			} else if (state.is(BlockTags.LOGS)) {
				logs.add(pos.immutable());
			} else if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
				stone.add(pos.immutable());
			}

			if (Math.floorMod(pos.getX(), GRID) == 0 && Math.floorMod(pos.getZ(), GRID) == 0 && KnownBlocks.isStandable(level, pos.above())
				&& level.canSeeSky(pos.above()) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
				floors.add(pos.above().immutable());
			}
		});

		double creativity = drifter.getPersonality().get(Trait.CREATIVITY);
		double caution = drifter.getPersonality().get(Trait.CAUTION);
		Site best = null;
		for (BlockPos spot : floors) {
			double flat = flatness(level, terrain, spot);
			if (flat < minFlatness) {
				continue;
			}

			double score = flat;
			StringBuilder why = new StringBuilder(String.format(Locale.ROOT, "flat %.0f%%", flat * 100.0));
			if (nearest(water, spot) <= 16.0) {
				score += 0.25;
				why.append(", water nearby");
			}

			if (nearest(logs, spot) <= 24.0) {
				score += 0.25;
				why.append(", trees");
			}

			if (nearest(stone, spot) <= 24.0) {
				score += 0.15 + 0.15 * caution;
				why.append(", stone");
			}

			// Builders like a view: a little higher than where it stands now is better.
			score += 0.02 * creativity * Math.max(-5, Math.min(10, spot.getY() - origin.getY()));
			score -= 0.2 * Math.sqrt(spot.distSqr(origin)) / SEARCH_RADIUS;
			score += drifter.getRandom().nextDouble() * 0.05;
			if (best == null || score > best.score()) {
				best = new Site(spot, score, why.toString());
			}
		}

		return best;
	}

	/**
	 * Share of the 7x7 footprint that is dry standable ground it has seen at this height; ground one block higher or
	 * lower counts half (a player would just level it).
	 */
	public static double flatness(Level level, TerrainKnowledge terrain, BlockPos spot) {
		double good = 0.0;
		int total = 0;
		for (int dx = -HALF_FOOTPRINT; dx <= HALF_FOOTPRINT; dx++) {
			for (int dz = -HALF_FOOTPRINT; dz <= HALF_FOOTPRINT; dz++) {
				total++;
				for (int dy : new int[]{0, 1, -1}) {
					BlockPos cell = spot.offset(dx, dy, dz);
					if (terrain.isKnown(cell.below()) && KnownBlocks.isStandable(level, cell) && level.getFluidState(cell.below()).isEmpty()) {
						good += dy == 0 ? 1.0 : 0.5;
						break;
					}
				}
			}
		}

		return good / total;
	}

	private static double nearest(List<BlockPos> positions, BlockPos from) {
		double best = Double.MAX_VALUE;
		for (BlockPos pos : positions) {
			best = Math.min(best, pos.distSqr(from));
		}

		return Math.sqrt(best);
	}
}
