package com.reiko.weather.client.sound;

import com.reiko.weather.entity.BallLightningEntity;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The crackle and hum that follows a ball of lightning around. */
public class BallLightningSound extends AbstractTickableSoundInstance {
    private final BallLightningEntity ball;

    public BallLightningSound(BallLightningEntity ball) {
        super(ModSounds.BALL_LIGHTNING.get(), SoundSource.WEATHER, RandomSource.create());
        this.ball = ball;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.x = ball.getX();
        this.y = ball.getY();
        this.z = ball.getZ();
    }

    @Override
    public void tick() {
        if (ball.isRemoved()) {
            stop();
            return;
        }
        this.x = ball.getX();
        this.y = ball.getY();
        this.z = ball.getZ();
        this.volume = 0.9F * ball.brightness(0.0F);
        this.pitch = 0.9F + random.nextFloat() * 0.2F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
