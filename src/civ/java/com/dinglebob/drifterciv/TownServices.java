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
	private static final double REPAIR = 6;
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
		if (town == null || line == null || !line.equals("enchanter") && !line.equals("smith")) {
			return;
		}

		Job job = jobs.get(drifter.getUUID());
		boolean money = DrifterApi.moneyWorth(stack) > 0;
		if (job != null && !job.player.equals(player.getUUID())) {
			if (!money && wanted(line, stack) != null) {
				say(drifter, "one at a time, im busy");
				giveBack(drifter, player, take(drifter, stack));
			}

			return;
		}

		TownReputation.Standing standing = TownReputation.of(town, player);
		if (job == null) {
			if (money || wanted(line, stack) == null) {
				return;
			}

			if (standing == TownReputation.Standing.WANTED) {
				say(drifter, "not doing anything for you");
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
			String kind = wanted(line, stack);
			ItemStack held = take(drifter, stack);
			if (job.item.isEmpty() && kind != null && (kind.equals("enchant") || kind.equals("repair") || kind.equals("netherite"))) {
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
		if (line.equals("smith") && job.kind != null && !job.kind.equals("netherite") && has(job, Items.NETHERITE_INGOT)
			&& has(job, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) && diamond(job.item)) {
			job.kind = "netherite";
		}

		Job it = job;
		double price = Math.round((it.kind.equals("enchant") ? ENCHANT : it.kind.equals("netherite") ? NETHERITE : REPAIR) * standing.prices);
		boolean ready = it.paid >= price && switch (it.kind) {
			case "enchant" -> true;
			case "netherite" -> has(it, Items.NETHERITE_INGOT) && has(it, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
			default -> it.materials.stream().anyMatch(m -> it.item.getItem().isValidRepairItem(it.item, m));
		};
		if (ready) {
			job.working = true;
			say(drifter, "ok, give me a minute");
			DrifterApi.think(drifter, "took a job: " + job.what());
		} else if (money || job.materials.isEmpty()) {
			String pay = TownNotices.describe(DrifterApi.money(Math.max(0, price - job.paid)));
			say(drifter, switch (job.kind) {
				case "enchant" -> "i can enchant that for " + pay + (job.paid > 0 ? " more" : "");
				case "netherite" -> "netherite? " + pay + ", and the ingot and the template";
				default -> "ill fix it for " + pay + (job.paid > 0 ? " more" : "") + ", bring the stuff to fix it with";
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

	private static boolean diamond(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("diamond_") && stack.getMaxStackSize() == 1;
	}

	private static boolean has(Job job, net.minecraft.world.item.Item item) {
		return job.materials.stream().anyMatch(m -> m.is(item));
	}

	/** That stack out of its pack (held apart for the job). */
	private static ItemStack take(DrifterEntity drifter, ItemStack stack) {
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

	private static void giveBack(DrifterEntity drifter, Player player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		drifter.getCamera().track(player.getEyePosition(), com.dinglebob.drifter.motor.CameraModel.TurnSpeed.NORMAL);
		drifter.tossForward(stack);
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
		DrifterApi.think(drifter, (done ? "done: " : "couldn't: ") + job.what());
		if (!done) {
			say(drifter, "sorry, couldnt do it. heres your stuff");
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
			say(drifter, "here you go " + job.playerName);
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
				say(drifter, "never mind then. heres your stuff back");
			}

			if (job.back != null) {
				handBack(drifter, job, false);
			}
		}
	}
}
