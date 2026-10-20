package com.dinglebob.drifter.encounter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.RideTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A convoy (docs/ENCOUNTERS.md 24): riders on horses passing through on their way somewhere far, a stone's throw from the
 * player. The one in front calls out a greeting and where they're bound; they ride on and are gone. Peaceful, unless
 * someone starts something. A horse whose rider falls stays behind (anyone's now).
 */
final class Convoy extends Encounter {
	private static final String RIDER = "rider";
	private static final List<String> PLACES = List.of("Ashford", "the river towns", "Stonehaven", "the coast", "Millbrook", "the northern camps",
		"Redwater", "the mountain pass");

	private final Vec3 far;
	private final String place;
	private final Map<DrifterEntity, Horse> horses = new HashMap<>();
	/** Each rider's own lane: its end of the road, side by side with the others (found in testing: riding in file to one
	 * point, the horses bumped into each other and got stuck over and over). */
	private final Map<DrifterEntity, Vec3> lanes = new HashMap<>();
	private boolean greeted;
	/** Each rider's best distance to its lane's end so far, and when it last got closer (stuck: another way). */
	private final Map<DrifterEntity, Double> best = new HashMap<>();
	private final Map<DrifterEntity, Long> gainedAt = new HashMap<>();
	private static final long STUCK = 20L * 15;

	private Convoy(ServerPlayer player, Vec3 far, String place) {
		super(Encounters.Kind.CONVOY, player);
		this.far = far;
		this.place = place;
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		// Their road: by the player, a little to one side, and on a long way; open country a horse can get across (found in
		// testing: in thick forest the horses got nowhere).
		BlockPos spot = null;
		Vec3 far = null;
		Vec3 across = null;
		for (int tries = 0; tries < 6 && spot == null; tries++) {
			BlockPos start = EncounterSpawner.hiddenSpot(level, player.position(), 50.0, 70.0, null, random);
			if (start == null) {
				continue;
			}

			Vec3 from = Vec3.atBottomCenterOf(start);
			Vec3 way = player.position().subtract(from).multiply(1.0, 0.0, 1.0).normalize();
			Vec3 side = new Vec3(-way.z, 0.0, way.x).scale((random.nextBoolean() ? 1 : -1) * (6.0 + random.nextDouble() * 4.0));
			Vec3 end = beyond(from, player.position().add(side), 90.0);
			if (openRoad(level, from, end)) {
				spot = start;
				far = end;
				across = way;
			}
		}

		if (spot == null) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] no open road for a convoy near {}", player.blockPosition().toShortString());
			return null;
		}

		Convoy convoy = new Convoy(player, far, PLACES.get(random.nextInt(PLACES.size())));
		int count = 3 + random.nextInt(2);
		Vec3 abreast = new Vec3(-across.z, 0.0, across.x);
		for (int i = 0; i < count; i++) {
			double offset = (i - (count - 1) / 2.0) * 3.0;
			BlockPos at = EncounterSpawner.ground(level, spot.getX() + (int)Math.round(abreast.x * offset), spot.getZ() + (int)Math.round(abreast.z * offset));
			at = at == null ? spot : at;
			Vec3 lane = far.add(abreast.scale(offset));
			DrifterEntity rider = EncounterSpawner.spawn(level, at, i == 0 ? Archetype.NOMAD : Archetype.CASUAL, drifter -> {
				EncounterSpawner.fighter(drifter, 1, false, random);
				EncounterSpawner.traits(drifter, 0.6, 0.3, 0.5, 0.4);
				drifter.getInventory().add(new ItemStack(Items.BREAD, 6));
			});
			Horse horse = EntityType.HORSE.create(level);
			if (rider == null || horse == null) {
				continue;
			}

			horse.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rider.getYRot(), 0.0F);
			horse.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			horse.setTamed(true);
			horse.setOwnerUUID(rider.getUUID());
			horse.equipSaddle(new ItemStack(Items.SADDLE), null);
			if (!level.addFreshEntity(horse)) {
				continue;
			}

			convoy.join(rider, RIDER);
			EncounterSpawner.add(level, rider);
			rider.meansToRide(true);
			rider.startRiding(horse, true);
			rider.meansToRide(false);
			convoy.horses.put(rider, horse);
			convoy.lanes.put(rider, lane);
			convoy.props.add(horse);
			think(rider, "riding for " + convoy.place);
		}

		if (convoy.horses.isEmpty()) {
			convoy.members.forEach(DrifterEntity::discard);
			return null;
		}

		return convoy;
	}

	/**
	 * Most of the way from here to there is ground a horse and rider fit over: no steep steps, nothing in the four
	 * blocks over it (a rider's head is that high; found in testing: under trees they rode their heads into the leaves).
	 */
	private static boolean openRoad(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from).multiply(1.0, 0.0, 1.0);
		int samples = (int)(step.length() / 3.0);
		int open = 0;
		BlockPos last = null;
		for (int i = 0; i <= samples; i++) {
			Vec3 at = from.add(step.scale((double)i / samples));
			int x = (int)Math.floor(at.x);
			int z = (int)Math.floor(at.z);
			// Water on the way: no road (a horse won't take a rider across; found in testing at a lake shore).
			BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (!level.getFluidState(surface.below()).isEmpty()) {
				return false;
			}

			BlockPos ground = EncounterSpawner.ground(level, x, z);
			boolean clear = ground != null && (last == null || Math.abs(ground.getY() - last.getY()) <= 2);
			for (int y = 0; clear && y < 4; y++) {
				clear = level.getBlockState(ground.above(y)).getCollisionShape(level, ground.above(y)).isEmpty()
					&& !level.getBlockState(ground.above(y)).is(net.minecraft.tags.BlockTags.LEAVES);
			}

			if (clear) {
				open++;
			}

			last = ground != null ? ground : last;
		}

		if (open < samples * 0.75) {
			com.dinglebob.drifter.Drifter.LOGGER.debug("[Drifter] convoy road {} -> {}: {} of {} open", BlockPos.containing(from).toShortString(),
				BlockPos.containing(to).toShortString(), open, samples + 1);
		}

		return open >= samples * 0.75;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 5;
	}

	/** A horse goes with them while one of them is on it. */
	@Override
	protected boolean stillTheirs(Entity prop) {
		return prop.getFirstPassenger() instanceof DrifterEntity rider && this.members.contains(rider);
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.members.isEmpty()) {
			this.end("no riders left");
			return;
		}

		if (player != null && !this.greeted) {
			for (DrifterEntity rider : this.members) {
				if (sees(rider, player) && rider.distanceTo(player) < 32.0) {
					this.greeted = true;
					String hello = this.level.getDayTime() % 24000L < 6000L ? "morning" : "good day";
					say(rider, hello + ", stranger! bound for " + this.place + ", " + direction(rider.position(), this.far) + " of here. safe travels!");
					break;
				}
			}
		}

		// Riders getting nowhere (a cliff, a lake, thick trees): another way on, open country, roughly onward.
		for (DrifterEntity rider : this.members) {
			Vec3 lane = this.lanes.getOrDefault(rider, this.far);
			double distance = rider.position().distanceTo(lane);
			if (distance < this.best.getOrDefault(rider, Double.MAX_VALUE) - 3.0) {
				this.best.put(rider, distance);
				this.gainedAt.put(rider, this.now());
			} else if (this.now() - this.gainedAt.getOrDefault(rider, this.now()) > STUCK) {
				Vec3 other = this.anotherWay(rider, lane);
				this.gainedAt.put(rider, this.now());
				this.best.remove(rider);
				if (other != null) {
					this.lanes.put(rider, other);
					think(rider, "no way through there: riding round by " + BlockPos.containing(other).toShortString());
					rider.getDrifterBrain().poke();
				}
			}
		}

		boolean anyLeft = false;
		for (DrifterEntity rider : this.members) {
			if (rider.position().distanceTo(this.lanes.getOrDefault(rider, this.far)) > 8.0) {
				anyLeft = true;
			}
		}

		if (!anyLeft || player != null && this.greeted && this.members.stream().allMatch(rider -> rider.distanceTo(player) > 90.0)) {
			this.end("rode on");
		}
	}

	/** Open country some way off roughly the way it was going (from straight on to well round to either side), or null. */
	@Nullable
	private Vec3 anotherWay(DrifterEntity rider, Vec3 lane) {
		Vec3 way = lane.subtract(rider.position()).multiply(1.0, 0.0, 1.0);
		double heading = Math.atan2(way.z, way.x);
		for (double turn : new double[] {0.6, -0.6, 1.1, -1.1, 1.6, -1.6}) {
			Vec3 to = rider.position().add(Math.cos(heading + turn) * 70.0, 0.0, Math.sin(heading + turn) * 70.0);
			if (openRoad(this.level, rider.position(), to)) {
				return to;
			}
		}

		return null;
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
		return "ride for " + this.place;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		Horse horse = this.horses.get(drifter);
		if (horse == null || !horse.isAlive()) {
			return null;
		}

		Vec3 lane = this.lanes.getOrDefault(drifter, this.far);
		return new Script(drifter, "ride for " + this.place, 20 * 60 * 3).then(() -> new RideTask(drifter, horse, lane, 3.0));
	}

	@Override
	public String status() {
		ServerPlayer player = this.player();
		String near = player == null || this.members.isEmpty() ? "" : ", nearest rider " + (int)this.members.stream().mapToDouble(player::distanceTo).min().orElse(0.0) + " blocks off";
		return super.status() + near + ", bound for " + this.place + " (" + BlockPos.containing(this.far).toShortString() + ")" + (this.greeted ? ", greeted them" : "");
	}
}
