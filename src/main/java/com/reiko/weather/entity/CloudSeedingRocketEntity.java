package com.reiko.weather.entity;

import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Climbs into the sky trailing smoke and bursts into a rain cloud that grows into a passing storm. */
public class CloudSeedingRocketEntity extends Entity implements ItemSupplier {
    private static final int FLIGHT_TICKS = 32;

    public CloudSeedingRocketEntity(EntityType<? extends CloudSeedingRocketEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = new Vec3(getDeltaMovement().x * 0.95, 0.75, getDeltaMovement().z * 0.95);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.FIREWORK, getX(), getY() - 0.3, getZ(), random.nextGaussian() * 0.05, -0.2, random.nextGaussian() * 0.05);
            level().addParticle(ParticleTypes.CLOUD, getX(), getY() - 0.5, getZ(), 0, 0, 0);
            return;
        }
        if (tickCount >= FLIGHT_TICKS || verticalCollision) {
            burst((ServerLevel) level());
        }
    }

    /** Who fired it (for the advancement). */
    @javax.annotation.Nullable
    public java.util.UUID owner;

    private void burst(ServerLevel level) {
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 120, 3.0, 1.0, 3.0, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.WEATHER, 3.0F, 0.7F);
        WeatherState state = WeatherManager.activeState(level);
        String message = "message.reikos_weather.rocket_fizzle";
        if (state != null) {
            float roll = random.nextFloat();
            WeatherType type = roll < 0.6F ? WeatherType.RAIN : roll < 0.9F ? WeatherType.HEAVY_RAIN : WeatherType.THUNDERSTORM;
            StormCell cell = WeatherManager.spawnCell(level, state, type, getX(), getZ(), 160 + random.nextFloat() * 80);
            cell.age = 500; // already well on its way
            message = "message.reikos_weather.rocket_seeded";
            if (owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer shooter) {
                com.reiko.weather.weather.WeatherAdvancements.award(shooter, "rainmaker");
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 128 * 128) {
                player.displayClientMessage(Component.translatable(message), true);
            }
        }
        discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.CLOUD_SEEDING_ROCKET.get());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
