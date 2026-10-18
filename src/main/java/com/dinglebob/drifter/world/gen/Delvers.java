package com.dinglebob.drifter.world.gen;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.registry.ModEntities;
import com.dinglebob.drifter.social.Disposition;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Delvers (user's request, 2026-10-17: "wandering drifters specifically designed for underground structures? more hostile
 * than friendly? i would love to be in a mineshaft, and run into a drifter looting a chest ... stacked too, full iron to
 * full diamond, along with a simply sword weapon, a bow"). An encounter (user, 2026-10-17: "it should spawn when im close
 * to a mineshaft (like i encounter one in a cave)", and despawn: "its more than a mob than a drifter"): while a player is
 * near a mineshaft, some (rolled once a visit; the first roll fixed by the world seed) have one at an unlooted chest
 * minecart 16 to 48 blocks off, out of the player's sight, come down to loot the place, in iron to diamond armor with its
 * favorite weapon, a bow, a pickaxe and torches. Mostly not friendly: a Bandit (kills on sight), an opportunist or
 * territorial. It loots the cart and works its way through the tunnels (DelveActivity). It's never saved with the world
 * and goes when no player is near (DrifterEntity#isDelver). Its kit is its starting kit (the design's "came from its
 * starting kit").
 * TODO(milestone 13, whims): ancient cities the same way.
 */
public final class Delvers {
	private Delvers() {
	}

	/** Where it's put: a cart this far from the player at least (it'd be seen appearing), and at most. */
	private static final double CLOSEST = 16.0;
	private static final double FARTHEST = 48.0;
	/** How often it looks round each player for a mineshaft (ticks). */
	private static final int LOOK_EVERY = 40;
	/** A mineshaft rolls again after this long (a later visit can meet another). */
	private static final long REVISIT = 24000L * 3;

	/** Mineshafts decided ("x,z" of their start chunk) and when, saved with the world. */
	private static final java.util.Map<String, Long> decided = new java.util.HashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Delvers::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Delvers::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> decided.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % LOOK_EVERY != 0 || !DrifterConfig.worldgenDelvers()) {
				return;
			}

			ServerLevel level = server.overworld();
			for (net.minecraft.server.level.ServerPlayer player : level.players()) {
				if (!player.isSpectator() && com.dinglebob.drifter.social.SocialWatcher.isRealPlayer(player)) {
					look(level, player);
				}
			}
		});
	}

	/** Round this player: an unlooted chest minecart of a mineshaft not decided lately, out of their sight, for a delver. */
	private static void look(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
		long now = level.getGameTime();
		List<MinecartChest> carts = level.getEntitiesOfClass(MinecartChest.class, player.getBoundingBox().inflate(FARTHEST, 24.0, FARTHEST),
			cart -> cart.getLootTable() != null && !cart.isRemoved());
		for (MinecartChest cart : carts) {
			double distance = cart.distanceTo(player);
			if (distance < CLOSEST || distance > FARTHEST || seen(level, player, cart)) {
				continue;
			}

			var start = level.structureManager().getStructureWithPieceAt(cart.blockPosition(), StructureTags.MINESHAFT);
			if (!start.isValid()) {
				continue;
			}

			String key = start.getChunkPos().x + "," + start.getChunkPos().z;
			Long at = decided.get(key);
			if (at != null && now - at < REVISIT) {
				continue;
			}

			// The first visit's roll is fixed by the world seed; later ones aren't.
			decided.put(key, now);
			RandomSource roll = at == null ? RandomSource.create(level.getSeed() ^ start.getChunkPos().toLong() ^ 0xDE17E5L) : level.getRandom();
			if (roll.nextDouble() >= DrifterConfig.spawnChance(DrifterConfig.Spawn.DELVERS)
				|| !com.dinglebob.drifter.world.DrifterCensus.roomFor(cart.blockPosition(), 1)) {
				return;
			}

			DrifterEntity delver = spawn(level, cart.blockPosition(), level.getRandom());
			if (delver != null) {
				Drifter.LOGGER.info("[Drifter] {} ({}) is delving the mineshaft at {}, {} blocks from {}", delver.getName().getString(),
					delver.getSocial().disposition().label(), cart.blockPosition().toShortString(), (int)distance, player.getName().getString());
			}

			return;
		}
	}

	/** The player can see the cart from where they are (nothing solid between their eyes and it). */
	private static boolean seen(ServerLevel level, net.minecraft.server.level.ServerPlayer player, MinecartChest cart) {
		var hit = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), cart.position().add(0.0, 0.5, 0.0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** A delver beside this spot (a chest minecart), geared up, delving from here; null if there's no room. */
	@Nullable
	public static DrifterEntity spawn(ServerLevel level, BlockPos near, RandomSource random) {
		BlockPos stand = standNear(level, near);
		if (stand == null) {
			return null;
		}

		DrifterEntity drifter = ModEntities.DRIFTER.create(level);
		if (drifter == null) {
			return null;
		}

		drifter.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), MobSpawnType.STRUCTURE, null);
		// Mostly the kind that comes down to loot: raiders, miners, a few hermits and fighters.
		double kind = random.nextDouble();
		drifter.getPersonality().reroll(random, kind < 0.35 ? Archetype.BANDIT : kind < 0.65 ? Archetype.MINER : kind < 0.85 ? Archetype.HERMIT
			: Archetype.PVP_SWEAT);
		double mood = random.nextDouble();
		drifter.getSocial().setDisposition(mood < 0.45 ? Disposition.BANDIT : mood < 0.75 ? Disposition.OPPORTUNIST : Disposition.TERRITORIAL);
		kit(level, drifter, random);
		// (Delving till it has been through the place; it doesn't settle down after.)
		drifter.startDelving(stand, Long.MAX_VALUE);
		return level.addFreshEntity(drifter) ? drifter : null;
	}

	/** Iron to diamond (a share of diamond pieces it rolls), its favorite weapon at that tier, a bow, and what a delve takes. */
	private static void kit(ServerLevel level, DrifterEntity drifter, RandomSource random) {
		double diamond = random.nextDouble();
		Item[][] armor = {
			{Items.IRON_HELMET, Items.DIAMOND_HELMET}, {Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE},
			{Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS}, {Items.IRON_BOOTS, Items.DIAMOND_BOOTS}};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			ItemStack piece = new ItemStack(armor[i][random.nextDouble() < diamond ? 1 : 0]);
			if (random.nextDouble() < 0.3) {
				enchant(level, piece, random);
			}

			drifter.setItemSlot(slots[i], piece);
		}

		Weapons.Tier tier = random.nextDouble() < diamond ? Weapons.Tier.DIAMOND : Weapons.Tier.IRON;
		Weapons.Family favorite = Weapons.favorite(drifter);
		Item weapon = favorite.at(tier);
		if (weapon == null) {
			weapon = tier == Weapons.Tier.DIAMOND ? Items.DIAMOND_SWORD : Items.IRON_SWORD;
		}

		ItemStack sword = new ItemStack(weapon);
		if (random.nextDouble() < 0.5) {
			enchant(level, sword, random);
		}

		var inventory = drifter.getInventory();
		inventory.add(sword);
		ItemStack bow = new ItemStack(Items.BOW);
		if (random.nextDouble() < 0.4) {
			enchant(level, bow, random);
		}

		inventory.add(bow);
		inventory.add(new ItemStack(Items.ARROW, 24 + random.nextInt(25)));
		// A shield, unless its weapon takes both hands (with Better Combat).
		if (!favorite.twoHanded() || !com.dinglebob.drifter.compat.BetterCombatCompat.active()) {
			drifter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}

		inventory.add(new ItemStack(tier == Weapons.Tier.DIAMOND && random.nextBoolean() ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE));
		inventory.add(new ItemStack(Items.TORCH, 24 + random.nextInt(25)));
		inventory.add(new ItemStack(random.nextBoolean() ? Items.COOKED_BEEF : Items.COOKED_PORKCHOP, 8 + random.nextInt(9)));
		inventory.add(new ItemStack(Items.COBBLESTONE, 32));
		inventory.add(new ItemStack(Items.WATER_BUCKET));
	}

	/** An enchanting-table enchantment at level 8 to 25, as a player of its gear would have. */
	private static void enchant(ServerLevel level, ItemStack stack, RandomSource random) {
		Optional<? extends net.minecraft.core.HolderSet<net.minecraft.world.item.enchantment.Enchantment>> table = level.registryAccess()
			.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
		ItemStack enchanted = EnchantmentHelper.enchantItem(random, stack, 8 + random.nextInt(18), level.registryAccess(), table);
		if (enchanted != stack) {
			stack.applyComponents(enchanted.getComponentsPatch());
		}
	}

	/** Room to stand within a few blocks (two clear blocks over a solid floor). */
	@Nullable
	private static BlockPos standNear(ServerLevel level, BlockPos near) {
		for (BlockPos pos : BlockPos.withinManhattan(near, 3, 2, 3)) {
			if (!pos.equals(near) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
				&& level.getFluidState(pos).isEmpty()) {
				return pos.immutable();
			}
		}

		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		CompoundTag data = new CompoundTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.data = tag;
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			return this.data;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_delvers");
	}

	private static void load(MinecraftServer server) {
		decided.clear();
		CompoundTag data = state(server).data;
		if (data.getInt("Version") < 2) {
			// (Version 1: decided once for good. They roll again on a later visit now.)
			for (Tag t : data.getList("Decided", Tag.TAG_STRING)) {
				decided.put(t.getAsString(), 0L);
			}

			return;
		}

		CompoundTag times = data.getCompound("Decided");
		for (String key : times.getAllKeys()) {
			decided.put(key, times.getLong(key));
		}
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", 2);
		CompoundTag times = new CompoundTag();
		decided.forEach(times::putLong);
		tag.put("Decided", times);
		State state = state(server);
		state.data = tag;
		state.setDirty();
	}
}
