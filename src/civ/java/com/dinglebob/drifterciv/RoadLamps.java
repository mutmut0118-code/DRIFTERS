package com.dinglebob.drifterciv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Road lamps (the user, 2026-11-13: "lets add lamps to the road ... i want us to work together ... so i can build the lamp
 * (and some variations), and when i close the world you take the lamps and put it along the road"). The lamps are the
 * user's own builds, saved as structure files ({@code data/drifter_civ/structure/road_lamp/}): a spruce lamp, an uncommon
 * one with two lanterns, and one with a hanging sign telling the towns either way and how far; birch and froglight ones for
 * the desert, acacia ones for the savanna (the user's notes beside them). Stood beside the roads between towns, a gap of
 * grass between them and the road, switching sides as they go.
 */
final class RoadLamps {
	private RoadLamps() {
	}

	/** A lamp every this many blocks along a road (the user, 2026-11-14: "make the lamps a bit closer to eachother"; was 28). */
	static final int EVERY = 22;
	/** None this near a road's ends (the towns' own streets). */
	static final int END_GAP = 24;
	/**
	 * A distance sign every this many lamps (about 500 blocks; the user, 2026-11-14: "with rarer distance signs"), besides the
	 * one where a road leaves each town ({@link #START}).
	 */
	static final int SIGN_EVERY = 23;
	/** Where a road leaves a town, a lamp with a distance sign this far along it (the user: "replace the sign at the start of
	 * every road from a town to a distance sign"; it was a standing signpost). */
	static final double START = 8;
	/** One lamp in this many is the one with two lanterns (where there's one). */
	static final int DOUBLE_ONE_IN = 6;
	/** Its post this far out from the road's line (the road reaches about three: a block of grass between). */
	static final double OUT = 4.2;
	/** Lamps this near a stretch's end wait for the stretch past it, whose banks could dig under them. */
	static final int JOIN = 18;

	/**
	 * One of the user's lamps: its file, its post's foot in it, and which way the road was from it as built (each one turned
	 * to stand to the road the same way).
	 */
	private record Lamp(String file, BlockPos foot, Direction toRoad) {
	}

	private static final Lamp SPRUCE = new Lamp("spruce", new BlockPos(0, 0, 0), Direction.SOUTH);
	private static final Lamp SPRUCE_DOUBLE = new Lamp("spruce_double", new BlockPos(1, 0, 1), Direction.NORTH);
	private static final Lamp SPRUCE_SIGN = new Lamp("spruce_sign", new BlockPos(0, 0, 1), Direction.NORTH);
	private static final Lamp DESERT = new Lamp("desert", new BlockPos(0, 0, 0), Direction.NORTH);
	private static final Lamp DESERT_SIGN = new Lamp("desert_sign", new BlockPos(0, 0, 1), Direction.NORTH);
	private static final Lamp SAVANNA = new Lamp("savanna", new BlockPos(0, 0, 0), Direction.NORTH);
	private static final Lamp SAVANNA_SIGN = new Lamp("savanna_sign", new BlockPos(0, 0, 1), Direction.NORTH);

	/** How far along the road each of its points is (from its first). */
	static double[] along(List<double[]> points) {
		double[] s = new double[points.size()];
		for (int i = 1; i < points.size(); i++) {
			s[i] = s[i - 1] + Math.hypot(points.get(i)[0] - points.get(i - 1)[0], points.get(i)[1] - points.get(i - 1)[1]);
		}

		return s;
	}

	/**
	 * The lamps of a road from {@code from} to {@code to} (its points, smoothed, and how far along each is) between {@code s0}
	 * and {@code s1} along it, those near a stretch's end waiting as {@link #JOIN} says (the caller works out the range).
	 * Not on a bridge, not on another road ({@code ways}), not where the land beside the road is far off its height or
	 * something's standing. How many.
	 */
	static int light(ServerLevel level, List<double[]> points, double[] s, double s0, double s1, String from, String to, long salt, Set<Long> ways) {
		double total = s[s.length - 1];
		int first = (int)Math.ceil(END_GAP / (double)EVERY);
		int last = (int)Math.floor((total - END_GAP) / EVERY);
		int count = 0;
		for (int k = Math.max(first, (int)Math.ceil(s0 / EVERY)); k <= last && k * EVERY < s1; k++) {
			double at = k * EVERY;
			if (at < s0) {
				continue;
			}

			boolean sign = k % SIGN_EVERY == 0;
			boolean twin = !sign && hash(k, salt) < 1.0 / DOUBLE_ONE_IN;
			int side = (k & 1) == 0 ? 1 : -1;
			if (one(level, points, s, at, side, sign, twin, from, to, total, ways)) {
				count++;
			}
		}

		return count;
	}

	/**
	 * The lamp with a distance sign where the road leaves a town: at its start ({@code atStart}, the {@code from} town's end)
	 * or its end, on the side a signpost stood. Whether it stood.
	 */
	static boolean leaving(ServerLevel level, List<double[]> points, double[] s, boolean atStart, String from, String to, Set<Long> ways) {
		double total = s[s.length - 1];
		if (total < 2 * START) {
			return false;
		}

		return one(level, points, s, atStart ? START : total - START, atStart ? 1 : -1, true, false, from, to, total, ways);
	}

	/** One lamp {@code at} this far along, on this side ({@code 1} left of the way it goes): tried a few blocks either way. */
	private static boolean one(ServerLevel level, List<double[]> points, double[] s, double at, int side, boolean sign, boolean twin, String from, String to,
		double total, Set<Long> ways) {
		for (double shift : new double[] {0, 1, -1, 2, -2, 3, -3}) {
			double[] p = point(points, s, at + shift);
			if (p == null) {
				continue;
			}

			double hx = Math.cos(p[2]);
			double hz = Math.sin(p[2]);
			// (Left of the way it goes: the heading turned a quarter.)
			double px = -hz * side;
			double pz = hx * side;
			int cx = Mth.floor(p[0]);
			int cz = Mth.floor(p[1]);
			int fx = Mth.floor(p[0] + px * OUT);
			int fz = Mth.floor(p[1] + pz * OUT);
			if (!TownBuilder.loadedAt(level, cx, cz) || !TownBuilder.loadedAt(level, fx, fz)) {
				return false;
			}

			// (Not by a bridge: it has its own lanterns. Not by water.)
			int road = TownBuilder.ground(level, cx, cz);
			BlockState top = level.getBlockState(new BlockPos(cx, road, cz));
			if (top.is(BlockTags.PLANKS) || TownBuilder.wet(level, cx, cz) || TownBuilder.wet(level, fx, fz)) {
				continue;
			}

			// (Within a block of the road's height, and no drop between them: on a hillside one stood on a lump of the bank,
			// over a road cut three blocks down.)
			int ground = TownBuilder.ground(level, fx, fz);
			int gap = TownBuilder.ground(level, Mth.floor(p[0] + px * (OUT - 1)), Mth.floor(p[1] + pz * (OUT - 1)));
			BlockState under = level.getBlockState(new BlockPos(fx, ground, fz));
			if (Math.abs(ground - road) > 1 || Math.abs(gap - ground) > 1 || WildRoad.worn(under) || under.is(BlockTags.PLANKS) || under.is(BlockTags.LEAVES)
				|| under.is(BlockTags.LOGS) || !under.isFaceSturdy(level, new BlockPos(fx, ground, fz), Direction.UP)) {
				continue;
			}

			Direction toRoad = Direction.getNearest(-px, 0, -pz);
			Lamp lamp = pick(level, new BlockPos(fx, ground + 1, fz), sign, twin);
			BoundingBox box = place(level, lamp, new BlockPos(fx, ground + 1, fz), toRoad, ways);
			if (box == null) {
				continue;
			}

			if (sign) {
				write(level, box, hx, hz, from, to, at + shift, total);
			}

			if (WildRoad.DEBUG) {
				DrifterCivilizations.LOGGER.info("[lamps] {} at {} {} {} (road {} {} {})", lamp.file(), fx, ground + 1, fz, cx, road, cz);
			}

			return true;
		}

		return false;
	}

	/** Which lamp: the desert's, the savanna's or the spruce one (everywhere else), with its sign or two lanterns. */
	private static Lamp pick(ServerLevel level, BlockPos at, boolean sign, boolean twin) {
		TownBuilder.Style style = TownBuilder.Style.at(level, at);
		if (style == TownBuilder.Style.DESERT) {
			return sign ? DESERT_SIGN : DESERT;
		}

		if (style == TownBuilder.Style.SAVANNA) {
			return sign ? SAVANNA_SIGN : SAVANNA;
		}

		return sign ? SPRUCE_SIGN : twin ? SPRUCE_DOUBLE : SPRUCE;
	}

	/** The point this far along (with its heading), or null past its ends. */
	@Nullable
	private static double[] point(List<double[]> points, double[] s, double at) {
		for (int i = 1; i < points.size(); i++) {
			if (s[i] >= at && s[i] > s[i - 1]) {
				if (at < s[i - 1]) {
					return null;
				}

				double f = (at - s[i - 1]) / (s[i] - s[i - 1]);
				double[] a = points.get(i - 1);
				double[] b = points.get(i);
				return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, Math.atan2(b[1] - a[1], b[0] - a[0])};
			}
		}

		return null;
	}

	/** The lamp stood with its post's foot here, turned to the road; the room it takes clear of all but plants. Its box, or null. */
	@Nullable
	private static BoundingBox place(ServerLevel level, Lamp lamp, BlockPos foot, Direction toRoad, Set<Long> ways) {
		StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(DrifterCivilizations.MOD_ID, "road_lamp/" + lamp.file()))
			.orElse(null);
		if (template == null) {
			return null;
		}

		Rotation turn = Rotation.NONE;
		for (Rotation one : Rotation.values()) {
			if (one.rotate(lamp.toRoad()) == toRoad) {
				turn = one;
			}
		}

		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn).setIgnoreEntities(true);
		BlockPos origin = foot.subtract(StructureTemplate.calculateRelativePosition(settings, lamp.foot()));
		BoundingBox box = template.getBoundingBox(settings, origin);
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (ways.contains(BlockPos.asLong(pos.getX(), 0, pos.getZ()))) {
				return null;
			}

			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !((state.canBeReplaced() || com.dinglebob.drifter.api.DrifterApi.naturalSlab(state)) && state.getFluidState().isEmpty())) {
				return null;
			}
		}

		// (Grass and flowers where it stands cleared, so they don't stand inside its post.)
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		template.placeInWorld(level, origin, origin, settings, RandomSource.create(foot.asLong()), Block.UPDATE_CLIENTS);
		return box;
	}

	/**
	 * Its hanging sign's two faces: each the town the way a reader facing it walks, and how far along the road (the user's
	 * sign: "city 200 blocks away or sum idk", and on its back "other city thats in the other direction").
	 */
	private static void write(ServerLevel level, BoundingBox box, double hx, double hz, String from, String to, double at, double total) {
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.ALL_HANGING_SIGNS) || !state.hasProperty(BlockStateProperties.ROTATION_16)) {
				continue;
			}

			// (Squared to the road: one was built a notch off.)
			int rotation = (state.getValue(BlockStateProperties.ROTATION_16) + 2) / 4 * 4 & 15;
			level.setBlock(pos, state.setValue(BlockStateProperties.ROTATION_16, rotation), Block.UPDATE_CLIENTS);
			if (!(level.getBlockEntity(pos) instanceof SignBlockEntity entity)) {
				continue;
			}

			// (Its front faces this way; one reading it looks, and walks, the other.)
			Direction faces = Direction.fromYRot(rotation * 22.5);
			boolean frontTo = -faces.getStepX() * hx - faces.getStepZ() * hz > 0;
			long seed = pos.asLong();
			entity.setText(text(frontTo ? to : from, frontTo ? total - at : at, seed), true);
			entity.setText(text(frontTo ? from : to, frontTo ? at : total - at, seed * 31 + 7), false);
			entity.setChanged();
			level.sendBlockUpdated(pos, state, level.getBlockState(pos), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * A face of the sign, in a few words (the user, 2026-11-14: "make the distance signs prose"): the town and about how far
	 * ("Ashford / lies some / 200 blocks / this way"); right by the town, a welcome. Hanging signs are narrow: ten letters or
	 * so a line, the town's name on a line of its own.
	 */
	static SignText text(String name, double far, long seed) {
		if (far < 60) {
			return lines("Welcome to", name, "", "");
		}

		long n = far < 100 ? Math.max(10, Math.round(far / 10) * 10) : far < 1000 ? Math.round(far / 50) * 50 : Math.round(far / 100) * 100;
		// (A name too long for a line: on two, and the distance plainly.)
		int cut = name.lastIndexOf(' ', 11);
		if (name.length() > 11 && cut > 0) {
			return lines(name.substring(0, cut), name.substring(cut + 1), "some " + n, "blocks on");
		}

		return switch ((int)Math.floorMod(seed ^ (seed >>> 29), 4L)) {
			case 0 -> lines(name, "lies some", n + " blocks", "this way");
			case 1 -> lines("This road", "goes on to", name + ",", n + " blocks");
			case 2 -> lines(name + " is", "about " + n, "blocks down", "this road");
			default -> lines("Walk " + n, "blocks to", "reach", name);
		};
	}

	private static SignText lines(String a, String b, String c, String d) {
		return new SignText().setMessage(0, Component.literal(a)).setMessage(1, Component.literal(b)).setMessage(2, Component.literal(c))
			.setMessage(3, Component.literal(d));
	}

	private static double hash(int k, long salt) {
		long h = k * 0x9E3779B97F4A7C15L ^ salt;
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/**
	 * Debug: every build within {@code radius} of {@code at} standing on the land (blocks that aren't the land, the road or
	 * a standing sign; touching ones are one build; a hanging sign is part of one), saved as {@code lamps_out/lamp_N.nbt}
	 * in the game's folder, with {@code lamps_out/lamps.txt}: each one's size, its foot (the block of it on the ground
	 * nearest the road), which way the road is from it and how far, its blocks, and what the standing signs beside it say
	 * (notes, taken away first so they aren't saved with a lamp). How many builds.
	 */
	static int grab(ServerLevel level, BlockPos at, int radius) throws java.io.IOException {
		Path out = level.getServer().getServerDirectory().resolve("lamps_out");
		Files.createDirectories(out);
		Set<Long> road = new HashSet<>();
		Set<Long> parts = new HashSet<>();
		Map<BlockPos, String> signs = new LinkedHashMap<>();
		for (int x = at.getX() - radius; x <= at.getX() + radius; x++) {
			for (int z = at.getZ() - radius; z <= at.getZ() + radius; z++) {
				int ground = TownBuilder.ground(level, x, z);
				if (WildRoad.worn(level.getBlockState(new BlockPos(x, ground, z)))) {
					road.add(BlockPos.asLong(x, 0, z));
				}

				for (int y = level.getMinBuildHeight() + 1; y < at.getY() + 32; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || land(state)) {
						continue;
					}

					// (A standing sign is a note beside a lamp; a hanging one is part of it, the distance sign.)
					if (state.is(BlockTags.SIGNS) && !state.is(BlockTags.ALL_HANGING_SIGNS)) {
						if (level.getBlockEntity(pos) instanceof SignBlockEntity entity) {
							signs.put(pos.immutable(), words(entity.getFrontText()) + (words(entity.getBackText()).isEmpty() ? "" : " / back: " + words(entity
								.getBackText())));
						}

						continue;
					}

					parts.add(pos.asLong());
				}
			}
		}

		for (BlockPos sign : signs.keySet()) {
			level.setBlock(sign, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		StringBuilder notes = new StringBuilder();
		Set<Long> seen = new HashSet<>();
		int count = 0;
		for (long start : parts) {
			if (!seen.add(start)) {
				continue;
			}

			// One build: everything touching, corners too.
			List<BlockPos> build = new ArrayList<>();
			ArrayDeque<Long> queue = new ArrayDeque<>(List.of(start));
			while (!queue.isEmpty()) {
				BlockPos pos = BlockPos.of(queue.poll());
				build.add(pos);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							long next = pos.offset(dx, dy, dz).asLong();
							if (parts.contains(next) && seen.add(next)) {
								queue.add(next);
							}
						}
					}
				}
			}

			int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			for (BlockPos pos : build) {
				x0 = Math.min(x0, pos.getX());
				y0 = Math.min(y0, pos.getY());
				z0 = Math.min(z0, pos.getZ());
				x1 = Math.max(x1, pos.getX());
				y1 = Math.max(y1, pos.getY());
				z1 = Math.max(z1, pos.getZ());
			}

			// Its foot: of its lowest blocks, the one nearest the road; the road's way from there, and how far its edge is.
			BlockPos foot = null;
			double footAway = Double.MAX_VALUE;
			long nearest = 0;
			for (BlockPos pos : build) {
				if (pos.getY() != y0) {
					continue;
				}

				for (long column : road) {
					double d = Math.hypot(BlockPos.getX(column) - pos.getX(), BlockPos.getZ(column) - pos.getZ());
					if (d < footAway) {
						footAway = d;
						foot = pos;
						nearest = column;
					}
				}
			}

			if (foot == null) {
				foot = build.get(0);
			}

			Direction toRoad = road.isEmpty() ? Direction.NORTH : Direction.getNearest(BlockPos.getX(nearest) - foot.getX(), 0, BlockPos.getZ(nearest) - foot.getZ());
			StructureTemplate template = new StructureTemplate();
			template.fillFromWorld(level, new BlockPos(x0, y0, z0), new Vec3i(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1), false, Blocks.AIR);
			CompoundTag tag = template.save(new CompoundTag());
			String name = "lamp_" + (++count);
			NbtIo.writeCompressed(tag, out.resolve(name + ".nbt"));
			int ground = foot.getY() - 1;
			notes.append(name).append(": at ").append(x0).append(' ').append(y0).append(' ').append(z0).append(", size ").append(x1 - x0 + 1).append('x')
				.append(y1 - y0 + 1).append('x').append(z1 - z0 + 1).append(", ").append(build.size()).append(" blocks, foot ").append(foot.getX() - x0).append(' ')
				.append(foot.getY() - y0).append(' ').append(foot.getZ() - z0).append(" (on ").append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(
					new BlockPos(foot.getX(), ground, foot.getZ())).getBlock()).getPath()).append("), road ").append(toRoad.getName()).append(" ")
				.append(String.format(java.util.Locale.ROOT, "%.1f", footAway)).append(" away");
			for (var sign : signs.entrySet()) {
				BlockPos pos = sign.getKey();
				if (pos.getX() >= x0 - 3 && pos.getX() <= x1 + 3 && pos.getZ() >= z0 - 3 && pos.getZ() <= z1 + 3) {
					notes.append("; sign: \"").append(sign.getValue()).append('"');
				}
			}

			for (BlockPos pos : build) {
				notes.append("\n    ").append(pos.getX() - x0).append(' ').append(pos.getY() - y0).append(' ').append(pos.getZ() - z0).append(' ').append(level.getBlockState(pos));
			}

			notes.append('\n');
		}

		for (var sign : signs.entrySet()) {
			notes.append("sign at ").append(sign.getKey().toShortString()).append(": \"").append(sign.getValue()).append("\"\n");
		}

		Files.writeString(out.resolve("lamps.txt"), notes.toString());
		DrifterCivilizations.LOGGER.info("[lamps] {} builds saved to {}:\n{}", count, out, notes);
		return count;
	}

	private static String words(SignText text) {
		StringBuilder words = new StringBuilder();
		for (var line : text.getMessages(false)) {
			words.append(line.getString()).append(' ');
		}

		return words.toString().trim();
	}

	/** The land and the road: what a build stands on, not part of it. */
	private static boolean land(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.BEDROCK) || WildRoad.worn(state) || state.is(Blocks.STONE)
			|| state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || com.dinglebob.drifter.api.DrifterApi.naturalSlab(state)
			|| WildRoad.slabOf(Blocks.PACKED_MUD.defaultBlockState()) != null && state.getBlock() == WildRoad.slabOf(Blocks.PACKED_MUD.defaultBlockState()).getBlock();
	}
}
