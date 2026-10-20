package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * A mugging (docs/ENCOUNTERS.md 5): out on the road, an armed Drifter blocks the way: "hand over five emeralds, or your
 * life". Throw it that much (emeralds, gold, a diamond counts as three) and it goes; refuse (walk off, or hit it) and it
 * attacks, no more warnings.
 */
final class Mugging extends Encounter {
	private static final String MUGGER = "mugger";
	/** It waits this long for the money. */
	private static final long WAITS = 20L * 40;

	@Nullable
	private DrifterEntity mugger;
	private final int demand;
	private long askedAt = -1;
	private boolean refused;
	private boolean paidUp;

	private Mugging(ServerPlayer player, int demand) {
		super(Encounters.Kind.MUGGING, player);
		this.demand = demand;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 22.0, 34.0, player.getLookAngle(), random);
		if (spot == null) {
			return null;
		}

		int tier = EncounterSpawner.gearTier(player);
		Mugging mugging = new Mugging(player, 4 + random.nextInt(5) + 2 * tier);
		// (Not a Bandit: those attack on sight. This one talks first.)
		DrifterEntity mugger = EncounterSpawner.spawn(level, spot, Archetype.PVP_SWEAT, drifter -> {
			EncounterSpawner.fighter(drifter, tier, false, random);
			EncounterSpawner.traits(drifter, 0.8, 0.85, 0.25, 0.6);
		});
		if (mugger == null) {
			return null;
		}

		mugging.join(mugger, MUGGER);
		mugging.mugger = mugger;
		EncounterSpawner.add(level, mugger);
		think(mugger, "waiting by the road for an easy mark");
		return mugging;
	}

	/** What it has got so far: emeralds and gold one each, a diamond three. */
	private static int value(DrifterEntity drifter) {
		return carried(drifter, s -> s.is(Items.EMERALD)) + carried(drifter, s -> s.is(Items.GOLD_INGOT)) + 3 * carried(drifter, s -> s.is(Items.DIAMOND));
	}

	@Override
	protected long longest() {
		return 20L * 60 * 6;
	}

	@Override
	protected void update() {
		DrifterEntity mugger = this.mugger;
		ServerPlayer player = this.player();
		if (mugger == null || !mugger.isAlive()) {
			this.end("the mugger is dead");
			return;
		}

		if (player == null || this.askedAt >= 0 && player.distanceTo(mugger) > 64.0) {
			this.end("they got away");
			return;
		}

		if (this.askedAt < 0 || this.paidUp) {
			return;
		}

		if (!this.refused) {
			if (value(mugger) >= this.demand) {
				this.paidUp = true;
				say(mugger, "pleasure doing business. move along.");
				this.end("they paid");
				return;
			}

			boolean hit = mugger.getLastHurtByMob() == player && mugger.tickCount - mugger.getLastHurtByMobTimestamp() < 40;
			if (hit || this.now() - this.askedAt > WAITS || player.distanceTo(mugger) > 16.0) {
				this.refused = true;
				say(mugger, "your choice.");
			}

			return;
		}

		if (sees(mugger, player)) {
			mugger.getDrifterBrain().pursue(mugger, player, this.now());
		}
	}

	@Override
	protected void playerDied() {
		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return this.player() == null || this.refused ? 0.0 : 0.92;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.askedAt < 0 ? "stop " + this.playerName + " on the road" : this.refused ? "rob " + this.playerName : "wait for the money";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return this.refused && player.getUUID().equals(this.player);
	}

	@Override
	public boolean relentless(DrifterEntity drifter) {
		return this.refused;
	}

	/** "Your money or your life": whenever they strike it down, it had it coming. */
	@Override
	public Killing killing(DrifterEntity drifter, Player player) {
		return Killing.NONE;
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
						say(drifter, "stop right there, " + this.playerName + ". " + this.demand + " emeralds (or gold, or diamonds), or your life. throw it here.");
						this.askedAt = this.now();
					}
				});
		}

		// Waiting: facing them, picking up what they throw.
		return new Script(drifter, "wait for the money", 20 * 6)
			.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
			.then(() -> thrownBy(drifter, player, 6.0).isEmpty() ? null : new CollectItemsTask(drifter, 6.0));
	}
}
