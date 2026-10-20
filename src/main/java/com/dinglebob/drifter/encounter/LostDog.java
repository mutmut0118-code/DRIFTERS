package com.dinglebob.drifter.encounter;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A lost dog (docs/ENCOUNTERS.md 15): someone walks up, worried: their dog ran off that way a while ago; here's a lead
 * in case you find him. He's out there somewhere, wandering, his name over his head. Bring him back (on the lead, or
 * however) and the owner thanks you with something; they go off together.
 */
final class LostDog extends Encounter {
	private static final String OWNER = "owner";
	private static final List<String> NAMES = List.of("Biscuit", "Rex", "Pepper", "Scout", "Bear", "Milo", "Rusty", "Buddy", "Ziggy", "Waffles");
	/** Back: the dog this near the owner. */
	private static final double REUNITED = 6.0;
	private static final long CALLS_EVERY = 20L * 40;

	private final String name;
	private final Vec3 waitAt;
	@Nullable
	private DrifterEntity owner;
	@Nullable
	private Wolf dog;
	private long askedAt = -1;
	private boolean found;
	private boolean thanked;
	private long lastCall = Long.MIN_VALUE / 2;

	private LostDog(ServerPlayer player, String name, Vec3 waitAt) {
		super(Encounters.Kind.LOST_DOG, player);
		this.name = name;
		this.waitAt = waitAt;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 22.0, 34.0, null, random);
		if (spot == null) {
			return null;
		}

		// The dog: off somewhere out of sight, the other way from the player.
		Vec3 from = Vec3.atBottomCenterOf(spot);
		BlockPos dogAt = EncounterSpawner.hiddenSpot(level, player.position(), 50.0, 85.0, player.position().subtract(from), random);
		Wolf dog = dogAt == null ? null : EntityType.WOLF.create(level);
		if (dog == null) {
			return null;
		}

		String name = NAMES.get(random.nextInt(NAMES.size()));
		LostDog encounter = new LostDog(player, name, player.position());
		DrifterEntity owner = EncounterSpawner.spawn(level, spot, Archetype.HOMEBODY, drifter -> {
			EncounterSpawner.traits(drifter, 0.4, 0.1, 0.6, 0.1);
			drifter.getInventory().add(new ItemStack(Items.LEAD));
			drifter.getInventory().add(new ItemStack(Items.BONE, 3));
			drifter.getInventory().add(EncounterSpawner.valuable(random));
		});
		if (owner == null) {
			return null;
		}

		dog.moveTo(dogAt.getX() + 0.5, dogAt.getY(), dogAt.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		dog.finalizeSpawn(level, level.getCurrentDifficultyAt(dogAt), MobSpawnType.EVENT, null);
		dog.setTame(true, true);
		dog.setOwnerUUID(owner.getUUID());
		dog.setOrderedToSit(false);
		dog.setInSittingPose(false);
		dog.setCustomName(Component.literal(name));
		dog.setPersistenceRequired();
		encounter.join(owner, OWNER);
		encounter.owner = owner;
		EncounterSpawner.add(level, owner);
		if (!level.addFreshEntity(dog)) {
			owner.discard();
			return null;
		}

		encounter.dog = dog;
		encounter.props.add(dog);
		think(owner, name + " ran off toward " + direction(from, dog.position()) + ": looking for him");
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 14;
	}

	/** The dog goes with the owner at the end, unless the player kept him on their lead. */
	@Override
	protected boolean stillTheirs(Entity prop) {
		return !(prop instanceof Wolf wolf && wolf.getLeashHolder() instanceof Player);
	}

	@Override
	protected void update() {
		DrifterEntity owner = this.owner;
		Wolf dog = this.dog;
		ServerPlayer player = this.player();
		if (owner == null || !owner.isAlive()) {
			this.end("the owner is gone");
			return;
		}

		if (dog == null || !dog.isAlive()) {
			say(owner, "...no. " + this.name + "? no...");
			this.end("the dog is dead");
			return;
		}

		if (!this.found && dog.distanceTo(owner) < REUNITED) {
			this.found = true;
			say(owner, this.name + "! there you are! oh, you silly dog.");
			// Its own lead on him now (the player's, if he came on one).
			dog.setLeashedTo(owner, true);
			think(owner, this.name + " is back");
		}

		if (player == null) {
			return;
		}

		if (this.found) {
			if (this.thanked || player.distanceTo(owner) > 48.0) {
				this.end(this.thanked ? "the dog is back, and they thanked them" : "the dog is back");
			}

			return;
		}

		if (this.askedAt >= 0 && this.now() - this.lastCall > CALLS_EVERY) {
			this.lastCall = this.now();
			say(owner, this.name + "! here, boy! " + this.name + "!");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return this.thanked || this.player() == null ? 0.0 : 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return this.askedAt < 0 || this.found && !this.thanked;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.askedAt < 0 ? "ask " + this.playerName + " about its dog" : this.found ? "thank " + this.playerName : "wait for news of " + this.name;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		Wolf dog = this.dog;
		if (player == null || dog == null) {
			return null;
		}

		if (this.askedAt < 0) {
			String way = direction(drifter.position(), dog.position());
			return new Script(drifter, this.describe(drifter), 20 * 60)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					if (drifter.distanceTo(player) < 7.0) {
						say(drifter, "excuse me... have you seen my dog? " + this.name + ", red collar. he ran off " + way
							+ " a while ago and won't come back. here, take this lead, in case you find him.");
						for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
							ItemStack stack = drifter.getInventory().getItem(slot);
							if (stack.is(Items.LEAD)) {
								drifter.tossForward(stack.split(1));
								break;
							}
						}

						this.askedAt = this.now();
						this.lastCall = this.now();
					}
				});
		}

		if (this.found) {
			return new Script(drifter, "thank " + this.playerName, 20 * 40)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					say(drifter, "thank you so much. here, it's not much, but take it.");
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if (!stack.isEmpty() && !stack.is(Items.BONE)) {
							drifter.tossForward(stack.split(stack.getCount()));
							break;
						}
					}

					think(drifter, this.playerName + " found " + this.name);
					this.thanked = true;
				});
		}

		// Waiting where it met them (they'll bring him back there), looking about.
		return new Script(drifter, "wait for news of " + this.name, 20 * 30).until(() -> this.found)
			.then(() -> drifter.position().distanceTo(this.waitAt) > 6.0 ? new NavigateTask(drifter, this.waitAt, 4.0, "back to where it waits", NavigateTask.Pace.STROLL) : null)
			.then(() -> new LookAroundTask(drifter, 3, 20));
	}

	@Override
	public String status() {
		Wolf dog = this.dog;
		return super.status() + (this.found ? ", the dog is back" : dog != null ? ", " + this.name + " at " + dog.blockPosition().toShortString() : "");
	}
}
