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

		// Its own helper, kept (milestone 16: looked up through Fabric's weakly kept map every time, and the first time made
		// as a whole player; see PlayerAdvancementsMixin).
		GameProfile profile = profileFor(drifter);
		FakePlayer fake = drifter.helperPlayer;
		if (fake == null || fake.level() != level || fake.getGameProfile() != profile) {
			HELPERS.add(profile.getId());
			fake = FakePlayer.get(level, profile);
			drifter.helperPlayer = fake;
			OWNERS.put(fake.getUUID(), new java.lang.ref.WeakReference<>(drifter));
		}

		syncIn(drifter, fake);
		try {
			return action.apply(fake);
		} finally {
			syncOut(drifter, fake);
		}
	}

	private static GameProfile profileFor(DrifterEntity drifter) {
		String name = drifter.getName().getString();
		GameProfile known = drifter.helperProfile;
		if (known != null && known.getName().equals(VALID_NAME.matcher(name).matches() ? name : "Drifter")) {
			return known;
		}

		GameProfile profile = new GameProfile(helperId(drifter), VALID_NAME.matcher(name).matches() ? name : "Drifter");
		drifter.helperProfile = profile;
		return profile;
	}

	/** Every helper's id (they have no advancements: PlayerAdvancementsMixin). */
	private static final java.util.Set<UUID> HELPERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static boolean isHelper(UUID id) {
		return HELPERS.contains(id);
	}

	/** Helpers back to their Drifters: its sword hits are the helper's, but they're its doing (relationships, kills). */
	private static final java.util.Map<UUID, java.lang.ref.WeakReference<DrifterEntity>> OWNERS = new java.util.concurrent.ConcurrentHashMap<>();

	/** The Drifter behind this entity: itself, or the Drifter whose fake-player helper it is; null for anyone else. */
	@org.jetbrains.annotations.Nullable
	public static DrifterEntity ownerOf(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity entity) {
		if (entity instanceof DrifterEntity drifter) {
			return drifter;
		}

		if (!(entity instanceof FakePlayer)) {
			return null;
		}

		var ref = OWNERS.get(entity.getUUID());
		DrifterEntity owner = ref == null ? null : ref.get();
		return owner != null && owner.isAlive() ? owner : null;
	}

	/** The UUID of this Drifter's fake player, so its own actions can be told apart (it shouldn't "hear" itself). */
	public static UUID helperId(DrifterEntity drifter) {
		// (Worked out once: it's a hash, and asked for on every attack and every sound; milestone 16.)
		UUID known = drifter.helperId;
		// (Again if its own id changed: loaded from a save.)
		if (known == null || drifter.helperIdFor != drifter.getUUID()) {
			known = UUID.nameUUIDFromBytes(("drifter-hands:" + drifter.getUUID()).getBytes(StandardCharsets.UTF_8));
			drifter.helperId = known;
			drifter.helperIdFor = drifter.getUUID();
		}

		return known;
	}

	private static void syncIn(DrifterEntity drifter, FakePlayer fake) {
		fake.moveTo(drifter.getX(), drifter.getY(), drifter.getZ(), drifter.getYRot(), drifter.getXRot());
		fake.setYHeadRot(drifter.getYHeadRot());
		fake.setOnGround(drifter.onGround());
		fake.setShiftKeyDown(drifter.isShiftKeyDown());
		fake.setPose(drifter.getPose());
		fake.getFoodData().setExhaustion(0.0F);
		// Its own experience (the enchanting table spends levels, a furnace's output gives points).
		fake.experienceLevel = drifter.experienceLevel();
		fake.experienceProgress = drifter.experienceProgress();
		fake.totalExperience = drifter.totalExperience();
		((com.dinglebob.drifter.mixin.PlayerAccessor)fake).drifter$setEnchantmentSeed(drifter.enchantmentSeed());

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

		drifter.setExperience(fake.experienceLevel, fake.experienceProgress, fake.totalExperience, fake.getEnchantmentSeed());

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
