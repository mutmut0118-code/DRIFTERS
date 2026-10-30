package com.dinglebob.drifterciv;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Bits of vanilla's villages for Drifter towns (milestone 23, docs/LIVED_WORLD.md W1; the user, 2026-10-30: "lets steal the
 * lamp builds from villages and use them for the towns", then "add the taiga decorations too"): the game's own village lamp
 * posts, by the town's style, and the taiga village's street bits (a campfire, hay with a campfire on it, log seats, a
 * garden box), read from the game's structure files and put down whole, their jigsaw blocks swapped for what they stand
 * for, as a village does. Each only where there's room for all of it on level ground.
 */
final class VillageBits {
	private VillageBits() {
	}

	/** Each style's lamp posts. */
	private static final Map<String, List<String>> LAMPS = Map.of(
		"plains", List.of("village/plains/plains_lamp_1"),
		"desert", List.of("village/desert/desert_lamp_1"),
		"savanna", List.of("village/savanna/savanna_lamp_post_01"),
		"taiga", List.of("village/taiga/taiga_lamp_post_1"),
		"snowy", List.of("village/snowy/snowy_lamp_post_01", "village/snowy/snowy_lamp_post_02", "village/snowy/snowy_lamp_post_03"));
	private static final List<String> DECORATIONS = List.of("village/taiga/taiga_decoration_1", "village/taiga/taiga_decoration_2",
		"village/taiga/taiga_decoration_3", "village/taiga/taiga_decoration_4", "village/taiga/taiga_decoration_5", "village/taiga/taiga_decoration_6");

	/**
	 * A lamp post of the town's style standing at {@code feet} (the block over the ground); where its light is, or null if
	 * one doesn't fit there.
	 */
	@Nullable
	static BlockPos lamp(ServerLevel level, BlockPos feet, String style, RandomSource random) {
		List<String> lamps = LAMPS.getOrDefault(style, LAMPS.get("plains"));
		BoundingBox box = place(level, lamps.get(random.nextInt(lamps.size())), feet, random, false, null);
		if (box == null) {
			return null;
		}

		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(pos).getLightEmission() > 0) {
				return pos.immutable();
			}
		}

		return feet;
	}

	/**
	 * One of the taiga village's street bits at {@code feet}, its wood the town's ({@code wood}: "oak", "acacia"...) and, in a
	 * desert town, sandstone for its cobblestone; where it went, or null if it doesn't fit.
	 */
	@Nullable
	static BoundingBox decoration(ServerLevel level, BlockPos feet, RandomSource random, String wood, boolean sandy) {
		BoundingBox box = place(level, DECORATIONS.get(random.nextInt(DECORATIONS.size())), feet, random, true, null);
		if (box != null) {
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState state = level.getBlockState(pos);
				BlockState swapped = swap(state, wood, sandy);
				if (swapped != state) {
					level.setBlock(pos.immutable(), swapped, Block.UPDATE_CLIENTS);
				}
			}
		}

		return box;
	}

	/** Spruce for the town's own wood, cobblestone for sandstone in the desert, the rest of the block as it was. */
	private static BlockState swap(BlockState state, String wood, boolean sandy) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String path = id.getPath();
		String to = path.startsWith("spruce_") && !wood.equals("spruce") ? wood + path.substring("spruce".length())
			: sandy && path.startsWith("cobblestone") ? "sandstone" + path.substring("cobblestone".length()) : null;
		if (to == null) {
			return state;
		}

		var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(to));
		return block.isPresent() && block.get() != Blocks.AIR ? block.get().withPropertiesOf(state) : state;
	}

	/**
	 * The structure file put down with its jigsaw block (its foot) at {@code feet}, turned at random, if every block of it
	 * goes in air (or plants) over ground it can stand on ({@code allLevel}: all of its foot on level ground; else only its
	 * post's). Its box, or null.
	 */
	@Nullable
	private static BoundingBox place(ServerLevel level, String file, BlockPos feet, RandomSource random, boolean allLevel, @Nullable Rotation turn) {
		var template = level.getStructureManager().get(ResourceLocation.withDefaultNamespace(file)).orElse(null);
		if (template == null) {
			return null;
		}

		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn != null ? turn : Rotation.getRandom(random)).setIgnoreEntities(true)
			.addProcessor(JigsawReplacementProcessor.INSTANCE);
		List<StructureTemplate.StructureBlockInfo> jigsaws = template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW);
		BlockPos foot = jigsaws.isEmpty() ? BlockPos.ZERO : jigsaws.get(0).pos();
		BlockPos origin = feet.subtract(foot);
		BoundingBox box = template.getBoundingBox(settings, origin);
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !(state.canBeReplaced() && state.getFluidState().isEmpty())) {
				return null;
			}
		}

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean post = x == feet.getX() && z == feet.getZ();
				BlockPos under = new BlockPos(x, box.minY() - 1, z);
				if ((allLevel || post) && !level.getBlockState(under).isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) {
					return null;
				}
			}
		}

		template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
		return box;
	}
}
