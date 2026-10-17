package com.dinglebob.drifter.client.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;

/**
 * Debug only: a scripted look at the game, for checking what can't be checked on a server (animations, models). Off
 * unless the environment variable {@code DRIFTER_CLIENT_TEST} names a script file; then, once a singleplayer world is
 * open, it runs the script one line at a time: {@code wait <ticks>}, {@code cmd <command>} (run by the world's server
 * with full permission), {@code shot <name>} (a screenshot into screenshots/), {@code hud off|on}, {@code quit}.
 */
public final class DevClientTest {
	private DevClientTest() {
	}

	private static Deque<String> lines;
	private static int waiting;

	public static void register() {
		String script = System.getenv("DRIFTER_CLIENT_TEST");
		if (script == null || script.isBlank()) {
			return;
		}

		try {
			lines = new ArrayDeque<>(Files.readAllLines(Path.of(script)));
		} catch (Exception e) {
			Drifter.LOGGER.error("[Drifter test] couldn't read {}", script, e);
			return;
		}

		Drifter.LOGGER.info("[Drifter test] running {} ({} lines)", script, lines.size());
		ClientTickEvents.END_CLIENT_TICK.register(DevClientTest::tick);
	}

	private static void tick(Minecraft mc) {
		if (lines == null || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
			return;
		}

		if (waiting > 0) {
			waiting--;
			return;
		}

		while (!lines.isEmpty()) {
			String line = lines.poll().trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			Drifter.LOGGER.info("[Drifter test] {}", line);
			if (line.startsWith("wait ")) {
				waiting = Integer.parseInt(line.substring(5).trim());
				return;
			} else if (line.startsWith("cmd ")) {
				MinecraftServer server = mc.getSingleplayerServer();
				String command = line.substring(4);
				server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
			} else if (line.startsWith("shot ")) {
				String name = line.substring(5).trim() + ".png";
				Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> Drifter.LOGGER.info("[Drifter test] {}", message.getString()));
			} else if (line.equals("hud off") || line.equals("hud on")) {
				mc.options.hideGui = line.equals("hud off");
			} else if (line.equals("quit")) {
				lines = null;
				mc.stop();
				return;
			}
		}
	}
}
