package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Mushroom rocks (pedestal rocks): boulders of hard rock balanced on slender stems, scattered over the desert.
 * Windblown sand never rises much above head height, so it's the bottom of each rock that gets
 * sandblasted, and the softer rock just above the ground is worn into a narrow neck, while the hard cap overhead
 * is left wide. Sand drifts up round their feet.
 *
 * @param rocks {x, z, base, height, cap radius x 10, stem radius x 10} for each rock
 */
public record MushroomRocks(int cx, int cz, int[] rocks, long seed) implements Landform {
    private static final int SPREAD = 26;
    private static final BlockState[] RED_STEM = {Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()};
    private static final BlockState[] PALE_STEM = {Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(24, 8) > 12 || !c.fits(ground, 8, 16)) {
            return null;
        }
        int n = 6 + c.random().nextInt(7);
        int[] rocks = new int[n * 6];
        int placed = 0;
        for (int attempt = 0; attempt < n * 3 && placed < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * SPREAD;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            float cap = 1.5F + c.random().nextFloat() * 2.0F;
            boolean clear = true;
            for (int k = 0; k < placed; k++) {
                float dd = Mth.sqrt(Mth.square(x - rocks[k * 6]) + Mth.square(z - rocks[k * 6 + 1]));
                if (dd < cap + rocks[k * 6 + 4] / 10.0F + 3.0F) clear = false;
            }
            if (!clear) continue;
            int base = c.groundAt(x, z);
            if (base < c.seaLevel() + 1) continue;
            rocks[placed * 6] = x;
            rocks[placed * 6 + 1] = z;
            rocks[placed * 6 + 2] = base;
            rocks[placed * 6 + 3] = Mth.clamp(3 + Math.round(cap * 1.2F) + c.random().nextInt(3), 3, 8);
            rocks[placed * 6 + 4] = Math.round(cap * 10.0F);
            rocks[placed * 6 + 5] = Math.round((0.7F + c.random().nextFloat() * 0.3F + cap * 0.15F) * 10.0F);
            placed++;
        }
        if (placed < 5) {
            return null;
        }
        return new MushroomRocks(c.x(), c.z(), java.util.Arrays.copyOf(rocks, placed * 6), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new MushroomRocks(tag.getInt("x"), tag.getInt("z"), tag.getIntArray("rocks"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.put("rocks", new IntArrayTag(rocks));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.MUSHROOM_ROCKS;
    }

    @Override
    public BoundingBox bounds() {
        int minX = cx, maxX = cx, minZ = cz, maxZ = cz, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < rocks.length; i += 6) {
            int r = Math.round(rocks[i + 4] / 10.0F) + 5;
            minX = Math.min(minX, rocks[i] - r);
            maxX = Math.max(maxX, rocks[i] + r);
            minZ = Math.min(minZ, rocks[i + 1] - r);
            maxZ = Math.max(maxZ, rocks[i + 1] + r);
            minY = Math.min(minY, rocks[i + 2]);
            maxY = Math.max(maxY, rocks[i + 2] + rocks[i + 3]);
        }
        return new BoundingBox(minX, minY - 6, minZ, maxX, maxY + 4, maxZ);
    }

    /** Where the middle of rock {@code i} is at {@code k} blocks above its foot: it leans a little, and the cap sits askew. */
    private float[] centre(int i, int k, int capBottom) {
        float lean = Terrain.hash(seed, i * 8 + 1) * Mth.TWO_PI, by = 0.8F * Terrain.hash(seed, i * 8 + 2);
        float f = Math.min(1.0F, k / (float) capBottom);
        float x = rocks[i * 6] + 0.5F + Mth.cos(lean) * by * f, z = rocks[i * 6 + 1] + 0.5F + Mth.sin(lean) * by * f;
        if (k >= capBottom) {
            float skew = Terrain.hash(seed, i * 8 + 3) * Mth.TWO_PI;
            x += Mth.cos(skew) * 0.5F;
            z += Mth.sin(skew) * 0.5F;
        }
        return new float[]{x, z};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floor = bounds().minY() + 1;
        float wind = Terrain.hash(seed, 0) * Mth.TWO_PI;
        for (int i = 0; i < rocks.length / 6; i++) {
            int rx = rocks[i * 6], rz = rocks[i * 6 + 1], base = rocks[i * 6 + 2], height = rocks[i * 6 + 3];
            float cap = rocks[i * 6 + 4] / 10.0F, stem = rocks[i * 6 + 5] / 10.0F;
            int capThick = height >= 6 ? 2 + (Terrain.hash(seed, i * 8 + 4) < 0.4F ? 1 : 0) : height >= 4 ? 2 : 1;
            int capBottom = height - capThick + 1;
            int reach = Mth.ceil(cap + 3.5F);
            for (int x = Math.max(area.minX(), rx - reach); x <= Math.min(area.maxX(), rx + reach); x++) {
                for (int z = Math.max(area.minZ(), rz - reach); z <= Math.min(area.maxZ(), rz + reach); z++) {
                    int ground = Terrain.ground(level, pos, x, z);
                    Holder<Biome> biome = level.getBiome(pos.set(x, ground, z));
                    boolean red = biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA);
                    BlockState[] bands = red ? RED_STEM : PALE_STEM;
                    BlockState capRock = red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
                    BlockState capTop = red ? (Terrain.hash(seed, i * 8 + 5) < 0.5F ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState())
                            : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    boolean any = false;
                    for (int h = Math.max(floor, Math.min(ground, base) - 1); h <= base + height; h++) {
                        int k = Math.max(0, h - base);
                        float[] m = centre(i, k, capBottom);
                        float d = Mth.sqrt(Mth.square(x + 0.5F - m[0]) + Mth.square(z + 0.5F - m[1]));
                        float r;
                        if (k >= capBottom) {
                            // the cap: wide, bevelled underneath and rounded on top
                            r = k == height ? cap - 0.7F : k == capBottom && capThick > 1 ? cap - 0.35F : cap;
                        } else if (k == 0) {
                            r = stem + 0.6F; // a little flare at the foot, below the reach of the sand
                        } else {
                            // narrowest just above the ground, where the sand blasts hardest, then flaring up into the cap
                            float f = k / (float) capBottom;
                            r = stem + (cap * 0.6F - stem) * (float) Math.pow(Math.max(0.0F, (f - 0.25F) / 0.75F), 2.0);
                        }
                        r += 0.3F * (Terrain.noise(seed, x, h, z) - 0.5F);
                        if (d > r) continue;
                        if (!any) {
                            Terrain.clearAbove(level, pos, x, Math.min(ground, h), z, base + height - Math.min(ground, h) + 3);
                            any = true;
                        }
                        BlockState s = k >= capBottom ? k == height ? capTop : capRock : bands[Math.floorMod(h + i, bands.length)];
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    if (any) continue;
                    // sand drifted round the foot, deepest on the downwind side
                    float dx = x + 0.5F - (rx + 0.5F), dz = z + 0.5F - (rz + 0.5F);
                    float d = Mth.sqrt(dx * dx + dz * dz);
                    float lee = d < 0.01F ? 0.0F : (dx * Mth.cos(wind) + dz * Mth.sin(wind)) / d;
                    float drift = stem + 1.6F + 1.4F * Math.max(0.0F, lee);
                    if (d > drift || Terrain.noise(seed, x, 3, z) > 0.75F || Math.abs(ground - base) > 2) continue;
                    pos.set(x, ground + 1, z);
                    if (!level.getBlockState(pos).isAir() && !Terrain.cover(level.getBlockState(pos))) continue;
                    Terrain.clearAbove(level, pos, x, ground, z, 3);
                    Terrain.set(level, pos, x, ground + 1, z, red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    if (Terrain.noise(seed, x, 4, z) > 0.94F) Terrain.set(level, pos, x, ground + 2, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
