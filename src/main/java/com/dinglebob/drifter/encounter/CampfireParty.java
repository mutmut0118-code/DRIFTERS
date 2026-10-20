package com.dinglebob.drifter.encounter;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A campfire party (docs/ENCOUNTERS.md 23): of an evening, a few Drifters set up a campfire and a cake on open ground,
 * dance, cheer and set off fireworks, and call the player over when they see them. After a few minutes they say
 * goodnight and go; the campfire and what's left of the cake stay.
 */
final class CampfireParty extends Encounter {
	private static final String HOST = "host";
	private static final String GUEST = "guest";
	private static final long LASTS = 20L * 60 * 4;
	private static final List<String> CHEERS = List.of("woo!", "best night ever", "ha! nice one", "who brought the cake?", "another one!");

	private final BlockPos fire;
	private final BlockPos cake;
	private boolean firePlaced;
	private boolean cakePlaced;
	/** Tries at putting the fire and the cake down (it gives up after a few: a party round nothing). */
	private int fireTries;
	private int cakeTries;
	private static final int TRIES = 3;
	private boolean invited;
	private long lastCheer = Long.MIN_VALUE / 2;

	private CampfireParty(ServerPlayer player, BlockPos fire, BlockPos cake) {
		super(Encounters.Kind.PARTY, player);
		this.fire = fire;
		this.cake = cake;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos found = EncounterSpawner.hiddenSpot(level, player.position(), 28.0, 40.0, null, random);
		if (found == null) {
			return null;
		}

		// The fire and the cake on bare ground near there (not in the grass).
		BlockPos spot = null;
		BlockPos cake = null;
		for (int dx = -3; dx <= 3 && cake == null; dx++) {
			for (int dz = -3; dz <= 3 && cake == null; dz++) {
				BlockPos fire = EncounterSpawner.bareGround(level, found.getX() + dx, found.getZ() + dz);
				BlockPos beside = fire == null ? null : EncounterSpawner.bareGround(level, fire.getX() + 2, fire.getZ());
				if (fire != null && beside != null && Math.abs(beside.getY() - fire.getY()) <= 1 && EncounterSpawner.hidden(level, fire)) {
					spot = fire;
					cake = beside;
				}
			}
		}

		if (spot == null) {
			return null;
		}

		CampfireParty party = new CampfireParty(player, spot, cake);
		int count = 3 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			boolean host = i == 0;
			boolean baker = i == 1;
			double angle = Math.PI * 2.0 * i / count;
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + (int)Math.round(Math.cos(angle) * 3.0), spot.getZ() + (int)Math.round(Math.sin(angle) * 3.0));
			DrifterEntity drifter = EncounterSpawner.spawn(level, at == null ? spot.north(2) : at, i % 2 == 0 ? Archetype.HELPER : Archetype.CASUAL, d -> {
				EncounterSpawner.traits(d, 0.6, 0.2, 0.4, 0.2);
				d.getPersonality().set(com.dinglebob.drifter.personality.Trait.SOCIABILITY, 0.9);
				if (host) {
					d.getInventory().add(new ItemStack(Items.CAMPFIRE));
				}

				if (baker) {
					d.getInventory().add(new ItemStack(Items.CAKE));
				}

				d.getInventory().add(new ItemStack(Items.FIREWORK_ROCKET, 2 + random.nextInt(3)));
				d.getInventory().add(new ItemStack(Items.BREAD, 4));
			});
			if (drifter == null) {
				continue;
			}

			party.join(drifter, host ? HOST : GUEST);
			EncounterSpawner.add(level, drifter);
		}

		return party;
	}

	@Override
	protected long longest() {
		return LASTS + 20L * 60;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.members.isEmpty()) {
			this.end("everyone went home");
			return;
		}

		this.firePlaced |= this.level.getBlockState(this.fire).is(net.minecraft.tags.BlockTags.CAMPFIRES);
		this.cakePlaced |= this.level.getBlockState(this.cake).is(net.minecraft.world.level.block.Blocks.CAKE);
		for (DrifterEntity drifter : this.members) {
			if (player != null && !this.invited && sees(drifter, player) && drifter.distanceTo(player) < 40.0) {
				this.invited = true;
				say(drifter, "hey, " + this.playerName + "! come join us!");
			}
		}

		if (this.now() - this.lastCheer > 20 * 25) {
			this.lastCheer = this.now();
			DrifterEntity cheering = this.members.get(this.level.getRandom().nextInt(this.members.size()));
			say(cheering, CHEERS.get(this.level.getRandom().nextInt(CHEERS.size())));
		}

		if (this.now() - this.startedAt > LASTS) {
			say(this.members.get(0), "goodnight, everyone!");
			this.end("the party's over");
		}
	}

	@Override
	public String status() {
		return super.status() + (this.firePlaced ? ", fire lit" : ", no fire yet") + (this.cakePlaced ? ", cake down" : "") + " at " + this.fire.toShortString();
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return 0.9;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return "party by the campfire";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		Script script = new Script(drifter, "party by the campfire", 20 * 30);
		// The host lights the fire; whoever has the cake puts it down (a few tries each, then the party goes on without).
		if (!this.firePlaced && this.fireTries < TRIES && carried(drifter, s -> s.is(Items.CAMPFIRE)) > 0) {
			this.fireTries++;
			script.then(() -> toPlace(drifter, this.fire));
			script.then(() -> this.level.getBlockState(this.fire).canBeReplaced() ? new PlaceBlockTask(drifter, this.fire, s -> s.is(Items.CAMPFIRE), "the campfire") : null);
			return script.then(() -> new Dance(drifter, Vec3.atCenterOf(this.fire), 40));
		}

		if (!this.cakePlaced && this.cakeTries < TRIES && carried(drifter, s -> s.is(Items.CAKE)) > 0) {
			this.cakeTries++;
			script.then(() -> toPlace(drifter, this.cake));
			script.then(() -> this.level.getBlockState(this.cake).canBeReplaced() ? new PlaceBlockTask(drifter, this.cake, s -> s.is(Items.CAKE), "the cake") : null);
			return script.then(() -> new Dance(drifter, Vec3.atCenterOf(this.fire), 40));
		}

		// Round the fire: a dance, a cheer, now and then a firework.
		script.then(() -> drifter.position().distanceTo(Vec3.atBottomCenterOf(this.fire)) > 4.0
			? new NavigateTask(drifter, Vec3.atBottomCenterOf(this.fire), 2.5, "back to the fire", NavigateTask.Pace.STROLL) : null);
		script.then(() -> new Dance(drifter, Vec3.atCenterOf(this.fire), 60 + drifter.getRandom().nextInt(60)));
		if (drifter.getRandom().nextInt(3) == 0 && carried(drifter, s -> s.is(Items.FIREWORK_ROCKET)) > 0) {
			script.then(() -> new SelectItemTask(drifter, s -> s.is(Items.FIREWORK_ROCKET), "a firework"));
			script.then(() -> {
				BlockPos ground = drifter.blockPosition().below();
				return drifter.level().getBlockState(ground).isFaceSturdy(drifter.level(), ground, Direction.UP)
					? UseOnBlockTask.useBlock(drifter, ground, Direction.UP, "set off a firework") : null;
			});
			script.then(() -> new LookAtTask(drifter, drifter.getEyePosition().add(0.0, 20.0, 0.5), 40));
		}

		return script;
	}

	/** Over to somewhere it can put a block down at {@code cell} from (the ground under it in reach and in sight); null if it's there. */
	@Nullable
	private static Task toPlace(DrifterEntity drifter, BlockPos cell) {
		if (com.dinglebob.drifter.hands.Reach.canReach(drifter, cell.below())) {
			return null;
		}

		BlockPos stand = com.dinglebob.drifter.brain.KnownBlocks.standingSpotFor(drifter, cell.below(), java.util.Set.of(cell));
		return stand != null ? new NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.6, "to the party spot", NavigateTask.Pace.STROLL)
			: new NavigateTask(drifter, Vec3.atBottomCenterOf(cell), 2.0, "to the party spot", NavigateTask.Pace.STROLL);
	}

	/** A dance round the fire: crouching up and down, now and then a hop, turned to the fire. */
	private static final class Dance extends Task {
		private final Vec3 fire;
		private final int ticks;
		private int t;

		Dance(DrifterEntity drifter, Vec3 fire, int ticks) {
			super(drifter);
			this.fire = fire;
			this.ticks = ticks;
		}

		@Override
		public String describe() {
			return "dance";
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 20;
		}

		@Override
		protected Status onTick() {
			VirtualKeys keys = this.drifter.getKeys();
			keys.releaseAll();
			keys.set(VirtualKeys.Key.SNEAK, (this.t / 4) % 2 == 0);
			keys.set(VirtualKeys.Key.JUMP, this.t % 30 == 0 && this.drifter.onGround());
			if (this.t % 20 == 0) {
				this.drifter.getCamera().track(this.fire, CameraModel.TurnSpeed.IDLE);
			}

			return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}
}
