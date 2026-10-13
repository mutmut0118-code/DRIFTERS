package com.dinglebob.drifter.hands;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.mojang.authlib.GameProfile;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;

import net.fabricmc.fabric.api.entity.FakePlayer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Borrows a Fabric server-side {@link FakePlayer} for the moment the Drifter interacts with the world
 * (DESIGN.md section 2, option C). Before each interaction the fake player is put exactly where the Drifter is,
 * looking where it looks, crouching if it crouches, holding the Drifter's own item stacks (the same objects, so tool
 * damage and used-up items land straight in the Drifter's inventory). Afterwards every slot is copied back and the
 * fake player is emptied again.
 *
 * <p>Because the action runs as a player, vanilla rules (drops, tool damage, placement rules) and protection mods
 * (which see it as a player) apply automatically. Each Drifter gets its own fake player profile, so protection mods
 * see a consistent identity per Drifter.
 */
public final class FakePlayerBridge {
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private FakePlayerBridge() {
	}

	public static <T> T act(DrifterEntity drifter, Function<FakePlayer, T> action) {
		if (!(drifter.level() instanceof ServerLevel level)) {
			throw new IllegalStateException("Drifter interactions only run on the server");
		}

		FakePlayer fake = FakePlayer.get(level, profileFor(drifter));
		syncIn(drifter, fake);
		try {
			return action.apply(fake);
		} finally {
			syncOut(drifter, fake);
		}
	}

	private static GameProfile profileFor(DrifterEntity drifter) {
		UUID id = UUID.nameUUIDFromBytes(("drifter-hands:" + drifter.getUUID()).getBytes(StandardCharsets.UTF_8));
		String name = drifter.getName().getString();
		return new GameProfile(id, VALID_NAME.matcher(name).matches() ? name : "Drifter");
	}

	private static void syncIn(DrifterEntity drifter, FakePlayer fake) {
		fake.moveTo(drifter.getX(), drifter.getY(), drifter.getZ(), drifter.getYRot(), drifter.getXRot());
		fake.setYHeadRot(drifter.getYHeadRot());
		fake.setOnGround(drifter.onGround());
		fake.setShiftKeyDown(drifter.isShiftKeyDown());
		fake.setPose(drifter.getPose());
		fake.getFoodData().setExhaustion(0.0F);

		Inventory inventory = fake.getInventory();
		DrifterInventory own = drifter.getInventory();
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			inventory.items.set(i, own.getItem(i));
		}

		for (int i = 0; i < DrifterInventory.ARMOR_SIZE; i++) {
			inventory.armor.set(i, own.getItem(DrifterInventory.MAIN_SIZE + i));
		}

		inventory.offhand.set(0, own.getItem(DrifterInventory.OFFHAND_SLOT));
		inventory.selected = own.getSelected();
	}

	private static void syncOut(DrifterEntity drifter, FakePlayer fake) {
		Inventory inventory = fake.getInventory();
		DrifterInventory own = drifter.getInventory();
		for (int i = 0; i < DrifterInventory.MAIN_SIZE; i++) {
			own.setItem(i, inventory.items.get(i));
		}

		for (int i = 0; i < DrifterInventory.ARMOR_SIZE; i++) {
			own.setItem(DrifterInventory.MAIN_SIZE + i, inventory.armor.get(i));
		}

		own.setItem(DrifterInventory.OFFHAND_SLOT, inventory.offhand.get(0));

		// Exhaustion the action cost the "player" (0.005 per block broken, for example) is the Drifter's.
		drifter.causeFoodExhaustion(fake.getFoodData().getExhaustionLevel());
		fake.getFoodData().setExhaustion(0.0F);

		inventory.items.replaceAll(stack -> ItemStack.EMPTY);
		inventory.armor.replaceAll(stack -> ItemStack.EMPTY);
		inventory.offhand.replaceAll(stack -> ItemStack.EMPTY);
		fake.setShiftKeyDown(false);
		if (fake.isUsingItem()) {
			fake.stopUsingItem();
		}
	}
}
