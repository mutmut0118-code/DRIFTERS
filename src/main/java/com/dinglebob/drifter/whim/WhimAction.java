package com.dinglebob.drifter.whim;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;

/**
 * One kind of whim behaviour (watch the sky, build a little something, feed an animal...), filled in by a whim's
 * parameters. Many whims share an action: docs/WHIMS.md "Templates".
 */
public interface WhimAction {
	/** It could do this here and now (what's about, what it carries): a cheap check, from what it knows. */
	boolean possible(DrifterEntity drifter, Whim whim);

	/** The activity that does it, or null if it can't after all. */
	@Nullable
	Activity start(DrifterEntity drifter, Whim whim);
}
