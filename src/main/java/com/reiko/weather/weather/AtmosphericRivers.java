package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Atmospheric rivers: long, narrow plumes of warm, wet air that come in off the ocean, mostly in autumn and
 * winter, and pour rain onto the coast and its mountains for days. The plume points inland and slides slowly
 * sideways along the coast, so wherever it sits it just keeps raining: floods, swollen rivers and mudslides
 * (and deep snow up on the cold peaks).
 */
public final class AtmosphericRivers {
    /** Checked every few seconds; at most one chance a day. */
    static void tick(ServerLevel level, WeatherState state) {
        if (!WeatherConfig.ATMOSPHERIC_RIVERS.get() || !WeatherConfig.REGIONAL_STORMS.get()) {
            return;
        }
        long day = level.getDayTime() / 24000L;
        if (state.riverDay == day || level.getDayTime() % 24000L < 2000L) {
            return;
        }
        state.riverDay = day;
        state.setDirty();
        if (state.cells.stream().anyMatch(c -> c.type == WeatherType.ATMOSPHERIC_RIVER) || Drought.severity(state) > 0.0F) {
            return;
        }
        Season season = WeatherManager.seasonInfo().season();
        float chance = season == null ? 0.06F : switch (season) {
            case AUTUMN, WINTER -> 0.12F;
            case SPRING -> 0.05F;
            case SUMMER -> 0.01F;
        };
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > chance) continue;
            float seaAngle = seaward(level, player);
            if (Float.isNaN(seaAngle)) continue;
            spawnFor(level, state, player.getX(), player.getZ(), seaAngle, random);
            return;
        }
    }

    /** Sets an atmospheric river down across (x, z), its plume running inland from the sea toward {@code seaAngle} + 180°. */
    public static StormCell spawnFor(ServerLevel level, WeatherState state, double x, double z, float seaAngle, RandomSource random) {
        // the plume's long axis points from the sea inland; it drifts slowly sideways, across that
        double along = seaAngle + Math.PI, drift = along + Math.PI / 2.0 * (random.nextBoolean() ? 1 : -1);
        double speed = 0.012 + random.nextDouble() * 0.008;
        int life = 36000 + random.nextInt(24000);
        float radius = 100.0F + random.nextFloat() * 30.0F;
        double back = speed * life * 0.4; // start off to one side, so it takes a while to arrive and then sits over us
        StormCell cell = WeatherManager.spawnCell(level, state, WeatherType.ATMOSPHERIC_RIVER,
                x - Math.cos(drift) * back, z - Math.sin(drift) * back, radius, Math.cos(drift) * speed, Math.sin(drift) * speed, life, 2400, false);
        return cell;
    }

    /** Which way the open sea is from a player (within ~400 blocks), or NaN if they're inland. */
    static float seaward(ServerLevel level, ServerPlayer player) {
        float best = Float.NaN;
        int bestCount = 0;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int ocean = 0;
            for (int d = 100; d <= 400; d += 100) {
                if (level.getBiome(BlockPos.containing(player.getX() + Mth.cos(a) * d, level.getSeaLevel(), player.getZ() + Mth.sin(a) * d))
                        .is(BiomeTags.IS_OCEAN)) ocean++;
            }
            if (ocean > bestCount) {
                bestCount = ocean;
                best = a;
            }
        }
        return bestCount >= 2 ? best : Float.NaN;
    }

    private AtmosphericRivers() {}
}
