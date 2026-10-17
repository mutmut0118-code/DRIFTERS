package com.dinglebob.drifter.world.gen;

import com.dinglebob.drifter.Drifter;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * A lived-in world (DESIGN.md section 4 "Optional world-gen camps", section 23; user's ideas, 2026-10-16): Drifter camps
 * on the surface (some still lived in), old strip mines deep down, and torches left in caves, as if people had been
 * playing here before you. Each runs once, when the chunk is first made, and each has its own setting. How often is in the
 * placed-feature data files (data/drifter/worldgen/placed_feature), so datapacks can change it.
 */
public final class DrifterFeatures {
	public static final Feature<NoneFeatureConfiguration> CAMP = register("camp", new CampFeature(NoneFeatureConfiguration.CODEC));
	public static final Feature<NoneFeatureConfiguration> STRIP_MINE = register("strip_mine", new StripMineFeature(NoneFeatureConfiguration.CODEC));
	public static final Feature<NoneFeatureConfiguration> WANDERER = register("wanderer", new WandererFeature(NoneFeatureConfiguration.CODEC));
	public static final Feature<NoneFeatureConfiguration> CAVE_TORCHES = register("cave_torches", new CaveTorchFeature(NoneFeatureConfiguration.CODEC));

	private DrifterFeatures() {
	}

	private static <F extends Feature<?>> F register(String name, F feature) {
		return Registry.register(BuiltInRegistries.FEATURE, Drifter.id(name), feature);
	}

	public static void register() {
		add("camp", GenerationStep.Decoration.SURFACE_STRUCTURES);
		add("wanderer", GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		add("strip_mine", GenerationStep.Decoration.UNDERGROUND_STRUCTURES);
		add("cave_torches", GenerationStep.Decoration.UNDERGROUND_DECORATION);
	}

	private static void add(String name, GenerationStep.Decoration step) {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), step, ResourceKey.create(Registries.PLACED_FEATURE, Drifter.id(name)));
	}
}
