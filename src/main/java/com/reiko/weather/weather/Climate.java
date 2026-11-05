package com.reiko.weather.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The broad climate at a position. Weather is global per dimension, but each weather type
 * manifests differently depending on the climate it is experienced in.
 */
public enum Climate {
    /** Biomes cold enough to snow (tundra, taiga, peaks...). */
    COLD,
    /** Everything that normally receives rain. */
    TEMPERATE,
    /** Biomes without precipitation (desert, badlands, savanna). */
    ARID;

    public static Climate at(Level level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        if (!biome.hasPrecipitation()) {
            return ARID;
        }
        return biome.coldEnoughToSnow(pos) ? COLD : TEMPERATE;
    }
}
