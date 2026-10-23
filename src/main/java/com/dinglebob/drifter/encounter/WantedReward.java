package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bounty paid (the user: "i kill the drifter, collect the bounty"): after a player brings down a Drifter they hold a
 * wanted poster for, a courier comes with the reward in its pack and hands it over.
 */
final class WantedReward extends Encounter {
	private static final String COURIER = "courier";

	private final Encounters.Wanted wanted;
	@Nullable
	private DrifterEntity courier;
	private boolean paid;

	private WantedReward(ServerPlayer player, Encounters.Wanted wanted) {
		super(Encounters.Kind.REWARD, player);
		this.wanted = wanted;
	}

	@Nullable
	static Encounter start(ServerPlayer player, Encounters.PlayerState state) {
		Encounters.Wanted wanted = state.wanted;
		if (wanted == null || !wanted.claimed) {
			return null;
		}

		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 30.0, 42.0, null, random);
		if (spot == null) {
			return null;
		}

		Item reward = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(wanted.rewardItem)).orElse(Items.DIAMOND);
		WantedReward encounter = new WantedReward(player, wanted);
		DrifterEntity courier = EncounterSpawner.spawn(level, spot, Archetype.CASUAL, drifter -> {
			EncounterSpawner.traits(drifter, 0.6, 0.3, 0.5, 0.3);
			drifter.getInventory().add(new ItemStack(reward, wanted.rewardCount));
			drifter.getInventory().add(new ItemStack(Items.IRON_SWORD));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 3));
		});
		if (courier == null) {
			return null;
		}

		encounter.join(courier, COURIER);
		encounter.courier = courier;
		EncounterSpawner.add(level, courier);
		think(courier, "bringing " + player.getGameProfile().getName() + " the bounty on " + wanted.name);
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 4;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.courier == null || !this.courier.isAlive()) {
			this.end("the courier is gone");
		} else if (this.paid) {
			Encounters.state(this.player).wanted = null;
			Encounters.changed();
			this.end("the bounty is paid");
		} else if (player == null || player.distanceTo(this.courier) > 96.0) {
			this.end("couldn't find them");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return this.player() == null || this.paid ? 0.0 : 0.9;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "pay " + this.playerName + " the bounty on " + this.wanted.name;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		return new Script(drifter, this.describe(drifter), 20 * 60)
			.then(() -> walkUpTo(drifter, player, 2.5))
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.now(() -> {
				if (drifter.distanceTo(player) > 6.0) {
					return;
				}

				say(drifter, line(drifter, "wanted.reward", "target", this.wanted.name));
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (!stack.isEmpty() && !stack.is(Items.IRON_SWORD) && !stack.is(Items.BREAD)) {
						drifter.tossForward(stack.split(stack.getCount()));
						break;
					}
				}

				this.paid = true;
			})
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 20));
	}
}
