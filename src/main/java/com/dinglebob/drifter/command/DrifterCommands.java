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

import com.dinglebob.drifter.entity.DetailTier;
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

		// Many at once, spread round where you stand (user's request: the 100-Drifter spread test, to try themselves).
		root.then(Commands.literal("spawnmany")
			.then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_SPAWN_MANY))
				.executes(ctx -> spawnMany(ctx, IntegerArgumentType.getInteger(ctx, "count"), DEFAULT_SPREAD))
				.then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_SPREAD))
					.executes(ctx -> spawnMany(ctx, IntegerArgumentType.getInteger(ctx, "count"), IntegerArgumentType.getInteger(ctx, "radius"))))));

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

		// Debug: experience levels (vanilla /xp only works on players).
		// Finding them (user's request): a list of every loaded Drifter with where it is, each name a click to go there, and a
		// teleport by name.
		root.then(Commands.literal("list").executes(DrifterCommands::list));
		// Timing per system (section 22 budget).
		root.then(Commands.literal("perf").executes(ctx -> {
			java.util.Map<DetailTier, Integer> tiers = new java.util.EnumMap<>(DetailTier.class);
			for (net.minecraft.server.level.ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
				for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
					tiers.merge(drifter.tier(), 1, Integer::sum);
				}
			}

			String counts = tiers.isEmpty() ? "none loaded" : tiers.entrySet().stream()
				.map(e -> e.getValue() + " " + e.getKey().name().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.joining(", "));
			ctx.getSource().sendSuccess(() -> Component.literal(com.dinglebob.drifter.debug.DrifterPerf.report() + "\nDetail: " + counts), false);
			return 1;
		}));
		// Run catch-up on the nearest Drifter as if it had been away this many in-game hours (debug, section 25).
		root.then(Commands.literal("catchup").then(Commands.argument("hours", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 240))
			.executes(ctx -> {
				DrifterEntity drifter = nearest(ctx.getSource());
				int hours = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "hours");
				var report = com.dinglebob.drifter.world.CatchUp.run(drifter, hours * 1000L);
				ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " " + report.describe()), false);
				return 1;
			})));
		// Force the nearest Drifter's detail tier (debug; "auto" goes back to choosing by distance).
		root.then(Commands.literal("tier").then(Commands.argument("tier", com.mojang.brigadier.arguments.StringArgumentType.word())
			.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("auto", "full", "reduced", "far"), builder))
			.executes(ctx -> {
				String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "tier");
				DetailTier forced = null;
				if (!name.equals("auto")) {
					try {
						forced = DetailTier.valueOf(name.toUpperCase(java.util.Locale.ROOT));
					} catch (IllegalArgumentException e) {
						ctx.getSource().sendFailure(Component.literal("Use auto, full, reduced or far"));
						return 0;
					}
				}

				DrifterEntity drifter = nearest(ctx.getSource());
				drifter.forceTier(forced);
				ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " detail: " + drifter.tier().name().toLowerCase(java.util.Locale.ROOT)
					+ (name.equals("auto") ? " (automatic)" : " (forced)")), false);
				return 1;
			})));
		// Who is kept running away from players (section 22 hybrid simulation).
		root.then(Commands.literal("sim").executes(ctx -> {
			var list = com.dinglebob.drifter.world.DrifterSimulation.simulated();
			ctx.getSource().sendSuccess(() -> Component.literal(list.isEmpty() ? "No Drifters are being kept running away from players right now"
				: "Kept running away from players (" + list.size() + " of " + com.dinglebob.drifter.DrifterConfig.simulatedDrifters() + "):"), false);
			for (var s : list) {
				ctx.getSource().sendSuccess(() -> Component.literal("  " + s.name() + " near " + s.chunk().getMiddleBlockX() + ", "
					+ s.chunk().getMiddleBlockZ() + " (" + s.level().location().getPath() + ")"), false);
			}

			// Traveling outside the running world (section 22 abstract travel).
			for (String line : com.dinglebob.drifter.world.DrifterTravel.describe(ctx.getSource().getServer())) {
				ctx.getSource().sendSuccess(() -> Component.literal("  Traveling: " + line), false);
			}

			return list.size();
		}));
		root.then(Commands.literal("tp")
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(com.dinglebob.drifter.world.DrifterRegistry.all().stream()
					.map(com.dinglebob.drifter.world.DrifterRegistry.Known::name).distinct(), builder))
				.executes(DrifterCommands::teleportTo)));

		root.then(Commands.literal("xp")
			.then(Commands.argument("levels", IntegerArgumentType.integer(0, 100))
				.executes(ctx -> {
					DrifterEntity drifter = nearest(ctx.getSource());
					drifter.giveExperienceLevels(IntegerArgumentType.getInteger(ctx, "levels"));
					ctx.getSource().sendSuccess(() -> Component.literal(label(drifter) + " is now level " + drifter.experienceLevel()), true);
					return drifter.experienceLevel();
				})));

		// Every Drifter at once (vanilla /give and @a only pick real players).
		root.then(Commands.literal("giveall")
			.then(Commands.argument("item", ItemArgument.item(buildContext))
				.executes(ctx -> giveAll(ctx, 1))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 64 * DrifterInventory.MAIN_SIZE))
					.executes(ctx -> giveAll(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));

		root.then(Commands.literal("config")
			.executes(DrifterCommands::showConfig)
			.then(Commands.literal("chat")
				.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setChatLines(com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("simulated")
				.then(Commands.argument("count", IntegerArgumentType.integer(0, com.dinglebob.drifter.DrifterConfig.MAX_SIMULATED)).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setSimulatedDrifters(IntegerArgumentType.getInteger(ctx, "count"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("offline")
				.then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("off", "game_time", "real_time"), builder))
					.executes(ctx -> {
						try {
							com.dinglebob.drifter.DrifterConfig.setOfflineProgression(com.dinglebob.drifter.DrifterConfig.OfflineProgression.valueOf(
								com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode").toUpperCase(java.util.Locale.ROOT)));
						} catch (IllegalArgumentException e) {
							ctx.getSource().sendFailure(Component.literal("Use off, game_time or real_time"));
							return 0;
						}

						return showConfig(ctx);
					})))
			.then(Commands.literal("aggression")
				.then(Commands.literal("players").then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100)).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setPlayerAggression(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value"));
					return showConfig(ctx);
				})))
				.then(Commands.literal("drifters").then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100)).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setDrifterAggression(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value"));
					return showConfig(ctx);
				}))))
			.then(Commands.literal("whims")
				.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setWhims(com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("wars")
				.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setDrifterWars(com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("banditgriefing")
				.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setBanditGriefing(com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("raiding")
				.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setRaiding(com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("share")
				.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					com.dinglebob.drifter.DrifterConfig.setShareReputation(com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"));
					return showConfig(ctx);
				})))
			.then(Commands.literal("sight")
				.then(Commands.argument("chunks", IntegerArgumentType.integer(com.dinglebob.drifter.DrifterConfig.MIN_SIGHT_CHUNKS,
						com.dinglebob.drifter.DrifterConfig.MAX_SIGHT_CHUNKS))
					.executes(ctx -> {
						com.dinglebob.drifter.DrifterConfig.setSightChunks(IntegerArgumentType.getInteger(ctx, "chunks"));
						return showConfig(ctx);
					})))
			.then(Commands.literal("path")
				.then(Commands.argument("blocks", IntegerArgumentType.integer(com.dinglebob.drifter.DrifterConfig.MIN_PATH_RANGE,
						com.dinglebob.drifter.DrifterConfig.MAX_PATH_RANGE))
					.executes(ctx -> {
						com.dinglebob.drifter.DrifterConfig.setPathRange(IntegerArgumentType.getInteger(ctx, "blocks"));
						return showConfig(ctx);
					})))
			.then(Commands.literal("fov")
				.then(Commands.argument("degrees", IntegerArgumentType.integer(com.dinglebob.drifter.DrifterConfig.MIN_FOV,
						com.dinglebob.drifter.DrifterConfig.MAX_FOV))
					.executes(ctx -> {
						com.dinglebob.drifter.DrifterConfig.setFov(IntegerArgumentType.getInteger(ctx, "degrees"));
						return showConfig(ctx);
					}))));

		root.then(Commands.literal("food")
			.then(Commands.argument("food", IntegerArgumentType.integer(0, FoodConstants.MAX_FOOD))
				.executes(ctx -> food(ctx, null))
				.then(Commands.argument("saturation", FloatArgumentType.floatArg(0.0F, FoodConstants.MAX_SATURATION))
					.executes(ctx -> food(ctx, FloatArgumentType.getFloat(ctx, "saturation"))))));

		root.then(Commands.literal("info").executes(DrifterCommands::info));
		// Debug: one line per tick in the server log (position, vertical speed, water state, keys, path step).
		root.then(Commands.literal("trace").then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20 * 120))
			.executes(ctx -> {
				DrifterEntity drifter = nearest(ctx.getSource());
				drifter.trace(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"));
				ctx.getSource().sendSuccess(() -> Component.literal("Tracing " + label(drifter) + " in the server log"), false);
				return 1;
			})));
		root.then(Commands.literal("inventory").executes(DrifterCommands::inventory));
		root.then(Commands.literal("remove").executes(DrifterCommands::remove));
		HandsCommands.register(root, buildContext);
		SensesCommands.register(root);
		NavCommands.register(root);
		BrainCommands.register(root);
		PersonalityCommands.register(root);
		SocialCommands.register(root);
		WhimCommands.register(root);
		HouseCommands.register(root);

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
		// Not inside blocks: from the console the spot is the world spawn's corner, sometimes in a tree (found in testing:
		// it suffocated there). The nearest open spot with ground under it, in the middle of a block, as players spawn.
		if (!level.noCollision(drifter)) {
			net.minecraft.core.BlockPos start = net.minecraft.core.BlockPos.containing(pos);
			Vec3 best = null;
			for (net.minecraft.core.BlockPos spot : net.minecraft.core.BlockPos.withinManhattan(start, 6, 8, 6)) {
				Vec3 middle = Vec3.atBottomCenterOf(spot);
				drifter.moveTo(middle.x, middle.y, middle.z, yaw, 0.0F);
				if (level.noCollision(drifter) && !level.getBlockState(spot.below()).getCollisionShape(level, spot.below()).isEmpty()
					&& level.getFluidState(spot).isEmpty()) {
					best = middle;
					break;
				}
			}

			Vec3 at = best != null ? best : pos;
			drifter.moveTo(at.x, at.y, at.z, yaw, 0.0F);
		}

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

	private static final int MAX_SPAWN_MANY = 200;
	private static final int DEFAULT_SPREAD = 2000;
	private static final int MAX_SPREAD = 10000;

	/**
	 * Spawns {@code count} Drifters at random spots on dry ground within {@code radius} blocks of where the command is run
	 * (spread evenly over the area). The land there is generated as needed, which takes a moment for big areas.
	 */
	private static int spawnMany(CommandContext<CommandSourceStack> ctx, int count, int radius) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		Vec3 center = source.getPosition();
		net.minecraft.util.RandomSource random = level.getRandom();
		long started = System.currentTimeMillis();
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			for (int attempt = 0; attempt < 10; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double distance = radius * Math.sqrt(random.nextDouble());
				int x = (int)Math.floor(center.x + Math.cos(angle) * distance);
				int z = (int)Math.floor(center.z + Math.sin(angle) * distance);
				level.getChunk(x >> 4, z >> 4);
				net.minecraft.core.BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					new net.minecraft.core.BlockPos(x, 0, z));
				// Dry ground: not on water or lava, nor on leaves.
				if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()
					|| level.getBlockState(top.below()).getCollisionShape(level, top.below()).isEmpty()) {
					continue;
				}

				DrifterEntity drifter = ModEntities.DRIFTER.create(level);
				if (drifter == null) {
					throw SPAWN_FAILED.create();
				}

				float yaw = random.nextFloat() * 360.0F;
				drifter.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, yaw, 0.0F);
				drifter.setYHeadRot(yaw);
				drifter.setYBodyRot(yaw);
				drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(drifter.blockPosition()), MobSpawnType.COMMAND, null);
				if (level.addFreshEntity(drifter)) {
					spawned++;
					// Running for a few seconds, as if someone had just passed: the busiest get picked to keep running.
					level.getChunkSource().addRegionTicket(com.dinglebob.drifter.world.DrifterSimulation.SPAWNED, drifter.chunkPosition(), 2,
						drifter.chunkPosition());
				}

				break;
			}
		}

		int total = spawned;
		long seconds = (System.currentTimeMillis() - started) / 1000L;
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
			"Spawned %d Drifters within %d blocks (%d s to load the land). /drifter list shows them all, /drifter perf the cost", total, radius,
			seconds)), true);
		return total;
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

	private static int showConfig(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
			"Drifter config (config/drifter.json): sight %d chunks (%d blocks in daylight), path range %d blocks (%d search nodes), FOV %d (view cone %.0f x %.0f degrees), chat lines %s, shared reputation %s",
			com.dinglebob.drifter.DrifterConfig.sightChunks(), (int)com.dinglebob.drifter.DrifterConfig.sightRange(),
			com.dinglebob.drifter.DrifterConfig.pathRange(), com.dinglebob.drifter.DrifterConfig.pathNodes(),
			com.dinglebob.drifter.DrifterConfig.fov(), com.dinglebob.drifter.DrifterConfig.coneHalfYaw() * 2.0F,
			com.dinglebob.drifter.DrifterConfig.coneHalfPitch() * 2.0F, com.dinglebob.drifter.DrifterConfig.chatLines() ? "on" : "off",
			com.dinglebob.drifter.DrifterConfig.shareReputation() ? "on" : "off") + ", raiding players' chests "
			+ (com.dinglebob.drifter.DrifterConfig.raiding() ? "on" : "off") + ", outpost Bandits griefing " + (com.dinglebob.drifter.DrifterConfig.banditGriefing() ? "on" : "off") + ", group wars " + (com.dinglebob.drifter.DrifterConfig.drifterWars() ? "on" : "off")
			+ ", Drifters kept running away from players "
			+ com.dinglebob.drifter.DrifterConfig.simulatedDrifters() + ", catch-up for time away "
			+ com.dinglebob.drifter.DrifterConfig.offlineProgression().name().toLowerCase(Locale.ROOT) + ", aggression to players "
			+ com.dinglebob.drifter.DrifterConfig.playerAggression() + "%, to each other " + com.dinglebob.drifter.DrifterConfig.drifterAggression() + "%"), true);
		return 1;
	}

	private static int giveAll(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		var input = ItemArgument.getItem(ctx, "item");
		int drifters = 0;
		// Every loaded Drifter in every dimension (found in testing: from the Overworld it skipped the ones in the Nether).
		for (DrifterEntity drifter : allDrifters(ctx.getSource().getServer())) {
			giveTo(drifter, input, count);
			drifters++;
		}

		int total = drifters;
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Gave %d %s to each of %d Drifters", count,
			input.getItem().getDescription().getString(), total)), true);
		return total;
	}

	/** Every loaded Drifter in every dimension. */
	private static List<DrifterEntity> allDrifters(net.minecraft.server.MinecraftServer server) {
		List<DrifterEntity> drifters = new java.util.ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			drifters.addAll(level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive));
		}

		drifters.sort(Comparator.comparing(drifter -> drifter.getName().getString().toLowerCase(Locale.ROOT)));
		return drifters;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		List<DrifterEntity> drifters = allDrifters(ctx.getSource().getServer());
		// The ones out of the loaded world too, where they were last (user's request: far-off ones went missing).
		java.util.Set<java.util.UUID> loaded = new java.util.HashSet<>();
		drifters.forEach(drifter -> loaded.add(drifter.getUUID()));
		List<com.dinglebob.drifter.world.DrifterRegistry.Known> away = com.dinglebob.drifter.world.DrifterRegistry.all().stream()
			.filter(k -> !loaded.contains(k.id())).toList();
		if (drifters.isEmpty() && away.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No Drifters in this world"), false);
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.literal(drifters.size() + " loaded, " + away.size() + " out of the loaded world (click a name to go there)"), false);
		long now = ctx.getSource().getServer().overworld().getGameTime();
		for (com.dinglebob.drifter.world.DrifterRegistry.Known k : away) {
			boolean traveling = com.dinglebob.drifter.world.DrifterTravel.isTraveling(k.id());
			Component line = Component.literal(String.format(Locale.ROOT, "%s %s %d %d %d (%s), %s", k.name(), traveling ? "set off from" : "last seen at",
				k.pos().getX(), k.pos().getY(), k.pos().getZ(), k.level().location().getPath(), ago(now - k.seen())))
				.withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GRAY)
					.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/drifter tp " + k.name()))
					.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
						Component.literal("Click to go to where " + k.name() + " was last"))));
			ctx.getSource().sendSuccess(() -> line, false);
		}

		for (DrifterEntity drifter : drifters) {
			String name = drifter.getName().getString();
			int stage = com.dinglebob.drifter.brain.Strategist.stage(drifter);
			com.dinglebob.drifter.brain.Brain brain = drifter.getDrifterBrain();
			String doing = brain.activity() == null ? "idle" : brain.activity().describe();
			Component line = Component.literal(String.format(Locale.ROOT, "%s at %d %d %d (%s), stage %d, %s", name, drifter.getBlockX(),
				drifter.getBlockY(), drifter.getBlockZ(), drifter.level().dimension().location().getPath(), stage, doing))
				.withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA)
					.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/drifter tp " + name))
					.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
						Component.literal("Click to go to " + name))));
			ctx.getSource().sendSuccess(() -> line, false);
		}

		return drifters.size();
	}

	/** "3 min ago", "2 days ago" (game time). */
	private static String ago(long ticks) {
		if (ticks < 20 * 60) {
			return "just now";
		}

		if (ticks < 24000) {
			return (ticks / 1200) + " min ago";
		}

		return String.format(Locale.ROOT, "%.1f days ago", ticks / 24000.0);
	}

	private static int teleportTo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		DrifterEntity drifter = allDrifters(ctx.getSource().getServer()).stream()
			.filter(d -> d.getName().getString().equalsIgnoreCase(name)).findFirst().orElse(null);
		net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (drifter == null) {
			// Out of the loaded world: to where it was last; it loads in (and catches up) as the player arrives.
			var known = com.dinglebob.drifter.world.DrifterRegistry.all().stream().filter(k -> k.name().equalsIgnoreCase(name)).findFirst()
				.orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("No Drifter called " + name)).create());
			ServerLevel level = ctx.getSource().getServer().getLevel(known.level());
			if (level == null) {
				throw new SimpleCommandExceptionType(Component.literal(name + " is in a dimension that isn't there")).create();
			}

			player.teleportTo(level, known.pos().getX() + 0.5, known.pos().getY(), known.pos().getZ() + 0.5, player.getYRot(), player.getXRot());
			ctx.getSource().sendSuccess(() -> Component.literal("Teleported to where " + known.name() + " was last (it loads in as you arrive)"), false);
			return 1;
		}

		player.teleportTo((ServerLevel)drifter.level(), drifter.getX(), drifter.getY(), drifter.getZ(), player.getYRot(), player.getXRot());
		ctx.getSource().sendSuccess(() -> Component.literal("Teleported to " + drifter.getName().getString()), false);
		return 1;
	}

	/** Adds up to {@code count}; returns how many fit. */
	private static int giveTo(DrifterEntity drifter, net.minecraft.commands.arguments.item.ItemInput input, int count) throws CommandSyntaxException {
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

		return given;
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
