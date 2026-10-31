package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.screens.Screen;

/**
 * Performance settings (milestone 16: "1000 drifters in a world"): how many of the computer's threads Drifters' path
 * searches and eye rays may use besides the server's own, and whether far-off ones think half as often. The same as
 * {@code /drifter config paththreads}. And Touch'n Motion on Drifters (its animations cost a little each frame per Drifter).
 */
public class DrifterPerformanceScreen extends DrifterOptionsScreen {
	public DrifterPerformanceScreen(Screen parent) {
		super(parent, "Drifter performance");
	}

	@Override
	protected void addOptions() {
		int processors = Runtime.getRuntime().availableProcessors();
		int automatic = Math.max(1, Math.min(6, processors / 4));
		this.heading("Speed");
		this.row(slider("Helper threads", -1, Math.min(DrifterConfig.MAX_PATH_THREADS, processors), DrifterConfig.pathThreads(),
				v -> v < 0 ? "auto (" + automatic + ")" : v == 0 ? "off" : String.valueOf(v), DrifterConfig::setPathThreads,
				"Threads for Drifters' route-finding and eyes, besides the server's own. Off: all on the server thread (slower with many Drifters)."),
			toggle("Far ones think less", DrifterConfig.farHalfRate(), DrifterConfig::setFarHalfRate,
				"Drifters with no player within 128 blocks look and think every other tick (never in a fight)."));
		this.note("Automatic uses a quarter of your computer's threads (" + processors + " here), at most 6.");
		this.heading("Animations");
		this.row(toggle("Touch'n Motion", DrifterConfig.touchNMotion(), DrifterConfig::setTouchNMotion,
			"With the Touch'n Motion mod (and EMF): its animations on Drifters within 64 blocks too, as on players. Off: only on players."), null);
	}
}
