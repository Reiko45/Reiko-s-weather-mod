package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.FireTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Peat: the dark, spongy remains of bog plants, cut and dried as a slow-burning fuel. Lightning (or a fire on
 * top of it) can set it smouldering, and a peat fire creeps slowly through the ground for a long time, sending
 * up smoke and now and then setting the surface alight, until it burns out or water puts it out.
 */
public class PeatBlock extends Block {
    public static final MapCodec<PeatBlock> CODEC = simpleCodec(PeatBlock::new);
    public static final BooleanProperty SMOULDERING = BooleanProperty.create("smouldering");

    public PeatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SMOULDERING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SMOULDERING);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(SMOULDERING);
    }

    /** Sets peat smouldering (if peat fires are on). */
    public static boolean ignite(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PeatBlock && !state.getValue(SMOULDERING) && WeatherConfig.SPEC.isLoaded() && WeatherConfig.PEAT_FIRES.get()
                && !wet(level, pos)) {
            level.setBlockAndUpdate(pos, state.setValue(SMOULDERING, true));
            return true;
        }
        return false;
    }

    private static boolean wet(BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(SMOULDERING)) return;
        // water puts it out; rain only rarely gets down far enough
        if (wet(level, pos) || level.isRainingAt(pos.above()) && random.nextInt(8) == 0 || !WeatherConfig.PEAT_FIRES.get()) {
            level.setBlockAndUpdate(pos, state.setValue(SMOULDERING, false));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
            return;
        }
        FireTracker.onSmoulder(level, pos);
        // creeping on through the peat around it
        BlockPos next = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        if (random.nextInt(3) == 0) {
            ignite(level, next);
        }
        // breaking out at the surface now and then
        BlockPos above = pos.above();
        if (random.nextInt(14) == 0 && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                && (level.getBlockState(above).isAir() || level.getBlockState(above).canBeReplaced()) && level.getBlockState(above).getFluidState().isEmpty()) {
            level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
        }
        // and in the end it burns itself out
        if (random.nextInt(45) == 0) {
            level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
        }
    }

    /** A fire burning right next to peat can set it smouldering. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (!level.isClientSide && !state.getValue(SMOULDERING) && level.getBlockState(from).getBlock() instanceof BaseFireBlock
                && level.random.nextInt(3) == 0) {
            ignite(level, pos);
        }
    }

    /** Smouldering peat scorches whatever walks on it. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(SMOULDERING) && !entity.isSteppingCarefully() && entity instanceof LivingEntity living && !living.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(SMOULDERING)) return;
        BlockPos above = pos.above();
        if (!level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN) && random.nextInt(2) == 0) {
            double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
            level.addParticle(random.nextInt(6) == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SMOKE, x, pos.getY() + 1.02, z,
                    0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
            if (random.nextInt(10) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.02, z, 0.0, 0.01, 0.0);
            }
        }
    }
}
