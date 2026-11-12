package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A glacier mouse: a ball of moss, the size of a fist, living on the bare ice of a glacier's lower tongue. Moss grows
 * all round it as it slowly rolls over (shading the ice under it, which then stands up a little so it tips over again),
 * and herds of them creep across the ice together, nobody quite knows how they agree which way.
 */
public class GlacierMouseBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 5, 11);

    public GlacierMouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(GlacierMouseBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // now and then it rolls over onto the next patch of ice (the herd drifts the same way, as the ice slopes)
        if (random.nextInt(6) != 0) return;
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos to = pos.relative(d);
        BlockState under = level.getBlockState(to.below());
        boolean ice = under.is(Blocks.ICE) || under.is(Blocks.PACKED_ICE) || under.is(Blocks.BLUE_ICE);
        if (ice && level.getBlockState(to).isAir()) {
            level.removeBlock(pos, false);
            level.setBlockAndUpdate(to, state);
        }
    }
}
