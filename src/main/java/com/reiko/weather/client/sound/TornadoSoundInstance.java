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
        float strength = tornado.strength();
        switch (tornado.kind()) {
            case TORNADO -> { this.volume = 0.2F + 2.8F * strength; this.pitch = 0.8F + 0.2F * strength; }
            case WATERSPOUT -> { this.volume = 0.2F + 1.6F * strength; this.pitch = 1.0F + 0.15F * strength; }
            case DUST_DEVIL -> { this.volume = 0.1F + 0.35F * strength; this.pitch = 1.7F; }
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
