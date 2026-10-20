package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builders (docs/ENCOUNTERS.md 26): a crew of two or three turns up near the player with a load of wood and puts up a
 * waystation for travellers (four log posts, a plank roof, a crafting table and a torch under it), block by block from
 * where each can reach, as players build; then they look it over and go. It stays.
 */
final class Builders extends Encounter {
	private static final String BUILDER = "builder";
	/** The waystation's size (posts at the corners), and its posts' height. */
	private static final int SIZE = 4;
	private static final int POST = 3;
	/** A block it can't get placed after this many tries is left out. */
	private static final int TRIES = 4;
	/** A block it just missed waits this long before anyone tries it again (from somewhere else, maybe). */
	private static final long MISS_WAIT = 20L * 3;

	private final BlockPos origin;
	/** Every block of it, in the order they go up (posts bottom up, then the roof, then what goes under it), and what. */
	private final Map<BlockPos, Item> plan = new LinkedHashMap<>();
	private final Set<BlockPos> keepClear;
	private final Map<BlockPos, DrifterEntity> claimed = new HashMap<>();
	private final Map<BlockPos, Integer> misses = new HashMap<>();
	private final Map<BlockPos, Long> missedAt = new HashMap<>();
	private boolean greeted;
	private boolean done;
	private long doneAt;

	private Builders(ServerPlayer player, BlockPos origin) {
		super(Encounters.Kind.BUILDERS, player);
		this.origin = origin;
		for (int y = 0; y < POST; y++) {
			for (int[] corner : new int[][] {{0, 0}, {SIZE - 1, 0}, {0, SIZE - 1}, {SIZE - 1, SIZE - 1}}) {
				this.plan.put(origin.offset(corner[0], y, corner[1]), Items.OAK_LOG);
			}
		}

		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				this.plan.put(origin.offset(x, POST, z), Items.OAK_PLANKS);
			}
		}

		this.plan.put(origin.offset(1, 0, 1), Items.CRAFTING_TABLE);
		// A torch on a post's inner side.
		this.plan.put(origin.offset(1, 1, 0), Items.TORCH);
		this.keepClear = Set.copyOf(this.plan.keySet());
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		BlockPos origin = null;
		for (int i = 0; i < 120 && origin == null; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 12.0 + random.nextDouble() * 28.0;
			BlockPos at = EncounterSpawner.ground(level, (int)Math.floor(player.getX() + Math.cos(angle) * distance), (int)Math.floor(player.getZ() + Math.sin(angle) * distance));
			if (at != null && flatAndClear(level, at)) {
				origin = at;
			}
		}

		if (origin == null) {
			return null;
		}

		BlockPos from = EncounterSpawner.hiddenSpot(level, Vec3.atBottomCenterOf(origin), 18.0, 34.0, null, random);
		if (from == null) {
			return null;
		}

		Builders crew = new Builders(player, origin);
		int count = 2 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			BlockPos at = EncounterSpawner.ground(level, from.getX() + random.nextInt(5) - 2, from.getZ() + random.nextInt(5) - 2);
			DrifterEntity builder = EncounterSpawner.spawn(level, at == null ? from : at, Archetype.BUILDER, drifter -> {
				EncounterSpawner.traits(drifter, 0.5, 0.2, 0.5, 0.2);
				drifter.getInventory().add(new ItemStack(Items.OAK_LOG, 14));
				drifter.getInventory().add(new ItemStack(Items.OAK_PLANKS, 20));
				drifter.getInventory().add(new ItemStack(Items.CRAFTING_TABLE));
				drifter.getInventory().add(new ItemStack(Items.TORCH, 2));
				drifter.getInventory().add(new ItemStack(Items.BREAD, 4));
			});
			if (builder != null) {
				crew.join(builder, BUILDER);
				EncounterSpawner.add(level, builder);
				think(builder, "off to put up a waystation at " + origin.toShortString());
			}
		}

		return crew;
	}

	/** Level ground under the whole footprint (and round it, to stand on) with room above for the build. */
	private static boolean flatAndClear(ServerLevel level, BlockPos origin) {
		for (int x = -1; x <= SIZE; x++) {
			for (int z = -1; z <= SIZE; z++) {
				BlockPos ground = EncounterSpawner.ground(level, origin.getX() + x, origin.getZ() + z);
				boolean inside = x >= 0 && x < SIZE && z >= 0 && z < SIZE;
				if (ground == null || inside && ground.getY() != origin.getY() || !inside && Math.abs(ground.getY() - origin.getY()) > 1) {
					return false;
				}

				if (inside) {
					for (int y = 0; y <= POST + 1; y++) {
						BlockState state = level.getBlockState(origin.offset(x, y, z));
						if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
							return false;
						}
					}
				}
			}
		}

		return true;
	}

	/** Already there (the right block in place). */
	private boolean placed(BlockPos cell) {
		Item item = this.plan.get(cell);
		BlockState state = this.level.getBlockState(cell);
		return item == Items.TORCH ? state.is(net.minecraft.world.level.block.Blocks.WALL_TORCH) || state.is(net.minecraft.world.level.block.Blocks.TORCH)
			: state.getBlock().asItem() == item;
	}

	/** Something solid beside it to build against. */
	private boolean supported(BlockPos cell) {
		for (Direction direction : Direction.values()) {
			BlockPos next = cell.relative(direction);
			if (!this.level.getBlockState(next).getCollisionShape(this.level, next).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** What's left to put up, in order. */
	private List<BlockPos> left() {
		List<BlockPos> left = new ArrayList<>();
		for (BlockPos cell : this.plan.keySet()) {
			if (!this.placed(cell) && this.misses.getOrDefault(cell, 0) < TRIES) {
				left.add(cell);
			}
		}

		return left;
	}

	@Override
	protected long longest() {
		return 20L * 60 * 10;
	}

	@Override
	protected void update() {
		ServerPlayer player = this.player();
		if (this.members.isEmpty()) {
			this.end("no builders left");
			return;
		}

		this.claimed.entrySet().removeIf(entry -> !entry.getValue().isAlive() || this.placed(entry.getKey()));
		if (player != null && !this.greeted) {
			for (DrifterEntity builder : this.members) {
				if (sees(builder, player) && builder.distanceTo(player) < 24.0) {
					this.greeted = true;
					say(builder, "hello! putting up a waystation for travellers. you're welcome to it.");
					break;
				}
			}
		}

		if (!this.done && this.left().isEmpty()) {
			this.done = true;
			this.doneAt = this.now();
			say(this.members.get(0), "there. that'll keep the rain off.");
			for (DrifterEntity builder : this.members) {
				think(builder, "finished the waystation at " + this.origin.toShortString());
			}
		}

		if (this.done && this.now() - this.doneAt > 20L * 15) {
			this.end("built it");
		}
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
		return this.done ? "look over the waystation" : "build a waystation";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		Vec3 middle = Vec3.atBottomCenterOf(this.origin).add(SIZE / 2.0 - 0.5, 0.0, SIZE / 2.0 - 0.5);
		if (this.done) {
			return new Script(drifter, "look over the waystation", 20 * 15)
				.then(() -> new Hold(drifter, "look it over", 20 * 10, false, () -> middle.add(0.0, 2.5, 0.0)));
		}

		// Its last go is over: a block it didn't get in counts a miss.
		this.claimed.entrySet().removeIf(entry -> {
			if (entry.getValue() != drifter) {
				return false;
			}

			if (!this.placed(entry.getKey())) {
				this.misses.merge(entry.getKey(), 1, Integer::sum);
				this.missedAt.put(entry.getKey(), this.now());
			}

			return true;
		});

		// The next block it can build against that nobody else is on, nearest it.
		BlockPos cell = null;
		double best = Double.MAX_VALUE;
		for (BlockPos next : this.left()) {
			DrifterEntity other = this.claimed.get(next);
			if (other != null && other != drifter || !this.supported(next) || this.now() - this.missedAt.getOrDefault(next, Long.MIN_VALUE / 2) < MISS_WAIT) {
				continue;
			}

			double distance = next.distSqr(drifter.blockPosition());
			if (distance < best) {
				best = distance;
				cell = next;
			}
		}

		if (cell == null) {
			// Nothing it can do this moment (the others are on it): it waits nearby.
			return new Script(drifter, "wait for a turn", 20 * 4)
				.then(() -> drifter.position().distanceTo(middle) > 8.0 ? new NavigateTask(drifter, middle, 5.0, "over to the build", NavigateTask.Pace.STROLL) : null)
				.then(() -> new Hold(drifter, "watch the others build", 20 * 3, false, () -> middle.add(0.0, 2.0, 0.0)));
		}

		BlockPos target = cell;
		Item item = this.plan.get(target);
		this.claimed.put(target, drifter);
		return new Script(drifter, "build a waystation", 20 * 30)
			.then(() -> toBuild(drifter, target, this.keepClear))
			.then(() -> this.level.getBlockState(target).canBeReplaced() ? new PlaceBlockTask(drifter, target, stack -> stack.is(item), item.getDescription().getString()) : null);
	}

	@Override
	public String status() {
		int total = this.plan.size();
		return super.status() + ", " + (total - this.left().size()) + " of " + total + " blocks up at " + this.origin.toShortString() + (this.done ? ", done" : "");
	}
}
