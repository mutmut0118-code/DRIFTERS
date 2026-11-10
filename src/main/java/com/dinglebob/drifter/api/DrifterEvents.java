package com.dinglebob.drifter.api;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.News;
import com.dinglebob.drifter.social.SocialEvent;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What happens to Drifters, for other mods to listen to (docs/API.md; milestone 22's addon). The same kind of events
 * Fabric API has: {@code DrifterEvents.DIED.register((drifter, source, comesBack) -> ...)}. All on the server thread.
 */
public final class DrifterEvents {
	private DrifterEvents() {
	}

	/** A Drifter died (its things already dropped); {@code comesBack}: it will respawn (by the respawn setting). */
	public static final Event<Died> DIED = EventFactory.createArrayBacked(Died.class, listeners -> (drifter, source, comesBack) -> {
		for (Died listener : listeners) {
			listener.onDied(drifter, source, comesBack);
		}
	});

	/** A Drifter came back from the dead (at its bed, its base or the world spawn), with nothing. */
	public static final Event<Respawned> RESPAWNED = EventFactory.createArrayBacked(Respawned.class, listeners -> drifter -> {
		for (Respawned listener : listeners) {
			listener.onRespawned(drifter);
		}
	});

	/** A Drifter's home was set, moved or forgotten ({@code home} null). */
	public static final Event<HomeSet> HOME_SET = EventFactory.createArrayBacked(HomeSet.class, listeners -> (drifter, home) -> {
		for (HomeSet listener : listeners) {
			listener.onHomeSet(drifter, home);
		}
	});

	/** A Drifter joined a group ({@code group} its id), or left one ({@code group} null). By UUID: it may not be loaded. */
	public static final Event<GroupChanged> GROUP_CHANGED = EventFactory.createArrayBacked(GroupChanged.class, listeners -> (member, group) -> {
		for (GroupChanged listener : listeners) {
			listener.onGroupChanged(member, group);
		}
	});

	/**
	 * Something a player did that a Drifter noticed and took to heart (a hit, a gift, a theft, a kill near it: the
	 * {@link SocialEvent} table, DESIGN.md section 14), and how its trust in them stands after.
	 */
	public static final Event<PlayerDeed> PLAYER_DEED = EventFactory.createArrayBacked(PlayerDeed.class, listeners -> (drifter, player, deed, trustAfter) -> {
		for (PlayerDeed listener : listeners) {
			listener.onPlayerDeed(drifter, player, deed, trustAfter);
		}
	});

	/**
	 * A Drifter heard news it hadn't heard (and took it to heart: a player's deed moved its trust in them, a friend's death
	 * saddened it). {@code from}: the Drifter that told it (null: put there by a mod). Another mod's news ({@code Kind.CUSTOM})
	 * is that mod's to act on here.
	 */
	/**
	 * A player answered a question a Drifter asked them in chat (yes or no, however they said it), before it says what it
	 * says to that or does what a yes means (API 4, 0.23.37: an addon hears an opinion agreed with, a favor taken on).
	 */
	public static final Event<PlayerAnswered> PLAYER_ANSWERED = EventFactory.createArrayBacked(PlayerAnswered.class, listeners -> (drifter, player, topic, does, yes) -> {
		for (PlayerAnswered listener : listeners) {
			listener.onAnswered(drifter, player, topic, does, yes);
		}
	});

	public static final Event<HeardNews> HEARD_NEWS = EventFactory.createArrayBacked(HeardNews.class, listeners -> (drifter, news, from) -> {
		for (HeardNews listener : listeners) {
			listener.onHeardNews(drifter, news, from);
		}
	});

	/**
	 * Two Drifters traded (milestone 22 groundwork): {@code asker} gave {@code paid} (empty: it was a gift) to {@code giver}
	 * for {@code got}. Both have what they were given.
	 */
	public static final Event<Traded> TRADED = EventFactory.createArrayBacked(Traded.class, listeners -> (asker, giver, paid, got) -> {
		for (Traded listener : listeners) {
			listener.onTraded(asker, giver, paid, got);
		}
	});

	/** A Drifter finished building something at its home: {@code what} is "hut" or "house" (more to come). */
	public static final Event<Built> BUILT = EventFactory.createArrayBacked(Built.class, listeners -> (drifter, what, where) -> {
		for (Built listener : listeners) {
			listener.onBuilt(drifter, what, where);
		}
	});

	/**
	 * Two groups' feud moved on: {@code what} is "came to blows" (skirmishing), "went to war", "made peace (why)" or "set off to
	 * raid" ({@code a} raiding {@code b}). Group ids ({@code DrifterApi.group}).
	 */
	public static final Event<Feud> FEUD = EventFactory.createArrayBacked(Feud.class, listeners -> (a, b, what) -> {
		for (Feud listener : listeners) {
			listener.onFeud(a, b, what);
		}
	});

	/**
	 * A player took things out of a chest or a barrel while Drifters saw them at it (what they took, and who saw; the user,
	 * 2026-11-07: theft seen as it happens). Whose it was, and whether that was theft, is for the listener to say.
	 */
	public static final Event<SeenTaking> SEEN_TAKING = EventFactory.createArrayBacked(SeenTaking.class, listeners -> (player, chest, taken, saw) -> {
		for (SeenTaking listener : listeners) {
			listener.onSeenTaking(player, chest, taken, saw);
		}
	});

	@FunctionalInterface
	public interface SeenTaking {
		void onSeenTaking(Player player, net.minecraft.core.BlockPos chest, java.util.List<ItemStack> taken, java.util.List<DrifterEntity> saw);
	}

	/**
	 * A player rang a bell as an alarm (struck it fast, five times: 0.23.35), once a ringing: the bell. Whether that was for
	 * nothing (a false alarm) is the listener's call.
	 */
	public static final Event<AlarmRung> ALARM_RUNG = EventFactory.createArrayBacked(AlarmRung.class, listeners -> (player, bell) -> {
		for (AlarmRung listener : listeners) {
			listener.onAlarmRung(player, bell);
		}
	});

	@FunctionalInterface
	public interface AlarmRung {
		void onAlarmRung(Player player, net.minecraft.core.BlockPos bell);
	}

	/** A player threw a Drifter something and it picked it up (what it took of it). */
	public static final Event<Received> RECEIVED = EventFactory.createArrayBacked(Received.class, listeners -> (drifter, player, stack) -> {
		for (Received listener : listeners) {
			listener.onReceived(drifter, player, stack);
		}
	});

	@FunctionalInterface
	public interface Received {
		void onReceived(DrifterEntity drifter, Player player, net.minecraft.world.item.ItemStack stack);
	}

	@FunctionalInterface
	public interface Died {
		void onDied(DrifterEntity drifter, DamageSource source, boolean comesBack);
	}

	@FunctionalInterface
	public interface Respawned {
		void onRespawned(DrifterEntity drifter);
	}

	@FunctionalInterface
	public interface HomeSet {
		void onHomeSet(DrifterEntity drifter, @Nullable Home home);
	}

	@FunctionalInterface
	public interface GroupChanged {
		void onGroupChanged(UUID member, @Nullable UUID group);
	}

	@FunctionalInterface
	public interface PlayerDeed {
		void onPlayerDeed(DrifterEntity drifter, Player player, SocialEvent deed, double trustAfter);
	}

	@FunctionalInterface
	public interface Traded {
		void onTraded(DrifterEntity asker, DrifterEntity giver, ItemStack paid, ItemStack got);
	}

	@FunctionalInterface
	public interface Built {
		void onBuilt(DrifterEntity drifter, String what, net.minecraft.core.BlockPos where);
	}

	@FunctionalInterface
	public interface Feud {
		void onFeud(UUID a, UUID b, String what);
	}

	/** A player answered a Drifter's question (a topic's, or one it asked itself): {@code does} is what a yes means ("team", "opinion"...). */
	@FunctionalInterface
	public interface PlayerAnswered {
		void onAnswered(DrifterEntity drifter, net.minecraft.server.level.ServerPlayer player, String topic, String does, boolean yes);
	}

	@FunctionalInterface
	public interface HeardNews {
		void onHeardNews(DrifterEntity drifter, News news, @Nullable DrifterEntity from);
	}
}
