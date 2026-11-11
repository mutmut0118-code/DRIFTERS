package com.dinglebob.drifter.world;

import net.minecraft.server.MinecraftServer;

/**
 * Work a background thread hands back to the server (a skin looked up, a world plan made), run on the server thread's next
 * turn. Not while the server's stopping: MinecraftServer#execute then runs it at once on the calling thread, and it touched
 * the world while the server was saving it (found in testing, 0.23.37: "Accessing LegacyRandomSource from multiple threads"
 * from the skin lookup, and a quit that hung on "Saving worlds" for twenty minutes).
 */
public final class ServerThread {
	private ServerThread() {
	}

	/** While the server thread waits ({@link #waitFor}): what's handed back, run by the wait itself. */
	private static java.util.Queue<Runnable> waiting;
	private static final Object LOCK = new Object();

	/**
	 * The server thread waits here for work done on other threads (a world's plan made before its first tick, so the wait
	 * sits in the game's own loading screen): what they hand back with {@link #later} is run here meanwhile, and
	 * {@code each} every turn (a few milliseconds). Until {@code done}, the server stops, or {@code mostMillis} have gone.
	 * The server's own queue isn't used: before the first tick it runs nothing handed to it.
	 */
	public static void waitFor(MinecraftServer server, java.util.function.BooleanSupplier done, long mostMillis, Runnable each) {
		if (!server.isSameThread()) {
			return;
		}

		java.util.Queue<Runnable> mine = new java.util.concurrent.ConcurrentLinkedQueue<>();
		synchronized (LOCK) {
			waiting = mine;
		}

		long until = System.currentTimeMillis() + mostMillis;
		try {
			while (!done.getAsBoolean() && server.isRunning() && System.currentTimeMillis() < until) {
				for (Runnable task = mine.poll(); task != null; task = mine.poll()) {
					run(task);
				}

				run(each);
				java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L);
			}
		} finally {
			synchronized (LOCK) {
				waiting = null;
			}

			for (Runnable task = mine.poll(); task != null; task = mine.poll()) {
				run(task);
			}
		}
	}

	/** (As the server's own queue does: what a task throws is logged, the rest go on.) */
	private static void run(Runnable task) {
		try {
			task.run();
		} catch (Throwable e) {
			com.dinglebob.drifter.Drifter.LOGGER.error("[Drifter] a task handed back to the server thread failed", e);
		}
	}

	public static void later(MinecraftServer server, Runnable task) {
		if (!server.isRunning()) {
			return;
		}

		synchronized (LOCK) {
			if (waiting != null) {
				waiting.add(task);
				return;
			}
		}

		server.execute(() -> {
			if (server.isSameThread() && server.isRunning()) {
				task.run();
			}
		});
	}
}
