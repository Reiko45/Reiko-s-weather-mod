package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Earth hummocks (thúfur in Iceland, pounus in Finland): a stretch of cold, wet grassland or tundra crowded with little
 * rounded mounds of turf, hundreds of them, packed so close that crossing it means teetering from one to the next or
 * stumbling in the wet troughs between. Each winter the frost works down into the waterlogged, silty soil, and the ice
 * growing in it heaves the ground up. Wherever it heaves a little more (where the turf is thicker, or the snow thinner),
 * a bump starts; the bump sheds snow and freezes deeper, so next winter it heaves more again, while the roots and moss
 * of the turf bind it together so that it never quite settles back in the thaw. Between the mounds the troughs stay low
 * and damp, mossy and muddy, and after the snowmelt water stands in some of them.
 *
 * @param y       the height of the ground in the middle of the patch
 * @param radius  how far the patch reaches from its middle, on average
 * @param angle   the bearing of the loose, crooked rows the hummocks fall into
 * @param spacing how far apart the hummocks are, in tenths of a block
 */
public record EarthHummocks(int cx, int cz, int y, int radius, float angle, int spacing, long seed) implements Landform {
    private static final BlockState[] FLOWERS = {Blocks.DANDELION.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 30, 34)) {
            return null;
        }
        // the gentlest of a few spots round the chunk's middle (the middle itself, if it's level enough)
        int bx = c.x(), bz = c.z(), best = Integer.MAX_VALUE;
        for (int k = -1; k < 8 && best > 5; k++) {
            int px = k < 0 ? c.x() : c.x() + Math.round(Mth.cos(k * Mth.PI / 4.0F) * 20.0F);
            int pz = k < 0 ? c.z() : c.z() + Math.round(Mth.sin(k * Mth.PI / 4.0F) * 20.0F);
            int spread = spread(c, px, pz, 14, 8);
            // (on a mountainside there's no point looking further)
            if (k < 0 && spread > 18) {
                return null;
            }
            if (spread < best) {
                best = spread;
                bx = px;
                bz = pz;
            }
        }
        if (best > 8) {
            return null;
        }
        // as wide a patch as the gentle ground round it allows
        RandomSource random = c.random();
        int radius = 18 + random.nextInt(5);
        if (spread(c, bx, bz, 26, 12) <= 8) {
            radius = 26 + random.nextInt(7);
            if (spread(c, bx, bz, 38, 12) <= 9) {
                radius = 34 + random.nextInt(10);
            }
        }
        return new EarthHummocks(bx, bz, c.groundAt(bx, bz), radius, random.nextFloat() * Mth.TWO_PI, 32 + random.nextInt(8), random.nextLong());
    }

    /**
     * How much the ground rises and falls round (x, z): the highest less the lowest of it and a ring of points round it
     * (a lot, if any of it is down at the sea).
     */
    private static int spread(PlanContext c, int x, int z, int r, int n) {
        int lo = c.groundAt(x, z), hi = lo;
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            int h = c.groundAt(x + Math.round(Mth.cos(a) * r), z + Math.round(Mth.sin(a) * r));
            lo = Math.min(lo, h);
            hi = Math.max(hi, h);
        }
        return lo <= c.seaLevel() ? 999 : hi - lo;
    }

    static Landform load(CompoundTag tag) {
        return new EarthHummocks(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getFloat("angle"), tag.getInt("spacing"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.putInt("spacing", spacing);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.EARTH_HUMMOCKS;
    }

    @Override
    public BoundingBox bounds() {
        // (the outline's lobes and wobble reach out to a little under 1.44 times the radius)
        int r = Mth.ceil(radius * 1.44F) + 1;
        return new BoundingBox(cx - r, y - 26, cz - r, cx + r, y + 30, cz + r);
    }

    /** How far into the patch a point is: 1 in the thick of it, falling away to 0 at its ragged edge. */
    private float patch(float px, float pz) {
        float dx = px - cx, dz = pz - cz;
        float th = (float) Mth.atan2(dz, dx);
        float lobes = 1.0F + 0.14F * Mth.sin(2.0F * th + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.09F * Mth.sin(3.0F * th + Terrain.hash(seed, 2) * Mth.TWO_PI)
                + 0.05F * Mth.sin(5.0F * th + Terrain.hash(seed, 3) * Mth.TWO_PI);
        float edge = radius * lobes * (0.88F + 0.24F * Terrain.field(seed + 2, px, pz, 9.0));
        return Terrain.smooth((edge - Mth.sqrt(dx * dx + dz * dz)) / (radius * 0.35F));
    }

    /**
     * The hummock over a point, {its height there (0 in a trough), and two 0-1 values to tell it from its neighbours}.
     * They lie roughly in staggered rows, but the rows wander and each hummock is jostled out of line, some are missing,
     * and they're all different sizes, some drawn out longer one way, so the field looks grown rather than laid out.
     * Each keeps a narrow trough between itself and the next (though now and then two have grown into one). Toward the
     * edge of the patch they thin out and shrink away.
     */
    private float[] hummock(float px, float pz) {
        float a = spacing / 10.0F, rowH = a * 0.866F;
        float[] l = Terrain.uv(px, pz, cx, cz, angle);
        float u = l[0] + 2.4F * (Terrain.field(seed + 3, px, pz, 11.0) - 0.5F), v = l[1] + 2.4F * (Terrain.field(seed + 4, px, pz, 11.0) - 0.5F);
        // the hummocks round about: {middle along, across, how far out this point is on it (1 at its foot), cell i, cell j}
        float[][] near = new float[9][];
        int count = 0, best = -1;
        int row = Mth.floor(v / rowH);
        for (int j = row - 1; j <= row + 1; j++) {
            float shift = (j & 1) == 0 ? 0.0F : 0.5F * a;
            int col = Mth.floor((u - shift) / a);
            for (int i = col - 1; i <= col + 1; i++) {
                float cu = (i + 0.5F + 0.55F * (Terrain.noise(seed, i, 1, j) - 0.5F)) * a + shift;
                float cv = (j + 0.5F + 0.55F * (Terrain.noise(seed, i, 2, j) - 0.5F)) * rowH;
                // (how far into the patch its middle is, near enough)
                double[] w = Terrain.xz(cu, cv, cx, cz, angle);
                float p = patch((float) w[0], (float) w[1]);
                if (p <= 0.0F || Terrain.noise(seed, i, 3, j) > 0.93F * (float) Math.pow(p, 0.7)) continue;
                float r = (1.15F + 0.6F * Terrain.noise(seed, i, 4, j)) * (0.55F + 0.45F * p);
                float stretch = Mth.sqrt(1.0F + 0.5F * Terrain.noise(seed, i, 5, j)), turn = Terrain.noise(seed, i, 6, j) * Mth.PI;
                float du = u - cu, dv = v - cv, along = du * Mth.cos(turn) + dv * Mth.sin(turn), across = -du * Mth.sin(turn) + dv * Mth.cos(turn);
                float d = Mth.sqrt(Mth.square(along / stretch) + Mth.square(across * stretch)) / r;
                // (the biggest of them, in the thick of the patch, rise a second block in the middle)
                boolean tall = p > 0.6F && Terrain.noise(seed, i, 7, j) < 0.3F && r > 1.25F;
                near[count] = new float[]{cu, cv, d, i, j, tall ? 1.0F : 0.0F};
                if (best < 0 || d < near[best][2]) best = count;
                count++;
            }
        }
        // (their feet a little ragged)
        if (best < 0 || near[best][2] >= 0.92F + 0.16F * Terrain.field(seed + 5, px, pz, 2.0)) return new float[]{0.0F, 0.0F, 0.0F};
        float[] h = near[best];
        int hi = (int) h[3], hj = (int) h[4];
        for (int k = 0; k < count; k++) {
            float[] o = near[k];
            if (k == best || o[2] > 1.4F) continue;
            int oi = (int) o[3], oj = (int) o[4];
            // (a pair grown together)
            if (Terrain.noise(seed + 9, hi + oi, 31 * (hj + oj), (hi - oi) * (hi - oi) + (hj - oj) * (hj - oj)) < 0.12F) continue;
            // the trough between: this point must lie well to this hummock's side of the line halfway between them
            float eu = h[0] - o[0], ev = h[1] - o[1], gap = Mth.sqrt(eu * eu + ev * ev);
            if (gap > 0.0F && ((u - (h[0] + o[0]) * 0.5F) * eu + (v - (h[1] + o[1]) * 0.5F) * ev) / gap < 0.5F) return new float[]{0.0F, 0.0F, 0.0F};
        }
        return new float[]{h[5] > 0.0F && h[2] < 0.6F ? 2.0F : 1.0F, Terrain.noise(seed, hi, 8, hj), Terrain.noise(seed, hi, 9, hj)};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        int x0 = Math.max(area.minX(), b.minX()), x1 = Math.min(area.maxX(), b.maxX());
        int z0 = Math.max(area.minZ(), b.minZ()), z1 = Math.min(area.maxZ(), b.maxZ());
        if (x0 > x1 || z0 > z1) return;
        int d = z1 - z0 + 1, n = (x1 - x0 + 1) * d;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // first, what each column is and is to be (from the ground as it is before any of this chunk is touched): how high
        // its hummock rises (-1 where the hummocks leave it be)
        int[] ground = new int[n], raise = new int[n];
        float[] strength = new float[n], kind = new float[n], flower = new float[n];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = (x - x0) * d + (z - z0);
                raise[i] = -1;
                float px = x + 0.5F, pz = z + 0.5F, p = patch(px, pz);
                if (p <= 0.0F) continue;
                int g = Terrain.ground(level, pos, x, z);
                ground[i] = g;
                if (g < b.minY() + 3 || g > b.maxY() - 6) continue;
                // only on soil, out of the water, and clear of any tree
                if (!level.getBlockState(pos.set(x, g, z)).is(BlockTags.DIRT) || !level.getFluidState(pos.set(x, g + 1, z)).isEmpty()) continue;
                boolean clear = true;
                for (int h = g + 1; h <= g + 4 && clear; h++) {
                    BlockState s = level.getBlockState(pos.set(x, h, z));
                    clear = (s.isAir() || Terrain.cover(s)) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty();
                }
                if (!clear) continue;
                float[] hm = hummock(px, pz);
                raise[i] = (int) hm[0];
                strength[i] = p;
                kind[i] = hm[1];
                flower[i] = hm[2];
            }
        }
        // then the puddles: in the wettest troughs, wherever the water would be held in on every side by ground standing a
        // block over it (hummocks, or higher ground), or by more of the same puddle. They're only planned where all round
        // them is in this chunk, so the ground round them is known.
        boolean[] puddle = new boolean[n];
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                int i = (x - x0) * d + (z - z0);
                puddle[i] = raise[i] == 0 && strength[i] > 0.5F && Terrain.field(seed + 6, x, z, 6.0) > 0.5F;
            }
        }
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int x = x0 + 1; x < x1; x++) {
                for (int z = z0 + 1; z < z1; z++) {
                    int i = (x - x0) * d + (z - z0);
                    if (!puddle[i]) continue;
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        int k = i + side.getStepX() * d + side.getStepZ();
                        int water = ground[i];
                        // (beside it, the same puddle, or ground at least a block over the water and solid at its level: a
                        // hummock heaved up from the same level, or higher ground with something firm under its top)
                        pos.set(x + side.getStepX(), water, z + side.getStepZ());
                        boolean held = raise[k] >= 0 && (puddle[k] ? ground[k] == water : ground[k] >= water && ground[k] + raise[k] > water
                                && (ground[k] == water || level.getBlockState(pos).isFaceSturdy(level, pos, side.getOpposite())));
                        if (!held) {
                            puddle[i] = false;
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = (x - x0) * d + (z - z0);
                if (raise[i] < 0) continue;
                int g = ground[i];
                if (puddle[i]) {
                    Terrain.clearAbove(level, pos, x, g, z, 4);
                    Terrain.set(level, pos, x, g - 1, z, Blocks.MUD.defaultBlockState());
                    Terrain.set(level, pos, x, g, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                BlockState was = level.getBlockState(pos.set(x, g, z));
                float m = Terrain.noise(seed, x, g, z);
                if (raise[i] > 0) {
                    // a hummock: the soil heaved up under its turf of grass, moss or podzol (more podzol where it's podzol round it)
                    int top = g + raise[i];
                    Terrain.clearAbove(level, pos, x, g, z, 4);
                    for (int h = g; h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.DIRT.defaultBlockState());
                    float k = was.is(Blocks.PODZOL) ? kind[i] * 0.5F : kind[i];
                    BlockState turf = k < 0.18F || Terrain.noise(seed + 1, x, top, z) < 0.08F ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : k < 0.36F ? Blocks.PODZOL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    Terrain.set(level, pos, x, top, z, turf);
                    if (m < 0.32F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    } else if (m < 0.42F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                    } else if (m < 0.432F && !level.getBiome(pos.set(x, top + 1, z)).value().coldEnoughToSnow(pos)) {
                        Terrain.set(level, pos, x, top + 1, z, FLOWERS[Math.min(FLOWERS.length - 1, (int) (flower[i] * FLOWERS.length))]);
                    } else if (m > 0.84F && turf.is(Blocks.MOSS_BLOCK)) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                    continue;
                }
                // a trough: damp, with moss and mud (and the bare, frost-churned soil showing here and there), fading out to
                // the ordinary ground at the patch's edge
                if (Terrain.noise(seed + 2, x, g, z) > strength[i] * 1.2F) continue;
                float wet = Terrain.field(seed + 6, x, z, 6.0) * 0.75F + m * 0.25F;
                // (no mud where it's cold enough to snow: snow won't lie on mud, and it'd show bare through the snow)
                boolean cold = level.getBiome(pos.set(x, g + 1, z)).value().coldEnoughToSnow(pos);
                BlockState floor = wet > 0.56F ? (cold ? Blocks.MOSS_BLOCK : Blocks.MUD).defaultBlockState() : wet > 0.42F ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : Terrain.field(seed + 7, x, z, 3.0) > 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : was;
                if (floor != was) Terrain.set(level, pos, x, g, z, floor);
                float q = Terrain.noise(seed + 3, x, g, z);
                if (!level.getBlockState(pos.set(x, g + 1, z)).isAir()) continue;
                if (floor.is(Blocks.MOSS_BLOCK) && q < 0.12F) {
                    Terrain.set(level, pos, x, g + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                } else if ((floor.is(Blocks.GRASS_BLOCK) || floor.is(Blocks.MOSS_BLOCK) || floor.is(Blocks.PODZOL)) && q > 0.86F) {
                    Terrain.set(level, pos, x, g + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
