package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small wind turbine. Its rotor faces whichever way you were looking when you placed it (in 16 steps, like a
 * sign), spins with the wind's strength, and it emits a redstone signal (0-15) that grows with wind speed.
 * Higher, more exposed windmills catch more wind.
 */
public class WindmillBlock extends BaseEntityBlock {
    public static final MapCodec<WindmillBlock> CODEC = simpleCodec(WindmillBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /** Which way the rotor faces, 0-15 like a standing sign: 0 = south, 4 = west, 8 = north, 12 = east. */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public WindmillBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(ROTATION, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, ROTATION);
    }

    /** The rotor faces the player who places it, so you see the sails. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation() + 180.0F));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    /** The rotor's facing as a yaw for the renderer (radians; its models face north at 0). */
    public static float rotorYaw(BlockState state) {
        return Mth.PI - RotationSegment.convertToDegrees(state.getValue(ROTATION)) * Mth.DEG_TO_RAD;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindmillBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Wind strength (0-1) at a windmill: weather wind, exposure and height. */
    public static float windAt(Level level, BlockPos pos, float weatherWind) {
        if (!Wind.isExposed(level, pos.above())) {
            weatherWind *= 0.25F;
        }
        float height = Mth.clamp(1.0F + (pos.getY() - level.getSeaLevel()) / 80.0F, 0.6F, 1.6F);
        // even a calm day has a breeze up high
        return Mth.clamp((0.08F + weatherWind) * height, 0.0F, 1.0F);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LocalWeather local = WeatherManager.local(level, pos);
        float wind = local == null ? 0.0F : local.profile().wind * local.intensity() * Wind.gust(level.getGameTime(), 0.0F);
        int power = Math.round(windAt(level, pos, wind) * 15.0F);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, 20);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }
}
