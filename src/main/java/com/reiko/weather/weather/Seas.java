package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.IceFloeEntity;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The moods of the sea: rip currents pulling swimmers out from beaches, swell and rogue waves, foam blown up the
 * beach in storms, tidal bores surging up rivers at spring tides, algal blooms in hot, still summers, rafts of
 * pumice from undersea eruptions, beaches that winter storms strip and calm summers build back up, seiches
 * sloshing a lake from shore to shore after a gale, longshore currents carrying swimmers and flotsam along the
 * beach, and upwelling: cold, rich water rising off some coasts when the wind blows offshore.
 */
public final class Seas {
    /** Swimmers caught in a rip current: the way out to sea, and how hard it's pulling. */
    private record Rip(float seaAngle, float strength) {}

    /** A tidal bore running up a river past a player: where it started, which way is upriver, when. */
    private record Bore(double x, double z, float inland, long start) {}

    private static final Map<UUID, Rip> RIPS = new HashMap<>();
    private static final Map<UUID, Long> RIP_WARNED = new HashMap<>();
    private static final Map<UUID, Bore> BORES = new HashMap<>();
    private static final Map<UUID, Long> LAST_BORE_TIDE = new HashMap<>();
    /** A seiche on a lake near a player: when it started, and which way the wind had piled the water up. */
    private record Seiche(long start, float angle) {}
    private static final Map<UUID, Seiche> SEICHES = new HashMap<>();
    private static final Map<UUID, Long> LAST_GALE = new HashMap<>();
    private static final Map<UUID, Float> SHORE = new HashMap<>();

    static void tickPlayer(ServerLevel level, WeatherState state, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        float wind = local.profile().wind * local.intensity();
        if (WeatherConfig.RIP_CURRENTS.get()) ripCurrent(level, player, wind);
        if (WeatherConfig.TIDAL_BORES.get()) bore(level, player);
        if (WeatherConfig.COASTAL_CURRENTS.get()) longshore(level, state, player, wind);
        if (WeatherConfig.SEICHES.get()) seiche(level, state, player, wind);
        if (player.tickCount % 20 != 0) return;

        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (WeatherConfig.SWELL.get()) {
            if (wind > 0.6F) {
                for (int i = 0; i < 2; i++) seaFoam(level, column(level, player, random, 24));
            }
            if (wind + swell(state, player.getX(), player.getZ()) > 0.65F && random.nextInt(400) == 0) rogueWave(level, player);
        }
        if (WeatherConfig.SEASONAL_BEACHES.get() && griefing) {
            Season season = WeatherManager.seasonInfo().season();
            if (season == Season.SUMMER && wind < 0.3F && local.profile().rain < 0.1F) {
                buildBeach(level, column(level, player, random, 32));
            } else if (season == Season.WINTER && wind > 0.45F) {
                stripBeach(level, column(level, player, random, 32));
            }
        }
        if (WeatherConfig.PUMICE_RAFTS.get() && random.nextInt(30000) == 0) {
            pumiceRafts(level, player, random);
        }
        if (WeatherConfig.ALGAL_BLOOMS.get() && state.bloom > 0.5F && random.nextInt(4) == 0) {
            // the bloom starves the water of oxygen, and the fish die off
            for (AbstractFish fish : level.getEntitiesOfClass(AbstractFish.class, player.getBoundingBox().inflate(32), f -> blooming(level, f.blockPosition()))) {
                if (random.nextInt(40) == 0) fish.hurt(level.damageSources().drown(), 2.0F);
            }
        }
    }

    // ------------------------------------------------------------------ seiches

    /**
     * A seiche: a long gale piles a lake's water up against the far shore, and when the wind drops the whole lake
     * sloshes back and forth like water in a bath, flooding each shore in turn for a few minutes.
     */
    private static void seiche(ServerLevel level, WeatherState state, ServerPlayer player, float wind) {
        long now = level.getGameTime();
        UUID id = player.getUUID();
        if (wind > 0.6F) LAST_GALE.put(id, now);
        Seiche s = SEICHES.get(id);
        if (s == null) {
            Long gale = LAST_GALE.get(id);
            if (gale != null && wind < 0.35F && now - gale < 1200 && now - gale > 100) {
                LAST_GALE.remove(id);
                if (nearLake(level, player)) {
                    SEICHES.put(id, new Seiche(now, state.windAngle));
                    player.displayClientMessage(Component.translatable("message.reikos_weather.seiche").withStyle(st -> st.withColor(0x88C8E8)), true);
                }
            }
            return;
        }
        long t = now - s.start;
        if (t > 3600) {
            SEICHES.remove(id);
            return;
        }
        if (t % 10 != 0 || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        // a minute from one shore to the other and back, dying away
        float phase = Mth.sin(t / 1200.0F * Mth.TWO_PI) * (1.0F - t / 3600.0F);
        if (Math.abs(phase) < 0.45F) return;
        float angle = phase > 0 ? s.angle : s.angle + Mth.PI;
        Vec3 toShore = Wind.direction(angle);
        RandomSource random = level.random;
        for (int i = 0; i < 6; i++) {
            BlockPos top = column(level, player, random, 40);
            if (top == null) continue;
            surge(level, player, top, toShore);
        }
    }

    /** The lake's water running up the beach on the shore it's sloshing toward. */
    private static void surge(ServerLevel level, ServerPlayer player, BlockPos top, Vec3 toShore) {
        BlockState ground = level.getBlockState(top.below());
        if (!level.getBlockState(top).isAir() || !ground.isFaceSturdy(level, top.below(), Direction.UP) || ground.getFluidState().is(FluidTags.WATER)) return;
        // the lake has to be right there, on the side the water is coming from, one block down
        BlockPos lake = BlockPos.containing(top.getX() - Math.round(toShore.x), top.getY() - 1, top.getZ() - Math.round(toShore.z));
        if (!level.getFluidState(lake).isSource() || !level.getFluidState(lake).is(FluidTags.WATER) || !level.getBlockState(lake.above()).isAir()) return;
        var biome = level.getBiome(lake);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) return;
        // a little flowing water with no source: it runs up the shore and drains away again by itself
        level.setBlockAndUpdate(top, net.minecraft.world.level.material.Fluids.FLOWING_WATER.getFlowing(6, false).createLegacyBlock());
        if (player.distanceToSqr(Vec3.atCenterOf(top)) < 20 * 20) WeatherAdvancements.award(player, "seiche");
    }

    private static boolean nearLake(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            for (int d = 6; d <= 30; d += 8) {
                BlockPos c = BlockPos.containing(player.getX() + Mth.cos(a) * d, 0, player.getZ() + Mth.sin(a) * d);
                if (!level.isLoaded(c)) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c).below();
                if (!level.getFluidState(top).is(FluidTags.WATER)) continue;
                var biome = level.getBiome(top);
                if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ longshore currents and upwelling

    /**
     * Longshore drift: waves coming in at an angle to a beach drive a current along it, inside the surf. Swimmers,
     * boats and anything floating in the shallows are carried slowly along the shore, the way the wind is slanting.
     */
    private static void longshore(ServerLevel level, WeatherState state, ServerPlayer player, float wind) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD || wind < 0.15F) return;
        UUID id = player.getUUID();
        if (player.tickCount % 40 == 0 || !SHORE.containsKey(id)) {
            SHORE.put(id, level.getBiome(player.blockPosition()).is(BiomeTags.IS_BEACH) || level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN)
                    ? seaward(level, player.getX(), player.getZ(), 12) : Float.NaN);
        }
        float sea = SHORE.get(id);
        if (Float.isNaN(sea)) return;
        Vec3 w = Wind.direction(state.windAngle);
        double tx = -Mth.sin(sea), tz = Mth.cos(sea);
        double along = w.x * tx + w.z * tz;
        if (Math.abs(along) < 0.2) return;
        double push = Math.signum(along) * 0.008 * (0.4 + wind) * Math.min(1.0, Math.abs(along) * 1.5);
        int sl = level.getSeaLevel();
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(20, 4, 20), e -> e.isInWater() && !e.isSpectator()
                && e.getY() > sl - 4 && e.getY() < sl + 1 && !(e instanceof com.reiko.weather.entity.IceFloeEntity))) {
            e.setDeltaMovement(e.getDeltaMovement().add(tx * push, 0.0, tz * push));
        }
        // players are moved on their own side, so nudge them less often, a little harder
        if (player.isInWater() && player.getY() > sl - 4 && player.tickCount % 4 == 0) {
            player.setDeltaMovement(player.getDeltaMovement().add(tx * push * 4.0, 0.0, tz * push * 4.0));
            player.hurtMarked = true;
        }
    }

    /**
     * Upwelling: on some coasts, when the wind blows steadily offshore in spring and summer, it pushes the warm
     * surface water out to sea and cold, nutrient-rich water rises from the deep to replace it. The air along the
     * shore turns cool, sea fog forms readily over the cold water, and the fishing is excellent. 0 to 1.
     */
    public static float upwelling(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!WeatherConfig.SPEC.isLoaded() || !WeatherConfig.UPWELLING.get() || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return 0.0F;
        var biome = level.getBiome(pos);
        if (!biome.is(BiomeTags.IS_BEACH) && !biome.is(BiomeTags.IS_OCEAN) || biome.is(Biomes.WARM_OCEAN) || biome.value().getBaseTemperature() < 0.2F) return 0.0F;
        // only some stretches of coast (where the shape of the sea floor lets it happen)
        int h = Mth.murmurHash3Mixer((pos.getX() >> 8) * 73856093 ^ (pos.getZ() >> 8) * 19349663);
        if ((h & 1023) >= 420) return 0.0F;
        Season season = level.isClientSide ? com.reiko.weather.network.SyncedWeather.season() : WeatherManager.seasonInfo().season();
        if (season == Season.AUTUMN || season == Season.WINTER) return 0.0F;
        float sea = seaward(level, pos.getX() + 0.5, pos.getZ() + 0.5, 16);
        if (Float.isNaN(sea)) return 0.0F;
        float angle;
        if (level.isClientSide) {
            angle = com.reiko.weather.network.SyncedWeather.windAngle();
        } else {
            WeatherState state = WeatherManager.activeState(level);
            if (state == null) return 0.0F;
            angle = state.windAngle;
        }
        Vec3 w = Wind.direction(angle);
        double offshore = w.x * Mth.cos(sea) + w.z * Mth.sin(sea);
        return Mth.clamp((float) (offshore - 0.3) / 0.5F, 0.0F, 1.0F);
    }

    /** Once a second for the world: the algal bloom spreading or dying back. */
    static void tickWorld(ServerLevel level, WeatherState state) {
        if (!WeatherConfig.ALGAL_BLOOMS.get()) {
            state.bloom = 0.0F;
            return;
        }
        Season season = WeatherManager.seasonInfo().season();
        WeatherType type = state.type;
        float before = state.bloom;
        float change;
        if (season == Season.SUMMER && (type == WeatherType.HEATWAVE || type == WeatherType.CLEAR)) {
            change = type == WeatherType.HEATWAVE ? 0.0009F : 0.0003F; // hot, still, sunny weather feeds it
        } else if (type.vanillaRain || type == WeatherType.WINDY || type.isSevere()) {
            change = -0.002F; // storms stir the water up and break it up
        } else {
            change = season == Season.SUMMER ? -0.0001F : -0.0008F;
        }
        state.bloom = Mth.clamp(state.bloom + change, 0.0F, 1.0F);
        if (Math.abs(state.bloom - before) > 1.0E-6F) {
            state.setDirty();
            if ((int) (state.bloom * 20) != (int) (before * 20)) WeatherManager.sync(level, state);
        }
    }

    /** Whether the water at a spot is warm sea, where algal blooms take hold. */
    public static boolean blooming(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.is(BiomeTags.IS_BEACH) && biome.value().getBaseTemperature() >= 0.8F;
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random, int area) {
        int x = player.getBlockX() + random.nextInt(area * 2 + 1) - area, z = player.getBlockZ() + random.nextInt(area * 2 + 1) - area;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /** How big the swell running ahead of the hurricanes is here, 0 to about 0.5. */
    public static float swell(WeatherState state, double x, double z) {
        float swell = 0.0F;
        for (StormCell cell : state.cells) {
            if (cell.type != WeatherType.HURRICANE) continue;
            double d = cell.distanceTo(x, z);
            if (d < 900) swell = Math.max(swell, 0.5F * cell.strength() * (float) (1.0 - d / 900));
        }
        return swell;
    }

    /**
     * Which way the open sea lies from a spot just off a beach (radians), or NaN if it isn't near a shore: some of
     * the ground round about has to be dry land and some open water.
     */
    private static float seaward(net.minecraft.world.level.Level level, double px, double pz, int distance) {
        int sea = level.getSeaLevel(), wet = 0, dry = 0;
        float sx = 0, sz = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int x = Mth.floor(px + Mth.cos(a) * distance), z = Mth.floor(pz + Mth.sin(a) * distance);
            if (!level.isLoaded(new BlockPos(x, 0, z))) return Float.NaN;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (level.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER) && top <= sea) {
                wet++;
                sx += Mth.cos(a);
                sz += Mth.sin(a);
            } else if (top > sea) {
                dry++;
            }
        }
        return wet >= 3 && dry >= 1 && dry <= 4 ? (float) Mth.atan2(sz, sx) : Float.NaN;
    }

    // ------------------------------------------------------------------ rip currents

    /**
     * Rip currents: the water piled up on a beach by the surf drains back out to sea in narrow, fast channels a few
     * blocks wide, spaced out along the shore. One will carry a swimmer straight out; swimming along the shore gets
     * you out of it.
     */
    private static void ripCurrent(ServerLevel level, ServerPlayer player, float wind) {
        UUID id = player.getUUID();
        if (!player.isInWater() || player.getVehicle() != null || player.isSpectator() || player.getAbilities().flying) {
            RIPS.remove(id);
            return;
        }
        if (player.tickCount % 20 == 0) {
            RIPS.remove(id);
            BlockPos pos = player.blockPosition();
            var biome = level.getBiome(pos);
            if ((biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN)) && Math.abs(pos.getY() - level.getSeaLevel()) < 4) {
                float a = seaward(level, player.getX(), player.getZ(), 18);
                if (!Float.isNaN(a)) {
                    // rips sit every 30-odd blocks along the shore, and come and go from day to day
                    double along = -player.getX() * Mth.sin(a) + player.getZ() * Mth.cos(a);
                    long bucket = Math.floorDiv((long) along, 34L);
                    long day = level.getDayTime() / 24000L;
                    int h = Mth.murmurHash3Mixer((int) (bucket * 0x9E3779B9L + day * 31 + level.getSeed()));
                    double centre = (bucket + 0.5) * 34.0 + ((h >>> 4) & 7) - 3.5;
                    float surf = 0.35F + Math.min(0.65F, wind + swell(WeatherManager.state(level), player.getX(), player.getZ()));
                    if ((h & 0xFF) < 90 && Math.abs(along - centre) < 2.0 + surf * 1.5) {
                        RIPS.put(id, new Rip(a, surf));
                    }
                }
            }
        }
        Rip rip = RIPS.get(id);
        if (rip == null) return;
        if (player.tickCount % 2 == 0) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.add(Mth.cos(rip.seaAngle) * 0.03 * rip.strength, 0.0, Mth.sin(rip.seaAngle) * 0.03 * rip.strength));
            player.hurtMarked = true;
        }
        long now = level.getGameTime();
        if (now - RIP_WARNED.getOrDefault(id, Long.MIN_VALUE / 2) > 1200) {
            RIP_WARNED.put(id, now);
            player.displayClientMessage(Component.translatable("message.reikos_weather.rip_current").withStyle(s -> s.withColor(0x55AAFF).withBold(true)), true);
            WeatherAdvancements.award(player, "rip_current");
        }
        if (player.tickCount % 4 == 0) {
            level.sendParticles(player, ParticleTypes.BUBBLE_POP, true, player.getX(), level.getSeaLevel() + 0.05, player.getZ(), 4, 1.5, 0.0, 1.5, 0.02);
        }
    }

    // ------------------------------------------------------------------ surf

    /** Storm waves whip up foam and throw it up the beach. */
    private static void seaFoam(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        int sea = level.getSeaLevel();
        if (top.getY() < sea || top.getY() > sea + 2 || !level.getBlockState(top).isAir()) return;
        var below = level.getBlockState(top.below());
        if (!below.is(BlockTags.SAND) && !below.is(Blocks.GRAVEL)) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 3; k++) {
                if (level.getFluidState(top.below().relative(d, k)).is(FluidTags.WATER)) {
                    level.setBlockAndUpdate(top, ModBlocks.SEA_FOAM.get().defaultBlockState());
                    return;
                }
            }
        }
    }

    /** A rogue wave: now and then in a big sea, a wave far bigger than the rest comes out of nowhere. */
    private static void rogueWave(ServerLevel level, ServerPlayer player) {
        Entity riding = player.getVehicle();
        boolean afloat = riding instanceof Boat || player.isInWater();
        if (!afloat || !level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN)) return;
        float a = WeatherManager.state(level).windAngle;
        Vec3 push = new Vec3(Mth.cos(a) * 1.2, 0.6, Mth.sin(a) * 1.2);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.WEATHER, 3.0F, 0.5F);
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.5, player.getZ(), 120, 3.0, 1.5, 3.0, 0.3);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2.5, player.getZ(), 30, 3.0, 1.0, 3.0, 0.05);
        player.displayClientMessage(Component.translatable("message.reikos_weather.rogue_wave").withStyle(s -> s.withColor(0x55AAFF).withBold(true)), true);
        if (riding instanceof Boat boat) {
            boat.setDeltaMovement(boat.getDeltaMovement().add(push));
            boat.hurtMarked = true;
            if (level.random.nextInt(3) == 0) boat.ejectPassengers(); // capsized
        }
        player.setDeltaMovement(player.getDeltaMovement().add(push));
        player.hurtMarked = true;
    }

    // ------------------------------------------------------------------ beaches through the year

    /** Calm summer swell carries sand back up the beach, building it out a little at the water's edge. */
    private static void buildBeach(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        int sea = level.getSeaLevel();
        BlockPos water = top.below();
        if (water.getY() != sea - 1 || !level.getFluidState(water).is(FluidTags.WATER) || !level.getBlockState(water.below()).is(Blocks.SAND)
                || !level.getBiome(water).is(BiomeTags.IS_BEACH)) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = water.relative(d);
            if (level.getBlockState(n).is(Blocks.SAND) && level.getBlockState(n.above()).isAir()) {
                level.setBlockAndUpdate(water, Blocks.SAND.defaultBlockState());
                return;
            }
        }
    }

    /** Winter storms drag sand off the beach face, cutting it back. */
    private static void stripBeach(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        int sea = level.getSeaLevel();
        BlockPos sand = top.below();
        if (sand.getY() != sea - 1 || !level.getBlockState(sand).is(Blocks.SAND) || !level.getBlockState(top).isAir()
                || !level.getBiome(sand).is(BiomeTags.IS_BEACH)) return;
        boolean wet = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = sand.relative(d);
            if (level.getFluidState(n).is(FluidTags.WATER)) wet = true;
            else if (!level.getBlockState(n).isFaceSturdy(level, n, d.getOpposite())) return; // the water would spill inland
        }
        if (wet) level.setBlockAndUpdate(sand, Blocks.WATER.defaultBlockState());
    }

    // ------------------------------------------------------------------ pumice

    /** An undersea eruption somewhere sets a raft of pumice adrift: a scatter of floating stone across the sea. */
    static void pumiceRafts(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (!WeatherConfig.PUMICE_RAFTS.get()) return;
        int placed = 0;
        int sea = level.getSeaLevel();
        float a = random.nextFloat() * Mth.TWO_PI;
        double cx = player.getX() + Mth.cos(a) * 60, cz = player.getZ() + Mth.sin(a) * 60;
        for (int i = 0; i < 40 && placed < 10; i++) {
            int x = Mth.floor(cx + random.nextGaussian() * 14), z = Mth.floor(cz + random.nextGaussian() * 14);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            BlockPos surface = new BlockPos(x, sea - 1, z);
            if (!level.getFluidState(surface).is(FluidTags.WATER) || !level.getFluidState(surface.below(3)).is(FluidTags.WATER)
                    || !level.getBlockState(surface.above()).isAir() || !level.getBiome(surface).is(BiomeTags.IS_OCEAN)) continue;
            IceFloeEntity.spawnPumice(level, surface);
            placed++;
        }
    }

    // ------------------------------------------------------------------ tidal bores

    /**
     * A tidal bore: on the biggest tides, where a river meets the sea, the incoming tide can't spread out fast
     * enough and runs up the river as a single breaking wave, against the current.
     */
    private static void bore(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        long now = level.getGameTime();
        Bore bore = BORES.get(id);
        if (bore == null) {
            if (player.tickCount % 20 != 0 || Tides.springness(level) < 1.0F) return;
            long time = level.getDayTime();
            float tide = Tides.cycle(time);
            if (tide > -0.3F || tide < -0.9F || Tides.cycle(time + 200L) <= tide) return; // only as the tide starts to rise
            long tideIndex = Math.floorDiv(time + 6000L, 12000L);
            if (LAST_BORE_TIDE.getOrDefault(id, Long.MIN_VALUE) == tideIndex) return;
            BlockPos pos = player.blockPosition();
            if (!level.getBiome(pos).is(BiomeTags.IS_RIVER) || Math.abs(pos.getY() - level.getSeaLevel()) > 6) return;
            // the sea has to be close by downriver
            float sx = 0, sz = 0;
            int ocean = 0;
            for (int i = 0; i < 8; i++) {
                float a = i * Mth.TWO_PI / 8.0F;
                BlockPos p = BlockPos.containing(player.getX() + Mth.cos(a) * 96, level.getSeaLevel(), player.getZ() + Mth.sin(a) * 96);
                if (level.isLoaded(p) && level.getBiome(p).is(BiomeTags.IS_OCEAN)) {
                    ocean++;
                    sx += Mth.cos(a);
                    sz += Mth.sin(a);
                }
            }
            if (ocean == 0 || ocean > 4) return;
            LAST_BORE_TIDE.put(id, tideIndex);
            float inland = (float) Mth.atan2(-sz, -sx);
            BORES.put(id, new Bore(player.getX(), player.getZ(), inland, now));
            player.displayClientMessage(Component.translatable("message.reikos_weather.tidal_bore").withStyle(s -> s.withColor(0x55AAFF).withBold(true)), true);
            return;
        }
        float along = -70.0F + (now - bore.start) * 0.25F; // the wave front, about 5 blocks a second
        if (along > 50.0F) {
            BORES.remove(id);
            return;
        }
        double ix = Mth.cos(bore.inland), iz = Mth.sin(bore.inland);
        double fx = bore.x + ix * along, fz = bore.z + iz * along;
        double surface = level.getSeaLevel() - 0.1;
        if (now % 2L == 0L) {
            // the breaking front, right across the river
            for (int l = -18; l <= 18; l += 2) {
                double px = fx - iz * l, pz = fz + ix * l;
                BlockPos at = BlockPos.containing(px, surface, pz);
                if (!level.getFluidState(at).is(FluidTags.WATER)) continue;
                level.sendParticles(player, ParticleTypes.SPLASH, true, px, surface + 1.0, pz, 3, 0.4, 0.3, 0.4, 0.2);
                if (level.random.nextInt(3) == 0) level.sendParticles(player, ParticleTypes.CLOUD, true, px, surface + 1.2, pz, 1, 0.3, 0.2, 0.3, 0.02);
            }
        }
        if (now % 20L == 0L) {
            level.playSound(null, fx, surface, fz, ModSounds.TIDAL_BORE.get(), SoundSource.WEATHER, 3.0F, 0.9F + level.random.nextFloat() * 0.2F);
        }
        // anything afloat in the front gets swept upriver
        AABB box = new AABB(fx - 20, surface - 3, fz - 20, fx + 20, surface + 3, fz + 20);
        for (Entity e : level.getEntities((Entity) null, box, e -> e.isInWater() || e instanceof Boat)) {
            double u = (e.getX() - bore.x) * ix + (e.getZ() - bore.z) * iz;
            if (Math.abs(u - along) > 3.0) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(ix * 0.12, 0.04, iz * 0.12));
            e.hurtMarked = true;
            if (e instanceof Boat boat) {
                for (Entity rider : boat.getPassengers()) {
                    if (rider instanceof ServerPlayer p) WeatherAdvancements.award(p, "tidal_bore");
                }
            }
        }
    }

    public static void reset() {
        SEICHES.clear();
        LAST_GALE.clear();
        SHORE.clear();
        RIPS.clear();
        RIP_WARNED.clear();
        BORES.clear();
        LAST_BORE_TIDE.clear();
    }

    private Seas() {}
}
