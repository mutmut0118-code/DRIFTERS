package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Hybrid offscreen simulation (DESIGN.md section 22, user's decision 2026-10-15): away from players, the few Drifters
 * doing the most (config, default 3) keep the chunks round them loaded and running as a player does, so they really
 * play, and crops grow round them (random ticks run near them as near a player: see ChunkMapMixin). The rest freeze with
 * their chunks and catch up later. Who is simulated is saved with the world, so it carries on after a restart.
 */
public final class DrifterSimulation {
	private DrifterSimulation() {
	}

	/** Players this close keep a Drifter running anyway; it doesn't need a slot. */
	private static final double PLAYER_RANGE = 128.0;
	/** Chunks round a simulated Drifter kept loaded (3: entities tick in its chunk and the ring round it, random ticks a
	 * little further, about as for a player standing still). */
	private static final int TICKET_RADIUS = 3;
	/** How far round a simulated Drifter counts as "near a player" for random ticks (blocks, as for players: 128). */
	private static final double TICK_RANGE = 128.0;
	/** A simulated Drifter keeps its slot unless another is clearly busier. */
	private static final double STICKINESS = 0.5;

	private static final TicketType<ChunkPos> TICKET = TicketType.create("drifter_simulated", Comparator.comparingLong(ChunkPos::toLong), 60);

	/** Who is simulated now, and where (dimension and chunk), for the tickets and the random-tick check. */
	public record Simulated(UUID id, String name, ResourceKey<Level> level, ChunkPos chunk) {
	}

	private static final List<Simulated> simulated = new ArrayList<>();

	public static boolean isSimulated(UUID id) {
		return simulated.stream().anyMatch(s -> s.id().equals(id));
	}

	public static List<Simulated> simulated() {
		return List.copyOf(simulated);
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(DrifterSimulation::load);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> simulated.clear());
		// A kept-running Drifter whose area unloads anyway (teleported far, or fell a long way, between two updates): its
		// spot moves to where it really is, so the ticket loads it back up there (found in a 100-Drifter test: three were
		// spread across the world by command and froze where they landed, their tickets left at spawn).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof DrifterEntity drifter && entity.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK) {
				for (int i = 0; i < simulated.size(); i++) {
					if (simulated.get(i).id().equals(drifter.getUUID())) {
						simulated.set(i, new Simulated(drifter.getUUID(), drifter.getName().getString(), level.dimension(), drifter.chunkPosition()));
						level.getChunkSource().addRegionTicket(TICKET, drifter.chunkPosition(), TICKET_RADIUS, drifter.chunkPosition());
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		int slots = DrifterConfig.simulatedDrifters();
		Set<UUID> before = new HashSet<>();
		simulated.forEach(s -> before.add(s.id()));

		// Every loaded Drifter away from all players is a candidate; the busiest get the slots.
		record Candidate(DrifterEntity drifter, double score) {
		}

		List<Candidate> candidates = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				if (nearPlayer(level, drifter)) {
					continue;
				}

				double score = busyness(drifter) + (before.contains(drifter.getUUID()) ? STICKINESS : 0.0);
				candidates.add(new Candidate(drifter, score));
			}
		}

		candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
		List<Simulated> now = new ArrayList<>();
		for (Candidate c : candidates) {
			if (now.size() >= slots) {
				break;
			}

			now.add(new Simulated(c.drifter().getUUID(), c.drifter().getName().getString(), c.drifter().level().dimension(), c.drifter().chunkPosition()));
		}

		// One that isn't loaded at all (its chunks unloaded, or the world just started): kept at its last spot, so the
		// ticket loads it back up and it carries on.
		for (Simulated old : simulated) {
			boolean loadedSomewhere = false;
			for (ServerLevel level : server.getAllLevels()) {
				loadedSomewhere |= level.getEntity(old.id()) != null;
			}

			if (!loadedSomewhere && now.size() < slots && now.stream().noneMatch(n -> n.id().equals(old.id()))) {
				now.add(old);
			}
		}

		simulated.clear();
		simulated.addAll(now);
		for (Simulated s : simulated) {
			ServerLevel level = server.getLevel(s.level());
			if (level != null) {
				level.getChunkSource().addRegionTicket(TICKET, s.chunk(), TICKET_RADIUS, s.chunk());
			}
		}

		if (server.getTickCount() % 200 == 0) {
			save(server);
		}
	}

	/** How much it's doing: out on a job beats resting at home. */
	private static double busyness(DrifterEntity drifter) {
		var activity = drifter.getDrifterBrain().activity();
		if (activity == null) {
			return 0.5;
		}

		String what = activity.describe();
		boolean resting = what.contains("wait for morning") || what.contains("sleep") || what.startsWith("idle") || what.contains("look around");
		double stage = com.dinglebob.drifter.brain.Strategist.stage(drifter) / 10.0;
		return (resting ? 1.0 : 2.0) + stage;
	}

	private static boolean nearPlayer(ServerLevel level, DrifterEntity drifter) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceTo(drifter) < PLAYER_RANGE) {
				return true;
			}
		}

		return false;
	}

	/** A simulated Drifter (or the manhunt hunter) is near enough this chunk for it to tick as near a player. */
	public static boolean nearSimulated(ResourceKey<Level> level, ChunkPos chunk) {
		for (Simulated s : simulated) {
			if (s.level() == level && chunkDistanceSq(s.chunk(), chunk) * 256.0 < TICK_RANGE * TICK_RANGE) {
				return true;
			}
		}

		return false;
	}

	private static double chunkDistanceSq(ChunkPos a, ChunkPos b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	// ---------------------------------------------------------------------------------------------
	// Saved with the world
	// ---------------------------------------------------------------------------------------------

	private static final class State extends SavedData {
		ListTag list = new ListTag();

		static State load(CompoundTag tag, HolderLookup.Provider registries) {
			State state = new State();
			state.list = tag.getList("Simulated", Tag.TAG_COMPOUND);
			return state;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.put("Simulated", this.list);
			return tag;
		}
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "drifter_simulation");
	}

	private static void save(MinecraftServer server) {
		ListTag list = new ListTag();
		for (Simulated s : simulated) {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("Id", s.id());
			tag.putString("Name", s.name());
			tag.putString("Level", s.level().location().toString());
			tag.putLong("Chunk", s.chunk().toLong());
			list.add(tag);
		}

		State state = state(server);
		state.list = list;
		state.setDirty();
	}

	private static void load(MinecraftServer server) {
		simulated.clear();
		for (Tag t : state(server).list) {
			CompoundTag tag = (CompoundTag)t;
			simulated.add(new Simulated(tag.getUUID("Id"), tag.getString("Name"),
				ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Level"))), new ChunkPos(tag.getLong("Chunk"))));
		}
	}
}
