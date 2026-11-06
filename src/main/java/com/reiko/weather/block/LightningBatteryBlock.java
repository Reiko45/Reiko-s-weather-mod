package com.reiko.weather.block;

import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.mojang.serialization.MapCodec;

/**
 * Stores lightning. Every strike within a few blocks adds a charge (up to four); put a lightning rod on
 * top. A redstone signal from the back, the sides or below releases one charge as a two-second,
 * full-strength pulse out of the front face. Comparators read how full it is.
 */
public class LightningBatteryBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LightningBatteryBlock> CODEC = simpleCodec(LightningBatteryBlock::new);
    public static final int MAX_CHARGE = 4;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty DISCHARGING = BooleanProperty.create("discharging");
    private static final int PULSE_TICKS = 40;

    public LightningBatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0)
                .setValue(POWERED, false).setValue(DISCHARGING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGE, POWERED, DISCHARGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the output faces away from the player, like a dispenser
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** Adds a charge from a lightning strike. Returns true if it took one. */
    public static boolean charge(ServerLevel level, BlockPos pos, BlockState state) {
        int charge = state.getValue(CHARGE);
        if (charge >= MAX_CHARGE) {
            return false;
        }
        level.setBlock(pos, state.setValue(CHARGE, charge + 1), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.2F + charge * 0.1F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
        if (charge + 1 == MAX_CHARGE) {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(pos).inflate(32))) {
                WeatherAdvancements.award(player, "bottled_lightning");
            }
        }
        return true;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (level.isClientSide) {
            return;
        }
        boolean signal = hasInput(level, pos, state.getValue(FACING));
        if (signal && !state.getValue(POWERED)) {
            BlockState next = state.setValue(POWERED, true);
            if (state.getValue(CHARGE) > 0 && !state.getValue(DISCHARGING)) {
                next = next.setValue(CHARGE, state.getValue(CHARGE) - 1).setValue(DISCHARGING, true);
                level.scheduleTick(pos, this, PULSE_TICKS);
                level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
                ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        30, 0.5, 0.5, 0.5, 0.3);
            }
            level.setBlock(pos, next, Block.UPDATE_ALL);
            updateFront(level, pos, next);
        } else if (!signal && state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        }
    }

    /** Redstone from the back, the sides or below. Not from above, where its lightning rod sits (rods pulse when struck). */
    private static boolean hasInput(Level level, BlockPos pos, Direction facing) {
        for (Direction d : Direction.values()) {
            if (d != facing && d != Direction.UP && level.getSignal(pos.relative(d), d) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DISCHARGING)) {
            BlockState next = state.setValue(DISCHARGING, false);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            updateFront(level, pos, next);
        }
    }

    private void updateFront(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos front = pos.relative(facing);
        level.neighborChanged(front, this, pos);
        level.updateNeighborsAtExceptFromFacing(front, this, facing.getOpposite());
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(DISCHARGING) && direction == state.getValue(FACING).getOpposite() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return Math.round(state.getValue(CHARGE) * 15.0F / MAX_CHARGE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int charge = state.getValue(CHARGE);
        if (charge > 0 && random.nextInt(8 - charge) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                    0.0, 0.05, 0.0);
        }
    }
}
