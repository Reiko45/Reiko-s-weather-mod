package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Coastal dunes with slacks, like those at Newborough Warren on Anglesey or along the Dutch coast. Behind the beach the
 * onshore wind has heaped the sand into ridges running along the shore, one behind another, each a little older and
 * greyer than the one in front. Between them it has scoured the sand away, down and down, until it reached the water
 * table, where damp sand no longer blows; there it stopped, leaving flat hollows (slacks) just above the level of the sea.
 * They stay damp all year and flood in winter into shallow pools, and so are green, with turf and moss and a little mud,
 * reeds round the pools, wildflowers, and a few small birches and willows, in among the bare yellow sand of the ridges.
 *
 * @param floor  the slacks' floor, and their pools' water level (the water table)
 * @param angle  the way out to sea
 * @param length how far along the shore the dunes run each way from the middle
 * @param ridges {how far seaward of the middle its crest runs, height, half-width in tenths, wobble in tenths, wobble's
 *               wavelength, wobble's phase in thousandths of a radian} for each ridge, from the sea inland
 * @param pools  {which slack (the one behind which ridge), how far seaward of the slack's middle line in tenths, how far along
 *               the shore, half-length in tenths, half-width in tenths} for each pool
 * @param trees  {x, z, kind (0 birch, 1 willow), trunk height} for each tree
 */
public record DuneSlacks(int cx, int cz, int floor, float angle, int length, int[] ridges, int[] pools, int[] trees, long seed) implements Landform {
    private static final int RF = 6, PF = 5, TF = 4;
    /** The survey: every 10 blocks, out to 40 each way. */
    private static final int STEP = 10, GRID = 9;
    /** How much broader a ridge is on its gentle seaward side than on its steep landward one. */
    private static final float SEAWARD = 1.25F, LANDWARD = 0.8F;
    private static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel();
        int here = c.groundAt(c.x(), c.z());
        if (here < sea - 8 || here > sea + 8 || !c.fits(sea, 20, 30)) {
            return null;
        }
        // a quick look round first: the sea must be in reach one way and low land the other
        int mid = GRID / 2;
        boolean wet = here < sea - 2, dry = here >= sea && here <= sea + 8;
        for (int[] d : DIRS) {
            int g = c.groundAt(c.x() + d[0] * mid * STEP, c.z() + d[1] * mid * STEP);
            wet |= g < sea - 2;
            dry |= g >= sea && g <= sea + 8;
        }
        if (!wet || !dry) {
            return null;
        }
        // the lie of the land round about
        int[][] g = new int[GRID][GRID];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) g[i][j] = c.groundAt(c.x() + STEP * (i - mid), c.z() + STEP * (j - mid));
        }
        // which way the sea is: from the middle of the land to the middle of the sea
        float lx = 0.0F, lz = 0.0F, sx = 0.0F, sz = 0.0F;
        int ln = 0, sn = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                if (g[i][j] < sea - 2) {
                    sx += i;
                    sz += j;
                    sn++;
                } else if (g[i][j] >= sea) {
                    lx += i;
                    lz += j;
                    ln++;
                }
            }
        }
        if (sn == 0 || ln == 0) {
            return null;
        }
        float angle = (float) Math.atan2(sz / sn - lz / ln, sx / sn - lx / ln);
        float ca = Mth.cos(angle), sa = Mth.sin(angle);
        int x0 = c.x() - mid * STEP, z0 = c.z() - mid * STEP;
        // the middle of the dunes: on low land near the start, with the shore not far seaward and low land all along behind it
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int far = Mth.square(i - mid) + Mth.square(j - mid);
                if (far <= 9 && g[i][j] >= sea && g[i][j] <= sea + 4) cells.add(new int[]{far, i, j});
            }
        }
        cells.sort((p, q) -> p[0] != q[0] ? p[0] - q[0] : p[1] != q[1] ? p[1] - q[1] : p[2] - q[2]);
        for (int[] cell : cells) {
            int ox = x0 + STEP * cell[1], oz = z0 + STEP * cell[2];
            int shore = -1;
            for (int u = 4; u <= 40 && shore < 0; u += 3) {
                if (between(g, x0, z0, ox + ca * u, oz + sa * u) < sea - 1.5F) shore = u;
            }
            if (shore < 18 || shore > 36) continue;
            int bad = 0, all = 0;
            List<Float> low = new ArrayList<>();
            for (int u = -44; u <= shore - 8; u += 8) {
                for (int v = -32; v <= 32; v += 16) {
                    float h = between(g, x0, z0, ox + ca * u - sa * v, oz + sa * u + ca * v);
                    all++;
                    if (h < sea - 1.5F || h > sea + 7.5F) bad++;
                    else low.add(h);
                }
            }
            if (bad * 6 > all) continue;
            // the beach runs on past both ends, roughly straight
            boolean straight = true;
            for (int v = -32; v <= 32; v += 64) {
                float land = between(g, x0, z0, ox + ca * (shore - 10) - sa * v, oz + sa * (shore - 10) + ca * v);
                float water = between(g, x0, z0, ox + ca * (shore + 10) - sa * v, oz + sa * (shore + 10) + ca * v);
                straight &= land >= sea - 1.5F && water < sea - 1.5F;
            }
            if (!straight) continue;
            low.sort(Float::compare);
            return layout(c, ox, oz, angle, shore, low.get(low.size() / 2) >= sea + 3 ? sea + 1 : sea);
        }
        return null;
    }

    /** The ridges, slacks, pools and trees, for dunes whose foredune stands a little back from the shore {@code shore} blocks seaward of (x, z). */
    private static Landform layout(PlanContext c, int x, int z, float angle, int shore, int floor) {
        int length = 30 + c.random().nextInt(14);
        // the foredune just back from the beach, then older ridges inland
        List<int[]> ridges = new ArrayList<>();
        float u = shore - 7 - c.random().nextInt(3);
        for (int k = 0; k < 4; k++) {
            int h = k == 0 ? 5 + c.random().nextInt(4) : 4 + c.random().nextInt(7);
            int w = 30 + 5 * h + c.random().nextInt(10);
            int wobble = 5 + c.random().nextInt(11), wave = 30 + c.random().nextInt(30), phase = c.random().nextInt(6283);
            // (no further inland than the bounds can reach)
            if (k >= 2 && u - 5.0F - (wobble + w * LANDWARD) / 10.0F < -56.0F) break;
            ridges.add(new int[]{Math.round(u), h, w, wobble, wave, phase});
            u -= 21 + c.random().nextInt(6);
        }
        int[] rs = new int[ridges.size() * RF];
        for (int k = 0; k < ridges.size(); k++) System.arraycopy(ridges.get(k), 0, rs, k * RF, RF);
        // a pool or three along each slack, where the water table stands above its floor
        List<int[]> pools = new ArrayList<>();
        for (int s = 0; s < ridges.size() - 1; s++) {
            int n = 1 + c.random().nextInt(3);
            for (int tries = 0; tries < 6 && n > 0; tries++) {
                int half = 30 + c.random().nextInt(45), wide = 20 + c.random().nextInt(16);
                int v = Math.round((c.random().nextFloat() * 2.0F - 1.0F) * (length - 14 - half / 10));
                int across = Math.round((c.random().nextFloat() - 0.5F) * 30.0F);
                boolean clear = true;
                for (int[] p : pools) if (p[0] == s && Math.abs(p[2] - v) < (p[3] + half) / 10 + 3) clear = false;
                if (!clear) continue;
                pools.add(new int[]{s, across, v, half, wide});
                n--;
            }
        }
        int[] ps = new int[pools.size() * PF];
        for (int k = 0; k < pools.size(); k++) System.arraycopy(pools.get(k), 0, ps, k * PF, PF);
        long seed = c.random().nextLong();
        DuneSlacks probe = new DuneSlacks(x, z, floor, angle, length, rs, ps, new int[0], seed);
        // a few small trees on the slack floors, out of the pools (each wholly inside a chunk, so its crown is built with its trunk)
        List<int[]> trees = new ArrayList<>();
        int want = 2 + c.random().nextInt(4);
        for (int tries = 0; tries < 14 && trees.size() < want; tries++) {
            int s = c.random().nextInt(ridges.size() - 1);
            float v = (c.random().nextFloat() * 2.0F - 1.0F) * (length - 12), du = (c.random().nextFloat() - 0.5F) * 5.0F;
            int kind = c.random().nextInt(2), height = kind == 0 ? 4 + c.random().nextInt(3) : 3 + c.random().nextInt(2);
            double[] p = Terrain.xz(probe.middle(s, v) + du, v, x + 0.5, z + 0.5, angle);
            int tx = inChunk(Mth.floor(p[0])), tz = inChunk(Mth.floor(p[1]));
            boolean ok = probe.flat(tx, tz);
            for (int dx = -2; dx <= 2 && ok; dx++) {
                for (int dz = -2; dz <= 2 && ok; dz++) ok = probe.pool(tx + dx, tz + dz) == 0;
            }
            for (int[] t : trees) ok &= Mth.square(t[0] - tx) + Mth.square(t[1] - tz) >= 49;
            if (ok) trees.add(new int[]{tx, tz, kind, height});
        }
        int[] ts = new int[trees.size() * TF];
        for (int k = 0; k < trees.size(); k++) System.arraycopy(trees.get(k), 0, ts, k * TF, TF);
        return new DuneSlacks(x, z, floor, angle, length, rs, ps, ts, seed);
    }

    /** The ground at a point, read between the survey's points. */
    private static float between(int[][] g, int x0, int z0, float x, float z) {
        float gx = Mth.clamp((x - x0) / STEP, 0.0F, GRID - 1.001F), gz = Mth.clamp((z - z0) / STEP, 0.0F, GRID - 1.001F);
        int i = Mth.floor(gx), j = Mth.floor(gz);
        float fx = gx - i, fz = gz - j;
        return Mth.lerp(fz, Mth.lerp(fx, g[i][j], g[i + 1][j]), Mth.lerp(fx, g[i][j + 1], g[i + 1][j + 1]));
    }

    /** Moves a coordinate a little, if need be, to keep a tree's crown inside its chunk. */
    private static int inChunk(int v) {
        int m = Math.floorMod(v, 16);
        return m < 3 ? v + 3 - m : m > 12 ? v - (m - 12) : v;
    }

    static Landform load(CompoundTag tag) {
        return new DuneSlacks(tag.getInt("x"), tag.getInt("z"), tag.getInt("floor"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("ridges"),
                tag.getIntArray("pools"), tag.getIntArray("trees"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("floor", floor);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("ridges", new IntArrayTag(ridges));
        tag.put("pools", new IntArrayTag(pools));
        tag.put("trees", new IntArrayTag(trees));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DUNE_SLACKS;
    }

    @Override
    public BoundingBox bounds() {
        int last = (ridges.length / RF - 1) * RF;
        float hi = ridges[0] + 5.0F + (ridges[3] + ridges[2] * SEAWARD) / 10.0F + 1.0F;
        float lo = ridges[last] - 5.0F - (ridges[last + 3] + ridges[last + 2] * LANDWARD) / 10.0F - 1.0F;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{lo, hi}) {
            for (float v : new float[]{-length - 5.0F, length + 5.0F}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, Mth.floor(p[0]) - 1);
                maxX = Math.max(maxX, Mth.floor(p[0]) + 1);
                minZ = Math.min(minZ, Mth.floor(p[1]) - 1);
                maxZ = Math.max(maxZ, Mth.floor(p[1]) + 1);
            }
        }
        return new BoundingBox(minX, floor - 16, minZ, maxX, floor + 26, maxZ);
    }

    /** Where a ridge's crest runs, at {@code v} along the shore: all of them swing together, each with a wobble of its own. */
    private float crest(int k, float v) {
        int o = k * RF;
        float together = (2.0F + 3.0F * Terrain.hash(seed, 1)) * Mth.sin(v * Mth.TWO_PI / (50.0F + 40.0F * Terrain.hash(seed, 2)) + Terrain.hash(seed, 3) * Mth.TWO_PI);
        return ridges[o] + together + ridges[o + 3] / 10.0F * Mth.sin(v * Mth.TWO_PI / ridges[o + 4] + ridges[o + 5] / 1000.0F);
    }

    /** The middle line of the slack behind ridge {@code s}. */
    private float middle(int s, float v) {
        return (crest(s, v) + crest(s + 1, v)) / 2.0F;
    }

    /** How high ridge {@code k} stands over the slacks at (u, v): a gentle seaward slope and a steeper landward one, hummocky along its crest. */
    private float ridge(int k, float u, float v) {
        int o = k * RF;
        float t = u - crest(k, v), half = ridges[o + 2] / 10.0F * (t > 0.0F ? SEAWARD : LANDWARD);
        if (Math.abs(t) >= half) return 0.0F;
        float p = 1.0F - Mth.square(t / half);
        return ridges[o + 1] * (0.75F + 0.4F * Terrain.field(seed + 30 + k, v, k * 97.0, 9.0)) * p * p;
    }

    /** {u, v, the dunes' height over the slack floor (tapered off at the ends), taper, which way it lies: 0 in a slack, 1 seaward, -1 landward, 2 outside}. */
    private float[] at(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        float u = l[0], v = l[1];
        // (dying away raggedly at the ends)
        float ends = 7.0F * (Terrain.field(seed + 50 + (v > 0.0F ? 1 : 0), u, 0.0, 9.0) - 0.5F);
        float taper = Terrain.smooth((length + ends - Math.abs(v)) / 10.0F);
        int last = ridges.length / RF - 1;
        if (taper <= 0.0F) return new float[]{u, v, 0.0F, 0.0F, 2.0F};
        float first = crest(0, v), back = crest(last, v);
        float side;
        if (u >= first) side = u - first < ridges[2] / 10.0F * SEAWARD ? 1.0F : 2.0F;
        else if (u <= back) side = back - u < ridges[last * RF + 2] / 10.0F * LANDWARD ? -1.0F : 2.0F;
        else side = 0.0F;
        if (side == 2.0F) return new float[]{u, v, 0.0F, 0.0F, 2.0F};
        float r = 0.0F;
        for (int k = 0; k <= last; k++) r = Math.max(r, ridge(k, u, v));
        return new float[]{u, v, r * taper, taper, side};
    }

    /** Whether a column is part of a slack's flat floor, well in from the ends of the dunes. */
    private boolean flat(int x, int z) {
        float[] a = at(x, z);
        return a[4] == 0.0F && a[3] >= 1.0F && a[2] < 0.5F;
    }

    /** How deep the pool is at a column, if it's in one (only where the floor round it is all flat, so it can't spill). */
    int pool(int x, int z) {
        if (pools.length == 0 || !flat(x, z)) return 0;
        float[] a = at(x, z);
        for (int p = 0; p < pools.length / PF; p++) {
            int o = p * PF;
            float du = (a[0] - middle(pools[o], a[1]) - pools[o + 1] / 10.0F) / (pools[o + 4] / 10.0F);
            float dv = (a[1] - pools[o + 2]) / (pools[o + 3] / 10.0F);
            float wob = Mth.square(0.85F + 0.3F * Terrain.field(seed + 40 + p, x, z, 3.0)), e = du * du + dv * dv;
            if (e >= wob) continue;
            if (!flat(x + 1, z) || !flat(x - 1, z) || !flat(x, z + 1) || !flat(x, z - 1)) return 0;
            return e < 0.35F * wob ? 2 : 1;
        }
        return 0;
    }

    private int tree(int x, int z) {
        for (int k = 0; k < trees.length; k += TF) if (trees[k] == x && trees[k + 1] == z) return k;
        return -1;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // (the flanks outside the slacks never lose any water, so a pond there is left as it is, and banked against)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> at(nx, nz)[4] != 0.0F;
        BlockState sand = Blocks.SAND.defaultBlockState(), sandstone = Blocks.SANDSTONE.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] a = at(x, z);
                if (a[4] == 2.0F) continue;
                boolean slack = a[4] == 0.0F;
                float dune = a[2], taper = a[3];
                int ground = Terrain.ground(level, pos, x, z);
                int surface = Terrain.surface(level, pos, x, ground, z);
                float target;
                if (slack) {
                    target = floor + dune;
                } else {
                    // the outer flanks: sand heaped on the ground, rising from the beach (or the land behind) to the crest
                    if (surface > ground) continue;
                    int k = a[4] > 0.0F ? 0 : ridges.length / RF - 1;
                    float t = Terrain.smooth(Math.abs(a[0] - crest(k, a[1])) / (ridges[k * RF + 2] / 10.0F * (a[4] > 0.0F ? SEAWARD : LANDWARD)));
                    target = Math.max(ground, Mth.lerp(t, floor, ground) + dune);
                }
                int top = Math.round(Mth.lerp(taper, ground, target));
                // (and never down in deep water off the ends, below where the sand could be held up)
                if (top == ground && (!slack || surface == ground && taper < 0.6F) || top - 2 < b.minY()) continue;
                int high = Math.min(Math.max(ground, surface), b.maxY());
                if (slack) {
                    // where a pond just outside comes up against it, keep the ground banked up to hold it in
                    for (int h = high; h > top; h--) {
                        if (Terrain.pondBeside(level, pos, x, h, z, outside)) {
                            top = h;
                            break;
                        }
                    }
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(16, b.maxY() - Math.max(ground, top)));
                int depth = slack && top == floor ? pool(x, z) : 0;
                // (anything but the slacks' flat floors is sand)
                boolean sandy = !slack || top != floor;
                int lo = Math.max(b.minY(), Math.min(ground, top - depth) - 2);
                BlockState skin = sandy || depth > 0 ? sand : floor(x, z, a[2]);
                for (int h = lo; h <= top; h++) {
                    int d = top - h;
                    BlockState s;
                    if (sandy) {
                        // sand over sandstone
                        s = d <= 1 ? sand : sandstone;
                    } else if (depth > 0) {
                        s = d < depth ? Blocks.WATER.defaultBlockState() : d == depth ? bed(x, z) : Blocks.DIRT.defaultBlockState();
                    } else {
                        s = d == 0 ? skin : d == 1 && !skin.is(Blocks.SAND) ? Blocks.DIRT.defaultBlockState() : sandstone;
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                // dig out above, but never let the sea in (the sea's own water is left where it is)
                for (int h = Math.max(top + 1, level.getSeaLevel()); h <= high; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                plants(level, pos, x, z, top, a, sandy, depth);
            }
        }
        // the trees, once the slacks under them are in
        for (int k = 0; k < trees.length; k += TF) {
            int x = trees[k], z = trees[k + 1];
            if (!Terrain.column(area, x, z) || !level.getBlockState(pos.set(x, floor, z)).is(Blocks.GRASS_BLOCK)) continue;
            if (trees[k + 2] == 0) birch(level, area, pos, x, z, trees[k + 3]);
            else willow(level, area, pos, x, z, trees[k + 3]);
        }
    }

    /** The floor of a slack: mostly turf, mossy where it's dampest, sand and a little mud round the pools. */
    private BlockState floor(int x, int z, float dune) {
        if (tree(x, z) >= 0) return Blocks.GRASS_BLOCK.defaultBlockState();
        float n = Terrain.noise(seed + 20, x, floor, z);
        boolean bank = false;
        for (int dx = -1; dx <= 1 && !bank; dx++) {
            for (int dz = -1; dz <= 1 && !bank; dz++) bank = (dx != 0 || dz != 0) && pool(x + dx, z + dz) > 0;
        }
        if (bank) return (n < 0.3F ? Blocks.SAND : n < 0.45F ? Blocks.MUD : Blocks.GRASS_BLOCK).defaultBlockState();
        if (dune > 0.2F && n < 0.6F) return Blocks.SAND.defaultBlockState();
        if (Terrain.field(seed + 21, x, z, 5.0) > 0.63F) return Blocks.MOSS_BLOCK.defaultBlockState();
        return (n < 0.025F ? Blocks.MUD : Blocks.GRASS_BLOCK).defaultBlockState();
    }

    /** The bottom of a pool: soft mud, clay or sand. */
    private BlockState bed(int x, int z) {
        float n = Terrain.noise(seed + 22, x, floor, z);
        return (n < 0.5F ? Blocks.MUD : n < 0.75F ? Blocks.CLAY : Blocks.SAND).defaultBlockState();
    }

    private void plants(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, float[] a, boolean sandy, int depth) {
        float n = Terrain.noise(seed + 23, x, top, z);
        if (sandy) {
            // a few dead bushes on the crests, where nothing else takes hold in the shifting sand
            if (a[2] >= 3.0F && n < 0.035F && top > floor + 2 && level.getBlockState(pos.set(x, top, z)).is(Blocks.SAND)) {
                Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
            return;
        }
        if (depth > 0) {
            if (depth == 2 && n < 0.08F) Terrain.set(level, pos, x, top + 1, z, Blocks.LILY_PAD.defaultBlockState());
            return;
        }
        if (tree(x, z) >= 0) return;
        // reeds round the pools (sugar cane, wherever there's water beside the block it grows from)
        boolean shore = pool(x + 1, z) > 0 || pool(x - 1, z) > 0 || pool(x, z + 1) > 0 || pool(x, z - 1) > 0;
        if (shore && n < 0.55F) {
            int tall = 1 + Math.round(Terrain.noise(seed + 24, x, 0, z) * 2.0F);
            for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, top + h, z, Blocks.SUGAR_CANE.defaultBlockState());
            return;
        }
        BlockState below = level.getBlockState(pos.set(x, top, z));
        if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.MOSS_BLOCK)) return;
        BlockState plant = null;
        if (n < 0.3F) plant = Blocks.SHORT_GRASS.defaultBlockState();
        else if (n < (below.is(Blocks.MOSS_BLOCK) ? 0.45F : 0.35F)) plant = Blocks.FERN.defaultBlockState();
        else if (n < 0.42F) {
            // the wildflowers: marsh orchids, grass of Parnassus, bird's-foot trefoil and the like
            float f = Terrain.field(seed + 25, x, z, 6.0);
            plant = (f < 0.25F ? Blocks.ALLIUM : f < 0.45F ? Blocks.OXEYE_DAISY : f < 0.65F ? Blocks.DANDELION : f < 0.85F ? Blocks.AZURE_BLUET
                    : Blocks.PINK_TULIP).defaultBlockState();
        }
        if (plant != null) Terrain.set(level, pos, x, top + 1, z, plant);
    }

    /** A small birch: a slim white trunk and a narrow crown. */
    private void birch(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int z, int height) {
        BlockState leaves = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int h = 1; h <= height; h++) Terrain.set(level, pos, x, floor + h, z, Blocks.BIRCH_LOG.defaultBlockState());
        int crown = floor + height;
        for (int dy = -2; dy <= 1; dy++) {
            int rad = dy < 0 ? 2 : 1;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    // (rounded off at the corners, and ragged)
                    if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2 || dy == 1 && Math.abs(dx) + Math.abs(dz) > 1) continue;
                    if (rad == 2 && Math.abs(dx) + Math.abs(dz) >= 3 && Terrain.noise(seed + 26, x + dx, crown + dy, z + dz) < 0.5F) continue;
                    leaf(level, area, pos, x + dx, crown + dy, z + dz, leaves);
                }
            }
        }
    }

    /** A willow-like tree: a short, stout trunk and a broad crown hanging down round its edges. */
    private void willow(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int z, int height) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int h = 1; h <= height; h++) Terrain.set(level, pos, x, floor + h, z, Blocks.OAK_LOG.defaultBlockState());
        int crown = floor + height;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = dx * dx + dz * dz;
                if (d > 10) continue;
                if (d > 0) leaf(level, area, pos, x + dx, crown, z + dz, leaves);
                if (d <= 5) leaf(level, area, pos, x + dx, crown + 1, z + dz, leaves);
                if (d <= 1) leaf(level, area, pos, x + dx, crown + 2, z + dz, leaves);
                if (d > 4) {
                    // the trailing fringe
                    int hang = 1 + Math.round(Terrain.noise(seed + 27, x + dx, 0, z + dz) * 1.4F);
                    for (int h = 1; h <= hang; h++) leaf(level, area, pos, x + dx, crown - h, z + dz, leaves);
                }
            }
        }
    }

    private static void leaf(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState leaves) {
        if (area.isInside(x, y, z) && level.getBlockState(pos.set(x, y, z)).isAir()) level.setBlock(pos, leaves, Terrain.FLAGS);
    }
}
