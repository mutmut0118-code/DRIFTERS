package com.dinglebob.drifter.executor.task;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.hands.FakePlayerBridge;
import com.dinglebob.drifter.mixin.FishingHookAccessor;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.personality.Trait;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Fishing (DESIGN.md section 10.5), as a player does it: rod out, a cast toward the water, eyes on the bobber, and when it
 * dips with a bite, a pull after a moment's reaction (quicker for the skilled); the catch flies in to its feet. The real
 * rod and bobber, cast by its helper player (the bobber's own bite timing, Lure and Luck of the Sea, the fishing loot
 * table). A cast that lands on the bank is pulled in and thrown a little further.
 */
public class FishTask extends Task {
	private enum Phase {
		SELECT, AIM, WAIT, PAUSE
	}

	private final Vec3 water;
	private final int wanted;
	private Phase phase = Phase.SELECT;
	@Nullable
	private SelectItemTask select;
	private int ticks;
	private int casts;
	private int caught;
	private int reaction = -1;
	/** Raised after a cast that fell short (degrees). */
	private float lift;
	private static final int MAX_CASTS = 12;
	/** No bite for this long: pull in and cast again (a bite comes after 5 to 30 s in vanilla). */
	private static final int BITE_WAIT = 20 * 40;
	/** The bobber not in the water this long after a cast: it fell short. */
	private static final int LAND_WAIT = 50;

	public FishTask(DrifterEntity drifter, Vec3 water, int wanted) {
		super(drifter);
		this.water = water;
		this.wanted = wanted;
	}

	@Override
	public String describe() {
		return "fish";
	}

	@Override
	protected int timeoutTicks() {
		return 20 * 60 * 6;
	}

	public int caught() {
		return this.caught;
	}

	@Nullable
	private FishingHook hook() {
		FishingHook hook = this.drifter.helperPlayer == null ? null : this.drifter.helperPlayer.fishing;
		return hook != null && !hook.isRemoved() ? hook : null;
	}

	@Override
	protected Status onTick() {
		this.drifter.getKeys().releaseAll();
		switch (this.phase) {
			case SELECT -> {
				if (!this.drifter.getMainHandItem().is(Items.FISHING_ROD)) {
					if (this.select == null) {
						this.select = new SelectItemTask(this.drifter, stack -> stack.is(Items.FISHING_ROD), "the fishing rod");
					}

					Status status = this.select.tick();
					if (status == Status.FAILURE) {
						return this.fail("has no fishing rod");
					}

					return Status.RUNNING;
				}

				this.phase = Phase.AIM;
				this.ticks = 0;
			}
			case AIM -> {
				// A little above where it means the bobber to land: it arcs down.
				Vec3 eye = this.drifter.getEyePosition();
				Vec3 at = this.water.add(0.0, 0.5 + eye.distanceTo(this.water) * 0.12, 0.0);
				float pitch = (float)Math.toDegrees(-Math.atan2(at.y - eye.y, Math.sqrt((at.x - eye.x) * (at.x - eye.x) + (at.z - eye.z) * (at.z - eye.z))));
				float yaw = (float)Math.toDegrees(Math.atan2(at.z - eye.z, at.x - eye.x)) - 90.0F;
				this.drifter.getCamera().lookAt(yaw, Mth.clamp(pitch - this.lift, -60.0F, 60.0F), CameraModel.TurnSpeed.NORMAL);
				if (!this.drifter.getCamera().isSettled() || ++this.ticks < 4) {
					return Status.RUNNING;
				}

				if (++this.casts > MAX_CASTS) {
					return this.caught > 0 ? Status.SUCCESS : this.fail("couldn't get the line in the water");
				}

				this.use();
				this.phase = Phase.WAIT;
				this.ticks = 0;
				this.reaction = -1;
			}
			case WAIT -> {
				FishingHook hook = this.hook();
				if (hook == null) {
					this.phase = Phase.AIM;
					this.ticks = 0;
					return Status.RUNNING;
				}

				// Eyes on the bobber.
				this.holdRod();
				this.drifter.getCamera().track(hook.position(), CameraModel.TurnSpeed.IDLE);
				this.ticks++;
				// (Bobbing, it floats just above the water's surface: in a water block, not "in water".)
				boolean onWater = hook.isInWater() || hook.level().getFluidState(hook.blockPosition()).is(net.minecraft.tags.FluidTags.WATER);
				if (!onWater && this.ticks > LAND_WAIT) {
					// On the bank (or in a tree): pull it in, throw again a little higher.
					if (com.dinglebob.drifter.memory.ThoughtLog.echo) {
						this.drifter.getMemory().thoughts().add(this.drifter.level().getGameTime(), String.format(java.util.Locale.ROOT,
							"cast landed at %.1f %.1f %.1f (aimed at %.1f %.1f %.1f, from %.1f %.1f %.1f, pitch %.0f)", hook.getX(), hook.getY(), hook.getZ(),
							this.water.x, this.water.y, this.water.z, this.drifter.getX(), this.drifter.getY(), this.drifter.getZ(), this.drifter.getXRot()));
					}

					this.use();
					this.lift = Math.min(30.0F, this.lift + 8.0F);
					this.phase = Phase.PAUSE;
					this.ticks = 0;
					return Status.RUNNING;
				}

				if (this.reaction < 0 && ((FishingHookAccessor)hook).drifter$biting()) {
					float skill = this.drifter.getPersonality().getF(Trait.SKILL);
					this.reaction = Math.round(Mth.lerp(skill, 12.0F, 3.0F)) + this.drifter.getRandom().nextInt(4);
				}

				if (this.reaction >= 0 && this.reaction-- == 0) {
					boolean bite = ((FishingHookAccessor)hook).drifter$biting();
					this.use();
					if (bite) {
						this.caught++;
					}

					this.phase = Phase.PAUSE;
					this.ticks = 0;
					return Status.RUNNING;
				}

				if (this.ticks > BITE_WAIT) {
					this.use();
					this.phase = Phase.PAUSE;
					this.ticks = 0;
				}
			}
			case PAUSE -> {
				// The catch flying in, a breath, then the next cast.
				if (++this.ticks < 25) {
					return Status.RUNNING;
				}

				if (this.caught >= this.wanted) {
					return Status.SUCCESS;
				}

				this.phase = Phase.AIM;
				this.ticks = 0;
			}
		}

		return Status.RUNNING;
	}

	/** A right-click with the rod: a cast with no bobber out, else pulling it in. */
	private void use() {
		FakePlayerBridge.act(this.drifter, fake -> fake.gameMode.useItem(fake, fake.level(), fake.getMainHandItem(), InteractionHand.MAIN_HAND));
		this.drifter.swing(InteractionHand.MAIN_HAND);
		this.holdRod();
	}

	/**
	 * Its helper player keeps the rod in hand while the line is out (the same rod, not a copy): the bobber reels itself in
	 * the moment its caster isn't holding a rod, and the helper's hands are emptied after every action.
	 */
	private void holdRod() {
		var fake = this.drifter.helperPlayer;
		if (fake != null) {
			var inventory = fake.getInventory();
			inventory.selected = this.drifter.getInventory().getSelected();
			inventory.items.set(inventory.selected, this.drifter.getMainHandItem().is(Items.FISHING_ROD) ? this.drifter.getMainHandItem()
				: net.minecraft.world.item.ItemStack.EMPTY);
		}
	}

	@Override
	protected void onStop() {
		if (this.select != null) {
			this.select.stop();
		}

		// Reeled in before it goes off to do something else.
		if (this.hook() != null && this.drifter.getMainHandItem().is(Items.FISHING_ROD)) {
			this.use();
		} else if (this.hook() != null) {
			this.hook().discard();
		}

		// The helper's hand empty again (the rod is the Drifter's).
		if (this.drifter.helperPlayer != null) {
			this.drifter.helperPlayer.getInventory().items.replaceAll(stack -> net.minecraft.world.item.ItemStack.EMPTY);
		}
	}
}
