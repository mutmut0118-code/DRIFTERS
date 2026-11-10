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

	public static void later(MinecraftServer server, Runnable task) {
		if (!server.isRunning()) {
			return;
		}

		server.execute(() -> {
			if (server.isSameThread() && server.isRunning()) {
				task.run();
			}
		});
	}
}
