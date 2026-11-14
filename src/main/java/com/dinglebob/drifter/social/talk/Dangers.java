package com.dinglebob.drifter.social.talk;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.social.Relationship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Places a Drifter keeps away from (docs/PLAYER_TALK.md F2.2; the user, 2026-11-12: a town mine broke into a dungeon
 * whose armored mobs killed its people one after another): where someone it doesn't distrust told it it's dangerous
 * ("dont go in the mine, theres a dungeon down there"), and where it saw someone killed by a monster. Its caving turns
 * back there, and its trips to the town mine skip a face there. Only what it was told or saw (fairness: no x-ray).
 */
public final class Dangers {
	private Dangers() {
	}

	/** A told danger stands this long (ticks); one it saw, as long as it remembers it. */
	private static final long TOLD_LASTS = 24000L * 5;
	/** How far round a danger it keeps away (blocks). */
	public static final double AROUND = 20.0;

	/** It knows of a danger this close to here. */
	public static boolean near(DrifterEntity drifter, BlockPos pos, double within) {
		long now = drifter.level().getGameTime();
		String dim = drifter.level().dimension().location().toString();
		double far = within * within;
		for (Heard.Place place : drifter.getHeard().places()) {
			if (!place.kind().equals("danger") || !place.dim().equals(dim) || now - place.at() > TOLD_LASTS || place.pos().distSqr(pos) > far) {
				continue;
			}

			// (From someone it distrusts: not believed.)
			if (place.tellerId() != null && !place.tellerId().equals(drifter.getUUID())) {
				Relationship r = drifter.getSocial().get(place.tellerId());
				if (r != null && r.trust < 0.0) {
					continue;
				}
			}

			return true;
		}

		for (MemoryEntry entry : drifter.getMemory().find(MemoryKind.DANGER, entry -> entry.label.equals("deaths"))) {
			if (entry.dimension.equals(drifter.level().dimension().location()) && BlockPos.containing(entry.pos).distSqr(pos) <= far) {
				return true;
			}
		}

		return false;
	}

	public static boolean near(DrifterEntity drifter, BlockPos pos) {
		return near(drifter, pos, AROUND);
	}

	/** It saw someone killed by a monster here: it remembers the place as dangerous. */
	static void sawKilled(DrifterEntity witness, BlockPos at) {
		witness.getMemory().remember(MemoryKind.DANGER, "deaths", net.minecraft.world.phys.Vec3.atCenterOf(at), witness.level().dimension().location(),
			witness.level().getGameTime(), com.dinglebob.drifter.memory.MemorySource.SEEN, 0.9F, null);
	}

	/** They say it's safe there now ("i cleared it"): what they told it of that place is forgotten. */
	static int cleared(DrifterEntity drifter, Player player, BlockPos at) {
		int before = drifter.getHeard().places().size();
		drifter.getHeard().places().removeIf(place -> place.kind().equals("danger") && place.pos().closerThan(at, AROUND * 2));
		drifter.getMemory().find(MemoryKind.DANGER, entry -> entry.label.equals("deaths") && BlockPos.containing(entry.pos).closerThan(at, AROUND * 2))
			.forEach(entry -> drifter.getMemory().forget(entry));
		return before - drifter.getHeard().places().size();
	}
}
