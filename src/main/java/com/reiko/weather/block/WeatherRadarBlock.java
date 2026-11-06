package com.reiko.weather.block;

import com.reiko.weather.ClientActions;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A radar station: right-click to watch storm cells move across a 1.6 km radius. A comparator reads
 * how close the nearest storm is (15 = overhead, fading to 0 at 1,500 blocks).
 */
public class WeatherRadarBlock extends Block {
    public static final IntegerProperty SIGNAL = IntegerProperty.create("signal", 0, 15);

    public WeatherRadarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIGNAL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGNAL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            ClientActions.Holder.instance.openRadar(Vec3.atCenterOf(pos), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int signal = 0;
        WeatherState weather = WeatherManager.activeState(level);
        if (weather != null) {
            for (StormCell cell : weather.cells) {
                double edge = Math.max(0, cell.distanceTo(pos.getX() + 0.5, pos.getZ() + 0.5) - cell.radius);
                signal = Math.max(signal, (int) Math.round(15 - edge / 100.0));
            }
        }
        signal = Math.max(0, Math.min(15, signal));
        if (signal != state.getValue(SIGNAL)) {
            level.setBlock(pos, state.setValue(SIGNAL, signal), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, 40);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(SIGNAL);
    }
}
