package com.dinglebob.drifter.encounter;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.LookAroundTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.personality.Archetype;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A thief in your base (docs/ENCOUNTERS.md 6): while the player is about their base but not right by their chests,
 * someone creeps in (crouched, no name showing), finds a chest of theirs, takes what's worth taking (a couple of dozen
 * at most: diamonds, emeralds, ingots, enchanted things) and creeps off. Catch them at it and they run; bring them down
 * and what they took drops with the rest of their pack. Let them go and it's gone.
 */
final class Burglar extends Encounter {
	private static final String THIEF = "thief";
	/** It takes this many things at most. */
	private static final int TAKES = 24;
	/** Their chests: this near their bed. */
	private static final int BASE = 10;

	private final BlockPos bed;
	private final Vec3 escape;
	@Nullable
	private DrifterEntity thief;
	private int looks;
	private boolean took;
	private boolean spotted;
	private boolean leaving;
	private int carriedBefore = -1;

	private Burglar(ServerPlayer player, BlockPos bed, Vec3 escape) {
		super(Encounters.Kind.BURGLAR, player);
		this.bed = bed;
		this.escape = escape;
	}

	/** Worth stealing. */
	static boolean valuable(ItemStack stack) {
		return stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.GOLD_INGOT) || stack.is(Items.IRON_INGOT)
			|| stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_SCRAP) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
			|| stack.is(Items.ENDER_PEARL) || stack.is(Items.RAW_GOLD) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.ENCHANTED_BOOK) || stack.isEnchanted()
			|| stack.is(Items.DIAMOND_BLOCK) || stack.is(Items.EMERALD_BLOCK) || stack.is(Items.GOLD_BLOCK);
	}

	@Nullable
	static Encounter start(ServerPlayer player) {
		BlockPos bed = player.getRespawnPosition();
		if (bed == null || !Encounters.nearHome(player, 0.0, 96.0)) {
			return null;
		}

		ServerLevel level = player.serverLevel();
		if (!worthIt(level, bed)) {
			return null;
		}

		// From the far side of the base from the player.
		var random = level.getRandom();
		Vec3 home = Vec3.atBottomCenterOf(bed);
		BlockPos spot = EncounterSpawner.hiddenSpot(level, home, 30.0, 48.0, home.subtract(player.position()), random);
		if (spot == null) {
			return null;
		}

		Burglar burglar = new Burglar(player, bed, beyond(home, Vec3.atBottomCenterOf(spot), 50.0));
		DrifterEntity thief = EncounterSpawner.spawn(level, spot, Archetype.HERMIT, drifter -> {
			EncounterSpawner.traits(drifter, 0.3, 0.2, 0.9, 0.2);
			drifter.setCustomNameVisible(false);
			drifter.getInventory().add(new ItemStack(Items.STONE_SWORD));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 3));
		});
		if (thief == null) {
			return null;
		}

		burglar.join(thief, THIEF);
		burglar.thief = thief;
		EncounterSpawner.add(level, thief);
		think(thief, "casing " + player.getGameProfile().getName() + "'s place");
		return burglar;
	}

	/** A chest of theirs near their bed with something worth taking in it. */
	private static boolean worthIt(ServerLevel level, BlockPos bed) {
		for (BlockPos pos : BlockPos.betweenClosed(bed.offset(-BASE, -4, -BASE), bed.offset(BASE, 4, BASE))) {
			var entity = level.getBlockEntity(pos);
			if ((entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity || entity instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity)
				&& entity instanceof Container container && theirs(level, pos)) {
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					if (valuable(container.getItem(slot))) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * A chest (or barrel...) by their bed is theirs, unless a Drifter built it (whether a player put it down isn't known
	 * for chests placed before the mod was in the world). Never an ender chest: nobody else can get into one.
	 */
	private static boolean theirs(ServerLevel level, BlockPos pos) {
		return !com.dinglebob.drifter.world.PlayerPlacedBlocks.isDrifterBuilt(level, pos) && !level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.ENDER_CHEST);
	}

	/** One of their chests it has seen (a player put down, by the bed), not tried yet. */
	@Nullable
	private BlockPos chest(DrifterEntity thief) {
		MemoryEntry entry = thief.getMemory().nearest(MemoryKind.STORAGE, this.level.dimension().location(), Vec3.atCenterOf(this.bed), BASE + 2,
			e -> theirs(this.level, e.blockPos()) && !thief.getDrifterBrain().looted.contains(e.blockPos()));
		return entry == null ? null : entry.blockPos();
	}

	@Override
	protected long longest() {
		return 20L * 60 * 6;
	}

	@Override
	protected void update() {
		DrifterEntity thief = this.thief;
		ServerPlayer player = this.player();
		if (thief == null || !thief.isAlive()) {
			this.end("the thief is dead");
			return;
		}

		int carried = carried(thief, Burglar::valuable);
		if (this.carriedBefore < 0) {
			this.carriedBefore = carried;
		}

		if (carried > this.carriedBefore && !this.took) {
			this.took = true;
			think(thief, "got into " + this.playerName + "'s chest");
		}

		if (player == null) {
			return;
		}

		boolean hit = thief.getLastHurtByMob() == player && thief.tickCount - thief.getLastHurtByMobTimestamp() < 40;
		if (!this.spotted && (hit || knowsWhere(thief, player) && player.distanceTo(thief) < 12.0)) {
			this.spotted = true;
			this.leaving = true;
			think(thief, "caught at it: running");
			if (this.took) {
				say(thief, "uh oh.");
			}
		}

		if (this.leaving && player.distanceTo(thief) > 64.0 && !player.hasLineOfSight(thief)) {
			this.end(this.took ? "it got away with their things" : "it ran off empty-handed");
		}
	}

	@Override
	public double urgency(DrifterEntity drifter) {
		return 0.95;
	}

	@Override
	public boolean urgent(DrifterEntity drifter) {
		return true;
	}

	@Override
	public String describe(DrifterEntity drifter) {
		return this.leaving ? "get away" : this.took ? "creep off" : "rob " + this.playerName + "'s chests";
	}

	/** Caught robbing them: no crime. */
	@Override
	public Killing killing(DrifterEntity drifter, Player player) {
		return Killing.NONE;
	}

	@Override
	public Activity start(DrifterEntity drifter) {
		if (this.leaving || this.took) {
			this.leaving = true;
			return new Script(drifter, "get away", 20 * 60).then(() -> new NavigateTask(drifter, this.escape, 4.0, "get away",
				this.spotted ? NavigateTask.Pace.TRAVEL : NavigateTask.Pace.SNEAK));
		}

		BlockPos chest = this.chest(drifter);
		if (chest == null) {
			// Into their base, and a look round for their chests (three looks round there; then it gives up on it).
			Vec3 base = Vec3.atBottomCenterOf(this.bed);
			if (drifter.position().distanceTo(base) > 16.0) {
				return new Script(drifter, "head for " + this.playerName + "'s base", 20 * 90)
					.then(() -> new NavigateTask(drifter, base, 14.0, "head for their base", NavigateTask.Pace.STROLL));
			}

			if (this.looks++ >= 3) {
				this.leaving = true;
				return null;
			}

			return new Script(drifter, "look for " + this.playerName + "'s chests", 20 * 40)
				.then(() -> drifter.position().distanceTo(base) > 4.0 ? new NavigateTask(drifter, base, 3.0, "creep in", NavigateTask.Pace.SNEAK) : null)
				.then(() -> new LookAroundTask(drifter, 3, 8));
		}

		return new Script(drifter, "rob " + this.playerName + "'s chest", 20 * 40)
			.then(() -> {
				if (com.dinglebob.drifter.hands.Reach.canReach(drifter, chest)) {
					return null;
				}

				BlockPos stand = KnownBlocks.standingSpotFor(drifter, chest, Set.of());
				return new NavigateTask(drifter, stand != null ? Vec3.atBottomCenterOf(stand) : Vec3.atCenterOf(chest), stand != null ? 0.5 : 2.5, "creep to the chest",
					NavigateTask.Pace.SNEAK);
			})
			.now(() -> drifter.getDrifterBrain().looted.add(chest))
			.then(() -> new ContainerTask(drifter, chest, ContainerTask.Mode.TAKE, Burglar::valuable, "the good stuff", TAKES, null));
	}

	@Override
	public String status() {
		return super.status() + (this.took ? ", took something" : "") + (this.spotted ? ", spotted" : "") + " (their bed at " + this.bed.toShortString() + ")";
	}
}
