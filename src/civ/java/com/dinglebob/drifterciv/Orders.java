package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TalkApi;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Made to order (shops worth more than looting, part 3, the first of the things only money buys; the user, 2026-11-12:
 * "Yes, those three", "Yes, build it", "Yes, go on"): tell a smith what you want ("make me an iron chestplate"; "... in
 * your colours" for its town's trim), throw it half the price, and it makes it the way it makes anything it sells (its
 * own stock, or what you bring it: throw it the diamonds for a diamond piece). The piece is put by for you the moment it
 * has one new (never sold to another, never worn); come by, pay the rest, and it's handed over, new, with its maker's
 * line. The one way to get exactly the piece you want without making it yourself.
 *
 * <ul>
 * <li>Asked and not paid for in two minutes: forgotten. One order a player with each smith.</li>
 * <li>Not made in {@link #MAKES_IN} days (it hasn't what it takes): it says so and gives the deposit back, what it has of it.</li>
 * <li>Made and not fetched in {@link #KEEPS} days: back into its stock; the deposit is its own.</li>
 * <li>The smith gone: the order with it (the journal says so).</li>
 * </ul>
 */
final class Orders {
	private Orders() {
	}

	static final int ASKED = 0;
	static final int PLACED = 1;
	static final int READY = 2;

	/** Half down. */
	private static final double DEPOSIT = 0.5;
	/** Asked and not paid for: forgotten after this long. */
	private static final long ASK_LASTS = 20L * 120;
	/** Days it has to make it; days it keeps it made. */
	private static final long MAKES_IN = 7;
	private static final long KEEPS = 10;
	/** It says it's ready (or what's still to pay) to its buyer this near, not more often than this. */
	private static final double NEAR = 8.0;
	private static final long SAYS_EVERY = 20L * 90;

	static final class Order {
		final UUID player;
		final String playerName;
		final UUID smith;
		final String smithName;
		final UUID town;
		final String townName;
		final Item item;
		final boolean trim;
		/** The whole price, and what's been paid of it (money points: an emerald is 6). */
		final double price;
		double paid;
		int state = ASKED;
		/** When it was asked (game time), the day it was placed, the day it was made. */
		final long at;
		long day;
		/** The piece, once made: put by, out of its pockets. */
		ItemStack piece = ItemStack.EMPTY;
		long said = Long.MIN_VALUE / 2;

		Order(UUID player, String playerName, UUID smith, String smithName, UUID town, String townName, Item item, boolean trim, double price, long at) {
			this.player = player;
			this.playerName = playerName;
			this.smith = smith;
			this.smithName = smithName;
			this.town = town;
			this.townName = townName;
			this.item = item;
			this.trim = trim;
			this.price = price;
			this.at = at;
		}

		String what() {
			return name(this.item);
		}

		double owed() {
			return Math.max(0, this.price - this.paid);
		}
	}

	private static final List<Order> orders = new ArrayList<>();

	static void register() {
		DrifterEvents.RECEIVED.register(Orders::received);
		// A smith dying: what it had put by for its orders is on it again, and falls with the rest; its orders die with it.
		DrifterEvents.DYING.register((drifter, source) -> {
			boolean any = false;
			for (Order order : List.copyOf(orders)) {
				if (order.smith.equals(drifter.getUUID())) {
					keep(drifter, order.piece);
					order.piece = ItemStack.EMPTY;
					orders.remove(order);
					any = true;
				}
			}

			if (any && drifter.getServer() != null) {
				save(drifter.getServer());
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(Orders::load);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> orders.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 61) {
				tick(server);
			}
		});
		// Debug: `/drifterciv order` lists them; `order stock` gives the smith of your open order the piece (from nothing:
		// for testing only), so it's ready at the next check.
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("drifterciv")
			.then(Commands.literal("order").requires(source -> source.hasPermission(2)).executes(ctx -> {
				List<String> lines = new ArrayList<>();
				for (Order o : orders) {
					lines.add(o.playerName + ": " + o.what() + (o.trim ? " (trimmed)" : "") + " from " + o.smithName + " of " + o.townName + ", "
						+ (o.state == ASKED ? "asked" : o.state == PLACED ? "being made" : "ready") + ", paid " + o.paid + " of " + o.price);
				}

				DrifterCivilizations.LOGGER.info("[orders] {}", String.join(" | ", lines));
				ctx.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "No orders." : String.join("\n", lines)), false);
				return 1;
			}).then(Commands.literal("stock").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				int n = 0;
				for (Order o : orders) {
					DrifterEntity smith = o.player.equals(player.getUUID()) && o.state == PLACED ? TownFounding.loaded(ctx.getSource().getServer(), o.smith) : null;
					if (smith != null) {
						smith.getInventory().add(new ItemStack(o.item));
						n++;
					}
				}

				int given = n;
				ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + given + " ordered pieces put in their smiths' pockets"), true);
				return n;
			})))));
	}

	private static String name(Item item) {
		return new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
	}

	/** This much as it's paid: in whole pieces of money (what {@link #pay} says of it). */
	private static double whole(double points) {
		return DrifterApi.moneyWorth(DrifterApi.money(Math.max(0.0, points)));
	}

	/** The half to pay first, in whole pieces of money. */
	private static double deposit(double price) {
		return Math.min(price, whole(price * DEPOSIT));
	}

	/** Words that make "make me ..." plainly an order when no piece is named. */
	private static final List<String> ABOUT = List.of("order", "armor", "armour", "weapon", "sword", "tool", "tools", "gear", "something", "anything", "helmet",
		"chestplate", "leggings", "boots", "pickaxe", "axe", "shield");

	/** An item's name as it's matched in what was said: letters, digits and spaces only. */
	private static String plainName(Item item) {
		return name(item).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
	}

	/** Into its pockets, or at its feet if they're full: never lost. */
	private static void keep(DrifterEntity smith, ItemStack stack) {
		if (!stack.isEmpty() && !smith.getInventory().add(stack) && !stack.isEmpty()) {
			smith.spawnAtLocation(stack);
		}
	}

	private static String pay(double points) {
		return TownNotices.describe(DrifterApi.money(Math.max(0.0, points)));
	}

	@Nullable
	private static Order of(UUID smith, UUID player) {
		for (Order o : orders) {
			if (o.smith.equals(smith) && o.player.equals(player)) {
				return o;
			}
		}

		return null;
	}

	/**
	 * What its smith has orders for and hasn't made yet (its trade makes them: {@link Trades#wares}). One it has new in its
	 * pockets is put by here and now, as it thinks what to do next (found in testing: the helmet it had just made for an
	 * order it put on its own head before the next check, and made another).
	 */
	static List<Item> wanted(DrifterEntity smith) {
		List<Item> items = new ArrayList<>();
		MinecraftServer server = smith.getServer();
		for (Order o : orders) {
			if (o.state != PLACED || !o.smith.equals(smith.getUUID())) {
				continue;
			}

			Town town = server == null ? null : Diplomacy.byId(o.town.toString());
			if (town != null && fill(server, o, smith, town)) {
				save(server);
			} else if (!items.contains(o.item)) {
				items.add(o.item);
			}
		}

		return items;
	}

	/** One new plain piece out of its pockets, put by for this order (true: it's ready). */
	private static boolean fill(MinecraftServer server, Order order, DrifterEntity smith, Town town) {
		ItemStack made = Trades.takeNewAny(smith, order.item);
		if (made.isEmpty()) {
			return false;
		}

		order.piece = made;
		if (order.trim) {
			DrifterApi.trim(server.registryAccess(), order.piece, TownUniforms.uniform(town));
		}

		Trades.madeBy(order.piece, smith, town);
		order.state = READY;
		order.day = Chronicle.day(server);
		order.said = Long.MIN_VALUE / 2;
		DrifterApi.think(smith, "put " + order.what() + " by for " + order.playerName);
		return true;
	}

	// ---------------------------------------------------------------------------------------------
	// Asking
	// ---------------------------------------------------------------------------------------------

	/** Said to a townsperson: "make me a ..." (true: answered). */
	static boolean heard(ServerPlayer player, DrifterEntity drifter, String text, @Nullable String intent) {
		Town town = Market.townOf(drifter);
		MinecraftServer server = drifter.getServer();
		if (town == null || server == null || TalkApi.better(text, intent, "order") == null) {
			return false;
		}

		// (Only when it's plainly about having a thing made: "you make me laugh" isn't. The thing named is one a smith of the
		// town makes, or it's said in so many words.)
		String said = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ") + " ";
		boolean smithHere = "smith".equals(town.lines.get(drifter.getUUID()));
		List<Item> makes = new ArrayList<>(Trades.makes(town, drifter));
		if (!smithHere) {
			for (var entry : town.lines.entrySet()) {
				DrifterEntity other = entry.getValue().equals("smith") ? TownFounding.loaded(server, entry.getKey()) : null;
				if (other != null) {
					makes.addAll(Trades.makes(town, other));
				}
			}
		}

		Item item = makes.stream().filter(one -> said.contains(" " + plainName(one) + " ")).max(Comparator.comparingInt(one -> plainName(one).length())).orElse(null);
		boolean plainly = item != null || ABOUT.stream().anyMatch(word -> said.contains(" " + word + " "));
		if (!plainly) {
			return false;
		}

		if (!smithHere) {
			return TalkApi.answer(drifter, player, "order.notsmith", null);
		}

		TownReputation.Standing standing = TownReputation.of(town, player);
		if (standing == TownReputation.Standing.WANTED || standing == TownReputation.Standing.UNWELCOME) {
			return TalkApi.answer(drifter, player, "order.no", null);
		}

		Order open = of(drifter.getUUID(), player.getUUID());
		if (open != null && open.state != ASKED) {
			return TalkApi.answer(drifter, player, open.state == READY ? "order.ready" : "order.already", null, "what", open.what(), "pay", pay(open.owed()));
		}

		// Which of the things it makes: the longest name said.
		if (item == null) {
			if (makes.isEmpty()) {
				return TalkApi.answer(drifter, player, "order.no", null);
			}

			// (A few, in one breath: a long list with commas was said in pieces. Found in testing.)
			List<String> names = makes.stream().map(Orders::name).limit(5).toList();
			return TalkApi.answer(drifter, player, "order.what", null, "goods", String.join(" or ", names) + (makes.size() > 5 ? " and more" : ""));
		}

		ItemStack plain = new ItemStack(item);
		boolean trim = (said.contains(" colours ") || said.contains(" colors ") || said.contains(" trim ") || said.contains(" trimmed "))
			&& plain.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR);
		// (In whole pieces of money, so what it says and what it takes are the same: found in testing, "half now, 1 emerald",
		// then "1 emerald more".)
		double price = whole(Math.max(1, Market.price(drifter, item) * Friends.price(drifter, player, standing.prices)));
		double thrown = open == null ? 0.0 : open.paid;
		orders.remove(open);
		Order order = new Order(player.getUUID(), player.getGameProfile().getName(), drifter.getUUID(), drifter.getName().getString(), town.id, town.name, item,
			trim, price, server.overworld().getGameTime());
		// (Asked again, or for something else: what they'd thrown toward the first ask counts toward this.)
		order.paid = thrown;
		orders.add(order);
		DrifterApi.think(drifter, player.getGameProfile().getName() + " asked for " + order.what() + " made");
		return TalkApi.answer(drifter, player, trim ? "order.quote.trim" : "order.quote", null, "what", order.what(), "pay", pay(price), "half",
			pay(deposit(price)));
	}

	// ---------------------------------------------------------------------------------------------
	// Paying
	// ---------------------------------------------------------------------------------------------

	private static void say(DrifterEntity drifter, String line) {
		com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
	}

	/** Money thrown to a smith by one with an order there: toward it (not while it has a job of theirs in hand: that's the job's). */
	private static void received(DrifterEntity drifter, Player player, ItemStack stack) {
		MinecraftServer server = drifter.getServer();
		Order order = of(drifter.getUUID(), player.getUUID());
		double worth = DrifterApi.moneyWorth(stack);
		if (server == null || order == null || worth <= 0 || TownServices.takesMoney(drifter, player) || order.paid >= order.price) {
			return;
		}

		order.paid += worth;
		String name = player.getGameProfile().getName();
		if (order.state == ASKED) {
			if (order.paid + 1.0e-6 < deposit(order.price)) {
				say(drifter, CivLines.pick(drifter, "order.more", pay(deposit(order.price) - order.paid) + " more and ill start on it", "pay",
					pay(deposit(order.price) - order.paid), "player", name, "what", order.what()));
			} else {
				order.state = PLACED;
				order.day = Chronicle.day(server);
				say(drifter, CivLines.pick(drifter, "order.placed", "right, " + order.what() + ". come back in a day or two", "player", name, "what", order.what()));
				DrifterApi.think(drifter, "took an order: " + order.what() + " for " + name);
			}
		} else if (order.state == READY) {
			handOver(server, order, drifter, player);
		}

		save(server);
	}

	// ---------------------------------------------------------------------------------------------
	// Making, and handing over
	// ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		long day = Chronicle.day(server);
		boolean changed = false;
		for (Order order : List.copyOf(orders)) {
			if (order.state == ASKED) {
				if (now - order.at > ASK_LASTS) {
					// (What was thrown short of the half: handed back if they're by, else it's with the smith, as any gift.)
					orders.remove(order);
					DrifterEntity smith = TownFounding.loaded(server, order.smith);
					ServerPlayer player = server.getPlayerList().getPlayer(order.player);
					if (order.paid > 0 && smith != null && player != null && player.level() == smith.level() && player.distanceTo(smith) <= 16.0) {
						refund(smith, player, order);
					}

					changed = true;
				}

				continue;
			}

			var record = DrifterApi.record(order.smith);
			Town town = Diplomacy.byId(order.town.toString());
			DrifterEntity smith = TownFounding.loaded(server, order.smith);
			boolean gone = record != null && record.status() == com.dinglebob.drifter.api.DrifterRecord.Status.GONE;
			if (smith != null && (town == null || !"smith".equals(town.lines.get(order.smith)))) {
				// (A smith no more, or its town gone: the piece is its own again, plain; the money back if they're by.)
				if (!order.piece.isEmpty()) {
					keep(smith, new ItemStack(order.item));
					order.piece = ItemStack.EMPTY;
				}

				ServerPlayer buyer = server.getPlayerList().getPlayer(order.player);
				if (buyer != null && buyer.level() == smith.level() && buyer.distanceTo(smith) <= 16.0) {
					refund(smith, buyer, order);
				}

				orders.remove(order);
				changed = true;
				continue;
			}

			if (smith == null && gone && order.piece.isEmpty()) {
				// (Gone for good with nothing put by: nothing to wait for. One with a piece put by died with it on it: DYING.)
				orders.remove(order);
				changed = true;
				continue;
			}

			if (smith == null) {
				continue;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(order.player);
			boolean near = player != null && player.level() == smith.level() && player.distanceTo(smith) <= NEAR && smith.hasLineOfSight(player);
			if (order.state == PLACED) {
				if (fill(server, order, smith, town)) {
					changed = true;
				} else if (day - order.day > MAKES_IN * 3) {
					// (Never made and never come back for: let go; the deposit is the smith's.)
					orders.remove(order);
					changed = true;
					continue;
				} else if (day - order.day > MAKES_IN && near) {
					// It couldn't: the deposit back, what it has of it.
					say(smith, CivLines.pick(smith, "order.cant", "sorry, i couldnt get what it takes for the " + order.what() + ". heres your money", "player",
						order.playerName, "what", order.what()));
					refund(smith, player, order);
					orders.remove(order);
					changed = true;
					continue;
				}
			}

			if (order.state == READY) {
				if (day - order.day > KEEPS) {
					// Not fetched: it's the smith's to sell again, as it was made (its colours off: plain, to its stock).
					keep(smith, new ItemStack(order.item));
					order.piece = ItemStack.EMPTY;
					orders.remove(order);
					changed = true;
				} else if (near) {
					handOver(server, order, smith, player);
				}
			}
		}

		if (changed) {
			save(server);
		}
	}

	/** Its buyer by it, the piece made: paid in full, handed over; else what's still to pay, said now and then. */
	private static void handOver(MinecraftServer server, Order order, DrifterEntity smith, Player player) {
		long now = server.overworld().getGameTime();
		if (order.paid + 1.0e-6 < order.price) {
			if (now - order.said >= SAYS_EVERY) {
				order.said = now;
				say(smith, CivLines.pick(smith, "order.done.pay", "your " + order.what() + " is ready. " + pay(order.owed()) + " more and its yours", "player",
					order.playerName, "what", order.what(), "pay", pay(order.owed())));
			}

			return;
		}

		TownServices.giveBack(smith, player, order.piece);
		say(smith, CivLines.pick(smith, "order.done", "here it is, " + order.playerName + ". made it myself", "player", order.playerName, "what", order.what()));
		DrifterApi.think(smith, "handed " + order.playerName + " the " + order.what() + " it ordered");
		Town town = Diplomacy.byId(order.town.toString());
		if (town != null) {
			town.trades++;
			Market.sold(town, order.item);
		}

		orders.remove(order);
		save(server);
	}

	/** What was paid toward it, back out of its pockets (as much of it as it has). */
	private static void refund(DrifterEntity smith, Player player, Order order) {
		// (As the piece of money that's worth it, out of its pockets: whole emeralds only left coins' small change with it.)
		ItemStack owed = order.paid <= 0 ? ItemStack.EMPTY : DrifterApi.money(order.paid);
		order.paid = 0;
		if (owed.isEmpty()) {
			return;
		}

		var pockets = smith.getInventory();
		int left = owed.getCount();
		smith.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
		for (int slot = 0; slot < pockets.getContainerSize() && left > 0; slot++) {
			if (pockets.getItem(slot).is(owed.getItem())) {
				ItemStack money = pockets.removeItem(slot, Math.min(left, pockets.getItem(slot).getCount()));
				left -= money.getCount();
				smith.tossForward(money).setTarget(player.getUUID());
			}
		}

		// (What it hasn't in that piece: in whatever money it has, to the emerald.)
		int more = left <= 0 ? 0 : (int)Math.floor(DrifterApi.moneyWorth(owed.copyWithCount(left)) / 6.0);
		int paid = more <= 0 ? 0 : DrifterApi.moneyTake(pockets, more);
		for (ItemStack money : DrifterApi.moneyStacks(paid)) {
			smith.tossForward(money).setTarget(player.getUUID());
		}
	}

	/** For the journal's Quests page: what they've ordered and how it stands. */
	static List<Journal.Line> lines(ServerPlayer player) {
		List<Journal.Line> lines = new ArrayList<>();
		for (Order o : orders) {
			if (o.player.equals(player.getUUID()) && o.state != ASKED) {
				String title = Character.toUpperCase(o.what().charAt(0)) + o.what().substring(1) + ", from " + o.smithName + " of " + o.townName;
				lines.add(new Journal.Line(title, o.state == READY ? "ready" : "being made", o.state == READY ? Journal.GOOD : Journal.PLAIN,
					List.of(o.owed() > 0 ? "Still to pay when you fetch it: " + pay(o.owed()) + "." : "Paid for.")));
			}
		}

		return lines;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved
	// ---------------------------------------------------------------------------------------------

	private static final int VERSION = 1;

	private static final class Saved extends SavedData {
		static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
			orders.clear();
			ListTag list = tag.getList("orders", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag o = list.getCompound(i);
				Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.getString("item")));
				int state = o.getInt("state");
				if (item == net.minecraft.world.item.Items.AIR || !o.hasUUID("player") || !o.hasUUID("smith") || !o.hasUUID("town") || state < PLACED || state > READY) {
					continue;
				}

				Order order = new Order(o.getUUID("player"), o.getString("playerName"), o.getUUID("smith"), o.getString("smithName"), o.getUUID("town"),
					o.getString("townName"), item, o.getBoolean("trim"), o.getDouble("price"), 0L);
				order.paid = o.getDouble("paid");
				order.state = o.getInt("state");
				order.day = o.getLong("day");
				if (o.contains("piece")) {
					order.piece = ItemStack.parse(registries, o.getCompound("piece")).orElse(ItemStack.EMPTY);
				}

				// (One made whose piece didn't load, a mod gone: to make again.)
				if (order.state == READY && order.piece.isEmpty()) {
					order.state = PLACED;
				}

				if (order.state != ASKED) {
					orders.add(order);
				}
			}

			return new Saved();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("version", VERSION);
			ListTag list = new ListTag();
			for (Order order : orders) {
				if (order.state == ASKED) {
					continue;
				}

				CompoundTag o = new CompoundTag();
				o.putUUID("player", order.player);
				o.putString("playerName", order.playerName);
				o.putUUID("smith", order.smith);
				o.putString("smithName", order.smithName);
				o.putUUID("town", order.town);
				o.putString("townName", order.townName);
				o.putString("item", BuiltInRegistries.ITEM.getKey(order.item).toString());
				o.putBoolean("trim", order.trim);
				o.putDouble("price", order.price);
				o.putDouble("paid", order.paid);
				o.putInt("state", order.state);
				o.putLong("day", order.day);
				if (!order.piece.isEmpty()) {
					o.put("piece", order.piece.save(registries));
				}

				list.add(o);
			}

			tag.put("orders", list);
			return tag;
		}
	}

	private static Saved saved(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Saved::new, Saved::load, null), "drifter_civ_orders");
	}

	static void load(MinecraftServer server) {
		orders.clear();
		saved(server);
	}

	private static void save(MinecraftServer server) {
		saved(server).setDirty();
	}
}
