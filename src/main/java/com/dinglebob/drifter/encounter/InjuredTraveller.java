package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * An injured traveller (docs/ENCOUNTERS.md 10): low on health and running from monsters, it begs for help. Save it and
 * it thanks you with something from its pack; ignore it and it may not make it. The monsters are ordinary zombies come
 * after it (wearing caps, so daylight doesn't end the chase for them).
 */
final class InjuredTraveller extends Encounter {
	private static final String TRAVELLER = "traveller";
	/** Chasers this far from it no longer count (it got away). */
	private static final double LOST_THEM = 40.0;

	private final List<Zombie> chasers = new ArrayList<>();
	private boolean safe;
	private long safeAt;
	/** It has seen the player (someone to run to; someone to thank). */
	private boolean sawPlayer;
	private boolean thanked;
	private long lastPlea = Long.MIN_VALUE / 2;

	private InjuredTraveller(ServerPlayer player) {
		super(Encounters.Kind.INJURED, player);
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 22.0, 32.0, null, random);
		if (spot == null) {
			return null;
		}

		InjuredTraveller encounter = new InjuredTraveller(player);
		DrifterEntity traveller = EncounterSpawner.spawn(level, spot, Archetype.NOMAD, drifter -> {
			EncounterSpawner.traits(drifter, 0.15, 0.1, 0.9, 0.2);
			drifter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
			drifter.getInventory().add(EncounterSpawner.valuable(random));
			drifter.getInventory().add(new ItemStack(Items.TORCH, 4));
			drifter.setHealth(8.0F);
		});
		if (traveller == null) {
			return null;
		}

		encounter.join(traveller, TRAVELLER);
		EncounterSpawner.add(level, traveller);
		// Its chasers, on its far side from the player.
		Vec3 behind = beyond(player.position(), traveller.position(), 10.0);
		int count = 2 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, (int)Math.floor(behind.x) + random.nextInt(5) - 2, (int)Math.floor(behind.z) + random.nextInt(5) - 2);
			for (int tries = 0; at == null && tries < 8; tries++) {
				at = EncounterSpawner.ground(level, traveller.getBlockX() + random.nextInt(11) - 5, traveller.getBlockZ() + random.nextInt(11) - 5);
			}

			Zombie zombie = EntityType.ZOMBIE.create(level);
			if (at == null || zombie == null) {
				continue;
			}

			zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			if (zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
			}

			zombie.setTarget(traveller);
			if (level.addFreshEntity(zombie)) {
				encounter.chasers.add(zombie);
			}
		}

		if (encounter.chasers.isEmpty()) {
			// (No zombies after it: no story. It goes, unseen.)
			traveller.discard();
			return null;
		}

		think(traveller, "hurt, and the zombies won't let up");
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 5;
	}

	@Override
	protected void update() {
		DrifterEntity traveller = this.members.isEmpty() ? null : this.members.get(0);
		if (traveller == null) {
			this.end("the traveller didn't make it");
			return;
		}

		this.chasers.removeIf(zombie -> !zombie.isAlive() || zombie.distanceTo(traveller) > LOST_THEM);
		for (Zombie zombie : this.chasers) {
			if (zombie.getTarget() == null) {
				zombie.setTarget(traveller);
			}
		}

		ServerPlayer player = this.player();
		if (!this.safe && this.chasers.isEmpty()) {
			this.safe = true;
			this.safeAt = this.now();
			think(traveller, "the zombies are gone");
		}

		if (player != null && knowsWhere(traveller, player)) {
			this.sawPlayer = true;
		}

		if (!this.safe && this.now() - this.lastPlea > 20 * 6) {
			this.lastPlea = this.now();
			boolean seen = player != null && knowsWhere(traveller, player);
			say(traveller, !seen ? "help! anyone! zombies!" : traveller.getRandom().nextBoolean() ? "help! please, help me!" : "zombies! " + this.playerName + ", help!");
		}

		// (Safe, and nobody came for half a minute: it goes on alone. Someone it saw, gone far off: the same.)
		if (this.thanked || this.safe && (!this.sawPlayer && this.now() - this.safeAt > 20 * 30 || player == null || this.sawPlayer && player.distanceTo(traveller) > 48.0)) {
			this.end(this.thanked ? "saved, and thanked them" : "got away on its own");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null || this.thanked) {
			return 0.0;
		}

		// Running from the zombies (away from them: no digging in with them on its heels), then to the player once it has
		// seen them (above even its own fleeing: they're its best chance), then thanking them (before digging in for the
		// night: a thank-you is quick, and it's not alone now).
		if (!this.safe) {
			return this.sawPlayer ? 1.31 : 1.32;
		}

		return this.sawPlayer ? 1.0 : 0.0;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return !this.safe || this.sawPlayer;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.safe ? "thank " + this.playerName : this.sawPlayer ? "run to " + this.playerName + " for help" : "run from the zombies, looking for help";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		if (!this.safe && this.sawPlayer) {
			return new Script(drifter, "run to " + this.playerName + " for help", 20 * 20)
				.then(() -> NavigateTask.follow(drifter, player, 2.0));
		}

		if (!this.safe) {
			// Away from the zombies, wherever that leads, shouting for anyone who'll hear.
			Vec3 from = drifter.position();
			Vec3 zombies = Vec3.ZERO;
			for (Zombie zombie : this.chasers) {
				zombies = zombies.add(zombie.position());
			}

			Vec3 away = this.chasers.isEmpty() ? from.add(1.0, 0.0, 0.0) : zombies.scale(1.0 / this.chasers.size());
			Vec3 to = beyond(away, from, 24.0);
			return new Script(drifter, "run from the zombies, looking for help", 20 * 15)
				.then(() -> new NavigateTask(drifter, to, 3.0, "run for it", NavigateTask.Pace.TRAVEL));
		}

		return new Script(drifter, "thank " + this.playerName, 20 * 40)
			.then(() -> walkUpTo(drifter, player, 2.5))
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.now(() -> {
				say(drifter, "thank you... I'd be dead without you. Here, take this.");
				// From its own pack: the best thing it carries.
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (!stack.isEmpty() && !stack.is(Items.TORCH)) {
						drifter.tossForward(stack.split(stack.getCount()));
						break;
					}
				}

				think(drifter, this.playerName + " saved it from the zombies");
				this.thanked = true;
			})
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 20));
	}
}
