package com.dinglebob.drifter.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Seen taking from a chest (the user, 2026-11-07: "we need to enforce and up crime awareness ... especially theft"): a
 * player opens a chest or a barrel; what's in it is noted, and who among the Drifters near can see them (then or while
 * it's open); when they close it, what they took out is told to whoever listens ({@link DrifterEvents#SEEN_TAKING}),
 * with the Drifters who saw them at it. Only what was seen: nobody looking, nothing told. Whose chest it was and whether
 * it was theft is for the listener (a town's justice) to say.
 */
public final class ChestWatch {
	private ChestWatch() {
	}

	/** Drifters this near the chest may see them at it. */
	private static final double WITNESS_RANGE = 24.0;
	/** Ticks for the chest's screen to open after the click, before it's given up on. */
	private static final int OPEN_WAIT = 10;

	private static final class Open {
		final ServerPlayer player;
		final BlockPos pos;
		final long since;
		@Nullable
		Container container;
		Map<Item, Integer> before = Map.of();
		final Set<DrifterEntity> saw = new LinkedHashSet<>();

		Open(ServerPlayer player, BlockPos pos, long since) {
			this.player = player;
			this.pos = pos;
			this.since = since;
		}
	}

	private static final Map<UUID, Open> open = new HashMap<>();

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (open.isEmpty()) {
				return;
			}

			for (Iterator<Open> it = open.values().iterator(); it.hasNext();) {
				Open one = it.next();
				ServerPlayer player = one.player;
				if (!(player.level() instanceof ServerLevel level) || player.isRemoved()) {
					it.remove();
					continue;
				}

				long now = level.getGameTime();
				if (one.container == null) {
					if (player.containerMenu instanceof ChestMenu menu) {
						one.container = menu.getContainer();
						one.before = counts(one.container);
						watch(level, one);
					} else if (now - one.since > OPEN_WAIT) {
						it.remove();
					}

					continue;
				}

				if (player.containerMenu instanceof ChestMenu menu && menu.getContainer() == one.container) {
					if ((now + player.getId()) % 10 == 0) {
						watch(level, one);
					}

					continue;
				}

				// Closed: what came out of it.
				it.remove();
				watch(level, one);
				Map<Item, Integer> after = counts(one.container);
				List<ItemStack> taken = new ArrayList<>();
				one.before.forEach((item, count) -> {
					int gone = count - after.getOrDefault(item, 0);
					if (gone > 0) {
						taken.add(new ItemStack(item, gone));
					}
				});

				List<DrifterEntity> saw = one.saw.stream().filter(DrifterEntity::isAlive).toList();
				if (!taken.isEmpty() && !saw.isEmpty()) {
					DrifterEvents.SEEN_TAKING.invoker().onSeenTaking(player, one.pos, taken, saw);
				}
			}
		});
	}

	/** A player used this block (a chest or a barrel): watched while its screen is open. */
	static void used(ServerPlayer player, BlockPos pos) {
		open.put(player.getUUID(), new Open(player, pos.immutable(), player.level().getGameTime()));
	}

	/** The Drifters near who can see them now join those who saw. */
	private static void watch(ServerLevel level, Open one) {
		for (DrifterEntity drifter : level.getEntitiesOfClass(DrifterEntity.class, new AABB(one.pos).inflate(WITNESS_RANGE), DrifterEntity::isAlive)) {
			if (drifter.getSocial().watcher().sees(one.player)) {
				one.saw.add(drifter);
			}
		}
	}

	private static Map<Item, Integer> counts(Container container) {
		Map<Item, Integer> counts = new HashMap<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		return counts;
	}
}
