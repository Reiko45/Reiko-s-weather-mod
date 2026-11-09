package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Travertine terraces: where warm, mineral-rich spring water spills down a hillside, it leaves its calcium
 * behind as it cools, building white rims that dam up a staircase of shallow, steaming blue pools.
 */
public record Travertine(int cx, int cz, int y, float angle, int steps, long seed) implements Landform {
    private static final float STEP = 4.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        float[] down = c.downhill(28);
        if (ground < c.seaLevel() + 6 || down[1] < 6 || down[1] > 24 || !c.fits(ground, 30, 20)) {
            return null;
        }
        return new Travertine(c.x(), c.z(), ground, down[0], 6 + c.random().nextInt(4), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Travertine(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("steps"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("steps", steps);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TRAVERTINE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(steps * STEP) + 14;
        return new BoundingBox(cx - r, y - steps * 2 - 8, cz - r, cx + r, y + 24, cz + r);
    }

    /** The water level of terrace {@code i}: each one a block or two below the last. */
    private int level(int i) {
        int drop = 0;
        for (int k = 0; k < i; k++) drop += 1 + (Terrain.hash(seed, 100 + k) < 0.45F ? 1 : 0);
        return y - drop;
    }

    private float halfWidth(int i) {
        return 7.0F - i * 0.25F + 2.0F * Terrain.hash(seed, 200 + i);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState calcite = Blocks.CALCITE.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0] + 2.0F;
                if (u < 0.0F || u >= steps * STEP) continue;
                int i = (int) (u / STEP);
                float pu = u - i * STEP;
                // each pool is a scallop, bulging out downhill
                float hw = halfWidth(i) * (0.75F + 0.25F * Mth.sin(pu / STEP * Mth.PI));
                float v = Math.abs(l[1] + 1.5F * Mth.sin(i * 1.7F + Terrain.hash(seed, 1) * 6.0F));
                if (v > hw + 1.0F) continue;
                int water = level(i);
                int ground = Terrain.ground(level, pos, x, z);
                Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, 16);
                for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                for (int h = Math.min(ground, water - 2) - 2; h < water; h++) Terrain.set(level, pos, x, h, z, calcite);
                boolean rim = pu > STEP - 0.9F || v > hw;
                if (rim) {
                    Terrain.set(level, pos, x, water, z, calcite);
                } else {
                    float n = Terrain.noise(seed, x, water, z);
                    // warm water welling up through vents under some of the pools
                    if (n < 0.04F) Terrain.set(level, pos, x, water - 1, z, ModBlocks.VOLCANIC_VENT.get().defaultBlockState());
                    Terrain.set(level, pos, x, water, z, Blocks.WATER.defaultBlockState());
                }
            }
        }
    }
}
