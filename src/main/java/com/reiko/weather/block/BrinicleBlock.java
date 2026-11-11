package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A brinicle: as sea ice freezes it squeezes out brine far colder and saltier than the sea round it. The brine sinks
 * in a thin stream and freezes the water it touches into a hollow tube of ice, which grows down, block by block, to
 * the sea floor. There it spreads out over the bottom in a web of ice. It only lasts while the cold does.
 */
public class BrinicleBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<BrinicleBlock> CODEC = simpleCodec(BrinicleBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);

    public BrinicleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /** Hangs from sea ice (or the brinicle above it). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(this) || above.is(Blocks.ICE) || above.is(Blocks.PACKED_ICE) || above.is(Blocks.BLUE_ICE);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            return state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > -6.0F) {
            // the cold has eased: the brine stops sinking and the tube melts away from the bottom up
            if (!level.getBlockState(pos.below()).is(this)) {
                level.setBlockAndUpdate(pos, state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            return;
        }
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (under.is(Blocks.WATER) && under.getFluidState().isSource()) {
            level.setBlockAndUpdate(below, defaultBlockState());
        } else if (!under.is(this) && under.isFaceSturdy(level, below, Direction.UP)) {
            // on the bottom: the brine spreads out over the sea floor and freezes a web of ice round the foot
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            for (int i = 1; i <= 1 + random.nextInt(3); i++) {
                BlockPos web = pos.relative(d, i);
                BlockState there = level.getBlockState(web);
                if (!there.is(Blocks.WATER) || !there.getFluidState().isSource()) break;
                level.setBlockAndUpdate(web, ModBlocks.FRAZIL_ICE.get().defaultBlockState());
            }
        }
    }
}
