package com.reiko.weather.block;

import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

/** Wails and emits a redstone signal while a tornado or waterspout is within 160 blocks. */
public class TornadoSirenBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int RANGE = 160;

    public TornadoSirenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean alarm = !level.getEntities(ModEntities.TORNADO.get(), new AABB(pos).inflate(RANGE, 128, RANGE),
                t -> t.kind() == TornadoEntity.Kind.TORNADO || t.kind() == TornadoEntity.Kind.WATERSPOUT || t.kind() == TornadoEntity.Kind.LANDSPOUT).isEmpty();
        if (alarm != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, alarm), Block.UPDATE_ALL);
        }
        if (alarm && level.getGameTime() / 20L % 6L == 0L) {
            level.playSound(null, pos, ModSounds.SIREN.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
        }
        level.scheduleTick(pos, this, 20);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
