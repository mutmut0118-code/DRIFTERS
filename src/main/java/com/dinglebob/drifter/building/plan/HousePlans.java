package com.dinglebob.drifter.building.plan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Every house plan a server knows (user's decision, 2026-10-16). Three places:
 * <ul>
 *   <li><b>mod</b>: structure files the mod (or a datapack) ships under {@code data/<ns>/structure/houses/};</li>
 *   <li><b>world</b>: structures saved in this world with a structure block, named {@code drifter_house_<name>};</li>
 *   <li><b>personal</b>: {@code config/drifter/houses/*.nbt}, for every world on this computer (imports and the user's own
 *       designs; never part of the mod).</li>
 * </ul>
 * Read when asked (a plan saved a moment ago with a structure block is there at once).
 */
public final class HousePlans {
	public static final String WORLD_PREFIX = "drifter_house_";

	private HousePlans() {
	}

	public static Path personalFolder() {
		return FabricLoader.getInstance().getConfigDir().resolve("drifter").resolve("houses");
	}

	/** Every plan, by id ("cabin", "world:drifter_house_tower", "personal:stone_house"). */
	public static Map<String, HousePlan> all(MinecraftServer server) {
		Map<String, HousePlan> plans = new LinkedHashMap<>();
		var manager = server.getStructureManager();
		var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
		List<ResourceLocation> ids = manager.listTemplates().toList();
		for (ResourceLocation id : ids) {
			boolean shipped = id.getPath().startsWith("houses/");
			boolean world = id.getPath().startsWith(WORLD_PREFIX);
			if (!shipped && !world) {
				continue;
			}

			manager.get(id).ifPresent(template -> {
				try {
					String name = shipped ? id.getPath().substring("houses/".length()) : "world:" + id.getPath();
					plans.put(name, HousePlan.fromStructure(name, shipped ? "mod" : "world", template.save(new CompoundTag()), blocks));
				} catch (RuntimeException e) {
					Drifter.LOGGER.warn("[Drifter] house plan {} couldn't be read: {}", id, e.toString());
				}
			});
		}

		// The world's generated plans (milestone 15).
		plans.putAll(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.all(server));

		Path folder = personalFolder();
		if (Files.isDirectory(folder)) {
			try (Stream<Path> files = Files.list(folder)) {
				for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".nbt")).toList()) {
					try {
						CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
						String name = "personal:" + file.getFileName().toString().replace(".nbt", "");
						plans.put(name, HousePlan.fromStructure(name, "personal", tag, blocks));
					} catch (IOException | RuntimeException e) {
						Drifter.LOGGER.warn("[Drifter] house plan {} couldn't be read: {}", file, e.toString());
					}
				}
			} catch (IOException e) {
				Drifter.LOGGER.warn("[Drifter] couldn't list {}: {}", folder, e.toString());
			}
		}

		return plans;
	}

	@Nullable
	public static HousePlan get(MinecraftServer server, String id) {
		// A town's store: a house plan with chests along its inside walls (made from the house plan when asked).
		if (id.startsWith(STORE_PREFIX)) {
			HousePlan base = get(server, id.substring(STORE_PREFIX.length()));
			return base == null ? null : STORES.computeIfAbsent(id, key -> withChests(base, key, STORE_CHESTS));
		}

		HousePlan plan = cached(server).get(id);
		// (A house begun from one of the first generator's plans keeps it.)
		if (plan == null && id.startsWith(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.LEGACY_PREFIX)) {
			plan = com.dinglebob.drifter.building.plan.gen.GeneratedPlans.legacy(server, id);
		}

		return plan;
	}

	/** A town's store (milestone 22 step 3c): "store:" and the house plan it's made from. */
	public static final String STORE_PREFIX = "store:";
	private static final int STORE_CHESTS = 8;
	private static final Map<String, HousePlan> STORES = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The plan with up to {@code count} chests along the inside of its walls: on the floor, against a wall, facing into the
	 * room with room in front to open them, clear of doors, torches and stairs, none side by side (two would join into a
	 * double chest).
	 */
	public static HousePlan withChests(HousePlan plan, String id, int count) {
		Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> cells = new java.util.HashMap<>();
		plan.cells.forEach(cell -> cells.put(cell.pos(), cell.state()));
		List<net.minecraft.core.BlockPos> doors = cells.entrySet().stream()
			.filter(e -> e.getValue().getBlock() instanceof net.minecraft.world.level.block.DoorBlock).map(Map.Entry::getKey).toList();
		List<HousePlan.Cell> chests = new ArrayList<>();
		List<HousePlan.Cell> sorted = new ArrayList<>(plan.cells);
		sorted.sort(java.util.Comparator.comparingInt((HousePlan.Cell c) -> c.pos().getZ()).thenComparingInt(c -> c.pos().getX()));
		for (HousePlan.Cell floor : sorted) {
			if (chests.size() >= count || floor.pos().getY() != 0 || !solid(floor.state())) {
				continue;
			}

			net.minecraft.core.BlockPos at = floor.pos().above();
			if (cells.containsKey(at) || cells.containsKey(at.above()) || doors.stream().anyMatch(door -> door.distManhattan(at) <= 3)
				|| chests.stream().anyMatch(chest -> Math.abs(chest.pos().getX() - at.getX()) <= 1 && Math.abs(chest.pos().getZ() - at.getZ()) <= 1)) {
				continue;
			}

			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				var wall = cells.get(at.relative(side));
				var front = at.relative(side.getOpposite());
				if (wall != null && solid(wall) && !cells.containsKey(front) && !cells.containsKey(front.above()) && cells.containsKey(front.below())) {
					chests.add(new HousePlan.Cell(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
						.setValue(net.minecraft.world.level.block.ChestBlock.FACING, side.getOpposite())));
					break;
				}
			}
		}

		List<HousePlan.Cell> all = new ArrayList<>(plan.cells);
		all.addAll(chests);
		return HousePlan.generated(id, plan.size, all);
	}

	private static boolean solid(net.minecraft.world.level.block.state.BlockState state) {
		return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
	}

	private static Map<String, HousePlan> cache = Map.of();
	/** The plans as last read, safe to read from world-generation threads (they never touch the files themselves). */
	private static volatile Map<String, HousePlan> snapshot = Map.of();

	/** The plans as last read (world generation's view; empty until the server has read them once). */
	public static Map<String, HousePlan> snapshot() {
		return snapshot;
	}
	private static long cachedAt = Long.MIN_VALUE / 2;

	/** The plans, read again at most once a minute (for the brain, which asks often). */
	public static Map<String, HousePlan> cached(MinecraftServer server) {
		long now = System.currentTimeMillis();
		if (now - cachedAt > 60_000L) {
			cache = all(server);
			snapshot = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cache));
			cachedAt = now;
		}

		return cache;
	}

	/** Forget the cache (a plan was just imported or kept). */
	public static void invalidate() {
		cachedAt = Long.MIN_VALUE / 2;
	}

	/** Saves a plan's structure tag into the personal folder (an import, or a world plan kept for every world). */
	public static Path savePersonal(String name, CompoundTag structure) throws IOException {
		Path folder = personalFolder();
		Files.createDirectories(folder);
		Path file = folder.resolve(name.replaceAll("[^a-z0-9_]", "_") + ".nbt");
		NbtIo.writeCompressed(structure, file);
		invalidate();
		return file;
	}

	/**
	 * The plans a Drifter weighs when it picks a house: every hand-made one (the mod's, the world's, personal ones) and a
	 * shortlist of generated ones that suit it.
	 */
	public static List<HousePlan> choicesFor(MinecraftServer server, com.dinglebob.drifter.entity.DrifterEntity drifter) {
		List<HousePlan> choices = new ArrayList<>();
		for (HousePlan plan : cached(server).values()) {
			if (!plan.source.equals("generated")) {
				choices.add(plan);
			}
		}

		choices.addAll(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.shortlist(server, drifter));
		return choices;
	}

	/** Names in use, for suggestions. */
	public static List<String> names(MinecraftServer server) {
		return new ArrayList<>(all(server).keySet());
	}
}
