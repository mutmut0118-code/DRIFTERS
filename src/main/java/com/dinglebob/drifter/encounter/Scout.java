package com.dinglebob.drifter.encounter;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * Turning the tables (docs/ENCOUNTERS.md 3): with a price on the player's head, before the hunters come, one of them
 * comes ahead to look: it creeps to where word has it the player is, crouches somewhere it can watch from, spyglass in
 * hand, for a couple of minutes, then slips away to report. Spot it and bring it down and the hunters lose their eyes:
 * they come a day later, one fewer, and its orders (a note in its pack) say who wants the player dead and when they
 * come. Let it go and the hunters come sooner, straight to where it saw them.
 */
final class Scout extends Encounter {
	private static final String SCOUT = "scout";
	/** It watches from this far off. */
	private static final double WATCH_MIN = 22.0;
	private static final double WATCH_MAX = 34.0;
	/** It watches this long once it has seen them, then goes. */
	private static final long WATCHES = 20L * 100;
	/** Got away with what it saw: the hunters come within this long. */
	private static final long REPORTED_DUE = 6000L;
	/** Didn't come back: the hunters come this much later. */
	private static final long LOST_DELAY = 24000L;

	private final String issuer;
	private Vec3 lead;
	@Nullable
	private DrifterEntity scout;
	/** Where it watches from. */
	@Nullable
	private Vec3 perch;
	/** Where it last saw them (what it reports), and when it first did. */
	@Nullable
	private BlockPos sawAt;
	private long firstSaw = -1;
	private boolean leaving;
	private boolean caught;
	private final Vec3 escape;

	private Scout(ServerPlayer player, String issuer, Vec3 lead, Vec3 escape) {
		super(Encounters.Kind.SCOUT, player);
		this.issuer = issuer;
		this.lead = lead;
		this.escape = escape;
	}

	@Nullable
	static Encounter start(ServerPlayer player, Encounters.PlayerState state) {
		if (state.bounty == null) {
			return null;
		}

		// What word got round (a debug bounty comes with where they are).
		Vec3 lead = BountyHunters.whereabouts(player);
		if (lead == null && state.bounty.lead() != null) {
			lead = Vec3.atBottomCenterOf(state.bounty.lead());
		}

		if (lead == null || lead.distanceTo(player.position()) > 120.0) {
			return null;
		}

		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos spot = EncounterSpawner.hiddenSpot(level, lead, 45.0, 65.0, null, random);
		if (spot == null) {
			return null;
		}

		String issuer = state.bounty.issuer();
		Vec3 from = Vec3.atBottomCenterOf(spot);
		Scout encounter = new Scout(player, issuer, lead, beyond(lead, from, 60.0));
		long dueAt = state.bounty.dueAt();
		String name = player.getGameProfile().getName();
		DrifterEntity scout = EncounterSpawner.spawn(level, spot, Archetype.HERMIT, drifter -> {
			EncounterSpawner.fighter(drifter, 0, false, random);
			EncounterSpawner.traits(drifter, 0.5, 0.3, 0.9, 0.5);
			drifter.setCustomNameVisible(false);
			drifter.getInventory().add(new ItemStack(Items.SPYGLASS));
			// Its orders: who, for whom, when.
			ItemStack note = new ItemStack(Items.PAPER);
			note.set(DataComponents.CUSTOM_NAME, Component.literal("Orders"));
			note.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("Find " + name + ". Report back."),
				Component.literal("The price on their head is from " + issuer + "."),
				Component.literal("We move on them on day " + (dueAt / 24000L + 1) + "."))));
			drifter.getInventory().add(note);
			drifter.getSocial().getOrMeet(player.getUUID(), name).trust = -40.0;
		});
		if (scout == null) {
			return null;
		}

		encounter.join(scout, SCOUT);
		encounter.scout = scout;
		EncounterSpawner.add(level, scout);
		think(scout, "scouting for the hunters: " + name + ", last heard of near " + BlockPos.containing(lead).toShortString());
		return encounter;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 8;
	}

	@Override
	protected void update() {
		DrifterEntity scout = this.scout;
		ServerPlayer player = this.player();
		Encounters.PlayerState state = Encounters.state(this.player);
		if (scout == null || !scout.isAlive()) {
			// Brought down: the hunters lose their eyes.
			if (state.bounty != null && !this.caught) {
				this.caught = true;
				state.bounty = state.bounty.scouted(state.bounty.dueAt() + LOST_DELAY, null, state.bounty.fewer() + 1);
				Encounters.changed();
			}

			this.end("the scout is dead");
			return;
		}

		if (player == null) {
			return;
		}

		if (sees(scout, player)) {
			this.sawAt = player.blockPosition();
			this.lead = player.position();
			if (this.firstSaw < 0) {
				this.firstSaw = this.now();
				think(scout, "found " + this.playerName + " at " + this.sawAt.toShortString());
			}
		}

		// Seen and come for (close, or hit): it runs for it.
		boolean hit = scout.getLastHurtByMob() == player && scout.tickCount - scout.getLastHurtByMobTimestamp() < 40;
		if (!this.leaving && (hit || knowsWhere(scout, player) && player.distanceTo(scout) < 10.0)) {
			this.leaving = true;
			think(scout, "spotted: running for it");
		}

		if (!this.leaving && this.firstSaw >= 0 && this.now() - this.firstSaw > WATCHES) {
			this.leaving = true;
			think(scout, "seen enough: back to the others");
		}

		// Out of sight and far: it got away with what it saw.
		if (this.leaving && player.distanceTo(scout) > 64.0 && !player.hasLineOfSight(scout)) {
			if (state.bounty != null && this.sawAt != null) {
				state.bounty = state.bounty.scouted(Math.min(state.bounty.dueAt(), this.now() + REPORTED_DUE), this.sawAt, state.bounty.fewer());
			} else if (state.bounty != null) {
				state.bounty = state.bounty.scouted(state.bounty.dueAt(), state.bounty.lead(), state.bounty.fewer());
			}

			Encounters.changed();
			this.end(this.sawAt != null ? "it got away with where they are" : "it never found them");
		}
	}

	/** Where it watches from: ground some way off the player, a little above them if it can, out of their sight now. */
	@Nullable
	private Vec3 perch(ServerPlayer player, DrifterEntity scout) {
		ServerLevel level = this.level;
		var random = scout.getRandom();
		Vec3 best = null;
		double bestScore = -Double.MAX_VALUE;
		for (int i = 0; i < 24; i++) {
			// On its own side of them.
			Vec3 side = scout.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double angle = Math.atan2(side.z, side.x) + (random.nextDouble() - 0.5) * 1.6;
			double distance = WATCH_MIN + random.nextDouble() * (WATCH_MAX - WATCH_MIN);
			BlockPos spot = EncounterSpawner.ground(level, (int)Math.floor(player.getX() + Math.cos(angle) * distance),
				(int)Math.floor(player.getZ() + Math.sin(angle) * distance));
			if (spot == null) {
				continue;
			}

			double score = Math.min(6, spot.getY() - player.getY()) + (EncounterSpawner.hidden(level, spot) ? 0.0 : -3.0);
			if (score > bestScore) {
				bestScore = score;
				best = Vec3.atBottomCenterOf(spot);
			}
		}

		return best;
	}

	/** However it ends, that was their scout (no second one). */
	@Override
	public void end(String why) {
		Encounters.PlayerState state = Encounters.state(this.player);
		if (state.bounty != null && state.bounty.scoutAt() >= 0) {
			state.bounty = state.bounty.scouted(state.bounty.dueAt(), state.bounty.lead(), state.bounty.fewer());
			Encounters.changed();
		}

		super.end(why);
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return 0.93;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.leaving ? "slip away" : this.firstSaw >= 0 ? "watch " + this.playerName : "look for " + this.playerName;
	}

	/** One of the hunters: bringing it down is no crime. */
	@Override
	public Killing killing(DrifterEntity drifter, Player player) {
		return Killing.NONE;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (this.leaving || player == null) {
			return new Script(drifter, "slip away", 20 * 60).then(() -> new NavigateTask(drifter, this.escape, 4.0, "slip away", NavigateTask.Pace.TRAVEL));
		}

		if (this.firstSaw < 0 || this.perch == null) {
			// Creeping in toward where word has it they are, and a look round.
			if (this.firstSaw >= 0 && this.perch == null) {
				this.perch = this.perch(player, drifter);
			}

			Vec3 to = this.perch != null ? this.perch : this.lead;
			return new Script(drifter, this.describe(drifter), 20 * 40)
				.then(() -> drifter.position().distanceTo(to) > 3.0 ? new NavigateTask(drifter, to, 2.0, "creep closer", NavigateTask.Pace.SNEAK) : null)
				.then(() -> new com.dinglebob.drifter.executor.task.LookAroundTask(drifter, 2, 10));
		}

		// At its perch: crouched, spyglass in hand, watching them.
		Vec3 perch = this.perch;
		return new Script(drifter, "watch " + this.playerName, 20 * 30)
			.then(() -> drifter.position().distanceTo(perch) > 2.0 ? new NavigateTask(drifter, perch, 1.0, "to a spot to watch from", NavigateTask.Pace.SNEAK) : null)
			.then(() -> new SelectItemTask(drifter, stack -> stack.is(Items.SPYGLASS), "the spyglass"))
			.then(() -> new Hold(drifter, "watch " + this.playerName + " through a spyglass", 20 * 20, true, () -> {
				ServerPlayer seen = this.player();
				return seen != null ? seen.getEyePosition() : null;
			}));
	}

	@Override
	public String status() {
		return super.status() + (this.leaving ? ", leaving" : this.firstSaw >= 0 ? ", watching them" : ", looking near " + BlockPos.containing(this.lead).toShortString());
	}
}
