package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.social.Disposition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A hostage (docs/ENCOUNTERS.md 14): some way off, bandits have made camp (a signal fire's smoke rises over it) with a
 * captive kneeling by the fire. Now and then the captive shouts for help, typing where it is when nobody's looking.
 * The bandits stand guard and go for anyone who comes close. Bring them all down and the captive, freed, thanks the
 * player with what it hid from them. Bringing the bandits down is no crime; the captive is another matter.
 */
final class Hostage extends Encounter {
	private static final String CAPTIVE = "captive";
	private static final String BANDIT = "bandit";
	/** The guards go for anyone this close (and seen). */
	private static final double GUARD = 22.0;
	private static final long CALLS_EVERY = 20L * 60;

	private final Vec3 camp;
	private final BlockPos fire;
	@Nullable
	private DrifterEntity captive;
	private final List<DrifterEntity> bandits = new ArrayList<>();
	private boolean firePlaced;
	private int fireTries;
	private boolean freed;
	private boolean thanked;
	private boolean alarmed;
	private boolean calledOver;
	private long lastCall = Long.MIN_VALUE / 2;
	private int calls;

	private Hostage(ServerPlayer player, Vec3 camp, BlockPos fire) {
		super(Encounters.Kind.HOSTAGE, player);
		this.camp = camp;
		this.fire = fire;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos found = EncounterSpawner.hiddenSpot(level, player.position(), 60.0, 90.0, null, random);
		if (found == null) {
			return null;
		}

		// The fire on bare ground there (hay under it: a signal fire, its smoke seen from far).
		BlockPos fire = null;
		for (int dx = -3; dx <= 3 && fire == null; dx++) {
			for (int dz = -3; dz <= 3 && fire == null; dz++) {
				BlockPos ground = EncounterSpawner.bareGround(level, found.getX() + dx, found.getZ() + dz);
				if (ground != null && EncounterSpawner.hidden(level, ground)) {
					fire = ground;
				}
			}
		}

		if (fire == null) {
			return null;
		}

		Vec3 camp = Vec3.atBottomCenterOf(fire);
		Hostage hostage = new Hostage(player, camp, fire);
		BlockPos kneel = EncounterSpawner.ground(level, fire.getX() + 2, fire.getZ());
		DrifterEntity captive = EncounterSpawner.spawn(level, kneel == null ? fire.north(2) : kneel, Archetype.CASUAL, drifter -> {
			EncounterSpawner.traits(drifter, 0.2, 0.1, 0.8, 0.3);
			// What it hid from them in its boot.
			drifter.getInventory().add(EncounterSpawner.valuable(random));
			drifter.getInventory().add(EncounterSpawner.valuable(random));
		});
		if (captive == null) {
			return null;
		}

		hostage.join(captive, CAPTIVE);
		hostage.captive = captive;
		EncounterSpawner.add(level, captive);
		think(captive, "taken by bandits, held at their camp by " + fire.toShortString());

		int tier = EncounterSpawner.gearTier(player);
		// One band, in one set of colours.
		var band = EncounterSpawner.band(level, tier, random);
		int count = 2 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0 * i / count + 1.0;
			BlockPos at = EncounterSpawner.ground(level, fire.getX() + (int)Math.round(Math.cos(angle) * 3.0), fire.getZ() + (int)Math.round(Math.sin(angle) * 3.0));
			boolean firstOne = i == 0;
			boolean archer = i == 2;
			DrifterEntity bandit = EncounterSpawner.spawn(level, at == null ? fire.south(2) : at, Archetype.BANDIT, drifter -> {
				drifter.getSocial().setDisposition(Disposition.BANDIT);
				EncounterSpawner.fighter(drifter, band, archer, random);
				EncounterSpawner.traits(drifter, 0.8, 0.85, 0.3, 0.5);
				if (firstOne) {
					drifter.getInventory().add(new ItemStack(Items.HAY_BLOCK));
					drifter.getInventory().add(new ItemStack(Items.CAMPFIRE));
				}
			});
			if (bandit != null) {
				hostage.join(bandit, BANDIT);
				hostage.bandits.add(bandit);
				EncounterSpawner.add(level, bandit);
				think(bandit, "guarding the camp");
			}
		}

		if (hostage.bandits.isEmpty()) {
			captive.discard();
			return null;
		}

		return hostage;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 15;
	}

	// (Captors and captive count as one side: the bandits don't rob it again, and it doesn't run from them.)

	@Override
	protected void update() {
		DrifterEntity captive = this.captive;
		ServerPlayer player = this.player();
		if (captive == null || !captive.isAlive()) {
			this.end("the captive is dead");
			return;
		}

		this.firePlaced |= this.level.getBlockState(this.fire).is(BlockTags.CAMPFIRES) || this.level.getBlockState(this.fire.above()).is(BlockTags.CAMPFIRES);
		this.bandits.removeIf(bandit -> !bandit.isAlive() || bandit.isRemoved());
		if (!this.freed && this.bandits.isEmpty()) {
			this.freed = true;
			say(captive, line(captive, "hostage.freed"));
			think(captive, "freed");
		}

		if (player == null) {
			return;
		}

		if (this.freed) {
			if (this.thanked || player.distanceTo(captive) > 64.0) {
				this.end(this.thanked ? "freed, and thanked them" : "freed");
			}

			return;
		}

		// Calling for help now and then, as a player would type it.
		if (this.now() - this.lastCall > CALLS_EVERY && this.calls < 8 && !sees(captive, player)) {
			this.lastCall = this.now();
			say(captive, line(captive, this.calls++ % 2 == 0 ? "hostage.call1" : "hostage.call2", "coords", coords(captive.blockPosition()), "n",
				this.bandits.size()));
		}

		if (sees(captive, player) && !this.alarmed && !this.calledOver) {
			this.calledOver = true;
			say(captive, line(captive, "hostage.seen", "player", this.playerName));
		}

		// The guards: anyone close (or who hit one of them).
		for (DrifterEntity bandit : this.bandits) {
			boolean hit = bandit.getLastHurtByMob() == player && bandit.tickCount - bandit.getLastHurtByMobTimestamp() < 100;
			if (sees(bandit, player) && (hit || player.position().distanceTo(this.camp) < GUARD || this.alarmed)) {
				if (!this.alarmed) {
					this.alarmed = true;
					say(bandit, line(bandit, "hostage.bandit"));
				}

				bandit.getDrifterBrain().pursue(bandit, player, this.now());
			}
		}
	}

	@Override
	protected void playerDied() {
		this.end("the player is dead");
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		if (drifter == this.captive) {
			return this.thanked ? 0.0 : 0.97;
		}

		var pursuit = drifter.getDrifterBrain().pursuit();
		return pursuit != null && pursuit.active(drifter, this.now()) ? 0.0 : 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return drifter != this.captive || !this.thanked;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		if (drifter == this.captive) {
			return this.freed ? "thank " + this.playerName : "kneel by the fire, a captive";
		}

		return "guard the camp";
	}

	@Override
	public boolean hostileTo(DrifterEntity drifter, Player player) {
		return drifter != this.captive && player.getUUID().equals(this.player);
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (drifter == this.captive) {
			if (this.freed && player != null) {
				return new Script(drifter, "thank " + this.playerName, 20 * 40)
					.then(() -> walkUpTo(drifter, player, 2.5))
					.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
					.now(() -> {
						say(drifter, line(drifter, "hostage.thanks"));
						for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
							ItemStack stack = drifter.getInventory().getItem(slot);
							if (!stack.isEmpty()) {
								drifter.tossForward(stack.split(stack.getCount()));
							}
						}

						think(drifter, this.playerName + " freed it from the bandits");
						this.thanked = true;
					});
			}

			// Kneeling by the fire, head down.
			return new Script(drifter, "kneel by the fire", 20 * 20).until(() -> this.freed)
				.then(() -> new Hold(drifter, "kneel by the fire", 20 * 15, true, () -> Vec3.atCenterOf(this.fire).add(0.0, -0.5, 0.0)));
		}

		// The one with the fire lights it (hay first, for the smoke); the rest keep watch round the camp.
		Script script = new Script(drifter, "guard the camp", 20 * 30);
		if (!this.firePlaced && this.fireTries < 3 && carried(drifter, s -> s.is(Items.CAMPFIRE)) > 0) {
			this.fireTries++;
			BlockPos hay = this.fire;
			BlockPos flame = this.fire.above();
			script.then(() -> toPlace(drifter, hay))
				.then(() -> this.level.getBlockState(hay).canBeReplaced() ? new PlaceBlockTask(drifter, hay, s -> s.is(Items.HAY_BLOCK), "a hay bale") : null)
				.then(() -> this.level.getBlockState(flame).canBeReplaced() && !this.level.getBlockState(hay).canBeReplaced()
					? new PlaceBlockTask(drifter, flame, s -> s.is(Items.CAMPFIRE), "the campfire") : null);
			return script;
		}

		return script
			.then(() -> drifter.position().distanceTo(this.camp) > 6.0 ? new NavigateTask(drifter, this.camp, 4.0, "back to the camp", NavigateTask.Pace.STROLL) : null)
			.then(() -> new LookAroundTask(drifter, 3, 20));
	}

	@Override
	public String status() {
		return super.status() + (this.freed ? ", freed" : ", " + this.bandits.size() + " guards") + (this.firePlaced ? ", fire lit" : "") + " at " + this.fire.toShortString();
	}
}
