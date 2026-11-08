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
import com.reiko.weather.weather.Wind;
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

import com.reiko.weather.otherworld.OtherworldType;
import com.reiko.weather.otherworld.OtherworldWeather;
import com.reiko.weather.weather.CustomWeather;
import com.reiko.weather.weather.CustomWeathers;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /rweather query | forecast | set &lt;type&gt; [seconds] | next | tornado [pos] [kind]
 *          | storm &lt;type&gt; [here] | storms [clear] | season [season]
 *          | volcano [create [pos] | wake | erupt | calm]
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
                        .then(Commands.argument("type", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(setSuggestions(ctx.getSource()), builder))
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
                                        Arrays.stream(WeatherType.values()).filter(t -> t.isStorm() || !t.natural()).map(WeatherType::id), builder))
                                .executes(ctx -> storm(ctx, false))
                                .then(Commands.literal("here").executes(ctx -> storm(ctx, true)))))
                .then(Commands.literal("meteor").requires(s -> s.hasPermission(2))
                        .executes(ctx -> meteor(ctx.getSource(), ctx.getSource().getPosition()))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> meteor(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos")))))
                .then(Commands.literal("wind").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("from", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(WIND_FROM, builder))
                                .executes(WeatherCommand::setWind)))
                .then(Commands.literal("storms").executes(WeatherCommand::listStorms)
                        .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(WeatherCommand::clearStorms)))
                .then(Commands.literal("volcano").executes(ctx -> VolcanoCommands.report(ctx.getSource()))
                        .then(Commands.literal("create").requires(s -> s.hasPermission(2))
                                .executes(ctx -> VolcanoCommands.create(ctx.getSource(), ctx.getSource().getPosition()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> VolcanoCommands.create(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos")))))
                        .then(Commands.literal("wake").requires(s -> s.hasPermission(2))
                                .executes(ctx -> VolcanoCommands.phase(ctx.getSource(), com.reiko.weather.volcano.Volcano.Phase.RUMBLING)))
                        .then(Commands.literal("erupt").requires(s -> s.hasPermission(2))
                                .executes(ctx -> VolcanoCommands.phase(ctx.getSource(), com.reiko.weather.volcano.Volcano.Phase.ERUPTING)))
                        .then(Commands.literal("calm").requires(s -> s.hasPermission(2))
                                .executes(ctx -> VolcanoCommands.phase(ctx.getSource(), com.reiko.weather.volcano.Volcano.Phase.COOLING))))
                .then(Commands.literal("season").executes(WeatherCommand::querySeason)
                        .then(Commands.argument("season", StringArgumentType.word()).requires(s -> s.hasPermission(2))
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Season.values()).map(Season::id), builder))
                                .executes(WeatherCommand::setSeason)))
                .then(Commands.literal("drought").executes(WeatherCommand::queryDrought)
                        .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(ctx -> startDrought(ctx, 6))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 60)).executes(ctx -> startDrought(ctx, IntegerArgumentType.getInteger(ctx, "days")))))
                        .then(Commands.literal("end").requires(s -> s.hasPermission(2)).executes(WeatherCommand::endDrought))));
    }

    private static int queryDrought(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WeatherState state = requireState(ctx.getSource());
        Component line = com.reiko.weather.weather.Drought.describe(state);
        ctx.getSource().sendSuccess(() -> line != null ? line : Component.translatable("commands.reikos_weather.drought.none", state.dryDays), false);
        return state.droughtDaysLeft;
    }

    private static int startDrought(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
        WeatherState state = requireState(ctx.getSource());
        com.reiko.weather.weather.Drought.start(ctx.getSource().getServer().overworld(), state, days);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikos_weather.drought.start", days), true);
        return days;
    }

    private static int endDrought(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WeatherState state = requireState(ctx.getSource());
        com.reiko.weather.weather.Drought.end(ctx.getSource().getServer().overworld(), state);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.reikos_weather.drought.end"), true);
        return 1;
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
        OtherworldType other = OtherworldWeather.active(source.getLevel());
        if (other != null) {
            OtherworldWeather.State s = OtherworldWeather.state(source.getLevel());
            source.sendSuccess(() -> Component.translatable("report.reikos_weather.otherworld",
                    other.displayName().withStyle(net.minecraft.ChatFormatting.AQUA), WeatherReport.duration(s.remaining)), false);
            return other.ordinal();
        }
        WeatherState state = requireState(source);
        ServerLevel overworld = source.getServer().overworld();
        BlockPos pos = source.getLevel() == overworld ? BlockPos.containing(source.getPosition()) : overworld.getSharedSpawnPos();
        source.sendSuccess(() -> WeatherReport.current(overworld, state, pos), false);
        source.sendSuccess(() -> WeatherReport.wind(overworld, state, pos), false);
        Component season = WeatherReport.season();
        if (season != null) source.sendSuccess(() -> season, false);
        Component storm = WeatherReport.approachingStorm(state, pos);
        if (storm != null) source.sendSuccess(() -> storm, false);
        Component terrain = WeatherReport.microclimate(overworld, state, pos);
        if (terrain != null) source.sendSuccess(() -> terrain, false);
        Component tide = WeatherReport.tide(overworld, pos);
        if (tide != null) source.sendSuccess(() -> tide, false);
        for (Component line : WeatherReport.conditions(overworld, state, pos)) source.sendSuccess(() -> line, false);
        Component volcano = WeatherReport.volcano(overworld, pos);
        if (volcano != null) source.sendSuccess(() -> volcano, false);
        return state.type.ordinal();
    }

    private static int forecast(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WeatherState state = requireState(ctx.getSource());
        ctx.getSource().sendSuccess(() -> WeatherReport.forecast(state), false);
        return state.forecast.size();
    }

    /** Built-in ids, datapack ids, or Nether/End weathers when run from there. */
    private static List<String> setSuggestions(CommandSourceStack source) {
        List<String> ids = new ArrayList<>();
        if (OtherworldWeather.handles(source.getLevel())) {
            for (OtherworldType t : OtherworldType.values()) {
                if (t.validIn(source.getLevel())) ids.add(t.id());
            }
            return ids;
        }
        for (WeatherType t : WeatherType.values()) ids.add(t.id());
        for (CustomWeather c : CustomWeathers.all()) ids.add(c.id().toString());
        return ids;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        ResourceLocation rl = ResourceLocationArgument.getId(ctx, "type");
        String id = rl.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? rl.getPath() : rl.toString();
        CommandSourceStack source = ctx.getSource();
        if (OtherworldWeather.handles(source.getLevel())) {
            return setOtherworld(source, id, seconds);
        }
        CustomWeather custom = CustomWeathers.get(rl.toString());
        WeatherType type = custom != null ? custom.base() : WeatherType.byId(id);
        if (type == null) {
            throw UNKNOWN_TYPE.create(id);
        }
        WeatherState state = requireState(source);
        ServerLevel overworld = source.getServer().overworld();
        int duration = seconds > 0 ? seconds * 20 : custom != null ? custom.randomDuration(overworld.random) : type.randomDuration(overworld.random);
        WeatherManager.setWeather(overworld, state, type, duration, custom != null ? custom.id().toString() : null);
        Component name = custom != null ? custom.displayName() : type.displayName();
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.set", name, WeatherReport.duration(duration)), true);
        return 1;
    }

    private static int setOtherworld(CommandSourceStack source, String id, int seconds) throws CommandSyntaxException {
        OtherworldType type = OtherworldType.byId(id);
        ServerLevel level = source.getLevel();
        if (type == null || !type.validIn(level)) {
            throw UNKNOWN_TYPE.create(id);
        }
        if (OtherworldWeather.active(level) == null) {
            throw DISABLED.create();
        }
        int duration = seconds > 0 ? seconds * 20 : -1;
        OtherworldWeather.State state = OtherworldWeather.state(level);
        OtherworldWeather.set(level, state, type, duration);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.set", type.displayName(),
                WeatherReport.duration(state.remaining)), true);
        return 1;
    }

    private static int next(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        WeatherManager.skip(source.getServer().overworld());
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.next", WeatherReport.backgroundName(state)), true);
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
        if (type == null || !type.isStorm() && type.natural()) {
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
        if (type == WeatherType.HURRICANE) {
            cell.power = 1.0F; // a major hurricane (it still weakens over land)
        }
        source.sendSuccess(() -> Component.translatable(here ? "commands.reikos_weather.storm_here" : "commands.reikos_weather.storm",
                type.displayName(), (int) cell.radius), true);
        return cell.id;
    }

    private static int meteor(CommandSourceStack source, Vec3 pos) throws CommandSyntaxException {
        if (source.getLevel().dimension() != Level.OVERWORLD) {
            throw WRONG_DIMENSION.create();
        }
        com.reiko.weather.sky.Meteors.spawn(source.getLevel(), pos);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.meteor"), true);
        return 1;
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

    /** Compass points the wind can blow from, in the order of their angle (the wind blowing east is angle 0). */
    private static final java.util.List<String> WIND_FROM = java.util.List.of("w", "nw", "n", "ne", "e", "se", "s", "sw");

    private static int setWind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String from = StringArgumentType.getString(ctx, "from").toLowerCase(java.util.Locale.ROOT);
        int index = WIND_FROM.indexOf(from);
        if (index < 0) {
            throw UNKNOWN_TYPE.create(from);
        }
        CommandSourceStack source = ctx.getSource();
        WeatherState state = requireState(source);
        state.windAngle = index * net.minecraft.util.Mth.TWO_PI / 8.0F;
        state.setDirty();
        WeatherManager.sync(source.getServer().overworld(), state);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.wind.set",
                Component.translatable("direction.reikos_weather." + Wind.compassFrom(state.windAngle))), true);
        return index;
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
        WeatherManager.refreshSeason(overworld, state);
        source.sendSuccess(() -> Component.translatable("commands.reikos_weather.season.set", season.displayName()), true);
        return season.ordinal() + 1;
    }

    private WeatherCommand() {}
}
