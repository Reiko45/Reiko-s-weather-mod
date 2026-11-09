package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * An oasis: where groundwater comes up in a hollow among the dunes, a pool of clear water ringed with grass,
 * reeds and palm trees, fading out into the sand.
 */
public record Oasis(int cx, int cz, int y, int radius, long seed) implements Landform {
    private static final float OUTER = 2.2F;

    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 7 + c.random().nextInt(5);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(radius, 8) > 9 || !c.fits(ground, 8, 20)) {
            return null;
        }
        int low = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            low = Math.min(low, c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius)));
        }
        // the hollow sits down among the dunes
        return new Oasis(c.x(), c.z(), Math.max(c.seaLevel() + 1, low - 1), radius, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Oasis(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.OASIS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * OUTER) + 6;
        return new BoundingBox(cx - r, y - 6, cz - r, cx + r, y + 24, cz + r);
    }

    /** Distance from the middle in pool radii, with a wavy shoreline. */
    private float distance(double x, double z) {
        double dx = x - cx, dz = z - cz;
        float a = (float) Math.atan2(dz, dx);
        float wobble = 1.0F + 0.18F * Mth.sin(a * 3.0F + Terrain.hash(seed, 1) * 6.0F) + 0.08F * Mth.sin(a * 7.0F + Terrain.hash(seed, 2) * 6.0F);
        return (float) Math.sqrt(dx * dx + dz * dz) / (radius * wobble);
    }

    /** Where the palms stand, round the water. */
    private int[][] palms() {
        int count = 4 + (int) (Terrain.hash(seed, 3) * 4);
        java.util.List<int[]> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            float a = (i + 0.6F * Terrain.hash(seed, 40 + i)) * Mth.TWO_PI / count;
            float r = radius * (0.72F + 0.5F * Terrain.hash(seed, 50 + i));
            int px = cx + Math.round(Mth.cos(a) * r), pz = cz + Math.round(Mth.sin(a) * r);
            if (distance(px + 0.5, pz + 0.5) >= 0.62F) list.add(new int[]{px, pz, i});
        }
        return list.toArray(new int[0][]);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] palms = palms();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = distance(x + 0.5, z + 0.5);
                if (d > OUTER) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, 0, z);
                if (d < 0.55F) {
                    // the pool, deepest in the middle
                    int depth = d < 0.25F ? 3 : d < 0.42F ? 2 : 1;
                    Terrain.clearAbove(level, pos, x, Math.min(ground, y), z, 20);
                    for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground, y - depth) - 2; h < y - depth + 1; h++) {
                        Terrain.set(level, pos, x, h, z, h == y - depth ? (n < 0.5F ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState())
                                : Blocks.SANDSTONE.defaultBlockState());
                    }
                    for (int h = y - depth + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (n > 0.93F && d < 0.45F) Terrain.set(level, pos, x, y + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    continue;
                }
                // the ground slopes gently up from the water's edge into the dunes
                float t = Terrain.smooth((d - 0.55F) / (OUTER - 0.55F));
                // right at the water's edge the bank is level with the water (so reeds can grow)
                int surface = d < 0.66F ? y : Math.round(Mth.lerp(t * t, y + 1, Math.max(ground, y + 1)));
                for (int[] p : palms) {
                    if (Math.abs(p[0] - x) <= 1 && Math.abs(p[1] - z) <= 1) surface = y + 1; // a level spot for each palm
                }
                Terrain.clearAbove(level, pos, x, Math.min(ground, surface), z, 12);
                for (int h = surface + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = Math.min(ground, surface) - 2; h < surface; h++) Terrain.set(level, pos, x, h, z, Blocks.SAND.defaultBlockState());
                // green near the water, patchy further out, sand beyond
                float green = 1.0F - Terrain.smooth((d - 0.9F) / 0.9F) + (Terrain.field(seed, x, z, 4.0) - 0.5F) * 0.6F;
                if (green < 0.45F) {
                    Terrain.set(level, pos, x, surface, z, Blocks.SAND.defaultBlockState());
                    // where the spring seeps out under the sand at the water's edge, it turns to quicksand
                    if (Terrain.quicksand() && d < 0.8F && surface <= y + 1 && Terrain.field(seed + 9, x, z, 3.0) > 0.7F) {
                        Terrain.set(level, pos, x, surface, z, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                        Terrain.set(level, pos, x, surface - 1, z, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                        continue;
                    }
                    if (n > 0.985F) Terrain.set(level, pos, x, surface + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    continue;
                }
                Terrain.set(level, pos, x, surface - 1, z, Blocks.DIRT.defaultBlockState());
                Terrain.set(level, pos, x, surface, z, n < 0.15F ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                if (surface == y && n > 0.5F) {
                    // reeds along the shore
                    int h = 2 + (int) (n * 10) % 2;
                    for (int k = 1; k <= h; k++) Terrain.set(level, pos, x, surface + k, z, Blocks.SUGAR_CANE.defaultBlockState());
                } else if (n > 0.8F) {
                    tall(level, pos, x, surface + 1, z, Blocks.TALL_GRASS.defaultBlockState());
                } else if (n > 0.55F) {
                    Terrain.set(level, pos, x, surface + 1, z, n > 0.72F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
        // palms round the water (each is built a chunk at a time, standing on its level spot)
        for (int[] p : palms) {
            Palms.grow(level, area, pos, p[0], y + 1, p[1], seed ^ (p[2] * 0x9E3779B97F4A7C15L));
        }
    }

    private static void tall(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState plant) {
        Terrain.set(level, pos, x, y, z, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        Terrain.set(level, pos, x, y + 1, z, plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
