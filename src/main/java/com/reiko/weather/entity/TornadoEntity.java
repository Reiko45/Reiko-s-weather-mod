package com.reiko.weather.entity;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.mixin.ServerGamePacketListenerImplAccessor;
import com.reiko.weather.registry.ModDamageTypes;
import com.reiko.weather.registry.ModTags;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
    /** The rating on the Enhanced Fujita scale the tornado will build up to, EF0 to EF5 (-1 for the other kinds of whirl). */
    private static final EntityDataAccessor<Byte> DATA_RATING = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.BYTE);
    /** How strong it is just now, on the same scale but smoothly (2.6 is an EF2 well on its way to EF3). */
    private static final EntityDataAccessor<Float> DATA_INTENSITY = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    /** Its look and habits: bits 0-2 the funnel's shape, 3 rain-wrapped, 4-6 how many suction vortices, 7 a satellite. */
    private static final EntityDataAccessor<Integer> DATA_STYLE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);

    /** The shape of a tornado's funnel (whatever it starts as, a violent one becomes a wedge, and they all rope out). */
    public enum Shape {
        /** The classic: narrow at the ground, widening steadily up to the cloud. */
        CONE,
        /** A straight-sided column, as wide at the ground as halfway up, flaring only under the cloud. */
        STOVEPIPE,
        /** A thin point at the ground under a broad funnel that bulges out halfway up. */
        DRILLBIT,
        /** Long and narrow, bending and swaying like an elephant's trunk. */
        TRUNK
    }

    /**
     * How often each rating turns up, EF0 to EF5, as real tornadoes do: most are weak, a few strong, and a violent
     * EF4 or EF5 very rare.
     */
    private static final float[] RATING_ODDS = {0.45F, 0.30F, 0.15F, 0.07F, 0.025F, 0.005F};
    /** How hard each rating pulls, lifts and throws, against the old single strength (EF2). */
    private static final float[] RATING_POWER = {0.45F, 0.7F, 1.0F, 1.2F, 1.4F, 1.6F};
    /** The funnel's typical width at each rating (its own width is scaled from this as it strengthens and weakens). */
    private static final float[] RATING_WIDTH = {3.0F, 4.25F, 6.0F, 8.25F, 11.0F, 14.25F};
    /** How much a tornado's core hurts anything caught in it, a second (EF0 and EF1 only throw you about). */
    private static final float[] CORE_DAMAGE = {0.0F, 0.0F, 1.0F, 2.0F, 3.0F, 5.0F};

    private static final int GROW_TICKS = 200;

    /** The different kinds of rotating column. */
    public enum Kind {
        TORNADO(0.11, 0.08F, 1.0, true, true),
        /** Forms over open water and dies when it reaches land. */
        WATERSPOUT(0.09, 0.06F, 0.6, false, true),
        /** A small, mostly harmless whirlwind of sand on hot, clear days. */
        DUST_DEVIL(0.17, 0.35F, 0.25, false, false),
        /** A whirl of flame and smoke spun up by a huge wildfire, spreading the fire wherever it wanders. */
        FIRE_WHIRL(0.12, 0.4F, 0.3, false, true),
        /** A thin, rope-like tornado that spins up under a growing storm without a big rotating updraught behind it. */
        LANDSPOUT(0.1, 0.15F, 0.6, false, true),
        /** A short-lived swirl of dust and debris kicked up along a storm's gust front. */
        GUSTNADO(0.2, 0.3F, 0.4, false, false),
        /** A wisp of steam fog twisted into a little whirl over unfrozen water in bitter cold. */
        STEAM_DEVIL(0.08, 0.3F, 0.1, false, false),
        /** A dust devil made of snow, on a sunny, windy snowfield. */
        SNOW_DEVIL(0.17, 0.35F, 0.2, false, false);

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

        /** The little whirlwinds nobody needs warning about. */
        public boolean small() {
            return this == DUST_DEVIL || this == GUSTNADO || this == STEAM_DEVIL || this == SNOW_DEVIL;
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
    /** The highest rating it's reached so far (on the server). */
    private int reached = -1;
    /** Which in a family of tornadoes from the same storm this is (0 the first; -1 not one that can start a family). */
    private int generation = -1;
    /** For a satellite: the tornado it circles, and where round it it is. */
    @org.jetbrains.annotations.Nullable private java.util.UUID parentId;
    private float orbit;
    /** Whether it's had its chance at a satellite, and at a successor from the same storm (and whether one's certain). */
    private boolean triedSatellite, triedSuccessor, wantsSatellite;

    /** Lets it start a family: the next tornado from the same storm can touch down as it ropes out. */
    public void setGeneration(int generation) {
        this.generation = generation;
    }

    /** Makes sure it throws off a satellite once it's strong (for the command). */
    public void wantSatellite() {
        this.wantsSatellite = true;
    }

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
        builder.define(DATA_RATING, (byte) -1);
        builder.define(DATA_INTENSITY, 0.0F);
        builder.define(DATA_STYLE, 0);
    }

    // ------------------------------------------------------------------ the kinds of tornado

    public Shape shape() {
        return Shape.values()[Math.floorMod(this.entityData.get(DATA_STYLE) & 7, Shape.values().length)];
    }

    /** Hidden inside a curtain of heavy rain: you barely see it until it's close. */
    public boolean rainWrapped() {
        return (this.entityData.get(DATA_STYLE) & 8) != 0;
    }

    /** How many small suction vortices whirl round inside it once it's strong (0 for a single-vortex tornado). */
    public int vortices() {
        return this.entityData.get(DATA_STYLE) >> 4 & 7;
    }

    /** A smaller tornado circling round a big one. */
    public boolean satellite() {
        return (this.entityData.get(DATA_STYLE) & 128) != 0;
    }

    public void setStyle(Shape shape, boolean rainWrapped, int vortices, boolean satellite) {
        this.entityData.set(DATA_STYLE, shape.ordinal() | (rainWrapped ? 8 : 0) | Mth.clamp(vortices, 0, 7) << 4 | (satellite ? 128 : 0));
    }

    /** The suction vortices whirling just now: they only form as it strengthens past EF2. */
    public int activeVortices() {
        return intensity() >= 2.5F ? vortices() : 0;
    }

    /** Where suction vortex {@code i} is just now (x, z): they race round the funnel's foot. */
    public double[] vortexAt(int i, float partialTick) {
        int n = Math.max(1, vortices());
        float angle = (age() + partialTick) * 0.11F + i * Mth.TWO_PI / n + (getId() % 7);
        float r = radiusAt(0.0F) * 0.8F + 1.0F;
        return new double[]{getX() + Mth.cos(angle) * r, getZ() + Mth.sin(angle) * r};
    }

    /** How far through its life it is, 0 to 1. */
    public float lifeFraction() {
        return age() / (float) Math.max(1, this.entityData.get(DATA_LIFETIME));
    }

    /** How far into roping out it is, 0 to 1 (a rated tornado's last quarter or so). */
    public float roping() {
        return peakRating() >= 0 ? smooth01((lifeFraction() - 0.72F) / 0.18F) : 0.0F;
    }

    /**
     * How far the funnel's axis is bent aside at a height (x, z): an elephant's trunk sways, and every tornado
     * twists into a sinuous rope as it dies.
     */
    public float[] axisOffset(float height, float partialTick) {
        float t = Mth.clamp(height / height(), 0.0F, 1.0F);
        float bend = (shape() == Shape.TRUNK && peakRating() >= 0 ? 0.8F * width() + 3.0F : 0.0F) + roping() * 0.5F * width() + roping() * 3.0F;
        if (bend <= 0.0F) return new float[]{0.0F, 0.0F};
        float sway = (age() + partialTick) * 0.006F + getId();
        float k = Mth.sin(t * Mth.PI) * bend;
        float wiggle = Mth.sin(t * 9.0F + sway * 3.0F) * roping() * 1.2F;
        return new float[]{Mth.cos(sway) * k + wiggle, Mth.sin(sway) * k};
    }

    /** The rating it builds up to at its peak, EF0 to EF5, or -1 for a whirl that isn't rated (a waterspout and so on). */
    public int peakRating() {
        return this.entityData.get(DATA_RATING);
    }

    /** Its strength just now on the EF scale, smoothly from 0 up to its peak rating. */
    public float intensity() {
        int peak = peakRating();
        return peak < 0 ? 0.0F : Mth.clamp(this.entityData.get(DATA_INTENSITY), 0.0F, peak);
    }

    /** Its rating just now: EF0 as it touches down, climbing to its peak and dropping back as it ropes out (-1 unrated). */
    public int rating() {
        int peak = peakRating();
        return peak < 0 ? -1 : Math.min(peak, Mth.floor(intensity() + 1.0E-3F));
    }

    /** The highest rating it's reached so far: what it'll be rated once it's over (on the server). */
    public int reached() {
        return Math.max(reached, rating());
    }

    public void setRating(int peak) {
        this.entityData.set(DATA_RATING, (byte) Mth.clamp(peak, -1, 5));
        this.entityData.set(DATA_INTENSITY, 0.0F);
    }

    /**
     * A tornado's life: it touches down weak, strengthens through the ratings over the first part of its life
     * (organizing), holds at its peak for a while (mature, when it does most of its damage), then shrinks back
     * into a thin rope and lifts. {@code f} is how far through its life it is, 0 to 1.
     */
    public static float intensityAt(float f, int peak) {
        if (peak <= 0) return 0.0F;
        // (a little over the peak at the top, so it holds there rather than flickering just under it)
        float top = peak + 0.2F;
        if (f < 0.4F) return top * smooth01((f - 0.03F) / 0.37F);
        if (f < 0.72F) return top;
        return top * (1.0F - smooth01((f - 0.72F) / 0.25F));
    }

    private static float smooth01(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** A value from a per-rating table, read off smoothly at a strength between the ratings. */
    private static float atIntensity(float[] table, float intensity) {
        int i = Mth.clamp(Mth.floor(intensity), 0, table.length - 2);
        return Mth.lerp(Mth.clamp(intensity - i, 0.0F, 1.0F), table[i], table[i + 1]);
    }

    /** A rating for a tornado that's just touched down, as often as real ones turn up. */
    public static int rollRating(net.minecraft.util.RandomSource random) {
        float roll = random.nextFloat();
        for (int r = 0; r < RATING_ODDS.length; r++) {
            roll -= RATING_ODDS[r];
            if (roll < 0.0F) return r;
        }
        return 0;
    }

    /** How hard it pulls, lifts and throws, for its kind and rating (1 for an EF2 tornado). */
    public double power() {
        return kind().suction * (peakRating() >= 0 ? atIntensity(RATING_POWER, intensity()) : 1.0F);
    }

    /** The funnel's width at the ground just now (a rated tornado's grows as it strengthens, and shrinks to a rope). */
    public float width() {
        float width = this.entityData.get(DATA_WIDTH);
        int peak = peakRating();
        return peak < 0 ? width : width * atIntensity(RATING_WIDTH, intensity()) / RATING_WIDTH[peak];
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
        float w = width();
        // (the violent ones are wedges, broad right down to the ground; the weak ones thin ropes)
        float cone = w * (0.25F + 0.07F * Math.max(0.0F, intensity() - 2.0F)) + t * t * w * 2.2F;
        float r = cone;
        if (kind() == Kind.TORNADO && peakRating() >= 0) {
            r = switch (shape()) {
                case CONE -> cone;
                case STOVEPIPE -> w * 0.5F + (t > 0.8F ? (t - 0.8F) / 0.2F * ((t - 0.8F) / 0.2F) * w * 1.6F : 0.0F);
                case DRILLBIT -> w * 0.08F + smooth01((t - 0.2F) / 0.35F) * w * 1.3F + t * t * w * 1.0F;
                case TRUNK -> w * 0.16F + t * w * 0.4F + t * t * t * w * 1.0F;
            };
            // whatever it started as, a violent tornado becomes a wedge...
            r = Mth.lerp(Mth.clamp((intensity() - 3.0F) / 1.5F, 0.0F, 1.0F), r, cone);
            // ...and every one thins to a rope at the end
            r = Mth.lerp(roping() * 0.75F, r, w * 0.12F + t * t * w);
        }
        return r * (0.35F + 0.65F * strength());
    }

    /** Radius within which entities are pulled in. */
    public double influenceRadius() {
        // (a real tornado's inflow reaches well out from the funnel: you feel it pulling long before you're in it)
        return kind().small() ? width() * 3.5 * strength() + 1.0 : Math.min(64.0, width() * 5.0 * strength() + 6.0);
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
            if (peakRating() >= 0 && reached() >= 0) announce(level, Component.translatable("message.reikos_weather.tornado_lifted", reached()), 0xFFAA66);
            discard();
            return;
        }
        if (peakRating() >= 0) {
            // strengthening, holding, roping out
            this.entityData.set(DATA_INTENSITY, intensityAt(age / (float) this.entityData.get(DATA_LIFETIME), peakRating()));
            int now = rating();
            if (!satellite() && !triedSatellite && now >= peakRating() && peakRating() >= 3) {
                triedSatellite = true;
                float chance = wantsSatellite ? 1.0F : peakRating() == 3 ? 0.3F : peakRating() == 4 ? 0.45F : 0.55F;
                if (random.nextFloat() < chance) spawnSatellite(level);
            }
            if (generation >= 0 && !triedSuccessor && lifeFraction() >= 0.72F) {
                triedSuccessor = true;
                spawnSuccessor(level);
            }
            if (now > reached) {
                if (reached >= 0 && now >= 2) {
                    announce(level, Component.translatable(now >= 4 ? "message.reikos_weather.tornado_warning_violent"
                            : "message.reikos_weather.tornado_warning_rated", now), now >= 4 ? 0xFF2020 : 0xFF5555);
                }
                reached = now;
            }
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
        if (satellite() && parentId != null) {
            // a satellite circles its parent, a little way out from it
            if (level.getEntity(parentId) instanceof TornadoEntity parent && parent.isAlive()) {
                orbit += 0.012F;
                double distance = Mth.clamp(parent.width() * 2.2F + 12.0F, 16.0F, 40.0F);
                nx = Mth.lerp(0.2, getX(), parent.getX() + Mth.cos(orbit) * distance);
                nz = Mth.lerp(0.2, getZ(), parent.getZ() + Mth.sin(orbit) * distance);
            } else {
                // its parent has lifted: it doesn't last long without it
                parentId = null;
                int lifetime = this.entityData.get(DATA_LIFETIME);
                if (lifetime - age > GROW_TICKS) this.entityData.set(DATA_LIFETIME, age + GROW_TICKS);
            }
        }
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
        if (!kind.small() && kind != Kind.FIRE_WHIRL && strength > 0.3F && age % 10 == 0) {
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
                if (rating() >= 2 && WeatherConfig.TORNADO_BREAKS_BUILDS.get()) tear(level, strength);
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
        AABB box = new AABB(getX() - influence, getY() - 3, getZ() - influence, getX() + influence, getY() + height() * 0.8, getZ() + influence);
        DamageSource damage = level.damageSources().source(ModDamageTypes.TORNADO, this);
        for (Entity entity : level.getEntities(this, box, e -> !(e instanceof TornadoEntity) && !(e instanceof Player) && !e.isSpectator())) {
            Vec3 velocity = suction(this, entity, strength);
            if (velocity != null) {
                entity.setDeltaMovement(velocity);
                entity.hurtMarked = true;
                entity.fallDistance = Math.min(entity.fallDistance, fallCap(this));
                if (entity.getY() > getY() + 3) CARRIED.put(entity, new long[]{level.getGameTime(), (long) (fallCap(this) * 100), rating()});
                if (kind().hurts && entity instanceof LivingEntity living && tickCount % 20 == 0
                        && horizontalDistance(entity) < radiusAt((float) (entity.getY() - getY())) * 1.5) {
                    living.hurt(damage, Math.max(1.0F, coreDamage()));
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
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - getX(), dz = player.getZ() - getZ();
            if (dx * dx + dz * dz < influence * influence && player.getY() > getY() - 4 && player.getY() < getY() + height()) {
                ((ServerGamePacketListenerImplAccessor) player.connection).reikos_weather$setAboveGroundTickCount(0);
                if (!player.isCreative()) {
                    player.fallDistance = Math.min(player.fallDistance, fallCap(this));
                    // (caught up off the ground: it'll be thrown, and the fall that follows is capped wherever it lands)
                    if (player.getY() > getY() + 3) CARRIED.put(player, new long[]{now, (long) (fallCap(this) * 100), rating()});
                    // a strong tornado's core is full of flying debris
                    float hurt = coreDamage() * strength();
                    if (hurt >= 0.5F && tickCount % 20 == 0 && !player.isSpectator()
                            && Math.sqrt(dx * dx + dz * dz) < radiusAt((float) Math.max(0.0, player.getY() - getY())) * 1.5) {
                        player.hurt(level.damageSources().source(ModDamageTypes.TORNADO, this), hurt);
                    }
                }
            }
        }
        landSoftly(level, now);
    }

    /** What a funnel has carried up lately (on the server): when, the most its fall can count for (x100), and the rating. */
    private static final java.util.Map<Entity, long[]> CARRIED = new java.util.WeakHashMap<>();

    /** Keeps the fall of anything thrown lately capped, however far from the funnel it comes down (and its flight excused). */
    private static void landSoftly(ServerLevel level, long now) {
        if (CARRIED.isEmpty()) return;
        var it = CARRIED.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity entity = e.getKey();
            long[] info = e.getValue();
            if (entity.isRemoved() || now - info[0] > THROW_COOLDOWN + 100 || now < info[0]) {
                it.remove();
                continue;
            }
            if (entity.level() != level) continue;
            entity.fallDistance = Math.min(entity.fallDistance, info[1] / 100.0F);
            if (entity instanceof ServerPlayer player) {
                ((ServerGamePacketListenerImplAccessor) player.connection).reikos_weather$setAboveGroundTickCount(0);
                // thrown by a violent tornado, and come down alive
                if (info[2] >= 4 && player.onGround() && now - info[0] > 10 && player.isAlive()) {
                    com.reiko.weather.weather.WeatherAdvancements.award(player, "wedge");
                    it.remove();
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
        float height = tornado.height();
        if (distance > influence || above > height * 0.8 || above < -6) {
            return null;
        }
        // (something just thrown out of the funnel falls clear of it before it can be caught again)
        long now = entity.level().getGameTime();
        Long thrown = THROWN.get(entity);
        if (thrown != null && now - thrown < THROW_COOLDOWN && now >= thrown) {
            return null;
        }
        double power = strength * tornado.power();
        double falloff = 1.0 - distance / influence;
        double nx = distance > 0.01 ? dx / distance : 0.0;
        double nz = distance > 0.01 ? dz / distance : 0.0;
        // a Rankine vortex: the wind spins up toward the funnel's wall and is calmer inside it
        double core = tornado.radiusAt((float) Math.max(0, above)) * 1.5 + 2.5;
        double ratio = distance / core;
        double swirl = 0.8 * power * (ratio < 1.0 ? ratio : Math.pow(1.0 / ratio, 0.7)) * Math.min(1.0, falloff * 4.0);
        // the funnel carries things up only so far before flinging them out of its side
        double ceiling = ceiling(tornado);
        double rising = Mth.clamp((ceiling - above) / (0.25 * ceiling + 2.0), 0.0, 1.0);
        if (rising < 0.15) {
            // flung out of the side of the funnel, hard: whatever it carried up lands a good way off
            THROWN.put(entity, now);
            double out = 1.0 + 2.0 * power;
            return new Vec3(-nx * out - nz * swirl, 0.35 * power, -nz * out + nx * swirl);
        }
        // air rushes in along the ground and turns upward round the funnel
        double inflow = 0.42 * power * Math.sqrt(falloff) * Math.min(1.0, ratio);
        inflow = inflow * rising - 0.7 * power * (1.0 - rising);
        double shape = Mth.clamp(1.5 - 0.4 * ratio, 0.0, 1.0);
        double updraft = 0.6 * power * shape * rising;
        // how much of the weight of whatever is caught the updraft bears
        double carry = Mth.clamp(updraft / 0.12, 0.0, 1.0);
        double grip = (0.06 + 0.24 * power) * Math.sqrt(falloff);
        Vec3 v = entity.getDeltaMovement();
        double targetX = nx * inflow - nz * swirl, targetZ = nz * inflow + nx * swirl;
        double vx = v.x + (targetX - v.x) * grip;
        double vz = v.z + (targetZ - v.z) * grip;
        double vy = updraft > 0.0 ? v.y + (updraft - v.y) * grip * shape * rising + 0.08 * carry : v.y;
        return new Vec3(vx, Math.min(vy, 0.7), vz);
    }

    /** How high above its base the funnel carries what it catches before throwing it out. */
    public static double ceiling(TornadoEntity tornado) {
        return tornado.height() * (0.15 + 0.4 * tornado.strength() * tornado.power());
    }

    /** How long something thrown out of a funnel falls free before a funnel can catch it again, in ticks. */
    private static final long THROW_COOLDOWN = 200L;
    /** When each thing caught was last thrown out (the local player on the client, mobs and items on the server). */
    private static final java.util.Map<Entity, Long> THROWN = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** How far something tossed by the funnel is counted as falling, at most (so a toss hurts, but needn't kill). */
    static float fallCap(TornadoEntity tornado) {
        // (higher for the violent ones: an EF5's toss is a fall of eighteen blocks)
        double p = tornado.strength() * tornado.power();
        return 3.0F + 8.0F * (float) Math.min(p, 1.0) + 12.0F * (float) Math.max(0.0, p - 1.0);
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

    /** A smaller tornado touching down beside this one and circling round it. */
    private void spawnSatellite(ServerLevel level) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        double distance = Mth.clamp(width() * 2.2F + 12.0F, 16.0F, 40.0F);
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(getX() + Mth.cos(angle) * distance, getY(), getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(BlockPos.containing(at))) return;
        TornadoEntity moon = com.reiko.weather.weather.WeatherHazards.spawnTornado(level, at, heading, Kind.TORNADO, random.nextInt(3));
        moon.setStyle(Shape.values()[random.nextInt(Shape.values().length)], rainWrapped(), 0, true);
        moon.parentId = getUUID();
        moon.orbit = angle;
        // (it doesn't outlast the big one)
        int left = this.entityData.get(DATA_LIFETIME) - age();
        moon.entityData.set(DATA_LIFETIME, Math.max(GROW_TICKS * 2, Math.min(moon.entityData.get(DATA_LIFETIME), left - 60)));
        announce(level, Component.translatable("message.reikos_weather.tornado_satellite"), 0xFF5555);
    }

    /**
     * As a tornado ropes out, the storm above it can wrap up a fresh rotation alongside and drop another one: a
     * family of tornadoes from the same supercell, one after another, more often on an outbreak day.
     */
    private void spawnSuccessor(ServerLevel level) {
        if (generation >= 4) return;
        com.reiko.weather.weather.LocalWeather local = com.reiko.weather.weather.WeatherManager.local(level, blockPosition());
        if (local == null || !local.profile().tornadoes || local.intensity() < 0.6F) return;
        boolean outbreak = com.reiko.weather.weather.TornadoOutbreaks.today(level);
        if (random.nextFloat() > (outbreak ? 0.7F : 0.4F)) return;
        if (level.getEntities(com.reiko.weather.registry.ModEntities.TORNADO.get(), e -> e.kind() == Kind.TORNADO).size()
                >= WeatherConfig.MAX_TORNADOES.get() + 2) return;
        // a little way off, to the right of the way the storm's going
        float side = heading + Mth.HALF_PI * 0.6F;
        double distance = 70.0 + random.nextDouble() * 40.0;
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(getX() + Mth.cos(side) * distance, getY(), getZ() + Mth.sin(side) * distance);
        BlockPos column = BlockPos.containing(at);
        if (!level.isLoaded(column)) return;
        at = new net.minecraft.world.phys.Vec3(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), at.z);
        TornadoEntity next = com.reiko.weather.weather.WeatherHazards.spawnTornado(level, at, heading, Kind.TORNADO, -1);
        next.generation = generation + 1;
        announce(level, Component.translatable("message.reikos_weather.tornado_family"), 0xFF5555);
    }

    /** Tells everyone nearby (it touches down, strengthens, lifts). */
    private void announce(ServerLevel level, Component message, int color) {
        if (!WeatherConfig.TORNADO_WARNINGS.get()) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 200 * 200) {
                player.displayClientMessage(message.copy().withStyle(s -> s.withColor(color).withBold(color != 0xFFAA66)), true);
            }
        }
    }

    /** How much the core hurts, a second. */
    private float coreDamage() {
        return peakRating() >= 0 ? atIntensity(CORE_DAMAGE, intensity()) : 0.0F;
    }

    /**
     * Whether a tornado of this rating tears a block apart: an EF2 breaks glass, fences, doors and wool; an EF3 rips
     * wood (planks, logs, wooden roofs); an EF4 soil and sand; an EF5 stone and brick (see the reikos_weather:tornado_ef2
     * to tornado_ef5 tags, each adding to the last). Never solid rock, ores, unbreakable blocks, or anything holding
     * things (chests, furnaces and the like keep their contents).
     */
    public static boolean tears(BlockState state, int rating) {
        if (rating < 2 || state.isAir() || state.hasBlockEntity() || state.getBlock().defaultDestroyTime() < 0.0F) return false;
        return state.is(ModTags.TORNADO_LIFTABLE) || state.is(ModTags.TORNADO_EF2)
                || rating >= 3 && state.is(ModTags.TORNADO_EF3) || rating >= 4 && state.is(ModTags.TORNADO_EF4)
                || rating >= 5 && state.is(ModTags.TORNADO_EF5);
    }

    /**
     * A strong tornado's damage path: it peels apart whatever it can, from the outside in, as wide as the funnel,
     * flinging some of it as debris; and a violent one scours the grass off the ground.
     */
    private void tear(ServerLevel level, float strength) {
        int rating = rating();
        int attempts = Mth.ceil((3 + 3 * (rating - 1)) * strength);
        float reach = width() * 0.6F + 2.0F;
        int debris = level.getEntitiesOfClass(FallingBlockEntity.class, getBoundingBox().inflate(40.0, 40.0, 40.0)).size();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int vortices = activeVortices();
        for (int i = 0; i < attempts; i++) {
            // in a multiple-vortex tornado the worst of it is in the little vortices whirling round inside: narrow
            // streaks of heavy damage, with lighter damage between them
            double cx = getX(), cz = getZ(), spread = reach;
            int power = rating;
            if (vortices > 0) {
                if (random.nextBoolean()) {
                    double[] v = vortexAt(random.nextInt(vortices), 0.0F);
                    cx = v[0];
                    cz = v[1];
                    spread = 2.5;
                    power = Math.min(5, rating + 1);
                } else {
                    power = rating - 1;
                }
            }
            double angle = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * spread;
            int x = Mth.floor(cx + Math.cos(angle) * r), z = Mth.floor(cz + Math.sin(angle) * r);
            int y = Mth.floor(getY()) - 1 + random.nextInt(16);
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (power >= 4 && (state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.PODZOL)
                    || state.is(net.minecraft.world.level.block.Blocks.MYCELIUM)) && level.getBlockState(pos.above()).isAir()) {
                // the grass scoured off, down to bare earth
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState(), 3);
                continue;
            }
            if (!tears(state, power) || !level.getFluidState(pos).isEmpty() || !exposed(level, pos)) continue;
            BlockPos at = pos.immutable();
            if (debris < 24 && random.nextInt(3) == 0) {
                // flung out as debris
                FallingBlockEntity piece = FallingBlockEntity.fall(level, at, state);
                piece.dropItem = false;
                double tx = -(z + 0.5 - getZ()), tz = x + 0.5 - getX();
                double len = Math.max(0.01, Math.sqrt(tx * tx + tz * tz));
                double out = 0.3 + 0.15 * power;
                piece.setDeltaMovement(tx / len * out, 0.5 + random.nextDouble() * 0.3 * power, tz / len * out);
                piece.hurtMarked = true;
                debris++;
            } else {
                // blown away
                level.destroyBlock(at, false);
            }
        }
    }

    /** Whether the wind can get at a block: open to the air on at least one side. */
    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(d)).isAir()) return true;
        }
        return false;
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
        this.entityData.set(DATA_RATING, tag.contains("Rating") ? tag.getByte("Rating") : (byte) (kind == Kind.TORNADO ? 2 : -1));
        this.entityData.set(DATA_INTENSITY, tag.contains("Intensity") ? tag.getFloat("Intensity") : Math.max(0, peakRating()));
        this.reached = tag.contains("Reached") ? tag.getInt("Reached") : -1;
        this.entityData.set(DATA_STYLE, tag.getInt("Style"));
        this.generation = tag.contains("Generation") ? tag.getInt("Generation") : -1;
        this.parentId = tag.hasUUID("Parent") ? tag.getUUID("Parent") : null;
        this.orbit = tag.getFloat("Orbit");
        this.triedSatellite = tag.getBoolean("TriedSatellite");
        this.triedSuccessor = tag.getBoolean("TriedSuccessor");
        this.wantsSatellite = tag.getBoolean("WantsSatellite");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Heading", heading);
        tag.putFloat("Width", width());
        tag.putInt("Lifetime", this.entityData.get(DATA_LIFETIME));
        tag.putInt("Age", age());
        tag.putString("Kind", kind().id());
        tag.putFloat("Height", height());
        tag.putByte("Rating", (byte) peakRating());
        tag.putFloat("Intensity", this.entityData.get(DATA_INTENSITY));
        tag.putInt("Reached", reached);
        tag.putInt("Style", this.entityData.get(DATA_STYLE));
        tag.putInt("Generation", generation);
        if (parentId != null) tag.putUUID("Parent", parentId);
        tag.putFloat("Orbit", orbit);
        tag.putBoolean("TriedSatellite", triedSatellite);
        tag.putBoolean("TriedSuccessor", triedSuccessor);
        tag.putBoolean("WantsSatellite", wantsSatellite);
    }
}
