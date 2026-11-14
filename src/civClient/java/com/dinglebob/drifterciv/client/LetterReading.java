package com.dinglebob.drifterciv.client;

import java.util.List;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * A letter read (the user, 2026-11-14: "mod the letter to were it opens one page when you right click it, making a lectern
 * sound effect when you open it (book flip/page flip noise) (making it compatible with sounds 'the mod)"): a right-click on
 * one opens its page, on the game's own book screen, with the game's own page-turn sound (so a resource pack or the Sounds
 * mod changes it as it does a book's). The seal broken is the server's business (Words.read).
 */
final class LetterReading {
	private LetterReading() {
	}

	/** The letter's marks (as Words has them). */
	private static final String LETTER = "drifter_civ_letter";
	private static final String TEXT = "drifter_civ_letter_text";

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			CustomData data = level.isClientSide && stack.is(Items.PAPER) ? stack.get(DataComponents.CUSTOM_DATA) : null;
			if (data == null || !data.contains(LETTER)) {
				return InteractionResultHolder.pass(stack);
			}

			CompoundTag tag = data.copyTag();
			String text = tag.getString(TEXT);
			// (One from before letters had a page: what it's called, at least.)
			if (text.isEmpty()) {
				text = stack.getHoverName().getString() + "\n\n" + "The ink has run. You can't make out the words.";
			}

			Minecraft minecraft = Minecraft.getInstance();
			minecraft.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(List.of(Component.literal(text)))));
			com.dinglebob.drifterciv.DrifterCivilizations.LOGGER.info("[letter] opened its page; the screen is now {}", minecraft.screen == null ? "none"
				: minecraft.screen.getClass().getSimpleName());
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
			// (Success: the server hears of it too, and breaks the seal.)
			return InteractionResultHolder.success(stack);
		});
	}
}
