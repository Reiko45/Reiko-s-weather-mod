package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * A desert pavement, or gibber plain: the stony desert of central Australia's gibber country, the reg of the Sahara, the
 * flats of the Mojave. Over thousands of years the wind has blown the fine sand and dust away from a flat stretch of
 * desert, leaving the pebbles behind, settled down and packed together as tightly as cobbles, and the sun and the dew have
 * coated them in a dark, glossy desert varnish (a film of clay, iron and manganese oxides laid down by bacteria). Under the
 * stones lies a layer of silt, the dust the pavement has caught and kept. Round its edges the pavement breaks up into the
 * sand; here and there it dips into a shallow, dry hollow where the rare rain stands for a day or two; and a few dead
 * bushes cling on where the stones are broken up. Scattered across it stand ventifacts: rocks the wind-blown sand has
 * carved, their windward faces planed into smooth facets and fluted with grooves, sloping up to a sharp crest, each with a
 * tail of sand caught in its lee. Some are boulders with one broad facet, some three-faced dreikanters with a keel pointing
 * into the wind, and some little ridges, yardangs in miniature, with a blunt prow facing the wind and a back tapering away
 * from it.
 *
 * @param y       the ground at the middle
 * @param low     the lowest ground the plan found under it (the pavement keeps to ground near the range the plan saw)
 * @param high    the highest
 * @param radius  how far the patch reaches from its middle, on average
 * @param wind    which way the prevailing wind blows (0 to +x, 1 to +z, 2 to -x, 3 to -z): from the rocks' facets to their lee
 * @param hollows {x, z, radius x 10} for each hollow
 * @param rocks   {x, z, shape, length, width, height} for each ventifact: x and z the corner of its footprint on its windward side
 *                (see {@link #rise}); its shape (0 a faceted boulder, 1 a dreikanter, 2 a little yardang); its length along the
 *                wind and width across it; and the most it stands above the ground
 */
public record DesertPavement(int cx, int cz, int y, int low, int high, int radius, int wind, int[] hollows, int[] rocks, long seed) implements Landform {
    /** The survey: how many points each way, and how far apart. */
    private static final int SURVEY = 9, STEP = 8, MID = SURVEY / 2;
    /** How far out (as {@link #edge} gives it) the pavement starts to break up into the sand round it. */
    private static final float INNER = 0.8F;
    private static final int[][] WINDS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int ROCK = 6;
    /** The rocks: each kind's body, and its faces the sandblast has polished. */
    private static final BlockState[] BODY = {Blocks.SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
            Blocks.GRANITE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.BASALT.defaultBlockState()};
    private static final BlockState[] POLISHED = {Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(),
            Blocks.POLISHED_GRANITE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState()};
    private static final BiPredicate<Integer, Integer> ANY = (nx, nz) -> true;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (!c.fits(ground, 12, 24)) {
            return null;
        }
        // a first look out to the edge of the survey: no use going on out at sea or in the mountains
        int lo = ground, hi = ground;
        for (int[] d : WINDS) {
            int h = c.groundAt(c.x() + d[0] * STEP * MID, c.z() + d[1] * STEP * MID);
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        if (hi <= c.seaLevel() + 1 || hi - lo > 24) {
            return null;
        }
        // the lie of the land, every 8 blocks out to 32 each way
        int[][] g = new int[SURVEY][SURVEY];
        for (int i = 0; i < SURVEY; i++) {
            for (int j = 0; j < SURVEY; j++) g[i][j] = c.groundAt(c.x() + STEP * (i - MID), c.z() + STEP * (j - MID));
        }
        // the flattest spot near the middle: gentle for 16 blocks round it and not much less so out to 24, and all of that
        // well above the sea
        int bi = -1, bj = -1, best = Integer.MAX_VALUE;
        for (int i = 1; i < SURVEY - 1; i++) {
            for (int j = 1; j < SURVEY - 1; j++) {
                int[] near = spread(g, i, j, 2), wide = spread(g, i, j, 3);
                if (near[1] - near[0] > 8 || wide[1] - wide[0] > 12 || wide[0] <= c.seaLevel() + 1) continue;
                int score = 4 * (near[1] - near[0]) + 2 * (wide[1] - wide[0]) + Mth.square(i - MID) + Mth.square(j - MID);
                if (score < best) {
                    best = score;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (bi < 0) {
            return null;
        }
        int px = c.x() + STEP * (bi - MID), pz = c.z() + STEP * (bj - MID);
        RandomSource random = c.random();
        // as big a patch as the gentle ground allows: a small one where it rolls a little, a broad one where it's flat right
        // out to the rim (with a look round the rim, which may lie beyond what the survey saw)
        int[] wide = spread(g, bi, bj, 3), far = spread(g, bi, bj, 4), span = wide;
        int radius = 25 + random.nextInt(6);
        if (wide[1] - wide[0] <= 7) {
            int big = far[1] - far[0] <= 7 && far[0] > c.seaLevel() + 1 ? 42 + random.nextInt(11) : 0, middling = 32 + random.nextInt(9);
            for (int want : new int[]{big, middling}) {
                if (want == 0) continue;
                int[] rim = ring(c, px, pz, Math.round(want * 1.15F), 10), under = want == big ? far : wide;
                int rlo = Math.min(under[0], rim[0]), rhi = Math.max(under[1], rim[1]);
                if (rim[0] > c.seaLevel() + 1 && rhi - rlo <= 9) {
                    radius = want;
                    span = new int[]{rlo, rhi};
                    break;
                }
            }
        }
        // the ground it covers, as far as the survey saw it
        int low = span[0], high = span[1];
        for (int i = 0; i < SURVEY; i++) {
            for (int j = 0; j < SURVEY; j++) {
                if (Mth.square(STEP * (i - bi)) + Mth.square(STEP * (j - bj)) > radius * radius) continue;
                low = Math.min(low, g[i][j]);
                high = Math.max(high, g[i][j]);
            }
        }
        int wind = random.nextInt(4);
        long seed = random.nextLong();
        int[] hollows = planHollows(random, px, pz, radius, seed);
        int[] rocks = planRocks(random, px, pz, radius, wind, seed, hollows);
        return new DesertPavement(px, pz, g[bi][bj], low, high, radius, wind, hollows, rocks, seed);
    }

    /** The lowest and highest of the surveyed ground within {@code r} steps of a point of the survey (as far as it reaches). */
    private static int[] spread(int[][] g, int i, int j, int r) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int a = Math.max(0, i - r); a <= Math.min(SURVEY - 1, i + r); a++) {
            for (int b = Math.max(0, j - r); b <= Math.min(SURVEY - 1, j + r); b++) {
                if (Mth.square(a - i) + Mth.square(b - j) > r * r) continue;
                lo = Math.min(lo, g[a][b]);
                hi = Math.max(hi, g[a][b]);
            }
        }
        return new int[]{lo, hi};
    }

    /** The lowest and highest ground on a ring round a point. */
    private static int[] ring(PlanContext c, int x, int z, int r, int n) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            int h = c.groundAt(x + Math.round(Mth.cos(a) * r), z + Math.round(Mth.sin(a) * r));
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return new int[]{lo, hi};
    }

    /** One to four hollows, each wholly on the pavement proper and well clear of the others. */
    private static int[] planHollows(RandomSource random, int px, int pz, int radius, long seed) {
        int want = radius < 32 ? 1 + random.nextInt(2) : 2 + random.nextInt(3), n = 0;
        int[] out = new int[want * 3];
        for (int attempt = 0; attempt < 30 && n < want; attempt++) {
            float a = random.nextFloat() * Mth.TWO_PI, d = Mth.sqrt(random.nextFloat()) * radius * 0.6F;
            int x = px + Math.round(Mth.cos(a) * d), z = pz + Math.round(Mth.sin(a) * d), r = 30 + random.nextInt(41);
            float reach = r / 10.0F * 1.2F + 1.0F;
            boolean ok = true;
            for (int k = 0; k < 8 && ok; k++) {
                float t = k * Mth.PI / 4.0F;
                ok = edge(px, pz, radius, seed, x + 0.5 + Mth.cos(t) * reach, z + 0.5 + Mth.sin(t) * reach) < INNER - 0.05F;
            }
            for (int k = 0; k < n && ok; k++) {
                ok = Mth.length(x - out[k * 3], z - out[k * 3 + 1]) > reach + out[k * 3 + 2] / 10.0F * 1.2F + 4.0F;
            }
            if (!ok) continue;
            out[n * 3] = x;
            out[n * 3 + 1] = z;
            out[n * 3 + 2] = r;
            n++;
        }
        return java.util.Arrays.copyOf(out, n * 3);
    }

    /**
     * Four to a dozen ventifacts (more on a bigger patch), well spread out over the pavement proper and clear of the
     * hollows, each lying wholly in one chunk so that it can be built all at once (see {@link #build}).
     */
    private static int[] planRocks(RandomSource random, int px, int pz, int radius, int wind, long seed, int[] hollows) {
        int want = Mth.clamp(Math.round(4.0F + (radius - 25) * 0.3F) + random.nextInt(3) - 1, 4, 12), n = 0;
        float spacing = radius < 32 ? 8.0F : 10.0F;
        int sx = WINDS[wind][0], sz = WINDS[wind][1];
        int[] out = new int[want * ROCK];
        float[] middles = new float[want * 2];
        for (int attempt = 0; attempt < 80 && n < want; attempt++) {
            float pick = random.nextFloat();
            int shape = pick < 0.4F ? 0 : pick < 0.65F ? 1 : 2, length, width, height;
            if (shape == 0) {
                length = 3 + random.nextInt(4);
                width = Math.min(length, 2 + random.nextInt(3));
                height = Math.min(length + 1, 3 + random.nextInt(3));
            } else if (shape == 1) {
                length = 3 + random.nextInt(3);
                width = length;
                height = 3 + random.nextInt(2) + (length >= 5 ? 1 : 0);
            } else {
                length = 3 + random.nextInt(4);
                width = 2 + random.nextInt(2);
                height = Math.min(length, 3 + random.nextInt(3));
            }
            float a = random.nextFloat() * Mth.TWO_PI, d = Mth.sqrt(random.nextFloat()) * radius * 0.68F;
            float mx = px + 0.5F + Mth.cos(a) * d, mz = pz + 0.5F + Mth.sin(a) * d;
            // its footprint, from its corner on the windward side (x0 to x1, and z0 to z1, out from it)
            int ex = sx * (length - 1) - sz * (width - 1), ez = sz * (length - 1) + sx * (width - 1);
            int x0 = Math.min(0, ex), x1 = Math.max(0, ex), z0 = Math.min(0, ez), z1 = Math.max(0, ez);
            int x = Mth.floor(mx - (x0 + x1 + 1) / 2.0F), z = Mth.floor(mz - (z0 + z1 + 1) / 2.0F);
            // (moved the shorter way into one chunk, if it lies across a chunk's edge)
            x += fit(x + x0, x + x1);
            z += fit(z + z0, z + z1);
            float fx = x + (x0 + x1 + 1) / 2.0F, fz = z + (z0 + z1 + 1) / 2.0F;
            boolean ok = true;
            for (int[] corner : new int[][]{{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
                ok &= edge(px, pz, radius, seed, x + corner[0] + 0.5, z + corner[1] + 0.5) < INNER - 0.1F;
            }
            for (int k = 0; k < hollows.length && ok; k += 3) {
                ok = Mth.length(fx - hollows[k] - 0.5F, fz - hollows[k + 1] - 0.5F) > hollows[k + 2] / 10.0F * 1.2F + length / 2.0F + 3.0F;
            }
            for (int k = 0; k < n && ok; k++) {
                ok = Mth.length(fx - middles[k * 2], fz - middles[k * 2 + 1]) > spacing + length / 2.0F;
            }
            if (!ok) continue;
            middles[n * 2] = fx;
            middles[n * 2 + 1] = fz;
            System.arraycopy(new int[]{x, z, shape, length, width, height}, 0, out, n * ROCK, ROCK);
            n++;
        }
        return java.util.Arrays.copyOf(out, n * ROCK);
    }

    /** How far to move a span of blocks to bring it wholly into one chunk (the shorter way), or 0 if it is already. */
    private static int fit(int from, int to) {
        if (from >> 4 == to >> 4) return 0;
        int edge = (to >> 4) << 4;
        return edge - from <= to - edge + 1 ? edge - from : edge - 1 - to;
    }

    static Landform load(CompoundTag tag) {
        return new DesertPavement(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("low"), tag.getInt("high"), tag.getInt("r"),
                tag.getInt("wind"), tag.getIntArray("hollows"), tag.getIntArray("rocks"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("low", low);
        tag.putInt("high", high);
        tag.putInt("r", radius);
        tag.putInt("wind", wind);
        tag.put("hollows", new IntArrayTag(hollows));
        tag.put("rocks", new IntArrayTag(rocks));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DESERT_PAVEMENT;
    }

    @Override
    public BoundingBox bounds() {
        // (the ragged edge reaches out to a little under 1.36 times the radius; the rocks stand up to 5 over ground no more
        // than 3 over the highest the plan saw, and the plants are cleared off up to 5 over it)
        int r = Mth.ceil(radius * 1.36F) + 1;
        return new BoundingBox(cx - r, low - 6, cz - r, cx + r, high + 10, cz + r);
    }

    /** How far out in the patch a point is: under 1 on it, with a lobed and ragged edge. */
    private static float edge(int cx, int cz, int radius, long seed, double x, double z) {
        double dx = x - cx - 0.5, dz = z - cz - 0.5;
        float th = (float) Mth.atan2(dz, dx);
        float lobes = 1.0F + 0.12F * Mth.sin(2.0F * th + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.07F * Mth.sin(3.0F * th + Terrain.hash(seed, 2) * Mth.TWO_PI)
                + 0.04F * Mth.sin(5.0F * th + Terrain.hash(seed, 3) * Mth.TWO_PI);
        return (float) Math.sqrt(dx * dx + dz * dz) / (radius * lobes * (0.9F + 0.2F * Terrain.field(seed + 4, x, z, 12.0)));
    }

    private float edge(double x, double z) {
        return edge(cx, cz, radius, seed, x, z);
    }

    /** Which hollow a column is in, or -1. */
    private int hollow(int x, int z) {
        for (int i = 0; i < hollows.length / 3; i++) {
            float d = (float) Mth.length(x - hollows[i * 3], z - hollows[i * 3 + 1]);
            if (d < hollows[i * 3 + 2] / 10.0F * (0.8F + 0.4F * Terrain.field(seed + 20 + i, x, z, 2.5))) return i;
        }
        return -1;
    }

    /** Where a column lies on ventifact {@code i}'s footprint: {along the wind from its windward edge, across it}. */
    private int[] cell(int i, int x, int z) {
        int dx = x - rocks[i * ROCK], dz = z - rocks[i * ROCK + 1], sx = WINDS[wind][0], sz = WINDS[wind][1];
        return new int[]{sx * dx + sz * dz, -sz * dx + sx * dz};
    }

    /**
     * How high ventifact {@code i} stands over its foot at a cell of its footprint ({@code du} along the wind from its
     * windward edge, {@code dv} across it), or 0 where it doesn't reach. Grooves run up its windward face along the wind,
     * in every other column.
     */
    private int rise(int i, int du, int dv) {
        int o = i * ROCK, shape = rocks[o + 2], length = rocks[o + 3], width = rocks[o + 4], height = rocks[o + 5];
        if (du < 0 || dv < 0 || du >= length || dv >= width) return 0;
        float s = (du + 0.5F) / length, q = (dv + 0.5F) / width * 2.0F - 1.0F, h;
        boolean groove = Math.floorMod(dv + (Terrain.hash(seed, 300 + i) < 0.5F ? 0 : 1), 2) == 1;
        switch (shape) {
            case 0 -> {
                // a boulder with one broad facet sloping up out of the wind to a crest, and a short, steep lee; its corners
                // worn off
                h = s < 0.62F ? 0.35F + 0.65F * s / 0.62F : 1.0F - 0.7F * Mth.square((s - 0.62F) / 0.38F);
                h *= height * (1.0F - 0.3F * q * q * q * q);
                if (Math.abs(q) > 0.6F && (s < 0.25F || s > 0.8F)) h -= 1.0F;
                if (groove && s < 0.5F && h >= 1.5F) h -= 1.0F;
            }
            case 1 -> {
                // a dreikanter: a keel pointing into the wind between two facets, rising to a peak, its lee rounded off
                if (Math.abs(q) > 0.35F + 0.9F * s) return 0;
                h = s < 0.55F ? 0.3F + 0.7F * s / 0.55F : 1.0F - 0.5F * Mth.square((s - 0.55F) / 0.45F);
                h *= height * (1.0F - 0.45F * Math.abs(q));
                if (groove && s < 0.45F && Math.abs(q) > 0.2F && h >= 1.5F) h -= 1.0F;
            }
            default -> {
                // a little yardang: a blunt prow facing the wind, tallest just behind it, and a long back tapering away
                h = Math.min(1.0F, s / 0.25F + 0.5F) * (1.0F - 0.75F * Math.max(0.0F, (s - 0.3F) / 0.7F));
                h *= height * (1.0F - 0.3F * q * q);
                if (groove && s < 0.3F && h >= 1.5F) h -= 1.0F;
            }
        }
        return Math.max(0, Math.round(h));
    }

    /** Whether a cell of a ventifact faces into the wind, on its facets or its prow (the faces the sandblast polishes). */
    private boolean windward(int i, int du) {
        int shape = rocks[i * ROCK + 2];
        float s = (du + 0.5F) / rocks[i * ROCK + 3];
        return s < (shape == 0 ? 0.62F : shape == 1 ? 0.55F : 0.3F);
    }

    /** Whether a column lies in the tail of sand caught in a ventifact's lee. */
    private boolean shadow(int x, int z) {
        for (int i = 0; i < rocks.length / ROCK; i++) {
            int[] c = cell(i, x, z);
            int length = rocks[i * ROCK + 3], width = rocks[i * ROCK + 4], tail = 2 + 2 * rocks[i * ROCK + 5];
            if (c[0] < length || c[0] >= length + tail || c[1] < -1 || c[1] > width) continue;
            float half = width * 0.5F * (1.0F - (c[0] - length + 0.5F) / tail) + 0.25F;
            if (Math.abs(c[1] - (width - 1) * 0.5F) < half + 0.4F * (Terrain.noise(seed + 8, x, 0, z) - 0.5F)) return true;
        }
        return false;
    }

    /** Whether a block is the ground itself, the kind the pavement can form on (rather than a structure's floor, say). */
    private static boolean natural(BlockState state) {
        return state.is(BlockTags.SAND) || state.is(BlockTags.DIRT) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.STONE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(ModBlocks.GIBBER.get());
    }

    /**
     * A column as the pavement finds it, {its ground, the top of the plants growing on it}, or null where the pavement leaves
     * it be: under water, off the gentle ground the plan saw, where a tree stands (it keeps its own ground), or where clearing
     * the plants off it would let water in.
     */
    @Nullable
    private int[] column(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z) {
        int ground = Terrain.ground(level, pos, x, z);
        if (ground < low - 3 || ground > high + 3 || ground < b.minY() + 3 || ground > b.maxY() - 7) return null;
        if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || !natural(level.getBlockState(pos.set(x, ground, z)))) return null;
        int plants = ground;
        for (int h = ground + 1; h <= ground + 5; h++) {
            BlockState s = level.getBlockState(pos.set(x, h, z));
            if (s.is(BlockTags.LOGS)) return null;
            if (!s.getFluidState().isEmpty() && plants > ground) return null;
            if (s.isAir() || s.is(BlockTags.LEAVES) || !Terrain.cover(s) || !s.getFluidState().isEmpty()) break;
            if (Terrain.pondBeside(level, pos, x, h, z, ANY)) return null;
            plants = h;
        }
        return new int[]{ground, plants};
    }

    /** The firm ground under the pavement's stones: silt, or sandstone under the sand, or terracotta in the badlands. */
    private static BlockState footing(BlockState was, boolean red) {
        if (red) return Blocks.TERRACOTTA.defaultBlockState();
        return was.is(BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    /** Whether a block is firm enough to bear the pavement as it is. */
    private static boolean firm(BlockState state) {
        return state.blocksMotion() && !(state.getBlock() instanceof FallingBlock) && !state.is(BlockTags.DIRT);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = rocks.length / ROCK, sx = WINDS[wind][0], sz = WINDS[wind][1];
        // the ventifacts standing in this chunk: the ground each stands on (the mean of the ground under it, read before any of
        // the chunk is touched), and what it's made of; or none, where any of its ground is unfit for it
        int[] foot = new int[count], kind = new int[count];
        java.util.Arrays.fill(foot, Integer.MIN_VALUE);
        for (int i = 0; i < count; i++) {
            int o = i * ROCK, length = rocks[o + 3], width = rocks[o + 4];
            if (!Terrain.column(area, rocks[o], rocks[o + 1])) continue;
            int sum = 0, cells = 0;
            boolean fit = true;
            for (int du = 0; du < length && fit; du++) {
                for (int dv = 0; dv < width && fit; dv++) {
                    if (rise(i, du, dv) == 0) continue;
                    int[] col = column(level, pos, b, rocks[o] + sx * du - sz * dv, rocks[o + 1] + sz * du + sx * dv);
                    fit = col != null;
                    if (fit) {
                        sum += col[0];
                        cells++;
                    }
                }
            }
            if (!fit || cells == 0) continue;
            foot[i] = Math.round(sum / (float) cells);
            // its rock to suit the ground in the middle of it: sandstone in the desert (or basalt or granite, harder rocks
            // carried in), red sandstone in the badlands, and granite, andesite or basalt on stony upland
            int mx = rocks[o] + sx * (length / 2) - sz * (width / 2), mz = rocks[o + 1] + sz * (length / 2) + sx * (width / 2);
            BlockState was = level.getBlockState(pos.set(mx, Terrain.ground(level, pos, mx, mz), mz));
            float pick = Terrain.hash(seed, 100 + i);
            if (level.getBiome(pos).is(BiomeTags.IS_BADLANDS) || was.is(Blocks.RED_SAND) || was.is(BlockTags.TERRACOTTA)) {
                kind[i] = pick < 0.75F ? 1 : 2;
            } else if (was.is(BlockTags.SAND)) {
                kind[i] = pick < 0.6F ? 0 : pick < 0.85F ? 4 : 2;
            } else {
                kind[i] = pick < 0.4F ? 3 : pick < 0.7F ? 2 : 4;
            }
        }
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = edge(x + 0.5, z + 0.5);
                if (e >= 1.0F) continue;
                int[] col = column(level, pos, b, x, z);
                if (col == null) continue;
                int ground = col[0];
                // a ventifact, if one stands here
                boolean rock = false;
                for (int i = 0; i < count && !rock; i++) {
                    if (foot[i] == Integer.MIN_VALUE) continue;
                    int[] c = cell(i, x, z);
                    int rise = rise(i, c[0], c[1]);
                    if (rise == 0) continue;
                    for (int h = ground + 1; h <= col[1]; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    ventifact(level, pos, x, z, ground, i, c, rise, foot[i], kind[i]);
                    rock = true;
                }
                if (rock) continue;
                BlockState was = level.getBlockState(pos.set(x, ground, z));
                boolean red = level.getBiome(pos).is(BiomeTags.IS_BADLANDS) || was.is(Blocks.RED_SAND) || was.is(BlockTags.TERRACOTTA);
                boolean sandy = was.is(BlockTags.SAND), hollow = hollow(x, z) >= 0;
                float n = Terrain.noise(seed, x, 0, z), bush = Terrain.noise(seed + 7, x, 0, z);
                BlockState top = ModBlocks.GIBBER.get().defaultBlockState();
                boolean dug = false;
                if (e > INNER || sandy && !hollow && shadow(x, z)) {
                    // the edge, where the pavement breaks up patchily into the sand round it; and the sand caught in the lee of
                    // a rock
                    float p = e > INNER ? Terrain.smooth((e - INNER) / (1.0F - INNER)) : 1.0F;
                    float r = 0.6F * Terrain.field(seed + 5, x, z, 3.0) + 0.4F * n;
                    if (e <= INNER || r > 1.0F - 0.95F * p) {
                        // (left as it was, but for the odd dead bush on the sand)
                        if (bush < 0.006F && sandy && col[1] == ground && level.getBlockState(pos.set(x, ground + 1, z)).isAir()) {
                            Terrain.set(level, pos, x, ground + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                        }
                        continue;
                    }
                    if (r > 0.82F - 0.9F * p) top = Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    // the pavement, its stones broken up here and there to the silt under them; and its hollows, a block deep,
                    // wherever that lets no water in from beside them
                    if (Terrain.field(seed + 6, x, z, 5.0) > 0.83F || n > 0.97F) top = Blocks.COARSE_DIRT.defaultBlockState();
                    dug = hollow && !Terrain.pondBeside(level, pos, x, ground, z, ANY);
                }
                for (int h = ground + 1; h <= col[1]; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                int floor = ground;
                if (dug) {
                    Terrain.set(level, pos, x, ground, z, Blocks.AIR.defaultBlockState());
                    floor--;
                }
                Terrain.set(level, pos, x, floor, z, top);
                if (!firm(level.getBlockState(pos.set(x, floor - 1, z)))) Terrain.set(level, pos, x, floor - 1, z, footing(was, red));
                // dead bushes, very few, rooted in the silt where the stones are broken up
                if (top.is(Blocks.COARSE_DIRT) && bush < 0.02F && level.getBlockState(pos.set(x, floor + 1, z)).isAir()) {
                    Terrain.set(level, pos, x, floor + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /**
     * One column of a ventifact, standing {@code rise} over its foot (or flush with the ground, where that's higher) and set
     * a block down into the ground. Its faces into the wind are polished smooth: the top of each cell on its facets or prow,
     * and every block standing proud of the cell upwind of it.
     */
    private void ventifact(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int i, int[] c, int rise, int foot, int kind) {
        int top = Math.max(ground, foot + rise);
        int shelter = Math.max(ground, foot + rise(i, c[0] - 1, c[1]));
        boolean facet = windward(i, c[0]);
        for (int h = ground - 1; h <= top; h++) {
            BlockState s = BODY[kind];
            if (h > shelter || h == top && facet) {
                s = POLISHED[kind];
            } else if (kind == 3 && Terrain.noise(seed + 9, x, h, z) < 0.25F) {
                // (andesite shot through with plain stone)
                s = Blocks.STONE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }
}
