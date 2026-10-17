package com.dinglebob.drifter.client.render;

import java.lang.reflect.Array;
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
	private static boolean ready;
	private static boolean failed;
	private static boolean emfReady;

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
	private static Class<?> abstractModifier;
	private static Constructor<?> animationStack;
	private static Method addAnimLayer;
	private static Constructor<?> animationApplier;
	private static Method applierTick;
	private static Method applierActive;
	private static Method setTickDelta;
	private static Method updatePart;
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
			abstractModifier = Class.forName(api + "api.layered.modifier.AbstractModifier");
			modifierLayer = Class.forName(api + "api.layered.ModifierLayer").getConstructor(animation, Array.newInstance(abstractModifier, 0).getClass());
			Class<?> stack = Class.forName(api + "api.layered.AnimationStack");
			animationStack = stack.getConstructor();
			addAnimLayer = stack.getMethod("addAnimLayer", int.class, animation);
			Class<?> applier = Class.forName(api + "impl.animation.AnimationApplier");
			animationApplier = applier.getConstructor(animation);
			applierTick = applier.getMethod("tick");
			applierActive = applier.getMethod("isActive");
			setTickDelta = applier.getMethod("setTickDelta", float.class);
			updatePart = applier.getMethod("updatePart", String.class, ModelPart.class);
			ready = true;
			failed = false;
			Drifter.LOGGER.info("[Drifter] Drifters play Better Combat's swing animations");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			Drifter.LOGGER.warn("[Drifter] Better Combat's animations didn't match ({}); Drifters show the vanilla swing", e.toString());
			return false;
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
	public static void apply(PlayerModel<DrifterEntity> model, DrifterEntity drifter, float ageInTicks) {
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
				stop(state, drifter);
				return;
			}

			setTickDelta.invoke(state.applier, ageInTicks - drifter.tickCount);
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
			if (emfReady && !state.emfPaused) {
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
		Object modifiers = Array.newInstance(abstractModifier, 2);
		Array.set(modifiers, 0, speedModifier.newInstance(speed));
		Array.set(modifiers, 1, mirrorModifier.newInstance(Boolean.parseBoolean(parts[3])));
		Object layer = modifierLayer.newInstance(keyframePlayer.newInstance(animation, 0), modifiers);
		Object stack = animationStack.newInstance();
		addAnimLayer.invoke(stack, 1000, layer);
		state.applier = animationApplier.newInstance(stack);
		state.lastTick = drifter.tickCount;
	}

	private static void stop(State state, DrifterEntity drifter) throws ReflectiveOperationException {
		state.applier = null;
		if (state.emfPaused) {
			state.emfPaused = false;
			Object emfEntity = emfEntityOf.invoke(null, drifter);
			if (emfEntity != null) {
				emfResume.invoke(null, emfEntity);
			}
		}
	}
}
