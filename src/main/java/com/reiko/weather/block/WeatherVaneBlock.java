package com.reiko.weather.block;

import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * A weather vane whose arrow turns into the wind. A comparator reads the direction the wind blows from:
 * 1 = north, 2 = northeast ... 8 = northwest.
 */
public class WeatherVaneBlock extends BaseEntityBlock {
    public static final MapCodec<WeatherVaneBlock> CODEC = simpleCodec(WeatherVaneBlock::new);
    public static final IntegerProperty DIRECTION = IntegerProperty.create("direction", 0, 8);
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    private static final List<String> COMPASS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");

    public WeatherVaneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DIRECTION, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherVaneBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WeatherState weather = WeatherManager.activeState(level);
        int direction = weather == null ? 0 : COMPASS.indexOf(Wind.compassFrom(weather.windAngle)) + 1;
        if (direction != state.getValue(DIRECTION)) {
            level.setBlock(pos, state.setValue(DIRECTION, direction), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, 40);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(DIRECTION);
    }
}
