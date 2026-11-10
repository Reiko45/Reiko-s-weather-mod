package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Paternoster lakes: a string of small lakes down the floor of a glacial valley, one below another like the beads
 * of a rosary (they're named after the paternoster beads). The glacier that carved the valley didn't wear its floor
 * down evenly: it plucked deep basins out of the softer, more shattered rock and rode over the harder bands, leaving
 * them standing across the valley as rock steps (riegels). Now the ice is gone, each basin holds a lake, milky
 * turquoise with rock flour, dammed behind the step below it, and down the middle of each step the rock is worn
 * smooth and pale where the meltwater used to pour over it.
 *
 * @param angle the way the valley falls
 * @param lakes {u along the valley, half-length, half-width, water y, depth, riegel height} for each lake, from the top
 */
public record PaternosterLakes(int cx, int cz, float angle, int[] lakes, long seed) implements Landform {
    private static final int STRIDE = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 12 || !c.fits(ground, 20, 40)) {
            return null;
        }
        float[] down = c.downhill(36);
        if (down[1] < 4 || down[1] > 36) {
            return null; // a valley floor falling steadily, not a flat or a cliff
        }
        float angle = down[0];
        int n = 3 + c.random().nextInt(3);
        int[] a = new int[n], b = new int[n];
        float[] u = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = 8 + c.random().nextInt(4);
            b[i] = 5 + c.random().nextInt(3);
            // spaced so that no lake's banks ever reach the next one's water
            if (i > 0) u[i] = u[i - 1] + 1.8F * (a[i - 1] + a[i]) + 3.0F + c.random().nextInt(3);
        }
        while (n > 3 && (u[n - 1] - u[0]) / 2.0F + 2.3F * Math.max(a[0], a[n - 1]) > 98.0F) n--;
        float shift = (u[0] + u[n - 1]) / 2.0F;
        int[] lakes = new int[n * STRIDE];
        int prev = Integer.MAX_VALUE, k = 0;
        for (int i = 0; i < n; i++) {
            float ui = u[i] - shift;
            double[] p = Terrain.xz(ui, 0.0, c.x() + 0.5, c.z() + 0.5, angle), q = Terrain.xz(ui + a[i] * 1.4F, 0.0, c.x() + 0.5, c.z() + 0.5, angle);
            int g = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1])), below = c.groundAt(Mth.floor(q[0]), Mth.floor(q[1]));
            // each a step below the last, at about the valley floor, and never needing much of a dam below it
            int water = Math.min(Math.min(g, prev - 3), below + 6);
            if (water < c.seaLevel() + 4 || g - water > 14) break;
            int o = k * STRIDE;
            lakes[o] = Math.round(ui);
            lakes[o + 1] = a[i];
            lakes[o + 2] = b[i];
            lakes[o + 3] = water;
            lakes[o + 4] = 3 + c.random().nextInt(4);
            lakes[o + 5] = 2 + c.random().nextInt(2);
            prev = water;
            k++;
        }
        if (k < 3) {
            return null;
        }
        return new PaternosterLakes(c.x(), c.z(), angle, Arrays.copyOf(lakes, k * STRIDE), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new PaternosterLakes(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getIntArray("lakes"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.put("lakes", new IntArrayTag(lakes));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PATERNOSTER_LAKES;
    }

    private int count() {
        return lakes.length / STRIDE;
    }

    /** How far up and down the valley, and across it, the lakes and their banks reach. */
    private float[] extent() {
        int last = (count() - 1) * STRIDE, side = 0;
        for (int o = 0; o < lakes.length; o += STRIDE) side = Math.max(side, lakes[o + 2]);
        return new float[]{lakes[0] - 2.3F * lakes[1], lakes[last] + 2.3F * lakes[last + 1], side * 1.9F + 2.0F};
    }

    @Override
    public BoundingBox bounds() {
        float[] e = extent();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (float u : new float[]{e[0], e[1]}) {
            for (float v : new float[]{-e[2], e[2]}) {
                double[] p = Terrain.xz(u, v, cx + 0.5, cz + 0.5, angle);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int o = 0; o < lakes.length; o += STRIDE) {
            low = Math.min(low, lakes[o + 3] - lakes[o + 4] - 4);
            high = Math.max(high, lakes[o + 3]);
        }
        return new BoundingBox(Mth.floor(minX) - 1, low, Mth.floor(minZ) - 1, Mth.floor(maxX) + 1, high + 40, Mth.floor(maxZ) + 1);
    }

    /** How far out from the middle of lake {@code k} a point is (below 1 is the water), given where it is along and across the valley. */
    private float dist(int k, float u, float v, double x, double z) {
        int o = k * STRIDE;
        return Mth.sqrt(Mth.square((u - lakes[o]) / lakes[o + 1]) + Mth.square(v / lakes[o + 2])) / (0.92F + 0.16F * Terrain.field(seed + k, x, z, 6.0));
    }

    /** How squarely a point lies on the downstream side of lake {@code k}, where the riegel holds it (0-1). */
    private float out(int k, float u, float v) {
        int o = k * STRIDE;
        float du = (u - lakes[o]) / lakes[o + 1], dv = v / lakes[o + 2];
        if (du <= 0.0F) return 0.0F;
        return Terrain.smooth((1.1F - Math.abs((float) Mth.atan2(dv, du))) / 0.5F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[] e = extent();
        int n = count();
        boolean[] wet = new boolean[n];
        BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                float u = l[0], v = l[1];
                if (u < e[0] || u > e[1] || Math.abs(v) > e[2]) continue;
                // which lake's water this is in (if any), and the highest bank of any lake it's on
                int lake = -1, hold = Integer.MIN_VALUE, bank = Integer.MIN_VALUE, by = -1;
                // and how far down and up the water of any lake it's in or beside reaches
                int deep = Integer.MAX_VALUE, brim = Integer.MIN_VALUE;
                float ld = 0.0F, bt = 0.0F, bo = 0.0F;
                int ground = Integer.MIN_VALUE;
                for (int k = 0; k < n; k++) {
                    float d = dist(k, u, v, x + 0.5, z + 0.5);
                    if (d < 1.0F) {
                        deep = Math.min(deep, lakes[k * STRIDE + 3] - lakes[k * STRIDE + 4] - 1);
                        brim = Math.max(brim, lakes[k * STRIDE + 3]);
                        if (lake >= 0) hold = Math.max(hold, lakes[k * STRIDE + 3] + 1); // two lakes meeting: kept apart by rock
                        else lake = k;
                        ld = lake == k ? d : ld;
                        continue;
                    }
                    float out = out(k, u, v), reach = 0.7F + 0.4F * out;
                    if (d >= 1.0F + reach) continue;
                    if (ground == Integer.MIN_VALUE) ground = Terrain.ground(level, pos, x, z);
                    float t = (d - 1.0F) / reach;
                    if (t < 0.55F) {
                        deep = Math.min(deep, lakes[k * STRIDE + 3] - lakes[k * STRIDE + 4] - 1);
                        brim = Math.max(brim, lakes[k * STRIDE + 3]);
                    }
                    int top = bankTop(k, t, out, v, ground);
                    if (top > bank) {
                        bank = top;
                        by = k;
                        bt = t;
                        bo = out;
                    }
                }
                if (lake < 0 && bank == Integer.MIN_VALUE) continue;
                if (ground == Integer.MIN_VALUE) ground = Terrain.ground(level, pos, x, z);
                // in and beside the lakes the rock is sound all the way down to the deepest water: no cave or pocket of
                // loose water in it to let the lake drain away (above the ground, it's all filled or dammed anyway)
                for (int h = Math.max(b.minY(), deep); h <= Math.min(brim, ground); h++) {
                    BlockState s = level.getBlockState(pos.set(x, h, z));
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), Terrain.FLAGS);
                }
                if (ground > b.maxY() - 4) continue; // (left standing, it holds the water in all the same)
                int surf = Terrain.surface(level, pos, x, ground, z);
                if (lake >= 0 && bank == Integer.MIN_VALUE && hold == Integer.MIN_VALUE) {
                    water(level, pos, b, x, z, lake, ld, ground, surf);
                    wet[lake] = true;
                    continue;
                }
                if (lake >= 0) {
                    hold = Math.max(hold, lakes[lake * STRIDE + 3] + 1);
                    bank = Math.max(bank, hold);
                }
                // never cut a bank down under water standing on it (dam the water off instead), nor beside a pond just
                // outside the lakes, which would pour out through the gap
                if (surf > ground) bank = Math.max(bank, surf + 1);
                for (int h = ground; h > bank; h--) {
                    if (Terrain.pondBeside(level, beside, x, h, z, this::outside)) {
                        bank = h;
                        break;
                    }
                }
                shore(level, pos, b, x, z, by, bt, bo, v, ground, bank);
            }
        }
        // rock flour in the water turns it a milky turquoise
        for (int k = 0; k < n; k++) {
            if (!wet[k]) continue;
            int lake = k, o = k * STRIDE;
            Terrain.setBiome(level, area, lakes[o + 3] - lakes[o + 4] - 2, lakes[o + 3] + 2, (x, z) -> {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
                return dist(lake, l[0], l[1], x + 0.5, z + 0.5) < 1.0F;
            }, Terrain.GLACIAL_LAKE_BIOME);
        }
    }

    /**
     * The top of lake {@code k}'s bank, {@code t} of the way out across it: never under a block over the water near the
     * water's edge, and on the downstream side raised into the riegel, whose far face falls away down the valley.
     */
    private int bankTop(int k, float t, float out, float v, int ground) {
        int o = k * STRIDE, water = lakes[o + 3], crest = water + 1 + lakes[o + 5];
        float base = Mth.lerp(Terrain.smooth(t), water + 1, ground);
        float riegel = t < 0.3F ? Mth.lerp(Terrain.smooth(t / 0.3F), water + 1, crest) : Mth.lerp(Terrain.smooth((t - 0.3F) / 0.7F), crest, ground);
        float top = Mth.lerp(out, base, Math.max(base, riegel));
        if (groove(t, out, v)) top -= 1.0F;
        int h = Math.round(top);
        return t < 0.55F ? Math.max(h, water + 1) : h;
    }

    /** Whether a column lies outside every lake and its banks, where this landform never changes anything. */
    private boolean outside(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx + 0.5, cz + 0.5, angle);
        float[] e = extent();
        if (l[0] < e[0] || l[0] > e[1] || Math.abs(l[1]) > e[2]) return true;
        for (int k = 0; k < count(); k++) {
            if (dist(k, l[0], l[1], x + 0.5, z + 0.5) < 1.7F + 0.4F * out(k, l[0], l[1])) return false;
        }
        return true;
    }

    /** The shallow, water-worn groove down the middle of the riegel (dying out before it reaches the ground below). */
    private static boolean groove(float t, float out, float v) {
        return out > 0.6F && Math.abs(v) < 1.5F && t > 0.25F && t < 0.85F;
    }

    private void water(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int k, float d, int ground, int surf) {
        int o = k * STRIDE, water = lakes[o + 3];
        int bed = water - 1 - Math.round(lakes[o + 4] * (float) Math.pow(Math.max(0.0F, 1.0F - d * d), 0.6));
        int surface = Math.min(b.maxY(), Math.max(ground, surf));
        Terrain.clearAbove(level, pos, x, surface, z, Math.min(12, b.maxY() - surface));
        for (int h = water + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        float n = Terrain.noise(seed, x, water, z);
        for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 2)); h <= bed; h++) {
            // pale silt and gravel on the bed, rock flour from under the ice
            BlockState s = h < bed ? Blocks.STONE.defaultBlockState()
                    : n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.7F ? Blocks.CLAY.defaultBlockState() : n < 0.85F ? Blocks.CALCITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            Terrain.set(level, pos, x, h, z, s);
        }
        for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
    }

    /** A column of bank, beach or riegel, shaped to {@code top}. */
    private void shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, int k, float t, float out, float v, int ground, int top) {
        if (top == ground || top > b.maxY() - 6) return;
        pos.set(x, ground, z);
        BlockState was = level.getBlockState(pos);
        int high = Math.max(ground, top);
        Terrain.clearAbove(level, pos, x, high, z, Math.min(12, b.maxY() - high));
        for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        float n = Terrain.noise(seed, x, 1, z);
        int water = k >= 0 ? lakes[k * STRIDE + 3] : top - 1;
        boolean riegel = k >= 0 && out > 0.4F, polished = k >= 0 && groove(t, out, v);
        boolean beach = k >= 0 && !riegel && t < 0.2F && top <= water + 2;
        boolean soil = !riegel && !beach && (was.is(BlockTags.DIRT) || was.is(Blocks.SNOW_BLOCK));
        for (int h = Math.max(b.minY(), Math.min(ground + 1, top - 3)); h <= top; h++) {
            BlockState s;
            float m = Terrain.noise(seed + 1, x, h, z);
            if (h < top) {
                s = soil && h == top - 1 ? Blocks.DIRT.defaultBlockState() : m < 0.7F ? Blocks.STONE.defaultBlockState() : m < 0.85F ? Blocks.ANDESITE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                if (s.is(Blocks.GRAVEL) && h == Math.min(ground + 1, top - 3)) s = Blocks.STONE.defaultBlockState(); // (never loose at the bottom)
            } else if (polished) {
                // the "cascade": smooth, pale rock where the meltwater poured over the step
                s = m < 0.45F ? Blocks.SMOOTH_STONE.defaultBlockState() : m < 0.8F ? Blocks.CALCITE.defaultBlockState() : Blocks.DIORITE.defaultBlockState();
            } else if (riegel) {
                // bare rock, ground smooth by the ice
                s = m < 0.45F ? Blocks.STONE.defaultBlockState() : m < 0.75F ? Blocks.ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
            } else if (beach) {
                s = n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.CLAY.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            } else if (soil) {
                s = was;
            } else {
                s = n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.8F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        // the odd boulder the ice left behind on the banks
        if (!riegel && !beach && n > 0.985F) {
            Terrain.set(level, pos, x, top + 1, z, (n > 0.993F ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
        }
    }
}
