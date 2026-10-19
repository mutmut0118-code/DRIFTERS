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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Better Combat's animations on a Drifter (DESIGN.md section 21), and Combat Roll's roll. Both mods play theirs through
 * the player animation library, which only animates real players, so a Drifter runs the same animation files through
 * the same library itself, set up the way Better Combat and Combat Roll set one up for a player
 * (AbstractClientPlayerEntityMixin in each): a stack per Drifter with a layer for each hand's holding pose (one for the
 * arms and body, one for the weapon in the hand), the roll, and the attack on top. Every frame the stack moves the
 * Drifter's own model parts, the whole body (applyBody), and the weapon in its hand (applyItem: many swings turn the
 * blade in the hand). With EMF (Fresh Animations and the like), the poses go through EMF Compat as a player's do, or
 * EMF's own animations on the arms and body pause. Everything through reflection: without the library, or if it
 * changes, Drifters show the vanilla arm swing and their own roll.
 */
public final class DrifterSwingAnimations {
	private DrifterSwingAnimations() {
	}

	/** One of Better Combat's pose layers: a hand's pose, for the arms and body or for the weapon in the hand. */
	private static final class PoseLayer {
		final Object layer;
		final Object mirror;
		final boolean mainHand;
		final boolean bodyChannel;
		/** What it shows now ("" for nothing; the animation's id and whether it's mirrored). */
		String showing = "";

		PoseLayer(boolean mainHand, boolean bodyChannel) throws ReflectiveOperationException {
			this.layer = modifierLayer.newInstance();
			this.mirror = mirrorModifier.newInstance(false);
			addModifier.invoke(this.layer, this.mirror, 0);
			this.mainHand = mainHand;
			this.bodyChannel = bodyChannel;
		}
	}

	private static final class State {
		final Object stack;
		final Object applier;
		final Object attackLayer;
		final Object attackSpeed;
		final Object attackMirror;
		final Object rollLayer;
		final Object rollSpeed;
		final PoseLayer mainBody;
		final PoseLayer mainItem;
		final PoseLayer offBody;
		final PoseLayer offItem;
		String seenSwing;
		int seenRoll;
		int lastTick;
		boolean emfPaused;
		/** Its poses were handed to EMF Compat last frame (cleared once when nothing plays). */
		boolean handed;
		/** A swing's trail, due at this tick (Integer.MIN_VALUE: none). */
		int trailAt = Integer.MIN_VALUE;
		String trailAnimation = "";
		float trailRange;
		boolean trailMirror;
		/** The roll animation plays now (its body turned the way it rolls, in applyBody). */
		boolean rolling;

		State(DrifterEntity drifter) throws ReflectiveOperationException {
			this.stack = animationStack.newInstance();
			this.attackLayer = modifierLayer.newInstance();
			this.attackSpeed = speedModifier.newInstance(1.0F);
			this.attackMirror = mirrorModifier.newInstance(false);
			addModifier.invoke(this.attackLayer, this.attackSpeed, 0);
			addModifier.invoke(this.attackLayer, this.attackMirror, 1);
			this.rollLayer = modifierLayer.newInstance();
			this.rollSpeed = speedModifier.newInstance(1.2F);
			addModifier.invoke(this.rollLayer, this.rollSpeed, 0);
			this.offItem = new PoseLayer(false, false);
			this.offBody = new PoseLayer(false, true);
			this.mainItem = new PoseLayer(true, false);
			this.mainBody = new PoseLayer(true, true);
			// Better Combat's order for a player: off hand under main hand, poses under the roll, the attack on top.
			addAnimLayer.invoke(this.stack, 1, this.offItem.layer);
			addAnimLayer.invoke(this.stack, 2, this.offBody.layer);
			addAnimLayer.invoke(this.stack, 3, this.mainItem.layer);
			addAnimLayer.invoke(this.stack, 4, this.mainBody.layer);
			addAnimLayer.invoke(this.stack, 1000, this.rollLayer);
			addAnimLayer.invoke(this.stack, 2000, this.attackLayer);
			this.applier = animationApplier.newInstance(this.stack);
			// A swing or roll from before it came into view isn't played.
			this.seenSwing = drifter.swingData();
			this.seenRoll = drifter.rollCount();
			this.lastTick = drifter.tickCount;
		}
	}

	private static final Map<DrifterEntity, State> STATES = new WeakHashMap<>();
	/** Test runs only (DevClientTest): what the animations do, in the log. */
	private static final boolean TRACE = System.getenv("DRIFTER_CLIENT_TEST") != null;
	private static int traced;
	private static boolean ready;
	private static boolean failed;
	/** Better Combat's weapon attributes (poses) are there too; without Better Combat only the roll is played. */
	private static boolean betterCombat;
	private static boolean emfReady;
	/**
	 * EMF Compat Core (with its Better Combat bridge): the way players' Better Combat animations reach an EMF model (Fresh
	 * Animations, FA+Player). A Drifter's go the same way: the arm poses handed to its pose manager each frame, which
	 * blends them into the EMF model (its own arm parts included), instead of pausing EMF's animations.
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
	private static Field builderBeginTick;
	private static Field builderEndTick;
	private static Field torso;
	private static Field head;
	private static Field bodyPart;
	private static Field rightArm;
	private static Field leftArm;
	private static Field rightLeg;
	private static Field leftLeg;
	private static Field rightItem;
	private static Field leftItem;
	private static Method fullyEnablePart;
	private static Method partEnabled;
	private static Field pitch;
	private static Field yawState;
	private static Field rollState;
	private static Field xState;
	private static Field yState;
	private static Field zState;
	private static Method setEnabled;
	private static Method build;
	private static Constructor<?> keyframePlayer;
	private static Constructor<?> speedModifier;
	private static Field speedField;
	private static Constructor<?> mirrorModifier;
	private static Method mirrorSetEnabled;
	private static Constructor<?> modifierLayer;
	private static Method addModifier;
	private static Method replaceWithFade;
	private static Method layerActive;
	private static Method standardFadeIn;
	private static Object easeInOutSine;
	private static Constructor<?> animationStack;
	private static Method addAnimLayer;
	private static Constructor<?> animationApplier;
	private static Method applierTick;
	private static Method applierActive;
	private static Method setTickDelta;
	private static Method updatePart;
	/** The whole body's turn, lean and lift (the library does this for players in their renderer). */
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
	/** Better Combat: a held item's weapon attributes, its pose, two-handedness; the client's leg setting. */
	private static Method weaponAttributes;
	private static Method attributesPose;
	private static Method attributesTwoHanded;
	private static Field clientConfig;
	private static Field legThreshold;
	/** Combat Roll's roll, when it's installed. */
	private static final ResourceLocation ROLL = ResourceLocation.fromNamespaceAndPath("combat_roll", "roll");

	private static boolean active() {
		if (ready || failed) {
			return ready;
		}

		failed = true;
		if (!FabricLoader.getInstance().isModLoaded("playeranimator")
			|| !FabricLoader.getInstance().isModLoaded("bettercombat") && !FabricLoader.getInstance().isModLoaded("combat_roll")) {
			return false;
		}

		try {
			String api = "dev.kosmx.playerAnim.";
			getAnimation = Class.forName(api + "minecraftApi.PlayerAnimationRegistry").getMethod("getAnimation", ResourceLocation.class);
			keyframeAnimation = Class.forName(api + "core.data.KeyframeAnimation");
			mutableCopy = keyframeAnimation.getMethod("mutableCopy");
			endTick = keyframeAnimation.getField("endTick");
			Class<?> builder = Class.forName(api + "core.data.KeyframeAnimation$AnimationBuilder");
			builderBeginTick = builder.getField("beginTick");
			builderEndTick = builder.getField("endTick");
			torso = builder.getField("torso");
			head = builder.getField("head");
			bodyPart = builder.getField("body");
			rightArm = builder.getField("rightArm");
			leftArm = builder.getField("leftArm");
			rightLeg = builder.getField("rightLeg");
			leftLeg = builder.getField("leftLeg");
			rightItem = builder.getField("rightItem");
			leftItem = builder.getField("leftItem");
			build = builder.getMethod("build");
			Class<?> collection = Class.forName(api + "core.data.KeyframeAnimation$StateCollection");
			fullyEnablePart = collection.getMethod("fullyEnablePart", boolean.class);
			partEnabled = collection.getMethod("isEnabled");
			pitch = collection.getField("pitch");
			yawState = collection.getField("yaw");
			rollState = collection.getField("roll");
			xState = collection.getField("x");
			yState = collection.getField("y");
			zState = collection.getField("z");
			setEnabled = Class.forName(api + "core.data.KeyframeAnimation$StateCollection$State").getMethod("setEnabled", boolean.class);
			keyframePlayer = Class.forName(api + "api.layered.KeyframeAnimationPlayer").getConstructor(keyframeAnimation, int.class);
			Class<?> speed = Class.forName(api + "api.layered.modifier.SpeedModifier");
			speedModifier = speed.getConstructor(float.class);
			speedField = speed.getField("speed");
			Class<?> mirror = Class.forName(api + "api.layered.modifier.MirrorModifier");
			mirrorModifier = mirror.getConstructor(boolean.class);
			mirrorSetEnabled = mirror.getMethod("setEnabled", boolean.class);
			Class<?> animation = Class.forName(api + "api.layered.IAnimation");
			Class<?> abstractModifier = Class.forName(api + "api.layered.modifier.AbstractModifier");
			Class<?> fade = Class.forName(api + "api.layered.modifier.AbstractFadeModifier");
			Class<?> ease = Class.forName(api + "core.util.Ease");
			standardFadeIn = fade.getMethod("standardFadeIn", int.class, ease);
			easeInOutSine = ease.getField("INOUTSINE").get(null);
			// The empty layer, then each modifier, then the animation: that way the library links them (its varargs
			// constructor doesn't, and an unlinked modifier says it's done: found in a client test, every swing ended at once).
			Class<?> layer = Class.forName(api + "api.layered.ModifierLayer");
			modifierLayer = layer.getConstructor();
			addModifier = layer.getMethod("addModifier", abstractModifier, int.class);
			replaceWithFade = layer.getMethod("replaceAnimationWithFade", fade, animation);
			layerActive = layer.getMethod("isActive");
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
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			Drifter.LOGGER.warn("[Drifter] The player animation library didn't match ({}); Drifters show the vanilla swing", e.toString());
			return false;
		}

		if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
			try {
				weaponAttributes = Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes", ItemStack.class);
				Class<?> attributes = Class.forName("net.bettercombat.api.WeaponAttributes");
				attributesPose = attributes.getMethod("pose");
				attributesTwoHanded = attributes.getMethod("isTwoHanded");
				betterCombat = true;
				try {
					clientConfig = Class.forName("net.bettercombat.client.BetterCombatClientMod").getField("config");
					legThreshold = Class.forName("net.bettercombat.config.ClientConfig").getField("legAnimationThreshold");
				} catch (ReflectiveOperationException e) {
					clientConfig = null;
				}

				Drifter.LOGGER.info("[Drifter] Drifters play Better Combat's swing and holding animations");
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				betterCombat = false;
				Drifter.LOGGER.warn("[Drifter] Better Combat's weapon poses didn't match ({}); Drifters play its swings only", e.toString());
			}
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
				Drifter.LOGGER.info("[Drifter] EMF Compat found: Drifters' animations go through it, as players' do");
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

	/** Combat Roll's roll animation will play for this Drifter's rolls (else the renderer shows its own). */
	public static boolean playsRolls() {
		if (!active() || !FabricLoader.getInstance().isModLoaded("combat_roll")) {
			return false;
		}

		try {
			return keyframeAnimation.isInstance(getAnimation.invoke(null, ROLL));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static void disable(Throwable e) {
		ready = false;
		failed = true;
		Drifter.LOGGER.warn("[Drifter] Drifter animations failed ({}); Drifters show the vanilla swing from now on", e.toString());
	}

	/** After the model's own pose (setupAnim): the poses, the roll and the swing on top, if any is playing. */
	private static int applied;

	public static void apply(PlayerModel<DrifterEntity> model, DrifterEntity drifter, float ageInTicks, float walking) {
		if (TRACE && applied++ % 2000 == 0) {
			Drifter.LOGGER.info("[Drifter test] apply called ({} times)", applied);
		}

		if (!active()) {
			return;
		}

		try {
			State state = STATES.get(drifter);
			if (state == null) {
				state = new State(drifter);
				STATES.put(drifter, state);
			}

			String data = drifter.swingData();
			if (!data.equals(state.seenSwing)) {
				state.seenSwing = data;
				startSwing(state, data, drifter);
			}

			int roll = drifter.rollCount();
			if (roll != state.seenRoll) {
				state.seenRoll = roll;
				startRoll(state, drifter);
			}

			for (int i = 0; i < 20 && state.lastTick < drifter.tickCount; i++) {
				if (betterCombat) {
					updatePoses(state, drifter);
				}


				applierTick.invoke(state.applier);
				state.lastTick++;
			}

			state.lastTick = drifter.tickCount;
			if (state.trailAt != Integer.MIN_VALUE && state.trailAt <= state.lastTick) {
				state.trailAt = Integer.MIN_VALUE;
				DrifterTrails.spawn(drifter, state.trailAnimation, state.trailRange, state.trailMirror);
			}

			state.rolling = state.rolling && (boolean)layerActive.invoke(state.rollLayer);
			if (!(boolean)applierActive.invoke(state.applier)) {
				release(state, drifter);
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

			if (TRACE && traced++ % 50 == 0) {
				Drifter.LOGGER.info("[Drifter test] animation frame: right arm xRot {} -> {}, poses {} | {} | {} | {}", before, model.rightArm.xRot,
					state.mainBody.showing, state.mainItem.showing, state.offBody.showing, state.offItem.showing);
			}

			if (emfCompat) {
				handToEmfCompat(model, drifter, walking);
				state.handed = true;
			} else if (emfReady && !state.emfPaused) {
				Object emfEntity = emfEntityOf.invoke(null, drifter);
				if (emfEntity != null) {
					emfPause.invoke(null, emfEntity, new ModelPart[] {model.body, model.jacket, model.rightArm, model.leftArm, model.rightSleeve,
						model.leftSleeve});
					state.emfPaused = true;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			disable(e);
		}
	}

	/**
	 * Better Combat's poses, as it sets them each tick for a player (updateAnimationsOnTick): the main hand weapon's pose,
	 * and the off hand's when dual wielding; none while swimming, using an item, climbing, gliding or holding a loaded
	 * crossbow; the arms-and-body part not while walking or crouching unless the weapon is two-handed.
	 */
	private static void updatePoses(State state, DrifterEntity drifter) throws ReflectiveOperationException {
		boolean leftHanded = drifter.getMainArm() == HumanoidArm.LEFT;
		ItemStack main = drifter.getMainHandItem();
		ItemStack off = drifter.getOffhandItem();
		String mainPose = "";
		String offPose = "";
		boolean twoHanded = false;
		if (!drifter.swinging && !drifter.isSwimming() && !drifter.isUsingItem() && !drifter.onClimbable() && !drifter.isFallFlying()
			&& !CrossbowItem.isCharged(main)) {
			Object mainAttributes = main.isEmpty() ? null : weaponAttributes.invoke(null, main);
			Object offAttributes = off.isEmpty() ? null : weaponAttributes.invoke(null, off);
			if (mainAttributes != null) {
				Object pose = attributesPose.invoke(mainAttributes);
				mainPose = pose == null ? "" : pose.toString();
				twoHanded = (boolean)attributesTwoHanded.invoke(mainAttributes);
			}

			// Dual wielding: two one-handed weapons.
			if (mainAttributes != null && !twoHanded && offAttributes != null && !(boolean)attributesTwoHanded.invoke(offAttributes)) {
				Object pose = attributesPose.invoke(offAttributes);
				offPose = pose == null ? "" : pose.toString();
			}
		}

		setPose(state.mainItem, mainPose, leftHanded, drifter);
		setPose(state.offItem, offPose, leftHanded, drifter);
		if (!twoHanded && (moving(drifter) || drifter.isCrouching())) {
			mainPose = "";
			offPose = "";
		}

		setPose(state.mainBody, mainPose, leftHanded, drifter);
		setPose(state.offBody, offPose, leftHanded, drifter);
	}

	/** Better Combat's PoseSubStack#setPose: the pose faded in (or out) when it changes, only its own channel's parts. */
	private static void setPose(PoseLayer pose, String id, boolean leftHanded, DrifterEntity drifter) throws ReflectiveOperationException {
		boolean mirror = pose.mainHand ? leftHanded : !leftHanded;
		Object animation = id.isEmpty() ? null : getAnimation.invoke(null, ResourceLocation.tryParse(id));
		if (animation != null && !keyframeAnimation.isInstance(animation)) {
			animation = null;
		}

		String showing = animation == null ? "" : id + (mirror ? " mirrored" : "");
		if (showing.equals(pose.showing)) {
			return;
		}

		pose.showing = showing;
		if (animation == null) {
			replaceWithFade.invoke(pose.layer, standardFadeIn.invoke(null, 5, easeInOutSine), null);
			return;
		}

		Object copy = mutableCopy.invoke(animation);
		if (pose.bodyChannel) {
			byActivity(copy, drifter);
			partOff(rightItem.get(copy));
			partOff(leftItem.get(copy));
		} else {
			for (Field part : new Field[] {head, torso, bodyPart, rightArm, leftArm, rightLeg, leftLeg}) {
				partOff(part.get(copy));
			}
		}

		mirrorSetEnabled.invoke(pose.mirror, mirror);
		replaceWithFade.invoke(pose.layer, standardFadeIn.invoke(null, 5, easeInOutSine), keyframePlayer.newInstance(build.invoke(copy), 0));
		if (TRACE) {
			Drifter.LOGGER.info("[Drifter test] pose {} ({}, {})", showing, pose.mainHand ? "main hand" : "off hand", pose.bodyChannel ? "arms and body" : "weapon");
		}
	}

	/** StateCollectionHelper#configure(part, false, false): the animation leaves this part alone. */
	private static void partOff(Object part) throws ReflectiveOperationException {
		for (Field state : new Field[] {pitch, rollState, yawState, xState, yState, zState}) {
			setEnabled.invoke(state.get(part), false);
		}
	}

	/** Better Combat's updateAnimationByCurrentActivity: the legs keep walking (moving, crouching, riding). */
	private static void byActivity(Object builder, DrifterEntity drifter) throws ReflectiveOperationException {
		boolean legsFree = drifter.getPose() == Pose.CROUCHING || drifter.isPassenger();
		if (!legsFree) {
			float threshold = legThreshold();
			double dx = drifter.getX() - drifter.xo;
			double dz = drifter.getZ() - drifter.zo;
			legsFree = threshold > 0.0F && (drifter.isSprinting() || moving(drifter)) && dx * dx + dz * dz > threshold * threshold;
		}

		if (legsFree) {
			partOff(rightLeg.get(builder));
			partOff(leftLeg.get(builder));
		}
	}

	private static float legThreshold() {
		try {
			Object config = clientConfig == null ? null : clientConfig.get(null);
			return config == null ? 0.0F : legThreshold.getFloat(config);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0.0F;
		}
	}

	/** Better Combat's isWalking: swimming, or moving along the ground (from where it was last tick: a Drifter's motion isn't sent). */
	private static boolean moving(DrifterEntity drifter) {
		double dx = drifter.getX() - drifter.xo;
		double dz = drifter.getZ() - drifter.zo;
		return drifter.isSwimming() || dx * dx + dz * dz > 0.03 * 0.03;
	}

	/**
	 * In the renderer's rotations (before the model is drawn): the whole-body movement, the twist into a slash and the
	 * lean into a stab or slam, as the library does for a player (PlayerRenderer's setupRotations: scale, then a move,
	 * then turns round a point 0.7 up); in a roll, the body turned the way it rolls (Combat Roll's own adjustment).
	 */
	public static void applyBody(DrifterEntity drifter, com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick, float bodyYaw) {
		if (!ready) {
			return;
		}

		State state = STATES.get(drifter);
		if (state == null) {
			return;
		}

		try {
			if (state.rolling) {
				float turn = net.minecraft.util.Mth.wrapDegrees(drifter.rollYaw() - bodyYaw);
				poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-turn));
			}

			if (!(boolean)applierActive.invoke(state.applier)) {
				return;
			}

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
			disable(e);
		}
	}

	/**
	 * Just before the weapon in a hand is drawn (ItemInHandLayer): its turn, shift and scale in the hand from the running
	 * animation, as the library does for a player's held item (HeldItemMixin): a swing turns the blade, a pose holds it.
	 */
	public static void applyItem(DrifterEntity drifter, HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		if (!ready) {
			return;
		}

		State state = STATES.get(drifter);
		if (state == null) {
			return;
		}

		try {
			if (!(boolean)applierActive.invoke(state.applier)) {
				return;
			}

			String part = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
			Object scale = get3DTransform.invoke(state.applier, part, scaleType, vec3f.newInstance(1.0F, 1.0F, 1.0F));
			Object rotation = get3DTransform.invoke(state.applier, part, rotationType, vec3fZero);
			Object position = get3DTransform.invoke(state.applier, part, positionType, vec3fZero);
			poseStack.scale(f(vecX, scale), f(vecY, scale), f(vecZ, scale));
			poseStack.translate(f(vecX, position) * 0.0625F, f(vecY, position) * 0.0625F, f(vecZ, position) * 0.0625F);
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(f(vecZ, rotation)));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotation(f(vecY, rotation)));
			poseStack.mulPose(com.mojang.math.Axis.XP.rotation(f(vecX, rotation)));
		} catch (ReflectiveOperationException | RuntimeException e) {
			disable(e);
		}
	}

	private static float f(Method getter, Object vec) throws ReflectiveOperationException {
		return ((Number)getter.invoke(vec)).floatValue();
	}

	/** Better Combat's playAttackAnimation: the swing over everything, fitted to the swing's length, mirrored for the other hand. */
	private static void startSwing(State state, String data, DrifterEntity drifter) throws ReflectiveOperationException {
		String[] parts = data.split(";");
		if (parts.length < 4) {
			return;
		}

		Object playable = getAnimation.invoke(null, ResourceLocation.tryParse(parts[1]));
		if (!keyframeAnimation.isInstance(playable)) {
			if (TRACE) {
				Drifter.LOGGER.info("[Drifter test] swing animation {} NOT FOUND (got {})", parts[1], playable);
			}

			return;
		}

		// As Better Combat sets one up for a player: the legs keep walking if it's moving, the whole torso moves, the head keeps
		// looking where it looks.
		Object builder = mutableCopy.invoke(playable);
		byActivity(builder, drifter);
		fullyEnablePart.invoke(torso.get(builder), true);
		setEnabled.invoke(pitch.get(head.get(builder)), false);
		int fadeIn = builderBeginTick.getInt(builder);
		Object animation = build.invoke(builder);
		int ticks = Math.max(1, Integer.parseInt(parts[2]));
		speedField.setFloat(state.attackSpeed, Math.max(0.2F, endTick.getInt(animation) / (float)ticks));
		mirrorSetEnabled.invoke(state.attackMirror, Boolean.parseBoolean(parts[3]));
		replaceWithFade.invoke(state.attackLayer, standardFadeIn.invoke(null, Math.max(0, fadeIn), easeInOutSine), keyframePlayer.newInstance(animation, 0));
		// Its trail at the top of the upswing (Better Combat's scheduled particles).
		if (parts.length >= 6) {
			try {
				state.trailRange = Float.parseFloat(parts[4]);
				state.trailAt = drifter.tickCount + Integer.parseInt(parts[5]);
				state.trailAnimation = parts[1];
				state.trailMirror = Boolean.parseBoolean(parts[3]);
			} catch (NumberFormatException e) {
				state.trailAt = Integer.MIN_VALUE;
			}
		}
		if (TRACE) {
			Drifter.LOGGER.info("[Drifter test] swing animation {} ({} ticks, mirrored {})", parts[1], ticks, parts[3]);
		}
	}

	/** Combat Roll's playRollAnimation: its roll, fitted to the roll's length, the body turned the way it rolls. */
	private static void startRoll(State state, DrifterEntity drifter) throws ReflectiveOperationException {
		Object playable = getAnimation.invoke(null, ROLL);
		if (!keyframeAnimation.isInstance(playable)) {
			return;
		}

		Object builder = mutableCopy.invoke(playable);
		int fadeIn = builderBeginTick.getInt(builder);
		int length = builderEndTick.getInt(builder);
		speedField.setFloat(state.rollSpeed, Math.max(0.2F, length / (float)Math.max(1, drifter.rollTicks())));
		replaceWithFade.invoke(state.rollLayer, standardFadeIn.invoke(null, Math.max(0, fadeIn), easeInOutSine), keyframePlayer.newInstance(build.invoke(builder), 0));
		state.rolling = true;
		if (TRACE) {
			Drifter.LOGGER.info("[Drifter test] roll animation ({} ticks)", drifter.rollTicks());
		}
	}

	/**
	 * This frame's pose to EMF Compat, the way its Better Combat bridge does for a player (PlayerModelMixin): its
	 * animation marked active (EMF's own arm animation steps aside), both arms, the legs when it's standing still, and the
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

	/** Nothing playing: EMF's own animations back. */
	private static void release(State state, DrifterEntity drifter) throws ReflectiveOperationException {
		if (state.handed) {
			state.handed = false;
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
