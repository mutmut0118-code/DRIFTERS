package com.dinglebob.drifter.social.talk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.Lines;
import com.dinglebob.drifter.social.ChatLines;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A friendly 1v1 a player asked for in chat and a Drifter took (chat phase 2): it fights them to a few hearts, not to the
 * death, and neither side's hits count as wrongs ({@code SocialWatcher.hitBy}). Down to three hearts it gives up ("gg you
 * win"); the player down to four and it stops ("gg ez"). Walking off, or three minutes, ends it too. A few seconds after,
 * it doesn't take their last swing as an attack.
 */
public final class Spar {
	private Spar() {
	}

	/** It gives up at this health; it stops when the player is down to this. */
	private static final float YIELDS_AT = 6.0F;
	private static final float SPARES_AT = 8.0F;
	private static final long LONGEST = 20L * 60 * 3;
	/** Neither goes below this in a spar. */
	private static final float LAST_HEART = 2.0F;
	private static final double WALKED_OFF = 40.0;
	/** It takes a 1v1 from this close only. */
	public static final double START_WITHIN = 20.0;
	/** After it's over, how long a hit from them is still the fight's. */
	private static final long CALM = 20L * 6;

	private static final class Bout {
		final DrifterEntity drifter;
		final UUID player;
		final long started;

		Bout(DrifterEntity drifter, UUID player, long started) {
			this.drifter = drifter;
			this.player = player;
			this.started = started;
		}
	}

	private static final Map<UUID, Bout> bouts = new HashMap<>();
	/** "drifter:player" -> until when a hit from them is still the fight's. */
	private static final Map<String, Long> calm = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Spar::tick);
		// Nobody dies in a 1v1 they agreed to for fun: a blow that would take either below a heart doesn't land, and that
		// one has lost (found in a test: two iron-sword hits took the player from full to two hearts, past the four-heart
		// stop in one blow).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof DrifterEntity drifter && source.getEntity() instanceof Player player) {
				Bout bout = bouts.get(drifter.getUUID());
				if (bout != null && bout.player.equals(player.getUUID()) && drifter.getHealth() - amount < LAST_HEART) {
					end(bout, false, "gave up");
					return false;
				}
			} else if (entity instanceof ServerPlayer player) {
				DrifterEntity drifter = source.getEntity() instanceof DrifterEntity hitter ? hitter
					: com.dinglebob.drifter.hands.FakePlayerBridge.ownerOf(source.getEntity());
				Bout bout = drifter == null ? null : bouts.get(drifter.getUUID());
				if (bout != null && bout.player.equals(player.getUUID()) && player.getHealth() - amount < LAST_HEART) {
					end(bout, true, "won");
					return false;
				}
			}

			return true;
		});
		// As each blow lands (a crit could take it past three hearts and on to dead a second later).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof DrifterEntity drifter) {
				Bout bout = bouts.get(drifter.getUUID());
				if (bout != null && drifter.getHealth() <= YIELDS_AT) {
					end(bout, false, "gave up");
				}
			} else if (entity instanceof ServerPlayer player) {
				for (Bout bout : bouts.values().toArray(Bout[]::new)) {
					if (bout.player.equals(player.getUUID()) && player.getHealth() <= SPARES_AT) {
						end(bout, true, "won");
					}
				}
			}
		});
	}

	/** It takes them on. */
	public static void start(DrifterEntity drifter, ServerPlayer player) {
		long now = drifter.level().getGameTime();
		bouts.put(drifter.getUUID(), new Bout(drifter, player.getUUID(), now));
		drifter.getMemory().thoughts().add(now, "sparring with " + player.getGameProfile().getName() + " (to a few hearts)");
		// (It drops what it's doing: it said "bet".)
		drifter.getDrifterBrain().restart("took a 1v1 with " + player.getGameProfile().getName());
		drifter.getDrifterBrain().pursue(drifter, player, now);
	}

	/** They're in a friendly fight now. */
	public static boolean on(DrifterEntity drifter, Player player) {
		Bout bout = bouts.get(drifter.getUUID());
		return bout != null && bout.player.equals(player.getUUID());
	}

	/** It's in one (with anyone). */
	public static boolean sparring(DrifterEntity drifter) {
		return bouts.containsKey(drifter.getUUID());
	}

	/** Their fight's just over: a hit from them now is the last swing, not an attack. */
	public static boolean calming(DrifterEntity drifter, Player player) {
		return calm.getOrDefault(drifter.getUUID() + ":" + player.getUUID(), Long.MIN_VALUE) > drifter.level().getGameTime();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0 || bouts.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		calm.values().removeIf(until -> until < now);
		for (Iterator<Bout> it = bouts.values().iterator(); it.hasNext();) {
			Bout bout = it.next();
			DrifterEntity drifter = bout.drifter;
			ServerPlayer player = server.getPlayerList().getPlayer(bout.player);
			if (!drifter.isAlive() || drifter.isRemoved() || player == null || !player.isAlive() || player.level() != drifter.level()) {
				it.remove();
				drifter.getDrifterBrain().endPursuit();
				continue;
			}

			if (drifter.distanceTo(player) > WALKED_OFF || drifter.level().getGameTime() - bout.started > LONGEST) {
				it.remove();
				finish(bout, player, null);
				continue;
			}

			// (It keeps after them: the chase renews while it sees them.)
			if (drifter.getSenses().awarenessOf(player) != null && drifter.getSenses().awarenessOf(player).visibleNow) {
				drifter.getDrifterBrain().pursue(drifter, player, drifter.level().getGameTime());
			}
		}
	}

	private static void end(Bout bout, boolean drifterWon, String why) {
		if (bouts.remove(bout.drifter.getUUID()) == null || !(bout.drifter.level() instanceof ServerLevel level)) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(bout.player);
		if (player != null) {
			finish(bout, player, drifterWon);
		}
	}

	/** Over: it stops, says gg, and they get on a little better for it. */
	private static void finish(Bout bout, ServerPlayer player, Boolean drifterWon) {
		DrifterEntity drifter = bout.drifter;
		long now = drifter.level().getGameTime();
		drifter.getDrifterBrain().endPursuit();
		calm.put(drifter.getUUID() + ":" + player.getUUID(), now + CALM);
		String name = player.getGameProfile().getName();
		if (drifterWon != null) {
			ChatLines.speak(drifter, Lines.pick(drifter, drifterWon ? "ask.duel_won" : "ask.duel_lost", "player", name));
			drifter.getMemory().thoughts().add(now, (drifterWon ? "won a spar against " : "lost a spar to ") + name);
		} else {
			drifter.getMemory().thoughts().add(now, "the spar with " + name + " is off");
		}

		var r = drifter.getSocial().getOrMeet(player.getUUID(), name);
		r.addFamiliarity(3.0);
		r.addTrust(2.0);
	}
}
