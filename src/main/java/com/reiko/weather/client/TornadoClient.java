package com.reiko.weather.client;

import com.reiko.weather.client.particle.FunnelParticle;
import com.reiko.weather.client.sound.TornadoSoundInstance;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Client half of the tornado: the funnel cloud, ground debris, its roar, and pulling in the local player. */
public final class TornadoClient {
    private static final Set<TornadoEntity> SOUNDING = Collections.newSetFromMap(new WeakHashMap<>());

    public static void tick(TornadoEntity tornado) {
        Minecraft mc = Minecraft.getInstance();
        if (!(tornado.level() instanceof ClientLevel level) || mc.isPaused()) {
            return;
        }
        float strength = tornado.strength();
        if (FunnelParticle.sprites != null) {
            float density = (float) Math.max(0.35, ClientConfig.PARTICLE_DENSITY.get());
            int base = switch (tornado.kind()) {
                case TORNADO -> 26; case WATERSPOUT -> 20; case DUST_DEVIL -> 9; case FIRE_WHIRL -> 14;
                case LANDSPOUT -> 16; case GUSTNADO -> 10; case STEAM_DEVIL -> 7; case SNOW_DEVIL -> 9;
            };
            int count = Mth.ceil(base * density * (0.3F + 0.7F * strength));
            for (int i = 0; i < count; i++) {
                float t = level.random.nextFloat();
                float height = t * t * tornado.height() * 0.9F;
                float angle = level.random.nextFloat() * Mth.TWO_PI;
                mc.particleEngine.add(new FunnelParticle(level, tornado, angle, height, FunnelParticle.sprites));
            }
        }
        spawnDebris(level, tornado, strength);

        if (ClientConfig.SOUNDS.get() && !SOUNDING.contains(tornado)) {
            SOUNDING.add(tornado);
            mc.getSoundManager().play(new TornadoSoundInstance(tornado));
        }

        LocalPlayer player = mc.player;
        if (player != null && !player.isSpectator() && !player.getAbilities().flying && strength > 0.05F) {
            Vec3 velocity = TornadoEntity.suction(tornado, player, strength);
            if (velocity != null) {
                player.setDeltaMovement(velocity);
            }
        }
    }

    private static void spawnDebris(ClientLevel level, TornadoEntity tornado, float strength) {
        float radius = tornado.radiusAt(0) * 2.0F + 2.0F;
        for (int i = 0; i < 4 * strength; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = level.random.nextDouble() * radius;
            double x = tornado.getX() + Math.cos(angle) * r;
            double z = tornado.getZ() + Math.sin(angle) * r;
            // clients only receive the MOTION_BLOCKING heightmap
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            BlockState ground = level.getBlockState(BlockPos.containing(x, top - 1, z));
            double tx = -Math.sin(angle) * 0.6, tz = Math.cos(angle) * 0.6;
            if (tornado.kind() == TornadoEntity.Kind.WATERSPOUT) {
                level.addParticle(level.random.nextBoolean() ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, x, top + 0.2, z, tx * 0.6, 0.3, tz * 0.6);
                continue;
            }
            if (tornado.kind() == TornadoEntity.Kind.FIRE_WHIRL) {
                level.addParticle(level.random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, x, top + 0.2 + level.random.nextDouble() * 3, z,
                        tx * 0.3, 0.1 + level.random.nextDouble() * 0.15, tz * 0.3);
                continue;
            }
            if (tornado.kind() == TornadoEntity.Kind.STEAM_DEVIL) {
                level.addParticle(ModParticles.STEAM_FOG.get(), x, top + 0.1, z, tx * 0.2, 0.05, tz * 0.2);
                continue;
            }
            if (tornado.kind() == TornadoEntity.Kind.SNOW_DEVIL) {
                level.addParticle(ModParticles.SNOWFLAKE.get(), x, top + 0.2 + level.random.nextDouble(), z, tx * 0.5, 0.15, tz * 0.5);
                continue;
            }
            if (tornado.kind() == TornadoEntity.Kind.GUSTNADO) {
                level.addParticle(ModParticles.DUST.get(), x, top + 0.2 + level.random.nextDouble(), z, tx * 0.6, 0.12, tz * 0.6);
                continue;
            }
            if (tornado.kind() == TornadoEntity.Kind.DUST_DEVIL) {
                level.addParticle(ModParticles.SAND.get(), x, top + 0.2 + level.random.nextDouble(), z, tx * 0.5, 0.15, tz * 0.5);
                continue;
            }
            if (!ground.isAir() && level.random.nextBoolean()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, top + 0.2, z, tx, 0.5 + level.random.nextDouble() * 0.5, tz);
            } else {
                level.addParticle(ParticleTypes.CLOUD, x, top + 0.3, z, tx * 0.5, 0.15, tz * 0.5);
            }
        }
    }

    private TornadoClient() {}
}
