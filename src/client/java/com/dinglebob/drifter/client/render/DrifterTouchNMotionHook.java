package com.dinglebob.drifter.client.render;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Deque;

import net.minecraft.client.model.HumanoidModel;

import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_texture_features.features.state.ETFState;

/**
 * The EMF side of {@link DrifterTouchNMotion}, loaded only with EMF there. EMF calls its hooks once a creature's model has
 * been animated (and again when the pose is copied onto its armour); Touch'n Motion's own hook acts only when that
 * creature is a player. For a Drifter whose stand-in the addon has just had, this one calls the addon's hook itself, with
 * the Drifter's animation state made over to the stand-in (the same state in every way but whose it is, the stand-in's
 * UUID and the stand-in itself): the addon's poses go onto the Drifter's model and armour, worked out for the stand-in.
 *
 * <p>Registered once the first Drifter is drawn, so after EMF Compat Core's hook (which puts captured poses back first,
 * as for a player). EMF's current state (read by the addon for the armour) is the made-over one for that call, put on
 * ETF's stack of states and taken off after.</p>
 */
final class DrifterTouchNMotionHook extends EMFAnimationApi.EMFAnimationHook {
	/** Test runs only (DevClientTest): how far the addon moves a Drifter's limbs, in the log now and then. */
	private static final boolean TRACE = System.getenv("DRIFTER_CLIENT_TEST") != null;
	private static final String[] TRACED = {"body", "right_arm", "left_arm", "right_leg", "left_leg", "head"};
	private static int traced;
	private static float tracedMost;
	private final EMFAnimationApi.EMFAnimationHook addon;
	private final Deque<Object> states;

	private DrifterTouchNMotionHook(EMFAnimationApi.EMFAnimationHook addon, Deque<Object> states) {
		this.addon = addon;
		this.states = states;
	}

	@SuppressWarnings("unchecked")
	static void register() throws Exception {
		// The addon's own hook: one more of it (it keeps all it knows in its own static state).
		Constructor<?> make = Class.forName("strm.touchnmotion.TouchNMotionHook").getDeclaredConstructor();
		make.setAccessible(true);
		Field stack = ETFState.class.getDeclaredField("stateStack");
		stack.setAccessible(true);
		DrifterTouchNMotionHook hook = new DrifterTouchNMotionHook((EMFAnimationApi.EMFAnimationHook)make.newInstance(), (Deque<Object>)stack.get(null));
		if (!EMFAnimationApi.registerAnimationHook(hook)) {
			throw new IllegalStateException("EMF turned the animation hook down");
		}
	}

	@Override
	public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
		EMFEntityRenderState state = context.activeState();
		DrifterStandIn standIn = state == null ? null : DrifterTouchNMotion.posing(state.emfEntity());
		if (standIn == null) {
			return;
		}

		EMFEntityRenderState asStandIn = madeOver(state, standIn);
		AnimationContext swapped = new AnimationContext(asStandIn, context.animatingModelRoot(), context.error(),
			context.partsRequestedToPauseThisAnimation(), context.animationHandler());
		this.states.push(asStandIn);
		Runnable unseat = DrifterTouchNMotion.seat(standIn);
		try {
			float[] before = TRACE ? pose(context) : null;
			this.addon.onAnimationEnd(swapped, wasCancelledByHook);
			if (before != null) {
				trace(standIn, before, pose(context));
			}
		} catch (RuntimeException | LinkageError e) {
			DrifterTouchNMotion.hookFailed(e);
		} finally {
			unseat.run();
			this.states.pop();
		}
	}

	@Override
	public void onBipedPoseCopyEnd(EMFBipedPose pose, HumanoidModel<?> model, boolean wasCancelledByHook) {
		EMFEntityRenderState state = EMFState.state();
		DrifterStandIn standIn = state == null ? null : DrifterTouchNMotion.posing(state.emfEntity());
		if (standIn == null) {
			return;
		}

		this.states.push(madeOver(state, standIn));
		Runnable unseat = DrifterTouchNMotion.seat(standIn);
		try {
			this.addon.onBipedPoseCopyEnd(pose, model, wasCancelledByHook);
		} catch (RuntimeException | LinkageError e) {
			DrifterTouchNMotion.hookFailed(e);
		} finally {
			unseat.run();
			this.states.pop();
		}
	}

	private static float[] pose(AnimationContext context) {
		var parts = context.animatingModelRoot().getAllVanillaPartsByNameEMF();
		float[] pose = new float[TRACED.length * 4];
		for (int i = 0; i < TRACED.length; i++) {
			var part = parts.get(TRACED[i]);
			if (part != null) {
				pose[i * 4] = part.xRot;
				pose[i * 4 + 1] = part.yRot;
				pose[i * 4 + 2] = part.zRot;
				pose[i * 4 + 3] = part.y;
			}
		}

		return pose;
	}

	private static void trace(DrifterStandIn standIn, float[] before, float[] after) {
		float most = 0.0F;
		String which = "";
		for (int i = 0; i < before.length; i++) {
			float moved = Math.abs(after[i] - before[i]);
			if (moved > most) {
				most = moved;
				which = TRACED[i / 4] + " " + "xyzp".charAt(i % 4);
			}
		}

		tracedMost = Math.max(tracedMost, most);
		if (traced++ % 300 == 0) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter test] Touch'n Motion on {}: moved most {} by {} (most so far {})",
				standIn.drifter.getName().getString(), which, most, tracedMost);
		}
	}

	/** The Drifter's state, but the stand-in's. */
	private static EMFEntityRenderState madeOver(EMFEntityRenderState state, DrifterStandIn standIn) {
		InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
			case "emfEntity", "entity" -> method.getParameterCount() == 0 ? standIn : call(state, method, args);
			case "uuid" -> method.getParameterCount() == 0 ? standIn.getUUID() : call(state, method, args);
			case "isPlayer" -> method.getParameterCount() == 0 ? Boolean.TRUE : call(state, method, args);
			// Never made the current state the usual way, so nothing to switch on or off.
			case "activate", "deactivate" -> null;
			case "equals" -> args[0] == proxy;
			case "hashCode" -> System.identityHashCode(proxy);
			case "toString" -> "Drifter stand-in state of " + state;
			default -> call(state, method, args);
		};
		return (EMFEntityRenderState)Proxy.newProxyInstance(EMFEntityRenderState.class.getClassLoader(), new Class<?>[] {EMFEntityRenderState.class},
			handler);
	}

	private static Object call(Object state, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(state, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
