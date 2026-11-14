package com.dinglebob.drifterciv.client;

import java.util.concurrent.atomic.AtomicLong;

import com.dinglebob.drifterciv.DrifterCivilizations;
import com.dinglebob.drifterciv.FarPaint;

import mobius.voxyseedgen.terrain.ColumnSpans;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paper towns in Voxy (optional: Voxy with Voxy SeedGen 0.3.x; docs/superpowers/specs/2026-11-14-lod-towns-design.md and
 * the spike's findings beside it). Voxy alone keeps only land the game has loaded; Voxy SeedGen fills the far land in
 * from the seed, a column at a time, and each column passes through here on its way into Voxy
 * ({@code mixin.voxy.VoxyChunkWriterMixin}, {@code LadderWriterMixin}). Where the painter has an answer (an unbuilt
 * place's sketch, an unlaid road) the column is rewritten: the ground's top block, what stands on it, the light.
 *
 * <p>A column's spans run bottom up with no gaps, heights counted from the world's floor, a light byte each (block
 * light << 4 | sky light). Called on Voxy SeedGen's worker threads; nothing here waits or touches the world.
 */
public final class FarVoxy {
	private FarVoxy() {
	}

	private static final AtomicLong PAINTED = new AtomicLong();
	private static volatile boolean off;
	private static volatile boolean said;

	public static void paint(int worldX, int worldZ, int stride, ColumnSpans spans) {
		if (off) {
			return;
		}

		try {
			FarPaint.Column column = FarView.painter().column(worldX + stride / 2, worldZ + stride / 2);
			if (column != null) {
				rewrite(spans, column);
				if (!said) {
					said = true;
					DrifterCivilizations.LOGGER.info("[far] Voxy: the first column of a sketched town or road painted, at {}, {}", worldX, worldZ);
				}

				PAINTED.incrementAndGet();
			}
		} catch (RuntimeException | LinkageError e) {
			off = true;
			DrifterCivilizations.LOGGER.warn("[far] Voxy: painting a column failed; towns and roads are left out of Voxy's far view from now on", e);
		}
	}

	static long painted() {
		return PAINTED.get();
	}

	private static void rewrite(ColumnSpans spans, FarPaint.Column column) {
		int count = spans.count();
		// (The ground: the highest span that isn't air, a tree or a plant. The top one is often a treetop.)
		int ground = count - 1;
		while (ground >= 0 && !FarPaint.ground(spans.state(ground))) {
			ground--;
		}

		if (ground < 0) {
			return;
		}

		int[] bottoms = new int[count];
		int[] tops = new int[count];
		BlockState[] states = new BlockState[count];
		@SuppressWarnings("unchecked")
		Holder<Biome>[] biomes = new Holder[count];
		int[] lights = new int[count];
		for (int i = 0; i < count; i++) {
			bottoms[i] = spans.bottom(i);
			tops[i] = spans.top(i);
			states[i] = spans.state(i);
			biomes[i] = spans.biome(i);
			lights[i] = spans.light(i);
		}

		spans.clear();
		if (column.lightOnly()) {
			// Only a lamp's glow on it: everything as it was (its trees too), the ground and the air over it lit.
			for (int i = 0; i < count; i++) {
				int block = i == ground || i == ground + 1 ? Math.max(column.light(), lights[i] >> 4) : lights[i] >> 4;
				spans.add(bottoms[i], tops[i], states[i], biomes[i], block, lights[i] & 15);
			}

			return;
		}

		// The ground's top, where the far land has it or (a road's bed, a house's plot) where it's asked for, the land cut
		// down or filled up to it; under it as it was; what grew on it gone.
		Holder<Biome> biome = biomes[ground];
		boolean water = !states[ground].getFluidState().isEmpty();
		int top = tops[ground];
		if (column.y() != FarPaint.NO_Y && !water) {
			int asked = column.y() + 1 - FarView.minY();
			if (Math.abs(asked - top) <= FarPaint.MOST_GRADE && asked - 1 > bottoms[0]) {
				top = asked;
			}
		}

		int laid = bottoms[0];
		for (int i = 0; i <= ground; i++) {
			int upTo = Math.min(tops[i], top - 1);
			if (upTo > bottoms[i]) {
				spans.add(bottoms[i], upTo, states[i], biomes[i], lights[i] >> 4, lights[i] & 15);
				laid = upTo;
			}
		}

		if (laid < top - 1) {
			spans.add(laid, top - 1, water ? states[ground] : Blocks.DIRT.defaultBlockState(), biome, 0, 0);
		}

		boolean roofed = !column.spans().isEmpty();
		spans.add(top - 1, top, column.surface() == null ? states[ground] : column.surface(), biome, Math.max(column.light(), lights[ground] >> 4), roofed ? 0 : 15);
		int cursor = top;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (FarPaint.Span span : column.spans()) {
			if (top + span.bottom() > cursor) {
				spans.add(cursor, top + span.bottom(), air, biome, column.light(), 0);
			}

			spans.add(top + span.bottom(), top + span.top(), span.block(), biome, span.light(), 15);
			cursor = top + span.top();
		}

		if (!roofed && column.light() > 0) {
			// (Lit air over the ground, so its glow shows on the ground's top whichever voxel Voxy takes the light from.)
			spans.add(top, top + 1, air, biome, column.light(), 15);
		}
	}
}
