package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A fumarole on a volcano's slopes, hissing out hot, sulfurous steam. Scalding to stand on (unless you sneak). */
public class VolcanicVentBlock extends Block {
    public static final MapCodec<VolcanicVentBlock> CODEC = simpleCodec(VolcanicVentBlock::new);

    public VolcanicVentBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Under a hot spring the vent bubbles up through the water, and steam drifts off the surface. */
    private static void hotSpring(Level level, BlockPos pos, RandomSource random) {
        BlockPos.MutableBlockPos p = pos.above().mutable();
        for (int i = 0; i < 6 && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER); i++) {
            level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, p.getX() + random.nextDouble(), p.getY() + 0.2, p.getZ() + random.nextDouble(), 0.0, 0.04, 0.0);
            p.move(0, 1, 0);
        }
        if (level.getBlockState(p).isAir()) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + (random.nextDouble() - 0.5) * 3.0 + 0.5, p.getY() + 0.1,
                        p.getZ() + (random.nextDouble() - 0.5) * 3.0 + 0.5, 0.0, 0.02, 0.0);
            }
            if (random.nextInt(8) == 0) {
                level.playLocalSound(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, com.reiko.weather.registry.ModSounds.HOT_SPRING.get(),
                        SoundSource.BLOCKS, 0.4F, 0.8F + random.nextFloat() * 0.4F, false);
            }
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity && !entity.isInWater()) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER)) {
            hotSpring(level, pos, random);
            return;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.CLOUD, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4,
                    0.0, 0.08 + random.nextDouble() * 0.06, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0.0, 0.05, 0.0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 0.6F + random.nextFloat() * 0.3F, false);
        }
        // blue fire: where the vent gas is rich in sulphur it burns as it meets the air, a ghostly blue only seen at night
        if (!level.isDay() && sulphurous(level, pos)) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.6, y - 0.05 + random.nextDouble() * 0.2,
                        z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
            }
            if (random.nextInt(12) == 0) {
                level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5F, 0.7F, false);
            }
        }
    }

    /** Sulphur crusting round the vent (or on it) means the gas will burn blue. */
    private static boolean sulphurous(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockState(p).is(com.reiko.weather.registry.ModBlocks.SULFUR_CRUST.get())) return true;
        }
        return false;
    }
}
