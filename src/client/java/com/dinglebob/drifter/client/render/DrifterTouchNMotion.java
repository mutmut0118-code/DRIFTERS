package com.dinglebob.drifter.client.render;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import com.mojang.blaze3d.vertex.PoseStack;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.client.mixin.EntityPassengersAccessor;
import com.dinglebob.drifter.client.mixin.LevelRendererAccessor;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.hands.HandSignals;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
 * <p>The rest, as for another player on a server with the addon (the user, 2026-10-31: "do all three"). Its hands: what
 * the server says the Drifter does ({@link HandSignals}: the block or creature it uses, the block it breaks, the chest it
 * has open; feeding, milking, shearing, an armour stand, a seed) is handed to the addon each tick as its own word from a
 * player's game ({@code ClientHands.receive}, under the stand-in's id). Riding: the stand-in rides what the Drifter rides
 * (DrifterStandIn#getVehicle), and takes its seat among the vehicle's riders while the addon looks (who rows a boat, which
 * seat), with the horse-riding lift and pose as for a player. Picking things up: the "into the pocket" gesture.</p>
 *
 * <p>Everything of the addon through reflection: without it (or without EMF), with the setting off, or if it doesn't
 * match, no stand-ins are made and Drifters look as before. Not through a Better Combat swing or a roll (those pose the
 * whole body). The extras (hands, riding, pockets) go on their own if they don't match.</p>
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
	/** The extras: the addon's hands from another player's game, its pocket, its horse riding (and EMF Compat Core's poses). */
	private static boolean extras;
	private static Constructor<?> handsState;
	private static Constructor<?> handsAct;
	private static Method receiveState;
	private static Method receiveAct;
	private static Method pocket;
	private static Method horsePre;
	private static Method horsePost;
	private static Method footPre;
	private static Method footPost;
	private static Method ridingApply;
	private static Method ridingHead;
	private static Method horseSync;
	private static Method ridingAnimation;
	private static Method savePoses;
	private static Method clearPoses;
	private static Method armPoseExcept;
	private static Constructor<?> poseSnapshot;
	private static String ridingSource = "horse_riding";
	/** What the server last said each Drifter's hands are at, by its id. */
	private static final Map<Integer, HandSignals.HandsPayload> HANDS = new HashMap<>();
	/** Drifters with the horse-riding pose kept for them (EMF Compat Core's poses, by the Drifter's UUID). */
	private static final Set<UUID> RIDING_POSED = new HashSet<>();
	/** Test runs only (DevClientTest): the hands and acts handed on, in the log. */
	private static final boolean TRACE = System.getenv("DRIFTER_CLIENT_TEST") != null;
	private static final Map<Integer, Integer> TRACED_FLAGS = new HashMap<>();
	private static int ticks;
	/** The stand-in of the Drifter whose model is being posed and drawn right now (null: none). */
	private static DrifterStandIn posing;

	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded("touch_n_motion") || !FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			return;
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
		ClientPlayNetworking.registerGlobalReceiver(HandSignals.HandsPayload.TYPE, (payload, context) -> HANDS.put(payload.drifter(), payload));
		ClientPlayNetworking.registerGlobalReceiver(HandSignals.ActPayload.TYPE, (payload, context) -> act(payload));
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

				if (ready) {
					try {
						Class<?> state = Class.forName("strm.touchnmotion.net.HandsState");
						Class<?> act = Class.forName("strm.touchnmotion.net.HandsAct");
						handsState = state.getConstructor(int.class, int.class, BlockPos.class, int.class, double.class, double.class, double.class,
							int.class, BlockPos.class, BlockPos.class, int.class, int.class, int.class, int.class, BlockPos.class, BlockPos.class);
						handsAct = act.getConstructor(int.class, int.class, int.class, double.class, double.class, double.class, boolean.class);
						Class<?> hands = Class.forName("strm.touchnmotion.net.ClientHands");
						receiveState = hands.getMethod("receive", state);
						receiveAct = hands.getMethod("receive", act);
						pocket = Class.forName("strm.touchnmotion.pocket.PocketStash").getMethod("picked", AbstractClientPlayer.class);
						Class<?> horse = Class.forName("strm.touchnmotion.horsesync.ClientEventHandler");
						horsePre = horse.getMethod("onRenderPlayerPre", Entity.class, PoseStack.class);
						horsePost = horse.getMethod("onRenderPlayerPost", Entity.class, PoseStack.class);
						Class<?> feet = Class.forName("strm.touchnmotion.footgrounding.FootGroundingFeature");
						footPre = feet.getMethod("onRenderPlayerPre", Player.class, PoseStack.class, float.class);
						footPost = feet.getMethod("onRenderPlayerPost", PoseStack.class);
						Class<?> riding = Class.forName("strm.touchnmotion.horsesync.RidingPose");
						ridingApply = riding.getMethod("apply", HumanoidModel.class, float.class, float.class, boolean.class);
						ridingHead = riding.getMethod("capturesHead");
						Class<?> sync = Class.forName("strm.touchnmotion.horsesync.HorseSync");
						horseSync = sync.getMethod("isEnabled");
						ridingAnimation = sync.getMethod("isRidingAnimation");
						ridingSource = (String)sync.getField("RIDING_SOURCE").get(null);
						Class<?> poses = Class.forName("strm.emfcompat.core.PoseManager");
						Class<?> snapshot = Class.forName("strm.emfcompat.core.PoseSnapshot");
						savePoses = poses.getMethod("savePoses", UUID.class, String.class, snapshot, snapshot, Map.class);
						clearPoses = poses.getMethod("clearPoses", UUID.class, String.class);
						armPoseExcept = poses.getMethod("hasArmPoseExcept", UUID.class, String.class);
						poseSnapshot = snapshot.getConstructor(ModelPart.class);
						extras = true;
					} catch (Exception | LinkageError e) {
						Drifter.LOGGER.warn("[Drifter] Touch'n Motion's hands and riding didn't match ({}); Drifters get its other animations", e.toString());
					}
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
		extras = false;
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
		if (!enabled() || drifter.isDeadOrDying() || drifter.rollProgress(partialTick) >= 0.0F
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
		Runnable unseat = seat(standIn);
		if (TRACE && !java.util.Objects.equals(TRACED_FLAGS.put(-drifter.getId() - 1, drifter.getVehicle() == null ? 0 : 1), drifter.getVehicle() == null ? 0 : 1)) {
			Drifter.LOGGER.info("[Drifter test] {} rides {} (its stand-in: {}), seated as rider {}", drifter.getName().getString(), drifter.getVehicle(),
				standIn.getVehicle(), standIn.getVehicle() == null ? -1 : standIn.getVehicle().getPassengers().indexOf(standIn));
		}

		try {
			before.invoke(null, standIn, stack, partialTick, NOTHING);
			posing = standIn;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			disable(e);
		} finally {
			unseat.run();
		}
	}

	/**
	 * Before the Drifter is drawn: on a horse, the addon's lift (its rider kept on the saddle of an animated horse), as
	 * for a player before it's drawn. True if it was done (and must be undone after: {@link #drawn}).
	 */
	public static boolean beforeDraw(DrifterEntity drifter, PoseStack stack, float partialTick) {
		DrifterStandIn standIn = extras && enabled() && drifter.getVehicle() instanceof AbstractHorse ? current(drifter) : null;
		if (standIn == null) {
			return false;
		}

		Runnable unseat = seat(standIn);
		try {
			horsePre.invoke(null, standIn, stack);
			footPre.invoke(null, standIn, stack, partialTick);
			return true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			disable(e);
			return false;
		} finally {
			unseat.run();
		}
	}

	/** The Drifter's draw is over (and the horse lift undone, if done). */
	public static void drawn(DrifterEntity drifter, PoseStack stack, boolean onHorse) {
		posing = null;
		DrifterStandIn standIn = onHorse ? STAND_INS.get(drifter.getId()) : null;
		if (standIn == null) {
			return;
		}

		Runnable unseat = seat(standIn);
		try {
			footPost.invoke(null, stack);
			horsePost.invoke(null, standIn, stack);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			disable(e);
		} finally {
			unseat.run();
		}
	}

	/**
	 * Riding a horse: the addon's riding pose on the Drifter's model, kept as EMF Compat Core's poses for the Drifter so
	 * they hold over EMF's animation (as the addon does for a player at the end of the player model's setupAnim).
	 */
	public static void ridingPose(HumanoidModel<?> model, DrifterEntity drifter) {
		if (!extras || !enabled()) {
			return;
		}

		UUID id = drifter.getUUID();
		try {
			if (!(drifter.getVehicle() instanceof AbstractHorse horse) || current(drifter) == null || !(boolean)horseSync.invoke(null)
				|| !(boolean)ridingAnimation.invoke(null)) {
				if (RIDING_POSED.remove(id)) {
					clearPoses.invoke(null, id, ridingSource);
				}

				return;
			}

			// A swing (Better Combat's, through EMF Compat) has the arms: only the legs sit then.
			boolean upperBody = !(boolean)armPoseExcept.invoke(null, id, ridingSource);
			ridingApply.invoke(null, model, horse.walkAnimation.position(), horse.walkAnimation.speed(), upperBody);
			Map<String, Object> parts = new HashMap<>();
			parts.put("left_leg", poseSnapshot.newInstance(model.leftLeg));
			parts.put("right_leg", poseSnapshot.newInstance(model.rightLeg));
			if (upperBody) {
				parts.put("body", poseSnapshot.newInstance(model.body));
				parts.put("left_arm", poseSnapshot.newInstance(model.leftArm));
				parts.put("right_arm", poseSnapshot.newInstance(model.rightArm));
				if ((boolean)ridingHead.invoke(null)) {
					parts.put("head", poseSnapshot.newInstance(model.head));
				}
			}

			savePoses.invoke(null, id, ridingSource, null, null, parts);
			RIDING_POSED.add(id);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			disable(e);
		}
	}

	/** This Drifter's stand-in as it is now, or null (none yet, or no longer its). */
	private static DrifterStandIn current(DrifterEntity drifter) {
		DrifterStandIn standIn = STAND_INS.get(drifter.getId());
		return standIn != null && standIn.drifter == drifter && !standIn.isRemoved() && standIn.level() == drifter.level() ? standIn : null;
	}

	/**
	 * A riding Drifter's stand-in in its seat among the vehicle's riders, for the moment the addon looks (who rows a boat,
	 * which seat is whose); the returned undo puts the Drifter back. Nothing for a Drifter on foot.
	 */
	static Runnable seat(DrifterStandIn standIn) {
		Entity vehicle = standIn.drifter.getVehicle();
		if (vehicle == null) {
			return () -> {
			};
		}

		EntityPassengersAccessor riders = (EntityPassengersAccessor)vehicle;
		ImmutableList<Entity> was = riders.drifter$passengers();
		int seat = was.indexOf(standIn.drifter);
		if (seat < 0) {
			return () -> {
			};
		}

		java.util.List<Entity> swapped = new ArrayList<>(was);
		swapped.set(seat, standIn);
		riders.drifter$setPassengers(ImmutableList.copyOf(swapped));
		return () -> riders.drifter$setPassengers(was);
	}

	/** A Drifter picked something up (the take-item packet): the addon's pocket gesture for its stand-in. */
	public static void pickedUp(DrifterEntity drifter) {
		DrifterStandIn standIn = extras && enabled() ? current(drifter) : null;
		if (TRACE) {
			Drifter.LOGGER.info("[Drifter test] {} picked something up (stand-in {})", drifter.getName().getString(), standIn == null ? "none" : standIn.getId());
		}

		if (standIn != null) {
			try {
				pocket.invoke(null, standIn);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				disable(e);
			}
		}
	}

	/** Something a Drifter did once (fed, milked, sheared, dressed a stand, planted): the addon's own act, from its stand-in. */
	private static void act(HandSignals.ActPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		DrifterStandIn standIn = extras && enabled() && minecraft.level != null && minecraft.level.getEntity(payload.drifter()) instanceof DrifterEntity drifter
			? current(drifter) : null;
		if (TRACE) {
			Drifter.LOGGER.info("[Drifter test] hand act {} on {} by drifter {} (stand-in {})", payload.kind(), payload.entity(), payload.drifter(),
				standIn == null ? "none" : standIn.getId());
		}

		if (standIn != null) {
			try {
				receiveAct.invoke(null, handsAct.newInstance(standIn.getId(), payload.kind(), payload.entity(), payload.x(), payload.y(), payload.z(),
					payload.mainHand()));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				disable(e);
			}
		}
	}

	/**
	 * Each tick, what each Drifter's hands are at, to the addon as its own word from a player's game (kept only while it
	 * goes on being told: so told every tick while busy, and once when idle again). Where the server named no block or
	 * creature, what the stand-in looks at, as a player's crosshair.
	 */
	private static void tellHands() {
		if (HANDS.isEmpty()) {
			return;
		}

		Iterator<HandSignals.HandsPayload> it = HANDS.values().iterator();
		while (it.hasNext()) {
			HandSignals.HandsPayload hands = it.next();
			DrifterStandIn standIn = STAND_INS.get(hands.drifter());
			if (hands.idle()) {
				it.remove();
			}

			if (!extras || standIn == null || standIn.isRemoved()) {
				continue;
			}

			int flags = hands.flags();
			BlockPos block = hands.block();
			int face = hands.face();
			double x = hands.hitX();
			double y = hands.hitY();
			double z = hands.hitZ();
			if ((flags & (HandSignals.BLOCK | HandSignals.ENTITY)) == 0) {
				HitResult hit = standIn.pick(standIn.blockInteractionRange(), 1.0F, false);
				if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
					flags |= HandSignals.BLOCK;
					block = blockHit.getBlockPos();
					face = blockHit.getDirection().get3DDataValue();
					x = hit.getLocation().x;
					y = hit.getLocation().y;
					z = hit.getLocation().z;
				}
			}

			if (TRACE && !java.util.Objects.equals(TRACED_FLAGS.put(hands.drifter(), flags * 4 + (hands.menu() ? 1 : 0)), flags * 4 + (hands.menu() ? 1 : 0))) {
				Drifter.LOGGER.info("[Drifter test] hands of {}: flags {} block {} entity {} menu {} at {} moved {}", standIn.drifter.getName().getString(), flags,
					block, hands.entity(), hands.menu(), hands.menuPos(), hands.actions());
			}

			try {
				receiveState.invoke(null, handsState.newInstance(standIn.getId(), flags, block, face, x, y, z, hands.entity(), null, null, -1,
					hands.menu() ? 1 : 0, hands.actions(), -1, null, hands.menuPos()));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				disable(e);
			}
		}
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
		if (minecraft.level == null) {
			HANDS.clear();
			RIDING_POSED.clear();
		}

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

		tellHands();
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
