package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Afternoon thunderstorms: on summer days (any day in the tropics) the sun heats the ground, the clouds
 * tower up through the afternoon and some of them break into storms, which die away again by dusk.
 */
public final class Convection {
    static void tick(ServerLevel level, WeatherState state) {
        if (!WeatherConfig.AFTERNOON_STORMS.get() || !WeatherConfig.REGIONAL_STORMS.get()) {
            return;
        }
        long dayTime = level.getDayTime(), day = dayTime / 24000L, time = dayTime % 24000L;
        if (time < 5000L || time > 8000L || state.convectionDay == day) {
            return;
        }
        state.convectionDay = day;
        state.setDirty();
        if (!state.type.isCalm() && state.type != WeatherType.HEATWAVE) {
            return; // it has to be a fine morning for the ground to heat up
        }
        Season season = WeatherManager.seasonInfo().season();
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || state.cells.size() >= WeatherConfig.MAX_STORM_CELLS.get() + 2) continue;
            Climate climate = Climate.at(level, player.blockPosition());
            boolean tropical = Monsoon.isTropical(level.getBiome(player.blockPosition()));
            if (climate == Climate.COLD || climate == Climate.ARID && !tropical) continue;
            if (season != null && season != Season.SUMMER && !tropical) continue;
            if (random.nextFloat() > (tropical ? 0.75F : 0.5F)) continue;
            if (state.cells.stream().anyMatch(c -> c.distanceTo(player.getX(), player.getZ()) < c.radius + 200)) continue;
            double angle = random.nextDouble() * Math.PI * 2.0, distance = 120.0 + random.nextDouble() * 260.0;
            float roll = random.nextFloat();
            WeatherType type = roll < 0.08F ? WeatherType.SUPERCELL : roll < 0.22F ? WeatherType.HAIL : WeatherType.THUNDERSTORM;
            float radius = 110.0F + random.nextFloat() * 60.0F;
            // it lives until about dusk, and takes a couple of minutes to tower up
            int maxAge = (int) Math.max(3000L, 12500L - time + random.nextInt(1500));
            double speed = 0.04 + random.nextDouble() * 0.04;
            Vec3 wind = Wind.direction(state.windAngle);
            WeatherManager.spawnCell(level, state, type, player.getX() + Math.cos(angle) * distance, player.getZ() + Math.sin(angle) * distance,
                    radius, wind.x * speed, wind.z * speed, maxAge, 2400, true);
        }
    }

    private Convection() {}
}
