package com.dinglebob.drifter.client;

import com.dinglebob.drifter.client.render.DrifterRenderer;
import com.dinglebob.drifter.registry.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class DrifterClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.DRIFTER, DrifterRenderer::new);
	}
}
