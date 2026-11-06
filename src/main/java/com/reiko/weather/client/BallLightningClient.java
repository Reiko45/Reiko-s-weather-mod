package com.reiko.weather.client;

import com.reiko.weather.client.sound.BallLightningSound;
import com.reiko.weather.entity.BallLightningEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Client side of ball lightning: sparks and its crackling sound. */
public final class BallLightningClient {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Set<BallLightningEntity> SOUNDING = Collections.newSetFromMap(new WeakHashMap<>());

    public static void tick(BallLightningEntity ball) {
        Minecraft mc = Minecraft.getInstance();
        if (SOUNDING.add(ball)) {
            mc.getSoundManager().play(new BallLightningSound(ball));
        }
        float brightness = ball.brightness(0.0F);
        if (RANDOM.nextFloat() < 0.6F * brightness) {
            double dx = (RANDOM.nextDouble() - 0.5) * 0.7, dy = (RANDOM.nextDouble() - 0.5) * 0.7, dz = (RANDOM.nextDouble() - 0.5) * 0.7;
            ball.level().addParticle(ParticleTypes.ELECTRIC_SPARK, ball.getX() + dx, ball.getY() + 0.3 + dy, ball.getZ() + dz,
                    dx * 0.3, dy * 0.3, dz * 0.3);
        }
        if (RANDOM.nextFloat() < 0.08F * brightness) {
            ball.level().addParticle(ParticleTypes.END_ROD, ball.getX(), ball.getY() + 0.3, ball.getZ(),
                    (RANDOM.nextDouble() - 0.5) * 0.05, 0.02, (RANDOM.nextDouble() - 0.5) * 0.05);
        }
    }

    private BallLightningClient() {}
}
