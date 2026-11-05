package com.reiko.weather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.weather.weather.WeatherHazards;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherReport;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /rweather query | forecast | set &lt;type&gt; [seconds] | next | tornado [pos]
 */
public final class WeatherCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TYPE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.reikos_weather.unknown", id));
    private static final SimpleCommandExceptionType DISABLED =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.disabled"));
    private static final SimpleCommandExceptionType WRONG_DIMENSION =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.overworld_only"));

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rweather")
                .then(Commands.literal("query").executes(WeatherCommand::query))
                .then(Commands.literal("forecast").executes(WeatherCommand::forecast))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(WeatherType.values()).map(WeatherType::id), builder))
                                .executes(ctx -> set(ctx, -1))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 1_000_000))
                                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("next").requires(s -> s.hasPermission(2)).executes(WeatherCommand::next))
                .then(Commands.literal("tornado").requires(s -> s.hasPermission(2))
                        .executes(ctx -> tornado(ctx.getSource(), ctx.getSource().getPosition()))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> tornado(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"))))));
    }

    private static WeatherState requireState(CommandSourceStack source) throws CommandSyntaxException {
        if (!WeatherManager.enabled()) {
            throw DISABLED.create();
        }
        WeatherState state = WeatherManager.activeState(source.getServer().overworld());
        if (state == null) {
            throw DISABLED.create();
        }
        return state;
    }

    private static int query(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        ServerLevel overworld = source.getServer().overworld();
        BlockPos pos = source.getLevel() == overworld ? BlockPos.containing(source.getPosition()) : overworld.getSharedSpawnPos();
        source.sendSuccess(() -> WeatherReport.current(overworld, state, pos), false);
        source.sendSuccess(() -> WeatherReport.wind(overworld, state, pos), false);
        return state.type.ordinal();
    }

    private static int forecast(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WeatherState state = requireState(ctx.getSource());
        ctx.getSource().sendSuccess(() -> WeatherReport.forecast(state), false);
        return state.forecast.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "type");
        WeatherType type = WeatherType.byId(id);
        if (type == null) {
            throw UNKNOWN_TYPE.create(id);
        }
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        ServerLevel overworld = source.getServer().overworld();
        int duration = seconds > 0 ? seconds * 20 : type.randomDuration(overworld.random);
        WeatherManager.setWeather(overworld, state, type, duration);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.set", type.displayName(), WeatherReport.duration(duration)), true);
        return 1;
    }

    private static int next(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        WeatherManager.skip(source.getServer().overworld());
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.next", state.type.displayName()), true);
        return 1;
    }

    private static int tornado(CommandSourceStack source, Vec3 pos) throws CommandSyntaxException {
        if (source.getLevel().dimension() != Level.OVERWORLD) {
            throw WRONG_DIMENSION.create();
        }
        WeatherHazards.spawnTornado(source.getLevel(), pos, source.getLevel().random.nextFloat() * (float) (Math.PI * 2));
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.tornado"), true);
        return 1;
    }

    private WeatherCommand() {}
}
