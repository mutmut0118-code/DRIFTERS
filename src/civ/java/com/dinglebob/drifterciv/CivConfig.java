package com.dinglebob.drifterciv;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Drifter: Civilizations' own settings (config/drifter_civ.json; the user, 2026-11-10: "a seperate option category for
 * friendship because i want this part to be customizable, with presets aswell"). For now only the Friendship page (D2);
 * the addon's older settings still live in Drifter's file. Changed by {@code /drifterciv config} and the addon's settings
 * screen in Mod Menu; saved at each change. Versioned.
 */
public final class CivConfig {
	private CivConfig() {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** How the friendship settings were set: one of the presets, or by hand. */
	public enum Preset {
		RELAXED("relaxed", "Relaxed"), NORMAL("normal", "Normal"), SLOW_BURN("slow_burn", "Slow burn"), HARDCORE("hardcore", "Hardcore"), CUSTOM("custom", "Custom");

		public final String id;
		public final String label;

		Preset(String id, String label) {
			this.id = id;
			this.label = label;
		}

		@Nullable
		public static Preset byId(String id) {
			for (Preset preset : values()) {
				if (preset.id.equalsIgnoreCase(id)) {
					return preset;
				}
			}

			return null;
		}
	}

	/** Friendships (D2): the Normal values are the design's. */
	public static final class Friends {
		public boolean on = true;
		public Preset preset = Preset.NORMAL;
		/** How fast you get closer: the stages' thresholds are divided by this. */
		public double pace = 1.0;
		/** How much more time together a best friend takes than a friend. */
		public double bestFriendHarder = 2.0;
		/** In-game days apart before a friendship cools (0: never). */
		public int coolDays = 10;
		public double agreeWeight = 1.0;
		public double disagreeWeight = 1.0;
		/** The share of a talk's worth each further talk of the day with the same player keeps (1: no falloff). */
		public double spamFalloff = 0.5;
		public boolean perkWelcome = true;
		public boolean perkFood = true;
		public boolean perkInvite = true;
		public boolean perkTips = true;
		public boolean perkBackup = true;
		public boolean perkWarnings = true;
		public boolean favors = true;
		/** Least in-game days between a friend's tips, and between its favors. */
		public double tipDays = 1.5;
		public double favorDays = 1.5;
		/** A friend's price off at its stall, in percent. */
		public int discount = 20;
		/** Tips and warnings whispered (false: said aloud). */
		public boolean whisper = true;
		/** A line in chat on becoming friends, and best friends. */
		public boolean announce = true;
		/** Only Drifters living in a town can become friends. */
		public boolean townsOnly = false;
	}

	private static final class Data {
		int version = 1;
		Friends friends = new Friends();
	}

	private static Data data = new Data();

	public static Friends friends() {
		return data.friends;
	}

	/**
	 * One setting of the Friendship page: its id (commands), label and tip (the screen), whether it's on/off, its range and
	 * step, how it's read, set and shown. Booleans read 0 or 1.
	 */
	public record Setting(String id, String label, String tip, boolean bool, double min, double max, double step, ToDoubleFunction<Friends> get,
		ObjDoubleConsumer<Friends> set, DoubleFunction<String> shown) {
		public double value() {
			return this.get.applyAsDouble(data.friends);
		}

		/** Set by hand: clamped, the preset becomes Custom, saved. */
		public void change(double value) {
			double v = this.bool ? (value >= 0.5 ? 1.0 : 0.0) : Math.max(this.min, Math.min(this.max, Math.round(value / this.step) * this.step));
			this.set.accept(data.friends, v);
			data.friends.preset = Preset.CUSTOM;
			save();
		}

		public String show() {
			return this.shown.apply(this.value());
		}
	}

	private static Setting toggle(String id, String label, String tip, ToDoubleFunction<Friends> get, ObjDoubleConsumer<Friends> set) {
		return new Setting(id, label, tip, true, 0, 1, 1, get, set, v -> v >= 0.5 ? "on" : "off");
	}

	private static Setting number(String id, String label, String tip, double min, double max, double step, ToDoubleFunction<Friends> get,
		ObjDoubleConsumer<Friends> set, DoubleFunction<String> shown) {
		return new Setting(id, label, tip, false, min, max, step, get, set, shown);
	}

	private static String times(double v) {
		return String.format(Locale.ROOT, "%.2fx", v);
	}

	private static String days(double v) {
		return String.format(Locale.ROOT, "%.1f days", v);
	}

	/** Every friendship setting, in the screen's order. */
	public static final List<Setting> FRIEND_SETTINGS = List.of(
		toggle("on", "Friendships", "Drifters can become your acquaintances, friends and best friends.", f -> f.on ? 1 : 0, (f, v) -> f.on = v >= 0.5),
		number("pace", "How fast", "How quickly talks and deeds bring you closer.", 0.25, 4.0, 0.05, f -> f.pace, (f, v) -> f.pace = v, CivConfig::times),
		number("bestFriendHarder", "Best friend takes", "How much longer a best friend takes than a friend.", 1.0, 4.0, 0.25, f -> f.bestFriendHarder,
			(f, v) -> f.bestFriendHarder = v, CivConfig::times),
		number("coolDays", "Cools after", "In-game days apart before a friendship cools to 'used to be friends'.", 4, 30, 1, f -> f.coolDays,
			(f, v) -> f.coolDays = (int)v == 4 ? 0 : (int)v, v -> v < 5 ? "never" : (int)v + " days"),
		number("agreeWeight", "Agreeing counts", "How much agreeing with its opinions warms it.", 0.0, 3.0, 0.1, f -> f.agreeWeight, (f, v) -> f.agreeWeight = v,
			CivConfig::times),
		number("disagreeWeight", "Disagreeing counts", "How much disagreeing cools it (argumentative ones enjoy it either way).", 0.0, 3.0, 0.1,
			f -> f.disagreeWeight, (f, v) -> f.disagreeWeight = v, CivConfig::times),
		number("spamFalloff", "Repeat talks keep", "How much each further talk the same day still counts (chatting nonstop gives less).", 0.0, 1.0, 0.05,
			f -> f.spamFalloff, (f, v) -> f.spamFalloff = v, v -> Math.round(v * 100) + "%"),
		toggle("perkWelcome", "Welcome back", "A friend comes over when you're back after a while.", f -> f.perkWelcome ? 1 : 0, (f, v) -> f.perkWelcome = v >= 0.5),
		toggle("perkFood", "Food when low", "A friend tosses you some of its food when you're hungry.", f -> f.perkFood ? 1 : 0, (f, v) -> f.perkFood = v >= 0.5),
		toggle("perkInvite", "Invites in", "A friend asks you in at night or in the rain, and offers its spare bed.", f -> f.perkInvite ? 1 : 0,
			(f, v) -> f.perkInvite = v >= 0.5),
		toggle("perkTips", "Tips", "A friend tells you about ores, caves and dangers it has seen.", f -> f.perkTips ? 1 : 0, (f, v) -> f.perkTips = v >= 0.5),
		number("tipDays", "Tips every", "Least in-game days between a friend's tips.", 0.5, 10.0, 0.5, f -> f.tipDays, (f, v) -> f.tipDays = v, CivConfig::days),
		toggle("perkBackup", "Backup in fights", "A friend fights bandits and hostile Drifters attacking you.", f -> f.perkBackup ? 1 : 0,
			(f, v) -> f.perkBackup = v >= 0.5),
		toggle("perkWarnings", "Trouble warnings", "A friend warns you when its town's guards are after you.", f -> f.perkWarnings ? 1 : 0,
			(f, v) -> f.perkWarnings = v >= 0.5),
		number("discount", "Friend's price", "How much cheaper a friend's stall is for you.", 0, 40, 1, f -> f.discount, (f, v) -> f.discount = (int)v,
			v -> (int)v + "% off"),
		toggle("favors", "Favors", "Now and then a friend asks you for a favor.", f -> f.favors ? 1 : 0, (f, v) -> f.favors = v >= 0.5),
		number("favorDays", "Favors every", "Least in-game days between a friend's favors.", 0.5, 10.0, 0.5, f -> f.favorDays, (f, v) -> f.favorDays = v,
			CivConfig::days),
		toggle("whisper", "Whisper tips", "Tips and warnings come as whispers (off: said aloud).", f -> f.whisper ? 1 : 0, (f, v) -> f.whisper = v >= 0.5),
		toggle("announce", "Announce", "A line in chat when someone becomes your friend or best friend.", f -> f.announce ? 1 : 0, (f, v) -> f.announce = v >= 0.5),
		toggle("townsOnly", "Townspeople only", "Only Drifters who live in a town can become friends.", f -> f.townsOnly ? 1 : 0, (f, v) -> f.townsOnly = v >= 0.5));

	@Nullable
	public static Setting setting(String id) {
		return FRIEND_SETTINGS.stream().filter(s -> s.id().equalsIgnoreCase(id)).findFirst().orElse(null);
	}

	/** Every friendship setting to a preset's values (Custom leaves them be). */
	public static void applyPreset(Preset preset) {
		if (preset == Preset.CUSTOM) {
			data.friends.preset = Preset.CUSTOM;
			save();
			return;
		}

		Friends f = new Friends();
		f.on = data.friends.on;
		f.townsOnly = data.friends.townsOnly;
		f.whisper = data.friends.whisper;
		f.announce = data.friends.announce;
		switch (preset) {
			case RELAXED -> {
				f.pace = 2.5;
				f.coolDays = 20;
				f.discount = 30;
				f.tipDays = 1.0;
				f.favorDays = 2.0;
			}
			case SLOW_BURN -> {
				f.pace = 0.5;
				f.bestFriendHarder = 3.0;
				f.discount = 10;
				f.tipDays = 3.0;
			}
			case HARDCORE -> {
				f.pace = 0.4;
				f.coolDays = 5;
				f.bestFriendHarder = 3.0;
				f.disagreeWeight = 1.5;
				f.discount = 10;
				f.favorDays = 1.0;
			}
			default -> {
			}
		}

		f.preset = preset;
		data.friends = f;
		save();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("drifter_civ.json");
	}

	public static void load() {
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (Exception e) {
				DrifterCivilizations.LOGGER.error("Couldn't read {}, using defaults", file, e);
			}
		}

		if (data.friends == null) {
			data.friends = new Friends();
		}

		if (data.friends.preset == null) {
			data.friends.preset = Preset.CUSTOM;
		}

		// (Hand-edited values put back in range.)
		Preset kept = data.friends.preset;
		for (Setting setting : FRIEND_SETTINGS) {
			double v = setting.value();
			setting.set().accept(data.friends, setting.bool() ? v : Math.max(setting.min(), Math.min(setting.max(), v)));
		}

		if (data.friends.coolDays != 0 && data.friends.coolDays < 5) {
			data.friends.coolDays = 5;
		}

		data.friends.preset = kept;
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			DrifterCivilizations.LOGGER.error("Couldn't save {}", file(), e);
		}
	}
}
