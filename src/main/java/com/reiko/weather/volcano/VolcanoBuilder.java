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

    /** An active volcano (lava, vents, lava tubes, black sand), or a long-extinct one (grass and a crater lake). */
    public enum Style { ACTIVE, EXTINCT }

    public static void build(WorldGenLevel level, VolcanoShape shape, BoundingBox area) {
        build(level, shape, area, Style.ACTIVE);
    }

    public static void build(WorldGenLevel level, VolcanoShape shape, BoundingBox area, Style style) {
        boolean active = style == Style.ACTIVE;
        boolean tubes = active && (!com.reiko.weather.config.WeatherConfig.SPEC.isLoaded() || com.reiko.weather.config.WeatherConfig.LAVA_TUBES.get());
        int sea = level.getSeaLevel();
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
                    level.setBlock(pos, active ? rock(shape, x, y, z, surface, crater) : extinctRock(shape, x, y, z, surface, crater, sea), FLAGS);
                }
                // black sand beaches where a volcanic island meets the sea
                if (active && !crater && surface >= sea - 4 && surface <= sea + 1) {
                    for (int y = surface - 1; y <= surface; y++) {
                        pos.set(x, y, z);
                        level.setBlock(pos, com.reiko.weather.registry.ModBlocks.BLACK_SAND.get().defaultBlockState(), FLAGS);
                    }
                }
                // the crater: hollow above its floor, with a lava lake (or, long extinct, a deep cold lake) at the bottom
                if (crater) {
                    int lake = active ? shape.lavaY() : shape.peakY() - 3;
                    BlockState fill = active ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
                    for (int y = surface + 1; y <= Math.max(ground, shape.peakY()) + 4 && y <= maxY; y++) {
                        pos.set(x, y, z);
                        level.setBlock(pos, y <= lake ? fill : Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                if (tubes && !crater) {
                    carveTubes(level, shape, pos, x, z, surface);
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
                if (active) {
                    decorate(level, shape, pos, x, z, surface, crater);
                }
            }
        }
    }

    /** A long-dead volcano is grassed over, with bare rock only near the rim, and mud and gravel under its lake. */
    private static BlockState extinctRock(VolcanoShape shape, int x, int y, int z, int surface, boolean crater, int sea) {
        float n = noise(shape, x, y, z);
        int depth = surface - y;
        if (crater) {
            if (depth == 0) return n < 0.45F ? Blocks.CLAY.defaultBlockState() : n < 0.8F ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            return n < 0.6F ? Blocks.STONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        }
        if (depth > 3) {
            return n < 0.55F ? Blocks.STONE.defaultBlockState() : n < 0.85F ? Blocks.TUFF.defaultBlockState() : basalt();
        }
        float f = Mth.clamp((surface - shape.baseY()) / (float) shape.height(), 0.0F, 1.0F);
        if (f > 0.85F && n < 0.35F) {
            return n < 0.2F ? Blocks.TUFF.defaultBlockState() : basalt(); // weathered crags near the rim
        }
        if (surface < sea) {
            return depth == 0 ? (n < 0.5F ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState()) : Blocks.DIRT.defaultBlockState();
        }
        return depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.85F ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    /**
     * Lava tubes: one to three caves running down under the flanks from near the crater, the hollow left when
     * the crust of an old lava flow set while the lava inside drained away. Here and there the roof has
     * fallen in, making a skylight.
     */
    private static void carveTubes(WorldGenLevel level, VolcanoShape shape, BlockPos.MutableBlockPos pos, int x, int z, int surface) {
        float dx = x - shape.cx(), dz = z - shape.cz();
        float r = Mth.sqrt(dx * dx + dz * dz);
        float start = shape.craterRadius() + 7.0F, end = shape.radius() * 0.8F;
        if (r < start || r > end) {
            return;
        }
        float theta = (float) Math.atan2(dz, dx);
        int count = 1 + (int) (tubeHash(shape, 1) * 2.99F);
        for (int k = 0; k < count; k++) {
            float along = tubeHash(shape, 100 + k) * Mth.TWO_PI + 0.18F * Mth.sin(r * 0.045F + k * 1.7F);
            float lateral = Math.abs(Mth.wrapDegrees((theta - along) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD * r;
            float radius = 2.6F;
            if (lateral >= radius) continue;
            int lx = Math.round(shape.cx() + Mth.cos(along) * r), lz = Math.round(shape.cz() + Mth.sin(along) * r);
            int lineSurface = shape.surfaceY(lx, lz);
            if (lineSurface == VolcanoShape.OUTSIDE) continue;
            float centre = lineSurface - 6.5F - 1.5F * Mth.sin(r * 0.09F + k);
            float half = Mth.sqrt(radius * radius - lateral * lateral);
            int bottom = Mth.floor(centre - half * 0.75F), top = Mth.ceil(centre + half * 0.9F);
            for (int y = bottom; y <= top; y++) {
                pos.set(x, y, z);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            float n = noise(shape, x, bottom, z);
            pos.set(x, bottom - 1, z);
            level.setBlock(pos, n < 0.05F ? Blocks.MAGMA_BLOCK.defaultBlockState() : n < 0.55F ? Blocks.SMOOTH_BASALT.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
            // a skylight where the roof has collapsed
            float stretch = r % 23.0F;
            if (stretch < 2.5F && lateral < 1.6F && tubeHash(shape, k * 1000 + (int) (r / 23.0F)) < 0.4F) {
                for (int y = top + 1; y <= surface; y++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    private static float tubeHash(VolcanoShape shape, int i) {
        int h = Mth.murmurHash3Mixer((int) (shape.seed() ^ shape.seed() >>> 32) + i * 0x9E3779B9);
        return (h >>> 8) / (float) (1 << 24);
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
