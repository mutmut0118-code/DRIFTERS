package com.dinglebob.drifter.nav;

import java.util.Locale;

/** The moves the pathfinder can chain (DESIGN.md section 12, "Move types"). */
public enum MoveType {
	START, WALK, DIAGONAL, JUMP_UP, STEP_DOWN, BRIDGE, PILLAR, CLIMB_UP, CLIMB_DOWN, SWIM, SWIM_UP, SWIM_DOWN;

	public String id() {
		return this.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
