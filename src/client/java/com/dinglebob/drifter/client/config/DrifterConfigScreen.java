package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Drifter's settings in-game (user's wish; milestone 12 step 6), opened from Mod Menu: every setting in config/drifter.json,
 * the same ones the /drifter config commands change, in sections, with the spawning, encounter and performance pages a
 * click away. In single player they reach the running world; on a server they only change this computer's file (the
 * server's file rules there).
 */
public class DrifterConfigScreen extends DrifterOptionsScreen {
	public DrifterConfigScreen(Screen parent) {
		super(parent, "Drifter settings");
	}

	@Override
	protected void addOptions() {
		this.note("Changes apply at once (also: /drifter config). Hover over a setting to see what it does.");

		this.heading("Drifters");
		this.row(slider("Sight", DrifterConfig.MIN_SIGHT_CHUNKS, DrifterConfig.MAX_SIGHT_CHUNKS, DrifterConfig.sightChunks(),
				v -> v + " chunks", DrifterConfig::setSightChunks, "How far a Drifter can make things out in daylight (less in the dark, rain and fog)."),
			slider("Route range", DrifterConfig.MIN_PATH_RANGE, DrifterConfig.MAX_PATH_RANGE, DrifterConfig.pathRange(),
				v -> v + " blocks", DrifterConfig::setPathRange, "How far one route search reaches. Farther finds better ways round, and costs more."));
		this.row(slider("Field of view", DrifterConfig.MIN_FOV, DrifterConfig.MAX_FOV, DrifterConfig.fov(), v -> v + "°",
				DrifterConfig::setFov, "Like your FOV slider: how wide they see."),
			slider("Kept running", 0, DrifterConfig.MAX_SIMULATED, DrifterConfig.simulatedDrifters(),
				v -> String.valueOf(v), DrifterConfig::setSimulatedDrifters,
				"The busiest few far from every player keep living in full; the rest catch up when you come back."));
		this.row(tip(CycleButton.<DrifterConfig.OfflineProgression>builder(mode -> Component.literal(switch (mode) {
					case OFF -> "off";
					case GAME_TIME -> "game time";
					case REAL_TIME -> "real time";
				}))
				.withValues(DrifterConfig.OfflineProgression.values())
				.withInitialValue(DrifterConfig.offlineProgression())
				.create(0, 0, WIDTH, 20, Component.literal("While closed"), (button, mode) -> DrifterConfig.setOfflineProgression(mode)),
				"What they did while the world was closed: nothing, the game time that passed, or the real time (capped at two days)."),
			toggle("Ragdolls on death", DrifterConfig.ragdolls(), DrifterConfig::setRagdolls,
				"With Sable Player Ragdoll: a Drifter that dies falls as a ragdoll (and a corpse with its things, with Ragdoll Corpse)."));
		this.row(toggle("Chat lines", DrifterConfig.chatLines(), DrifterConfig::setChatLines, "Short lines in chat (hi, ty, gg...) and what encounter Drifters say."),
			toggle("Thoughts in the log", DrifterConfig.logThoughts(), DrifterConfig::setLogThoughts,
				"Every decision they make goes to the game log, in plain words. For finding out why one did something."));
		this.row(slider("Chat heard within", DrifterConfig.MIN_CHAT_RANGE, DrifterConfig.MAX_CHAT_RANGE, DrifterConfig.chatRange(), v -> v + " blocks",
			DrifterConfig::setChatRange, "How far off you see what a Drifter says in chat (their talk with each other, hellos, rips...)."), null);
		this.row(toggle("Whims (things for fun)", DrifterConfig.whims(), DrifterConfig::setWhims, "Watching sunsets, little builds, snowball fights: what they do when bored."),
			toggle("Parkour jumps", DrifterConfig.parkour(), DrifterConfig::setParkour, "Jumping gaps on their routes, as players do."));

		this.heading("When a Drifter dies");
		var lives = slider("Lives", 1, DrifterConfig.MAX_LIVES, DrifterConfig.lives(), v -> String.valueOf(v), DrifterConfig::setLives,
			"How many times it comes back before it logs off for good.");
		lives.active = DrifterConfig.respawnMode() == DrifterConfig.RespawnMode.LIVES;
		this.row(tip(CycleButton.<DrifterConfig.RespawnMode>builder(mode -> Component.literal(switch (mode) {
					case BED -> "back at its bed";
					case LIVES -> "limited lives";
					case PERMADEATH -> "gone for good";
				}))
				.withValues(DrifterConfig.RespawnMode.values())
				.withInitialValue(DrifterConfig.respawnMode())
				.create(0, 0, WIDTH, 20, Component.literal("On death"), (button, mode) -> {
					DrifterConfig.setRespawnMode(mode);
					lives.active = mode == DrifterConfig.RespawnMode.LIVES;
				}),
				"Back at its bed (or its base, or the world spawn if it has none yet) with nothing, like a player; the same but only a few times; or never."),
			lives);
		this.row(toggle("Death messages", DrifterConfig.deathMessages(), DrifterConfig::setDeathMessages, "\"<name> was slain by Zombie\" in chat, as for a player."), null);

		this.heading("Toward players and each other");
		this.row(slider("Hostile to players", 0, 100, DrifterConfig.playerAggression(), DrifterConfigScreen::aggressionText,
				DrifterConfig::setPlayerAggression, "0: never hostile. 100: hostile on sight. 50: as each one's personality has it."),
			slider("Hostile to others", 0, 100, DrifterConfig.drifterAggression(), DrifterConfigScreen::aggressionText,
				DrifterConfig::setDrifterAggression, "The same between Drifters: 100 makes every Drifter outside its group an enemy."));
		this.row(toggle("Share reputation", DrifterConfig.shareReputation(), DrifterConfig::setShareReputation,
				"Players seen together share a little of each other's standing."),
			toggle("Raid players' chests", DrifterConfig.raiding(), DrifterConfig::setRaiding, "Bandits, and Drifters with a real grudge, raid players' chests."));
		this.row(slider("Group size", 2, DrifterConfig.MAX_GROUP_SIZE, DrifterConfig.groupSize(), v -> "up to " + v, DrifterConfig::setGroupSize,
			"How many Drifters a group (friends who team up and share a base) can have. Four is the usual."), null);
		this.row(toggle("Group wars and raids", DrifterConfig.drifterWars(), DrifterConfig::setDrifterWars, "Drifter groups fall into feuds, skirmish and raid each other."),
			toggle("Bandits kill on sight", DrifterConfig.banditsKillOnSight(), DrifterConfig::setBanditsKillOnSight,
				"Bandits attack anyone outside their gang on sight: players, Drifters, villagers, pets."));
		this.row(toggle("Outpost Bandits grief", DrifterConfig.banditGriefing(), DrifterConfig::setBanditGriefing,
			"Outpost Bandits break doors, take chests and set fires in players' bases."), null);

		this.heading("World generation (new chunks only)");
		this.row(toggle("World-gen camps", DrifterConfig.worldgenCamps(), DrifterConfig::setWorldgenCamps, "Drifter camps out in the world."),
			toggle("Old strip mines", DrifterConfig.worldgenMines(), DrifterConfig::setWorldgenMines, "Old strip mines someone dug long ago."));
		this.row(toggle("Torches in caves", DrifterConfig.worldgenCaveTorches(), DrifterConfig::setWorldgenCaveTorches, "Torches left in caves by someone who came before."),
			toggle("Drifter villages", DrifterConfig.worldgenVillages(), DrifterConfig::setWorldgenVillages, "Drifters living in some villages."));
		this.row(toggle("Bandit outposts", DrifterConfig.worldgenOutposts(), DrifterConfig::setWorldgenOutposts, "Bandit gangs holding some pillager outposts."),
			toggle("Wandering Drifters", DrifterConfig.worldgenWanderers(), DrifterConfig::setWorldgenWanderers,
				"Lone Drifters with no home yet: just starting out, or passing through."));
		this.row(toggle("Mineshaft delvers", DrifterConfig.worldgenDelvers(), DrifterConfig::setWorldgenDelvers,
			"Well-armed Drifters delving some mineshafts, looting their chests. Mostly unfriendly."), null);

		this.heading("More");
		this.row(this.page("Spawning...", "How common each kind of new Drifter is, the most at once, and their skins.", () -> new DrifterSpawnScreen(this)),
			this.page("Encounters...", "Random events with Drifters you meet once: how often, and each kind on or off.", () -> new DrifterEncounterScreen(this)));
		this.row(this.page("Performance...", "Helper threads, and how often far-off Drifters think.", () -> new DrifterPerformanceScreen(this)), null);
	}

	private static String aggressionText(int value) {
		return value <= 0 ? "never" : value >= 100 ? "always" : value == DrifterConfig.DEFAULT_AGGRESSION ? "normal" : value + "%";
	}
}
