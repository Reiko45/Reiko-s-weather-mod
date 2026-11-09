package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Hoodoos: tall, thin spires of soft, banded rock, each one protected from the rain by a cap of harder stone
 * on top, while everything around it has been washed away. They pinch in at the neck just under the cap.
 */
public record Hoodoos(int cx, int cz, long seed) implements Landform {
    private static final BlockState[] BANDS = {Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(30, 8) > 16 || !c.fits(ground, 10, 30)) {
            return null;
        }
        return new Hoodoos(c.x(), c.z(), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Hoodoos(tag.getInt("x"), tag.getInt("z"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.HOODOOS;
    }

    @Override
    public BoundingBox bounds() {
        return new BoundingBox(cx - 44, -64, cz - 44, cx + 44, 320, cz + 44);
    }

    /** {x, z, radius, height} for each spire, clustered in a few groups. */
    private float[][] spires() {
        int count = 14 + (int) (Terrain.hash(seed, 0) * 12);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            int group = i % 4;
            float ga = Terrain.hash(seed, 300 + group) * Mth.TWO_PI, gr = Terrain.hash(seed, 310 + group) * 26.0F;
            float a = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, r = Terrain.hash(seed, 20 + i) * 10.0F;
            list[i] = new float[]{cx + Mth.cos(ga) * gr + Mth.cos(a) * r, cz + Mth.sin(ga) * gr + Mth.sin(a) * r,
                    1.1F + Terrain.hash(seed, 30 + i) * 1.2F, 6.0F + Terrain.hash(seed, 40 + i) * 11.0F};
        }
        return list;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int offset = (int) (Terrain.hash(seed, 1) * BANDS.length);
        BlockState cap = Terrain.hash(seed, 2) < 0.5F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
        for (float[] s : spires()) {
            int minX = Mth.floor(s[0] - s[2] - 2), maxX = Mth.floor(s[0] + s[2] + 2);
            int minZ = Mth.floor(s[1] - s[2] - 2), maxZ = Mth.floor(s[1] + s[2] + 2);
            for (int x = Math.max(area.minX(), minX); x <= Math.min(area.maxX(), maxX); x++) {
                for (int z = Math.max(area.minZ(), minZ); z <= Math.min(area.maxZ(), maxZ); z++) {
                    float d = (float) Math.sqrt((x + 0.5 - s[0]) * (x + 0.5 - s[0]) + (z + 0.5 - s[1]) * (z + 0.5 - s[1]));
                    if (d > s[2] + 1.6F) continue;
                    int ground = Terrain.ground(level, pos, x, z);
                    int height = Math.round(s[3]);
                    if (d < s[2] + 0.4F) Terrain.clearAbove(level, pos, x, ground, z, height + 3);
                    for (int k = 1; k <= height + 1; k++) {
                        boolean capLayer = k >= height;
                        float r = capLayer ? s[2] + 1.0F : k >= height - 2 ? s[2] - 0.45F : s[2] * (1.0F + 0.25F * (1.0F - k / (float) height));
                        r += 0.3F * (Terrain.noise(seed, x, k, z) - 0.5F);
                        if (d > r) continue;
                        Terrain.set(level, pos, x, ground + k, z, capLayer ? cap : BANDS[Math.floorMod((ground + k) / 2 + offset, BANDS.length)]);
                    }
                }
            }
        }
    }
}
