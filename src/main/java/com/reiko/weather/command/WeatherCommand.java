package com.reiko.weather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.Seasons;
import com.reiko.weather.weather.StormCell;
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
 * /rweather query | forecast | set &lt;type&gt; [seconds] | next | tornado [pos] [kind]
 *          | storm &lt;type&gt; [here] | storms [clear] | season [season]
 */
public final class WeatherCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TYPE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.reikos_weather.unknown", id));
    private static final SimpleCommandExceptionType DISABLED =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.disabled"));
    private static final SimpleCommandExceptionType SERENE =
            new SimpleCommandExceptionType(Component.translatable("commands.reikos_weather.season.serene"));
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
                        .executes(ctx -> tornado(ctx.getSource(), ctx.getSource().getPosition(), "tornado"))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> tornado(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"), "tornado"))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(TornadoEntity.Kind.values()).map(TornadoEntity.Kind::id), builder))
                                        .executes(ctx -> tornado(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"),
                                                StringArgumentType.getString(ctx, "kind"))))))
                .then(Commands.literal("storm").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(WeatherType.values()).filter(WeatherType::isStorm).map(WeatherType::id), builder))
                                .executes(ctx -> storm(ctx, false))
                                .then(Commands.literal("here").executes(ctx -> storm(ctx, true)))))
                .then(Commands.literal("storms").executes(WeatherCommand::listStorms)
                        .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(WeatherCommand::clearStorms)))
                .then(Commands.literal("season").executes(WeatherCommand::querySeason)
                        .then(Commands.argument("season", StringArgumentType.word()).requires(s -> s.hasPermission(2))
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Season.values()).map(Season::id), builder))
                                .executes(WeatherCommand::setSeason))));
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
        Component season = WeatherReport.season();
        if (season != null) source.sendSuccess(() -> season, false);
        Component storm = WeatherReport.approachingStorm(state, pos);
        if (storm != null) source.sendSuccess(() -> storm, false);
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

    private static int tornado(CommandSourceStack source, Vec3 pos, String kindId) throws CommandSyntaxException {
        if (source.getLevel().dimension() != Level.OVERWORLD) {
            throw WRONG_DIMENSION.create();
        }
        TornadoEntity.Kind kind = TornadoEntity.Kind.byId(kindId);
        if (kind == null) {
            throw UNKNOWN_TYPE.create(kindId);
        }
        WeatherHazards.spawnTornado(source.getLevel(), pos, source.getLevel().random.nextFloat() * (float) (Math.PI * 2), kind);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.tornado." + kind.id()), true);
        return 1;
    }

    private static int storm(CommandContext<CommandSourceStack> ctx, boolean here) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "type");
        WeatherType type = WeatherType.byId(id);
        if (type == null || !type.isStorm()) {
            throw UNKNOWN_TYPE.create(id);
        }
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        ServerLevel overworld = source.getServer().overworld();
        Vec3 pos = source.getPosition();
        StormCell cell = here ? WeatherManager.spawnCell(overworld, state, type, pos.x, pos.z, -1)
                : WeatherManager.spawnCellUpwind(overworld, state, type, pos, -1);
        if (here) {
            cell.age = 900; // already fully formed
        }
        source.sendSuccess(() -> Component.translatable(here ? "commands.reikos_weather.storm_here" : "commands.reikos_weather.storm",
                type.displayName(), (int) cell.radius), true);
        return cell.id;
    }

    private static int listStorms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        if (state.cells.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.reikos_weather.storms.none"), false);
            return 0;
        }
        Vec3 pos = source.getPosition();
        for (StormCell cell : state.cells) {
            int distance = (int) cell.distanceTo(pos.x, pos.z);
            String direction = WeatherReport.compass(cell.x - pos.x, cell.z - pos.z);
            source.sendSuccess(() -> Component.translatable("commands.reikos_weather.storms.entry", cell.type.displayName(),
                    distance, Component.translatable("direction.reikos_weather." + direction), (int) cell.radius,
                    Math.round(cell.strength() * 100)), false);
        }
        return state.cells.size();
    }

    private static int clearStorms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        int count = state.cells.size();
        state.cells.clear();
        state.setDirty();
        WeatherManager.sync(source.getServer().overworld(), state);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.storms.cleared", count), true);
        return count;
    }

    private static int querySeason(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireState(ctx.getSource());
        Component season = WeatherReport.season();
        ctx.getSource().sendSuccess(() -> season != null ? season : Component.translatable("commands.reikos_weather.season.off"), false);
        return WeatherManager.seasonInfo().season() == null ? 0 : WeatherManager.seasonInfo().season().ordinal() + 1;
    }

    private static int setSeason(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "season");
        Season season = Season.byId(id);
        if (season == null) {
            throw UNKNOWN_TYPE.create(id);
        }
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        if (Seasons.usingSereneSeasons()) {
            throw SERENE.create();
        }
        ServerLevel overworld = source.getServer().overworld();
        Seasons.setSeason(overworld, state, season);
        Seasons.compute(overworld, state);
        WeatherManager.sync(overworld, state);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.season.set", season.displayName()), true);
        return season.ordinal() + 1;
    }

    private WeatherCommand() {}
}
