package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A snow bridge: snow built up over a gap (a narrow stream in winter, or a crevasse on a glacier) until it spans it.
 * It looks just like the snow round it. In hard frost it holds your weight; in a thaw it gives way under you, and
 * even in the cold, running or jumping onto it can bring it down. A section of it goes at once, not just one block.
 */
public class SnowBridgeBlock extends Block {
    public SnowBridgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(SnowBridgeBlock::new);
    }

    /** How warm the air is here, in °C. */
    private static float air(Level level, BlockPos pos) {
        return level instanceof ServerLevel server ? Temperature.ambient(server, pos, WeatherManager.local(server, pos)) : -10.0F;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide || entity.getBbWidth() < 0.5F) return;
        // soft and rotten in a thaw; in the cold, only a hard knock (a running weight) brings it down
        boolean weak = air(level, pos) > -1.5F;
        if (weak || entity.isSprinting() || level.random.nextInt(400) == 0) giveWay(level, pos, 4 + level.random.nextInt(8));
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!level.isClientSide && fallDistance > 1.0F && entity.getBbWidth() >= 0.5F) giveWay(level, pos, 1);
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /** Starts the bridge collapsing here, with a creak and a puff of snow to warn whoever is on it. */
    public static void giveWay(Level level, BlockPos pos, int delay) {
        if (level.getBlockTicks().hasScheduledTick(pos, level.getBlockState(pos).getBlock())) return;
        level.playSound(null, pos, SoundEvents.POWDER_SNOW_STEP, SoundSource.BLOCKS, 0.9F, 0.6F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.4, 0.05, 0.4, 0.02);
        }
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.playSound(null, pos, SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);
        level.removeBlock(pos, false);
        // the rest of the span goes with it, a moment later
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(d);
            if (level.getBlockState(next).is(this) && random.nextFloat() < 0.65F) {
                level.scheduleTick(next, this, 2 + random.nextInt(4));
            }
        }
        BlockPos above = pos.above();
        if (level.getBlockState(above).is(Blocks.SNOW)) level.removeBlock(above, false);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // it sags and melts through in the thaw, quicker in the sun
        float air = air(level, pos);
        boolean sun = level.isDay() && level.getBrightness(LightLayer.SKY, pos.above()) >= 14;
        if (air > 2.0F && (sun || random.nextInt(3) == 0)) level.removeBlock(pos, false);
    }
}
