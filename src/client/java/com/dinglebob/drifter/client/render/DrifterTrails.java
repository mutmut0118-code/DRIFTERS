package com.dinglebob.drifter.client.render;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Better Combat's weapon trails (the slash arcs that follow a swing) for a Drifter's swings, as Better Combat draws them
 * for another player's (SlashParticleUtil#spawnParticles): the swing's own trail, or the one its trail settings give the
 * animation, in the weapon's colours, at the top of the upswing. Read through reflection; if anything doesn't match it
 * switches itself off.
 */
final class DrifterTrails {
	private DrifterTrails() {
	}

	private static boolean ready;
	private static boolean failed;
	private static Field clientConfig;
	private static Field showTrails;
	private static Field trailConfigManager;
	private static Field animationBased;
	private static Field trailAppearance;
	private static Method resolve;
	private static Method merge;
	private static Method weaponAttributes;
	private static Method weaponTrail;
	private static Method attacks;
	private static Method attackAnimation;
	private static Method attackTrail;
	private static Field entries;
	private static Method entryParticles;
	private static Method entryRoll;
	private static Method entryStab;
	private static Method layerTop;
	private static Method layerBottom;
	private static Method placementType;
	private static Method placementX;
	private static Method placementY;
	private static Method placementZ;
	private static Method placementYaw;
	private static Method placementPitch;
	private static Method placementRoll;
	private static Field primary;
	private static Field secondary;
	private static Method partColor;
	private static Method partGlows;
	private static Constructor<?> slashEffect;

	private static boolean active() {
		if (ready || failed) {
			return ready;
		}

		failed = true;
		if (!FabricLoader.getInstance().isModLoaded("bettercombat")) {
			return false;
		}

		try {
			clientConfig = Class.forName("net.bettercombat.client.BetterCombatClientMod").getField("config");
			showTrails = Class.forName("net.bettercombat.config.ClientConfig").getField("isShowingWeaponTrails");
			trailConfigManager = Class.forName("net.bettercombat.BetterCombatMod").getField("trailConfig");
			Class<?> trailConfig = Class.forName("net.bettercombat.config.TrailConfig");
			animationBased = trailConfig.getField("animation_based");
			trailAppearance = trailConfig.getField("trail_appearance");
			Class<?> conditional = Class.forName("net.bettercombat.api.fx.ConditionalTrailAppearance");
			resolve = conditional.getMethod("resolve", ItemStack.class);
			merge = conditional.getMethod("merge", conditional);
			weaponAttributes = Class.forName("net.bettercombat.logic.WeaponRegistry").getMethod("getAttributes", ItemStack.class);
			Class<?> attributes = Class.forName("net.bettercombat.api.WeaponAttributes");
			weaponTrail = attributes.getMethod("trailAppearance");
			attacks = attributes.getMethod("attacks");
			Class<?> attack = Class.forName("net.bettercombat.api.WeaponAttributes$Attack");
			attackAnimation = attack.getMethod("animation");
			attackTrail = attack.getMethod("trailParticles");
			Class<?> trails = Class.forName("net.bettercombat.client.particle.TrailParticles");
			entries = trails.getField("ENTRIES");
			Class<?> entry = Class.forName("net.bettercombat.client.particle.TrailParticles$Entry");
			entryParticles = entry.getMethod("particles");
			entryRoll = entry.getMethod("rollOffset");
			entryStab = entry.getMethod("stabPosition");
			Class<?> layered = Class.forName("net.bettercombat.client.particle.TrailParticles$LayeredParticle");
			layerTop = layered.getMethod("top");
			layerBottom = layered.getMethod("bottom");
			Class<?> placement = Class.forName("net.bettercombat.api.fx.ParticlePlacement");
			placementType = placement.getMethod("particle_type");
			placementX = placement.getMethod("x_addition");
			placementY = placement.getMethod("y_addition");
			placementZ = placement.getMethod("z_addition");
			placementYaw = placement.getMethod("local_yaw");
			placementPitch = placement.getMethod("pitch_addition");
			placementRoll = placement.getMethod("roll_set");
			Class<?> appearance = Class.forName("net.bettercombat.api.fx.TrailAppearance");
			primary = appearance.getField("primary");
			secondary = appearance.getField("secondary");
			Class<?> part = Class.forName("net.bettercombat.api.fx.TrailAppearance$Part");
			partColor = part.getMethod("color_rgba");
			partGlows = part.getMethod("glows");
			slashEffect = Class.forName("net.bettercombat.particle.SlashParticleEffect").getConstructor(ParticleType.class, float.class, float.class,
				float.class, float.class, float.class, boolean.class, long.class);
			ready = true;
			failed = false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			Drifter.LOGGER.warn("[Drifter] Better Combat's weapon trails didn't match ({}); Drifters' swings leave no trail", e.toString());
		}

		return ready;
	}

	/** The trail of this swing ({@code animation}) of the weapon in its main hand, {@code range} blocks long. */
	static void spawn(DrifterEntity drifter, String animation, float range, boolean mirror) {
		if (!active()) {
			return;
		}

		try {
			Object client = clientConfig.get(null);
			if (client == null || !showTrails.getBoolean(client)) {
				return;
			}

			ItemStack stack = drifter.getMainHandItem();
			Object attributes = stack.isEmpty() ? null : weaponAttributes.invoke(null, stack);
			Object manager = trailConfigManager.get(null);
			Object config = manager == null ? null : manager.getClass().getField("value").get(manager);
			if (config == null) {
				return;
			}

			List<?> placements = placements(attributes, config, animation);
			if (placements == null || placements.isEmpty()) {
				return;
			}

			Object defaults = trailAppearance.get(config);
			Object weapon = attributes == null ? null : weaponTrail.invoke(attributes);
			Object look = weapon == null ? resolve.invoke(defaults, stack) : resolve.invoke(merge.invoke(defaults, weapon), stack);
			if (look == null) {
				return;
			}

			draw(drifter, placements, look, range, mirror);
		} catch (ReflectiveOperationException | RuntimeException e) {
			ready = false;
			failed = true;
			Drifter.LOGGER.warn("[Drifter] Weapon trail failed ({}); Drifters' swings leave no trail from now on", e.toString());
		}
	}

	/** The swing's own trail (from its weapon's attack with this animation), or the one the trail settings give the animation. */
	@Nullable
	private static List<?> placements(@Nullable Object attributes, Object config, String animation) throws ReflectiveOperationException {
		if (attributes != null) {
			Object[] all = (Object[])attacks.invoke(attributes);
			if (all != null) {
				for (Object attack : all) {
					if (animation.equals(attackAnimation.invoke(attack)) && attackTrail.invoke(attack) instanceof List<?> own && !own.isEmpty()) {
						return own;
					}
				}
			}
		}

		return animationBased.get(config) instanceof Map<?, ?> byAnimation && byAnimation.get(animation) instanceof List<?> list ? list : null;
	}

	/** SlashParticleUtil#spawnParticles, for a Drifter. */
	private static void draw(DrifterEntity drifter, List<?> placements, Object look, float range, boolean mirror) throws ReflectiveOperationException {
		Map<?, ?> kinds = (Map<?, ?>)entries.get(null);
		float weaponRange = range + 0.25F;
		Object top = primary.get(look);
		Object bottom = secondary.get(look);
		for (Object placement : placements) {
			if (!(kinds.get(placementType.invoke(placement)) instanceof List<?> trails)) {
				continue;
			}

			float flip = mirror ? -1.0F : 1.0F;
			float offhandRoll = mirror ? 180.0F : 0.0F;
			float yaw = drifter.getYRot();
			float pitch = drifter.getXRot();
			Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F).normalize();
			Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
			Vec3 at = new Vec3(drifter.getX(), drifter.getEyeY() - 0.25 + (float)placementY.invoke(placement), drifter.getZ())
				.add(forward.scale((float)placementZ.invoke(placement))).add(right.scale((float)placementX.invoke(placement) * flip));
			Vec3 stab = at.add(forward.scale(weaponRange - 1.5));
			for (Object trail : trails) {
				Vec3 pos = (boolean)entryStab.invoke(trail) ? stab : at;
				float roll = ((float)placementRoll.invoke(placement) + (float)entryRoll.invoke(trail) + offhandRoll) * flip;
				float localYaw = (float)placementYaw.invoke(placement) * flip;
				float pitchAdded = pitch + (float)placementPitch.invoke(placement);
				for (Object layer : (List<?>)entryParticles.invoke(trail)) {
					if (top != null) {
						add(drifter, layerBottom.invoke(layer), weaponRange, pitchAdded, yaw, localYaw, roll, top, pos);
					}

					if (bottom != null) {
						add(drifter, layerTop.invoke(layer), weaponRange, pitchAdded, yaw, localYaw, roll, bottom, pos);
					}
				}
			}
		}
	}

	private static void add(DrifterEntity drifter, Object type, float range, float pitch, float yaw, float localYaw, float roll, Object part, Vec3 pos)
		throws ReflectiveOperationException {
		Object effect = slashEffect.newInstance(type, range, pitch, yaw, localYaw, roll, (boolean)partGlows.invoke(part), (long)partColor.invoke(part));
		drifter.level().addParticle((ParticleOptions)effect, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
	}
}
