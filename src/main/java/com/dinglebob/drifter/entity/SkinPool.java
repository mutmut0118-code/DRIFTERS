package com.dinglebob.drifter.entity;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The pool of skins a Drifter can be born with (DESIGN.md sections 2 and 4). A skin is a texture plus an
 * arm type (wide "Steve" arms or slim "Alex" arms), picked once and saved.
 *
 * <p>Milestone 1 seeds the pool with the nine vanilla default player skins in both arm types, which
 * every client already has. Bundled Drifter skins (assets/drifter/textures/entity/drifter/...) and
 * archetype weights come later.
 */
public final class SkinPool {
	public record Skin(ResourceLocation texture, boolean slim) {
	}

	private static final List<String> VANILLA_DEFAULT_NAMES = List.of("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri");

	public static final List<Skin> SKINS = VANILLA_DEFAULT_NAMES.stream()
		.flatMap(name -> java.util.stream.Stream.of(
			new Skin(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + name + ".png"), false),
			new Skin(ResourceLocation.withDefaultNamespace("textures/entity/player/slim/" + name + ".png"), true)
		))
		.toList();

	public static final Skin FALLBACK = new Skin(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"), false);

	private SkinPool() {
	}

	public static Skin pick(RandomSource random) {
		return SKINS.get(random.nextInt(SKINS.size()));
	}

	public static Skin pick(RandomSource random, boolean slim) {
		List<Skin> matching = SKINS.stream().filter(skin -> skin.slim() == slim).toList();
		return matching.get(random.nextInt(matching.size()));
	}
}
