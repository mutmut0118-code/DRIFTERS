package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.Disposition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * An escort (docs/ENCOUNTERS.md 13): a traveller on its way to a village asks the player to walk with it ("say yes"),
 * and pays when they get there. It leads the way (it knows where it's going), waits when they fall behind, goes on alone
 * if they leave it. Half the time bandits jump them on the way, going for the traveller first.
 */
final class Escort extends Encounter {
	private static final String TRAVELLER = "traveller";
	private static final String BANDIT = "bandit";
	/** The village is this far off. */
	private static final double MIN_TRIP = 120.0;
	private static final double MAX_TRIP = 600.0;
	/** There once it's this near the village. */
	private static final double ARRIVED = 40.0;
	/** It waits when they're this far behind; gives up when they're this far for this long. */
	private static final double BEHIND = 20.0;
	private static final double LEFT_BEHIND = 80.0;
	private static final long LEFT_FOR = 20L * 60;
	/** Each leg of the walk. */
	private static final double LEG = 28.0;

	private final Vec3 village;
	private final Vec3 from;
	private final int pay;
	@Nullable
	private DrifterEntity traveller;
	private long askedAt = -1;
	private boolean accepted;
	private boolean arrived;
	private boolean paid;
	/** Bandits jump them at this much of the way (or never: below 0). */
	private final double ambushAt;
	private boolean ambushed;
	private long farSince = -1;
	private long waitedAt = Long.MIN_VALUE / 2;

	private Escort(ServerPlayer player, Vec3 village, Vec3 from, int pay, double ambushAt) {
		super(Encounters.Kind.ESCORT, player);
		this.village = village;
		this.from = from;
		this.pay = pay;
		this.ambushAt = ambushAt;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos found = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 40, false);
		if (found == null) {
			return null;
		}

		Vec3 village = new Vec3(found.getX() + 0.5, player.getY(), found.getZ() + 0.5);
		double trip = village.multiply(1.0, 0.0, 1.0).distanceTo(player.position().multiply(1.0, 0.0, 1.0));
		if (trip < MIN_TRIP || trip > MAX_TRIP) {
			return null;
		}

		BlockPos spot = EncounterSpawner.hiddenSpot(level, player.position(), 22.0, 34.0, null, random);
		if (spot == null) {
			return null;
		}

		int pay = 6 + random.nextInt(4) + (int)(trip / 100.0);
		Escort escort = new Escort(player, village, player.position(), pay, random.nextBoolean() ? 0.35 + random.nextDouble() * 0.3 : -1.0);
		DrifterEntity traveller = EncounterSpawner.spawn(level, spot, Archetype.NOMAD, drifter -> {
			EncounterSpawner.traits(drifter, 0.3, 0.1, 0.8, 0.2);
			drifter.getInventory().add(new ItemStack(Items.EMERALD, pay));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 6));
			drifter.getInventory().add(new ItemStack(Items.WOODEN_SWORD));
		});
		if (traveller == null) {
			return null;
		}

		escort.join(traveller, TRAVELLER);
		escort.traveller = traveller;
		EncounterSpawner.add(level, traveller);
		think(traveller, "on its way to the village by " + BlockPos.containing(village).toShortString() + ", and scared of the road");
		return escort;
	}

	/** How much of the way it has come (0 to 1). */
	private double progress(DrifterEntity traveller) {
		double total = this.from.multiply(1.0, 0.0, 1.0).distanceTo(this.village.multiply(1.0, 0.0, 1.0));
		double left = traveller.position().multiply(1.0, 0.0, 1.0).distanceTo(this.village.multiply(1.0, 0.0, 1.0));
		return total <= 0.0 ? 1.0 : Math.max(0.0, 1.0 - left / total);
	}

	@Override
	protected long longest() {
		return 20L * 60 * 25;
	}

	@Override
	protected boolean sameSide(DrifterEntity one, DrifterEntity other) {
		return one.encounterRole().equals(other.encounterRole());
	}

	@Override
	protected void chat(ServerPlayer player, String words) {
		if (this.askedAt >= 0 && !this.accepted && saidAny(words, "yes", "ok", "okay", "sure", "yeah", "yep", "fine", "go", "deal", "alright")) {
			this.accepted = true;
			this.traveller.getDrifterBrain().poke();
			say(this.traveller, "great! stay close, and keep your eyes open.");
		} else if (this.askedAt >= 0 && !this.accepted && saidAny(words, "no", "nope", "nah")) {
			say(this.traveller, "oh... alright. I'll take my chances.");
			this.end("they said no");
		}
	}

	@Override
	protected void update() {
		DrifterEntity traveller = this.traveller;
		ServerPlayer player = this.player();
		if (traveller == null || !traveller.isAlive()) {
			this.end("the traveller was killed");
			return;
		}

		if (player == null) {
			this.end("they're gone");
			return;
		}

		if (this.askedAt >= 0 && !this.accepted && this.now() - this.askedAt > 20L * 60) {
			say(traveller, "never mind, then.");
			this.end("no answer");
			return;
		}

		if (!this.accepted) {
			return;
		}

		// There.
		if (!this.arrived && traveller.position().multiply(1.0, 0.0, 1.0).distanceTo(this.village.multiply(1.0, 0.0, 1.0)) < ARRIVED) {
			this.arrived = true;
			think(traveller, "made it to the village");
		}

		if (this.paid || this.arrived && player.distanceTo(traveller) > 64.0) {
			this.end(this.paid ? "got there, and paid them" : "got there");
			return;
		}

		// Left behind for good.
		if (player.distanceTo(traveller) > LEFT_BEHIND) {
			if (this.farSince < 0) {
				this.farSince = this.now();
			} else if (this.now() - this.farSince > LEFT_FOR) {
				say(traveller, "fine, I'll go on alone.");
				this.end("they left it");
				return;
			}
		} else {
			this.farSince = -1;
		}

		// The bandits: ahead on the road, partway there.
		if (!this.ambushed && this.ambushAt >= 0.0 && this.progress(traveller) >= this.ambushAt) {
			this.ambushed = true;
			this.ambush(player, traveller);
		}

		for (DrifterEntity bandit : this.members) {
			if (bandit == traveller) {
				continue;
			}

			boolean hit = bandit.getLastHurtByMob() == player && bandit.tickCount - bandit.getLastHurtByMobTimestamp() < 100;
			if (sees(bandit, player) && (hit || bandit.distanceTo(player) < 6.0)) {
				bandit.getDrifterBrain().pursue(bandit, player, this.now());
			} else if (sees(bandit, traveller)) {
				bandit.getDrifterBrain().pursue(bandit, traveller, this.now());
			}
		}
	}

	/** Two bandits come out ahead of them. */
	private void ambush(ServerPlayer player, DrifterEntity traveller) {
		ServerLevel level = this.level;
		var random = level.getRandom();
		Vec3 ahead = traveller.position().add(this.village.subtract(traveller.position()).multiply(1.0, 0.0, 1.0).normalize().scale(30.0));
		BlockPos spot = EncounterSpawner.hiddenSpot(level, ahead, 0.0, 14.0, null, random);
		if (spot == null) {
			return;
		}

		int tier = EncounterSpawner.gearTier(player);
		for (int i = 0; i < 2; i++) {
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + random.nextInt(5) - 2, spot.getZ() + random.nextInt(5) - 2);
			DrifterEntity bandit = EncounterSpawner.spawn(level, at == null ? spot : at, Archetype.BANDIT, drifter -> {
				drifter.getSocial().setDisposition(Disposition.BANDIT);
				EncounterSpawner.fighter(drifter, Math.max(0, tier - 1), false, random);
				EncounterSpawner.traits(drifter, 0.8, 0.9, 0.2, 0.5);
			});
			if (bandit != null) {
				this.join(bandit, BANDIT);
				EncounterSpawner.add(level, bandit);
				think(bandit, "waiting on the road for travellers");
				if (i == 0) {
					say(bandit, "well, well. a traveller with a bodyguard.");
				}
			}
		}

		think(traveller, "bandits on the road!");
	}

	@Override
	protected void playerDied() {
		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		if (drifter != this.traveller) {
			var pursuit = drifter.getDrifterBrain().pursuit();
			return pursuit != null && pursuit.active(drifter, this.now()) ? 0.0 : 0.9;
		}

		return this.paid || this.player() == null ? 0.0 : 0.92;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return drifter != this.traveller || this.askedAt < 0 || this.accepted;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		if (drifter != this.traveller) {
			return "rob the traveller";
		}

		return this.askedAt < 0 ? "ask " + this.playerName + " to walk with it" : !this.accepted ? "wait for an answer"
			: this.arrived ? "pay " + this.playerName : "walk to the village";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return drifter != this.traveller && player.getUUID().equals(this.player);
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (player == null) {
			return null;
		}

		if (drifter != this.traveller) {
			return search(drifter, "rob the traveller", "the traveller", () -> this.traveller != null ? this.traveller.position() : player.position(), 12.0);
		}

		if (this.askedAt < 0) {
			String way = direction(player.position(), this.village);
			int far = (int)Math.round(this.village.multiply(1.0, 0.0, 1.0).distanceTo(player.position().multiply(1.0, 0.0, 1.0)) / 10.0) * 10;
			return new Script(drifter, this.describe(drifter), 20 * 60)
				.then(() -> walkUpTo(drifter, player, 3.0))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					if (drifter.distanceTo(player) < 7.0) {
						say(drifter, "excuse me, " + this.playerName + "! I'm going to the village " + way + " of here, about " + far
							+ " blocks. the roads aren't safe... would you walk with me? " + this.pay + " emeralds when we get there. (say yes)");
						this.askedAt = this.now();
					}
				});
		}

		if (!this.accepted) {
			return new Script(drifter, "wait for an answer", 20 * 5).until(() -> this.accepted).then(() -> new LookAtTask(drifter, player.getEyePosition(), 40));
		}

		if (this.arrived) {
			return new Script(drifter, "pay " + this.playerName, 20 * 40)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					say(drifter, "we made it! thank you, really. as promised.");
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if (stack.is(Items.EMERALD)) {
							drifter.tossForward(stack.split(stack.getCount()));
						}
					}

					think(drifter, this.playerName + " walked it to the village");
					this.paid = true;
				});
		}

		// They fell behind: it waits for them.
		if (player.distanceTo(drifter) > BEHIND) {
			return new Script(drifter, "wait for " + this.playerName, 20 * 6).until(() -> player.distanceTo(drifter) < BEHIND * 0.6)
				.now(() -> {
					if (this.now() - this.waitedAt > 20L * 60) {
						this.waitedAt = this.now();
						say(drifter, "wait up! don't leave me out here.");
					}
				})
				.then(() -> new Hold(drifter, "wait for " + this.playerName, 20 * 5, false, () -> {
					var awareness = drifter.getSenses().awarenessOf(player);
					return awareness != null && awareness.lastSeenPos != null ? awareness.lastSeenPos.add(0.0, 1.5, 0.0) : null;
				}));
		}

		// On toward the village, a leg at a time (open ground on the way).
		Vec3 leg = this.leg(drifter);
		return new Script(drifter, "walk to the village", 20 * 40)
			.then(() -> new NavigateTask(drifter, leg, 3.0, "on toward the village", NavigateTask.Pace.STROLL));
	}

	/** The next stretch: ground about a leg on toward the village (or the village itself, near the end). */
	private Vec3 leg(DrifterEntity drifter) {
		Vec3 here = drifter.position();
		Vec3 to = this.village.subtract(here).multiply(1.0, 0.0, 1.0);
		if (to.length() <= LEG) {
			BlockPos end = EncounterSpawner.ground(this.level, (int)Math.floor(this.village.x), (int)Math.floor(this.village.z));
			return end != null ? Vec3.atBottomCenterOf(end) : this.village;
		}

		double base = Math.atan2(to.z, to.x);
		for (double turn : new double[] {0.0, 0.35, -0.35, 0.7, -0.7}) {
			for (double length : new double[] {LEG, LEG * 0.7}) {
				int x = (int)Math.floor(here.x + Math.cos(base + turn) * length);
				int z = (int)Math.floor(here.z + Math.sin(base + turn) * length);
				BlockPos ground = EncounterSpawner.ground(this.level, x, z);
				if (ground != null) {
					return Vec3.atBottomCenterOf(ground);
				}
			}
		}

		return here.add(to.normalize().scale(LEG));
	}

	@Override
	public String status() {
		DrifterEntity traveller = this.traveller;
		return super.status() + (this.accepted ? ", " + (traveller == null ? 0 : (int)Math.round(this.progress(traveller) * 100)) + "% of the way" : ", asking")
			+ " to the village by " + BlockPos.containing(this.village).toShortString() + (this.ambushed ? ", bandits!" : "");
	}
}
