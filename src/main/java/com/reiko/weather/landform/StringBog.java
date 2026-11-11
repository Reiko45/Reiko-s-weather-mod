package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A string bog (an aapa mire), as in Lapland, northern Sweden and the Hudson Bay lowlands: a wide, wet, all but
 * level peatland where water seeps slowly down a very gentle slope through the peat. Over thousands of years frost
 * heave, the flow of the water and the mosses growing fastest where it's driest have combed the bog into long, narrow,
 * gently curving ridges of peat, the strings, lying across the slope like the lines on a contour map. Between them lie
 * long pools of dark, peat-stained water, the flarks, each held up by the string below it, so the bog comes down the
 * slope in a staircase of still water a block at a time. Grass, ferns and berries grow on the strings, and on the
 * broader ones the odd stunted spruce or birch; round the edges of some flarks the moss has grown out over the water in
 * a floating, quaking mat.
 *
 * @param angle  the way the ground falls, the way the water seeps (the strings lie across it)
 * @param length how far the bog reaches up and down the slope from its middle
 * @param width  how far it reaches across the slope from its middle
 * @param bands  five numbers for each flark and the string below it, from the top of the slope down: where the flark
 *               begins (along the slope), how wide it is, how wide its string is, its water level, and flags
 *               ({@link #MATS}: quaking mats round it; {@link #HIGH}: its string stands a block higher along its crest)
 */
public record StringBog(int cx, int cz, float angle, int length, int width, int[] bands, long seed) implements Landform {
    private static final int STRIDE = 5, BLEND = 7, MATS = 1, HIGH = 2;
    /** The ground is surveyed on a grid this many points across, this far apart, and fitted out to this far from each spot tried. */
    private static final int GRID = 12, STEP = 8, SURVEY = 28;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 24, 50)) {
            return null;
        }
        // nothing much higher or lower for a long way round: not among hills or mountains, and well back from the sea
        int low = ground, high = ground;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * 56.0F), c.z() + Math.round(Mth.sin(a) * 56.0F));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        if (high - low > 22 || low < c.seaLevel() - 1) {
            return null;
        }
        // survey the ground on a grid and fit a plane to it round each of a few spots near the start: the bog goes
        // where the ground is flattest, on the gentlest slope
        int[] grid = new int[GRID * GRID];
        Arrays.fill(grid, Integer.MIN_VALUE);
        float bestScore = Float.MAX_VALUE, bestBase = 0.0F, bestSlope = 0.0F, bestAngle = 0.0F, bestRough = 0.0F;
        int bestX = 0, bestZ = 0;
        for (int ox = -12; ox <= 12; ox += 8) {
            for (int oz = -12; oz <= 12; oz += 8) {
                double n = 0, sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0, sh = 0, sxh = 0, szh = 0;
                List<int[]> points = new ArrayList<>();
                for (int i = 0; i < GRID; i++) {
                    for (int j = 0; j < GRID; j++) {
                        int dx = offset(i) - ox, dz = offset(j) - oz;
                        if (dx * dx + dz * dz > SURVEY * SURVEY) continue;
                        int k = i * GRID + j;
                        if (grid[k] == Integer.MIN_VALUE) grid[k] = c.groundAt(c.x() + offset(i), c.z() + offset(j));
                        int h = grid[k];
                        points.add(new int[]{dx, dz, h});
                        n++;
                        sx += dx;
                        sz += dz;
                        sxx += dx * dx;
                        szz += dz * dz;
                        sxz += dx * dz;
                        sh += h;
                        sxh += dx * h;
                        szh += dz * h;
                    }
                }
                double[] fit = solve(new double[][]{{n, sx, sz}, {sx, sxx, sxz}, {sz, sxz, szz}}, new double[]{sh, sxh, szh});
                if (fit == null) continue;
                double base = fit[0], gx = fit[1], gz = fit[2], slope = Math.sqrt(gx * gx + gz * gz);
                if (slope > 0.12) continue;
                // how far the ground strays from the plane, and whether it's a trough across the slope (a valley, which
                // would have a stream running down it)
                double strays = 0, inner = 0, outer = 0, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                int nInner = 0, nOuter = 0, min = Integer.MAX_VALUE;
                for (int[] p : points) {
                    double r = p[2] - (base + gx * p[0] + gz * p[1]);
                    strays += Math.abs(r);
                    lo = Math.min(lo, r);
                    hi = Math.max(hi, r);
                    min = Math.min(min, p[2]);
                    if (slope > 0.015) {
                        double v = Math.abs((-p[0] * gz + p[1] * gx) / slope);
                        if (v > 0.6 * SURVEY) {
                            outer += r;
                            nOuter++;
                        } else if (v < 0.3 * SURVEY) {
                            inner += r;
                            nInner++;
                        }
                    }
                }
                double rough = strays / n;
                if (rough > 5.0 || hi - lo > 22.0 || min < c.seaLevel() - 1) continue;
                if (nInner > 0 && nOuter > 0 && outer / nOuter - inner / nInner > 5.0) continue;
                float score = (float) (rough + slope * 10.0);
                if (score < bestScore) {
                    bestScore = score;
                    bestX = ox;
                    bestZ = oz;
                    bestBase = (float) base;
                    bestSlope = (float) slope;
                    bestRough = (float) rough;
                    // downhill, against the plane's rise (any way at all on dead-flat ground)
                    bestAngle = slope > 0.015 ? (float) Math.atan2(-gz, -gx) : Float.NaN;
                }
            }
        }
        if (bestScore == Float.MAX_VALUE) {
            return null;
        }
        long seed = c.random().nextLong();
        float angle = Float.isNaN(bestAngle) ? c.random().nextFloat() * Mth.TWO_PI : bestAngle;
        // (a smaller bog on rougher ground, where there's more of it to level)
        boolean rough = bestRough > 3.0F;
        int length = 36 + c.random().nextInt(rough ? 6 : 15), width = 38 + c.random().nextInt(rough ? 8 : 19);
        // lay out the flarks and strings down the slope, each flark's water level with the one above or a block lower
        float reach = length + extent(seed) + 2.0F;
        List<int[]> list = new ArrayList<>();
        int previous = Integer.MAX_VALUE;
        for (int t = Mth.floor(-reach); t < reach; ) {
            int flark = c.random().nextFloat() < 0.75F ? 3 + c.random().nextInt(4) : 5 + c.random().nextInt(4);
            float r = c.random().nextFloat();
            int string = r < 0.4F ? 2 : r < 0.8F ? 3 : 4;
            int water = Math.round(bestBase - bestSlope * (t + flark / 2.0F));
            water = Math.max(c.seaLevel(), previous == Integer.MAX_VALUE ? water : Mth.clamp(water, previous - 1, previous));
            int flags = c.random().nextFloat() < 0.3F ? MATS : 0;
            if (string >= 3 && c.random().nextFloat() < 0.5F) flags |= HIGH;
            list.add(new int[]{t, flark, string, water, flags});
            previous = water;
            t += flark + string;
        }
        int[] bands = new int[list.size() * STRIDE];
        for (int k = 0; k < list.size(); k++) {
            int[] band = list.get(k);
            // (a high crest only between two flarks at the same level, so a string never stands more than two over either)
            if (k + 1 < list.size() && list.get(k + 1)[3] != band[3]) band[4] &= ~HIGH;
            System.arraycopy(band, 0, bands, k * STRIDE, STRIDE);
        }
        return new StringBog(c.x() + bestX, c.z() + bestZ, angle, length, width, bands, seed);
    }

    /** The survey grid's offsets from the start, straddling it. */
    private static int offset(int i) {
        return -44 + i * STEP;
    }

    /** Solves three linear equations in three unknowns (for the plane fit), or null if there's no single answer. */
    @Nullable
    private static double[] solve(double[][] m, double[] r) {
        double det = det(m);
        if (Math.abs(det) < 1e-9) return null;
        double[] out = new double[3];
        for (int col = 0; col < 3; col++) {
            double[][] a = {m[0].clone(), m[1].clone(), m[2].clone()};
            for (int row = 0; row < 3; row++) a[row][col] = r[row];
            out[col] = det(a) / det;
        }
        return out;
    }

    private static double det(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    /** How far the strings bow and waver up or down the slope at most. */
    private static float extent(long seed) {
        return Math.abs(bow(seed)) + wave(seed) + 2.0F;
    }

    /** How far the strings bow down the slope (or up it) at the sides of the bog. */
    private static float bow(long seed) {
        return (Terrain.hash(seed, 1) - 0.5F) * 12.0F;
    }

    /** How far they waver in one long, slow wave across it. */
    private static float wave(long seed) {
        return 1.0F + 1.5F * Terrain.hash(seed, 2);
    }

    static Landform load(CompoundTag tag) {
        return new StringBog(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getIntArray("bands"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.put("bands", new IntArrayTag(bands));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.STRING_BOG;
    }

    @Override
    public BoundingBox bounds() {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{-length - 1.0F, length + 1.0F}) {
            for (float v : new float[]{-width - 1.0F, width + 1.0F}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int k = 3; k < bands.length; k += STRIDE) {
            low = Math.min(low, bands[k]);
            high = Math.max(high, bands[k]);
        }
        return new BoundingBox(Mth.floor(minX) - 1, low - 14, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, high + 36, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    /**
     * How far out a point lies against the bog's rounded, lobed edge: below 1 inside it, 1 on the edge. The outline
     * never reaches past the oblong {@code length} by {@code width} either side of the middle.
     */
    private float out(float u, float v) {
        float a = Math.abs(u) / length, b = Math.abs(v) / width;
        float r = (float) Math.pow(Math.pow(a, 2.4) + Math.pow(b, 2.4), 1.0 / 2.4);
        float phi = (float) Math.atan2(v / width, u / length);
        float edge = 0.88F + 0.07F * Mth.sin(3.0F * phi + Mth.TWO_PI * Terrain.hash(seed, 7)) + 0.05F * Mth.sin(5.0F * phi + Mth.TWO_PI * Terrain.hash(seed, 8));
        return r / edge;
    }

    private float soft() {
        return (float) BLEND / Math.min(length, width);
    }

    /** 0 over the bog proper, rising to 1 at its edge, where it eases into the land round it. */
    private float blend(float out) {
        float soft = soft();
        return Mth.clamp((out - (1.0F - soft)) / soft, 0.0F, 1.0F);
    }

    /** How far down the pattern a point lies: the strings follow lines of this, bowed, wavering and wandering a little. */
    private float along(int x, int z, float u, float v) {
        float s = v / width, freq = Mth.TWO_PI / (width * (1.6F + Terrain.hash(seed, 3)));
        return u + bow(seed) * s * s + wave(seed) * Mth.sin(v * freq + Mth.TWO_PI * Terrain.hash(seed, 4))
                + 3.2F * (Terrain.field(seed, x, z, 16.0) - 0.5F);
    }

    /**
     * Where a column lies in the pattern.
     *
     * @param band   which flark and string it belongs to
     * @param top    the height of its top: the water in a pool, otherwise its ground
     * @param pool   whether it's in a flark's pool (which only fills where {@link #wet} says it's held in)
     * @param across how far it is into the band, from the string above it
     * @param crest  how far it is onto the string, from the flark above it (-1 in the flark)
     */
    private record Spot(int band, int top, boolean pool, float across, float crest) {}

    private Spot spot(int x, int z, float u, float v) {
        float t = along(x, z, u, v);
        int n = bands.length / STRIDE, k = 0;
        while (k + 1 < n && bands[(k + 1) * STRIDE] <= t) k++;
        int i = k * STRIDE, flark = bands[i + 1], string = bands[i + 2], water = bands[i + 3], flags = bands[i + 4];
        float tau = t - bands[i];
        if (tau >= flark) {
            // the string: a block over the flark above it, two along the crest of a high one
            float d = tau - flark;
            boolean crest = (flags & HIGH) != 0 && d > 0.7F && d < string - 0.7F;
            return new Spot(k, water + (crest ? 2 : 1), false, tau, d);
        }
        // the flark: a long pool across the slope, rounded off at its ends, kept back from the bog's edge and broken
        // here and there by a bridge of moss
        float reach = (0.5F + 0.45F * Terrain.hash(seed, 80 + k)) * width + 6.0F * (Terrain.field(seed + 5, x, z, 8.0) - 0.5F);
        float end = Mth.clamp((Math.abs(v) - (reach - 7.0F)) / 7.0F, 0.0F, 1.0F);
        float half = flark / 2.0F, open = half * Mth.sqrt(1.0F - end * end);
        boolean pool = tau >= 0.0F && Math.abs(tau - half) < open && Terrain.field(seed + 3, x, z, 10.0) > 0.16F
                && out(u, v) < 1.0F - soft() - 3.0F / Math.min(length, width);
        return new Spot(k, pool ? water : water + 1, pool, tau, -1.0F);
    }

    /** The lowest a column's ground or water stands, if it's part of the bog proper (MIN_VALUE if it isn't). */
    private int lowest(int x, int z) {
        float[] l = uv(x, z);
        if (blend(out(l[0], l[1])) > 0.0F) return Integer.MIN_VALUE;
        return spot(x, z, l[0], l[1]).top();
    }

    /** Whether a flark's pool fills a column: only where nothing beside it is lower, so the strings hold every pool in. */
    private boolean wet(int x, int z, Spot s) {
        if (!s.pool()) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (lowest(x + d.getStepX(), z + d.getStepZ()) < s.top()) return false;
        }
        return true;
    }

    /** Whether the moss has grown out over the water here, in a floating mat along the edge of the flark. */
    private boolean mat(int x, int z, Spot s) {
        int i = s.band() * STRIDE, flark = bands[i + 1];
        if ((bands[i + 4] & MATS) == 0 || Terrain.field(seed + 6, x, z, 5.0) < 0.38F) return false;
        float edge = Math.min(1.0F + 1.4F * Terrain.field(seed + 7, x, z, 4.0), flark * 0.5F - 0.6F);
        return s.across() < edge || s.across() > flark - edge;
    }

    /** Whether a flark is two blocks deep here rather than one: along its middle, mostly. */
    private boolean deep(int x, int z, Spot s) {
        int flark = bands[s.band() * STRIDE + 1];
        return s.across() > 1.2F && s.across() < flark - 1.2F && Terrain.field(seed + 4, x, z, 5.0) > 0.35F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState peat = ModBlocks.PEAT.get().defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BiPredicate<Integer, Integer> outside = (nx, nz) -> {
            float[] l = uv(nx, nz);
            return out(l[0], l[1]) > 1.0F;
        };
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                float[] l = uv(x, z);
                float u = l[0], v = l[1], o = out(u, v);
                if (o > 1.0F) continue;
                float t = blend(o);
                int ground = Goosenecks.ground(level, pos, x, z);
                if (t > 0.0F && (ground > b.maxY() - 4 || ground <= b.minY())) continue;
                int standing = Math.min(b.maxY() - 1, Goosenecks.standing(level, pos, x, ground, z));
                Spot s = spot(x, z, u, v);
                boolean wet = t == 0.0F && wet(x, z, s);
                // (a pool that isn't held in is a lawn of moss, level with the strings)
                int planned = s.pool() && !wet ? s.top() + 1 : s.top();
                // out at the edge the bog eases into the land round about
                int top = t > 0.0F ? Math.round(Mth.lerp(t, planned, ground)) : planned;
                if (t > 0.0F && top == ground && standing == ground) continue;
                // (a pond just outside keeps a bank between it and the bog)
                for (int h = top + 1; h <= standing; h++) {
                    if (Terrain.pondBeside(level, pos, x, h, z, outside)) top = h;
                }
                boolean mat = wet && mat(x, z, s);
                int bed = wet ? top - 1 - (mat || deep(x, z, s) ? 1 : 0) : top;
                Terrain.clearAbove(level, pos, x, ground, z, b.maxY() - ground);
                for (int h = top + 1; h <= standing; h++) Terrain.set(level, pos, x, h, z, air);
                // peat under the bog, filling any hollow (and soil under its edges)
                BlockState fill = t > 0.0F ? dirt : peat;
                for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 4)); h < bed; h++) {
                    BlockState here = level.getBlockState(pos.set(x, h, z));
                    if (t == 0.0F && h >= bed - 4 || h > ground || here.isAir() || !here.getFluidState().isEmpty() || Terrain.cover(here)) {
                        Terrain.set(level, pos, x, h, z, fill);
                    }
                }
                float n = Terrain.noise(seed, x, top, z), m = Terrain.noise(seed + 1, x, top, z), patch = Terrain.field(seed + 2, x, z, 5.0);
                if (wet) {
                    // dark water over soft mud or peat, and the floating moss along the edges of some flarks
                    Terrain.set(level, pos, x, bed, z, n < 0.55F ? Blocks.MUD.defaultBlockState() : peat);
                    for (int h = bed + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    if (mat) {
                        Terrain.set(level, pos, x, top, z, ModBlocks.QUAKING_MAT.get().defaultBlockState());
                    } else if (n > 0.975F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    }
                } else if (t > 0.0F) {
                    // the land round the bog
                    Terrain.set(level, pos, x, top, z, t < 0.5F && patch > 0.6F ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (m < 0.22F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (m < 0.28F) Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                } else if (s.crest() >= 0.0F) {
                    // the strings: ridges of peat under moss, podzol and grass, dry enough for ferns and berries
                    Terrain.set(level, pos, x, top, z, patch > 0.55F ? Blocks.MOSS_BLOCK.defaultBlockState() : patch > 0.3F ? Blocks.PODZOL.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (m < 0.05F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1 + Math.round(n)));
                    } else if (m < 0.25F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                    } else if (m < 0.6F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (m > 0.95F && patch > 0.55F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                } else {
                    // the wet lawns of moss and sedge between the pools
                    Terrain.set(level, pos, x, top, z, patch > 0.45F ? Blocks.MOSS_BLOCK.defaultBlockState() : patch > 0.25F ? peat : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (m < 0.18F) Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (m > 0.96F) Terrain.set(level, pos, x, top + 1, z, Blocks.LILY_OF_THE_VALLEY.defaultBlockState()); // cotton grass
                }
            }
        }
        // the odd stunted tree along the crests of the broader strings (a second pass, so that their crowns reach over
        // columns already built)
        for (int x = Math.max(area.minX() - 1, b.minX()); x <= Math.min(area.maxX() + 1, b.maxX()); x++) {
            for (int z = Math.max(area.minZ() - 1, b.minZ()); z <= Math.min(area.maxZ() + 1, b.maxZ()); z++) {
                if (Terrain.noise(seed + 9, x, 0, z) < 0.985F) continue;
                float[] l = uv(x, z);
                if (blend(out(l[0], l[1])) > 0.0F) continue;
                Spot s = spot(x, z, l[0], l[1]);
                int string = bands[s.band() * STRIDE + 2];
                if (string < 3 || s.crest() < 0.8F || s.crest() > string - 0.8F) continue;
                tree(level, area, pos, x, s.top(), z);
            }
        }
    }

    /** A spruce or birch only a few blocks high, stunted by the cold and the waterlogged peat. */
    private void tree(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        boolean spruce = Terrain.noise(seed + 10, x, 0, z) < 0.65F;
        int height = 2 + (int) (Terrain.noise(seed + 11, x, 0, z) * (spruce ? 2.99F : 1.99F));
        BlockState log = (spruce ? Blocks.SPRUCE_LOG : Blocks.BIRCH_LOG).defaultBlockState();
        BlockState leaves = (spruce ? Blocks.SPRUCE_LEAVES : Blocks.BIRCH_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int k = 1; k <= height; k++) Terrain.setIn(level, area, pos, x, y + k, z, log);
        int crown = y + height;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0, middle = dx == 0 && dz == 0;
                    float n = Terrain.noise(seed + 12, x + dx, crown + dy, z + dz);
                    // a spruce tapers to a spike over a ragged skirt; a birch has a rounder little head
                    boolean leaf = middle ? dy == 1
                            : spruce ? dy == 0 && !corner || dy == -1 && (!corner || height >= 3 && n > 0.5F)
                            : dy == 1 ? !corner : dy == 0 ? !corner || n > 0.4F : !corner && n > 0.3F;
                    if (!leaf || !area.isInside(x + dx, crown + dy, z + dz) || !level.getBlockState(pos.set(x + dx, crown + dy, z + dz)).isAir()) continue;
                    level.setBlock(pos, leaves, Terrain.FLAGS);
                }
            }
        }
    }
}
