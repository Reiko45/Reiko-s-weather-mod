package com.reiko.weather.block;

import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/**
 * Outputs a redstone signal from the weather at its position. Right-click to switch what it measures:
 * precipitation, storm danger, wind, the weather itself (a 0-15 code) or the season.
 */
public class WeatherStationBlock extends Block {
    public enum Mode implements StringRepresentable {
        PRECIPITATION, STORM, WIND, WEATHER, SEASON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public WeatherStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.PRECIPITATION).setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE, POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int power = measure(state.getValue(MODE), level, pos);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, 20);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Mode mode = state.getValue(MODE).next();
            int power = measure(mode, level, pos);
            level.setBlock(pos, state.setValue(MODE, mode).setValue(POWER, power), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.5F, 1.2F);
            player.displayClientMessage(Component.translatable("block.reikos_weather.weather_station.mode",
                    Component.translatable("block.reikos_weather.weather_station.mode." + mode.getSerializedName()), power), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static int measure(Mode mode, Level level, BlockPos pos) {
        LocalWeather local = WeatherManager.local(level, pos);
        if (local == null) {
            return 0;
        }
        WeatherType type = local.type();
        return switch (mode) {
            case PRECIPITATION -> Math.round(local.profile().rain * 15.0F);
            case STORM -> type.vanillaThunder || type.isSevere() ? 15
                    : type == WeatherType.HAIL || type == WeatherType.HEAVY_RAIN ? 10
                    : type.vanillaRain ? 5 : 0;
            case WIND -> Math.round(local.profile().wind * 15.0F);
            case WEATHER -> type.signalCode();
            case SEASON -> {
                Season season = WeatherManager.seasonInfo().season();
                yield season == null ? 0 : season.ordinal() + 1;
            }
        };
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(POWER);
    }
}
