package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A snow roller: a hollow cylinder of wet snow the wind has rolled up across an open field, with the bare
 * track it left behind it. Rare and short-lived: it slumps away as soon as it thaws.
 */
public class SnowRollerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SnowRollerBlock> CODEC = simpleCodec(SnowRollerBlock::new);
    private static final VoxelShape ALONG_X = box(1, 0, 3, 15, 11, 13);
    private static final VoxelShape ALONG_Z = box(3, 0, 1, 13, 11, 15);

    public SnowRollerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // it rolls toward FACING, so its axis lies across that
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? ALONG_Z : ALONG_X;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11 || !level.getBiome(pos).value().coldEnoughToSnow(pos) || random.nextInt(8) == 0) {
            level.removeBlock(pos, false);
        }
    }
}
