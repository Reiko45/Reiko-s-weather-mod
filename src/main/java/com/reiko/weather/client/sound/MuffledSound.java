package com.reiko.weather.client.sound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Wraps a one-shot sound to play it quieter and lower, as if heard through walls. "Global" sounds
 * (thunder is played at volume 10000 so it reaches the whole map) become non-positional so that
 * turning their volume down does not also shrink how far they carry.
 */
public class MuffledSound implements SoundInstance {
    private final SoundInstance delegate;
    private final float volumeScale;
    private final float pitchScale;
    private final boolean global;

    public MuffledSound(SoundInstance delegate, float volumeScale, float pitchScale, boolean global) {
        this.delegate = delegate;
        this.volumeScale = volumeScale;
        this.pitchScale = pitchScale;
        this.global = global;
    }

    @Override public ResourceLocation getLocation() { return delegate.getLocation(); }
    @Override public WeighedSoundEvents resolve(SoundManager manager) { return delegate.resolve(manager); }
    @Override public Sound getSound() { return delegate.getSound(); }
    @Override public SoundSource getSource() { return delegate.getSource(); }
    @Override public boolean isLooping() { return delegate.isLooping(); }
    @Override public boolean isRelative() { return global || delegate.isRelative(); }
    @Override public int getDelay() { return delegate.getDelay(); }
    @Override public float getPitch() { return delegate.getPitch() * pitchScale; }
    @Override public double getX() { return global ? 0.0 : delegate.getX(); }
    @Override public double getY() { return global ? 0.0 : delegate.getY(); }
    @Override public double getZ() { return global ? 0.0 : delegate.getZ(); }
    @Override public boolean canStartSilent() { return delegate.canStartSilent(); }
    @Override public boolean canPlaySound() { return delegate.canPlaySound(); }

    @Override
    public float getVolume() {
        float volume = delegate.getVolume();
        return (global ? Math.min(1.0F, volume) : volume) * volumeScale;
    }

    @Override
    public Attenuation getAttenuation() {
        return global ? Attenuation.NONE : delegate.getAttenuation();
    }
}
