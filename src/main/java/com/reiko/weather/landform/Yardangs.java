package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Yardangs: a fleet of streamlined rock ridges, like upturned boats, all lying the same way across a wind-scoured
 * plain. The wind that blows steadily from one quarter of the desert sandblasts soft, layered rock, carving the
 * channels between the ridges deeper and deeper, until what's left is shaped like the wind itself: a blunt, tall
 * prow facing into it, tapering away to a low tail downwind. Between them, the wind has carried off all the fine
 * sand and left a stony desert pavement.
 *
 * @param y      the level of the scoured plain
 * @param angle  the way the wind blows (from the prows towards the tails)
 * @param ridges {u, v, length, height, half width x 10} for each ridge, along and across the wind from the middle
 */
public record Yardangs(int cx, int cz, int y, float angle, int[] ridges, long seed) implements Landform {
    private static final float ALONG = 46.0F, ACROSS = 40.0F;
    private static final BlockState[] DESERT = {Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState()};
    private static final BlockState[] BADLANDS = {Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
            Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.CUT_RED_SANDSTONE.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState()};
    /** The softer beds (the terracotta), which the wind scoops back further than the sandstone. */
    private static final boolean[] SOFT = {false, false, false, true, false, true, false, false, true};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 16, 20)) {
            return null;
        }
        int sum = ground, min = ground, max = ground;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10.0F;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * 38), c.z() + Math.round(Mth.sin(a) * 38));
            sum += g;
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        if (max - min > 16 || min < c.seaLevel() + 1) {
            return null;
        }
        int n = 5 + c.random().nextInt(6);
        int[] ridges = new int[n * 5];
        float spacing = Mth.clamp(64.0F / n, 7.0F, 12.0F);
        for (int i = 0; i < n; i++) {
            int length = 15 + c.random().nextInt(26);
            float room = (64.0F - length) / 2.0F;
            ridges[i * 5] = Math.round((c.random().nextFloat() * 2.0F - 1.0F) * room);
            ridges[i * 5 + 1] = Math.round((i - (n - 1) / 2.0F) * spacing + (c.random().nextFloat() - 0.5F) * 3.0F);
            ridges[i * 5 + 2] = length;
            // the longer ridges are the taller ones
            ridges[i * 5 + 3] = Mth.clamp(4 + Math.round((length - 15) * 0.25F) + c.random().nextInt(3) - 1, 4, 12);
            ridges[i * 5 + 4] = Math.round(Mth.clamp(length * 0.15F + 1.0F + c.random().nextFloat(), 2.8F, 6.5F) * 10.0F);
        }
        // the wind has scoured the plain a little below the land round about
        return new Yardangs(c.x(), c.z(), Math.round(sum / 11.0F) - 1, c.random().nextFloat() * Mth.TWO_PI, ridges, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Yardangs(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getIntArray("ridges"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.put("ridges", new IntArrayTag(ridges));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.YARDANGS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(ALONG * 1.32F * 1.1F) + 2;
        return new BoundingBox(cx - r, y - 14, cz - r, cx + r, y + 18, cz + r);
    }

    /**
     * How high ridge {@code i} stands above the plain at a point, {@code narrow} blocks slimmer than its full
     * width (the soft beds are cut back further).
     */
    private float ridge(int i, float u, float v, float narrow) {
        float length = ridges[i * 5 + 2];
        float s = (u - ridges[i * 5] + length / 2.0F) / length; // 0 at the blunt prow facing the wind, 1 at the tail
        if (s <= 0.0F || s >= 1.0F) return 0.0F;
        // wide and tall just behind the prow, tapering away downwind
        float half = ridges[i * 5 + 4] / 10.0F * Mth.sqrt(Math.min(1.0F, s / 0.06F)) * (1.0F - 0.7F * s) - narrow;
        if (half <= 0.3F) return 0.0F;
        float q = Math.abs(v - ridges[i * 5 + 1]) / half;
        if (q >= 1.0F) return 0.0F;
        float crest = ridges[i * 5 + 3] * Mth.sqrt(Math.min(1.0F, s / 0.1F)) * Math.min(1.0F, (float) Math.pow((1.0F - s) / 0.88F, 0.65));
        // steep flanks under a rounded keel
        return crest * (float) Math.pow(1.0F - Math.pow(q, 2.2), 0.55);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = ridges.length / 5;
        int offset = Math.round(Terrain.hash(seed, 1) * DESERT.length);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                float e = Mth.sqrt(Mth.square(u / ALONG) + Mth.square(v / ACROSS)) / (0.92F + 0.16F * Terrain.field(seed, x, z, 16.0));
                if (e > 1.3F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 3 || ground > b.maxY() - 4) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                BlockState[] bands = red ? BADLANDS : DESERT;
                // the scoured plain, blending back up (or down) to the land round it
                int floor = e < 1.0F ? y : Math.round(Mth.lerp(Terrain.smooth((e - 1.0F) / 0.3F), y, ground));
                float hard = 0.0F, soft = 0.0F, tail = Float.MAX_VALUE;
                for (int i = 0; i < count; i++) {
                    hard = Math.max(hard, ridge(i, u, v, 0.0F));
                    soft = Math.max(soft, ridge(i, u, v, 0.7F));
                    float s = (u - ridges[i * 5] + ridges[i * 5 + 2] / 2.0F) / ridges[i * 5 + 2];
                    if (s > 0.9F && s < 1.4F) tail = Math.min(tail, Math.abs(v - ridges[i * 5 + 1]));
                }
                if (e >= 1.0F && floor == ground && hard < 0.5F) continue;
                int top = Math.min(b.maxY() - 1, floor + Math.round(hard));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(10, b.maxY() - Math.max(ground, top)));
                for (int h = Math.max(floor, top) + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = Math.min(ground, floor) - 2; h <= top; h++) {
                    int band = Math.floorMod(h + offset + Math.round(Terrain.field(seed + 2, x, z, 20.0) * 1.6F), bands.length);
                    if (h > floor && SOFT[band] && h - floor > soft) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState()); // undercut along the softer beds
                        continue;
                    }
                    Terrain.set(level, pos, x, h, z, bands[band]);
                }
                if (hard >= 0.5F) continue;
                // the desert pavement: a stony crust the wind has picked clean of sand, with sand caught behind the tails
                float p = Terrain.field(seed + 3, x, z, 3.5);
                float n = Terrain.noise(seed, x, floor, z);
                BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
                BlockState pave;
                if (e >= 1.05F || tail < 1.6F) {
                    pave = sand;
                } else {
                    pave = n > 0.93F ? Blocks.COBBLESTONE.defaultBlockState() : p < 0.4F ? Blocks.GRAVEL.defaultBlockState()
                            : p < 0.64F ? Blocks.COARSE_DIRT.defaultBlockState() : p < 0.76F ? Blocks.COBBLESTONE.defaultBlockState() : sand;
                }
                Terrain.set(level, pos, x, floor, z, pave);
                if (n < 0.012F && !pave.is(Blocks.COBBLESTONE) && !pave.is(Blocks.GRAVEL)) {
                    Terrain.set(level, pos, x, floor + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
