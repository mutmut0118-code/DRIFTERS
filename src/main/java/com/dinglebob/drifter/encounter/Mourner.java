package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A mourner (docs/ENCOUNTERS.md 25): someone by the road puts up a grave for a friend (a sign with their name, a poppy)
 * and kneels there. Come close and it talks about them. After a while it goes; the grave stays.
 */
final class Mourner extends Encounter {
	private static final String MOURNER = "mourner";
	private static final long STAYS = 20L * 60 * 3;

	private final BlockPos grave;
	private final BlockPos flower;
	private final String friend;
	@Nullable
	private DrifterEntity mourner;
	private boolean dug;
	private int spoke;
	private long spokeAt = Long.MIN_VALUE / 2;
	private long readyAt = -1;

	private Mourner(ServerPlayer player, BlockPos grave, BlockPos flower, String friend) {
		super(Encounters.Kind.MOURNER, player);
		this.grave = grave;
		this.flower = flower;
		this.friend = friend;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 25.0, 40.0, null, random);
		if (spot == null) {
			return null;
		}

		// The grave: open ground beside where it stands, a flower next to it.
		BlockPos grave = EncounterSpawner.ground(level, spot.getX() + 2, spot.getZ());
		BlockPos flower = grave == null ? null : EncounterSpawner.ground(level, grave.getX() + 1, grave.getZ());
		if (grave == null || flower == null || Math.abs(grave.getY() - spot.getY()) > 1 || Math.abs(flower.getY() - grave.getY()) > 1) {
			return null;
		}

		String friend = com.dinglebob.drifter.identity.Usernames.generate(random, level.getServer());
		Mourner encounter = new Mourner(player, grave, flower, friend);
		DrifterEntity mourner = EncounterSpawner.spawn(level, spot, Archetype.HOMEBODY, drifter -> {
			EncounterSpawner.traits(drifter, 0.4, 0.1, 0.5, 0.2);
			drifter.getInventory().add(new ItemStack(Items.OAK_SIGN));
			drifter.getInventory().add(new ItemStack(Items.POPPY));
		});
		if (mourner == null) {
			return null;
		}

		encounter.join(mourner, MOURNER);
		encounter.mourner = mourner;
		EncounterSpawner.add(level, mourner);
		think(mourner, "burying " + friend);
		return encounter;
	}

	@Override
	protected long longest() {
		return STAYS + 20L * 60 * 2;
	}

	@Override
	protected void update() {
		DrifterEntity mourner = this.mourner;
		ServerPlayer player = this.player();
		if (mourner == null || !mourner.isAlive()) {
			this.end("gone");
			return;
		}

		if (!this.dug && this.level.getBlockEntity(this.grave) instanceof net.minecraft.world.level.block.entity.SignBlockEntity) {
			this.dug = true;
			this.readyAt = this.now();
			com.dinglebob.drifter.whim.WhimActions.writeSign(this.level, this.grave, "Here lies " + this.friend + ". Gone too soon.");
			think(mourner, "put up a grave for " + this.friend + " at " + this.grave.toShortString());
		}

		// Someone comes close: it talks about them.
		if (player != null && this.dug && (player.distanceTo(mourner) < 8.0 && knowsWhere(mourner, player) || player.distanceTo(mourner) < 4.0)
			&& this.spoke < 3 && this.now() - this.spokeAt > 20 * 12) {
			this.spokeAt = this.now();
			say(mourner, switch (this.spoke++) {
				case 0 -> this.friend + " was a good friend...";
				case 1 -> "creepers. never saw it coming.";
				default -> "did you know them? ...no. no one really did.";
			});
		}

		if (this.dug && this.now() - this.readyAt > STAYS) {
			think(mourner, "said goodbye to " + this.friend);
			this.end("it said goodbye");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.dug ? "mourn " + this.friend : "put up a grave for " + this.friend;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		Script script = new Script(drifter, this.describe(drifter), 20 * 40);
		Vec3 kneel = Vec3.atBottomCenterOf(this.grave.west(2));
		if (!this.dug) {
			script.then(() -> drifter.position().distanceTo(kneel) > 1.5 ? new NavigateTask(drifter, kneel, 1.0, "to the grave", NavigateTask.Pace.STROLL) : null);
			script.then(() -> this.level.getBlockState(this.grave).canBeReplaced() ? new PlaceBlockTask(drifter, this.grave, s -> s.is(ItemTags.SIGNS), "a grave sign") : null);
			script.then(() -> this.level.getBlockState(this.flower).canBeReplaced() ? new PlaceBlockTask(drifter, this.flower, s -> s.is(Items.POPPY), "a poppy") : null);
			return script;
		}

		return script.then(() -> drifter.position().distanceTo(kneel) > 2.0 ? new NavigateTask(drifter, kneel, 1.0, "to the grave", NavigateTask.Pace.STROLL) : null)
			.then(() -> new Kneel(drifter, Vec3.atCenterOf(this.grave), 20 * 25))
			.then(() -> new LookAtTask(drifter, drifter.getEyePosition().add(drifter.getLookAngle().multiply(1, 0, 1).scale(10)), 40));
	}

	/** Crouched by the grave, head down. */
	private static final class Kneel extends Task {
		private final Vec3 at;
		private final int ticks;
		private int t;

		Kneel(DrifterEntity drifter, Vec3 at, int ticks) {
			super(drifter);
			this.at = at;
			this.ticks = ticks;
		}

		@Override
		public String describe() {
			return "kneel by the grave";
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 20;
		}

		@Override
		protected Status onTick() {
			VirtualKeys keys = this.drifter.getKeys();
			keys.releaseAll();
			keys.set(VirtualKeys.Key.SNEAK, true);
			if (this.t % 40 == 0) {
				this.drifter.getCamera().track(this.at.add(0.0, -0.5, 0.0), CameraModel.TurnSpeed.IDLE);
			}

			return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}
}
