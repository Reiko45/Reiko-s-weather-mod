package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Winds the sun drives, close to the ground. By day the land warms faster than the sea, so a sea breeze blows
 * in off the water; at night the land cools and a gentler land breeze blows back out. On slopes, warmed air
 * creeps uphill by day (a valley wind) and cold air drains downhill at night (a mountain wind), hardest of all
 * off snow and ice (a katabatic wind). A proper prevailing wind sweeps them all away.
 */
public final class Breezes {
    /**
     * The wind as it blows here, {angle (radians, the way it blows toward), strength 0 to 1}, given the prevailing
     * wind's angle and strength.
     */
    public static float[] adjust(Level level, int x, int z, float windAngle, float wind) {
        if (!WeatherConfig.SPEC.isLoaded() || !WeatherConfig.SEA_BREEZES.get() || level.dimension() != Level.OVERWORLD) {
            return new float[]{windAngle, wind};
        }
        Microclimate.Info info = Microclimate.at(level, x, z, windAngle);
        if (Float.isNaN(info.seaAngle()) && info.slope() < 0.12F) {
            return new float[]{windAngle, wind};
        }
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        float day = Mth.clamp(sun * 4.0F, -1.0F, 1.0F); // above 0 by day, below at night
        float lx = 0.0F, lz = 0.0F;
        if (!Float.isNaN(info.seaAngle())) {
            float onshore = info.seaAngle() + Mth.PI; // from the sea toward the land
            float s = day > 0 ? 0.32F * day : 0.16F * day;
            lx += Mth.cos(onshore) * s;
            lz += Mth.sin(onshore) * s;
        }
        if (info.slope() >= 0.12F) {
            boolean icy = level.getBiome(new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z))
                    .value().coldEnoughToSnow(new BlockPos(x, level.getSeaLevel() + 40, z));
            float s = (day > 0 ? 0.22F * day : 0.25F * day * (icy ? 2.2F : 1.0F)) * info.slope();
            lx += Mth.cos(info.slopeAngle()) * s;
            lz += Mth.sin(info.slopeAngle()) * s;
        }
        float local = Mth.sqrt(lx * lx + lz * lz);
        if (local < 0.02F) {
            return new float[]{windAngle, wind};
        }
        // the prevailing wind swamps the local winds once it's blowing at all hard
        float k = Mth.clamp(1.0F - wind / 0.55F, 0.0F, 1.0F);
        float wx = Mth.cos(windAngle) * wind + lx * k, wz = Mth.sin(windAngle) * wind + lz * k;
        float strength = Mth.sqrt(wx * wx + wz * wz);
        return new float[]{strength < 1.0E-4F ? windAngle : (float) Mth.atan2(wz, wx), Math.max(strength, wind * 0.8F)};
    }

    private Breezes() {}
}
