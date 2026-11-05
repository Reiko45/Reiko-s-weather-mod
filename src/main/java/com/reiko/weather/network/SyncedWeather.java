package com.reiko.weather.network;

import com.reiko.weather.weather.WeatherType;

/**
 * The client's copy of the server weather. Lives in common code (no client classes) so the payload
 * handler is safe to register on dedicated servers; only the client ever reads it.
 */
public final class SyncedWeather {
    private static boolean managed;
    private static WeatherType type = WeatherType.CLEAR;
    private static int elapsed;
    private static float windAngle;
    private static int rainbowTicks;

    public static void accept(WeatherSyncPayload payload) {
        managed = payload.weather() >= 0;
        type = WeatherType.byOrdinal(payload.weather());
        elapsed = payload.elapsed();
        windAngle = payload.windAngle();
        rainbowTicks = payload.rainbow();
    }

    /** Advances the local copy between syncs. */
    public static void tick() {
        elapsed++;
        if (rainbowTicks > 0) {
            rainbowTicks--;
        }
    }

    public static void reset() {
        managed = false;
        type = WeatherType.CLEAR;
        elapsed = 0;
        windAngle = 0.0F;
        rainbowTicks = 0;
    }

    public static boolean managed() { return managed; }
    public static WeatherType type() { return type; }
    public static int elapsed() { return elapsed; }
    public static float windAngle() { return windAngle; }
    public static int rainbowTicks() { return rainbowTicks; }

    private SyncedWeather() {}
}
