package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * Squeaky snow: in a hard frost, below about -10°C, the snow crystals are too cold for the pressure of a footstep to
 * melt them slippery, so they grind and snap against one another instead, and every step squeaks and creaks.
 */
public final class SqueakySnow {
    public static void onPlaySound(PlayLevelSoundEvent event) {
        if (!event.getLevel().isClientSide() || event.getSound() == null || !ClientConfig.SQUEAKY_SNOW.get()) return;
        if (!event.getSound().is(SoundEvents.SNOW_STEP.getLocation())) return;
        if (ClientWeather.airTemperature() < -10.0F) {
            event.setSound(ModSounds.SNOW_SQUEAK);
        }
    }

    private SqueakySnow() {}
}
