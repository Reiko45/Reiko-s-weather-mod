package com.reiko.weather.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.function.DoubleSupplier;

/** A looping, non-positional ambience bed whose volume and pitch follow live suppliers. */
public class AmbientLoop extends AbstractTickableSoundInstance {
    private final DoubleSupplier targetVolume;
    private final DoubleSupplier targetPitch;
    private int silentTicks;

    public AmbientLoop(SoundEvent sound, DoubleSupplier targetVolume, DoubleSupplier targetPitch) {
        super(sound, SoundSource.WEATHER, RandomSource.create());
        this.targetVolume = targetVolume;
        this.targetPitch = targetPitch;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.01F;
        this.pitch = (float) targetPitch.getAsDouble();
    }

    @Override
    public void tick() {
        float target = (float) targetVolume.getAsDouble();
        this.volume = Mth.lerp(0.06F, this.volume, Math.max(0.001F, target));
        this.pitch = Mth.lerp(0.05F, this.pitch, (float) targetPitch.getAsDouble());
        if (target < 0.01F) {
            if (++silentTicks > 60) {
                this.stop();
            }
        } else {
            silentTicks = 0;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
