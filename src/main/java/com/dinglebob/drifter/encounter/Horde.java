package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
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
 * Chased by a horde (docs/ENCOUNTERS.md 20): someone comes running flat out with a pack of zombies on their heels (in
 * caps, so daylight doesn't end it), shouts "run!" and goes right past the player, leading the lot of them to them. It
 * keeps running; the zombies find whoever's nearest. They're the world's ordinary zombies after that.
 */
final class Horde extends Encounter {
	private static final String RUNNER = "runner";

	private final Vec3 escape;
	private final List<Zombie> horde = new ArrayList<>();
	@Nullable
	private DrifterEntity runner;
	private boolean shouted;
	private boolean passed;

	private Horde(ServerPlayer player, Vec3 escape) {
		super(Encounters.Kind.HORDE, player);
		this.escape = escape;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 28.0, 38.0, null, random);
		if (spot == null) {
			return null;
		}

		// Past the player and on the other way.
		Vec3 escape = beyond(Vec3.atBottomCenterOf(spot), player.position(), 70.0);
		Horde encounter = new Horde(player, escape);
		DrifterEntity runner = EncounterSpawner.spawn(level, spot, Archetype.NOMAD, drifter -> {
			EncounterSpawner.traits(drifter, 0.2, 0.1, 0.9, 0.2);
			drifter.getInventory().add(new ItemStack(Items.BREAD, 3));
			drifter.setHealth(14.0F);
		});
		if (runner == null) {
			return null;
		}

		encounter.join(runner, RUNNER);
		encounter.runner = runner;
		EncounterSpawner.add(level, runner);
		// The pack, behind it.
		Vec3 behind = beyond(player.position(), runner.position(), 8.0);
		int count = 5 + random.nextInt(3);
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, (int)Math.floor(behind.x) + random.nextInt(7) - 3, (int)Math.floor(behind.z) + random.nextInt(7) - 3);
			for (int tries = 0; at == null && tries < 8; tries++) {
				at = EncounterSpawner.ground(level, runner.getBlockX() + random.nextInt(13) - 6, runner.getBlockZ() + random.nextInt(13) - 6);
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

			zombie.setTarget(runner);
			if (level.addFreshEntity(zombie)) {
				encounter.horde.add(zombie);
			}
		}

		if (encounter.horde.size() < 3) {
			encounter.horde.forEach(Zombie::discard);
			runner.discard();
			return null;
		}

		think(runner, "running for its life, a whole pack of zombies behind it");
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 4;
	}

	@Override
	protected void update() {
		DrifterEntity runner = this.runner;
		ServerPlayer player = this.player();
		if (runner == null || !runner.isAlive()) {
			this.end("the runner didn't make it");
			return;
		}

		this.horde.removeIf(zombie -> !zombie.isAlive());
		if (player == null) {
			return;
		}

		if (!this.shouted && sees(runner, player)) {
			this.shouted = true;
			say(runner, runner.getRandom().nextBoolean() ? "RUN! there's loads of them!" : "zombies! run, " + this.playerName + ", RUN!");
		}

		// Gone past them: each zombie goes for whoever's nearer now (the player, as often as not).
		if (!this.passed && runner.distanceTo(player) < 10.0) {
			this.passed = true;
		}

		if (this.passed) {
			for (Zombie zombie : this.horde) {
				if (zombie.getTarget() == runner && zombie.distanceTo(player) + 4.0 < zombie.distanceTo(runner)) {
					zombie.setTarget(null);
				}
			}
		}

		if (runner.distanceTo(player) > 80.0 || runner.position().distanceTo(this.escape) < 6.0) {
			this.end("it ran on");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return 1.32;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "run from the horde";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		return new Script(drifter, "run from the horde", 20 * 40).then(() -> new NavigateTask(drifter, this.escape, 4.0, "run for it", NavigateTask.Pace.TRAVEL));
	}

	@Override
	public String status() {
		return super.status() + ", " + this.horde.size() + " zombies" + (this.passed ? ", gone past the player" : "");
	}
}
