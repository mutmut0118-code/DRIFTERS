package com.dinglebob.drifter.brain;

import java.util.Locale;
import java.util.UUID;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.social.SocialEvent;
import com.dinglebob.drifter.social.SocialWatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Guesses it makes, right or wrong, as a player does (the user, 2026-10-16: "their logic needs more assumption, allow
 * them to make mistakes"; milestone 20 "wrong guesses"). Here: who trampled its crops. It notices who it sees on its
 * farm, and when it next works the farm and finds farmland trampled back to dirt, it blames the last one it saw there
 * (a person over an animal), whether they did it or not. It may well have done it itself, jumping about.
 */
public final class Hunches {
	/** Who it last saw on its farm counts for this long. */
	private static final long VISITOR_FOR = 24000L;
	/** A person seen there outweighs an animal for this long. */
	private static final long PERSON_OUTWEIGHS = 20L * 60 * 2;
	/** Farmland cells gone since it last worked the farm before it calls it trampling. */
	private static final int TRAMPLED = 2;

	private Hunches() {
	}

	/** Every couple of seconds: anyone it can see standing on its farm. */
	public static void noteFarmVisitors(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || home.farm == null || !home.dimension.equals(drifter.level().dimension().location())
			|| home.farm.distSqr(drifter.blockPosition()) > 64 * 64) {
			return;
		}

		long now = drifter.level().getGameTime();
		int reach = home.farmRadius + 1;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (!awareness.visibleNow || !(awareness.entity instanceof LivingEntity seen) || seen == drifter || !seen.isAlive()) {
				continue;
			}

			BlockPos at = seen.blockPosition();
			if (Math.abs(at.getX() - home.farm.getX()) > reach || Math.abs(at.getZ() - home.farm.getZ()) > reach
				|| Math.abs(at.getY() - home.farm.getY()) > 2) {
				continue;
			}

			boolean person = SocialWatcher.isRealPlayer(seen) || seen instanceof DrifterEntity;
			if (person || !home.farmVisitorPerson || now - home.farmVisitorAt > PERSON_OUTWEIGHS) {
				home.farmVisitor = seen.getUUID();
				home.farmVisitorName = seen instanceof Player player ? player.getGameProfile().getName() : seen.getName().getString();
				home.farmVisitorKind = person ? "" : seen.getType().getDescription().getString().toLowerCase(Locale.ROOT);
				home.farmVisitorPerson = person;
				home.farmVisitorAt = now;
			}
		}
	}

	/** Its farmland as it is now (the cells round the water, where it tills). */
	public static int farmland(Level level, Home home) {
		int count = 0;
		int radius = home.farmRadius;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (level.getBlockState(home.farm.offset(dx, -1, dz)).is(Blocks.FARMLAND)) {
					count++;
				}
			}
		}

		return count;
	}

	/** Back at its farm: farmland gone since last time is trampling, and it knows who did it (it thinks). */
	public static void checkFarm(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home == null || home.farm == null || home.farmlandSeen < 0) {
			return;
		}

		int now = farmland(drifter.level(), home);
		int lost = home.farmlandSeen - now;
		home.farmlandSeen = now;
		if (lost < TRAMPLED) {
			return;
		}

		long tick = drifter.level().getGameTime();
		String plots = lost + " of its farm's plots";
		if (home.farmVisitor == null || tick - home.farmVisitorAt > VISITOR_FOR) {
			think(drifter, plots + " trampled: no idea who did it");
			return;
		}

		if (!home.farmVisitorPerson) {
			think(drifter, plots + " trampled: it must have been the " + home.farmVisitorKind + " it saw there");
			return;
		}

		think(drifter, plots + " trampled: it must have been " + home.farmVisitorName);
		// A player it blames: they did it, as far as it knows (trust down a little, a small grudge).
		Player player = drifter.level().getPlayerByUUID(home.farmVisitor);
		UUID id = home.farmVisitor;
		if (player != null && SocialWatcher.isRealPlayer(player) || drifter.getSocial().get(id) != null) {
			drifter.getSocial().record(id, home.farmVisitorName, SocialEvent.TRAMPLED_CROPS, 0.0);
		}
	}

	/** Done at the farm for now: what it left there is what it expects to find next time. */
	public static void leaveFarm(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null && home.farm != null) {
			home.farmlandSeen = farmland(drifter.level(), home);
		}
	}

	private static void think(DrifterEntity drifter, String thought) {
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), thought);
	}
}
