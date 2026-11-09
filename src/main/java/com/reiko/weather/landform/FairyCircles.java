package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Fairy circles: bare, round patches of sand dotted through dry grassland by the thousand, spaced so evenly they
 * fall into a rough honeycomb, each ringed by grass growing taller than the rest. Where rain is this scarce, the
 * grass tussocks compete for every drop: each bare circle is a little reservoir whose water is drawn off by the
 * grass round its rim, and the pattern sorts itself out until every patch has its fair share.
 *
 * @param y       the level of the ground in the middle
 * @param spacing how far apart the circles are
 */
public record FairyCircles(int cx, int cz, int y, float angle, int spacing, long seed) implements Landform {
    private static final float RADIUS = 40.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        // only on fairly flat ground: the pattern is the work of water soaking in, not running off
        if (ground < c.seaLevel() + 1 || c.relief(30, 8) > 10 || !c.fits(ground, 10, 10)) {
            return null;
        }
        return new FairyCircles(c.x(), c.z(), ground, c.random().nextFloat() * Mth.PI, 9 + c.random().nextInt(4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new FairyCircles(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("spacing"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("spacing", spacing);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.FAIRY_CIRCLES;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(RADIUS * 1.15F) + 4;
        return new BoundingBox(cx - r, y - 10, cz - r, cx + r, y + 12, cz + r);
    }

    /** How far out in the patch of grassland a point is (under 1 inside it). */
    private float extent(double x, double z) {
        return (float) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) / (RADIUS * (0.85F + 0.3F * Terrain.field(seed, x, z, 16.0)));
    }

    /** The circle at a point of the honeycomb, if there is one there: {x, z, radius}. */
    @Nullable
    private float[] circle(int i, int j) {
        int id = (i + 200) * 401 + (j + 200);
        // a honeycomb, each circle nudged a little out of place
        float u = (i + j * 0.5F) * spacing + (Terrain.hash(seed, id * 4) - 0.5F) * spacing * 0.24F;
        float v = j * spacing * 0.8660254F + (Terrain.hash(seed, id * 4 + 1) - 0.5F) * spacing * 0.24F;
        double[] p = Terrain.xz(u, v, cx, cz, angle);
        // fewer and fewer of them towards the edge of the patch
        float e = extent(p[0], p[1]);
        if (Terrain.hash(seed, id * 4 + 2) < 0.08F + 0.92F * Terrain.smooth((e - 0.65F) / 0.3F)) return null;
        return new float[]{(float) p[0], (float) p[1], 1.5F + 1.5F * Terrain.hash(seed, id * 4 + 3)};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState lower = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        float row = spacing * 0.8660254F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = extent(x + 0.5, z + 0.5);
                if (e > 1.0F) continue;
                // the nearest circle
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                int j0 = Mth.floor(l[1] / row), i0 = Mth.floor(l[0] / spacing - j0 * 0.5F);
                float best = Float.MAX_VALUE, radius = 0.0F;
                for (int dj = -1; dj <= 2; dj++) {
                    for (int di = -2; di <= 2; di++) {
                        float[] c = circle(i0 + di, j0 + dj);
                        if (c == null) continue;
                        float d = Mth.sqrt(Mth.square(x + 0.5F - c[0]) + Mth.square(z + 0.5F - c[1]));
                        if (d - c[2] < best - radius) {
                            best = d;
                            radius = c[2];
                        }
                    }
                }
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 5) continue;
                pos.set(x, ground, z);
                BlockState top = level.getBlockState(pos);
                if (!(top.is(BlockTags.DIRT) || top.is(BlockTags.SAND)) || !level.getFluidState(pos.move(0, 1, 0)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, ground, z);
                if (best < radius) {
                    // the bare circle: loose sand where nothing grows (a few coarse crumbs of soil at the rim)
                    Terrain.clearAbove(level, pos, x, ground, z, 4);
                    Terrain.set(level, pos, x, ground, z, best > radius - 0.8F && n > 0.7F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    Terrain.set(level, pos, x, ground - 1, z, Blocks.SAND.defaultBlockState());
                    continue;
                }
                Terrain.set(level, pos, x, ground, z, Blocks.GRASS_BLOCK.defaultBlockState());
                pos.set(x, ground + 1, z);
                if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(BlockTags.REPLACEABLE)) continue;
                boolean rim = best < radius + 1.6F;
                pos.set(x, ground + 2, z);
                boolean room = level.getBlockState(pos).isAir();
                // the grass round each circle grows taller on the water it draws from it
                if ((rim ? n < 0.75F : n > 0.965F) && room) {
                    Terrain.set(level, pos, x, ground + 1, z, lower);
                    Terrain.set(level, pos, x, ground + 2, z, upper);
                } else if (rim || n < 0.45F) {
                    Terrain.set(level, pos, x, ground + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
