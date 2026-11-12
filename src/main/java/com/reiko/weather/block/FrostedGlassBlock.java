package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.WindowFrost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A window of plain glass grown over with frost ferns: on a bitter night the damp air inside a room freezes on the
 * cold glass in feathery, branching crystals. It always turns back into the same glass when it warms up.
 */
public class FrostedGlassBlock extends TransparentBlock {
    public static final MapCodec<FrostedGlassBlock> CODEC = simpleCodec(FrostedGlassBlock::new);

    public FrostedGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (WindowFrost.thaws(level, pos, random)) level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(Blocks.GLASS);
    }
}
