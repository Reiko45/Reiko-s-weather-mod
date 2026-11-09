package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A braided river: in cold, bare country, meltwater rivers carry far more gravel than they can move, so it piles
 * up into a wide, flat plain the river splits across in a tangle of shallow channels, dividing and rejoining
 * round bars of stones. Driftwood lies stranded where the last flood dropped it.
 */
public record BraidedRiver(int cx, int cz, int y, float angle, int length, int width, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int length = 110 + c.random().nextInt(70);
        int width = 14 + c.random().nextInt(8);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() || !c.fits(ground, 8, 12)) {
            return null;
        }
        // lie it along the valley: the way across which the land is flattest
        float best = 0.0F;
        int bestRange = Integer.MAX_VALUE, bestMin = ground;
        float start = c.random().nextFloat() * Mth.PI;
        for (int k = 0; k < 6; k++) {
            float a = start + k * Mth.PI / 6.0F;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int i = -4; i <= 4; i++) {
                double[] p = Terrain.xz(i * length / 8.0, 0.0, c.x(), c.z(), a);
                int h = c.groundAt((int) p[0], (int) p[1]);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
            if (max - min < bestRange) {
                bestRange = max - min;
                bestMin = min;
                best = a;
            }
        }
        if (bestRange > 7 || bestMin < c.seaLevel()) {
            return null;
        }
        return new BraidedRiver(c.x(), c.z(), bestMin, best, length, width, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new BraidedRiver(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.BRAIDED_RIVER;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 12;
        return new BoundingBox(cx - r, y - 4, cz - r, cx + r, y + 20, cz + r);
    }

    /** The half-width of the gravel plain {@code u} blocks along it, narrowing to nothing at the ends. */
    private float plain(float u) {
        float half = length / 2.0F;
        return width * (0.75F + 0.35F * Mth.sin(u * 0.04F + Terrain.hash(seed, 1) * 6.0F)) * Terrain.smooth((half - Math.abs(u)) / 18.0F);
    }

    /** How deep the water runs at a point (0 on a bar). */
    private int channel(float u, float v, float half) {
        int deepest = 0;
        for (int j = 0; j < 5; j++) {
            float f = 0.025F + 0.03F * Terrain.hash(seed, 10 + j), p = Terrain.hash(seed, 20 + j) * Mth.TWO_PI;
            float w = j == 0 ? 2.4F : 1.1F + 0.9F * Terrain.hash(seed, 30 + j);
            float centre = (half - w - 2.0F) * 0.85F * Mth.sin(u * f + p) * (j == 0 ? 0.6F : 1.0F);
            // channels pinch out and break up here and there, leaving the bars joined
            if (j > 0 && Terrain.field(seed + j, u, 0.0, 22.0) < 0.3F) continue;
            float off = Math.abs(v - centre);
            if (off < w) deepest = Math.max(deepest, j == 0 && off < w * 0.6F ? 2 : 1);
        }
        return deepest;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float halfLength = length / 2.0F;
        Direction.Axis along = Math.abs(Mth.cos(angle)) > Math.abs(Mth.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = Math.abs(l[1]);
                if (Math.abs(u) > halfLength) continue;
                float half = plain(u);
                if (v > half + 6.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, y, z);
                if (v > half) {
                    // the plain's edge: the valley floor comes down to it (or is banked up to hold the water)
                    float t = (v - half) / 6.0F;
                    int bank = Math.round(Mth.lerp(t * t, y + 1, ground));
                    if (bank == ground) continue;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, bank), z, 12);
                    for (int h = bank + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = ground; h <= bank; h++) Terrain.set(level, pos, x, h, z, h == bank ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    continue;
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, 14);
                for (int h = y + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                int depth = v < half - 1.5F && Math.abs(u) < halfLength - 10.0F ? channel(u, l[1], half) : 0;
                for (int h = Math.min(ground, y - 3); h <= y - depth; h++) {
                    Terrain.set(level, pos, x, h, z, Terrain.noise(seed, x, h, z) < 0.15F ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                }
                if (depth > 0) {
                    for (int h = y - depth + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                // the bars: bare gravel, the older ones grassing over, with boulders and stranded driftwood
                float age = Terrain.field(seed + 7, x, z, 9.0);
                if (age > 0.7F) {
                    Terrain.set(level, pos, x, y, z, Blocks.COARSE_DIRT.defaultBlockState());
                    if (n < 0.35F) Terrain.set(level, pos, x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                    else if (n > 0.96F) Terrain.set(level, pos, x, y + 1, z, Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                } else if (n > 0.985F) {
                    Terrain.set(level, pos, x, y + 1, z, n > 0.993F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                } else if (n < 0.01F) {
                    // a drift log, lying the way the water ran
                    BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along);
                    int dx = along == Direction.Axis.X ? 1 : 0, dz = 1 - dx;
                    // (back over the columns already built, so their clearing doesn't take it away again)
                    for (int k = 0; k < 3; k++) Terrain.setIn(level, area, pos, x - dx * k, y + 1, z - dz * k, log);
                }
            }
        }
    }
}
