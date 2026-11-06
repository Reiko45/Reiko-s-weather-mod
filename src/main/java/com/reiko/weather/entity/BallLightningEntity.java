package com.reiko.weather.entity;

import com.reiko.weather.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Ball lightning: a crackling orb that drifts a little above the ground, zaps anything that gets too
 * close, and pops after a while (or when hit, or when it touches water).
 */
public class BallLightningEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);

    /** Set by the client setup: sparks and the crackling sound. */
    public static Consumer<BallLightningEntity> clientTicker = ball -> {};

    private float heading;

    public BallLightningEntity(EntityType<? extends BallLightningEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.heading = level.random.nextFloat() * Mth.TWO_PI;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE, 0);
        builder.define(DATA_LIFETIME, 300 + (int) (Math.random() * 300));
    }

    public int age() {
        return this.entityData.get(DATA_AGE);
    }

    /** 0-1 fade in/out. */
    public float brightness(float partialTick) {
        float age = age() + partialTick;
        int lifetime = this.entityData.get(DATA_LIFETIME);
        return Mth.clamp(Math.min(age / 20.0F, (lifetime - age) / 20.0F), 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTicker.accept(this);
            return;
        }
        int age = age() + 1;
        this.entityData.set(DATA_AGE, age);
        if (age >= this.entityData.get(DATA_LIFETIME) || isInWaterOrRain() && random.nextInt(200) == 0 || isInWater()) {
            pop();
            return;
        }
        // drift and bob about a block off the ground
        heading += (random.nextFloat() - 0.5F) * 0.3F;
        int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        double targetY = ground + 1.3 + Mth.sin(age * 0.12F) * 0.35;
        Vec3 motion = new Vec3(Mth.cos(heading) * 0.045, (targetY - getY()) * 0.08, Mth.sin(heading) * 0.045);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision) {
            heading += Mth.PI;
        }
        if (age % 15 == 0) {
            zap((ServerLevel) level());
        }
    }

    private void zap(ServerLevel level) {
        DamageSource source = level.damageSources().source(ModDamageTypes.BALL_LIGHTNING, this);
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5),
                e -> !(e instanceof Player p && (p.isSpectator() || p.isCreative())))) {
            target.hurt(source, 2.0F);
            hit = true;
            if (target instanceof net.minecraft.server.level.ServerPlayer player) {
                com.reiko.weather.weather.WeatherAdvancements.award(player, "shocking");
            }
        }
        if (hit) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 20, 1.0, 0.6, 1.0, 0.2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.WEATHER, 1.0F, 1.6F);
        }
    }

    private void pop() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 60, 0.4, 0.4, 0.4, 0.6);
            level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.WEATHER, 1.5F, 0.8F);
            if (random.nextInt(7) == 0) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(getX(), getY() - 1, getZ())));
                    level.addFreshEntity(bolt);
                }
            }
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isRemoved()) {
            pop();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_AGE, tag.getInt("Age"));
        if (tag.contains("Lifetime")) this.entityData.set(DATA_LIFETIME, tag.getInt("Lifetime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age());
        tag.putInt("Lifetime", this.entityData.get(DATA_LIFETIME));
    }
}
