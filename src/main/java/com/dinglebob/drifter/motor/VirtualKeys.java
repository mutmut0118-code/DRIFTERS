package com.dinglebob.drifter.motor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Drifter's movement keyboard (DESIGN.md sections 8 and 16.2): the same discrete keys a player holds.
 * The motor layer presses and releases these; {@link com.dinglebob.drifter.entity.DrifterEntity} turns them
 * into movement exactly the way the client turns a player's keys into movement.
 */
public class VirtualKeys {
	public enum Key {
		FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	private final boolean[] down = new boolean[Key.values().length];

	public boolean isDown(Key key) {
		return this.down[key.ordinal()];
	}

	public void set(Key key, boolean pressed) {
		this.down[key.ordinal()] = pressed;
	}

	public void releaseAll() {
		java.util.Arrays.fill(this.down, false);
	}

	public boolean anyDown() {
		for (boolean pressed : this.down) {
			if (pressed) {
				return true;
			}
		}

		return false;
	}

	/** -1, 0 or 1, like the client's KeyboardInput: opposite keys cancel out. */
	public float forwardImpulse() {
		return impulse(this.isDown(Key.FORWARD), this.isDown(Key.BACK));
	}

	public float leftImpulse() {
		return impulse(this.isDown(Key.LEFT), this.isDown(Key.RIGHT));
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0F;
		}

		return positive ? 1.0F : -1.0F;
	}

	/**
	 * Parses "forward+sprint+jump" (also accepts commas and spaces, and the aliases w/a/s/d).
	 * "none" or an empty string releases everything.
	 *
	 * @throws IllegalArgumentException on an unknown key name
	 */
	public static List<Key> parse(String text) {
		List<Key> keys = new ArrayList<>();
		for (String part : text.toLowerCase(Locale.ROOT).split("[+, ]+")) {
			if (part.isEmpty() || part.equals("none")) {
				continue;
			}

			keys.add(switch (part) {
				case "w", "forward", "fwd" -> Key.FORWARD;
				case "s", "back", "backward" -> Key.BACK;
				case "a", "left" -> Key.LEFT;
				case "d", "right" -> Key.RIGHT;
				case "jump", "space" -> Key.JUMP;
				case "sneak", "shift", "crouch" -> Key.SNEAK;
				case "sprint", "ctrl" -> Key.SPRINT;
				default -> throw new IllegalArgumentException(part);
			});
		}

		return keys;
	}

	public void setOnly(List<Key> keys) {
		this.releaseAll();
		for (Key key : keys) {
			this.set(key, true);
		}
	}

	public String describe() {
		return describe(this.bits());
	}

	/** The keys down, one bit each (by Key order): cheap to keep, described later. */
	public int bits() {
		int bits = 0;
		for (int i = 0; i < this.down.length; i++) {
			if (this.down[i]) {
				bits |= 1 << i;
			}
		}

		return bits;
	}

	private static final String[] DESCRIBED = new String[1 << Key.values().length];

	/** "forward+sprint" for these {@link #bits()} ("none" for none). */
	public static String describe(int bits) {
		String known = DESCRIBED[bits];
		if (known != null) {
			return known;
		}

		List<String> names = new ArrayList<>();
		for (Key key : Key.values()) {
			if ((bits & 1 << key.ordinal()) != 0) {
				names.add(key.id());
			}
		}

		String text = names.isEmpty() ? "none" : String.join("+", names);
		DESCRIBED[bits] = text;
		return text;
	}
}
