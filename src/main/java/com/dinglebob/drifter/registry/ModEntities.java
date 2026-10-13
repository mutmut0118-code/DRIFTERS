package com.dinglebob.drifter.registry;

import com.dinglebob.drifter.Drifter;
import com.dinglebob.drifter.entity.DrifterEntity;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModEntities {
	/**
	 * Player-sized, tracked and updated like a player (players use updateInterval 2).
	 * MISC category for now: no vanilla natural spawning and no share of the monster or creature caps
	 * (see the spawn-group design question in docs/PROGRESS.md).
	 */
	public static final EntityType<DrifterEntity> DRIFTER = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		Drifter.id("drifter"),
		EntityType.Builder.<DrifterEntity>of(DrifterEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.eyeHeight(Player.DEFAULT_EYE_HEIGHT)
			.vehicleAttachment(Player.DEFAULT_VEHICLE_ATTACHMENT)
			.clientTrackingRange(10)
			.updateInterval(2)
			.build("drifter")
	);

	public static final Item DRIFTER_SPAWN_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		Drifter.id("drifter_spawn_egg"),
		new SpawnEggItem(DRIFTER, 0x2E6B8A, 0xC9A26B, new Item.Properties())
	);

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(DRIFTER, DrifterEntity.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(DRIFTER_SPAWN_EGG));
	}
}
