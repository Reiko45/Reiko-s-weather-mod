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
        LocalWeather local = LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, climate, pos.getX() + 0.5, pos.getZ() + 0.5);
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
        LocalWeather local = LocalWeather.resolve(state.type, CustomWeathers.of(state), state.intensity(), state.cells, Climate.at(level, pos), pos.getX() + 0.5, pos.getZ() + 0.5);
        float strength = local.profile().wind * local.intensity();
        return Component.translatable("report.reikos_weather.wind",
                Component.literal(String.valueOf(Wind.kmh(strength))).withStyle(ChatFormatting.WHITE),
                Component.translatable("direction.reikos_weather." + Wind.compassFrom(state.windAngle)));
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
