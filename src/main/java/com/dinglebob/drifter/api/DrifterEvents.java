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
	public interface HeardNews {
		void onHeardNews(DrifterEntity drifter, News news, @Nullable DrifterEntity from);
	}
}
