package com.dinglebob.drifter.building.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A plan's materials made the Drifter's own (user's idea, 2026-10-16: "it can use logs that are around it, so it's not a
 * specific spruce house... it generalizes it and creates variety"). Wood is a family (logs, planks, stairs, slabs,
 * fences, doors, trapdoors...): the plan's main wood becomes the wood it has or sees most round it, and a second wood the
 * plan uses for contrast becomes a second wood it knows (or stays). Cobblestone and cobbled deepslate swap the same way.
 * Everything else stays as the plan has it. Block properties (which way a stair faces) carry over.
 */
public final class HousePalette {
	/** Overworld woods, in the order players usually have them. */
	public static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
	/** How dark each wood's planks are (1 light to 7 dark): a frame should stand out from its wall (guidebook: "pair dark with light"). */
	private static final Map<String, Integer> DARKNESS = Map.of("birch", 1, "cherry", 2, "oak", 3, "jungle", 4, "acacia", 4, "mangrove", 5,
		"spruce", 6, "dark_oak", 7);
	/** A second wood that stands out against each (when it can be any). */
	private static final Map<String, String> PARTNER = Map.of("oak", "spruce", "birch", "spruce", "spruce", "oak", "dark_oak", "oak",
		"jungle", "dark_oak", "acacia", "dark_oak", "mangrove", "dark_oak", "cherry", "dark_oak");
	/** Parts of a wood family, as their ids end (the stripped ones start with "stripped_"). */
	private static final List<String> PARTS = List.of("_log", "_wood", "_planks", "_stairs", "_slab", "_fence_gate", "_fence", "_door",
		"_trapdoor", "_button", "_pressure_plate", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign");

	/**
	 * A building stone (0.22.40-side.2, the user: "more variation to stone materials, instead of just using cobblestone"):
	 * what a plan's cobblestone becomes ({@code rough}, and its stairs, slabs and walls), what its stone bricks become
	 * ({@code fine}; {@code finePart} its stairs and slabs), the item it's dug as, the block it's dug from, the stones that
	 * look well as its trim (the steps round the foundation, the sills; the user: "think about the palette": pairs chosen
	 * by eye, not any two), and what a textured wall scatters through its fine blocks (only its own: cracked bricks,
	 * tiles; the user didn't like one stone dotted through another).
	 */
	public record Stone(String name, String rough, String fine, String finePart, Item raw, Block natural, List<String> trims, List<String> fineMix,
		@Nullable Block madeFrom) {
	}

	/** Blocks of what a stone is made from (sand) to one of it (sandstone: four sand). */
	private static final int MADE_FROM_EACH = 4;

	/**
	 * Stones a house can be built of; cobblestone (the plan as drawn) first. No granite or diorite (the user: "take granite
	 * out of the equation"; diorite's white speckle clashes the same way). The pairs: stone bricks over a polished andesite
	 * or deepslate brick step; deepslate bricks over a stone brick one; tuff bricks over deepslate bricks; polished
	 * andesite over deepslate or stone bricks.
	 */
	public static final List<Stone> STONES = List.of(
		new Stone("cobblestone", "cobblestone", "stone_bricks", "stone_brick", Items.COBBLESTONE, Blocks.STONE, List.of("andesite", "deepslate"),
			List.of("cracked_stone_bricks"), null),
		new Stone("deepslate", "cobbled_deepslate", "deepslate_bricks", "deepslate_brick", Items.COBBLED_DEEPSLATE, Blocks.DEEPSLATE,
			List.of("cobblestone"), List.of("cracked_deepslate_bricks", "deepslate_tiles"), null),
		new Stone("andesite", "andesite", "polished_andesite", "polished_andesite", Items.ANDESITE, Blocks.ANDESITE, List.of("deepslate", "cobblestone"),
			List.of(), null),
		new Stone("tuff", "tuff", "tuff_bricks", "tuff_brick", Items.TUFF, Blocks.TUFF, List.of("deepslate"), List.of("polished_tuff"), null),
		// The desert's and the badlands' (biome houses: the sand round them, as the wood is the trees round them): sandstone
		// walls, smooth sandstone steps and sills; made from the sand it digs (four to a block) or dug as it is.
		new Stone("sandstone", "sandstone", "cut_sandstone", "smooth_sandstone", Items.SANDSTONE, Blocks.SANDSTONE, List.of(), List.of(),
			Blocks.SAND),
		new Stone("red_sandstone", "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone", Items.RED_SANDSTONE, Blocks.RED_SANDSTONE,
			List.of(), List.of(), Blocks.RED_SAND));
	/** Saved with the woods (so a town build's palette reaches every builder as it is): the main stone, the trim's, the mix. */
	public static final String STONE_KEY = "#stone";
	public static final String TRIM_KEY = "#trim";
	public static final String MIX_KEY = "#mix";
	/** One block in this many of a textured brick wall is another of its own stone (a cracked brick, a tile). */
	private static final int MIX_ONE_IN = 6;
	/** How often a Drifter builds in a stone round it rather than cobblestone, gives the house a trim, mixes its walls. */
	private static final float LOCAL_STONE_CHANCE = 0.75F;
	private static final float TRIM_CHANCE = 0.7F;
	private static final float MIX_CHANCE = 0.6F;
	/** How far round it a Drifter counts the stone it knows of, and the most blocks it counts. */
	private static final int STONE_RADIUS = 48;
	private static final int STONE_COUNT_MAX = 4000;

	private final Map<String, String> woods;
	private final boolean deepslate;
	/** The main stone and the trim's (null: as drawn, or the old cobblestone-to-deepslate swap); the mix's seed (null: none). */
	@Nullable
	private final Stone stone;
	@Nullable
	private final Stone trim;
	@Nullable
	private final Integer mix;

	private HousePalette(Map<String, String> woods, boolean deepslate) {
		this.woods = woods;
		this.deepslate = deepslate;
		this.stone = stoneNamed(woods.get(STONE_KEY));
		Stone trim = stoneNamed(woods.get(TRIM_KEY));
		this.trim = trim == null ? this.stone : trim;
		Integer mix = null;
		try {
			mix = woods.containsKey(MIX_KEY) ? Integer.valueOf(woods.get(MIX_KEY)) : null;
		} catch (NumberFormatException e) {
			// (No mix.)
		}

		this.mix = this.stone == null ? null : mix;
	}

	/** A saved palette (the one it picked when it chose the plan). */
	public static HousePalette of(Map<String, String> woods, boolean deepslate) {
		return new HousePalette(Map.copyOf(woods), deepslate);
	}

	/** This palette's woods with stones in place of the plan's cobblestone and stone bricks (for the parts exhibit and tests). */
	public HousePalette withStone(String stone, String trim, @Nullable Integer mix) {
		Map<String, String> map = new HashMap<>(this.woods);
		map.put(STONE_KEY, stone);
		map.put(TRIM_KEY, trim);
		map.remove(MIX_KEY);
		if (mix != null) {
			map.put(MIX_KEY, Integer.toString(mix));
		}

		return new HousePalette(Map.copyOf(map), false);
	}

	/** The woods it swaps, and the stones under {@link #STONE_KEY} and the rest (saved as they are). */
	public Map<String, String> woods() {
		return this.woods;
	}

	public boolean deepslate() {
		return this.deepslate;
	}

	@Nullable
	public static Stone stoneNamed(@Nullable String name) {
		for (Stone stone : STONES) {
			if (stone.name().equals(name)) {
				return stone;
			}
		}

		return null;
	}

	/** As the plan is: nothing swapped. */
	public static HousePalette asDrawn() {
		return new HousePalette(Map.of(), false);
	}

	/**
	 * This Drifter's palette for this plan: the plan's woods by how much it uses them, matched to the woods it has (logs and
	 * planks on it and in its chests) and sees round home, most first; deepslate for cobblestone if it has more of that.
	 */
	/** The plan's main wood as this one, its other woods one that stands out against it (world-gen houses: the wood that grows round them). */
	/**
	 * Its stone, kept with its woods (so a home's saved palette has it): what cobblestone (and its stairs, slabs and walls)
	 * becomes, e.g. "sandstone" (a desert town: milestone 23 A3).
	 */
	public static final String STONE = "@stone";

	public static HousePalette forWood(HousePlan plan, String wood) {
		return forWood(plan, wood, null);
	}

	public static HousePalette forWood(HousePlan plan, String wood, @org.jetbrains.annotations.Nullable String stone) {
		Map<String, String> map = new HashMap<>();
		if (stone != null) {
			map.put(STONE, stone);
		}

		List<String> planWoods = woodsByUse(plan);
		for (int i = 0; i < planWoods.size(); i++) {
			map.put(planWoods.get(i), i == 0 ? wood : PARTNER.getOrDefault(wood, wood));
		}

		return new HousePalette(map, false);
	}

	public static HousePalette forDrifter(DrifterEntity drifter, HousePlan plan) {
		List<String> planWoods = woodsByUse(plan);
		List<String> theirs = woodsAround(drifter);
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < planWoods.size(); i++) {
			String to = i < theirs.size() ? theirs.get(i) : theirs.isEmpty() ? planWoods.get(i) : theirs.get(0);
			map.put(planWoods.get(i), to);
		}

		// The second wood (the frame, the roof) the one it knows that stands out most against the first, of its three most
		// at hand (two woods alike, oak and jungle say, make a flat wall). One wood only: the house is all that wood.
		if (planWoods.size() >= 2 && theirs.size() >= 2) {
			String main = theirs.get(0);
			String best = theirs.get(1);
			for (String candidate : theirs.subList(1, Math.min(3, theirs.size()))) {
				if (contrast(main, candidate) > contrast(main, best)) {
					best = candidate;
				}
			}

			map.put(planWoods.get(1), best);
			if (planWoods.size() >= 3 && map.get(planWoods.get(2)).equals(best)) {
				map.put(planWoods.get(2), theirs.get(1).equals(best) ? theirs.get(Math.min(2, theirs.size() - 1)) : theirs.get(1));
			}
		}

		chooseStones(drifter, plan, map);
		return new HousePalette(map, false);
	}

	/**
	 * The plan's stone in a stone it has or knows of round it (enough for the walls), the stairs and slabs (the step round
	 * the foundation, the sills) in a second one that goes with it, and some brick walls textured. Each Drifter's taste for each plan
	 * is its own and stays the same (the same draw every time it looks at that plan); only stone it could get counts.
	 */
	private static void chooseStones(DrifterEntity drifter, HousePlan plan, Map<String, String> map) {
		double[] need = stoneNeeds(plan);
		if (need[0] + need[1] <= 0.0) {
			return;
		}

		Map<Stone, Integer> have = stonesAround(drifter);
		RandomSource random = RandomSource.create(drifter.getUUID().getLeastSignificantBits() * 31L + plan.id.hashCode());
		Stone cobblestone = STONES.get(0);
		Stone main = cobblestone;
		Stone local = null;
		for (Stone stone : STONES) {
			if (stone != cobblestone && have.getOrDefault(stone, 0) >= need[0] && (local == null || have.get(stone) > have.get(local))) {
				local = stone;
			}
		}

		// (Carrying more cobbled deepslate than cobblestone, as before 0.22.40-side.2: deepslate.)
		if (local != null && (random.nextFloat() < LOCAL_STONE_CHANCE || local.name().equals("deepslate")
			&& count(drifter, Items.COBBLED_DEEPSLATE) > count(drifter, Items.COBBLESTONE))) {
			main = local;
		}

		// The trim: a stone that goes with it, the first of those it could get enough of (its own kind of house, the other
		// first now and then).
		Stone trim = main;
		if (random.nextFloat() < TRIM_CHANCE) {
			List<String> trims = new ArrayList<>(main.trims());
			if (trims.size() > 1 && random.nextBoolean()) {
				java.util.Collections.reverse(trims);
			}

			for (String name : trims) {
				Stone stone = stoneNamed(name);
				if (stone != null && have.getOrDefault(stone, 0) >= need[1]) {
					trim = stone;
					break;
				}
			}
		}

		boolean mixed = random.nextFloat() < MIX_CHANCE;
		int seed = random.nextInt(1 << 20);
		if (main == cobblestone && trim == cobblestone && !mixed) {
			return;
		}

		map.put(STONE_KEY, main.name());
		map.put(TRIM_KEY, trim.name());
		if (mixed) {
			map.put(MIX_KEY, Integer.toString(seed));
		}
	}

	/**
	 * The raw stone the plan's stone blocks come to: [0] its full blocks (the main stone), [1] its stairs, slabs and walls
	 * (the trim's).
	 */
	static double[] stoneNeeds(HousePlan plan) {
		double main = 0.0;
		double trim = 0.0;
		for (HousePlan.Cell cell : plan.cells) {
			String path = BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getPath();
			if (path.equals("cobblestone")) {
				main += 1.0;
			} else if (path.equals("stone_bricks")) {
				main += 1.0;
			} else if (path.startsWith("cobblestone_") || path.startsWith("stone_brick_")) {
				trim += path.endsWith("_stairs") ? 1.5 : path.endsWith("_slab") ? 0.5 : 1.0;
			}
		}

		return new double[] {main, trim};
	}

	/** The last count (it looks at many plans at once: one look round for them all). */
	private static java.util.UUID countedFor;
	private static long countedAt;
	private static Map<Stone, Integer> counted = Map.of();

	/** Each stone it has (on it, in its chests) and knows of round it that it could dig (a stone block it's seen: one apiece). */
	public static synchronized Map<Stone, Integer> stonesAround(DrifterEntity drifter) {
		long now = drifter.level().getGameTime();
		if (drifter.getUUID().equals(countedFor) && now - countedAt >= 0 && now - countedAt < 100) {
			return counted;
		}

		Map<Stone, Integer> have = new HashMap<>();
		for (Stone stone : STONES) {
			int count = count(drifter, stone.raw());
			Block fine = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(stone.fine()));
			if (fine != Blocks.AIR && fine.asItem() != stone.raw()) {
				count += count(drifter, fine.asItem());
			}

			if (stone.madeFrom() != null) {
				count += count(drifter, stone.madeFrom().asItem()) / MADE_FROM_EACH;
			}

			have.put(stone, count);
		}

		var level = drifter.level();
		Map<Stone, Integer> from = new HashMap<>();
		for (var pos : KnownBlocks.find(drifter, STONE_RADIUS, state -> naturalStone(state) != null || madeFromOf(state) != null, STONE_COUNT_MAX)) {
			BlockState state = level.getBlockState(pos);
			Stone stone = naturalStone(state);
			Stone source = madeFromOf(state);
			if (stone != null && KnownBlocks.mayTake(level, pos)) {
				have.merge(stone, 1, Integer::sum);
			} else if (source != null && KnownBlocks.mayTake(level, pos)) {
				from.merge(source, 1, Integer::sum);
			}
		}

		from.forEach((stone, blocks) -> have.merge(stone, blocks / MADE_FROM_EACH, Integer::sum));

		countedFor = drifter.getUUID();
		countedAt = now;
		counted = Map.copyOf(have);
		return counted;
	}

	/** The stone this block (sand) is made into, or null. */
	@Nullable
	private static Stone madeFromOf(BlockState state) {
		for (Stone stone : STONES) {
			if (stone.madeFrom() != null && state.is(stone.madeFrom())) {
				return stone;
			}
		}

		return null;
	}

	@Nullable
	private static Stone naturalStone(BlockState state) {
		for (Stone stone : STONES) {
			if (state.is(stone.natural())) {
				return stone;
			}
		}

		return null;
	}

	private static int contrast(String a, String b) {
		return Math.abs(DARKNESS.getOrDefault(a, 4) - DARKNESS.getOrDefault(b, 4));
	}

	/** The plan's woods, the most used first. */
	static List<String> woodsByUse(HousePlan plan) {
		Map<String, Integer> uses = new LinkedHashMap<>();
		for (HousePlan.Cell cell : plan.cells) {
			String wood = woodOf(BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getPath());
			if (wood != null) {
				uses.merge(wood, 1, Integer::sum);
			}
		}

		List<String> order = new ArrayList<>(uses.keySet());
		order.sort((a, b) -> uses.get(b) - uses.get(a));
		return order;
	}

	/** Woods it has (on it and stored at home) and sees round it, the most first. */
	public static List<String> woodsAround(DrifterEntity drifter) {
		Map<String, Integer> score = new LinkedHashMap<>();
		var inventory = drifter.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			var stack = inventory.getItem(slot);
			if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
				String wood = woodOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
				if (wood != null) {
					score.merge(wood, stack.getCount() * (stack.is(ItemTags.LOGS) ? 4 : 1), Integer::sum);
				}
			}
		}

		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			home.stored().forEach((item, count) -> {
				String wood = woodOf(BuiltInRegistries.ITEM.getKey(item).getPath());
				if (wood != null && (new net.minecraft.world.item.ItemStack(item).is(ItemTags.LOGS) || new net.minecraft.world.item.ItemStack(item).is(ItemTags.PLANKS))) {
					score.merge(wood, count, Integer::sum);
				}
			});
		}

		// Trees it knows round here: a few points a log.
		for (var pos : KnownBlocks.find(drifter, 48, state -> state.is(BlockTags.LOGS), 64)) {
			String wood = woodOf(BuiltInRegistries.BLOCK.getKey(drifter.level().getBlockState(pos).getBlock()).getPath());
			if (wood != null) {
				score.merge(wood, 3, Integer::sum);
			}
		}

		List<String> order = new ArrayList<>(score.keySet());
		order.sort((a, b) -> score.get(b) - score.get(a));
		return order;
	}

	/** Which overworld wood an id belongs to ("spruce_stairs" → spruce), or null. */
	@Nullable
	public static String woodOf(String path) {
		String p = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
		for (String wood : WOODS) {
			if (!p.startsWith(wood + "_")) {
				continue;
			}

			String rest = p.substring(wood.length());
			for (String part : PARTS) {
				if (rest.equals(part)) {
					return wood;
				}
			}
		}

		return null;
	}

	private static int count(DrifterEntity drifter, Item item) {
		Home home = drifter.getDrifterBrain().home();
		return drifter.getInventory().count(stack -> stack.is(item)) + (home == null ? 0 : home.stored().getOrDefault(item, 0));
	}

	/** The block this palette puts where the plan has {@code state}, with the same facing, half and the rest (unmixed). */
	public BlockState apply(BlockState state) {
		return this.apply(state, null);
	}

	/** The same, at {@code at} in the plan (where a mixed wall has its other blocks: the same ones every time). */
	public BlockState apply(BlockState state, @Nullable net.minecraft.core.BlockPos at) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String path = id.getPath();
		String swapped = path;
		String wood = woodOf(path);
		// Glass panes go in as glass blocks (user, 2026-10-17: "none of the panes connect to the wall": a lone pane in a
		// one-block window is a thin post).
		if (path.endsWith("glass_pane")) {
			swapped = path.substring(0, path.length() - "_pane".length());
		} else if (wood != null && this.woods.containsKey(wood) && !this.woods.get(wood).equals(wood)) {
			String to = this.woods.get(wood);
			swapped = path.startsWith("stripped_") ? "stripped_" + to + path.substring(("stripped_" + wood).length()) : to + path.substring(wood.length());
		} else if (this.stone != null && (path.startsWith("cobblestone") || path.equals("stone_bricks") || path.startsWith("stone_brick_"))) {
			swapped = this.stoneFor(path, at);
		} else if (this.woods.containsKey(STONE) && path.startsWith("cobblestone")) {
			// (A town's stone, from its biome: the addon's, milestone 23.)
			swapped = this.woods.get(STONE) + path.substring("cobblestone".length());
		} else if (this.deepslate && path.startsWith("cobblestone")) {
			swapped = "cobbled_deepslate" + path.substring("cobblestone".length());
		}

		if (swapped.equals(path)) {
			return state;
		}

		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), swapped));
		if (block == Blocks.AIR) {
			return state;
		}

		BlockState out = block.defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			out = copy(state, out, property);
		}

		return out;
	}

	/**
	 * A plan's cobblestone or stone brick block in this palette's stones: full blocks in the main stone, stairs, slabs and
	 * walls in the trim's (its polished or brick form where there is one; a trim of the house's own stone keeps the plan's
	 * rough steps rough). Textured: one block in six of a brick wall a cracked brick or the like, of the same stone.
	 */
	private String stoneFor(String path, @Nullable net.minecraft.core.BlockPos at) {
		if (path.equals("cobblestone")) {
			return this.stone.rough();
		}

		if (path.equals("stone_bricks")) {
			List<String> mix = this.stone.fineMix();
			return !mix.isEmpty() && this.mixed(at, 1) ? mix.get(Math.floorMod(hash(at, this.mix + 2), mix.size())) : this.stone.fine();
		}

		boolean fine = path.startsWith("stone_brick_");
		String rest = path.substring(fine ? "stone_brick".length() : "cobblestone".length());
		// (A trim of another stone in its dressed form, polished or brick, even where the plan has cobblestone steps.)
		boolean dressed = fine || this.trim != this.stone;
		for (String candidate : dressed ? List.of(this.trim.finePart() + rest, this.trim.rough() + rest) : List.of(this.trim.rough() + rest)) {
			if (BuiltInRegistries.BLOCK.containsKey(ResourceLocation.withDefaultNamespace(candidate))) {
				return candidate;
			}
		}

		return path;
	}

	private boolean mixed(@Nullable net.minecraft.core.BlockPos at, int salt) {
		return at != null && this.mix != null && Math.floorMod(hash(at, this.mix * 7 + salt), MIX_ONE_IN) == 0;
	}

	private static int hash(@Nullable net.minecraft.core.BlockPos at, int seed) {
		long h = at == null ? seed : (at.asLong() * 0x9E3779B97F4A7C15L) ^ (seed * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		return (int)(h ^ (h >>> 29));
	}

	/**
	 * The same shape of building stone in two stones (granite stairs and cobblestone stairs, a cracked brick and a stone
	 * brick): what it built before it ran out of one stone still counts once the plan has another.
	 */
	public static boolean sameButStone(BlockState have, BlockState want) {
		String a = stoneShape(BuiltInRegistries.BLOCK.getKey(have.getBlock()).getPath());
		return a != null && a.equals(stoneShape(BuiltInRegistries.BLOCK.getKey(want.getBlock()).getPath()));
	}

	/** The stone a building stone's block or item is made of ("granite_stairs", "polished_granite": granite), or null. */
	@Nullable
	public static Stone stoneOf(String path) {
		for (Stone stone : STONES) {
			if (path.equals(stone.rough()) || path.equals(stone.fine()) || stone.fineMix().contains(path)) {
				return stone;
			}

			for (String prefix : List.of(stone.rough(), stone.finePart())) {
				if (path.startsWith(prefix + "_") && stoneShape(path) != null) {
					return stone;
				}
			}
		}

		return null;
	}

	/** "full", "_stairs", "_slab" or "_wall" for a building stone's block, else null. */
	@Nullable
	private static String stoneShape(String path) {
		for (Stone stone : STONES) {
			if (path.equals(stone.rough()) || path.equals(stone.fine()) || stone.fineMix().contains(path)) {
				return "full";
			}

			for (String prefix : List.of(stone.rough(), stone.finePart())) {
				if (path.startsWith(prefix + "_")) {
					String rest = path.substring(prefix.length());
					if (rest.equals("_stairs") || rest.equals("_slab") || rest.equals("_wall")) {
						return rest;
					}
				}
			}
		}

		return null;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
		return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
	}

	/** The plan's cells in this palette. */
	public List<HousePlan.Cell> apply(HousePlan plan) {
		List<HousePlan.Cell> out = new ArrayList<>(plan.cells.size());
		for (HousePlan.Cell cell : plan.cells) {
			out.add(new HousePlan.Cell(cell.pos(), this.apply(cell.state(), cell.pos())));
		}

		return out;
	}

	public String describe() {
		if (this.woods.isEmpty() && !this.deepslate) {
			return "as drawn";
		}

		List<String> parts = new ArrayList<>();
		this.woods.forEach((from, to) -> {
			if (from.equals(STONE)) {
				parts.add("cobblestone → " + to);
			} else if (!from.startsWith("#")) {
				parts.add(from.equals(to) ? from + " stays" : from + " → " + to);
			}
		});
		if (this.stone != null) {
			parts.add("stone: " + this.stone.name() + (this.trim != this.stone ? ", trim " + this.trim.name() : "") + (this.mix != null && !this.stone.fineMix().isEmpty() ? ", textured" : ""));
		} else if (this.deepslate) {
			parts.add("cobblestone → cobbled deepslate");
		}

		return String.join(", ", parts);
	}
}
