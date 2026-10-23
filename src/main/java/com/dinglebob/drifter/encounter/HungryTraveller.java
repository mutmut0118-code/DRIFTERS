package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Lost and hungry (docs/ENCOUNTERS.md 12): a traveller who hasn't eaten in days walks up and asks for food. Throw it
 * some and it eats, then gives you something for your kindness; none, and it goes on its way.
 */
final class HungryTraveller extends Encounter {
	private static final String TRAVELLER = "traveller";
	private static final long WAITS = 20L * 90;
	/** Fed: it has eaten up to here. */
	private static final int FED = 12;

	@Nullable
	private DrifterEntity traveller;
	private long askedAt = -1;
	private boolean thanked;

	private HungryTraveller(ServerPlayer player) {
		super(Encounters.Kind.HUNGRY, player);
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 25.0, 36.0, null, random);
		if (spot == null) {
			return null;
		}

		HungryTraveller encounter = new HungryTraveller(player);
		DrifterEntity traveller = EncounterSpawner.spawn(level, spot, Archetype.NOMAD, drifter -> {
			EncounterSpawner.traits(drifter, 0.4, 0.1, 0.6, 0.1);
			drifter.getInventory().add(EncounterSpawner.valuable(random));
			drifter.getFoodData().setFoodLevel(3);
		});
		if (traveller == null) {
			return null;
		}

		encounter.join(traveller, TRAVELLER);
		encounter.traveller = traveller;
		EncounterSpawner.add(level, traveller);
		think(traveller, "starving, a long way from anywhere");
		return encounter;
	}

	private static boolean hasFood(DrifterEntity drifter) {
		return carried(drifter, stack -> stack.has(DataComponents.FOOD)) > 0;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 5;
	}

	@Override
	protected void update() {
		DrifterEntity traveller = this.traveller;
		ServerPlayer player = this.player();
		if (traveller == null || !traveller.isAlive() || player == null) {
			this.end("gone");
			return;
		}

		if (this.thanked) {
			this.end("fed, and thanked them");
		} else if (this.askedAt >= 0 && !hasFood(traveller) && traveller.getFoodData().getFoodLevel() < FED
			&& (this.now() - this.askedAt > WAITS || player.distanceTo(traveller) > 32.0)) {
			say(traveller, line(traveller, "hungry.giveup"));
			this.end("nobody fed it");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		if (this.player() == null || this.thanked) {
			return 0.0;
		}

		// Fed something: it eats first (its own way of eating), then thanks them.
		if (hasFood(drifter) && drifter.getFoodData().getFoodLevel() < FED) {
			return 0.0;
		}

		// (Waiting where it is, not off foraging: it asked.)
		return 0.99;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.askedAt < 0 ? "ask " + this.playerName + " for food" : drifter.getFoodData().getFoodLevel() >= FED ? "thank " + this.playerName : "wait, hungry";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		if (this.askedAt < 0) {
			return new Script(drifter, this.describe(drifter), 20 * 60)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					if (drifter.distanceTo(player) < 7.0) {
						say(drifter, line(drifter, "hungry.ask", "player", this.playerName));
						this.askedAt = this.now();
					}
				});
		}

		if (drifter.getFoodData().getFoodLevel() >= FED) {
			return new Script(drifter, "thank " + this.playerName, 20 * 40)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					say(drifter, line(drifter, "hungry.thanks", "player", this.playerName));
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if (!stack.isEmpty() && !stack.has(DataComponents.FOOD)) {
							drifter.tossForward(stack.split(stack.getCount()));
							break;
						}
					}

					think(drifter, this.playerName + " fed it when it was starving");
					this.thanked = true;
				});
		}

		// Waiting: facing them, picking up what they throw.
		return new Script(drifter, "wait, hungry", 20 * 6)
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.then(() -> thrownBy(drifter, player, 6.0).isEmpty() ? null : new CollectItemsTask(drifter, 6.0));
	}
}
