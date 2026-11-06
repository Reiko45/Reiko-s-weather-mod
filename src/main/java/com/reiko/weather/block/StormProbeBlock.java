package com.reiko.weather.block;

import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;

/**
 * A storm probe: set it down in a tornado's path. A tornado passing nearby records some readings (1);
 * one passing right over it records core readings (2). Pick it up again for storm data, which
 * meteorologist villagers pay well for.
 */
public class StormProbeBlock extends Block {
    public static final IntegerProperty READINGS = IntegerProperty.create("readings", 0, 2);
    public static final double NEAR = 16.0;
    public static final double CORE = 6.0;
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 15, 13);

    public StormProbeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(READINGS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(READINGS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** A whirlwind is passing at the given horizontal distance. */
    public static void record(ServerLevel level, BlockPos pos, double distance) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StormProbeBlock)) {
            return;
        }
        int readings = distance <= CORE ? 2 : distance <= NEAR ? 1 : 0;
        if (readings > state.getValue(READINGS)) {
            level.setBlock(pos, state.setValue(READINGS, readings), Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer serverPlayer && state.getValue(READINGS) == 2) {
            WeatherAdvancements.award(serverPlayer, "eye_of_the_storm");
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
