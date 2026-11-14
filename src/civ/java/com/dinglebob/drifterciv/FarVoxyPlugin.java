package com.dinglebob.drifterciv;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The Voxy hookup's mixins go in only when Voxy SeedGen is there in a version they were written against (0.3.x): any
 * other, and nothing of it is touched (the far view is then drawn without the sketched towns).
 */
public final class FarVoxyPlugin implements IMixinConfigPlugin {
	public static final boolean WANTED = FabricLoader.getInstance().getModContainer("voxyseedgen")
		.map(mod -> mod.getMetadata().getVersion().getFriendlyString().startsWith("0.3.")).orElse(false);

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return WANTED;
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
	}
}
