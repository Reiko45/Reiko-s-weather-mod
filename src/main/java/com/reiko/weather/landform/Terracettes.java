package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Terracettes: a flight of narrow, step-like tracks running across a steep grassy hillside, one above another a metre
 * or so apart, so that from across the valley the slope looks ribbed like corduroy. They're sheep and cattle tracks.
 * Grazing animals walk across a slope rather than up or down it, keeping to the level, and their hooves tread out a
 * little shelf of bare earth; the soil creeping slowly downhill under its turf, helped along by frost and by the
 * treading, slumps a little below each shelf and heaps up a little above it, until the hillside is stepped into treads
 * and grassy risers. The tracks keep roughly to the contours, wavering a little, and now and then one climbs to join the
 * one above, splits off it, or peters out.
 *
 * @param angle  the way downhill
 * @param grid   the lie of the land, surveyed every {@value STEP} blocks down the slope and across it, {@value NU} rows
 *               down by {@value NV} across, from {@value U0} blocks up the slope and {@value V0} across it
 * @param tracks the tracks, {the height of its tread, where across the slope it starts, where it ends} for each
 * @param links  where one track climbs to the next, {the lower track, where across the slope it leaves it, where it
 *               reaches the one above} for each
 */
public record Terracettes(int cx, int cz, float angle, int[] grid, int[] tracks, int[] links, long seed) implements Landform {
    private static final int STEP = 6, NU = 9, NV = 12, U0 = -24, V0 = -33;
    /** How steep the hillside must be for tracks, and how steep it may be before it's a crag: height per block. */
    private static final float GENTLE = 0.26F, CRAG = 1.35F;

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel(), ground = c.groundAt(c.x(), c.z());
        if (ground < sea + 3 || !c.fits(ground, 30, 40)) {
            return null;
        }
        // the lie of the land round about, every 10 blocks out to 20 each way
        int[][] h = new int[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) h[i][j] = c.groundAt(c.x() + 10 * (i - 2), c.z() + 10 * (j - 2));
        }
        // the steepest spot among the middle nine: steep over 20 blocks, but an even face (not a crag, a gully or a knoll)
        float best = -1.0F, gx = 0.0F, gz = 0.0F;
        int bi = 2, bj = 2;
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                float sx = (h[i + 1][j] - h[i - 1][j]) / 20.0F, sz = (h[i][j + 1] - h[i][j - 1]) / 20.0F, slope = Mth.sqrt(sx * sx + sz * sz);
                float rough = 0.0F;
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) rough = Math.max(rough, Math.abs(h[i + a][j + b] - h[i][j] - 10.0F * (sx * a + sz * b)));
                }
                if (slope < 0.3F || slope > 1.2F || rough > 6.0F || h[i][j] < sea + 4) continue;
                float score = slope - rough * 0.04F;
                if (score > best) {
                    best = score;
                    gx = sx;
                    gz = sz;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (best < 0.0F) {
            return null;
        }
        // a closer survey of that face, lined up with the fall of the slope
        float angle = (float) Mth.atan2(-gz, -gx);
        int fx = c.x() + 10 * (bi - 2), fz = c.z() + 10 * (bj - 2);
        int[] grid = new int[NU * NV];
        for (int i = 0; i < NU; i++) {
            for (int j = 0; j < NV; j++) {
                double[] p = Terrain.xz(U0 + STEP * i, V0 + STEP * j, fx, fz, angle);
                grid[i * NV + j] = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            }
        }
        // how high the steep part of it reaches, and how steep it is on the whole
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE, steep = 0;
        float total = 0.0F;
        for (int i = 1; i < NU - 1; i++) {
            for (int j = 1; j < NV - 1; j++) {
                float su = (grid[(i + 1) * NV + j] - grid[(i - 1) * NV + j]) / (2.0F * STEP);
                float sv = (grid[i * NV + j + 1] - grid[i * NV + j - 1]) / (2.0F * STEP);
                float slope = Mth.sqrt(su * su + sv * sv);
                int y = grid[i * NV + j];
                if (slope < GENTLE || slope > CRAG || y < sea + 2) continue;
                steep++;
                total += slope;
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
        }
        if (steep < 16 || high - low < 7) {
            return null;
        }
        // a track every 2 or 3 blocks up the face (3 on the steepest, so that they don't crowd), each running 20 to 60
        // blocks across it
        RandomSource random = c.random();
        boolean crowded = total / steep > 0.75F;
        List<int[]> list = new ArrayList<>();
        int reach = -V0 - 3;
        for (int y = low + 1 + random.nextInt(2); y < high && list.size() < 10; y += crowded ? 3 : 2 + random.nextInt(2)) {
            int length = 20 + random.nextInt(21) + random.nextInt(21), start = -reach + random.nextInt(Math.max(1, 2 * reach - length + 1));
            list.add(new int[]{y, start, Math.min(reach, start + length)});
        }
        if (list.size() < 3) {
            return null;
        }
        // here and there one climbs to the next: the lower one ending there (it merges into the upper), the upper one
        // starting there (it splits off the lower), or both running on
        List<int[]> linkList = new ArrayList<>();
        for (int k = 0; k + 1 < list.size(); k++) {
            if (random.nextFloat() > 0.5F) continue;
            int[] lower = list.get(k), upper = list.get(k + 1);
            int from = Math.max(lower[1], upper[1]) + 3, to = Math.min(lower[2], upper[2]) - 3, run = (upper[0] - lower[0]) * (3 + random.nextInt(2));
            if (to - from < run + 2) continue;
            int dir = random.nextBoolean() ? 1 : -1, spare = random.nextInt(to - from - run + 1);
            int v0 = dir > 0 ? from + spare : to - spare, v1 = v0 + dir * run;
            linkList.add(new int[]{k, v0, v1});
            float pick = random.nextFloat();
            if (pick < 0.35F) {
                if (dir > 0) lower[2] = v0;
                else lower[1] = v0;
            } else if (pick < 0.7F) {
                if (dir > 0) upper[1] = v1;
                else upper[2] = v1;
            }
        }
        int[] tracks = new int[list.size() * 3], links = new int[linkList.size() * 3];
        for (int k = 0; k < list.size(); k++) System.arraycopy(list.get(k), 0, tracks, k * 3, 3);
        for (int k = 0; k < linkList.size(); k++) System.arraycopy(linkList.get(k), 0, links, k * 3, 3);
        return new Terracettes(fx, fz, angle, grid, tracks, links, random.nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Terracettes(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getIntArray("grid"), tag.getIntArray("tracks"),
                tag.getIntArray("links"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.put("grid", new IntArrayTag(grid));
        tag.put("tracks", new IntArrayTag(tracks));
        tag.put("links", new IntArrayTag(links));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TERRACETTES;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{U0, U0 + STEP * (NU - 1)}) {
            for (float v : new float[]{V0, V0 + STEP * (NV - 1)}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.floor(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.floor(p[1]));
            }
        }
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int k = 0; k < tracks.length; k += 3) {
            low = Math.min(low, tracks[k]);
            high = Math.max(high, tracks[k]);
        }
        // (a tread is cut or built at most a block from the ground, with the plants cleared a little above it)
        return new BoundingBox(minX, low - 2, minZ, maxX, high + 4, maxZ);
    }

    /** The lie of the land, smoothed out from the survey (u down the slope and v across it from the middle). */
    private float surface(float u, float v) {
        float gu = (u - U0) / STEP, gv = (v - V0) / STEP;
        int iu = Mth.floor(gu), iv = Mth.floor(gv);
        float fu = gu - iu, fv = gv - iv, sum = 0.0F;
        for (int a = -1; a <= 2; a++) {
            float wu = spline(fu, a);
            int i = Mth.clamp(iu + a, 0, NU - 1);
            for (int b = -1; b <= 2; b++) sum += wu * spline(fv, b) * grid[i * NV + Mth.clamp(iv + b, 0, NV - 1)];
        }
        return sum;
    }

    /** Catmull-Rom weights: how much node {@code k} (-1 to 2) counts {@code t} of the way from node 0 to node 1. */
    static float spline(float t, int k) {
        float t2 = t * t, t3 = t2 * t;
        return switch (k) {
            case -1 -> (-t + 2.0F * t2 - t3) * 0.5F;
            case 0 -> (2.0F - 5.0F * t2 + 3.0F * t3) * 0.5F;
            case 1 -> (t + 4.0F * t2 - 3.0F * t3) * 0.5F;
            default -> (t3 - t2) * 0.5F;
        };
    }

    /** Whether track {@code k} has a short gap at {@code v}, where the animals have strayed off it. */
    private boolean gap(int k, float v) {
        return Terrain.field(seed + 50 + k, v, k * 13.0, 5.0) < 0.1F;
    }

    /**
     * Whether track {@code k} comes within 3 rows of blocks of the track above or below it (where that runs) at {@code v},
     * where the slope is {@code w} (height per row).
     */
    private boolean crowded(int k, float v, float w) {
        for (int n = k - 1; n <= k + 1; n += 2) {
            if (n < 0 || n * 3 >= tracks.length || v < tracks[n * 3 + 1] || v > tracks[n * 3 + 2]) continue;
            if (Math.abs(tracks[n * 3] - tracks[k * 3]) / w < 3.0F) return true;
        }
        return false;
    }

    /** How far down from the foot of the step above it track {@code k} wanders at {@code v}, in rows of blocks. */
    private float wander(int k, float v) {
        return 0.9F * Terrain.field(seed + 80 + k, v, k * 17.0, 13.0) - 0.3F;
    }

    /** Whether a block is the turf or bare soil the tracks are trodden into. */
    private static boolean soil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
    }

    /** A plant (or snow) small enough to set back on the turf where a column is nudged up or down. */
    private static boolean small(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.getBlock() instanceof FlowerBlock || state.is(Blocks.SNOW);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float cs = Mth.cos(angle), sn = Mth.sin(angle);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (u < U0 + 2 || u > U0 + STEP * (NU - 1) - 2 || v < V0 + 2 || v > V0 + STEP * (NV - 1) - 2) continue;
                // the smoothed slope here, along x and along z (a block east is cs down the slope and -sn across it)
                float here = surface(u, v);
                float sx = (surface(u + cs, v - sn) - surface(u - cs, v + sn)) * 0.5F, sz = (surface(u + sn, v + cs) - surface(u - sn, v - cs)) * 0.5F;
                float slope = Mth.sqrt(sx * sx + sz * sz);
                if (slope < GENTLE || slope > CRAG) continue;
                // the tracks passing by: a column may be one's tread, or the row just above or below it, which keep the
                // tread a level shelf with a step up behind it (rows are one block wide, edge to edge, along each track)
                int tread = Integer.MIN_VALUE, lo = Integer.MIN_VALUE, hi = Integer.MAX_VALUE;
                float nearest = 1.0F, w = Math.abs(sx) + Math.abs(sz);
                for (int k = 0; k * 3 < tracks.length; k++) {
                    int y = tracks[k * 3];
                    // (where the slope steepens until the tracks would crowd together, every other one gives out)
                    if (v < tracks[k * 3 + 1] || v > tracks[k * 3 + 2] || gap(k, v) || k % 2 == 1 && crowded(k, v, w)) continue;
                    float f = (here - (y + 0.5F - w * (0.5F + wander(k, v)))) / w;
                    int row = Mth.floor(f + 0.5F);
                    if (row == 0 && Math.abs(f) < nearest) {
                        nearest = Math.abs(f);
                        tread = y;
                    } else if (row == 1) {
                        lo = Math.max(lo, y + 1);
                    } else if (row == -1) {
                        hi = Math.min(hi, y);
                    }
                }
                for (int i = 0; i * 3 < links.length; i++) {
                    int k = links[i * 3], v0 = links[i * 3 + 1], v1 = links[i * 3 + 2];
                    if (v < Math.min(v0, v1) || v > Math.max(v0, v1) || (tracks[k * 3 + 3] - tracks[k * 3]) / w < 3.0F) continue;
                    // a track slanting up the slope: its height changes along it, so its line cuts across the contours
                    int y0 = tracks[k * 3], y1 = tracks[k * 3 + 3];
                    float t = (v - v0) / (v1 - v0), y = Mth.lerp(t, y0, y1), rise = (y1 - y0) / (float) (v1 - v0);
                    float fx = sx + rise * sn, fz = sz - rise * cs, wl = Math.abs(fx) + Math.abs(fz);
                    if (wl < 0.05F) continue;
                    float f = (here - (y + 0.5F - wl * (0.5F + Mth.lerp(t, wander(k, v0), wander(k + 1, v1))))) / wl;
                    int row = Mth.floor(f + 0.5F), at = Math.round(y);
                    if (row == 0 && Math.abs(f) < nearest) {
                        nearest = Math.abs(f);
                        tread = at;
                    } else if (row == 1) {
                        lo = Math.max(lo, at + 1);
                    } else if (row == -1) {
                        hi = Math.min(hi, at);
                    }
                }
                if (tread == Integer.MIN_VALUE && lo == Integer.MIN_VALUE && hi == Integer.MAX_VALUE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                BlockState top = level.getBlockState(pos.set(x, ground, z)), above = level.getBlockState(pos.set(x, ground + 1, z));
                // only turf, out in the open (round a tree, or water, the track gives out)
                if (!soil(top) || above.is(BlockTags.LOGS) || !above.getFluidState().isEmpty() || ground < b.minY() + 1 || ground > b.maxY() - 3) continue;
                if (tread != Integer.MIN_VALUE) {
                    if (Math.abs(ground - tread) <= 1) path(level, pos, x, z, ground, tread);
                } else if (lo <= hi && ground == lo - 1) {
                    // the little grassy riser behind a tread
                    nudge(level, pos, x, z, ground, 1, top, above);
                } else if (lo <= hi && ground == hi + 1) {
                    // and the edge below one, no higher than it
                    nudge(level, pos, x, z, ground, -1, top, above);
                }
            }
        }
    }

    /** A track's tread of trodden earth, cut down or built up a block where the ground doesn't quite meet it. */
    private void path(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int tread) {
        // (never cut down where water stands beside it)
        if (ground > tread && Terrain.pondBeside(level, pos, x, ground, z, (nx, nz) -> true)) return;
        Terrain.clearAbove(level, pos, x, ground, z, 2);
        if (ground > tread) {
            Terrain.set(level, pos, x, ground, z, Blocks.AIR.defaultBlockState());
        } else if (ground < tread) {
            Terrain.set(level, pos, x, ground, z, Blocks.DIRT.defaultBlockState());
        }
        // bare earth, mostly packed hard, with patches scuffed loose (a path only with air over it: it turns to dirt otherwise)
        boolean open = level.getBlockState(pos.set(x, tread + 1, z)).isAir();
        boolean scuffed = Terrain.field(seed + 7, x, z, 3.0) > 0.68F || Terrain.noise(seed + 8, x, tread, z) < 0.08F;
        Terrain.set(level, pos, x, tread, z, open && !scuffed ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
    }

    /** Raises a column of turf a block ({@code by} 1) or lowers it ({@code by} -1), setting any small plant back on it. */
    private static void nudge(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int by, BlockState top, BlockState above) {
        if (by < 0 && Terrain.pondBeside(level, pos, x, ground, z, (nx, nz) -> true)) return;
        Terrain.clearAbove(level, pos, x, ground, z, 2);
        Terrain.set(level, pos, x, ground, z, by > 0 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
        Terrain.set(level, pos, x, ground + by, z, top);
        if (small(above)) Terrain.set(level, pos, x, ground + by + 1, z, above);
    }
}
