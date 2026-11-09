package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Kettle lakes among kames: when the glaciers retreated they left blocks of dead ice buried in their gravel,
 * which melted out slowly into deep, round, steep-sided ponds. Between them stand kames, mounds of sand and
 * gravel dropped by meltwater streams on (or against) the old ice.
 *
 * @param ponds {x, z, radius, water y} for each kettle
 * @param kames {x, z, radius, height} for each mound
 */
public record KettleLakes(int cx, int cz, int y, int[] ponds, int[] kames, long seed) implements Landform {
    private static final int SPREAD = 38;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(SPREAD / 2, 10) > 10 || !c.fits(ground, 14, 14)) {
            return null;
        }
        int n = 4 + c.random().nextInt(5);
        int[] ponds = new int[n * 4];
        for (int i = 0; i < n; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            int r = 3 + c.random().nextInt(5);
            // the water stands a block under the lowest point of its rim
            int low = Integer.MAX_VALUE;
            for (int k = 0; k < 8; k++) {
                float b = k * Mth.TWO_PI / 8.0F;
                low = Math.min(low, c.groundAt(x + Math.round(Mth.cos(b) * (r + 1)), z + Math.round(Mth.sin(b) * (r + 1))));
            }
            if (low < c.seaLevel()) low = Integer.MIN_VALUE; // never right by the sea
            ponds[i * 4] = x;
            ponds[i * 4 + 1] = z;
            ponds[i * 4 + 2] = low == Integer.MIN_VALUE ? 0 : r;
            ponds[i * 4 + 3] = low == Integer.MIN_VALUE ? 0 : low - 1;
        }
        int m = 2 + c.random().nextInt(3);
        int[] kames = new int[m * 4];
        for (int i = 0; i < m; i++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            kames[i * 4] = c.x() + Math.round(Mth.cos(a) * d);
            kames[i * 4 + 1] = c.z() + Math.round(Mth.sin(a) * d);
            kames[i * 4 + 2] = 7 + c.random().nextInt(6);
            kames[i * 4 + 3] = 4 + c.random().nextInt(4);
        }
        return new KettleLakes(c.x(), c.z(), ground, ponds, kames, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new KettleLakes(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getIntArray("ponds"), tag.getIntArray("kames"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.put("ponds", new IntArrayTag(ponds));
        tag.put("kames", new IntArrayTag(kames));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.KETTLE_LAKES;
    }

    @Override
    public BoundingBox bounds() {
        int r = SPREAD + 16;
        return new BoundingBox(cx - r, y - 22, cz - r, cx + r, y + 24, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                // the nearest kettle (by how far into it this is) and the tallest kame
                int pond = -1;
                float pd = Float.MAX_VALUE;
                for (int i = 0; i < ponds.length / 4; i++) {
                    if (ponds[i * 4 + 2] == 0) continue;
                    float d = Mth.sqrt(Mth.square(x + 0.5F - ponds[i * 4]) + Mth.square(z + 0.5F - ponds[i * 4 + 1]))
                            / (ponds[i * 4 + 2] * (0.9F + 0.2F * Terrain.field(seed + i, x, z, 4.0)));
                    if (d < pd) {
                        pd = d;
                        pond = i;
                    }
                }
                float kame = 0.0F;
                for (int i = 0; i < kames.length / 4; i++) {
                    float d = Mth.sqrt(Mth.square(x + 0.5F - kames[i * 4]) + Mth.square(z + 0.5F - kames[i * 4 + 1])) / kames[i * 4 + 2];
                    if (d < 1.0F) kame = Math.max(kame, kames[i * 4 + 3] * Terrain.smooth(1.0F - d));
                }
                boolean inPond = pond >= 0 && pd < 1.6F;
                if (!inPond && kame < 0.5F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, ground, z);
                if (inPond) {
                    int water = ponds[pond * 4 + 3], r = ponds[pond * 4 + 2];
                    if (pd < 1.0F) {
                        // steep-sided and deep for its size
                        int depth = 2 + Math.round(r * 1.1F * Mth.sqrt(1.0F - pd * pd));
                        Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, 12);
                        for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                        for (int h = Math.min(ground + 1, water - depth - 1); h <= water - depth; h++) {
                            Terrain.set(level, pos, x, h, z, n < 0.5F ? Blocks.MUD.defaultBlockState() : n < 0.8F ? Blocks.GRAVEL.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                        }
                        for (int h = water - depth + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                        if (pd > 0.6F && n < 0.12F) Terrain.set(level, pos, x, water + 1, z, Blocks.LILY_PAD.defaultBlockState());
                        else if (n > 0.85F) Terrain.set(level, pos, x, water - depth + 1, z, Blocks.SEAGRASS.defaultBlockState());
                        continue;
                    }
                    // the steep banks, never lower than a block over the water
                    float t = (pd - 1.0F) / 0.6F;
                    int bank = Math.max(water + 1, Math.round(Mth.lerp(t, water + 1 + r * 0.4F, ground)));
                    if (kame > 0.5F) bank = Math.max(bank, ground + Math.round(kame));
                    if (bank > ground + 6) bank = ground + 6;
                    shape(level, pos, x, z, ground, bank, n);
                    continue;
                }
                shape(level, pos, x, z, ground, ground + Math.round(kame), n);
            }
        }
    }

    /** Sets a column's ground to {@code top}: dug out, or built up with sand and gravel under turf. */
    private static void shape(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, float n) {
        if (top == ground) return;
        Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 12);
        for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        pos.set(x, ground, z);
        BlockState turf = level.getBlockState(pos).is(Blocks.SNOW_BLOCK) ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        for (int h = ground; h < top; h++) Terrain.set(level, pos, x, h, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState());
        if (top > ground) {
            Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
            Terrain.set(level, pos, x, top, z, turf);
            if (n > 0.75F && turf.is(Blocks.GRASS_BLOCK)) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        } else {
            Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
        }
    }
}
