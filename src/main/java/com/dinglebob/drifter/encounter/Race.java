package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A race (docs/ENCOUNTERS.md 19): someone walks up and points at a hilltop: "first one up there wins". Say "go" and it
 * counts down from three, and you're off. Get there first and it pays its bet; lose and it crows about it (it bet only
 * its own emeralds: you lose nothing but face).
 */
final class Race extends Encounter {
	private static final String RACER = "racer";
	/** There: this near the top (across) and up on it. */
	private static final double FINISH = 3.0;

	private final BlockPos top;
	private final int bet;
	@Nullable
	private DrifterEntity racer;
	private long askedAt = -1;
	private boolean ready;
	private int countdown = -1;
	private boolean racing;
	/** Who got there first: the player (true), the racer (false), or nobody yet (null). */
	@Nullable
	private Boolean playerWon;
	private long finishedAt;
	private boolean paid;

	private Race(ServerPlayer player, BlockPos top, int bet) {
		super(Encounters.Kind.RACE, player);
		this.top = top;
		this.bet = bet;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		// The hilltop: the highest ground some way off, well above here.
		BlockPos top = null;
		for (int i = 0; i < 60; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 50.0 + random.nextDouble() * 50.0;
			BlockPos spot = EncounterSpawner.ground(level, (int)Math.floor(player.getX() + Math.cos(angle) * distance), (int)Math.floor(player.getZ() + Math.sin(angle) * distance));
			if (spot != null && spot.getY() - player.getBlockY() >= 6 && spot.getY() - player.getBlockY() <= 40 && (top == null || spot.getY() > top.getY())) {
				top = spot;
			}
		}

		if (top == null) {
			return null;
		}

		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 22.0, 32.0, null, random);
		if (spot == null) {
			return null;
		}

		int bet = 3 + random.nextInt(4);
		Race race = new Race(player, top, bet);
		DrifterEntity racer = EncounterSpawner.spawn(level, spot, Archetype.SPEEDRUNNER, drifter -> {
			EncounterSpawner.traits(drifter, 0.7, 0.3, 0.3, 0.2);
			drifter.getInventory().add(new ItemStack(Items.EMERALD, bet));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 4));
		});
		if (racer == null) {
			return null;
		}

		race.join(racer, RACER);
		race.racer = racer;
		EncounterSpawner.add(level, racer);
		think(racer, "fancies a race up the hill at " + top.toShortString());
		return race;
	}

	private boolean atTop(Entity entity) {
		Vec3 top = Vec3.atBottomCenterOf(this.top);
		return entity.position().multiply(1.0, 0.0, 1.0).distanceTo(top.multiply(1.0, 0.0, 1.0)) <= FINISH && entity.getY() >= this.top.getY() - 1.0;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 8;
	}

	@Override
	protected void chat(ServerPlayer player, String words) {
		if (this.askedAt >= 0 && !this.ready && saidAny(words, "go", "ready", "yes", "ok", "okay", "sure", "deal", "lets", "yeah")) {
			this.ready = true;
			this.countdown = 3;
		} else if (this.askedAt >= 0 && !this.ready && saidAny(words, "no", "nope", "nah")) {
			say(this.racer, "suit yourself.");
			this.end("they said no");
		}
	}

	@Override
	protected void update() {
		DrifterEntity racer = this.racer;
		ServerPlayer player = this.player();
		if (racer == null || !racer.isAlive() || player == null) {
			this.end("gone");
			return;
		}

		if (this.askedAt >= 0 && !this.ready && this.now() - this.askedAt > 20L * 60) {
			say(racer, "chicken.");
			this.end("no answer");
			return;
		}

		// 3, 2, 1, go!
		if (this.countdown > 0) {
			say(racer, this.countdown + "...");
			this.countdown--;
			return;
		}

		if (this.countdown == 0) {
			this.countdown = -1;
			this.racing = true;
			say(racer, "go!");
			racer.getDrifterBrain().poke();
			return;
		}

		if (this.racing && this.playerWon == null) {
			if (this.atTop(player)) {
				this.playerWon = true;
				this.finishedAt = this.now();
				say(racer, "no way! you win. fair and square.");
			} else if (this.atTop(racer)) {
				this.playerWon = false;
				this.finishedAt = this.now();
				say(racer, "ha! I win! better luck next time, " + this.playerName + ".");
				think(racer, "beat " + this.playerName + " up the hill");
			}

			return;
		}

		if (this.playerWon != null && (this.paid || !this.playerWon && this.now() - this.finishedAt > 20L * 20 || player.distanceTo(racer) > 64.0)) {
			this.end(this.playerWon ? "the player won" + (this.paid ? ", and was paid" : "") : "the racer won");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return this.player() == null || this.paid ? 0.0 : 0.95;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.askedAt < 0 ? "challenge " + this.playerName + " to a race" : !this.racing ? "wait for the off"
			: this.playerWon == null ? "race up the hill" : this.playerWon ? "pay up" : "gloat";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		if (this.askedAt < 0) {
			String way = direction(player.position(), Vec3.atCenterOf(this.top));
			return new Script(drifter, this.describe(drifter), 20 * 60)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, Vec3.atCenterOf(this.top), 20))
				.now(() -> {
					if (drifter.distanceTo(player) < 7.0) {
						say(drifter, "hey " + this.playerName + ", race you! first one to the top of that hill " + way + " of here (" + coords(this.top) + ") wins. "
							+ this.bet + " emeralds says I beat you. say go when you're ready!");
						this.askedAt = this.now();
					}
				});
		}

		if (!this.racing) {
			// On the line beside them, looking at the hill.
			return new Script(drifter, "wait for the off", 20 * 3).until(() -> this.racing).then(() -> new Hold(drifter, "wait for the off", 20 * 2, false, () -> Vec3.atCenterOf(this.top)));
		}

		if (this.playerWon == null) {
			return new Script(drifter, "race up the hill", 20 * 60 * 3)
				.then(() -> new NavigateTask(drifter, Vec3.atBottomCenterOf(this.top), 1.0, "race to the top", NavigateTask.Pace.TRAVEL));
		}

		if (this.playerWon) {
			return new Script(drifter, "pay up", 20 * 40)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if (stack.is(Items.EMERALD)) {
							drifter.tossForward(stack.split(stack.getCount()));
						}
					}

					say(drifter, "a bet's a bet. you're quick!");
					think(drifter, "lost a race to " + this.playerName);
					this.paid = true;
				});
		}

		return new Script(drifter, "gloat", 20 * 10).then(() -> new Hold(drifter, "catch its breath", 20 * 8, false, player::getEyePosition));
	}

	@Override
	public String status() {
		return super.status() + " to " + this.top.toShortString() + (this.playerWon == null ? this.racing ? ", racing" : "" : this.playerWon ? ", the player won" : ", the racer won");
	}
}
