package com.reiko.weather.network;

import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.Seasons;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of the server weather. Lives in common code (no client classes) so the payload
 * handler is safe to register on dedicated servers; only the client ever reads it. Between syncs the
 * client advances it on its own (storm cells keep drifting, timers keep counting).
 */
public final class SyncedWeather {
    private static boolean managed;
    private static WeatherType type = WeatherType.CLEAR;
    private static int elapsed;
    private static int remaining;
    private static float windAngle;
    private static int rainbowTicks;
    @Nullable
    private static Season season;
    private static int dayOfSeason = -1;
    private static int daysPerSeason = -1;
    private static WeatherType[] forecast = new WeatherType[0];
    private static List<StormCell> cells = List.of();

    public static void accept(WeatherSyncPayload payload) {
        managed = payload.weather() >= 0;
        type = WeatherType.byOrdinal(payload.weather());
        elapsed = payload.elapsed();
        remaining = payload.remaining();
        windAngle = payload.windAngle();
        rainbowTicks = payload.rainbow();
        season = Season.byOrdinal(payload.season());
        dayOfSeason = payload.dayOfSeason();
        daysPerSeason = payload.daysPerSeason();
        forecast = new WeatherType[payload.forecast().length];
        for (int i = 0; i < forecast.length; i++) {
            forecast[i] = WeatherType.byOrdinal(payload.forecast()[i]);
        }
        cells = new ArrayList<>(payload.cells());
        Seasons.setTemperatureShift(managed ? payload.temperatureShift() : 0.0F);
    }

    /** Advances the local copy between syncs. */
    public static void tick() {
        elapsed++;
        if (remaining > 0) remaining--;
        if (rainbowTicks > 0) rainbowTicks--;
        for (StormCell cell : cells) {
            cell.tick();
        }
    }

    public static void reset() {
        managed = false;
        type = WeatherType.CLEAR;
        elapsed = remaining = rainbowTicks = 0;
        windAngle = 0.0F;
        season = null;
        dayOfSeason = daysPerSeason = -1;
        forecast = new WeatherType[0];
        cells = List.of();
        Seasons.setTemperatureShift(0.0F);
    }

    public static boolean managed() { return managed; }
    public static WeatherType type() { return type; }
    public static int elapsed() { return elapsed; }
    public static int remaining() { return remaining; }
    public static float windAngle() { return windAngle; }
    public static int rainbowTicks() { return rainbowTicks; }
    @Nullable public static Season season() { return season; }
    public static int dayOfSeason() { return dayOfSeason; }
    public static int daysPerSeason() { return daysPerSeason; }
    public static WeatherType[] forecast() { return forecast; }
    public static List<StormCell> cells() { return cells; }

    private SyncedWeather() {}
}
