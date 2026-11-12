package com.dinglebob.drifter.world.gen;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.api.TownUniform;
import com.dinglebob.drifter.brain.Weapons;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.gear.ArmorSets;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.registry.ModEntities;
import com.dinglebob.drifter.social.Disposition;
import com.dinglebob.drifter.world.SoldierKit;
import com.dinglebob.drifter.world.Uniforms;

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
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
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

	/**
	 * The mineshafts delvers come to: vanilla's, and Hopo's Better Mineshaft's when it's there (user, 2026-10-17; tag
	 * drifter:delver_mineshafts, a datapack can add more).
	 */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> MINESHAFTS = net.minecraft.tags.TagKey.create(
		Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Drifter.MOD_ID, "delver_mineshafts"));

	/**
	 * The ruins looters come to (the user, 2026-11-06: "50% chance there is a drifter/drifters looting a lootable structure
	 * (ruins, ruined portals, etc) and a 50% chance those looters are civilians from a town ... or delvers/bandits"): ruined
	 * portals, pyramids, an igloo, Philips Ruins' and Hopo's when they're there (tag drifter:looter_ruins).
	 */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> RUINS = net.minecraft.tags.TagKey.create(
		Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Drifter.MOD_ID, "looter_ruins"));
	/** A ruin has looters at it this often (rolled once a visit, the first by the world seed). */
	private static final double RUIN_LOOTERS = 0.5;
	/** Of those, this share are a town's people out scavenging (if there's a town near with room), the rest delvers and bandits. */
	private static final double TOWNSFOLK = 0.5;
	/** How long a town's scavengers go through the place before they go about their lives (ticks). */
	private static final long SCAVENGES = 20L * 60 * 4;
	/** In a town with a uniform, a citizen's armor piece is a modded one of the same tier this often (when one fits). */
	public static final double CITIZEN_MODDED_CHANCE = 0.2;
	/** A town leader's chestplate is a modded one of diamond's tier this often (when one fits and takes a trim), else diamond. */
	public static final double LEADER_MODDED_CHEST_CHANCE = 0.5;
	/** With armor mods, a delver has a stray trim (random pattern and material) on one piece that takes one this often. */
	public static final double LOOT_TRIM_CHANCE = 0.1;

	/**
	 * Who makes a new Drifter one of the nearest town's people (Drifter: Civilizations): for a spot, the way to make one of
	 * them, or null when there's no town near with room.
	 */
	@Nullable
	private static volatile java.util.function.BiFunction<ServerLevel, BlockPos, java.util.function.Consumer<DrifterEntity>> townLooters;

	public static void setTownLooters(@Nullable java.util.function.BiFunction<ServerLevel, BlockPos, java.util.function.Consumer<DrifterEntity>> which) {
		townLooters = which;
	}

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

	/**
	 * Round this player: an unlooted chest minecart, or chest or barrel (Hopo's mineshafts keep theirs in rooms), of a
	 * mineshaft not decided lately, out of their sight, for a delver.
	 */
	private static void look(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
		long now = level.getGameTime();
		List<BlockPos> loot = new java.util.ArrayList<>();
		for (MinecartChest cart : level.getEntitiesOfClass(MinecartChest.class, player.getBoundingBox().inflate(FARTHEST, 24.0, FARTHEST),
			cart -> cart.getLootTable() != null && !cart.isRemoved())) {
			loot.add(cart.blockPosition());
		}

		int reach = (int)Math.ceil(FARTHEST / 16.0);
		for (int cx = -reach; cx <= reach; cx++) {
			for (int cz = -reach; cz <= reach; cz++) {
				var chunk = level.getChunkSource().getChunkNow(player.chunkPosition().x + cx, player.chunkPosition().z + cz);
				if (chunk == null) {
					continue;
				}

				for (var entity : chunk.getBlockEntities().values()) {
					if (entity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container
						&& container.getLootTable() != null && Math.abs(entity.getBlockPos().getY() - player.getBlockY()) <= 24) {
						loot.add(entity.getBlockPos());
					}
				}
			}
		}

		for (BlockPos spot : loot) {
			double distance = Math.sqrt(spot.distToCenterSqr(player.position()));
			if (distance < CLOSEST || distance > FARTHEST || seen(level, player, spot)) {
				continue;
			}

			var start = level.structureManager().getStructureWithPieceAt(spot, MINESHAFTS);
			if (!start.isValid()) {
				var ruin = level.structureManager().getStructureWithPieceAt(spot, RUINS);
				if (ruin.isValid() && ruin(level, player, spot, ruin, now, distance)) {
					return;
				}

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
				|| !com.dinglebob.drifter.world.DrifterCensus.roomFor(spot, 1)) {
				return;
			}

			DrifterEntity delver = spawn(level, spot, level.getRandom());
			if (delver != null) {
				Drifter.LOGGER.info("[Drifter] {} ({}) is delving the mineshaft at {}, {} blocks from {}", delver.getName().getString(),
					delver.getSocial().disposition().label(), spot.toShortString(), (int)distance, player.getName().getString());
			}

			return;
		}
	}

	/** A ruin's chest: looters at it, half the time (decided once a visit); true if it was decided now. */
	private static boolean ruin(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos spot,
		net.minecraft.world.level.levelgen.structure.StructureStart start, long now, double distance) {
		String key = "r" + start.getChunkPos().x + "," + start.getChunkPos().z;
		Long at = decided.get(key);
		if (at != null && now - at < REVISIT) {
			return false;
		}

		decided.put(key, now);
		RandomSource roll = at == null ? RandomSource.create(level.getSeed() ^ start.getChunkPos().toLong() ^ 0x1007E5L) : level.getRandom();
		if (roll.nextDouble() >= RUIN_LOOTERS || !DrifterConfig.worldgenDelvers() || !com.dinglebob.drifter.world.DrifterCensus.roomFor(spot, 3)) {
			return true;
		}

		int count = 1 + roll.nextInt(3);
		var join = roll.nextDouble() < TOWNSFOLK && townLooters != null ? townLooters.apply(level, spot) : null;
		List<String> who = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			DrifterEntity looter = join != null ? scavenger(level, spot, level.getRandom(), join) : spawn(level, spot, level.getRandom());
			if (looter != null) {
				who.add(looter.getName().getString());
			}
		}

		if (!who.isEmpty()) {
			Drifter.LOGGER.info("[Drifter] {} ({}) looting the ruin at {}, {} blocks from {}", String.join(", ", who), join != null ? "townsfolk scavenging"
				: "delvers", spot.toShortString(), (int)distance, player.getName().getString());
		}

		return true;
	}

	/**
	 * One of a town's people out scavenging at a ruin: a working life's gear, friendly enough, one of the town's ({@code
	 * join}: its group, a house there), going through the place a few minutes and then about its life (home to its town).
	 */
	@Nullable
	public static DrifterEntity scavenger(ServerLevel level, BlockPos near, RandomSource random, java.util.function.Consumer<DrifterEntity> join) {
		BlockPos stand = standNear(level, near);
		DrifterEntity drifter = stand == null ? null : ModEntities.DRIFTER.create(level);
		if (drifter == null) {
			return null;
		}

		drifter.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), MobSpawnType.STRUCTURE, null);
		double kind = random.nextDouble();
		drifter.getPersonality().reroll(random, kind < 0.3 ? Archetype.CASUAL : kind < 0.55 ? Archetype.MINER : kind < 0.75 ? Archetype.HOARDER
			: kind < 0.9 ? Archetype.NOMAD : Archetype.HELPER);
		drifter.getSocial().setDisposition(random.nextBoolean() ? Disposition.WARY_NEUTRAL : Disposition.FRIENDLY);
		townKit(drifter, random, "citizen");
		if (!level.addFreshEntity(drifter)) {
			return null;
		}

		join.accept(drifter);
		drifter.startDelving(stand, level.getGameTime() + SCAVENGES, false);
		return drifter;
	}

	/** The player can see the spot from where they are (nothing solid between their eyes and it; a chest itself doesn't count). */
	private static boolean seen(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos spot) {
		var hit = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), net.minecraft.world.phys.Vec3.atCenterOf(spot).add(0.0, 0.5, 0.0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
			&& ((net.minecraft.world.phys.BlockHitResult)hit).getBlockPos().equals(spot);
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
		// Every one kills on sight (user, 2026-10-17: "when i spawn a delver, it doesnt try to kill me on sight (it should)").
		drifter.getSocial().setDisposition(Disposition.BANDIT);
		kit(level, drifter, random);
		// (Delving till it has been through the place; it doesn't settle down after.)
		drifter.startDelving(stand, Long.MAX_VALUE);
		return level.addFreshEntity(drifter) ? drifter : null;
	}

	/**
	 * Iron to diamond (a share of diamond pieces it rolls), its favorite weapon at that tier, a bow, and what a delve takes.
	 * With armor mods its armor is a realistic mix (spec 2026-11-10-modded-armor-kits-design.md, goal 5): each piece now and
	 * then a modded one of its tier ({@link SoldierKit#mixed}), rarely one whole modded set at the tier it rolled
	 * ({@link SoldierKit#lootedSet}), and now and then a stray trim on one piece ({@link #LOOT_TRIM_CHANCE}).
	 */
	private static void kit(ServerLevel level, DrifterEntity drifter, RandomSource random) {
		double diamond = random.nextDouble();
		ArmorSets.Set looted = SoldierKit.rollsLootedSet(random, drifter)
			? SoldierKit.lootedSet(random.nextDouble() < diamond ? ArmorSets.Tier.DIAMOND : ArmorSets.Tier.IRON, random, drifter) : null;
		for (EquipmentSlot slot : ArmorSets.SLOTS) {
			ArmorSets.Tier tier = random.nextDouble() < diamond ? ArmorSets.Tier.DIAMOND : ArmorSets.Tier.IRON;
			ItemStack piece = new ItemStack(looted != null ? looted.pieces().get(slot) : SoldierKit.mixed(tier.vanilla(slot), slot, random, drifter));
			if (random.nextDouble() < 0.3) {
				enchant(level, piece, random);
			}

			drifter.setItemSlot(slot, piece);
		}

		if (SoldierKit.armorMods() && random.nextDouble() < LOOT_TRIM_CHANCE) {
			lootTrim(level, drifter, random);
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

	/**
	 * A delver's gear for a Drifter settled in a town made ready (for testing): armor, weapon, shield, bow and pickaxe as a
	 * delver's, and the iron tools of a working life (axe, shovel, hoe).
	 */
	public static void settledKit(DrifterEntity drifter, RandomSource random) {
		kit((ServerLevel)drifter.level(), drifter, random);
		var inventory = drifter.getInventory();
		inventory.add(new ItemStack(Items.IRON_AXE));
		inventory.add(new ItemStack(Items.IRON_SHOVEL));
		inventory.add(new ItemStack(Items.IRON_HOE));
		inventory.add(new ItemStack(Items.BREAD, 8));
	}

	/**
	 * What a town made ready's people wear and carry, by their place in it (the user, 2026-10-28: "for made ready towns,
	 * everyone is wearing diamond and i think everyone feels a bit too overpowered; i cant tell a guard from a citizen, or
	 * a citizen from a leader"):
	 * <ul>
	 *   <li>"citizen": a working life's gear, humble (stone tools, a leather piece maybe), settled (iron tools, a stone or
	 *       iron sword, a piece or two of leather, chain or iron) or well off (iron tools and sword, up to three pieces of
	 *       chain and iron, never a full suit);</li>
	 *   <li>"guard": a full iron suit (a diamond piece now and then, enchanted sometimes), an iron weapon, shield and bow;</li>
	 *   <li>"leader": a gold helmet for a crown, an iron or diamond chestplate, a diamond weapon, a shield.</li>
	 * </ul>
	 * No uniform: the kit as before uniforms (guards in untrimmed iron); {@link #townKit(DrifterEntity, RandomSource, String, TownUniform)}.
	 */
	public static void townKit(DrifterEntity drifter, RandomSource random, String role) {
		townKit(drifter, random, role, null);
	}

	/**
	 * The town kit in its town's uniform (modded armor in starting kits, spec 2026-11-10-modded-armor-kits-design.md): a guard
	 * wears the uniform's set on every slot (iron where the set is unknown or gone), its trim on every piece that takes one, no
	 * diamond piece (a uniform is the same on every guard); the leader the set's legs and boots, a gold helmet for a crown and
	 * a chestplate that is, as before uniforms, half the time the set's own and half the time a tier up (diamond's, or a modded
	 * one of diamond's tier that takes a trim), all trimmed; a citizen its
	 * working life's pieces as before, each now and then a modded piece of the same tier instead ({@link #CITIZEN_MODDED_CHANCE}),
	 * with no town trim. Null uniform: {@link #townKit(DrifterEntity, RandomSource, String)}'s kit exactly.
	 */
	public static void townKit(DrifterEntity drifter, RandomSource random, String role, @Nullable TownUniform uniform) {
		ServerLevel level = (ServerLevel)drifter.level();
		var inventory = drifter.getInventory();
		Weapons.Family favorite = Weapons.favorite(drifter);
		boolean guard = role.equals("guard");
		boolean leader = role.equals("leader");
		double life = guard || leader ? 0.9 : random.nextDouble();
		boolean humble = life < 0.35;
		boolean wellOff = life >= 0.8;

		// Armor.
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		int worn = 0;
		Item[][] suits = {
			{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
			{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
			{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
			{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}};
		// (A guard or the leader of a town with a uniform wears it; the rest, and everyone of a town without one, as before.)
		boolean uniformed = uniform != null && (guard || leader);
		if (uniformed) {
			dressInUniform(level, drifter, random, leader, uniform);
		}

		for (int i = 0; i < slots.length && !uniformed; i++) {
			Item piece = null;
			if (guard) {
				// (A diamond piece at most.)
				boolean diamond = worn == 0 && random.nextFloat() < 0.08F;
				piece = suits[diamond ? 3 : 2][i];
				worn += diamond ? 1 : 0;
			} else if (leader) {
				piece = i == 0 ? Items.GOLDEN_HELMET : i == 1 ? suits[random.nextBoolean() ? 3 : 2][1] : suits[2][i];
			} else if ((wellOff ? random.nextFloat() < 0.5F : !humble && random.nextFloat() < 0.35F || humble && random.nextFloat() < 0.15F) && worn < (wellOff ? 3 : 2)) {
				// (Never a full suit: that's a guard's look.)
				float kind = random.nextFloat();
				int suit = humble ? 0 : wellOff ? (kind < 0.55F ? 2 : 1) : kind < 0.5F ? 0 : kind < 0.75F ? 1 : 2;
				piece = suits[suit][i];
				worn++;
				// (In a town with a uniform, any mod's armor too: now and then a modded piece of the same tier.)
				if (uniform != null && random.nextDouble() < CITIZEN_MODDED_CHANCE) {
					Item modded = ArmorSets.modPieceAt(slots[i], ArmorSets.Tier.values()[suit], random, drifter);
					if (modded != null) {
						piece = modded;
					}
				}
			}

			if (piece != null) {
				ItemStack stack = new ItemStack(piece);
				if ((guard || leader) && random.nextFloat() < 0.3F) {
					enchant(level, stack, random);
				}

				drifter.setItemSlot(slots[i], stack);
			}
		}

		// A weapon: the leader's diamond, a guard's iron, most others' stone or iron, the humble now and then none.
		Weapons.Tier tier = leader ? Weapons.Tier.DIAMOND : guard || wellOff ? Weapons.Tier.IRON
			: humble ? (random.nextBoolean() ? Weapons.Tier.STONE : null) : random.nextFloat() < 0.4F ? Weapons.Tier.IRON : Weapons.Tier.STONE;
		if (tier != null) {
			Item weapon = favorite.at(tier);
			if (weapon == null) {
				weapon = switch (tier) {
					case DIAMOND -> Items.DIAMOND_SWORD;
					case IRON -> Items.IRON_SWORD;
					default -> Items.STONE_SWORD;
				};
			}

			ItemStack stack = new ItemStack(weapon);
			if ((guard || leader) && random.nextFloat() < 0.4F) {
				enchant(level, stack, random);
			}

			worn(stack, random);
			inventory.add(stack);
		}

		// (What it wears has seen its years in the town: nothing in a town made ready is new off the anvil. The user,
		// 2026-11-12: what falls off a body is used; what a shop sells is new.)
		for (EquipmentSlot slot : slots) {
			worn(drifter.getItemBySlot(slot), random);
		}

		// (Everyone in a town has a shield by then: found by the user, a town just made with half its people off to the store
		// together for planks to make one, crowding it and leaving the town empty.)
		if (!favorite.twoHanded() || !com.dinglebob.drifter.compat.BetterCombatCompat.active()) {
			drifter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}

		if (guard || random.nextFloat() < 0.2F) {
			inventory.add(new ItemStack(Items.BOW));
			inventory.add(new ItemStack(Items.ARROW, guard ? 32 : 12 + random.nextInt(12)));
		}

		// The tools of its working life.
		boolean iron = !humble;
		inventory.add(new ItemStack(iron ? Items.IRON_PICKAXE : Items.STONE_PICKAXE));
		// (And a spare, as a player keeps one: section 10.17.)
		inventory.add(new ItemStack(Items.STONE_PICKAXE));
		inventory.add(new ItemStack(iron ? Items.IRON_AXE : Items.STONE_AXE));
		inventory.add(new ItemStack(iron && random.nextBoolean() ? Items.IRON_SHOVEL : Items.STONE_SHOVEL));
		inventory.add(new ItemStack(humble ? Items.WOODEN_HOE : random.nextBoolean() ? Items.IRON_HOE : Items.STONE_HOE));
		inventory.add(new ItemStack(Items.TORCH, 8 + random.nextInt(12)));
		inventory.add(new ItemStack(Items.BREAD, 6 + random.nextInt(6)));
		if (random.nextBoolean()) {
			inventory.add(new ItemStack(random.nextBoolean() ? Items.COOKED_BEEF : Items.COOKED_PORKCHOP, 4 + random.nextInt(6)));
		}

		// (Enough for a Drifter's first wants, and a boat: found in testing, a town just made sent its people for planks for a
		// boat and for more cobblestone, the last two things on a newcomer's list, many of them to the store at once.)
		inventory.add(new ItemStack(Items.COBBLESTONE, 16 + random.nextInt(17)));
		inventory.add(new ItemStack(Items.OAK_BOAT));
		if (!humble && random.nextFloat() < 0.4F) {
			inventory.add(new ItemStack(Items.WATER_BUCKET));
		}
	}

	/**
	 * A guard's or the leader's armor in the town's uniform: the set's piece in every slot (iron where it has none), the
	 * leader's helmet a gold crown and its chestplate, as before uniforms, as likely the set's own as a tier up (no stronger
	 * than a leader before uniforms); the uniform's trim on every piece that takes one; enchanted now and then, as before.
	 */
	private static void dressInUniform(ServerLevel level, DrifterEntity drifter, RandomSource random, boolean leader, TownUniform uniform) {
		ArmorSets.Set set = Uniforms.set(uniform);
		ArmorTrim trim = Uniforms.trim(level.registryAccess(), uniform);
		for (EquipmentSlot slot : ArmorSets.SLOTS) {
			Item piece = leader && slot == EquipmentSlot.HEAD ? Items.GOLDEN_HELMET
				: leader && slot == EquipmentSlot.CHEST ? (random.nextBoolean() ? leaderChest(drifter, random) : Uniforms.piece(set, slot))
				: Uniforms.piece(set, slot);
			ItemStack stack = Uniforms.trimmed(new ItemStack(piece), trim);
			if (random.nextFloat() < 0.3F) {
				enchant(level, stack, random);
			}

			drifter.setItemSlot(slot, stack);
		}
	}

	/**
	 * The leader's chestplate when it rolls a tier above its guards': a modded one of diamond's tier now and then
	 * ({@link #LEADER_MODDED_CHEST_CHANCE}, leaned by its taste, only one that takes the town's trim), else diamond's.
	 */
	private static Item leaderChest(DrifterEntity drifter, RandomSource random) {
		if (random.nextDouble() < LEADER_MODDED_CHEST_CHANCE) {
			Item modded = ArmorSets.modPieceAt(EquipmentSlot.CHEST, ArmorSets.Tier.DIAMOND, random, drifter);
			if (modded != null && new ItemStack(modded).is(ItemTags.TRIMMABLE_ARMOR)) {
				return modded;
			}
		}

		return ArmorSets.Tier.DIAMOND.vanilla(EquipmentSlot.CHEST);
	}

	/**
	 * A stray trim, like loot from elsewhere: one of its worn pieces that takes a trim (at random) gets a random pattern and
	 * material of those registered (no town's colours: a delver belongs to none).
	 */
	private static void lootTrim(ServerLevel level, DrifterEntity drifter, RandomSource random) {
		List<ItemStack> trimmable = new java.util.ArrayList<>();
		for (EquipmentSlot slot : ArmorSets.SLOTS) {
			ItemStack worn = drifter.getItemBySlot(slot);
			if (!worn.isEmpty() && worn.is(ItemTags.TRIMMABLE_ARMOR)) {
				trimmable.add(worn);
			}
		}

		if (trimmable.isEmpty()) {
			return;
		}

		ItemStack piece = trimmable.get(random.nextInt(trimmable.size()));
		var pattern = level.registryAccess().registryOrThrow(Registries.TRIM_PATTERN).getRandom(random);
		var material = level.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).getRandom(random);
		if (pattern.isPresent() && material.isPresent()) {
			Uniforms.trimmed(piece, new ArmorTrim(material.get(), pattern.get(), false));
		}
	}

	/** Wear on a kit's piece, by its use so far: a tenth to something over half of its life gone. */
	private static void worn(ItemStack stack, RandomSource random) {
		if (!stack.isEmpty() && stack.isDamageableItem() && !stack.isDamaged()) {
			stack.setDamageValue((int)(stack.getMaxDamage() * (WORN_LEAST + (WORN_MOST - WORN_LEAST) * random.nextDouble())));
		}
	}

	private static final double WORN_LEAST = 0.1;
	private static final double WORN_MOST = 0.55;

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
