package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
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
 * Stromatolites: knobbly, domed pillars of rock standing in the warm shallows of a bay so salty that hardly anything
 * else can live in it. They're built by mats of microbes (cyanobacteria) on their tops: sticky with slime, the mat
 * traps the fine sand the tide brings in, and grows up through it towards the light, cementing a thin layer of
 * lime behind it. Layer on layer, a few centimetres a century, the columns rise. They're among the oldest signs of
 * life on Earth: for billions of years, before anything grazed on them, reefs of them were everywhere, and the oxygen
 * their mats gave off is what we breathe today.
 *
 * @param angle   the way out to sea
 * @param columns {x, z, radius x 10, top y} for each stromatolite
 */
public record Stromatolites(int cx, int cz, int sea, float angle, int length, int[] columns, long seed) implements Landform {
    private static final int F = 4;

    @Nullable
    static Landform plan(PlanContext c) {
        return c.search(12, 12, Stromatolites::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        // in shallow water, with the shore close by
        if (ground < c.seaLevel() - 5 || ground > c.seaLevel() - 2) {
            return null;
        }
        float seaward = c.seaward(14, 1);
        if (Float.isNaN(seaward)) {
            return null;
        }
        int length = 50 + c.random().nextInt(31);
        int sea = c.seaLevel();
        int n = 15 + c.random().nextInt(26);
        int[] columns = new int[n * F];
        int placed = 0;
        for (int attempt = 0; attempt < 120 && placed < n; attempt++) {
            float u = -6.0F + c.random().nextFloat() * 20.0F, v = (c.random().nextFloat() - 0.5F) * (length - 18.0F);
            float r = 1.0F + c.random().nextFloat() * 2.0F;
            double[] p = Terrain.xz(u, v, c.x(), c.z(), seaward);
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
            // they grow in clusters, some touching, but none quite on top of another
            boolean clear = true;
            for (int k = 0; k < placed && clear; k++) {
                float dd = Mth.sqrt(Mth.square(x - columns[k * F]) + Mth.square(z - columns[k * F + 1]));
                if (dd < (r + columns[k * F + 2] / 10.0F) * 0.8F) clear = false;
            }
            if (!clear) continue;
            // up to four blocks tall, and always with one to three blocks of water over the top
            int floor = floor(sea, u);
            int top = Mth.clamp(sea - 2 - c.random().nextInt(3), floor + 1, Math.min(floor + 4, sea - 2));
            columns[placed * F] = x;
            columns[placed * F + 1] = z;
            columns[placed * F + 2] = Math.round(r * 10.0F);
            columns[placed * F + 3] = top;
            placed++;
        }
        if (placed < 15) {
            return null;
        }
        return new Stromatolites(c.x(), c.z(), sea, seaward, length, java.util.Arrays.copyOf(columns, placed * F), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Stromatolites(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("columns"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("columns", new IntArrayTag(columns));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.STROMATOLITES;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F + 1.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-25.0F, 27.0F}) {
            for (float v : new float[]{-half, half}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, sea - 18, minZ, maxX, sea + 16, maxZ);
    }

    /** The shallow, sandy floor of the bay {@code u} blocks out to sea of the middle, shelving gently away from the beach. */
    private static int floor(int sea, float u) {
        return sea - 2 - Math.round(4.0F * Terrain.smooth((u + 10.0F) / 28.0F));
    }

    /** The bay's floor, and the beach behind it, {@code u} blocks out to sea. */
    private float profile(float u) {
        if (u >= -10.0F) return floor(sea, u);
        return Mth.lerp(Terrain.smooth((-10.0F - u) / 6.0F), sea - 2, sea + 1);
    }

    /** The top of the tallest stromatolite over a column, and which it is: {top, index}, or null. */
    @Nullable
    private int[] stromatolite(int x, int z) {
        int best = Integer.MIN_VALUE, which = -1;
        for (int i = 0; i < columns.length / F; i++) {
            float r = columns[i * F + 2] / 10.0F;
            float d = Mth.sqrt(Mth.square(x + 0.5F - columns[i * F] - 0.5F) + Mth.square(z + 0.5F - columns[i * F + 1] - 0.5F));
            float wobble = r * (0.85F + 0.3F * Terrain.field(seed + i, x, z, 2.0));
            if (d >= wobble) continue;
            // steep-sided, with a rounded, lumpy cap
            float t = d / wobble;
            float shape = t < 0.5F ? 1.0F : Mth.sqrt(1.0F - Mth.square((t - 0.5F) / 0.5F));
            int base = floor(sea, Terrain.uv(columns[i * F] + 0.5, columns[i * F + 1] + 0.5, cx, cz, angle)[0]);
            float knob = (Terrain.field(seed + 50 + i, x * 1.7, z * 1.7, 1.6) - 0.5F) * 1.4F;
            int top = Math.min(sea - 2, Math.round(base + (columns[i * F + 3] - base) * shape + knob));
            if (top > best) {
                best = top;
                which = i;
            }
        }
        return which < 0 ? null : new int[]{best, which};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (Math.abs(v) > half || u < -24.0F || u > 26.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 6 || ground < b.minY() + 4) continue;
                boolean wet = !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || u > -16.0F;
                float target = profile(Mth.clamp(u, -16.0F, 18.0F));
                // fading into the land behind, the sea bed beyond and the shore at either end
                float fade = Terrain.smooth((u + 24.0F) / 8.0F) * Terrain.smooth((26.0F - u) / 8.0F) * Terrain.smooth((half - Math.abs(v)) / 10.0F);
                // land standing well above the beach is left as a bluff behind the bay, not quarried away
                if (ground > target + 5) fade *= Mth.clamp(1.0F - (ground - target - 5) / 6.0F, 0.0F, 1.0F);
                int top = Math.round(Mth.lerp(fade, ground, target));
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, Math.min(10, b.maxY() - Math.max(ground, top)));
                // a pond standing here goes; one just beyond the bay keeps a bank of sandstone between it and the sand
                int surface = Terrain.surface(level, pos, x, ground, z);
                for (int h = top + 1; h <= Math.max(Math.max(ground, surface), sea - 1); h++) {
                    boolean water = h <= sea - 1 && wet;
                    Terrain.set(level, pos, x, h, z, water ? Blocks.WATER.defaultBlockState() : Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> {
                        float[] n = Terrain.uv(nx + 0.5, nz + 0.5, cx, cz, angle);
                        return Math.abs(n[1]) > half || n[0] < -24.0F || n[0] > 26.0F;
                    }) ? Blocks.SANDSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // fine sand on the floor of the bay, and a beach of tiny white shells (the only creature the salt suits)
                float n = Terrain.noise(seed, x, top, z);
                BlockState sand = top >= sea - 1 || n < 0.25F ? ModBlocks.SHELL_SAND.get().defaultBlockState() : Blocks.SAND.defaultBlockState();
                int bottom = Math.max(b.minY() + 1, Math.min(ground + 1, top - 1));
                for (int h = bottom; h <= top; h++) Terrain.set(level, pos, x, h, z, sand);
                pos.set(x, bottom - 1, z);
                BlockState under = level.getBlockState(pos);
                if (under.isAir() || !under.getFluidState().isEmpty()) level.setBlock(pos, Blocks.SANDSTONE.defaultBlockState(), Terrain.FLAGS);
                int[] s = stromatolite(x, z);
                if (s == null || s[0] <= top || !wet) continue;
                // layer on layer of trapped sand and lime, with the living mat on top
                int i = s[1], offset = Math.round(Terrain.hash(seed, 100 + i) * 3.0F);
                for (int h = top + 1; h <= s[0]; h++) {
                    BlockState layer;
                    if (h == s[0]) {
                        layer = Terrain.noise(seed + 2, x, h, z) < 0.7F ? Blocks.GREEN_TERRACOTTA.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
                    } else {
                        int band = Math.floorMod(h + offset + (Terrain.noise(seed + 3, x, h, z) < 0.15F ? 1 : 0), 3);
                        layer = band == 0 ? Blocks.CALCITE.defaultBlockState() : band == 1 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, layer);
                }
            }
        }
    }
}
