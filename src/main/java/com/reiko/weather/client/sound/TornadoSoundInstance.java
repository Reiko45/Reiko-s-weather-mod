package com.reiko.weather.client.sound;

import com.reiko.weather.entity.TornadoEntity;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The roar of a tornado, following it around. */
public class TornadoSoundInstance extends AbstractTickableSoundInstance {
    private final TornadoEntity tornado;

    public TornadoSoundInstance(TornadoEntity tornado) {
        super(ModSounds.TORNADO.get(), SoundSource.WEATHER, RandomSource.create());
        this.tornado = tornado;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.x = tornado.getX();
        this.y = tornado.getY() + 4;
        this.z = tornado.getZ();
    }

    @Override
    public void tick() {
        if (tornado.isRemoved()) {
            this.stop();
            return;
        }
        this.x = tornado.getX();
        this.y = tornado.getY() + 4;
        this.z = tornado.getZ();
        this.volume = 0.2F + 2.8F * tornado.strength();
        this.pitch = 0.8F + 0.2F * tornado.strength();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
