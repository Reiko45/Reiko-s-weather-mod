package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** On bitterly cold, clear, still mornings the air fills with glittering ice crystals ("diamond dust"). */
public final class DiamondDust {
    private static final RandomSource RANDOM = RandomSource.create();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        float strength = ClientWeather.diamondDust();
        if (level == null || mc.player == null || strength < 0.05F) {
            return;
        }
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 wind = Wind.direction(ClientWeather.windAngle());
        float wanted = strength * 9.0F * density;
        int count = (int) wanted + (RANDOM.nextFloat() < wanted - (int) wanted ? 1 : 0);
        for (int i = 0; i < count; i++) {
            double x = cam.x + (RANDOM.nextDouble() - 0.5) * 24.0, y = cam.y - 2.0 + RANDOM.nextDouble() * 8.0, z = cam.z + (RANDOM.nextDouble() - 0.5) * 24.0;
            if (!level.canSeeSky(BlockPos.containing(x, y, z))) continue;
            level.addParticle(ModParticles.DIAMOND_DUST.get(), x, y, z, wind.x * 0.015, -0.004, wind.z * 0.015);
        }
    }

    private DiamondDust() {}
}
