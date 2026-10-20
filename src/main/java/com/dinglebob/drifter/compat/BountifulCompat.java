package com.dinglebob.drifter.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Bountiful (milestone 21, the user's "yes": Drifters doing bounties), optional. Its board (a block) holds bounties, each
 * a paper with objectives (deliver items, kill mobs) and rewards; a player takes one through the board's screen (the
 * board notes it as taken for them) and turns it in by right-clicking the board with it. The board's screen can't open
 * for a Drifter's helper player, so taking one goes through the board's own records (marked taken for that helper,
 * by reflection, as the screen does); turning it in is the real right-click (Bountiful's own code checks and takes the
 * items and pays the reward). The bounties' contents are read from their item data. Nothing here runs without the mod.
 */
public final class BountifulCompat {
	public static final String MOD = "bountiful";
	private static final ResourceLocation BOARD = ResourceLocation.fromNamespaceAndPath("bountiful", "bountyboard");

	private BountifulCompat() {
	}

	public static boolean loaded() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(MOD);
	}

	/** Its bounty board block, or null without the mod. */
	@Nullable
	public static Block board() {
		return loaded() ? BuiltInRegistries.BLOCK.getOptional(BOARD).orElse(null) : null;
	}

	/** One thing a bounty asks for or gives: an item (or "#tag") and how many; or a mob to kill. */
	public record Entry(String content, int amount, boolean item) {
	}

	/** A bounty on a board: its slot, what it asks for, what it gives. */
	public record Offer(int slot, List<Entry> objectives, List<Entry> rewards) {
	}

	/** The bounties on this board this helper player hasn't taken (read from the board's saved data); empty if none. */
	public static List<Offer> offers(ServerLevel level, BlockPos pos, Player helper) {
		List<Offer> offers = new ArrayList<>();
		BlockEntity board = level.getBlockEntity(pos);
		if (board == null) {
			return offers;
		}

		Set<Integer> taken = taken(board, helper);
		CompoundTag data = board.saveWithoutMetadata(level.registryAccess());
		ListTag items = data.getCompound("bounty_inv").getList("Items", Tag.TAG_COMPOUND);
		for (Tag t : items) {
			CompoundTag item = (CompoundTag)t;
			int slot = item.getByte("Slot");
			if (taken != null && taken.contains(slot)) {
				continue;
			}

			CompoundTag components = item.getCompound("components");
			offers.add(new Offer(slot, entries(components.getList("bountiful:objects", Tag.TAG_COMPOUND)),
				entries(components.getList("bountiful:rewards", Tag.TAG_COMPOUND))));
		}

		return offers;
	}

	private static List<Entry> entries(ListTag list) {
		List<Entry> entries = new ArrayList<>();
		for (Tag t : list) {
			CompoundTag entry = (CompoundTag)t;
			String logic = entry.getString("logicName");
			entries.add(new Entry(entry.getString("content"), entry.getInt("amount"), logic.endsWith(":item") || logic.endsWith(":item_tag")));
		}

		return entries;
	}

	/** The slots this helper has taken already (the board's own record), or null if it can't be read. */
	@SuppressWarnings("unchecked")
	@Nullable
	private static Set<Integer> taken(BlockEntity board, Player helper) {
		try {
			return (Set<Integer>)board.getClass().getMethod("maskFor", Player.class).invoke(board, helper);
		} catch (ReflectiveOperationException | ClassCastException e) {
			Drifter.LOGGER.warn("[Drifter] couldn't read a Bountiful board's taken bounties: {}", e.toString());
			return null;
		}
	}

	/**
	 * Takes the bounty in this slot for this helper, as the board's screen does: a copy of the paper, the board noting it
	 * taken for them, the time it was picked up. The paper, or empty if it couldn't.
	 */
	public static ItemStack take(ServerLevel level, BlockPos pos, Player helper, int slot) {
		BlockEntity board = level.getBlockEntity(pos);
		Set<Integer> taken = board == null ? null : taken(board, helper);
		if (taken == null || taken.contains(slot)) {
			return ItemStack.EMPTY;
		}

		try {
			Container all = (Container)board.getClass().getMethod("fullInventoryCopy").invoke(board);
			ItemStack paper = all.getItem(slot).copy();
			if (paper.isEmpty()) {
				return ItemStack.EMPTY;
			}

			taken.add(slot);
			board.setChanged();
			Class<?> bountyStack = Class.forName("io.ejekta.bountiful.components.BountyStack");
			Object bounty = bountyStack.getConstructor(ItemStack.class).newInstance(paper);
			bountyStack.getMethod("setPickedUp", long.class).invoke(bounty, level.getGameTime());
			return paper;
		} catch (ReflectiveOperationException | ClassCastException e) {
			Drifter.LOGGER.warn("[Drifter] couldn't take a Bountiful bounty: {}", e.toString());
			return ItemStack.EMPTY;
		}
	}

	/** A bounty paper (Bountiful's item). */
	public static boolean isBounty(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.fromNamespaceAndPath(MOD, "bounty"));
	}
}
