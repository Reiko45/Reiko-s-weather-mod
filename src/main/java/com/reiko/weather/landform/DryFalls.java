package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Dry falls: the cliffs of a giant waterfall with no water left in it, like Dry Falls in the channelled scablands of
 * eastern Washington. Again and again at the end of the last Ice Age, the ice dam holding back a vast glacial lake gave
 * way and the whole lake drained across the plateau in a few days, in floods that dwarfed any river on Earth. Where
 * they poured over the edge of the basalt plateau they plucked the jointed rock away block by block, and the falls ate
 * their way back upstream into a great scalloped horseshoe of sheer cliffs, several times as wide as Niagara and more
 * than twice as high. When the ice that had turned a river over them melted, the river went back to its old course, and
 * the falls were left dry: an amphitheatre of bare, banded cliffs, flow upon flow of dark basalt with thin beds of ash
 * between, with rubble heaped at their feet and still lakes lying in the plunge pools the water dug at their foot.
 * Below, the floor runs on downstream as a broad, flat-bottomed, steep-walled channel, a coulee, opening out onto the
 * lowland. Up on the plateau, the old lip of the falls is bare rock, scoured clean of soil, grooved by the flood and
 * drilled with potholes.
 *
 * @param angle  the way the floods flowed (downstream, out through the coulee)
 * @param radius the rough radius of the horseshoe of cliffs
 * @param floor  the floor of the amphitheatre and the coulee
 * @param rim    the plateau's height round the cliffs
 * @param mouth  how far downstream of the middle the coulee comes out onto the low ground
 * @param lip    how far back from the cliffs the bare rock of the old lip reaches
 * @param pools  {u, v, radius, depth} of each plunge pool, from the middle along the flow and across it
 * @param top    the highest ground sampled (for the bounds)
 */
public record DryFalls(int cx, int cz, float angle, int radius, int floor, int rim, int mouth, int lip, int[] pools, int top, long seed) implements Landform {
    /** How far the coulee runs on beyond its mouth as it fades out, and how thick the band of rock rebuilt behind the cliff face is. */
    private static final int FLARE = 12, WALL = 3;
    /** The survey: how many points each way, and how far apart. */
    private static final int GRID = 9, SPACING = 16;
    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1}, DZ = {0, 1, 1, 1, 0, -1, -1, -1};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || !c.fits(ground, 50, 60)) {
            return null;
        }
        int mid = GRID / 2;
        int[][] g = new int[GRID][GRID];
        for (int[] row : g) Arrays.fill(row, Integer.MIN_VALUE);
        g[mid][mid] = ground;
        // a first look 48 blocks out all round: there's no escarpment here unless the ground falls a good way somewhere
        int lo = ground, hi = ground;
        for (int k = 0; k < 8; k++) {
            int i = mid + 3 * DX[k], j = mid + 3 * DZ[k];
            g[i][j] = c.groundAt(c.x() + SPACING * (i - mid), c.z() + SPACING * (j - mid));
            lo = Math.min(lo, g[i][j]);
            hi = Math.max(hi, g[i][j]);
        }
        if (hi - lo < 14) {
            return null;
        }
        // then the lie of the land, every 16 blocks out to 64 each way
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                if (g[i][j] == Integer.MIN_VALUE) g[i][j] = c.groundAt(c.x() + SPACING * (i - mid), c.z() + SPACING * (j - mid));
            }
        }
        // the likeliest places for the falls: the edge of high, level ground, with plateau behind and either side, and low ground ahead
        List<float[]> sites = new ArrayList<>();
        for (int i = mid - 2; i <= mid + 2; i++) {
            for (int j = mid - 2; j <= mid + 2; j++) {
                int h = g[i][j];
                if (h < c.seaLevel() + 8) continue;
                for (int k = 0; k < 8; k++) {
                    int dx = DX[k], dz = DZ[k];
                    int b1 = node(g, i - dx, j - dz), b2 = node(g, i - 2 * dx, j - 2 * dz);
                    int behind = b2 == Integer.MIN_VALUE ? b1 : Math.min(b1, b2), most = Math.max(b1, b2);
                    if (b1 == Integer.MIN_VALUE || behind < h - 4 || most > h + 14) continue;
                    int s1 = node(g, i - dz, j + dx), s2 = node(g, i + dz, j - dx);
                    if (s1 == Integer.MIN_VALUE || s2 == Integer.MIN_VALUE || Math.min(s1, s2) < h - 8) continue;
                    int a2 = node(g, i + 2 * dx, j + 2 * dz), a3 = node(g, i + 3 * dx, j + 3 * dz), a4 = node(g, i + 4 * dx, j + 4 * dz);
                    if (a2 == Integer.MIN_VALUE || a3 == Integer.MIN_VALUE) continue;
                    int ahead = Math.min(a2, a4 == Integer.MIN_VALUE ? a3 : Math.min(a3, a4));
                    if (h - ahead < 15) continue;
                    float off = Mth.sqrt(Mth.square(i - mid) + Mth.square(j - mid)) * SPACING;
                    float score = Math.min(h - ahead, 35) - 0.5F * Math.max(0, h - behind) - 0.3F * Math.abs(Math.min(s1, s2) - h) - 0.08F * off;
                    sites.add(new float[]{score, i, j, k});
                }
            }
        }
        sites.sort((p, q) -> Float.compare(q[0], p[0]));
        int tried = 0;
        long last = Long.MIN_VALUE;
        for (float[] s : sites) {
            long here = (long) s[1] * GRID + (long) s[2];
            if (here == last) continue;
            last = here;
            Landform falls = falls(c, c.x() + SPACING * ((int) s[1] - mid), c.z() + SPACING * ((int) s[2] - mid), s[3] * Mth.PI / 4.0F);
            if (falls != null) return falls;
            if (++tried >= 3) break;
        }
        return null;
    }

    private static int node(int[][] g, int i, int j) {
        return i < 0 || j < 0 || i >= GRID || j >= GRID ? Integer.MIN_VALUE : g[i][j];
    }

    /** Falls cut back into the plateau's edge at a spot, with the floods flowing roughly a way (or none, if it won't take them). */
    @Nullable
    private static Landform falls(PlanContext c, int ox, int oz, float rough) {
        int here = c.groundAt(ox, oz);
        // the way the land falls, more exactly
        float angle = rough;
        int lowest = Integer.MAX_VALUE;
        for (int k = -2; k <= 2; k++) {
            float a = rough + k * Mth.PI / 12.0F;
            int h = at(c, ox, oz, a, 40, 0);
            if (h < lowest) {
                lowest = h;
                angle = a;
            }
        }
        int radius = 25 + c.random().nextInt(16);
        // the plateau round the back and the sides, and the low ground the coulee will open onto
        int back = at(c, ox, oz, angle, -radius * 0.7F, 0), left = at(c, ox, oz, angle, -radius * 0.5F, -radius * 0.65F);
        int right = at(c, ox, oz, angle, -radius * 0.5F, radius * 0.65F), side0 = at(c, ox, oz, angle, 0, -radius), side1 = at(c, ox, oz, angle, 0, radius);
        int[] plateau = {here, back, left, right, side0, side1};
        int top = here;
        for (int p : plateau) top = Math.max(top, p);
        Arrays.sort(plateau);
        int rim = plateau[3];
        int[] line = new int[7];
        int low = lowest;
        for (int k = 0; k < line.length; k++) {
            line[k] = at(c, ox, oz, angle, 8 + 8 * k, 0);
            low = Math.min(low, line[k]);
        }
        if (rim - low < 15) {
            return null;
        }
        // the cliffs stand 20 to 35 high, down to about the low ground
        int floor = Mth.clamp(low - 1, rim - 35, rim - 20);
        if (floor < c.seaLevel() + 1 || Math.min(back, Math.min(left, right)) < floor + 14 || Math.min(side0, side1) < floor + 10) {
            return null;
        }
        // (and the plateau going on behind the lip, not just a ridge the falls would have cut straight through)
        int beyond = at(c, ox, oz, angle, -(radius * 1.2F + 8.0F), 0);
        if (beyond < rim - 10) {
            return null;
        }
        top = Math.max(top, beyond);
        // the coulee's mouth: where its floor comes out level with the low ground (and the ground stays low beyond)
        int mouth = -1;
        for (int k = 0; k < line.length && mouth < 0; k++) {
            if (line[k] <= floor + 3) mouth = 8 + 8 * k;
        }
        if (mouth < 0 || mouth > 48 || at(c, ox, oz, angle, mouth + FLARE, 0) > floor + 6) {
            return null;
        }
        int lip = 8 + c.random().nextInt(5);
        long seed = c.random().nextLong();
        DryFalls shape = new DryFalls(ox, oz, angle, radius, floor, rim, mouth, lip, new int[0], top, seed);
        // the plunge pools, dug by the falls at the foot of the cliffs round the back
        int want = 1 + c.random().nextInt(3);
        int[] pools = new int[0];
        for (int t = 0; t < want * 3 && pools.length / 4 < want; t++) {
            float theta = Mth.PI + (c.random().nextFloat() - 0.5F) * 2.2F;
            int r = 4 + c.random().nextInt(4), depth = 3 + c.random().nextInt(4);
            float near = shape.reach(theta);
            for (int k = 1; k <= 4; k++) near = Math.min(near, Math.min(shape.reach(theta - k * 0.1F), shape.reach(theta + k * 0.1F)));
            float d = radius * near - r - 6.0F;
            float pu = Mth.cos(theta) * d, pv = Mth.sin(theta) * d;
            boolean clear = d > r + 2;
            for (int i = 0; i < pools.length && clear; i += 4) {
                clear = Mth.sqrt(Mth.square(pu - pools[i]) + Mth.square(pv - pools[i + 1])) > r + pools[i + 2] + 4;
            }
            // (on ground the cut goes well down into, not a hollow already lower than the floor)
            if (!clear || at(c, ox, oz, angle, pu, pv) < floor + 4) continue;
            pools = Arrays.copyOf(pools, pools.length + 4);
            pools[pools.length - 4] = Math.round(pu);
            pools[pools.length - 3] = Math.round(pv);
            pools[pools.length - 2] = r;
            pools[pools.length - 1] = depth;
        }
        if (pools.length == 0) {
            return null;
        }
        DryFalls falls = new DryFalls(ox, oz, angle, radius, floor, rim, mouth, lip, pools, top, seed);
        BoundingBox b = falls.bounds();
        int reach = Math.max(Math.max(c.x() - b.minX(), b.maxX() - c.x()), Math.max(c.z() - b.minZ(), b.maxZ() - c.z()));
        return reach <= 108 ? falls : null;
    }

    /** The ground {@code u} blocks downstream and {@code v} across from a point. */
    private static int at(PlanContext c, int ox, int oz, float angle, float u, float v) {
        double[] p = Terrain.xz(u, v, ox + 0.5, oz + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new DryFalls(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("radius"), tag.getInt("floor"), tag.getInt("rim"),
                tag.getInt("mouth"), tag.getInt("lip"), tag.getIntArray("pools"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("radius", radius);
        tag.putInt("floor", floor);
        tag.putInt("rim", rim);
        tag.putInt("mouth", mouth);
        tag.putInt("lip", lip);
        tag.put("pools", new IntArrayTag(pools));
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DRY_FALLS;
    }

    @Override
    public BoundingBox bounds() {
        float most = 1.0F;
        for (int k = 0; k < 72; k++) most = Math.max(most, reach(k * Mth.TWO_PI / 72.0F));
        // (the jagged face stands out up to 1.8 blocks either way)
        float out = WALL + 2.0F, ring = lip * 1.3F + out;
        // the horseshoe and the bare lip round its back, and the coulee running on downstream
        float[][] boxes = {{-(radius * most + ring), radius * 0.3F + 1.0F, -(radius + ring), radius + ring},
                {0.0F, mouth + FLARE + 1.0F, -(radius + FLARE * 0.9F + out), radius + FLARE * 0.9F + out}};
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float[] box : boxes) {
            for (int k = 0; k < 4; k++) {
                double[] p = Terrain.xz(box[k & 1], box[2 + (k >> 1)], cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        return new BoundingBox(Mth.floor(minX) - 1, floor - 10, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, top + 30, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    private static float wrap(float a) {
        return a - Mth.TWO_PI * Mth.floor(a / Mth.TWO_PI + 0.5F);
    }

    /**
     * How far back the cliffs stand at a bearing from the middle (0 downstream), as a share of the radius: round the back,
     * the falls retreated faster in some places than others and left the cliffs scalloped into alcoves.
     */
    private float reach(float theta) {
        float back = Terrain.smooth((Math.abs(wrap(theta)) - Mth.HALF_PI) / 0.5F);
        if (back <= 0.0F) return 1.0F;
        int count = 2 + Math.round(Terrain.hash(seed, 1));
        float r = 0.0F;
        for (int k = 0; k < count; k++) {
            float at = Mth.PI + (k - (count - 1) * 0.5F) * (1.9F / count) + (Terrain.hash(seed, 2 + k) - 0.5F) * 0.25F;
            float d = Math.abs(wrap(theta - at));
            r = Math.max(r, (0.1F + 0.1F * Terrain.hash(seed, 6 + k)) * Terrain.smooth(1.0F - d / 0.45F));
        }
        return 1.0F + r * back;
    }

    /** How far a column stands back from the foot of the cliffs (negative inside the cut). */
    private float edge(int x, int z, float u, float v) {
        if (u > mouth + FLARE) return 99.0F;
        float wobble = 2.4F * (Terrain.field(seed + 1, x, z, 7.0) - 0.5F) + 1.2F * (Terrain.field(seed + 2, x, z, 2.5) - 0.5F);
        if (u <= 0.0F) return Mth.sqrt(u * u + v * v) - radius * reach((float) Mth.atan2(v, u)) - wobble;
        float half = radius * (1.0F - 0.1F * Terrain.smooth(u / 40.0F)) + Math.max(0.0F, u - mouth) * 0.9F;
        return Math.abs(v) - half - wobble;
    }

    /** Whether a column is on the bare rock of the old lip, round the back of the horseshoe. */
    private boolean lipAt(int x, int z, float u, float e) {
        if (e < WALL || u > radius * 0.3F) return false;
        float width = lip * (0.7F + 0.6F * Terrain.field(seed + 5, x, z, 8.0));
        if (u > 0.0F) width *= Terrain.smooth((radius * 0.3F - u) / (radius * 0.3F));
        return e < WALL + width;
    }

    /** Whether the landform fills, lowers or rebuilds a column. */
    private boolean touched(int x, int z) {
        float[] l = uv(x, z);
        float e = edge(x, z, l[0], l[1]);
        return e < WALL || lipAt(x, z, l[0], e) || banked(x, z, l[0], l[1]);
    }

    /** Whether a column is in a plunge pool or the banks that hold it in. */
    private boolean banked(int x, int z, float u, float v) {
        float[] p = pool(x, z, u, v);
        return p[0] >= 0.0F && p[1] < 1.0F + 2.5F / pools[(int) p[0] * 4 + 2];
    }

    private static boolean skipped(int ground, BoundingBox b) {
        return ground >= b.maxY() - 4 || ground <= b.minY() + 2;
    }

    /** {index, how far out from its middle as a share of its radius} of the plunge pool nearest a column ({-1, far} if none). */
    private float[] pool(int x, int z, float u, float v) {
        float best = 1.0E6F, which = -1;
        for (int i = 0; i < pools.length / 4; i++) {
            float d = Mth.sqrt(Mth.square(u - pools[i * 4]) + Mth.square(v - pools[i * 4 + 1]))
                    / (pools[i * 4 + 2] * (0.88F + 0.24F * Terrain.field(seed + 7, x, z, 5.0)));
            if (d < best) {
                best = d;
                which = i;
            }
        }
        return new float[]{which, best};
    }

    /** How high the rubble fallen from the cliffs is heaped over the floor at a column. */
    private int talus(int x, int z, float e, float u, int high, float pool) {
        if (u > mouth - 4 || high < floor + 6) return 0;
        float width = 4.0F + 7.0F * Terrain.field(seed + 3, x, z, 9.0), k = 1.0F + e / width;
        if (k <= 0.0F) return 0;
        float heap = (rim - floor) * 0.42F * (float) Math.pow(k, 1.5) * (0.45F + 0.55F * Terrain.field(seed + 4, x, z, 5.0));
        // (kept back from the edges of the plunge pools)
        heap *= Terrain.smooth((pool - 1.25F) / 0.8F);
        return Math.min(high - floor, Math.round(heap));
    }

    /** How deep a channel the floods scoured into the lip runs at a column (they flowed straight over it, downstream). */
    private int groove(float u, float v) {
        int count = 3 + Math.round(Terrain.hash(seed, 10) * 3.0F);
        for (int k = 0; k < count; k++) {
            float at = (Terrain.hash(seed, 11 + k) - 0.5F) * 1.5F * radius, wiggle = 1.6F * Mth.sin(u * 0.13F + k * 1.7F);
            if (Math.abs(v - at - wiggle) < 0.7F + 0.6F * Terrain.hash(seed, 17 + k)) return Terrain.field(seed + 20 + k, u, k * 13.0, 7.0) > 0.5F ? 2 : 1;
        }
        return 0;
    }

    /** The potholes drilled into the lip by boulders whirled round in eddies of the flood: {u, v, radius, depth}. */
    private float[][] potholes() {
        int count = 4 + Math.round(Terrain.hash(seed, 30) * 5.0F);
        float[][] holes = new float[count][];
        for (int i = 0; i < count; i++) {
            float theta = Mth.PI + (Terrain.hash(seed, 31 + i) - 0.5F) * 2.6F;
            float d = radius * reach(theta) + WALL + 2.5F + Terrain.hash(seed, 41 + i) * Math.max(0.0F, lip - 4.0F);
            holes[i] = new float[]{Mth.cos(theta) * d, Mth.sin(theta) * d, 1.1F + 1.2F * Terrain.hash(seed, 51 + i), 2.0F + Math.round(2.0F * Terrain.hash(seed, 61 + i))};
        }
        return holes;
    }

    private static int pothole(float[][] holes, float u, float v) {
        int deepest = 0;
        for (float[] h : holes) {
            float d = Mth.sqrt(Mth.square(u - h[0]) + Mth.square(v - h[1])) / h[2];
            if (d < 1.0F) deepest = Math.max(deepest, Math.round(h[3] * Terrain.smooth((1.0F - d) / 0.35F)));
        }
        return deepest;
    }

    /**
     * The flows of basalt the plateau is built of, from the bottom of the bounds up: in each, columnar basalt below and
     * the finer, smoother entablature above (0 and 1), with thin beds of ash between some of them (2).
     */
    private byte[] beds(int span) {
        byte[] beds = new byte[span];
        int i = 0;
        for (int k = 0; i < span; k++) {
            int flow = 4 + Math.round(Terrain.hash(seed, 100 + k) * 4.0F);
            int upper = Math.max(1, Math.round(flow * (0.25F + 0.2F * Terrain.hash(seed, 140 + k))));
            for (int t = 0; t < flow && i < span; t++) beds[i++] = (byte) (t >= flow - upper ? 1 : 0);
            if (Terrain.hash(seed, 180 + k) < 0.6F) {
                int ash = Terrain.hash(seed, 220 + k) < 0.3F ? 2 : 1;
                for (int t = 0; t < ash && i < span; t++) beds[i++] = 2;
            }
        }
        return beds;
    }

    /** The plateau's rock at a height, its beds lying very nearly level. */
    private BlockState rock(byte[] beds, int base, int x, int y, int z) {
        int i = Mth.clamp(y - base + Math.round(1.6F * (Terrain.field(seed + 6, x, z, 24.0) - 0.5F)), 0, beds.length - 1);
        return switch (beds[i]) {
            case 0 -> Blocks.BASALT.defaultBlockState();
            case 1 -> Blocks.SMOOTH_BASALT.defaultBlockState();
            default -> Blocks.TUFF.defaultBlockState();
        };
    }

    /** The rubble of broken basalt fallen from the cliffs. */
    private BlockState rubble(int x, int h, int z) {
        float n = Terrain.noise(seed + 8, x, h, z);
        if (n < 0.3F) return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.values()[Math.floorMod(x * 7 + h * 3 + z, 3)]);
        return n < 0.48F ? Blocks.SMOOTH_BASALT.defaultBlockState() : n < 0.6F ? Blocks.TUFF.defaultBlockState()
                : n < 0.85F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    }

    /** The boulders the floods rolled along the floor and dropped as they slackened: the height of one over a column. */
    private int boulder(int x, int z) {
        int best = 0;
        for (int i = Math.floorDiv(x, 11) - 1; i <= Math.floorDiv(x, 11) + 1; i++) {
            for (int j = Math.floorDiv(z, 11) - 1; j <= Math.floorDiv(z, 11) + 1; j++) {
                if (Terrain.noise(seed + 40, i, 0, j) > 0.3F) continue;
                float bx = i * 11 + 2.0F + 7.0F * Terrain.noise(seed + 41, i, 0, j), bz = j * 11 + 2.0F + 7.0F * Terrain.noise(seed + 42, i, 0, j);
                float r = 1.0F + 1.3F * Terrain.noise(seed + 43, i, 0, j), d = Mth.sqrt(Mth.square(x + 0.5F - bx) + Mth.square(z + 0.5F - bz)) / r;
                if (d < 1.0F) best = Math.max(best, Math.round((1.0F + 2.0F * Terrain.noise(seed + 44, i, 0, j)) * Mth.sqrt(1.0F - d * d)));
            }
        }
        return best;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), probe = new BlockPos.MutableBlockPos();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        if (minX > maxX || minZ > maxZ) return;
        int base = b.minY();
        byte[] beds = beds(b.maxY() - base + 1);
        float[][] holes = potholes();
        // (a column left alone keeps any pond in it, so wherever the cut opens beside one, the rock stays)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> !touched(nx, nz) || skipped(Goosenecks.ground(level, probe, nx, nz), b);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float[] l = uv(x, z);
                float u = l[0], v = l[1], e = edge(x, z, u, v);
                boolean lipped = lipAt(x, z, u, e), pooled = banked(x, z, u, v);
                if (e >= WALL && !lipped && !pooled) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                // (a pool and its banks are always built, however low the ground, so its water stays in)
                if (ground >= b.maxY() - 4 || ground <= b.minY() + 2 && !pooled) continue;
                int high = Math.max(ground, Goosenecks.standing(level, pos, x, ground, z));
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                boolean edge = false;
                for (Direction d : Direction.Plane.HORIZONTAL) edge |= outside.test(x + d.getStepX(), z + d.getStepZ());
                if (e < 0.0F || pooled) {
                    cut(level, pos, x, z, u, v, e, ground, high, was, edge, outside, beds, base, b);
                } else if (e < WALL) {
                    wall(level, pos, x, z, u, v, ground, high, was, edge, outside, beds, base, b);
                } else {
                    lip(level, pos, x, z, u, v, e, ground, high, edge, outside, beds, base, holes, b);
                }
            }
        }
    }

    /** A column inside the horseshoe or the coulee: cut down to the floor, with rubble heaped against the cliffs and pools at their foot. */
    private void cut(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float u, float v, float e, int ground, int high, BlockState was,
                     boolean edge, BiPredicate<Integer, Integer> outside, byte[] beds, int base, BoundingBox b) {
        // the floor, rising to meet the ground where the coulee fades out beyond its mouth
        int target = u <= mouth ? floor : Math.round(Mth.lerp(Terrain.smooth((u - mouth) / FLARE), floor, Math.max(floor, high)));
        float[] p = pool(x, z, u, v);
        int pi = (int) p[0];
        float r = pi < 0 ? 1.0F : pools[pi * 4 + 2];
        boolean wet = p[1] < 1.0F, bank = !wet && p[1] < 1.0F + 2.5F / r;
        if (high <= target && !wet && !bank) {
            // low ground the floods never had to cut: left as it is, but for any pond on it, which is silted up (so none
            // is left standing beside ground the cut has lowered)
            if (high > ground) {
                sound(level, pos, x, z, ground + 1, high - 1);
                Terrain.set(level, pos, x, high, z, was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) && level.getBlockState(pos.set(x, high - 1, z)).blocksMotion()
                        ? was : Blocks.GRAVEL.defaultBlockState());
            }
            return;
        }
        int heap = wet || bank ? 0 : talus(x, z, e, u, high, p[1]);
        int solid = wet || bank ? floor : Math.min(high, target) + heap;
        Terrain.clearAbove(level, pos, x, high, z, b.maxY() - high);
        for (int h = solid + 1; h <= high; h++) {
            Terrain.set(level, pos, x, h, z, edge && Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(beds, base, x, h, z) : Blocks.AIR.defaultBlockState());
        }
        // (and any pond deeper than the cut goes, filled in under the new floor)
        if (!wet) sound(level, pos, x, z, ground + 1, Math.min(solid, high));
        float n = Terrain.noise(seed, x, floor, z);
        if (wet) {
            // a plunge pool: deep in the middle where the falling water drilled down, a still lake now
            int water = floor - 1, depth = pools[pi * 4 + 3], bed = water - Math.max(1, Math.round(depth * (1.0F - p[1] * p[1])));
            sound(level, pos, x, z, floor - 9, bed - 1);
            Terrain.set(level, pos, x, bed, z, n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.CLAY.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState());
            for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            Terrain.set(level, pos, x, floor, z, Blocks.AIR.defaultBlockState());
            return;
        }
        if (bank) {
            // the pool's banks, sound all the way down so its water stays in
            sound(level, pos, x, z, floor - 9, floor - 1);
            floorTop(level, pos, x, floor, z, was, n, false);
            return;
        }
        if (heap > 0) {
            sound(level, pos, x, z, target - 2, target);
            for (int h = target + 1; h <= target + heap; h++) Terrain.set(level, pos, x, h, z, rubble(x, h, z));
            return;
        }
        // (where the coulee fades out beyond its mouth, its floor rises to meet the ground)
        sound(level, pos, x, z, target - 2, target - 1);
        floorTop(level, pos, x, target, z, was, n, true);
    }

    /** Fills any hollow (a cave, say) in a column between two heights with stone (no deeper than the bounds). */
    private void sound(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int from, int to) {
        for (int h = Math.max(from, floor - 10); h <= to; h++) {
            BlockState s = level.getBlockState(pos.set(x, h, z));
            if (!s.blocksMotion() || !s.getFluidState().isEmpty()) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
        }
    }

    /** The coulee's floor: the soil of the country round about, bars of gravel the floods left, and boulders they dropped. */
    private void floorTop(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState was, float n, boolean open) {
        boolean gravel = Terrain.field(seed + 13, x, z, 6.0) > 0.68F || n < 0.04F;
        boolean coarse = Terrain.field(seed + 14, x, z, 4.0) > 0.62F;
        BlockState top, under;
        if (was.is(BlockTags.SAND)) {
            top = gravel ? Blocks.GRAVEL.defaultBlockState() : was;
            under = was.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
        } else if (was.is(BlockTags.TERRACOTTA)) {
            top = gravel ? Blocks.GRAVEL.defaultBlockState() : coarse ? Blocks.RED_SAND.defaultBlockState() : was;
            under = Blocks.TERRACOTTA.defaultBlockState();
        } else if (was.is(BlockTags.DIRT)) {
            top = gravel ? Blocks.GRAVEL.defaultBlockState() : coarse ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            under = Blocks.DIRT.defaultBlockState();
        } else {
            top = gravel || coarse ? Blocks.GRAVEL.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
            under = Blocks.STONE.defaultBlockState();
        }
        Terrain.set(level, pos, x, y - 1, z, under);
        Terrain.set(level, pos, x, y, z, top);
        if (!open) return;
        int boulder = boulder(x, z);
        if (boulder > 0) {
            for (int h = 1; h <= boulder; h++) Terrain.set(level, pos, x, y + h, z, h == boulder ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BASALT.defaultBlockState());
            return;
        }
        float m = Terrain.noise(seed + 9, x, y, z);
        if (top.is(Blocks.GRASS_BLOCK) && m < 0.1F) {
            Terrain.set(level, pos, x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        } else if ((top.is(BlockTags.SAND) || top.is(BlockTags.TERRACOTTA) || top.is(Blocks.COARSE_DIRT)) && m > 0.985F) {
            Terrain.set(level, pos, x, y + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    /** The band of rock behind the cliff face, rebuilt in its beds from the bottom of the bounds up. */
    private void wall(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float u, float v, int ground, int high, BlockState was,
                      boolean edge, BiPredicate<Integer, Integer> outside, byte[] beds, int base, BoundingBox b) {
        if (high <= floor + 1) {
            // where the cliffs have run out at the mouth, only any water is filled in
            for (int h = ground + 1; h <= high; h++) Terrain.set(level, pos, x, h, z, rock(beds, base, x, h, z));
            return;
        }
        // round the back, the top of the cliffs is the bare lip, notched where the scoured channels run over it
        boolean bare = u < radius * 0.3F;
        int notch = bare ? groove(u, v) : 0, crest = high - notch;
        boolean soil = !bare && high == ground && (was.is(BlockTags.DIRT) || was.is(BlockTags.SAND) || was.is(BlockTags.TERRACOTTA));
        if (bare) Terrain.clearAbove(level, pos, x, high, z, b.maxY() - high);
        for (int h = base; h <= crest; h++) {
            BlockState s = rock(beds, base, x, h, z);
            if (soil && h == crest) s = was;
            else if (soil && h == crest - 1 && was.is(BlockTags.DIRT)) s = Blocks.DIRT.defaultBlockState();
            Terrain.set(level, pos, x, h, z, s);
        }
        for (int h = crest + 1; h <= high; h++) {
            Terrain.set(level, pos, x, h, z, edge && Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(beds, base, x, h, z) : Blocks.AIR.defaultBlockState());
        }
        if (notch > 0 && Terrain.noise(seed + 10, x, crest, z) < 0.4F) Terrain.set(level, pos, x, crest, z, Blocks.GRAVEL.defaultBlockState());
    }

    /** The old lip of the falls up on the plateau: rock scoured bare by the floods, grooved and drilled with potholes. */
    private void lip(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float u, float v, float e, int ground, int high, boolean edge,
                     BiPredicate<Integer, Integer> outside, byte[] beds, int base, float[][] holes, BoundingBox b) {
        Terrain.clearAbove(level, pos, x, high, z, b.maxY() - high);
        for (int h = Math.min(ground, high - 2); h <= high; h++) Terrain.set(level, pos, x, h, z, rock(beds, base, x, h, z));
        int hole = e >= WALL + 1.0F ? pothole(holes, u, v) : 0, sink = Math.max(hole, groove(u, v));
        float n = Terrain.noise(seed + 11, x, high, z);
        if (sink == 0) {
            if (Terrain.field(seed + 12, x, z, 4.0) > 0.74F) Terrain.set(level, pos, x, high, z, Blocks.GRAVEL.defaultBlockState()); // a skim of flood gravel
            return;
        }
        for (int h = high - sink + 1; h <= high; h++) {
            Terrain.set(level, pos, x, h, z, edge && Terrain.pondBeside(level, pos, x, h, z, outside) ? rock(beds, base, x, h, z) : Blocks.AIR.defaultBlockState());
        }
        int bottom = high - sink;
        Terrain.set(level, pos, x, bottom - 1, z, rock(beds, base, x, bottom - 1, z));
        if (hole > 0) {
            // the stones that ground it out, still lying in the bottom
            Terrain.set(level, pos, x, bottom, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : n < 0.8F ? Blocks.COBBLESTONE.defaultBlockState() : rock(beds, base, x, bottom, z));
        } else if (n < 0.45F) {
            Terrain.set(level, pos, x, bottom, z, Blocks.GRAVEL.defaultBlockState());
        }
    }
}
