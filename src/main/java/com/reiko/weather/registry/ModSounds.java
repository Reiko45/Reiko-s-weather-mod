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
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIN_ROOF = register("weather.rain_roof");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_WHISTLE = register("weather.wind_whistle");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALL_LIGHTNING = register("entity.ball_lightning");
    public static final DeferredHolder<SoundEvent, SoundEvent> AVALANCHE = register("weather.avalanche");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUNE_SONG = register("weather.dune_song");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_CRACK = register("weather.frost_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIRDSONG = register("ambient.birdsong");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR = register("entity.meteor");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_ALERT = register("item.weather_radio.alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEESE = register("ambient.geese");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLCANO_RUMBLE = register("weather.volcano_rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLCANO_ERUPTION = register("weather.volcano_eruption");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATIC_CRACKLE = register("weather.static_crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER = register("block.geyser.erupt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOT_SPRING = register("block.hot_spring.bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_SING = register("ambient.ice_sing");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOWHOLE = register("block.blowhole.spout");
    public static final DeferredHolder<SoundEvent, SoundEvent> EARTHQUAKE = register("weather.earthquake");
    public static final DeferredHolder<SoundEvent, SoundEvent> TIDAL_BORE = register("weather.tidal_bore");
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_QUAKE = register("weather.frost_quake");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_SHOVE = register("weather.ice_shove");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_FALL = register("weather.tree_fall");

    public static final DeferredHolder<SoundEvent, SoundEvent> SNOW_SQUEAK = register("weather.snow_squeak");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(ReikosWeather.id(name)));
    }

    private ModSounds() {}
}
