package com.dinglebob.drifter.client.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The blocks being broken, by the id of whoever breaks each (a Drifter's are its Touch'n Motion stand-in's too). */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("destroyingBlocks")
	Int2ObjectMap<BlockDestructionProgress> drifter$destroyingBlocks();
}
