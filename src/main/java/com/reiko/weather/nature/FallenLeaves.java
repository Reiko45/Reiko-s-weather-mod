package com.reiko.weather.nature;

import com.reiko.weather.block.LeafPileBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.StormDebris;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/** In autumn, leaves drift down and pile up under the trees (more of them in the wind). */
public final class FallenLeaves {
    public static void tick(ServerLevel level, ServerPlayer player, Climate climate, float wind) {
        if (!WeatherConfig.LEAF_PILES.get() || climate == Climate.ARID) {
            return;
        }
        Seasons season = Seasons.of(WeatherManager.seasonInfo().season(), WeatherManager.seasonInfo().dayOfSeason(), WeatherManager.seasonInfo().daysPerSeason());
        if (season.autumn <= 0.0F) {
            return;
        }
        RandomSource random = level.random;
        if (random.nextFloat() < 0.04F * season.autumn * (1.0F + wind)) {
            BlockPos ground = StormDebris.groundUnderTrees(level, player, random, 40);
            if (ground != null) {
                // deeper piles in late autumn
                LeafPileBlock.addLayer(level, ground, ModBlocks.LEAF_PILE.get(), season.autumn > 0.6F ? 3 : 2);
            }
        }
    }

    /** How far into leaf-fall the season is: 0 outside autumn, building up through it. */
    private record Seasons(float autumn) {
        static Seasons of(Season season, int day, int days) {
            if (season != Season.AUTUMN || days <= 0) {
                return new Seasons(0.0F);
            }
            return new Seasons(Math.min(1.0F, 0.25F + (day + 1) / (float) days));
        }
    }

    private FallenLeaves() {}
}
