package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A volcanic neck (or plug), like Devils Tower: a great stump of dark rock standing sheer out of the plains. It's the
 * lava that cooled and set solid in the throat of a volcano, or in a blister of magma that never broke the surface;
 * as it cooled it shrank and cracked into tall, many-sided columns. The softer rock round it has long since worn
 * away, and the columns that break off the walls lie heaped in a skirt of rubble round its foot. Its top is gently
 * rounded and grassed over.
 *
 * @param y the foot of the tower: the lowest ground round it
 */
public record VolcanicNeck(int cx, int cz, int y, int radius, int height, long seed) implements Landform {
    private static final float CELL = 2.4F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3) {
            return null;
        }
        int radius = 10 + c.random().nextInt(9);
        int height = 35 + c.random().nextInt(26);
        if (!c.fits(ground, 8, height + 16)) {
            return null;
        }
        int low = ground, high = ground;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * 1.5F), c.z() + Math.round(Mth.sin(a) * radius * 1.5F));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        if (high - low > 14) {
            return null;
        }
        return new VolcanicNeck(c.x(), c.z(), low, radius, height, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new VolcanicNeck(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("h"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("h", height);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.VOLCANIC_NECK;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.95F) + 6;
        return new BoundingBox(cx - r, y - 6, cz - r, cx + r, y + height + 16, cz + r);
    }

    /** The column a point falls in, as {cell x, cell z, centre x, centre z}: the nearest of a jittered grid of centres. */
    private float[] cell(float px, float pz) {
        int gx = Mth.floor(px / CELL), gz = Mth.floor(pz / CELL);
        float best = Float.MAX_VALUE;
        float[] out = new float[4];
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int ci = gx + i, cj = gz + j;
                float ox = (ci + 0.15F + 0.7F * Terrain.noise(seed, ci, 1, cj)) * CELL, oz = (cj + 0.15F + 0.7F * Terrain.noise(seed, ci, 2, cj)) * CELL;
                float d = Mth.square(ox - px) + Mth.square(oz - pz);
                if (d < best) {
                    best = d;
                    out[0] = ci;
                    out[1] = cj;
                    out[2] = ox;
                    out[3] = oz;
                }
            }
        }
        return out;
    }

    /** The tower's radius at a height: flaring at the foot, then near-sheer, drawing in a little toward the top. */
    private float radiusAt(float h) {
        float t = (h - y) / height;
        return radius * (1.04F - 0.12F * t + 0.25F * Mth.square(Math.max(0.0F, 1.0F - t / 0.25F)));
    }

    /** The top of column (i, k): the gently domed summit, with columns broken off short round its edge. */
    private int cellTop(int i, int k, float dc) {
        float dome = 3.0F * Math.max(0.0F, 1.0F - Mth.square(dc / radius));
        float broken = Terrain.noise(seed, i, 8, k) * 5.0F * Terrain.smooth((dc / radius - 0.7F) / 0.3F);
        return y + height + Math.round(dome - broken);
    }

    private static BlockState column(float r) {
        return r < 0.45F ? Blocks.BASALT.defaultBlockState() : r < 0.7F ? Blocks.POLISHED_BASALT.defaultBlockState()
                : r < 0.9F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    /** The scree: broken lengths of column every which way, with grit between. */
    private BlockState rubble(int x, int h, int z) {
        float n = Terrain.noise(seed + 5, x, h, z);
        if (n < 0.32F) {
            Direction.Axis axis = n < 0.11F ? Direction.Axis.X : n < 0.22F ? Direction.Axis.Z : Direction.Axis.Y;
            return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        }
        if (n < 0.52F) return Blocks.BLACKSTONE.defaultBlockState();
        if (n < 0.67F) return Blocks.SMOOTH_BASALT.defaultBlockState();
        if (n < 0.8F) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        if (n < 0.87F) return Blocks.TUFF.defaultBlockState();
        return Blocks.GRAVEL.defaultBlockState();
    }

    /** A length of column lying on the scree where it fell, or null: {start x, start z, along x?, length (signed)}. */
    private int[] fallen(int k) {
        float a = Terrain.hash(seed, 20 + k) * Mth.TWO_PI, d = radius * (1.2F + 0.45F * Terrain.hash(seed, 30 + k));
        boolean alongX = Math.abs(Mth.cos(a)) > Math.abs(Mth.sin(a));
        int length = 3 + Math.round(Terrain.hash(seed, 40 + k) * 2.0F);
        int sign = (alongX ? Mth.cos(a) : Mth.sin(a)) > 0 ? 1 : -1;
        return new int[]{cx + Math.round(Mth.cos(a) * d), cz + Math.round(Mth.sin(a) * d), alongX ? 1 : 0, length * sign};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float apronW = radius * 0.9F + 4.0F, apronH = height * 0.18F + 2.0F;
        int pieces = 4 + Math.round(Terrain.hash(seed, 3) * 2.0F);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                float d = Mth.sqrt(Mth.square(px - cx) + Mth.square(pz - cz));
                float t = 1.0F - (d - radius * 0.95F) / apronW;
                if (t <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 4) continue;
                // the tower, built column by column: each column stands or not as a whole, so its outline follows them
                float[] cl = cell(px, pz);
                int ci = (int) cl[0], ck = (int) cl[1];
                float dc = Mth.sqrt(Mth.square(cl[2] - cx) + Mth.square(cl[3] - cz));
                float jitter = (Terrain.noise(seed, ci, 7, ck) - 0.5F) * 2.4F;
                int ctop = cellTop(ci, ck, dc), hmax = Integer.MIN_VALUE;
                if (dc < radius * 1.3F + 2.0F) {
                    for (int h = ctop; h >= y - 2; h--) {
                        if (dc < radiusAt(h) + jitter) {
                            hmax = h;
                            break;
                        }
                    }
                }
                // the apron of fallen columns heaped round the foot
                int apron = y + Math.round(apronH * (float) Math.pow(Math.min(1.0F, t), 1.7) + (Terrain.field(seed + 6, x, z, 4.0) - 0.5F) * 2.5F);
                int top = Math.max(hmax, apron);
                if (top <= ground && hmax == Integer.MIN_VALUE) continue;
                Holder<Biome> biome = level.getBiome(pos.set(x, ground, z));
                boolean dry = biome.is(BiomeTags.IS_BADLANDS) || biome.is(Biomes.DESERT);
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(16, b.maxY() - Math.max(ground, top)));
                BlockState col = column(Terrain.noise(seed, ci, 9, ck));
                boolean soil = hmax == ctop && dc < radius * 0.8F;
                for (int h = Math.max(b.minY(), Math.min(ground, y) - 3); h <= top; h++) {
                    BlockState s;
                    if (h <= hmax) {
                        if (soil && h == hmax) s = dry ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                        else if (soil && h == hmax - 1) s = Blocks.DIRT.defaultBlockState();
                        else s = col;
                    } else if (h > ground) {
                        s = h == top && t < 0.4F && !dry && Terrain.noise(seed, x, 4, z) < 0.5F ? Blocks.GRASS_BLOCK.defaultBlockState() : rubble(x, h, z);
                    } else {
                        continue;
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                float n = Terrain.noise(seed, x, 5, z);
                pos.set(x, top, z);
                BlockState surface = level.getBlockState(pos);
                if (surface.is(Blocks.GRASS_BLOCK) && n < 0.4F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (surface.is(Blocks.COARSE_DIRT) && n < 0.06F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (hmax < top) {
                    // a broken length of column lying on the scree
                    for (int k = 0; k < pieces; k++) {
                        int[] f = fallen(k);
                        int along = f[2] == 1 ? x - f[0] : z - f[1];
                        boolean on = f[2] == 1 ? z == f[1] : x == f[0];
                        if (on && (f[3] > 0 ? along >= 0 && along < f[3] : along <= 0 && along > f[3])) {
                            Terrain.set(level, pos, x, top + 1, z, Blocks.POLISHED_BASALT.defaultBlockState()
                                    .setValue(RotatedPillarBlock.AXIS, f[2] == 1 ? Direction.Axis.X : Direction.Axis.Z));
                            break;
                        }
                    }
                }
            }
        }
    }
}
