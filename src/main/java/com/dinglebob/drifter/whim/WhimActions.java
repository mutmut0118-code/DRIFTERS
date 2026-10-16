package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.MineTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PillarUpTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.UseOnEntityTask;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The whim action types (milestone 13 step 1). Each is a small player routine that whims fill in with their own
 * parameters: watch the sky, climb a pillar to look round, go up to a high point or a landmark for the view, watch or
 * feed an animal, hop about, swim, pick a flower, light a dark spot, plant a sapling, put up a sign, build a little
 * something, say something, look round, peek in a chest. Everything goes through the usual tasks (reach, sight, real
 * items), so whims follow the same rules as everything else.
 */
public final class WhimActions {
	private static final Map<String, WhimAction> ACTIONS = new HashMap<>();

	static {
		ACTIONS.put("watch_sky", new WatchSky());
		ACTIONS.put("lookout", new Lookout());
		ACTIONS.put("high_point", new HighPoint());
		ACTIONS.put("visit", new Visit());
		ACTIONS.put("watch_mob", new WatchMob());
		ACTIONS.put("feed_mob", new FeedMob());
		ACTIONS.put("hop", new Hop());
		ACTIONS.put("swim", new Swim());
		ACTIONS.put("pick_flower", new PickFlower());
		ACTIONS.put("torch_dark", new TorchDark());
		ACTIONS.put("plant_sapling", new PlantSapling());
		ACTIONS.put("sign", new Sign());
		ACTIONS.put("build", new Build());
		ACTIONS.put("chat", new Chat());
		ACTIONS.put("look_around", new LookRound());
		ACTIONS.put("peek_chest", new PeekChest());
		ACTIONS.put("dance", new Dance());
		ACTIONS.put("gift", new Gift());
		ACTIONS.put("hang_out", new HangOut());
		ACTIONS.put("visit_friend", new VisitFriend());
		ACTIONS.put("watch_block", new WatchBlock());
		ACTIONS.put("craft", new CraftForFun());
		ACTIONS.put("eat", new EatThis());
		ACTIONS.put("oops_drop", new OopsDrop());
	}

	private WhimActions() {
	}

	@Nullable
	public static WhimAction get(String name) {
		return ACTIONS.get(name);
	}

	public static Set<String> names() {
		return ACTIONS.keySet();
	}

	/** A line in chat (the chat-lines setting and its rate limits apply). */
	public static void say(DrifterEntity drifter, String line) {
		com.dinglebob.drifter.social.ChatLines.sayLine(drifter, line);
	}

	// ---------------------------------------------------------------------------------------------
	// Shared pieces
	// ---------------------------------------------------------------------------------------------

	/**
	 * An activity that runs a list of steps in order, each made when its turn comes (so it reads the world then). A
	 * step that fails ends it; the whim is a whim, it doesn't try hard.
	 */
	static class Steps extends Activity {
		private final String what;
		private final int timeout;
		private final List<Supplier<Task>> steps = new ArrayList<>();
		private int next;
		private boolean failed;

		Steps(DrifterEntity drifter, String what, int timeout) {
			super(drifter);
			this.what = what;
			this.timeout = timeout;
		}

		Steps then(Supplier<Task> step) {
			this.steps.add(step);
			return this;
		}

		@Override
		public String describe() {
			return this.what;
		}

		@Override
		protected int timeoutTicks() {
			return this.timeout;
		}

		@Override
		protected void stepDone(Task step, Status status) {
			if (status == Status.FAILURE) {
				this.failed = true;
			}
		}

		@Override
		protected Status next() {
			if (this.failed) {
				return Status.SUCCESS;
			}

			while (this.next < this.steps.size()) {
				Task task = this.steps.get(this.next++).get();
				if (task != null) {
					return this.begin(task);
				}
			}

			return Status.SUCCESS;
		}
	}

	/** Looks at something (a spot, or an entity as it moves) for a while; now and then a hop if it's having fun. */
	static class Gaze extends Task {
		private final Supplier<Vec3> at;
		private final int ticks;
		private final boolean hops;
		private final String what;
		private int looked;

		Gaze(DrifterEntity drifter, Supplier<Vec3> at, int ticks, boolean hops, String what) {
			super(drifter);
			this.at = at;
			this.ticks = ticks;
			this.hops = hops;
			this.what = what;
		}

		@Override
		public String describe() {
			return this.what;
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 60;
		}

		@Override
		protected Status onTick() {
			Vec3 point = this.at.get();
			if (point == null) {
				return Status.SUCCESS;
			}

			VirtualKeys keys = this.drifter.getKeys();
			keys.releaseAll();
			keys.set(VirtualKeys.Key.JUMP, this.hops && this.drifter.onGround() && this.drifter.getRandom().nextInt(30) == 0);
			this.drifter.getCamera().track(point, CameraModel.TurnSpeed.IDLE);
			return ++this.looked >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}

	/** Jumps a number of times where it stands (or in water: bobs, now and then a stroke forward). */
	static class Bounce extends Task {
		private final int times;
		private final boolean swim;
		private int done;
		private boolean wasOnGround = true;
		private int ticks;

		Bounce(DrifterEntity drifter, int times, boolean swim) {
			super(drifter);
			this.times = times;
			this.swim = swim;
		}

		@Override
		public String describe() {
			return this.swim ? "swim about" : "hop about";
		}

		@Override
		protected int timeoutTicks() {
			return this.swim ? this.times + 40 : this.times * 30 + 40;
		}

		@Override
		protected Status onTick() {
			VirtualKeys keys = this.drifter.getKeys();
			this.ticks++;
			if (this.swim) {
				keys.set(VirtualKeys.Key.JUMP, true);
				keys.set(VirtualKeys.Key.FORWARD, (this.ticks / 20) % 3 == 0);
				if (this.ticks % 40 == 0) {
					this.drifter.getCamera().track(this.drifter.getEyePosition().add(this.drifter.getRandom().nextGaussian() * 5, 0.0,
						this.drifter.getRandom().nextGaussian() * 5), CameraModel.TurnSpeed.IDLE);
				}

				return this.ticks >= this.times ? Status.SUCCESS : Status.RUNNING;
			}

			boolean onGround = this.drifter.onGround();
			if (onGround && !this.wasOnGround) {
				this.done++;
			}

			this.wasOnGround = onGround;
			keys.set(VirtualKeys.Key.JUMP, onGround);
			return this.done >= this.times ? Status.SUCCESS : Status.RUNNING;
		}

		@Override
		protected void onStop() {
			this.drifter.getKeys().releaseAll();
		}
	}

	static Task walkTo(DrifterEntity drifter, BlockPos spot, String what) {
		return new NavigateTask(drifter, Vec3.atBottomCenterOf(spot), 1.0, what, NavigateTask.Pace.STROLL);
	}

	/** Where the sun (or the moon) is in the sky now, as a point far off from its eyes. */
	static Vec3 skyPoint(DrifterEntity drifter, String what) {
		Level level = drifter.level();
		double angle = level.getTimeOfDay(1.0F) * Math.PI * 2.0;
		Vec3 sun = new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
		Vec3 dir = switch (what) {
			case "moon" -> sun.scale(-1.0);
			case "stars", "clouds", "rain" -> new Vec3(drifter.getLookAngle().x, 1.6, drifter.getLookAngle().z).normalize();
			default -> sun;
		};
		// Too low to see behind the horizon: just above it.
		if (dir.y < 0.05) {
			dir = new Vec3(dir.x, 0.05, dir.z).normalize();
		}

		return drifter.getEyePosition().add(dir.scale(60.0));
	}

	/** A spot it knows, close by, where it can see the sky (its feet there, open above). */
	@Nullable
	static BlockPos openSky(DrifterEntity drifter, int radius) {
		Level level = drifter.level();
		if (level.canSeeSky(BlockPos.containing(drifter.getEyePosition()))) {
			return drifter.blockPosition();
		}

		BlockPos[] best = {null};
		double[] bestDistance = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), radius, pos -> {
			if (KnownBlocks.isStandable(level, pos) && level.canSeeSky(pos.above())) {
				double d = pos.distSqr(drifter.blockPosition());
				if (d < bestDistance[0]) {
					bestDistance[0] = d;
					best[0] = pos.immutable();
				}
			}
		});
		return best[0];
	}

	/** An animal (or any mob) of these types it can see close by, nearest first. */
	@Nullable
	static Entity seen(DrifterEntity drifter, List<String> types, double range) {
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			Entity entity = awareness.entity;
			if (!awareness.visibleNow || !entity.isAlive() || entity.distanceTo(drifter) > range) {
				continue;
			}

			String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			if (types.contains(id) && entity.distanceTo(drifter) < bestDistance) {
				best = entity;
				bestDistance = entity.distanceTo(drifter);
			}
		}

		return best;
	}

	static Predicate1 itemMatcher(String item) {
		if (item.startsWith("#")) {
			var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse(item.substring(1)));
			return stack -> stack.is(tag);
		}

		Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
		return stack -> stack.is(it);
	}

	interface Predicate1 extends java.util.function.Predicate<ItemStack> {
	}

	/** Home, if it's close (whims at home stay within its base). */
	@Nullable
	static Home homeNear(DrifterEntity drifter, double range) {
		Home home = drifter.getDrifterBrain().home();
		return home != null && home.near(drifter, range) ? home : null;
	}

	/** A free, flat bit of ground it knows near here (not in its buildings, not right on its home's middle). */
	@Nullable
	static BlockPos freeGround(DrifterEntity drifter, int radius, int width, int height) {
		Level level = drifter.level();
		Home home = drifter.getDrifterBrain().home();
		BlockPos[] best = {null};
		double[] bestScore = {Double.MAX_VALUE};
		drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), radius, pos -> {
			if (!KnownBlocks.isStandable(level, pos) || !level.getFluidState(pos.below()).isEmpty() || home != null && home.inOwnBuilding(pos, 2)
				|| com.dinglebob.drifter.world.PlayerPlacedBlocks.isPlayerPlaced(level, pos.below())) {
				return;
			}

			for (int dx = 0; dx < width; dx++) {
				for (int dy = 0; dy < height; dy++) {
					BlockPos cell = pos.offset(dx, dy, 0);
					if (!level.getBlockState(cell).canBeReplaced() || !level.getFluidState(cell).isEmpty()) {
						return;
					}
				}

				if (!level.getBlockState(pos.offset(dx, -1, 0)).isFaceSturdy(level, pos.offset(dx, -1, 0), Direction.UP)) {
					return;
				}
			}

			double d = pos.distSqr(drifter.blockPosition());
			double score = Math.abs(Math.sqrt(d) - 4.0);
			if (score < bestScore[0]) {
				bestScore[0] = score;
				best[0] = pos.immutable();
			}
		});
		return best[0];
	}

	// ---------------------------------------------------------------------------------------------
	// The actions
	// ---------------------------------------------------------------------------------------------

	/** Watching the sunset, the sunrise, the moon, the stars, the rain (params: what). */
	static final class WatchSky implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return whim.param("from", "outside").equals("door") ? doorway(drifter) != null : openSky(drifter, 12) != null;
		}

		/** Just inside its hut door, looking out (rain from the doorway). */
		@Nullable
		static BlockPos doorway(DrifterEntity drifter) {
			Home home = homeNear(drifter, 24.0);
			if (home == null || !home.hutBuilt || home.borrowed()) {
				return null;
			}

			var hut = com.dinglebob.drifter.building.StarterHut.of(home);
			return hut.doorPos().relative(hut.door.getOpposite());
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			String what = whim.param("what", "sun");
			BlockPos door = whim.param("from", "outside").equals("door") ? doorway(drifter) : null;
			if (door != null) {
				var hut = com.dinglebob.drifter.building.StarterHut.of(drifter.getDrifterBrain().home());
				Vec3 out = Vec3.atCenterOf(hut.doorPos().relative(hut.door, 12)).add(0, 1, 0);
				return new Steps(drifter, whim.thought(), whim.duration() + 20 * 40)
					.then(() -> walkTo(drifter, door, "stand in the doorway"))
					.then(() -> new Gaze(drifter, () -> out, whim.duration(), false, whim.thought()));
			}

			BlockPos spot = openSky(drifter, 12);
			if (spot == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), whim.duration() + 20 * 40)
				.then(() -> spot.equals(drifter.blockPosition()) ? null : walkTo(drifter, spot, "step outside"))
				.then(() -> new Gaze(drifter, () -> skyPoint(drifter, what), whim.duration(), false, whim.thought()));
		}
	}

	/** A 1x1 pillar up to look round (params: height; left standing, as players do). */
	static final class Lookout implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			int height = whim.param("height", 6);
			if (!drifter.onGround() || com.dinglebob.drifter.hands.Tools.buildingBlockCount(drifter) < height + 2 || homeNear(drifter, 6.0) != null) {
				return false;
			}

			// Open all the way up (found in testing: a branch overhead stopped the pillar).
			for (int dy = 2; dy <= height + 2; dy++) {
				if (!drifter.level().getBlockState(drifter.blockPosition().above(dy)).isAir()) {
					return false;
				}
			}

			return true;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			int height = whim.param("height", 6);
			return new Steps(drifter, whim.thought(), 20 * 90)
				.then(() -> new PillarUpTask(drifter, height))
				.then(() -> new LookAroundTask(drifter))
				.then(() -> new Gaze(drifter, () -> drifter.getEyePosition().add(drifter.getLookAngle().multiply(1, 0, 1).scale(40).add(0, -4, 0)),
					whim.duration(), false, whim.thought()))
				.then(() -> new LookAroundTask(drifter));
		}
	}

	/** Up to the highest spot it knows round here for the view. */
	static final class HighPoint implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			BlockPos top = top(drifter, whim.param("radius", 40));
			return top != null && top.getY() >= drifter.getBlockY() + whim.param("rise", 8);
		}

		@Nullable
		static BlockPos top(DrifterEntity drifter, int radius) {
			Level level = drifter.level();
			BlockPos[] best = {null};
			drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), radius, pos -> {
				if ((best[0] == null || pos.getY() > best[0].getY()) && KnownBlocks.isStandable(level, pos) && level.canSeeSky(pos.above())) {
					best[0] = pos.immutable();
				}
			});
			return best[0];
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos top = top(drifter, whim.param("radius", 40));
			if (top == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 120 + whim.duration())
				.then(() -> new NavigateTask(drifter, Vec3.atBottomCenterOf(top), 2.0, "climb up for the view"))
				.then(() -> new LookAroundTask(drifter))
				.then(() -> new Gaze(drifter, () -> drifter.getEyePosition().add(drifter.getLookAngle().multiply(1, 0, 1).scale(60).add(0, -10, 0)),
					whim.duration(), false, whim.thought()))
				.then(() -> {
					if (!whim.chat().isEmpty() && drifter.getRandom().nextBoolean()) {
						say(drifter, whim.chat().get(drifter.getRandom().nextInt(whim.chat().size())));
					}

					return new LookAroundTask(drifter);
				});
		}
	}

	/** Going to see a place it remembers (params: kind = landmark | structure; radius). */
	static final class Visit implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return place(drifter, whim) != null;
		}

		@Nullable
		static MemoryEntry place(DrifterEntity drifter, Whim whim) {
			MemoryKind kind = whim.param("kind", "landmark").equals("structure") ? MemoryKind.STRUCTURE : MemoryKind.LANDMARK;
			double radius = whim.param("radius", 96);
			List<String> labels = whim.params("labels");
			return drifter.getMemory().nearest(kind, drifter.level().dimension().location(), drifter.position(), radius,
				e -> e.pos.distanceTo(drifter.position()) > 16.0 && (labels.isEmpty() || labels.stream().anyMatch(l -> e.label.contains(l))));
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			MemoryEntry place = place(drifter, whim);
			if (place == null) {
				return null;
			}

			Vec3 at = place.pos;
			String what = "go and see the " + place.label.replace("minecraft:", "").replace('_', ' ');
			return new Steps(drifter, what, 20 * 60 * 4)
				.then(() -> new com.dinglebob.drifter.brain.activity.ExploreActivity(drifter, what, at, d -> d.position().distanceTo(at) < 12.0))
				.then(() -> new LookAroundTask(drifter))
				.then(() -> new Gaze(drifter, () -> at.add(0, 2, 0), whim.duration(), false, what));
		}
	}

	/** Watching an animal for a while (params: mobs). */
	static final class WatchMob implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return seen(drifter, whim.params("mobs"), whim.param("range", 16)) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Entity mob = seen(drifter, whim.params("mobs"), whim.param("range", 16));
			if (mob == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), whim.duration() + 20 * 30)
				.then(() -> mob.distanceTo(drifter) > 6.0 ? new NavigateTask(drifter, mob.position(), 4.0, "go closer", NavigateTask.Pace.STROLL) : null)
				.then(() -> new Gaze(drifter, () -> mob.isAlive() ? mob.getEyePosition() : null, whim.duration(), false, whim.thought()));
		}
	}

	/** Feeding an animal on the way past (params: food, a map of mob id to item id or tag). */
	static final class FeedMob implements WhimAction {
		@Nullable
		static Map.Entry<Entity, String> pair(DrifterEntity drifter, Whim whim) {
			for (Map.Entry<String, String> entry : whim.paramMap("food").entrySet()) {
				if (WhimGoal.count(drifter, entry.getValue()) == 0) {
					continue;
				}

				Entity mob = seen(drifter, List.of(entry.getKey()), 16.0);
				if (mob instanceof Animal animal && !animal.isBaby()) {
					return Map.entry(mob, entry.getValue());
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return pair(drifter, whim) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Map.Entry<Entity, String> pair = pair(drifter, whim);
			if (pair == null) {
				return null;
			}

			Entity mob = pair.getKey();
			Predicate1 food = itemMatcher(pair.getValue());
			return new Steps(drifter, whim.thought(), 20 * 40)
				.then(() -> new NavigateTask(drifter, mob.position(), 2.0, "walk up to it", NavigateTask.Pace.STROLL))
				.then(() -> new SelectItemTask(drifter, food, "the food"))
				.then(() -> mob.isAlive() ? new UseOnEntityTask(drifter, mob, "feed it") : null)
				.then(() -> new Gaze(drifter, () -> mob.isAlive() ? mob.getEyePosition() : null, 40, false, "watch it eat"));
		}
	}

	/** Hopping about: on the spot, or on its bed (params: times, on = here | bed). */
	static final class Hop implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return !whim.param("on", "here").equals("bed") || bed(drifter) != null;
		}

		@Nullable
		static BlockPos bed(DrifterEntity drifter) {
			Home home = homeNear(drifter, 24.0);
			if (home == null) {
				return null;
			}

			BlockPos bed = home.houseBed(drifter.level());
			return bed != null ? bed : home.hutBuilt ? com.dinglebob.drifter.building.StarterHut.of(home).bed(drifter.level()) : null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos bed = whim.param("on", "here").equals("bed") ? bed(drifter) : null;
			return new Steps(drifter, whim.thought(), 20 * 60)
				.then(() -> bed == null ? null : new NavigateTask(drifter, Vec3.atCenterOf(bed).add(0, 0.1, 0), 0.4, "onto the bed", NavigateTask.Pace.STROLL))
				.then(() -> new Bounce(drifter, whim.param("times", 6), false));
		}
	}

	/** A swim in water close by (params: radius). */
	static final class Swim implements WhimAction {
		@Nullable
		static BlockPos water(DrifterEntity drifter, int radius) {
			List<BlockPos> found = KnownBlocks.find(drifter, radius, state -> state.is(Blocks.WATER), 40);
			Level level = drifter.level();
			for (BlockPos pos : found) {
				// Open water at the top, deep enough to swim, not a flowing trickle.
				if (level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).is(Blocks.WATER)) {
					return pos;
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return drifter.getAirSupply() >= drifter.getMaxAirSupply() && water(drifter, whim.param("radius", 24)) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos water = water(drifter, whim.param("radius", 24));
			if (water == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), whim.duration() + 20 * 60)
				.then(() -> new NavigateTask(drifter, Vec3.atBottomCenterOf(water), 1.0, "into the water", NavigateTask.Pace.STROLL))
				.then(() -> drifter.isInWater() ? new Bounce(drifter, whim.duration(), true) : null)
				.then(() -> {
					BlockPos dry = KnownBlocks.nearestDryGround(drifter, 16, 0.0, 0);
					return dry == null ? null : new NavigateTask(drifter, Vec3.atBottomCenterOf(dry), 1.0, "out of the water");
				});
		}
	}

	/** Picking a flower it sees on the way (params: radius). */
	static final class PickFlower implements WhimAction {
		@Nullable
		static BlockPos flower(DrifterEntity drifter, int radius, boolean newOnly) {
			List<BlockPos> found = KnownBlocks.find(drifter, radius, state -> state.is(BlockTags.SMALL_FLOWERS), 16);
			Home home = drifter.getDrifterBrain().home();
			for (BlockPos pos : found) {
				Item item = drifter.level().getBlockState(pos).getBlock().asItem();
				// A collector: only a kind it hasn't got yet (on it or in its chests).
				if (newOnly && (drifter.getInventory().count(stack -> stack.is(item)) > 0 || home != null && home.stored().getOrDefault(item, 0) > 0)) {
					continue;
				}

				if (KnownBlocks.mayTake(drifter.level(), pos)) {
					return pos;
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return flower(drifter, whim.param("radius", 20), whim.param("new_only", false)) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos flower = flower(drifter, whim.param("radius", 20), whim.param("new_only", false));
			if (flower == null) {
				return null;
			}

			String name = drifter.level().getBlockState(flower).getBlock().getName().getString().toLowerCase(Locale.ROOT);
			return new Steps(drifter, "pick a " + name, 20 * 60)
				.then(() -> new NavigateTask(drifter, Vec3.atBottomCenterOf(flower), 2.0, "walk to the " + name, NavigateTask.Pace.STROLL))
				.then(() -> new MineTask(drifter, flower))
				.then(() -> new CollectItemsTask(drifter, 4.0));
		}
	}

	/** A torch on a dark spot nearby (params: radius). */
	static final class TorchDark implements WhimAction {
		@Nullable
		static BlockPos dark(DrifterEntity drifter, int radius) {
			Level level = drifter.level();
			BlockPos[] best = {null};
			drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), radius, pos -> {
				if (best[0] == null && KnownBlocks.isStandable(level, pos) && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) < 4
					&& level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) < 8
					&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && pos.distSqr(drifter.blockPosition()) > 4) {
					best[0] = pos.immutable();
				}
			});
			return best[0];
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return drifter.getInventory().count(stack -> stack.is(net.minecraft.world.item.Items.TORCH)) > 4 && dark(drifter, whim.param("radius", 10)) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos spot = dark(drifter, whim.param("radius", 10));
			if (spot == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 40)
				.then(() -> spot.closerThan(drifter.blockPosition(), 3.5) ? null : new NavigateTask(drifter, Vec3.atBottomCenterOf(spot), 2.5, "walk over"))
				.then(() -> new PlaceBlockTask(drifter, spot, stack -> stack.is(net.minecraft.world.item.Items.TORCH), "a torch"));
		}
	}

	/** Planting a sapling it carries on grass near home (params: radius). */
	static final class PlantSapling implements WhimAction {
		@Nullable
		static BlockPos spot(DrifterEntity drifter, int radius) {
			Level level = drifter.level();
			Home home = drifter.getDrifterBrain().home();
			BlockPos[] best = {null};
			drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), radius, pos -> {
				if (best[0] != null || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
					|| !(level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) || level.getBlockState(pos.below()).is(Blocks.DIRT))
					|| home != null && (home.inOwnBuilding(pos, 3) || home.center.closerThan(pos, 6)) || !level.canSeeSky(pos)) {
					return;
				}

				best[0] = pos.immutable();
			});
			return best[0];
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return drifter.getInventory().count(stack -> stack.is(ItemTags.SAPLINGS)) > 0 && spot(drifter, whim.param("radius", 12)) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos spot = spot(drifter, whim.param("radius", 12));
			if (spot == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 40)
				.then(() -> spot.closerThan(drifter.blockPosition(), 3.5) ? null : new NavigateTask(drifter, Vec3.atBottomCenterOf(spot), 2.5, "walk over"))
				.then(() -> new PlaceBlockTask(drifter, spot, stack -> stack.is(ItemTags.SAPLINGS), "a sapling"));
		}
	}

	/**
	 * A sign with something on it, on the ground near here or by home (params: lines, a list of options with {name},
	 * {group}, {day}; one picked).
	 */
	static final class Sign implements WhimAction {
		/** A player's base it knows (it saw them sleep there) within range, out of spawn protection: for a prank sign. */
		@Nullable
		static BlockPos playerBase(DrifterEntity drifter, int range) {
			var server = drifter.getServer();
			BlockPos spawn = drifter.level().getSharedSpawnPos();
			for (var r : drifter.getSocial().all()) {
				if (r.knownBase != null && r.knownBase.closerThan(drifter.blockPosition(), range) && (server == null
					|| Math.max(Math.abs(r.knownBase.getX() - spawn.getX()), Math.abs(r.knownBase.getZ() - spawn.getZ())) > server.getSpawnProtectionRadius() + 8)) {
					return r.knownBase;
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			if (whim.param("near", "here").equals("player_base")) {
				return playerBase(drifter, whim.param("radius", 200)) != null;
			}

			return freeGround(drifter, 8, 1, 2) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			List<String> options = whim.params("lines");
			if (options.isEmpty()) {
				return null;
			}

			String text = fill(drifter, options.get(drifter.getRandom().nextInt(options.size())));
			if (whim.param("near", "here").equals("player_base")) {
				BlockPos base = playerBase(drifter, whim.param("radius", 200));
				if (base == null) {
					return null;
				}

				Vec3 at = Vec3.atBottomCenterOf(base);
				BlockPos[] spot = {null};
				return new Steps(drifter, whim.thought(), 20 * 60 * 5)
					.then(() -> new com.dinglebob.drifter.brain.activity.ExploreActivity(drifter, "sneak over to their base", at, d -> d.position().distanceTo(at) < 10.0))
					.then(() -> {
						spot[0] = freeGround(drifter, 8, 1, 2);
						return spot[0] == null ? null : new NavigateTask(drifter, Vec3.atBottomCenterOf(spot[0]), 2.5, "walk over");
					})
					.then(() -> spot[0] == null ? null : new PlaceBlockTask(drifter, spot[0], stack -> stack.is(ItemTags.SIGNS), "a sign"))
					.then(() -> {
						if (spot[0] != null && drifter.level().getBlockEntity(spot[0]) instanceof SignBlockEntity) {
							write(drifter.level(), spot[0], text);
							drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "left a sign by a player's base: \"" + text + "\"");
						}

						return null;
					});
			}

			BlockPos spot = freeGround(drifter, 8, 1, 2);
			if (spot == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 40)
				.then(() -> spot.closerThan(drifter.blockPosition(), 3.5) ? null : new NavigateTask(drifter, Vec3.atBottomCenterOf(spot), 2.5, "walk over"))
				.then(() -> new PlaceBlockTask(drifter, spot, stack -> stack.is(ItemTags.SIGNS), "a sign"))
				.then(() -> {
					if (drifter.level().getBlockEntity(spot) instanceof SignBlockEntity) {
						write(drifter.level(), spot, text);
						drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "wrote \"" + text + "\" on a sign at " + spot.toShortString());
					}

					return null;
				});
		}

		static String fill(DrifterEntity drifter, String line) {
			var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
			return line.replace("{name}", drifter.getName().getString())
				.replace("{group}", group == null ? drifter.getName().getString() : group.name)
				.replace("{day}", String.valueOf(drifter.level().getDayTime() / 24000L + 1));
		}

		/** What it writes on the sign (as a player types it in the sign screen), up to four short lines. */
		static void write(Level level, BlockPos pos, String text) {
			if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
				return;
			}

			String[] words = text.split(" ");
			List<String> lines = new ArrayList<>();
			StringBuilder line = new StringBuilder();
			for (String word : words) {
				if (line.length() + word.length() + 1 > 15 && line.length() > 0) {
					lines.add(line.toString());
					line = new StringBuilder();
				}

				line.append(line.length() > 0 ? " " : "").append(word);
			}

			lines.add(line.toString());
			var signText = sign.getFrontText();
			for (int i = 0; i < Math.min(4, lines.size()); i++) {
				signText = signText.setMessage(i, Component.literal(lines.get(i)));
			}

			sign.setText(signText, true);
			sign.setChanged();
			level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
		}
	}

	/**
	 * A little build from a pattern (params: blocks, a list of [dx, dy, dz, item] placed in order from the ground up;
	 * the whim's needs list what it uses): a snow golem, a monument pillar, a campfire with log seats.
	 */
	static final class Build implements WhimAction {
		record Cell(int dx, int dy, int dz, String item) {
		}

		static List<Cell> cells(Whim whim) {
			List<Cell> cells = new ArrayList<>();
			if (whim.params().has("blocks")) {
				for (var element : whim.params().getAsJsonArray("blocks")) {
					var a = element.getAsJsonArray();
					cells.add(new Cell(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsString()));
				}
			}

			return cells;
		}

		@Nullable
		static BlockPos site(DrifterEntity drifter, List<Cell> cells) {
			int width = cells.stream().mapToInt(Cell::dx).max().orElse(0) + 1;
			int height = cells.stream().mapToInt(Cell::dy).max().orElse(0) + 1;
			return freeGround(drifter, 12, width, height + 1);
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			List<Cell> cells = cells(whim);
			return !cells.isEmpty() && site(drifter, cells) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			List<Cell> cells = cells(whim);
			BlockPos site = site(drifter, cells);
			if (site == null) {
				return null;
			}

			Steps steps = new Steps(drifter, whim.thought(), 20 * 90);
			steps.then(() -> site.closerThan(drifter.blockPosition(), 3.0) ? null
				: new NavigateTask(drifter, Vec3.atBottomCenterOf(site.relative(Direction.SOUTH, 2)), 1.5, "walk over"));
			for (Cell cell : cells) {
				BlockPos pos = site.offset(cell.dx(), cell.dy(), cell.dz());
				Predicate1 item = itemMatcher(cell.item());
				steps.then(() -> drifter.level().getBlockState(pos).canBeReplaced() ? new PlaceBlockTask(drifter, pos, item, cell.item()) : null);
			}

			return steps.then(() -> new Gaze(drifter, () -> Vec3.atCenterOf(site.above()), 40, true, "admire it"));
		}
	}

	/** Saying something to whoever's about (params: lines; [M] whims). */
	static final class Chat implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return com.dinglebob.drifter.DrifterConfig.chatLines() && !whim.params("lines").isEmpty();
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			List<String> lines = whim.params("lines");
			Entity listener = nearestCompany(drifter);
			return new Steps(drifter, whim.thought(), 20 * 20)
				.then(() -> listener == null ? null : new Gaze(drifter, listener::getEyePosition, 20, false, "turn to them"))
				.then(() -> {
					say(drifter, Sign.fill(drifter, lines.get(drifter.getRandom().nextInt(lines.size()))));
					return listener == null ? null : new Gaze(drifter, listener::getEyePosition, 30, false, "look at them");
				});
		}

		@Nullable
		static Entity nearestCompany(DrifterEntity drifter) {
			Entity best = null;
			for (Awareness awareness : drifter.getSenses().tracked()) {
				if (awareness.visibleNow && awareness.identified && awareness.entity.distanceTo(drifter) < 16.0
					&& (awareness.entity instanceof net.minecraft.world.entity.player.Player || awareness.entity instanceof DrifterEntity)
					&& (best == null || awareness.entity.distanceTo(drifter) < best.distanceTo(drifter))) {
					best = awareness.entity;
				}
			}

			return best;
		}
	}

	/** A good look round (params: times; at a cliff edge or on top of its build). */
	static final class LookRound implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return drifter.onGround();
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Steps steps = new Steps(drifter, whim.thought(), 20 * 40);
			for (int i = 0; i < whim.param("times", 2); i++) {
				steps.then(() -> new LookAroundTask(drifter));
			}

			return steps;
		}
	}

	/** A friend close by: a group mate or a Drifter it trusts 50+, or a player it's friendly with; seen now. */
	@Nullable
	static Entity friend(DrifterEntity drifter, double range) {
		var group = com.dinglebob.drifter.world.DrifterGroups.of(drifter.getUUID());
		Entity best = null;
		for (Awareness awareness : drifter.getSenses().tracked()) {
			Entity e = awareness.entity;
			// Seen in the last few seconds is enough: it knows they're about (found in testing: "in view right now" missed a
			// friend standing just behind it).
			if (!awareness.seenWithin(drifter.level().getGameTime(), 20 * 10) || !awareness.identified || !e.isAlive() || e.distanceTo(drifter) > range) {
				continue;
			}

			boolean friendly = false;
			if (e instanceof DrifterEntity other && other != drifter) {
				var bond = drifter.getBonds().get(other.getUUID());
				friendly = group != null && group.members.contains(other.getUUID()) || bond != null && bond.trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND;
			} else if (e instanceof net.minecraft.world.entity.player.Player player && com.dinglebob.drifter.social.SocialWatcher.isRealPlayer(player)) {
				var r = drifter.getSocial().get(player.getUUID());
				friendly = r != null && r.trust >= com.dinglebob.drifter.social.Mode.FRIENDLY_TRUST;
			}

			if (friendly && (best == null || e.distanceTo(drifter) < best.distanceTo(drifter))) {
				best = e;
			}
		}

		return best;
	}

	static String nameOf(Entity entity) {
		return entity.getName().getString();
	}

	/** A crouch dance (on its own when it's happy, or at someone; params: ticks). */
	static final class Dance implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return drifter.onGround();
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Entity watcher = Chat.nearestCompany(drifter);
			int ticks = whim.param("ticks", 60);
			return new Steps(drifter, whim.thought(), ticks + 40).then(() -> new Task(drifter) {
				private int t;

				@Override
				public String describe() {
					return "dance";
				}

				@Override
				protected Status onTick() {
					VirtualKeys keys = this.drifter.getKeys();
					keys.releaseAll();
					keys.set(VirtualKeys.Key.SNEAK, (this.t / 3) % 2 == 0);
					keys.set(VirtualKeys.Key.JUMP, this.t % 25 == 0 && this.drifter.onGround());
					if (watcher != null && watcher.isAlive()) {
						this.drifter.getCamera().track(watcher.getEyePosition(), CameraModel.TurnSpeed.IDLE);
					}

					return ++this.t >= ticks ? Status.SUCCESS : Status.RUNNING;
				}

				@Override
				protected void onStop() {
					this.drifter.getKeys().releaseAll();
				}
			});
		}
	}

	/** Giving a friend something it can spare (params: items, keep = how many of it it keeps). */
	static final class Gift implements WhimAction {
		@Nullable
		static Integer spareSlot(DrifterEntity drifter, Whim whim) {
			int keep = whim.param("keep", 0);
			for (String item : whim.params("items")) {
				Predicate1 matcher = itemMatcher(item);
				if (drifter.getInventory().count(matcher) <= keep) {
					continue;
				}

				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					if (matcher.test(drifter.getInventory().getItem(slot))) {
						return slot;
					}
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return friend(drifter, 20.0) != null && spareSlot(drifter, whim) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Entity friend = friend(drifter, 20.0);
			if (friend == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 40)
				.then(() -> friend.distanceTo(drifter) > 3.0 ? new NavigateTask(drifter, friend.position(), 2.5, "walk over to " + nameOf(friend), NavigateTask.Pace.STROLL) : null)
				.then(() -> new Gaze(drifter, friend::getEyePosition, 15, false, "look at " + nameOf(friend)))
				.then(() -> {
					Integer slot = spareSlot(drifter, whim);
					if (slot != null && friend.distanceTo(drifter) < 5.0) {
						ItemStack given = drifter.getInventory().getItem(slot).split(1);
						String what = given.getHoverName().getString().toLowerCase(Locale.ROOT);
						boolean food = given.has(net.minecraft.core.component.DataComponents.FOOD);
						drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "gave " + nameOf(friend) + (food ? " some " : " a ") + what);
						drifter.tossForward(given);
					}

					return new Gaze(drifter, friend::getEyePosition, 20, false, "look at " + nameOf(friend));
				});
		}
	}

	/** Walking about with a friend for a while (params: rounds). */
	static final class HangOut implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return friend(drifter, 24.0) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Entity friend = friend(drifter, 24.0);
			if (friend == null) {
				return null;
			}

			Steps steps = new Steps(drifter, "hang out with " + nameOf(friend), whim.duration() + 20 * 60);
			for (int i = 0; i < whim.param("rounds", 6); i++) {
				steps.then(() -> !friend.isAlive() ? null : friend.distanceTo(drifter) > 4.0
					? new NavigateTask(drifter, friend.position(), 3.0, "keep up with " + nameOf(friend), NavigateTask.Pace.STROLL)
					: new Gaze(drifter, () -> friend.isAlive() ? friend.getEyePosition() : null, 40, false, "chill with " + nameOf(friend)));
			}

			return steps;
		}
	}

	/** Dropping by a friend's home (a Drifter it trusts, whose home it has seen). */
	static final class VisitFriend implements WhimAction {
		@Nullable
		static com.dinglebob.drifter.social.Relationship friendWithHome(DrifterEntity drifter, int radius) {
			Home home = drifter.getDrifterBrain().home();
			for (var bond : drifter.getBonds().all()) {
				if (bond.trust >= com.dinglebob.drifter.social.DrifterBonds.FRIEND && bond.knownBase != null
					&& bond.knownBase.closerThan(drifter.blockPosition(), radius) && !bond.knownBase.closerThan(drifter.blockPosition(), 24)
					&& (home == null || !bond.knownBase.closerThan(home.center, 16))) {
					return bond;
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return friendWithHome(drifter, whim.param("radius", 160)) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			var bond = friendWithHome(drifter, whim.param("radius", 160));
			if (bond == null) {
				return null;
			}

			Vec3 at = Vec3.atBottomCenterOf(bond.knownBase);
			String what = "drop by " + bond.name + "'s place";
			return new Steps(drifter, what, 20 * 60 * 5)
				.then(() -> new com.dinglebob.drifter.brain.activity.ExploreActivity(drifter, what, at, d -> d.position().distanceTo(at) < 10.0))
				.then(() -> new LookAroundTask(drifter))
				.then(() -> {
					Entity there = friend(drifter, 16.0);
					return there == null ? null : new Gaze(drifter, there::getEyePosition, 60, false, "say hi to " + nameOf(there));
				});
		}
	}

	/** Watching a block for a while: crops ripening, a sapling, a fire (params: blocks, ids or "#tags"; radius). */
	static final class WatchBlock implements WhimAction {
		@Nullable
		static BlockPos find(DrifterEntity drifter, Whim whim) {
			List<String> ids = whim.params("blocks");
			return KnownBlocks.find(drifter, whim.param("radius", 16), state -> ids.stream().anyMatch(id -> matches(state, id)), 1).stream().findFirst()
				.orElse(null);
		}

		static boolean matches(BlockState state, String id) {
			if (id.startsWith("#")) {
				return state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(id.substring(1))));
			}

			return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(id);
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return find(drifter, whim) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			BlockPos block = find(drifter, whim);
			if (block == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), whim.duration() + 20 * 40)
				.then(() -> block.closerThan(drifter.blockPosition(), 5.0) ? null
					: new NavigateTask(drifter, Vec3.atBottomCenterOf(block), 3.0, "go over", NavigateTask.Pace.STROLL))
				.then(() -> new Gaze(drifter, () -> Vec3.atCenterOf(block), whim.duration(), false, whim.thought()));
		}
	}

	/**
	 * Making something it doesn't need, just because (docs/WHIMS.md section 27): the first of the whim's items it can make
	 * quickly from what it has (params: items).
	 */
	static final class CraftForFun implements WhimAction {
		@Nullable
		static Item pick(DrifterEntity drifter, Whim whim) {
			var world = com.dinglebob.drifter.brain.KnownWorld.scan(drifter);
			for (String id : whim.params("items")) {
				Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
				if (drifter.getInventory().count(stack -> stack.is(item)) > 0) {
					continue;
				}

				var plan = com.dinglebob.drifter.brain.Planner.plan(drifter, world, item, 1);
				if (plan.feasible() && plan.next() != null && WhimPrep.quick(plan) && plan.actions().size() <= 4) {
					return item;
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return pick(drifter, whim) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Item item = pick(drifter, whim);
			if (item == null) {
				return null;
			}

			String name = item.getDescription().getString().toLowerCase(Locale.ROOT);
			return new Activity(drifter) {
				private int steps;

				@Override
				public String describe() {
					return "make a " + name + " just because";
				}

				@Override
				protected int timeoutTicks() {
					return 20 * 60 * 3;
				}

				@Override
				protected Status next() {
					if (this.drifter.getInventory().count(stack -> stack.is(item)) > 0) {
						this.think("made a " + name + " just because");
						return Status.SUCCESS;
					}

					var plan = com.dinglebob.drifter.brain.Planner.plan(this.drifter, com.dinglebob.drifter.brain.KnownWorld.scan(this.drifter), item, 1);
					if (++this.steps > 6 || !plan.feasible() || plan.next() == null || !WhimPrep.quick(plan)) {
						return Status.SUCCESS;
					}

					return this.begin(com.dinglebob.drifter.brain.Goals.activityFor(this.drifter, plan.next()));
				}
			};
		}
	}

	/** Eating something in particular, wise or not (a spider eye, rotten flesh; params: items). */
	static final class EatThis implements WhimAction {
		@Nullable
		static Predicate1 food(DrifterEntity drifter, Whim whim) {
			for (String item : whim.params("items")) {
				Predicate1 matcher = itemMatcher(item);
				if (drifter.getInventory().count(matcher) > 0) {
					return matcher;
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			return drifter.getFoodData().getFoodLevel() < 20 && food(drifter, whim) != null;
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Predicate1 food = food(drifter, whim);
			if (food == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 20)
				.then(() -> new SelectItemTask(drifter, food, "it"))
				.then(() -> new com.dinglebob.drifter.executor.task.UseItemTask(drifter));
		}
	}

	/** Dropping a stack by mistake ("oops") and picking it straight back up (params: items it might fumble). */
	static final class OopsDrop implements WhimAction {
		@Nullable
		static Integer slot(DrifterEntity drifter, Whim whim) {
			for (String item : whim.params("items")) {
				Predicate1 matcher = itemMatcher(item);
				for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
					if (matcher.test(drifter.getInventory().getItem(slot)) && drifter.getInventory().getItem(slot).getCount() >= 8) {
						return slot;
					}
				}
			}

			return null;
		}

		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			// Not by water or a drop, where it would really lose it.
			return drifter.onGround() && !drifter.isInWater() && slot(drifter, whim) != null
				&& drifter.level().getBlockState(drifter.blockPosition().below()).isFaceSturdy(drifter.level(), drifter.blockPosition().below(), Direction.UP);
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Integer slot = slot(drifter, whim);
			if (slot == null) {
				return null;
			}

			return new Steps(drifter, whim.thought(), 20 * 20)
				.then(() -> {
					ItemStack stack = drifter.getInventory().getItem(slot);
					if (!stack.isEmpty()) {
						drifter.getCamera().track(drifter.getEyePosition().add(drifter.getLookAngle().multiply(1, 0, 1).scale(3)).add(0, -1.5, 0),
							CameraModel.TurnSpeed.NORMAL);
						// Fumbled, not thrown away: it'll pick it back up (what it throws away on purpose it leaves).
						drifter.tossForward(stack.split(stack.getCount()), false);
						if (drifter.getRandom().nextBoolean()) {
							say(drifter, "oops");
						}
					}

					// (A dropped stack can't be picked up for 2 seconds: it looks at it a moment first.)
					return new Gaze(drifter, () -> drifter.getEyePosition().add(drifter.getLookAngle().multiply(1, 0, 1).scale(2)).add(0, -1.5, 0), 60,
						false, "oops");
				})
				.then(() -> new CollectItemsTask(drifter, 6.0));
		}
	}

	/** Opening one of its chests at home for a look (and closing it again). */
	static final class PeekChest implements WhimAction {
		@Override
		public boolean possible(DrifterEntity drifter, Whim whim) {
			Home home = homeNear(drifter, 16.0);
			return home != null && home.hasChest();
		}

		@Override
		public Activity start(DrifterEntity drifter, Whim whim) {
			Home home = homeNear(drifter, 16.0);
			if (home == null || !home.hasChest()) {
				return null;
			}

			BlockPos chest = home.chests().get(drifter.getRandom().nextInt(home.chests().size()));
			BlockPos stand = KnownBlocks.standingSpotFor(drifter, chest, Set.of());
			return new Steps(drifter, whim.thought(), 20 * 40)
				.then(() -> stand == null || com.dinglebob.drifter.hands.Reach.canReach(drifter, chest) ? null
					: new NavigateTask(drifter, Vec3.atBottomCenterOf(stand), 0.5, "walk to the chest"))
				.then(() -> new ContainerTask(drifter, chest, ContainerTask.Mode.TAKE, stack -> false, "nothing", 0, null));
		}
	}
}
