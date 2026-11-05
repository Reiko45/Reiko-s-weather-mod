package com.reiko.weather.weather;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Builds the chat lines shown by the barometer and the /rweather command. */
public final class WeatherReport {
    public static Component current(ServerLevel level, WeatherState state, BlockPos pos) {
        Climate climate = Climate.at(level, pos);
        return Component.translatable("report.reikos_weather.current",
                state.type.displayName().withStyle(ChatFormatting.AQUA),
                Component.translatable("climate.reikos_weather." + climate.name().toLowerCase(java.util.Locale.ROOT)),
                duration(state.remaining).withStyle(ChatFormatting.GRAY));
    }

    public static Component wind(ServerLevel level, WeatherState state, BlockPos pos) {
        WeatherProfile profile = WeatherProfiles.get(state.type, Climate.at(level, pos));
        float strength = profile.wind * state.intensity();
        return Component.translatable("report.reikos_weather.wind",
                Component.literal(String.valueOf(Wind.kmh(strength))).withStyle(ChatFormatting.WHITE),
                Component.translatable("direction.reikos_weather." + Wind.compassFrom(state.windAngle)));
    }

    public static Component forecast(WeatherState state) {
        MutableComponent line = Component.translatable("report.reikos_weather.forecast");
        boolean first = true;
        for (WeatherState.Forecast f : state.forecast) {
            line.append(first ? Component.literal(" ") : Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(f.type().displayName().withStyle(ChatFormatting.YELLOW));
            first = false;
        }
        return line;
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
