package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A boiling river, like the Shanay-Timpishka in the Peruvian Amazon: a jungle stream so hot that it steams all day long
 * and scalds anything that falls in. Rain falling on hills far off soaks down along faults deep into the earth, where the
 * hot rock heats it, and wells up again at a spring at the river's head, close to boiling. From there the stream winds
 * away down through the forest in a chain of quiet, milky green-blue reaches, stepping down from one to the next over
 * little rapids and falls, and spreads out at last into a broad pool. The ground round the spring is baked warm, stained
 * orange and crusted with yellow sulphur; the banks steam, and the ferns and jungle plants only crowd in a little way back
 * from the scalding water.
 *
 * @param course  {x, z} of each point along the stream's line, about 6 blocks apart, from the spring down to the last pool
 * @param reaches {how far along the stream it starts, its water level} for each level reach of the stream, from the head down
 * @param width   how wide the stream is, on average
 * @param depth   how deep its reaches are in the middle
 * @param spring  the spring pool's radius, in tenths of a block (and {@code pool}, the last pool's)
 * @param top     the highest ground about (for the bounds)
 */
public record BoilingRiver(int cx, int cz, int[] course, int[] reaches, int width, int depth, int spring, int pool, int top,
                           long seed) implements Landform {
    /** What a column is: untouched, a bank, a rock or sill standing in the stream, the stream itself, or one of its pools. */
    private static final int NONE = 0, BANK = 1, LIP = 2, WATER = 3, POOL = 4;
    /** How far the banks reach out beyond the water, and how far apart the points of the course are. */
    private static final float BANKS = 5.0F, STEP = 6.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 24, 44)) {
            return null;
        }
        float[] down = c.downhill(32);
        if (down[1] < 2) {
            return null;
        }
        // (a fall one way across the whole of the ground it would run over, not just a dip here)
        int below = c.groundAt(c.x() + Math.round(Mth.cos(down[0]) * 40.0F), c.z() + Math.round(Mth.sin(down[0]) * 40.0F));
        int above = c.groundAt(c.x() - Math.round(Mth.cos(down[0]) * 40.0F), c.z() - Math.round(Mth.sin(down[0]) * 40.0F));
        if (above - below < 4) {
            return null;
        }
        long seed = c.random().nextLong();
        int length = 60 + c.random().nextInt(51), width = 5 + c.random().nextInt(6), depth = 2 + c.random().nextInt(3);
        float widest = width * 0.5F * 1.15F;
        int spring = Math.round(Math.max(3.5F + c.random().nextFloat() * 2.0F, widest + 1.5F) * 10.0F);
        int pool = Math.round(Math.max(4.5F + c.random().nextFloat() * 3.0F, widest + 2.0F) * 10.0F);
        float period = 5.0F + c.random().nextFloat() * 3.0F, phase = c.random().nextFloat() * Mth.TWO_PI;
        // follow the lie of the land down from here, and back up the other way to where the spring rises
        int steps = Math.round(length / 2.0F / STEP);
        List<int[]> downstream = walk(c, ground, down[0], steps, true, period, phase);
        List<int[]> upstream = walk(c, ground, down[0] + Mth.PI, steps, false, period, phase + Mth.PI);
        List<int[]> nodes = new ArrayList<>();
        for (int i = upstream.size() - 1; i >= 0; i--) nodes.add(upstream.get(i));
        nodes.add(new int[]{c.x(), c.z(), ground});
        nodes.addAll(downstream);
        int[] course = new int[nodes.size() * 2];
        for (int i = 0; i < nodes.size(); i++) {
            course[i * 2] = nodes.get(i)[0];
            course[i * 2 + 1] = nodes.get(i)[1];
        }
        Shape shape = new BoilingRiver(c.x(), c.z(), course, new int[]{0, 0}, width, depth, spring, pool, 0, seed).shape();
        float total = shape.length;
        // where it steps down from one reach to the next: wherever the ground along it has fallen a block or two below the
        // reach above (a reach of a dozen blocks at the least), from below the spring to above the last pool
        float[] ts = new float[nodes.size()];
        for (int i = 0; i < ts.length; i++) ts[i] = shape.near(nodes.get(i)[0] + 0.5F, nodes.get(i)[1] + 0.5F)[0];
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        float first = spring / 10.0F + 5.0F, last = total - pool / 10.0F - 5.0F, from = 0.0F;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < ts.length; i++) {
            int g = nodes.get(i)[2], fallen = lowest - g;
            float at = i > 0 ? (ts[i - 1] + ts[i]) / 2.0F : 0.0F, len = at - from;
            if (at >= first && at <= last && (fallen >= 2 && len >= 9.0F || fallen >= 1 && len >= 16.0F)) {
                starts.add(Math.round(at));
                from = at;
                lowest = g;
            } else {
                lowest = Math.min(lowest, g);
            }
        }
        int n = starts.size();
        int[] reaches = new int[n * 2];
        for (int j = 0; j < n; j++) reaches[j * 2] = starts.get(j);
        BoilingRiver r = new BoilingRiver(c.x(), c.z(), course, reaches, width, depth, spring, pool, 0, seed);
        shape = r.shape();
        // the ground along the stream, beside each reach, and round the spring and the last pool
        int[] low = new int[n];
        java.util.Arrays.fill(low, Integer.MAX_VALUE);
        List<int[]> samples = new ArrayList<>();
        for (int i = 0; i < ts.length; i++) {
            int[] p = nodes.get(i);
            float t = ts[i];
            int j = r.reach(t);
            low[j] = Math.min(low[j], p[2]);
            // (a point right by a step counts for the reach above it too)
            if (j > 0 && t - r.start(j) < 2.5F) low[j - 1] = Math.min(low[j - 1], p[2]);
            samples.add(new int[]{j, p[2]});
        }
        // (beside the stream, and round its pools, the bank may be banked up a block or two where the ground falls away)
        for (int j = 0; j < n; j++) {
            float[] m = shape.point((r.start(j) + r.end(j)) / 2.0F);
            for (int side = -1; side <= 1; side += 2) {
                float off = side * (width / 2.0F + 2.0F);
                int g = c.groundAt(Mth.floor(m[0] - m[3] * off), Mth.floor(m[1] + m[2] * off));
                low[j] = Math.min(low[j], g + 2);
                samples.add(new int[]{j, g});
            }
        }
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + Mth.PI / 4.0F, rs = spring / 10.0F + 1.0F, rp = pool / 10.0F + 1.0F;
            int g = c.groundAt(Mth.floor(shape.xs[0] + Mth.cos(a) * rs), Mth.floor(shape.zs[0] + Mth.sin(a) * rs));
            low[0] = Math.min(low[0], g + 1);
            samples.add(new int[]{0, g});
            int e = shape.xs.length - 1;
            g = c.groundAt(Mth.floor(shape.xs[e] + Mth.cos(a) * rp), Mth.floor(shape.zs[e] + Mth.sin(a) * rp));
            low[n - 1] = Math.min(low[n - 1], g + 1);
            samples.add(new int[]{n - 1, g});
        }
        // each reach's water a block under the lowest ground along it, and never above the one upstream
        for (int j = 0; j < n; j++) {
            int level = low[j] - 1;
            if (j > 0) {
                level = Math.min(level, reaches[j * 2 - 1]);
                // (falls of a few blocks at most: any steeper and it isn't a gentle jungle slope)
                if (reaches[j * 2 - 1] - level > 7) return null;
            }
            reaches[j * 2 + 1] = level;
        }
        if (reaches[1] - reaches[n * 2 - 1] < 5 || reaches[n * 2 - 1] < c.seaLevel()) {
            return null;
        }
        // and nowhere cut too deep into the slope
        int high = Integer.MIN_VALUE;
        for (int[] s : samples) {
            if (s[1] - reaches[s[0] * 2 + 1] > 11) return null;
            high = Math.max(high, s[1]);
        }
        return new BoilingRiver(c.x(), c.z(), course, reaches, width, depth, spring, pool, high, seed);
    }

    /**
     * The way a stream would run from here, a point every few blocks: down the slope (or, the other way, up it), keeping
     * to the hollows, and taking the gentle way down rather than the steep one, winding a little as it goes.
     * Each point is {x, z, the ground there}.
     */
    private static List<int[]> walk(PlanContext c, int ground, float heading, int steps, boolean down, float period, float phase) {
        List<int[]> out = new ArrayList<>();
        float x = c.x() + 0.5F, z = c.z() + 0.5F, h = heading;
        int g = ground;
        for (int i = 0; i < steps; i++) {
            float bias = 0.55F * Mth.sin(i * Mth.TWO_PI / period + phase), best = Float.MAX_VALUE, ba = h, bx = x, bz = z;
            int bg = g;
            for (int k = -2; k <= 2; k++) {
                float a = h + k * 0.4F;
                // (always on roughly the same way, never doubling back)
                if (Math.abs(Mth.wrapDegrees((a - heading) * Mth.RAD_TO_DEG)) > 70.0F) continue;
                float nx = x + Mth.cos(a + bias) * STEP, nz = z + Mth.sin(a + bias) * STEP;
                int ng = c.groundAt(Mth.floor(nx), Mth.floor(nz));
                float score = (down ? ng + Math.max(0, g - ng - 1) * 1.5F : Math.max(0, g - ng) * 3.0F + Math.max(0, ng - g - 1)) + Math.abs(k) * 0.5F;
                if (score < best) {
                    best = score;
                    ba = a;
                    bx = nx;
                    bz = nz;
                    bg = ng;
                }
            }
            x = bx;
            z = bz;
            h = ba;
            g = bg;
            out.add(new int[]{Mth.floor(x), Mth.floor(z), g});
        }
        return out;
    }

    static Landform load(CompoundTag tag) {
        return new BoilingRiver(tag.getInt("x"), tag.getInt("z"), tag.getIntArray("course"), tag.getIntArray("reaches"), tag.getInt("width"), tag.getInt("depth"),
                tag.getInt("spring"), tag.getInt("pool"), tag.getInt("top"), tag.getLong("seed"));
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
        tag.putInt("spring", spring);
        tag.putInt("pool", pool);
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BOILING_RIVER;
    }

    @Override
    public BoundingBox bounds() {
        int low = Integer.MAX_VALUE;
        for (int j = 0; j < count(); j++) low = Math.min(low, levelAt(j));
        // (the smoothed line keeps within its points)
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < course.length; i += 2) {
            minX = Math.min(minX, course[i]);
            maxX = Math.max(maxX, course[i]);
            minZ = Math.min(minZ, course[i + 1]);
            maxZ = Math.max(maxZ, course[i + 1]);
        }
        int r = Mth.ceil(Math.max(width * 0.5F * 1.15F, Math.max(spring, pool) / 10.0F * 1.12F) + BANKS) + 3;
        return new BoundingBox(minX - r, low - depth - 12, minZ - r, maxX + r, top + 32, maxZ + r);
    }

    private int count() {
        return reaches.length / 2;
    }

    private int start(int j) {
        return reaches[j * 2];
    }

    private float end(int j) {
        return j + 1 < count() ? start(j + 1) : shape().length;
    }

    private int levelAt(int j) {
        return reaches[j * 2 + 1];
    }

    /** Which reach a point {@code t} along the stream is in. */
    private int reach(float t) {
        int j = 0;
        while (j + 1 < count() && t >= start(j + 1)) j++;
        return j;
    }

    private float halfWidth(float t) {
        return width * 0.5F * (0.85F + 0.3F * Terrain.field(seed + 5, t, 0.0, 14.0));
    }

    private Shape shape() {
        return new Shape(this);
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
        final BoilingRiver river;
        final float[] xs, zs, arc;
        final float length;
        private final java.util.Map<Long, int[]> known = new java.util.HashMap<>();

        Shape(BoilingRiver river) {
            this.river = river;
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

        float inSpring(int x, int z) {
            return inPool(x, z, xs[0], zs[0], river.spring, 6);
        }

        float inLast(int x, int z) {
            return inPool(x, z, xs[xs.length - 1], zs[zs.length - 1], river.pool, 7);
        }

        /**
         * What a column is: {kind, level, more, the lowest water that can lie beside it}. For the stream and its pools the
         * level is the water's, and more how deep it is; for a rock, the level is the height of its top; for a bank, the
         * least height it's built up to (a block over any water beside it), and more how far it is from the water, in tenths.
         */
        int[] classify(int x, int z) {
            return known.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> work(x, z));
        }

        private int[] work(int x, int z) {
            BoilingRiver r = river;
            float rs = r.spring / 10.0F, rp = r.pool / 10.0F;
            float ds = inSpring(x, z);
            if (ds < 1.0F) return new int[]{POOL, r.levelAt(0), 1 + Math.round((r.depth + 1) * Mth.sqrt(1.0F - ds * ds)), r.levelAt(0)};
            float df = inLast(x, z);
            if (df < 1.0F) return new int[]{POOL, r.levelAt(r.count() - 1), 1 + Math.round(r.depth * Mth.sqrt(1.0F - df * df)), r.levelAt(r.count() - 1)};
            float[] nr = near(x + 0.5F, z + 0.5F);
            float t = nr[0], d = nr[1], hw = r.halfWidth(t);
            if (d < hw) {
                int j = r.reach(t);
                float into = t - r.start(j);
                int step = j > 0 ? r.levelAt(j - 1) - r.levelAt(j) : 0;
                // below a step of a block, a few rocks break the surface of the rapids
                if (step == 1 && into < 3.0F && d < hw - 0.6F && Terrain.noise(r.seed + 8, x, r.levelAt(j), z) < 0.28F) {
                    return new int[]{LIP, r.levelAt(j - 1), 0, r.waterNear(t, length, true)};
                }
                int deep = 1 + Math.round((r.depth - 1) * Mth.sqrt(Math.max(0.0F, 1.0F - (d / hw) * (d / hw))));
                // (and below a fall the water has scoured out a plunge pool)
                if (step >= 2 && into < 3.5F) deep += 1;
                return new int[]{WATER, r.levelAt(j), deep, r.waterNear(t, length, true)};
            }
            float edge = Math.min(Math.min(ds * rs - rs, df * rp - rp), d - hw);
            if (edge < BANKS) {
                // (a levee raised where the ground falls away slopes off again away from the water)
                int least = r.waterNear(t, length, false) + 1 - Math.max(0, (int) (edge - 1.5F));
                return new int[]{BANK, least, Math.round(edge * 10.0F), r.waterNear(t, length, true)};
            }
            return new int[]{NONE, 0, 0};
        }

        /**
         * Whether the water can stand in a column at {@code level}: only where every column beside it is certain to be
         * solid or water at that level (a bank or rock at least as high, or water at least as high, which, if higher, will
         * itself be a rock or a fall). Where it can't, the column is a rock instead or, at the head of a fall, it pours over.
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
                if (k[0] == NONE) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                int lvl = k[1];
                if (k[0] == BANK) {
                    bank(level, pos, b, shape, x, z, ground, standing, lvl, k[3] - depth - 4, k[2] / 10.0F, was, outside);
                    continue;
                }
                // sound rock under it all, as deep as the water beside it goes, with no hollow in it for the water to find
                for (int h = Math.max(b.minY(), Math.min(lvl - 8, k[3] - depth - 4)); h <= Math.min(ground, lvl); h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z));
                }
                boolean water = k[0] >= WATER && shape.held(x, z, lvl), pour = k[0] >= WATER && !water && shape.spout(x, z, lvl);
                int bed = pour ? lvl - 1 : water ? lvl - k[2] : lvl;
                int clear = Math.max(0, Math.min(32, b.maxY() - Math.max(ground, standing)));
                Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
                for (int h = lvl + 1; h <= Math.min(b.maxY(), Math.max(ground, standing)); h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
                }
                float n = Terrain.noise(seed, x, lvl, z);
                for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
                    Terrain.set(level, pos, x, h, z, h < bed ? rock(x, h, z) : water || pour ? bed(x, z, n, k[0] == POOL) : sill(x, h, z));
                }
                if (water || pour) {
                    wet.add(lvl);
                    for (int h = bed + 1; h <= lvl; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (pour) level.scheduleTick(pos.set(x, lvl, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
                    // the spring wells up through vents in its floor, bubbling
                    if (water && k[0] == POOL && lvl == levelAt(0) && shape.inSpring(x, z) < 0.45F && Terrain.noise(seed + 11, x, 0, z) < 0.45F) {
                        Terrain.set(level, pos, x, bed, z, Blocks.SOUL_SAND.defaultBlockState());
                        level.scheduleTick(pos.set(x, bed, z).immutable(), Blocks.SOUL_SAND, 20);
                    }
                }
            }
        }
        // the water's milky green-blue, from the bed to a little over it, reach by reach
        for (int w : wet) {
            Terrain.setBiome(level, area, w - depth - 4, w + 3, (x, z) -> {
                int[] k = shape.classify(x, z);
                return k[0] >= LIP && k[1] == w;
            }, Terrain.BOILING_RIVER_BIOME);
        }
    }

    /** The banks: hot bare ground, rock and mud by the water, cut back into the slope, with ferns and jungle plants further back. */
    private void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, Shape shape, int x, int z, int ground, int standing, int least,
                      int deepest, float edge, BlockState was, BiPredicate<Integer, Integer> outside) {
        // (cut back in a slope from the water up to the forest floor)
        int top = Math.max(least, Math.min(ground, least + Math.round(edge * 1.3F)));
        if (skipped(top, b)) return; // (high above the stream, or far below it: nothing to do)
        if (top == ground && standing <= ground && edge >= 3.0F) {
            // the forest floor, left as it is but for a fern here and there among the trees
            if (was.is(BlockTags.DIRT) && level.getBlockState(pos.set(x, top + 1, z)).isAir() && Terrain.noise(seed + 9, x, top, z) < 0.12F) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
            }
            return;
        }
        int clear = Math.max(0, Math.min(32, b.maxY() - Math.max(ground, standing)));
        Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, clear);
        for (int h = top + 1; h <= Math.max(ground, standing); h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
        }
        // (solid all down the face the water shows, as deep as the stream beside it goes)
        for (int h = Math.max(b.minY(), Math.min(ground + 1, deepest)); h < top; h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            if (h > ground || here.isAir() || !here.getFluidState().isEmpty()) {
                Terrain.set(level, pos, x, h, z, h >= top - 2 && h > ground ? Blocks.DIRT.defaultBlockState() : rock(x, h, z));
            }
        }
        float n = Terrain.noise(seed + 2, x, top, z);
        float rs = spring / 10.0F;
        boolean hot = shape.inSpring(x, z) * rs < rs + 4.0F;
        BlockState surface;
        if (hot) {
            // round the spring: ground baked orange, yellow sulphur crusting it, and wet mossy stones
            surface = n < 0.5F ? ModBlocks.STEAMING_GROUND.get().defaultBlockState() : n < 0.72F ? ModBlocks.SULFUR_CRUST.get().defaultBlockState()
                    : n < 0.88F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock(x, top, z);
        } else if (edge < 1.5F) {
            surface = n < 0.25F ? rock(x, top, z) : n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : n < 0.72F ? ModBlocks.STEAMING_GROUND.get().defaultBlockState() : Blocks.MUD.defaultBlockState();
        } else if (edge < 3.0F) {
            surface = n < 0.3F ? Blocks.MUD.defaultBlockState() : n < 0.48F ? Blocks.MOSS_BLOCK.defaultBlockState()
                    : n < 0.58F ? ModBlocks.STEAMING_GROUND.get().defaultBlockState() : n < 0.66F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : turf(was);
        } else {
            surface = n < 0.15F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.25F ? Blocks.MUD.defaultBlockState() : turf(was);
        }
        Terrain.set(level, pos, x, top, z, surface);
        if (hot || edge < 1.5F || !surface.is(BlockTags.DIRT) || top + 2 > b.maxY() || !level.getBlockState(pos.set(x, top + 1, z)).isAir()) return;
        float m = Terrain.noise(seed + 9, x, top, z);
        if (m < 0.2F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
        } else if (m < 0.3F && edge >= 2.5F && level.getBlockState(pos.set(x, top + 2, z)).isAir()) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.LARGE_FERN.defaultBlockState());
            Terrain.set(level, pos, x, top + 2, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (m < 0.42F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        } else if (m < 0.47F && edge >= 3.0F) {
            // a low jungle bush
            Terrain.set(level, pos, x, top + 1, z, Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        } else if (surface.is(Blocks.MOSS_BLOCK) && m < 0.6F) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /** The forest floor's own soil, wherever the bank was cut back through it (bare rock stays rock). */
    private static BlockState turf(BlockState was) {
        return was.is(BlockTags.DIRT) || was.isAir() ? Blocks.GRASS_BLOCK.defaultBlockState() : was;
    }

    private static boolean skipped(int top, BoundingBox b) {
        return top > b.maxY() - 3 || top < b.minY() + 1;
    }

    /** The rock under the forest: grey stone with a little andesite and tuff. */
    private BlockState rock(int x, int y, int z) {
        float band = Terrain.field(seed + 7, x * 0.6 + y * 1.3, z * 0.6 - y * 0.4, 3.0);
        return band < 0.22F ? Blocks.ANDESITE.defaultBlockState() : band > 0.85F ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** A rock or sill in the stream: wet stone, furred with moss. */
    private BlockState sill(int x, int y, int z) {
        float n = Terrain.noise(seed + 10, x, y, z);
        return n < 0.45F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.6F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : rock(x, y, z);
    }

    /** The stream's bed: stones, gravel and pale clay, and mud in the pools. */
    private BlockState bed(int x, int z, float n, boolean pool) {
        if (pool) {
            return n < 0.35F ? Blocks.CLAY.defaultBlockState() : n < 0.6F ? Blocks.MUD.defaultBlockState() : n < 0.8F ? Blocks.GRAVEL.defaultBlockState() : rock(x, 0, z);
        }
        return n < 0.3F ? Blocks.GRAVEL.defaultBlockState() : n < 0.5F ? rock(x, 0, z) : n < 0.65F ? Blocks.CLAY.defaultBlockState()
                : n < 0.8F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.9F ? Blocks.MUD.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** The river scalds anyone who wades or swims in it (half a heart a second). */
    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.BOILING_RIVERS.get() || !player.isInWater() || player.isSpectator()) return;
        int[] k = shape().classify(player.getBlockX(), player.getBlockZ());
        if (k[0] < WATER || player.getY() > k[1] + 1.2) return;
        player.hurt(level.damageSources().hotFloor(), 1.0F);
    }
}
