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
		if (id.equals(STALL)) {
			return STALL_PLAN;
		}

		if (id.equals(WELL)) {
			return WELL_PLAN;
		}

		if (id.equals(BOARD)) {
			return BOARD_PLAN;
		}

		if (id.equals(LAMP)) {
			return LAMP_PLAN;
		}

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

	/**
	 * A town's market stall (milestone 22 step 4): 5 by 4, a plank counter along the front (customers stand
	 * before it), a plank floor behind it where the keeper stands ({@link #STALL_STAND}), fence posts at the corners, a slab
	 * roof, a plank back wall with a door (the stall's back to its door: it's turned with its door away from the road).
	 */
	public static final String STALL = "town:stall";
	public static final net.minecraft.core.BlockPos STALL_STAND = new net.minecraft.core.BlockPos(2, 1, 2);
	private static final HousePlan STALL_PLAN = stall();

	private static HousePlan stall() {
		List<HousePlan.Cell> cells = new ArrayList<>();
		var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
		var fence = net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState();
		var slab = net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState();
		// (A plank counter: barrels are containers, and Drifters passing by went to loot them.)
		var counter = net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState();
		var door = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
			.setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH)
			.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT);
		for (int x = 0; x <= 4; x++) {
			for (int z = 1; z <= 3; z++) {
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 0, z), planks));
			}

			for (int z = 0; z <= 3; z++) {
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 3, z), slab));
			}
		}

		for (int x = 1; x <= 3; x++) {
			cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 1, 1), counter));
		}

		for (int y = 1; y <= 2; y++) {
			for (int x : new int[] {0, 4}) {
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, y, 1), fence));
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, y, 3), planks));
			}

			for (int x = 1; x <= 3; x++) {
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, y, 3), x == 2
					? door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, y == 1 ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
						: net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) : planks));
			}
		}

		return HousePlan.generated(STALL, new net.minecraft.core.Vec3i(5, 4, 4), cells);
	}

	/**
	 * A town's well (milestone 22; the user, 2026-10-28: "well with real water"): 4 by 4, a cobblestone floor and rim, the
	 * 2x2 basin in the middle left open for the water someone pours in ({@link #WELL_WATER}: two cells corner to corner, the
	 * other two fill themselves), fence posts at the corners and a cobblestone slab roof.
	 */
	public static final String WELL = "town:well";
	public static final List<net.minecraft.core.BlockPos> WELL_WATER = List.of(new net.minecraft.core.BlockPos(1, 1, 1), new net.minecraft.core.BlockPos(2, 1, 2));
	private static final HousePlan WELL_PLAN = well();

	private static HousePlan well() {
		List<HousePlan.Cell> cells = new ArrayList<>();
		var stone = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
		var fence = net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState();
		var slab = net.minecraft.world.level.block.Blocks.COBBLESTONE_SLAB.defaultBlockState();
		for (int x = 0; x <= 3; x++) {
			for (int z = 0; z <= 3; z++) {
				boolean rim = x == 0 || x == 3 || z == 0 || z == 3;
				boolean corner = (x == 0 || x == 3) && (z == 0 || z == 3);
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 0, z), stone));
				if (rim) {
					cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 1, z), stone));
				}

				if (corner) {
					cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 2, z), fence));
					cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 3, z), fence));
				}

				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 4, z), slab));
			}
		}

		return HousePlan.generated(WELL, new net.minecraft.core.Vec3i(4, 5, 4), cells);
	}

	/**
	 * A town's notice board (milestone 22): a plank board on two fence posts, three signs on its front (north, before it's
	 * turned: {@link #BOARD_SIGNS}) that the town keeps up to date, an oak slab over it.
	 */
	public static final String BOARD = "town:board";
	public static final List<net.minecraft.core.BlockPos> BOARD_SIGNS = List.of(new net.minecraft.core.BlockPos(0, 2, 0), new net.minecraft.core.BlockPos(1, 2, 0),
		new net.minecraft.core.BlockPos(2, 2, 0));
	private static final HousePlan BOARD_PLAN = board();

	private static HousePlan board() {
		List<HousePlan.Cell> cells = new ArrayList<>();
		var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
		var fence = net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState();
		var slab = net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState();
		var sign = net.minecraft.world.level.block.Blocks.OAK_WALL_SIGN.defaultBlockState()
			.setValue(net.minecraft.world.level.block.WallSignBlock.FACING, net.minecraft.core.Direction.NORTH);
		for (int x = 0; x <= 2; x++) {
			if (x != 1) {
				cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 0, 1), fence));
			}

			cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 1, 1), planks));
			cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 2, 1), planks));
			cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 2, 0), sign));
			cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 3, 0), slab));
			cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(x, 3, 1), slab));
		}

		return HousePlan.generated(BOARD, new net.minecraft.core.Vec3i(3, 4, 2), cells);
	}

	/** A town's lamp post (milestone 22): a cobblestone foot, two fence posts and a torch on top. */
	public static final String LAMP = "town:lamp";
	private static final HousePlan LAMP_PLAN = lamp();

	private static HousePlan lamp() {
		List<HousePlan.Cell> cells = new ArrayList<>();
		cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(0, 0, 0), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState()));
		cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(0, 1, 0), net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState()));
		cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(0, 2, 0), net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState()));
		cells.add(new HousePlan.Cell(new net.minecraft.core.BlockPos(0, 3, 0), net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState()));
		return HousePlan.generated(LAMP, new net.minecraft.core.Vec3i(1, 4, 1), cells);
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
