package com.dinglebob.drifter.nav;

import java.util.HashMap;
import java.util.Map;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.Tools;
import com.dinglebob.drifter.perception.TerrainKnowledge;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pathfinder's view of terrain (DESIGN.md section 12). Blocks the Drifter has seen ({@link TerrainKnowledge})
 * are read as they really are; everything else is UNKNOWN and assumed passable at a moderate cost.
 * Hard bans: never through lava or fire, never digging player-placed blocks or containers.
 */
public final class NavView {
	/** Blocks no taller than this (carpet, snow layers) are walked on, not climbed or dug. */
	public static final double LOW_BLOCK_HEIGHT = 0.2;

	public enum Kind {
		/** Air or something you walk through (grass, flowers, torches). */
		OPEN,
		/** A thin block you walk on top of: carpet, a thin snow layer. Passable, but not something to stand above. */
		LOW,
		/** Solid: has to be dug ({@link Cell#digSeconds}, infinite if it can't be). */
		SOLID,
		WATER,
		/** Ladders, vines, scaffolding. */
		CLIMB,
		/** A wooden door or fence gate: passable after opening it. */
		DOOR,
		/** Passable but unpleasant: cobwebs, powder snow, sweet berry bushes. */
		AVOID,
		/** Never: lava, fire, cactus, unloaded chunks. */
		BANNED,
		/** Never seen: assumed passable. */
		UNKNOWN
	}

	/**
	 * @param built it plainly belongs to somebody's build (glass, planks, wool, doors, stairs...): players go around
	 *              those rather than through them
	 */
	public record Cell(Kind kind, float digSeconds, boolean fallingAbove, boolean liquidBeside, boolean built) {
		public Cell(Kind kind, float digSeconds, boolean fallingAbove, boolean liquidBeside) {
			this(kind, digSeconds, fallingAbove, liquidBeside, false);
		}

		public boolean passable() {
			return this.kind == Kind.OPEN || this.kind == Kind.LOW || this.kind == Kind.WATER || this.kind == Kind.CLIMB || this.kind == Kind.DOOR
				|| this.kind == Kind.AVOID || this.kind == Kind.UNKNOWN;
		}

		public boolean diggable() {
			return this.kind == Kind.SOLID && Float.isFinite(this.digSeconds);
		}
	}

	private final NavWorld world;
	private final com.dinglebob.drifter.perception.KnownTerrain knowledge;
	private final LocalGrid<Cell> cache;
	private final com.dinglebob.drifter.brain.TreeLogs trees;
	/**
	 * What it reads of the Drifter itself, taken when the view is made (on the server thread), so a search on another
	 * thread never touches the Drifter (milestone 16): where it stands, where it got burned lately, its scaffolding, its
	 * boots, its tools for dig times and whether it's on the ground.
	 */
	private final BlockPos feet;
	private final java.util.List<BlockPos> lavaBurns;
	/** Leaks it plugged lately: never dug out again by a route (water's behind them). */
	private final java.util.Set<BlockPos> leakPlugs;
	private final java.util.Set<BlockPos> scaffold;
	private final boolean leatherBoots;
	private final java.util.List<net.minecraft.world.item.ItemStack> tools;
	private final boolean onGround;

	/** On the live world and what it knows now (server thread only). */
	public NavView(DrifterEntity drifter) {
		this(drifter, NavWorld.live(drifter.level()), drifter.getSenses().terrain());
	}

	/** On {@code world} (the live world, or {@link WorldCopy}) and {@code knowledge} (live, or a copy). Made on the server thread. */
	public NavView(DrifterEntity drifter, NavWorld world, com.dinglebob.drifter.perception.KnownTerrain knowledge) {
		this.cache = new LocalGrid<>(drifter.blockPosition(), com.dinglebob.drifter.DrifterConfig.pathRange() + 8);
		this.world = world;
		this.trees = new com.dinglebob.drifter.brain.TreeLogs(world);
		this.knowledge = knowledge;
		this.feet = drifter.blockPosition().immutable();
		this.lavaBurns = drifter.recentLavaBurns();
		this.leakPlugs = drifter.recentLeakPlugs();
		this.scaffold = new java.util.HashSet<>(drifter.scaffold());
		this.leatherBoots = drifter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(net.minecraft.world.item.Items.LEATHER_BOOTS);
		this.tools = Tools.toolsOf(drifter);
		this.onGround = drifter.onGround();
		// (Its own position tells it it's underground: rock overhead and no sky.)
		// (Not in the Nether: rock overhead everywhere, but what it hasn't seen there is mostly open caverns and lava seas.)
		this.underground = com.dinglebob.drifter.brain.mining.Caver.underground(drifter, drifter.blockPosition())
			&& !drifter.getSenses().time().canSeeSky() && !drifter.level().dimensionType().hasCeiling();
		this.lavaWorld = drifter.level().dimensionType().ultraWarm();
		this.rockSeconds = Tools.digSeconds(this.tools, this.onGround, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), world, this.feet);
	}

	/** The world it reads. */
	public NavWorld world() {
		return this.world;
	}

	private BlockState state(BlockPos pos) {
		return this.world.getBlockState(pos);
	}

	/** Near where it just got into lava (within a block or so). */
	private boolean nearRecentLava(BlockPos pos) {
		for (BlockPos burn : this.lavaBurns) {
			if (burn.distSqr(pos) <= 2) {
				return true;
			}
		}

		return false;
	}

	/** It's underground right now (unseen cells near its level count as rock). */
	private final boolean underground;
	/** In the Nether: what it hasn't seen there is caverns and lava seas, no ground to guess from. */
	private final boolean lavaWorld;

	/** The unseen-ground guess (milestone 20); off: unseen is both open and solid at any height, as before. */
	public static boolean GUESS_GROUND = true;
	/** How far round an unseen column it looks for ground it has seen. */
	private static final int GUESS_RADIUS = 3;
	/** Unsure within this many blocks of the guessed ground; past it, earth below and air above. */
	private static final int GUESS_SLACK = 2;
	/** How far up and down from its feet it looks for that ground. */
	private static final int GUESS_SPAN = 20;
	private static final int NO_GUESS = Integer.MIN_VALUE;
	/** Guessed ground per column (x, z), worked out once a view. */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap guesses = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

	/**
	 * The ground it guesses an unseen column has: the top of the nearest ground it has seen (a solid block, or water, with
	 * what's over it seen and open), from the nearest ring of columns round it that has any (the average of those);
	 * {@link #NO_GUESS} with nothing seen within {@link #GUESS_RADIUS}.
	 */
	private int guessedGround(int x, int z) {
		long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
		if (this.guesses.containsKey(key)) {
			return this.guesses.get(key);
		}

		int guess = NO_GUESS;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int ring = 0; ring <= GUESS_RADIUS && guess == NO_GUESS; ring++) {
			int total = 0;
			int found = 0;
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					int top = this.seenGround(at, x + dx, z + dz);
					if (top != NO_GUESS) {
						total += top;
						found++;
					}
				}
			}

			if (found > 0) {
				guess = Math.round(total / (float)found);
			}
		}

		this.guesses.put(key, guess);
		return guess;
	}

	/** Seen ground per column, worked out once a view (each column is asked by up to two dozen neighbours). */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap seenTops = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

	/** The top of the ground it has seen in this column, near its own height, or {@link #NO_GUESS}. */
	private int seenGround(BlockPos.MutableBlockPos at, int x, int z) {
		long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
		if (this.seenTops.containsKey(key)) {
			return this.seenTops.get(key);
		}

		int seen = this.scanSeenGround(at, x, z);
		this.seenTops.put(key, seen);
		return seen;
	}

	private int scanSeenGround(BlockPos.MutableBlockPos at, int x, int z) {
		int top = this.feet.getY() + GUESS_SPAN;
		int bottom = this.feet.getY() - GUESS_SPAN;
		boolean openAbove = false;
		for (int y = top; y >= bottom; y--) {
			at.set(x, y, z);
			if (!this.knowledge.isKnown(at)) {
				openAbove = false;
				continue;
			}

			BlockState state = this.state(at);
			boolean ground = !state.getCollisionShape(this.world, at).isEmpty() || !state.getFluidState().isEmpty();
			if (ground && openAbove && !state.is(net.minecraft.tags.BlockTags.LEAVES)) {
				return y;
			}

			openAbove = !ground;
		}

		return NO_GUESS;
	}
	/** How long stone takes it to dig with what it carries. */
	private final float rockSeconds;

	public Cell cell(BlockPos pos) {
		Cell cell = this.cache.get(pos);
		if (cell == null) {
			BlockPos at = pos.immutable();
			cell = this.compute(at);
			this.cache.put(at, cell);
		}

		return cell;
	}

	public boolean isKnown(BlockPos pos) {
		return this.knowledge.isKnown(pos);
	}

	private Cell compute(BlockPos pos) {
		if (!this.world.loaded(pos) || this.world.isOutsideBuildHeight(pos)) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		// Near where it just got into lava: kept clear for a while (it walked straight back in and burned to death).
		// (Never the blocks right round where it stands, so it can always walk out.)
		if (this.nearRecentLava(pos) && pos.distSqr(this.feet) > 2) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		// A leak it plugged: water behind it.
		if (!this.leakPlugs.isEmpty() && this.leakPlugs.contains(pos)) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		if (!this.knowledge.isKnown(pos)) {
			// Under water it has seen, it expects more water, not ground (its sight stops at the surface; found in
			// testing: it planned to walk across a pond on "unseen ground" just under the surface, and couldn't get out).
			BlockPos above = pos.above();
			if (this.knowledge.isKnown(above) && this.state(above).getFluidState().is(FluidTags.WATER)) {
				return new Cell(Kind.WATER, 0.0F, false, false);
			}

			// Beside water it has seen, at the same height: water is level, so it expects more of it (the banks it can see).
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = pos.relative(side);
				if (this.knowledge.isKnown(next) && this.state(next).getFluidState().is(FluidTags.WATER) && this.state(next).getFluidState().isSource()) {
					return new Cell(Kind.WATER, 0.0F, false, false);
				}
			}

			// Beside or under lava it has seen: lava spreads sideways and down, so it expects more of it (found in testing: a
			// step down into an unseen cell by a lava flow, and it burned to death).
			if (this.knownLavaAt(above)) {
				return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
			}

			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (this.knownLavaAt(pos.relative(side))) {
					return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
				}
			}

			// Underground, unseen means rock as far as it can tell (a player down a mine expects stone, not open air): planned
			// as stone to dig, so a route follows its known tunnels or digs honestly, instead of hoping for air and replanning
			// at every wall (found in testing: "found stone where it expected open space" fifteen times, then giving up).
			if (this.underground && pos.getY() <= this.feet.getY() + 3) {
				return new Cell(Kind.SOLID, this.rockSeconds, false, false);
			}

			// Above ground: a guess at where the ground is (milestone 20, "wrong guesses"), from the nearest ground it has seen
			// round about, as a player reads a hillside it can't see all of: well under that, earth; well over it, air.
			// (Before, unseen was both at any height: routes floated straight over valleys and through hills, and every one
			// came apart when it saw the real ground.) Near the guess it stays unsure; with nothing seen near, as before.
			if (GUESS_GROUND && !this.lavaWorld) {
				int ground = this.guessedGround(pos.getX(), pos.getZ());
				if (ground != NO_GUESS) {
					if (pos.getY() <= ground - GUESS_SLACK) {
						return new Cell(Kind.SOLID, this.rockSeconds, false, false);
					}

					if (pos.getY() > ground + GUESS_SLACK) {
						return new Cell(Kind.OPEN, 0.0F, false, false);
					}
				}
			}

			return new Cell(Kind.UNKNOWN, 0.0F, false, false);
		}

		BlockState state = this.state(pos);
		FluidState fluid = state.getFluidState();
		// Pointed dripstone too: landing on a stalagmite hurts a lot (found in testing: it died stepping onto them in a cave).
		if (fluid.is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.POINTED_DRIPSTONE)
			// A lit campfire burns whoever steps on it (found in a whim test: walked over one while exploring).
			|| state.is(BlockTags.CAMPFIRES) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
			// Never through a portal by accident: going through one is a decision (EnterPortalActivity steps in by hand).
			|| state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		if (state.is(BlockTags.WOODEN_DOORS) || state.getBlock() instanceof FenceGateBlock) {
			return new Cell(Kind.DOOR, 0.0F, false, false);
		}

		if (state.is(BlockTags.CLIMBABLE)) {
			return new Cell(Kind.CLIMB, 0.0F, false, false);
		}

		// Powder snow: never into it without leather boots (found in testing: it sank in and froze to death).
		if (state.is(Blocks.POWDER_SNOW) && !this.leatherBoots) {
			return new Cell(Kind.BANNED, Float.POSITIVE_INFINITY, false, false);
		}

		// A grown berry bush (the ones that prick) in the way: broken as it goes, instantly, like a player clearing a path,
		// rather than a slow, painful walk through (found by the user: Drifters kept dying in berry bushes).
		if (state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) > 0
			&& !this.world.playerPlaced(pos)) {
			return new Cell(Kind.SOLID, BERRY_BUSH_SECONDS, false, false);
		}

		if (state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)) {
			return new Cell(Kind.AVOID, 0.0F, false, false);
		}

		VoxelShape collision = state.getCollisionShape(this.world, pos);
		if (collision.isEmpty()) {
			// Open, but lava right beside it at the same height (or just above): a flow can run into it, and one slip is
			// fatal. Allowed, at a price, so routes keep a block away where they can.
			if (!fluid.is(FluidTags.WATER)) {
				boolean lavaBeside = this.knownLavaAt(pos.above());
				for (Direction side : Direction.Plane.HORIZONTAL) {
					lavaBeside |= this.knownLavaAt(pos.relative(side)) || this.dropIntoLava(pos.relative(side));
				}

				if (lavaBeside) {
					return new Cell(Kind.AVOID, 0.0F, false, false);
				}
			}

			return new Cell(fluid.is(FluidTags.WATER) ? Kind.WATER : Kind.OPEN, 0.0F, false, false);
		}

		if (collision.max(Direction.Axis.Y) <= LOW_BLOCK_HEIGHT) {
			return new Cell(Kind.LOW, 0.0F, false, false);
		}

		// Never through a portal frame (found in testing: a route out of its walled-in portal room went through the obsidian,
		// and the portal home collapsed), nor obsidian at all (nine seconds a block with a diamond pickaxe; no player tunnels
		// through it).
		// Nor through what Drifters built (its own walls: it uses the door; user, 2026-10-17), its own scaffolding aside.
		boolean protectedBlock = this.world.hasBlockEntityAt(pos) || this.world.playerPlaced(pos)
			|| this.world.drifterBuilt(pos) && !this.scaffold.contains(pos)
			|| state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || this.besidePortal(pos);
		float dig = protectedBlock ? Float.POSITIVE_INFINITY : this.digSeconds(state, pos);
		BlockState above = this.state(pos.above());
		boolean falling = above.getBlock() instanceof FallingBlock;
		boolean liquidBeside = false;
		for (Direction direction : Direction.values()) {
			if (direction != Direction.DOWN) {
				FluidState neighbor = this.state(pos.relative(direction)).getFluidState();
				if (neighbor.is(FluidTags.LAVA) && this.knowledge.isKnown(pos.relative(direction))) {
					// Digging this would open into known lava: never.
					dig = Float.POSITIVE_INFINITY;
				} else if (!neighbor.isEmpty()) {
					liquidBeside = true;
				}
			}
		}

		// A log that isn't part of a tree is somebody's build (a village house post, a cabin wall).
		boolean built = looksBuilt(state) || state.is(BlockTags.LOGS) && !this.trees.check(pos);
		return new Cell(Kind.SOLID, dig, falling, liquidBeside, built);
	}

	/** Dig time by block kind, worked out once a search (it goes through its tools each time; performance 2026-10-17). */
	private final it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap<BlockState> digTimes = new it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap<>();

	private float digSeconds(BlockState state, BlockPos pos) {
		if (this.digTimes.containsKey(state)) {
			return this.digTimes.getFloat(state);
		}

		float seconds = Tools.digSeconds(this.tools, this.onGround, state, this.world, pos);
		this.digTimes.put(state, seconds);
		return seconds;
	}

	/** Breaking a berry bush in the way: instant (hardness 0), plus a moment to aim. */
	private static final float BERRY_BUSH_SECONDS = 0.2F;

	/**
	 * Open air beside it with lava a short drop below (a lava lake's shore, a ledge over a lava fall): one slip and it's in
	 * (found in testing: walking a Nether lava lake's shore, a jump knocked it off into the lava).
	 */
	private boolean dropIntoLava(BlockPos side) {
		for (int down = 0; down <= 3; down++) {
			BlockPos cell = side.below(down);
			if (this.knownLavaAt(cell)) {
				return down > 0;
			}

			if (!this.state(cell).getCollisionShape(this.world, cell).isEmpty()) {
				return false;
			}
		}

		return false;
	}

	/** How far up a roof or upper floor is looked for over a spot inside a building. */
	private static final int INDOOR_ROOF = 20;

	/**
	 * Inside a finished building: standing on a floor someone built, under something built (a ceiling, an upper floor, a
	 * roof). A player takes the stairs or the door there; it never pillars or bridges indoors (the user's screenshots,
	 * 2026-10-29: cobblestone pillars and floors left inside town houses; "it keeps coming back").
	 */
	public boolean indoors(BlockPos feet) {
		if (!this.builtOrPlaced(feet.below())) {
			return false;
		}

		for (int up = 2; up <= INDOOR_ROOF; up++) {
			if (this.builtOrPlaced(feet.above(up))) {
				return true;
			}
		}

		return false;
	}

	private boolean builtOrPlaced(BlockPos pos) {
		return this.world.drifterBuilt(pos) && !this.scaffold.contains(pos) || this.world.playerPlaced(pos);
	}

	/** Lava it has seen within two blocks across and one below or above: no pillaring there (a jump can tip it in). */
	public boolean lavaWithinTwo(BlockPos feet) {
		long key = feet.asLong();
		byte known = this.lavaNear.get(key);
		if (known != 0) {
			return known > 0;
		}

		boolean lava = false;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -2, -2), feet.offset(2, 1, 2))) {
			if (this.knownLavaAt(pos)) {
				lava = true;
				break;
			}
		}

		this.lavaNear.put(key, (byte)(lava ? 1 : -1));
		return lava;
	}

	private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap lavaNear = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();

	private boolean knownLavaAt(BlockPos pos) {
		return this.lavaSection(pos) && this.knowledge.isKnown(pos) && this.state(pos).getFluidState().is(FluidTags.LAVA);
	}

	/** Each section's "can it have lava at all?" from its palette (most can't: the check is then free). */
	private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap lavaSections = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
	private long lastLavaKey = Long.MIN_VALUE;
	private boolean lastLava;
	private static final java.util.function.Predicate<BlockState> LAVA = state -> state.getFluidState().is(FluidTags.LAVA);

	private boolean lavaSection(BlockPos pos) {
		int sx = pos.getX() >> 4;
		int sy = pos.getY() >> 4;
		int sz = pos.getZ() >> 4;
		long key = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
		if (key == this.lastLavaKey) {
			return this.lastLava;
		}

		byte known = this.lavaSections.get(key);
		if (known == 0) {
			known = (byte)(this.world.sectionMayHave(sx, sy, sz, LAVA) ? 1 : -1);
			this.lavaSections.put(key, known);
		}

		this.lastLavaKey = key;
		this.lastLava = known > 0;
		return this.lastLava;
	}

	/**
	 * Blocks a player can tell are part of a build just by looking at them. Cobblestone too: in a natural world it only
	 * turns up in villages, dungeons and temples (stone drops it, but stone itself is natural).
	 */
	public static boolean looksBuilt(BlockState state) {
		return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.SMOOTH_STONE)
			|| state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.STAIRS)
			|| state.is(BlockTags.SLABS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS)
			|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BEDS) || state.is(BlockTags.STONE_BRICKS)
			|| state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES) || state.is(Blocks.BRICKS)
			|| state.is(Blocks.BOOKSHELF) || state.is(Blocks.TORCH) || state.is(Blocks.LANTERN);
	}

	/** Something it can stand on top of: a known solid block no taller than a full block, or unknown (assumed). */
	public boolean standable(BlockPos floor) {
		Cell cell = this.cell(floor);
		// Unseen (or assumed rock underground): assumed fine to stand on, without peeking at what's really there. Unless it
		// guesses that's air (well above the ground it has seen round about).
		if (cell.kind() == Kind.UNKNOWN || !this.knowledge.isKnown(floor)) {
			return cell.kind() != Kind.OPEN;
		}

		if (cell.kind() == Kind.LOW) {
			// Standing on carpet puts the feet in the carpet's own block, not above it.
			return false;
		}

		if (cell.kind() != Kind.SOLID) {
			return cell.kind() == Kind.CLIMB;
		}

		VoxelShape shape = this.state(floor).getCollisionShape(this.world, floor);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0;
	}

	/**
	 * Good footing to jump from or land on: a full-height block it has seen with ordinary grip (not ice, slime, honey or
	 * soul sand, which change how a jump goes), so the jump goes as the physics says.
	 */
	public boolean jumpFooting(BlockPos floor) {
		if (!this.knowledge.isKnown(floor) || this.cell(floor).kind() != Kind.SOLID) {
			return false;
		}

		BlockState state = this.state(floor);
		VoxelShape shape = state.getCollisionShape(this.world, floor);
		var block = state.getBlock();
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.9 && shape.max(Direction.Axis.Y) <= 1.0 && block.getFriction() == 0.6F
			&& block.getSpeedFactor() == 1.0F && block.getJumpFactor() == 1.0F;
	}

	/**
	 * Something to stop against behind this climbable block, going this way: a ladder attached on that side (a ladder always
	 * hangs on a solid block, seen or not), vines hanging on that face, or a solid block it has seen there.
	 */
	public boolean wallBehindClimb(BlockPos climb, Direction going) {
		BlockState state = this.state(climb);
		if (state.getBlock() instanceof net.minecraft.world.level.block.LadderBlock) {
			return state.getValue(net.minecraft.world.level.block.LadderBlock.FACING).getOpposite() == going;
		}

		if (state.getBlock() instanceof net.minecraft.world.level.block.VineBlock) {
			var face = net.minecraft.world.level.block.VineBlock.PROPERTY_BY_DIRECTION.get(going);
			return face != null && state.getValue(face);
		}

		return this.knowledge.isKnown(climb.relative(going)) && this.cell(climb.relative(going)).kind() == Kind.SOLID;
	}

	private boolean besidePortal(BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (this.state(pos.relative(direction)).is(Blocks.NETHER_PORTAL)) {
				return true;
			}
		}

		return false;
	}

	/** How the pathfinder sees the cells round {@code feet}, layer by layer (debug: `/drifter cells`, stuck routes). */
	public static String describeAround(DrifterEntity drifter, BlockPos feet) {
		NavView view = new NavView(drifter);
		StringBuilder out = new StringBuilder(String.format(java.util.Locale.ROOT, "at %.2f %.2f %.2f, from %s", drifter.getX(), drifter.getY(),
			drifter.getZ(), feet.toShortString()));
		for (int dy = 2; dy >= -1; dy--) {
			out.append('\n').append(" y").append(feet.getY() + dy).append(':');
			for (int dz = -1; dz <= 1; dz++) {
				out.append('\n').append("  ");
				for (int dx = -1; dx <= 1; dx++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					String kind = view.cell(pos).kind().name();
					out.append(String.format(java.util.Locale.ROOT, "%-7s", kind.substring(0, Math.min(6, kind.length())) + (view.standable(pos) ? "+" : "")));
				}
			}
		}

		return out.toString();
	}

	public boolean isWater(BlockPos pos) {
		return this.cell(pos).kind() == Kind.WATER;
	}

	/** Water you can float in (a source block), not water running past. */
	public boolean isStillWater(BlockPos pos) {
		return this.isWater(pos) && this.state(pos).getFluidState().isSource();
	}
}
