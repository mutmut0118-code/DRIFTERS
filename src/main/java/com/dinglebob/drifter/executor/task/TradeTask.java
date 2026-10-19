package com.dinglebob.drifter.executor.task;

import java.util.ArrayList;
import java.util.List;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.trade.TradeBook;
import com.dinglebob.drifter.trade.Trading;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * Trading with a villager in reach (DESIGN.md section 10.12), as a player does: it looks at the villager and right-clicks
 * (the villager's own interaction starts the trade, with this player's reputation discounts), reads the offers (now it
 * knows them), decides from what's really on offer, makes its trades one at a time with a moment for each click, and
 * closes the screen. Its helper player can't open a screen, so the trades are made as the screen makes them
 * (Trading#trade).
 */
public class TradeTask extends Task {
	private enum Phase {
		AIM, READ, TRADE, DONE
	}

	private final AbstractVillager villager;
	private final boolean lookOnly;
	private Phase phase = Phase.AIM;
	private int wait;
	private boolean open;
	private final List<Trading.Step> steps = new ArrayList<>();
	private int stepIndex;
	private int doneOfStep;
	private int traded;
	/** Reading the offers when the screen opens. */
	private static final int READ_TICKS = 25;
	/** Each click (pick the offer, shift-click the result). */
	private static final int TRADE_TICKS = 10;

	/** {@code lookOnly}: it only opens the screen to see what this villager has. */
	public TradeTask(DrifterEntity drifter, AbstractVillager villager, boolean lookOnly) {
		super(drifter);
		this.villager = villager;
		this.lookOnly = lookOnly;
	}

	@Override
	public String describe() {
		return this.lookOnly ? "see what the villager trades" : "trade with the villager";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 45;
	}

	/** How many trades it made. */
	public int traded() {
		return this.traded;
	}

	@Override
	protected Status onTick() {
		if (!this.villager.isAlive() || this.villager.isRemoved()) {
			return this.fail("the villager is gone");
		}

		double reach = this.drifter.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		if (this.villager.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > (reach + 1.0) * (reach + 1.0)) {
			return this.fail("the villager walked off");
		}

		this.drifter.getKeys().releaseAll();
		this.drifter.getCamera().track(this.villager.getEyePosition(), CameraModel.TurnSpeed.NORMAL);
		switch (this.phase) {
			case AIM -> {
				if (!this.drifter.getCamera().isSettled() || ++this.wait < 3) {
					return Status.RUNNING;
				}

				if (this.villager.getBoundingBox().distanceToSqr(this.drifter.getEyePosition()) > reach * reach) {
					return this.fail("out of reach");
				}

				if (this.villager.isSleeping()) {
					return this.fail("the villager is asleep");
				}

				if (this.villager.isTrading()) {
					return this.fail("the villager is busy trading");
				}

				boolean[] started = {false};
				InteractionResult result = FakePlayerBridge.act(this.drifter, fake -> {
					InteractionResult r = fake.interactOn(this.villager, InteractionHand.MAIN_HAND);
					started[0] = this.villager.getTradingPlayer() == fake;
					return r;
				});
				this.drifter.swing(InteractionHand.MAIN_HAND);
				if (!started[0]) {
					return this.fail(result.consumesAction() ? "the villager has nothing to trade" : "the villager won't trade");
				}

				this.open = true;
				long now = this.drifter.level().getGameTime();
				this.drifter.getDrifterBrain().trades().saw(this.villager, now);
				this.phase = Phase.READ;
				this.wait = 0;
			}
			case READ -> {
				if (++this.wait < READ_TICKS) {
					return Status.RUNNING;
				}

				if (!this.lookOnly) {
					// What's really on offer now (prices move with demand and its reputation).
					TradeBook.Seen seen = this.drifter.getDrifterBrain().trades().get(this.villager.getUUID());
					Trading.Plan plan = seen == null ? null : Trading.plan(this.drifter, seen);
					if (plan != null) {
						this.steps.addAll(plan.steps());
						this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "trading: " + plan.why());
					}
				}

				this.phase = this.steps.isEmpty() ? Phase.DONE : Phase.TRADE;
				this.wait = 0;
			}
			case TRADE -> {
				if (++this.wait < TRADE_TICKS) {
					return Status.RUNNING;
				}

				this.wait = 0;
				Trading.Step step = this.steps.get(this.stepIndex);
				boolean made = FakePlayerBridge.act(this.drifter, fake -> Trading.trade(fake, this.villager, step.offer()));
				if (made) {
					this.traded++;
					this.doneOfStep++;
				}

				if (!made || this.doneOfStep >= step.times()) {
					if (made || this.doneOfStep > 0) {
						this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), "traded: " + step.label()
							+ (this.doneOfStep < step.times() ? " (" + this.doneOfStep + " of " + step.times() + ")" : ""));
					}

					this.stepIndex++;
					this.doneOfStep = 0;
					if (this.stepIndex >= this.steps.size()) {
						this.phase = Phase.DONE;
					}
				}
			}
			case DONE -> {
				this.close();
				return Status.SUCCESS;
			}
		}

		return Status.RUNNING;
	}

	/** Closes the trading screen (the villager stops trading; its special prices for this player go). */
	private void close() {
		if (this.open) {
			this.open = false;
			if (this.villager.getTradingPlayer() != null && this.villager.getTradingPlayer().getUUID().equals(FakePlayerBridge.helperId(this.drifter))) {
				this.villager.setTradingPlayer(null);
			}

			// What it saw is what's there after its trades.
			if (this.villager.isAlive()) {
				this.drifter.getDrifterBrain().trades().saw(this.villager, this.drifter.level().getGameTime());
			}
		}
	}

	@Override
	protected void onStop() {
		this.close();
	}
}
