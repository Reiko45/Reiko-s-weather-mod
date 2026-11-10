package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Aiguilles (French for "needles"): a cluster of slender, sharp granite spires standing up off a high ridge. Frost
 * made them: water seeps into the joints of the granite, freezes and prises them a little wider, night after night,
 * until the blocks between them fall away and only thin pinnacles are left, far too steep to hold anything but a
 * little snow on their ledges. At their feet lies a snowfield, fed by all the snow that slides off the rock.
 *
 * @param low    the lowest ground round about, the foot of the snowfield
 * @param angle  the line of the ridge
 * @param spires {x, z, base y, height, base radius in tenths, lean in tenths along x, along z} for each spire
 */
public record Aiguilles(int cx, int cz, int low, float angle, int[] spires, long seed) implements Landform {
    private static final int STRIDE = 7;
    /** How far out from the ridge the snowfield at the foot of the spires reaches. */
    private static final float FIELD = 11.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 40) {
            return null;
        }
        // a ridge: falling away to one side, and not under a cliff on the other (if it is, step up onto its crest)
        float[] down = c.downhill(14);
        float fall = down[0];
        int ox = Math.round(Mth.cos(fall) * FIELD), oz = Math.round(Mth.sin(fall) * FIELD), x0 = c.x(), z0 = c.z();
        int above = c.groundAt(x0 - ox, z0 - oz);
        if (above > ground + 5) {
            x0 -= ox;
            z0 -= oz;
            ground = above;
            above = c.groundAt(x0 - ox, z0 - oz);
            if (above > ground + 5) return null;
        }
        int below = c.groundAt(x0 + ox, z0 + oz);
        float angle = fall + Mth.HALF_PI;
        int n = 4 + c.random().nextInt(5);
        float spacing = 5.0F + c.random().nextFloat() * 3.0F;
        int[] spires = new int[n * STRIDE];
        int lo = Math.min(ground, Math.min(below, above)), hi = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            float u = (i - (n - 1) / 2.0F) * spacing + (c.random().nextFloat() - 0.5F) * 2.0F, v = (c.random().nextFloat() - 0.5F) * 4.0F;
            double[] p = Terrain.xz(u, v, x0 + 0.5, z0 + 0.5, angle);
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]), base = c.groundAt(x, z);
            // the tallest in the middle of the cluster
            float middle = 1.0F - Math.abs(i - (n - 1) / 2.0F) / n;
            int o = i * STRIDE;
            spires[o] = x;
            spires[o + 1] = z;
            spires[o + 2] = base;
            spires[o + 3] = 15 + Math.round((10.0F + 10.0F * c.random().nextFloat()) * middle);
            spires[o + 4] = 15 + c.random().nextInt(16);
            spires[o + 5] = Math.round((c.random().nextFloat() - 0.5F) * 24.0F);
            spires[o + 6] = Math.round((c.random().nextFloat() - 0.5F) * 24.0F);
            lo = Math.min(lo, base);
            hi = Math.max(hi, base);
            top = Math.max(top, base + spires[o + 3]);
        }
        if (hi - lo > 30 || !c.fits(lo, 8, top - lo + 4)) {
            return null;
        }
        return new Aiguilles(x0, z0, lo, angle, spires, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Aiguilles(tag.getInt("x"), tag.getInt("z"), tag.getInt("low"), tag.getFloat("angle"), tag.getIntArray("spires"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("low", low);
        tag.putFloat("angle", angle);
        tag.put("spires", new IntArrayTag(spires));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.AIGUILLES;
    }

    /** How far along the ridge the cluster runs either side of the middle. */
    private float span() {
        float span = 0.0F;
        for (int o = 0; o < spires.length; o += STRIDE) {
            span = Math.max(span, Math.abs(Terrain.uv(spires[o] + 0.5, spires[o + 1] + 0.5, cx + 0.5, cz + 0.5, angle)[0]));
        }
        return span;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(span() + FIELD + 8.0F), top = low;
        for (int o = 0; o < spires.length; o += STRIDE) top = Math.max(top, spires[o + 2] + spires[o + 3] + 2);
        return new BoundingBox(cx - r, low - 4, cz - r, cx + r, top, cz + r);
    }

    /** Whether spire {@code i} fills a block {@code h} above its base: tapering, ledged where its joints are, to a single-block tip. */
    private boolean spire(int i, int x, int h, int z) {
        int o = i * STRIDE, height = spires[o + 3];
        if (h > height) return false;
        float t = Math.max(0.0F, h) / height;
        double ax = spires[o] + 0.5 + spires[o + 5] / 10.0 * t, az = spires[o + 1] + 0.5 + spires[o + 6] / 10.0 * t;
        if (h >= height - 3) return Mth.floor(ax) == x && Mth.floor(az) == z;
        // ledges and notches where the rock is jointed, the same all round at each level
        float joint = (Terrain.noise(seed + i, 0, h / 3, 0) - 0.5F) * 0.9F * (1.0F - t);
        float r = spires[o + 4] / 10.0F * (float) Math.pow(1.0F - t, 0.85) + 0.5F + joint;
        double dx = x + 0.5 - ax, dz = z + 0.5 - az;
        return dx * dx + dz * dz <= r * r;
    }

    /** Whether a column is anywhere near spire {@code i}. */
    private boolean near(int i, int x, int z) {
        int o = i * STRIDE;
        float reach = spires[o + 4] / 10.0F + 3.5F;
        return Math.abs(x - spires[o]) <= reach && Math.abs(z - spires[o + 1]) <= reach;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float span = span();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                // the snowfield: along the foot of the spires, ragged at its edges
                float over = Math.max(0.0F, Math.abs(l[0]) - span) / 8.0F;
                float sf = 1.0F - Mth.sqrt(Mth.square(l[1] / (FIELD * (0.75F + 0.5F * Terrain.field(seed + 1, x, z, 7.0)))) + over * over);
                boolean any = false;
                for (int i = 0; i < spires.length / STRIDE && !any; i++) any = near(i, x, z);
                if (!any && sf <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (and under any water, which leaves overhanging it can hide from Terrain.ground)
                while (ground > b.minY() && !level.getFluidState(pos.set(x, ground, z)).isEmpty()) ground--;
                if (ground < b.minY() + 1 || ground > b.maxY() - 4) continue;
                pos.set(x, ground, z);
                boolean cold = level.getBiome(pos).value().coldEnoughToSnow(pos.above());
                int rock = Integer.MIN_VALUE;
                if (any) rock = spires(level, pos, b, x, z, ground, cold);
                if (rock == Integer.MIN_VALUE && sf > 0.0F) snowfield(level, pos, b, x, z, ground, sf, cold);
            }
        }
    }

    /** The spires' rock in a column (and snow on their ledges); returns the highest block, or MIN_VALUE if none. */
    private int spires(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground, boolean cold) {
        int top = Integer.MIN_VALUE;
        boolean cleared = false;
        for (int i = 0; i < spires.length / STRIDE; i++) {
            if (!near(i, x, z)) continue;
            int o = i * STRIDE, base = spires[o + 2];
            // rooted in the ridge: where the ground falls away under its foot, the rock goes on down to meet it
            boolean foot = spire(i, x, 0, z);
            int from = foot ? Math.min(ground + 1, base) - base : 1;
            for (int h = from; h <= spires[o + 3]; h++) {
                int y = base + h;
                if (y > b.maxY() - 2 || h > 0 && !spire(i, x, h, z)) continue;
                if (!cleared) {
                    Terrain.clearAbove(level, pos, x, ground, z, Math.min(64, b.maxY() - ground));
                    cleared = true;
                }
                // granite, banded with diorite and grey stone along the joints
                float f = Terrain.field(seed + 3, x * 2.0 + y, z * 2.0 - y, 4.0);
                BlockState s = f < 0.6F ? Blocks.GRANITE.defaultBlockState() : f < 0.82F ? Blocks.STONE.defaultBlockState() : Blocks.DIORITE.defaultBlockState();
                Terrain.set(level, pos, x, y, z, s);
                top = Math.max(top, y);
                // snow lying on each ledge, wherever there's open air over the rock
                if (cold && h > 0 && !spire(i, x, h + 1, z) && y + 1 <= b.maxY()) {
                    pos.set(x, y + 1, z);
                    if (level.getBlockState(pos).isAir()) {
                        float n = Terrain.noise(seed + 4, x, y, z);
                        if (n < 0.8F) level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(n * 3.0F)), Terrain.FLAGS);
                    }
                }
            }
        }
        return top;
    }

    /** The snowfield at the spires' feet: a few blocks of old, packed snow (or, where it's too mild for that, scree). */
    private void snowfield(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground, float sf, boolean cold) {
        if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) return;
        float n = Terrain.noise(seed + 5, x, 0, z);
        Terrain.clearAbove(level, pos, x, ground, z, Math.min(6, b.maxY() - ground));
        if (!cold) {
            if (n < 0.6F) Terrain.set(level, pos, x, ground, z, (n < 0.3F ? Blocks.GRAVEL : n < 0.45F ? Blocks.ANDESITE : Blocks.COBBLESTONE).defaultBlockState());
            return;
        }
        int depth = Math.min(3, Math.round(3.0F * Terrain.smooth(sf / 0.6F) * (0.5F + 0.5F * Terrain.field(seed + 6, x, z, 5.0))));
        for (int h = 1; h <= depth; h++) Terrain.set(level, pos, x, ground + h, z, Blocks.SNOW_BLOCK.defaultBlockState());
        if (n < 0.7F) Terrain.set(level, pos, x, ground + depth + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(n * 4.0F) % 4));
    }
}
