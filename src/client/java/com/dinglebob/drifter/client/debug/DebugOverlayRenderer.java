package com.dinglebob.drifter.client.debug;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.dinglebob.drifter.debug.overlay.DebugSnapshotPayload;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.nav.MoveType;
import com.dinglebob.drifter.perception.Senses;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Drifter debug overlay (DESIGN.md section 25) from the snapshots the server sends. It draws only what's
 * in the snapshot, which is only what the Drifter perceives and remembers:
 * <ul>
 *   <li>its view cone (white) and peripheral edges (grey), from its head;</li>
 *   <li>the last block-sampling rays (faint blue; green where they found something worth remembering);</li>
 *   <li>tracked entities as boxes where it last saw them: yellow (glimpse), orange (suspicious), red (detected);</li>
 *   <li>remembered blocks as boxes coloured by kind, fading with confidence, labelled up close;</li>
 *   <li>heard sounds as purple crosses where it thinks they came from;</li>
 *   <li>the path it's following, coloured by move, with red boxes on blocks it will dig;</li>
 *   <li>a text panel over its head: task, awareness of you, time sense, memory counts, last thoughts.</li>
 * </ul>
 */
public final class DebugOverlayRenderer {
	/** Snapshots older than this are dropped (overlay turned off, or the Drifter left range). */
	private static final long STALE_MS = 1500;
	private static final double LABEL_DISTANCE = 10.0;
	private static final float CONE_LENGTH = 6.0F;

	private record Received(DebugSnapshotPayload snapshot, long time) {
	}

	private static final Map<Integer, Received> SNAPSHOTS = new HashMap<>();

	private DebugOverlayRenderer() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DebugSnapshotPayload.TYPE,
			(payload, context) -> SNAPSHOTS.put(payload.drifterId(), new Received(payload, System.currentTimeMillis())));
		WorldRenderEvents.LAST.register(DebugOverlayRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (SNAPSHOTS.isEmpty()) {
			return;
		}

		long nowMs = System.currentTimeMillis();
		SNAPSHOTS.values().removeIf(received -> nowMs - received.time() > STALE_MS);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			SNAPSHOTS.clear();
			return;
		}

		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		PoseStack poseStack = context.matrixStack() != null ? context.matrixStack() : new PoseStack();
		Vec3 camera = context.camera().getPosition();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (Received received : SNAPSHOTS.values()) {
			DebugSnapshotPayload snapshot = received.snapshot();
			Entity drifter = minecraft.level.getEntity(snapshot.drifterId());
			if (drifter == null) {
				continue;
			}

			VertexConsumer lines = buffers.getBuffer(RenderType.lines());
			poseStack.pushPose();
			poseStack.translate(-camera.x, -camera.y, -camera.z);
			drawCone(poseStack, lines, drifter, partialTick);
			for (DebugSnapshotPayload.RayLine ray : snapshot.rays()) {
				int color = ray.notable() ? 0xFF33FF66 : ray.hit() ? 0x664488FF : 0x33AAAAAA;
				line(poseStack, lines, ray.from(), ray.to(), color);
			}

			for (DebugSnapshotPayload.MemoryMark memory : snapshot.memories()) {
				int color = kindColor(memory.kind());
				float alpha = Mth.clamp(0.25F + 0.75F * memory.confidence(), 0.25F, 1.0F);
				AABB box = new AABB(memory.pos(), memory.pos()).inflate(memory.longTerm() ? 0.51 : 0.3);
				LevelRenderer.renderLineBox(poseStack, lines, box, red(color), green(color), blue(color), alpha);
			}

			for (DebugSnapshotPayload.TrackedMark mark : snapshot.tracked()) {
				Entity entity = minecraft.level.getEntity(mark.entityId());
				double width = entity == null ? 0.6 : entity.getBbWidth();
				double height = entity == null ? 1.8 : entity.getBbHeight();
				Vec3 at = mark.lastSeen();
				AABB box = new AABB(at.x - width / 2, at.y, at.z - width / 2, at.x + width / 2, at.y + height, at.z + width / 2).inflate(0.05);
				int color = awarenessColor(mark.awareness());
				LevelRenderer.renderLineBox(poseStack, lines, box, red(color), green(color), blue(color), mark.visible() ? 1.0F : 0.4F);
			}

			Vec3 previous = drifter.getPosition(partialTick).add(0.0, 0.1, 0.0);
			for (DebugSnapshotPayload.PathMark step : snapshot.path()) {
				Vec3 point = Vec3.atBottomCenterOf(step.to()).add(0.0, 0.1, 0.0);
				line(poseStack, lines, previous, point, moveColor(step.type()));
				previous = point;
				for (net.minecraft.core.BlockPos dig : step.digs()) {
					LevelRenderer.renderLineBox(poseStack, lines, new AABB(dig).deflate(0.05), 1.0F, 0.2F, 0.2F, 0.9F);
				}
			}

			for (DebugSnapshotPayload.SoundMark sound : snapshot.sounds()) {
				Vec3 p = sound.pos();
				float fade = Mth.clamp(1.0F - sound.age() / 600.0F, 0.2F, 1.0F);
				int color = (int)(fade * 255) << 24 | 0xCC55FF;
				line(poseStack, lines, p.add(-0.4, 0, 0), p.add(0.4, 0, 0), color);
				line(poseStack, lines, p.add(0, -0.4, 0), p.add(0, 0.4, 0), color);
				line(poseStack, lines, p.add(0, 0, -0.4), p.add(0, 0, 0.4), color);
			}

			poseStack.popPose();
			buffers.endBatch(RenderType.lines());

			drawText(poseStack, buffers, snapshot, drifter, camera, partialTick);
		}

		buffers.endBatch();
	}

	private static void drawCone(PoseStack poseStack, VertexConsumer lines, Entity drifter, float partialTick) {
		Vec3 eye = drifter.getEyePosition(partialTick);
		float yaw = drifter.getViewYRot(partialTick);
		float pitch = drifter.getViewXRot(partialTick);
		// Its screen's four corners (a flat 16:9 picture at its FOV, the same test its eyes use).
		float[][] corners = {{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};
		Vec3[] ends = new Vec3[corners.length];
		for (int i = 0; i < corners.length; i++) {
			ends[i] = eye.add(Senses.screenDirection(yaw, pitch, corners[i][0], corners[i][1]).scale(CONE_LENGTH));
			line(poseStack, lines, eye, ends[i], 0xCCFFFFFF);
		}

		for (int i = 0; i < ends.length; i++) {
			line(poseStack, lines, ends[i], ends[(i + 1) % ends.length], 0xCCFFFFFF);
		}

		line(poseStack, lines, eye, eye.add(Vec3.directionFromRotation(pitch, yaw).scale(CONE_LENGTH + 1.0F)), 0xFFFFFF55);
		line(poseStack, lines, eye, eye.add(Vec3.directionFromRotation(0.0F, yaw - Senses.PERIPHERAL_HALF_YAW).scale(CONE_LENGTH * 0.6F)), 0x66AAAAAA);
		line(poseStack, lines, eye, eye.add(Vec3.directionFromRotation(0.0F, yaw + Senses.PERIPHERAL_HALF_YAW).scale(CONE_LENGTH * 0.6F)), 0x66AAAAAA);
	}

	private static void drawText(PoseStack poseStack, MultiBufferSource buffers, DebugSnapshotPayload snapshot, Entity drifter, Vec3 camera, float partialTick) {
		Vec3 top = drifter.getPosition(partialTick).add(0.0, drifter.getBbHeight() + 0.6, 0.0);
		List<String> lines = snapshot.lines();
		for (int i = 0; i < lines.size(); i++) {
			double y = top.y + (lines.size() - i) * 0.25;
			int color = i == 0 ? 0xFFFFFF55 : lines.get(i).startsWith(">") ? 0xFFBBBBBB : 0xFFFFFFFF;
			DebugRenderer.renderFloatingText(poseStack, buffers, lines.get(i), top.x, y, top.z, color, 0.02F, true, 0.0F, true);
		}

		for (DebugSnapshotPayload.TrackedMark mark : snapshot.tracked()) {
			Vec3 at = mark.lastSeen().add(0.0, 2.1, 0.0);
			String text = String.format(Locale.ROOT, "%s %.2f%s", mark.label(), mark.awareness(), mark.visible() ? "" : " (last seen)");
			DebugRenderer.renderFloatingText(poseStack, buffers, text, at.x, at.y, at.z, awarenessColor(mark.awareness()) | 0xFF000000, 0.02F, true, 0.0F, true);
		}

		for (DebugSnapshotPayload.MemoryMark memory : snapshot.memories()) {
			if (memory.pos().distanceTo(camera) <= LABEL_DISTANCE) {
				String text = String.format(Locale.ROOT, "%s %.2f", memory.label(), memory.confidence());
				DebugRenderer.renderFloatingText(poseStack, buffers, text, memory.pos().x, memory.pos().y + 0.7, memory.pos().z,
					kindColor(memory.kind()) | 0xFF000000, 0.015F, true, 0.0F, false);
			}
		}

		for (DebugSnapshotPayload.SoundMark sound : snapshot.sounds()) {
			if (sound.pos().distanceTo(camera) <= LABEL_DISTANCE * 2) {
				DebugRenderer.renderFloatingText(poseStack, buffers, sound.kind() + " " + sound.age() / 20 + "s", sound.pos().x, sound.pos().y + 0.6,
					sound.pos().z, 0xFFCC55FF, 0.015F, true, 0.0F, true);
			}
		}
	}

	private static void line(PoseStack poseStack, VertexConsumer lines, Vec3 from, Vec3 to, int argb) {
		PoseStack.Pose pose = poseStack.last();
		Vec3 normal = to.subtract(from).normalize();
		int a = argb >>> 24;
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		lines.addVertex(pose, (float)from.x, (float)from.y, (float)from.z).setColor(r, g, b, a).setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
		lines.addVertex(pose, (float)to.x, (float)to.y, (float)to.z).setColor(r, g, b, a).setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
	}

	/** Path colours by move: walking white, up/down light blue, bridge blue, pillar cyan, climb green, swim aqua, jumps orange. */
	private static int moveColor(int ordinal) {
		MoveType type = MoveType.values()[Mth.clamp(ordinal, 0, MoveType.values().length - 1)];
		return switch (type) {
			case BRIDGE -> 0xFF3355FF;
			case PILLAR -> 0xFF33FFFF;
			case JUMP_UP, STEP_DOWN -> 0xFF99CCFF;
			case CLIMB_UP, CLIMB_DOWN -> 0xFF66FF66;
			case SWIM, SWIM_UP, SWIM_DOWN -> 0xFF33CCCC;
			case PARKOUR -> 0xFFFF9933;
			default -> 0xFFFFFFFF;
		};
	}

	private static int awarenessColor(float awareness) {
		if (awareness >= 1.0F) {
			return 0xFF3333;
		} else if (awareness >= 0.6F) {
			return 0xFF9933;
		} else if (awareness >= 0.3F) {
			return 0xFFFF33;
		}

		return 0x999999;
	}

	private static int kindColor(int ordinal) {
		MemoryKind kind = MemoryKind.values()[Mth.clamp(ordinal, 0, MemoryKind.values().length - 1)];
		return switch (kind) {
			case ORE -> 0x33DDFF;
			case STATION -> 0x33FF66;
			case STORAGE -> 0xCC8833;
			case DANGER -> 0xFF3333;
			case RESOURCE -> 0x99FF33;
			case STRUCTURE -> 0xFF66FF;
			case PLAYER -> 0xFFFFFF;
			case ITEM -> 0xFFFF99;
			case MINED -> 0x888888;
			case HURT -> 0xFF7777;
			case MOB -> 0xFFAA55;
			case LANDMARK -> 0x66AAFF;
		};
	}

	private static float red(int rgb) {
		return (rgb >> 16 & 0xFF) / 255.0F;
	}

	private static float green(int rgb) {
		return (rgb >> 8 & 0xFF) / 255.0F;
	}

	private static float blue(int rgb) {
		return (rgb & 0xFF) / 255.0F;
	}
}
