package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A coast of columnar basalt: an old lava flow that cracked into polygonal columns as it cooled, now worn into
 * a pavement of stepping stones running down into the sea, rising to a wall of columns at the back.
 */
public record BasaltColumns(int cx, int cz, float seaAngle, int length, int width, long seed) implements Landform {
    private static final BlockState COLUMN = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState TOP = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final float CELL = 2.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 5 || ground > c.seaLevel() + 8 || !c.fits(ground, 20, 30)) {
            return null;
        }
        float sea = c.seaward(34, 2);
        if (Float.isNaN(sea)) {
            return null;
        }
        return new BasaltColumns(c.x(), c.z(), sea, 26 + c.random().nextInt(13), 15 + c.random().nextInt(7), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new BasaltColumns(tag.getInt("x"), tag.getInt("z"), tag.getFloat("sea"), tag.getInt("length"), tag.getInt("width"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("sea", seaAngle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BASALT_COLUMNS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.max(length, width) + 4;
        return new BoundingBox(cx - r, -64, cz - r, cx + r, 200, cz + r);
    }

    /** Toward the sea, and along the coast. */
    private float[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Mth.cos(seaAngle), s = Mth.sin(seaAngle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    /** The column a point belongs to: the nearest of a jittered grid of centres, {cellX, cellZ, gap to the next nearest}. */
    private double[] cell(double x, double z) {
        int gx = Mth.floor(x / CELL), gz = Mth.floor(z / CELL);
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int bx = 0, bz = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int cxi = gx + i, czj = gz + j;
                double px = (cxi + 0.15 + 0.7 * Terrain.noise(seed, cxi, 1, czj)) * CELL;
                double pz = (czj + 0.15 + 0.7 * Terrain.noise(seed, cxi, 2, czj)) * CELL;
                double d = (px - x) * (px - x) + (pz - z) * (pz - z);
                if (d < best) {
                    second = best;
                    best = d;
                    bx = cxi;
                    bz = czj;
                } else if (d < second) {
                    second = d;
                }
            }
        }
        return new double[]{bx, bz, Math.sqrt(second) - Math.sqrt(best)};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sea = level.getSeaLevel();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                double[] cell = cell(x + 0.5, z + 0.5);
                double ccx = (cell[0] + 0.5) * CELL, ccz = (cell[1] + 0.5) * CELL;
                // the whole column shares its centre's footprint test and height, so the tops are flat polygons
                float[] l = local(ccx, ccz);
                float wobble = 1.0F + 0.2F * (Terrain.field(seed, ccx, ccz, 9.0) - 0.5F);
                float e = (l[0] / width) * (l[0] / width) + (l[1] / length) * (l[1] / length);
                if (e > wobble) continue;
                float t = Mth.clamp((l[0] / width + 1.0F) / 2.0F, 0.0F, 1.0F); // 0 at the back, 1 out at sea
                float base = Mth.lerp(t, sea + 10.0F, sea - 3.0F);
                int top = Math.round(base + (Terrain.noise(seed, (int) cell[0], 3, (int) cell[1]) - 0.5F) * 3.4F);
                int ground = Terrain.ground(level, pos, x, z);
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 20);
                for (int h = Math.min(ground, top) - 3; h <= top; h++) {
                    Terrain.set(level, pos, x, h, z, h == top && cell[2] > 0.45 ? TOP : COLUMN);
                }
                for (int h = top + 1; h <= Math.max(ground, sea); h++) {
                    Terrain.set(level, pos, x, h, z, h <= sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
}
