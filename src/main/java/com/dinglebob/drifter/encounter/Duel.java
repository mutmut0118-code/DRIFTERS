package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A duel challenge (docs/ENCOUNTERS.md 18): a PvP sweat walks up and bets diamonds on a 1v1. Hit it to say yes. It's a
 * fight to a few hearts, not to the death: down to three hearts it gives up and pays its stake; the player down to four
 * and it stops, laughing, and takes nothing. No answer in half a minute (or walking off): "coward", and it goes.
 * Killed in the fight, it's a kill like any Drifter's; killed after it gave up, it's murder ({@link Encounters}: a price
 * on their head at once, and its friends come for revenge).
 */
final class Duel extends Encounter {
	private static final String CHALLENGER = "challenger";
	/** It gives up at this health, and stops when the player is down to this. */
	private static final float YIELDS_AT = 6.0F;
	private static final float SPARES_AT = 8.0F;
	private static final long ANSWER_WITHIN = 20L * 30;

	@Nullable
	private DrifterEntity challenger;
	private final int stake;
	private long askedAt = -1;
	private boolean accepted;
	/** Over: who won (the player, or not). */
	private boolean settled;
	private boolean playerWon;
	private boolean paid;
	/** It said it gave up, hit again. */
	private boolean protested;

	private Duel(ServerPlayer player, int stake) {
		super(Encounters.Kind.DUEL, player);
		this.stake = stake;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 25.0, 38.0, null, random);
		if (spot == null) {
			return null;
		}

		int stake = 2 + random.nextInt(4);
		int tier = EncounterSpawner.gearTier(player);
		Duel duel = new Duel(player, stake);
		DrifterEntity challenger = EncounterSpawner.spawn(level, spot, Archetype.PVP_SWEAT, drifter -> {
			EncounterSpawner.fighter(drifter, tier, false, random);
			EncounterSpawner.traits(drifter, 0.9, 0.8, 0.2, 0.3);
			drifter.getInventory().add(new ItemStack(Items.DIAMOND, stake));
		});
		if (challenger == null) {
			return null;
		}

		duel.join(challenger, CHALLENGER);
		duel.challenger = challenger;
		EncounterSpawner.add(level, challenger);
		think(challenger, "looking for a worthy fight");
		return duel;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 6;
	}

	@Override
	protected void update() {
		DrifterEntity challenger = this.challenger;
		ServerPlayer player = this.player();
		if (challenger == null || !challenger.isAlive()) {
			this.end("the challenger is dead");
			return;
		}

		if (player == null) {
			this.end("they're gone");
			return;
		}

		if (this.askedAt < 0) {
			return;
		}

		if (!this.accepted) {
			boolean hit = challenger.getLastHurtByMob() == player && challenger.tickCount - challenger.getLastHurtByMobTimestamp() < 40;
			if (hit) {
				this.accepted = true;
				say(challenger, line(challenger, "duel.accept"));
			} else if (this.now() - this.askedAt > ANSWER_WITHIN || player.distanceTo(challenger) > 24.0) {
				say(challenger, line(challenger, "duel.coward"));
				this.end("no answer");
			}

			return;
		}

		if (!this.settled) {
			if (!this.judge(challenger, player) && sees(challenger, player)) {
				challenger.getDrifterBrain().pursue(challenger, player, this.now());
			}

			return;
		}

		if (this.paid || player.distanceTo(challenger) > 48.0) {
			this.end(this.paid ? "the stake is paid" : "they walked off with nothing");
		}
	}

	/** Down to a few hearts, one of them: it's over (true). */
	private boolean judge(DrifterEntity challenger, ServerPlayer player) {
		if (!this.accepted || this.settled) {
			return false;
		}

		if (challenger.getHealth() <= YIELDS_AT) {
			this.settle(challenger, true);
			say(challenger, line(challenger, "duel.yield"));
			return true;
		}

		if (player.getHealth() <= SPARES_AT) {
			this.settle(challenger, false);
			say(challenger, line(challenger, "duel.spare"));
			this.end("the player lost");
			return true;
		}

		return false;
	}

	// As each blow lands, not a second later (found looking at the code: a crit could take it from above three hearts past
	// the check and on to dead).
	@Override
	protected void hurt(DrifterEntity drifter, DamageSource source) {
		ServerPlayer player = this.player();
		if (player == null) {
			return;
		}

		if (!this.judge(drifter, player) && this.settled && this.playerWon && source.getEntity() == player && !this.protested) {
			// Hit after it gave up: it says so (from here on, killing it is murder).
			this.protested = true;
			say(drifter, line(drifter, "duel.after_yield"));
			think(drifter, this.playerName + " kept hitting it after it gave up");
		}
	}

	@Override
	protected void playerHurt(ServerPlayer player, DamageSource source) {
		if (this.challenger != null && this.challenger.isAlive()) {
			this.judge(this.challenger, player);
		}
	}

	/** Killed in the fight it asked for: a kill like any; after it gave up: murder. */
	@Override
	public Killing killing(DrifterEntity drifter, Player player) {
		return player.getUUID().equals(this.player) && this.settled && this.playerWon ? Killing.MURDER : Killing.KILL;
	}

	/** The fight stops: no more chase. */
	private void settle(DrifterEntity challenger, boolean playerWon) {
		this.settled = true;
		this.playerWon = playerWon;
		challenger.getDrifterBrain().endPursuit();
		think(challenger, playerWon ? "lost a duel to " + this.playerName : "won a duel against " + this.playerName);
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		if (this.player() == null) {
			return 0.0;
		}

		// Asking, waiting for an answer, paying up; the fight itself is the chase's.
		if (this.askedAt < 0 || !this.accepted) {
			return 0.92;
		}

		return this.settled && this.playerWon && !this.paid ? 0.95 : 0.0;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.askedAt < 0 ? "challenge " + this.playerName + " to a duel" : this.settled ? "pay up" : "duel " + this.playerName;
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return this.accepted && !this.settled && player.getUUID().equals(this.player);
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		if (this.askedAt < 0) {
			return new Script(drifter, this.describe(drifter), 20 * 90)
				.then(() -> walkUpTo(drifter, player, 3.0))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					if (drifter.distanceTo(player) < 7.0) {
						say(drifter, line(drifter, "duel.ask", "player", this.playerName, "stake", this.stake));
						this.askedAt = this.now();
					}
				});
		}

		if (this.settled && this.playerWon) {
			return new Script(drifter, "pay up", 20 * 40)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if (stack.is(Items.DIAMOND)) {
							drifter.tossForward(stack.split(stack.getCount()));
						}
					}

					say(drifter, line(drifter, "duel.paid"));
					this.paid = true;
				});
		}

		// Waiting for an answer: facing them.
		return new Script(drifter, "wait for an answer", 20 * 5).then(() -> new LookAtTask(drifter, player.getEyePosition(), 40));
	}
}
