package com.dinglebob.drifter.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.debug.WoodToStoneScript;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.CollectItemsTask;
import com.dinglebob.drifter.executor.task.ContainerTask;
import com.dinglebob.drifter.executor.task.CraftTask;
import com.dinglebob.drifter.executor.task.MineTask;
import com.dinglebob.drifter.executor.task.MoveToTask;
import com.dinglebob.drifter.executor.task.PlaceBlockTask;
import com.dinglebob.drifter.executor.task.SelectItemTask;
import com.dinglebob.drifter.executor.task.SequenceTask;
import com.dinglebob.drifter.executor.task.UseItemTask;
import com.dinglebob.drifter.executor.task.UseOnBlockTask;
import com.dinglebob.drifter.executor.task.WaitTask;
import com.dinglebob.drifter.executor.task.WearArmorTask;
import com.dinglebob.drifter.hands.Containers;
import com.dinglebob.drifter.hands.Reach;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 2 (Hands) debug commands: each starts one executor task on the nearest Drifter, which reports
 * "Done" or "Failed (reason)" in chat when it finishes. Starting a new one cancels the old one.
 */
final class HandsCommands {
	/** Vanilla furnace slot numbers (AbstractFurnaceBlockEntity keeps them protected). */
	private static final int FURNACE_INPUT = 0;
	private static final int FURNACE_FUEL = 1;
	private static final int FURNACE_RESULT = 2;
	/** Ticks a standard furnace takes per item; smokers and blast furnaces are faster but burn fuel faster too. */
	private static final int FURNACE_TICKS_PER_ITEM = 200;
	private static final SimpleCommandExceptionType NO_FACE = new SimpleCommandExceptionType(
		Component.literal("The Drifter can't see or reach any face of that block"));

	private HandsCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
		root.then(Commands.literal("mine")
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> start(ctx, drifter -> new MineTask(drifter, BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));

		root.then(Commands.literal("place")
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> start(ctx, drifter -> {
					ItemStack held = drifter.getMainHandItem();
					Item item = held.getItem();
					return new PlaceBlockTask(drifter, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
						stack -> !held.isEmpty() && stack.is(item), held.isEmpty() ? "its held item" : held.getHoverName().getString());
				}))
				.then(Commands.argument("item", ItemPredicateArgument.itemPredicate(context))
					.executes(ctx -> start(ctx, drifter -> new PlaceBlockTask(drifter, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
						ItemPredicateArgument.getItemPredicate(ctx, "item"), argText(ctx, "item")))))));

		root.then(Commands.literal("useon")
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> {
					BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
					DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
					Direction face = nearestVisibleFace(drifter, pos).orElseThrow(NO_FACE::create);
					return start(ctx, drifter, new UseOnBlockTask(drifter, pos, face,
						"use " + DrifterCommands.describe(drifter.getMainHandItem()) + " on " + pos.toShortString()));
				})));

		root.then(Commands.literal("use").executes(ctx -> start(ctx, UseItemTask::new)));

		root.then(Commands.literal("eat").executes(ctx -> start(ctx, drifter -> new SequenceTask(drifter, "eat", List.of(
			new SelectItemTask(drifter, stack -> isEdible(drifter, stack), "food"),
			new UseItemTask(drifter))))));

		root.then(Commands.literal("craft")
			.then(Commands.argument("item", ItemPredicateArgument.itemPredicate(context))
				.executes(ctx -> craft(ctx, 1))
				.then(Commands.argument("times", IntegerArgumentType.integer(1, 64))
					.executes(ctx -> craft(ctx, IntegerArgumentType.getInteger(ctx, "times"))))));

		root.then(Commands.literal("container")
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.then(transfer("put", ContainerTask.Mode.PUT, context))
				.then(transfer("take", ContainerTask.Mode.TAKE, context))
				.then(Commands.literal("putall").executes(ctx -> containerAll(ctx, ContainerTask.Mode.PUT)))
				.then(Commands.literal("takeall").executes(ctx -> containerAll(ctx, ContainerTask.Mode.TAKE)))));

		root.then(Commands.literal("smelt")
			.then(Commands.argument("furnace", BlockPosArgument.blockPos())
				.then(Commands.argument("input", ItemPredicateArgument.itemPredicate(context))
					.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
						.then(Commands.argument("fuel", ItemPredicateArgument.itemPredicate(context))
							.executes(HandsCommands::smelt))))));

		root.then(Commands.literal("wear").executes(ctx -> start(ctx, WearArmorTask::new)));

		root.then(Commands.literal("swaphands").executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			drifter.getInventory().swapHands();
			ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " swapped hands: now holding "
				+ DrifterCommands.describe(drifter.getMainHandItem()) + ", offhand " + DrifterCommands.describe(drifter.getOffhandItem())), false);
			return 1;
		}));

		root.then(Commands.literal("walkto")
			.then(Commands.argument("pos", BlockPosArgument.blockPos())
				.executes(ctx -> start(ctx, drifter -> new MoveToTask(drifter, Vec3.atBottomCenterOf(BlockPosArgument.getBlockPos(ctx, "pos")), 0.5)))));

		root.then(Commands.literal("collect").executes(ctx -> start(ctx, drifter -> new CollectItemsTask(drifter, 8.0))));

		root.then(Commands.literal("script")
			.then(Commands.literal("woodtostone")
				.executes(ctx -> start(ctx, drifter -> new WoodToStoneScript(drifter, ctx.getSource()::sendSystemMessage)))));

		root.then(Commands.literal("stop").executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			boolean brainWasOn = drifter.getDrifterBrain().isEnabled();
			drifter.getDrifterBrain().setEnabled(false);
			if (brainWasOn) {
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + "'s brain is off (/drifter brain on to resume)"), false);
			}

			if (!drifter.getTasks().cancel("stopped by command") && !brainWasOn) {
				ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + " wasn't doing anything"), false);
			}

			drifter.getKeys().releaseAll();
			drifter.getCamera().clearTarget();
			return 1;
		}));

		root.then(Commands.literal("task").executes(ctx -> {
			DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
			Task current = drifter.getTasks().current();
			ctx.getSource().sendSuccess(() -> Component.literal(DrifterCommands.label(drifter) + ": "
				+ (current == null ? "no task" : current.describe())), false);
			return 1;
		}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> transfer(String name, ContainerTask.Mode mode, CommandBuildContext context) {
		return Commands.literal(name)
			.then(Commands.argument("item", ItemPredicateArgument.itemPredicate(context))
				.executes(ctx -> containerItem(ctx, mode, Integer.MAX_VALUE))
				.then(Commands.argument("count", IntegerArgumentType.integer(1))
					.executes(ctx -> containerItem(ctx, mode, IntegerArgumentType.getInteger(ctx, "count")))));
	}

	private static int containerItem(CommandContext<CommandSourceStack> ctx, ContainerTask.Mode mode, int count) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		Predicate<ItemStack> filter = ItemPredicateArgument.getItemPredicate(ctx, "item");
		return start(ctx, drifter -> new ContainerTask(drifter, pos, mode, filter, argText(ctx, "item"), count, null));
	}

	private static int containerAll(CommandContext<CommandSourceStack> ctx, ContainerTask.Mode mode) throws CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		return start(ctx, drifter -> new ContainerTask(drifter, pos, mode, stack -> true, "items", Integer.MAX_VALUE, null));
	}

	private static int craft(CommandContext<CommandSourceStack> ctx, int times) throws CommandSyntaxException {
		Predicate<ItemStack> wanted = ItemPredicateArgument.getItemPredicate(ctx, "item");
		return start(ctx, drifter -> new CraftTask(drifter, wanted, argText(ctx, "item"), times, null));
	}

	/** Loads a furnace with input and just enough fuel, waits for it to finish, then takes the results out. */
	private static int smelt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BlockPos furnace = BlockPosArgument.getLoadedBlockPos(ctx, "furnace");
		Predicate<ItemStack> input = ItemPredicateArgument.getItemPredicate(ctx, "input");
		Predicate<ItemStack> fuel = ItemPredicateArgument.getItemPredicate(ctx, "fuel");
		int count = IntegerArgumentType.getInteger(ctx, "count");
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		if (!(drifter.level().getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity)) {
			throw new SimpleCommandExceptionType(Component.literal("That isn't a furnace, smoker or blast furnace")).create();
		}

		int fuelSlot = drifter.getInventory().findSlot(fuel, 0, DrifterInventory.MAIN_SIZE);
		Map<Item, Integer> burnTimes = AbstractFurnaceBlockEntity.getFuel();
		Integer burnTicks = fuelSlot < 0 ? null : burnTimes.get(drifter.getInventory().getItem(fuelSlot).getItem());
		if (burnTicks == null || burnTicks <= 0) {
			throw new SimpleCommandExceptionType(Component.literal("The Drifter has no " + argText(ctx, "fuel") + " that burns")).create();
		}

		int fuelCount = Math.max(1, (int)Math.ceil(count * (double)FURNACE_TICKS_PER_ITEM / burnTicks));
		int timeout = count * FURNACE_TICKS_PER_ITEM * 3 + 20 * 10;
		List<Task> steps = new ArrayList<>();
		steps.add(new ContainerTask(drifter, furnace, ContainerTask.Mode.PUT, input, argText(ctx, "input"), count, new int[]{FURNACE_INPUT}));
		steps.add(new ContainerTask(drifter, furnace, ContainerTask.Mode.PUT, fuel, argText(ctx, "fuel"), fuelCount, new int[]{FURNACE_FUEL}));
		steps.add(new WaitTask(drifter, "the furnace to finish", () -> furnaceFinished(drifter, furnace), timeout));
		steps.add(new ContainerTask(drifter, furnace, ContainerTask.Mode.TAKE, stack -> true, "smelted items", Integer.MAX_VALUE, new int[]{FURNACE_RESULT}));
		return start(ctx, drifter, new SequenceTask(drifter, "smelt " + count + " " + argText(ctx, "input"), steps));
	}

	private static boolean furnaceFinished(DrifterEntity drifter, BlockPos furnace) {
		Container container = Containers.get(drifter.level(), furnace);
		if (container == null) {
			return true;
		}

		// Done when the last input item is cooked, or the fuel ran out and the fire went out (partial batch).
		boolean lit = drifter.level().getBlockState(furnace).getOptionalValue(AbstractFurnaceBlock.LIT).orElse(false);
		return container.getItem(FURNACE_INPUT).isEmpty() || !lit && container.getItem(FURNACE_FUEL).isEmpty();
	}

	private static boolean isEdible(DrifterEntity drifter, ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food != null && drifter.canEat(food.canAlwaysEat());
	}

	private static Optional<Direction> nearestVisibleFace(DrifterEntity drifter, BlockPos pos) {
		Vec3 eye = drifter.getEyePosition();
		Direction best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction face : Direction.values()) {
			Optional<Vec3> point = Reach.aimPoint(drifter, pos, face);
			if (point.isPresent() && eye.distanceToSqr(point.get()) < bestDistance) {
				bestDistance = eye.distanceToSqr(point.get());
				best = face;
			}
		}

		return Optional.ofNullable(best);
	}

	/** The text the player typed for an argument, for messages ("#logs", "oak_planks"). */
	private static String argText(CommandContext<CommandSourceStack> ctx, String name) {
		for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
			if (node.getNode().getName().equals(name)) {
				return node.getRange().get(ctx.getInput()).replace("minecraft:", "");
			}
		}

		return name;
	}

	@FunctionalInterface
	private interface TaskFactory {
		Task create(DrifterEntity drifter) throws CommandSyntaxException;
	}

	private static int start(CommandContext<CommandSourceStack> ctx, TaskFactory factory) throws CommandSyntaxException {
		DrifterEntity drifter = DrifterCommands.nearest(ctx.getSource());
		return start(ctx, drifter, factory.create(drifter));
	}

	private static int start(CommandContext<CommandSourceStack> ctx, DrifterEntity drifter, Task task) {
		CommandSourceStack source = ctx.getSource();
		drifter.getTasks().start(task, source::sendSystemMessage);
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s starts: %s", DrifterCommands.label(drifter), task.describe())), false);
		return 1;
	}
}
