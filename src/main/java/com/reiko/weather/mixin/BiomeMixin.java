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

    /** Snow caps: high ground is colder, so the snowline climbs and falls with the seasons. */
    @ModifyReturnValue(method = "getTemperature", at = @At("RETURN"))
    private float reikos_weather$snowCaps(float temperature, net.minecraft.core.BlockPos pos) {
        // (a plain return-value hook: this runs for every column of falling rain and snow, every frame)
        return temperature - com.reiko.weather.weather.Highlands.lapse(pos);
    }

    /** Polynyas: patches of an icy sea that never freeze over. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void reikos_weather$polynya(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, boolean mustBeAtEdge,
                                        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof net.minecraft.server.level.ServerLevel && com.reiko.weather.weather.IcyWaters.keepsOpen(level, pos)) {
            cir.setReturnValue(false);
        }
    }

    /** The monsoon brings rain to savannas in summer. */
    @ModifyReturnValue(method = "hasPrecipitation", at = @At("RETURN"))
    private boolean reikos_weather$monsoon(boolean precipitation) {
        return precipitation || com.reiko.weather.weather.Monsoon.addsRain((Biome) (Object) this);
    }
}
