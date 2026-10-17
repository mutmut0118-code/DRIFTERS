package com.dinglebob.drifter.client.render;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Better Combat's swing animations on a Drifter (DESIGN.md section 21). Better Combat plays them through the player
 * animation library, which only animates real players, so a Drifter plays them itself: the same animation files, run by
 * the same library (its keyframe player, speed and mirror modifiers), applied to the Drifter's own model parts. With EMF
 * (Fresh Animations and the like), EMF's own animations on the arms and body pause for the swing, as the EMF Better
 * Combat bridge does for players. Everything through reflection: without the library, or if it changes, Drifters just
 * show the vanilla arm swing.
 */
public final class DrifterSwingAnimations {
	private DrifterSwingAnimations() {
	}

	private static final class State {
		String seen;
		@Nullable Object applier;
		int lastTick;
		boolean emfPaused;
	}

	private static final Map<DrifterEntity, State> STATES = new WeakHashMap<>();
	/** Test runs only (DevClientTest): what the swing animations do, in the log. */
	private static final boolean TRACE = System.getenv("DRIFTER_CLIENT_TEST") != null;
	private static int traced;
	private static boolean ready;
	private static boolean failed;
	private static boolean emfReady;
	/**
	 * EMF Compat Core (with its Better Combat bridge): the way players' Better Combat swings reach an EMF model (Fresh
	 * Animations, FA+Player). A Drifter's swing goes the same way: the arm poses handed to its pose manager each frame,
	 * which blends them into the EMF model (its own arm parts included), instead of pausing EMF's animations.
	 */
	private static boolean emfCompat;
	private static Method clearPoses;
	private static Method savePoses;
	private static Constructor<?> poseSnapshot;
	private static Constructor<?> poseSnapshotFlag;
	private static Method markAttackActive;
	private static Method tickCooldown;
	private static Method bodyFollow;
	private static Method attackLegs;
	private static final String EMF_SOURCE = "better_combat";

	private static Method getAnimation;
	private static Class<?> keyframeAnimation;
	private static Method mutableCopy;
	private static Field endTick;
	private static Field torso;
	private static Field head;
	private static Method fullyEnablePart;
	private static Field pitch;
	private static Method setEnabled;
	private static Method build;
	private static Constructor<?> keyframePlayer;
	private static Constructor<?> speedModifier;
	private static Constructor<?> mirrorModifier;
	private static Constructor<?> modifierLayer;
	private static Method addModifier;
	private static Method setAnimation;
	private static Constructor<?> animationStack;
	private static Method addAnimLayer;
	private static Constructor<?> animationApplier;
	private static Method applierTick;
	private static Method applierActive;
	private static Method setTickDelta;
	private static Method updatePart;
	/** The whole body's turn, lean and lift in a swing (the library does this for players in their renderer). */
	private static Method get3DTransform;
	private static Object scaleType;
	private static Object positionType;
	private static Object rotationType;
	private static Constructor<?> vec3f;
	private static Object vec3fZero;
	private static Method vecX;
	private static Method vecY;
	private static Method vecZ;
	/** The model's own handle on the running animation: with bendy-lib installed it draws the elbow and knee bends from it. */
	private static Method getEmoteSupplier;
	private static Method supplierSet;
	private static Method emfEntityOf;
	private static Method emfPause;
	private static Method emfResume;

	private static boolean active() {
		if (ready || failed) {
			return ready;
		}

		failed = true;
		if (!FabricLoader.getInstance().isModLoaded("bettercombat") || !FabricLoader.getInstance().isModLoaded("playeranimator")) {
			return false;
		}

		try {
			String api = "dev.kosmx.playerAnim.";
			getAnimation = Class.forName(api + "minecraftApi.PlayerAnimationRegistry").getMethod("getAnimation", ResourceLocation.class);
			keyframeAnimation = Class.forName(api + "core.data.KeyframeAnimation");
			mutableCopy = keyframeAnimation.getMethod("mutableCopy");
			endTick = keyframeAnimation.getField("endTick");
			Class<?> builder = Class.forName(api + "core.data.KeyframeAnimation$AnimationBuilder");
			torso = builder.getField("torso");
			head = builder.getField("head");
			build = builder.getMethod("build");
			Class<?> collection = Class.forName(api + "core.data.KeyframeAnimation$StateCollection");
			fullyEnablePart = collection.getMethod("fullyEnablePart", boolean.class);
			pitch = collection.getField("pitch");
			setEnabled = Class.forName(api + "core.data.KeyframeAnimation$StateCollection$State").getMethod("setEnabled", boolean.class);
			keyframePlayer = Class.forName(api + "api.layered.KeyframeAnimationPlayer").getConstructor(keyframeAnimation, int.class);
			speedModifier = Class.forName(api + "api.layered.modifier.SpeedModifier").getConstructor(float.class);
			mirrorModifier = Class.forName(api + "api.layered.modifier.MirrorModifier").getConstructor(boolean.class);
			Class<?> animation = Class.forName(api + "api.layered.IAnimation");
			Class<?> abstractModifier = Class.forName(api + "api.layered.modifier.AbstractModifier");
			// The empty layer, then each modifier, then the animation: that way the library links them (its varargs
			// constructor doesn't, and an unlinked modifier says it's done: found in a client test, every swing ended at once).
			Class<?> layer = Class.forName(api + "api.layered.ModifierLayer");
			modifierLayer = layer.getConstructor();
			addModifier = layer.getMethod("addModifier", abstractModifier, int.class);
			setAnimation = layer.getMethod("setAnimation", animation);
			Class<?> stack = Class.forName(api + "api.layered.AnimationStack");
			animationStack = stack.getConstructor();
			addAnimLayer = stack.getMethod("addAnimLayer", int.class, animation);
			Class<?> applier = Class.forName(api + "impl.animation.AnimationApplier");
			animationApplier = applier.getConstructor(animation);
			applierTick = applier.getMethod("tick");
			applierActive = applier.getMethod("isActive");
			setTickDelta = applier.getMethod("setTickDelta", float.class);
			updatePart = applier.getMethod("updatePart", String.class, ModelPart.class);
			Class<?> transform = Class.forName(api + "api.TransformType");
			Class<?> vec = Class.forName(api + "core.util.Vec3f");
			get3DTransform = applier.getMethod("get3DTransform", String.class, transform, vec);
			scaleType = transform.getField("SCALE").get(null);
			positionType = transform.getField("POSITION").get(null);
			rotationType = transform.getField("ROTATION").get(null);
			vec3f = vec.getConstructor(float.class, float.class, float.class);
			vec3fZero = vec.getField("ZERO").get(null);
			vecX = vec.getMethod("getX");
			vecY = vec.getMethod("getY");
			vecZ = vec.getMethod("getZ");
			try {
				getEmoteSupplier = Class.forName(api + "impl.IMutableModel").getMethod("getEmoteSupplier");
				supplierSet = Class.forName(api + "core.util.SetableSupplier").getMethod("set", Object.class);
			} catch (ReflectiveOperationException e) {
				// No bends then (an older or newer library): the rest still works.
				getEmoteSupplier = null;
			}

			ready = true;
			failed = false;
			Drifter.LOGGER.info("[Drifter] Drifters play Better Combat's swing animations");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			Drifter.LOGGER.warn("[Drifter] Better Combat's animations didn't match ({}); Drifters show the vanilla swing", e.toString());
			return false;
		}

		if (FabricLoader.getInstance().isModLoaded("emf_compat_core")) {
			try {
				Class<?> manager = Class.forName("strm.emfcompat.core.PoseManager");
				Class<?> snapshot = Class.forName("strm.emfcompat.core.PoseSnapshot");
				clearPoses = manager.getMethod("clearPoses", java.util.UUID.class, String.class);
				savePoses = manager.getMethod("savePoses", java.util.UUID.class, String.class, snapshot, snapshot, Map.class, org.joml.Vector3f.class);
				poseSnapshot = snapshot.getConstructor(ModelPart.class);
				poseSnapshotFlag = snapshot.getConstructor(ModelPart.class, boolean.class);
				if (FabricLoader.getInstance().isModLoaded("emf_compat_better_combat")) {
					Class<?> pause = Class.forName("strm.emfcompat.bettercombat.compat.AttackPauseOverride");
					markAttackActive = pause.getMethod("markAttackActive", java.util.UUID.class);
					tickCooldown = pause.getMethod("tickCooldown", java.util.UUID.class);
					Class<?> client = Class.forName("strm.emfcompat.bettercombat.EMFCompatBetterCombatClient");
					bodyFollow = client.getMethod("isBodyFollow");
					attackLegs = client.getMethod("isAttackLegs");
				}

				emfCompat = true;
				Drifter.LOGGER.info("[Drifter] EMF Compat found: Drifters' swings go through it, as players' do");
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				emfCompat = false;
				Drifter.LOGGER.warn("[Drifter] EMF Compat didn't match ({}); swings pause EMF's own arm animations instead", e.toString());
			}
		}

		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
			try {
				Class<?> emf = Class.forName("traben.entity_model_features.EMFAnimationApi");
				for (Method method : emf.getMethods()) {
					Class<?>[] params = method.getParameterTypes();
					if (method.getName().equals("emfEntityOf") && params.length == 1 && params[0].isAssignableFrom(Entity.class)) {
						emfEntityOf = method;
					} else if (method.getName().equals("pauseCustomAnimationsForThesePartsOfEntity") && params.length == 2) {
						emfPause = method;
					} else if (method.getName().equals("resumeAllCustomAnimationsForEntity") && params.length == 1) {
						emfResume = method;
					}
				}

				emfReady = emfEntityOf != null && emfPause != null && emfResume != null;
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				emfReady = false;
			}

			if (!emfReady) {
				Drifter.LOGGER.warn("[Drifter] EMF's animation API didn't match; swing animations may fight EMF's own");
			}
		}

		return true;
	}

	/** After the model's own pose (setupAnim): the swing animation on top, if one is playing. */
	private static int applied;

	public static void apply(PlayerModel<DrifterEntity> model, DrifterEntity drifter, float ageInTicks, float walking) {
		if (TRACE && applied++ % 200 == 0) {
			Drifter.LOGGER.info("[Drifter test] apply called ({} times)", applied);
		}

		if (!active()) {
			return;
		}

		State state = STATES.computeIfAbsent(drifter, d -> {
			State fresh = new State();
			// A swing from before it came into view isn't played.
			fresh.seen = d.swingData();
			return fresh;
		});
		try {
			String data = drifter.swingData();
			if (!data.equals(state.seen)) {
				if (TRACE) {
					Drifter.LOGGER.info("[Drifter test] swing data {}", data);
				}

				state.seen = data;
				start(state, data, drifter);
			}

			if (state.applier == null) {
				return;
			}

			for (int i = 0; i < 20 && state.lastTick < drifter.tickCount; i++) {
				applierTick.invoke(state.applier);
				state.lastTick++;
			}

			state.lastTick = drifter.tickCount;
			if (!(boolean)applierActive.invoke(state.applier)) {
				if (TRACE) {
					Drifter.LOGGER.info("[Drifter test] swing animation ended");
				}

				stop(state, drifter);
				return;
			}

			setTickDelta.invoke(state.applier, ageInTicks - drifter.tickCount);
			float before = model.rightArm.xRot;
			updatePart.invoke(state.applier, "head", model.head);
			updatePart.invoke(state.applier, "torso", model.body);
			updatePart.invoke(state.applier, "rightArm", model.rightArm);
			updatePart.invoke(state.applier, "leftArm", model.leftArm);
			updatePart.invoke(state.applier, "rightLeg", model.rightLeg);
			updatePart.invoke(state.applier, "leftLeg", model.leftLeg);
			// The second skin layer follows (PlayerModel#setupAnim's copies, again after the animation).
			model.hat.copyFrom(model.head);
			model.jacket.copyFrom(model.body);
			model.rightSleeve.copyFrom(model.rightArm);
			model.leftSleeve.copyFrom(model.leftArm);
			model.rightPants.copyFrom(model.rightLeg);
			model.leftPants.copyFrom(model.leftLeg);
			// The model draws the bends (elbows, knees, the waist) from the running animation, as for a player.
			if (getEmoteSupplier != null && getEmoteSupplier.getDeclaringClass().isInstance(model)) {
				supplierSet.invoke(getEmoteSupplier.invoke(model), state.applier);
			}

			if (TRACE && traced++ % 10 == 0) {
				Drifter.LOGGER.info("[Drifter test] swing frame: right arm xRot {} -> {}, yRot {}, body yRot {}", before, model.rightArm.xRot,
					model.rightArm.yRot, model.body.yRot);
			}
			if (emfCompat) {
				handToEmfCompat(model, drifter, walking);
			} else if (emfReady && !state.emfPaused) {
				Object emfEntity = emfEntityOf.invoke(null, drifter);
				if (emfEntity != null) {
					emfPause.invoke(null, emfEntity, new ModelPart[] {model.body, model.jacket, model.rightArm, model.leftArm, model.rightSleeve,
						model.leftSleeve});
					state.emfPaused = true;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			ready = false;
			failed = true;
			Drifter.LOGGER.warn("[Drifter] Swing animation failed ({}); Drifters show the vanilla swing from now on", e.toString());
		}
	}

	/**
	 * In the renderer's rotations (before the model is drawn): the swing's whole-body movement, the twist into a slash and
	 * the lean into a stab or slam, as the library does for a player (PlayerRenderer's setupRotations: scale, then a move,
	 * then turns round a point 0.7 up).
	 */
	public static void applyBody(DrifterEntity drifter, com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick) {
		if (!ready) {
			return;
		}

		State state = STATES.get(drifter);
		if (state == null || state.applier == null) {
			return;
		}

		try {
			setTickDelta.invoke(state.applier, partialTick);
			Object scale = get3DTransform.invoke(state.applier, "body", scaleType, vec3f.newInstance(1.0F, 1.0F, 1.0F));
			poseStack.scale(f(vecX, scale), f(vecY, scale), f(vecZ, scale));
			Object position = get3DTransform.invoke(state.applier, "body", positionType, vec3fZero);
			poseStack.translate(f(vecX, position), f(vecY, position) + 0.7, f(vecZ, position));
			Object rotation = get3DTransform.invoke(state.applier, "body", rotationType, vec3fZero);
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(f(vecZ, rotation)));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotation(f(vecY, rotation)));
			poseStack.mulPose(com.mojang.math.Axis.XP.rotation(f(vecX, rotation)));
			poseStack.translate(0.0, -0.7, 0.0);
		} catch (ReflectiveOperationException | RuntimeException e) {
			ready = false;
			failed = true;
			Drifter.LOGGER.warn("[Drifter] Swing body movement failed ({}); Drifters show the vanilla swing from now on", e.toString());
		}
	}

	private static float f(Method getter, Object vec) throws ReflectiveOperationException {
		return ((Number)getter.invoke(vec)).floatValue();
	}

	private static void start(State state, String data, DrifterEntity drifter) throws ReflectiveOperationException {
		String[] parts = data.split(";");
		if (parts.length < 4) {
			return;
		}

		Object playable = getAnimation.invoke(null, ResourceLocation.tryParse(parts[1]));
		if (!keyframeAnimation.isInstance(playable)) {
			return;
		}

		// As Better Combat sets one up for a player: the whole torso moves, the head keeps looking where it looks.
		Object builder = mutableCopy.invoke(playable);
		fullyEnablePart.invoke(torso.get(builder), true);
		setEnabled.invoke(pitch.get(head.get(builder)), false);
		Object animation = build.invoke(builder);
		int ticks = Math.max(1, Integer.parseInt(parts[2]));
		float speed = Math.max(0.2F, endTick.getInt(animation) / (float)ticks);
		Object layer = modifierLayer.newInstance();
		addModifier.invoke(layer, speedModifier.newInstance(speed), 0);
		addModifier.invoke(layer, mirrorModifier.newInstance(Boolean.parseBoolean(parts[3])), 1);
		setAnimation.invoke(layer, keyframePlayer.newInstance(animation, 0));
		Object stack = animationStack.newInstance();
		addAnimLayer.invoke(stack, 1000, layer);
		state.applier = animationApplier.newInstance(stack);
		state.lastTick = drifter.tickCount;
		if (TRACE) {
			Drifter.LOGGER.info("[Drifter test] swing animation {} ({} ticks, speed {}, mirrored {})", parts[1], ticks, speed, parts[3]);
		}
	}

	/**
	 * This frame's swing pose to EMF Compat, the way its Better Combat bridge does for a player (PlayerModelMixin): its
	 * attack marked active (EMF's own arm animation steps aside), both arms, the legs when it's standing still, and the
	 * body's turn when the bridge's "body follows arms" is on.
	 */
	private static void handToEmfCompat(PlayerModel<DrifterEntity> model, DrifterEntity drifter, float walking) throws ReflectiveOperationException {
		java.util.UUID id = drifter.getUUID();
		clearPoses.invoke(null, id, EMF_SOURCE);
		if (markAttackActive != null) {
			tickCooldown.invoke(null, id);
			markAttackActive.invoke(null, id);
		}

		org.joml.Vector3f body = bodyFollow == null || (boolean)bodyFollow.invoke(null)
			? new org.joml.Vector3f(model.body.xRot, model.body.yRot, model.body.zRot) : null;
		Map<String, Object> legs = null;
		if ((attackLegs == null || (boolean)attackLegs.invoke(null)) && walking < 0.15F) {
			legs = new java.util.HashMap<>();
			legs.put("left_leg", poseSnapshotFlag.newInstance(model.leftLeg, true));
			legs.put("right_leg", poseSnapshotFlag.newInstance(model.rightLeg, true));
		}

		savePoses.invoke(null, id, EMF_SOURCE, poseSnapshot.newInstance(model.leftArm), poseSnapshot.newInstance(model.rightArm), legs, body);
	}

	private static void stop(State state, DrifterEntity drifter) throws ReflectiveOperationException {
		state.applier = null;
		if (emfCompat) {
			clearPoses.invoke(null, drifter.getUUID(), EMF_SOURCE);
		}

		if (state.emfPaused) {
			state.emfPaused = false;
			Object emfEntity = emfEntityOf.invoke(null, drifter);
			if (emfEntity != null) {
				emfResume.invoke(null, emfEntity);
			}
		}
	}
}
