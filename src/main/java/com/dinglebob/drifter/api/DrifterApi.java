package com.dinglebob.drifter.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.world.DrifterGroups;

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
	public static final int API_VERSION = 1;

	private static final List<Supplier<Goals.Goal>> GOALS = new ArrayList<>();
	private static final List<CatchUpStep> CATCH_UP = new ArrayList<>();

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

	// ----- Reading and changing a Drifter -----

	/** Every Drifter loaded now, in every world. */
	public static List<DrifterEntity> loaded(MinecraftServer server) {
		List<DrifterEntity> all = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			all.addAll(level.getEntities(EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive));
		}

		return all;
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

	/** How much it trusts another Drifter (-100 to 100; 0 for a stranger). */
	public static double trustInDrifter(DrifterEntity drifter, UUID other) {
		var r = drifter.getBonds().get(other);
		return r == null ? 0.0 : r.trust;
	}

	/** A thought in its log (what `/drifter thoughts` shows), as if it thought it now. */
	public static void think(DrifterEntity drifter, String thought) {
		drifter.getMemory().thoughts().add(drifter.level().getGameTime(), thought);
	}

	/** A Drifter you meet once (an encounter's, a delver): never saved, gone when its part is over. */
	public static boolean temporary(DrifterEntity drifter) {
		return drifter.isTemporary();
	}
}
