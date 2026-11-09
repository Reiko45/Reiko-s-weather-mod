package com.reiko.weather.weather;

import com.reiko.weather.block.RimeBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The work of the frost: rime ice growing into the wind in freezing fog, snow rollers on windy fields of wet
 * snow, and pressure ridges where thick lake ice cracks and buckles.
 */
public final class Frostwork {
    static void tickPlayer(ServerLevel level, WeatherState state, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherType type = local.type();
        // rime: freezing fog freezes onto whatever it's blown against, more of it the higher up you are
        if (WeatherConfig.RIME_ICE.get() && (type == WeatherType.FOG || type == WeatherType.SEA_FOG || local.profile().fog > 0.6F)
                && player.tickCount % 2 == 0 && Temperature.ambient(level, player.blockPosition(), local) < -1.0F) {
            float altitude = Mth.clamp((float) (player.getY() - level.getSeaLevel()) / 60.0F, 0.0F, 1.0F);
            for (int i = 0; i < 2 + Math.round(4 * altitude); i++) rime(level, state, player, random);
        }
        // snow rollers: a strong wind over wet, sticky snow on open ground
        float wind = local.profile().wind * local.intensity();
        if (WeatherConfig.SNOW_ROLLERS.get() && wind >= 0.45F && random.nextInt(500) == 0) {
            float t = Temperature.ambient(level, player.blockPosition(), local);
            if (t > -3.0F && t < 1.5F) snowRoller(level, state, player, random);
        }
        // lake ice under a hard frost cracks and buckles into pressure ridges
        if (WeatherConfig.LAKE_ICE.get() && climate == Climate.COLD && random.nextInt(2500) == 0
                && Temperature.ambient(level, player.blockPosition(), local) < -6.0F) {
            pressureRidge(level, player, random);
        }
    }

    private static void rime(ServerLevel level, WeatherState state, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(41) - 20, z = player.getBlockZ() + random.nextInt(41) - 20;
        if (!level.isLoaded(new BlockPos(x, 0, z))) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos support = new BlockPos(x, top - 1 - random.nextInt(6), z);
        BlockState s = level.getBlockState(support);
        if (s.isAir() || !s.getFluidState().isEmpty()) return;
        // the face the wind blows onto (the wind comes from the opposite way to where it's blowing)
        float from = state.windAngle + Mth.PI;
        Direction windward = Direction.fromYRot(Math.toDegrees(from) - 90.0);
        if (random.nextInt(4) == 0) windward = Direction.UP;
        BlockPos air = support.relative(windward);
        if (!level.canSeeSky(air) && windward != Direction.UP && !level.canSeeSky(air.above())) return;
        RimeBlock.coat(level, air, windward.getOpposite(), ModBlocks.RIME.get());
    }

    private static void snowRoller(ServerLevel level, WeatherState state, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(49) - 24, z = player.getBlockZ() + random.nextInt(49) - 24;
        if (!level.isLoaded(new BlockPos(x, 0, z))) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
        BlockState snow = level.getBlockState(top);
        if (!snow.is(Blocks.SNOW) || snow.getValue(SnowLayerBlock.LAYERS) > 3 || !level.canSeeSky(top.above())) return;
        Direction downwind = Direction.fromYRot(Math.toDegrees(state.windAngle) - 90.0);
        // it needs a few blocks of level, snowy ground upwind to roll up
        for (int k = 1; k <= 3; k++) {
            BlockPos p = top.relative(downwind.getOpposite(), k);
            if (!level.getBlockState(p).is(Blocks.SNOW)) return;
        }
        level.setBlockAndUpdate(top, ModBlocks.SNOW_ROLLER.get().defaultBlockState().setValue(com.reiko.weather.block.SnowRollerBlock.FACING, downwind));
        for (int k = 1; k <= 3; k++) {
            BlockPos p = top.relative(downwind.getOpposite(), k);
            BlockState s = level.getBlockState(p);
            int layers = s.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) level.setBlockAndUpdate(p, s.setValue(SnowLayerBlock.LAYERS, layers - 1));
            else level.removeBlock(p, false); // the bare track it rolled along
        }
    }

    private static boolean lakeIce(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).is(Blocks.ICE) && level.getFluidState(p.below()).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir();
    }

    private static void pressureRidge(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = player.getBlockX() + random.nextInt(65) - 32, z = player.getBlockZ() + random.nextInt(65) - 32;
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            BlockPos ice = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
            if (!lakeIce(level, ice) || !lakeIce(level, ice.offset(6, 0, 0)) || !lakeIce(level, ice.offset(-6, 0, 0))
                    || !lakeIce(level, ice.offset(0, 0, 6)) || !lakeIce(level, ice.offset(0, 0, -6))) continue;
            // a broken line of buckled slabs across the lake
            float a = random.nextFloat() * Mth.PI;
            int length = 6 + random.nextInt(8);
            for (int k = -length / 2; k <= length / 2; k++) {
                BlockPos p = BlockPos.containing(ice.getX() + Mth.cos(a) * k, ice.getY(), ice.getZ() + Mth.sin(a) * k);
                if (!lakeIce(level, p) || random.nextInt(5) == 0) continue;
                level.setBlockAndUpdate(p, Blocks.PACKED_ICE.defaultBlockState());
                if (random.nextInt(3) > 0) level.setBlockAndUpdate(p.above(), Blocks.ICE.defaultBlockState());
            }
            level.playSound(null, ice, SoundEvents.GLASS_BREAK, SoundSource.AMBIENT, 2.5F, 0.4F);
            level.playSound(null, ice, com.reiko.weather.registry.ModSounds.ICE_SING.get(), SoundSource.AMBIENT, 3.0F, 0.7F);
            return;
        }
    }

    private Frostwork() {}
}
