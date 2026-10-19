package com.dinglebob.drifter.nav;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Path searches on other threads (milestone 16, performance: the user's "1000 Drifters"). Each search reads only a frozen
 * copy of the world ({@link WorldCopy}) and of what its Drifter knows, so it can run anywhere; the Drifter waits a tick
 * or two for it, as before it waited its turn for the server thread's 2 ms of searching, which with a crowd was seconds
 * (found in a 300-Drifter test: "most of them stood around"). Searches for Drifters near a player go first. The threads
 * run at low priority, so the game's own threads come first. `/drifter config paththreads` (0: on the server thread as
 * before).
 */
public final class PathWorkers {
	private PathWorkers() {
	}

	/** A search waiting or running: near a player first, then oldest first. */
	static final class Job implements Runnable, Comparable<Job> {
		private static final AtomicLong order = new AtomicLong();
		final Runnable work;
		final int priority;
		final long sequence = order.getAndIncrement();

		Job(Runnable work, int priority) {
			this.work = work;
			this.priority = priority;
		}

		@Override
		public void run() {
			this.work.run();
		}

		@Override
		public int compareTo(Job other) {
			return this.priority != other.priority ? Integer.compare(this.priority, other.priority) : Long.compare(this.sequence, other.sequence);
		}
	}

	@Nullable
	private static ThreadPoolExecutor pool;
	private static int poolThreads;
	private static final AtomicInteger threadNumber = new AtomicInteger();

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdown());
	}

	/** How many threads to use: the setting, or (automatic) a quarter of the machine's, 1 to 6. */
	public static int threads() {
		int setting = DrifterConfig.pathThreads();
		if (setting >= 0) {
			return setting;
		}

		return Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() / 4));
	}

	/** Searches run on other threads (the setting isn't 0). */
	public static boolean enabled() {
		return threads() > 0;
	}

	/** Runs {@code work} on a path thread; {@code priority} lower first (0 near a player). */
	public static void submit(Runnable work, int priority) {
		int threads = threads();
		if (pool == null || poolThreads != threads) {
			shutdown();
			poolThreads = threads;
			pool = new ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, "Drifter path " + threadNumber.incrementAndGet());
				thread.setDaemon(true);
				thread.setPriority(Thread.NORM_PRIORITY - 2);
				thread.setUncaughtExceptionHandler((t, e) -> Drifter.LOGGER.error("[Drifter] path thread failed", e));
				return thread;
			});
			pool.allowCoreThreadTimeOut(true);
		}

		pool.execute(new Job(work, priority));
	}

	/** Searches waiting for a thread (for `/drifter perf`). */
	public static int waiting() {
		return pool == null ? 0 : pool.getQueue().size();
	}

	private static void shutdown() {
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
	}
}
