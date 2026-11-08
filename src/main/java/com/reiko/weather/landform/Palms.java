package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Palm trees, built by hand: a slim trunk leaning and curving up from the base, and a crown of long, drooping
 * fronds (with a few dates or coconuts hanging under them). The leaves are marked persistent, so they never
 * decay away from the trunk.
 */
public final class Palms {
    private static final BlockState LOG = Blocks.JUNGLE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState LEAVES = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    /** Builds the part of a palm growing from (x, y + 1, z) that falls inside {@code area}. */
    public static void grow(WorldGenLevel level, BoundingBox area, BlockPos.MutableBlockPos pos, int x, int y, int z, long seed) {
        int height = 6 + (int) (Terrain.hash(seed, 1) * 4);
        float lean = Terrain.hash(seed, 2) * Mth.TWO_PI;
        float curve = 0.25F + 0.35F * Terrain.hash(seed, 3);
        double lx = Mth.cos(lean), lz = Mth.sin(lean);
        int px = x, pz = z;
        for (int h = 1; h <= height; h++) {
            // the trunk leans more the higher it gets
            double off = curve * h * h / height;
            int tx = x + (int) Math.round(lx * off), tz = z + (int) Math.round(lz * off);
            if (tx != px || tz != pz) {
                Terrain.setIn(level, area, pos, tx, y + h - 1, tz, LOG); // keep the trunk joined up at each step
            }
            Terrain.setIn(level, area, pos, tx, y + h, tz, LOG);
            px = tx;
            pz = tz;
        }
        int top = y + height;
        Terrain.setIn(level, area, pos, px, top + 1, pz, LEAVES);
        int fronds = 6 + (int) (Terrain.hash(seed, 4) * 3);
        for (int f = 0; f < fronds; f++) {
            float a = (f + 0.3F * Terrain.hash(seed, 10 + f)) * Mth.TWO_PI / fronds;
            int length = 3 + (int) (Terrain.hash(seed, 20 + f) * 2);
            for (int s = 1; s <= length; s++) {
                int fx = px + Math.round(Mth.cos(a) * s), fz = pz + Math.round(Mth.sin(a) * s);
                // fronds arch up a little, then droop at the tips
                int fy = top + (s == 1 ? 1 : s <= length - 1 ? 0 : -1);
                Terrain.setIn(level, area, pos, fx, fy, fz, LEAVES);
                if (s == length) Terrain.setIn(level, area, pos, fx, fy - 1, fz, LEAVES);
            }
        }
        // a bunch of fruit hanging under the crown
        if (Terrain.hash(seed, 5) < 0.6F) {
            Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(seed));
            BlockState fruit = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, face.getOpposite()).setValue(CocoaBlock.AGE, 2);
            Terrain.setIn(level, area, pos, px + face.getStepX(), top - 1, pz + face.getStepZ(), fruit);
        }
    }

    private Palms() {}
}
