package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.Precip;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.client.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Spawns the particle precipitation around the camera every tick. */
public final class WeatherParticleSpawner {
    private static final RandomSource RANDOM = RandomSource.create();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !ClientWeather.active()) {
            return;
        }
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.DECREASED) density *= 0.6F;
        else if (status == ParticleStatus.MINIMAL) density *= 0.25F;
        if (density <= 0.0F) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (Precip precip : Precip.values()) {
            float rate = ClientWeather.precipRate(precip);
            if (precip == Precip.NONE || rate < 0.01F) {
                continue;
            }
            float wanted = rate * baseCount(precip) * density;
            int count = (int) wanted + (RANDOM.nextFloat() < wanted - (int) wanted ? 1 : 0);
            for (int i = 0; i < count; i++) {
                spawn(level, precip, camera);
            }
            if (precip == Precip.HAIL && ClientConfig.SOUNDS.get() && RANDOM.nextFloat() < rate * 0.35F) {
                playHailSound(level, camera, rate);
            }
        }
        spawnRipples(level, camera, density);
        spawnMist(level, camera, density);
    }

    /** Raindrops ringing out on nearby water. */
    private static void spawnRipples(ClientLevel level, Vec3 camera, float density) {
        float rain = ClientWeather.rain();
        if (rain < 0.2F || !ClientConfig.WET_GROUND.get()) {
            return;
        }
        float wanted = rain * 10.0F * density;
        for (int i = 0; i < wanted; i++) {
            int x = (int) Math.floor(camera.x + (RANDOM.nextDouble() * 2 - 1) * 14);
            int z = (int) Math.floor(camera.z + (RANDOM.nextDouble() * 2 - 1) * 14);
            BlockPos water = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            FluidState fluid = level.getFluidState(water);
            if (fluid.is(FluidTags.WATER) && Math.abs(water.getY() - camera.y) < 16
                    && level.getBiome(water).value().getPrecipitationAt(water) == Biome.Precipitation.RAIN) {
                double y = water.getY() + fluid.getHeight(level, water) + 0.01;
                level.addParticle(ModParticles.RIPPLE.get(), x + RANDOM.nextDouble(), y, z + RANDOM.nextDouble(), 0, 0, 0);
            }
        }
    }

    /** Low banks of morning mist. */
    private static void spawnMist(ClientLevel level, Vec3 camera, float density) {
        float mist = ClientWeather.mist();
        if (mist < 0.15F || RANDOM.nextFloat() > mist * density * 0.6F) {
            return;
        }
        double x = camera.x + (RANDOM.nextDouble() * 2 - 1) * 18;
        double z = camera.z + (RANDOM.nextDouble() * 2 - 1) * 18;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        if (Math.abs(top - camera.y) < 12) {
            level.addParticle(ModParticles.MIST.get(), x, top + 0.2 + RANDOM.nextDouble() * 1.2, z, 0, 0, 0);
        }
    }

    private static int baseCount(Precip precip) {
        return switch (precip) {
            case HAIL -> 16;
            case SLEET -> 14;
            case DOWNPOUR -> 24;
            case SNOW -> 10;
            case BLIZZARD -> 28;
            case SAND -> 26;
            case DUST -> 4;
            case LEAVES -> 2;
            case ASH -> 22;
            case NONE -> 0;
        };
    }

    private static void spawn(ClientLevel level, Precip precip, Vec3 camera) {
        float wind = ClientWeather.wind();
        Vec3 windDir = Wind.direction(ClientWeather.windAngle());
        boolean falling = precip == Precip.HAIL || precip == Precip.SLEET || precip == Precip.DOWNPOUR || precip == Precip.SNOW || precip == Precip.ASH;
        double radius = falling ? 14.0 : 20.0;
        // spawn upwind so particles blow into view
        double upwind = falling ? wind * 6.0 : 6.0 + wind * 6.0;
        double x = camera.x + (RANDOM.nextDouble() * 2 - 1) * radius - windDir.x * upwind;
        double z = camera.z + (RANDOM.nextDouble() * 2 - 1) * radius - windDir.z * upwind;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double y;
        if (falling || precip == Precip.BLIZZARD && RANDOM.nextBoolean()) {
            y = camera.y + 6.0 + RANDOM.nextDouble() * 10.0;
            if (y < top) {
                return; // would start inside terrain or above a roof we can't see past
            }
        } else if (precip == Precip.DUST) {
            // dust clouds roll along the ground
            if (top > camera.y + 8.0 || top < camera.y - 24.0) {
                return;
            }
            y = top + RANDOM.nextDouble() * 2.5;
        } else {
            y = camera.y - 3.0 + RANDOM.nextDouble() * 10.0;
            if (y < top) {
                if (top > camera.y + 8.0) {
                    return;
                }
                y = top + RANDOM.nextDouble() * 2.0;
            }
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        Biome.Precipitation columnPrecip = level.getBiome(pos).value().getPrecipitationAt(pos);
        switch (precip) {
            case DOWNPOUR -> { if (columnPrecip != Biome.Precipitation.RAIN) return; }
            case SNOW, BLIZZARD -> { if (columnPrecip != Biome.Precipitation.SNOW) return; }
            case HAIL, SLEET -> { if (columnPrecip == Biome.Precipitation.NONE) return; }
            default -> {}
        }

        SimpleParticleType type = switch (precip) {
            case HAIL -> ModParticles.HAIL.get();
            case SLEET -> ModParticles.SLEET.get();
            case DOWNPOUR -> ModParticles.DOWNPOUR.get();
            case SNOW -> ModParticles.SNOWFLAKE.get();
            case BLIZZARD -> ModParticles.BLIZZARD.get();
            case SAND -> ModParticles.SAND.get();
            case DUST -> ModParticles.DUST.get();
            case LEAVES -> ModParticles.LEAF.get();
            case ASH -> ModParticles.ASH.get();
            case NONE -> null;
        };
        if (type == null) {
            return;
        }
        double speed = 0.05 + wind * 0.9;
        double vx = windDir.x * speed, vz = windDir.z * speed;
        double vy = switch (precip) {
            case HAIL -> -0.9 - RANDOM.nextDouble() * 0.4;
            case SLEET -> -0.75 - RANDOM.nextDouble() * 0.2;
            case DOWNPOUR -> -1.1 - RANDOM.nextDouble() * 0.3;
            case SNOW -> -0.08;
            case BLIZZARD -> -0.06;
            case ASH -> -0.07 - RANDOM.nextDouble() * 0.03;
            default -> 0.0;
        };
        if (precip == Precip.BLIZZARD) {
            vx *= 0.85;
            vz *= 0.85;
        } else if (precip == Precip.SNOW) {
            vx *= 0.25;
            vz *= 0.25;
        } else if (precip == Precip.ASH) {
            vx *= 0.4;
            vz *= 0.4;
        }
        level.addParticle(type, x, y, z, vx, vy, vz);
    }

    private static void playHailSound(ClientLevel level, Vec3 camera, float rate) {
        double x = camera.x + (RANDOM.nextDouble() * 2 - 1) * 8.0;
        double z = camera.z + (RANDOM.nextDouble() * 2 - 1) * 8.0;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        if (Math.abs(top - camera.y) > 12) {
            return;
        }
        float exposed = top <= camera.y + 1 ? 1.0F : 0.5F;
        level.playLocalSound(x, top, z, ModSounds.HAIL.get(), SoundSource.WEATHER,
                (0.15F + 0.25F * rate) * exposed, 0.85F + RANDOM.nextFloat() * 0.3F, false);
    }

    private WeatherParticleSpawner() {}
}
