package com.dinglebob.drifter.debug.overlay;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.Drifter;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Debug overlay data for one Drifter, sent to operators who turned the overlay on (DESIGN.md section 25).
 * It holds only what the Drifter perceives and remembers, so the overlay can't show anything it doesn't know.
 */
public record DebugSnapshotPayload(
	int drifterId,
	List<String> lines,
	List<RayLine> rays,
	List<TrackedMark> tracked,
	List<MemoryMark> memories,
	List<SoundMark> sounds
) implements CustomPacketPayload {
	public static final Type<DebugSnapshotPayload> TYPE = new Type<>(Drifter.id("debug_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DebugSnapshotPayload> CODEC = StreamCodec.of(DebugSnapshotPayload::write, DebugSnapshotPayload::read);

	/** A block-sampling ray: where it started and stopped, whether it hit a block and whether that block was notable. */
	public record RayLine(Vec3 from, Vec3 to, boolean hit, boolean notable) {
	}

	/** A tracked entity: awareness 0 to 1, whether it's identified (and as what), whether it's in view, where it was last seen. */
	public record TrackedMark(int entityId, float awareness, boolean identified, boolean visible, String label, Vec3 lastSeen) {
	}

	/** A remembered thing: kind (MemoryKind ordinal), where, how sure, what, short or long term. */
	public record MemoryMark(int kind, Vec3 pos, float confidence, String label, boolean longTerm) {
	}

	/** A heard sound: rough position, kind, how many ticks ago. */
	public record SoundMark(Vec3 pos, String kind, int age) {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
		buf.writeDouble(vec.x);
		buf.writeDouble(vec.y);
		buf.writeDouble(vec.z);
	}

	private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private static void write(RegistryFriendlyByteBuf buf, DebugSnapshotPayload payload) {
		buf.writeVarInt(payload.drifterId);
		buf.writeVarInt(payload.lines.size());
		payload.lines.forEach(buf::writeUtf);
		buf.writeVarInt(payload.rays.size());
		for (RayLine ray : payload.rays) {
			writeVec(buf, ray.from());
			writeVec(buf, ray.to());
			buf.writeBoolean(ray.hit());
			buf.writeBoolean(ray.notable());
		}

		buf.writeVarInt(payload.tracked.size());
		for (TrackedMark mark : payload.tracked) {
			buf.writeVarInt(mark.entityId());
			buf.writeFloat(mark.awareness());
			buf.writeBoolean(mark.identified());
			buf.writeBoolean(mark.visible());
			buf.writeUtf(mark.label());
			writeVec(buf, mark.lastSeen());
		}

		buf.writeVarInt(payload.memories.size());
		for (MemoryMark mark : payload.memories) {
			buf.writeVarInt(mark.kind());
			writeVec(buf, mark.pos());
			buf.writeFloat(mark.confidence());
			buf.writeUtf(mark.label());
			buf.writeBoolean(mark.longTerm());
		}

		buf.writeVarInt(payload.sounds.size());
		for (SoundMark mark : payload.sounds) {
			writeVec(buf, mark.pos());
			buf.writeUtf(mark.kind());
			buf.writeVarInt(mark.age());
		}
	}

	private static DebugSnapshotPayload read(RegistryFriendlyByteBuf buf) {
		int id = buf.readVarInt();
		List<String> lines = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			lines.add(buf.readUtf());
		}

		List<RayLine> rays = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			rays.add(new RayLine(readVec(buf), readVec(buf), buf.readBoolean(), buf.readBoolean()));
		}

		List<TrackedMark> tracked = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			tracked.add(new TrackedMark(buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(), readVec(buf)));
		}

		List<MemoryMark> memories = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			memories.add(new MemoryMark(buf.readVarInt(), readVec(buf), buf.readFloat(), buf.readUtf(), buf.readBoolean()));
		}

		List<SoundMark> sounds = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			sounds.add(new SoundMark(readVec(buf), buf.readUtf(), buf.readVarInt()));
		}

		return new DebugSnapshotPayload(id, lines, rays, tracked, memories, sounds);
	}
}
