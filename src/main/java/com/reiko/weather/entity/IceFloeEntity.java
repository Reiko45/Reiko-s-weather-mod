package com.reiko.weather.entity;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherHazards;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A slab of river ice broken loose in the spring thaw. It floats low in the water and drifts along the river
 * (and with the wind), slowly melting. You can stand on it, like a boat. Floes that pile up against each
 * other in a narrow stretch form an ice jam, and the river backs up and floods its banks.
 */
public class IceFloeEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(IceFloeEntity.class, EntityDataSerializers.FLOAT);
    /** An iceberg rather than a floe: far bigger, riding high, and very slow to melt. */
    private static final EntityDataAccessor<Boolean> DATA_BERG = SynchedEntityData.defineId(IceFloeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int MAX_NEAR = 16;

    private int life = 6000;
    private int maxLife = 6000;
    private double headingX, headingZ;
    private double checkX, checkZ;
    private int stuckTicks;
    private int floodCooldown;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;

    public IceFloeEntity(EntityType<? extends IceFloeEntity> type, Level level) {
        super(type, level);
    }

    /** Ice breaking up on a thawing river: now and then a floe breaks off where the ice just melted. */
    public static void breakUp(ServerLevel level, BlockPos water) {
        var biome = level.getBiome(water);
        boolean seaIce = biome.is(BiomeTags.IS_OCEAN) && WeatherConfig.ICEBERGS.get() && biome.value().getBaseTemperature() < 0.4F;
        if (!WeatherConfig.ICE_BREAKUP.get() || level.random.nextFloat() > 0.3F || !biome.is(BiomeTags.IS_RIVER) && !seaIce) {
            return;
        }
        if (level.getEntitiesOfClass(IceFloeEntity.class, new AABB(water).inflate(48)).size() >= MAX_NEAR) {
            return;
        }
        // the floe takes some of the ice around it with it
        for (BlockPos p : BlockPos.betweenClosed(water.offset(-1, 0, -1), water.offset(1, 0, 1))) {
            if (level.getBlockState(p).is(Blocks.ICE) && level.getFluidState(p.below()).is(FluidTags.WATER)) {
                level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
            }
        }
        IceFloeEntity floe = new IceFloeEntity(ModEntities.ICE_FLOE.get(), level);
        float size = 0.8F + level.random.nextFloat() * 0.8F;
        floe.setSize(size);
        floe.life = floe.maxLife = 4800 + level.random.nextInt(4800);
        floe.moveTo(water.getX() + 0.5, water.getY() + 0.7, water.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        Vec3 wind = Wind.direction(WeatherManager.state(level).windAngle);
        floe.headingX = wind.x;
        floe.headingZ = wind.z;
        level.addFreshEntity(floe);
        level.playSound(null, water, SoundEvents.GLASS_BREAK, SoundSource.AMBIENT, 0.6F, 0.6F + level.random.nextFloat() * 0.2F);
    }

    /** Calves (or sets adrift) an iceberg at a spot on open water. */
    public static IceFloeEntity spawnBerg(ServerLevel level, BlockPos water, float size) {
        IceFloeEntity berg = new IceFloeEntity(ModEntities.ICE_FLOE.get(), level);
        berg.entityData.set(DATA_BERG, true);
        berg.setSize(size);
        berg.life = berg.maxLife = 24000 + level.random.nextInt(24000);
        berg.moveTo(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        Vec3 wind = Wind.direction(WeatherManager.state(level).windAngle);
        berg.headingX = wind.x;
        berg.headingZ = wind.z;
        level.addFreshEntity(berg);
        return berg;
    }

    public boolean berg() {
        return entityData.get(DATA_BERG);
    }

    /** How high an iceberg stands out of the water. */
    public float bergHeight() {
        return 1.5F + size() * 0.7F;
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        entityData.set(DATA_SIZE, berg() ? Mth.clamp(size, 1.5F, 5.0F) : Mth.clamp(size, 0.3F, 2.0F));
        refreshDimensions();
    }

    /** Whether it's stuck fast in an ice jam. */
    public boolean jammed() {
        return stuckTicks > 100;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_BERG, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key) || DATA_BERG.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return berg() ? EntityDimensions.fixed(2.0F * size(), bergHeight()) : EntityDimensions.fixed(2.0F * size(), 0.45F);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true; // you can stand on it
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return other instanceof IceFloeEntity || other.canBeCollidedWith() || other.isPushable();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false; // it moves itself, along the river
    }

    /** Hit it hard enough and it shatters. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player || amount >= 4.0F) {
            shatter();
        }
        return true;
    }

    private void shatter() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), getX(), getY() + 0.3, getZ(),
                    30, size(), 0.15, size(), 0.1);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        BlockPos pos = BlockPos.containing(getX(), getY() + 0.1, getZ());
        FluidState fluid = level.getFluidState(pos);
        Vec3 v = getDeltaMovement();
        boolean afloat = fluid.is(FluidTags.WATER);
        if (!afloat && level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            pos = pos.below();
            fluid = level.getFluidState(pos);
            afloat = true;
        }
        if (afloat) {
            if (tickCount % 20 == 0) steer(level);
            double surface = pos.getY() + fluid.getHeight(level, pos);
            double drift = (0.035 + 0.035 * windStrength(level, pos)) * (berg() ? 0.5 : 1.0);
            Vec3 flow = fluid.getFlow(level, pos);
            double vx = Mth.lerp(0.04, v.x, headingX * drift + flow.x * 0.06);
            double vz = Mth.lerp(0.04, v.z, headingZ * drift + flow.z * 0.06);
            // it floats low, with most of it under water (and a crust of snow showing)
            setDeltaMovement(vx, (surface - (berg() ? 0.4 : 0.18) - getY()) * 0.2, vz);
            if (berg() && level.getBiome(pos).value().getBaseTemperature() >= 0.4F) life -= 3; // melting fast in warmer water
        } else {
            setDeltaMovement(v.x * 0.6, v.y - 0.04, v.z * 0.6);
            life -= 3; // stranded on the bank, it melts away fast
        }
        double ox = getX(), oz = getZ();
        move(MoverType.SELF, getDeltaMovement());
        double dx = getX() - ox, dz = getZ() - oz;
        if (horizontalCollision && tickCount % 20 != 0) {
            steer(level);
        }
        // anything standing on it rides along (players move themselves, on their side)
        if (dx * dx + dz * dz > 1.0E-6) {
            for (Entity rider : riders()) {
                if (!(rider instanceof Player)) rider.move(MoverType.SHULKER, new Vec3(dx, 0.0, dz));
            }
        }
        if (tickCount % 20 == 0) {
            for (Entity rider : riders()) {
                if (rider instanceof ServerPlayer player) WeatherAdvancements.award(player, berg() ? "iceberg_ahead" : "floe_rider");
            }
        }
        if (!berg()) jam(level, afloat);
        // melting: the last part of its life it shrinks away
        if (--life <= 0) {
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 20, size(), 0.1, size(), 0.1);
            discard();
            return;
        }
        float left = life / (float) Math.max(1, maxLife);
        if (left < 0.3F && tickCount % 40 == 0) {
            setSize(size() * 0.97F);
        }
    }

    private double windStrength(ServerLevel level, BlockPos pos) {
        LocalWeather local = WeatherManager.local(level, pos);
        return local == null ? 0.2 : local.profile().wind * local.intensity();
    }

    /** Picks the way down the river: the direction with the most open water ahead, preferring to carry on as it was going. */
    private void steer(ServerLevel level) {
        WeatherState state = WeatherManager.state(level);
        Vec3 wind = Wind.direction(state.windAngle);
        double bestScore = -1.0, bx = headingX, bz = headingZ;
        int y = Mth.floor(getY() + 0.1);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0, ux = Math.cos(a), uz = Math.sin(a);
            int water = 0;
            for (int d = 2; d <= 12; d += 2) {
                BlockPos p = BlockPos.containing(getX() + ux * d, y, getZ() + uz * d);
                if (!level.isLoaded(p)) break;
                if (level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.below()).is(FluidTags.WATER)
                        && level.getBlockState(p).isAir()) water++;
                else break;
            }
            double score = water + 2.0 * (ux * headingX + uz * headingZ) + 0.5 * (ux * wind.x + uz * wind.z);
            if (score > bestScore) {
                bestScore = score;
                bx = ux;
                bz = uz;
            }
        }
        headingX = bx;
        headingZ = bz;
    }

    /** Stuck fast against other floes in the river: the water backs up and spills over the banks. */
    private void jam(ServerLevel level, boolean afloat) {
        if (floodCooldown > 0) floodCooldown--;
        if (tickCount % 40 != 0) return;
        double moved = Math.hypot(getX() - checkX, getZ() - checkZ);
        checkX = getX();
        checkZ = getZ();
        List<IceFloeEntity> near = level.getEntitiesOfClass(IceFloeEntity.class, getBoundingBox().inflate(4.0), e -> e != this);
        if (afloat && moved < 0.3 && near.size() >= 2) {
            stuckTicks += 40;
        } else {
            stuckTicks = 0;
        }
        if (jammed() && floodCooldown <= 0) {
            floodCooldown = 400;
            WeatherHazards.jamFlood(level, blockPosition());
        }
    }

    private List<Entity> riders() {
        AABB box = getBoundingBox();
        return level().getEntities(this, new AABB(box.minX, box.maxY - 0.05, box.minZ, box.maxX, box.maxY + 0.6, box.maxZ),
                e -> !(e instanceof IceFloeEntity) && !e.isSpectator() && e.getY() >= box.maxY - 0.1);
    }

    private void clientTick() {
        double ox = getX(), oz = getZ();
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * k, getY() + (lerpY - getY()) * k, getZ() + (lerpZ - getZ()) * k);
            lerpSteps--;
        }
        double dx = getX() - ox, dz = getZ() - oz;
        if (dx * dx + dz * dz > 1.0E-8) {
            // carry the local player along if they're standing on it
            for (Entity rider : riders()) {
                if (rider instanceof Player player && player.isLocalPlayer()) {
                    player.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
                }
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = steps + 2;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < (berg() ? 320 * 320 : 128 * 128);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_BERG, tag.getBoolean("Berg"));
        setSize(tag.contains("Size") ? tag.getFloat("Size") : 1.0F);
        life = tag.getInt("Life");
        maxLife = Math.max(1, tag.getInt("MaxLife"));
        headingX = tag.getDouble("HeadingX");
        headingZ = tag.getDouble("HeadingZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Berg", berg());
        tag.putFloat("Size", size());
        tag.putInt("Life", life);
        tag.putInt("MaxLife", maxLife);
        tag.putDouble("HeadingX", headingX);
        tag.putDouble("HeadingZ", headingZ);
    }
}
