package com.dinglebob.drifterciv;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.DrifterApi;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A town's notices on Bountiful's bounty board (the user, 2026-11-07: "will there be notices compat with bountiful?", then
 * "yes build it"), only with Bountiful installed. Each notice up in a town is also a real bounty on the town's board (the
 * one on its plaza, or any a player put up in the town): take it and turn it in at the board the Bountiful way. Its reward
 * is a command Bountiful runs (`drifterciv bountiful ...`), so the town pays it: its poster's money then the till's, as when
 * you throw the things to the poster; and what was handed in goes to the poster (or the till), not into thin air. Filled
 * either way (at the board, or thrown) or taken down, it comes off the board; a paper turned in after that gets its things
 * back. Drifters reading the board can fill them too (a hunter bringing in the leather).
 *
 * Bountiful's board is reached into by reflection (its bounties, and adding and removing one as its own code does): if a
 * Bountiful update changes that, this quietly stops and notices still work by throwing.
 */
final class TownBounties {
	private TownBounties() {
	}

	private static final String MOD = "bountiful";
	private static final ResourceLocation BOARD = ResourceLocation.fromNamespaceAndPath(MOD, "bountyboard");
	/** The mark on a notice's paper (its custom data): the notice's id. */
	private static final String MARK = "DrifterNotice";
	/** How far from a town's middle its board can be (a player's own too). */
	private static final int REACH = 40;
	/** Bountiful's board, as reached (null: not found, or it changed and this is off). */
	private static Field bounties;
	private static Method add;
	private static Method remove;
	private static Method free;
	private static boolean broken;

	static boolean on() {
		return !broken && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(MOD);
	}

	/** Each check: the town's board found (once a day if it has none), and its notices on it, and nothing else of ours. */
	static void check(MinecraftServer server, Town town) {
		if (!on()) {
			return;
		}

		ServerLevel level = level(server, town);
		BlockPos board = board(level, town, Chronicle.day(server));
		if (board != null) {
			sync(level, town, board);
		}
	}

	@Nullable
	private static ServerLevel level(MinecraftServer server, Town town) {
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(town.dimension)));
		return level != null ? level : server.overworld();
	}

	/** Its board: the one it knows if it's still there, else one looked for (no more than once a day); null if none. */
	@Nullable
	private static BlockPos board(ServerLevel level, Town town, long day) {
		var block = BuiltInRegistries.BLOCK.getOptional(BOARD).orElse(null);
		if (block == null) {
			return null;
		}

		if (town.bountyBoard != null) {
			if (!level.isLoaded(town.bountyBoard)) {
				return null;
			}

			if (level.getBlockState(town.bountyBoard).is(block)) {
				return town.bountyBoard;
			}

			town.bountyBoard = null;
		}

		BlockPos center = town.center;
		if (town.bountyLook >= day || !level.hasChunksAt(center.offset(-REACH, 0, -REACH), center.offset(REACH, 0, REACH))) {
			return null;
		}

		town.bountyLook = day;
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-REACH, -12, -REACH), center.offset(REACH, 12, REACH))) {
			if (level.getBlockState(pos).is(block) && (best == null || pos.distSqr(center) < best.distSqr(center))) {
				best = pos.immutable();
			}
		}

		town.bountyBoard = best;
		return best;
	}

	/** Its notices up on the board (each once), and its papers for notices gone (filled, or taken down) off it. */
	private static void sync(ServerLevel level, Town town, BlockPos pos) {
		BlockEntity board = level.getBlockEntity(pos);
		Container papers = board == null ? null : papers(board);
		if (papers == null) {
			return;
		}

		Map<UUID, Integer> ours = new HashMap<>();
		for (int slot = 0; slot < papers.getContainerSize(); slot++) {
			UUID id = mark(papers.getItem(slot));
			if (id != null) {
				if (ours.containsKey(id) || open(town, id) == null) {
					call(remove, board, slot);
				} else {
					ours.put(id, slot);
				}
			}
		}

		if (broken) {
			return;
		}

		for (TownNotices.Notice notice : town.notices) {
			if (ours.containsKey(notice.id) || notice.count <= 0) {
				continue;
			}

			Integer slot = slotFor(board, papers);
			ItemStack paper = slot == null ? ItemStack.EMPTY : paper(level, town, notice);
			if (paper.isEmpty()) {
				return;
			}

			call(add, board, slot, paper);
			DrifterCivilizations.LOGGER.info("[bounties] {}'s notice on its board at {}: {}", town.name, pos.toShortString(), notice.text);
		}
	}

	/** A slot for a new paper: a free one, else one of Bountiful's own (taken off for it; never one of ours); null if none. */
	@Nullable
	@SuppressWarnings("unchecked")
	private static Integer slotFor(BlockEntity board, Container papers) {
		Object slots = call(free, board);
		if (slots instanceof Set<?> set && !set.isEmpty()) {
			return (Integer)set.iterator().next();
		}

		List<Integer> theirs = new ArrayList<>();
		for (int slot = 0; slot < papers.getContainerSize(); slot++) {
			if (!papers.getItem(slot).isEmpty() && mark(papers.getItem(slot)) == null) {
				theirs.add(slot);
			}
		}

		if (theirs.isEmpty()) {
			return null;
		}

		int slot = theirs.get(board.getLevel().getRandom().nextInt(theirs.size()));
		call(remove, board, slot);
		return slot;
	}

	/**
	 * A notice as a Bountiful paper: asks for its things, pays by a command (the town pays: {@link #cashIn}), shows its pay
	 * ("9 emeralds from Hollowbrook"), runs out when the notice is taken down; marked as the notice's, named for the town.
	 */
	private static ItemStack paper(ServerLevel level, Town town, TownNotices.Notice notice) {
		long now = level.getGameTime();
		long day = Chronicle.day(level.getServer());
		long time = level.getServer().overworld().getDayTime() % 24000L;
		long left = Math.max(20L * 60, ((notice.day + TownNotices.STAYS - day) * 24000L + 1000L - time) / 20L);
		ItemStack money = DrifterApi.money(notice.points);
		CompoundTag info = new CompoundTag();
		info.putString("rarity", "UNCOMMON");
		info.putLong("timeStarted", now);
		info.putLong("timeToComplete", left);
		info.putLong("timePickedUp", now);
		CompoundTag wants = new CompoundTag();
		wants.putString("id", "drifterciv_notice.goods");
		wants.putString("content", notice.item);
		wants.putString("logicName", "minecraft:item");
		wants.putInt("amount", notice.count);
		CompoundTag pays = new CompoundTag();
		// (Its id is an entry of ours in Bountiful's pools, data/bountiful/bounty_pools/drifter_civ: so the board shows an emerald.)
		pays.putString("id", "drifterciv_notice.pay");
		pays.putString("content", "drifterciv bountiful " + notice.id + " " + notice.item + " " + notice.count + " %PLAYER_NAME%");
		pays.putString("logicName", "minecraft:command");
		pays.putString("rarity", "UNCOMMON");
		pays.putInt("amount", money.getCount());
		pays.putString("name", TownNotices.describe(money) + " from " + town.name);
		ListTag objects = new ListTag();
		objects.add(wants);
		ListTag rewards = new ListTag();
		rewards.add(pays);
		CompoundTag components = new CompoundTag();
		components.put("bountiful:bounty_info", info);
		components.put("bountiful:objects", objects);
		components.put("bountiful:rewards", rewards);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", MOD + ":bounty");
		tag.putInt("count", 1);
		tag.put("components", components);
		ItemStack paper = ItemStack.parseOptional(level.registryAccess(), tag);
		if (paper.isEmpty()) {
			DrifterCivilizations.LOGGER.warn("[bounties] couldn't make a Bountiful paper for {}'s notice (Bountiful changed?)", town.name);
			broken = true;
			return ItemStack.EMPTY;
		}

		CompoundTag mark = new CompoundTag();
		mark.putString(MARK, notice.id.toString());
		paper.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
		paper.set(DataComponents.CUSTOM_NAME, Component.literal("Notice from " + town.name).withStyle(style -> style.withItalic(false)));
		return paper;
	}

	/**
	 * A town's paper turned in at a board (Bountiful has taken the things from whoever turned it in, and runs this): the town
	 * pays them (the poster's money, then the till's), the things go to the poster (or the till), the notice comes down. If
	 * it's down already (filled by throwing, or old), they get their things back. {@code who} is the player's name, or the
	 * Drifter's whose helper turned it in.
	 */
	static boolean cashIn(MinecraftServer server, UUID id, Item item, int count, String who) {
		Player helper = DrifterApi.actingHelper();
		Player to = helper != null && helper.getGameProfile().getName().equals(who) ? helper : server.getPlayerList().getPlayerByName(who);
		if (to == null) {
			DrifterCivilizations.LOGGER.warn("[bounties] a notice's paper turned in by {}, who isn't here", who);
			return false;
		}

		DrifterEntity drifter = DrifterApi.drifterBehind(to);
		String name = drifter != null ? drifter.getName().getString() : to.getGameProfile().getName();
		Town town = null;
		TownNotices.Notice notice = null;
		for (Town one : Towns.all()) {
			notice = open(one, id);
			if (notice != null) {
				town = one;
				break;
			}
		}

		ItemStack goods = new ItemStack(item, count);
		String what = count + " " + goods.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
		if (town == null) {
			// (Down already: its things back.)
			give(to, goods);
			tell(to, "That notice was already taken down: your " + what + " back.");
			return true;
		}

		DrifterEntity poster = TownFounding.loaded(server, notice.poster);
		if (poster != null && poster == drifter) {
			poster = null;
		}

		// Paid: its poster's money, then the till's.
		List<ItemStack> paid = new ArrayList<>();
		double owed = notice.points;
		if (poster != null) {
			owed = TownNotices.take(poster.getInventory(), owed, paid);
		}

		Container till = TownNotices.till(server, town);
		if (owed > 0 && till != null) {
			owed = TownNotices.take(till, owed, paid);
			till.setChanged();
		}

		double got = 0;
		for (ItemStack money : paid) {
			got += DrifterApi.moneyWorth(money);
			give(to, money);
		}

		// What was handed in: to its poster, else the till, else by the board.
		for (int left = count; left > 0; ) {
			ItemStack part = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
			left -= part.getCount();
			if (poster != null) {
				poster.getInventory().add(part);
			}

			if (!part.isEmpty() && till != null) {
				part = addTo(till, part);
			}

			if (!part.isEmpty() && town.bountyBoard != null) {
				net.minecraft.world.Containers.dropItemStack(to.level(), town.bountyBoard.getX() + 0.5, town.bountyBoard.getY() + 1.0, town.bountyBoard.getZ() + 0.5, part);
			}
		}

		town.notices.remove(notice);
		String pay = paid.isEmpty() ? "nothing (the till was empty)" : TownNotices.describe(DrifterApi.money(got)) + (owed > 0 ? " (all it had)" : "");
		tell(to, town.name + " paid you " + pay + " for the " + what + ".");
		Chronicle.add(server, "town", name + " filled " + town.name + "'s notice: " + what);
		if (poster != null) {
			com.dinglebob.drifter.social.ChatLines.speak(poster, owed > 0 ? "got your " + what + ", thats all i could pay" : "got the " + what + ", thanks " + name + "!");
			if (drifter == null) {
				DrifterApi.addTrustInPlayer(poster, to.getUUID(), name, 10.0);
			}
		}

		if (drifter != null) {
			DrifterApi.think(drifter, "filled " + town.name + "'s notice at the board: " + what + " for " + pay);
		}

		DrifterCivilizations.LOGGER.info("[bounties] {} filled {}'s notice at the board: {} for {}", name, town.name, what, pay);
		// (Off the board now, not at the next check.)
		check(server, town);
		Towns.save(server);
		return true;
	}

	/** Debug: a copy of the town's first notice's paper on its board (as if taken from it), or empty. */
	static ItemStack paperOnBoard(MinecraftServer server, Town town) {
		ServerLevel level = level(server, town);
		BlockPos pos = town.bountyBoard;
		BlockEntity board = pos == null || !level.isLoaded(pos) ? null : level.getBlockEntity(pos);
		Container papers = board == null || !on() ? null : papers(board);
		if (papers != null) {
			for (int slot = 0; slot < papers.getContainerSize(); slot++) {
				if (mark(papers.getItem(slot)) != null) {
					return papers.getItem(slot).copy();
				}
			}
		}

		return ItemStack.EMPTY;
	}

	/**
	 * Debug: the player right-clicks the town's board with what's in their hand (from where they are), as a real click does
	 * on the server: a paper is turned in, an empty hand opens the board. False if it has no board.
	 */
	static boolean click(ServerPlayer player, Town town) {
		BlockPos pos = town.bountyBoard;
		if (pos == null || !player.serverLevel().isLoaded(pos)) {
			return false;
		}

		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
		player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		return true;
	}

	/** Into a container as far as it goes; what didn't fit. */
	private static ItemStack addTo(Container into, ItemStack stack) {
		for (int slot = 0; slot < into.getContainerSize() && !stack.isEmpty(); slot++) {
			ItemStack there = into.getItem(slot);
			if (there.isEmpty()) {
				into.setItem(slot, stack.copyAndClear());
			} else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
				int moved = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
				there.grow(moved);
				stack.shrink(moved);
			}
		}

		into.setChanged();
		return stack;
	}

	/** Into their pack (a player's, or a Drifter's helper's, which the Drifter gets), or at their feet. */
	private static void give(Player to, ItemStack stack) {
		to.getInventory().placeItemBackInInventory(stack.copy());
	}

	private static void tell(Player to, String text) {
		if (to instanceof ServerPlayer player && DrifterApi.drifterBehind(player) == null) {
			player.sendSystemMessage(Component.literal(text));
		}
	}

	/** A notice of this town's still up, by its id, or null. */
	@Nullable
	private static TownNotices.Notice open(Town town, UUID id) {
		for (TownNotices.Notice notice : town.notices) {
			if (notice.id.equals(id) && notice.count > 0) {
				return notice;
			}
		}

		return null;
	}

	/** The notice a paper is for (its mark), or null. */
	@Nullable
	private static UUID mark(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(MARK)) {
			return null;
		}

		try {
			return UUID.fromString(data.copyTag().getString(MARK));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The board's bounties (Bountiful's own container), reached once; null (and off) if it can't be. */
	@Nullable
	private static Container papers(BlockEntity board) {
		try {
			if (bounties == null) {
				Class<?> type = board.getClass();
				bounties = type.getDeclaredField("bounties");
				bounties.setAccessible(true);
				add = type.getDeclaredMethod("addBounty", int.class, ItemStack.class);
				add.setAccessible(true);
				remove = type.getDeclaredMethod("removeBounty", int.class);
				remove.setAccessible(true);
				free = type.getDeclaredMethod("getFreeSlots");
				free.setAccessible(true);
			}

			return bounties.get(board) instanceof Container container ? container : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			DrifterCivilizations.LOGGER.warn("[bounties] couldn't reach Bountiful's board (Bountiful changed?): {}", e.toString());
			broken = true;
			return null;
		}
	}

	@Nullable
	private static Object call(Method method, Object on, Object... args) {
		try {
			return method.invoke(on, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			DrifterCivilizations.LOGGER.warn("[bounties] couldn't use Bountiful's board (Bountiful changed?): {}", e.toString());
			broken = true;
			return null;
		}
	}
}
