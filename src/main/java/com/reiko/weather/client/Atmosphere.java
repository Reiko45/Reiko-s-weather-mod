package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/** Small touches: breath in cold air, and leaves still dripping after the rain. */
public final class Atmosphere {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Entity, Boolean> COLD = new WeakHashMap<>();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            return;
        }
        if (ClientConfig.BREATH.get() && level.dimension() != Level.NETHER) {
            breath(mc, level);
        }
        if (ClientConfig.DRIPPING_LEAVES.get()) {
            drip(mc, level);
        }
    }

    private static void breath(Minecraft mc, ClientLevel level) {
        long time = level.getGameTime();
        AABB area = mc.player.getBoundingBox().inflate(20);
        for (Entity entity : level.getEntities((Entity) null, area, e -> e instanceof Player || e instanceof AbstractVillager || e instanceof Animal)) {
            LivingEntity living = (LivingEntity) entity;
            if (!living.isAlive() || living.isInWater() || living.isSpectator() || living.isInvisible()) continue;
            long phase = Math.floorMod(time + entity.getId() * 13L, 70L);
            if (phase == 0 || !COLD.containsKey(entity)) {
                BlockPos eyes = BlockPos.containing(living.getEyePosition());
                COLD.put(entity, Temperature.ambient(level, eyes, ClientWeather.local()) < 3.0F);
            }
            if (phase >= 6 || !COLD.getOrDefault(entity, false)) continue;
            Vec3 look = living.getViewVector(1.0F);
            boolean firstPerson = entity == mc.player && mc.options.getCameraType().isFirstPerson();
            double forward = firstPerson ? 0.55 : 0.3;
            double down = firstPerson ? 0.18 : 0.08;
            Vec3 mouth = living.getEyePosition().add(look.scale(forward)).subtract(0, down, 0);
            level.addParticle(ModParticles.BREATH.get(), mouth.x, mouth.y, mouth.z,
                    look.x * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01, 0.005, look.z * 0.03 + (RANDOM.nextDouble() - 0.5) * 0.01);
        }
    }

    /** After the rain stops, leaves keep dripping for a while. */
    private static void drip(Minecraft mc, ClientLevel level) {
        float wet = ClientWeather.wetness();
        if (wet < 0.1F || ClientWeather.rain() > 0.05F) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int attempts = (int) (30 * wet);
        for (int i = 0; i < attempts; i++) {
            pos.set(cam.x + RANDOM.nextInt(21) - 10, cam.y + RANDOM.nextInt(12) - 3, cam.z + RANDOM.nextInt(21) - 10);
            if (!level.getBlockState(pos).is(BlockTags.LEAVES)) continue;
            pos.move(0, -1, 0);
            if (!level.getBlockState(pos).isAir() || RANDOM.nextInt(3) != 0) continue;
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + RANDOM.nextDouble(), pos.getY() + 0.95, pos.getZ() + RANDOM.nextDouble(), 0, 0, 0);
        }
    }

    public static void reset() {
        COLD.clear();
    }

    private Atmosphere() {}
}
