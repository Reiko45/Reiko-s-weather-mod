package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tar: natural asphalt, oozing up through cracks from oil-bearing rock below and pooling at the surface, soft and
 * sticky in the warmth. Anything that wades in sinks to the knees and sticks fast, and can only drag itself out
 * slowly, heaving against the edge of the pool: for thousands of years, animals that came to drink at the rainwater lying on top were trapped in pools like
 * these, and their bones are found in them still. Bubbles of gas well up through it and burst with a soft plop.
 */
public class TarBlock extends Block {
    public static final MapCodec<TarBlock> CODEC = simpleCodec(TarBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);
    /** What you stand on: you sink most of the way in. */
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 4, 16);

    public TarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FLOOR;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity) || entity.getY() > pos.getY() + 0.94) return;
        if (entity instanceof Player player && player.getAbilities().flying) return;
        // stuck fast: every step is a slow, sucking pull, and it can barely jump...
        Vec3 v = entity.getDeltaMovement();
        double up = v.y > 0.0 ? v.y * 0.5 : v.y;
        // ...but heaving against the edge of the pool, it can drag itself up and out, a little at a time
        // (a slow climb up the side, then a last heave up over the lip of the pool once near the top)
        if (entity.horizontalCollision) up = Math.max(up, entity.getY() > pos.getY() + 0.6 ? 0.5 : 0.16);
        entity.setDeltaMovement(v.x * 0.35, up, v.z * 0.35);
        entity.resetFallDistance();
        if (level.isClientSide && level.random.nextInt(12) == 0 && entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5) {
            level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.5F,
                    0.6F + level.random.nextFloat() * 0.2F, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        // gas bubbling up through the tar, more in the heat of the day
        if (random.nextInt(level.isDay() ? 14 : 30) != 0) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6, y = pos.getY() + 0.95, z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        for (int i = 0; i < 4; i++) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, (random.nextDouble() - 0.5) * 0.08, 0.06 + random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.08);
        }
        if (random.nextInt(3) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.35F, 0.45F + random.nextFloat() * 0.2F, false);
        }
    }
}
