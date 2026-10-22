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
		HousePlan plan = cached(server).get(id);
		// (A house begun from one of the first generator's plans keeps it.)
		if (plan == null && id.startsWith(com.dinglebob.drifter.building.plan.gen.GeneratedPlans.LEGACY_PREFIX)) {
			plan = com.dinglebob.drifter.building.plan.gen.GeneratedPlans.legacy(server, id);
		}

		return plan;
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
