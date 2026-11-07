package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types are data-driven; their JSON lives in data/reikos_weather/damage_type. */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> HAIL = ResourceKey.create(Registries.DAMAGE_TYPE, ReikosWeather.id("hail"));
    public static final ResourceKey<DamageType> TORNADO = ResourceKey.create(Registries.DAMAGE_TYPE, ReikosWeather.id("tornado"));
    public static final ResourceKey<DamageType> ICICLE = ResourceKey.create(Registries.DAMAGE_TYPE, ReikosWeather.id("icicle"));
    public static final ResourceKey<DamageType> HEATSTROKE = ResourceKey.create(Registries.DAMAGE_TYPE, ReikosWeather.id("heatstroke"));
    public static final ResourceKey<DamageType> BALL_LIGHTNING = ResourceKey.create(Registries.DAMAGE_TYPE, ReikosWeather.id("ball_lightning"));
    public static final ResourceKey<DamageType> ACID_RAIN = ResourceKey.create(Registries.DAMAGE_TYPE, ReikosWeather.id("acid_rain"));

    private ModDamageTypes() {}
}
