package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.BiPredicate;

/**
 * A coal seam fire: a bed of coal that caught light where it comes out on a hillside, from a lightning strike or a grass
 * fire, and has smouldered underground ever since, like Burning Mountain in New South Wales (alight for some six thousand
 * years) or the seam fires of Wyoming's Powder River Basin. The fire creeps along the seam into the hill only as fast as
 * air can reach it through the cracks, and leaves the coal behind it burnt out to ash. The ground over the burnt-out seam
 * sags and caves into pits, and the shales and sandstones above and round it are baked hard and brick-red into clinker,
 * which outlasts the softer rock round about. Along the line where the fire is still burning, the ground is hot, bare and
 * grey, split by cracks that breathe out smoke and fumes and crust their lips with yellow sulphur and white salts. The
 * heat has killed everything on the burnt ground, leaving dead, stripped trunks standing over scorched earth, but just
 * beyond the fire front, where it hasn't reached yet, the grass grows right up to its edge.
 *
 * @param angle   the way the seam's outcrop runs along the hillside (downhill is a quarter turn round from it)
 * @param length  how far along the hillside the fire has burnt
 * @param step    how far apart the points along the hillside are where the plan found the outcrop
 * @param outcrop how far downhill (or uphill, if negative) of the middle line the seam comes out, at each of those points
 * @param burnt   how far back into the hill the fire has eaten from the outcrop, at the most
 * @param strip   how wide the smouldering ground along the fire front is
 * @param live0   where along the hillside the stretch of the front that's still burning starts
 * @param live1   and where it ends
 * @param low     the lowest ground sampled (for the bounds)
 * @param high    the highest ground sampled (for the bounds)
 */
public record CoalSeamFire(int cx, int cz, float angle, int length, int step, int[] outcrop, int burnt, int strip, int live0, int live1, int low,
                           int high, long seed) implements Landform {
    /** How many points the plan looks at along the outcrop each side of the middle. */
    private static final int STATIONS = 3;
    /** The parts of the fire's ground, downhill to uphill (and beyond the front, the grass it hasn't reached). */
    private static final int NONE = 0, RUBBLE = 1, BAND = 2, BURNT = 3, STRIP = 4, FRONT = 5;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 20, 50)) {
            return null;
        }
        // the lie of the land round about, every 12 blocks out to 24 each way
        int[][] g = new int[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) g[i][j] = c.groundAt(c.x() + 12 * (i - 2), c.z() + 12 * (j - 2));
        }
        // the best place for the outcrop: on a steady slope, neither flat nor a cliff, and as near the middle as can be
        float best = -1.0E9F, down = 0.0F;
        int bi = -1, bj = -1;
        for (int i = 1; i < 4; i++) {
            for (int j = 1; j < 4; j++) {
                float gx = (g[i + 1][j] - g[i - 1][j]) / 24.0F, gz = (g[i][j + 1] - g[i][j - 1]) / 24.0F;
                float slope = Mth.sqrt(gx * gx + gz * gz);
                if (slope < 0.22F || slope > 1.4F || g[i][j] < c.seaLevel() + 3 || !c.fits(g[i][j], 20, 50)) continue;
                // (the ground falling away the same way on both sides of it, not a hump or a hollow)
                boolean steady = Math.abs(gx) > Math.abs(gz) ? (g[i - 1][j] - g[i][j]) * gx < 0 && (g[i][j] - g[i + 1][j]) * gx < 0
                        : (g[i][j - 1] - g[i][j]) * gz < 0 && (g[i][j] - g[i][j + 1]) * gz < 0;
                float score = -Math.abs(slope - 0.55F) - 0.12F * (Math.abs(i - 2) + Math.abs(j - 2)) - (steady ? 0.0F : 0.5F);
                if (score > best) {
                    best = score;
                    bi = i;
                    bj = j;
                    down = (float) Mth.atan2(-gz, -gx);
                }
            }
        }
        if (bi < 0) {
            return null;
        }
        int px = c.x() + 12 * (bi - 2), pz = c.z() + 12 * (bj - 2), seam = g[bi][bj];
        // the outcrop runs along the hillside, across the way it falls
        float angle = down - Mth.HALF_PI;
        int length = 40 + c.random().nextInt(31), step = Math.round(length / 6.0F);
        // where the seam comes out at points along the hillside: it lies level, so the outcrop follows the contour
        int[] outcrop = new int[2 * STATIONS + 1];
        boolean[] slopes = new boolean[2 * STATIONS + 1];
        int min = seam, max = seam;
        for (int k = -STATIONS; k <= STATIONS; k++) {
            int up = at(c, px, pz, angle, k * step, -12), mid = at(c, px, pz, angle, k * step, 0), below = at(c, px, pz, angle, k * step, 12);
            min = Math.min(min, Math.min(below, mid));
            max = Math.max(max, Math.max(up, mid));
            slopes[k + STATIONS] = up - below >= 4 && below >= c.seaLevel() + 1;
            // (where the contour isn't between the samples, it's somewhere further off: a guess part way there will do)
            float v = 0.0F;
            if (mid > seam) v = below < seam ? 12.0F * (mid - seam) / (mid - below) : 6.0F;
            else if (mid < seam) v = up > seam ? -12.0F * (seam - mid) / (up - mid) : -6.0F;
            outcrop[k + STATIONS] = Mth.clamp(Math.round(v), -9, 9);
        }
        // the fire runs only as far along as the hillside keeps sloping
        int reach = 0;
        while (reach < STATIONS && slopes[STATIONS + reach + 1] && slopes[STATIONS - reach - 1]) reach++;
        float half = reach * step + step * 0.5F;
        if (!slopes[STATIONS] || half < 20.0F) {
            return null;
        }
        length = Math.min(length, Math.round(half * 2.0F));
        // (no sharp kinks in it from one point to the next)
        for (int k = 1; k < outcrop.length; k++) outcrop[k] = Mth.clamp(outcrop[k], outcrop[k - 1] - 6, outcrop[k - 1] + 6);
        int burnt = 6 + c.random().nextInt(6), strip = 3 + c.random().nextInt(3);
        int live0 = Math.round(-length * 0.5F + c.random().nextFloat() * length * 0.25F);
        int live1 = Math.round(length * 0.5F - c.random().nextFloat() * length * 0.25F);
        return new CoalSeamFire(px, pz, angle, length, step, outcrop, burnt, strip, live0, live1, min, max, c.random().nextLong());
    }

    /** The ground {@code u} blocks along the hillside and {@code v} down it from a point. */
    private static int at(PlanContext c, int px, int pz, float angle, float u, float v) {
        double[] p = Terrain.xz(u, v, px + 0.5, pz + 0.5, angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new CoalSeamFire(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("step"), tag.getIntArray("outcrop"),
                tag.getInt("burnt"), tag.getInt("strip"), tag.getInt("live0"), tag.getInt("live1"), tag.getInt("low"), tag.getInt("high"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("step", step);
        tag.put("outcrop", new IntArrayTag(outcrop));
        tag.putInt("burnt", burnt);
        tag.putInt("strip", strip);
        tag.putInt("live0", live0);
        tag.putInt("live1", live1);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.COAL_SEAM_FIRE;
    }

    @Override
    public BoundingBox bounds() {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int o : outcrop) {
            lo = Math.min(lo, o);
            hi = Math.max(hi, o);
        }
        // (uphill, the coal at its widest, the burnt ground and the smouldering strip and the grass along the front; downhill, the rubble)
        float half = length / 2.0F + 6.0F, up = lo - (2.3F + burnt * 1.25F + strip * 1.25F + 6.0F), downhill = hi + 2.3F + 10.0F;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double[] p = Terrain.xz((k & 1) == 0 ? -half : half, (k & 2) == 0 ? up : downhill, cx + 0.5, cz + 0.5, angle);
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        return new BoundingBox(Mth.floor(minX) - 1, low - 14, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, high + 36, Mth.floor(maxZ) + 1);
    }

    private float[] uv(int x, int z) {
        return Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
    }

    /** How far downhill of the middle line the seam comes out, {@code u} blocks along the hillside. */
    private float outcropAt(float u) {
        float t = Mth.clamp(u / step + STATIONS, 0.0F, 2 * STATIONS);
        int i = Math.min(2 * STATIONS - 1, (int) t);
        // (a smooth curve through the points, not a line kinked at each)
        float f = t - i, p0 = outcrop[Math.max(0, i - 1)], p1 = outcrop[i], p2 = outcrop[i + 1], p3 = outcrop[Math.min(2 * STATIONS, i + 2)];
        return 0.5F * (2.0F * p1 + (p2 - p0) * f + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * f * f + (3.0F * p1 - p0 - 3.0F * p2 + p3) * f * f * f);
    }

    /** 1 along the middle of the fire's length, falling to 0 at its ends. */
    private float middle(float u) {
        return Terrain.smooth((length / 2.0F - Math.abs(u)) / 12.0F);
    }

    /**
     * Where the parts of the fire's ground meet at a column, measured from where the seam comes out: {half the width of
     * the coal band, how far uphill of it the burnt ground goes, how wide the smouldering strip beyond that is, and how far
     * downhill the rubble from the outcrop lies}.
     */
    private float[] edges(int x, int z, float u) {
        float band = (1.0F + 1.3F * Terrain.field(seed + 1, x, z, 6.0)) * Terrain.smooth((length / 2.0F + 4.0F - Math.abs(u)) / 4.0F);
        float back = burnt * middle(u) * (0.8F + 0.45F * Terrain.field(seed + 2, x, z, 10.0));
        // (the front has gone out along part of its length, where the fire ran short of air or coal)
        float live = Terrain.smooth((u - live0) / 6.0F) * Terrain.smooth((live1 - u) / 6.0F) * Math.min(1.0F, middle(u) * 2.0F);
        float smoulder = strip * live * (0.75F + 0.5F * Terrain.field(seed + 3, x, z, 7.0));
        float rubble = (2.5F + 4.5F * Terrain.field(seed + 4, x, z, 7.0)) * Terrain.smooth((length / 2.0F + 2.0F - Math.abs(u)) / 6.0F);
        return new float[]{band, back, smoulder < 0.8F ? 0.0F : smoulder, rubble};
    }

    /** What part of the fire's ground a column is in, from how far downhill of the outcrop it lies. */
    private static int part(float w, float[] e) {
        if (w > e[0] + e[3]) return NONE;
        if (w > e[0]) return RUBBLE;
        if (w >= -e[0]) return BAND;
        if (w > -(e[0] + e[1])) return BURNT;
        float front = -(e[0] + e[1] + e[2]);
        if (w > front) return STRIP;
        return w > front - 3.0F ? FRONT : NONE;
    }

    private int part(int x, int z) {
        float[] l = uv(x, z);
        if (Math.abs(l[0]) > length / 2.0F + 5.0F) return NONE;
        return part(l[1] - outcropAt(l[0]), edges(x, z, l[0]));
    }

    /** Whether the landform fills or lowers a column (the rubble below it and the grass along the front it only dresses). */
    private boolean touched(int x, int z) {
        int p = part(x, z);
        return p == BAND || p == BURNT || p == STRIP;
    }

    private static boolean skipped(int ground, BoundingBox b) {
        return ground >= b.maxY() - 14 || ground <= b.minY() + 4;
    }

    /** The pits where the ground over the burnt-out seam has caved in: {u, w, radius, depth}. */
    private float[][] pits() {
        int count = 3 + Math.round(Terrain.hash(seed, 20) * 4.0F);
        float[][] pits = new float[count][];
        for (int i = 0; i < count; i++) {
            float u = (Terrain.hash(seed, 21 + i) - 0.5F) * length * 0.75F, r = 1.5F + 1.6F * Terrain.hash(seed, 31 + i);
            float room = Math.max(0.0F, burnt * middle(u) * 0.8F - 2.0F * r - 1.5F);
            pits[i] = new float[]{u, -(1.8F + r + Terrain.hash(seed, 41 + i) * room), r, 2.0F + Math.round(Terrain.hash(seed, 51 + i) * 2.0F)};
        }
        return pits;
    }

    /** How far a column has sunk into a collapse pit (0 if it's not in one). */
    private int pit(float[][] pits, int x, int z, float u, float w) {
        int deepest = 0;
        for (float[] p : pits) {
            float d = Mth.sqrt(Mth.square(u - p[0]) + Mth.square(w - p[1])) / (p[2] * (0.85F + 0.3F * Terrain.field(seed + 5, x, z, 3.0)));
            // (steep-sided, as the roof fell in all at once)
            if (d < 1.0F) deepest = Math.max(deepest, Math.round(p[3] * Terrain.smooth((1.0F - d) / 0.4F)));
        }
        return deepest;
    }

    private static boolean nearPit(float[][] pits, float u, float w) {
        for (float[] p : pits) {
            if (Mth.sqrt(Mth.square(u - p[0]) + Mth.square(w - p[1])) < p[2] * 1.15F + 2.0F) return true;
        }
        return false;
    }

    /** How far across the smouldering strip (0 at its back, 1 at the fire front) crack {@code k} runs, {@code u} along it. */
    private float crackLine(int k, float u) {
        return 0.33F + 0.34F * k + 0.1F * Mth.sin(u * 0.27F + k * 2.1F + Terrain.hash(seed, 60 + k) * 6.0F);
    }

    /** How deep a crack splits the smouldering strip at a column (0 if none, -1 if one runs just beside it). */
    private int crack(float u, float across, float width) {
        int near = 0;
        for (int k = 0; k < 2; k++) {
            // (broken into lengths of a few blocks to a dozen or so)
            if (Terrain.field(seed + 10 + k, u, k * 37.0, 5.0) < 0.3F) continue;
            float off = Math.abs(across - crackLine(k, u)) * width;
            if (off < 0.6F) return 2 + Math.round(2.0F * Terrain.field(seed + 12 + k, u, k * 37.0, 4.0));
            if (off < 1.7F) near = -1;
        }
        return near;
    }

    /** The wood the dead trees are of: the trees that grew round about. */
    private static Block wood(Holder<Biome> biome, float n) {
        if (biome.is(BiomeTags.IS_SAVANNA)) return Blocks.STRIPPED_ACACIA_LOG;
        if (biome.is(BiomeTags.IS_BADLANDS)) return Blocks.STRIPPED_OAK_LOG;
        return n < 0.5F ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG;
    }

    /** The rock the seam lies in, where it isn't baked: banded clays in badlands, sandstone under sand, else stone. */
    private static BlockState country(BlockState was) {
        if (was.is(BlockTags.TERRACOTTA) || was.is(Blocks.RED_SAND)) return Blocks.TERRACOTTA.defaultBlockState();
        if (was.is(BlockTags.SAND)) return Blocks.SANDSTONE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    /** Whether the rock at a height has been baked into clinker: in beds along the bedding, and in patches across it. */
    private boolean baked(int x, int y, int z) {
        return Terrain.field(seed + 6, y + 2.0 * Terrain.field(seed + 7, x, z, 16.0), 0.0, 2.2) > 0.4F || Terrain.field(seed + 8, x, z, 5.0) > 0.68F;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), probe = new BlockPos.MutableBlockPos();
        int minX = Math.max(area.minX(), b.minX()), maxX = Math.min(area.maxX(), b.maxX());
        int minZ = Math.max(area.minZ(), b.minZ()), maxZ = Math.min(area.maxZ(), b.maxZ());
        if (minX > maxX || minZ > maxZ) return;
        // (a column the fire leaves alone keeps any pond in it, so where a pit or a crack opens beside one, it stays solid)
        BiPredicate<Integer, Integer> outside = (nx, nz) -> !touched(nx, nz) || skipped(Goosenecks.ground(level, probe, nx, nz), b);
        BlockState clinker = ModBlocks.CLINKER.get().defaultBlockState(), smoulder = ModBlocks.SMOULDERING_GROUND.get().defaultBlockState();
        BlockState sulfur = ModBlocks.SULFUR_CRUST.get().defaultBlockState(), coal = Blocks.COAL_ORE.defaultBlockState();
        float[][] pits = pits();
        // the top of the burnt ground in each column of the chunk, for the dead trees once it's all done
        int[] tops = new int[256];
        Arrays.fill(tops, Integer.MIN_VALUE);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                float[] l = uv(x, z);
                float u = l[0];
                if (Math.abs(u) > length / 2.0F + 5.0F) continue;
                float w = l[1] - outcropAt(u);
                float[] e = edges(x, z, u);
                int part = part(w, e);
                if (part == NONE) continue;
                int ground = Goosenecks.ground(level, pos, x, z);
                if (skipped(ground, b)) continue;
                int standing = Goosenecks.standing(level, pos, x, ground, z);
                pos.set(x, ground, z);
                BlockState was = level.getBlockState(pos);
                float n = Terrain.noise(seed, x, ground, z);
                if (part == RUBBLE || part == FRONT) {
                    if (standing > ground) continue; // (a pond: left as it is)
                    if (part == RUBBLE) rubble(level, pos, x, ground, z, (w - e[0]) / Math.max(1.0F, e[3]), n, clinker);
                    else front(level, pos, x, ground, z, n, was);
                    continue;
                }
                // the ground over the fire: whatever grew on it is dead and gone, and any pond on it long since boiled dry and silted up
                int top = Math.max(ground, standing);
                BlockState rock = country(was);
                Terrain.clearAbove(level, pos, x, top, z, b.maxY() - top);
                for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, rock);
                boolean edge = false;
                for (Direction d : Direction.Plane.HORIZONTAL) edge |= outside.test(x + d.getStepX(), z + d.getStepZ());
                if (part == BAND) {
                    // where the seam comes out: coal, its roof baked red over it
                    for (int h = top - 2; h <= top; h++) {
                        float m = Terrain.noise(seed + 9, x, h, z);
                        Terrain.set(level, pos, x, h, z, h == top && m < 0.18F ? clinker : m < 0.06F ? rock : coal);
                    }
                    if (n > 0.97F) Terrain.set(level, pos, x, top + 1, z, clinker); // a block of clinker fallen from above
                    continue;
                }
                if (part == STRIP) {
                    strip(level, pos, x, top, z, u, (-(e[0] + e[1]) - w) / e[2], e[2], n, edge, outside, clinker, smoulder, sulfur);
                    continue;
                }
                // the burnt-out ground behind the fire: baked rock and scorched earth, sunk into pits where the seam has caved in
                for (int h = top - 3; h < top; h++) Terrain.set(level, pos, x, h, z, baked(x, h, z) ? clinker : rock);
                int sunk = pit(pits, x, z, u, w);
                if (sunk > 0) {
                    for (int h = top - sunk + 1; h <= top; h++) {
                        Terrain.set(level, pos, x, h, z, edge && Terrain.pondBeside(level, pos, x, h, z, outside) ? rock : Blocks.AIR.defaultBlockState());
                    }
                    int floor = top - sunk;
                    // the roof that fell in, broken up, with fumes still seeping from the deepest
                    Terrain.set(level, pos, x, floor - 1, z, clinker);
                    float m = Terrain.noise(seed + 13, x, floor, z);
                    Terrain.set(level, pos, x, floor, z, m < 0.35F ? clinker : m < 0.6F ? Blocks.GRAVEL.defaultBlockState()
                            : m < 0.85F ? Blocks.COARSE_DIRT.defaultBlockState() : sunk >= 3 ? sulfur : Blocks.TUFF.defaultBlockState());
                    continue;
                }
                float m = Terrain.noise(seed + 14, x, top, z);
                BlockState surface = baked(x, top, z) ? m < 0.8F ? clinker : Blocks.GRAVEL.defaultBlockState()
                        : m < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : m < 0.85F ? Blocks.GRAVEL.defaultBlockState() : clinker;
                Terrain.set(level, pos, x, top, z, surface);
                if (surface.is(Blocks.COARSE_DIRT) && n < 0.07F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (!surface.is(Blocks.GRAVEL) && !nearPit(pits, u, w)) {
                    tops[(x & 15) * 16 + (z & 15)] = top;
                }
            }
        }
        trees(level, area, pos, tops, minX, maxX, minZ, maxZ);
    }

    /** The rubble of baked rock and coal tumbled downhill from the outcrop, thinning out away from it. */
    private void rubble(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, float out, float n, BlockState clinker) {
        if (n > 0.75F - 0.5F * out) return;
        float m = Terrain.noise(seed + 15, x, ground, z);
        boolean firm = level.getBlockState(pos.set(x, ground - 1, z)).blocksMotion();
        BlockState s = m < 0.5F ? clinker : m < 0.65F && firm ? Blocks.GRAVEL.defaultBlockState()
                : m < 0.9F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
        // (the plants it fell on are buried, though trees standing in it live on)
        for (int h = ground + 1; h <= ground + 2; h++) {
            BlockState above = level.getBlockState(pos.set(x, h, z));
            if (above.isAir() || above.is(BlockTags.LOGS) || above.is(BlockTags.LEAVES) || !Terrain.cover(above) || !above.getFluidState().isEmpty()) break;
            Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        }
        Terrain.set(level, pos, x, ground, z, s);
        if (s.is(Blocks.COARSE_DIRT) && m > 0.86F && level.getBlockState(pos.set(x, ground + 1, z)).isAir()) {
            Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    /** Just beyond the fire front, where the heat hasn't reached yet: the grass grows thick right up to it. */
    private void front(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, float n, BlockState was) {
        if (!was.is(Blocks.GRASS_BLOCK) || n > 0.6F || !level.getBlockState(pos.set(x, ground + 1, z)).isAir()) return;
        BlockState plant = n < 0.06F ? Blocks.FERN.defaultBlockState() : n < 0.1F ? Blocks.DANDELION.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
        Terrain.set(level, pos, x, ground + 1, z, plant);
    }

    /**
     * The strip of smouldering ground along the fire front, {@code across} of the way from the burnt ground behind it to
     * the front: hot and bare, split by cracks that breathe out fumes and crust their lips with sulphur and white salts.
     */
    private void strip(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z, float u, float across, float width, float n,
                       boolean edge, BiPredicate<Integer, Integer> outside, BlockState clinker, BlockState smoulder, BlockState sulfur) {
        for (int h = top - 4; h < top - 1; h++) Terrain.set(level, pos, x, h, z, clinker);
        Terrain.set(level, pos, x, top - 1, z, smoulder);
        int crack = crack(u, across, width);
        if (crack > 0) {
            for (int h = top - crack + 1; h <= top; h++) {
                Terrain.set(level, pos, x, h, z, edge && Terrain.pondBeside(level, pos, x, h, z, outside) ? clinker : Blocks.AIR.defaultBlockState());
            }
            Terrain.set(level, pos, x, top - crack, z, smoulder);
            if (crack > 3) Terrain.set(level, pos, x, top - crack - 1, z, smoulder);
            return;
        }
        BlockState surface = smoulder;
        if (crack < 0) {
            // round the vents
            float m = Terrain.noise(seed + 16, x, top, z);
            surface = m < 0.5F ? sulfur : m < 0.6F ? Blocks.CALCITE.defaultBlockState() : smoulder;
        } else if (n < 0.04F) {
            surface = sulfur;
        }
        Terrain.set(level, pos, x, top, z, surface);
    }

    /** Dead trees on the burnt ground: bare, stripped trunks with a stub of a branch or two (only where they're wholly in this chunk). */
    private void trees(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int[] tops, int minX, int maxX, int minZ, int maxZ) {
        for (int i = Math.floorDiv(minX, 5); i <= Math.floorDiv(maxX, 5); i++) {
            for (int j = Math.floorDiv(minZ, 5); j <= Math.floorDiv(maxZ, 5); j++) {
                if (Terrain.noise(seed + 17, i, 0, j) > 0.55F) continue;
                int tx = i * 5 + (int) (Terrain.noise(seed + 18, i, 0, j) * 4.99F), tz = j * 5 + (int) (Terrain.noise(seed + 19, i, 0, j) * 4.99F);
                if (tx - 2 < area.minX() || tx + 2 > area.maxX() || tz - 2 < area.minZ() || tz + 2 > area.maxZ()) continue;
                if (tx < minX || tx > maxX || tz < minZ || tz > maxZ) continue;
                int top = tops[(tx & 15) * 16 + (tz & 15)];
                if (top == Integer.MIN_VALUE || !level.getBlockState(pos.set(tx, top + 1, tz)).isAir()) continue;
                pos.set(tx, top, tz);
                Block log = wood(level.getBiome(pos), Terrain.noise(seed + 20, i, 0, j));
                int height = 3 + (int) (Terrain.noise(seed + 21, i, 0, j) * 3.99F);
                for (int h = 1; h <= height; h++) Terrain.set(level, pos, tx, top + h, tz, log.defaultBlockState());
                // a stub or two of the branches it had, the rest long since fallen
                int stubs = Terrain.noise(seed + 22, i, 0, j) < 0.5F ? 1 : 2;
                for (int k = 0; k < stubs; k++) {
                    Direction d = Direction.from2DDataValue((int) (Terrain.noise(seed + 23, i, k, j) * 3.99F) + k);
                    int y = top + height - 1 - k, reach = height > 4 && Terrain.noise(seed + 25, i, k, j) < 0.4F ? 2 : 1;
                    if (y <= top + 1) continue;
                    BlockState branch = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
                    for (int r = 1; r <= reach; r++) {
                        int bx = tx + d.getStepX() * r, bz = tz + d.getStepZ() * r;
                        if (!level.getBlockState(pos.set(bx, y, bz)).isAir()) break;
                        Terrain.setIn(level, area, pos, bx, y, bz, branch);
                    }
                }
            }
        }
    }
}
