package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * An ice shelf, like the Ross or the Ronne on the coast of Antarctica: the floating edge of an ice sheet. The ice flows
 * slowly down off the land under its own weight, and where it reaches the coast it doesn't stop but carries on out over
 * the sea, lifting off the sea bed at the grounding line and floating as a vast, flat-topped slab, most of its thickness
 * hidden under the water. It is fed from behind and by the snow that falls on it, and wasted by the sea, which
 * melts it from below and breaks great blocks off its front: so it ends in a sheer cliff of ice, banded blue where old
 * layers of snow were squeezed into clear glacier ice, with the waves eating caves into its foot. Where the ice is
 * stretched as it spreads out it cracks into crevasses running back from the front, and the blocks that calve away
 * drift off as icebergs.
 *
 * @param cx     the grounding line: where the coast is, under the middle of the shelf
 * @param angle  the way it flows, out to sea
 * @param length how far its front stands out from the coast
 * @param width  how wide it is
 * @param top    the y of its top
 * @param under  how far under the water its underside lies at the coast (it thins toward the front)
 * @param ramp   how far back over the land it takes to come down to the ground
 */
public record IceShelf(int cx, int cz, int sea, float angle, int length, int width, int top, int under, int ramp, long seed) implements Landform {
    private static final int NONE = 100000;
    /** How far past its front the broken-off bergs drift. */
    private static final float DRIFT = 22.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int sea = c.seaLevel();
        int here = c.groundAt(c.x(), c.z());
        if (here > sea + 24 || here < sea - 40 || !c.fits(sea, 32, 40)) {
            return null;
        }
        // a look round first: there must be both dry land and open, deep sea in reach, and that shows which way the sea is
        boolean land = here >= sea, deep = here <= sea - 9;
        float sx = 0.0F, sz = 0.0F;
        for (int i = 0; i < 16; i++) {
            float a = (i % 8) * Mth.PI / 4.0F;
            int r = i < 8 ? 24 : 48;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * r), c.z() + Math.round(Mth.sin(a) * r));
            land |= g >= sea;
            deep |= g <= sea - 9;
            float w = Mth.clamp(sea - 1 - g, -10, 20);
            sx += w * Mth.cos(a);
            sz += w * Mth.sin(a);
        }
        if (!land || !deep || sx * sx + sz * sz < 1.0F) {
            return null;
        }
        float angle = (float) Mth.atan2(sz, sx);
        // out that way: the coast, and the open sea beyond it, which must come soon after
        int shore = NONE, open = NONE;
        for (int t = -60; t <= 84 && open == NONE; t += 6) {
            int g = at(c, angle, t, 0.0F);
            if (g >= sea - 1) shore = t;
            else if (shore != NONE && g <= sea - 9) open = t;
        }
        if (shore == NONE || open == NONE || open - shore > 40 || shore > 40 || at(c, angle, shore - 12, 0.0F) < sea - 1) {
            return null;
        }
        for (int t = shore + 2; t <= shore + 4 && at(c, angle, t, 0.0F) >= sea - 1; t += 2) shore = t;
        double[] s = Terrain.xz(shore, 0.0, c.x() + 0.5, c.z() + 0.5, angle);
        int x = Mth.floor(s[0]), z = Mth.floor(s[1]);
        int length = 40 + c.random().nextInt(41), width = 40 + c.random().nextInt(51);
        int top = sea - 1 + 8 + c.random().nextInt(9), under = 15 + c.random().nextInt(6), ramp = 14 + c.random().nextInt(10);
        long seed = c.random().nextLong();
        // narrower where land closes in on either side
        float side = 0.4F * width;
        if (at(c, angle, shore + length * 0.7F, side) >= sea - 2 || at(c, angle, shore + length * 0.7F, -side) >= sea - 2) width = Math.max(40, Math.round(width * 0.6F));
        // it all has to fit round the chunk it starts in
        length = Math.min(length, 86 - shore);
        while (new IceShelf(x, z, sea, angle, length, width, top, under, ramp, seed).reach(c.x(), c.z()) > 108) {
            if (length > 44) {
                length -= 4;
            } else if (width > 44) {
                width -= 6;
            } else {
                return null;
            }
        }
        // and its front must stand in deep water, deep enough for it to float off the sea bed if it can
        int front = at(c, angle, shore + length, 0.0F);
        while (length >= 46 && front > sea - 9) front = at(c, angle, shore + (length -= 6), 0.0F);
        if (front > sea - 9) {
            return null;
        }
        under = Mth.clamp(sea - 1 - front + 3, 13, under);
        return new IceShelf(x, z, sea, angle, length, width, top, under, ramp, seed);
    }

    /** The ground {@code u} along a way and {@code v} across it from the middle of the chunk. */
    private static int at(PlanContext c, float a, float u, float v) {
        double[] p = Terrain.xz(u, v, c.x() + 0.5, c.z() + 0.5, a);
        return c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
    }

    static Landform load(CompoundTag tag) {
        return new IceShelf(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getInt("top"), tag.getInt("under"), tag.getInt("ramp"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("top", top);
        tag.putInt("under", under);
        tag.putInt("ramp", ramp);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ICE_SHELF;
    }

    @Override
    public BoundingBox bounds() {
        float u0 = -ramp - 2.0F, u1 = length + DRIFT, v = width / 2.0F + 6.0F;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            double[] p = Terrain.xz(k < 2 ? u0 : u1, k % 2 == 0 ? -v : v, cx + 0.5, cz + 0.5, angle);
            minX = Math.min(minX, Mth.floor(p[0]));
            maxX = Math.max(maxX, Mth.floor(p[0]));
            minZ = Math.min(minZ, Mth.floor(p[1]));
            maxZ = Math.max(maxZ, Mth.floor(p[1]));
        }
        return new BoundingBox(minX - 1, sea - under - 6, minZ - 1, maxX + 1, top + 12, maxZ + 1);
    }

    private int reach(int x, int z) {
        BoundingBox b = bounds();
        return Math.max(Math.max(Math.abs(b.minX() - x), Math.abs(b.maxX() - x)), Math.max(Math.abs(b.minZ() - z), Math.abs(b.maxZ() - z)));
    }

    /** Where the ice front stands, {@code v} across from the middle: bowed out in the middle, and ragged. */
    private float front(float v) {
        float s = Mth.clamp(v / (width / 2.0F), -1.3F, 1.3F);
        return length - 0.16F * length * s * s + 6.0F * (Terrain.field(seed + 1, v, 0.0, 11.0) - 0.5F) + 2.0F * (Terrain.field(seed + 2, v, 0.0, 3.0) - 0.5F);
    }

    /** How far out its side edge lies {@code u} along it, on one side or the other: it spreads a little as it leaves the coast. */
    private float side(float u, float v) {
        return width / 2.0F * (0.9F + 0.1F * Mth.clamp(u / length, 0.0F, 1.0F)) + 4.0F * (Terrain.field(seed + 3, u, v < 0.0F ? 0.0 : 50.0, 10.0) - 0.5F);
    }

    private boolean inside(float u, float v) {
        return u >= -ramp && u <= front(v) && Math.abs(v) <= side(u, v);
    }

    private int crevasses() {
        return 2 + Mth.floor(Terrain.hash(seed, 1) * 3.99F);
    }

    /** The y of the floor of a crevasse at a point (NONE outside every crevasse). */
    private int crevasse(float u, float v, int surface) {
        int best = NONE;
        int n = crevasses();
        for (int k = 0; k < n; k++) {
            // spread out along the front, each in its own stretch of it
            float vk = ((k + 0.5F + 0.6F * (Terrain.hash(seed, 10 + k) - 0.5F)) / n * 2.0F - 1.0F) * width / 2.0F * 0.75F;
            float len = 10.0F + 15.0F * Terrain.hash(seed, 20 + k);
            float in = front(vk) - u;
            if (in < -2.0F || in > len) continue;
            float open = 1.0F - Math.max(0.0F, in) / len;
            float line = vk + 1.2F * Mth.sin(u * 0.16F + 6.0F * Terrain.hash(seed, 50 + k));
            float half = (0.6F + 0.8F * Terrain.hash(seed, 30 + k)) * (0.4F + 0.6F * open);
            if (Math.abs(v - line) >= half) continue;
            // deepest at the front, closing up as it runs in; it never reaches the water
            int deepest = sea + 2 + Mth.floor(Terrain.hash(seed, 40 + k) * 3.99F);
            best = Math.min(best, Math.round(Mth.lerp(Terrain.smooth(open), surface - 2, deepest)));
        }
        return best;
    }

    private int caves() {
        return 1 + (Terrain.hash(seed, 2) < 0.5F ? 1 : 0);
    }

    /** A sea cave eaten into the foot of the front at a point: {its lowest water, its roof}, or null outside every cave. */
    @Nullable
    private int[] cave(float u, float v) {
        for (int k = 0; k < caves(); k++) {
            float vc = (Terrain.hash(seed, 60 + k) * 1.4F - 0.7F) * width / 2.0F;
            float deep = 5.0F + 5.0F * Terrain.hash(seed, 61 + k), half = 2.0F + 1.5F * Terrain.hash(seed, 62 + k);
            float in = front(v) - u;
            if (in < -1.0F) continue;
            float r = Mth.square(Math.max(0.0F, in) / deep) + Mth.square((v - vc) / half);
            if (r >= 1.0F) continue;
            float f = Mth.sqrt(1.0F - r);
            int roof = 1 + Mth.floor(Terrain.hash(seed, 63 + k) * 2.99F);
            return new int[]{sea - 1 - Math.round(2.5F * f), sea - 1 + Math.round(roof * f)};
        }
        return null;
    }

    private int bergs() {
        return 4 + Mth.floor(Terrain.hash(seed, 3) * 5.99F);
    }

    /** The berg floating at a point: {its top, its underside}, or null where there's none. */
    @Nullable
    private int[] berg(float u, float v) {
        int[] best = null;
        int n = bergs();
        for (int k = 0; k < n; k++) {
            // strung out along the foot of the front, some drifted further off than others
            float vb = ((k + 0.5F + 0.7F * (Terrain.hash(seed, 70 + k) - 0.5F)) / n * 2.0F - 1.0F) * width / 2.0F * 0.85F;
            float r = 1.5F + 2.5F * Terrain.hash(seed, 80 + k);
            float ub = front(vb) + 2.0F + r + 6.0F * Terrain.hash(seed, 90 + k);
            float[] l = Terrain.uv(u, v, ub, vb, Terrain.hash(seed, 100 + k) * Mth.PI);
            float aspect = 0.6F + 0.4F * Terrain.hash(seed, 110 + k);
            // a broken-off block, squarish with its corners knocked off
            float q = (float) (Math.pow(Math.abs(l[0]) / r, 3.0) + Math.pow(Math.abs(l[1]) / (r * aspect), 3.0));
            if (q >= 1.0F) continue;
            int above = 1 + Mth.floor(Terrain.hash(seed, 120 + k) * Math.min(4.0F, r));
            float tilt = 0.25F * (Terrain.hash(seed, 130 + k) - 0.5F) * l[0];
            int t = sea - 1 + Math.max(0, Math.round(above * (0.75F + 0.25F * (1.0F - q)) + tilt));
            int b = sea - 1 - Math.round((2 * above + 1) * (0.5F + 0.5F * (1.0F - q)));
            if (best == null || t > best[0]) best = new int[]{t, b};
        }
        return best;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                if (inside(u, v)) {
                    shelf(level, pos, b, x, z, u, v);
                } else if (u > length * 0.5F) {
                    int[] berg = berg(u, v);
                    if (berg != null) berg(level, pos, b, x, z, berg);
                }
            }
        }
    }

    /** One column of the shelf itself. */
    private void shelf(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v) {
        int ground = Terrain.ground(level, pos, x, z);
        if (ground >= b.maxY() - 2) return;
        boolean afloat = ground < sea - 1;
        // level on top, with long gentle swells
        float h = top + 1.1F * (Terrain.field(seed + 4, x, z, 16.0) - 0.5F) * 2.0F;
        // coming down over the land behind, and tapering off at the sides where it lies on the land
        if (u < 0.0F) h = ground + (h - ground) * Terrain.smooth((u + ramp) / ramp);
        if (!afloat) h = ground + (h - ground) * Terrain.smooth((side(u, v) - Math.abs(v)) / 7.0F);
        int surface = Math.min(Math.round(h), b.maxY() - 4);
        if (surface <= ground) return;
        // floating, thinning toward the front, except near the coast where it rests on the sea bed
        float thick = under - 4.0F * Mth.clamp(u / length, 0.0F, 1.0F) + 2.0F * (Terrain.field(seed + 5, x, z, 9.0) - 0.5F);
        int base = Math.max(ground + 1, Math.max(b.minY() + 1, sea - 1 - Math.round(thick)));
        if (!afloat) Terrain.clearAbove(level, pos, x, ground, z, Math.min(b.maxY() - ground, surface - ground + 10));
        float edge = Math.min(front(v) - u, side(u, v) - Math.abs(v));
        int snow = 2 + (Terrain.field(seed + 6, x, z, 7.0) > 0.55F ? 1 : 0);
        if (afloat) {
            // weed growing up into the ice from the sea bed is cut short under it
            BlockState below = level.getBlockState(pos.set(x, base - 1, z));
            if (below.is(Blocks.TALL_SEAGRASS)) Terrain.set(level, pos, x, base - 1, z, Blocks.WATER.defaultBlockState());
            else if (below.is(Blocks.KELP_PLANT)) Terrain.set(level, pos, x, base - 1, z, Blocks.KELP.defaultBlockState());
        }
        for (int y = base; y <= surface; y++) Terrain.set(level, pos, x, y, z, ice(x, y, z, surface - y < snow && surface >= sea, edge));
        int crack = afloat && u > 0.0F ? crevasse(u, v, surface) : NONE;
        if (crack != NONE) {
            for (int y = Math.max(crack + 1, base + 1); y <= surface; y++) Terrain.set(level, pos, x, y, z, Blocks.AIR.defaultBlockState());
            if (crack + 1 > base) Terrain.set(level, pos, x, crack, z, Blocks.SNOW_BLOCK.defaultBlockState()); // drifted in from the top
            return;
        }
        int[] cave = afloat && ground < sea - 5 ? cave(u, v) : null;
        if (cave != null && cave[0] > base + 1 && cave[1] < surface - 3) {
            // waves breaking in, eating it out at the waterline
            for (int y = cave[0]; y <= cave[1]; y++) Terrain.set(level, pos, x, y, z, y <= sea - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        // snow drifts on top, drawn out by the wind off the land
        float[] w = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle + 0.3F);
        float drift = Terrain.field(seed + 7, w[0] * 0.3, w[1], 5.0) * 0.7F + Terrain.field(seed + 8, x, z, 3.0) * 0.3F;
        int layers = Mth.clamp(Math.round((drift - 0.48F) * 16.0F), 0, 6);
        if (layers > 0 && edge > 1.5F && level.getBlockState(pos.set(x, surface + 1, z)).isAir()) {
            Terrain.set(level, pos, x, surface + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
        }
    }

    /** The ice of the shelf: packed snow and ice, with bands of blue glacier ice, most of all in the face of the cliffs; firn and snow on top. */
    private BlockState ice(int x, int y, int z, boolean snow, float edge) {
        if (snow) return Blocks.SNOW_BLOCK.defaultBlockState();
        float n = Terrain.noise(seed + 9, x, y, z);
        int band = Math.floorMod(y - sea + Math.round(1.5F * Terrain.field(seed + 10, x, z, 18.0)), 5);
        if (band == 0 && n < 0.85F || band == 1 && n < 0.25F) return Blocks.BLUE_ICE.defaultBlockState();
        if (edge < 1.5F && n < 0.3F || n < 0.06F) return Blocks.BLUE_ICE.defaultBlockState();
        return Blocks.PACKED_ICE.defaultBlockState();
    }

    /** One column of a berg calved off the front. */
    private void berg(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int[] berg) {
        int ground = Terrain.ground(level, pos, x, z);
        if (ground >= sea - 2 || !level.getFluidState(pos.set(x, sea - 1, z)).is(Fluids.WATER)) return;
        int base = Math.max(Math.max(ground + 1, b.minY() + 1), berg[1]);
        for (int y = base; y <= berg[0]; y++) {
            float n = Terrain.noise(seed + 11, x, y, z);
            BlockState s = y == berg[0] && y >= sea + 1 ? Blocks.SNOW_BLOCK.defaultBlockState() : n < 0.3F ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            Terrain.set(level, pos, x, y, z, s);
        }
    }
}
