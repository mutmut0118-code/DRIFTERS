package com.dinglebob.drifter.client.render;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.client.mixin.LevelRendererAccessor;
import com.dinglebob.drifter.entity.DrifterEntity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Touch'n Motion on Drifters (the user, 2026-10-30: "i would love it if drifters were to be compatible with this"). The
 * addon (an EMF Compat extension, a mod of its own) adds to an EMF player model: feet that stand on the ground under them,
 * a hand on the wall, the torso leaning with the motion, hands to doors, chests and the blocks used, mining swings onto the
 * block, a look at creatures nearby. It animates players only: every part of it takes a client player, finds players by
 * id and UUID in the client world, and its EMF hook leaves every other creature alone.
 *
 * <p>So each Drifter it would animate gets a {@link DrifterStandIn}: a player in the client world that is never drawn
 * and copies the Drifter, and the addon is handed the stand-in where it takes a player. Before the Drifter's model is posed
 * ({@code ModelSpace.before}, the addon's own step at that point for a player, which works out what the hands and feet go
 * to), and in EMF's animation hook ({@link DrifterTouchNMotionHook}: the addon's hook, given the Drifter's animation state
 * with the stand-in as its creature), which puts the addon's poses on the Drifter's model and its armour. The Drifter's
 * block cracks are the stand-in's too (the addon tells a player's mining by them).</p>
 *
 * <p>Everything of the addon through reflection: without it (or without EMF), with the setting off, or if it doesn't
 * match, no stand-ins are made and Drifters look as before. Not for a Drifter riding (the stand-in isn't on the mount),
 * nor through a Better Combat swing or a roll (those pose the whole body).</p>
 */
public final class DrifterTouchNMotion {
	private DrifterTouchNMotion() {
	}

	/** Drifters further than this from the camera get none (the addon's work is per creature, every frame). */
	private static final double RANGE = 64.0;
	/** A stand-in goes after this many ticks with its Drifter undrawn. */
	private static final int KEEP_TICKS = 40;

	private static final Map<Integer, DrifterStandIn> STAND_INS = new HashMap<>();
	/** Drifters drawn without a stand-in yet: theirs is made after the tick. */
	private static final Set<DrifterEntity> WANTED = new HashSet<>();
	private static final Consumer<AbstractClientPlayer> NOTHING = player -> {
	};
	private static boolean checked;
	private static boolean ready;
	private static Method before;
	private static int ticks;
	/** The stand-in of the Drifter whose model is being posed and drawn right now (null: none). */
	private static DrifterStandIn posing;

	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded("touch_n_motion") || !FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			return;
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
	}

	/** The addon is there and matched (looked up the first time, on the render thread, after every mod has started). */
	private static boolean active() {
		if (!checked) {
			checked = true;
			if (FabricLoader.getInstance().isModLoaded("touch_n_motion") && FabricLoader.getInstance().isModLoaded("entity_model_features")) {
				try {
					before = Class.forName("strm.touchnmotion.ModelSpace").getMethod("before", LivingEntity.class, PoseStack.class, float.class,
						Consumer.class);
					DrifterTouchNMotionHook.register();
					ready = true;
					Drifter.LOGGER.info("[Drifter] Touch'n Motion found: its animations play on Drifters too");
				} catch (Exception | LinkageError e) {
					Drifter.LOGGER.warn("[Drifter] Touch'n Motion didn't match ({}); Drifters go without its animations", e.toString());
				}
			}
		}

		return ready;
	}

	private static boolean enabled() {
		return DrifterConfig.touchNMotion() && active();
	}

	private static void disable(Throwable e) {
		ready = false;
		posing = null;
		// The stand-ins go at the next tick (never while the world's creatures are being drawn).
		Drifter.LOGGER.warn("[Drifter] Touch'n Motion on Drifters failed ({}); Drifters go without its animations from now on", e.toString());
	}

	/**
	 * Right before the Drifter's model is posed (the renderer's setupAnim; a mixin): the stand-in brought up to date and
	 * handed to the addon, on the pose stack as it is there, the model's own space.
	 */
	public static void beforeAnimating(DrifterEntity drifter, PoseStack stack, float partialTick) {
		posing = null;
		if (!enabled() || drifter.isPassenger() || drifter.isDeadOrDying() || drifter.rollProgress(partialTick) >= 0.0F
			|| DrifterSwingAnimations.swingPlaying(drifter)) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(drifter.position()) > RANGE * RANGE) {
			return;
		}

		// Made at the next tick, not now: nothing is put in the world while its creatures are being drawn.
		DrifterStandIn standIn = STAND_INS.get(drifter.getId());
		if (standIn == null || standIn.drifter != drifter || standIn.isRemoved() || standIn.level() != drifter.level()) {
			WANTED.add(drifter);
			return;
		}

		standIn.drawnAt = ticks;
		try {
			before.invoke(null, standIn, stack, partialTick, NOTHING);
			posing = standIn;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			disable(e);
		}
	}

	/** The Drifter's draw is over. */
	public static void drawn() {
		posing = null;
	}

	/** EMF's hook: the stand-in for this creature, if it is the Drifter being posed now and the addon had its turn. */
	static DrifterStandIn posing(Object entity) {
		DrifterStandIn standIn = posing;
		return standIn != null && standIn.drifter == entity ? standIn : null;
	}

	/** EMF's hook failed: no more of it. */
	static void hookFailed(Throwable e) {
		disable(e);
	}

	/**
	 * This Drifter's stand-in, made (and put in the client world) the first time; null without the addon. Not while the
	 * world's creatures are being drawn. A fishing Drifter's bobber is the stand-in's too (DrifterFishers, as the bobber
	 * comes), so the addon plays its cast and haul.
	 */
	public static DrifterStandIn standIn(DrifterEntity drifter) {
		if (!enabled() || !(drifter.level() instanceof ClientLevel level)) {
			return null;
		}

		DrifterStandIn standIn = STAND_INS.get(drifter.getId());
		if (standIn != null && (standIn.drifter != drifter || standIn.level() != level || standIn.isRemoved())) {
			remove(standIn);
			standIn = null;
		}

		if (standIn == null) {
			standIn = new DrifterStandIn(level, drifter);
			standIn.drawnAt = ticks;
			level.addEntity(standIn);
			STAND_INS.put(drifter.getId(), standIn);
		}

		return standIn;
	}

	/** After the world's tick: every stand-in a copy of its Drifter again, or gone with it. */
	private static void tick(Minecraft minecraft) {
		ticks++;
		if (!WANTED.isEmpty()) {
			for (DrifterEntity drifter : WANTED) {
				if (ready && !drifter.isRemoved() && drifter.level() == minecraft.level) {
					standIn(drifter);
				}
			}

			WANTED.clear();
		}

		if (STAND_INS.isEmpty()) {
			return;
		}

		Int2ObjectMap<BlockDestructionProgress> cracks = minecraft.levelRenderer == null ? null
			: ((LevelRendererAccessor)minecraft.levelRenderer).drifter$destroyingBlocks();
		Iterator<DrifterStandIn> it = STAND_INS.values().iterator();
		while (it.hasNext()) {
			DrifterStandIn standIn = it.next();
			DrifterEntity drifter = standIn.drifter;
			boolean fishing = standIn.fishing != null && !standIn.fishing.isRemoved();
			if (!ready || drifter.isRemoved() || drifter.level() != minecraft.level || standIn.level() != minecraft.level
				|| standIn.isRemoved() || ticks - standIn.drawnAt > KEEP_TICKS && !fishing) {
				it.remove();
				discard(standIn, cracks);
				continue;
			}

			standIn.copyTick();
			// The block it breaks, under the stand-in's id as well as its own.
			if (cracks != null) {
				BlockDestructionProgress crack = cracks.get(drifter.getId());
				if (crack != null) {
					cracks.put(standIn.getId(), crack);
				} else {
					cracks.remove(standIn.getId());
				}
			}
		}
	}

	private static void remove(DrifterStandIn standIn) {
		STAND_INS.remove(standIn.drifter.getId(), standIn);
		Minecraft minecraft = Minecraft.getInstance();
		discard(standIn, minecraft.levelRenderer == null ? null : ((LevelRendererAccessor)minecraft.levelRenderer).drifter$destroyingBlocks());
	}

	private static void discard(DrifterStandIn standIn, Int2ObjectMap<BlockDestructionProgress> cracks) {
		if (cracks != null) {
			cracks.remove(standIn.getId());
		}

		if (!standIn.isRemoved() && standIn.level() instanceof ClientLevel level && level.getEntity(standIn.getId()) == standIn) {
			level.removeEntity(standIn.getId(), Entity.RemovalReason.DISCARDED);
		}
	}
}
