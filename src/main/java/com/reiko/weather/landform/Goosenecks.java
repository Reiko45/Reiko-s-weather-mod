package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;

/**
 * Goosenecks: a river's meanders sunk deep into a plateau of flat-lying rock. The river once looped lazily across a
 * plain; then the land began slowly to rise under it, and the river cut down as fast as the land rose, keeping every
 * bend. Now each loop is a canyon of its own, walled in banded sandstone and mudstone that climbs in steps (the hard
 * beds standing as cliffs, the soft ones weathered back into ledges), and between one loop and the next only a narrow
 * neck of rock is left. One day the river will break through a neck and leave its loop high and dry. Down on the floor
 * it lies still in long reaches, each held behind a sill of rock.
 *
 * @param angle   the way the land falls, and the river runs
 * @param omega   how far the river swings round at the sharpest point of each bend (radians off the way it runs)
 * @param meander the length of river in one full meander (two bends)
 * @param bends   how many bends it makes
 * @param rim     the highest ground sampled along it
 * @param levels  the water in each reach, from the upstream end down
 */
public record Goosenecks(int cx, int cz, float angle, float omega, int meander, int bends, int rim, int[] levels, long seed) implements Landform {
    /** How long the straighter stretches at either end are, where the canyon opens out of the plateau. */
    private static final int LEAD = 28;
    /** How far either side of the river the canyon reaches at its widest, at the rim. */
    private static final float HW_MAX = 12.0F;
    /** Columns this far from the river or farther are never touched. */
    private static final float OUT = HW_MAX + 2.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 16 || !c.fits(ground, 45, 40)) {
            return null;
        }
        float omega = (1.6F + c.random().nextFloat() * 0.12F) * (c.random().nextBoolean() ? 1.0F : -1.0F);
        int meander = 145 + c.random().nextInt(26);
        int bends = 2 + c.random().nextInt(2);
        int depth = 20 + c.random().nextInt(12);
        long seed = c.random().nextLong();
        // run it the way the land falls, if it falls at all
        float[] down = c.downhill(48);
        float angle = down[1] > 0 ? down[0] : c.random().nextFloat() * Mth.TWO_PI;
        int[] levels = new int[bends + 1];
        Goosenecks probe = new Goosenecks(c.x(), c.z(), angle, omega, meander, bends, ground, levels, seed);
        float[][] p = probe.course();
        float[] box = probe.extent(p);
        if (Math.max(Math.max(c.x() - box[0], box[2] - c.x()), Math.max(c.z() - box[1], box[3] - c.z())) + HW_MAX + 3.0F > 108.0F) {
            return null;
        }
        // the ground all along the river's course
        int n = probe.length(), rim = ground;
        int[] g = new int[13];
        for (int i = 0; i <= 12; i++) {
            int s = Math.round(i * n / 12.0F);
            g[i] = c.groundAt(Mth.floor(p[0][s]), Mth.floor(p[1][s]));
            if (g[i] < c.seaLevel() + 10) return null; // never through a lake, or out to the sea
            rim = Math.max(rim, g[i]);
        }
        // each reach below the one above it (never higher), and deep down in the rock all the way
        int prev = Integer.MAX_VALUE;
        float mid = n - 2.0F * LEAD;
        for (int r = 0; r < levels.length; r++) {
            float s0 = LEAD + r * mid / levels.length, s1 = s0 + mid / levels.length;
            int low = Integer.MAX_VALUE;
            for (int i = 0; i <= 12; i++) {
                float s = i * n / 12.0F;
                if (s >= s0 - 14.0F && s <= s1 + 14.0F) low = Math.min(low, g[i]);
            }
            int level = Math.min(prev, low - depth - 1);
            if (r > 0) level = Math.max(level, prev - 3); // (stepping down a few blocks at a time)
            // on a lower plateau the canyon is shallower (the check below still wants a good depth of rock all along)
            level = Math.max(level, c.seaLevel() - 6);
            levels[r] = level;
            prev = level;
        }
        if (levels[levels.length - 1] < c.seaLevel() - 6) {
            return null;
        }
        for (int i = 0; i <= 12; i++) {
            float s = i * n / 12.0F;
            int above = g[i] - probe.water(s) - 1;
            // a canyon all the way along, and land round the ends to hold the river in
            if (probe.fullDepth(s) ? above < 16 || above > 42 : above < 6) return null;
        }
        return new Goosenecks(c.x(), c.z(), angle, omega, meander, bends, rim, levels, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Goosenecks(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getFloat("omega"), tag.getInt("meander"), tag.getInt("bends"),
                tag.getInt("rim"), tag.getIntArray("levels"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putFloat("omega", omega);
        tag.putInt("meander", meander);
        tag.putInt("bends", bends);
        tag.putInt("rim", rim);
        tag.put("levels", new IntArrayTag(levels));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GOOSENECKS;
    }

    @Override
    public BoundingBox bounds() {
        float[] e = extent(course());
        int pad = Mth.ceil(HW_MAX) + 3, low = Integer.MAX_VALUE;
        for (int l : levels) low = Math.min(low, l);
        return new BoundingBox(Mth.floor(e[0]) - pad, low - 7, Mth.floor(e[1]) - pad, Mth.floor(e[2]) + pad, rim + 36, Mth.floor(e[3]) + pad);
    }

    /** The length of the river through it. */
    private int length() {
        return Math.round(bends * meander / 2.0F) + 2 * LEAD;
    }

    /** The river's middle line, a point every block from the upstream end: {xs, zs}. */
    private float[][] course() {
        int n = length();
        float[] xs = new float[n + 1], zs = new float[n + 1];
        float x = 0.0F, z = 0.0F;
        for (int i = 0; i <= n; i++) {
            xs[i] = x;
            zs[i] = z;
            float s = i + 0.5F;
            // a sine-generated curve, the shape real meanders take; it straightens out toward the ends
            float taper = Terrain.smooth(Math.min(s, n - s) / LEAD);
            float theta = omega * taper * Mth.sin(Mth.TWO_PI * (s - LEAD) / meander - Mth.HALF_PI);
            x += Mth.cos(theta);
            z += Mth.sin(theta);
        }
        // turned to run the way the land falls, and centred
        float turn = angle - (float) Mth.atan2(zs[n] - zs[0], xs[n] - xs[0]);
        float c = Mth.cos(turn), sn = Mth.sin(turn);
        for (int i = 0; i <= n; i++) {
            float rx = xs[i] * c - zs[i] * sn, rz = xs[i] * sn + zs[i] * c;
            xs[i] = rx;
            zs[i] = rz;
        }
        float[] e = extent(new float[][]{xs, zs});
        float ox = cx + 0.5F - (e[0] + e[2]) / 2.0F, oz = cz + 0.5F - (e[1] + e[3]) / 2.0F;
        for (int i = 0; i <= n; i++) {
            xs[i] += ox;
            zs[i] += oz;
        }
        return new float[][]{xs, zs};
    }

    /** {min x, min z, max x, max z} of a course. */
    private float[] extent(float[][] p) {
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < p[0].length; i++) {
            minX = Math.min(minX, p[0][i]);
            maxX = Math.max(maxX, p[0][i]);
            minZ = Math.min(minZ, p[1][i]);
            maxZ = Math.max(maxZ, p[1][i]);
        }
        return new float[]{minX, minZ, maxX, maxZ};
    }

    /** Whether a point {@code s} blocks down the river is in the stretch cut to its full depth (where the water lies). */
    private boolean fullDepth(float s) {
        return s >= LEAD && s <= length() - LEAD;
    }

    /** 1 along the stretch cut to its full depth, easing to 0 at the ends. */
    private float ends(float s) {
        return Terrain.smooth(Math.min(s, length() - s) / LEAD);
    }

    /** The water's level in the reach a point {@code s} blocks down the river is in. */
    private int water(float s) {
        int k = levels.length;
        return levels[Mth.clamp(Mth.floor((s - LEAD) / (length() - 2.0F * LEAD) * k), 0, k - 1)];
    }

    /** Half the width of the canyon's flat floor. */
    private float floorHalf(float s, float ends) {
        return (4.6F + 0.9F * Mth.sin(s * 0.05F + Terrain.hash(seed, 3) * Mth.TWO_PI)) * (0.45F + 0.55F * ends);
    }

    /** Half the width of the river on the floor. */
    private float riverHalf(float s) {
        return floorHalf(s, 1.0F) - 1.6F;
    }

    /**
     * The least height the floor can stand at in a column, from where it lies (and so whether water beside it could
     * get out): the river and its sandy edges at the water's level, the rest of the floor a block higher.
     */
    private int lowest(float d, float s) {
        if (d >= OUT || d >= floorHalf(s, ends(s))) return Integer.MIN_VALUE;
        int w = water(s);
        return fullDepth(s) && d < riverHalf(s) + 0.9F ? w : w + 1;
    }

    /**
     * Whether the river fills a column. It only does where every column beside it is floor at least as high, so each
     * reach is held in by its banks and by the sill below it, and a reach never spills into the next one down.
     */
    private boolean wet(float[] dist, float[] along, int k, int gd) {
        float d = dist[k], s = along[k];
        if (d >= OUT || !fullDepth(s) || d >= riverHalf(s)) return false;
        int w = water(s);
        for (int j : new int[]{k - gd, k + gd, k - 1, k + 1}) {
            if (lowest(dist[j], along[j]) < w) return false;
        }
        return true;
    }

    /** The beds of rock from the bottom of the bounds up: which bed each height is in (odd beds soft, even beds hard). */
    private int[] beds(int span) {
        int[] beds = new int[span];
        int k = 0, left = 0;
        for (int i = 0; i < span; i++) {
            if (left == 0) {
                k++;
                left = k % 2 == 0 ? 3 + Math.round(Terrain.hash(seed, 100 + k) * 4.0F) : 1 + Math.round(Terrain.hash(seed, 100 + k));
            }
            beds[i] = k;
            left--;
        }
        return beds;
    }

    /** The top of a column (its ground, if the canyon doesn't reach it) for a column whose ground is {@code ground}. */
    private int topAt(float d, float s, int ground, float[] cum, int base) {
        int w = water(s);
        float e = ends(s), fw = floorHalf(s, e);
        boolean full = fullDepth(s);
        // the floor rises out of the plateau at either end, but never to less than a block over the water
        int bed = full ? w + 1 : Math.max(w + 1, Math.round(Mth.lerp(e, ground - 1, w + 1)));
        if (d < fw) return full && d < riverHalf(s) + 0.9F ? w : bed;
        // the walls: the canyon is as wide at each height as the beds of rock below it have let it weather back
        float cap = Mth.lerp(e, fw + 2.0F, HW_MAX), c0 = cum[Mth.clamp(bed - base, 0, cum.length - 1)];
        for (int y = bed + 1; y <= ground; y++) {
            if (d < Math.min(fw + cum[Mth.clamp(y - base, 0, cum.length - 1)] - c0, cap)) return y - 1;
        }
        return ground;
    }

    private static BlockState rock(int bed, boolean red, long seed) {
        float n = Terrain.hash(seed, 200 + bed);
        if (bed % 2 == 0) {
            // the hard sandstones that stand as cliffs
            return red ? n < 0.55F ? Blocks.RED_SANDSTONE.defaultBlockState() : n < 0.85F ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()
                    : Blocks.CUT_RED_SANDSTONE.defaultBlockState()
                    : n < 0.55F ? Blocks.SANDSTONE.defaultBlockState() : n < 0.85F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : Blocks.CUT_SANDSTONE.defaultBlockState();
        }
        // the soft mudstones between them, in bands of colour
        if (red) {
            return n < 0.25F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : n < 0.45F ? Blocks.RED_TERRACOTTA.defaultBlockState()
                    : n < 0.6F ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : n < 0.75F ? Blocks.TERRACOTTA.defaultBlockState()
                    : n < 0.88F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState();
        }
        return n < 0.3F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : n < 0.5F ? Blocks.WHITE_TERRACOTTA.defaultBlockState()
                : n < 0.7F ? Blocks.TERRACOTTA.defaultBlockState() : n < 0.85F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState()
                : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    }

    /** The ground under a column, past any lily pads too (which {@link Terrain#ground} stops at). */
    static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = Terrain.ground(level, pos, x, z);
        while (y > level.getMinBuildHeight() && level.getBlockState(pos.set(x, y, z)).is(Blocks.LILY_PAD)) {
            y--;
            for (BlockState s = level.getBlockState(pos.set(x, y, z)); y > level.getMinBuildHeight()
                    && (Terrain.cover(s) || s.getBlock() instanceof LiquidBlock); s = level.getBlockState(pos.set(x, y, z))) {
                y--;
            }
        }
        return y;
    }

    /** How high a column has to be cleared to take away all that stands on its ground: any water, and lily pads on it. */
    static int standing(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
        int top = Math.max(ground, Terrain.surface(level, pos, x, ground, z));
        return level.getBlockState(pos.set(x, top + 1, z)).is(Blocks.LILY_PAD) ? top + 1 : top;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // how far each column here (and two round them) is from the river, and how far down the river that is
        int gx = x0 - 2, gz = z0 - 2, gw = x1 - x0 + 5, gd = z1 - z0 + 5;
        float[] dist = new float[gw * gd], along = new float[gw * gd];
        nearest(course(), gx, gz, gw, gd, dist, along);
        BiPredicate<Integer, Integer> outside = (nx, nz) -> dist[(nx - gx) * gd + (nz - gz)] >= OUT;
        int base = b.minY(), span = b.maxY() - base + 1;
        int[] beds = beds(span);
        float[] cum = new float[span];
        // (the hard beds stand almost sheer, the soft ones between them weather back into ledges)
        for (int i = 1; i < span; i++) cum[i] = cum[i - 1] + (beds[i - 1] % 2 == 0 ? 0.06F : 1.1F);
        int low = Integer.MAX_VALUE;
        for (int l : levels) low = Math.min(low, l);
        int sound = Math.max(b.minY(), low - 5);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int k = (x - gx) * gd + (z - gz);
                float d = dist[k], s = along[k];
                if (d >= OUT) continue;
                int ground = ground(level, pos, x, z);
                if (ground > b.maxY() - 2) continue;
                boolean red = level.getBiome(pos.set(x, ground, z)).is(BiomeTags.IS_BADLANDS);
                int top = topAt(d, s, ground, cum, base), w = water(s);
                boolean floor = d < floorHalf(s, ends(s)), wet = floor && wet(dist, along, k, gd);
                int standing = Math.min(b.maxY(), standing(level, pos, x, ground, z));
                // the rock is made whole as deep as the river goes: no pocket of water left in it to run out
                for (int h = sound; h <= Math.min(top, ground); h++) {
                    if (!level.getFluidState(pos.set(x, h, z)).isEmpty()) Terrain.set(level, pos, x, h, z, rock(beds[h - base], red, seed));
                }
                if (!floor && top >= ground && standing == ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(12, b.maxY() - ground));
                for (int h = Math.max(sound, top + 1); h <= standing; h++) {
                    // (a pond just outside keeps a wall of rock between it and the canyon)
                    Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, outside)
                            ? rock(beds[h - base], red, seed) : Blocks.AIR.defaultBlockState());
                }
                float n = Terrain.noise(seed, x, top, z);
                if (floor) {
                    boolean channel = fullDepth(s) && d < riverHalf(s);
                    floor(level, pos, x, z, ground, top, w, wet, channel, d < riverHalf(s) * 0.5F, red, n, beds, base, sound, dist, along, k, gd);
                    continue;
                }
                if (top >= ground) continue;
                // the walls: bare rock in its beds, as deep as the face the next column down leaves open
                int face = top;
                for (int j : new int[]{k - gd, k + gd, k - 1, k + 1}) {
                    face = Math.min(face, dist[j] >= OUT ? ground : topAt(dist[j], along[j], ground, cum, base));
                }
                for (int h = Math.max(sound, Math.min(face, top - 2)); h <= top; h++) {
                    if (level.getBlockState(pos.set(x, h, z)).isAir()) continue; // a cave opening in the wall
                    Terrain.set(level, pos, x, h, z, rock(beds[h - base], red, seed));
                }
                // loose rubble lying on the soft ledges, and the odd dead bush in it
                if (beds[top - base] % 2 == 1 && n < 0.4F && level.getBlockState(pos.set(x, top - 1, z)).blocksMotion()) {
                    Terrain.set(level, pos, x, top, z, n < 0.3F ? red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState());
                    if (n < 0.03F) Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /** The canyon floor: the river lying still in its reaches between banks of sand, with reeds of cane along it. */
    private void floor(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int top, int w, boolean wet, boolean channel,
                       boolean deep, boolean red, float n, int[] beds, int base, int sound, float[] dist, float[] along, int k, int gd) {
        BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
        int solid = wet ? w - (deep ? 2 : 1) : top;
        // sound rock under it all (filling any hollow or cave, so the river can't drain away)
        for (int h = sound; h < solid; h++) {
            BlockState here = level.getBlockState(pos.set(x, h, z));
            if (h >= solid - 1 || h > ground || here.isAir() || !here.getFluidState().isEmpty() || Terrain.cover(here)) {
                Terrain.set(level, pos, x, h, z, rock(beds[h - base] & ~1, red, seed));
            }
        }
        if (wet) {
            Terrain.set(level, pos, x, solid, z, n < 0.55F ? Blocks.GRAVEL.defaultBlockState() : n < 0.85F ? sand : Blocks.CLAY.defaultBlockState());
            for (int h = solid + 1; h <= w; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            return;
        }
        if (channel) {
            // a sill of rock and cobbles where one reach steps down to the next
            Terrain.set(level, pos, x, top, z, n < 0.5F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.8F ? Blocks.GRAVEL.defaultBlockState()
                    : rock(beds[top - base] & ~1, red, seed));
            return;
        }
        boolean gravel = Terrain.field(seed + 5, x, z, 5.0) > 0.75F;
        Terrain.set(level, pos, x, top, z, gravel ? Blocks.GRAVEL.defaultBlockState() : sand);
        if (top == w) {
            // reeds of cane along the water's edge
            boolean byWater = false;
            for (int j : new int[]{k - gd, k + gd, k - 1, k + 1}) byWater |= wet(dist, along, j, gd) && water(along[j]) == w;
            if (byWater && !gravel && n > 0.6F) {
                int tall = 1 + Math.round(Terrain.noise(seed + 6, x, top, z) * 2.0F);
                for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, top + h, z, Blocks.SUGAR_CANE.defaultBlockState());
            }
        } else if (n < 0.03F && !gravel) {
            Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        } else if (n > 0.992F) {
            Terrain.set(level, pos, x, top + 1, z, rock(beds[top + 1 - base] & ~1, red, seed)); // a block fallen from the cliffs
        }
    }

    /**
     * Fills in, for every column of a patch, its distance from the river's middle line ({@code dist}) and how far down
     * the river the nearest point of it is ({@code along}).
     */
    private static void nearest(float[][] p, int gx, int gz, int gw, int gd, float[] dist, float[] along) {
        float[] xs = p[0], zs = p[1];
        float reach = OUT + 2.0F;
        java.util.Arrays.fill(dist, Float.MAX_VALUE);
        for (int i = 0; i + 1 < xs.length; i++) {
            float ax = xs[i], az = zs[i], bx = xs[i + 1], bz = zs[i + 1];
            int ix0 = Math.max(0, Mth.floor(Math.min(ax, bx) - reach) - gx), ix1 = Math.min(gw - 1, Mth.ceil(Math.max(ax, bx) + reach) - gx);
            int iz0 = Math.max(0, Mth.floor(Math.min(az, bz) - reach) - gz), iz1 = Math.min(gd - 1, Mth.ceil(Math.max(az, bz) + reach) - gz);
            float ex = bx - ax, ez = bz - az, len = ex * ex + ez * ez;
            for (int ix = ix0; ix <= ix1; ix++) {
                for (int iz = iz0; iz <= iz1; iz++) {
                    float px = gx + ix + 0.5F - ax, pz = gz + iz + 0.5F - az;
                    float t = len > 0.0F ? Mth.clamp((px * ex + pz * ez) / len, 0.0F, 1.0F) : 0.0F;
                    float dx = px - t * ex, dz = pz - t * ez, dd = dx * dx + dz * dz;
                    int k = ix * gd + iz;
                    if (dd < dist[k]) {
                        dist[k] = dd;
                        along[k] = i + t;
                    }
                }
            }
        }
        for (int k = 0; k < dist.length; k++) {
            if (dist[k] != Float.MAX_VALUE) dist[k] = Mth.sqrt(dist[k]);
        }
    }
}
