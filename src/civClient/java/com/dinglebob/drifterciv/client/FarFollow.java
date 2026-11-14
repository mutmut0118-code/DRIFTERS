package com.dinglebob.drifterciv.client;

import com.dinglebob.drifterciv.DrifterCivilizations;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Voxy SeedGen (optional, found at runtime) fills the far land round where the player stood when its fill began, and
 * looks at where they are again only when that whole pass is done (minutes). A new player is put at the default start
 * and then, after "Who are you?", at their nation's town, thousands of blocks off: the far land stayed round the first
 * spot (the user, 2026-11-14: "the chunks always generate at 0,0 and not where the player is spawned"). So when the
 * player has been moved a long way in one go, Voxy SeedGen's fill is stopped; set to keep up with the player (its
 * default) it starts a new one round the new place within a second. (Its plain {@code stop()} is the player's own "stop":
 * it switches "keep up with me" off in their settings and saves that, found in testing; {@code stopForWorldClosing()}
 * only ends the fill that's running.) Nothing of Voxy SeedGen's is compiled against: two calls by name, and off for
 * good at the first one that fails.
 */
public final class FarFollow {
	private FarFollow() {
	}

	/** A move of more than this many blocks in one tick is a teleport worth starting the far land over for. */
	private static final double JUMP = 512.0;

	private static double lastX;
	private static double lastZ;
	private static ResourceKey<Level> lastDimension;
	private static boolean off;

	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded("voxyseedgen")) {
			return;
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (off || client.player == null || client.level == null) {
				lastDimension = null;
				return;
			}

			double x = client.player.getX();
			double z = client.player.getZ();
			ResourceKey<Level> dimension = client.level.dimension();
			if (dimension.equals(lastDimension) && Math.hypot(x - lastX, z - lastZ) > JUMP) {
				restart((int) x, (int) z);
			}

			lastX = x;
			lastZ = z;
			lastDimension = dimension;
		});
	}

	private static void restart(int x, int z) {
		try {
			Object config = Class.forName("mobius.voxyseedgen.config.VoxySeedGenConfig").getMethod("get").invoke(null);
			if (!config.getClass().getField("continuous").getBoolean(config)) {
				return;
			}

			Object stopped = Class.forName("mobius.voxyseedgen.voxy.SeedFill").getMethod("stopForWorldClosing").invoke(null);
			DrifterCivilizations.LOGGER.info("[far] moved a long way (to {}, {}): Voxy SeedGen's fill {}", x, z,
				Boolean.TRUE.equals(stopped) ? "stopped, to start over here" : "wasn't running");
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			off = true;
			DrifterCivilizations.LOGGER.warn("[far] couldn't ask Voxy SeedGen to start over round the player; left alone from now on ({})", e.toString());
		}
	}
}
