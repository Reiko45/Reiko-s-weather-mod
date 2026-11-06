package com.reiko.weather.volcano;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Builds a volcano into the world, one area (a chunk, during world generation) at a time: fills the cone
 * up from the existing ground, clears trees off its new slopes, carves the crater and fills its lava lake.
 */
public final class VolcanoBuilder {
    private static final int FLAGS = 2;

    public static void build(WorldGenLevel level, VolcanoShape shape, BoundingBox area) {
        // finished chunks keep the final heightmaps; chunks still being generated keep the "_WG" ones
        Heightmap.Types floorType = level instanceof ServerLevel ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                int surface = shape.surfaceY(x, z);
                if (surface == VolcanoShape.OUTSIDE) {
                    continue;
                }
                surface = Mth.clamp(surface, minY + 1, maxY - 30);
                int ground = groundY(level, floorType, pos, x, z);
                boolean crater = shape.inCrater(x, z);
                if (!crater && ground < surface) {
                    // the foot of the cone runs smoothly down into lower land (or the sea bed) instead of ending in a cliff
                    float edge = Mth.clamp((shape.radius() - (float) shape.horizontalDistance(x, z)) / (shape.radius() * 0.3F), 0.0F, 1.0F);
                    surface = Math.round(Mth.lerp(edge * edge * (3.0F - 2.0F * edge), ground, surface));
                }
                // fill rock up to the cone's surface
                for (int y = Math.min(ground, surface) - 2; y <= surface; y++) {
                    if (y <= ground - 3) continue;
                    pos.set(x, y, z);
                    level.setBlock(pos, rock(shape, x, y, z, surface, crater), FLAGS);
                }
                // the crater: hollow above its floor, a lava lake at the bottom
                if (crater) {
                    for (int y = surface + 1; y <= Math.max(ground, shape.peakY()) + 4 && y <= maxY; y++) {
                        pos.set(x, y, z);
                        level.setBlock(pos, y <= shape.lavaY() ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                // nothing growing on fresh volcanic rock: clear trees and plants above the new surface
                for (int y = surface + 1; y <= surface + 28 && y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE)
                            || state.is(BlockTags.FLOWERS) || state.is(Blocks.SNOW) || state.is(Blocks.BAMBOO)) {
                        if (state.getFluidState().isEmpty()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        }
                    }
                }
                decorate(level, shape, pos, x, z, surface, crater);
            }
        }
    }

    /** The real ground at a column: below any water, trees and plants. */
    private static int groundY(WorldGenLevel level, Heightmap.Types type, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(type, x, z) - 1;
        int min = level.getMinBuildHeight();
        while (y > min) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE) || state.isAir()) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    private static float noise(VolcanoShape shape, int x, int y, int z) {
        long h = Mth.getSeed(x, y, z) ^ shape.seed();
        h = h * 6364136223846793005L + 1442695040888963407L;
        return ((h >>> 40) & 0xFFFF) / 65535.0F;
    }

    /** What the volcano is made of: ash and tuff at its foot, basalt up the flanks, blackstone and magma near the top. */
    private static BlockState rock(VolcanoShape shape, int x, int y, int z, int surface, boolean crater) {
        float n = noise(shape, x, y, z);
        int depth = surface - y;
        if (crater) {
            if (depth == 0) return n < 0.22F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            return n < 0.08F ? Blocks.MAGMA_BLOCK.defaultBlockState() : n < 0.6F ? Blocks.BLACKSTONE.defaultBlockState() : basalt();
        }
        float f = Mth.clamp((surface - shape.baseY()) / (float) shape.height(), 0.0F, 1.0F);
        if (depth > 3) {
            return n < 0.5F ? Blocks.STONE.defaultBlockState() : n < 0.8F ? Blocks.TUFF.defaultBlockState() : basalt();
        }
        if (shape.onOldLavaFlow(x, z)) {
            if (depth == 0 && n < 0.08F) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return n < 0.55F ? basalt() : n < 0.8F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        }
        if (f > 0.78F) {
            if (depth == 0 && n < 0.05F) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return n < 0.45F ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.8F ? basalt() : Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (f > 0.35F) {
            return n < 0.45F ? basalt() : n < 0.75F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        }
        if (depth == 0 && n < 0.25F) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        // only the very foot has a little soil, where the forest creeps up onto the mountain
        if (f < 0.12F && n > 0.8F) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return n < 0.55F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
    }

    private static BlockState basalt() {
        return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Y);
    }

    /** Steam vents and sulfur on the upper slopes, a dusting of ash lower down. */
    private static void decorate(WorldGenLevel level, VolcanoShape shape, BlockPos.MutableBlockPos pos, int x, int z, int surface, boolean crater) {
        if (crater) {
            return;
        }
        float f = Mth.clamp((surface - shape.baseY()) / (float) shape.height(), 0.0F, 1.0F);
        float n = noise(shape, x, 7777, z);
        pos.set(x, surface, z);
        if (f > 0.55F && n < 0.006F) {
            level.setBlock(pos, ModBlocks.VOLCANIC_VENT.get().defaultBlockState(), FLAGS);
        } else if (f > 0.5F && nearVent(shape, x, z) && n < 0.45F || f > 0.92F && n < 0.12F) {
            level.setBlock(pos, ModBlocks.SULFUR_CRUST.get().defaultBlockState(), FLAGS);
        } else if (f < 0.6F && f > 0.05F && n > 0.93F) {
            pos.set(x, surface + 1, z);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, ModBlocks.VOLCANIC_ASH.get().defaultBlockState(), FLAGS);
            }
        }
    }

    private static boolean nearVent(VolcanoShape shape, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if ((dx != 0 || dz != 0) && noise(shape, x + dx, 7777, z + dz) < 0.006F) {
                    int s = shape.surfaceY(x + dx, z + dz);
                    if (s != VolcanoShape.OUTSIDE && Mth.clamp((s - shape.baseY()) / (float) shape.height(), 0, 1) > 0.55F) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private VolcanoBuilder() {}
}
