package com.dinglebob.drifter.executor.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dinglebob.drifter.brain.Storage;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Throwing junk out of its inventory to make room (DESIGN.md section 10.18, "Junk policy"): opens the inventory (a short
 * pause, head tilted down a little), then drops one junk stack at a time in front of it, like a player pressing Q on
 * each, with a beat between throws.
 */
public class TossJunkTask extends Task {
	private static final int MIN_GAP = 4;
	private static final int MAX_GAP = 9;
	private List<Map.Entry<Integer, Integer>> toss = List.of();
	private int index;
	private int wait;
	private int thrown;

	public TossJunkTask(DrifterEntity drifter) {
		super(drifter);
	}

	@Override
	public String describe() {
		return "throw out junk";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 20;
	}

	@Override
	protected void onStart() {
		this.toss = new ArrayList<>(Storage.junk(this.drifter).entrySet());
		this.drifter.getKeys().releaseAll();
		this.wait = InventoryPause.roll(this.drifter, InventoryPause.MOVE_ITEM_MIN, InventoryPause.MOVE_ITEM_MAX);
		float pitch = Mth.clamp(this.drifter.getXRot() + InventoryPause.HEAD_TILT_DOWN, -90.0F, 90.0F);
		this.drifter.getCamera().lookAt(this.drifter.getYRot(), pitch, CameraModel.TurnSpeed.NORMAL);
	}

	@Override
	protected Status onTick() {
		if (--this.wait > 0) {
			return Status.RUNNING;
		}

		if (this.index >= this.toss.size()) {
			return this.thrown > 0 ? Status.SUCCESS : this.fail("nothing to throw out");
		}

		Map.Entry<Integer, Integer> entry = this.toss.get(this.index++);
		ItemStack stack = this.drifter.getInventory().getItem(entry.getKey());
		if (!stack.isEmpty()) {
			this.drifter.tossForward(stack.split(Math.min(entry.getValue(), stack.getCount())));
			this.thrown++;
		}

		this.wait = Mth.nextInt(this.drifter.getRandom(), MIN_GAP, MAX_GAP);
		return Status.RUNNING;
	}
}
