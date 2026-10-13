package com.dinglebob.drifter.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.entity.SkinPool;
import com.dinglebob.drifter.motor.CameraModel;
import com.dinglebob.drifter.motor.VirtualKeys;
import com.dinglebob.drifter.registry.ModEntities;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Debug commands for driving a Drifter by hand (DESIGN.md section 25). Every command except spawn acts on the
 * Drifter nearest to whoever runs it (within {@link #SEARCH_RADIUS} blocks). Operator level 2.
 */
public final class DrifterCommands {
	public static final double SEARCH_RADIUS = 128.0;
	private static final SimpleCommandExceptionType NO_DRIFTER = new SimpleCommandExceptionType(
		Component.literal("No Drifter within " + (int)SEARCH_RADIUS + " blocks")
	);
	private static final SimpleCommandExceptionType SPAWN_FAILED = new SimpleCommandExceptionType(Component.literal("Could not spawn a Drifter here"));

	private DrifterCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher, registryAccess));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("drifter").requires(source -> source.hasPermission(2));

		root.then(Commands.literal("spawn")
			.executes(ctx -> spawn(ctx, null, null))
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"), null))
				.then(Commands.literal("wide").executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"), false)))
				.then(Commands.literal("slim").executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"), true)))));

		root.then(Commands.literal("keys")
			.then(Commands.argument("keys", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
					List.of("none", "forward", "forward+sprint", "forward+sprint+jump", "forward+sneak", "back", "left", "right", "forward+left", "jump", "sneak"),
					builder))
				.executes(ctx -> keys(ctx, -1))
				.then(Commands.argument("ticks", IntegerArgumentType.integer(1))
					.executes(ctx -> keys(ctx, IntegerArgumentType.getInteger(ctx, "ticks"))))));

		root.then(Commands.literal("look")
			.then(Commands.argument("yaw", FloatArgumentType.floatArg())
				.then(Commands.argument("pitch", FloatArgumentType.floatArg(-90.0F, 90.0F))
					.executes(ctx -> lookAngles(ctx, CameraModel.TurnSpeed.NORMAL))
					.then(speedArgument().executes(ctx -> lookAngles(ctx, speed(ctx)))))));

		root.then(Commands.literal("lookat")
			.then(Commands.literal("me")
				.executes(ctx -> lookAtMe(ctx, CameraModel.TurnSpeed.NORMAL))
				.then(speedArgument().executes(ctx -> lookAtMe(ctx, speed(ctx)))))
			.then(Commands.literal("stop").executes(DrifterCommands::lookStop))
			.then(Commands.argument("pos", Vec3Argument.vec3())
				.executes(ctx -> lookAtPos(ctx, CameraModel.TurnSpeed.NORMAL))
				.then(speedArgument().executes(ctx -> lookAtPos(ctx, speed(ctx))))));

		root.then(Commands.literal("slot")
			.then(Commands.argument("slot", IntegerArgumentType.integer(0, DrifterInventory.HOTBAR_SIZE - 1))
				.executes(DrifterCommands::slot)));

		root.then(Commands.literal("give")
			.then(Commands.argument("item", ItemArgument.item(buildContext))
				.executes(ctx -> give(ctx, 1))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 64 * DrifterInventory.MAIN_SIZE))
					.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));

		root.then(Commands.literal("food")
			.then(Commands.argument("food", IntegerArgumentType.integer(0, FoodConstants.MAX_FOOD))
				.executes(ctx -> food(ctx, null))
				.then(Commands.argument("saturation", FloatArgumentType.floatArg(0.0F, FoodConstants.MAX_SATURATION))
					.executes(ctx -> food(ctx, FloatArgumentType.getFloat(ctx, "saturation"))))));

		root.then(Commands.literal("info").executes(DrifterCommands::info));
		root.then(Commands.literal("inventory").executes(DrifterCommands::inventory));
		root.then(Commands.literal("remove").executes(DrifterCommands::remove));
		HandsCommands.register(root, buildContext);

		dispatcher.register(root);
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> speedArgument() {
		return Commands.argument("speed", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
				java.util.Arrays.stream(CameraModel.TurnSpeed.values()).map(CameraModel.TurnSpeed::id), builder));
	}

	private static CameraModel.TurnSpeed speed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "speed");
		for (CameraModel.TurnSpeed speed : CameraModel.TurnSpeed.values()) {
			if (speed.id().equals(id.toLowerCase(Locale.ROOT))) {
				return speed;
			}
		}

		throw new SimpleCommandExceptionType(Component.literal("Unknown speed '" + id + "' (idle, normal or combat)")).create();
	}

	static DrifterEntity nearest(CommandSourceStack source) throws CommandSyntaxException {
		Vec3 pos = source.getPosition();
		Optional<DrifterEntity> found = source.getLevel()
			.getEntitiesOfClass(DrifterEntity.class, new AABB(pos, pos).inflate(SEARCH_RADIUS), DrifterEntity::isAlive)
			.stream()
			.min(Comparator.comparingDouble(drifter -> drifter.distanceToSqr(pos)));
		return found.orElseThrow(NO_DRIFTER::create);
	}

	static String label(DrifterEntity drifter) {
		return drifter.getName().getString();
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, String name, Boolean slim) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		DrifterEntity drifter = ModEntities.DRIFTER.create(level);
		if (drifter == null) {
			throw SPAWN_FAILED.create();
		}

		Vec3 pos = source.getPosition();
		float yaw = source.getRotation().y;
		drifter.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		drifter.setYHeadRot(yaw);
		drifter.setYBodyRot(yaw);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(drifter.blockPosition()), MobSpawnType.COMMAND, null);
		if (slim != null) {
			drifter.setSkin(SkinPool.pick(drifter.getRandom(), slim));
		}

		if (name != null) {
			drifter.setCustomName(Component.literal(name));
			drifter.setCustomNameVisible(true);
		}

		if (!level.addFreshEntity(drifter)) {
			throw SPAWN_FAILED.create();
		}

		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Spawned %s with skin %s (%s arms)",
			label(drifter), drifter.getSkinTexture(), drifter.hasSlimArms() ? "slim" : "wide")), true);
		return 1;
	}

	private static int keys(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		List<VirtualKeys.Key> pressed;
		try {
			pressed = VirtualKeys.parse(StringArgumentType.getString(ctx, "keys"));
		} catch (IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Component.literal(
				"Unknown key '" + e.getMessage() + "'. Keys: forward, back, left, right, jump, sneak, sprint (join with +), or none")).create();
		}

		drifter.holdKeys(pressed, ticks);
		String duration = ticks > 0 ? " for " + ticks + " ticks" : "";
		ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " holds " + drifter.getKeys().describe() + duration), false);
		return 1;
	}

	private static int lookAngles(CommandContext<CommandSourceStack> ctx, CameraModel.TurnSpeed speed) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		drifter.getCamera().lookAt(FloatArgumentType.getFloat(ctx, "yaw"), FloatArgumentType.getFloat(ctx, "pitch"), speed);
		ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " looks at " + drifter.getCamera().describeTarget()), false);
		return 1;
	}

	private static int lookAtMe(CommandContext<CommandSourceStack> ctx, CameraModel.TurnSpeed speed) throws CommandSyntaxException {
		Player player = ctx.getSource().getPlayerOrException();
		DrifterEntity drifter = nearest(ctx.getSource());
		drifter.getCamera().lookAt(player, speed);
		ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " tracks " + drifter.getCamera().describeTarget()), false);
		return 1;
	}

	private static int lookAtPos(CommandContext<CommandSourceStack> ctx, CameraModel.TurnSpeed speed) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		drifter.getCamera().lookAt(Vec3Argument.getVec3(ctx, "pos"), speed);
		ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " looks at " + drifter.getCamera().describeTarget()), false);
		return 1;
	}

	private static int lookStop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		drifter.getCamera().clearTarget();
		ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " stops looking at anything"), false);
		return 1;
	}

	private static int slot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		drifter.getInventory().requestSelect(slot);
		ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " switches to hotbar slot " + slot), false);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		var input = ItemArgument.getItem(ctx, "item");
		int maxStack = input.getItem().getDefaultMaxStackSize();
		int remaining = count;
		int given = 0;
		while (remaining > 0) {
			int amount = Math.min(maxStack, remaining);
			ItemStack stack = input.createItemStack(amount, false);
			drifter.getInventory().add(stack);
			given += amount - stack.getCount();
			remaining -= amount;
			if (!stack.isEmpty()) {
				break;
			}
		}

		int total = given;
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Gave %d %s to %s%s",
			total, input.getItem().getDescription().getString(), label(drifter), total < count ? " (inventory full)" : "")), true);
		return total;
	}

	private static int food(CommandContext<CommandSourceStack> ctx, Float saturation) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		int foodLevel = IntegerArgumentType.getInteger(ctx, "food");
		drifter.getFoodData().setFoodLevel(foodLevel);
		float sat = saturation == null ? Math.min(drifter.getFoodData().getSaturationLevel(), foodLevel) : Math.min(saturation, foodLevel);
		drifter.getFoodData().setSaturation(sat);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s food %d, saturation %.1f", label(drifter), foodLevel, sat)), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		var food = drifter.getFoodData();
		var inventory = drifter.getInventory();
		List<String> lines = List.of(
			String.format(Locale.ROOT, "%s at %.1f %.1f %.1f, skin %s (%s arms)", label(drifter), drifter.getX(), drifter.getY(), drifter.getZ(),
				drifter.getSkinTexture(), drifter.hasSlimArms() ? "slim" : "wide"),
			String.format(Locale.ROOT, "Health %.1f/%.1f, armor %d, food %d, saturation %.1f, exhaustion %.2f",
				drifter.getHealth(), drifter.getMaxHealth(), drifter.getArmorValue(), food.getFoodLevel(), food.getSaturationLevel(), food.getExhaustionLevel()),
			String.format(Locale.ROOT, "Pose %s, sprinting %s, sneak key %s, on ground %s, horizontal speed last tick %.2f blocks/s",
				drifter.getPose().name().toLowerCase(Locale.ROOT), drifter.isSprinting(), drifter.isShiftKeyDown(), drifter.onGround(),
				Math.sqrt(Mth.square(drifter.getX() - drifter.xo) + Mth.square(drifter.getZ() - drifter.zo)) * 20.0),
			String.format(Locale.ROOT, "Keys %s; camera yaw %.1f pitch %.1f, body yaw %.1f, target %s",
				drifter.getKeys().describe(), drifter.getYRot(), drifter.getXRot(), drifter.yBodyRot, drifter.getCamera().describeTarget()),
			String.format(Locale.ROOT, "Hotbar slot %d%s holding %s",
				inventory.getSelected(), inventory.isSwitching() ? " (switching)" : "", describe(inventory.getSelectedItem()))
		);
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}

		return 1;
	}

	private static int inventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		DrifterInventory inventory = drifter.getInventory();
		StringBuilder text = new StringBuilder(label(drifter)).append("'s inventory:");
		boolean any = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				any = true;
				text.append("\n  ").append(slotName(slot)).append(": ").append(describe(stack));
			}
		}

		if (!any) {
			text.append(" empty");
		}

		String message = text.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static String slotName(int slot) {
		if (slot < DrifterInventory.HOTBAR_SIZE) {
			return "hotbar " + slot;
		} else if (slot < DrifterInventory.MAIN_SIZE) {
			return "main " + slot;
		} else if (slot < DrifterInventory.OFFHAND_SLOT) {
			return new String[]{"feet", "legs", "chest", "head"}[slot - DrifterInventory.MAIN_SIZE];
		}

		return "offhand";
	}

	static String describe(ItemStack stack) {
		return stack.isEmpty() ? "nothing" : stack.getCount() + "x " + stack.getHoverName().getString();
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DrifterEntity drifter = nearest(ctx.getSource());
		String name = label(drifter);
		drifter.discard();
		ctx.getSource().sendSuccess(() -> Component.literal("Removed " + name), true);
		return 1;
	}
}
