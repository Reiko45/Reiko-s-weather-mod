package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * River terraces: flat benches stepping up the sides of a river valley. Each was once the river's floodplain; then
 * the river began cutting down again (as the land rose, or the sea fell, or the climate changed) and left it high
 * and dry, and made a new floodplain below. So the oldest terraces are the highest, each one a flat tread of old
 * river gravel under the turf, with a short steep bank down to the next.
 *
 * @param angle  the way the river runs
 * @param width  how far either side of the river the terraces reach
 * @param levels the height of each tread, lowest first
 */
public record RiverTerraces(int cx, int cz, float angle, int length, int width, int[] levels, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        // a chunk's middle seldom lands in the river bed itself, so look round about for it
        return c.search(12, 12, RiverTerraces::planAt);
    }

    @Nullable
    private static Landform planAt(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int river = c.seaLevel() - 1;
        if (ground > river - 1 || ground < river - 14) {
            return null; // it has to be planned from the river itself
        }
        // the river's course: the way the ground stays low
        float start = c.random().nextFloat() * Mth.PI, best = start;
        int bestHigh = Integer.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            float a = start + k * Mth.PI / 6.0F;
            int high = Math.max(c.groundAt(c.x() + Math.round(Mth.cos(a) * 28), c.z() + Math.round(Mth.sin(a) * 28)),
                    c.groundAt(c.x() - Math.round(Mth.cos(a) * 28), c.z() - Math.round(Mth.sin(a) * 28)));
            if (high < bestHigh) {
                bestHigh = high;
                best = a;
            }
        }
        if (bestHigh > river) {
            return null;
        }
        // land either side, high enough to have been cut into terraces
        int left = c.groundAt(c.x() - Math.round(Mth.sin(best) * 36), c.z() + Math.round(Mth.cos(best) * 36));
        int right = c.groundAt(c.x() + Math.round(Mth.sin(best) * 36), c.z() - Math.round(Mth.cos(best) * 36));
        int banks = Math.min(left, right);
        if (banks < river + 5) {
            return null;
        }
        int[] levels = banks >= river + 12
                ? new int[]{river + 2 + c.random().nextInt(2), river + 6 + c.random().nextInt(2), river + 10 + c.random().nextInt(3)}
                : new int[]{river + 2 + c.random().nextInt(2), river + 6 + c.random().nextInt(2)};
        return new RiverTerraces(c.x(), c.z(), best, 90 + c.random().nextInt(31), 36 + c.random().nextInt(9), levels, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RiverTerraces(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getIntArray("levels"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.put("levels", new IntArrayTag(levels));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.RIVER_TERRACES;
    }

    @Override
    public BoundingBox bounds() {
        float half = length / 2.0F;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (float u : new float[]{-half, half}) {
            for (float v : new float[]{-width, width}) {
                double[] w = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(w[0]));
                maxX = Math.max(maxX, Mth.floor(w[0]));
                minZ = Math.min(minZ, Mth.floor(w[1]));
                maxZ = Math.max(maxZ, Mth.floor(w[1]));
            }
        }
        return new BoundingBox(minX, levels[0] - 6, minZ, maxX, levels[levels.length - 1] + 18, maxZ);
    }

    /**
     * The tread a column's ground belongs to: everything between one level and the next is planed off flat to the
     * lower one. Below the lowest tread (the floodplain and the river) and well above the highest, nothing changes.
     */
    private int tread(int ground) {
        if (ground < levels[0]) return ground;
        for (int k = levels.length - 1; k >= 0; k--) {
            if (ground >= levels[k]) return k == levels.length - 1 && ground >= levels[k] + 5 ? ground : levels[k];
        }
        return ground;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float wob = 4.0F * (Terrain.field(seed, x, z, 14.0) - 0.5F);
                float fade = Terrain.smooth(Math.min((half - Math.abs(l[0])) / 16.0F, (width + wob - Math.abs(l[1])) / 12.0F));
                if (fade <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 12) continue;
                // leave the river (and any pond) alone
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                int top = ground - Math.round((ground - tread(ground)) * fade);
                if (top >= ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, 10);
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // turf over a skin of soil, over the old river gravels the terrace is built of (which show in the banks)
                Terrain.set(level, pos, x, top, z, Blocks.GRASS_BLOCK.defaultBlockState());
                Terrain.set(level, pos, x, top - 1, z, Blocks.DIRT.defaultBlockState());
                for (int h = top - 3; h <= top - 2; h++) {
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState s = n < 0.55F ? Blocks.GRAVEL.defaultBlockState() : n < 0.85F ? Blocks.SAND.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                    pos.set(x, h, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, s, Terrain.FLAGS);
                }
                float n = Terrain.noise(seed, x, top, z);
                if (n > 0.97F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                    Terrain.set(level, pos, x, top + 2, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                } else if (n > 0.65F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (n < 0.015F) {
                    Terrain.set(level, pos, x, top + 1, z, n < 0.007F ? Blocks.DANDELION.defaultBlockState() : Blocks.POPPY.defaultBlockState());
                }
            }
        }
    }
}
