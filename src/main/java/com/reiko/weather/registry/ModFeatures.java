package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.worldgen.CoastalLichenFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World-generation features (added to biomes by the biome modifiers in data/reikos_weather/neoforge/biome_modifier). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> REGISTER = DeferredRegister.create(Registries.FEATURE, ReikosWeather.MOD_ID);

    public static final DeferredHolder<Feature<?>, CoastalLichenFeature> COASTAL_LICHEN = REGISTER.register("coastal_lichen",
            () -> new CoastalLichenFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
