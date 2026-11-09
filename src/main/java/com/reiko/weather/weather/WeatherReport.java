package com.reiko.weather.weather;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Locale;

/** Builds the chat lines shown by the barometer and the /rweather command. */
public final class WeatherReport {
    public static Component current(ServerLevel level, WeatherState state, BlockPos pos) {
        Climate climate = Climate.at(level, pos);
        LocalWeather local = Microclimate.apply(level, climate, pos.getX(), pos.getZ(), state.windAngle,
                LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, climate, pos.getX() + 0.5, pos.getZ() + 0.5));
        Component climateName = Component.translatable("climate.reikos_weather." + climate.name().toLowerCase(Locale.ROOT));
        if (local.cell() != null && local.cellWeight() >= 0.5F) {
            return Component.translatable("report.reikos_weather.current_cell",
                    local.type().displayName().withStyle(ChatFormatting.AQUA), climateName);
        }
        return Component.translatable("report.reikos_weather.current",
                backgroundName(state).withStyle(ChatFormatting.AQUA), climateName,
                duration(state.remaining).withStyle(ChatFormatting.GRAY));
    }

    /** The background weather's name, using a datapack weather's own name when one is active. */
    public static MutableComponent backgroundName(WeatherState state) {
        CustomWeather custom = CustomWeathers.of(state);
        return custom != null ? custom.displayName() : state.type.displayName();
    }

    public static Component wind(ServerLevel level, WeatherState state, BlockPos pos) {
        LocalWeather local = WeatherManager.local(level, pos);
        if (local == null) local = LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, Climate.at(level, pos), pos.getX() + 0.5, pos.getZ() + 0.5);
        float[] breeze = Breezes.adjust(level, pos.getX(), pos.getZ(), state.windAngle, local.profile().wind * local.intensity());
        return Component.translatable("report.reikos_weather.wind",
                Component.literal(String.valueOf(Wind.kmh(breeze[1]))).withStyle(ChatFormatting.WHITE),
                Component.translatable("direction.reikos_weather." + Wind.compassFrom(breeze[0])));
    }

    public static Component forecast(WeatherState state) {
        MutableComponent line = Component.translatable("report.reikos_weather.forecast");
        boolean first = true;
        for (WeatherState.Forecast f : state.forecast) {
            line.append(first ? Component.literal(" ") : Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
            CustomWeather custom = CustomWeathers.get(f.custom());
            line.append((custom != null ? custom.displayName() : f.type().displayName()).withStyle(ChatFormatting.YELLOW));
            first = false;
        }
        return line;
    }

    @Nullable
    public static Component season() {
        Seasons.Info info = WeatherManager.seasonInfo();
        if (info.season() == null) {
            return null;
        }
        if (info.dayOfSeason() < 0) {
            return Component.translatable("report.reikos_weather.season", info.season().displayName().withStyle(ChatFormatting.GREEN));
        }
        return Component.translatable("report.reikos_weather.season_day", info.season().displayName().withStyle(ChatFormatting.GREEN),
                info.dayOfSeason() + 1, info.daysPerSeason());
    }

    /** "A thunderstorm 640 blocks to the west is heading your way", or null if none is near. */
    @Nullable
    public static Component approachingStorm(WeatherState state, BlockPos pos) {
        StormCell cell = LocalWeather.nearestAround(state.cells, pos.getX(), pos.getZ(), 1500);
        if (cell == null) {
            return null;
        }
        int distance = (int) (cell.distanceTo(pos.getX(), pos.getZ()) - cell.radius);
        String direction = compass(cell.x - pos.getX(), cell.z - pos.getZ());
        return Component.translatable(cell.approaching(pos.getX(), pos.getZ()) ? "report.reikos_weather.storm_approaching" : "report.reikos_weather.storm_passing",
                cell.type.displayName().withStyle(ChatFormatting.GOLD), distance,
                Component.translatable("direction.reikos_weather." + direction));
    }

    /** "Local climate: a sheltered valley", or null when the terrain isn't doing much here. */
    @Nullable
    public static Component microclimate(ServerLevel level, WeatherState state, BlockPos pos) {
        Microclimate.Info info = Microclimate.at(level, pos.getX(), pos.getZ(), state.windAngle);
        java.util.List<Component> parts = new java.util.ArrayList<>();
        if (info.valley() > 0.3F) parts.add(Component.translatable("report.reikos_weather.microclimate.valley"));
        if (info.peak() > 0.3F) parts.add(Component.translatable("report.reikos_weather.microclimate.peak"));
        if (info.rainShadow() > 0.3F) parts.add(Component.translatable("report.reikos_weather.microclimate.rain_shadow"));
        if (info.lakeEffect() > 0.3F && Climate.at(level, pos) == Climate.COLD) parts.add(Component.translatable("report.reikos_weather.microclimate.lake_effect"));
        if (parts.isEmpty()) {
            return null;
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) list.append(", ");
            list.append(parts.get(i));
        }
        return Component.translatable("report.reikos_weather.microclimate", list.withStyle(ChatFormatting.AQUA));
    }

    /**
     * The things going on around here that aren't the weather type itself: a drought, a hurricane's category (and
     * whether you're in its eye), a föhn wind, cold air pooling in a frost hollow.
     */
    public static java.util.List<Component> conditions(ServerLevel level, WeatherState state, BlockPos pos) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        Component drought = Drought.describe(state);
        if (drought != null) lines.add(drought.copy().withStyle(ChatFormatting.GOLD));
        for (StormCell cell : state.cells) {
            if (cell.type != WeatherType.HURRICANE) continue;
            double edge = cell.distanceTo(pos.getX(), pos.getZ()) - cell.radius;
            if (edge > 1200) continue;
            String key = cell.inEye(pos.getX(), pos.getZ()) ? "report.reikos_weather.hurricane_eye"
                    : edge < 0 ? "report.reikos_weather.hurricane_in" : "report.reikos_weather.hurricane_near";
            lines.add(Component.translatable(key, cell.category()).withStyle(ChatFormatting.RED));
        }
        Climate climate = Climate.at(level, pos);
        LocalWeather raw = LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, climate, pos.getX() + 0.5, pos.getZ() + 0.5);
        Microclimate.Info info = Microclimate.at(level, pos.getX(), pos.getZ(), state.windAngle);
        if (Microclimate.foehn(info, raw.profile()) > 0.3F) {
            lines.add(Component.translatable("report.reikos_weather.foehn").withStyle(ChatFormatting.GOLD));
        }
        if (Microclimate.frostHollow(level, info, raw.profile()) > 0.3F) {
            lines.add(Component.translatable("report.reikos_weather.frost_hollow").withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    /** The tide, for players on or near the coast (null inland). */
    @javax.annotation.Nullable
    public static Component tide(ServerLevel level, BlockPos pos) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return null;
        }
        for (BlockPos p : new BlockPos[]{pos, pos.offset(24, 0, 0), pos.offset(-24, 0, 0), pos.offset(0, 0, 24), pos.offset(0, 0, -24)}) {
            var biome = level.getBiome(p);
            if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) {
                return Tides.describe(level).copy().withStyle(ChatFormatting.AQUA);
            }
        }
        return null;
    }

    /** "Volcano 420 blocks to the northeast: erupting", for the nearest known volcano within 1024 blocks. */
    @Nullable
    public static Component volcano(ServerLevel level, BlockPos pos) {
        com.reiko.weather.volcano.Volcano v = com.reiko.weather.volcano.VolcanoManager.nearest(level, pos.getX(), pos.getZ(), 1024);
        if (v == null) {
            return null;
        }
        ChatFormatting color = switch (v.phase) {
            case DORMANT -> ChatFormatting.GRAY;
            case RUMBLING -> ChatFormatting.GOLD;
            case ERUPTING -> ChatFormatting.RED;
            case COOLING -> ChatFormatting.YELLOW;
        };
        return Component.translatable("report.reikos_weather.volcano", (int) v.shape.horizontalDistance(pos.getX(), pos.getZ()),
                Component.translatable("direction.reikos_weather." + compass(v.shape.cx() - pos.getX(), v.shape.cz() - pos.getZ())),
                Component.translatable("volcano.reikos_weather.phase." + v.phase.id()).withStyle(color));
    }

    /** 8-point compass name for a horizontal offset (north is -Z). */
    public static String compass(double dx, double dz) {
        double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return names[(int) Math.round(bearing / 45.0) % 8];
    }

    public static MutableComponent duration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        return minutes > 0
                ? Component.translatable("report.reikos_weather.minutes", minutes, seconds % 60)
                : Component.translatable("report.reikos_weather.seconds", seconds);
    }

    private WeatherReport() {}
}
