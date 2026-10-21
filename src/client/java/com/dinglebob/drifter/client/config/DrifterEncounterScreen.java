package com.dinglebob.drifter.client.config;

import com.dinglebob.drifter.DrifterConfig;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;

/**
 * Encounters (milestone 21): on or off, how often, and each kind on its own, grouped as docs/ENCOUNTERS.md has them. In a
 * world on a server, the server's settings are what count (`/drifter config encounters ...`).
 */
public final class DrifterEncounterScreen extends DrifterOptionsScreen {
	/** Each group: its heading, then each kind's id, name and what happens. */
	private static final String[][][] GROUPS = {
		{{"Bounties"},
			{"bounty_hunters", "Bounty hunters", "A price on your head (a grudge, or Drifters you killed): armed hunters come for you."},
			{"scout", "Scouts", "Before the hunters come, one watches you from afar. Bring it down and they come late, one short."},
			{"revenge", "Revenge squads", "Friends of a Drifter you killed come looking for you."},
			{"wanted", "Wanted posters", "A map to a real Drifter's home and a reward for bringing it down."}},
		{{"Danger"},
			{"ambush", "Ambushes", "Bandits lying in wait on the road ahead of you."},
			{"mugging", "Muggings", "\"Your money or your life\": pay up or fight."},
			{"sniper", "Snipers", "An archer on higher ground shooting at you while you work."},
			{"village_raid", "Village raids", "Raiders attacking the village you're in."},
			{"burglar", "Thieves in your base", "Someone sneaking into your base to rob your chests."},
			{"horde", "Hordes", "Someone running past with zombies behind them, leading them to you."}},
		{{"Someone needs help"},
			{"injured_traveller", "Injured travellers", "Someone hurt, running from zombies, begging for help."},
			{"hungry_traveller", "Hungry travellers", "Someone starving asks for food and thanks you for it."},
			{"trapped_miner", "Trapped miners", "Someone stuck underground types where they are. Dig them out."},
			{"escort", "Escorts", "Walk a traveller to a village and keep them safe on the way."},
			{"hostage", "Hostages", "Bandits holding someone at their camp."},
			{"lost_dog", "Lost dogs", "Find someone's dog and bring him back."}},
		{{"Drama"},
			{"thief", "Thieves", "A thief runs past with the owner shouting after them."},
			{"feud", "Feuds", "Two Drifters fighting. Pick a side, or just watch."},
			{"duel", "Duels", "A 1v1 challenge with diamonds on it."},
			{"race", "Races", "First to the top of a hill."}},
		{{"Peaceful and odd"},
			{"merchant", "Merchants", "A travelling merchant with rare things for emeralds (sometimes a treasure map)."},
			{"campfire_party", "Campfire parties", "An evening party with a campfire, cake and fireworks."},
			{"convoy", "Convoys", "Riders on horses passing through, in open country."},
			{"builders", "Builders", "A crew puts up a little shelter near you and goes."},
			{"mourner", "Mourners", "Someone grieving at a grave by the road."},
			{"watcher", "The watcher", "A figure far off at night, gone when you get close."}}};

	public DrifterEncounterScreen(Screen parent) {
		super(parent, "Drifter encounters");
	}

	@Override
	protected void addOptions() {
		this.note("Random events with Drifters you meet once, per player. Times are in-game days.");
		// In quarter days: a quarter of a day to ten days.
		this.row(toggle("Encounters", DrifterConfig.encounters(), DrifterConfig::setEncounters, "All of them on or off."),
			slider("About one every", 1, 40, (int)Math.round(DrifterConfig.encounterDays() * 4.0),
				v -> (v % 4 == 0 ? String.valueOf(v / 4) : String.valueOf(v / 4.0)) + (v == 4 ? " day" : " days"),
				v -> DrifterConfig.setEncounterDays(v / 4.0), "How often one comes, on average. A day is 20 minutes."));
		for (String[][] group : GROUPS) {
			this.heading(group[0][0]);
			for (int i = 1; i < group.length; i += 2) {
				this.row(this.kind(group[i]), i + 1 < group.length ? this.kind(group[i + 1]) : null);
			}
		}
	}

	private CycleButton<Boolean> kind(String[] kind) {
		String id = kind[0];
		return toggle(kind[1], !DrifterConfig.encounterOff(id), on -> DrifterConfig.setEncounterOff(id, !on), kind[2] + " (/drifter encounter start " + id + ")");
	}
}
