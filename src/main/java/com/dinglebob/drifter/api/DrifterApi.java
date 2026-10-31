package com.dinglebob.drifter.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.social.News;
import com.dinglebob.drifter.social.NewsBook;
import com.dinglebob.drifter.world.DrifterGroups;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * The door other mods use into Drifter (docs/API.md; milestone 22, Drifter: Civilizations). Everything here stays put
 * while Drifter's insides change; what it hands out (a Drifter, its home, its goals) is the real thing. Registering is
 * done once, at mod start. All of it on the server thread.
 */
public final class DrifterApi {
	/** Bumped when something here changes in a way a mod using it would notice. */
	public static final int API_VERSION = 3;

	private static final List<Supplier<Goals.Goal>> GOALS = new ArrayList<>();
	private static final List<CatchUpStep> CATCH_UP = new ArrayList<>();
	private static final List<java.util.function.BiPredicate<DrifterEntity, DrifterGroups.Group>> JOIN_RULES = new ArrayList<>();

	private DrifterApi() {
	}

	/** Drifter's version (as in its jar's name). */
	public static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.dinglebob.drifter.Drifter.MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	// ----- Adding to Drifters -----

	/**
	 * A goal every Drifter has from now on (one made for each Drifter): scored and picked like Drifter's own, so staying
	 * alive still comes first. Call at mod start, before any world loads.
	 */
	public static void registerGoal(Supplier<Goals.Goal> goal) {
		GOALS.add(goal);
	}

	/** A step of catch-up (Drifters away from players), run for each slice of time after Drifter's own. */
	public static void registerCatchUpStep(CatchUpStep step) {
		CATCH_UP.add(step);
	}

	/**
	 * A say in who joins a group of friends: false and this Drifter doesn't join that group (it stays friends). Call at mod
	 * start.
	 */
	public static void registerJoinRule(java.util.function.BiPredicate<DrifterEntity, DrifterGroups.Group> mayJoin) {
		JOIN_RULES.add(mayJoin);
	}

	/** (Drifter's own use.) Every join rule lets it join this group. */
	public static boolean mayJoin(DrifterEntity drifter, DrifterGroups.Group group) {
		return JOIN_RULES.stream().allMatch(rule -> rule.test(drifter, group));
	}

	/** (Drifter's own use: the registered goals, made for one Drifter.) */
	public static List<Goals.Goal> goals() {
		List<Goals.Goal> made = new ArrayList<>(GOALS.size());
		GOALS.forEach(goal -> made.add(goal.get()));
		return made;
	}

	/** (Drifter's own use.) */
	public static List<CatchUpStep> catchUpSteps() {
		return CATCH_UP;
	}

	// ----- The world's directory of Drifters -----

	/** Every Drifter that has lived in this world (gone ones too), as the world keeps it: readable while they're unloaded. */
	public static java.util.Collection<DrifterRecord> directory() {
		return com.dinglebob.drifter.world.DrifterRegistry.records();
	}

	/** One Drifter's record, or null if the world never knew it. */
	@Nullable
	public static DrifterRecord record(UUID drifter) {
		return com.dinglebob.drifter.world.DrifterRegistry.record(drifter);
	}

	// ----- Reading and changing a Drifter -----

	/** Every Drifter loaded now, in every world. */
	public static List<DrifterEntity> loaded(MinecraftServer server) {
		List<DrifterEntity> all = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			all.addAll(level.getEntities(EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive));
		}

		return all;
	}

	/** A Bandit at heart: born one (its archetype), or one now (how it meets people: an outpost's, an encounter's). */
	public static boolean bandit(DrifterEntity drifter) {
		return drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.BANDIT
			|| drifter.getSocial().disposition() == com.dinglebob.drifter.social.Disposition.BANDIT;
	}

	/**
	 * Not a Bandit (a ready-made town's people; the user, 2026-10-28: "bandits shouldnt be in towns"): one born a Bandit
	 * gets a personality of another kind, and its disposition is rolled again from it.
	 */
	public static void notABandit(DrifterEntity drifter) {
		if (!bandit(drifter)) {
			return;
		}

		com.dinglebob.drifter.personality.Archetype other;
		do {
			other = com.dinglebob.drifter.personality.Archetype.pick(drifter.getRandom());
		} while (other == com.dinglebob.drifter.personality.Archetype.BANDIT);

		if (drifter.getPersonality().archetype() == com.dinglebob.drifter.personality.Archetype.BANDIT) {
			drifter.getPersonality().reroll(drifter.getRandom(), other);
		}

		drifter.getSocial().setDisposition(null);
	}

	/** A Bandit through and through (one a town threw out): it kills on sight again, by the setting. */
	public static void turnBandit(DrifterEntity drifter) {
		drifter.getSocial().setDisposition(com.dinglebob.drifter.social.Disposition.BANDIT);
	}

	/** (Debug.) A town's crook goes at it in a few seconds. */
	public static void crookSoon(DrifterEntity drifter) {
		com.dinglebob.drifter.social.CrookGoal.soon(drifter);
	}

	/**
	 * Settled in a town: a Bandit there gives up killing on sight and sizes strangers up as an Opportunist instead (found:
	 * a ready-made town's Bandit, elected its leader, killed the player walking about the town). Its personality stays.
	 */
	public static void settleDown(DrifterEntity drifter) {
		if (drifter.getSocial().disposition() == com.dinglebob.drifter.social.Disposition.BANDIT) {
			drifter.getSocial().setDisposition(com.dinglebob.drifter.social.Disposition.OPPORTUNIST);
		}
	}

	/** Its home (base, bed, chests), or null if it has none yet. */
	@Nullable
	public static Home home(DrifterEntity drifter) {
		return drifter.getDrifterBrain().home();
	}

	/** Gives it a home (or takes it away, null). */
	public static void setHome(DrifterEntity drifter, @Nullable Home home) {
		drifter.getDrifterBrain().setHome(home);
	}

	/** The group it's in, or null. */
	@Nullable
	public static DrifterGroups.Group group(UUID drifter) {
		return DrifterGroups.of(drifter);
	}

	/** How much it trusts a player (-100 to 100; 0 for a stranger). */
	public static double trustInPlayer(DrifterEntity drifter, UUID player) {
		var r = drifter.getSocial().get(player);
		return r == null ? 0.0 : r.trust;
	}

	/** It thinks this much better (or worse, under 0) of a player (met now if it hadn't: by name). */
	public static void addTrustInPlayer(DrifterEntity drifter, UUID player, String name, double change) {
		drifter.getSocial().getOrMeet(player, name).addTrust(change);
	}

	/** How much it trusts another Drifter (-100 to 100; 0 for a stranger). */
	public static double trustInDrifter(DrifterEntity drifter, UUID other) {
		var r = drifter.getBonds().get(other);
		return r == null ? 0.0 : r.trust;
	}

	/** A thought in its log (what `/drifter thoughts` shows), as if it thought it now. */
	public static void think(DrifterEntity drifter, String thought) {
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), thought);
	}

	/**
	 * News for {@code drifter} to carry and pass on to the Drifters it meets (it saw it, or it was its own doing: tell it
	 * only what it could know). {@code key} is the same for the same happening, so it's heard once however many tell it;
	 * {@code about} is who or what it's about (or null), {@code what} the rest in words ("about what": "Ann was chosen
	 * mayor of Oakvale"); it's worth telling for {@code lasts} game ticks, five tellings on at most. Whoever hears it fires
	 * {@link DrifterEvents#HEARD_NEWS}; {@code data} rides along for the listener.
	 */
	public static void tell(DrifterEntity drifter, String key, @Nullable UUID about, String aboutName, String what, long lasts, CompoundTag data) {
		drifter.getNews().saw(NewsBook.custom(key, about, aboutName, what, lasts, data, drifter.level().getGameTime()));
	}

	/** The news it carries (what it saw, and what it was told, with how many tellings on). */
	public static List<News> news(DrifterEntity drifter) {
		return drifter.getNews().all();
	}

	/**
	 * Most members this group takes (a town is a big group); 0 goes back to the setting ("Group size", four by default).
	 * Its chests (the base's stockpile) are {@code group.chests}, its base {@code group.base}.
	 */
	public static void setGroupLimit(DrifterGroups.Group group, int limit) {
		group.limit = Math.max(0, limit);
		DrifterGroups.changed();
	}

	/**
	 * A new Drifter standing at {@code feet}, carrying nothing (a fresh start, as a new player), with a personality and a
	 * skin of its own; named {@code name}, or a username of its own. Null if the world wouldn't take it.
	 */
	@Nullable
	public static DrifterEntity spawn(ServerLevel level, net.minecraft.core.BlockPos feet, @Nullable String name) {
		DrifterEntity drifter = com.dinglebob.drifter.registry.ModEntities.DRIFTER.create(level);
		if (drifter == null) {
			return null;
		}

		float yaw = level.getRandom().nextFloat() * 360.0F;
		drifter.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0.0F);
		drifter.setYHeadRot(yaw);
		drifter.setYBodyRot(yaw);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), net.minecraft.world.entity.MobSpawnType.COMMAND, null);
		if (name != null) {
			drifter.setCustomName(net.minecraft.network.chat.Component.literal(name));
			drifter.setCustomNameVisible(true);
		}

		return level.addFreshEntity(drifter) ? drifter : null;
	}

	/**
	 * Keeps it within {@code radius} blocks of {@code center} (in the dimension it's in now) when it explores: "one island".
	 * Null lets it go anywhere again. Saved with it.
	 */
	public static void setRoam(DrifterEntity drifter, @Nullable net.minecraft.core.BlockPos center, int radius) {
		drifter.getDrifterBrain().setRoam(center, radius);
	}

	/** A soldier in a battle going on now (`/drifter battle`, warbands): they come and go by the dozen. */
	public static boolean inBattle(DrifterEntity drifter) {
		return com.dinglebob.drifter.world.Battle.inBattle(drifter);
	}

	/** A group by its id (as events give it), or null if it's gone. */
	@Nullable
	public static DrifterGroups.Group groupById(UUID id) {
		return DrifterGroups.byId(id);
	}

	// ---------------------------------------------------------------------------------------------
	// Houses and towns (milestone 22)
	// ---------------------------------------------------------------------------------------------

	/**
	 * A house plan: its id and size before it's turned (x wide, y high, z deep); {@code groundLayer}: its lowest layer is the
	 * ground (grass, a path), so its corner goes at the ground's own level, not on top of it.
	 */
	public record HousePlanInfo(String id, int sizeX, int sizeY, int sizeZ, boolean groundLayer) {
	}

	/** Every house plan there is (the mod's, generated ones, the player's saved ones). */
	public static List<HousePlanInfo> housePlans(MinecraftServer server) {
		List<HousePlanInfo> plans = new ArrayList<>();
		com.dinglebob.drifter.building.plan.HousePlans.cached(server).values()
			.forEach(plan -> plans.add(new HousePlanInfo(plan.id, plan.size.getX(), plan.size.getY(), plan.size.getZ(), plan.cells.stream()
				.anyMatch(cell -> cell.pos().getY() == 0 && com.dinglebob.drifter.building.plan.PlacedPlan.isGround(cell.state())))));
		return plans;
	}

	/**
	 * The lower half of a house's front door (the one on {@code front}, that one walks straight out of), as put up with this
	 * plan, corner and turn; null if it has none (milestone 23 B3: a seller's shop at its door).
	 */
	@Nullable
	public static net.minecraft.core.BlockPos frontDoor(MinecraftServer server, String planId, net.minecraft.core.BlockPos origin,
		net.minecraft.world.level.block.Rotation rotation, net.minecraft.core.Direction front) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(server, planId);
		return plan == null ? null : com.dinglebob.drifter.building.plan.HousePlacer.door(new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin,
			rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn()), front);
	}

	/** The sides of its box a plan's doors are on once turned this way (to put its front to a road). */
	public static java.util.Set<net.minecraft.core.Direction> doorSides(MinecraftServer server, String planId, net.minecraft.world.level.block.Rotation rotation) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(server, planId);
		if (plan == null) {
			return java.util.Set.of();
		}

		// (Kept per plan and turn: a town's layout asks it of every plan at every spot; found measuring a town: most of its
		// 17 seconds.)
		DoorSides known = DOOR_SIDES.get(planId + "|" + rotation);
		if (known != null && known.plan() == plan) {
			return known.sides();
		}

		var sides = java.util.Set.copyOf(com.dinglebob.drifter.building.plan.HousePlacer.doorSides(new com.dinglebob.drifter.building.plan.PlacedPlan(plan,
			net.minecraft.core.BlockPos.ZERO, rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn())));
		DOOR_SIDES.put(planId + "|" + rotation, new DoorSides(plan, sides));
		return sides;
	}

	private record DoorSides(Object plan, java.util.Set<net.minecraft.core.Direction> sides) {
	}

	/** Another mod makes its own towns where villages would be (Drifter: Civilizations, milestone 23). */
	private static volatile boolean townsForVillages;

	/**
	 * Its house isn't finished: for the next {@code days} it builds the rest of its plan (what's missing of it) with what it
	 * gathers (a town's newcomer still putting its house up: milestone 23 A5; a house a raid wrecked mends the same way).
	 */
	public static void stillBuilding(DrifterEntity drifter, int days) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			home.mendUntil = drifter.level().getGameTime() + 24000L * days;
		}
	}

	/** A name as a Drifter would have one (for a grave, a sign: the same lists Drifters' names come from). */
	/** A Drifter's kind of name, not checked against those taken (safe off the server thread; for the past, not the living). */
	public static String looseName(net.minecraft.util.RandomSource random) {
		return com.dinglebob.drifter.identity.Usernames.loose(random);
	}

	public static String randomName(MinecraftServer server, net.minecraft.util.RandomSource random) {
		return com.dinglebob.drifter.identity.Usernames.generate(random, server);
	}

	/** An addon that makes towns where villages would be says so; villages (and the wandering trader) then don't come. */
	public static void setTownsForVillages(boolean on) {
		townsForVillages = on;
	}

	/** Villages are replaced: an addon makes towns instead and the setting's on (new chunks only). */
	public static boolean villagesReplaced() {
		return townsForVillages && com.dinglebob.drifter.DrifterConfig.replaceVillages();
	}

	/** A new world is a civilized world (the addon's world plan of nations), and how many nations and how spread out. */
	public static boolean civilizedWorld() {
		return townsForVillages && com.dinglebob.drifter.DrifterConfig.replaceVillages() && com.dinglebob.drifter.DrifterConfig.civilizedWorld();
	}

	/** 1 few, 2 normal, 3 many. */
	public static int nationsSetting() {
		return com.dinglebob.drifter.DrifterConfig.nations();
	}

	/** 1 round spawn to 5 over the world. */
	public static int spreadSetting() {
		return com.dinglebob.drifter.DrifterConfig.civSpread();
	}

	/**
	 * Structures an addon places itself (a civilized world's towns, put where its world plan says): no placements, so /locate
	 * and generation don't look for them the usual way (as villages, replaced).
	 */
	public static void setNotPlaced(java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> which) {
		notPlaced = which;
	}

	public static boolean notPlaced(net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> structure) {
		return notPlaced.test(structure);
	}

	private static volatile java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure>> notPlaced = s -> false;

	/**
	 * A slab the land itself has (Terrain Slabs: grass, dirt, sand and stone slabs smoothing hillsides, marked {@code
	 * generated}), not one somebody put down: half a block of ground, cleared like a plant where something's built.
	 */
	public static boolean naturalSlab(net.minecraft.world.level.block.state.BlockState state) {
		return com.dinglebob.drifter.nav.NavView.naturalSlab(state);
	}

	/** Villagers stay out of it too (none spawning as zombie villagers to cure). */
	public static boolean noVillagers() {
		return villagesReplaced() && !com.dinglebob.drifter.DrifterConfig.villagers();
	}

	private static final java.util.Map<String, DoorSides> DOOR_SIDES = new java.util.concurrent.ConcurrentHashMap<>();

	/** A house as put up: its plan, corner, turn and wood, the box it fills, and its front door's lower half. */
	public record PlacedHouse(String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation, String wood,
		net.minecraft.world.level.levelgen.structure.BoundingBox box, @Nullable net.minecraft.core.BlockPos door, @Nullable String stone) {
	}

	/**
	 * Loose bits of trees in this area after something was put up at once (ground evened, roads, fields): trunks left in
	 * the air and leaves no tree's log holds, gone. ({@link #placeHouse} does this round each house itself.)
	 */
	public static void tidyTrees(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox area) {
		com.dinglebob.drifter.world.gen.Felling.tidy(level, area, null);
	}

	/**
	 * (A town made at once, for testing; not how Drifters build: they go block by block.) The house from this plan, its
	 * corner at {@code origin}, turned and in {@code wood} ("oak", "spruce"...), put up all at once; null if there's no such
	 * plan. {@code front}: the door wanted on that side (for the door's place; the turn is the caller's).
	 */
	@Nullable
	public static PlacedHouse placeHouse(ServerLevel level, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation,
		String wood, @Nullable net.minecraft.core.Direction front) {
		return placeHouse(level, planId, origin, rotation, wood, null, front);
	}

	private static volatile java.util.function.BiPredicate<net.minecraft.world.level.Level, net.minecraft.core.BlockPos> settled = (level, pos) -> false;

	/**
	 * Settled land (the addon's towns): trees, leaves and plants there aren't gathered, Drifters fetch wood and seeds from the
	 * wild ({@link com.dinglebob.drifter.brain.KnownBlocks#mayGather}).
	 */
	public static void setSettled(java.util.function.BiPredicate<net.minecraft.world.level.Level, net.minecraft.core.BlockPos> which) {
		settled = which;
	}

	public static boolean settled(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
		return settled.test(level, pos);
	}

	/**
	 * Debug: how much of a house put up from this plan here is gone: for each layer up from its corner, how many of the
	 * blocks the plan has there are air now (null if there's no such plan).
	 */
	@Nullable
	public static int[] missingByLayer(ServerLevel level, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(level.getServer(), planId);
		if (plan == null) {
			return null;
		}

		var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn());
		int[] missing = new int[plan.size.getY() + 1];
		for (var cell : placed.buildMap().entrySet()) {
			int layer = cell.getKey().getY() - origin.getY();
			if (layer >= 0 && layer < missing.length && !cell.getValue().isAir() && level.getBlockState(cell.getKey()).isAir()) {
				missing[layer]++;
			}
		}

		return missing;
	}

	/** The same, its cobblestone of {@code stone} ("sandstone", "mossy_cobblestone"...) rather than cobblestone (null: as drawn). */
	@Nullable
	public static PlacedHouse placeHouse(ServerLevel level, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation,
		String wood, @Nullable String stone, @Nullable net.minecraft.core.Direction front) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(level.getServer(), planId);
		if (plan == null) {
			return null;
		}

		var palette = com.dinglebob.drifter.building.plan.HousePalette.forWood(plan, wood, stone);
		var placed = com.dinglebob.drifter.building.plan.HousePlacer.place(level, plan, origin, rotation, palette);
		return new PlacedHouse(planId, origin.immutable(), rotation, wood, placed.box(), com.dinglebob.drifter.building.plan.HousePlacer.door(placed, front),
			stone);
	}

	/**
	 * It lives in this house from now on: its home is the house (counted as built and lived in; a bed put in if it has none,
	 * and a chest), it builds no hut, and {@code shared} chests (a town's store) are its too. Returns its new home.
	 */
	public static Home settleIn(DrifterEntity drifter, PlacedHouse house, List<net.minecraft.core.BlockPos> shared) {
		ServerLevel level = (ServerLevel)drifter.level();
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(level.getServer(), house.planId());
		var palette = plan == null ? com.dinglebob.drifter.building.plan.HousePalette.asDrawn()
			: com.dinglebob.drifter.building.plan.HousePalette.forWood(plan, house.wood(), house.stone());
		var box = house.box();
		// (Its middle at floor level: a block up from a plan whose lowest layer is its ground.)
		boolean groundLayer = plan != null && plan.cells.stream()
			.anyMatch(cell -> cell.pos().getY() == 0 && com.dinglebob.drifter.building.plan.PlacedPlan.isGround(cell.state()));
		Home home = new Home(new net.minecraft.core.BlockPos((box.minX() + box.maxX()) / 2, house.origin().getY() + (groundLayer ? 1 : 0),
			(box.minZ() + box.maxZ()) / 2), level.dimension().location(), level.getGameTime());
		home.noHut = true;
		home.builtRecorded = true;
		home.planId = house.planId();
		home.planOrigin = house.origin();
		home.planRotation = house.rotation().name();
		home.planWoods.clear();
		home.planWoods.putAll(palette.woods());
		home.planDeepslate = palette.deepslate();
		home.planDone = true;
		home.setPlanBox(box);
		com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(level, box);
		if (plan != null) {
			var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, house.origin(), house.rotation(), palette);
			net.minecraft.core.BlockPos bed = com.dinglebob.drifter.building.plan.HousePlacer.bedIn(level, placed);
			if (bed == null) {
				var spot = com.dinglebob.drifter.brain.activity.BuildPlanActivity.bedSpot(level, placed, placed.buildMap());
				if (spot != null) {
					bed = spot.foot().relative(spot.facing());
					level.setBlock(spot.foot(), net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, spot.facing())
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
					level.setBlock(bed, net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BedBlock.FACING, spot.facing())
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
				}
			}

			home.planBed = bed;
		}

		shared.forEach(home::addChest);
		drifter.getDrifterBrain().setHome(home);
		// Its house furnished as a player furnishes a base (the user's furnishing guidebook, 2026-10-23): a storage wall with
		// the crafting table in it, furnace and smoker with the fuel chest, a dump chest by the door, a bedside chest. It
		// lived there: it knows them, and the inside of its house (found in testing: never having seen in, it couldn't find
		// the stairs to its own chest, and pillared).
		if (plan != null) {
			var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, house.origin(), house.rotation(), palette);
			long now = level.getGameTime();
			for (var spot : com.dinglebob.drifter.building.Furnishing.furnish(level, placed, home.planBed, net.minecraft.world.level.block.Block.UPDATE_ALL)) {
				var state = level.getBlockState(spot.pos());
				if (spot.use().chest()) {
					// (A double chest once: its two halves are one container.)
					if (state.is(net.minecraft.world.level.block.Blocks.CHEST) && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
						!= net.minecraft.world.level.block.state.properties.ChestType.RIGHT) {
						home.addChest(spot.pos());
					}
				} else {
					drifter.getMemory().remember(com.dinglebob.drifter.memory.MemoryKind.STATION, net.minecraft.core.registries.BuiltInRegistries.BLOCK
						.getKey(state.getBlock()).toString(), net.minecraft.world.phys.Vec3.atCenterOf(spot.pos()), level.dimension().location(), now,
						com.dinglebob.drifter.memory.MemorySource.SEEN, 0.7F, null);
				}
			}

			knowsBox(drifter, box.inflatedBy(3));
		}

		return home;
	}

	/**
	 * (A town made at once, for testing.) It lives in this house too, alongside whoever lives there already (a house with
	 * more than one room takes more than one household; user, 2026-10-23): a bed of its own in a room away from theirs
	 * (and a chest beside it), the house's chests and stations shared, and {@code shared} chests (a town's store) its too.
	 * Null if there's no room for its bed.
	 */
	@Nullable
	public static Home settleInToo(DrifterEntity drifter, PlacedHouse house, List<net.minecraft.core.BlockPos> shared) {
		ServerLevel level = (ServerLevel)drifter.level();
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(level.getServer(), house.planId());
		if (plan == null) {
			return null;
		}

		var palette = com.dinglebob.drifter.building.plan.HousePalette.forWood(plan, house.wood(), house.stone());
		var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, house.origin(), house.rotation(), palette);
		var spot = com.dinglebob.drifter.brain.activity.BuildPlanActivity.bedSpot(level, placed, placed.buildMap());
		if (spot == null) {
			return null;
		}

		var box = house.box();
		boolean groundLayer = plan.cells.stream()
			.anyMatch(cell -> cell.pos().getY() == 0 && com.dinglebob.drifter.building.plan.PlacedPlan.isGround(cell.state()));
		Home home = new Home(new net.minecraft.core.BlockPos((box.minX() + box.maxX()) / 2, house.origin().getY() + (groundLayer ? 1 : 0),
			(box.minZ() + box.maxZ()) / 2), level.dimension().location(), level.getGameTime());
		home.noHut = true;
		home.builtRecorded = true;
		home.planId = house.planId();
		home.planOrigin = house.origin();
		home.planRotation = house.rotation().name();
		home.planWoods.clear();
		home.planWoods.putAll(palette.woods());
		home.planDeepslate = palette.deepslate();
		home.planDone = true;
		home.groundEased = true;
		home.setPlanBox(box);
		var bed = net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, spot.facing());
		level.setBlock(spot.foot(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
		level.setBlock(spot.head(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
		home.planBed = spot.head();
		// A chest of its own beside the head of its bed, facing the same way the bed does into the room.
		for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[] {spot.facing().getClockWise(), spot.facing().getCounterClockWise()}) {
			net.minecraft.core.BlockPos by = spot.head().relative(side);
			if (level.getBlockState(by).isAir() && level.getBlockState(by.below()).isFaceSturdy(level, by.below(), net.minecraft.core.Direction.UP)
				&& placed.contains(by, 0)) {
				com.dinglebob.drifter.building.Furnish.chest(level, by, spot.facing().getOpposite(), 3);
				break;
			}
		}

		shared.forEach(home::addChest);
		long now = level.getGameTime();
		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			var state = level.getBlockState(pos);
			if (state.is(net.minecraft.world.level.block.Blocks.CHEST) && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
				!= net.minecraft.world.level.block.state.properties.ChestType.RIGHT) {
				home.addChest(pos.immutable());
			} else if (state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE) || state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) {
				drifter.getMemory().remember(com.dinglebob.drifter.memory.MemoryKind.STATION, net.minecraft.core.registries.BuiltInRegistries.BLOCK
					.getKey(state.getBlock()).toString(), net.minecraft.world.phys.Vec3.atCenterOf(pos), level.dimension().location(), now,
					com.dinglebob.drifter.memory.MemorySource.SEEN, 0.7F, null);
			}
		}

		drifter.getDrifterBrain().setHome(home);
		knowsBox(drifter, box.inflatedBy(3));
		return home;
	}

	/** How many households this house takes: one, and one more for each other room big enough to sleep in (up to four). */
	public static int households(ServerLevel level, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(level.getServer(), planId);
		if (plan == null) {
			return 1;
		}

		return com.dinglebob.drifter.building.Furnishing.households(level,
			new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn()));
	}

	/** (A town made at once, for testing.) It knows every block in this box, as if it had looked all over it. */
	public static void knowsBox(DrifterEntity drifter, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		var terrain = drifter.getSenses().terrain();
		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			terrain.mark(pos);
		}
	}

	/** The tree this log or grown leaf belongs to felled whole, trunk and crown (clearing for a build). True if one was. */
	public static boolean fellTree(ServerLevel level, net.minecraft.core.BlockPos pos) {
		return com.dinglebob.drifter.world.gen.Felling.fellTree(level, pos);
	}

	/**
	 * (A town made at once, for testing.) Gear of the middle to late game, as a Drifter well on in its life has: iron armor
	 * with some diamond, its favorite weapon at that tier, a shield, a bow and arrows, iron tools (user, 2026-10-23: "all of
	 * the players should be like mid-late game, above tier 2, that way drifters can defend themselves").
	 */
	public static void equipSettled(DrifterEntity drifter, net.minecraft.util.RandomSource random) {
		com.dinglebob.drifter.world.gen.Delvers.settledKit(drifter, random);
	}

	/**
	 * (A town made ready.) Gear by its place in the town: "citizen" (a working life's, humble to well off), "guard" (a full
	 * iron suit, shield and bow) or "leader" (a gold helmet for a crown, a diamond weapon).
	 */
	public static void equipTown(DrifterEntity drifter, net.minecraft.util.RandomSource random, String role) {
		com.dinglebob.drifter.world.gen.Delvers.townKit(drifter, random, role);
	}

	/** (A town made ready.) The stall keeper's leather apron: a leather tunic in place of what it wore. */
	public static void wearApron(DrifterEntity drifter) {
		drifter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE));
	}

	/** It builds no hut of its own (a town's people live in houses); it still builds a house. */
	public static void setNoHut(DrifterEntity drifter, boolean noHut) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			home.noHut = noHut;
		}
	}

	/** A group of these Drifters, the first leading, named {@code name}; null if fewer than two. */
	@Nullable
	public static DrifterGroups.Group formGroup(List<DrifterEntity> members, String name) {
		if (members.size() < 2) {
			return null;
		}

		DrifterEntity leader = members.get(0);
		DrifterGroups.Group group = DrifterGroups.form(leader.getUUID(), leader.getName().getString(), members.get(1).getUUID(),
			members.get(1).getName().getString(), name);
		group.limit = Math.max(group.limit, members.size());
		for (DrifterEntity member : members.subList(2, members.size())) {
			DrifterGroups.join(group, member.getUUID(), member.getName().getString());
		}

		return group;
	}

	/**
	 * What it thinks of another Drifter, set outright, by id (it needn't be loaded): trust and how well it knows them (a
	 * town made ready, its people with a past).
	 */
	public static void setBond(DrifterEntity drifter, UUID other, String name, double trust, double familiarity) {
		var r = drifter.getBonds().heardOf(other, name);
		r.addTrust(trust - r.trust);
		r.addFamiliarity(familiarity - r.familiarity);
	}

	/** Its trust in another Drifter, set (they know each other from then on). */
	public static void setTrustInDrifter(DrifterEntity drifter, DrifterEntity other, double trust) {
		drifter.getBonds().setTrust(other, trust);
	}

	/** Its trust in another Drifter moved by {@code delta} (they know each other from then on). */
	public static void addTrustInDrifter(DrifterEntity drifter, DrifterEntity other, double delta) {
		drifter.getBonds().setTrust(other, trustInDrifter(drifter, other.getUUID()) + delta);
	}

	/**
	 * Neighbours who get along don't turn into rivals over the same trees and ground (only those already at odds do); off
	 * in the plain mod. For a mod whose Drifters settle together (towns).
	 */
	public static void setNeighbourly(boolean neighbourly) {
		com.dinglebob.drifter.social.DrifterBonds.neighbourly = neighbourly;
	}

	/** Every group there is. */
	public static List<DrifterGroups.Group> groups() {
		return DrifterGroups.all();
	}

	/** The house plans it would weigh for a house of its own (every hand-made one, and generated ones to its taste). */
	public static List<HousePlanInfo> housePlansFor(DrifterEntity drifter) {
		List<HousePlanInfo> plans = new ArrayList<>();
		if (drifter.getServer() == null) {
			return plans;
		}

		com.dinglebob.drifter.building.plan.HousePlans.choicesFor(drifter.getServer(), drifter)
			.forEach(plan -> plans.add(new HousePlanInfo(plan.id, plan.size.getX(), plan.size.getY(), plan.size.getZ(), plan.cells.stream()
				.anyMatch(cell -> cell.pos().getY() == 0 && com.dinglebob.drifter.building.plan.PlacedPlan.isGround(cell.state())))));
		return plans;
	}

	/**
	 * Where this plan's corner goes with its corner column at {@code corner} (x and z; y is found), turned so, if the ground
	 * there as this Drifter knows it suits a house (it has seen it; not too steep, no water, clear of its own things). Null
	 * if it doesn't, or it doesn't know enough of it.
	 */
	@Nullable
	public static net.minecraft.core.BlockPos siteFor(DrifterEntity drifter, String planId, net.minecraft.core.BlockPos corner,
		net.minecraft.world.level.block.Rotation rotation) {
		var plan = drifter.getServer() == null ? null : com.dinglebob.drifter.building.plan.HousePlans.get(drifter.getServer(), planId);
		if (plan == null) {
			return null;
		}

		var site = com.dinglebob.drifter.building.plan.PlanSite.at(drifter, plan, com.dinglebob.drifter.building.plan.HousePalette.forDrifter(drifter, plan),
			corner.getX(), corner.getZ(), rotation);
		return site == null ? null : site.origin();
	}

	/**
	 * {@link #siteFor} for many spots in a row with one plan (a town looking along its roads for a plot): what it takes to
	 * work out is worked out once. The function gives null where the ground doesn't suit.
	 */
	public static java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.Rotation, net.minecraft.core.BlockPos> siteFinder(
		DrifterEntity drifter, String planId) {
		var plan = drifter.getServer() == null ? null : com.dinglebob.drifter.building.plan.HousePlans.get(drifter.getServer(), planId);
		if (plan == null) {
			return (corner, rotation) -> null;
		}

		var finder = com.dinglebob.drifter.building.plan.PlanSite.finder(drifter, plan, com.dinglebob.drifter.building.plan.HousePalette.forDrifter(drifter, plan));
		return (corner, rotation) -> {
			var site = finder.apply(corner, rotation);
			return site == null ? null : site.origin();
		};
	}

	/** A spot for a house: its corner and turn. */
	public record HouseSite(net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation) {
	}

	/** The best spot for this plan round its home that it knows of (as it picks one for a house of its own), or null. */
	@Nullable
	public static HouseSite siteNear(DrifterEntity drifter, String planId) {
		var plan = drifter.getServer() == null ? null : com.dinglebob.drifter.building.plan.HousePlans.get(drifter.getServer(), planId);
		if (plan == null) {
			return null;
		}

		var site = com.dinglebob.drifter.building.plan.PlanSite.choose(drifter, plan, com.dinglebob.drifter.building.plan.HousePalette.forDrifter(drifter, plan));
		return site == null ? null : new HouseSite(site.origin(), site.rotation());
	}

	/** Why the last spots looked at for a house were turned down, the most common first ("too steep 40, water 12"). */
	public static String siteRejections() {
		return com.dinglebob.drifter.building.plan.PlanSite.lastRejections();
	}

	/**
	 * It builds this house here, as its home's house (in the woods it has), as a player picks a plan and a spot; group mates
	 * living at the same home build it with it. Its corner {@code origin} as {@link #siteFor} gives it.
	 */
	public static void buildHouseAt(DrifterEntity drifter, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation) {
		Home home = drifter.getDrifterBrain().home();
		var plan = drifter.getServer() == null ? null : com.dinglebob.drifter.building.plan.HousePlans.get(drifter.getServer(), planId);
		if (home == null || plan == null) {
			return;
		}

		var palette = com.dinglebob.drifter.building.plan.HousePalette.forDrifter(drifter, plan);
		home.planId = planId;
		home.planOrigin = origin.immutable();
		home.planRotation = rotation.name();
		home.planWoods.clear();
		home.planWoods.putAll(palette.woods());
		home.planDeepslate = palette.deepslate();
		home.planDone = false;
		home.planBed = null;
		home.groundEased = false;
		home.setPlanBox(com.dinglebob.drifter.building.plan.PlacedPlan.of(plan, home).box());
	}

	/**
	 * A home of its own at the house it's to build there (it moves out of its old home: a group's base): no hut, the house
	 * to build (as {@link #buildHouseAt}), its old home's farm and pen, and its old home's chests and {@code chests} kept as
	 * its own too. Until the house is up it has no bed (it sleeps out or digs in). Returns the new home.
	 */
	@Nullable
	public static Home homeAtHouse(DrifterEntity drifter, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation,
		List<net.minecraft.core.BlockPos> chests) {
		var plan = drifter.getServer() == null ? null : com.dinglebob.drifter.building.plan.HousePlans.get(drifter.getServer(), planId);
		if (plan == null) {
			return null;
		}

		var palette = com.dinglebob.drifter.building.plan.HousePalette.forDrifter(drifter, plan);
		var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, palette);
		var box = placed.box();
		boolean groundLayer = plan.cells.stream()
			.anyMatch(cell -> cell.pos().getY() == 0 && com.dinglebob.drifter.building.plan.PlacedPlan.isGround(cell.state()));
		Home old = drifter.getDrifterBrain().home();
		Home home = new Home(new net.minecraft.core.BlockPos((box.minX() + box.maxX()) / 2, origin.getY() + (groundLayer ? 1 : 0), (box.minZ() + box.maxZ()) / 2),
			drifter.level().dimension().location(), drifter.level().getGameTime());
		home.noHut = true;
		if (old != null && old.dimension.equals(home.dimension)) {
			home.farm = old.farm;
			home.farmRadius = old.farmRadius;
			home.pen = old.pen;
			old.chests().forEach(home::addChest);
		}

		chests.forEach(home::addChest);
		drifter.getDrifterBrain().setHome(home);
		buildHouseAt(drifter, planId, origin, rotation);
		return home;
	}

	/** The plan for a town's store made from this house plan: the house with chests along its inside walls. */
	public static String storePlan(String housePlanId) {
		return com.dinglebob.drifter.building.plan.HousePlans.STORE_PREFIX + housePlanId;
	}

	/**
	 * Something these Drifters build together that isn't a house of theirs (a town's store): the plan at {@code origin}
	 * turned so, in the first one's woods (the same for all), called {@code label} ("store"). Each works on it by day when
	 * near it (alongside its own house); placing every block itself, from what it has or gathers. When it's done
	 * {@link DrifterEvents#BUILT} says {@code "town:" + label}, with the origin.
	 */
	public static void townBuild(List<DrifterEntity> builders, String planId, net.minecraft.core.BlockPos origin,
		net.minecraft.world.level.block.Rotation rotation, String label) {
		if (builders.isEmpty() || builders.get(0).getServer() == null) {
			return;
		}

		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(builders.get(0).getServer(), planId);
		if (plan == null) {
			return;
		}

		var palette = com.dinglebob.drifter.building.plan.HousePalette.forDrifter(builders.get(0), plan);
		for (DrifterEntity builder : builders) {
			joinTownBuild(builder, planId, origin, rotation, label, palette.woods(), palette.deepslate());
		}
	}

	/** One more on a town build already under way (the woods as the others have them). */
	public static void joinTownBuild(DrifterEntity builder, String planId, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation,
		String label, java.util.Map<String, String> woods, boolean deepslate) {
		Home home = builder.getDrifterBrain().home();
		if (home == null) {
			return;
		}

		Home.Build build = new Home.Build();
		build.planId = planId;
		build.origin = origin.immutable();
		build.rotation = rotation.name();
		build.woods.putAll(woods);
		build.deepslate = deepslate;
		build.label = label;
		home.project = build;
	}

	/** Its town build (null if none): plan, origin, turn, woods, label, done. */
	@Nullable
	public static Home.Build townBuildOf(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		return home == null ? null : home.project;
	}

	/** It stops working on its town build (done, or called off). */
	public static void endTownBuild(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			home.project = null;
		}
	}

	/** Where the chests of this plan stand once built at {@code origin}, turned so. */
	public static List<net.minecraft.core.BlockPos> chestsIn(MinecraftServer server, String planId, net.minecraft.core.BlockPos origin,
		net.minecraft.world.level.block.Rotation rotation) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(server, planId);
		if (plan == null) {
			return List.of();
		}

		return new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn()).build
			.stream().filter(cell -> cell.state().getBlock() instanceof net.minecraft.world.level.block.ChestBlock).map(cell -> cell.pos().immutable()).toList();
	}

	/**
	 * It keeps a shop (a town's market stall): right-clicked, it opens the trading screen with what {@code shop} offers,
	 * every trade moving real things; by day it stocks up from its home's chests and stands at {@code shop.stand}. Null: no
	 * shop. Not saved (set it again when the world loads, or each check).
	 */
	public static void setShop(DrifterEntity drifter, @Nullable ShopKeeper shop) {
		drifter.setShop(shop);
	}

	/**
	 * It lives in a town and shares its work (a town founded by its people): its roads made into path, its ground kept lit,
	 * the ground round its house eased into the land, each by its own hands. Null: none. Not saved (set it again each check).
	 */
	public static void setTownWork(DrifterEntity drifter, @Nullable TownWork work) {
		drifter.setTownWork(work);
	}

	/** The plan of a town's market stall (a counter, the keeper behind it; its door at the back). */
	public static String stallPlan() {
		return com.dinglebob.drifter.building.plan.HousePlans.STALL;
	}

	/** A price on this player's head, from {@code issuer} for {@code reason} (bounty hunters come); false if there is one. */
	public static boolean postBounty(net.minecraft.server.level.ServerPlayer player, String issuer, String reason) {
		return com.dinglebob.drifter.encounter.Encounters.postBountyFor(player, issuer, reason);
	}

	/** It goes on this trade run to another town (step 4), or (null) it's off it. */
	public static void setTradeRun(DrifterEntity drifter, @Nullable TradeRun run) {
		drifter.setTradeRun(run);
	}

	/** It goes on this errand to another town (an envoy; null: none). */
	public static void setErrand(DrifterEntity drifter, @Nullable Errand errand) {
		drifter.setErrand(errand);
	}

	/** The errand it's on, or null. */
	@Nullable
	public static Errand errand(DrifterEntity drifter) {
		return drifter.getErrand();
	}

	/** Two groups' tension (0 to 100: 30 they fight on sight, 70 war and raids; DrifterWars). */
	public static double tension(UUID a, UUID b) {
		return com.dinglebob.drifter.world.DrifterWars.tension(a, b);
	}

	/** Two groups' tension, set (a peace pact holds it down; a war declared puts it up). */
	public static void setTension(MinecraftServer server, UUID a, UUID b, double tension) {
		com.dinglebob.drifter.world.DrifterWars.setTension(a, b, tension, server.overworld().getGameTime());
	}

	/** Its group's wars run by another mod (a town's): no truce of their own (see {@link #declareWar}, {@link #makePeace}). */
	public static void setWarManaged(UUID group, boolean managed) {
		com.dinglebob.drifter.world.DrifterWars.setManaged(group, managed);
	}

	/** Where its raids go for groups another mod knows the places of (a town's store); null from it: as usual. */
	public static void setRaidTargets(@Nullable java.util.function.BiFunction<UUID, UUID, net.minecraft.core.BlockPos> targets) {
		com.dinglebob.drifter.world.DrifterWars.setRaidTargets(targets);
	}

	/** Where its groups gather for a raid (a town's plaza), for groups another mod knows the places of; null: as usual. */
	public static void setRaidMusters(@Nullable java.util.function.Function<UUID, net.minecraft.core.BlockPos> musters) {
		com.dinglebob.drifter.world.DrifterWars.setRaidMusters(musters);
	}

	/** Two groups at war (fighting on sight, raids). */
	public static void declareWar(MinecraftServer server, UUID a, UUID b) {
		com.dinglebob.drifter.world.DrifterWars.setTension(a, b, com.dinglebob.drifter.world.DrifterWars.WAR + 10.0, server.overworld().getGameTime());
	}

	/** Two groups at peace, with a truce of this many game days. */
	public static void makePeace(MinecraftServer server, UUID a, UUID b, int days) {
		com.dinglebob.drifter.world.DrifterWars.peace(server, a, b, 24000L * days);
	}

	/** The trade run it's on, or null. */
	@Nullable
	public static TradeRun tradeRun(DrifterEntity drifter) {
		return drifter.getTradeRun();
	}

	/** The plans of a town's well, notice board and lamp post (milestone 22; built as town builds). */
	public static String wellPlan() {
		return com.dinglebob.drifter.building.plan.HousePlans.WELL;
	}

	public static String boardPlan() {
		return com.dinglebob.drifter.building.plan.HousePlans.BOARD;
	}

	public static String lampPlan() {
		return com.dinglebob.drifter.building.plan.HousePlans.LAMP;
	}

	/** The cells of a well built at {@code origin}, turned so, that water is poured into (corner to corner of its basin). */
	public static List<net.minecraft.core.BlockPos> wellWater(MinecraftServer server, net.minecraft.core.BlockPos origin,
		net.minecraft.world.level.block.Rotation rotation) {
		return inPlan(server, com.dinglebob.drifter.building.plan.HousePlans.WELL, origin, rotation, com.dinglebob.drifter.building.plan.HousePlans.WELL_WATER);
	}

	/** The signs of a notice board built at {@code origin}, turned so (left to right as you read them). */
	public static List<net.minecraft.core.BlockPos> boardSigns(MinecraftServer server, net.minecraft.core.BlockPos origin,
		net.minecraft.world.level.block.Rotation rotation) {
		return inPlan(server, com.dinglebob.drifter.building.plan.HousePlans.BOARD, origin, rotation, com.dinglebob.drifter.building.plan.HousePlans.BOARD_SIGNS);
	}

	private static List<net.minecraft.core.BlockPos> inPlan(MinecraftServer server, String planId, net.minecraft.core.BlockPos origin,
		net.minecraft.world.level.block.Rotation rotation, List<net.minecraft.core.BlockPos> local) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(server, planId);
		var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn());
		return local.stream().map(pos -> placed.toWorld(pos).immutable()).toList();
	}

	/** Where a stall's keeper stands (behind its counter), the stall built at {@code origin} turned so. */
	public static net.minecraft.core.BlockPos stallStand(MinecraftServer server, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Rotation rotation) {
		var plan = com.dinglebob.drifter.building.plan.HousePlans.get(server, com.dinglebob.drifter.building.plan.HousePlans.STALL);
		return new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, com.dinglebob.drifter.building.plan.HousePalette.asDrawn())
			.toWorld(com.dinglebob.drifter.building.plan.HousePlans.STALL_STAND);
	}

	/**
	 * (A town made at once, for testing: its people stocked its store themselves.) It knows what's in this chest now, as if
	 * it had just looked in.
	 */
	public static void rememberChest(DrifterEntity drifter, net.minecraft.core.BlockPos chest) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null && drifter.level().getBlockEntity(chest) instanceof net.minecraft.world.Container container) {
			home.addChest(chest);
			home.record(chest, container);
		}
	}

	/** Its shop's trading screen for this player now, as if right-clicked (debug). False if it has none, or nothing on offer. */
	public static boolean openShop(DrifterEntity drifter, net.minecraft.world.entity.player.Player player) {
		return drifter.getShop() != null && com.dinglebob.drifter.trade.DrifterMerchant.open(drifter, drifter.getShop(), player);
	}

	/** One of its personality traits (0 to 1), by name ("greed", "sociability", ...; 0.5 if there's no such trait). */
	public static double trait(DrifterEntity drifter, String name) {
		for (com.dinglebob.drifter.personality.Trait trait : com.dinglebob.drifter.personality.Trait.values()) {
			if (trait.id().equals(name)) {
				return drifter.getPersonality().get(trait);
			}
		}

		return 0.5;
	}

	/** What one of these is worth to a Drifter, in points (an emerald is 6): what it trades by. */
	public static double worth(net.minecraft.world.item.ItemStack stack) {
		return com.dinglebob.drifter.social.ItemValues.each(stack);
	}

	/** About this many points of money as one stack: Numismatic Overhaul coins when that mod is here, else emeralds. */
	public static net.minecraft.world.item.ItemStack money(double points) {
		return com.dinglebob.drifter.trade.Money.priceStack(points);
	}

	/** What a stack of money is worth in points (0: not money). */
	public static double moneyWorth(net.minecraft.world.item.ItemStack stack) {
		return com.dinglebob.drifter.trade.Money.worth(stack);
	}

	/** Prices are in Numismatic Overhaul coins (that mod is here), not emeralds. */
	public static boolean coins() {
		return com.dinglebob.drifter.trade.Money.COINS;
	}

	/** These chests are its too (a town's store, its group's). */
	public static void addChests(DrifterEntity drifter, List<net.minecraft.core.BlockPos> chests) {
		Home home = drifter.getDrifterBrain().home();
		if (home != null) {
			chests.forEach(home::addChest);
		}
	}

	/** A Drifter you meet once (an encounter's, a delver): never saved, gone when its part is over. */
	public static boolean temporary(DrifterEntity drifter) {
		return drifter.isTemporary();
	}
}
