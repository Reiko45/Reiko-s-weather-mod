package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A canyon: a broad, deep trench a river has sawn down through flat-lying beds of rock as the land slowly rose under
 * it. Hard sandstones stand out as sheer cliffs and the softer mudstones between them weather back into slopes of
 * rubble, so the walls climb in great steps. Rainwater trickling over the cliffs for thousands of years has left desert
 * varnish, dark streaks of manganese and iron oxide, running down their faces. The river that cut it now winds across
 * the floor in a chain of quiet pools between gravel bars.
 *
 * @param width the canyon's half-width {@code depth} blocks above its floor
 * @param rim   the highest ground sampled along it (for the bounds)
 * @param floor the floor, at evenly spaced points from the upstream end down
 */
public record Canyon(int cx, int cz, float angle, int length, int width, int depth, int rim, int[] floor, long seed) implements Landform {
    private static final int CONTROL = 16;
    /** How far from each end the canyon is at its full depth (and the river runs). */
    private static final float FADE = 30.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 14 || !c.fits(ground, 60, 40)) {
            return null;
        }
        int length = 150 + c.random().nextInt(41);
        int width = 13 + c.random().nextInt(10);
        int depth = 25 + c.random().nextInt(21);
        long seed = c.random().nextLong();
        // run it the way the land falls
        float angle = c.downhill(length / 2)[0];
        int[] floor = new int[CONTROL + 1];
        Canyon probe = new Canyon(c.x(), c.z(), angle, length, width, depth, ground, floor, seed);
        int previous = Integer.MAX_VALUE, rim = ground, shallow = Integer.MAX_VALUE, deep = 0;
        for (int i = 0; i <= CONTROL; i++) {
            float u = (i / (float) CONTROL - 0.5F) * length;
            double[] p = probe.point(u);
            int h = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            if (h < c.seaLevel() + 4) return null; // never out to the sea or through a lake
            rim = Math.max(rim, h);
            // never climbing as it goes downstream, and never dropping so steeply the pools can't step down it
            previous = Math.max(Math.min(previous, h - depth), i == 0 ? Integer.MIN_VALUE : floor[i - 1] - 3);
            floor[i] = Math.max(previous, c.seaLevel() - 12);
            if (i >= 2 && i <= CONTROL - 2) {
                shallow = Math.min(shallow, h - floor[i]);
                deep = Math.max(deep, h - floor[i]);
            }
        }
        // deep enough all the way to be a canyon, and not cut through a mountain
        if (shallow < 18 || deep > depth + 30) {
            return null;
        }
        return new Canyon(c.x(), c.z(), angle, length, width, depth, rim, floor, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Canyon(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getInt("depth"),
                tag.getInt("rim"), tag.getIntArray("floor"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("depth", depth);
        tag.putInt("rim", rim);
        tag.put("floor", new IntArrayTag(floor));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CANYON;
    }

    @Override
    public BoundingBox bounds() {
        // (out past the ends, and the walls, far enough to seal in any pool the cut comes up against)
        float half = length / 2.0F + 2.0F, out = width * 1.3F + 14.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-half, half}) {
            for (float v : new float[]{-out, out}) {
                double[] w = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        int low = Integer.MAX_VALUE;
        for (int f : floor) low = Math.min(low, f);
        return new BoundingBox(minX, low - 12, minZ, maxX, rim + 40, maxZ);
    }

    /** How far the canyon's middle line swings aside, {@code u} blocks downstream of the middle. */
    private float wiggle(float u) {
        return 7.0F * Mth.sin(u * 0.035F + Terrain.hash(seed, 1) * Mth.TWO_PI) + 3.0F * Mth.sin(u * 0.09F + Terrain.hash(seed, 2) * Mth.TWO_PI);
    }

    private double[] point(float u) {
        return Terrain.xz(u, wiggle(u), cx, cz, angle);
    }

    /** Along the canyon and across it (from its winding middle line). */
    private float[] local(double x, double z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        l[1] -= wiggle(l[0]);
        return l;
    }

    private float floorAt(float u) {
        float t = Mth.clamp((u / length + 0.5F) * CONTROL, 0.0F, CONTROL);
        int i = Math.min(CONTROL - 1, (int) t);
        return Mth.lerp(t - i, floor[i], floor[i + 1]);
    }

    /** Half the width of the flat floor. */
    private float floorHalf(float u) {
        return 5.0F + 1.5F * Mth.sin(u * 0.05F + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** How far across the floor the river has swung, and its distance from it. */
    private float river(float u, float v) {
        float off = (floorHalf(u) - 3.0F) * Mth.sin(u * 0.09F + Terrain.hash(seed, 4) * Mth.TWO_PI);
        return Math.abs(v - off);
    }

    /** Whether a column is part of the floor in the stretch where it lies at its planned level. */
    private boolean floorColumn(float u, float v) {
        return Math.abs(u) < length / 2.0F - FADE - 2.0F && Math.abs(v) < floorHalf(u);
    }

    /**
     * How deep the river stands at a column (0 if it's dry). A pool only fills where every column beside it is floor
     * at least as high, so each pool is held in by the floor round it and the bar at its downstream end.
     */
    private int wet(int x, int z) {
        float[] l = local(x + 0.5, z + 0.5);
        if (!floorColumn(l[0], l[1]) || river(l[0], l[1]) > 1.2F) return 0;
        int bed = Math.round(floorAt(l[0]));
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] s : sides) {
            float[] n = local(x + s[0] + 0.5, z + s[1] + 0.5);
            if (!floorColumn(n[0], n[1]) || Math.round(floorAt(n[0])) < bed) return 0;
        }
        return river(l[0], l[1]) < 0.55F ? 2 : 1;
    }

    /** The beds of rock from {@code base} up: which bed each block of height is in (odd beds soft, even beds hard). */
    private int[] beds(int span) {
        int[] beds = new int[span];
        int k = 0, left = 0;
        for (int i = 0; i < span; i++) {
            if (left == 0) {
                k++;
                left = k % 2 == 0 ? 3 + Math.round(Terrain.hash(seed, 100 + k) * 4.0F) : 2 + Math.round(Terrain.hash(seed, 100 + k) * 2.0F);
            }
            beds[i] = k;
            left--;
        }
        return beds;
    }

    /** Half the width of the open canyon at height {@code y} over a column whose floor is {@code bed}. */
    private float halfWidth(float u, int y, int bed, float[] cum, int base) {
        int i = Mth.clamp(y - base, 0, cum.length - 1), b0 = Mth.clamp(bed - base, 0, cum.length - 1);
        float span = cum[Mth.clamp(bed + depth - base, 0, cum.length - 1)] - cum[b0];
        float fh = floorHalf(u) * (0.4F + 0.6F * Terrain.smooth((length / 2.0F - Math.abs(u)) / FADE));
        return Math.min(fh + (width - fh) * (cum[i] - cum[b0]) / Math.max(1.0F, span), width * 1.3F);
    }

    /** The top of a column after cutting (its ground if the canyon doesn't reach it). */
    private int cut(float u, float v, int ground, int bed, float[] cum, int base) {
        for (int y = bed + 1; y <= ground; y++) {
            if (v < halfWidth(u, y, bed, cum, base)) return y - 1;
        }
        return ground;
    }

    private int bedAt(float u, int ground) {
        float ends = Terrain.smooth((length / 2.0F - Math.abs(u)) / FADE);
        return Math.round(Mth.lerp(ends, ground - 1, floorAt(u)));
    }

    private BlockState rock(int bed, boolean red) {
        float n = Terrain.hash(seed, 200 + bed);
        if (bed % 2 == 0) {
            // the hard sandstones that stand as cliffs
            return red ? n < 0.6F ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()
                    : n < 0.6F ? Blocks.SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        }
        // the soft mudstones and siltstones between them
        if (red) {
            return n < 0.3F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : n < 0.5F ? Blocks.RED_TERRACOTTA.defaultBlockState()
                    : n < 0.65F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : n < 0.8F ? Blocks.TERRACOTTA.defaultBlockState()
                    : n < 0.9F ? Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState();
        }
        return n < 0.35F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : n < 0.55F ? Blocks.WHITE_TERRACOTTA.defaultBlockState()
                : n < 0.75F ? Blocks.TERRACOTTA.defaultBlockState() : n < 0.9F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState()
                : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        int base = b.minY(), span = b.maxY() - base + 1;
        int[] beds = beds(span);
        float[] cum = new float[span];
        int[] bedTop = new int[span];
        for (int i = 1; i < span; i++) cum[i] = cum[i - 1] + (beds[i - 1] % 2 == 0 ? 0.25F : 1.7F);
        for (int i = span - 1; i >= 0; i--) bedTop[i] = i == span - 1 || beds[i + 1] != beds[i] ? i : bedTop[i + 1];
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = local(x + 0.5, z + 0.5);
                float u = l[0], v = Math.abs(l[1]);
                if (Math.abs(u) > half + 1.5F || v > width * 1.3F + 3.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 12) continue;
                boolean middle = floorColumn(u, l[1]);
                if (ground < b.minY() + 4 && !middle) continue;
                int bed = bedAt(u, ground);
                int top = middle ? bed : Math.abs(u) > half ? ground : cut(u, v, ground, bed, cum, base);
                // anything standing on the column: a pool, or a little lava lake
                int wetTop = ground;
                while (wetTop < b.maxY() - 12 && !level.getFluidState(pos.set(x, wetTop + 1, z)).isEmpty()) wetTop++;
                if (top >= ground && !middle) {
                    // left standing, but any water or lava in it that the cut beside it would open is turned to rock
                    int low = wetTop;
                    for (int[] s : sides) {
                        float[] o = local(x + s[0] + 0.5, z + s[1] + 0.5);
                        // (taking the bed beside it as low as it could be, if that column's ground is a little lower)
                        if (Math.abs(o[0]) <= half) low = Math.min(low, cut(o[0], Math.abs(o[1]), wetTop, bedAt(o[0], ground - 4), cum, base));
                    }
                    for (int h = Math.max(b.minY(), low + 1); h <= wetTop; h++) {
                        if (!level.getFluidState(pos.set(x, h, z)).isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.SANDSTONE.defaultBlockState());
                    }
                    continue;
                }
                boolean red = level.getBiome(pos.set(x, Math.max(ground, top), z)).is(BiomeTags.IS_BADLANDS);
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(10, b.maxY() - ground));
                for (int h = top + 1; h <= wetTop; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                float n = Terrain.noise(seed, x, top, z);
                if (top <= bed) {
                    floor(level, pos, b, x, z, u, l[1], ground, bed, middle, red, n, beds, base);
                    continue;
                }
                // the walls: bare rock in its beds, as deep as the face the next column down leaves open
                int low = top;
                for (int[] s : sides) {
                    float[] o = local(x + s[0] + 0.5, z + s[1] + 0.5);
                    low = Math.min(low, cut(o[0], Math.abs(o[1]), ground, bedAt(o[0], ground), cum, base));
                }
                int side = l[1] > 0 ? 1 : 0;
                // desert varnish: dark streaks hanging down the cliffs from where the water runs over their lips
                boolean streak = Terrain.noise(seed + 11, Math.round(u * 0.8F), side, 0) > 0.55F;
                int streakLength = 2 + Math.round(Terrain.noise(seed + 12, Math.round(u * 0.8F), side, 0) * 9.0F);
                for (int h = Math.min(low, top - 2); h <= top; h++) {
                    pos.set(x, h, z);
                    if (level.getBlockState(pos).isAir()) continue; // a cave opening in the wall
                    int i = Mth.clamp(h - base, 0, span - 1);
                    int k = beds[i];
                    BlockState s = rock(k, red);
                    // (only on the open face: the blocks higher than the next column down)
                    if (h > low && k % 2 == 0 && streak && bedTop[i] - i < streakLength) {
                        float d = Terrain.noise(seed + 13, x, h, z);
                        s = d < 0.45F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : d < 0.8F ? Blocks.BLACK_TERRACOTTA.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    } else if (h == top && k % 2 == 1 && n < 0.35F && !level.getBlockState(pos.set(x, h - 1, z)).isAir()) {
                        // loose rubble lying on the soft slopes
                        s = n < 0.25F ? red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                if (Terrain.noise(seed + 9, x, top, z) < 0.9F) continue;
                pos.set(x, top, z);
                if (level.getBlockState(pos).is(BlockTags.SAND)) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
        }
    }

    /** The canyon floor: a sandy flat with the river's pools winding across it, and rocks fallen from the cliffs. */
    private void floor(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, int ground, int bed,
                       boolean middle, boolean red, float n, int[] beds, int base) {
        BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
        BlockState hard = rock(beds[Mth.clamp(bed - base, 0, beds.length - 1)] & ~1, red);
        // solid rock under the floor (filling any hollow or cave beneath it, so the pools can't drain away)
        for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 3)); h < bed; h++) Terrain.set(level, pos, x, h, z, hard);
        int wet = middle ? wet(x, z) : 0;
        float r = river(u, v);
        if (wet > 0) {
            Terrain.set(level, pos, x, bed - wet, z, n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : n < 0.85F ? sand : Blocks.CLAY.defaultBlockState());
            for (int h = bed - wet + 1; h <= bed; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            return;
        }
        // gravel bars where the river runs dry, and along the old wash at either end
        BlockState top = r < 1.6F || Terrain.field(seed + 5, x, z, 5.0) > 0.72F ? Blocks.GRAVEL.defaultBlockState() : sand;
        Terrain.set(level, pos, x, bed, z, top);
        if (!middle) return;
        boolean byWater = false;
        for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            // (a pool beside it, at its own level: one a step lower is no use to cane)
            if (wet(x + s[0], z + s[1]) > 0 && Math.round(floorAt(local(x + s[0] + 0.5, z + s[1] + 0.5)[0])) == bed) byWater = true;
        }
        if (byWater && top.is(BlockTags.SAND) && n > 0.55F) {
            // reeds of cane along the pools
            int tall = 1 + Math.round(Terrain.noise(seed + 6, x, bed, z) * 2.0F);
            for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, bed + h, z, Blocks.SUGAR_CANE.defaultBlockState());
        } else if (!byWater && n > 0.985F) {
            Terrain.set(level, pos, x, bed + 1, z, hard); // a block fallen from the cliffs
        } else if (!byWater && top.is(BlockTags.SAND) && n < 0.012F) {
            Terrain.set(level, pos, x, bed + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }
}
