package com.reiko.weather.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A rain puddle on flat ground. Splashes when walked through and dries up once the rain stops. */
public class PuddleBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

    public PuddleBlock(Properties properties) {
        super(properties);
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
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isRainingAt(pos) && random.nextInt(2) == 0) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide && entity.getDeltaMovement().horizontalDistanceSqr() > 0.002 && level.random.nextInt(3) == 0) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.SPLASH, entity.getX() + (level.random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.1,
                        entity.getZ() + (level.random.nextDouble() - 0.5) * 0.6, 0.0, 0.1, 0.0);
            }
            if (level.random.nextInt(4) == 0) {
                level.playLocalSound(entity.getX(), pos.getY(), entity.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS,
                        0.08F, 1.4F + level.random.nextFloat() * 0.4F, false);
            }
        }
    }
}
