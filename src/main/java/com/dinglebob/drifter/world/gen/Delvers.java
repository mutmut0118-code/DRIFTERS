package com.dinglebob.drifter.world.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.registry.ModEntities;
import com.dinglebob.drifter.social.Disposition;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Delvers (user's request, 2026-10-17: "wandering drifters specifically designed for underground structures? more hostile
 * than friendly? i would love to be in a mineshaft, and run into a drifter looting a chest ... stacked too, full iron to
 * full diamond, along with a simply sword weapon, a bow"). Some mineshafts (fixed by the world seed) have one: the first
 * time one of its unlooted chest minecarts loads, a Drifter is there beside it, come down to loot the place, in iron to
 * diamond armor with its favorite weapon, a bow, a pickaxe and torches. Mostly not friendly: a Bandit, an opportunist or
 * territorial. It loots the cart and works its way through the tunnels (DelveActivity), then lives as any Drifter does.
 * Its kit is its starting kit (the design's "came from its starting kit"); everything after it gathers.
 * TODO(milestone 13, whims): ancient cities the same way.
 */
public final class Delvers {
	private Delvers() {
	}

	/** It isn't put there in sight of a player (they'd see it appear). */
	private static final double NOT_NEAR_PLAYER = 24.0;
	/** It delves this long at most (a day or so) before it's an ordinary Drifter. */
	private static final long DELVE_MIN = 24000L;
	private static final long DELVE_EXTRA = 12000L;

	private record Pending(ServerLevel level, UUID cart) {
	}

	private static final List<Pending> pending = new ArrayList<>();
	/** Mineshafts already decided ("x,z" of their start chunk), saved with the world. */
	private static final Set<String> decided = new HashSet<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Delvers::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Delvers::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			pending.clear();
			decided.clear();
		});
		// A cart is noted as it loads and dealt with at the end of the tick (not while its chunk is still loading).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof MinecartChest cart && level.dimension() == Level.OVERWORLD && cart.getLootTable() != null
				&& DrifterConfig.worldgenDelvers()) {
				pending.add(new Pending(level, cart.getUUID()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pending.isEmpty()) {
				return;
			}

			List<Pending> now = new ArrayList<>(pending);
			pending.clear();
			for (Pending p : now) {
				if (p.level().getEntity(p.cart()) instanceof MinecartChest cart && !cart.isRemoved()) {
					consider(p.level(), cart);
				}
			}
		});
	}

	private static void consider(ServerLevel level, MinecartChest cart) {
		var start = level.structureManager().getStructureWithPieceAt(cart.blockPosition(), StructureTags.MINESHAFT);
		if (!start.isValid()) {
			return;
		}

		String key = start.getChunkPos().x + "," + start.getChunkPos().z;
		if (decided.contains(key) || level.getNearestPlayer(cart, NOT_NEAR_PLAYER) != null) {
			return;
		}

		decided.add(key);
		RandomSource fixed = RandomSource.create(level.getSeed() ^ start.getChunkPos().toLong() ^ 0xDE17E5L);
		if (fixed.nextDouble() >= DrifterConfig.spawnChance(DrifterConfig.Spawn.DELVERS)
			|| !com.dinglebob.drifter.world.DrifterCensus.roomFor(cart.blockPosition(), 1)) {
			return;
		}

		DrifterEntity delver = spawn(level, cart.blockPosition(), level.getRandom());
		if (delver != null) {
			Drifter.LOGGER.info("[Drifter] world gen: {} ({}) is delving the mineshaft at {}", delver.getName().getString(),
				delver.getSocial().disposition().label(), cart.blockPosition().toShortString());
		}
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
		drifter.startDelving(stand, level.getGameTime() + DELVE_MIN + random.nextInt((int)DELVE_EXTRA));
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
		for (Tag t : state(server).data.getList("Decided", Tag.TAG_STRING)) {
			decided.add(t.getAsString());
		}
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", 1);
		ListTag list = new ListTag();
		decided.forEach(key -> list.add(StringTag.valueOf(key)));
		tag.put("Decided", list);
		State state = state(server);
		state.data = tag;
		state.setDirty();
	}
}
