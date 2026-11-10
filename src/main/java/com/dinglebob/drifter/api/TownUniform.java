package com.dinglebob.drifter.api;

/**
 * A town's uniform (API 6, docs/API.md "Uniforms"): the armor set its guards wear and the trim on every piece, as plain
 * strings a mod can keep with its town. {@code armorSet} is a modded matching set's id ({@code immersive_armors:warrior})
 * or {@code "minecraft:iron"}; {@code trimPattern} and {@code trimMaterial} are registry ids ({@code minecraft:sentry},
 * {@code minecraft:gold}). An id that no longer resolves (a mod removed) falls back: an unknown set to iron, an unknown
 * trim to none.
 */
public record TownUniform(String armorSet, String trimPattern, String trimMaterial) {
}
