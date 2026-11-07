package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * On cool, calm mornings a bank of fog builds up out at sea and rolls in over the coast, then burns off
 * by the middle of the morning.
 */
public final class SeaFog {
    static void tick(ServerLevel level, WeatherState state) {
        if (!WeatherConfig.SEA_FOG.get()) {
            return;
        }
        long dayTime = level.getDayTime(), time = dayTime % 24000L;
        long day = (dayTime + 1200L) / 24000L; // the dawn belongs to the day it starts
        if (time < 22800L && time >= 800L || state.seaFogDay == day) {
            return;
        }
        state.seaFogDay = day;
        state.setDirty();
        WeatherType type = state.type;
        if (!type.isCalm() && type != WeatherType.FOG && type != WeatherType.DRIZZLE) {
            return;
        }
        Season season = WeatherManager.seasonInfo().season();
        float chance = season == null ? 0.35F : switch (season) {
            case SPRING, AUTUMN -> 0.5F;
            case SUMMER -> 0.35F;
            case WINTER -> 0.25F;
        };
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > chance) continue;
            if (state.cells.stream().anyMatch(c -> c.type == WeatherType.SEA_FOG && c.distanceTo(player.getX(), player.getZ()) < 500)) continue;
            // which way is the open sea?
            float bestAngle = 0.0F;
            int best = 0;
            for (int i = 0; i < 16; i++) {
                float angle = i * Mth.TWO_PI / 16.0F;
                int ocean = 0;
                for (int d = 96; d <= 288; d += 96) {
                    BlockPos p = BlockPos.containing(player.getX() + Mth.cos(angle) * d, level.getSeaLevel(), player.getZ() + Mth.sin(angle) * d);
                    if (level.getBiome(p).is(BiomeTags.IS_OCEAN)) ocean++;
                }
                if (ocean > best) {
                    best = ocean;
                    bestAngle = angle;
                }
            }
            if (best < 2) continue;
            float radius = 140.0F + random.nextFloat() * 90.0F;
            double dx = Mth.cos(bestAngle), dz = Mth.sin(bestAngle), distance = radius + 120.0;
            double speed = 0.1 + random.nextDouble() * 0.05;
            // gone by mid-morning, when the sun burns it off
            int life = (int) ((time >= 22800L ? 24000L - time : -time) + 4500L + random.nextInt(1500));
            WeatherManager.spawnCell(level, state, WeatherType.SEA_FOG, player.getX() + dx * distance, player.getZ() + dz * distance,
                    radius, -dx * speed, -dz * speed, life, 1200, false);
        }
    }

    private SeaFog() {}
}
