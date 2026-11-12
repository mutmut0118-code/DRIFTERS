package com.dinglebob.drifterciv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.api.DrifterEvents;
import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Services for players (milestone 23 B5; the user, 2026-11-06: "go to b4 b5 and b6"): its town's enchanter enchants
 * something of yours, its smith repairs it or upgrades diamond to netherite. Throw it the item, then the money (and what
 * it takes: lapis for an enchanting, the material for a repair, the ingot and template for netherite); once
 * it's paid it goes to its station and does it ({@code ServiceGoal}) and hands it back. It holds your things apart while
 * it works (never worn, sold or lost); not paid within two minutes, or it can't, and it hands them back. One job at a
 * time. What the town thinks of you (D1) sets its price; the wanted, it won't serve.
 */
final class TownServices {
	private TownServices() {
	}

	/** What each costs (money points: an emerald is 6). */
	private static final double ENCHANT = 12;
	/** A repair: this share of what the piece sells for new, an emerald at most (the user, 2026-11-12: keeping good gear good is the cheap way). */
	private static final double REPAIR = 6;
	private static final double REPAIR_SHARE = 0.15;
	/** Its own colours on a piece that carried another town's (BloodGoods). */
	private static final double RETRIM = 12;
	private static final double NETHERITE = 18;
	/** Not paid in this long: everything back. */
	private static final long WAITS = 20L * 120;

	/** A job: whose, what kind, the item, what came with it, what's been paid, and once done what goes back. */
	private static final class Job {
		final UUID player;
		final String playerName;
		@Nullable
		String kind;
		ItemStack item = ItemStack.EMPTY;
		final List<ItemStack> materials = new ArrayList<>();
		double paid;
		final long since;
		boolean working;
		/** Its own material put to a repair (charged for): what's left of it stays with it. */
		ItemStack own = ItemStack.EMPTY;
		@Nullable
		List<ItemStack> back;

		Job(UUID player, String playerName, long since) {
			this.player = player;
			this.playerName = playerName;
			this.since = since;
		}

		String what() {
			return switch (this.kind == null ? "" : this.kind) {
				case "enchant" -> "enchant " + this.playerName + "'s " + name(this.item);
				case "repair" -> "repair " + this.playerName + "'s " + name(this.item);
				case "netherite" -> "make " + this.playerName + "'s " + name(this.item) + " netherite";
				case "retrim" -> "put its town's colours on " + this.playerName + "'s " + name(this.item);
				default -> "a job for " + this.playerName;
			};
		}
	}

	/** Each Drifter's job (not saved: after a reload, it's lost... so things go back first: see {@link #check}). */
	private static final Map<UUID, Job> jobs = new HashMap<>();

	static void register() {
		DrifterEvents.RECEIVED.register(TownServices::received);
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}

	/** The town it's one of, and its line there; or null. */
	@Nullable
	private static Town town(DrifterEntity drifter) {
		var group = DrifterApi.group(drifter.getUUID());
		if (group == null) {
			return null;
		}

		for (Town town : Towns.all()) {
			if (group.id.equals(town.group)) {
				return town;
			}
		}

		return null;
	}

	private static void say(DrifterEntity drifter, String line) {
		com.dinglebob.drifter.social.ChatLines.speak(drifter, line);
	}

	/** A player threw it something: toward a job? */
	private static void received(DrifterEntity drifter, Player player, ItemStack stack) {
		MinecraftServer server = drifter.getServer();
		Town town = server == null ? null : town(drifter);
		String line = town == null ? null : town.lines.get(drifter.getUUID());
		// (One of a dead townsperson's pieces: its town's to take back, an enemy's to buy, one of its nation's to refuse;
		// only a smith who doesn't care goes on, to put its own colours on it. BloodGoods.)
		int blooded = BloodGoods.marked(stack) ? BloodGoods.received(drifter, player, stack) : BloodGoods.NOTHING;
		if (blooded == BloodGoods.SEEN_TO) {
			return;
		}

		boolean blood = blooded == BloodGoods.RETRIM;

		if (town == null || line == null || !line.equals("enchanter") && !line.equals("smith")) {
			return;
		}

		Job job = jobs.get(drifter.getUUID());
		boolean money = DrifterApi.moneyWorth(stack) > 0;
		if (job != null && !job.player.equals(player.getUUID())) {
			if (!money && (blood || wanted(line, stack) != null)) {
				say(drifter, CivLines.pick(drifter, "service.busy", "one at a time, im busy", "player", player.getGameProfile().getName()));
				giveBack(drifter, player, take(drifter, stack));
			}

			return;
		}

		TownReputation.Standing standing = TownReputation.of(town, player);
		if (job == null) {
			if (money || !blood && wanted(line, stack) == null) {
				return;
			}

			if (standing == TownReputation.Standing.WANTED) {
				say(drifter, CivLines.pick(drifter, "service.refuse", "not doing anything for you", "player", player.getGameProfile().getName()));
				giveBack(drifter, player, take(drifter, stack));
				return;
			}

			job = new Job(player.getUUID(), player.getGameProfile().getName(), drifter.level().getGameTime());
			jobs.put(drifter.getUUID(), job);
		}

		if (job.working || job.back != null) {
			return;
		}

		if (money) {
			// (Its pay: it keeps that.)
			job.paid += DrifterApi.moneyWorth(stack);
		} else {
			String kind = blood ? "retrim" : wanted(line, stack);
			ItemStack held = take(drifter, stack);
			if (job.item.isEmpty() && kind != null && (kind.equals("enchant") || kind.equals("repair") || kind.equals("netherite") || kind.equals("retrim"))) {
				job.item = held;
				job.kind = kind;
			} else {
				job.materials.add(held);
			}
		}

		if (job.item.isEmpty()) {
			return;
		}

		// (A diamond piece with what netherite takes, or a worn one: which it is now.)
		if (line.equals("smith") && job.kind != null && !job.kind.equals("netherite") && !job.kind.equals("retrim") && has(job, Items.NETHERITE_INGOT)
			&& has(job, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) && diamond(job.item)) {
			job.kind = "netherite";
		}

		Job it = job;
		// (A repair with nothing brought to mend it with: with its own, if it carries it, charged for.)
		boolean brought = it.materials.stream().anyMatch(m -> it.item.getItem().isValidRepairItem(it.item, m));
		ItemStack own = it.kind.equals("repair") && !brought ? ownMaterial(drifter, it.item) : ItemStack.EMPTY;
		int units = own.isEmpty() ? 0 : Math.min(Math.min(own.getCount(), drifter.experienceLevel()),
			(int)Math.ceil(it.item.getDamageValue() / Math.max(1.0, it.item.getMaxDamage() / 4)));
		double price = Math.max(1, Math.round(switch (it.kind) {
			case "enchant" -> ENCHANT;
			case "netherite" -> NETHERITE;
			case "retrim" -> RETRIM;
			default -> Math.max(1.0, Math.min(REPAIR, REPAIR_SHARE * Market.price(drifter, it.item.getItem()))) + units * Market.price(drifter, own.getItem());
		} * standing.prices));
		boolean ready = it.paid >= price && switch (it.kind) {
			case "enchant", "retrim" -> true;
			case "netherite" -> has(it, Items.NETHERITE_INGOT) && has(it, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
			default -> brought || units > 0;
		};
		if (ready) {
			if (units > 0) {
				job.own = take(drifter, own.copyWithCount(units));
				job.materials.add(job.own);
			}

			job.working = true;
			say(drifter, CivLines.pick(drifter, "service.start", "ok, give me a minute", "player", job.playerName));
			DrifterApi.think(drifter, "took a job: " + job.what());
		} else if (money || job.materials.isEmpty()) {
			String pay = TownNotices.describe(DrifterApi.money(Math.max(0, price - job.paid)));
			String more = pay + (job.paid > 0 ? " more" : "");
			say(drifter, switch (job.kind) {
				case "enchant" -> CivLines.pick(drifter, "service.quote.enchant", "i can enchant that for " + more, "pay", more, "player", job.playerName);
				case "netherite" -> CivLines.pick(drifter, "service.quote.netherite", "netherite? " + pay + ", and the ingot and the template", "pay", more,
					"player", job.playerName);
				case "retrim" -> CivLines.pick(drifter, "service.quote.retrim", "i can put our colours on that for " + more + ". then its just armor", "pay", more,
					"player", job.playerName);
				default -> units > 0
					? CivLines.pick(drifter, "service.quote.repair.own", "ill fix it for " + more + ", ive got what it takes", "pay", more, "player", job.playerName)
					: CivLines.pick(drifter, "service.quote.repair", "ill fix it for " + more + ", bring the stuff to fix it with", "pay", more,
						"player", job.playerName);
			});
		}
	}

	/** What kind of job a thing thrown to it is for, by its line (or materials: "material"), or null for nothing. */
	@Nullable
	private static String wanted(String line, ItemStack stack) {
		if (line.equals("enchanter")) {
			return stack.is(Items.LAPIS_LAZULI) ? "material" : stack.isEnchantable() && !stack.isEnchanted() ? "enchant" : null;
		}

		if (stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {
			return "material";
		}

		if (stack.isDamageableItem() && stack.isDamaged()) {
			return "repair";
		}

		if (diamond(stack)) {
			return "netherite";
		}

		// (Something to repair with: ingots, diamonds, planks, leather...)
		return stack.getMaxStackSize() > 1 ? "material" : null;
	}

	/** What it carries that mends this (an ingot, a diamond, planks...): the stack, or empty. */
	private static ItemStack ownMaterial(DrifterEntity drifter, ItemStack item) {
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.getMaxStackSize() > 1 && item.getItem().isValidRepairItem(item, stack)) {
				return stack.copyWithCount(inventory.count(other -> ItemStack.isSameItemSameComponents(other, stack)));
			}
		}

		return ItemStack.EMPTY;
	}

	private static boolean diamond(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("diamond_") && stack.getMaxStackSize() == 1;
	}

	private static boolean has(Job job, net.minecraft.world.item.Item item) {
		return job.materials.stream().anyMatch(m -> m.is(item));
	}

	/** That stack out of its pack (held apart for the job). */
	static ItemStack take(DrifterEntity drifter, ItemStack stack) {
		var inventory = drifter.getInventory();
		int left = stack.getCount();
		ItemStack held = stack.copyWithCount(0);
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack in = inventory.getItem(slot);
			if (ItemStack.isSameItemSameComponents(in, stack)) {
				int n = Math.min(left, in.getCount());
				in.shrink(n);
				held.grow(n);
				left -= n;
			}
		}

		return held.isEmpty() ? stack.copy() : held;
	}

	static void giveBack(DrifterEntity drifter, Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		drifter.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
		// (Theirs to pick up, nobody else's: a townsperson passing took it. Found in testing, 0.23.53.)
		drifter.tossForward(stack).setTarget(player.getUUID());
	}

	/** Money this player throws it now goes toward a job of theirs it's waiting to be paid for. */
	static boolean takesMoney(DrifterEntity drifter, Player player) {
		Job job = jobs.get(drifter.getUUID());
		return job != null && job.player.equals(player.getUUID()) && !job.working && job.back == null;
	}

	/** It has a player's things in hand for a job. */
	static boolean busy(DrifterEntity drifter) {
		return jobs.containsKey(drifter.getUUID());
	}

	/** The job it's working on, for its goal. */
	@Nullable
	static TownWork.Service service(DrifterEntity drifter) {
		Job job = jobs.get(drifter.getUUID());
		return job == null || !job.working || job.back != null || job.kind == null ? null
			: new TownWork.Service(job.kind, job.what(), job.item, List.copyOf(job.materials));
	}

	/** It's done (or couldn't): back to the player, now if they're near, else when they come by. */
	static void serviced(DrifterEntity drifter, List<ItemStack> back, boolean done) {
		Job job = jobs.get(drifter.getUUID());
		if (job == null) {
			return;
		}

		job.working = false;
		job.back = new ArrayList<>(back);
		// (Its town's colours on it, and it's nobody's dead's any more.)
		Town town = town(drifter);
		if (done && "retrim".equals(job.kind) && town != null && !back.isEmpty()
			&& DrifterApi.trim(drifter.level().registryAccess(), back.get(0), TownUniforms.uniform(town))) {
			BloodGoods.clear(back.get(0));
		}

		// (Its own material it didn't use, the job not done: back in its pockets, not to the player.)
		if (!job.own.isEmpty()) {
			int mine = job.own.getCount();
			for (var each = job.back.iterator(); each.hasNext() && mine > 0;) {
				ItemStack stack = each.next();
				if (stack.getItem() == job.own.getItem() && stack != job.back.get(0)) {
					ItemStack kept = stack.split(Math.min(mine, stack.getCount()));
					mine -= kept.getCount();
					drifter.getInventory().add(kept);
					if (stack.isEmpty()) {
						each.remove();
					}
				}
			}
		}

		DrifterApi.think(drifter, (done ? "done: " : "couldn't: ") + job.what());
		if (!done) {
			say(drifter, CivLines.pick(drifter, "service.failed", "sorry, couldnt do it. heres your stuff", "player", job.playerName));
		}

		handBack(drifter, job, done);
	}

	/** Back to its player if they're near (true: handed back). */
	private static boolean handBack(DrifterEntity drifter, Job job, boolean done) {
		MinecraftServer server = drifter.getServer();
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(job.player);
		if (player == null || player.level() != drifter.level() || player.distanceTo(drifter) > 16.0 || job.back == null) {
			return false;
		}

		for (ItemStack stack : job.back) {
			giveBack(drifter, player, stack);
		}

		if (done) {
			say(drifter, CivLines.pick(drifter, "service.done", "here you go " + job.playerName, "player", job.playerName));
		}

		jobs.remove(drifter.getUUID());
		return true;
	}

	/** Each check: a job done, its player come by: handed back; one not paid in time: everything back. */
	static void check(MinecraftServer server, Town town) {
		for (var entry : List.copyOf(jobs.entrySet())) {
			DrifterEntity drifter = TownFounding.loaded(server, entry.getKey());
			Job job = entry.getValue();
			if (drifter == null) {
				continue;
			}

			if (job.back == null && !job.working && drifter.level().getGameTime() - job.since > WAITS) {
				job.back = new ArrayList<>();
				if (!job.item.isEmpty()) {
					job.back.add(job.item);
				}

				job.back.addAll(job.materials);
				say(drifter, CivLines.pick(drifter, "service.timeout", "never mind then. heres your stuff back", "player", job.playerName));
			}

			if (job.back != null) {
				handBack(drifter, job, false);
			}
		}
	}
}
