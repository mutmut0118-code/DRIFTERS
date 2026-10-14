package com.dinglebob.drifter.executor.task;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.motor.CameraModel;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Right-clicks an entity with the held item (feeding an animal to breed it), looking at it and within entity reach. */
public class UseOnEntityTask extends Task {
	private final Entity target;
	private final String label;
	private int settled;

	public UseOnEntityTask(DrifterEntity drifter, Entity target, String label) {
		super(drifter);
		this.target = target;
		this.label = label;
	}

	@Override
	public String describe() {
		return this.label;
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 5;
	}

	@Override
	protected Status onTick() {
		if (!this.target.isAlive()) {
			return this.fail("it's gone");
		}

		double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		if (this.target.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
			return this.fail("out of reach");
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(this.target.getBoundingBox().getCenter(), CameraModel.TurnSpeed.NORMAL);
		if (!this.drifter.getCamera().isSettled() || ++this.settled < 3) {
			return Status.RUNNING;
		}

		InteractionResult result = FakePlayerBridge.act(this.drifter, fake -> fake.interactOn(this.target, InteractionHand.MAIN_HAND));
		if (!result.consumesAction()) {
			return this.fail("nothing happened");
		}

		this.drifter.swing(InteractionHand.MAIN_HAND);
		return Status.SUCCESS;
	}

	@Override
	protected void onStop() {
		this.drifter.getCamera().clearTarget();
	}
}
