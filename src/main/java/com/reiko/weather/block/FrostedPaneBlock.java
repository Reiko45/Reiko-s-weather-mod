package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.WindowFrost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A glass pane grown over with frost ferns on a bitter night; it turns back into the same pane when it warms up. */
public class FrostedPaneBlock extends IronBarsBlock {
    public static final MapCodec<FrostedPaneBlock> CODEC = simpleCodec(FrostedPaneBlock::new);

    public FrostedPaneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (WindowFrost.thaws(level, pos, random)) level.setBlockAndUpdate(pos, WindowFrost.copyPane(state, Blocks.GLASS_PANE.defaultBlockState()));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(Blocks.GLASS_PANE);
    }
}
