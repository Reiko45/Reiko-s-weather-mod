package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Tors: castles of bare granite on a hilltop, stacked like heaps of rounded cushions. Deep underground, water
 * seeping down the joints in the granite rotted it to grit, while the solid blocks between the cracks stayed
 * whole; when frost and meltwater stripped the rotten rock away, the blocks were left standing in piles on the
 * summit, split by horizontal and vertical joints. Frost keeps prising them apart, and the broken blocks lie
 * strewn down the slopes as a field of boulders called clitter.
 *
 * @param y    the summit's ground
 * @param low  the lowest ground sampled round the hill (for the bounds)
 * @param tors {x, z, radius x 10, height, base, angle x 100} for each stack
 */
public record Tors(int cx, int cz, int y, int low, int[] tors, long seed) implements Landform {
    private static final int CLITTER = 30;
    private static final int CELL = 5;
    private static final BlockState[] BOULDERS = {Blocks.GRANITE.defaultBlockState(), Blocks.GRANITE.defaultBlockState(),
            Blocks.GRANITE.defaultBlockState(), Blocks.POLISHED_GRANITE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int x = c.x(), z = c.z();
        int h = c.groundAt(x, z);
        if (h < c.seaLevel() + 3 || !c.fits(h, 20, 20)) {
            return null;
        }
        // walk uphill to the top of the hill
        for (int step = 0; step < 2; step++) {
            int bx = x, bz = z, bh = h;
            for (int i = 0; i < 6; i++) {
                float a = i * Mth.TWO_PI / 6.0F + step * 0.5F;
                int px = x + Math.round(Mth.cos(a) * 10), pz = z + Math.round(Mth.sin(a) * 10);
                int ph = c.groundAt(px, pz);
                if (ph > bh) {
                    bx = px;
                    bz = pz;
                    bh = ph;
                }
            }
            if (bh == h) break;
            x = bx;
            z = bz;
            h = bh;
        }
        // a real hilltop: nothing much higher round about, and the land falling away from it at least a little
        int low = h, high = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int g = c.groundAt(x + Math.round(Mth.cos(a) * 22), z + Math.round(Mth.sin(a) * 22));
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high > h + 2 || h - low < 3 || h - low > 32 || !c.fits(h, 40, 20)) {
            return null;
        }
        int n = 2 + c.random().nextInt(3);
        int[] tors = new int[n * 6];
        int placed = 0;
        for (int attempt = 0; attempt < n * 4 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = 2.0F + c.random().nextFloat() * 10.0F;
            int tx = x + Math.round(Mth.cos(a) * d), tz = z + Math.round(Mth.sin(a) * d);
            float r = 2.5F + c.random().nextFloat() * 2.5F;
            boolean clear = true;
            for (int k = 0; k < placed; k++) {
                float dd = Mth.sqrt(Mth.square(tx - tors[k * 6]) + Mth.square(tz - tors[k * 6 + 1]));
                if (dd < r + tors[k * 6 + 2] / 10.0F + 2.0F) clear = false;
            }
            if (!clear) continue;
            tors[placed * 6] = tx;
            tors[placed * 6 + 1] = tz;
            tors[placed * 6 + 2] = Math.round(r * 10.0F);
            // the broadest stacks stand the tallest
            tors[placed * 6 + 3] = Mth.clamp(Math.round(r * 1.6F) + c.random().nextInt(4), 4, 10);
            tors[placed * 6 + 4] = c.groundAt(tx, tz);
            tors[placed * 6 + 5] = Math.round(c.random().nextFloat() * Mth.PI * 100.0F);
            placed++;
        }
        if (placed < 2) {
            return null;
        }
        return new Tors(x, z, h, low, java.util.Arrays.copyOf(tors, placed * 6), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Tors(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("low"), tag.getIntArray("tors"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("low", low);
        tag.put("tors", new IntArrayTag(tors));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TORS;
    }

    @Override
    public BoundingBox bounds() {
        int top = y;
        for (int i = 0; i < tors.length; i += 6) top = Math.max(top, tors[i + 4] + tors[i + 3]);
        int r = CLITTER + 4;
        return new BoundingBox(cx - r, low - 12, cz - r, cx + r, top + 4, cz + r);
    }

    /** Whether stack {@code i} has rock at a block: courses of rounded blocks, each a little smaller than the one below. */
    private boolean rock(int i, int x, int by, int z) {
        int o = i * 6;
        float r = tors[o + 2] / 10.0F, angle = tors[o + 5] / 100.0F;
        int height = tors[o + 3], k = Math.max(0, by - tors[o + 4]);
        if (k >= height) return false;
        // which course (a layer of blocks between two horizontal joints) this is in
        int course = 0, start = 0, thick;
        while (true) {
            thick = 2 + (Terrain.hash(seed, i * 64 + course) < 0.45F ? 1 : 0);
            if (k < start + thick || start + thick >= height) break;
            start += thick;
            course++;
        }
        thick = Math.min(thick, height - start);
        int j = k - start;
        float shrink = 1.0F - 0.4F * start / height;
        float ox = (Terrain.hash(seed, i * 64 + 8 + course) - 0.5F) * 1.4F, oz = (Terrain.hash(seed, i * 64 + 16 + course) - 0.5F) * 1.4F;
        float a = angle + (Terrain.hash(seed, i * 64 + 24 + course) - 0.5F) * 0.5F;
        float half1 = r * shrink * (0.85F + 0.3F * Terrain.hash(seed, i * 64 + 32 + course));
        float half2 = r * shrink * (0.65F + 0.3F * Terrain.hash(seed, i * 64 + 40 + course));
        // the joints are worn into grooves, and the top of the stack is rounded off
        float inset = 0.0F;
        if (j == 0 && course > 0) inset = 0.6F;
        if (j == thick - 1 && start + thick >= height) inset = Math.max(inset, 1.1F);
        half1 -= inset;
        half2 -= inset;
        if (half1 < 0.4F || half2 < 0.4F) return false;
        float[] l = Terrain.uv(x + 0.5, z + 0.5, tors[o] + ox, tors[o + 1] + oz, a);
        float p = Math.abs(l[0]) / half1, q = Math.abs(l[1]) / half2;
        float d = p * p * p + q * q * q; // a rounded-off square, like a weathered block
        if (d > 1.0F) return false;
        // a vertical joint splitting the course, opened up towards the outside
        if (Terrain.hash(seed, i * 64 + 48 + course) < 0.7F) {
            float jp = (Terrain.hash(seed, i * 64 + 56 + course) - 0.5F) * half1;
            if (Math.abs(l[0] - jp) < 0.45F && d > 0.3F) return false;
        }
        return true;
    }

    /** The clitter boulder (if any) in a cell of the grid round the summit: {x, z, radius, height, rock}. */
    @Nullable
    private float[] boulder(int ci, int cj) {
        int id = 1000 + (ci + 64) * 131 + (cj + 64);
        float px = cx + (ci + Terrain.hash(seed, id * 8 + 1)) * CELL, pz = cz + (cj + Terrain.hash(seed, id * 8 + 2)) * CELL;
        float d = Mth.sqrt(Mth.square(px - cx) + Mth.square(pz - cz));
        if (d > CLITTER) return null;
        // few on the very top, most on the slopes just below it
        float p = 0.7F * Terrain.smooth((d - 4.0F) / 6.0F) * (1.0F - Terrain.smooth((d - 20.0F) / 10.0F));
        for (int i = 0; i < tors.length / 6; i++) {
            float dt = Mth.sqrt(Mth.square(px - tors[i * 6]) + Mth.square(pz - tors[i * 6 + 1]));
            float r = tors[i * 6 + 2] / 10.0F;
            if (dt < r + 0.8F) return null;
            if (dt < r + 6.0F) p = Math.max(p, 0.85F); // blocks toppled off the stack lie round its foot
        }
        if (Terrain.hash(seed, id * 8) > p) return null;
        float size = Terrain.hash(seed, id * 8 + 3);
        float radius = 0.6F + 1.3F * size * size;
        int height = radius < 1.0F ? 1 : 1 + Math.round(Terrain.hash(seed, id * 8 + 4));
        return new float[]{px, pz, radius, height, Terrain.hash(seed, id * 8 + 5) * BOULDERS.length};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = tors.length / 6;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dc = Mth.sqrt(Mth.square(x + 0.5F - cx) + Mth.square(z + 0.5F - cz));
                if (dc > CLITTER + 2) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 6) continue;
                boolean stack = false;
                float apron = Float.MAX_VALUE;
                for (int i = 0; i < count; i++) {
                    float r = tors[i * 6 + 2] / 10.0F;
                    float dt = Mth.sqrt(Mth.square(x + 0.5F - tors[i * 6]) + Mth.square(z + 0.5F - tors[i * 6 + 1]));
                    apron = Math.min(apron, dt - r);
                    if (dt > r + 1.5F) continue;
                    int base = tors[i * 6 + 4], top = base + tors[i * 6 + 3];
                    boolean any = false;
                    for (int h = Math.max(b.minY(), Math.min(ground, base) - 1); h < top; h++) {
                        if (!rock(i, x, h, z)) continue;
                        if (!any) {
                            Terrain.clearAbove(level, pos, x, Math.min(ground, h), z, top - Math.min(ground, h) + 3);
                            any = true;
                        }
                        // the weathered faces on top are paler and smoother
                        boolean open = !rock(i, x, h + 1, z);
                        float n = Terrain.noise(seed, x, h, z);
                        Terrain.set(level, pos, x, h, z, open && n < 0.5F || n < 0.08F ? Blocks.POLISHED_GRANITE.defaultBlockState() : Blocks.GRANITE.defaultBlockState());
                    }
                    stack |= any;
                }
                if (stack) continue;
                pos.set(x, ground, z);
                BlockState surface = level.getBlockState(pos);
                boolean soil = surface.is(BlockTags.DIRT) || surface.is(Blocks.SNOW_BLOCK) || surface.is(BlockTags.SAND) || surface.is(Blocks.GRAVEL);
                // grit and bare rock round the foot of the stacks, where the rotten granite has washed out
                if (apron < 2.5F && soil) {
                    float n = Terrain.noise(seed, x, 1, z);
                    if (n < 0.75F) {
                        Terrain.clearAbove(level, pos, x, ground, z, 3);
                        Terrain.set(level, pos, x, ground, z, n < 0.3F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.55F ? Blocks.GRAVEL.defaultBlockState()
                                : Blocks.GRANITE.defaultBlockState());
                    }
                }
                // the clitter: boulders strewn down the slopes
                int ci = Math.floorDiv(x - cx, CELL), cj = Math.floorDiv(z - cz, CELL);
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        float[] bo = boulder(ci + di, cj + dj);
                        if (bo == null) continue;
                        float d = Mth.sqrt(Mth.square(x + 0.5F - bo[0]) + Mth.square(z + 0.5F - bo[1]));
                        if (d >= bo[2]) continue;
                        int height = d < bo[2] * 0.6F ? Math.round(bo[3]) : 1;
                        BlockState rock = BOULDERS[Math.min(BOULDERS.length - 1, (int) bo[4])];
                        Terrain.clearAbove(level, pos, x, ground, z, height + 3);
                        // half sunk into the ground
                        for (int h = ground; h <= ground + height; h++) Terrain.set(level, pos, x, h, z, rock);
                    }
                }
            }
        }
    }
}
