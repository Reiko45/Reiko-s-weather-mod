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
        if (Drought.severity(state) > 0.5F) {
            return; // in a drought the air is too dry for the clouds to build into storms
        }
        Season season = WeatherManager.seasonInfo().season();
        RandomSource random = level.random;
        // on a tornado outbreak day (spring or summer) the storms build whatever the day, and many are supercells
        boolean outbreak = TornadoOutbreaks.outbreakDay(day);
        for (ServerPlayer player : level.players()) {
          for (int storm = 0; storm < (outbreak ? 3 : 1); storm++) {
            if (player.isSpectator() || state.cells.size() >= WeatherConfig.MAX_STORM_CELLS.get() + (outbreak ? 4 : 2)) continue;
            Climate climate = Climate.at(level, player.blockPosition());
            boolean tropical = Monsoon.isTropical(level.getBiome(player.blockPosition()));
            if (climate == Climate.COLD || climate == Climate.ARID && !tropical) continue;
            if (season != null && season != Season.SUMMER && !tropical && !outbreak) continue;
            if (!thunderyDay(day, tropical) && !outbreak) continue;
            double angle = random.nextDouble() * Math.PI * 2.0, distance = 120.0 + storm * 250.0 + random.nextDouble() * 260.0;
            double sx = player.getX() + Math.cos(angle) * distance, sz = player.getZ() + Math.sin(angle) * distance;
            if (storm == 0 && state.cells.stream().anyMatch(c -> c.distanceTo(player.getX(), player.getZ()) < c.radius + 200)) continue;
            if (storm > 0 && state.cells.stream().anyMatch(c -> c.distanceTo(sx, sz) < c.radius + 150)) continue;
            float roll = random.nextFloat();
            float supercells = outbreak ? 0.55F : 0.08F;
            WeatherType type = roll < supercells ? WeatherType.SUPERCELL : roll < supercells + 0.14F ? WeatherType.HAIL : WeatherType.THUNDERSTORM;
            float radius = 110.0F + random.nextFloat() * 60.0F;
            // it lives until about dusk, and takes a couple of minutes to tower up
            int maxAge = (int) Math.max(3000L, 12500L - time + random.nextInt(1500));
            double speed = 0.04 + random.nextDouble() * 0.04;
            Vec3 wind = Wind.direction(state.windAngle);
            WeatherManager.spawnCell(level, state, type, sx, sz, radius, wind.x * speed, wind.z * speed, maxAge, 2400, true);
          }
        }
    }

    /**
     * Whether today is one for afternoon storms, worked out from the day count alone, so a client can see it coming
     * in the morning sky (the turreted castellanus clouds) before the storms build.
     */
    public static boolean thunderyDay(long day, boolean tropical) {
        int h = net.minecraft.util.Mth.murmurHash3Mixer((int) day * 0x2545F491 + 0x7F4A7C15 + (int) (day >>> 32));
        return (h >>> 8) / (float) (1 << 24) < (tropical ? 0.75F : 0.5F);
    }

    private Convection() {}
}
