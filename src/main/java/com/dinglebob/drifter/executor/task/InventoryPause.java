package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * The "inventory open" pause (DESIGN.md sections 1 and 10.2): a player has to open a screen to craft or move items,
 * so they stand still for a moment. Durations in ticks, scaled by the Efficiency trait (an efficient player knows
 * where everything is in the inventory; a scatterbrained one hunts around for it).
 */
final class InventoryPause {
	/** Crafting session: 0.5 to 2 s (section 10.2). */
	static final int CRAFT_MIN = 10;
	static final int CRAFT_MAX = 40;
	/** Moving one item between inventory and hotbar, or putting on armor. */
	static final int MOVE_ITEM_MIN = 6;
	static final int MOVE_ITEM_MAX = 16;
	/** Looking over a container's contents after opening it. */
	static final int CONTAINER_MIN = 4;
	static final int CONTAINER_MAX = 10;
	/** Ticks per shift-click while moving stacks or crafting repeatedly. */
	static final int CLICK_TICKS = 3;
	/** How far the head tilts down while a screen is "open", in degrees of pitch. */
	static final float HEAD_TILT_DOWN = 25.0F;

	private InventoryPause() {
	}

	static int roll(DrifterEntity drifter, int min, int max) {
		double efficiency = drifter.getPersonality().get(com.dinglebob.drifter.personality.Trait.EFFICIENCY);
		double scale = net.minecraft.util.Mth.lerp(efficiency, 1.4, 0.7);
		return Math.max(1, (int)Math.round((min + drifter.getRandom().nextInt(max - min + 1)) * scale));
	}
}
