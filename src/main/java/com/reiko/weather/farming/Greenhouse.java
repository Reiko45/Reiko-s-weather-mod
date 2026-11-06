package com.reiko.weather.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * A greenhouse is anything with a glass roof: the first solid thing above is glass (blocks or panes).
 * Crops under glass are safe from frost and hail and keep growing through winter.
 */
public final class Greenhouse {
    private static final int MAX_HEIGHT = 16;

    public static boolean underGlass(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= MAX_HEIGHT; i++) {
            p.move(0, 1, 0);
            BlockState state = level.getBlockState(p);
            if (state.isAir()) {
                continue;
            }
            return state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES);
        }
        return false;
    }

    private Greenhouse() {}
}
