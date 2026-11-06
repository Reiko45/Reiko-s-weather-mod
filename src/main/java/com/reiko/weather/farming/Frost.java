package com.reiko.weather.farming;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Freezing nights nip exposed crops: they lose growth, and seedlings die. Greenhouses keep them safe. */
public final class Frost {
    private static final int AREA = 32;

    public static void tick(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!WeatherConfig.CROP_FROST.get()) {
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < 3; i++) {
            int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA;
            int z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !(crop instanceof com.reiko.weather.block.KaleCropBlock)
                    && random.nextFloat() < 0.25F && isFrosty(level, pos, local)) {
                nip(level, pos, state, crop);
            }
        }
    }

    /** Freezing air over a crop that isn't under glass. */
    public static boolean isFrosty(ServerLevel level, BlockPos pos, LocalWeather local) {
        return Temperature.ambient(level, pos, local) < 0.0F && !Greenhouse.underGlass(level, pos)
                && (SkyPhenomena.sunHeight(level, 1.0F) < 0.1F || Temperature.ambient(level, pos, local) < -4.0F);
    }

    public static void nip(ServerLevel level, BlockPos pos, BlockState state, CropBlock crop) {
        int age = crop.getAge(state);
        if (age == 0) {
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        } else {
            level.setBlockAndUpdate(pos, crop.getStateForAge(Math.max(0, age - 2)));
        }
    }

    private Frost() {}
}
