package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * "Stop, thief!" (docs/ENCOUNTERS.md 16): a Drifter sprints past with what it stole, the one it stole from on its heels.
 * Stop the thief and the owner, once it has its things back, gives you a share; let it go and both are soon gone. (The
 * stolen goods are the owner's own, carried off by the thief.)
 */
final class Thief extends Encounter {
	private static final String THIEF = "thief";
	private static final String OWNER = "owner";

	private final Vec3 escape;
	@Nullable
	private DrifterEntity thief;
	@Nullable
	private DrifterEntity owner;
	@Nullable
	private Vec3 thiefDiedAt;
	private boolean rewarded;
	private long lastShout = Long.MIN_VALUE / 2;

	private Thief(ServerPlayer player, Vec3 escape) {
		super(Encounters.Kind.THIEF, player);
		this.escape = escape;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		// From one side, past the player, off the other side.
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 side = look.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-look.z, 0.0, look.x).normalize();
		if (random.nextBoolean()) {
			side = side.scale(-1.0);
		}

		BlockPos from = EncounterSpawner.hiddenSpot(level, player.position(), 30.0, 40.0, side, random);
		if (from == null) {
			return null;
		}

		Vec3 escape = beyond(Vec3.atBottomCenterOf(from), player.position(), 60.0);
		Thief encounter = new Thief(player, escape);
		DrifterEntity thief = EncounterSpawner.spawn(level, from, Archetype.NOMAD, drifter -> {
			EncounterSpawner.traits(drifter, 0.3, 0.2, 0.7, 0.2);
			drifter.getInventory().add(new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(4)));
			drifter.getInventory().add(new ItemStack(Items.EMERALD, 2 + random.nextInt(4)));
			if (random.nextInt(3) == 0) {
				drifter.getInventory().add(new ItemStack(Items.DIAMOND));
			}

			drifter.getInventory().add(new ItemStack(Items.BREAD, 2));
		});
		if (thief == null) {
			return null;
		}

		encounter.join(thief, THIEF);
		encounter.thief = thief;
		EncounterSpawner.add(level, thief);
		Vec3 back = beyond(player.position(), thief.position(), 9.0);
		BlockPos at = EncounterSpawner.ground(level, (int)Math.floor(back.x), (int)Math.floor(back.z));
		DrifterEntity owner = EncounterSpawner.spawn(level, at == null ? from : at, Archetype.CASUAL, drifter -> {
			EncounterSpawner.traits(drifter, 0.7, 0.5, 0.4, 0.6);
			drifter.getInventory().add(new ItemStack(Items.STONE_SWORD));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 3));
		});
		if (owner != null) {
			encounter.join(owner, OWNER);
			encounter.owner = owner;
			EncounterSpawner.add(level, owner);
			think(owner, thief.getName().getString() + " ran off with its gold");
		}

		think(thief, "got away with the gold: running for it");
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 4;
	}

	@Override
	protected void update() {
		DrifterEntity thief = this.thief;
		if (thief != null && !thief.isAlive()) {
			this.thiefDiedAt = thief.position();
			this.thief = null;
			if (this.owner != null) {
				this.owner.getDrifterBrain().endPursuit();
			}
		}

		if (this.owner != null && !this.owner.isAlive()) {
			this.owner = null;
		}

		if (this.thief != null && this.owner != null) {
			if (sees(this.owner, this.thief)) {
				this.owner.getDrifterBrain().pursue(this.owner, this.thief, this.now());
				if (this.now() - this.lastShout > 20 * 5) {
					this.lastShout = this.now();
					say(this.owner, line(this.owner, "thief.shout", "thief", this.thief.getName().getString()));
				}
			}

			// Out of sight of everyone and well off: it got away.
			if (this.thief.position().distanceTo(this.escape) < 8.0) {
				this.end("the thief got away");
			}

			return;
		}

		if (this.thief != null && this.owner == null && this.thief.position().distanceTo(this.escape) < 8.0) {
			this.end("the thief got away");
		}

		if (this.thief == null && (this.owner == null || this.rewarded)) {
			this.end(this.rewarded ? "the thief stopped, the owner paid a share" : "the thief stopped");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		if (drifter == this.thief) {
			return 0.98;
		}

		// The owner: after the thief (the chase does it), then its things back and a share for the player.
		if (drifter == this.owner) {
			if (this.thief != null) {
				return 0.95;
			}

			return this.thiefDiedAt != null && !this.rewarded ? 0.9 : 0.0;
		}

		return 0.0;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return drifter == this.thief || drifter == this.owner && this.thief != null;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		if (drifter == this.thief) {
			return "get away with the gold";
		}

		return this.thief != null ? "catch " + this.thief.getName().getString() : "get its things back";
	}

	@Override
	protected boolean sameSide(DrifterEntity one, DrifterEntity other) {
		return false;
	}

	/** Stopping the thief is the point; the one it robbed is anyone. */
	@Override
	public Killing killing(DrifterEntity drifter, net.minecraft.world.entity.player.Player player) {
		return drifter == this.thief ? Killing.NONE : super.killing(drifter, player);
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		if (drifter == this.thief) {
			return new Script(drifter, "get away with the gold", 20 * 60 * 2)
				.then(() -> new NavigateTask(drifter, this.escape, 4.0, "run for it", NavigateTask.Pace.TRAVEL));
		}

		DrifterEntity thief = this.thief;
		if (thief != null) {
			return new Script(drifter, "catch " + thief.getName().getString(), 20 * 30)
				.then(() -> thief.isAlive() ? NavigateTask.follow(drifter, thief, 2.0) : null);
		}

		Vec3 where = this.thiefDiedAt;
		ServerPlayer player = this.player();
		if (where == null) {
			return null;
		}

		return new Script(drifter, "get its things back", 20 * 90)
			.then(() -> new NavigateTask(drifter, where, 1.5, "to its things", NavigateTask.Pace.TRAVEL))
			.then(() -> new CollectItemsTask(drifter, 6.0))
			.now(() -> think(drifter, "got its things back from " + "the thief"))
			.then(() -> player != null && player.isAlive() && player.distanceTo(drifter) < 32.0 ? walkUpTo(drifter, player, 2.5) : null)
			.then(() -> player != null && player.distanceTo(drifter) < 5.0 ? new LookAtTask(drifter, player.getEyePosition(), 10) : null)
			.now(() -> {
				if (player != null && player.distanceTo(drifter) < 5.0) {
					say(drifter, line(drifter, "thief.thanks", "player", this.playerName));
					// Half of what it got back (gold and emeralds), from its own hands.
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if ((stack.is(Items.GOLD_INGOT) || stack.is(Items.EMERALD) || stack.is(Items.DIAMOND)) && stack.getCount() > 0) {
							int half = Math.max(1, stack.getCount() / 2);
							drifter.tossForward(stack.split(half));
						}
					}
				}

				this.rewarded = true;
			});
	}
}
