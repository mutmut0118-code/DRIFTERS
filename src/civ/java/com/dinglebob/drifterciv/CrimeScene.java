package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.ChatLines;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.world.DrifterGroups;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A crime seen in town plays out in chat over a few lines, from more than one of its people (the user, 2026-11-16: "can it be
 * like a 2 to 4 part chat / where they're like, omg they took a horse / somebody stop them! / are you seeing this?? / get
 * him!! / he got away :( / anyway"; then "expand this to the rest of the crimes to / and add variations for lines for
 * different personalities / gta style"). The one who saw it shouts (Justice), then one to three others near join in a few
 * seconds apart, each as its kind would (a newbie panics, a bandit's impressed, a fighter wants a go); half a minute later,
 * if the one who did it has gone off (and not paid), one of them says they got away. Those who were in the middle of a talk
 * pick it back up after ("anyway": Conversations.aside). Every crime: a thing stolen, a horse, the farms, the animals, a
 * false alarm, a dead one's armor kept, a killing, a massacre, someone wanted back in town, and Drifters' own (a crook's
 * theft, a fight).
 */
final class CrimeScene {
	private CrimeScene() {
	}

	/** How near the one who shouted the others have to be to join in (a killing's screams carry further). */
	private static final double JOIN = 24.0;
	private static final double JOIN_GRAVE = 40.0;
	/** At most this many join in (more for a killing). */
	private static final int MOST_JOIN = 3;
	/** When it's checked whether they got away; how far off counts as gone. */
	private static final long GOT_AWAY_AFTER = 20L * 30;
	private static final double GONE = 40.0;

	/** A line still to say: who, its key (null: the got-away check), when, and of whom. */
	private record Later(DrifterEntity drifter, @Nullable String key, long at, Entity culprit, String name, String who, Town town, String kind) {
	}

	private static final List<Later> later = new ArrayList<>();

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrimeScene::tick);
	}

	/** The one who saw it has shouted: the others near join in, and later, whether they got away. {@code who}: the one killed or hit. */
	static void start(MinecraftServer server, Town town, Entity culprit, String kind, DrifterEntity witness, @Nullable String who) {
		String name = culprit instanceof Player player ? player.getGameProfile().getName() : culprit.getName().getString();
		long now = server.overworld().getGameTime();
		// (One scene at a time for one culprit: a second thing taken doesn't start it over.)
		if (later.stream().anyMatch(l -> l.culprit() == culprit)) {
			return;
		}

		boolean grave = kind.equals("murder") || kind.equals("massacre");
		double reach = grave ? JOIN_GRAVE : JOIN;
		List<DrifterEntity> near = new ArrayList<>();
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (group != null) {
			for (UUID id : group.members) {
				DrifterEntity one = TownFounding.loaded(server, id);
				if (one != null && one != witness && one != culprit && one.isAlive() && one.level() == witness.level() && one.distanceTo(witness) <= reach
					&& !one.isSleeping()) {
					near.add(one);
				}
			}
		}

		java.util.Collections.shuffle(near, new java.util.Random(witness.getRandom().nextLong()));
		int join = Math.min(near.size(), (grave ? 2 : 1) + witness.getRandom().nextInt(MOST_JOIN));
		String chime = Lines.has("crime.chime." + kind) ? "crime.chime." + kind : "crime.chime";
		long at = now;
		for (int i = 0; i < join; i++) {
			at += 30 + witness.getRandom().nextInt(40);
			// (The first who joins in speaks to what it was; after, some say anything: "are you seeing this??".)
			String key = i == 0 || !Lines.has("crime.chime") || witness.getRandom().nextDouble() < 0.6 ? chime : "crime.chime";
			later.add(new Later(near.get(i), key, at, culprit, name, who == null ? "" : who, town, kind));
		}

		// (Whether they got away: said by the one who saw it, or one who joined in.)
		if (Lines.has("crime.got_away." + kind)) {
			DrifterEntity teller = join > 0 && witness.getRandom().nextBoolean() ? near.get(0) : witness;
			later.add(new Later(teller, null, now + GOT_AWAY_AFTER, culprit, name, who == null ? "" : who, town, kind));
		}
	}

	private static void tick(MinecraftServer server) {
		if (later.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}

		long now = server.overworld().getGameTime();
		List<Later> due = new ArrayList<>();
		for (Iterator<Later> it = later.iterator(); it.hasNext();) {
			Later l = it.next();
			if (now >= l.at()) {
				it.remove();
				due.add(l);
			}
		}

		for (Later l : due) {
			DrifterEntity drifter = l.drifter();
			if (!drifter.isAlive() || drifter.isRemoved()) {
				continue;
			}

			String key = l.key();
			if (key == null) {
				// Got away: gone off (dead or out of sight counts), and a fine not paid (a guard got it: nothing to say).
				Entity culprit = l.culprit() instanceof Player p ? server.getPlayerList().getPlayer(p.getUUID()) : l.culprit();
				boolean gone = culprit == null || culprit.isRemoved() || !culprit.isAlive() || culprit.level() != drifter.level()
					|| culprit.distanceTo(drifter) > GONE;
				Town.Criminal criminal = l.culprit() instanceof Player ? l.town().crimes.get(l.culprit().getUUID()) : null;
				boolean paid = l.culprit() instanceof Player && fined(l.kind()) && (criminal == null || criminal.fine <= 0);
				if (!gone || paid) {
					continue;
				}

				key = "crime.got_away." + l.kind();
			}

			ChatLines.toEveryone(drifter);
			ChatLines.speak(drifter, Lines.pick(drifter, key, "player", l.name(), "who", l.who()));
		}
	}

	/** A crime that's fined (paid: it's over). */
	private static boolean fined(String kind) {
		return switch (kind) {
			case "theft", "horse", "farms", "livestock", "peace", "blood" -> true;
			default -> false;
		};
	}
}
