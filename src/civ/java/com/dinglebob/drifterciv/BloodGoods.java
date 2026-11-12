package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TownUniform;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Blood goods (shops worth more than looting, part 2; the user, 2026-11-12: "Yes, those three", "Yes, build it"): the
 * armour in a town's colours that one of its people died in is known for whose it was.
 *
 * <ul>
 * <li><b>Marked as it falls</b> ({@link DrifterEvents#DYING}): each piece it wore carrying its town's trim, with whose it
 *     was and of which town, and a line under its name saying so ("Worn by Ansel of Hollowbrook"). Nothing else is: armour
 *     with no trim, a wanderer's, a bandit's.</li>
 * <li><b>Only by being seen:</b> one of that town's people who sees a player wearing or holding it. Then, once a piece,
 *     those who see it think the less of them (twice over for one known there as a killer of its people), and a guard who
 *     sees it asks for it back: thrown to anyone of the town within a minute, that's the end of it; kept, it's a fine, as
 *     theft is ({@link Justice}), and asked again another day.</li>
 * <li><b>Thrown to someone:</b> one of its own town takes it back (it's the town's again). A keeper or smith of another
 *     town of its nation won't touch it. A keeper of a town at war with its town buys it, a trophy. A smith of a town that
 *     doesn't care (at war with it, or nothing to it) puts its own colours on it for a fee ({@link TownServices}): then
 *     it's just armour.</li>
 * <li>Back on one of its own town's people (picked up where it fell), it's the town's again.</li>
 * </ul>
 */
final class BloodGoods {
	private BloodGoods() {
	}

	private static final String KEY = "drifter_civ_blood";
	private static final String WORN_BY = "Worn by ";
	/** Seen in it: each of the town's people who sees it trusts them this much less, a piece (no more than {@link #MOST} at once). */
	private static final double WARY = 10.0;
	private static final double MOST = 30.0;
	/** And the word about them in the town, a piece. */
	private static final double WORD = 5.0;
	/** A guard's ask: this long to hand it over; asked again (after a fine) not before this long. */
	private static final long HAND_OVER = 20L * 60;
	private static final long ASKS_AGAIN = 24000L;
	/** A trophy: this share of what the piece is worth new. */
	private static final double TROPHY = 0.5;
	/** Handing it back unasked: thought the better of, this much. */
	private static final double GAVE_BACK = 3.0;

	/** What became of a marked piece thrown to someone ({@link #received}). */
	static final int SEEN_TO = 0;
	static final int RETRIM = 1;
	static final int NOTHING = 2;

	/** A guard's ask still open: the town, the player, who asked, when. */
	private record Ask(UUID town, UUID player, UUID guard, long since) {
	}

	private static final Map<String, Ask> asks = new HashMap<>();
	private static final Map<String, Long> asked = new HashMap<>();

	static void register() {
		DrifterEvents.DYING.register(BloodGoods::dying);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 37) {
				tick(server);
			}
		});
		// Debug: `/drifterciv blood` what's on you and what's asked; `blood mark`: what you wear becomes a dead guard's of the
		// nearest town (its trim put on, from nothing: for testing only).
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("drifterciv").then(net.minecraft.commands.Commands.literal("blood").requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					List<String> lines = new ArrayList<>();
					for (EquipmentSlot slot : EquipmentSlot.values()) {
						describe(player.getItemBySlot(slot), "on you", lines);
					}

					for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
						describe(player.getInventory().getItem(slot), "carried", lines);
					}

					asks.values().forEach(ask -> lines.add("asked for by a guard of " + ask.town() + ", " + (player.level().getGameTime() - ask.since()) / 20 + "s ago"));
					Town near = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					if (near != null) {
						lines.add(near.name + ": " + TownReputation.describe(ctx.getSource().getServer(), near, player) + ", fine "
							+ (near.crimes.get(player.getUUID()) == null ? 0 : near.crimes.get(player.getUUID()).fine));
					}

					String out = lines.isEmpty() ? "Nothing of anyone's dead on you." : String.join("\n", lines);
					DrifterCivilizations.LOGGER.info("[blood] {}", String.join(" | ", lines));
					ctx.getSource().sendSuccess(() -> Component.literal(out), false);
					return 1;
				})
				.then(net.minecraft.commands.Commands.literal("mark").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Town town = Towns.nearest(player.level().dimension().location().toString(), player.blockPosition());
					if (town == null) {
						ctx.getSource().sendFailure(Component.literal("No town"));
						return 0;
					}

					int n = 0;
					for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
						ItemStack stack = player.getItemBySlot(slot);
						if (!stack.isEmpty() && DrifterApi.trim(player.level().registryAccess(), stack, TownUniforms.uniform(town))) {
							clear(stack);
							mark(stack, town, "Someone");
							n++;
						}
					}

					int marked = n;
					DrifterCivilizations.LOGGER.info("[blood] debug: {} worn pieces marked as {}'s dead's", marked, town.name);
					ctx.getSource().sendSuccess(() -> Component.literal("Debug: " + marked + " worn pieces are now a dead guard's of " + town.name), true);
					return n;
				})))));
	}

	// ---------------------------------------------------------------------------------------------
	// The mark
	// ---------------------------------------------------------------------------------------------

	@Nullable
	private static CompoundTag mark(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(KEY) ? null : data.copyTag().getCompound(KEY);
	}

	static boolean marked(ItemStack stack) {
		return mark(stack) != null;
	}

	/** The town it was one of (if it still stands), or null. */
	@Nullable
	private static Town townOf(@Nullable CompoundTag mark) {
		return mark == null ? null : Diplomacy.byId(mark.getString("town"));
	}

	private static void put(ItemStack stack, CompoundTag mark) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, mark));
	}

	/** Nobody's dead's any more: the mark and its line off. */
	static void clear(ItemStack stack) {
		if (!marked(stack)) {
			return;
		}

		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY));
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			List<Component> kept = lore.lines().stream().filter(line -> !line.getString().startsWith(WORN_BY)).toList();
			if (kept.isEmpty()) {
				stack.remove(DataComponents.LORE);
			} else {
				stack.set(DataComponents.LORE, new ItemLore(kept));
			}
		}
	}

	/** One of a town's people is dying: what it wears in its town's colours is known for whose it was. */
	private static void dying(DrifterEntity drifter, DamageSource source) {
		Town town = drifter.isTemporary() ? null : Justice.townOrWasOf(drifter);
		if (town == null) {
			return;
		}

		TownUniform now = TownUniforms.uniform(town);
		TownUniform own = DrifterApi.townUniform(town.id, town.uniformSet);
		String who = drifter.getName().getString();
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = drifter.getItemBySlot(slot);
			if (stack.isEmpty() || marked(stack) || !DrifterApi.wearsTrim(stack, now) && !DrifterApi.wearsTrim(stack, own)) {
				continue;
			}

			mark(stack, town, who);
		}
	}

	/** This piece is known for whose it was: the mark, and the line under its name. */
	private static void mark(ItemStack stack, Town town, String who) {
		CompoundTag mark = new CompoundTag();
		mark.putString("town", town.id.toString());
		mark.putString("who", who);
		put(stack, mark);
		List<Component> lines = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			lines.addAll(lore.lines());
		}

		lines.add(Component.literal(WORN_BY + who + " of " + town.name).withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	private static void describe(ItemStack stack, String where, List<String> lines) {
		CompoundTag mark = mark(stack);
		if (mark != null) {
			Town town = townOf(mark);
			lines.add(stack.getHoverName().getString() + " (" + where + "): " + mark.getString("who") + " of " + (town == null ? "a town that's gone" : town.name)
				+ (mark.getBoolean("seen") ? ", seen" : ""));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Seen in it
	// ---------------------------------------------------------------------------------------------

	private static String key(UUID town, UUID player) {
		return town + "/" + player;
	}

	private static void say(DrifterEntity drifter, String line) {
		com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
	}

	/** What they wear and hold, for anyone to see. */
	private static List<ItemStack> shown(Player player) {
		List<ItemStack> out = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			if (marked(stack)) {
				out.add(stack);
			}
		}

		return out;
	}

	/** Are they wearing or holding a piece of this town's dead, for anyone to see? (What's put away, nobody knows of.) */
	private static boolean shows(Player player, Town town) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (townOf(mark(player.getItemBySlot(slot))) == town) {
				return true;
			}
		}

		return false;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}

			Map<Town, List<ItemStack>> by = new HashMap<>();
			for (ItemStack stack : shown(player)) {
				Town town = townOf(mark(stack));
				if (town != null && town.dimension.equals(player.level().dimension().location().toString())) {
					by.computeIfAbsent(town, k -> new ArrayList<>()).add(stack);
				}
			}

			by.forEach((town, pieces) -> seen(server, town, player, pieces, now));
		}

		// Its own town's people with one on them again (picked up where it fell, handed back): the town's again.
		for (Town town : Towns.all()) {
			DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
			if (group == null) {
				continue;
			}

			for (UUID id : group.members) {
				DrifterEntity member = TownFounding.loaded(server, id);
				if (member == null) {
					continue;
				}

				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack stack = member.getItemBySlot(slot);
					if (townOf(mark(stack)) == town) {
						clear(stack);
					}
				}

				var inventory = member.getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if (townOf(mark(stack)) == town) {
						clear(stack);
					}
				}
			}
		}

		// A guard's ask run out: seen still in it by one of the town's people, it's a fine. Not seen in it again (handed over,
		// put away, gone off) for a good while after, it's let drop, and a guard may ask again another time it's seen.
		for (var entry : List.copyOf(asks.entrySet())) {
			Ask ask = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(ask.player());
			Town town = Diplomacy.byId(ask.town().toString());
			if (town == null || now - ask.since() > HAND_OVER * 5) {
				asks.remove(entry.getKey());
				asked.remove(entry.getKey());
				continue;
			}

			if (player == null || now - ask.since() < HAND_OVER || !shows(player, town)) {
				continue;
			}

			DrifterEntity witness = Justice.witness(server, town, player);
			if (witness != null) {
				asks.remove(entry.getKey());
				Justice.crime(server, town, player, "blood", witness);
			}
		}
	}

	/** One of the town's people sees them in it: thought the less of, once a piece; a guard who sees it asks for it back. */
	private static void seen(MinecraftServer server, Town town, ServerPlayer player, List<ItemStack> pieces, long now) {
		DrifterEntity witness = Justice.witness(server, town, player);
		DrifterGroups.Group group = town.group == null ? null : DrifterApi.groupById(town.group);
		if (witness == null || group == null) {
			return;
		}

		List<DrifterEntity> see = new ArrayList<>();
		DrifterEntity guard = null;
		for (UUID id : group.members) {
			DrifterEntity member = TownFounding.loaded(server, id);
			if (member != null && member.isAlive() && member.level() == player.level() && member.distanceTo(player) <= Justice.WITNESS
				&& member.getSocial().watcher().sees(player)) {
				see.add(member);
				if ("guard".equals(town.lines.get(id)) && (guard == null || member.distanceTo(player) < guard.distanceTo(player))) {
					guard = member;
				}
			}
		}

		List<ItemStack> fresh = pieces.stream().filter(stack -> !mark(stack).getBoolean("seen")).toList();
		String name = player.getGameProfile().getName();
		String who = mark(pieces.get(0)).getString("who");
		if (!fresh.isEmpty()) {
			for (ItemStack stack : fresh) {
				CompoundTag mark = mark(stack);
				mark.putBoolean("seen", true);
				put(stack, mark);
			}

			// (One known there as a killer of its people, in their kit: an insult on top.)
			Town.Criminal criminal = town.crimes.get(player.getUUID());
			double each = WARY * (criminal != null && criminal.kills > 0 ? 2.0 : 1.0);
			double less = Math.min(MOST * (each / WARY), each * fresh.size());
			for (DrifterEntity member : see) {
				DrifterApi.addTrustInPlayer(member, player.getUUID(), name, -less);
			}

			town.heard.merge(player.getUUID(), -WORD * fresh.size(), Double::sum);
			say(witness, CivLines.pick(witness, "blood.seen", "thats " + who + "'s armor", "player", name, "who", who));
			DrifterApi.think(witness, "saw " + name + " in " + who + "'s armour");
			Towns.save(server);
		}

		String key = key(town.id, player.getUUID());
		if (guard != null && !asks.containsKey(key) && now - asked.getOrDefault(key, Long.MIN_VALUE / 2) >= ASKS_AGAIN) {
			asks.put(key, new Ask(town.id, player.getUUID(), guard.getUUID(), now));
			asked.put(key, now);
			say(guard, CivLines.pick(guard, "blood.ask", "that was " + who + "'s. hand it over, " + name, "player", name, "who", who));
			DrifterApi.think(guard, "asked " + name + " for " + who + "'s armour back");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Thrown to someone
	// ---------------------------------------------------------------------------------------------

	/** The stack itself where the Drifter has it now (its pockets, or on it), or null. */
	@Nullable
	private static ItemStack held(DrifterEntity drifter, ItemStack like) {
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (ItemStack.isSameItemSameComponents(inventory.getItem(slot), like)) {
				return inventory.getItem(slot);
			}
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (ItemStack.isSameItemSameComponents(drifter.getItemBySlot(slot), like)) {
				return drifter.getItemBySlot(slot);
			}
		}

		return null;
	}

	/**
	 * A marked piece thrown to a Drifter by a player: {@link #RETRIM} when it's for a smith to put its own colours on
	 * ({@link TownServices} goes on with that job), {@link #SEEN_TO} when it was dealt with here, {@link #NOTHING} when
	 * the mark means nothing to this one (its town gone, or no keeper or smith: whatever it would do with any piece).
	 */
	static int received(DrifterEntity drifter, Player player, ItemStack stack) {
		MinecraftServer server = drifter.getServer();
		CompoundTag mark = mark(stack);
		Town theirs = townOf(mark);
		Town mine = Market.townOf(drifter);
		if (server == null || mark == null || theirs == null || mine == null) {
			return NOTHING;
		}

		String line = mine.lines.getOrDefault(drifter.getUUID(), "");
		String name = player.getGameProfile().getName();
		String who = mark.getString("who");
		if (mine == theirs) {
			// Back with its town.
			ItemStack held = held(drifter, stack);
			if (held != null) {
				clear(held);
			}

			// (All of it handed over: a guard's ask is done with. Unasked: thought the better of.)
			String key = key(mine.id, player.getUUID());
			if (!shows(player, mine) && asks.remove(key) == null) {
				DrifterApi.addTrustInPlayer(drifter, player.getUUID(), name, GAVE_BACK);
			}

			say(drifter, CivLines.pick(drifter, "blood.back", "ill see it gets back to " + who + "'s people", "player", name, "who", who));
			DrifterApi.think(drifter, "took " + who + "'s armour back from " + name);
			return SEEN_TO;
		}

		boolean keeper = drifter.getUUID().equals(mine.keeper);
		boolean smith = line.equals("smith");
		if (!keeper && !smith) {
			return NOTHING;
		}

		if (Nations.fellows(mine, theirs)) {
			say(drifter, CivLines.pick(drifter, "blood.ours", "thats one of ours, off someone dead. im not touching it", "player", name, "who", who,
				"town", theirs.name));
			TownServices.giveBack(drifter, player, TownServices.take(drifter, stack));
			return SEEN_TO;
		}

		if (smith) {
			return RETRIM;
		}

		if (!Annals.atWar(WorldPlan.powerOf(mine), WorldPlan.powerOf(theirs))) {
			// (Nothing to it: a keeper doesn't deal in armour. Handed back, with where to take it.)
			say(drifter, CivLines.pick(drifter, "blood.nothing", "not mine to buy. a smith could put new colours on it", "player", name, "who", who,
				"town", theirs.name));
			TownServices.giveBack(drifter, player, TownServices.take(drifter, stack));
			return SEEN_TO;
		}

		// An enemy's: a trophy, bought with what it has on it.
		int price = (int)Math.max(1, Math.round(DrifterApi.worth(new ItemStack(stack.getItem())) * TROPHY / 6.0));
		int paid = DrifterApi.moneyTake(drifter.getInventory(), price);
		if (paid <= 0) {
			say(drifter, CivLines.pick(drifter, "blood.trophy.broke", "one of " + theirs.name + "'s! id take it but ive nothing to pay with", "player", name,
				"town", theirs.name));
			TownServices.giveBack(drifter, player, TownServices.take(drifter, stack));
			return SEEN_TO;
		}

		drifter.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
		for (ItemStack money : DrifterApi.moneyStacks(paid)) {
			drifter.tossForward(money).setTarget(player.getUUID());
		}

		say(drifter, CivLines.pick(drifter, "blood.trophy", "one of " + theirs.name + "'s! ill take that off you", "player", name, "town", theirs.name));
		DrifterApi.think(drifter, "bought " + who + " of " + theirs.name + "'s armour from " + name + ", a trophy");
		return SEEN_TO;
	}
}
