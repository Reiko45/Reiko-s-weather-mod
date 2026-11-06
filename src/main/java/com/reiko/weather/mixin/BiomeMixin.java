package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reiko.weather.weather.Seasons;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Seasonal temperature: colder winters make temperate biomes snow; warmer springs melt it again. */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @ModifyReturnValue(method = "getTemperature", at = @At("RETURN"))
    private float reikos_weather$season(float temperature) {
        return temperature + Seasons.temperatureShiftForCurrentThread();
    }

    /** The monsoon brings rain to savannas in summer. */
    @ModifyReturnValue(method = "hasPrecipitation", at = @At("RETURN"))
    private boolean reikos_weather$monsoon(boolean precipitation) {
        return precipitation || com.reiko.weather.weather.Monsoon.addsRain((Biome) (Object) this);
    }
}
