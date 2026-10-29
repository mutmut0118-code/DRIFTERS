package com.dinglebob.drifter.debug;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Debug (the user, 2026-10-29: stray cobblestone in and round houses "keeps coming back"): with {@code /drifter placelog
 * true}, every block a Drifter puts down goes to the server log with who, where, what it was doing and which code placed
 * it, so the next stray block can be traced to its job. Off by default and not saved.
 */
public final class PlaceLog {
	public static boolean on;
	private static String last = "";

	private static final StackWalker WALKER = StackWalker.getInstance();

	private PlaceLog() {
	}

	public static void placed(DrifterEntity drifter, BlockPos pos) {
		if (!on) {
			return;
		}

		// (A block placed through a task is reported by the click and by the task: once.)
		String key = drifter.getUUID() + "@" + pos.asLong() + "@" + drifter.level().getGameTime();
		if (key.equals(last)) {
			return;
		}

		last = key;

		// (A click that opened a door or a chest instead placed nothing.)
		if (drifter.level().getBlockState(pos).isAir()) {
			return;
		}

		String block = BuiltInRegistries.BLOCK.getKey(drifter.level().getBlockState(pos).getBlock()).getPath();
		var brain = drifter.getDrifterBrain();
		String goal = brain.currentGoal() == null ? "-" : brain.currentGoal().id();
		String doing = brain.activity() == null ? "-" : brain.activity().describe();
		// The first three frames outside the logging itself: which task or activity placed it.
		String by = WALKER.walk(frames -> frames.filter(f -> !f.getClassName().endsWith("PlaceLog") && !f.getClassName().endsWith("Senses"))
			.limit(3).map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + ":" + f.getLineNumber())
			.reduce((a, b) -> a + "<" + b).orElse("?"));
		Drifter.LOGGER.info("[placelog] {} put {} at {} {} {} | goal {} | {} | by {}", drifter.getName().getString(), block, pos.getX(), pos.getY(), pos.getZ(),
			goal, doing, by);
	}
}
