package com.dinglebob.drifter.brain.activity;

import java.util.Set;
import java.util.stream.Collectors;

import com.dinglebob.drifter.brain.mining.Caver;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;

/**
 * A look round a cave on a hunch (user's idea, 2026-10-16: "if they don't see any ore when they run into a cave, they
 * still go just in case, if it's a waste of time oh well"). It hasn't seen anything in there; it goes in a little way
 * anyway, lighting its way, and looks. Ore it sees is remembered (its next mining trip goes for it); if there was nothing,
 * it shrugs and gets on with its day.
 */
public class CaveHunchActivity extends Activity {
	/** Walks to new spots in the cave: a look, not a full trip. */
	private static final int LEGS = 8;
	/** How far under the entrance it heads (the frontier search leans toward this depth). */
	private static final int DEPTH_BELOW = 16;

	private final Caver caver;
	private final Set<String> oreBefore;

	public CaveHunchActivity(DrifterEntity drifter, BlockPos cave) {
		super(drifter);
		this.caver = new Caver(drifter, cave, cave.getY() - DEPTH_BELOW, LEGS);
		this.oreBefore = this.oreKnown();
	}

	@Override
	public String describe() {
		return "look round a cave, just in case";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 4;
	}

	@Override
	protected void stepDone(Task step, Status status) {
		this.caver.stepDone(step, status);
		this.relay();
	}

	@Override
	protected Status next() {
		Task step = this.caver.next();
		this.relay();
		if (step != null) {
			return this.begin(step);
		}

		this.finished = true;
		Set<String> found = this.oreKnown();
		found.removeAll(this.oreBefore);
		this.think(found.isEmpty() ? "nothing much in that cave: a waste of time, oh well"
			: "the hunch paid off: " + found.stream().map(key -> pretty(key.substring(0, key.indexOf('@')))).distinct().collect(Collectors.joining(", ")) + " in the cave");
		return Status.SUCCESS;
	}

	private boolean finished;

	/** It has had its look (the goal lets go then). */
	public boolean finished() {
		return this.finished;
	}

	/** "minecraft:copper_ore" as "copper ore". */
	private static String pretty(String label) {
		return label.substring(label.indexOf(':') + 1).replace('_', ' ');
	}

	private void relay() {
		String thought = this.caver.takeThought();
		if (thought != null) {
			this.think(thought);
		}
	}

	/** The ore it knows of, as "label@x,y,z". */
	private Set<String> oreKnown() {
		return this.drifter.getMemory().all().filter(entry -> entry.kind == MemoryKind.ORE)
			.map(entry -> entry.label + "@" + BlockPos.containing(entry.pos).toShortString())
			.collect(Collectors.toCollection(java.util.HashSet::new));
	}
}
