package com.dinglebob.drifter.command;

import java.util.Comparator;

import com.dinglebob.drifter.compat.IcyHorses;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.AABB;

/**
 * Debug for Icy's Better Horses (horse culture, docs/superpowers/plans/2026-11-16-horse-culture.md): what Drifter reads of
 * the nearest horse through {@link IcyHorses}.
 */
final class IcyCommands {
	private IcyCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("icy").requires(source -> source.hasPermission(2)).executes(ctx -> {
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			String line = "Icy's: present " + IcyHorses.present() + (horse == null ? "; no horse near" : "; " + horse.getName().getString() + ": owner "
				+ IcyHorses.owner(horse) + ", bond " + IcyHorses.bond(horse) + ", breed " + IcyHorses.breed(horse) + ", gait " + IcyHorses.gait(horse)
				+ ", favourite " + IcyHorses.favouriteTreat(horse) + ", cart " + IcyHorses.cart(horse) + ", speed "
				+ String.format(java.util.Locale.ROOT, "%.3f", horse.getDeltaMovement().horizontalDistance()) + ", gait " + com.dinglebob.drifter.skills.Gait.of(horse) + ", leashed " + horse.isLeashed() + ", saddled " + horse.isSaddled() + ", at " + horse.blockPosition().toShortString());
			String said = line + "; free stall here " + com.dinglebob.drifter.api.DrifterApi.stallNear(source.getLevel(), net.minecraft.core.BlockPos.containing(pos));
			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {}", said);
			source.sendSuccess(() -> Component.literal(said), false);
			return 1;
		}).then(Commands.literal("own").executes(ctx -> {
			// (Debug: the nearest horse made the player's own, in Icy's terms, to test that Drifters leave it be.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			if (horse == null || source.getPlayer() == null) {
				source.sendFailure(Component.literal("No horse near, or not a player"));
				return 0;
			}

			try {
				IcyHorses.setOwner(horse, source.getPlayer().getUUID());
				horse.setOwnerUUID(source.getPlayer().getUUID());
			} catch (RuntimeException e) {
				com.dinglebob.drifter.Drifter.LOGGER.warn("[icy] own failed", e);
				return 0;
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {} is the player's now", horse.getName().getString());
			source.sendSuccess(() -> Component.literal("Debug: " + horse.getName().getString() + " is yours now"), false);
			return 1;
		})).then(Commands.literal("keep").executes(ctx -> {
			// (Debug: the nearest horse made the nearest Drifter's own, its stall where it stands.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			horse.setTamed(true);
			com.dinglebob.drifter.api.DrifterApi.keepHorse(drifter, horse, horse.blockPosition());
			// (Debug: and one of its favourite treat in the Drifter's pocket, to see it given.)
			var favourite = IcyHorses.treatFor(horse);
			if (favourite != null) {
				drifter.getInventory().add(new net.minecraft.world.item.ItemStack(favourite));
			}

			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {} is {}'s now; favourite treat {}; Icy's treats {}", horse.getName().getString(), drifter.getName().getString(),
				IcyHorses.treatFor(horse), IcyHorses.treats());
			return 1;
		})).then(Commands.literal("travel").then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(ctx -> {
			// (Debug: the nearest Drifter off on a long trip there as its trips go: its horse from the stable, the road.)
			var source = ctx.getSource();
			var drifter = DrifterCommands.nearest(source);
			var to = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "to");
			if (drifter == null) {
				return 0;
			}

			drifter.getDrifterBrain().setEnabled(false);
			com.dinglebob.drifter.Drifter.LOGGER.info("[trip] {} from {}: horse {} at {}, stall {}", drifter.getName().getString(), drifter.blockPosition().toShortString(),
				drifter.getDrifterBrain().horse(), drifter.getDrifterBrain().horseAt(), drifter.getDrifterBrain().stall());
			drifter.getTasks().start(new Trip(drifter, to), source::sendSystemMessage);
			return 1;
		}))).then(Commands.literal("capture").executes(ctx -> {
			// (Debug: the nearest Drifter takes an enemy's horse tied up near it, if there is one, as a raider at the front does.)
			var source = ctx.getSource();
			var drifter = DrifterCommands.nearest(source);
			if (drifter == null) {
				return 0;
			}

			drifter.getDrifterBrain().setEnabled(false);
			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {} had horse {} (forgotten for the test)", drifter.getName().getString(), drifter.getDrifterBrain().horse());
			drifter.getDrifterBrain().setHorse(null, null);
			long now = drifter.level().getGameTime();
			for (var awareness : drifter.getSenses().tracked()) {
				if (awareness.entity instanceof AbstractHorse h) {
					java.util.UUID owner = com.dinglebob.drifter.compat.IcyHorses.owner(h);
					var theirs = owner == null ? null : source.getLevel().getEntity(owner);
					com.dinglebob.drifter.Drifter.LOGGER.info("[icy]  sees {}: tied {}, seen {}, {} off, owner {}, hostile {}", h.getName().getString(),
						com.dinglebob.drifter.skills.StableGoal.tiedUp(h), awareness.seenWithin(now, 100), String.format(java.util.Locale.ROOT, "%.1f", h.distanceTo(drifter)),
						theirs == null ? owner : theirs.getName().getString(),
						theirs instanceof com.dinglebob.drifter.entity.DrifterEntity d && com.dinglebob.drifter.world.DrifterWars.hostile(drifter, d));
				}
			}
			drifter.getTasks().start(new Again(drifter, "capture", 3, () -> com.dinglebob.drifter.skills.StableGoal.capture(drifter)), source::sendSystemMessage);
			return 1;
		})).then(Commands.literal("lead").executes(ctx -> {
			// (Debug: the nearest Drifter puts a lead on the nearest horse and walks 40 blocks east leading it.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			drifter.getDrifterBrain().setEnabled(false);
			drifter.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEAD));
			boolean on = com.dinglebob.drifter.skills.StableGoal.lead(drifter, horse);
			com.dinglebob.drifter.Drifter.LOGGER.info("[icy] lead on {}", on);
			drifter.getTasks().start(new com.dinglebob.drifter.executor.task.NavigateTask(drifter, drifter.position().add(40, 0, 0), 2.0, "lead east").leading(horse),
				source::sendSystemMessage);
			return 1;
		})).then(Commands.literal("ride").executes(ctx -> {
			// (Debug: the nearest Drifter told to ride the nearest horse 60 blocks east, whoever's it is.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			drifter.getTasks().start(new com.dinglebob.drifter.executor.task.RideTask(drifter, horse, drifter.position().add(300, 0, 0), 4.0), source::sendSystemMessage);
			return 1;
		}).then(Commands.literal("hurry").executes(ctx -> {
			// (Debug: the same, in a hurry: a gallop.)
			var source = ctx.getSource();
			var pos = source.getPosition();
			AbstractHorse horse = source.getLevel().getEntitiesOfClass(AbstractHorse.class, new AABB(pos, pos).inflate(16)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(pos))).orElse(null);
			var drifter = DrifterCommands.nearest(source);
			if (horse == null || drifter == null) {
				return 0;
			}

			drifter.getTasks().start(new com.dinglebob.drifter.executor.task.RideTask(drifter, horse, drifter.position().add(300, 0, 0), 4.0, true),
				source::sendSystemMessage);
			return 1;
		}))));
	}

	/** (Debug: a whole trip there, a stretch at a time as its trips go.) */
	private static final class Trip extends com.dinglebob.drifter.executor.Task {
		private final net.minecraft.core.BlockPos to;
		@org.jetbrains.annotations.Nullable
		private com.dinglebob.drifter.executor.Task stretch;
		private int stretches;

		Trip(com.dinglebob.drifter.entity.DrifterEntity drifter, net.minecraft.core.BlockPos to) {
			super(drifter);
			this.to = to;
		}

		@Override
		public String describe() {
			return "trip to " + this.to.toShortString();
		}

		@Override
		protected int timeoutTicks() {
			return 20 * 60 * 15;
		}

		@Override
		public com.dinglebob.drifter.executor.Task leaf() {
			return this.stretch == null ? this : this.stretch.leaf();
		}

		@Override
		protected Status onTick() {
			if (this.stretch != null) {
				Status status = this.stretch.tick();
				if (status == Status.RUNNING) {
					return status;
				}

				this.stretch.stop();
				com.dinglebob.drifter.Drifter.LOGGER.info("[trip] {}: {} ({}) riding {}", this.drifter.getName().getString(), this.stretch.describe(),
					status == Status.SUCCESS ? "done" : this.stretch.failureReason(), this.drifter.getVehicle());
				this.stretch = null;
			}

			if (this.drifter.blockPosition().closerThan(this.to, 8.0) && !this.drifter.isPassenger()) {
				return Status.SUCCESS;
			}

			if (++this.stretches > 40) {
				return this.fail("too many stretches");
			}

			this.stretch = com.dinglebob.drifter.nav.Legs.toward(this.drifter, this.to, 4.0, "travel");
			return Status.RUNNING;
		}

		@Override
		protected void onStop() {
			if (this.stretch != null) {
				this.stretch.stop();
			}
		}
	}

	/** (Debug: a step worked out afresh each time, up to so many times, till there's none.) */
	private static final class Again extends com.dinglebob.drifter.executor.Task {
		private final String what;
		private final java.util.function.Supplier<com.dinglebob.drifter.executor.Task> next;
		private int left;
		@org.jetbrains.annotations.Nullable
		private com.dinglebob.drifter.executor.Task step;

		Again(com.dinglebob.drifter.entity.DrifterEntity drifter, String what, int times, java.util.function.Supplier<com.dinglebob.drifter.executor.Task> next) {
			super(drifter);
			this.what = what;
			this.left = times;
			this.next = next;
		}

		@Override
		public String describe() {
			return this.what;
		}

		@Override
		protected Status onTick() {
			if (this.step != null) {
				Status status = this.step.tick();
				if (status == Status.RUNNING) {
					return status;
				}

				this.step.stop();
				com.dinglebob.drifter.Drifter.LOGGER.info("[icy] {}: {} ({})", this.drifter.getName().getString(), this.step.describe(),
					status == Status.SUCCESS ? "done" : this.step.failureReason());
				this.step = null;
			}

			if (this.left-- <= 0 || (this.step = this.next.get()) == null) {
				return Status.SUCCESS;
			}

			return Status.RUNNING;
		}

		@Override
		protected void onStop() {
			if (this.step != null) {
				this.step.stop();
			}
		}
	}
}
