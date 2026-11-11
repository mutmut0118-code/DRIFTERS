package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A town's notices (milestone 23 D5; the user, 2026-11-06: "only d1 and d5"): real needs, posted by real people, in player
 * speak. Each morning its keeper (or leader) posts what the town's short of ("need 32 iron, paying 9 emeralds"), when it
 * and the till have the money; throw the poster that many (to it, in one go or several) and it pays you there and then,
 * from its own money first, then the till's. A warning goes up when there are bandits about ("bandits up north, watch
 * yourselves"). They're in the Gazette, on the plaza board's last sign, in `/drifterciv town`, and with Bountiful on the town's
 * bounty board as real bounties ({@link TownBounties}); taken down when done or after three days.
 */
final class TownNotices {
	private TownNotices() {
	}

	/** A notice: who posted it, what for, how many, for how much (money points), its words, when. */
	static final class Notice {
		final UUID poster;
		final String posterName;
		final String item;
		final int count;
		final double points;
		final String text;
		final long day;
		/** What each player has handed over toward it so far. */
		final Map<UUID, Integer> delivered = new HashMap<>();
		/** Its own id (its paper on a Bountiful board is marked with it: TownBounties). */
		UUID id = UUID.randomUUID();

		Notice(UUID poster, String posterName, String item, int count, double points, String text, long day) {
			this.poster = poster;
			this.posterName = posterName;
			this.item = item;
			this.count = count;
			this.points = points;
			this.text = text;
			this.day = day;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Poster", this.poster);
			tag.putString("PosterName", this.posterName);
			tag.putString("Item", this.item);
			tag.putInt("Count", this.count);
			tag.putDouble("Points", this.points);
			tag.putString("Text", this.text);
			tag.putLong("Day", this.day);
			tag.putUUID("Id", this.id);
			CompoundTag given = new CompoundTag();
			this.delivered.forEach((id, n) -> given.putInt(id.toString(), n));
			tag.put("Delivered", given);
			return tag;
		}

		static Notice load(CompoundTag tag) {
			Notice notice = new Notice(tag.getUUID("Poster"), tag.getString("PosterName"), tag.getString("Item"), tag.getInt("Count"),
				tag.getDouble("Points"), tag.getString("Text"), tag.getLong("Day"));
			if (tag.hasUUID("Id")) {
				notice.id = tag.getUUID("Id");
			}

			CompoundTag given = tag.getCompound("Delivered");
			for (String key : given.getAllKeys()) {
				try {
					notice.delivered.put(UUID.fromString(key), given.getInt(key));
				} catch (IllegalArgumentException ignored) {
				}
			}

			return notice;
		}
	}

	/** Notices up at once at most, and how long one stays up (days). */
	private static final int MOST = 3;
	static final int STAYS = 3;
	/**
	 * What it'll pay over an item's worth (a request pays a good deal better than the stall), and the least a notice pays
	 * (money points: 3 emeralds; the user, 2026-11-07, "yes please" to more than "16 paper for 1 emerald").
	 */
	private static final double PREMIUM = 1.6;
	private static final double LEAST = 18.0;
	/** Bandits within this of the town: a warning. */
	private static final double BANDITS_NEAR = 300.0;

	static void register() {
		DrifterEvents.RECEIVED.register(TownNotices::received);
	}

	/** Each check: the morning's notices (old ones down, new ones up). */
	static void check(MinecraftServer server, Town town) {
		long day = Chronicle.day(server);
		long time = server.overworld().getDayTime() % 24000L;
		if (town.noticeDay >= day || time < 1000L || time > 6000L || town.group == null) {
			return;
		}

		town.noticeDay = day;
		town.notices.removeIf(notice -> day - notice.day >= STAYS || notice.count <= 0);
		town.warning = bandits(server, town);
		DrifterEntity poster = poster(server, town);
		if (poster != null && town.notices.size() < MOST) {
			Home home = DrifterApi.home(poster);
			List<Item> wants = new ArrayList<>(Market.wanted());
			wants.addAll(List.of(Items.GLASS, Items.STONE_BRICKS, Items.WHITE_WOOL, Items.STRING, Items.IRON_INGOT, Items.LEATHER, Items.PAPER));
			java.util.Collections.shuffle(wants, new java.util.Random(day * 31 + town.id.hashCode()));
			for (Item item : wants) {
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				if (town.notices.size() >= MOST || town.notices.stream().anyMatch(n -> n.item.equals(id)) || home != null && home.stored(item) >= 32) {
					continue;
				}

				int count = 8 * (2 + poster.getRandom().nextInt(5));
				double points = Math.max(LEAST, Math.round(DrifterApi.worth(new ItemStack(item)) * count * PREMIUM));
				if (money(poster, town, server) < points) {
					continue;
				}

				String what = count + " " + new ItemStack(item).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				String pay = describe(DrifterApi.money(points));
				String[] says = {"need " + what + ", paying " + pay, "anyone got " + what + "? " + pay, "buying " + what + " for " + pay,
					"looking for " + what + ", will pay " + pay};
				String text = says[poster.getRandom().nextInt(says.length)] + " - " + poster.getName().getString();
				town.notices.add(new Notice(poster.getUUID(), poster.getName().getString(), id, count, points, text, day));
				DrifterApi.think(poster, "put up a notice: " + text);
				break;
			}
		}

		Towns.save(server);
	}

	/** Who posts the town's notices: its keeper, else its leader (loaded). */
	@Nullable
	private static DrifterEntity poster(MinecraftServer server, Town town) {
		DrifterEntity keeper = town.keeper == null ? null : TownFounding.loaded(server, town.keeper);
		return keeper != null ? keeper : town.leader == null ? null : TownFounding.loaded(server, town.leader);
	}

	/** Bandits about (a Bandit Drifter loaded within reach of the town): the warning, which way; else null. */
	@Nullable
	private static String bandits(MinecraftServer server, Town town) {
		var level = server.overworld();
		for (DrifterEntity one : level.getEntitiesOfClass(DrifterEntity.class, new net.minecraft.world.phys.AABB(town.center).inflate(BANDITS_NEAR, 64, BANDITS_NEAR),
			DrifterEntity::isAlive)) {
			if (DrifterApi.bandit(one) && !town.crooks.contains(one.getUUID())) {
				double dx = one.getX() - town.center.getX();
				double dz = one.getZ() - town.center.getZ();
				String way = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
				return "bandits up " + way + ", watch yourselves";
			}
		}

		return null;
	}

	/** Money it could pay with: its own, and the till's. */
	private static double money(DrifterEntity poster, Town town, MinecraftServer server) {
		double has = 0;
		for (int slot = 0; slot < poster.getInventory().getContainerSize(); slot++) {
			has += DrifterApi.moneyWorth(poster.getInventory().getItem(slot));
		}

		Container till = till(server, town);
		if (till != null) {
			for (int slot = 0; slot < till.getContainerSize(); slot++) {
				has += DrifterApi.moneyWorth(till.getItem(slot));
			}
		}

		return has;
	}

	@Nullable
	static Container till(MinecraftServer server, Town town) {
		BlockPos at = town.till();
		return at != null && server.overworld().isLoaded(at) && server.overworld().getBlockEntity(at) instanceof Container chest ? chest : null;
	}

	/** A player threw a Drifter something: toward a notice of its? All of it in: it pays them. */
	private static void received(DrifterEntity drifter, Player player, ItemStack stack) {
		MinecraftServer server = drifter.getServer();
		if (server == null) {
			return;
		}

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		for (Town town : Towns.all()) {
			for (Notice notice : List.copyOf(town.notices)) {
				if (!notice.poster.equals(drifter.getUUID()) || !notice.item.equals(id)) {
					continue;
				}

				int given = notice.delivered.merge(player.getUUID(), stack.getCount(), Integer::sum);
				if (given < notice.count) {
					com.dinglebob.drifter.social.ChatLines.speak(drifter, CivLines.pick(drifter, "town.notice.progress", given + " of " + notice.count + ", keep em coming",
						"given", given, "count", notice.count));
					Towns.save(server);
					return;
				}

				pay(server, town, drifter, player, notice.points);
				town.notices.remove(notice);
				Chronicle.add(server, "town", player.getGameProfile().getName() + " brought " + notice.posterName + " the " + notice.count + " "
					+ new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(notice.item))).getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
					+ " " + town.name + " needed");
				DrifterApi.addTrustInPlayer(drifter, player.getUUID(), player.getGameProfile().getName(), 10.0);
				Towns.save(server);
				return;
			}
		}
	}

	/** It pays the player this much: its own money first, then the till's, handed over (tossed to them). */
	private static void pay(MinecraftServer server, Town town, DrifterEntity poster, Player player, double points) {
		List<ItemStack> paid = new ArrayList<>();
		double left = points;
		left = take(poster.getInventory(), left, paid);
		Container till = till(server, town);
		if (left > 0 && till != null) {
			left = take(till, left, paid);
			till.setChanged();
		}

		poster.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
		// (Alike together: a handful of emeralds, not one at a time.)
		List<ItemStack> handed = new ArrayList<>();
		for (ItemStack stack : paid) {
			ItemStack into = handed.stream().filter(h -> ItemStack.isSameItemSameComponents(h, stack) && h.getCount() < h.getMaxStackSize()).findFirst().orElse(null);
			if (into != null) {
				into.grow(stack.getCount());
			} else {
				handed.add(stack.copy());
			}
		}

		for (ItemStack stack : handed) {
			poster.tossForward(stack);
		}

		com.dinglebob.drifter.social.ChatLines.speak(poster, left > 0 ? CivLines.pick(poster, "town.notice.short", "thats all i have, sorry")
			: CivLines.pick(poster, "town.notice.paid", "thanks! here, as promised"));
	}

	/** Money out of this container, a piece at a time, till it's worth {@code points}; what's still owed. */
	static double take(Container from, double points, List<ItemStack> into) {
		for (int slot = 0; slot < from.getContainerSize() && points > 0; slot++) {
			ItemStack stack = from.getItem(slot);
			double each = stack.isEmpty() ? 0 : DrifterApi.moneyWorth(stack.copyWithCount(1));
			while (each > 0 && !stack.isEmpty() && points > 0) {
				into.add(stack.split(1));
				points -= each;
			}
		}

		return points;
	}

	static String describe(ItemStack money) {
		return money.getCount() + " " + money.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}

	/** Its notices up now, said (and the warning). */
	static List<String> lines(Town town) {
		List<String> lines = new ArrayList<>();
		if (town.warning != null) {
			lines.add(town.warning);
		}

		for (Notice notice : town.notices) {
			lines.add(notice.text);
		}

		return lines;
	}
}
