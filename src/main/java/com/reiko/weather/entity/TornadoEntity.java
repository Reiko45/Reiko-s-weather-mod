package com.reiko.weather.entity;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModDamageTypes;
import com.reiko.weather.registry.ModTags;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * A tornado. It has no model: the client renders it as a rotating funnel of particles.
 * It wanders across the landscape, sucking up mobs, items and loose blocks, and dissipates after a while.
 */
public class TornadoEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);

    public static final float HEIGHT = 70.0F;
    private static final int GROW_TICKS = 200;
    private static final double SPEED = 0.11;

    /** Set by the client setup; spawns the funnel particles and moves the local player. */
    public static Consumer<TornadoEntity> clientTicker = tornado -> {};

    private float heading;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(float heading, int lifetime, float width) {
        this.heading = heading;
        this.entityData.set(DATA_LIFETIME, lifetime);
        this.entityData.set(DATA_WIDTH, width);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 6.0F);
        builder.define(DATA_LIFETIME, 1800);
        builder.define(DATA_AGE, 0);
    }

    public float width() {
        return this.entityData.get(DATA_WIDTH);
    }

    public int age() {
        return this.entityData.get(DATA_AGE);
    }

    /** 0 to 1: grows when touching down and shrinks before dissipating. */
    public float strength() {
        int age = age();
        int lifetime = this.entityData.get(DATA_LIFETIME);
        float grow = Mth.clamp(age / (float) GROW_TICKS, 0.0F, 1.0F);
        float fade = Mth.clamp((lifetime - age) / (float) GROW_TICKS, 0.0F, 1.0F);
        return Math.min(grow, fade);
    }

    /** Funnel radius at a height above the base. */
    public float radiusAt(float height) {
        float t = Mth.clamp(height / HEIGHT, 0.0F, 1.0F);
        float base = width() * 0.25F;
        return (base + t * t * width() * 2.2F) * (0.35F + 0.65F * strength());
    }

    /** Radius within which entities are pulled in. */
    public double influenceRadius() {
        return width() * 3.5 * strength() + 2.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTicker.accept(this);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        int age = age() + 1;
        this.entityData.set(DATA_AGE, age);
        if (age >= this.entityData.get(DATA_LIFETIME)) {
            discard();
            return;
        }

        // wander, nudged along by the wind
        WeatherState weather = WeatherManager.activeState(level);
        float windAngle = weather != null ? weather.windAngle : heading;
        heading += (random.nextFloat() - 0.5F) * 0.08F + Mth.wrapDegrees((windAngle - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * 0.002F;
        double nx = getX() + Mth.cos(heading) * SPEED;
        double nz = getZ() + Mth.sin(heading) * SPEED;
        BlockPos column = BlockPos.containing(nx, 0, nz);
        if (!level.isLoaded(column)) {
            discard();
            return;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        setPos(nx, Mth.lerp(0.1, getY(), ground), nz);

        float strength = strength();
        if (strength > 0.05F) {
            pullEntities(level, strength);
            if (WeatherConfig.TORNADO_LIFTS_BLOCKS.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                liftBlocks(level, strength);
            }
        }
    }

    private void pullEntities(ServerLevel level, float strength) {
        double influence = influenceRadius();
        AABB box = new AABB(getX() - influence, getY() - 3, getZ() - influence, getX() + influence, getY() + HEIGHT * 0.6, getZ() + influence);
        DamageSource damage = level.damageSources().source(ModDamageTypes.TORNADO, this);
        for (Entity entity : level.getEntities(this, box, e -> !(e instanceof TornadoEntity) && !(e instanceof Player) && !e.isSpectator())) {
            Vec3 velocity = suction(this, entity, strength);
            if (velocity != null) {
                entity.setDeltaMovement(velocity);
                entity.hurtMarked = true;
                if (entity instanceof LivingEntity living && tickCount % 20 == 0
                        && horizontalDistance(entity) < radiusAt((float) (entity.getY() - getY())) * 1.5) {
                    living.hurt(damage, 1.0F);
                }
            }
        }
    }

    /**
     * New velocity for an entity caught in the tornado, or null if it is out of reach.
     * Shared with the client so the local player gets identical physics.
     */
    public static Vec3 suction(TornadoEntity tornado, Entity entity, float strength) {
        double dx = tornado.getX() - entity.getX();
        double dz = tornado.getZ() - entity.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double influence = tornado.influenceRadius();
        double above = entity.getY() - tornado.getY();
        if (distance > influence || above > HEIGHT * 0.6 || above < -4) {
            return null;
        }
        double falloff = 1.0 - distance / influence;
        double nx = distance > 0.01 ? dx / distance : 0.0;
        double nz = distance > 0.01 ? dz / distance : 0.0;
        double core = tornado.radiusAt((float) Math.max(0, above)) * 1.3;
        double inward = 0.05 * falloff * strength;
        double spin = 0.16 * falloff * strength;
        double lift = distance < core ? 0.13 * strength : 0.025 * falloff * strength;
        if (above > HEIGHT * 0.35) {
            // spat out near the top
            inward = -0.12 * strength;
            lift = 0.0;
        }
        Vec3 motion = entity.getDeltaMovement().scale(0.92);
        motion = motion.add(nx * inward - nz * spin, lift, nz * inward + nx * spin);
        double maxUp = 0.6;
        if (motion.y > maxUp) {
            motion = new Vec3(motion.x, maxUp, motion.z);
        }
        return motion;
    }

    private void liftBlocks(ServerLevel level, float strength) {
        int attempts = Mth.ceil(3 * strength);
        float reach = radiusAt(0) * 1.6F + 1.0F;
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * reach;
            int x = Mth.floor(getX() + Math.cos(angle) * r);
            int z = Mth.floor(getZ() + Math.sin(angle) * r);
            if (!level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos pos = BlockPos.containing(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModTags.TORNADO_LIFTABLE)) {
                // plants sit on top of the motion-blocking surface
                pos = pos.above();
                state = level.getBlockState(pos);
                if (!state.is(ModTags.TORNADO_LIFTABLE)) {
                    continue;
                }
            }
            FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, state);
            double tx = -(z + 0.5 - getZ());
            double tz = x + 0.5 - getX();
            double len = Math.max(0.01, Math.sqrt(tx * tx + tz * tz));
            debris.setDeltaMovement(tx / len * 0.5, 0.6 + random.nextDouble() * 0.4, tz / len * 0.5);
            debris.hurtMarked = true;
        }
    }

    private double horizontalDistance(Entity entity) {
        double dx = getX() - entity.getX();
        double dz = getZ() - entity.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.heading = tag.getFloat("Heading");
        this.entityData.set(DATA_WIDTH, tag.getFloat("Width"));
        this.entityData.set(DATA_LIFETIME, tag.getInt("Lifetime"));
        this.entityData.set(DATA_AGE, tag.getInt("Age"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Heading", heading);
        tag.putFloat("Width", width());
        tag.putInt("Lifetime", this.entityData.get(DATA_LIFETIME));
        tag.putInt("Age", age());
    }
}
