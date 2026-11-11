package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Aufeis ("ice on top"): in a hard winter, water keeps seeping out of springs and stream banks, spreads over the ice
 * already there and freezes, layer on layer, until a valley floor is buried under a sheet of milky, banded ice that
 * lasts long into spring. Slippery underfoot.
 */
public class AufeisBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];
    static {
        for (int i = 1; i <= 8; i++) SHAPES[i] = Block.box(0, 0, 0, 16, i * 2, 16);
    }

    public AufeisBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(AufeisBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(this)) return below.getValue(LAYERS) == 8;
        return below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE) || below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && state.getValue(LAYERS) < 8 && context.getClickedFace() == Direction.UP;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        if (there.is(this)) return there.setValue(LAYERS, Math.min(8, there.getValue(LAYERS) + 1));
        return super.getStateForPlacement(context);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // it melts back slowly, a layer at a time, once the spring is properly under way
        float air = Temperature.ambient(level, pos, WeatherManager.local(level, pos));
        if (air > 1.5F && random.nextFloat() < Math.min(1.0F, (air - 1.5F) * 0.2F)) {
            int layers = state.getValue(LAYERS);
            BlockState above = level.getBlockState(pos.above());
            if (above.is(this)) return;
            level.setBlockAndUpdate(pos, layers > 1 ? state.setValue(LAYERS, layers - 1) : Blocks.AIR.defaultBlockState());
        }
    }
}
