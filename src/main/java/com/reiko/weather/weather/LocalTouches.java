package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * Small touches the weather leaves on the ground round players: hair ice on fallen logs, bubbles frozen into the ice
 * of boggy lakes, fog dripping off trees onto the crops below, and the desert flowering after a wet winter.
 */
public final class LocalTouches {
    private static final int AREA = 40;

    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherProfile p = local.profile();
        long time = Math.floorMod(level.getDayTime(), 24000L);
        Season season = WeatherManager.seasonInfo().season();
        boolean night = time > 13000L || time < 1500L;
        if (WeatherConfig.HAIR_ICE.get() && night && climate != Climate.ARID && random.nextInt(4) == 0) {
            float air = Temperature.ambient(level, player.blockPosition(), local);
            // hair ice needs damp air just below freezing: a few degrees colder and the water in the wood freezes solid
            if (air < 0.5F && air > -5.0F && (Temperature.humidity(level, player.blockPosition(), local) > 0.55F || p.fog > 0.3F)) {
                for (int i = 0; i < 3; i++) hairIce(level, column(level, player, random));
            }
        }
        if (WeatherConfig.FROZEN_BUBBLES.get() && climate != Climate.ARID && random.nextInt(8) == 0
                && (season == Season.WINTER || climate == Climate.COLD)) {
            float air = Temperature.ambient(level, player.blockPosition(), local);
            if (air < -3.0F) frozenBubbles(level, column(level, player, random), random);
        }
        if (WeatherConfig.FOG_DRIP.get() && p.fog > 0.35F && p.rain < 0.05F && random.nextInt(2) == 0) {
            fogDrip(level, column(level, player, random), random);
        }
        if (WeatherConfig.SUPERBLOOMS.get() && climate == Climate.ARID && season == Season.SPRING && ClimateYears.leaning(level) > 0.3F) {
            for (int i = 0; i < 2; i++) bloom(level, column(level, player, random), random);
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /**
     * Hair ice: on a damp night just below freezing, a fungus living in a rotting log pushes water out through the
     * pores of the wood, and it freezes into masses of hair-fine white threads that melt away once the sun is up.
     */
    private static void hairIce(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.getBlockState(top).isAir()) return;
        BlockState log = level.getBlockState(top.below());
        // a log lying on the ground, not a beam in a wall or a roof
        if (!log.is(BlockTags.LOGS) || !log.hasProperty(RotatedPillarBlock.AXIS) || log.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) return;
        if (!level.getBlockState(top.below(2)).is(BlockTags.DIRT) && !level.getBlockState(top.below(2)).is(Blocks.MOSS_BLOCK)) return;
        if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath().startsWith("stripped")) return;
        level.setBlockAndUpdate(top, ModBlocks.HAIR_ICE.get().defaultBlockState());
    }

    /**
     * Bubbles frozen into lake ice: rotting plants on the bed of a boggy lake give off marsh gas, and as the ice
     * thickens it catches the rising bubbles in stacks of flat white discs.
     */
    private static void frozenBubbles(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null) return;
        BlockPos ice = top.below();
        if (!level.getBlockState(ice).is(Blocks.ICE) || !level.getFluidState(ice.below()).is(FluidTags.WATER)) return;
        boolean boggy = level.getBiome(ice).is(Biomes.SWAMP) || level.getBiome(ice).is(Biomes.MANGROVE_SWAMP);
        if (!boggy) {
            // a soft, muddy bed under the water
            BlockPos.MutableBlockPos p = ice.below().mutable();
            for (int i = 0; i < 10 && level.getFluidState(p).is(FluidTags.WATER); i++) p.move(Direction.DOWN);
            BlockState bed = level.getBlockState(p);
            boggy = bed.is(Blocks.MUD) || bed.is(Blocks.CLAY) || bed.is(ModBlocks.PEAT.get()) || bed.is(Blocks.DIRT) || bed.is(Blocks.GRASS_BLOCK)
                    || bed.is(Blocks.MOSS_BLOCK) || bed.is(Blocks.PODZOL);
            if (boggy && level.getBiome(ice).is(BiomeTags.IS_OCEAN)) boggy = false;
        }
        if (boggy && random.nextInt(2) == 0) level.setBlockAndUpdate(ice, ModBlocks.BUBBLE_ICE.get().defaultBlockState());
    }

    /** Fog drip: trees comb droplets out of fog that drifts through their leaves, enough to water what grows below. */
    private static void fogDrip(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null || !level.getBlockState(top.below()).is(BlockTags.LEAVES)) return;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top);
        BlockState at = level.getBlockState(ground);
        BlockState below = level.getBlockState(ground.below());
        if (below.is(Blocks.FARMLAND) && below.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
            level.setBlock(ground.below(), below.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
        }
        if (at.getBlock() instanceof CropBlock && random.nextInt(3) == 0) {
            at.randomTick(level, ground, random);
        }
    }

    /**
     * A superbloom: after a wet winter, seeds that have lain in the desert sand for years all wake at once, and the
     * desert floor is carpeted with flowers for a few weeks in spring.
     */
    private static void bloom(ServerLevel level, @Nullable BlockPos top, RandomSource random) {
        if (top == null || !level.getBlockState(top).isAir() || !level.canSeeSky(top)) return;
        BlockState ground = level.getBlockState(top.below());
        if (!ground.is(BlockTags.SAND) && !ground.is(BlockTags.TERRACOTTA)) return;
        // they come up in drifts of one colour or another, not evenly everywhere
        float patch = com.reiko.weather.landform.Terrain.field(level.getSeed() ^ ClimateYears.year(level), top.getX(), top.getZ(), 14.0);
        if (patch < 0.38F || random.nextFloat() > (patch - 0.38F) * 2.2F) return;
        float kind = com.reiko.weather.landform.Terrain.field(level.getSeed() + 7, top.getX(), top.getZ(), 22.0);
        Block flower = kind < 0.4F ? ModBlocks.DESERT_GOLD.get() : kind < 0.7F ? ModBlocks.DESERT_POPPY.get() : ModBlocks.SAND_VERBENA.get();
        if (flower.defaultBlockState().canSurvive(level, top)) {
            level.setBlockAndUpdate(top, flower.defaultBlockState());
        }
    }

    /** Whether a spot is just inside a cave mouth, where a cool draught blows out on a hot day. */
    public static boolean caveDraught(ServerLevel level, BlockPos eyes) {
        if (!WeatherConfig.CAVE_BREATH.get() || level.canSeeSky(eyes)) return false;
        int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eyes);
        if (sky < 2) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            boolean passage = true;
            for (int k = 2; k <= 5 && passage; k++) {
                BlockPos in = eyes.relative(d, k);
                passage = level.getBlockState(in).isAir() && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, in) <= Math.max(1, 6 - k);
            }
            if (passage) return true;
        }
        return false;
    }

    private LocalTouches() {}
}
