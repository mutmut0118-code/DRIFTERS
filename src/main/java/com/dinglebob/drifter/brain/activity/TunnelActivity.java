package com.dinglebob.drifter.brain.activity;

import com.dinglebob.drifter.brain.mining.Tunnel;
import com.dinglebob.drifter.brain.mining.TunnelDigger;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;

/**
 * Debug (`/drifter tunnel <length> [targetY]`): digs one tunnel straight ahead with the same digger its mining trips
 * and night mining use, a level 1x2 tunnel or a staircase down to a height. For staging tests (water, caves, lava).
 */
public class TunnelActivity extends Activity {
	private final Tunnel tunnel;
	private final TunnelDigger digger;

	public TunnelActivity(DrifterEntity drifter, int length, int targetY) {
		super(drifter);
		this.tunnel = new Tunnel(drifter.blockPosition(), drifter.getDirection(), length * 2);
		this.tunnel.legs.add(new Tunnel.Leg(drifter.blockPosition(), drifter.getDirection(), length, targetY));
		this.digger = new TunnelDigger(drifter, this.tunnel);
	}

	@Override
	public String describe() {
		return "dig a test tunnel";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 5;
	}

	@Override
	protected boolean countsAsFailure(Task step) {
		return this.digger.countsAsFailure(step);
	}

	@Override
	protected void stepDone(Task step, Status status) {
		this.digger.stepDone(step, status);
		this.relay();
	}

	private void relay() {
		String thought = this.digger.takeThought();
		if (thought != null) {
			this.think(thought);
		}
	}

	@Override
	protected Status next() {
		Task step = this.digger.next();
		this.relay();
		return step == null ? Status.SUCCESS : this.begin(step);
	}
}
