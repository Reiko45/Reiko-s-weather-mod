package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A glowworm grotto, like the caves at Waitomo in New Zealand: a winding stream cave through soft limestone hills. Rain
 * soaking down through the hills turns faintly acid and eats out the joints in the rock, and in time an underground stream
 * dissolves itself a passage a few blocks wide and high, opening out here and there into broad chambers. The stream runs
 * along the floor in quiet reaches held back behind little rimstone dams of calcite, slipping over them or falling a block
 * or two into the next, from a sump pool at the far end, where the passage dips under water, to a pool just inside the
 * cave mouth on the hillside. The walls are streaked with calcite flowstone, and dripstone hangs from the roof. Out of the
 * daylight, the roof over the stream is thickly hung with glowworms, fungus-gnat larvae that let down threads of sticky
 * silk and glow blue to lure the midges hatching from the water, so that the dark inner cave looks like a night sky.
 *
 * @param course   {x, z} of each point along the cave's line, about 5 blocks apart, from the mouth into the hill
 * @param reaches  {how far in it starts, its water level} for each level reach of the stream, from the mouth in
 * @param chambers {how far in, half-width (in tenths), half-length, how much higher its roof is} for each chamber
 * @param width    how wide the passage is, on average, and {@code height} how high its roof stands over the floor
 * @param depth    how deep the stream is in its channel
 * @param mouth    the height of the floor at the mouth, from which it steps down to the stream
 * @param pool     how far in the pool inside the mouth ends, and {@code sump} how long the sump pool at the far end is
 * @param arch     how far in the roof closes over (daylight reaches a little way past it, and no glowworms)
 */
public record GlowwormGrotto(int cx, int cz, int[] course, int[] reaches, int[] chambers, int width, int height, int depth, int mouth, int pool,
                             int sump, int arch, long seed) implements Landform {
    /** What a column is: rock (the walls), the cave's dry floor, or its stream and pools. */
    private static final int NONE = 0, FLOOR = 1, WATER = 2;
    private static final float STEP = 5.0F;
    /** How much rock the planned roof has over it, and how thick the shell of sound rock laid round the whole cave is. */
    private static final int COVER = 7, SHELL = 3;
    /** How far the cave's line may reach from the middle of the chunk it was planned in. */
    private static final int LIMIT = 86;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 32, 48)) {
            return null;
        }
        // hill country, not flat ground
        if (c.relief(24, 8) < 8) {
            return null;
        }
        // survey the ground about for the foot of a hill: a spot on a slope with the ground rising well above it, and
        // still high further in, where a stream could have cut its way out of the hillside
        int n = 7, step = 10, mid = n / 2;
        int[][] g = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) g[i][j] = c.groundAt(c.x() + (i - mid) * step, c.z() + (j - mid) * step);
        }
        List<float[]> spots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int here = g[i][j];
                if (here < c.seaLevel() + 3) continue;
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int i2 = i + 2 * di, j2 = j + 2 * dj, i3 = i + 3 * di, j3 = j + 3 * dj, ib = i - di, jb = j - dj;
                        if (di == 0 && dj == 0 || i2 < 0 || i2 >= n || j2 < 0 || j2 >= n) continue;
                        int rise = g[i2][j2] - here;
                        if (i3 >= 0 && i3 < n && j3 >= 0 && j3 < n) rise = Math.min(rise, g[i3][j3] - here);
                        // (on the slope itself, not down in a hollow)
                        if (ib >= 0 && ib < n && jb >= 0 && jb < n && g[ib][jb] > here + 1) continue;
                        if (rise < 7) continue;
                        float score = rise - 0.08F * (Math.abs(i - mid) + Math.abs(j - mid)) * step;
                        spots.add(new float[]{score, i, j, (float) Mth.atan2(dj, di)});
                    }
                }
            }
        }
        spots.sort((p, q) -> Float.compare(q[0], p[0]));
        int tried = 0, last = -1;
        for (float[] s : spots) {
            int i = (int) s[1], j = (int) s[2];
            if (i * n + j == last) continue; // (another way in from the same spot is no better)
            last = i * n + j;
            GlowwormGrotto grotto = attempt(c, c.x() + (i - mid) * step, c.z() + (j - mid) * step, s[3]);
            if (grotto != null) return grotto;
            if (++tried >= 2) break;
        }
        return null;
    }

    /** A cave running into the hill from a mouth at (px, pz), setting off the way {@code angle} points; null if the hill won't hold it. */
    @Nullable
    private static GlowwormGrotto attempt(PlanContext c, int px, int pz, float angle) {
        RandomSource random = c.random();
        int mouthG = c.groundAt(px, pz);
        int width = 5 + random.nextInt(4), height = 5 + random.nextInt(3), depth = 1 + random.nextInt(2);
        long seed = random.nextLong();
        int target = 42 + random.nextInt(39);
        float period = 4.0F + random.nextFloat() * 3.0F, phase = random.nextFloat() * Mth.TWO_PI;
        // into the hill, winding a little, and keeping under the high ground
        List<int[]> nodes = new ArrayList<>();
        nodes.add(new int[]{px, pz, mouthG});
        float x = px + 0.5F, z = pz + 0.5F, h = angle;
        for (int i = 1; i * STEP <= target; i++) {
            float bias = 0.4F * Mth.sin(i * Mth.TWO_PI / period + phase), best = -Float.MAX_VALUE, ba = h, bx = x, bz = z;
            int bg = 0;
            for (int k = -1; k <= 1; k++) {
                float a = h + k * 0.35F;
                if (Math.abs(Mth.wrapDegrees((a - angle) * Mth.RAD_TO_DEG)) > 60.0F) continue;
                float nx = x + Mth.cos(a + bias) * STEP, nz = z + Mth.sin(a + bias) * STEP;
                int ng = c.groundAt(Mth.floor(nx), Mth.floor(nz));
                float score = ng - Math.abs(k) * 0.75F;
                if (score > best) {
                    best = score;
                    ba = a;
                    bx = nx;
                    bz = nz;
                    bg = ng;
                }
            }
            if (Math.abs(bx - c.x()) > LIMIT || Math.abs(bz - c.z()) > LIMIT) break;
            // (past the mouth the hill has to stand well over it, or the cave ends here)
            if (i * STEP >= 15 && bg < mouthG + 4) break;
            x = bx;
            z = bz;
            h = ba;
            nodes.add(new int[]{Mth.floor(x), Mth.floor(z), bg});
        }
        if (nodes.size() < 7) {
            return null;
        }
        int[] course = new int[nodes.size() * 2];
        for (int i = 0; i < nodes.size(); i++) {
            course[i * 2] = nodes.get(i)[0];
            course[i * 2 + 1] = nodes.get(i)[1];
        }
        Shape shape = new GlowwormGrotto(px, pz, course, new int[]{0, 0}, new int[0], width, height, depth, 0, 0, 1, 0, seed).shape();
        float total = shape.length;
        if (total < 32.0F) {
            return null;
        }
        int sumpLen = 6 + random.nextInt(3);
        // the chambers, where the stream has swung about and undercut the walls: one well in, and in a long cave a second nearer the mouth
        List<int[]> rooms = new ArrayList<>();
        for (int k = 0; k < (total > 58.0F ? 2 : 1); k++) {
            int lk = 5 + random.nextInt(4);
            float tk = (k == 0 ? 0.55F + 0.15F * random.nextFloat() : 0.3F + 0.08F * random.nextFloat()) * total;
            int rk = 45 + random.nextInt(26), ek = 2 + random.nextInt(3);
            if (tk - lk < 18.0F || tk + lk > total - sumpLen - 3) continue;
            boolean clear = true;
            for (int[] r : rooms) clear &= Math.abs(r[0] - tk) > r[2] + lk + 4;
            if (clear) rooms.add(new int[]{Math.round(tk), rk, lk, ek});
        }
        int[] chambers = new int[rooms.size() * 4];
        for (int k = 0; k < rooms.size(); k++) System.arraycopy(rooms.get(k), 0, chambers, k * 4, 4);
        // the steps up the stream, a dozen blocks or so apart and never in a chamber: mostly little dams, now and then a fall of two
        List<int[]> steps = new ArrayList<>();
        steps.add(new int[]{0, 0});
        int rise = 0;
        float t = 24.0F + random.nextFloat() * 6.0F;
        while (t < total - sumpLen - 3 && rise < 5) {
            boolean inRoom = false;
            for (int[] r : rooms) {
                if (t > r[0] - r[2] - 2 && t < r[0] + r[2] + 2) {
                    t = r[0] + r[2] + 3;
                    inRoom = true;
                }
            }
            if (inRoom) continue;
            rise += random.nextFloat() < 0.35F && rise <= 3 ? 2 : 1;
            steps.add(new int[]{Math.round(t), rise});
            t += 10 + random.nextInt(8);
        }
        int[] reaches = new int[steps.size() * 2];
        for (int j = 0; j < steps.size(); j++) {
            reaches[j * 2] = steps.get(j)[0];
            reaches[j * 2 + 1] = steps.get(j)[1];
        }
        GlowwormGrotto probe = new GlowwormGrotto(px, pz, course, reaches, chambers, width, height, depth, 0, 0, sumpLen, 0, seed);
        // the stream sits low enough everywhere past the mouth that the roof has plenty of rock over it
        int cap = mouthG - 1;
        float[] ts = new float[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            ts[i] = shape.near(nodes.get(i)[0] + 0.5F, nodes.get(i)[1] + 0.5F)[0];
            if (ts[i] < 15.0F) continue;
            cap = Math.min(cap, nodes.get(i)[2] - COVER - 2 - Mth.ceil(probe.roofHeight(ts[i], total)) - probe.levelAt(probe.reach(ts[i])));
        }
        // (and the chambers' walls are well buried too)
        for (int[] r : rooms) {
            float[] m = shape.point(r[0]);
            for (int side = -1; side <= 1; side += 2) {
                float off = side * (r[1] / 10.0F + 1.0F);
                int gs = c.groundAt(Mth.floor(m[0] - m[3] * off), Mth.floor(m[1] + m[2] * off));
                cap = Math.min(cap, gs - 5 - 2 - Mth.ceil(probe.roofHeight(r[0], total) * 0.6F) - probe.levelAt(probe.reach(r[0])));
            }
        }
        // the floor steps down from the mouth to the stream, two blocks along for each block down, but not too far
        int drop = mouthG - 1 - cap;
        if (drop > 7 || !c.fits(cap, 12, 0)) {
            return null;
        }
        for (int j = 0; j < reaches.length / 2; j++) reaches[j * 2 + 1] += cap;
        int rampEnd = 2 + 2 * drop;
        int poolEnd = Math.min(rampEnd + 1 + 5 + random.nextInt(3), reaches.length > 2 ? reaches[2] - 3 : rampEnd + 8);
        probe = new GlowwormGrotto(px, pz, course, reaches, chambers, width, height, depth, mouthG, poolEnd, sumpLen, 0, seed);
        // where the hill closes over it, going in from the mouth
        int arch = -1;
        for (int i = 0; i < nodes.size(); i++) {
            float ti = ts[i];
            int base = ti < rampEnd ? probe.rampFloor(ti) : probe.levelAt(probe.reach(ti)) + 1;
            if (nodes.get(i)[2] >= base + Mth.ceil(probe.roofHeight(ti, total)) + 3) {
                arch = Math.round(i > 0 ? (ts[i - 1] + ti) / 2.0F : ti);
                break;
            }
        }
        // (most of it deep in the dark)
        if (arch < 0 || arch > total * 0.45F || total - sumpLen - (arch + 8) < 12.0F) {
            return null;
        }
        return new GlowwormGrotto(px, pz, course, reaches, chambers, width, height, depth, mouthG, poolEnd, sumpLen, arch, seed);
    }

    static Landform load(CompoundTag tag) {
        return new GlowwormGrotto(tag.getInt("x"), tag.getInt("z"), tag.getIntArray("course"), tag.getIntArray("reaches"), tag.getIntArray("chambers"),
                tag.getInt("width"), tag.getInt("height"), tag.getInt("depth"), tag.getInt("mouth"), tag.getInt("pool"), tag.getInt("sump"),
                tag.getInt("arch"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.put("course", new IntArrayTag(course));
        tag.put("reaches", new IntArrayTag(reaches));
        tag.put("chambers", new IntArrayTag(chambers));
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putInt("depth", depth);
        tag.putInt("mouth", mouth);
        tag.putInt("pool", pool);
        tag.putInt("sump", sump);
        tag.putInt("arch", arch);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GLOWWORM_GROTTO;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < course.length; i += 2) {
            minX = Math.min(minX, course[i]);
            maxX = Math.max(maxX, course[i]);
            minZ = Math.min(minZ, course[i + 1]);
            maxZ = Math.max(maxZ, course[i + 1]);
        }
        int low = Integer.MAX_VALUE, high = mouth, extra = 0;
        for (int j = 0; j < count(); j++) {
            low = Math.min(low, levelAt(j));
            high = Math.max(high, levelAt(j) + 2);
        }
        float widest = width * 0.5F * 1.15F + 1.0F;
        for (int k = 0; k + 3 < chambers.length; k += 4) {
            widest = Math.max(widest, chambers[k + 1] / 10.0F * 1.08F);
            extra = Math.max(extra, chambers[k + 3]);
        }
        // (the smoothed line keeps within its points; the shell reaches a few blocks beyond the walls)
        int r = Mth.ceil(widest) + SHELL + 3;
        return new BoundingBox(minX - r, low - depth - 2 - SHELL - 1, minZ - r, maxX + r, high + height + extra + 2 + 24, maxZ + r);
    }

    private int count() {
        return reaches.length / 2;
    }

    private int start(int j) {
        return reaches[j * 2];
    }

    private int levelAt(int j) {
        return reaches[j * 2 + 1];
    }

    /** Which reach a point {@code t} into the cave is in. */
    private int reach(float t) {
        int j = 0;
        while (j + 1 < count() && t >= start(j + 1)) j++;
        return j;
    }

    private float rampEnd() {
        return 2.0F + 2.0F * Math.max(0, mouth - levelAt(0) - 1);
    }

    /** The floor stepping down from the mouth to the stream. */
    private int rampFloor(float t) {
        return Math.max(levelAt(0) + 1, mouth - Mth.floor(Math.max(0.0F, t - 2.0F) / 2.0F));
    }

    /** How much chamber {@code k} swells the cave at {@code t} (0 outside it, 1 in its middle). */
    private float swell(int k, float t) {
        return Math.max(0.0F, 1.0F - Mth.square((t - chambers[k * 4]) / chambers[k * 4 + 2]));
    }

    private float halfWidth(float t) {
        float hw = width * 0.5F * (0.85F + 0.3F * Terrain.field(seed + 5, t, 0.0, 9.0));
        // (the mouth flares out a little)
        hw += Math.max(0.0F, 3.0F - t) * 0.3F;
        for (int k = 0; k < chambers.length / 4; k++) {
            float f = swell(k, t);
            if (f > 0.0F) hw = Math.max(hw, chambers[k * 4 + 1] / 10.0F * Mth.sqrt(f) * (0.92F + 0.16F * Terrain.field(seed + 6 + k, t, 0.0, 4.0)));
        }
        return hw;
    }

    /** How high the roof stands over the floor in the middle at {@code t}: higher in the chambers, and down into the water at the sump. */
    private float roofHeight(float t, float length) {
        float hc = height;
        for (int k = 0; k < chambers.length / 4; k++) hc += chambers[k * 4 + 3] * swell(k, t);
        if (t > length - sump) hc *= Mth.clamp((length - 1.5F - t) / Math.max(1.0F, sump - 1.5F), 0.0F, 1.0F);
        return hc;
    }

    private Shape shape() {
        return new Shape(this);
    }

    /**
     * The cave's line, smoothed from the points of its course, and what each column near it is (worked out once for each
     * column, and kept while a chunk is built).
     */
    private static final class Shape {
        final GlowwormGrotto cave;
        final float[] xs, zs, arc;
        final float length;
        private final java.util.Map<Long, int[]> known = new java.util.HashMap<>();

        Shape(GlowwormGrotto cave) {
            this.cave = cave;
            int n = cave.course.length / 2;
            float[] x = new float[n], z = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = cave.course[i * 2] + 0.5F;
                z[i] = cave.course[i * 2 + 1] + 0.5F;
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

        /** {t, distance, offset}: how far in the nearest point of the line to (x, z) is, how far away it is, and how far off to one side. */
        float[] near(float x, float z) {
            float bestD = Float.MAX_VALUE, bestT = 0.0F, bestV = 0.0F;
            for (int i = 0; i + 1 < xs.length; i++) {
                float ex = xs[i + 1] - xs[i], ez = zs[i + 1] - zs[i], len = ex * ex + ez * ez;
                float px = x - xs[i], pz = z - zs[i];
                float f = len > 0.0F ? Mth.clamp((px * ex + pz * ez) / len, 0.0F, 1.0F) : 0.0F;
                float dd = Mth.square(px - f * ex) + Mth.square(pz - f * ez);
                if (dd < bestD) {
                    bestD = dd;
                    bestT = arc[i] + f * (arc[i + 1] - arc[i]);
                    bestV = len > 0.0F ? (ex * pz - ez * px) / Mth.sqrt(len) : 0.0F;
                }
            }
            return new float[]{bestT, Mth.sqrt(bestD), bestV};
        }

        /** {x, z, dx, dz}: the point {@code t} along the line, and the way it's running there. */
        float[] point(float t) {
            int i = 0;
            while (i + 2 < xs.length && arc[i + 1] < t) i++;
            float seg = Math.max(1.0E-4F, arc[i + 1] - arc[i]), f = Mth.clamp((t - arc[i]) / seg, 0.0F, 1.0F);
            float dx = (xs[i + 1] - xs[i]) / seg, dz = (zs[i + 1] - zs[i]) / seg;
            return new float[]{xs[i] + f * (xs[i + 1] - xs[i]), zs[i] + f * (zs[i + 1] - zs[i]), dx, dz};
        }

        /**
         * What a column is: {kind, level, lowest, roof, floor, t, ramp}. Level is the water's for the stream (and the least
         * height of the floor, for the dry floor); lowest is the bottom of its bed; roof is the height of the first block of
         * rock over the hollow; floor is the top of the dry floor; t is how far into the cave it is; and ramp is 1 where the
         * floor steps down from the mouth (following the ground down where the hillside is lower).
         */
        int[] classify(int x, int z) {
            return known.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> work(x, z));
        }

        private int[] work(int x, int z) {
            GlowwormGrotto g = cave;
            float[] nr = near(x + 0.5F, z + 0.5F);
            float t = nr[0], d = nr[1], v = nr[2];
            int tt = Math.round(t);
            float hw = g.halfWidth(t);
            if (d >= hw) return new int[]{NONE, 0, 0, 0, 0, tt, 0};
            float u = d / hw, ramp = g.rampEnd();
            int j = g.reach(t), w = g.levelAt(j);
            boolean slope = t < ramp;
            int base = slope ? g.rampFloor(t) : w + 1;
            // (beside a fall of two, a step half-way up)
            if (!slope && j + 1 < g.count() && g.levelAt(j + 1) - w >= 2 && g.start(j + 1) - t < 1.5F) base = w + 2;
            // an arched roof, lumpy with old solution hollows
            float prof = 0.35F + 0.65F * Mth.sqrt(Math.max(0.0F, 1.0F - u * u));
            int roof = base + Math.round(g.roofHeight(t, length) * prof + 1.8F * (Terrain.field(g.seed + 2, x, z, 4.0) - 0.5F));
            boolean sumpZone = t >= length - g.sump;
            if (!slope && t >= ramp + 1.0F) {
                boolean wet;
                int deep;
                if (t <= g.pool || sumpZone) {
                    // the pools fill the passage from wall to wall
                    wet = true;
                    deep = 1 + Math.round((g.depth + (sumpZone ? 2 : 1) - 1) * Mth.sqrt(Math.max(0.0F, 1.0F - u * u)));
                } else {
                    float in = 0.0F;
                    for (int k = 0; k < g.chambers.length / 4; k++) in = Math.max(in, g.swell(k, t));
                    // the stream wanders from side to side of the passage, and spreads out into a pool in a chamber
                    float sw = Math.max(0.9F + 0.7F * Terrain.field(g.seed + 4, t, 0.0, 10.0), (hw - 2.2F) * Mth.sqrt(in));
                    float room = Math.max(0.0F, hw - sw - 0.7F);
                    float so = room * (2.0F * Terrain.field(g.seed + 3, t, 0.0, 16.0) - 1.0F);
                    float off = Math.abs(v - so);
                    wet = off < sw;
                    deep = 1 + Math.round((g.depth - 1 + (in > 0.3F ? 1 : 0)) * Mth.sqrt(Math.max(0.0F, 1.0F - (off / sw) * (off / sw))));
                }
                if (wet) {
                    // (the sump's roof comes right down into the water at the far end)
                    roof = Math.max(roof, sumpZone ? w + 1 : w + 2);
                    return new int[]{WATER, w, w - deep, roof, w - deep, tt, 0};
                }
            }
            int floor = base + (u > 0.8F ? 1 : 0);
            if (roof < floor + 3) return new int[]{NONE, 0, 0, 0, 0, tt, 0}; // (no room under the roof here: it's wall)
            int least = slope ? g.levelAt(0) + 1 : floor;
            return new int[]{FLOOR, least, least, roof, floor, tt, slope ? 1 : 0};
        }

        /**
         * Whether the water can stand in a column at {@code level}: only where every column beside it is certain to be
         * solid or water at that level (rock, the floor or a dam at least as high, or water at least as high, which, if
         * higher, will itself be a dam or a fall). Where it can't, the column is a dam instead or, at the head of a fall, it pours over.
         */
        boolean held(int x, int z, int level) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int[] k = classify(x + d.getStepX(), z + d.getStepZ());
                if (k[0] != NONE && k[1] < level) return false;
            }
            return true;
        }

        /** Whether water that can't stand in a column pours over instead, into water two or more blocks below it. */
        boolean spout(int x, int z, int level) {
            boolean pours = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                int[] k = classify(nx, nz);
                if (k[0] == NONE || k[1] >= level) continue;
                if (k[0] != WATER || k[1] > level - 2 || !held(nx, nz, k[1]) || k[3] <= level) return false;
                pours = true;
            }
            return pours;
        }

        /** Whether water lies beside a column at height {@code h} (so nothing that isn't solid goes there). */
        boolean wetBeside(int x, int z, int h) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int[] k = classify(x + d.getStepX(), z + d.getStepZ());
                if (k[0] == WATER && k[1] >= h) return true;
            }
            return false;
        }
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        Shape shape = shape();
        // what each column here (and a shell's breadth round them) is, to work out the shell of sound rock round the cave
        int m = SHELL, w = x1 - x0 + 1 + 2 * m, l = z1 - z0 + 1 + 2 * m;
        int[][] ks = new int[w * l][];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < l; j++) ks[i * l + j] = shape.classify(x0 - m + i, z0 - m + j);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int idx = (x - x0 + m) * l + (z - z0 + m);
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int di = -m; di <= m; di++) {
                    for (int dj = -m; dj <= m; dj++) {
                        int[] q = ks[idx + di * l + dj];
                        if (q[0] == NONE) continue;
                        lo = Math.min(lo, q[2] - m);
                        hi = Math.max(hi, q[3] + m);
                    }
                }
                if (lo == Integer.MAX_VALUE) continue;
                lo = Math.max(lo, b.minY());
                hi = Math.min(hi, b.maxY());
                int[] k = ks[idx];
                if (k[0] == NONE) {
                    wall(level, pos, b, ks, idx, l, x, z, lo, hi);
                } else {
                    hollow(level, pos, b, shape, k, x, z, lo, hi);
                }
            }
        }
    }

    /**
     * Rock round the cave: everything that isn't sound rock within the shell is made so, so no aquifer, flooded cave or sea
     * can break into it; where the ground dips lower than the water beside it, a bank is raised; and any pond standing on
     * the ground right beside the cave is filled in, so it can't spill in where the cave opens to the sky.
     */
    private void wall(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int[][] ks, int idx, int l, int x, int z, int lo, int hi) {
        int ground = Goosenecks.ground(level, pos, x, z);
        int standing = Goosenecks.standing(level, pos, x, ground, z);
        int wet = Integer.MIN_VALUE, beside = Integer.MIN_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int[] q = ks[idx + d.getStepX() * l + d.getStepZ()];
            if (q[0] == WATER) wet = Math.max(wet, q[1]);
            if (q[0] != NONE) beside = Math.max(beside, q[3]);
        }
        int t = ks[idx][5];
        // (a little higher under the ground beside the cave, where a pond might have been cleared out of the mouth)
        int deep = beside != Integer.MIN_VALUE ? Math.min(b.maxY(), hi + 6) : hi;
        for (int h = lo; h <= Math.min(deep, ground - 1); h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            // (the soil under the turf is left be, unless it's loose or hollow)
            if (h < ground - 3 && h <= hi || !here.blocksMotion() || !here.getFluidState().isEmpty() || here.getBlock() instanceof FallingBlock) {
                Terrain.set(level, pos, x, h, z, rock(x, h, z, t));
            }
        }
        int top = ground;
        if (wet != Integer.MIN_VALUE && wet + 1 > ground) {
            Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, Math.max(0, Math.min(24, b.maxY() - Math.max(ground, standing))));
            for (int h = ground + 1; h <= wet + 1; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z, t));
            top = wet + 1;
        }
        if (beside != Integer.MIN_VALUE) {
            for (int h = top + 1; h <= Math.min(Math.min(standing, beside + 9), b.maxY()); h++) {
                if (!level.getFluidState(pos.set(x, h, z)).isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z, t));
            }
        }
    }

    /** A column of the cave itself: rock beneath, the floor or the stream, the hollow, and the roof (or open sky at the mouth). */
    private void hollow(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, Shape shape, int[] k, int x, int z, int lo, int hi) {
        int ground = Goosenecks.ground(level, pos, x, z);
        int standing = Goosenecks.standing(level, pos, x, ground, z);
        int roof = k[3], lvl = k[1], t = k[5];
        boolean roofed = ground >= roof + 2;
        boolean water = k[0] == WATER && shape.held(x, z, lvl), pour = k[0] == WATER && !water && shape.spout(x, z, lvl);
        int solid;
        if (k[0] == FLOOR) {
            solid = k[6] == 1 ? Mth.clamp(ground, lvl, k[4]) : k[4];
        } else {
            solid = water ? k[2] : pour ? lvl - 1 : lvl;
        }
        int wtop = water || pour ? lvl : solid;
        int open = roofed ? roof - 1 : Math.min(b.maxY(), Math.max(ground, standing));
        if (!roofed) Terrain.clearAbove(level, pos, x, Math.max(ground, standing), z, Math.max(0, Math.min(24, b.maxY() - Math.max(ground, standing))));
        // sound rock beneath (down to the ground, where the floor stands out over lower ground, so nothing is left hanging)
        for (int h = Math.max(b.minY(), Math.min(lo, Math.max(ground + 1, lo - 16))); h < solid; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z, t));
        // (in front of the mouth, the hillside's own turf where the floor meets it)
        boolean keep = k[0] == FLOOR && !roofed && solid == ground;
        float n = Terrain.noise(seed + 12, x, solid, z);
        if (!keep) {
            BlockState top;
            if (k[0] == FLOOR) {
                top = floor(x, solid, z, n, t);
            } else if (water) {
                top = bed(n, k[2] <= lvl - 3);
            } else {
                // a rimstone dam of calcite, or the lip of a fall
                top = n < 0.75F ? Blocks.CALCITE.defaultBlockState() : rock(x, solid, z, t);
            }
            Terrain.set(level, pos, x, solid, z, top);
        }
        for (int h = solid + 1; h <= wtop; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
        if (pour) level.scheduleTick(pos.set(x, wtop, z).immutable(), Fluids.WATER, 1); // (set it pouring over the edge)
        for (int h = wtop + 1; h <= open; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        if (roofed) {
            for (int h = roof; h <= Math.min(hi, ground - 1); h++) Terrain.set(level, pos, x, h, z, rock(x, h, z, t));
            // a pond on the hill over the cave, where the mouth beside it is cut down through its edge, is filled in
            int beside = Integer.MIN_VALUE;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int[] q = shape.classify(x + d.getStepX(), z + d.getStepZ());
                if (q[0] != NONE) beside = Math.max(beside, q[3]);
            }
            for (int h = ground + 1; h <= Math.min(Math.min(standing, beside + 9), b.maxY()); h++) {
                if (!level.getFluidState(pos.set(x, h, z)).isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z, t));
            }
        }
        // ferns and moss in the daylight at the mouth
        if (t <= arch + 3 && k[0] == FLOOR && !keep && solid + 1 <= open && !shape.wetBeside(x, z, solid + 1)
                && level.getBlockState(pos.set(x, solid, z)).is(Blocks.MOSS_BLOCK)) {
            float p = Terrain.noise(seed + 13, x, solid, z);
            if (p < 0.3F) Terrain.set(level, pos, x, solid + 1, z, Blocks.FERN.defaultBlockState());
            else if (p < 0.5F) Terrain.set(level, pos, x, solid + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
        if (!roofed || roof - 1 <= wtop) return;
        int c = roof - 1;
        if (t >= arch + 8) {
            // the glowworms, out of the daylight: thickest over the water, where the midges hatch
            float thick = 0.3F + 0.5F * Terrain.field(seed + 15, x, z, 5.0) + (k[0] == WATER ? 0.15F : 0.0F);
            if (Terrain.noise(seed + 14, x, c, z) < thick && !shape.wetBeside(x, z, c)) {
                Terrain.set(level, pos, x, c, z, ModBlocks.GLOWWORMS.get().defaultBlockState());
                return;
            }
        }
        if (t < arch) return;
        // dripstone: a short stalactite hanging over the floor, and now and then a stalagmite growing up to meet it
        int room = k[0] == FLOOR ? 3 : 2;
        float q = Terrain.noise(seed + 16, x, c, z);
        if (q < 0.045F) {
            int len = q < 0.012F ? 3 : q < 0.025F ? 2 : 1;
            while (len > 0 && c - len + 1 < wtop + room + 1) len--;
            for (int i = 0; i < len; i++) {
                if (shape.wetBeside(x, z, c - i)) return;
            }
            for (int i = 0; i < len; i++) {
                DripstoneThickness th = i == len - 1 ? DripstoneThickness.TIP : i == len - 2 ? DripstoneThickness.FRUSTUM : DripstoneThickness.BASE;
                Terrain.set(level, pos, x, c - i, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                        .setValue(PointedDripstoneBlock.THICKNESS, th));
            }
        } else if (q > 0.985F && k[0] == FLOOR && solid + 2 < c - 1 && !shape.wetBeside(x, z, solid + 1) && !shape.wetBeside(x, z, solid + 2)) {
            boolean two = q > 0.993F;
            BlockState mite = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP);
            Terrain.set(level, pos, x, solid + 1, z, mite.setValue(PointedDripstoneBlock.THICKNESS, two ? DripstoneThickness.FRUSTUM : DripstoneThickness.TIP));
            if (two) Terrain.set(level, pos, x, solid + 2, z, mite.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
        }
    }

    /** The limestone: streaked with pale calcite flowstone where water has run down the walls, a little dripstone, and moss at the mouth. */
    private BlockState rock(int x, int y, int z, int t) {
        float n = Terrain.noise(seed + 8, x, y, z);
        if (t <= arch + 2 && y >= mouth - 3) {
            // (green with moss where the daylight reaches)
            float wet = Math.min(1.0F, (arch + 2 - t) / 8.0F + 0.2F);
            if (n < wet * 0.4F) return Blocks.MOSS_BLOCK.defaultBlockState();
            if (n < wet * 0.7F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (Terrain.field(seed + 7, x, z, 3.0) > 0.78F) return n < 0.8F ? Blocks.CALCITE.defaultBlockState() : ModBlocks.LIMESTONE.get().defaultBlockState();
        if (Terrain.field(seed + 9, x * 0.4 + y * 1.7, z * 0.4 - y * 0.6, 4.0) < 0.1F) return Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        if (n < 0.04F) return Blocks.CALCITE.defaultBlockState();
        return ModBlocks.LIMESTONE.get().defaultBlockState();
    }

    /** The dry floor: limestone and flowstone, and silt and gravel left by floods; moss at the mouth. */
    private BlockState floor(int x, int y, int z, float n, int t) {
        if (t <= arch + 3) {
            float wet = Mth.clamp((arch + 4 - t) / 6.0F, 0.0F, 1.0F);
            if (n < 0.6F * wet) return Blocks.MOSS_BLOCK.defaultBlockState();
            if (n < 0.75F * wet) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (n < 0.14F) return Blocks.GRAVEL.defaultBlockState();
        if (n < 0.24F) return Blocks.CLAY.defaultBlockState();
        if (n < 0.38F) return Blocks.CALCITE.defaultBlockState();
        return rock(x, y, z, t);
    }

    /** The stream's bed: gravel, clay and pebbles of limestone, and soft mud in the deep pools. */
    private BlockState bed(float n, boolean deep) {
        if (deep) return n < 0.4F ? Blocks.MUD.defaultBlockState() : n < 0.75F ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        return n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.6F ? Blocks.CLAY.defaultBlockState()
                : n < 0.85F ? ModBlocks.LIMESTONE.get().defaultBlockState() : Blocks.CALCITE.defaultBlockState();
    }
}
