package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A meltwater rill: in a fast thaw the ground under the snow is still frozen, so the meltwater can't soak in and runs
 * off over the surface in little trickling streams, down the slope below the melting snow. They dry up as soon as the
 * snow above them has gone, and freeze to a glaze when the frost comes back at night. {@link #FACING} is the way the
 * water runs.
 */
public class MeltwaterRillBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MeltwaterRillBlock> CODEC = simpleCodec(MeltwaterRillBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

    public MeltwaterRillBlock(Properties properties) {
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
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(below).isEmpty();
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
        float air = Temperature.ambient(level, pos, null);
        if (air < -1.0F) {
            // the night frost glazes it over
            level.setBlockAndUpdate(pos, ModBlocks.FROZEN_PUDDLE.get().defaultBlockState());
            return;
        }
        if (!fed(level, pos, state.getValue(FACING)) && random.nextInt(2) == 0) {
            level.removeBlock(pos, false);
        }
    }

    /** Whether there's still snow melting on the slope above, feeding the rill. */
    public static boolean fed(Level level, BlockPos pos, Direction flow) {
        Direction up = flow.getOpposite();
        for (int d = 1; d <= 10; d++) {
            BlockPos column = pos.relative(up, d);
            if (!level.isLoaded(column)) return true;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (top.getY() < pos.getY() - 1 || top.getY() > pos.getY() + 12) return false;
            for (int k = 0; k <= 1; k++) {
                BlockState s = level.getBlockState(top.below(k));
                if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(ModBlocks.DUSTY_SNOW.get())) return true;
            }
        }
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                    0.08F + random.nextFloat() * 0.05F, 1.6F + random.nextFloat() * 0.3F, false);
        }
        if (random.nextInt(6) == 0) {
            Direction flow = state.getValue(FACING);
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5 + flow.getStepX() * 0.4, pos.getY() + 0.05,
                    pos.getZ() + 0.5 + flow.getStepZ() * 0.4, 0.0, 0.0, 0.0);
        }
    }
}
