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
 * A mesa and its outliers: a flat-topped table of layered rock with sheer cliffs all round, the last of a plateau
 * that rivers and rain have cut away. A hard caprock keeps the top flat while the softer beds below crumble from
 * under it, so the cliffs stay sheer as they retreat, standing on aprons of their own fallen rubble. Nearby stand
 * the pieces already cut off from it: smaller buttes, and a lone spire that's nearly gone.
 *
 * @param base  the level of the plain
 * @param parts {x, z, radius x 10, top, kind (0 mesa, 1 butte, 2 spire)} for the mesa and each outlier
 */
public record Mesa(int cx, int cz, int base, int[] parts, long seed) implements Landform {
    /** The beds of the badlands, one block each, the same colours at the same heights everywhere. */
    private static final BlockState[] BANDS = {Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
            Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState()};
    /** Which beds are harder, standing out as ledges, and which softer, cut back a little. */
    private static final int[] LEDGE = {0, 0, 1, 1, 0, 0, -1, 0, 0, -1, 1, 0, 1, 0, 0, -1, 0, 0, 1, 0, -1, 0};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2) {
            return null;
        }
        int min = ground, max = ground;
        for (int i = 0; i < 16; i++) {
            float a = i * Mth.TWO_PI / 8.0F + (i >= 8 ? 0.4F : 0.0F);
            int r = i < 8 ? 24 : 48;
            int g = c.groundAt(c.x() + Math.round(Mth.cos(a) * r), c.z() + Math.round(Mth.sin(a) * r));
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        if (max - min > 24) {
            return null;
        }
        // the caprock stands well clear of all the land round about
        int top = Math.max(max + 6, min + 18 + c.random().nextInt(12));
        if (!c.fits(min, 6, top - min + 8)) {
            return null;
        }
        int radius = 17 + c.random().nextInt(8);
        int buttes = 2 + c.random().nextInt(2);
        int[] parts = new int[(buttes + 2) * 5];
        parts[0] = c.x();
        parts[1] = c.z();
        parts[2] = radius * 10;
        parts[3] = top;
        parts[4] = 0;
        float start = c.random().nextFloat() * Mth.TWO_PI;
        float spread = Mth.PI * (0.5F + 0.4F * c.random().nextFloat());
        for (int i = 0; i <= buttes; i++) {
            boolean spire = i == buttes;
            // the buttes off one side of the mesa, and the spire between two of them
            float a = start + (spire ? 0.5F : i) * spread / Math.max(1, buttes - 1) + (c.random().nextFloat() - 0.5F) * 0.3F;
            float r = spire ? 1.6F + c.random().nextFloat() * 0.8F : 5.0F + c.random().nextFloat() * 4.0F;
            float d = radius * 1.15F + (spire ? 8.0F : 10.0F + r) + c.random().nextFloat() * 6.0F;
            int o = (i + 1) * 5;
            parts[o] = c.x() + Math.round(Mth.cos(a) * d);
            parts[o + 1] = c.z() + Math.round(Mth.sin(a) * d);
            parts[o + 2] = Math.round(r * 10.0F);
            // the outliers are being worn down: some have lost their caprock and a bed or two
            parts[o + 3] = spire ? top - 2 - c.random().nextInt(6) : top - c.random().nextInt(4);
            parts[o + 4] = spire ? 2 : 1;
        }
        return new Mesa(c.x(), c.z(), min, parts, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Mesa(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getIntArray("parts"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.put("parts", new IntArrayTag(parts));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.MESA;
    }

    @Override
    public BoundingBox bounds() {
        int minX = cx, maxX = cx, minZ = cz, maxZ = cz, top = base;
        for (int i = 0; i < parts.length; i += 5) {
            int r = Math.round(parts[i + 2] / 10.0F * 1.3F + talusLength(i / 5)) + 3;
            minX = Math.min(minX, parts[i] - r);
            maxX = Math.max(maxX, parts[i] + r);
            minZ = Math.min(minZ, parts[i + 1] - r);
            maxZ = Math.max(maxZ, parts[i + 1] + r);
            top = Math.max(top, parts[i + 3]);
        }
        return new BoundingBox(minX, base - 4, minZ, maxX, top + 3, maxZ);
    }

    private float talusHeight(int p) {
        return (parts[p * 5 + 3] - base) * (parts[p * 5 + 4] == 2 ? 0.22F : 0.36F);
    }

    private float talusLength(int p) {
        return talusHeight(p) * 1.35F + 1.0F;
    }

    /** How far the foot of the cliff stands from the middle of part {@code p}, in a direction. */
    private float edge(int p, float theta) {
        float r = parts[p * 5 + 2] / 10.0F;
        int kind = parts[p * 5 + 4];
        float amp = kind == 0 ? 1.0F : kind == 1 ? 0.6F : 0.25F;
        long s = seed + p * 31L;
        float e = r * (1.0F + amp * (0.14F * Mth.sin(3.0F * theta + Terrain.hash(s, 1) * 6.0F) + 0.08F * Mth.sin(5.0F * theta + Terrain.hash(s, 2) * 6.0F)
                + 0.05F * Mth.sin(8.0F * theta + Terrain.hash(s, 3) * 6.0F)));
        if (kind == 0) {
            // re-entrant gullies cut back into the mesa's flanks
            int notches = 3 + Math.round(Terrain.hash(s, 4) * 2.0F);
            for (int k = 0; k < notches; k++) {
                float at = (k + Terrain.hash(s, 10 + k) * 0.7F) * Mth.TWO_PI / notches;
                float d = Math.abs(Mth.wrapDegrees((theta - at) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD * r;
                float width = 2.0F + Terrain.hash(s, 20 + k) * 2.5F;
                if (d < width) e -= (3.0F + Terrain.hash(s, 30 + k) * 5.0F) * Terrain.smooth(1.0F - d / width);
            }
        }
        return e;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = parts.length / 5;
        int offset = Math.round(Terrain.hash(seed, 1) * BANDS.length);
        float[] edges = new float[count], dists = new float[count];
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                // how far outside each part's cliff foot this column is, and the highest talus over it
                float talus = Float.NEGATIVE_INFINITY;
                int top = Integer.MIN_VALUE;
                for (int p = 0; p < count; p++) {
                    float dx = x + 0.5F - parts[p * 5], dz = z + 0.5F - parts[p * 5 + 1];
                    dists[p] = Mth.sqrt(dx * dx + dz * dz);
                    edges[p] = dists[p] > parts[p * 5 + 2] / 10.0F * 1.3F + talusLength(p) + 1.0F ? -1.0F : edge(p, (float) Mth.atan2(dz, dx));
                    if (edges[p] < 0.0F) continue;
                    float out = dists[p] - edges[p];
                    if (out < 1.5F) top = Math.max(top, parts[p * 5 + 3]);
                    float len = talusLength(p);
                    if (out < len) talus = Math.max(talus, base + talusHeight(p) * (float) Math.pow(Math.max(0.0F, 1.0F - Math.max(0.0F, out) / len), 1.3F));
                }
                if (top == Integer.MIN_VALUE && talus == Float.NEGATIVE_INFINITY) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > b.maxY() - 2) continue;
                int tal = talus == Float.NEGATIVE_INFINITY ? Integer.MIN_VALUE : Math.round(talus + 0.6F * (Terrain.field(seed, x, z, 4.0) - 0.5F));
                int high = Math.max(top, tal);
                if (high <= ground && top == Integer.MIN_VALUE) continue;
                Holder<Biome> biome = level.getBiome(pos.set(x, ground, z));
                boolean desert = !biome.is(BiomeTags.IS_BADLANDS) && !biome.is(BiomeTags.IS_SAVANNA);
                boolean grassy = biome.is(BiomeTags.IS_SAVANNA);
                Terrain.clearAbove(level, pos, x, Math.max(ground, high), z, Math.min(8, b.maxY() - Math.max(ground, high)));
                int capTop = Integer.MIN_VALUE;
                for (int h = Math.max(b.minY(), Math.min(ground, base) - 1); h <= high; h++) {
                    int band = Math.floorMod(h + offset, BANDS.length);
                    // the cliffs: each bed's foot a little further out or further in than the next
                    boolean cliff = false;
                    for (int p = 0; p < count; p++) {
                        if (edges[p] < 0.0F || h > parts[p * 5 + 3]) continue;
                        float jag = 0.5F * (Terrain.noise(seed + p, x, h, z) - 0.5F);
                        if (dists[p] <= edges[p] + LEDGE[band] * 0.6F + jag) {
                            cliff = true;
                            if (h == parts[p * 5 + 3]) capTop = h;
                        }
                    }
                    if (cliff) {
                        Terrain.set(level, pos, x, h, z, BANDS[band]);
                    } else if (h <= tal) {
                        // the talus: rubble fallen from the cliffs, over the beds that carry on under it
                        BlockState s = BANDS[band];
                        if (h == tal) {
                            float n = Terrain.noise(seed, x, h, z);
                            s = n < 0.5F ? desert ? Blocks.SAND.defaultBlockState() : Blocks.RED_SAND.defaultBlockState()
                                    : n < 0.7F ? Blocks.TERRACOTTA.defaultBlockState() : n < 0.85F ? BANDS[Math.floorMod(h + 3 + offset, BANDS.length)]
                                    : grassy ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                        }
                        Terrain.set(level, pos, x, h, z, s);
                    } else if (h > ground) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                }
                if (capTop == Integer.MIN_VALUE) continue;
                // the flat top: a hard caprock under a skin of sand, or of grass on the savanna's plateaus
                float n = Terrain.noise(seed, x, capTop, z);
                BlockState cap = desert ? Blocks.SANDSTONE.defaultBlockState() : Blocks.RED_SANDSTONE.defaultBlockState();
                Terrain.set(level, pos, x, capTop - 1, z, cap);
                Terrain.set(level, pos, x, capTop - 2, z, n < 0.5F ? cap : desert ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState());
                BlockState surface;
                if (grassy) {
                    surface = n < 0.65F ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    surface = n < 0.6F ? desert ? Blocks.SAND.defaultBlockState() : Blocks.RED_SAND.defaultBlockState() : n < 0.8F ? Blocks.COARSE_DIRT.defaultBlockState() : cap;
                }
                Terrain.set(level, pos, x, capTop, z, surface);
                if (surface.is(Blocks.GRASS_BLOCK) && n < 0.3F) {
                    Terrain.set(level, pos, x, capTop + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (!surface.is(Blocks.GRASS_BLOCK) && !surface.is(cap.getBlock()) && n > 0.97F) {
                    Terrain.set(level, pos, x, capTop + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }
}
