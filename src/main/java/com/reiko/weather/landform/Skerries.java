package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A skerry guard: a swarm of low, bare rocky islets strung along a cold, rocky coast, like the archipelagos off Stockholm,
 * the Åland islands or western Norway. They are the tops of knobs of hard granite and gneiss that the ice sheets of the
 * last ice age rode over and ground smooth into whalebacks: long and gently rounded on the side the ice came from, a
 * little steeper and rougher on the far side, where it froze onto the rock and plucked blocks away. Since the ice melted
 * the land has been slowly rising out of the sea, so the knobs now break the surface as islets of every size. The biggest
 * carry a skin of turf and moss in their hollows and a stunted spruce or two, the smallest are only awash, more lie just
 * under the water, and kelp and seagrass sway in the channels between them.
 *
 * @param sea    the sea level
 * @param angle  the way the ice flowed (out to sea, off the land)
 * @param islets {x, z, half-width in tenths, half-length in tenths, height of its crest above the sea's surface (0 awash,
 *               -1 just under it), kind of rock, twist from the ice's direction in thousandths of a radian} for each islet
 * @param trees  {x, z} where a stunted spruce stands
 */
public record Skerries(int cx, int cz, int sea, float angle, int[] islets, int[] trees, long seed) implements Landform {
    private static final int N = 7;
    /** The survey: every 8 blocks, out to 40 each way. */
    private static final int STEP = 8, GRID = 11;
    /** How far from the middle of the swarm the islets lie. */
    private static final int SPREAD = 33;
    /** How far an islet's rock reaches past its waterline under the sea, at most. */
    private static final float SKIRT = 10.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel();
        int here = c.groundAt(c.x(), c.z());
        if (here < sea - 24 || here > sea + 10 || !c.fits(sea, 48, 20)) {
            return null;
        }
        // a quick look round first: there must be both shallow sea and dry land in reach
        int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        boolean shallow = shallow(here, sea), land = here >= sea;
        for (int i = 0; i < 16 && !(shallow && land); i++) {
            int r = i < 8 ? 3 : 5;
            int g = c.groundAt(c.x() + dirs[i % 8][0] * r * STEP, c.z() + dirs[i % 8][1] * r * STEP);
            shallow |= shallow(g, sea);
            land |= g >= sea;
        }
        if (!shallow || !land) {
            return null;
        }
        // the lie of the sea bed and the shore round about
        int mid = GRID / 2;
        int[][] g = new int[GRID][GRID];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) g[i][j] = c.groundAt(c.x() + STEP * (i - mid), c.z() + STEP * (j - mid));
        }
        // the middle of the swarm: in shallow water, with as much shallow water round it as can be found, and land not far off
        int bi = -1, bj = -1, best = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int far = Mth.square(i - mid) + Mth.square(j - mid);
                if (far > 16 || !shallow(g[i][j], sea)) continue;
                int score = 0;
                boolean shore = false;
                for (int a = 0; a < GRID; a++) {
                    for (int b = 0; b < GRID; b++) {
                        int d = Mth.square(a - i) + Mth.square(b - j);
                        if (d <= 25 && shallow(g[a][b], sea)) score++;
                        if (d <= 36 && g[a][b] >= sea) shore = true;
                    }
                }
                // (nearer the start wins a tie)
                score = score * 64 - far;
                if (shore && score > best) {
                    best = score;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (bi < 0 || best < 17 * 64) {
            return null;
        }
        int fx = c.x() + STEP * (bi - mid), fz = c.z() + STEP * (bj - mid);
        // the ice came off the land: from the middle of the dry ground nearby, out through here to sea
        float lx = 0.0F, lz = 0.0F;
        int ln = 0;
        for (int a = 0; a < GRID; a++) {
            for (int b = 0; b < GRID; b++) {
                if (Mth.square(a - bi) + Mth.square(b - bj) > 49 || g[a][b] < sea) continue;
                lx += a;
                lz += b;
                ln++;
            }
        }
        float angle = (float) Math.atan2(bj - lz / ln, bi - lx / ln) + (c.random().nextFloat() - 0.5F) * 0.5F;
        // the islets: many small, a few big, scattered over the shallows with channels left open between them
        int n = 12 + c.random().nextInt(19);
        List<int[]> list = new ArrayList<>();
        for (int tries = 0; tries < n * 4 && list.size() < n; tries++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            float r = 1.2F + 5.8F * (float) Math.pow(c.random().nextFloat(), 1.6);
            float len = Math.min(r * (1.15F + 0.6F * c.random().nextFloat()), 9.5F);
            int kind = c.random().nextFloat() < 0.45F ? 0 : c.random().nextFloat() < 0.75F ? 1 : 2;
            float roll = c.random().nextFloat(), tall = c.random().nextFloat();
            int twist = Math.round((c.random().nextFloat() - 0.5F) * 500.0F);
            int x = fx + Math.round(Mth.cos(a) * d), z = fz + Math.round(Mth.sin(a) * d);
            float depth = sea - 1 - between(g, c.x() - mid * STEP, c.z() - mid * STEP, x, z);
            if (depth < 1.0F || depth > 14.0F) continue;
            boolean clear = true;
            for (int[] o : list) {
                float ro = (o[2] + o[3]) / 20.0F;
                if (Mth.square(o[0] - x) + Mth.square(o[1] - z) < Mth.square(((r + len) / 2.0F + ro) * 0.85F + 1.0F)) clear = false;
            }
            if (!clear) continue;
            // some only awash, some just under the water; the rest standing a few blocks clear, the bigger the higher
            int h = roll < 0.12F ? -1 : roll < 0.26F ? 0 : 1 + Math.round(tall * Math.min(3.0F, r * 0.6F)) + (r > 5.5F && tall > 0.6F ? 1 : 0);
            h = Math.min(h, Math.max(0, Math.round(r * 0.9F)));
            list.add(new int[]{x, z, Math.round(r * 10.0F), Math.round(len * 10.0F), h, kind, twist});
        }
        if (list.size() < 12) {
            return null;
        }
        int[] islets = new int[list.size() * N];
        for (int i = 0; i < list.size(); i++) System.arraycopy(list.get(i), 0, islets, i * N, N);
        long seed = c.random().nextLong();
        Skerries probe = new Skerries(fx, fz, sea, angle, islets, new int[0], seed);
        // a stunted spruce or two in a hollow of turf on the biggest, highest islets
        // (each wholly inside one chunk, so that its crown is built along with its trunk)
        List<Integer> big = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) if (islets[i * N + 2] >= 45 && islets[i * N + 4] >= 3) big.add(i);
        big.sort((p, q) -> islets[q * N + 2] - islets[p * N + 2]);
        List<int[]> trees = new ArrayList<>();
        for (int k = 0; k < Math.min(2, big.size()); k++) {
            int o = big.get(k) * N;
            double[] p = Terrain.xz(islets[o + 3] * 0.02F, (c.random().nextFloat() - 0.5F) * islets[o + 2] * 0.06F, islets[o] + 0.5, islets[o + 1] + 0.5,
                    angle + islets[o + 6] / 1000.0F);
            for (int t = 0; t < 5; t++) {
                int x = inChunk(Mth.floor(p[0]) + (t == 1 ? 2 : t == 2 ? -2 : 0)), z = inChunk(Mth.floor(p[1]) + (t == 3 ? 2 : t == 4 ? -2 : 0));
                float[] r = probe.rise(x + 0.5F, z + 0.5F);
                if (r[0] < 2.5F || !probe.turf(x, z, r)) continue;
                if (c.groundAt(x, z) < sea - 1) trees.add(new int[]{x, z});
                break;
            }
        }
        int[] sites = new int[trees.size() * 2];
        for (int k = 0; k < trees.size(); k++) {
            sites[k * 2] = trees.get(k)[0];
            sites[k * 2 + 1] = trees.get(k)[1];
        }
        return new Skerries(fx, fz, sea, angle, islets, sites, seed);
    }

    /** Shallow enough for skerries: the sea bed 1 to 13 blocks under the surface. */
    private static boolean shallow(int ground, int sea) {
        return ground <= sea - 2 && ground >= sea - 14;
    }

    /** The ground at a point, read between the survey's points. */
    private static float between(int[][] g, int x0, int z0, int x, int z) {
        float gx = Mth.clamp((x - x0) / (float) STEP, 0.0F, GRID - 1.001F), gz = Mth.clamp((z - z0) / (float) STEP, 0.0F, GRID - 1.001F);
        int i = Mth.floor(gx), j = Mth.floor(gz);
        float fx = gx - i, fz = gz - j;
        return Mth.lerp(fz, Mth.lerp(fx, g[i][j], g[i + 1][j]), Mth.lerp(fx, g[i][j + 1], g[i + 1][j + 1]));
    }

    /** Moves a coordinate a little, if need be, to keep a tree's crown inside its chunk. */
    private static int inChunk(int v) {
        int m = Math.floorMod(v, 16);
        return m < 2 ? v + 2 - m : m > 13 ? v - (m - 13) : v;
    }

    static Landform load(CompoundTag tag) {
        return new Skerries(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getIntArray("islets"), tag.getIntArray("trees"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.put("islets", new IntArrayTag(islets));
        tag.put("trees", new IntArrayTag(trees));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SKERRIES;
    }

    @Override
    public BoundingBox bounds() {
        int minX = cx, minZ = cz, maxX = cx, maxZ = cz;
        for (int o = 0; o < islets.length; o += N) {
            int r = Mth.ceil(reach(o)) + 1;
            minX = Math.min(minX, islets[o] - r);
            maxX = Math.max(maxX, islets[o] + r);
            minZ = Math.min(minZ, islets[o + 1] - r);
            maxZ = Math.max(maxZ, islets[o + 1] + r);
        }
        return new BoundingBox(minX, sea - 40, minZ, maxX, sea + 14, maxZ);
    }

    /** How far from its middle an islet's rock can reach, its skirt under the sea included. */
    private float reach(int o) {
        return Math.max(islets[o + 3] * 0.12F, islets[o + 2] / 10.0F) / 0.85F + SKIRT;
    }

    /**
     * How high the rock stands at a point: {its rise above the sea's surface block (far below zero away from every
     * islet), which islet (or -1), where along it (negative on the side the ice came from), how far out (1 at its waterline)}.
     */
    float[] rise(float x, float z) {
        float best = -99.0F, which = -1.0F, along = 0.0F, out = 9.0F;
        for (int i = 0; i < islets.length / N; i++) {
            int o = i * N;
            float reach = reach(o);
            if (Math.abs(x - islets[o] - 0.5F) > reach || Math.abs(z - islets[o + 1] - 0.5F) > reach) continue;
            float r = islets[o + 2] / 10.0F, len = islets[o + 3] / 10.0F;
            float[] l = Terrain.uv(x, z, islets[o] + 0.5, islets[o + 1] + 0.5, angle + islets[o + 6] / 1000.0F);
            // a whaleback: its long, gentle tail up-ice, its shorter, steeper face down-ice
            float s = l[0] < 0.0F ? l[0] / (len * 1.2F) : l[0] / (len * 0.8F), w = l[1] / r;
            float wobble = 0.85F + 0.3F * Terrain.field(seed + 100 + i, x, z, 5.0);
            float q = (s * s + w * w) / (wobble * wobble);
            float peak = islets[o + 4] + 0.45F, e;
            if (q < 1.0F) {
                e = peak - (peak + 0.5F) * q;
                // the far side, where the ice plucked blocks away, is rougher
                if (s > 0.0F) e -= 0.9F * Terrain.field(seed + 200 + i, x, z, 2.0) * Terrain.smooth(s * 1.5F);
            } else {
                // and under the sea its flanks go on down to the sea bed
                float past = Mth.sqrt(l[0] * l[0] + l[1] * l[1]) * (1.0F - 1.0F / Mth.sqrt(q));
                if (past > SKIRT) continue;
                e = -0.5F - Math.max(1.2F, 2.0F * (peak + 0.5F) / r) * past;
            }
            if (e > best) {
                best = e;
                which = i;
                along = s;
                out = Mth.sqrt(q);
            }
        }
        return new float[]{best, which, along, out};
    }

    /** Whether the top of the rock at a column (risen as {@code r} says) is turf: in the hollows of the bigger islets, out of reach of the spray. */
    private boolean turf(int x, int z, float[] r) {
        int i = (int) r[1];
        if (i < 0 || Math.round(r[0]) < 2) return false;
        if (tree(x, z)) return true;
        int o = i * N;
        return islets[o + 2] >= 35 && islets[o + 4] >= 2 && r[3] < 0.87F && Terrain.field(seed + 300 + i, x, z, 3.0) > 0.42F;
    }

    private boolean tree(int x, int z) {
        for (int k = 0; k < trees.length; k += 2) if (trees[k] == x && trees[k + 1] == z) return true;
        return false;
    }

    /** Granite, grey banded gneiss, or a speckled mix, with a little andesite (more on the rough plucked faces). */
    private BlockState rock(int i, int x, int h, int z, boolean lee) {
        float n = Terrain.noise(seed + 3, x, h, z);
        if (n < 0.05F || lee && n < 0.18F) return Blocks.ANDESITE.defaultBlockState();
        // the gneiss is banded along the foliation squeezed into it when it lay deep in the roots of old mountains
        float a = angle + i * 0.7F;
        float band = Mth.frac((x * Mth.cos(a) + z * Mth.sin(a)) * 0.22F + h * 0.18F + Terrain.field(seed + 4, x, z, 7.0) * 1.6F);
        return switch (islets[i * N + 5]) {
            case 0 -> band < 0.2F ? Blocks.STONE.defaultBlockState() : Blocks.GRANITE.defaultBlockState();
            case 1 -> band < 0.3F ? Blocks.DIORITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            default -> band < 0.4F ? Blocks.DIORITE.defaultBlockState() : n < 0.6F ? Blocks.GRANITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        };
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float[] r = rise(x + 0.5F, z + 0.5F);
                if (r[1] < 0.0F) continue;
                int i = (int) r[1];
                int ground = Terrain.ground(level, pos, x, z);
                int top = sea - 1 + Math.round(r[0]);
                if (top > ground && ground >= b.minY()) {
                    if (ground >= sea - 1) Terrain.clearAbove(level, pos, x, ground, z, 10);
                    boolean lee = r[2] > 0.15F;
                    for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, rock(i, x, h, z, lee));
                    if (turf(x, z, r)) {
                        boolean moss = !tree(x, z) && Terrain.field(seed + 301, x, z, 4.0) > 0.62F;
                        Terrain.set(level, pos, x, top, z, (moss ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState());
                        float n = Terrain.noise(seed + 5, x, top, z);
                        if (!tree(x, z) && n < 0.5F) {
                            Terrain.set(level, pos, x, top + 1, z, (n < 0.06F ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
                        }
                    } else if (top >= sea + 1 && islets[i * N + 2] >= 35 && Terrain.field(seed + 300 + i, x, z, 3.0) > 0.34F
                            && Terrain.noise(seed + 6, x, top, z) < 0.35F) {
                        // moss creeping out over the bare rock round the turf
                        Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                    if (top < sea - 2) weed(level, pos, x, top, z);
                } else if (top <= ground && ground < sea - 1) {
                    // the channels: sand, with a scatter of gravel washed off the rocks, and weed growing on it
                    float n = Terrain.noise(seed + 7, x, ground, z);
                    if (r[3] < 1.6F && n < 0.3F && level.getBlockState(pos.set(x, ground, z)).is(Blocks.SAND)) {
                        Terrain.set(level, pos, x, ground, z, Blocks.GRAVEL.defaultBlockState());
                    }
                    if (ground < sea - 2) weed(level, pos, x, ground, z);
                }
            }
        }
        // the trees, once the islets under them are in
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int k = 0; k < trees.length; k += 2) {
            int x = trees[k], z = trees[k + 1];
            if (!Terrain.column(area, x, z)) continue;
            int top = sea - 1 + Math.round(rise(x + 0.5F, z + 0.5F)[0]);
            if (!level.getBlockState(pos.set(x, top, z)).is(Blocks.GRASS_BLOCK)) continue;
            int height = 3 + Math.round(Terrain.hash(seed, 50 + k) * 2.0F);
            for (int h = 1; h <= height; h++) Terrain.set(level, pos, x, top + h, z, log);
            // a ragged little cone, shorn back on the side the wind comes off the land
            for (int dy = 2; dy <= height + 1; dy++) {
                int layer = height + 1 - dy, rad = layer == 0 ? 0 : layer % 2 == 1 ? 1 : 2;
                for (int dx = -rad; dx <= rad; dx++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > rad || dx == 0 && dz == 0 && dy <= height) continue;
                        if (dx * Mth.cos(angle) + dz * Mth.sin(angle) < -1.0F && Terrain.noise(seed + 8, x + dx, top + dy, z + dz) < 0.7F) continue;
                        if (!area.isInside(x + dx, top + dy, z + dz) || !level.getBlockState(pos.set(x + dx, top + dy, z + dz)).isAir()) continue;
                        level.setBlock(pos, leaves, Terrain.FLAGS);
                    }
                }
            }
        }
    }

    /** Kelp and seagrass on the floor of a column under the sea (rock or sand), {@code floor} its top. */
    private void weed(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int floor, int z) {
        int deep = sea - 1 - floor;
        if (!level.getFluidState(pos.set(x, floor + 1, z)).is(Fluids.WATER) || !level.getFluidState(pos.set(x, sea - 1, z)).is(Fluids.WATER)) return;
        float n = Terrain.noise(seed + 9, x, floor, z), bed = Terrain.field(seed + 10, x, z, 6.0);
        if (deep >= 4 && bed > 0.55F && n < 0.35F) {
            // a frond of kelp, reaching up toward the light but never quite to the surface
            int tall = 1 + Math.round(Terrain.noise(seed + 11, x, 0, z) * (deep - 2));
            for (int h = 1; h <= tall; h++) {
                BlockState s = h < tall ? Blocks.KELP_PLANT.defaultBlockState()
                        : Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 20 + Math.round(Terrain.noise(seed + 12, x, 0, z) * 5.0F));
                Terrain.set(level, pos, x, floor + h, z, s);
            }
        } else if (bed < 0.6F && n < 0.45F) {
            if (deep >= 3 && n < 0.12F) {
                Terrain.set(level, pos, x, floor + 1, z, Blocks.TALL_SEAGRASS.defaultBlockState());
                Terrain.set(level, pos, x, floor + 2, z, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else {
                Terrain.set(level, pos, x, floor + 1, z, Blocks.SEAGRASS.defaultBlockState());
            }
        }
    }
}
