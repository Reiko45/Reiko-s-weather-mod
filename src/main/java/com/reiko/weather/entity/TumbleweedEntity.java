package com.reiko.weather.entity;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.weather.Breezes;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.LocalWeather;
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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tumbleweed: the dried-out top of a desert plant, snapped off at the root by the wind and sent rolling and bouncing
 * across the open ground, scattering its seeds as it goes. It piles up against fences and walls, and breaks up in
 * water, on a cactus, or when it's knocked about.
 */
public class TumbleweedEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(TumbleweedEntity.class, EntityDataSerializers.FLOAT);
    private static final int MAX_NEAR = 10;

    private int life = 2400;
    private int caught;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    /** How far it has rolled, for drawing it turning over (client only). */
    public float roll, prevRoll;
    public float heading;

    public TumbleweedEntity(EntityType<? extends TumbleweedEntity> type, Level level) {
        super(type, level);
    }

    /** Now and then on a windy day in dry, open country, a tumbleweed comes rolling in from upwind of a player. */
    public static void trySpawn(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        if (!WeatherConfig.TUMBLEWEEDS.get() || level.random.nextInt(160) != 0) return;
        float wind = local.profile().wind * local.intensity();
        if (wind < 0.35F || local.profile().rain > 0.05F || local.profile().snowLayers > 0) return;
        WeatherState state = WeatherManager.activeState(level);
        if (state == null) return;
        if (level.getEntitiesOfClass(TumbleweedEntity.class, player.getBoundingBox().inflate(64)).size() >= MAX_NEAR) return;
        Vec3 dir = Wind.direction(state.windAngle);
        double back = 26 + level.random.nextInt(18), side = (level.random.nextDouble() - 0.5) * 40;
        int x = Mth.floor(player.getX() - dir.x * back - dir.z * side), z = Mth.floor(player.getZ() - dir.z * back + dir.x * side);
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (!dryOpenCountry(level, top) || !level.getFluidState(top.below()).isEmpty() || !level.canSeeSky(top)) return;
        TumbleweedEntity weed = new TumbleweedEntity(ModEntities.TUMBLEWEED.get(), level);
        weed.setSize(0.6F + level.random.nextFloat() * 0.5F);
        weed.moveTo(x + 0.5, top.getY() + 0.1, z + 0.5);
        weed.life = 1800 + level.random.nextInt(1800);
        level.addFreshEntity(weed);
    }

    /** Deserts, badlands and dry savanna and scrub: where the tumbleweeds grow. */
    private static boolean dryOpenCountry(Level level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)
                || Climate.at(level, pos) == Climate.ARID && !biome.is(BiomeTags.IS_BEACH);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        entityData.set(DATA_SIZE, size);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 0.8F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(size(), size());
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Any knock breaks it up into a scatter of dry twigs. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) return false;
        breakUp(true);
        return true;
    }

    private void breakUp(boolean drops) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.TUMBLEWEED.get().defaultBlockState()),
                    getX(), getY() + size() / 2, getZ(), 25, size() / 3, size() / 3, size() / 3, 0.05);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 0.8F, 1.3F);
            if (drops && server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS)) {
                int n = 1 + random.nextInt(2);
                server.addFreshEntity(new ItemEntity(server, getX(), getY() + 0.3, getZ(), new ItemStack(Items.STICK, n)));
                if (random.nextInt(3) == 0) server.addFreshEntity(new ItemEntity(server, getX(), getY() + 0.3, getZ(), new ItemStack(Items.DEAD_BUSH)));
            }
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
        if (--life <= 0 || isInWater() || isInLava() || level.getNearestPlayer(this, 128) == null) {
            breakUp(false);
            return;
        }
        if (isOnFire()) life = Math.min(life, 20);
        // the wind where it is (the background wind, bent by local breezes)
        float strength = 0.3F;
        float angle = 0.0F;
        WeatherState state = WeatherManager.activeState(level);
        LocalWeather local = WeatherManager.local(level, blockPosition());
        if (state != null && local != null) {
            float[] breeze = Breezes.adjust(level, getBlockX(), getBlockZ(), state.windAngle, local.profile().wind * local.intensity());
            angle = breeze[0];
            strength = breeze[1];
            if (local.profile().rain > 0.2F) life = Math.min(life, 200); // a soaking leaves it a sodden heap
        }
        Vec3 dir = Wind.direction(angle);
        double speed = 0.04 + 0.32 * strength;
        Vec3 v = getDeltaMovement();
        double grip = onGround() ? 0.08 : 0.03;
        double vx = Mth.lerp(grip, v.x, dir.x * speed), vz = Mth.lerp(grip, v.z, dir.z * speed), vy = v.y - 0.04;
        if (onGround()) {
            // it bounces along, now and then taking a big hop on a gust
            vy = random.nextFloat() < 0.04F + 0.1F * strength ? 0.18 + random.nextFloat() * 0.22 * (0.5 + strength) : Math.max(vy, 0.0);
        }
        setDeltaMovement(vx, vy, vz);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) {
            // try to hop over; if it can't, it's caught (on a fence, a wall, a bush) and stays put
            if (onGround() && random.nextFloat() < 0.15F * strength) setDeltaMovement(getDeltaMovement().x, 0.42, getDeltaMovement().z);
            if (++caught > 60) setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        } else if (caught > 0) {
            caught--;
        }
        setDeltaMovement(getDeltaMovement().multiply(0.98, 0.98, 0.98));
        // a cactus spikes it apart
        AABB box = getBoundingBox().inflate(0.05);
        if (level.getBlockStates(box).anyMatch(s -> s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH))) breakUp(false);
    }

    private void clientTick() {
        double ox = getX(), oz = getZ();
        if (lerpSteps > 0) {
            double k = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * k, getY() + (lerpY - getY()) * k, getZ() + (lerpZ - getZ()) * k);
            lerpSteps--;
        }
        double dx = getX() - ox, dz = getZ() - oz, d = Math.sqrt(dx * dx + dz * dz);
        prevRoll = roll;
        if (d > 1.0E-4) {
            heading = (float) Mth.atan2(dz, dx);
            roll += (float) (d / Math.max(0.2, size() * 0.5)) * Mth.RAD_TO_DEG;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSize(tag.contains("Size") ? tag.getFloat("Size") : 0.8F);
        life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Size", size());
        tag.putInt("Life", life);
    }
}
