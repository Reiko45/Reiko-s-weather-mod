package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A crystal cave, like the Cave of the Crystals at Naica in Mexico: a cavern deep under a desert mountain, sitting over
 * a body of magma, that filled with hot water thick with dissolved gypsum. For half a million years the water stayed
 * just warm enough, and so still, that the gypsum came out of it a molecule at a time and grew into selenite crystals of
 * a size found nowhere else: milky, glassy beams as thick as tree trunks and as long as a house, thrusting up out of
 * the floor and out of the walls at every angle and crossing one another from side to side, with smaller sprays of
 * crystal on the walls and roof. The water has mostly gone now, leaving a shallow pool on the floor among heaps of white
 * gypsum rubble, and the only way in is a narrow, winding passage climbing up to a small hole in the hillside. It's
 * utterly dark, save for a few patches of glowing lichen.
 *
 * @param y      the bottom of the cavern's floor (its pool lies a block or two above it)
 * @param aspect how much narrower it is across than along, and {@code angle} the way it runs
 * @param beams  {start x, y, z, end x, y, z (all in tenths of a block), radius (tenths), axis} for each great crystal
 * @param path   {x, floor y, z} for each point along the passage up to the surface, from inside the cavern out
 * @param top    the highest ground round the passage's mouth (for the bounds)
 */
public record CrystalCave(int cx, int cz, int y, int radius, int height, float aspect, float angle, int[] beams, int[] path, int top, long seed)
        implements Landform {
    /** How far apart the points along the passage are. */
    private static final int STEP = 3;
    private static final int BEAM = 8;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 6 || !c.fits(ground, 70, 24)) {
            return null;
        }
        RandomSource random = c.random();
        int radius = 14 + random.nextInt(9), height = 10 + random.nextInt(9), below = 20 + random.nextInt(16);
        float aspect = 0.7F + 0.25F * random.nextFloat(), angle = random.nextFloat() * Mth.PI;
        long seed = random.nextLong();
        CrystalCave probe = new CrystalCave(c.x(), c.z(), 0, radius, height, aspect, angle, new int[0], new int[0], 0, seed);
        // the rock over it, round its edge and half-way in: a mountain's worth, with no sea or deep valley over it anywhere
        int[] ring = new int[8];
        int low = ground;
        for (int i = 0; i < 12; i++) {
            double[] p = i < 8 ? probe.edge(i * Mth.TWO_PI / 8.0F, 1.0F) : probe.edge((i - 8) * Mth.HALF_PI + Mth.PI / 4.0F, 0.5F);
            int h = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            if (i < 8) ring[i] = h;
            low = Math.min(low, h);
            if (low < c.seaLevel() + 1) return null;
        }
        // well down, and roofed with plenty of sound rock even under the lowest ground over it
        int y = Math.min(ground - below, low - Math.round(height * 1.2F) - 9);
        if (ground - y > 50 || y < c.seaLevel() - 30 || !c.fits(y, 8, 0)) {
            return null;
        }
        probe = new CrystalCave(c.x(), c.z(), y, radius, height, aspect, angle, new int[0], new int[0], 0, seed);
        // the passage sets off toward the lowest ground round it, so it comes out on a hillside if there's one
        Integer[] order = {0, 1, 2, 3, 4, 5, 6, 7};
        java.util.Arrays.sort(order, (p, q) -> Integer.compare(ring[p], ring[q]));
        for (int k = 0; k < 3; k++) {
            Climb climb = probe.climb(c, order[k] * Mth.TWO_PI / 8.0F, random);
            if (climb != null) {
                return new CrystalCave(c.x(), c.z(), y, radius, height, aspect, angle, probe.crystals(random), climb.path, climb.top, seed);
            }
        }
        return null;
    }

    private record Climb(int[] path, int top) {}

    /** A passage climbing from the cavern up to daylight, setting off the way {@code t} points; null if it can't get out. */
    @Nullable
    private Climb climb(PlanContext c, float t, RandomSource random) {
        double[] start = edge(t, 0.55F), rim = edge(t, 1.0F);
        float a = (float) Mth.atan2(rim[1] - start[1], rim[0] - start[0]), phase = random.nextFloat() * Mth.TWO_PI;
        int[] s0 = shape(Mth.floor(start[0]), Mth.floor(start[1]));
        int f = Math.max(water(), s0 == null ? water() : s0[0]);
        List<int[]> nodes = new ArrayList<>();
        nodes.add(new int[]{Mth.floor(start[0]), f, Mth.floor(start[1])});
        double px = start[0], pz = start[1];
        int topY = Integer.MIN_VALUE, trench = 0;
        boolean mouth = false;
        for (int i = 1; i <= 40; i++) {
            // winding as it climbs
            float heading = a + 0.7F * Mth.sin(i * 0.5F + phase) + 0.25F * Mth.sin(i * 1.3F + phase * 2.0F);
            px += STEP * Mth.cos(heading);
            pz += STEP * Mth.sin(heading);
            f += 2;
            int nx = Mth.floor(px), nz = Mth.floor(pz);
            if (Math.abs(nx - cx) > 96 || Math.abs(nz - cz) > 96) return null;
            nodes.add(new int[]{nx, f, nz});
            if (out(nx + 0.5, nz + 0.5) < 1.3F) continue; // still in the cavern's own rock, which is deeply roofed
            int g = c.groundAt(nx, nz);
            if (g - f <= 5) mouth = true;
            if (mouth) {
                // the mouth: a short, steep cutting up to the surface, never by the sea
                if (g < c.seaLevel() + 2) return null;
                topY = Math.max(topY, g);
                if (f >= g - 1) {
                    int[] path = new int[nodes.size() * 3];
                    for (int k = 0; k < nodes.size(); k++) System.arraycopy(nodes.get(k), 0, path, k * 3, 3);
                    return new Climb(path, Math.max(topY, f));
                }
                if (++trench > 6) return null;
            }
        }
        return null;
    }

    /** The great crystals: most rooted in the floor, some in the walls, leaning every way and crossing one another. */
    private int[] crystals(RandomSource random) {
        int n = Mth.clamp(7 + (radius - 14) + random.nextInt(6), 8, 20);
        int[] out = new int[n * BEAM];
        for (int i = 0; i < n; i++) {
            float t = random.nextFloat() * Mth.TWO_PI, sy, az, el;
            double[] p;
            if (random.nextFloat() < 0.35F) {
                p = edge(t, 0.88F);
                int[] s = shape(Mth.floor(p[0]), Mth.floor(p[1]));
                sy = (s == null ? y + 3 : s[0]) + 1.0F + random.nextFloat() * 2.0F;
                // out of the wall, roughly toward the middle
                az = (float) Mth.atan2(cz + 0.5 - p[1], cx + 0.5 - p[0]) + (random.nextFloat() - 0.5F) * 1.7F;
                el = (random.nextFloat() * 55.0F - 12.0F) * Mth.DEG_TO_RAD;
            } else {
                p = edge(t, Mth.sqrt(random.nextFloat()) * 0.75F);
                int[] s = shape(Mth.floor(p[0]), Mth.floor(p[1]));
                sy = (s == null ? y : s[0]) - 0.5F;
                az = random.nextFloat() * Mth.TWO_PI;
                el = (12.0F + random.nextFloat() * 55.0F) * Mth.DEG_TO_RAD;
            }
            float len = 8 + random.nextInt(Math.min(18, radius + 4));
            float ux = Mth.cos(el) * Mth.cos(az), uy = Mth.sin(el), uz = Mth.cos(el) * Mth.sin(az);
            int axis = Math.abs(uy) >= Math.max(Math.abs(ux), Math.abs(uz)) ? 1 : Math.abs(ux) >= Math.abs(uz) ? 0 : 2;
            int[] beam = {Math.round((float) p[0] * 10.0F), Math.round(sy * 10.0F), Math.round((float) p[1] * 10.0F), Math.round(((float) p[0] + ux * len) * 10.0F),
                    Math.round((sy + uy * len) * 10.0F), Math.round(((float) p[1] + uz * len) * 10.0F), random.nextFloat() < 0.6F ? 10 : 15, axis};
            System.arraycopy(beam, 0, out, i * BEAM, BEAM);
        }
        return out;
    }

    static Landform load(CompoundTag tag) {
        return new CrystalCave(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getInt("height"), tag.getFloat("aspect"), tag.getFloat("angle"),
                tag.getIntArray("beams"), tag.getIntArray("path"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putInt("height", height);
        tag.putFloat("aspect", aspect);
        tag.putFloat("angle", angle);
        tag.put("beams", new IntArrayTag(beams));
        tag.put("path", new IntArrayTag(path));
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CRYSTAL_CAVE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * 1.28F) + 3;
        int minX = cx - r, maxX = cx + r, minZ = cz - r, maxZ = cz + r;
        for (int i = 0; i + 2 < path.length; i += 3) {
            minX = Math.min(minX, path[i] - 7);
            maxX = Math.max(maxX, path[i] + 7);
            minZ = Math.min(minZ, path[i + 2] - 7);
            maxZ = Math.max(maxZ, path[i + 2] + 7);
        }
        return new BoundingBox(minX, y - 4, minZ, maxX, Math.max(top + 14, y + height + 12), maxZ);
    }

    /** The pool's level: a still sheet over the lowest part of the floor. */
    private int water() {
        return y + 2;
    }

    private float wobble(float theta) {
        return 1.0F + 0.1F * Mth.sin(2.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.07F * Mth.sin(3.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI)
                + 0.04F * Mth.sin(5.0F * theta + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** A point {x, z} at {@code s} of the way out to the cavern's edge, the way {@code t} points round it. */
    private double[] edge(float t, float s) {
        float r = s * radius * wobble(t);
        return Terrain.xz(r * Mth.cos(t), r * Mth.sin(t) * aspect, cx + 0.5, cz + 0.5, angle);
    }

    /** How far out from the middle a point is, as a fraction of the cavern's radius that way. */
    private float out(double x, double z) {
        float[] l = Terrain.uv(x, z, cx + 0.5, cz + 0.5, angle);
        float u = l[0], v = l[1] / aspect;
        return Mth.sqrt(u * u + v * v) / (radius * wobble((float) Mth.atan2(v, u)));
    }

    /**
     * The cavern at a column, {the top of its floor, the underside of its roof} (no cavern where they meet), or null well
     * outside it: a lens, flat-floored in the middle and rising to meet a rounded roof all round, lumpy with old
     * solution hollows.
     */
    @Nullable
    private int[] shape(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        float u = l[0], v = l[1] / aspect;
        float r = Mth.sqrt(u * u + v * v) / (radius * wobble((float) Mth.atan2(v, u)));
        if (r >= 1.05F) return null;
        float rr = Math.min(1.0F, r * r);
        float swell = 0.85F + 0.3F * Terrain.field(seed + 1, x, z, 9.0);
        int roof = y + 1 + Math.round(height * Mth.sqrt(1.0F - rr) * swell + 2.5F * (Terrain.field(seed + 2, x, z, 5.0) - 0.5F));
        // the floor tilts a little toward one end, where the last of the water has gathered
        float tilt = (u * Mth.cos(Terrain.hash(seed, 5) * Mth.TWO_PI) + v * Mth.sin(Terrain.hash(seed, 5) * Mth.TWO_PI)) / radius;
        int floor = y + Math.round(6.5F * Terrain.smooth((r - 0.4F) / 0.55F) + 1.6F * Terrain.field(seed + 3, x, z, 7.0) + 1.2F * tilt - 0.2F);
        return new int[]{Math.max(y, floor), roof};
    }

    /** The passage at a column: {how far off its line, its floor, how far along it, its half-width}, or null well away from it. */
    @Nullable
    private float[] passage(int x, int z) {
        int n = path.length / 3;
        float best = Float.MAX_VALUE, floor = 0.0F, along = 0.0F;
        for (int i = 0; i + 1 < n; i++) {
            float ax = path[i * 3] + 0.5F, az = path[i * 3 + 2] + 0.5F, ex = path[i * 3 + 3] - path[i * 3], ez = path[i * 3 + 5] - path[i * 3 + 2];
            float l2 = ex * ex + ez * ez;
            float t = l2 < 0.01F ? 0.0F : Mth.clamp(((x + 0.5F - ax) * ex + (z + 0.5F - az) * ez) / l2, 0.0F, 1.0F);
            float d2 = Mth.square(ax + ex * t - x - 0.5F) + Mth.square(az + ez * t - z - 0.5F);
            if (d2 < best) {
                best = d2;
                floor = Mth.lerp(t, path[i * 3 + 1], path[i * 3 + 4]);
                along = (i + t) * STEP;
            }
        }
        float half = 1.1F + 0.6F * Terrain.field(seed + 60, along, 0.0, 7.0);
        if (along > (n - 3) * STEP) half += 0.6F; // the mouth opens out a little
        float d = Mth.sqrt(best);
        return d > half + 3.0F ? null : new float[]{d, floor, along, half};
    }

    /** The great crystals that might reach into a stretch of columns: {start x, y, z, end x, y, z, radius, axis, lowest y, highest y}. */
    private List<float[]> crystalsNear(int x0, int z0, int x1, int z1) {
        List<float[]> near = new ArrayList<>();
        for (int i = 0; i + BEAM - 1 < beams.length; i += BEAM) {
            float sx = beams[i] / 10.0F, sy = beams[i + 1] / 10.0F, sz = beams[i + 2] / 10.0F;
            float ex = beams[i + 3] / 10.0F, ey = beams[i + 4] / 10.0F, ez = beams[i + 5] / 10.0F, r = beams[i + 6] / 10.0F;
            if (Math.max(sx, ex) + r + 1 < x0 || Math.min(sx, ex) - r - 1 > x1 + 1 || Math.max(sz, ez) + r + 1 < z0 || Math.min(sz, ez) - r - 1 > z1 + 1) continue;
            near.add(new float[]{sx, sy, sz, ex, ey, ez, r, beams[i + 7], Math.min(sy, ey) - r - 1, Math.max(sy, ey) + r + 1});
        }
        return near;
    }

    /** Which way the crystal through a block lies (0-2 for x, y, z), or -1 where there's none. */
    private static int crystal(List<float[]> near, int x, int h, int z) {
        float px = x + 0.5F, py = h + 0.5F, pz = z + 0.5F;
        for (float[] k : near) {
            if (py < k[8] || py > k[9]) continue;
            float dx = k[3] - k[0], dy = k[4] - k[1], dz = k[5] - k[2];
            float l2 = dx * dx + dy * dy + dz * dz;
            float t = Mth.clamp(((px - k[0]) * dx + (py - k[1]) * dy + (pz - k[2]) * dz) / l2, 0.0F, 1.0F);
            float d2 = Mth.square(k[0] + dx * t - px) + Mth.square(k[1] + dy * t - py) + Mth.square(k[2] + dz * t - pz);
            if (d2 < k[6] * k[6] + 0.1F) return (int) k[7];
        }
        return -1;
    }

    private static BlockState selenite(int axis) {
        return ModBlocks.SELENITE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.values()[axis]);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the cavern's floor and roof over this stretch and two blocks round it, to work out the shell of sound rock round it
        int w = x1 - x0 + 5, l = z1 - z0 + 5;
        int[] fl = new int[w * l], rf = new int[w * l];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < l; j++) {
                int[] s = shape(x0 - 2 + i, z0 - 2 + j);
                fl[i * l + j] = s == null ? 0 : s[0];
                rf[i * l + j] = s == null || s[1] <= s[0] + 1 ? Integer.MIN_VALUE : s[1];
            }
        }
        List<float[]> near = crystalsNear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
        int water = water(), sea = level.getSeaLevel();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int idx = (x - x0 + 2) * l + (z - z0 + 2);
                int ff = fl[idx], rr = rf[idx];
                // the shell: everything within a couple of blocks of the cavern is made sound rock, so no water or cave can break into it
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int di = -2; di <= 2; di++) {
                    for (int dj = -2; dj <= 2; dj++) {
                        int k = idx + di * l + dj;
                        if (rf[k] == Integer.MIN_VALUE) continue;
                        lo = Math.min(lo, fl[k] - 2);
                        hi = Math.max(hi, rf[k] + 2);
                    }
                }
                float[] pp = passage(x, z);
                if (lo == Integer.MAX_VALUE && pp == null) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // (where the ground dips low over it the roof comes down to keep it covered)
                if (rr != Integer.MIN_VALUE) rr = Math.min(rr, ground - 5);
                boolean cav = rr > ff + 1;
                hi = Math.min(hi, ground - 3);
                // the passage: a narrow, winding tunnel, opening out to the sky in a short cutting at its mouth
                boolean inP = false, open = false, band = false;
                int pf = 0, ptop = 0, flood = ground, to = hi;
                if (pp != null) {
                    pf = Mth.floor(pp[1] + 0.5F);
                    int ph = pp[3] > 1.45F ? 4 : 3;
                    flood = Terrain.surface(level, pos, x, ground, z);
                    inP = pp[0] <= pp[3] && pf < ground;
                    open = inP && ground - (pf + ph) <= 1 && ground > sea && Math.max(ground, flood) <= b.maxY() - 10;
                    ptop = open ? Math.max(ground, flood) : Math.min(pf + ph, ground - 2);
                    // ponds near the mouth (beside the cutting, or over the tunnel just short of it) are walled in
                    band = !open && pp[0] <= pp[3] + 2.5F && pf + 10 >= ground && flood > ground;
                    // (the shell round it reaches far enough up and down to cover its neighbours, whose floors can be a couple of blocks off at a bend)
                    to = Math.max(to, open ? ptop : Math.min(pf + 7, ground));
                    if (band) to = Math.max(to, Math.min(flood, b.maxY()));
                }
                if (open) Terrain.clearAbove(level, pos, x, ptop, z, Math.min(12, b.maxY() - ptop));
                int from = Math.min(lo, pp != null ? pf - 2 : Integer.MAX_VALUE);
                boolean below = false;
                for (int h = Math.max(b.minY(), from); h <= Math.min(to, b.maxY()); h++) {
                    BlockState s = null;
                    if (cav && h > ff && h < rr) {
                        int k = crystal(near, x, h, z);
                        s = k >= 0 ? selenite(k) : h <= water ? Blocks.WATER.defaultBlockState() : air;
                    } else if (inP && h > pf && h <= ptop) {
                        s = air;
                    } else if (h >= lo && h <= hi) {
                        int k = crystal(near, x, h, z);
                        s = k >= 0 ? selenite(k) : cav && h == ff && ff < water ? poolBed(x, h, z) : rock(x, h, z);
                    } else if (pp != null && pp[0] <= pp[3] + 2.5F) {
                        BlockState here = level.getBlockState(pos.set(x, h, z));
                        if (h > ground ? band : here.isAir() || !here.getFluidState().isEmpty() || below && here.getBlock() instanceof FallingBlock) {
                            s = rockFor(here, x, h, z);
                        }
                    } else if (below) {
                        BlockState here = level.getBlockState(pos.set(x, h, z));
                        if (here.getBlock() instanceof FallingBlock) s = rockFor(here, x, h, z);
                    }
                    if (s != null) Terrain.set(level, pos, x, h, z, s);
                    below = s != null && !s.blocksMotion();
                }
                if (cav) decorate(level, pos, x, z, ff, rr, water);
            }
        }
    }

    /** The cavern's floor and roof, once it's hollowed out: gypsum rubble, sprays of small crystals, dripstone, and a little lichen. */
    private void decorate(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ff, int rr, int water) {
        BlockState selenite = ModBlocks.SELENITE.get().defaultBlockState();
        boolean lichen = Terrain.field(seed + 20, x, z, 6.0) > 0.82F;
        if (ff >= water && level.getBlockState(pos.set(x, ff + 1, z)).isAir()) {
            BlockState fb = level.getBlockState(pos.set(x, ff, z));
            if (Terrain.field(seed + 23, x, z, 5.0) > 0.5F && !fb.is(ModBlocks.SELENITE.get()) && level.getBlockState(pos.set(x, ff - 1, z)).blocksMotion()) {
                Terrain.set(level, pos, x, ff, z, ModBlocks.GYPSUM_SAND.get().defaultBlockState());
            }
            float n = Terrain.noise(seed + 24, x, ff, z);
            if (n < 0.05F) {
                // a lump of fallen crystal or gypsum
                Terrain.set(level, pos, x, ff + 1, z, n < 0.025F ? Blocks.CALCITE.defaultBlockState() : selenite(Math.floorMod(x * 7 + z * 3, 3)));
            } else if (n < 0.08F && rr - ff > 4) {
                // a small crystal standing up out of the floor
                Terrain.set(level, pos, x, ff + 1, z, selenite);
                if (n < 0.065F && level.getBlockState(pos.set(x, ff + 2, z)).isAir()) Terrain.set(level, pos, x, ff + 2, z, selenite);
            } else if (lichen && n < 0.4F) {
                Terrain.set(level, pos, x, ff + 1, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true));
            }
        }
        int c = rr - 1;
        if (c <= Math.max(ff + 2, water + 1) || !level.getBlockState(pos.set(x, c, z)).isAir() || !level.getBlockState(pos.set(x, rr, z)).blocksMotion()) return;
        float n = Terrain.noise(seed + 25, x, c, z);
        if (lichen && n < 0.45F) {
            Terrain.set(level, pos, x, c, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
        } else if (Terrain.field(seed + 21, x, z, 4.0) > 0.62F && n < 0.45F) {
            // sprays of smaller crystals hanging from the roof (lying along the wall where it curves down to the floor)
            float r = out(x + 0.5, z + 0.5);
            int axis = r < 0.7F ? 1 : Math.abs(x + 0.5F - cx) > Math.abs(z + 0.5F - cz) ? 0 : 2;
            int len = 1 + Math.round(Terrain.noise(seed + 26, x, c, z) * 2.0F);
            for (int h = c; h > c - len && h > Math.max(ff + 2, water + 1); h--) {
                if (!level.getBlockState(pos.set(x, h, z)).isAir()) break;
                Terrain.set(level, pos, x, h, z, selenite(axis));
            }
        } else if (n < 0.04F) {
            boolean two = n < 0.02F && c - 1 > Math.max(ff + 2, water + 1) && level.getBlockState(pos.set(x, c - 1, z)).isAir();
            BlockState drip = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
            Terrain.set(level, pos, x, c, z, drip.setValue(PointedDripstoneBlock.THICKNESS, two ? DripstoneThickness.FRUSTUM : DripstoneThickness.TIP));
            if (two) Terrain.set(level, pos, x, c - 1, z, drip.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
        }
    }

    /** The cavern's rock: grey stone, with bands of tuff, and pale calcite and dripstone where the water once seeped through. */
    private BlockState rock(int x, int h, int z) {
        float band = Terrain.field(seed + 7, x * 0.8 + h * 1.1, z * 0.8 - h * 0.6, 4.0);
        float n = Terrain.noise(seed + 8, x, h, z);
        if (band > 0.74F) return n < 0.65F ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
        if (band < 0.18F) return n < 0.55F ? Blocks.CALCITE.defaultBlockState() : Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        if (n < 0.05F) return Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        if (n < 0.09F) return Blocks.CALCITE.defaultBlockState();
        if (n < 0.12F) return Blocks.TUFF.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    /** The bottom of the pool: fine white gypsum mud set hard, and clay. */
    private BlockState poolBed(int x, int h, int z) {
        return Terrain.noise(seed + 27, x, h, z) < 0.5F ? Blocks.CLAY.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
    }

    /** Rock to fill in round the passage: sandstone where the ground was sand (so nothing loose hangs over it), stone elsewhere. */
    private BlockState rockFor(BlockState here, int x, int h, int z) {
        if (here.is(Blocks.RED_SAND)) return Blocks.RED_SANDSTONE.defaultBlockState();
        if (here.is(BlockTags.SAND)) return Blocks.SANDSTONE.defaultBlockState();
        if (here.is(BlockTags.TERRACOTTA)) return here;
        return Terrain.noise(seed + 28, x, h, z) < 0.8F ? Blocks.STONE.defaultBlockState() : rock(x, h, z);
    }
}
