package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Abstract travel (DESIGN.md section 22): a Drifter that sets off for somewhere outside the running part of the world,
 * with no player near and not one of the few kept running, would only freeze at the edge. Instead it leaves the world
 * as a traveler (saved with the world) and turns up at its destination once that place is running and the walk would
 * be over; the time from its arrival on is caught up as usual.
 */
public final class DrifterTravel {
	/** Players this close see it walk; it travels for real then. */
	private static final double PLAYER_RANGE = 128.0;
	/** Blocks a tick on a long walk, about a player's mix of sprinting and walking (section 10.9). */
	private static final double BLOCKS_PER_TICK = 0.22;
	/** How far round its destination it looks for a place to stand. */
	private static final int LANDING_SEARCH = 6;

	private record Traveler(UUID id, String name, CompoundTag entity, ResourceKey<Level> level, BlockPos destination, long arrives) {
	}

	private static final List<Traveler> travelers = new ArrayList<>();

	private DrifterTravel() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DrifterTravel::load);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> travelers.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7 && !travelers.isEmpty()) {
				arrivals(server);
			}
		});
	}

	public static List<String> describe(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		for (Traveler t : travelers) {
			long left = t.arrives() - server.overworld().getGameTime();
			lines.add(String.format(java.util.Locale.ROOT, "%s heading for %s (%s)%s", t.name(), t.destination().toShortString(),
				t.level().location(), left > 0 ? ", there in about " + (left / 20 / 60 + 1) + " min" : ", waiting for that area to load"));
		}

		return lines;
	}

	/**
	 * Whether it should travel abstractly to {@code target} instead of walking: the target is outside the running world,
	 * no player is near, it isn't kept running, and it isn't the manhunt hunter (who is always kept running).
	 */
	public static boolean shouldTravel(DrifterEntity drifter, BlockPos target) {
		if (!(drifter.level() instanceof ServerLevel level) || level.isPositionEntityTicking(target)) {
			return false;
		}

		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceTo(drifter) < PLAYER_RANGE) {
				return false;
			}
		}

		return !DrifterSimulation.isSimulated(drifter.getUUID()) && !com.dinglebob.drifter.social.Manhunt.isHunter(drifter);
	}

	/** Leaves the world for {@code target}; it turns up there later. */
	public static void depart(DrifterEntity drifter, BlockPos target) {
		ServerLevel level = (ServerLevel)drifter.level();
		CompoundTag tag = new CompoundTag();
		if (!drifter.saveAsPassenger(tag)) {
			return;
		}

		long walk = (long)(Math.sqrt(drifter.blockPosition().distSqr(target)) / BLOCKS_PER_TICK);
		travelers.add(new Traveler(drifter.getUUID(), drifter.getName().getString(), tag, level.dimension(), target.immutable(),
			level.getGameTime() + walk));
		Drifter.LOGGER.info("{} sets off for {} (outside the running world): back in the world there in about {} min", drifter.getName().getString(),
			target.toShortString(), walk / 20 / 60 + 1);
		drifter.discard();
		save(level.getServer());
	}

	private static void arrivals(MinecraftServer server) {
		boolean changed = false;
		for (var iterator = travelers.iterator(); iterator.hasNext(); ) {
			Traveler t = iterator.next();
			ServerLevel level = server.getLevel(t.level());
			if (level == null) {
				iterator.remove();
				changed = true;
				continue;
			}

			if (level.getGameTime() < t.arrives() || !level.isPositionEntityTicking(t.destination())) {
				continue;
			}

			BlockPos spot = landing(level, t.destination());
			Entity entity = EntityType.loadEntityRecursive(t.entity(), level, e -> {
				e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, e.getYRot(), e.getXRot());
				return e;
			});
			if (entity instanceof DrifterEntity drifter) {
				// Catch-up covers the time since it got there, not the walk.
				drifter.setLastActive(t.arrives());
				level.addFreshEntity(drifter);
				Drifter.LOGGER.info("{} arrives at {}", t.name(), spot.toShortString());
			}

			iterator.remove();
			changed = true;
		}

		if (changed) {
			save(server);
		}
	}

	/** A spot to stand at or near the destination: the nearest standable one, else the top of the ground there. */
	private static BlockPos landing(ServerLevel level, BlockPos destination) {
		for (BlockPos pos : BlockPos.withinManhattan(destination, LANDING_SEARCH, LANDING_SEARCH, LANDING_SEARCH)) {
			if (com.dinglebob.drifter.brain.KnownBlocks.isStandable(level, pos) && level.getFluidState(pos).isEmpty()
				&& level.getFluidState(pos.below()).isEmpty()) {
				return pos.immutable();
			}
		}

		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destination);
	}

	public static boolean isTraveling(UUID id) {
		return travelers.stream().anyMatch(t -> t.id().equals(id));
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Travelers", Tag.TAG_COMPOUND);
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.put("Travelers", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_travel");
	}

	private static void save(MinecraftServer server) {
		ListTag list = new ListTag();
		for (Traveler t : travelers) {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", t.id());
			tag.putString("Name", t.name());
			tag.put("Entity", t.entity());
			tag.putString("Level", t.level().location().toString());
			tag.putLong("Destination", t.destination().asLong());
			tag.putLong("Arrives", t.arrives());
			list.add(tag);
		}

		State state = state(server);
		state.list = list;
		state.setDirty();
	}

	private static void load(MinecraftServer server) {
		travelers.clear();
		for (Tag t : state(server).list) {
			CompoundTag tag = (CompoundTag)t;
			ResourceLocation level = ResourceLocation.tryParse(tag.getString("Level"));
			if (level != null) {
				travelers.add(new Traveler(tag.getUUID("Id"), tag.getString("Name"), tag.getCompound("Entity"), ResourceKey.create(Registries.DIMENSION, level),
					BlockPos.of(tag.getLong("Destination")), tag.getLong("Arrives")));
			}
		}
	}
}
