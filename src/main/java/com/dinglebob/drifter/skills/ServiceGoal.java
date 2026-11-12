package com.dinglebob.drifter.skills;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.api.TownWork;
import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.brain.KnownBlocks;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.task.EnchantTask;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.executor.task.SmithTask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A service for a player (milestone 23 B5; the user, 2026-11-06: "go to b4 b5 and b6"): its town took a job for it
 * ({@link TownWork#service}): an enchanting, a repair, a netherite upgrade, of something a player handed over (and paid
 * for). It takes the job's item to its station (an enchanting table, an anvil, a smithing table it knows), does it as a
 * player would (the table's own roll with its own levels and lapis; an anvil's repair with the materials and its levels;
 * the smithing table with the template and ingot), and hands the result back ({@link TownWork#serviced}). Never with the
 * item kept: it's out of its pack but for the moment it works on it.
 */
public final class ServiceGoal extends Goals.Goal {
	@Override
	public String id() {
		return "service";
	}

	@Override
	public String name(Goals.Context context) {
		TownWork.Service job = job(context.drifter());
		return job == null ? "do a job for someone" : job.what();
	}

	@Nullable
	private static TownWork.Service job(DrifterEntity drifter) {
		TownWork town = drifter.getTownWork();
		return town == null ? null : town.service(drifter);
	}

	@Override
	public double urgency(Goals.Context context) {
		// (Above keeping its shop: a job taken waited behind its stall all day, a test.)
		return job(context.drifter()) == null || context.threat().urgent() ? 0.0 : 0.92;
	}

	@Override
	public boolean urgent(Goals.Context context) {
		return true;
	}

	@Override
	public double feasibility(Goals.Context context) {
		return 1.0;
	}

	@Override
	public int minCommitTicks() {
		return 20 * 30;
	}

	/** The station for a kind of job it knows of near (home's enchanting table first), or null. */
	@Nullable
	private static BlockPos station(DrifterEntity drifter, String kind) {
		Home home = drifter.getDrifterBrain().home();
		if (kind.equals("enchant") && home != null && home.enchantingTable(drifter.level()) != null) {
			return home.enchantingTable(drifter.level());
		}

		java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> is = state -> switch (kind) {
			case "enchant" -> state.is(Blocks.ENCHANTING_TABLE);
			case "repair" -> state.getBlock() instanceof AnvilBlock;
			default -> state.is(Blocks.SMITHING_TABLE);
		};
		// Its own house first (its workshop: it knows what's in it).
		var box = home == null ? null : home.planBox();
		if (box != null && drifter.level().isLoaded(home.center)) {
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (is.test(drifter.level().getBlockState(pos))) {
					return pos.immutable();
				}
			}
		}

		var found = KnownBlocks.find(drifter, 96, is, 1);
		return found.isEmpty() ? null : found.get(0);
	}

	@Override
	public Activity start(Goals.Context context) {
		DrifterEntity drifter = context.drifter();
		TownWork town = drifter.getTownWork();
		TownWork.Service job = job(drifter);
		if (town == null || job == null) {
			return null;
		}

		BlockPos station = station(drifter, job.kind());
		if (station == null) {
			drifter.getMemory().thoughts().add(drifter.level().getGameTime(), "has nowhere to " + job.kind() + " it: handing it back");
			java.util.List<ItemStack> back = new java.util.ArrayList<>();
			back.add(job.item());
			job.materials().forEach(stack -> back.add(stack.copy()));
			town.serviced(drifter, back, false);
			return null;
		}

		return new Activity(drifter) {
			private int step;
			private ItemStack working = ItemStack.EMPTY;
			/** How many of each material it carried of its own before the job's were put in its pack. */
			private final java.util.Map<net.minecraft.world.item.Item, Integer> ownBefore = new java.util.HashMap<>();

			@Override
			public String describe() {
				return job.what();
			}

			@Override
			protected int timeoutTicks() {
				return 20 * 120;
			}

			@Override
			protected void onStop() {
				// (Stopped before it was done: the item back with its owner, as it was.)
				if (this.step < 3) {
					this.handBack(false);
				}
			}

			private void handBack(boolean done) {
				java.util.List<ItemStack> back = new java.util.ArrayList<>();
				if (this.working.isEmpty()) {
					back.add(job.item());
					job.materials().forEach(stack -> back.add(stack.copy()));
				} else {
					back.add(this.takeOut());
					// What's left of what it was given (the materials it didn't use).
					for (ItemStack given : job.materials()) {
						var inventory = this.drifter.getInventory();
						// (Only what the work left of them: not its own of the same kind. 0.23.53: it handed over its own iron.)
						int own = this.ownBefore.getOrDefault(given.getItem(), 0);
						int left = Math.max(0, Math.min(given.getCount(), inventory.count(stack -> stack.getItem() == given.getItem()) - own));
						this.ownBefore.merge(given.getItem(), 0, Integer::sum);
						for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
							ItemStack stack = inventory.getItem(slot);
							if (stack.getItem() == given.getItem()) {
								int n = Math.min(left, stack.getCount());
								back.add(stack.split(n));
								left -= n;
							}
						}
					}
				}

				this.step = 4;
				town.serviced(this.drifter, back, done);
			}

			/** The job's item (as it is now) out of its pack. */
			private ItemStack takeOut() {
				var inventory = this.drifter.getInventory();
				// (The very one it worked on first: it may carry others like it, its own to sell. Found reading it, 0.23.53.)
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					if (inventory.getItem(slot) == this.working && !this.working.isEmpty()) {
						inventory.setItem(slot, ItemStack.EMPTY);
						return this.working;
					}
				}

				// (An enchanting may leave another stack in its place: the enchanted one of its kind before a plain one of its own.)
				for (int slot = 0; slot < inventory.getContainerSize() && job.kind().equals("enchant"); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if (stack.getItem() == this.working.getItem() && stack.isEnchanted()) {
						inventory.setItem(slot, ItemStack.EMPTY);
						return stack;
					}
				}

				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if (!stack.isEmpty() && (stack == this.working || stack.getItem() == this.working.getItem() || job.kind().equals("netherite")
						&& stack.getItem().getDescriptionId().contains("netherite") && stack.getItem() != net.minecraft.world.item.Items.NETHERITE_INGOT)) {
						inventory.setItem(slot, ItemStack.EMPTY);
						return stack;
					}
				}

				return this.working;
			}

			@Override
			protected Status next() {
				switch (this.step++) {
					case 0 -> {
						if (com.dinglebob.drifter.hands.Reach.canReach(this.drifter, station)) {
							return Status.RUNNING;
						}

						BlockPos stand = KnownBlocks.standingSpotFor(this.drifter, station, java.util.Set.of());
						return this.begin(new NavigateTask(this.drifter, Vec3.atBottomCenterOf(stand != null ? stand : station), stand != null ? 0.6 : 2.5,
							"go to the " + job.kind() + " station"));
					}
					case 1 -> {
						// Into its pack for the work (and the materials it was given).
						// (Its pack takes its own copy of what's put in and empties what was handed it: the one in the pack is
						// the one it works on, and the one it hands back. 0.23.53.)
						this.drifter.getInventory().add(job.item().copy());
						this.working = ItemStack.EMPTY;
						for (int slot = 0; slot < this.drifter.getInventory().getContainerSize(); slot++) {
							if (ItemStack.isSameItemSameComponents(this.drifter.getInventory().getItem(slot), job.item())) {
								this.working = this.drifter.getInventory().getItem(slot);
								break;
							}
						}

						if (this.working.isEmpty()) {
							return Status.FAILURE;
						}

						for (ItemStack stack : job.materials()) {
							this.ownBefore.putIfAbsent(stack.getItem(), this.drifter.getInventory().count(other -> other.getItem() == stack.getItem()));
						}

						for (ItemStack stack : job.materials()) {
							this.drifter.getInventory().add(stack.copy());
						}

						return switch (job.kind()) {
							case "enchant" -> this.begin(new EnchantTask(this.drifter, station, stack -> stack.getItem() == job.item().getItem() && !stack.isEnchanted(),
								job.what()));
							case "netherite" -> {
								int slot = -1;
								for (int s = 0; s < this.drifter.getInventory().getContainerSize(); s++) {
									if (this.drifter.getInventory().getItem(s).getItem() == job.item().getItem()) {
										slot = s;
										break;
									}
								}

								yield slot < 0 ? Status.FAILURE : this.begin(new SmithTask(this.drifter, station, slot));
							}
							case "retrim" -> {
								// (At the smithing table: its town does the fitting when it hands it back.)
								this.drifter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
								this.drifter.level().levelEvent(1044, station, 0);
								yield Status.RUNNING;
							}
							default -> {
								this.repair();
								yield Status.RUNNING;
							}
						};
					}
					default -> {
						this.handBack(true);
						return Status.SUCCESS;
					}
				}
			}

			/**
			 * At the anvil: each unit of the material back to a quarter of its wear (as an anvil does), a level a unit from its
			 * own; what's left of the materials goes back with the item.
			 */
			private void repair() {
				ItemStack item = this.working;
				var inventory = this.drifter.getInventory();
				for (ItemStack material : job.materials()) {
					while (item.isDamaged() && this.drifter.experienceLevel() > 0 && inventory.count(stack -> stack.getItem() == material.getItem()) > 0) {
						for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
							if (inventory.getItem(slot).getItem() == material.getItem()) {
								inventory.getItem(slot).shrink(1);
								break;
							}
						}

						item.setDamageValue(Math.max(0, item.getDamageValue() - item.getMaxDamage() / 4));
						this.drifter.giveExperienceLevels(-1);
					}
				}

				this.drifter.level().levelEvent(1030, station, 0);
			}
		};
	}
}
