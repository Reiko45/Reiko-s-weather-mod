package com.reiko.weather.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** A blob of lava flung out of an erupting crater. It arcs through the air trailing smoke and bursts where it lands. */
public class VolcanicBombEntity extends Entity {
    public static final double GRAVITY = 0.05;
    private static final int MAX_AGE = 400;

    public VolcanicBombEntity(EntityType<? extends VolcanicBombEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement().add(0.0, -GRAVITY, 0.0).scale(0.995);
        Vec3 from = position();
        Vec3 to = from.add(motion);
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                double t = random.nextDouble();
                level().addAlwaysVisibleParticle(i == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, true,
                        from.x + (to.x - from.x) * t + random.nextGaussian() * 0.15, from.y + (to.y - from.y) * t + random.nextGaussian() * 0.15,
                        from.z + (to.z - from.z) * t + random.nextGaussian() * 0.15, 0.0, 0.03, 0.0);
            }
            if (random.nextInt(4) == 0) level().addParticle(ParticleTypes.LAVA, from.x, from.y, from.z, 0.0, 0.0, 0.0);
            setDeltaMovement(motion);
            setPos(to);
            return;
        }
        if (tickCount > MAX_AGE || to.y < level().getMinBuildHeight()) {
            discard();
            return;
        }
        // don't burst on the way out of the crater's own lava lake
        if (tickCount > 3) {
            BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() != HitResult.Type.MISS) {
                impact((ServerLevel) level(), hit.getLocation());
                return;
            }
        }
        List<Entity> struck = level().getEntities(this, getBoundingBox().expandTowards(motion).inflate(0.3),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        if (!struck.isEmpty()) {
            impact((ServerLevel) level(), struck.get(0).position().add(0.0, 0.5, 0.0));
            return;
        }
        setDeltaMovement(motion);
        setPos(to);
    }

    private void impact(ServerLevel level, Vec3 at) {
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        BlockPos pos = BlockPos.containing(at);
        if (!level.getFluidState(pos).isEmpty() && !level.getBlockState(pos).is(Blocks.LAVA)) {
            // quenched in water with a hiss
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 12, 0.4, 0.2, 0.4, 0.02);
            discard();
            return;
        }
        level.explode(this, at.x, at.y, at.z, 1.4F, griefing, griefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(2.5))) {
            living.igniteForSeconds(4.0F);
        }
        if (griefing) {
            // the bomb's own rock, still glowing
            for (int i = 0; i < 3; i++) {
                BlockPos p = pos.offset(random.nextInt(3) - 1, -1 - random.nextInt(2), random.nextInt(3) - 1);
                BlockState state = level.getBlockState(p);
                BlockState above = level.getBlockState(p.above());
                if (state.isSolidRender(level, p) && !state.hasBlockEntity() && !state.is(BlockTags.FEATURES_CANNOT_REPLACE)
                        && (above.isAir() || above.canBeReplaced())) {
                    level.setBlockAndUpdate(p, random.nextInt(5) < 2 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                    break;
                }
            }
        }
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 10, 0.5, 0.3, 0.5, 0.0);
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 384 * 384;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Never written to disk: a bomb frozen mid-air in an unloaded chunk shouldn't land days later. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
