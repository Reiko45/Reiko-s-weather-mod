package com.reiko.weather.client.sound;

import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A looping, non-positional wind bed whose volume and pitch follow the wind strength. */
public class WindSoundInstance extends AbstractTickableSoundInstance {
    private int silentTicks;

    public WindSoundInstance() {
        super(ModSounds.WIND.get(), SoundSource.WEATHER, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.01F;
        this.pitch = 1.0F;
    }

    public static float targetVolume() {
        if (!ClientConfig.SOUNDS.get()) {
            return 0.0F;
        }
        float otherworld = com.reiko.weather.client.OtherworldClient.windVolume();
        if (!ClientWeather.active()) {
            return otherworld;
        }
        float strength = Mth.clamp((ClientWeather.wind() - 0.3F) / 0.7F, 0.0F, 1.0F);
        Minecraft mc = Minecraft.getInstance();
        float gust = mc.level != null ? Wind.gust(mc.level.getGameTime(), 0.0F) : 1.0F;
        return Math.max(otherworld, strength * (0.25F + 0.75F * ClientWeather.exposure()) * gust * 0.9F);
    }

    @Override
    public void tick() {
        float target = targetVolume();
        this.volume = Mth.lerp(0.05F, this.volume, Math.max(0.001F, target));
        this.pitch = 0.75F + Math.max(ClientWeather.wind(), com.reiko.weather.client.OtherworldClient.windVolume()) * 0.45F;
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
