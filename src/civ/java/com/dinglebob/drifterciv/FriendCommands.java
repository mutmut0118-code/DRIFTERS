package com.dinglebob.drifterciv;

import java.util.Arrays;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Commands for friendships (D2): {@code /drifterciv config friendship ...} (the Friendship settings) and {@code /drifterciv
 * friends} (yours, and debug ones). Registered on the same {@code drifterciv} root as the rest (Brigadier merges them).
 */
final class FriendCommands {
	private FriendCommands() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
			dispatcher.register(Commands.literal("drifterciv").then(Commands.literal("config").then(config())));
			dispatcher.register(Commands.literal("drifterciv").then(Friends.command()));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> config() {
		return Commands.literal("friendship")
			.executes(FriendCommands::show)
			.then(Commands.literal("preset").requires(source -> source.hasPermission(2))
				.then(Commands.argument("preset", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(CivConfig.Preset.values()).map(p -> p.id), builder))
					.executes(ctx -> {
						CivConfig.Preset preset = CivConfig.Preset.byId(StringArgumentType.getString(ctx, "preset"));
						if (preset == null) {
							ctx.getSource().sendFailure(Component.literal("No such preset (relaxed, normal, slow_burn, hardcore, custom)"));
							return 0;
						}

						CivConfig.applyPreset(preset);
						ctx.getSource().sendSuccess(() -> Component.literal("Friendship preset " + preset.id), true);
						return 1;
					})))
			.then(Commands.argument("setting", StringArgumentType.word()).requires(source -> source.hasPermission(2))
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CivConfig.FRIEND_SETTINGS.stream().map(CivConfig.Setting::id), builder))
				.then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> {
					CivConfig.Setting setting = CivConfig.setting(StringArgumentType.getString(ctx, "setting"));
					if (setting == null) {
						ctx.getSource().sendFailure(Component.literal("No such friendship setting"));
						return 0;
					}

					setting.change(DoubleArgumentType.getDouble(ctx, "value"));
					ctx.getSource().sendSuccess(() -> Component.literal(setting.label() + ": " + setting.show() + " (preset custom)"), true);
					return 1;
				})));
	}

	private static int show(CommandContext<CommandSourceStack> ctx) {
		StringBuilder out = new StringBuilder("Friendship (preset " + CivConfig.friends().preset.id + "):");
		for (CivConfig.Setting setting : CivConfig.FRIEND_SETTINGS) {
			out.append("\n  ").append(setting.id()).append(" = ").append(setting.show());
		}

		String shown = out.toString();
		ctx.getSource().sendSuccess(() -> Component.literal(shown), false);
		return 1;
	}
}
