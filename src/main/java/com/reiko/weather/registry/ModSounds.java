package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, ReikosWeather.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = register("weather.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAIL = register("weather.hail");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO = register("weather.tornado");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN = register("block.tornado_siren");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(ReikosWeather.id(name)));
    }

    private ModSounds() {}
}
