package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Beach cusps: a row of crescent scallops along the edge of a sandy beach, as evenly spaced as if laid out with a tape,
 * sharp points of sand (horns) jutting toward the sea with smoothly curved bays between them. They're made by the swash:
 * a wave running up the beach is split by each horn and steered into the bays either side, where the two streams meet,
 * scour, and run back down to the sea together. So the bays are dug out and the sand they lose is dropped on the horns,
 * which grow out further and split the swash more sharply still, and the spacing settles to suit the reach of the waves,
 * a few metres apart for small ones and tens of metres for big ones. The coarser sand and shell drift onto the horns,
 * and the wrack the waves throw up is left in a scalloped line along the top of the bays.
 *
 * @param angle   the way out to sea
 * @param length  how far the cusps run along the shore
 * @param shore   the waterline, as surveyed: how far out to sea of the middle it is (in tenths) every {@value STATION}
 *                blocks along the shore, centred on the middle
 * @param spacing how far apart the horns are, in tenths of a block
 * @param horn    how far the horns reach out to sea, in tenths
 * @param bay     how far the bays cut back into the beach, in tenths
 */
public record BeachCusps(int cx, int cz, int sea, float angle, int length, int[] shore, int spacing, int horn, int bay, long seed) implements Landform {
    private static final int STATION = 8, SEAWARD = 16, LANDWARD = 20;

    @Nullable
    static Landform plan(PlanContext c) {
        // (a long coast offers a lot of starts: once the survey has had its fill of samples, give up)
        int[] spent = {0};
        return c.search(12, 12, p -> spent[0] > 100 ? null : planAt(p, spent));
    }

    @Nullable
    private static Landform planAt(PlanContext c, int[] spent) {
        int sea = c.seaLevel(), ground = c.groundAt(c.x(), c.z());
        spent[0]++;
        if (ground < sea - 3 || ground > sea + 3) {
            return null;
        }
        float seaward = c.seaward(16, 2);
        spent[0] += 13;
        if (Float.isNaN(seaward)) {
            return null;
        }
        // the waterline, station by station along the coast each way from here, for as long as it stays a low beach
        // shelving into shallow sea (a river mouth, a headland or a cliff ends it)
        RandomSource random = c.random();
        int want = 40 + random.nextInt(61), reach = want / 2 / STATION + 1;
        float[] line = new float[2 * reach + 1];
        line[reach] = waterline(c, seaward, 0.0F, 0, spent);
        if (Float.isNaN(line[reach])) {
            return null;
        }
        int lo = 0, hi = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (int j = 1; j <= reach; j++) {
                float last = line[reach + side * (j - 1)], u = waterline(c, seaward, last, side * j * STATION, spent);
                if (Float.isNaN(u) || Math.abs(u - last) > 8.0F) break;
                line[reach + side * j] = u;
                if (side < 0) lo = -j;
                else hi = j;
            }
        }
        int run = (hi - lo) * STATION;
        if (run < 40) {
            return null;
        }
        int length = Math.min(want, run);
        // (a bump on a gently shelving shore shifts the waterline a long way: a lone one is passed over)
        float[] raw = line.clone();
        for (int j = lo + 1; j < hi; j++) {
            float a = raw[reach + j - 1], b = raw[reach + j], d = raw[reach + j + 1];
            line[reach + j] = Math.max(Math.min(a, b), Math.min(Math.max(a, b), d));
        }
        // turned to face straight out from the line of the coast, and centred on the run of good shore
        float mv = 0.0F, mu = 0.0F, cov = 0.0F, var = 0.0F;
        for (int j = lo; j <= hi; j++) {
            mv += j * STATION;
            mu += line[reach + j];
        }
        mv /= hi - lo + 1;
        mu /= hi - lo + 1;
        for (int j = lo; j <= hi; j++) {
            cov += (j * STATION - mv) * (line[reach + j] - mu);
            var += Mth.square(j * STATION - mv);
        }
        float m = cov / var, angle = seaward - (float) Math.atan(m), mid = (lo + hi) * STATION / 2.0F;
        double[] centre = Terrain.xz(mu + m * (mid - mv), mid, c.x(), c.z(), seaward);
        int x0 = Mth.floor(centre[0]), z0 = Mth.floor(centre[1]);
        // the surveyed waterline as seen from there (each further along the shore than the last)
        float[][] seen = new float[hi - lo + 1][];
        for (int j = lo; j <= hi; j++) {
            double[] p = Terrain.xz(line[reach + j], j * STATION, c.x(), c.z(), seaward);
            seen[j - lo] = Terrain.uv(p[0], p[1], x0, z0, angle);
            if (j > lo && seen[j - lo][1] <= seen[j - lo - 1][1]) return null;
        }
        // and every STATION blocks along it, a little smoothed
        int n0 = Mth.ceil(length / 2.0F / STATION) + 1, n = 2 * n0 + 1;
        float[] at = new float[n];
        for (int k = 0; k < n; k++) {
            float v = (k - n0) * STATION;
            int j = 0;
            while (j < seen.length - 2 && seen[j + 1][1] < v) j++;
            float t = Mth.clamp((v - seen[j][1]) / (seen[j + 1][1] - seen[j][1]), 0.0F, 1.0F);
            at[k] = Mth.lerp(t, seen[j][0], seen[j + 1][0]);
        }
        int[] shore = new int[n];
        for (int k = 0; k < n; k++) shore[k] = Math.round((at[Math.max(0, k - 1)] + 2.0F * at[k] + at[Math.min(n - 1, k + 1)]) * 2.5F);
        int spacing = 80 + random.nextInt(61), horn = 20 + random.nextInt(15), bay = 25 + random.nextInt(13);
        return new BeachCusps(x0, z0, sea, angle, length, shore, spacing, horn, bay, random.nextLong());
    }

    /**
     * Where the shore {@code v} along the coast meets the sea, as how far out along {@code angle} from the middle it is,
     * looking either side of {@code guess}; NaN unless it's a low shore there, with shallow sea off it.
     */
    private static float waterline(PlanContext c, float angle, float guess, int v, int[] spent) {
        int sea = c.seaLevel();
        float land = guess - 14.0F, water = guess + 14.0F;
        int behind = ground(c, land, v, angle), off = ground(c, water, v, angle);
        spent[0] += 2;
        if (behind < sea - 1 || behind > sea + 5 || off > sea - 3) {
            return Float.NaN;
        }
        for (int k = 0; k < 4; k++) {
            float m = (land + water) * 0.5F;
            if (ground(c, m, v, angle) >= sea - 1) land = m;
            else water = m;
            spent[0]++;
        }
        return (land + water) * 0.5F;
    }

    private static int ground(PlanContext c, float u, float v, float angle) {
        double[] p = Terrain.xz(u, v, c.x(), c.z(), angle);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new BeachCusps(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("shore"),
                tag.getInt("spacing"), tag.getInt("horn"), tag.getInt("bay"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("shore", new IntArrayTag(shore));
        tag.putInt("spacing", spacing);
        tag.putInt("horn", horn);
        tag.putInt("bay", bay);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BEACH_CUSPS;
    }

    @Override
    public BoundingBox bounds() {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, half = length / 2.0F + 1.0F;
        for (int s : shore) {
            lo = Math.min(lo, s / 10.0F);
            hi = Math.max(hi, s / 10.0F);
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        // (the smoothed waterline can swing a little past the surveyed points)
        for (float u : new float[]{lo - LANDWARD - 3.0F, hi + SEAWARD + 3.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, sea - 26, minZ, maxX, sea + 22, maxZ);
    }

    /** How far out to sea of the middle the waterline is (before the cusps), {@code v} along the shore. */
    private float shore(float v) {
        int n0 = (shore.length - 1) / 2;
        float g = v / STATION + n0;
        int i = Mth.floor(g);
        float t = g - i, sum = 0.0F;
        for (int a = -1; a <= 2; a++) sum += Terracettes.spline(t, a) * shore[Mth.clamp(i + a, 0, shore.length - 1)];
        return sum / 10.0F;
    }

    /**
     * The cusps {@code v} along the shore: {how far the waterline is pushed out to sea there (in toward the land, in a
     * bay), how far into a bay it is (0 at a horn, 1 in the middle of one)}. Each bay is a smooth curve and each horn a
     * point, a little irregular in their spacing and size as real ones are.
     */
    private float[] cusp(float v) {
        float p = (v + length / 2.0F + 2.4F * (Terrain.field(seed + 3, v, 0.0, 19.0) - 0.5F)) / (spacing / 10.0F);
        int i = Mth.floor(p);
        float t = p - i, in = (float) Math.pow(Mth.sin(t * Mth.PI), 0.7);
        float out = horn / 10.0F * Mth.lerp(t, 0.85F + 0.3F * Terrain.hash(seed, 100 + i), 0.85F + 0.3F * Terrain.hash(seed, 101 + i));
        float back = bay / 10.0F * (0.85F + 0.3F * Terrain.hash(seed, 300 + i));
        return new float[]{out - (out + back) * in, in};
    }

    /**
     * How much the cusps shape the beach {@code w} up it from the waterline: fully through the swash zone, fading out up
     * the beach and out under the sea. (It fades slowly enough that the beach always climbs from the sea, so every hollow
     * below the sea's level opens to it.)
     */
    private static float reach(float w) {
        return Terrain.smooth((w + 13.0F) / 7.0F) * Terrain.smooth((14.0F - w) / 7.0F);
    }

    /** The beach's height {@code w} blocks up from its waterline: shelving gently under the sea, a gentle face, then the berm. */
    private float profile(float w) {
        if (w < 0.0F) return sea - 1.5F + Math.max(w, -14.0F) * 0.4F;
        return sea - 1.5F + Math.min(w, 9.0F) * 0.3F + Math.max(0.0F, w - 9.0F) * 0.12F;
    }

    /** Whether a column is beyond the beach, which is left untouched. */
    private boolean outside(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
        float w = shore(l[1]) - l[0];
        return Math.abs(l[1]) > length / 2.0F || w < -SEAWARD || w > LANDWARD;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float v = l[1], w = shore(v) - l[0];
                if (Math.abs(v) > half || w < -SEAWARD || w > LANDWARD) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 10 || ground < b.minY() + 4) continue;
                // the beach shaped into cusps through the swash zone, shelving off into the sea bed and blending back into
                // the land behind, and fading back into the ordinary shore at both ends
                float ends = Terrain.smooth((half - Math.abs(v)) / 12.0F);
                float[] cusp = cusp(v);
                float up = w + cusp[0] * ends * reach(w), target = profile(up);
                if (w < -9.0F) target = Mth.lerp(Terrain.smooth((-w - 9.0F) / 7.0F), target, ground);
                if (w > 10.0F) target = Mth.lerp(Terrain.smooth((w - 10.0F) / 10.0F), target, ground);
                int top = Math.round(Mth.lerp(ends, ground, target));
                int surface = Terrain.surface(level, pos, x, ground, z);
                // (a pond standing on the beach goes, even where the sand stays as it was)
                boolean core = ends > 0.3F && w > -9.0F && w < 10.0F;
                if (top == ground && !core && surface <= Math.max(ground, sea - 1)) continue;
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                // the sea over it (in the bays, the sea's own water, open to it); a pond just beyond the beach keeps a bank
                // of sandstone between it and the sand
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea - 1 ? Blocks.WATER.defaultBlockState()
                            : Terrain.pondBeside(level, pos, x, h, z, this::outside) ? Blocks.SANDSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // clean sand, the coarse shell sand drifted onto the horns
                int bottom = Math.max(b.minY() + 1, Math.min(ground + 1, top - 2));
                boolean shells = cusp[1] < 0.3F && up > -2.5F && up < 5.0F && Terrain.field(seed + 5, x, z, 2.5) > 0.5F;
                for (int h = bottom; h <= top; h++) {
                    Terrain.set(level, pos, x, h, z, h == top && shells ? ModBlocks.SHELL_SAND.get().defaultBlockState() : Blocks.SAND.defaultBlockState());
                }
                // (the sand needs something firm under it)
                BlockState under = level.getBlockState(pos.set(x, bottom - 1, z));
                if (under.isAir() || !under.getFluidState().isEmpty()) Terrain.set(level, pos, x, bottom - 1, z, Blocks.SANDSTONE.defaultBlockState());
                if (top >= sea && cusp[1] > 0.35F && Math.abs(up - 6.2F) < 0.7F && ends > 0.5F && Terrain.noise(seed + 6, x, top, z) < 0.6F
                        && level.getBlockState(pos.set(x, top + 1, z)).isAir()) {
                    // the wrack line, scalloped along the top of the bays where the swash runs furthest up the beach
                    Terrain.set(level, pos, x, top + 1, z, ModBlocks.BEACH_WRACK.get().defaultBlockState());
                } else if (top <= sea - 4 && w < -6.0F && Terrain.noise(seed + 7, x, top, z) > 0.9F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
            }
        }
    }
}
