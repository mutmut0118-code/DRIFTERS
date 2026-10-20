package com.dinglebob.drifter.whim;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Every whim (milestone 13, docs/WHIMS.md), loaded from {@code drifter_whims} data files on every reload. Whims whose
 * action type isn't known (a datapack's typo, or a modded one whose mod isn't there) are left out with a log line.
 */
public final class Whims {
	private static final String DIRECTORY = "drifter_whims";
	private static volatile Map<String, Whim> whims = Map.of();

	private Whims() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Drifter.id("whims");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				load(manager);
			}
		});
	}

	public static List<Whim> all() {
		return List.copyOf(whims.values());
	}

	@Nullable
	public static Whim byId(String id) {
		return whims.get(id);
	}

	/**
	 * Every whim file, each whim checked as it loads ({@link WhimCheck}): small mistakes are warnings in the log (whim, file,
	 * what, and a guess at what was meant), and only a whim that can't work is left out.
	 */
	private static void load(ResourceManager manager) {
		Map<String, Whim> loaded = new LinkedHashMap<>();
		Map<String, ResourceLocation> from = new java.util.HashMap<>();
		int skipped = 0;
		int warned = 0;
		for (Map.Entry<ResourceLocation, Resource> file : manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (!root.has("whims")) {
					Drifter.LOGGER.warn("[Drifter] {} has no \"whims\" list (a whim file is {\"whims\": [ ... ]})", file.getKey());
					continue;
				}

				int n = 0;
				for (JsonElement element : root.getAsJsonArray("whims")) {
					n++;
					String name = element.isJsonObject() && element.getAsJsonObject().has("id") ? element.getAsJsonObject().get("id").getAsString() : "number " + n;
					try {
						JsonObject json = element.getAsJsonObject();
						Whim whim = Whim.parse(json);
						if (WhimActions.get(whim.action()) == null) {
							Drifter.LOGGER.warn("[Drifter] whim {} in {} left out: no action called \"{}\"{}", whim.id(), file.getKey(), whim.action(),
								WhimCheck.didYouMean(whim.action(), WhimActions.names()));
							skipped++;
							continue;
						}

						// The mod a modded whim needs isn't installed: left out (its ids aren't in the game to check).
						String mod = whim.param("requires_mod", "");
						if (!mod.isEmpty() && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(mod)) {
							skipped++;
							continue;
						}

						List<String> warnings = WhimCheck.check(json, whim);
						for (String warning : warnings) {
							Drifter.LOGGER.warn("[Drifter] whim {} in {}: {}", whim.id(), file.getKey(), warning);
						}

						warned += warnings.isEmpty() ? 0 : 1;
						if (loaded.containsKey(whim.id())) {
							Drifter.LOGGER.warn("[Drifter] whim {} in {} replaces the one of the same id in {}", whim.id(), file.getKey(), from.get(whim.id()));
						}

						loaded.put(whim.id(), whim);
						from.put(whim.id(), file.getKey());
					} catch (RuntimeException e) {
						Drifter.LOGGER.warn("[Drifter] whim {} in {} left out: {}", name, file.getKey(),
							e instanceof IllegalArgumentException ? e.getMessage() : e.toString());
						skipped++;
					}
				}
			} catch (Exception e) {
				Drifter.LOGGER.warn("[Drifter] couldn't read whims from {}: {}", file.getKey(), e.toString());
			}
		}

		whims = Map.copyOf(loaded);
		Drifter.LOGGER.info("[Drifter] loaded {} whims{}{}", loaded.size(), skipped > 0 ? " (" + skipped + " left out)" : "",
			warned > 0 ? "; " + warned + " with warnings above" : "");
	}
}
