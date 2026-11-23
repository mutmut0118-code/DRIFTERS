package com.dinglebob.drifter.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The optional mods' mixins (drifter.compat.mixins.json, not required): each is applied only when its mod is loaded, and
 * whether it really went in is noted here, so the code that relies on it can switch itself off (with one warning) when another
 * version of that mod doesn't match.
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
	/** The Trinkets hook (compatmixin.TrinketsApiMixin) is in place: Drifters wearing no trinkets may skip Trinkets' scan (R40). */
	static volatile boolean trinketsHook;

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".TrinketsApiMixin")) {
			return FabricLoader.getInstance().isModLoaded("trinkets");
		}

		if (mixinClassName.endsWith(".IcyCartMixin")) {
			return FabricLoader.getInstance().isModLoaded(IcyHorses.MOD_ID);
		}

		if (mixinClassName.endsWith(".TusklinMushroomMixin")) {
			return FabricLoader.getInstance().isModLoaded("alexsmobs");
		}

		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (mixinClassName.endsWith(".TrinketsApiMixin")) {
			trinketsHook = true;
		}

		// (Whether the tusklin's attack check really calls ours: its name differs by environment, and a new Alex's Mobs may not
		// have it; said once in the log either way.)
		if (mixinClassName.endsWith(".TusklinMushroomMixin")) {
			boolean wired = false;
			for (org.objectweb.asm.tree.MethodNode method : targetClass.methods) {
				if (!method.name.equals("canAttack") && !method.name.equals("method_18395")) {
					continue;
				}

				for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions) {
					if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.contains("sparesMushroomHolder")) {
						wired = true;
					}
				}
			}

			if (wired) {
				org.slf4j.LoggerFactory.getLogger("drifter").info("[compat] Alex's Mobs: tusklins spare a Drifter holding a mushroom, as a player");
			} else {
				org.slf4j.LoggerFactory.getLogger("drifter").warn("[compat] Alex's Mobs: couldn't find the tusklin's attack check; a mushroom doesn't spare Drifters");
			}
		}
	}
}
