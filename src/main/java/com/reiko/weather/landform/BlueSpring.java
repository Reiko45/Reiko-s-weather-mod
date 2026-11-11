package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A blue spring, like the Blautopf at Blaubeuren in Germany: a deep, round, funnel-shaped pool at the foot of a limestone
 * cliff, its water an unreal, glowing blue. Rain soaking into a limestone upland dissolves its way down through the
 * rock along cracks and joints, and over thousands of years the cracks widen into a whole drowned cave system that
 * gathers the water of a great stretch of country. Where that underground river finally meets the surface, at the foot
 * of the hills, it wells up out of a cave mouth at the bottom of a deep, steep-sided shaft it has dissolved for itself,
 * and spills out over the rim as a stream that's full-grown from its first yard. The water has been filtered through so
 * much rock that it's almost perfectly clear, and the fine particles of chalk it carries scatter the light, so the
 * deep funnel glows a vivid azure that darkens to ink where the cave opens at the bottom.
 *
 * @param angle  the way the stream flows out of it (away from the cliff, downhill)
 * @param water  the spring's water level
 * @param depth  how deep its funnel goes
 * @param radius the pool's radius, in tenths of a block
 * @param cliff  the height of the cliff behind it
 * @param pools  {u (tenths of a block, along the stream from the spring's middle), radius (tenths), depth, water} for the
 *               spring itself and then each pool its stream steps down through
 * @param top    the highest ground about (for the bounds)
 */
public record BlueSpring(int cx, int cz, float angle, int water, int depth, int radius, int cliff, int[] pools, int top, long seed) implements Landform {
    /** What each column is: untouched, a bank, the cliff, a lip of rock between basins, a little basin in the channel, or a pool. */
    private static final int NONE = 0, BANK = 1, CLIFF = 2, LIP = 3, BASIN = 4, POOL = 5;
    /** Half the width of the stream's channel between the pools, and how far the banks reach beyond the water. */
    private static final float CHANNEL = 1.3F, BANKS = 2.5F;
    /** How far back from the spring's edge the cliff reaches (fading into the hillside), and how far round it the cliff runs from straight behind. */
    private static final float BEHIND = 16.0F, FACE = 3.0F, ARC = 1.75F;
    /** The survey: every 8 blocks out to 32 each way. */
    private static final int SURVEY = 9;
    private static final int[][] WAYS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 40, 40)) {
            return null;
        }
        // (nowhere near a slope worth the name: not worth surveying)
        if (c.relief(24, 8) < 8) {
            return null;
        }
        // the lie of the land round about
        int mid = SURVEY / 2;
        int[][] g = new int[SURVEY][SURVEY];
        for (int i = 0; i < SURVEY; i++) {
            for (int j = 0; j < SURVEY; j++) g[i][j] = c.groundAt(c.x() + 8 * (i - mid), c.z() + 8 * (j - mid));
        }
        // the foot of a steep rise or a little cliff, with the ground falling gently away from it the other way
        List<int[]> sites = new ArrayList<>();
        for (int i = 2; i < SURVEY - 2; i++) {
            for (int j = 2; j < SURVEY - 2; j++) {
                int foot = g[i][j];
                if (foot < c.seaLevel() + 3) continue;
                for (int w = 0; w < 8; w++) {
                    int di = WAYS[w][0], dj = WAYS[w][1];
                    int back1 = g[i - di][j - dj], back2 = g[i - 2 * di][j - 2 * dj], ahead1 = g[i + di][j + dj], ahead2 = g[i + 2 * di][j + 2 * dj];
                    int left = g[i - dj][j + di], right = g[i + dj][j - di];
                    if (back1 - foot < 4 || back2 - foot < 9 || ahead1 > foot + 1 || ahead2 > foot - 1 || Math.min(left, right) < foot - 5) continue;
                    int score = (back2 - foot) + 2 * (back1 - foot) + Math.min(foot - ahead2, 8) - Math.abs(i - mid) - Math.abs(j - mid);
                    sites.add(new int[]{score, i, j, w});
                }
            }
        }
        sites.sort((p, q) -> q[0] != p[0] ? q[0] - p[0] : p[1] != q[1] ? p[1] - q[1] : p[2] != q[2] ? p[2] - q[2] : p[3] - q[3]);
        for (int k = 0; k < Math.min(3, sites.size()); k++) {
            int[] s = sites.get(k);
            float a = (float) Math.atan2(WAYS[s[3]][1], WAYS[s[3]][0]);
            Landform spring = at(c, c.x() + 8 * (s[1] - mid), c.z() + 8 * (s[2] - mid), a);
            if (spring != null) return spring;
        }
        return null;
    }

    /** The spring at the foot of the rise behind a spot, flowing out the way {@code a} (or none, if it won't fit there). */
    @Nullable
    private static Landform at(PlanContext c, int fx, int fz, float a) {
        int foot = c.groundAt(fx, fz);
        // where the rise begins, a little way back
        float rise = -10.0F;
        for (int k = 1; k <= 5; k++) {
            if (ground(c, fx, fz, a, -2.0F * k, 0.0F) >= foot + 3) {
                rise = -2.0F * k + 1.0F;
                break;
            }
        }
        float r = 6.0F + c.random().nextFloat() * 4.0F;
        // the pool lies right against the foot of the rise
        double[] m = Terrain.xz(rise + r * 0.95F, 0.0, fx + 0.5, fz + 0.5, a);
        int sx = Mth.floor(m[0]), sz = Mth.floor(m[1]);
        int centre = c.groundAt(sx, sz);
        // the water stands a block under the lowest of its rim (unless that's well down a slope, when the rim's built up a little there)
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < 12; i++) {
            float t = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(sx + Math.round(Mth.cos(t) * (r + 1.5F)), sz + Math.round(Mth.sin(t) * (r + 1.5F)));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        int water = Math.max(Math.max(low - 1, centre - 5), c.seaLevel() + 2);
        if (water + 1 - low > 4 || water > foot + 1) {
            return null;
        }
        int depth = 12 + c.random().nextInt(9);
        if (!c.fits(water, depth + 12, 30)) {
            return null;
        }
        // the stream: down the slope from the spring's lip, stepping down over little falls into pools
        float reach = r * 1.12F;
        int n = 1 + c.random().nextInt(3);
        int[] pools = new int[(n + 1) * 4];
        pools[1] = Math.round(r * 10.0F);
        pools[2] = depth;
        pools[3] = water;
        float u = reach;
        int prev = water, count = 0;
        for (int i = 1; i <= n; i++) {
            float pr = 1.8F + c.random().nextFloat() * 1.7F;
            u += 3.0F + c.random().nextFloat() * 4.0F + pr;
            int g = ground(c, sx, sz, a, u, 0.0F);
            // (never rising downstream, and never more than a few blocks below the pool above: where the ground drops away it's built up instead)
            if (g > prev + 3) break;
            int level = Mth.clamp(g - 1, prev - 5, prev - 2);
            if (level < c.seaLevel() + 1) break;
            pools[i * 4] = Math.round(u * 10.0F);
            pools[i * 4 + 1] = Math.round(pr * 10.0F);
            pools[i * 4 + 2] = Math.min(2 + c.random().nextInt(3), Math.round(pr) + 1);
            pools[i * 4 + 3] = level;
            prev = level;
            u += pr;
            count++;
        }
        if (count == 0) {
            return null;
        }
        pools = java.util.Arrays.copyOf(pools, (count + 1) * 4);
        // the cliff behind, standing no higher than the hill it's cut into
        int behind = Math.max(ground(c, sx, sz, a, -(reach + 4.0F), 0.0F), ground(c, sx, sz, a, -(reach + 10.0F), 0.0F));
        int cliff = Math.max(water + 6, Math.min(water + 9 + c.random().nextInt(7), behind + 2));
        int top = Math.max(Math.max(high, behind), Math.max(cliff, foot)) + 2;
        return new BlueSpring(sx, sz, a, water, depth, Math.round(r * 10.0F), cliff, pools, top, c.random().nextLong());
    }

    /** The ground {@code u} along a way and {@code v} across it from a spot. */
    private static int ground(PlanContext c, int x, int z, float a, float u, float v) {
        double[] p = Terrain.xz(u, v, x + 0.5, z + 0.5, a);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new BlueSpring(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("water"), tag.getInt("depth"), tag.getInt("radius"),
                tag.getInt("cliff"), tag.getIntArray("pools"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("water", water);
        tag.putInt("depth", depth);
        tag.putInt("radius", radius);
        tag.putInt("cliff", cliff);
        tag.put("pools", new IntArrayTag(pools));
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BLUE_SPRING;
    }

    private int count() {
        return pools.length / 4;
    }

    private float pu(int i) {
        return pools[i * 4] / 10.0F;
    }

    private float radius(int i) {
        return pools[i * 4 + 1] / 10.0F;
    }

    private int level(int i) {
        return pools[i * 4 + 3];
    }

    /** How far along the stream a pool can reach either side of its middle (with its edge at its most ragged). */
    private float reach(int i) {
        return radius(i) * 1.12F;
    }

    /** The lowest the water goes: the floor of the cave at its far end. */
    private int deepest() {
        return water - depth - 2;
    }

    @Override
    public BoundingBox bounds() {
        int n = count(), low = deepest();
        for (int i = 1; i < n; i++) low = Math.min(low, level(i) - pools[i * 4 + 2]);
        float back = reach(0) + BEHIND + 2.0F;
        float u0 = -back, u1 = pu(n - 1) + reach(n - 1) + BANKS + 2.0F, w = Math.max(back, amplitude() + 3.5F * 1.12F + BANKS + 2.0F);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{u0, u1}) {
            for (float v : new float[]{-w, w}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, low - 8, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, top + 16, Mth.floor(maxZ) + 1);
    }

    private float amplitude() {
        return 1.0F + 1.5F * Terrain.hash(seed, 1);
    }

    /** Where the stream's line wanders across the slope, {@code u} blocks along it (it leaves the spring straight). */
    private float meander(float u) {
        float settle = Terrain.smooth((u - reach(0) - 1.0F) / 5.0F);
        return settle * amplitude() * Mth.sin(u * (0.12F + 0.08F * Terrain.hash(seed, 2)) + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** How far into pool {@code i} a spot is (under 1 inside it). */
    private float inPool(int i, int x, int z, float u, float v) {
        float du = u - pu(i), dv = v - meander(pu(i));
        return Mth.sqrt(du * du + dv * dv) / (radius(i) * (0.88F + 0.24F * Terrain.field(seed + 5 + i, x, z, i == 0 ? 5.0 : 2.5)));
    }

    /** How far the water drops from the pool above into pool {@code i}. */
    private int fall(int i) {
        return i <= 0 || i >= count() ? 0 : level(i - 1) - level(i);
    }

    /** The stream's level at a point along it: {level, what it is (0 a lip, 1 a basin, 2 within a pool's reach), the pool}. */
    private int[] step(float u) {
        int n = count();
        for (int i = 0; i < n; i++) {
            if (u > pu(i) + reach(i)) continue;
            if (u >= pu(i) - reach(i)) return new int[]{level(i), 2, i};
            if (i == 0) return new int[]{level(i), 0, -1};
            return fall(i) >= 2 ? new int[]{level(i - 1), 1, -1} : new int[]{level(i - 1), 0, -1};
        }
        return new int[]{level(n - 1), 0, -1};
    }

    /** The highest water that can be beside a column of the banks, {@code u} along the stream (or far below, if none can). */
    private int waterNear(float u) {
        int most = Integer.MIN_VALUE / 2;
        for (float t = u - 1.25F; t <= u + 1.25F; t += 0.25F) {
            int[] s = step(t);
            if (s[1] > 0) most = Math.max(most, s[0]);
        }
        return most;
    }

    private boolean onStream(float u, float margin) {
        return u > pu(0) - reach(0) - margin && u < pu(count() - 1) + reach(count() - 1) + margin;
    }

    /** How far out beyond the spring's ragged edge a spot is, and how far round from straight behind it: {out, round}. */
    private float[] behind(int x, int z, float u, float v) {
        float d = inPool(0, x, z, u, v);
        return new float[]{(d - 1.0F) * radius(0) * (0.88F + 0.24F * Terrain.field(seed + 5, x, z, 5.0)), (float) Math.abs(Math.atan2(v, -u))};
    }

    /** Whether a spot {out, round} is part of the cliff. */
    private static boolean cliffy(float[] b) {
        return b[0] >= 0.0F && b[0] <= BEHIND && b[1] < ARC;
    }

    /** The least height the cliff stands at a spot {out, round}: right up at the face, falling away behind into the hill and round towards its ends. */
    private int cliffAt(int x, int z, float[] b) {
        float ends = Terrain.smooth((ARC - b[1]) / 0.7F), back = Terrain.smooth((BEHIND - b[0]) / (BEHIND - FACE));
        float jag = 1.0F + 0.25F * (Terrain.field(seed + 6, x, z, 3.0) - 0.5F);
        return water + 1 + Math.round((cliff - water - 1) * ends * back * jag);
    }

    /** What a column is: {kind, level, pool} (see FairyPools: the same stream, with a cliff round the back of its first pool). */
    private int[] classify(int x, int z, float u, float v) {
        int n = count();
        float near = Float.MAX_VALUE;
        int nearest = -1;
        for (int i = 0; i < n; i++) {
            if (Math.abs(u - pu(i)) > reach(i) + BANKS + 1.0F) continue;
            float d = inPool(i, x, z, u, v);
            if (d < 1.0F) return new int[]{POOL, level(i), i};
            if (d * radius(i) - radius(i) < near) {
                near = d * radius(i) - radius(i);
                nearest = i;
            }
        }
        if (u < reach(0) && cliffy(behind(x, z, u, v))) return new int[]{CLIFF, water + 1, -1};
        float off = Math.abs(v - meander(u));
        if (off < CHANNEL && onStream(u, 0.0F)) {
            int[] s = step(u);
            if (s[1] == 2) {
                int i = s[2];
                if (u < pu(i) && fall(i) >= 2) return new int[]{POOL, s[0], i};
                if (u >= pu(i) && fall(i + 1) >= 2) return new int[]{BASIN, s[0], -1};
                return new int[]{LIP, s[0], -1};
            }
            return new int[]{s[1] == 1 ? BASIN : LIP, s[0], -1};
        }
        if (nearest >= 0 && near < BANKS || off < CHANNEL + BANKS && onStream(u, BANKS)) {
            return new int[]{BANK, waterNear(u) + 1, -1};
        }
        return new int[]{NONE, 0, -1};
    }

    private int[] classify(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        return classify(x, z, l[0], l[1]);
    }

    /** Whether water in a column that can't hold it pours over the edge instead, straight down into a pool two or more blocks below. */
    private boolean spout(int x, int z, int level) {
        boolean pours = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            int[] k = classify(nx, nz);
            if (k[0] == NONE) return false;
            if (k[1] >= level) continue;
            if (k[0] != POOL || k[1] > level - 2 || !held(nx, nz, k[1])) return false;
            pours = true;
        }
        return pours;
    }

    /** Whether water can stand in a column at a level: only where every column beside it is certain to be solid or water there. */
    private boolean held(int x, int z, int level) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int[] k = classify(x + d.getStepX(), z + d.getStepZ());
            if (k[0] == NONE || k[1] < level) return false;
        }
        return true;
    }

    // ---- the funnel and the cave ----

    /** The deepest point of the funnel, a little towards the cliff, where the cave opens: {u, v}. */
    private float[] throat() {
        return new float[]{-0.3F * radius(0), 0.0F};
    }

    /** The way the cave runs into the hill (roughly straight back from the spring). */
    private float caveAngle() {
        return angle + Mth.PI + (Terrain.hash(seed, 7) - 0.5F) * 0.7F;
    }

    /** How long the cave is, from the throat to its far end. */
    private float caveLength() {
        return radius(0) * 0.85F + 4.0F + 4.0F * Terrain.hash(seed, 8);
    }

    /** The cave at a spot: {how far along it from the throat, how far across it}. */
    private float[] cave(int x, int z) {
        double[] t = Terrain.xz(throat()[0], throat()[1], cx + 0.5, cz + 0.5, angle);
        return Terrain.uv(x + 0.5, z + 0.5, t[0], t[1], caveAngle());
    }

    /** The cave's floor and roof at a column, {floor, roof}, or null where it doesn't pass. */
    @Nullable
    private int[] tunnel(int x, int z) {
        float[] c = cave(x, z);
        float len = caveLength(), along = c[0];
        if (along < 0.0F || along > len) return null;
        float half = Mth.lerp(along / len, 1.8F, 1.1F);
        if (Math.abs(c[1]) > half) return null;
        // (it runs level out of the funnel, then down into the hill)
        int floor = water - depth + 1 - Math.max(0, (int) ((along - radius(0) * 0.7F) / 4.0F));
        int roof = floor + 2 + (Math.abs(c[1]) < half * 0.55F ? 1 : 0);
        return new int[]{floor, roof};
    }

    private boolean inTunnel(int x, int y, int z) {
        int[] t = tunnel(x, z);
        return t != null && y >= t[0] && y <= t[1];
    }

    /** The funnel's depth at a column of the spring: deepest at the throat, shelving to a block or two at its rim. */
    private int funnel(int x, int z, float u, float v) {
        float d = inPool(0, x, z, u, v);
        float[] t = throat();
        float fromThroat = Mth.sqrt(Mth.square(u - t[0]) + Mth.square(v - t[1])), toEdge = Math.max(0.0F, 1.0F - d) * radius(0);
        float s = fromThroat / Math.max(0.01F, fromThroat + toEdge);
        if (s > 0.86F) return 1;
        return Math.max(2, Math.round(depth * (float) Math.pow(1.0F - s, 1.15F)));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            int[] k = classify(nx, nz);
            return k[0] == NONE || (k[0] == BANK || k[0] == CLIFF) && skipped(Math.max(Goosenecks.ground(level, new BlockPos.MutableBlockPos(), nx, nz), k[1]), b);
        };
        boolean wetAny = false;
        int low = deepest(), high = water;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                int[] k = classify(x, z, u, v);
                if (k[0] == NONE) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                int standing = Math.min(b.maxY(), Goosenecks.standing(level, pos, x, ground, z));
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                float n = Terrain.noise(seed, x, k[1], z);
                int lvl = k[1];
                // round the spring the rock is limestone all the way down, sound and without a hollow for the water to find
                boolean spring = k[0] == CLIFF || k[0] == POOL && k[2] == 0 || Mth.sqrt(u * u + v * v) < reach(0) + BANKS + 1.5F;
                int deep = Math.max(b.minY(), spring ? deepest() - 2 : lvl - 8);
                for (int h = deep; h <= Math.min(ground, spring ? water : lvl); h++) {
                    if (inTunnel(x, h, z)) continue;
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (spring || here.isAir() || !here.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, spring ? cave(x, h, z) : rock(x, h, z));
                }
                int clear = Math.max(0, Math.min(20, b.maxY() - Math.max(ground, standing)));
                if (k[0] == CLIFF) {
                    cliff(level, pos, b, x, z, u, v, ground, standing, was, clear, outside);
                } else if (k[0] == BANK) {
                    bank(level, pos, b, x, z, ground, standing, lvl, clear, was, n, outside);
                } else {
                    boolean wet = k[0] >= BASIN && held(x, z, lvl), pour = k[0] >= BASIN && !wet && spout(x, z, lvl);
                    int sink = 0;
                    if (pour) {
                        wet = true;
                        sink = 1;
                    } else if (wet) {
                        sink = 1;
                        if (k[0] == POOL && k[2] == 0) {
                            sink = funnel(x, z, u, v);
                        } else if (k[0] == POOL) {
                            // (the head of a pool, where a fall comes down into it, is scoured out a little deeper)
                            float d = inPool(k[2], x, z, u, v);
                            sink = d >= 1.0F ? 2 : 1 + Math.round((pools[k[2] * 4 + 2] - 1) * Mth.sqrt(Math.max(0.0F, 1.0F - d * d)));
                        }
                    }
                    int bed = lvl - sink;
                    clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
                    for (int h = lvl + 1; h <= Math.min(b.maxY(), Math.max(ground, standing)); h++) {
                        Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
                    }
                    for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
                        if (inTunnel(x, h, z)) continue;
                        BlockState s = spring ? cave(x, h, z) : rock(x, h, z);
                        // (nothing loose over the cave)
                        if (h == bed) s = !wet ? lip(x, h, z) : inTunnel(x, h - 1, z) ? s : bed(x, h, z, k[2] == 0 ? (float) sink / depth : 0.0F);
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    if (wet) {
                        wetAny = true;
                        for (int h = bed + 1; h <= lvl; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                        if (pour) level.scheduleTick(pos.set(x, lvl, z).immutable(), Fluids.WATER, 1); // set it pouring over the edge
                        if (k[2] == 0) faceVines(level, pos, x, z);
                    }
                    low = Math.min(low, bed);
                }
                // the cave, brim full, running back into the hill from the bottom of the funnel
                int[] t = tunnel(x, z);
                if (t != null) {
                    for (int h = t[0]; h <= t[1]; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    wetAny = true;
                }
            }
        }
        // the spring's famous blue, and its stream's
        if (wetAny) {
            Terrain.setBiome(level, area, low - 4, high + 3, (x, z) -> classify(x, z)[0] >= LIP || tunnel(x, z) != null, Terrain.BLUE_SPRING_BIOME);
        }
    }

    /** The cliff: limestone rising sheer from the back of the pool, mossy and streaked where the water seeps out of it, fading back into the hillside. */
    private void cliff(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, int ground, int standing, BlockState was,
                       int clear, BiPredicate<Integer, Integer> outside) {
        float[] bh = behind(x, z, u, v);
        int least = cliffAt(x, z, bh), tgt = Math.max(ground, least);
        if (skipped(tgt, b)) return;
        boolean face = bh[0] < FACE;
        // (the face itself is limestone right up to the top; behind it the hill keeps its own ground, built up where it's lower)
        int from = face ? Math.min(ground + 1, water) : ground + 1;
        boolean soil = was.is(BlockTags.DIRT) || was.is(Blocks.GRASS_BLOCK);
        if (tgt > ground || standing > ground) clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
        for (int h = Math.max(b.minY(), from); h <= tgt; h++) {
            if (inTunnel(x, h, z)) continue;
            BlockState s = wall(x, h, z);
            if (h == tgt && !face) {
                s = soil && tgt == ground ? was : Terrain.noise(seed + 11, x, h, z) < 0.35F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            } else if (h == tgt - 1 && !face && tgt > ground) {
                s = Blocks.DIRT.defaultBlockState();
            } else if (h == tgt && Terrain.noise(seed + 12, x, h, z) < 0.45F) {
                s = Blocks.MOSS_BLOCK.defaultBlockState(); // (moss over the brink)
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        // anything left standing over the new top (a pond) is let out, unless that would empty a pond beside it
        for (int h = tgt + 1; h <= standing; h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? wall(x, h, z) : Blocks.AIR.defaultBlockState());
        }
        BlockState top = level.getBlockState(pos.set(x, tgt, z));
        if (top.is(BlockTags.DIRT) && level.getBlockState(pos.set(x, tgt + 1, z)).isAir()) plant(level, pos, x, tgt, z, top);
    }

    /** The cliff's rock: grey-white limestone, streaked green and dark with moss where water seeps down it. */
    private BlockState wall(int x, int y, int z) {
        float streak = Terrain.field(seed + 13, x * 1.7 + z * 1.7, y * 0.15, 2.5);
        float n = Terrain.noise(seed + 14, x, y, z);
        if (streak < 0.22F) return n < 0.5F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.75F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (n < 0.06F) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (n > 0.93F) return Blocks.CALCITE.defaultBlockState();
        return ModBlocks.LIMESTONE.get().defaultBlockState();
    }

    /** The rock deep round the spring: limestone, dark and wet round the cave. */
    private BlockState cave(int x, int y, int z) {
        if (y <= water - depth + 6 && y >= deepest() - 1) {
            for (Direction d : Direction.values()) {
                if (inTunnel(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) {
                    float n = Terrain.noise(seed + 15, x, y, z);
                    return n < 0.4F ? Blocks.TUFF.defaultBlockState() : n < 0.75F ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                }
            }
        }
        return Terrain.noise(seed + 16, x, y, z) < 0.12F ? Blocks.CALCITE.defaultBlockState() : ModBlocks.LIMESTONE.get().defaultBlockState();
    }

    /** Vines trailing down the cliff over the water, and glow lichen on the wet rock just above it. */
    private void faceVines(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            float[] l = Terrain.uv(nx + 0.5, nz + 0.5, cx + 0.5, cz + 0.5, angle);
            if (classify(nx, nz, l[0], l[1])[0] != CLIFF) continue;
            float[] bh = behind(nx, nz, l[0], l[1]);
            int rock = cliffAt(nx, nz, bh);
            float k = Terrain.noise(seed + 17, x, d.ordinal(), z);
            if (k < 0.4F && rock >= water + 5) {
                int end = rock - 2 - Math.round(Terrain.noise(seed + 18, x, d.ordinal(), z) * (rock - water - 3));
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d), true);
                for (int h = rock - 1; h >= Math.max(end, water + 2); h--) {
                    if (!level.getBlockState(pos.set(x, h, z)).isAir()) break;
                    Terrain.set(level, pos, x, h, z, vine);
                }
            } else if (k > 0.85F && level.getBlockState(pos.set(x, water + 1, z)).isAir()) {
                Terrain.set(level, pos, x, water + 1, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true));
            }
            return;
        }
    }

    /** The banks: built up a block over the water where the ground falls away, mossy limestone where the stream has cut through the turf. */
    private void bank(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int ground, int standing, int least, int clear,
                      BlockState was, float n, BiPredicate<Integer, Integer> outside) {
        int top = Math.max(ground, least);
        if (skipped(top, b)) return;
        clearAbove(level, pos, x, Math.max(ground, standing), z, clear, outside);
        for (int h = top + 1; h <= standing; h++) {
            Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(x, h, z) : Blocks.AIR.defaultBlockState());
        }
        boolean turf = was.is(BlockTags.DIRT) && n > 0.25F;
        int from = least > Integer.MIN_VALUE / 4 ? Math.min(ground + 1, least - 7) : ground - 4;
        for (int h = Math.max(b.minY(), from); h < top; h++) {
            if (h > ground || level.getBlockState(pos.set(x, h, z)).isAir() || !level.getBlockState(pos).getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, rock(x, h, z));
        }
        BlockState surface = turf ? top > ground ? Blocks.MOSS_BLOCK.defaultBlockState() : was
                : n < 0.15F ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.25F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock(x, top, z);
        Terrain.set(level, pos, x, top, z, surface);
        if (surface.is(BlockTags.DIRT)) plant(level, pos, x, top, z, surface);
    }

    /** Ferns and moss on the damp ground round the water. */
    private void plant(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState surface) {
        float m = Terrain.noise(seed + 9, x, y, z);
        if (m < 0.24F) {
            Terrain.set(level, pos, x, y + 1, z, m < 0.15F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
        } else if (surface.is(Blocks.MOSS_BLOCK) && m < 0.45F) {
            Terrain.set(level, pos, x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    private static boolean skipped(int top, BoundingBox b) {
        return top > b.maxY() - 2 || top < b.minY() + 1;
    }

    /** The rock along the stream: limestone, with the odd band of grey stone. */
    private BlockState rock(int x, int y, int z) {
        float band = Terrain.field(seed + 7, x * 0.6 + y * 1.3, z * 0.6 - y * 0.4, 3.0);
        return band < 0.2F ? Blocks.STONE.defaultBlockState() : band > 0.85F ? Blocks.CALCITE.defaultBlockState() : ModBlocks.LIMESTONE.get().defaultBlockState();
    }

    /** The top of a lip of rock the water runs over: worn smooth and furred with moss. */
    private BlockState lip(int x, int y, int z) {
        float n = Terrain.noise(seed + 8, x, y, z);
        return n < 0.4F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.55F ? Blocks.MOSS_BLOCK.defaultBlockState() : rock(x, y, z);
    }

    /** The bottom of a pool: pale limestone and chalky calcite (which give the water its glow), gravel deep down, moss in the shallows. */
    private BlockState bed(int x, int y, int z, float deep) {
        float n = Terrain.noise(seed + 10, x, y, z);
        if (deep > 0.75F) return n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : n < 0.85F ? ModBlocks.LIMESTONE.get().defaultBlockState() : Blocks.CLAY.defaultBlockState();
        if (deep > 0.15F) return n < 0.45F ? ModBlocks.LIMESTONE.get().defaultBlockState() : n < 0.8F ? Blocks.CALCITE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        return n < 0.3F ? Blocks.GRAVEL.defaultBlockState() : n < 0.5F ? ModBlocks.LIMESTONE.get().defaultBlockState() : n < 0.65F ? Blocks.CALCITE.defaultBlockState()
                : n < 0.85F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
    }

    /** Clears trees and plants out of a column above y (as {@link Terrain#clearAbove}), but leaves anything that's walling in a pond just outside. */
    private static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int height, BiPredicate<Integer, Integer> outside) {
        int max = Math.min(level.getMaxBuildHeight() - 1, y + height);
        for (int h = y + 1; h <= max; h++) {
            BlockState s = level.getBlockState(pos.set(x, h, z));
            if (s.isAir() || !Terrain.cover(s) || !s.getFluidState().isEmpty() || Terrain.pondBeside(level, pos, x, h, z, outside)) continue;
            Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        }
    }
}
