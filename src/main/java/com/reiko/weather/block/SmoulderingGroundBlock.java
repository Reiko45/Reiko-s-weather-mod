package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Smouldering ground over a burning coal seam: cracked, ashy rock with the fire still creeping along underneath. It
 * breathes out smoke and fumes, it's hot underfoot, and snow never lies on it.
 */
public class SmoulderingGroundBlock extends Block {
    public static final MapCodec<SmoulderingGroundBlock> CODEC = simpleCodec(SmoulderingGroundBlock::new);

    public SmoulderingGroundBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity living && !living.fireImmune() && level.getGameTime() % 20L == 0L) {
            DamageSource hot = level.damageSources().hotFloor();
            entity.hurt(hot, 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        BlockState on = level.getBlockState(above);
        if (on.is(Blocks.SNOW) || on.is(Blocks.POWDER_SNOW) || on.is(BlockTags.ICE) && !on.is(Blocks.BLUE_ICE)) {
            level.removeBlock(above, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.05, z, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
        if (random.nextInt(40) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, pos.getY() + 1.1, z, 0.0, 0.03, 0.0);
        }
    }
}
