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
 * with full permission), {@code shot <name>} (a screenshot into screenshots/), {@code hud off|on}, {@code quit},
 * {@code fps <ticks>} (the frame rate over that many ticks, logged: average and lowest) and {@code jfr <seconds> <file>}
 * (a Java Flight Recorder profile of the game for that long, for tools/jfrtop.py).
 */
public final class DevClientTest {
	private DevClientTest() {
	}

	private static Deque<String> lines;
	private static int waiting;
	/** Frame-rate measuring: ticks left, the sum and the lowest seen. */
	private static int fpsTicks;
	private static int fpsCount;
	private static long fpsSum;
	private static int fpsMin = Integer.MAX_VALUE;

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

		if (fpsTicks > 0) {
			int fps = mc.getFps();
			fpsSum += fps;
			fpsCount++;
			fpsMin = Math.min(fpsMin, fps);
			if (--fpsTicks == 0) {
				Drifter.LOGGER.info("[Drifter test] fps average {} lowest {} over {} ticks", fpsSum / Math.max(1, fpsCount), fpsMin, fpsCount);
			}

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
			} else if (line.startsWith("screen ")) {
				// A settings page, as Mod Menu opens it (closing it goes back to the game).
				mc.setScreen(switch (line.substring(7).trim()) {
					case "encounters" -> new com.dinglebob.drifter.client.config.DrifterEncounterScreen(null);
					case "spawning" -> new com.dinglebob.drifter.client.config.DrifterSpawnScreen(null);
					case "performance" -> new com.dinglebob.drifter.client.config.DrifterPerformanceScreen(null);
					case "none" -> null;
					default -> new com.dinglebob.drifter.client.config.DrifterConfigScreen(null);
				});
			} else if (line.startsWith("scroll ")) {
				// The mouse wheel over the middle of the screen (positive: up).
				if (mc.screen != null) {
					mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, 0.0,
						Double.parseDouble(line.substring(7).trim()));
				}
			} else if (line.startsWith("gui ")) {
				// The GUI scale (0: auto), as in Video Settings.
				mc.options.guiScale().set(Integer.parseInt(line.substring(4).trim()));
				mc.resizeDisplay();
			} else if (line.startsWith("chat ")) {
				// Said in chat as the player (an encounter's "yes" or "go").
				if (mc.player != null) {
					mc.player.connection.sendChat(line.substring(5));
				}
			} else if (line.startsWith("shot ")) {
				String name = line.substring(5).trim() + ".png";
				Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> Drifter.LOGGER.info("[Drifter test] {}", message.getString()));
			} else if (line.startsWith("fps ")) {
				fpsTicks = Integer.parseInt(line.substring(4).trim());
				fpsSum = 0;
				fpsCount = 0;
				fpsMin = Integer.MAX_VALUE;
				return;
			} else if (line.startsWith("jfr ")) {
				String[] parts = line.split(" ", 3);
				try {
					jdk.jfr.Recording recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
					recording.setDuration(java.time.Duration.ofSeconds(Long.parseLong(parts[1])));
					recording.setDestination(Path.of(parts[2]));
					recording.start();
				} catch (Exception e) {
					Drifter.LOGGER.error("[Drifter test] couldn't start a profile", e);
				}
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
