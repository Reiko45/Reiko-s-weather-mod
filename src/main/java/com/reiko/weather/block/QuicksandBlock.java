package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Quicksand: sand so waterlogged it can't bear any weight. You sink in slowly and struggle to move, but it's
 * denser than you are, so you only go in to about your waist (unless it's deep and you go in head first). Slow,
 * careful movements (sneaking) get you out faster than thrashing about.
 */
public class QuicksandBlock extends Block {
    public static final MapCodec<QuicksandBlock> CODEC = simpleCodec(QuicksandBlock::new);

    public QuicksandBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Only ground liquefied by an earthquake is given a tick (natural quicksand never is): once the shaking's over
     * and the water drains out of it, it settles back into ordinary sand.
     */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        level.setBlockAndUpdate(pos, Blocks.SAND.defaultBlockState());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ec && ec.getEntity() instanceof FallingBlockEntity) {
            return Shapes.block();
        }
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        boolean careful = entity.isSteppingCarefully();
        entity.makeStuckInBlock(state, new Vec3(careful ? 0.45 : 0.22, 0.06, careful ? 0.45 : 0.22));
        // buoyant: once you're in to the waist you don't sink any further
        BlockPos chest = BlockPos.containing(entity.getX(), entity.getY() + entity.getBbHeight() * 0.55, entity.getZ());
        if (level.getBlockState(chest).is(this)) {
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(v.x, Math.max(v.y, 0.02), v.z);
        }
        if (level.isClientSide) {
            if ((entity.xOld != entity.getX() || entity.zOld != entity.getZ()) && level.random.nextInt(3) == 0) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), entity.getX(), pos.getY() + 1.0, entity.getZ(), 0, 0.05, 0);
            }
            return;
        }
        if (entity instanceof LivingEntity living && living.tickCount % 20 == 0
                && level.getBlockState(BlockPos.containing(entity.getEyePosition())).is(this)) {
            living.hurt(level.damageSources().inWall(), 1.0F); // in over your head
        }
        if (entity instanceof ServerPlayer player && entity.tickCount % 20 == 0) {
            WeatherAdvancements.award(player, "sinking_feeling");
        }
    }

    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return PathType.POWDER_SNOW; // mobs steer round it
    }
}
