package com.dinglebob.drifter.world.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;
import com.dinglebob.drifter.social.Disposition;
import com.dinglebob.drifter.world.DrifterGroups;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Vanilla structures that people have moved into (user's ideas, 2026-10-16: a world already lived in): some villages get
 * a few Drifters living in the houses (each takes a bed as its home, with a chest by it; they're a group), and some
 * pillager outposts are held by a gang of Bandit Drifters, who keep new pillagers from turning up there while any of them
 * is alive. Decided once per structure, when its start chunk first loads; done when the whole structure is loaded.
 */
public final class LivedInStructures {
	private static final double VILLAGE_CHANCE = 0.4;
	private static final double OUTPOST_CHANCE = 0.5;
	private static final int CHECK_TICKS = 100;
	private static final int MIN_VILLAGERS = 2;
	private static final int MAX_VILLAGERS = 4;
	private static final int BANDITS = 3;

	/** A structure waiting for all its chunks to load. */
	private record Pending(String kind, String dimension, BoundingBox box) {
	}

	/** An outpost a gang holds: its box and its group. */
	private record Held(String dimension, BoundingBox box, java.util.UUID group) {
	}

	private static final Set<String> decided = new HashSet<>();
	private static final List<Pending> pending = new ArrayList<>();
	private static final List<Held> held = new ArrayList<>();
	private static boolean dirty;

	private LivedInStructures() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(LivedInStructures::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(LivedInStructures::save);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			decided.clear();
			pending.clear();
			held.clear();
		});
		ServerChunkEvents.CHUNK_LOAD.register(LivedInStructures::onChunk);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				settlePending(server);
				if (dirty) {
					save(server);
				}
			}
		});
		// While a gang holds an outpost, no new pillager turns up there (they took it over).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractIllager && !held.isEmpty()) {
				String dimension = level.dimension().location().toString();
				for (Held h : held) {
					if (h.dimension().equals(dimension) && h.box().isInside(entity.blockPosition()) && DrifterGroups.byId(h.group()) != null) {
						entity.discard();
						return;
					}
				}
			}
		});
	}

	private static void onChunk(ServerLevel level, LevelChunk chunk) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}

		var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

		for (var entry : chunk.getAllStarts().entrySet()) {
			ResourceLocation id = registry.getKey(entry.getKey());
			var start = entry.getValue();
			if (id == null || !start.isValid()) {
				continue;
			}

			String kind = id.getPath().startsWith("village_") ? "village" : id.getPath().equals("pillager_outpost") ? "outpost" : null;
			String key = id + "@" + chunk.getPos().x + "," + chunk.getPos().z;
			if (kind == null || !decided.add(key)) {
				continue;
			}

			dirty = true;
			boolean chosen = livedIn(level, kind, chunk.getPos());
			if (chosen) {
				pending.add(new Pending(kind, level.dimension().location().toString(), start.getBoundingBox()));
			}

			Drifter.LOGGER.debug("[Drifter] world gen: the {} at {} {}", kind, start.getBoundingBox().getCenter().toShortString(),
				chosen ? "will be lived in" : "stays as it is");
		}
	}

	/**
	 * Whether this village or outpost is (or will be) lived in: fixed by the world seed and where it starts, not a roll when
	 * it loads, so `/drifter locate` can tell before anyone has been there.
	 */
	public static boolean livedIn(ServerLevel level, String kind, net.minecraft.world.level.ChunkPos start) {
		boolean village = kind.equals("village");
		if (village ? !DrifterConfig.worldgenVillages() : !DrifterConfig.worldgenOutposts()) {
			return false;
		}

		long seed = level.getSeed() ^ (village ? 0x5EEDL : 0x0B57L);
		RandomSource random = RandomSource.create(seed + start.x * 341873128712L + start.z * 132897987541L);
		return random.nextDouble() < (village ? VILLAGE_CHANCE : OUTPOST_CHANCE);
	}

	/** A village or outpost found by {@link #locate}: where, and whether Drifters live there. */
	public record Found(BlockPos pos, boolean livedIn) {
	}

	/**
	 * The nearest lived-in village (or Bandit outpost) from here, searching outward the way /locate does (one candidate
	 * spot per placement region, nearest rings first); null if none within the search.
	 */
	@Nullable
	public static Found locate(ServerLevel level, String kind, BlockPos from, int maxRegions) {
		var setKey = kind.equals("village") ? net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.VILLAGES
			: net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.PILLAGER_OUTPOSTS;
		var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(setKey);
		if (set == null || !(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement placement)) {
			return null;
		}

		var state = level.getChunkSource().getGeneratorState();
		int spacing = placement.spacing();
		int cx = from.getX() >> 4;
		int cz = from.getZ() >> 4;
		int rx0 = Math.floorDiv(cx, spacing);
		int rz0 = Math.floorDiv(cz, spacing);
		Found best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int r = 0; r <= maxRegions; r++) {
			for (int rx = rx0 - r; rx <= rx0 + r; rx++) {
				for (int rz = rz0 - r; rz <= rz0 + r; rz++) {
					if (Math.max(Math.abs(rx - rx0), Math.abs(rz - rz0)) != r) {
						continue;
					}

					// (It takes a chunk in the region, not the region's number.)
					var chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
					if (!placement.isStructureChunk(state, chunk.x, chunk.z) || !livedIn(level, kind, chunk)) {
						continue;
					}

					BlockPos at = present(level, set, placement, chunk);
					if (at == null) {
						continue;
					}

					double distance = at.distSqr(from);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = new Found(at, true);
					}
				}
			}

			// A find in this ring: one more ring could still hold a nearer one (regions are squares), then stop.
			if (best != null && r > 0 && bestDistance < Math.pow((r + 1) * spacing * 16.0, 2)) {
				break;
			}
		}

		return best;
	}

	/** One of the set's structures really starts in this chunk (biome and terrain permitting): where its middle is. */
	@Nullable
	private static BlockPos present(ServerLevel level, net.minecraft.world.level.levelgen.structure.StructureSet set,
		net.minecraft.world.level.levelgen.structure.placement.StructurePlacement placement, net.minecraft.world.level.ChunkPos chunk) {
		for (var entry : set.structures()) {
			var structure = entry.structure().value();
			var check = level.structureManager().checkStructurePresence(chunk, structure, placement, false);
			if (check == net.minecraft.world.level.levelgen.structure.StructureCheckResult.START_NOT_PRESENT) {
				continue;
			}

			var access = level.getChunk(chunk.x, chunk.z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
			var start = level.structureManager().getStartForStructure(net.minecraft.core.SectionPos.bottomOf(access), structure, access);
			if (start != null && start.isValid()) {
				return start.getBoundingBox().getCenter();
			}
		}

		return null;
	}

	private static void settlePending(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (var it = pending.iterator(); it.hasNext();) {
			Pending p = it.next();
			BoundingBox box = p.box();
			if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
				continue;
			}

			it.remove();
			dirty = true;
			if (p.kind().equals("village")) {
				village(level, box);
			} else {
				outpost(level, box);
			}
		}
	}

	/** Drifters move into a village: each takes one of its beds, with a chest beside it; they're one group. */
	private static void village(ServerLevel level, BoundingBox box) {
		RandomSource random = level.getRandom();
		List<BlockPos> beds = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), Math.max(box.minY(), level.getMinBuildHeight()), box.minZ(), box.maxX(),
			Math.min(box.maxY(), level.getMaxBuildHeight() - 1), box.maxZ())) {
			var state = level.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				beds.add(pos.immutable());
			}
		}

		java.util.Collections.shuffle(beds, new java.util.Random(random.nextLong()));
		int count = Math.min(beds.size(), MIN_VILLAGERS + random.nextInt(MAX_VILLAGERS - MIN_VILLAGERS + 1));
		List<DrifterEntity> residents = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos bed = beds.get(i);
			BlockPos stand = standBeside(level, bed);
			if (stand == null) {
				continue;
			}

			DrifterEntity drifter = create(level, stand, null, 2 + random.nextInt(4));
			if (drifter == null) {
				continue;
			}

			// It lives in the village house, as one that moved in by itself does (no hut of its own).
			Home home = new Home(stand, level.dimension().location(), level.getGameTime());
			home.houseBed = bed;
			com.dinglebob.drifter.building.Interior room = com.dinglebob.drifter.building.Interior.of(level, bed);
			BlockPos chest = room != null ? room.spot(com.dinglebob.drifter.building.Interior.Kind.CHEST, java.util.Set.of(stand)) : chestBeside(level, stand);
			if (chest != null) {
				level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
				home.addChest(chest);
			}

			drifter.getDrifterBrain().setHome(home);
			residents.add(drifter);
		}

		group(residents, " of the village");
		Drifter.LOGGER.info("[Drifter] world gen: {} Drifters moved into the village at {}", residents.size(), box.getCenter().toShortString());
	}

	/** Debug: a Bandit gang living here as if it held an outpost (for testing their raids without finding one). */
	public static int gangAt(ServerLevel level, BlockPos middle) {
		List<DrifterEntity> gang = new ArrayList<>();
		for (int i = 0; i < BANDITS; i++) {
			BlockPos spot = groundNear(level, middle, level.getRandom(), 0, 3);
			DrifterEntity drifter = create(level, spot != null ? spot : middle, Disposition.BANDIT, 3 + level.getRandom().nextInt(4));
			if (drifter == null) {
				continue;
			}

			Home home = new Home(middle, level.dimension().location(), level.getGameTime());
			home.outpost = true;
			drifter.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL));
			drifter.getDrifterBrain().setHome(home);
			gang.add(drifter);
		}

		group(gang, "'s Raiders");
		return gang.size();
	}

	/** A gang of Bandits holds the outpost: they live by its tower, and while they do no new pillagers turn up there. */
	private static void outpost(ServerLevel level, BoundingBox box) {
		RandomSource random = level.getRandom();
		// On the ground by the tower (found in testing: the middle of the box is the tower, and they stood on its roof).
		BlockPos middle = groundNear(level, box.getCenter(), random);
		if (middle == null) {
			return;
		}

		List<DrifterEntity> gang = new ArrayList<>();
		for (int i = 0; i < BANDITS; i++) {
			BlockPos spot = groundNear(level, middle, random, 0, 3);
			DrifterEntity drifter = create(level, spot != null ? spot : middle, Disposition.BANDIT, 3 + random.nextInt(4));
			if (drifter == null) {
				continue;
			}

			Home home = new Home(middle, level.dimension().location(), level.getGameTime());
			home.outpost = true;
			// Its starting kit has what it raids with (fire for the woodwork).
			drifter.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL));
			drifter.getDrifterBrain().setHome(home);
			gang.add(drifter);
		}

		var group = group(gang, "'s Raiders");
		if (group != null) {
			held.add(new Held(level.dimension().location().toString(), box, group.id));
			// Pillagers there now: gone too (they lost it).
			level.getEntitiesOfClass(AbstractIllager.class, net.minecraft.world.phys.AABB.of(box)).forEach(net.minecraft.world.entity.Entity::discard);
		}

		Drifter.LOGGER.info("[Drifter] world gen: a Bandit gang holds the pillager outpost at {}", box.getCenter().toShortString());
	}

	@Nullable
	private static DrifterEntity create(ServerLevel level, BlockPos at, @Nullable Disposition disposition, int days) {
		DrifterEntity drifter = ModEntities.DRIFTER.create(level);
		if (drifter == null) {
			return null;
		}

		drifter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
		if (disposition != null) {
			drifter.getSocial().setDisposition(disposition);
		}

		drifter.addPendingCatchUp(24000L * days);
		return level.addFreshEntity(drifter) ? drifter : null;
	}

	/** They all know and trust each other, and are one group (named after its first member). */
	@Nullable
	private static DrifterGroups.Group group(List<DrifterEntity> members, String suffix) {
		if (members.size() < 2) {
			return null;
		}

		for (DrifterEntity a : members) {
			for (DrifterEntity b : members) {
				if (a != b) {
					a.getBonds().setTrust(b, 70.0);
				}
			}
		}

		DrifterEntity leader = members.get(0);
		var group = DrifterGroups.form(leader.getUUID(), leader.getName().getString(), members.get(1).getUUID(), members.get(1).getName().getString(),
			leader.getName().getString() + suffix);
		for (int i = 2; i < members.size(); i++) {
			DrifterGroups.join(group, members.get(i).getUUID(), members.get(i).getName().getString());
		}

		return group;
	}

	/** A floor cell beside the bed with room to stand. */
	/** Open, dry ground round the tower: 8 to 11 blocks out from the middle of the outpost. */
	@Nullable
	private static BlockPos groundNear(ServerLevel level, BlockPos center, RandomSource random) {
		return groundNear(level, center, random, 8, 11);
	}

	@Nullable
	private static BlockPos groundNear(ServerLevel level, BlockPos center, RandomSource random, int min, int max) {
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			BlockPos column = center.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
			BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			var ground = level.getBlockState(feet.below());
			if (ground.isSolid() && ground.getFluidState().isEmpty() && !ground.is(net.minecraft.tags.BlockTags.LEAVES)
				&& Math.abs(feet.getY() - center.getY()) < 12 && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
				return feet;
			}
		}

		return null;
	}

	@Nullable
	private static BlockPos standBeside(ServerLevel level, BlockPos bed) {
		for (BlockPos pos : BlockPos.withinManhattan(bed, 2, 1, 2)) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isSolid()) {
				return pos.immutable();
			}
		}

		return null;
	}

	@Nullable
	private static BlockPos chestBeside(ServerLevel level, BlockPos stand) {
		for (BlockPos pos : BlockPos.withinManhattan(stand, 2, 0, 2)) {
			if (!pos.equals(stand) && level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid() && !byDoor(level, pos)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** Next to a door (a chest there would block the way in). */
	private static boolean byDoor(ServerLevel level, BlockPos pos) {
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(side)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				return true;
			}
		}

		return false;
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
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_lived_in");
	}

	private static void load(MinecraftServer server) {
		CompoundTag tag = state(server).data;
		decided.clear();
		pending.clear();
		held.clear();
		for (Tag t : tag.getList("Decided", Tag.TAG_STRING)) {
			decided.add(t.getAsString());
		}

		for (Tag t : tag.getList("Pending", Tag.TAG_COMPOUND)) {
			CompoundTag p = (CompoundTag)t;
			pending.add(new Pending(p.getString("Kind"), p.getString("Dimension"), box(p.getIntArray("Box"))));
		}

		for (Tag t : tag.getList("Held", Tag.TAG_COMPOUND)) {
			CompoundTag h = (CompoundTag)t;
			held.add(new Held(h.getString("Dimension"), box(h.getIntArray("Box")), h.getUUID("Group")));
		}
	}

	private static void save(MinecraftServer server) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		decided.forEach(key -> list.add(StringTag.valueOf(key)));
		tag.put("Decided", list);
		ListTag pend = new ListTag();
		for (Pending p : pending) {
			CompoundTag t = new CompoundTag();
			t.putString("Kind", p.kind());
			t.putString("Dimension", p.dimension());
			t.putIntArray("Box", ints(p.box()));
			pend.add(t);
		}

		tag.put("Pending", pend);
		ListTag hold = new ListTag();
		for (Held h : held) {
			CompoundTag t = new CompoundTag();
			t.putString("Dimension", h.dimension());
			t.putIntArray("Box", ints(h.box()));
			t.putUUID("Group", h.group());
			hold.add(t);
		}

		tag.put("Held", hold);
		State state = state(server);
		state.data = tag;
		state.setDirty();
		dirty = false;
	}

	private static int[] ints(BoundingBox box) {
		return new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
	}

	private static BoundingBox box(int[] v) {
		return new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5]);
	}
}
