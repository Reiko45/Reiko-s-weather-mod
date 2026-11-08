package com.reiko.weather.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import javax.annotation.Nullable;

/**
 * Hurricanes, done properly: they form over the open sea and come in toward the coast on a track that curves
 * round (west, then north, then away to the north-east) instead of drifting with the wind. Warm sea feeds them
 * and they strengthen; over land or cold water they weaken, and a spent one is just a big rainstorm. A strong
 * one has a calm, clear eye ringed by the eyewall, and spiral rain bands sweeping round it.
 */
public final class Hurricanes {
    /** Sets up a newly formed hurricane. */
    static void setUp(StormCell cell, RandomSource random) {
        cell.steer = false;
        cell.power = 0.5F + random.nextFloat() * 0.2F;
        // over its life the track curves round by about a quarter turn
        cell.turn = (float) (Mth.HALF_PI * (0.7 + random.nextDouble() * 0.6) / Math.max(1, cell.maxAge));
    }

    /** Once a second: warm sea strengthens it, land and cold water starve it. Returns true if it died down to a rainstorm. */
    static boolean develop(ServerLevel level, StormCell cell) {
        Holder<Biome> biome = level.getBiome(BlockPos.containing(cell.x, level.getSeaLevel(), cell.z));
        float change;
        if (biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)) {
            change = 0.0012F;
        } else if (biome.is(Biomes.OCEAN) || biome.is(Biomes.DEEP_OCEAN)) {
            change = 0.0004F;
        } else if (biome.is(BiomeTags.IS_OCEAN)) {
            change = -0.0015F; // cold water
        } else {
            change = -0.004F; // over land it's cut off from the warm sea that drives it
        }
        Season season = WeatherManager.seasonInfo().season();
        if (change > 0.0F && season != null && season != Season.SUMMER && season != Season.AUTUMN) {
            change *= 0.4F;
        }
        cell.power = Mth.clamp(cell.power + change, 0.0F, 1.0F);
        if (cell.power < 0.25F) {
            // what's left of it is a big rainstorm, drifting with the wind
            cell.type = WeatherType.HEAVY_RAIN;
            cell.turn = 0.0F;
            cell.steer = true;
            cell.power = 1.0F;
            return true;
        }
        return false;
    }

    /** Forms a hurricane out at sea (mostly to the east and south) heading in toward a player, or null if there's no sea near. */
    @Nullable
    static StormCell spawnOffshore(ServerLevel level, WeatherState state, ServerPlayer target) {
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = -Math.PI / 6.0 + random.nextDouble() * Math.PI * 0.75;
            double distance = 1000.0 + random.nextDouble() * 400.0;
            double x = target.getX() + Math.cos(angle) * distance, z = target.getZ() + Math.sin(angle) * distance;
            if (!overSea(level, x, z, 250)) continue;
            StormCell cell = WeatherManager.spawnCell(level, state, WeatherType.HURRICANE, x, z, -1);
            // aimed a little to the left of the player: the track bends back toward them as it comes in
            double heading = Math.atan2(target.getZ() - z, target.getX() - x) - 0.25 + random.nextDouble() * 0.2;
            double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
            cell.vx = Math.cos(heading) * speed;
            cell.vz = Math.sin(heading) * speed;
            return cell;
        }
        return null;
    }

    private static boolean overSea(ServerLevel level, double x, double z, double r) {
        int sea = level.getSeaLevel(), ocean = 0;
        if (!level.getBiome(BlockPos.containing(x, sea, z)).is(BiomeTags.IS_OCEAN)) return false;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0;
            if (level.getBiome(BlockPos.containing(x + Math.cos(a) * r, sea, z + Math.sin(a) * r)).is(BiomeTags.IS_OCEAN)) ocean++;
        }
        return ocean >= 3;
    }

    private Hurricanes() {}
}
