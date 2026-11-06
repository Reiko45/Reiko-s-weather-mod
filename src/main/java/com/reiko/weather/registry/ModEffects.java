package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Warmth (hot cocoa) and Refreshed (melon cooler): they shift how warm you feel by about 10 °C. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTER = DeferredRegister.create(Registries.MOB_EFFECT, ReikosWeather.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> WARMTH = REGISTER.register("warmth",
            () -> new TemperatureEffect(MobEffectCategory.BENEFICIAL, 0xE0782A));
    public static final DeferredHolder<MobEffect, MobEffect> REFRESHED = REGISTER.register("refreshed",
            () -> new TemperatureEffect(MobEffectCategory.BENEFICIAL, 0x7FD8F0));

    private static final class TemperatureEffect extends MobEffect {
        TemperatureEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private ModEffects() {}
}
