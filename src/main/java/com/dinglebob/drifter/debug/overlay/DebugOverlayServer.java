package com.dinglebob.drifter.debug.overlay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.memory.DrifterMemory;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.memory.ThoughtLog;
import com.dinglebob.drifter.nav.MoveType;
import com.dinglebob.drifter.nav.PathStep;
import com.dinglebob.drifter.perception.Awareness;
import com.dinglebob.drifter.perception.Senses;
import com.dinglebob.drifter.perception.TimeSense;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Sends debug overlay snapshots to operators who turned it on with {@code /drifter debug on}, for every Drifter
 * within {@link #RANGE} blocks of them, every {@link #INTERVAL_TICKS} ticks.
 */
public final class DebugOverlayServer {
	public static final int INTERVAL_TICKS = 5;
	public static final double RANGE = 64.0;
	/** Memories farther than this from the Drifter aren't drawn (keeps packets small). */
	public static final double MEMORY_DRAW_RANGE = 48.0;
	public static final int MAX_MEMORY_MARKS = 200;
	public static final int MAX_PATH_MARKS = 128;

	private static final Set<UUID> SUBSCRIBERS = new HashSet<>();

	private DebugOverlayServer() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(DebugSnapshotPayload.TYPE, DebugSnapshotPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(DebugOverlayServer::tick);
	}

	public static boolean toggle(ServerPlayer player, boolean on) {
		return on ? SUBSCRIBERS.add(player.getUUID()) : SUBSCRIBERS.remove(player.getUUID());
	}

	public static boolean isOn(ServerPlayer player) {
		return SUBSCRIBERS.contains(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (SUBSCRIBERS.isEmpty() || server.getTickCount() % INTERVAL_TICKS != 0) {
			return;
		}

		for (UUID id : SUBSCRIBERS) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !ServerPlayNetworking.canSend(player, DebugSnapshotPayload.TYPE)) {
				continue;
			}

			for (DrifterEntity drifter : player.serverLevel().getEntitiesOfClass(DrifterEntity.class, player.getBoundingBox().inflate(RANGE))) {
				ServerPlayNetworking.send(player, snapshot(drifter, player));
			}
		}
	}

	private static DebugSnapshotPayload snapshot(DrifterEntity drifter, Player viewer) {
		Senses senses = drifter.getSenses();
		DrifterMemory memory = drifter.getMemory();
		long now = drifter.level().getGameTime();

		List<String> lines = new ArrayList<>();
		lines.add(drifter.getName().getString());
		Task task = drifter.getTasks().current();
		lines.add("Task: " + (task == null ? "none" : task.describe()));
		Awareness ofViewer = senses.awarenessOf(viewer);
		lines.add(ofViewer == null ? "Awareness of you: none"
			: String.format(Locale.ROOT, "Awareness of you: %.2f (%s)", ofViewer.value, ofViewer.identified ? "knows it's you" : ofViewer.level().id()));
		TimeSense time = senses.time();
		lines.add(String.format(Locale.ROOT, "Thinks it's %s (%d)%s, weather: %s", TimeSense.describeTimeOfDay(time.believedTimeOfDay()),
			time.believedTimeOfDay(), time.isExact() ? "" : " [guessing]", time.describeWeather()));
		lines.add(String.format(Locale.ROOT, "Memory: %d long-term, %d short-term, %d chunks explored, %d tracked",
			memory.count(MemoryKind.Tier.LONG), memory.count(MemoryKind.Tier.SHORT), memory.map().exploredCount(), senses.tracked().size()));
		for (ThoughtLog.Thought thought : memory.thoughts().latest(4)) {
			lines.add("> " + thought.text());
		}

		List<DebugSnapshotPayload.RayLine> rays = senses.recentRays().stream()
			.map(ray -> new DebugSnapshotPayload.RayLine(ray.from(), ray.to(), ray.hit(), ray.notable()))
			.toList();

		List<DebugSnapshotPayload.TrackedMark> tracked = senses.tracked().stream()
			.map(a -> new DebugSnapshotPayload.TrackedMark(a.entity.getId(), a.value, a.identified, a.visibleNow,
				a.identified ? label(a) : "?", a.lastSeenPos))
			.toList();

		Vec3 center = drifter.position();
		var dimension = drifter.level().dimension().location();
		List<DebugSnapshotPayload.MemoryMark> memories = memory.all()
			.filter(entry -> entry.dimension.equals(dimension) && entry.pos.distanceTo(center) <= MEMORY_DRAW_RANGE)
			.limit(MAX_MEMORY_MARKS)
			.map(entry -> new DebugSnapshotPayload.MemoryMark(entry.kind.ordinal(), entry.pos, entry.confidence(now),
				Senses.shortLabel(entry.label), entry.kind.tier == MemoryKind.Tier.LONG))
			.toList();

		List<DebugSnapshotPayload.SoundMark> sounds = senses.recentSounds().stream()
			.map(sound -> new DebugSnapshotPayload.SoundMark(sound.roughPos(), sound.kind().id(), (int)(now - sound.time())))
			.toList();

		List<PathStep> fullPath = drifter.getDebugPath();
		List<DebugSnapshotPayload.PathMark> path = fullPath.subList(Math.min(drifter.getDebugPathIndex(), fullPath.size()),
				Math.min(fullPath.size(), drifter.getDebugPathIndex() + MAX_PATH_MARKS)).stream()
			.map(step -> new DebugSnapshotPayload.PathMark(step.to(), step.type().ordinal(), step.digs()))
			.toList();
		if (!fullPath.isEmpty()) {
			long digs = fullPath.stream().skip(drifter.getDebugPathIndex()).mapToLong(step -> step.digs().size()).sum();
			long placed = fullPath.stream().skip(drifter.getDebugPathIndex())
				.filter(step -> step.type() == MoveType.BRIDGE || step.type() == MoveType.PILLAR).count();
			lines.add(2, String.format(Locale.ROOT, "Path: %d steps left, %d blocks to dig, %d to place",
				fullPath.size() - drifter.getDebugPathIndex(), digs, placed));
		}

		return new DebugSnapshotPayload(drifter.getId(), lines, rays, tracked, memories, sounds, path);
	}

	private static String label(Awareness awareness) {
		if (awareness.entity instanceof Player player) {
			return player.getGameProfile().getName();
		}

		return awareness.entity.getType().getDescription().getString();
	}
}
