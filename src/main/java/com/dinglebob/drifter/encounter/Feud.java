package com.dinglebob.drifter.encounter;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A feud (docs/ENCOUNTERS.md 17): two Drifters at each other's throats, out of the blue. Watch, or pick a side: the one
 * you helped, if it wins, thanks you with something; the one you hit remembers it.
 */
final class Feud extends Encounter {
	private static final String FIGHTER = "fighter";
	private static final List<String> SHOUTS = List.of("you'll pay for what you did to my farm!", "that was MY diamond!", "liar! cheat!",
		"you burned my house down!", "come on then!", "I'll teach you!");

	@Nullable
	private DrifterEntity one;
	@Nullable
	private DrifterEntity other;
	/** The one the player helped (by hitting the other), or null. */
	@Nullable
	private DrifterEntity helped;
	@Nullable
	private DrifterEntity winner;
	private boolean thanked;
	private boolean shouted;

	private Feud(ServerPlayer player) {
		super(Encounters.Kind.FEUD, player);
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 25.0, 38.0, null, random);
		if (spot == null) {
			return null;
		}

		Feud feud = new Feud(player);
		for (int i = 0; i < 2; i++) {
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + (i == 0 ? -2 : 2), spot.getZ() + random.nextInt(3) - 1);
			DrifterEntity fighter = EncounterSpawner.spawn(level, at == null ? spot : at, i == 0 ? Archetype.CASUAL : Archetype.HOMEBODY, drifter -> {
				EncounterSpawner.fighter(drifter, random.nextInt(2), false, random);
				EncounterSpawner.traits(drifter, 0.8, 0.8, 0.3, 0.8);
				drifter.getInventory().add(EncounterSpawner.valuable(random));
			});
			if (fighter == null) {
				return null;
			}

			feud.join(fighter, FIGHTER);
			EncounterSpawner.add(level, fighter);
			if (i == 0) {
				feud.one = fighter;
			} else {
				feud.other = fighter;
			}
		}

		if (feud.one != null && feud.other != null) {
			think(feud.one, "at it with " + feud.other.getName().getString() + " again");
			think(feud.other, "at it with " + feud.one.getName().getString() + " again");
		}

		return feud;
	}

	@Override
	protected boolean sameSide(DrifterEntity one, DrifterEntity other) {
		return false;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 5;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		DrifterEntity one = this.one;
		DrifterEntity other = this.other;
		boolean oneUp = one != null && one.isAlive();
		boolean otherUp = other != null && other.isAlive();
		if (!oneUp && !otherUp) {
			this.end("both down");
			return;
		}

		// Whose side the player took: the one they didn't hit (dead or not: a blow that ends it counts).
		if (player != null && this.helped == null) {
			if (one != null && hitBy(one, player)) {
				this.helped = other;
			} else if (other != null && hitBy(other, player)) {
				this.helped = one;
			}
		}

		if (oneUp && otherUp) {
			if (!this.shouted && sees(one, other)) {
				this.shouted = true;
				say(one, SHOUTS.get(one.getRandom().nextInt(SHOUTS.size())));
			}

			if (sees(one, other)) {
				one.getDrifterBrain().pursue(one, other, this.now());
			}

			if (sees(other, one)) {
				other.getDrifterBrain().pursue(other, one, this.now());
			}

			return;
		}

		if (this.winner == null) {
			this.winner = oneUp ? one : other;
			this.winner.getDrifterBrain().endPursuit();
			think(this.winner, "won the fight");
		}

		if (this.thanked || this.helped != this.winner || player == null || player.distanceTo(this.winner) > 48.0) {
			this.end(this.thanked ? "the winner thanked them" : "one of them won");
		}
	}

	private static boolean hitBy(DrifterEntity drifter, ServerPlayer player) {
		return drifter.getLastHurtByMob() == player && drifter.tickCount - drifter.getLastHurtByMobTimestamp() < 40;
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		// The fight is the chase's; after it, the winner the player helped goes and thanks them.
		return drifter == this.winner && this.helped == this.winner && !this.thanked && this.player() != null ? 0.95 : 0.0;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return drifter == this.winner ? "thank " + this.playerName : "settle a score";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		return new Script(drifter, "thank " + this.playerName, 20 * 40)
			.then(() -> walkUpTo(drifter, player, 2.5))
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.now(() -> {
				say(drifter, "thanks for the help. here, you earned it.");
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (!stack.isEmpty() && (stack.is(net.minecraft.world.item.Items.EMERALD) || stack.is(net.minecraft.world.item.Items.GOLD_INGOT)
						|| stack.is(net.minecraft.world.item.Items.DIAMOND) || stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE))) {
						drifter.tossForward(stack.split(stack.getCount()));
						break;
					}
				}

				this.thanked = true;
			});
	}
}
