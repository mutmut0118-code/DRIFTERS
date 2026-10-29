package com.dinglebob.drifter.api;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;

import net.minecraft.core.BlockPos;

/**
 * An errand to another town (milestone 22 step 6, diplomacy: an envoy with a message for its leader), set by Drifter:
 * Civilizations with {@link DrifterApi#setErrand}: it walks to {@code there}, finds the one it's sent to ({@code meet}, or
 * anyone of theirs about), says its piece, hears the answer, and walks back {@code home} with it. Every step walked; the
 * news gets home when it does. Not saved: an errand under way when the world closes, the town finishes without it.
 */
public interface Errand {
	/** The other town's middle, and its name. */
	BlockPos there();

	String thereName();

	/** Its own town's middle. */
	BlockPos home();

	/** The one it's sent to (their leader), or null for anyone of theirs. */
	@Nullable
	UUID meet();

	/** Where the one it's sent to lives (it goes there if they aren't about the middle), or null. */
	@Nullable
	default BlockPos meetAt() {
		return null;
	}

	/** What it says when it finds them. */
	String message();

	/** It's said its piece to {@code met} (null: it found nobody to tell): the other town decides; their answer, if any, spoken. */
	void arrived(DrifterEntity envoy, @Nullable DrifterEntity met);

	/** It's back home with the answer. */
	void back(DrifterEntity envoy);

	/** It's said its piece already (an errand given again after a trip out of the running world): home from here. */
	default boolean told() {
		return false;
	}

	/** It couldn't go on (the way, the time): why. */
	default void gaveUp(DrifterEntity envoy, String why) {
	}
}
