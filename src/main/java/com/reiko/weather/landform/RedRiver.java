package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A red river, like the Rio Tinto in southern Spain: a stream the colour of rust and as sour as vinegar. It rises from a
 * hillside of iron ore, where rain soaking into the rock eats into the sulphide minerals and seeps out again thick with
 * dissolved iron and acid, leaving the slope above as a crumbling, rusty "gossan" of ochre and red and orange earth with
 * lumps of the ore showing through. From the seep the water runs away downhill in a chain of still, blood-red reaches,
 * stepping down over little rapids and falls, to a pool at the bottom. As it goes the iron comes out of the water and
 * stains everything it touches: the bed and the banks are ochre, red and orange, laid down in thin layered terraces where
 * the water spreads out over flat rock, with a pale yellow-ochre band at the waterline. Nothing grows in it or near it.
 *
 * @param course  {x, z} of each point along the stream's line, about 6 blocks apart, from the seep down to the last pool
 * @param reaches {how far along the stream it starts, its water level, 1 where it spreads out thin and wide} for each level
 *                reach, from the head down
 * @param width   how wide the stream is, on average, and {@code depth} how deep its reaches are in the middle
 * @param head    the seep pool's radius, in tenths of a block (and {@code pool}, the last pool's)
 * @param gossan  how far the rusty ground spreads up the hillside round the seep
 * @param top     the highest ground about (for the bounds)
 */
public record RedRiver(int cx, int cz, int[] course, int[] reaches, int width, int depth, int head, int pool, int gossan, int top,
                       long seed) implements Landform {
    /** What a column is: untouched, a bank, a rock or ledge standing in the stream, the stream itself, or one of its pools. */
    private static final int NONE = 0, BANK = 1, LIP = 2, WATER = 3, POOL = 4;
    /** How far the banks reach out beyond the water (nothing grows within {@code DEAD} of it), and how far apart the points of the course are. */
    private static final float BANKS = 6.0F, DEAD = 4.0F, STEP = 6.0F;
    /** How far the stream's line may reach from the middle of the chunk it was planned in. */
    private static final int LIMIT = 88;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 24, 44)) {
            return null;
        }
        float[] down = c.downhill(32);
        if (down[1] < 3) {
            return null;
        }
        // (a steady fall one way across all the ground it would run over, not just a dip here)
        int below = c.groundAt(c.x() + Math.round(Mth.cos(down[0]) * 40.0F), c.z() + Math.round(Mth.sin(down[0]) * 40.0F));
        int above = c.groundAt(c.x() - Math.round(Mth.cos(down[0]) * 40.0F), c.z() - Math.round(Mth.sin(down[0]) * 40.0F));
        if (above - below < 6 || below < c.seaLevel() + 1) {
            return null;
        }
        RandomSource random = c.random();
        long seed = random.nextLong();
        int length = 60 + random.nextInt(61), width = 4 + random.nextInt(6), depth = 1 + random.nextInt(3);
        float widest = width * 0.5F * 1.15F * 1.4F;
        int head = Math.round(Math.max(2.5F + random.nextFloat() * 1.5F, width * 0.5F * 1.15F + 1.0F) * 10.0F);
        int pool = Math.round(Math.max(4.5F + random.nextFloat() * 3.0F, widest + 1.5F) * 10.0F);
        int gossan = 9 + random.nextInt(6);
        float period = 5.0F + random.nextFloat() * 3.0F, phase = random.nextFloat() * Mth.TWO_PI;
        // follow the lie of the land down from here, and back up the other way to the seep on the hillside
        List<int[]> downstream = walk(c, ground, down[0], Math.round(length * 0.62F / STEP), true, period, phase);
        List<int[]> upstream = walk(c, ground, down[0] + Mth.PI, Math.round(length * 0.38F / STEP), false, period, phase + Mth.PI);
        List<int[]> nodes = new ArrayList<>();
        for (int i = upstream.size() - 1; i >= 0; i--) nodes.add(upstream.get(i));
        nodes.add(new int[]{c.x(), c.z(), ground});
        nodes.addAll(downstream);
        if (nodes.size() < 9) {
            return null;
        }
        int[] course = new int[nodes.size() * 2];
        for (int i = 0; i < nodes.size(); i++) {
            course[i * 2] = nodes.get(i)[0];
            course[i * 2 + 1] = nodes.get(i)[1];
        }
        Shape shape = new RedRiver(c.x(), c.z(), course, new int[]{0, 0, 0}, width, depth, head, pool, gossan, 0, seed).shape();
        float total = shape.length;
        if (total < 54.0F) {
            return null;
        }
        // where it steps down from one reach to the next: wherever the ground along it has fallen a block or two below the
        // reach above (nine blocks along it at the least), from below the seep to above the last pool
        float[] ts = new float[nodes.size()];
        for (int i = 0; i < ts.length; i++) ts[i] = shape.near(nodes.get(i)[0] + 0.5F, nodes.get(i)[1] + 0.5F)[0];
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        float first = head / 10.0F + 5.0F, last = total - pool / 10.0F - 5.0F, from = 0.0F;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < ts.length; i++) {
            int g = nodes.get(i)[2], fallen = lowest - g;
            float at = i > 0 ? (ts[i - 1] + ts[i]) / 2.0F : 0.0F, len = at - from;
            if (at >= first && at <= last && (fallen >= 2 && len >= 9.0F || fallen >= 1 && len >= 15.0F)) {
                starts.add(Math.round(at));
                from = at;
                lowest = g;
            } else {
                lowest = Math.min(lowest, g);
            }
        }
        int n = starts.size();
        int[] reaches = new int[n * 3];
        for (int j = 0; j < n; j++) {
            reaches[j * 3] = starts.get(j);
            float len = (j + 1 < n ? starts.get(j + 1) : total) - starts.get(j);
            // (here and there a long reach spreads out thin over a flat of rock, laying down its terraces)
            reaches[j * 3 + 2] = j > 0 && j + 1 < n && len >= 14.0F && random.nextFloat() < 0.4F ? 1 : 0;
        }
        RedRiver r = new RedRiver(c.x(), c.z(), course, reaches, width, depth, head, pool, gossan, 0, seed);
        shape = r.shape();
        // the ground along the stream, beside each reach, and round the seep and the last pool, each {how far along, the ground}
        int[] low = new int[n];
        java.util.Arrays.fill(low, Integer.MAX_VALUE);
        List<float[]> samples = new ArrayList<>();
        for (int i = 0; i < ts.length; i++) {
            int[] p = nodes.get(i);
            int j = r.reach(ts[i]);
            low[j] = Math.min(low[j], p[2]);
            // (a point right by a step counts for the reach above it too)
            if (j > 0 && ts[i] - r.start(j) < 2.5F) low[j - 1] = Math.min(low[j - 1], p[2]);
            samples.add(new float[]{ts[i], p[2]});
        }
        for (int j = 0; j < n; j++) {
            float mid = (r.start(j) + r.end(j)) / 2.0F;
            float[] m = shape.point(mid);
            for (int side = -1; side <= 1; side += 2) {
                float off = side * (r.halfWidth(mid) + 2.0F);
                int g = c.groundAt(Mth.floor(m[0] - m[3] * off), Mth.floor(m[1] + m[2] * off));
                // (the bank may be banked up a block or two where the ground falls away)
                low[j] = Math.min(low[j], g + 2);
                samples.add(new float[]{mid, g});
            }
        }
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + Mth.PI / 4.0F, rh = head / 10.0F + 1.0F, rp = pool / 10.0F + 1.0F;
            int g = c.groundAt(Mth.floor(shape.xs[0] + Mth.cos(a) * rh), Mth.floor(shape.zs[0] + Mth.sin(a) * rh));
            low[0] = Math.min(low[0], g + 1);
            samples.add(new float[]{0.0F, g});
            int e = shape.xs.length - 1;
            g = c.groundAt(Mth.floor(shape.xs[e] + Mth.cos(a) * rp), Mth.floor(shape.zs[e] + Mth.sin(a) * rp));
            low[n - 1] = Math.min(low[n - 1], g + 1);
            samples.add(new float[]{total, g});
        }
        // each reach's water a block under the lowest ground along it, and never above the one upstream; where it has far
        // to fall to the next, it comes down a staircase of little falls cut into the end of the reach above
        List<int[]> steps = new ArrayList<>();
        int upper = Integer.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            int level = Math.min(low[j] - 1, upper), fall = upper - level;
            if (j > 0 && fall > 3) {
                int k = (fall + 2) / 3, at = r.start(j);
                // (rapids and little falls, nothing higher, with room for them in the reach above)
                if (fall > 12 || at - 3 * (k - 1) < (j == 1 ? first : r.start(j - 1) + 4)) return null;
                for (int i = 1; i < k; i++) steps.add(new int[]{at - 3 * (k - i), upper - Math.round(fall * i / (float) k), 0});
            }
            steps.add(new int[]{r.start(j), level, reaches[j * 3 + 2]});
            upper = level;
        }
        reaches = new int[steps.size() * 3];
        for (int j = 0; j < steps.size(); j++) System.arraycopy(steps.get(j), 0, reaches, j * 3, 3);
        int m = steps.size();
        if (reaches[1] - reaches[m * 3 - 2] < 4 || reaches[m * 3 - 2] < c.seaLevel() + 1) {
            return null;
        }
        // and nowhere cut too deep into the slope
        r = new RedRiver(c.x(), c.z(), course, reaches, width, depth, head, pool, gossan, 0, seed);
        int high = Integer.MIN_VALUE;
        for (float[] sm : samples) {
            if (sm[1] - r.levelAt(r.reach(sm[0])) > 13) return null;
            high = Math.max(high, (int) sm[1]);
        }
        // (the gossan on the hillside above the seep)
        float[] up = upstream(course);
        int g = c.groundAt(Mth.floor(up[0] + up[2] * gossan * 0.6F), Mth.floor(up[1] + up[3] * gossan * 0.6F));
        high = Math.max(high, g);
        return new RedRiver(c.x(), c.z(), course, reaches, width, depth, head, pool, gossan, high, seed);
    }

    /**
     * The way water would run from here, a point every few blocks: down the slope (or, the other way, back up it), keeping
     * to the hollows and taking the gentler way rather than the steepest, and swinging from side to side a little as it goes.
     * Each point is {x, z, the ground there}.
     */
    private static List<int[]> walk(PlanContext c, int ground, float heading, int steps, boolean down, float period, float phase) {
        List<int[]> out = new ArrayList<>();
        float x = c.x() + 0.5F, z = c.z() + 0.5F, h = heading;
        int g = ground;
        for (int i = 0; i < steps; i++) {
            float sway = 0.5F * Mth.sin(i * Mth.TWO_PI / period + phase), best = Float.MAX_VALUE, ba = h, bx = x, bz = z;
            int bg = g;
            for (int k = -2; k <= 2; k++) {
                float a = h + k * 0.35F;
                if (Math.abs(Mth.wrapDegrees((a - heading) * Mth.RAD_TO_DEG)) > 65.0F) continue;
                float nx = x + Mth.cos(a + sway) * STEP, nz = z + Mth.sin(a + sway) * STEP;
                int ng = c.groundAt(Mth.floor(nx), Mth.floor(nz));
                // downstream the lowest way on, but not over a drop; upstream the way that rises, but gently
                float score = down ? ng - g + Math.max(0, g - ng - 2) * 1.5F : g - ng + Math.max(0, ng - g - 3) * 1.5F;
                score += Math.abs(k) * 0.4F;
                if (score < best) {
                    best = score;
                    ba = a;
                    bx = nx;
                    bz = nz;
                    bg = ng;
                }
            }
            if (Math.abs(bx - c.x()) > LIMIT || Math.abs(bz - c.z()) > LIMIT) break;
            x = bx;
            z = bz;
            h = ba;
            g = bg;
            out.add(new int[]{Mth.floor(x), Mth.floor(z), g});
        }
        return out;
    }

    static Landform load(CompoundTag tag) {
        return new RedRiver(tag.getInt("x"), tag.getInt("z"), tag.getIntArray("course"), tag.getIntArray("reaches"), tag.getInt("width"), tag.getInt("depth"),
                tag.getInt("head"), tag.getInt("pool"), tag.getInt("gossan"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.put("course", new IntArrayTag(course));
        tag.put("reaches", new IntArrayTag(reaches));
        tag.putInt("width", width);
        tag.putInt("depth", depth);
        tag.putInt("head", head);
        tag.putInt("pool", pool);
        tag.putInt("gossan", gossan);
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RED_RIVER;
    }

    @Override
    public BoundingBox bounds() {
        int low = Integer.MAX_VALUE;
        for (int j = 0; j < count(); j++) low = Math.min(low, levelAt(j));
        // (the smoothed line keeps within its points)
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < course.length; i += 2) {
            minX = Math.min(minX, course[i]);
            maxX = Math.max(maxX, course[i]);
            minZ = Math.min(minZ, course[i + 1]);
            maxZ = Math.max(maxZ, course[i + 1]);
        }
        int r = Mth.ceil(Math.max(width * 0.5F * 1.15F * 1.4F, Math.max(head, pool) / 10.0F * 1.12F) + BANKS) + 3;
        minX -= r;
        maxX += r;
        minZ -= r;
        maxZ += r;
        // and the gossan up the hillside round the seep
        float[] up = upstream(course);
        float gx = up[0] + up[2] * gossan * 0.5F, gz = up[1] + up[3] * gossan * 0.5F, gr = gossan * 1.25F + 2.0F;
        minX = Math.min(minX, Mth.floor(gx - gr));
        maxX = Math.max(maxX, Mth.ceil(gx + gr));
        minZ = Math.min(minZ, Mth.floor(gz - gr));
        maxZ = Math.max(maxZ, Mth.ceil(gz + gr));
        return new BoundingBox(minX, low - depth - 12, minZ, maxX, top + 32, maxZ);
    }

    private int count() {
        return reaches.length / 3;
    }

    private int start(int j) {
        return reaches[j * 3];
    }

    private float end(int j) {
        return j + 1 < count() ? start(j + 1) : shape().length;
    }

    private int levelAt(int j) {
        return reaches[j * 3 + 1];
    }

    private boolean spread(int j) {
        return reaches[j * 3 + 2] == 1;
    }

    /** Which reach a point {@code t} along the stream is in. */
    private int reach(float t) {
        int j = 0;
        while (j + 1 < count() && t >= start(j + 1)) j++;
        return j;
    }

    private float halfWidth(float t) {
        float hw = width * 0.5F * (0.85F + 0.3F * Terrain.field(seed + 5, t, 0.0, 14.0));
        // (wider where it spreads out over a flat, easing in and out of it)
        int j = reach(t);
        if (spread(j)) {
            float in = Math.min(t - start(j), start(j + 1) - t);
            hw = Math.max(hw, Math.min(hw * 1.4F, 5.5F) * Terrain.smooth(in / 5.0F));
        }
        return hw;
    }

    private Shape shape() {
        return new Shape(this);
    }

    /** {x, z, dx, dz}: the seep at the head of a course, and the way back up the hillside from it. */
    private static float[] upstream(int[] course) {
        float dx = course[0] - course[2], dz = course[1] - course[3], l = Math.max(1.0E-4F, Mth.sqrt(dx * dx + dz * dz));
        return new float[]{course[0] + 0.5F, course[1] + 0.5F, dx / l, dz / l};
    }

    /** The highest water that can lie beside a column {@code t} along the stream (or, with {@code below}, the lowest). */
    private int waterNear(float t, float length, boolean below) {
        return levelAt(reach(Mth.clamp(below ? t + 2.0F : t - 2.0F, 0.0F, length)));
    }

    /**
     * The stream's line, smoothed from the points of its course, and what each column near it is (worked out once for
     * each column, and kept while a chunk is built).
     */
    private static final class Shape {
        final RedRiver river;
        final float[] xs, zs, arc;
        final float length;
        final float[] gossanUp;
        private final java.util.Map<Long, int[]> known = new java.util.HashMap<>();

        Shape(RedRiver river) {
            this.river = river;
            gossanUp = upstream(river.course);
            int n = river.course.length / 2;
            float[] x = new float[n], z = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = river.course[i * 2] + 0.5F;
                z[i] = river.course[i * 2 + 1] + 0.5F;
            }
            // (cutting the corners twice over, keeping the ends where they are)
            for (int pass = 0; pass < 2; pass++) {
                int m = x.length;
                float[] x2 = new float[m * 2], z2 = new float[m * 2];
                x2[0] = x[0];
                z2[0] = z[0];
                for (int i = 0; i + 1 < m; i++) {
                    x2[i * 2 + 1] = x[i] * 0.75F + x[i + 1] * 0.25F;
                    z2[i * 2 + 1] = z[i] * 0.75F + z[i + 1] * 0.25F;
                    x2[i * 2 + 2] = x[i] * 0.25F + x[i + 1] * 0.75F;
                    z2[i * 2 + 2] = z[i] * 0.25F + z[i + 1] * 0.75F;
                }
                x2[m * 2 - 1] = x[m - 1];
                z2[m * 2 - 1] = z[m - 1];
                x = x2;
                z = z2;
            }
            xs = x;
            zs = z;
            arc = new float[x.length];
            for (int i = 1; i < x.length; i++) arc[i] = arc[i - 1] + Mth.sqrt(Mth.square(x[i] - x[i - 1]) + Mth.square(z[i] - z[i - 1]));
            length = arc[x.length - 1];
        }

        /** {t, distance}: how far along the stream the nearest point of its line to (x, z) is, and how far away it is. */
        float[] near(float x, float z) {
            float bestD = Float.MAX_VALUE, bestT = 0.0F;
            for (int i = 0; i + 1 < xs.length; i++) {
                float ex = xs[i + 1] - xs[i], ez = zs[i + 1] - zs[i], len = ex * ex + ez * ez;
                float px = x - xs[i], pz = z - zs[i];
                float f = len > 0.0F ? Mth.clamp((px * ex + pz * ez) / len, 0.0F, 1.0F) : 0.0F;
                float dd = Mth.square(px - f * ex) + Mth.square(pz - f * ez);
                if (dd < bestD) {
                    bestD = dd;
                    bestT = arc[i] + f * (arc[i + 1] - arc[i]);
                }
            }
            return new float[]{bestT, Mth.sqrt(bestD)};
        }

        /** {x, z, dx, dz}: the point {@code t} along the stream, and the way it's running there. */
        float[] point(float t) {
            int i = 0;
            while (i + 2 < xs.length && arc[i + 1] < t) i++;
            float seg = Math.max(1.0E-4F, arc[i + 1] - arc[i]), f = Mth.clamp((t - arc[i]) / seg, 0.0F, 1.0F);
            float dx = (xs[i + 1] - xs[i]) / seg, dz = (zs[i + 1] - zs[i]) / seg;
            return new float[]{xs[i] + f * (xs[i + 1] - xs[i]), zs[i] + f * (zs[i + 1] - zs[i]), dx, dz};
        }

        /** How far into a pool centred at (px, pz) a spot is (under 1 inside it). */
        float inPool(int x, int z, float px, float pz, int radius, int salt) {
            return Mth.sqrt(Mth.square(x + 0.5F - px) + Mth.square(z + 0.5F - pz)) / (radius / 10.0F * (0.88F + 0.24F * Terrain.field(river.seed + salt, x, z, 3.0)));
        }

        float inHead(int x, int z) {
            return inPool(x, z, xs[0], zs[0], river.head, 6);
        }

        float inLast(int x, int z) {
            return inPool(x, z, xs[xs.length - 1], zs[zs.length - 1], river.pool, 7);
        }

        /** How far into the gossan a spot is (under 1 inside it): a rusty tongue of ground up the hillside from the seep. */
        float inGossan(int x, int z) {
            float[] up = gossanUp;
            float gx = up[0] + up[2] * river.gossan * 0.5F, gz = up[1] + up[3] * river.gossan * 0.5F;
            float[] l = Terrain.uv(x + 0.5, z + 0.5, gx, gz, (float) Mth.atan2(up[3], up[2]));
            float a = (float) Mth.atan2(l[1], l[0]);
            float r = river.gossan * (0.85F + 0.25F * Terrain.field(river.seed + 20, Math.cos(a) * 4.0, Math.sin(a) * 4.0, 2.5));
            return Mth.sqrt(Mth.square(l[0] / 1.2F) + Mth.square(l[1] / 0.8F)) / r;
        }

        /**
         * What a column is: {kind, level, more, the lowest water that can lie beside it, which reach}. For the stream and its
         * pools the level is the water's, and more how deep it is; for a rock, the level is the height of its top; for a bank,
         * the least height it's built up to (a block over any water beside it), more how far it is from the water, in tenths,
         * and in place of the reach, the highest water that can lie beside it.
         */
        int[] classify(int x, int z) {
            return known.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> work(x, z));
        }

        private int[] work(int x, int z) {
            RedRiver r = river;
            float rh = r.head / 10.0F, rp = r.pool / 10.0F;
            int last = r.count() - 1;
            float dh = inHead(x, z);
            if (dh < 1.0F) return new int[]{POOL, r.levelAt(0), 1 + Math.round(r.depth * Mth.sqrt(1.0F - dh * dh)), r.levelAt(0), 0};
            float dl = inLast(x, z);
            if (dl < 1.0F) return new int[]{POOL, r.levelAt(last), 1 + Math.round(r.depth * Mth.sqrt(1.0F - dl * dl)), r.levelAt(last), last};
            float[] nr = near(x + 0.5F, z + 0.5F);
            float t = nr[0], d = nr[1], hw = r.halfWidth(t);
            if (d < hw) {
                int j = r.reach(t);
                float into = t - r.start(j);
                int step = j > 0 ? r.levelAt(j - 1) - r.levelAt(j) : 0;
                // below a step of a block, the rapids: ledges of stained rock breaking the surface
                if (step == 1 && into < 3.0F && d < hw - 0.6F && Terrain.noise(r.seed + 8, x, r.levelAt(j), z) < 0.3F) {
                    return new int[]{LIP, r.levelAt(j - 1), 0, r.waterNear(t, length, true), j};
                }
                // (where it spreads out, it runs a block deep over flat rock)
                int deep = r.spread(j) ? 1 : 1 + Math.round((r.depth - 1) * Mth.sqrt(Math.max(0.0F, 1.0F - (d / hw) * (d / hw))));
                // and below a fall the water has scoured out a plunge pool
                if (step >= 2 && into < 3.5F) deep += 1;
                return new int[]{WATER, r.levelAt(j), deep, r.waterNear(t, length, true), j};
            }
            float edge = Math.min(Math.min(dh * rh - rh, dl * rp - rp), d - hw);
            if (edge < BANKS) {
                // (a levee raised where the ground falls away slopes off again away from the water)
                int least = r.waterNear(t, length, false) + 1 - Math.max(0, (int) (edge - 1.5F));
                return new int[]{BANK, least, Math.round(edge * 10.0F), r.waterNear(t, length, true), r.waterNear(t, length, false)};
            }
            return new int[]{NONE, 0, 0, 0, 0};
        }

        /**
         * Whether the water can stand in a column at {@code level}: only where every column beside it is certain to be
         * solid or water at that level (a bank or rock at least as high, or water at least as high, which, if higher, will
         * itself be a rock or a fall). Where it can't, the column is a ledge of rock instead or, at the head of a fall, it pours over.
         */
        boolean held(int x, int z, int level) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int[] k = classify(x + d.getStepX(), z + d.getStepZ());
                if (k[0] == NONE || k[1] < level) return false;
            }
            return true;
        }

        /**
         * Whether water that can't stand in a column pours over the edge instead: where every column beside it is solid
         * or water at its level, except at least one that is water two or more blocks below, which it will fall into.
         */
        boolean spout(int x, int z, int level) {
            boolean pours = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                int[] k = classify(nx, nz);
                if (k[0] == NONE) return false;
                if (k[1] >= level) continue;
                if (k[0] < WATER || k[1] > level - 2 || !held(nx, nz, k[1])) return false;
                pours = true;
            }
            return pours;
        }
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        Shape shape = shape();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // (a bank left alone, far above or below the stream, keeps any pond in it)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            int[] k = shape.classify(nx, nz);
            return k[0] == NONE || k[0] == BANK && skipped(Math.max(Goosenecks.ground(level, new BlockPos.MutableBlockPos(), nx, nz), k[1]), b);
        };
        java.util.Set<Integer> wet = new java.util.TreeSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int[] k = shape.classify(x, z);
                if (k[0] == NONE) {
                    if (shape.inGossan(x, z) < 1.0F) stain(level, pos, b, shape, x, z, BANKS + 1.0F, Integer.MIN_VALUE);
                    continue;
                }
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                int lvl = k[1];
                if (k[0] == BANK) {
                    bank(level, pos, b, shape, x, z, ground, standing, lvl, k[3] - depth - 4, k[2] / 10.0F, k[4], outside);
                    continue;
                }
                // sound rock under it all, as deep as the water beside it goes, with no hollow in it for the water to find
                for (int h = Math.max(b.minY(), Math.min(lvl - 8, k[3] - depth - 4)); h <= Math.min(ground, lvl); h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (here.isAir() || !here.getFluidState().isEmpty() || here.getBlock() instanceof FallingBlock) Terrain.set(level, pos, x, h, z, rock(x, h, z));
                }
                boolean water = k[0] >= WATER && shape.held(x, z, lvl), pour = k[0] >= WATER && !water && shape.spout(x, z, lvl);
                int bed = pour ? lvl - 1 : water ? lvl - k[2] : lvl;
                int clear = Math.max(0, Math.min(32, b.maxY() - Math.max(ground, standing)));
                Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                for (int h = lvl + 1; h <= Math.min(b.maxY(), Math.max(ground, standing)); h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
                }
                int j = k[4];
                for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
                    Terrain.set(level, pos, x, h, z, h < bed ? rock(x, h, z) : water || pour ? bed(x, h, z, k, j) : ledge(x, h, z, lvl));
                }
                if (water || pour) {
                    wet.add(lvl);
                    for (int h = bed + 1; h <= lvl; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (pour) level.scheduleTick(pos.set(x, lvl, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
                }
            }
        }
        // the water's rust red, from the bed to a little over it, reach by reach
        for (int w : wet) {
            Terrain.setBiome(level, area, w - depth - 4, w + 3, (x, z) -> {
                int[] k = shape.classify(x, z);
                return k[0] >= LIP && k[1] == w;
            }, Terrain.RED_RIVER_BIOME);
        }
    }

    /**
     * The banks: cut back in a slope from the water, stained in layers of ochre, red and orange with a pale band at the
     * waterline, bare of anything growing near the water, with a few dead bushes further out.
     */
    private void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, Shape shape, int x, int z, int ground, int standing, int least,
                      int deepest, float edge, int water, BiPredicate<Integer, Integer> outside) {
        int top = Math.max(least, Math.min(ground, least + Math.round(edge * 1.3F)));
        if (skipped(top, b)) return; // (high above the stream, or far below it: nothing to do)
        if (top == ground && standing <= ground) {
            // the ground as it lies, only stained and laid bare (with no hollow under it for the water to find)
            for (int h = Math.max(b.minY(), deepest); h < ground; h++) {
                BlockState here = level.getBlockState(pos.set(x, h, z));
                if (here.isAir() || !here.getFluidState().isEmpty() || here.getBlock() instanceof FallingBlock) Terrain.set(level, pos, x, h, z, rock(x, h, z));
            }
            stain(level, pos, b, shape, x, z, edge, water);
            return;
        }
        int clear = Math.max(0, Math.min(32, b.maxY() - Math.max(ground, standing)));
        Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
        for (int h = top + 1; h <= Math.max(ground, standing); h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
        }
        // (solid all down the face the water shows, as deep as the stream beside it goes, in layers where it shows)
        for (int h = Math.max(b.minY(), Math.min(ground + 1, deepest)); h < top; h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            if (h > ground || here.isAir() || !here.getFluidState().isEmpty() || here.getBlock() instanceof FallingBlock) {
                Terrain.set(level, pos, x, h, z, h >= top - 3 ? layer(x, h, z, water) : rock(x, h, z));
            }
        }
        Terrain.set(level, pos, x, top, z, surface(x, top, z, edge, water));
        deadBush(level, pos, b, x, top, z, edge, 0.07F);
    }

    /** Ground left at its own height but stained rusty and laid bare: near the water, and on the gossan round the seep. */
    private void stain(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, Shape shape, int x, int z, float edge, int water) {
        int ground = Goosenecks.ground(level, pos, x, z);
        if (ground + 2 > b.maxY() || ground < b.minY() + 2) return;
        // (a pond standing on it is left be)
        if (Terrain.surface(level, pos, x, ground, z) > ground) return;
        BlockState was = level.getBlockState(pos.set(x, ground, z));
        float in = shape.inGossan(x, z);
        if (in >= 1.0F && edge >= DEAD) {
            // further out, the odd dead bush among what still grows
            deadBush(level, pos, b, x, ground, z, edge, 0.04F);
            return;
        }
        // nothing grows here
        Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, Math.min(32, b.maxY() - ground)));
        if (!stainable(was)) return;
        BlockState top = in < 1.0F ? gossan(x, ground, z, in) : surface(x, ground, z, edge, water);
        // (sand would be left hanging over a hollow: it's set in its place as stone)
        if (top.getBlock() instanceof FallingBlock && !level.getBlockState(pos.set(x, ground - 1, z)).blocksMotion()) top = Blocks.RED_SANDSTONE.defaultBlockState();
        Terrain.set(level, pos, x, ground, z, top);
        if (in < 1.0F && level.getBlockState(pos.set(x, ground - 1, z)).is(BlockTags.DIRT) && Terrain.noise(seed + 21, x, ground - 1, z) < 0.6F) {
            Terrain.set(level, pos, x, ground - 1, z, Blocks.COARSE_DIRT.defaultBlockState());
        }
        if (in < 1.0F) deadBush(level, pos, b, x, ground, z, DEAD, 0.06F);
    }

    /** Whether a block is plain ground the iron can stain: soil, sand, gravel, clay, terracotta or common stone. */
    private static boolean stainable(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.CALCITE);
    }

    /** Now and then a dead bush, out beyond where nothing at all grows, on ground a dead bush can stand on. */
    private void deadBush(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int top, int z, float edge, float chance) {
        if (edge < DEAD || top + 1 > b.maxY() || Terrain.noise(seed + 9, x, top, z) >= chance) return;
        if (bushOn(level.getBlockState(pos.set(x, top, z))) && level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    private static boolean bushOn(BlockState below) {
        return below.is(BlockTags.SAND) || below.is(BlockTags.TERRACOTTA) || below.is(BlockTags.DIRT);
    }

    private static boolean skipped(int top, BoundingBox b) {
        return top > b.maxY() - 3 || top < b.minY() + 1;
    }

    /**
     * The top of a bank: a pale yellow-ochre band right at the waterline, then ochre and red and orange stain, and bare
     * coarse earth and stained rock further back (the soil's own turf only beyond where nothing grows).
     */
    private BlockState surface(int x, int y, int z, float edge, int water) {
        float n = Terrain.noise(seed + 2, x, y, z);
        if (edge < 1.5F) {
            return n < 0.55F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState() : n < 0.8F ? ModBlocks.OCHRE.get().defaultBlockState() : Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        }
        if (edge < 3.0F) {
            return water != Integer.MIN_VALUE && n < 0.6F ? layer(x, y, z, water) : n < 0.75F ? ModBlocks.OCHRE.get().defaultBlockState()
                    : n < 0.88F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.RED_TERRACOTTA.defaultBlockState();
        }
        if (edge < DEAD) {
            return n < 0.45F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.62F ? ModBlocks.OCHRE.get().defaultBlockState()
                    : n < 0.78F ? Blocks.RED_SANDSTONE.defaultBlockState() : n < 0.9F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        }
        return n < 0.55F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.7F ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    /** The iron's stain laid down in thin layers, one colour over another, banded by height over the water. */
    private BlockState layer(int x, int y, int z, int water) {
        int band = Math.floorMod(y - water + (Terrain.noise(seed + 3, x, y, z) < 0.15F ? 1 : 0), 4);
        return switch (band) {
            case 0 -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case 1 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case 2 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            default -> ModBlocks.OCHRE.get().defaultBlockState();
        };
    }

    /** The gossan: crumbling rusty earth, ochre and red and orange, with lumps of the iron ore showing through. */
    private BlockState gossan(int x, int y, int z, float in) {
        float n = Terrain.noise(seed + 4, x, y, z), patch = Terrain.field(seed + 22, x, z, 3.0);
        if (n < 0.02F + 0.03F * (1.0F - in)) return n < 0.005F ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
        if (patch < 0.3F) return Blocks.COARSE_DIRT.defaultBlockState();
        if (patch < 0.5F) return ModBlocks.OCHRE.get().defaultBlockState();
        if (patch < 0.68F) return Blocks.RED_TERRACOTTA.defaultBlockState();
        if (patch < 0.84F) return Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        return n < 0.6F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
    }

    /** The rock under it all, stained right through: red sandstone, ochre and plain terracotta. */
    private BlockState rock(int x, int y, int z) {
        float band = Terrain.field(seed + 7, x * 0.6 + y * 1.3, z * 0.6 - y * 0.4, 3.0);
        return band < 0.3F ? ModBlocks.OCHRE.get().defaultBlockState() : band > 0.75F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.RED_SANDSTONE.defaultBlockState();
    }

    /** A ledge or the lip of a step in the stream: stained rock, in layers. */
    private BlockState ledge(int x, int y, int z, int level) {
        float n = Terrain.noise(seed + 10, x, y, z);
        return n < 0.4F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : n < 0.7F ? ModBlocks.OCHRE.get().defaultBlockState() : Blocks.RED_SANDSTONE.defaultBlockState();
    }

    /**
     * The bed: ochre and red terracotta, with red sandstone; where the water spreads out thin, flat rock laid down in rings
     * of orange and red, like the terraces of an old rice field; and soft ochre in the pools.
     */
    private BlockState bed(int x, int y, int z, int[] k, int j) {
        float n = Terrain.noise(seed + 11, x, y, z);
        if (k[0] == POOL) return n < 0.5F ? ModBlocks.OCHRE.get().defaultBlockState() : n < 0.8F ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        if (spread(j)) {
            float ring = Terrain.field(seed + 12, x, z, 2.5) * 4.0F;
            return switch (Math.floorMod((int) ring, 3)) {
                case 0 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                case 1 -> Blocks.RED_TERRACOTTA.defaultBlockState();
                default -> n < 0.5F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState() : ModBlocks.OCHRE.get().defaultBlockState();
            };
        }
        return n < 0.4F ? ModBlocks.OCHRE.get().defaultBlockState() : n < 0.65F ? Blocks.RED_TERRACOTTA.defaultBlockState()
                : n < 0.85F ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    }
}
