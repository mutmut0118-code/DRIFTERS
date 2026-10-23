package com.dinglebob.drifter.encounter;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.LookAtTask;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A trapped miner (docs/ENCOUNTERS.md 11): "help, I'm stuck!" A Drifter mining a little way under the ground near the
 * player had its tunnel cave in behind it and its pickaxe break: it's shut in a pocket of rock, knocking on the walls,
 * and types where it is in chat as a player would. Dig down to it and it climbs out after you, thanks you and hands
 * over what it mined. (The pocket is the one thing set up beforehand: the bit of tunnel it was in.)
 */
final class TrappedMiner extends Encounter {
	private static final String MINER = "miner";
	/** It calls out this often until someone gets to it. */
	private static final long CALLS_EVERY = 20L * 45;

	/** Its pocket (feet and head, and the cell beside them), and the rock round it. */
	private final List<BlockPos> pocket;
	private final List<BlockPos> shell = new ArrayList<>();
	@Nullable
	private DrifterEntity miner;
	private boolean free;
	private boolean thanked;
	private long lastCall = Long.MIN_VALUE / 2;
	private int calls;

	private TrappedMiner(ServerPlayer player, List<BlockPos> pocket) {
		super(Encounters.Kind.MINER, player);
		this.pocket = pocket;
		for (BlockPos cell : pocket) {
			for (Direction direction : Direction.values()) {
				BlockPos next = cell.relative(direction);
				if (!pocket.contains(next) && !this.shell.contains(next)) {
					this.shell.add(next);
				}
			}
		}
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = level.getRandom();
		List<BlockPos> pocket = null;
		for (int i = 0; i < 40 && pocket == null; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 10.0 + random.nextDouble() * 16.0;
			int x = (int)Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int)Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}

			int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos feet = new BlockPos(x, surface - 7 - random.nextInt(5), z);
			Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			List<BlockPos> cells = List.of(feet, feet.above(), feet.relative(along), feet.relative(along).above());
			if (sealedRock(level, cells, surface)) {
				pocket = cells;
			}
		}

		if (pocket == null) {
			return null;
		}

		for (BlockPos cell : pocket) {
			level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
		}

		TrappedMiner encounter = new TrappedMiner(player, pocket);
		BlockPos feet = pocket.get(0);
		DrifterEntity miner = EncounterSpawner.spawn(level, feet, Archetype.MINER, drifter -> {
			EncounterSpawner.traits(drifter, 0.5, 0.2, 0.6, 0.1);
			drifter.getInventory().add(new ItemStack(Items.RAW_IRON, 4 + random.nextInt(8)));
			drifter.getInventory().add(new ItemStack(Items.COAL, 6 + random.nextInt(10)));
			if (random.nextInt(3) == 0) {
				drifter.getInventory().add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
			} else {
				drifter.getInventory().add(new ItemStack(Items.RAW_GOLD, 2 + random.nextInt(4)));
			}

			drifter.getInventory().add(new ItemStack(Items.BREAD, 4));
			drifter.getInventory().add(new ItemStack(Items.TORCH, 3));
		});
		if (miner == null) {
			return null;
		}

		encounter.join(miner, MINER);
		encounter.miner = miner;
		EncounterSpawner.add(level, miner);
		think(miner, "the tunnel caved in behind it and its pickaxe broke: shut in at " + feet.toShortString());
		return encounter;
	}

	/** Every cell and the rock all round them natural stone, solid up to near the surface, nothing flowing anywhere near. */
	private static boolean sealedRock(ServerLevel level, List<BlockPos> cells, int surface) {
		for (BlockPos cell : cells) {
			for (BlockPos near : BlockPos.betweenClosed(cell.offset(-2, -2, -2), cell.offset(2, 2, 2))) {
				BlockState state = level.getBlockState(near);
				if (!state.getFluidState().isEmpty() || state.isAir() && !cells.contains(near.immutable())) {
					return false;
				}
			}

			if (!stone(level.getBlockState(cell))) {
				return false;
			}
		}

		// No cave over it (solid up to a few blocks under the surface).
		BlockPos top = cells.get(0).above(2);
		for (int y = top.getY(); y < surface - 2; y++) {
			if (level.getBlockState(new BlockPos(top.getX(), y, top.getZ())).isAir()) {
				return false;
			}
		}

		return true;
	}

	private static boolean stone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT);
	}

	@Override
	protected long longest() {
		return 20L * 60 * 12;
	}

	@Override
	protected void update() {
		DrifterEntity miner = this.miner;
		ServerPlayer player = this.player();
		if (miner == null || !miner.isAlive()) {
			this.end("the miner didn't make it");
			return;
		}

		if (!this.free) {
			// Someone broke through (a wall of its pocket gone), or is right there.
			for (BlockPos rock : this.shell) {
				if (this.level.getBlockState(rock).getCollisionShape(this.level, rock).isEmpty()) {
					this.free = true;
					break;
				}
			}

			if (player != null && player.distanceTo(miner) < 3.0) {
				this.free = true;
			}

			if (this.free) {
				say(miner, line(miner, "miner.freed"));
				think(miner, "dug out");
			} else if (this.now() - this.lastCall > CALLS_EVERY && this.calls < 12) {
				this.lastCall = this.now();
				BlockPos at = miner.blockPosition();
				say(miner, line(miner, "miner.call" + (this.calls++ % 3 + 1), "coords", coords(at), "depth",
					this.level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) - at.getY()));
			}

			return;
		}

		if (this.thanked || player == null || player.distanceTo(miner) > 48.0) {
			this.end(this.thanked ? "dug out, and thanked them" : "dug out");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return this.thanked ? 0.0 : 0.96;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return !this.thanked;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.free ? "thank " + this.playerName : "wait to be dug out";
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		ServerPlayer player = this.player();
		if (this.free && player != null) {
			return new Script(drifter, "thank " + this.playerName, 20 * 40)
				.then(() -> walkUpTo(drifter, player, 2.5))
				.then(() -> new LookAtTask(drifter, player.getEyePosition(), 10))
				.now(() -> {
					say(drifter, line(drifter, "miner.thanks"));
					for (int slot = 0; slot < com.dinglebob.drifter.entity.DrifterInventory.MAIN_SIZE; slot++) {
						ItemStack stack = drifter.getInventory().getItem(slot);
						if (stack.is(Items.DIAMOND) || stack.is(Items.RAW_GOLD) || stack.is(Items.RAW_IRON)) {
							drifter.tossForward(stack.split(stack.getCount()));
						}
					}

					think(drifter, this.playerName + " dug it out");
					this.thanked = true;
				});
		}

		// Shut in: knocking on the rock, listening.
		return new Script(drifter, "wait to be dug out", 20 * 20).until(() -> this.free).then(() -> new Knock(drifter, this.shell, 20 * 15));
	}

	/** Knocking on the walls of its pocket now and then (the sound of a fist on stone), looking about. */
	private static final class Knock extends Task {
		private final List<BlockPos> walls;
		private final int ticks;
		private int t;

		Knock(DrifterEntity drifter, List<BlockPos> walls, int ticks) {
			super(drifter);
			this.walls = walls;
			this.ticks = ticks;
		}

		@Override
		public String describe() {
			return "knock on the rock";
		}

		@Override
		protected int timeoutTicks() {
			return this.ticks + 20;
		}

		@Override
		protected Status onTick() {
			this.drifter.getKeys().releaseAll();
			if (this.t % 50 == 0) {
				// A wall in reach, beside its head or above it.
				BlockPos wall = this.walls.get(this.drifter.getRandom().nextInt(this.walls.size()));
				if (wall.closerToCenterThan(this.drifter.getEyePosition(), 2.5)) {
					this.drifter.getCamera().track(Vec3.atCenterOf(wall), CameraModel.TurnSpeed.NORMAL);
				}
			}

			if (this.t % 50 >= 12 && this.t % 50 <= 24 && this.t % 6 == 0) {
				this.drifter.swing(InteractionHand.MAIN_HAND);
				BlockPos at = com.dinglebob.drifter.hands.Reach.crosshair(this.drifter).getBlockPos();
				BlockState state = this.drifter.level().getBlockState(at);
				if (!state.isAir()) {
					this.drifter.level().playSound(null, at, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
				}
			}

			return ++this.t >= this.ticks ? Status.SUCCESS : Status.RUNNING;
		}
	}

	@Override
	public String status() {
		return super.status() + (this.free ? ", dug out" : ", shut in at " + this.pocket.get(0).toShortString());
	}
}
