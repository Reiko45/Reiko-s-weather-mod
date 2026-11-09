package com.reiko.weather.entity;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.mixin.ServerGamePacketListenerImplAccessor;
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
import net.minecraft.server.level.ServerPlayer;
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
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    private static final int GROW_TICKS = 200;

    /** The different kinds of rotating column. */
    public enum Kind {
        TORNADO(0.11, 0.08F, 1.0, true, true),
        /** Forms over open water and dies when it reaches land. */
        WATERSPOUT(0.09, 0.06F, 0.6, false, true),
        /** A small, mostly harmless whirlwind of sand on hot, clear days. */
        DUST_DEVIL(0.17, 0.35F, 0.25, false, false),
        /** A whirl of flame and smoke spun up by a huge wildfire, spreading the fire wherever it wanders. */
        FIRE_WHIRL(0.12, 0.4F, 0.3, false, true);

        public final double speed;
        public final float wander;
        public final double suction;
        public final boolean liftsBlocks;
        public final boolean hurts;

        Kind(double speed, float wander, double suction, boolean liftsBlocks, boolean hurts) {
            this.speed = speed;
            this.wander = wander;
            this.suction = suction;
            this.liftsBlocks = liftsBlocks;
            this.hurts = hurts;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Kind byId(String id) {
            for (Kind k : values()) {
                if (k.id().equalsIgnoreCase(id)) return k;
            }
            return null;
        }
    }

    /** Set by the client setup; spawns the funnel particles and moves the local player. */
    public static Consumer<TornadoEntity> clientTicker = tornado -> {};

    private float heading;
    private int ticksOnLand;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(Kind kind, float heading, int lifetime, float width, float height) {
        this.heading = heading;
        this.entityData.set(DATA_KIND, (byte) kind.ordinal());
        this.entityData.set(DATA_LIFETIME, lifetime);
        this.entityData.set(DATA_WIDTH, width);
        this.entityData.set(DATA_HEIGHT, height);
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(this.entityData.get(DATA_KIND), Kind.values().length)];
    }

    /** Funnel height in blocks. */
    public float height() {
        return this.entityData.get(DATA_HEIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, 6.0F);
        builder.define(DATA_LIFETIME, 1800);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_KIND, (byte) 0);
        builder.define(DATA_HEIGHT, 70.0F);
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
        float t = Mth.clamp(height / height(), 0.0F, 1.0F);
        float base = width() * 0.25F;
        return (base + t * t * width() * 2.2F) * (0.35F + 0.65F * strength());
    }

    /** Radius within which entities are pulled in. */
    public double influenceRadius() {
        return width() * 3.5 * strength() + (kind() == Kind.DUST_DEVIL ? 1.0 : 2.0);
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

        Kind kind = kind();
        // wander, nudged along by the wind
        WeatherState weather = WeatherManager.activeState(level);
        float windAngle = weather != null ? weather.windAngle : heading;
        heading += (random.nextFloat() - 0.5F) * kind.wander + Mth.wrapDegrees((windAngle - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * 0.002F;
        // stays nearly in place while it forms (and as it dies), then travels at full speed
        float st = strength();
        double speed = kind.speed * st * st * st;
        double nx = getX() + Mth.cos(heading) * speed;
        double nz = getZ() + Mth.sin(heading) * speed;
        BlockPos column = BlockPos.containing(nx, 0, nz);
        if (!level.isLoaded(column)) {
            discard();
            return;
        }
        // follow the median height of the surrounding ground, so a lone tree, ravine or pit doesn't throw it off
        int[] heights = new int[5];
        int[][] offsets = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int i = 0; i < offsets.length; i++) {
            BlockPos sample = column.offset(offsets[i][0], 0, offsets[i][1]);
            heights[i] = level.isLoaded(sample)
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample.getX(), sample.getZ())
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        }
        java.util.Arrays.sort(heights);
        int ground = heights[2];
        setPos(nx, Mth.lerp(0.1, getY(), ground), nz);

        if (kind == Kind.WATERSPOUT) {
            boolean overWater = !level.getFluidState(BlockPos.containing(nx, ground - 1, nz)).isEmpty();
            ticksOnLand = overWater ? 0 : ticksOnLand + 1;
            int lifetime = this.entityData.get(DATA_LIFETIME);
            if (ticksOnLand > 60 && lifetime - age > GROW_TICKS) {
                // collapses once it comes ashore
                this.entityData.set(DATA_LIFETIME, age + GROW_TICKS);
            }
        }

        float strength = strength();
        if (kind != Kind.DUST_DEVIL && kind != Kind.FIRE_WHIRL && strength > 0.3F && age % 10 == 0) {
            recordProbes(level);
        }
        if (kind == Kind.FIRE_WHIRL && strength > 0.3F && age % 8 == 0) {
            spreadFire(level);
        }
        if (strength > 0.05F) {
            pullEntities(level, strength);
            excusePlayers(level);
            if (kind.liftsBlocks && WeatherConfig.TORNADO_LIFTS_BLOCKS.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                liftBlocks(level, strength);
            }
        }
    }

    /** A fire whirl flings burning embers onto whatever it passes over, and sets alight anything it catches. */
    private void spreadFire(ServerLevel level) {
        for (Entity entity : level.getEntities(this, getBoundingBox().inflate(radiusAt(0) * 1.5, 2, radiusAt(0) * 1.5),
                e -> e instanceof LivingEntity && !e.fireImmune())) {
            entity.igniteForSeconds(4.0F);
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            BlockPos column = BlockPos.containing(getX() + (random.nextDouble() - 0.5) * 8, 0, getZ() + (random.nextDouble() - 0.5) * 8);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (level.getBlockState(top).isAir() || level.getBlockState(top).canBeReplaced() && level.getFluidState(top).isEmpty()) {
                if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, top, net.minecraft.core.Direction.UP)) {
                    level.setBlockAndUpdate(top, net.minecraft.world.level.block.BaseFireBlock.getState(level, top));
                }
            }
        }
    }

    /** Storm probes nearby take readings. */
    private void recordProbes(ServerLevel level) {
        level.getPoiManager().getInRange(holder -> holder.is(com.reiko.weather.registry.ModVillagers.STORM_PROBE_POI), blockPosition(),
                (int) com.reiko.weather.block.StormProbeBlock.NEAR + 2, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .forEach(poi -> {
                    BlockPos p = poi.getPos();
                    double dx = p.getX() + 0.5 - getX(), dz = p.getZ() + 0.5 - getZ();
                    com.reiko.weather.block.StormProbeBlock.record(level, p, Math.sqrt(dx * dx + dz * dz));
                });
    }

    private void pullEntities(ServerLevel level, float strength) {
        double influence = influenceRadius();
        AABB box = new AABB(getX() - influence, getY() - 3, getZ() - influence, getX() + influence, getY() + height() * 0.6, getZ() + influence);
        DamageSource damage = level.damageSources().source(ModDamageTypes.TORNADO, this);
        for (Entity entity : level.getEntities(this, box, e -> !(e instanceof TornadoEntity) && !(e instanceof Player) && !e.isSpectator())) {
            Vec3 velocity = suction(this, entity, strength);
            if (velocity != null) {
                entity.setDeltaMovement(velocity);
                entity.hurtMarked = true;
                if (kind().hurts && entity instanceof LivingEntity living && tickCount % 20 == 0
                        && horizontalDistance(entity) < radiusAt((float) (entity.getY() - getY())) * 1.5) {
                    living.hurt(damage, 1.0F);
                }
            }
        }
    }

    /**
     * Players are moved client-side, and a tornado can keep them airborne for longer than vanilla's
     * anti-fly check allows, so reset that check for everyone within reach.
     */
    private void excusePlayers(ServerLevel level) {
        double influence = influenceRadius() + 4;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - getX(), dz = player.getZ() - getZ();
            if (dx * dx + dz * dz < influence * influence && player.getY() > getY() - 4 && player.getY() < getY() + height()) {
                ((ServerGamePacketListenerImplAccessor) player.connection).reikos_weather$setAboveGroundTickCount(0);
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
        float height = tornado.height();
        if (distance > influence || above > height * 0.6 || above < -6) {
            return null;
        }
        strength *= (float) tornado.kind().suction;
        double falloff = 1.0 - distance / influence;
        double nx = distance > 0.01 ? dx / distance : 0.0;
        double nz = distance > 0.01 ? dz / distance : 0.0;
        double core = tornado.radiusAt((float) Math.max(0, above)) * 1.5 + 1.0;
        double inward = 0.09 * Math.sqrt(falloff) * strength;
        double spin = 0.14 * falloff * strength;
        double lift;
        if (distance < core) {
            lift = 0.21 * strength;
        } else if (distance < core * 2.0) {
            lift = 0.11 * strength * (2.0 - distance / core); // tapering updraft around the funnel
        } else {
            lift = 0.02 * falloff * strength;
        }
        if (above > height * 0.35) {
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
        Kind kind = Kind.byId(tag.getString("Kind"));
        this.entityData.set(DATA_KIND, (byte) (kind == null ? 0 : kind.ordinal()));
        this.entityData.set(DATA_HEIGHT, tag.contains("Height") ? tag.getFloat("Height") : 70.0F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Heading", heading);
        tag.putFloat("Width", width());
        tag.putInt("Lifetime", this.entityData.get(DATA_LIFETIME));
        tag.putInt("Age", age());
        tag.putString("Kind", kind().id());
        tag.putFloat("Height", height());
    }
}
