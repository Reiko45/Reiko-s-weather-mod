package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Moeraki boulders: huge, almost perfectly round stones scattered along a sandy beach, half sunk in the sand. Each is a
 * concretion: millions of years ago, on the sea floor, calcite crept out through the mud round some small core (a
 * shell, a bone) and cemented it, growing outward like a pearl into a hard ball. Later the mud dried and shrank inside
 * them and cracked into a web of fissures (septaria), which filled with veins of yellow-brown calcite. Now the soft
 * mudstone cliffs that held them are being worn back by the sea, the boulders roll out onto the beach, and the waves
 * slowly wear them open: some show their veins, some are split into segments along the cracks, and a few have broken
 * clean in half, showing the hollow, crystal-lined heart.
 *
 * @param angle    the way out to sea
 * @param length   how far the beach runs along the coast
 * @param boulders {w up the beach from the waterline, v along the coast, radius x 10, kind, centre's height above
 *                 the beach x 10, split angle x 100} for each boulder; kind 0 is whole, 1 split into segments, 2 broken
 *                 in half
 */
public record MoerakiBoulders(int cx, int cz, int sea, float angle, int length, int[] boulders, long seed) implements Landform {
    private static final int F = 6, BACK = 12;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, MoerakiBoulders::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 2 || ground > c.seaLevel() + 3) {
            return null;
        }
        float seaward = c.seaward(16, 2);
        if (Float.isNaN(seaward)) {
            return null;
        }
        int length = 46 + c.random().nextInt(25);
        int n = 8 + c.random().nextInt(13);
        int[] boulders = new int[n * F];
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < n; attempt++) {
            float r = 1.5F + c.random().nextFloat() * 2.0F;
            float w = -3.0F + c.random().nextFloat() * 12.0F, v = (c.random().nextFloat() - 0.5F) * (length - 14.0F);
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                float dd = Mth.sqrt(Mth.square(w - boulders[k * F]) + Mth.square(v - boulders[k * F + 1]));
                if (dd < r + boulders[k * F + 2] / 10.0F + 1.5F) clear = false;
            }
            if (!clear) continue;
            // a couple broken clean in half (only up on the dry sand), more split along their cracks
            float pick = c.random().nextFloat();
            int kind = pick < 0.14F && w > 3.0F && r > 2.0F ? 2 : pick < 0.45F ? 1 : 0;
            boulders[placed * F] = Math.round(w);
            boulders[placed * F + 1] = Math.round(v);
            boulders[placed * F + 2] = Math.round(r * 10.0F);
            boulders[placed * F + 3] = kind;
            // sunk anywhere from a third to over half their depth in the sand
            boulders[placed * F + 4] = Math.round(r * (kind == 2 ? 0.25F : -0.2F + c.random().nextFloat() * 0.5F) * 10.0F);
            boulders[placed * F + 5] = Math.round(c.random().nextFloat() * Mth.PI * 100.0F);
            placed++;
        }
        if (placed < 8) {
            return null;
        }
        // there are nearly always a couple lying broken open
        int broken = 0;
        for (int k = 0; k < placed; k++) if (boulders[k * F + 3] == 2) broken++;
        for (int k = 0; k < placed && broken < 2; k++) {
            if (boulders[k * F + 3] == 2 || boulders[k * F] <= 3 || boulders[k * F + 2] <= 20) continue;
            boulders[k * F + 3] = 2;
            boulders[k * F + 4] = Math.round(boulders[k * F + 2] * 0.25F);
            broken++;
        }
        return new MoerakiBoulders(c.x(), c.z(), c.seaLevel(), seaward, length, java.util.Arrays.copyOf(boulders, placed * F), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new MoerakiBoulders(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"),
                tag.getIntArray("boulders"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("boulders", new IntArrayTag(boulders));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.MOERAKI_BOULDERS;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-(BACK + 11.0F), 15.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, sea - 16, minZ, maxX, sea + 22, maxZ);
    }

    /** How far the waterline sits out to sea of the middle, {@code v} along the coast. */
    private float shore(float v) {
        return 1.5F * Mth.sin(v * 0.05F + Terrain.hash(seed, 1) * 6.0F) + 0.8F * Mth.sin(v * 0.13F + Terrain.hash(seed, 2) * 6.0F);
    }

    /** The beach's height {@code w} blocks up from the waterline: a gentle slope of firm sand. */
    private float profile(float w) {
        if (w < 0.0F) return sea - 1 + Math.max(w, -8.0F) * 0.4F;
        return sea - 1 + 3.0F * Terrain.smooth(Math.min(w, BACK) / BACK);
    }

    /** Where boulder {@code i}'s centre is: {x, y, z}. */
    private double[] centre(int i) {
        float w = boulders[i * F], v = boulders[i * F + 1];
        double[] p = Terrain.xz(shore(v) - w, v, cx, cz, angle);
        return new double[]{p[0], Math.round(profile(w)) + 1.0 + boulders[i * F + 4] / 10.0, p[1]};
    }

    /** The block boulder {@code i} has at an offset from its centre: null outside it, air in a crack or its hollow. */
    @Nullable
    private BlockState boulder(int i, float dx, float dy, float dz, int x, int y, int z) {
        float r = boulders[i * F + 2] / 10.0F;
        int kind = boulders[i * F + 3];
        float split = boulders[i * F + 5] / 100.0F, sx = Mth.cos(split), sz = Mth.sin(split);
        float n = Terrain.noise(seed + i, x, y, z);
        if (kind == 2) {
            // broken in half: the halves lie a little apart, each open to show its hollow, crystal-lined heart
            float side = dx * sx + dz * sz >= 0.0F ? 1.0F : -1.0F, gap = 0.9F;
            float ax = dx - side * sx * gap, az = dz - side * sz * gap;
            if ((ax * sx + az * sz) * side < 0.0F) return null;
            float d = Mth.sqrt(ax * ax + dy * dy + az * az);
            if (d >= r) return null;
            if (d < r - 1.3F) return Blocks.AIR.defaultBlockState();
            if (d < r - 0.6F) return n < 0.6F ? Blocks.CALCITE.defaultBlockState() : Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            return rock(i, d, r, n);
        }
        float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= r) return null;
        // the septarian cracks: thin sheets radiating out from the middle
        for (int k = 0; k < 3; k++) {
            float a = split + k * 1.1F + Terrain.hash(seed, 100 + i * 4 + k) * 0.8F, tilt = (Terrain.hash(seed, 200 + i * 4 + k) - 0.5F) * 1.2F;
            float nx = Mth.cos(a) * Mth.cos(tilt), ny = Mth.sin(tilt), nz = Mth.sin(a) * Mth.cos(tilt);
            float off = Math.abs(dx * nx + dy * ny + dz * nz);
            if (kind == 1 && k < 2 && dy > 0.0F && off < 0.45F + 0.25F * dy / r) return Blocks.AIR.defaultBlockState(); // opened up along the cracks
            if (off < 0.42F && d > r * 0.25F) return n < 0.55F ? Blocks.CALCITE.defaultBlockState() : Blocks.YELLOW_TERRACOTTA.defaultBlockState();
        }
        return rock(i, d, r, n);
    }

    /** Grey-brown concretion: a crust of mudstone round a heart of harder, greyer stone. */
    private BlockState rock(int i, float d, float r, float n) {
        float brown = 0.25F + 0.3F * Terrain.hash(seed, 300 + i);
        if (d > r - 1.0F) {
            if (n < brown) return Blocks.PACKED_MUD.defaultBlockState();
            if (n < brown + 0.1F) return Blocks.MUD_BRICKS.defaultBlockState();
            return n < 0.82F ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        }
        return n < 0.6F ? Blocks.TUFF.defaultBlockState() : n < 0.85F ? Blocks.ANDESITE.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        int count = boulders.length / F;
        double[][] centres = new double[count][];
        for (int i = 0; i < count; i++) centres[i] = centre(i);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float v = l[1], w = shore(v) - l[0];
                if (Math.abs(v) > half || w < -12.0F || w > BACK + 8.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 6 || ground < b.minY() + 3) continue;
                boolean wet = !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || w < 0.0F;
                float target;
                if (w < -6.0F) {
                    target = Mth.lerp(Terrain.smooth((-w - 6.0F) / 6.0F), profile(-6.0F), ground); // shelving off to the sea bed
                } else if (w <= BACK) {
                    target = profile(w);
                } else {
                    target = Mth.lerp(Terrain.smooth((w - BACK) / 8.0F), profile(BACK), ground); // into the land behind
                }
                // the beach fades back into the ordinary shore at both ends
                float ends = Terrain.smooth((half - Math.abs(v)) / 10.0F);
                int top = Math.round(Mth.lerp(ends, ground, target));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(12, b.maxY() - Math.max(ground, top)));
                // a pond standing here goes; one just beyond the beach keeps a bank of sandstone between it and the sand
                int surface = Terrain.surface(level, pos, x, ground, z);
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    boolean water = h <= sea - 1 && wet;
                    Terrain.set(level, pos, x, h, z, water ? Blocks.WATER.defaultBlockState() : Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> {
                        float[] n = Terrain.uv(nx + 0.5, nz + 0.5, cx, cz, angle);
                        float nw = shore(n[1]) - n[0];
                        return Math.abs(n[1]) > half || nw < -12.0F || nw > BACK + 8.0F;
                    }) ? Blocks.SANDSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // firm sand, grey with mud near the water
                int bottom = Math.max(b.minY() + 1, Math.min(ground + 1, top - 2));
                for (int h = bottom; h <= top; h++) {
                    float g = Terrain.noise(seed + 50, x, h, z);
                    Terrain.set(level, pos, x, h, z, w < 1.0F && g < 0.15F ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState());
                }
                // the sand needs something firm under it
                pos.set(x, bottom - 1, z);
                BlockState under = level.getBlockState(pos);
                if (under.isAir() || !under.getFluidState().isEmpty()) level.setBlock(pos, Blocks.SANDSTONE.defaultBlockState(), Terrain.FLAGS);
                if (wet && top < sea - 2 && Terrain.noise(seed, x, 3, z) > 0.88F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SEAGRASS.defaultBlockState());
                }
                // the boulders, half sunk in the sand
                for (int i = 0; i < count; i++) {
                    float r = boulders[i * F + 2] / 10.0F;
                    float dx = (float) (x + 0.5 - centres[i][0]), dz = (float) (z + 0.5 - centres[i][2]);
                    if (dx * dx + dz * dz > Mth.square(r + 1.5F)) continue;
                    double cy = centres[i][1];
                    for (int h = Mth.floor(cy - r); h <= Mth.ceil(cy + r); h++) {
                        BlockState s = boulder(i, dx, (float) (h + 0.5 - cy), dz, x, h, z);
                        if (s == null) continue;
                        // a crack or hollow under the sand is full of it; one in the shallows, of water
                        if (s.isAir() && h <= top) s = Blocks.SAND.defaultBlockState();
                        else if (s.isAir() && h <= sea - 1) s = Blocks.WATER.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                }
            }
        }
    }
}
