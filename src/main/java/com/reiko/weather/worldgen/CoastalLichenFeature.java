package com.reiko.weather.worldgen;

import com.mojang.serialization.Codec;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.landform.Terrain;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The lichen zones of a rocky shore. Just above the reach of all but the waves, black tar lichen stains the rock like
 * spilt paint; above it, out of reach of all but the biggest storms' spray, comes a band of bright orange lichen. Seen
 * from the sea, a rocky coast is striped black and orange along its foot.
 */
public class CoastalLichenFeature extends Feature<NoneFeatureConfiguration> {
    public CoastalLichenFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (WeatherConfig.SPEC.isLoaded() && !WeatherConfig.COASTAL_LICHEN.get()) return false;
        WorldGenLevel level = ctx.level();
        int sea = level.getSeaLevel(), x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
        long seed = level.getSeed() ^ 0x5DEECE66DL;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), n = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                // (it grows in patches, not an even coat)
                if (Terrain.field(seed, x, z, 7.0) < 0.32F) continue;
                if (!nearSea(level, x, z, sea)) continue;
                for (int y = sea; y <= sea + 7; y++) {
                    p.set(x, y, z);
                    if (!rock(level.getBlockState(p))) continue;
                    Block lichen = y <= sea + 1 ? ModBlocks.BLACK_LICHEN.get() : ModBlocks.ORANGE_LICHEN.get();
                    for (Direction d : Direction.values()) {
                        if (d == Direction.DOWN) continue;
                        n.setWithOffset(p, d);
                        // only faces in this chunk (the neighbours' are theirs to coat), open to the air and the spray
                        if (n.getX() < x0 || n.getX() >= x0 + 16 || n.getZ() < z0 || n.getZ() >= z0 + 16) continue;
                        if (Terrain.hash(seed, (n.getX() * 73856093) ^ (n.getY() * 19349663) ^ (n.getZ() * 83492791) ^ d.ordinal()) < 0.35F) continue;
                        BlockState there = level.getBlockState(n);
                        var face = MultifaceBlock.getFaceProperty(d.getOpposite());
                        if (there.isAir()) {
                            level.setBlock(n, lichen.defaultBlockState().setValue(face, true), Block.UPDATE_CLIENTS);
                            any = true;
                        } else if (there.is(lichen)) {
                            level.setBlock(n, there.setValue(face, true), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
        return any;
    }

    /** Whether the open sea is within a few blocks of a column. */
    private static boolean nearSea(WorldGenLevel level, int x, int z, int sea) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx += 5) {
            for (int dz = -5; dz <= 5; dz += 5) {
                p.set(x + dx, sea - 1, z + dz);
                if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.move(0, -2, 0)).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    private static boolean rock(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.BASALT)
                || s.is(Blocks.SMOOTH_BASALT) || s.is(ModBlocks.LIMESTONE.get());
    }
}
