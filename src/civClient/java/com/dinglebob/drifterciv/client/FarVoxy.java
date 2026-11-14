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
		int ground = count - 1;
		while (ground >= 0 && spans.state(ground).isAir()) {
			ground--;
		}

		if (ground < 0) {
			return;
		}

		int[] bottoms = new int[ground + 1];
		int[] tops = new int[ground + 1];
		BlockState[] states = new BlockState[ground + 1];
		@SuppressWarnings("unchecked")
		Holder<Biome>[] biomes = new Holder[ground + 1];
		int[] lights = new int[ground + 1];
		for (int i = 0; i <= ground; i++) {
			bottoms[i] = spans.bottom(i);
			tops[i] = spans.top(i);
			states[i] = spans.state(i);
			biomes[i] = spans.biome(i);
			lights[i] = spans.light(i);
		}

		// Everything under the ground as it was; the ground with the painter's block and light; then what stands on it, air
		// between (what stood there before, a tree, is gone: a house is there now).
		spans.clear();
		for (int i = 0; i < ground; i++) {
			spans.add(bottoms[i], tops[i], states[i], biomes[i], lights[i] >> 4, lights[i] & 15);
		}

		boolean roofed = !column.spans().isEmpty();
		Holder<Biome> biome = biomes[ground];
		int surface = tops[ground];
		spans.add(bottoms[ground], surface, column.surface() == null ? states[ground] : column.surface(), biome, Math.max(column.light(), lights[ground] >> 4),
			roofed ? 0 : 15);
		int cursor = surface;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (FarPaint.Span span : column.spans()) {
			if (surface + span.bottom() > cursor) {
				spans.add(cursor, surface + span.bottom(), air, biome, column.light(), 0);
			}

			spans.add(surface + span.bottom(), surface + span.top(), span.block(), biome, span.light(), 15);
			cursor = surface + span.top();
		}

		if (!roofed && column.light() > 0) {
			// (Lit air over the ground, so its glow shows on the ground's top whichever voxel Voxy takes the light from.)
			spans.add(surface, surface + 1, air, biome, column.light(), 15);
		}
	}
}
