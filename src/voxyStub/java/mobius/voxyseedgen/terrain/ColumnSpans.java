package mobius.voxyseedgen.terrain;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NOT Voxy SeedGen's code: only the names and shapes of the few public methods of its column type that Drifter:
 * Civilizations' optional Voxy hookup calls (client/FarVoxy), so the addon compiles without Voxy SeedGen (All Rights
 * Reserved, never in this repository). Compile-only: never in a jar; at run time the real class is Voxy SeedGen's own.
 * As of Voxy SeedGen 0.3.1: a column's spans bottom up, heights counted from the world's floor, a light byte each
 * (block light << 4 | sky light).
 */
public final class ColumnSpans {
	private ColumnSpans() {
	}

	public void add(int bottomY, int topY, BlockState state, Holder<Biome> biome, int blockLight, int skyLight) {
		throw new AssertionError("stub");
	}

	public void clear() {
		throw new AssertionError("stub");
	}

	public int count() {
		throw new AssertionError("stub");
	}

	public int bottom(int index) {
		throw new AssertionError("stub");
	}

	public int top(int index) {
		throw new AssertionError("stub");
	}

	public BlockState state(int index) {
		throw new AssertionError("stub");
	}

	public Holder<Biome> biome(int index) {
		throw new AssertionError("stub");
	}

	public int light(int index) {
		throw new AssertionError("stub");
	}
}
