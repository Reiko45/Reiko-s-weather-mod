package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(Registries.PARTICLE_TYPE, ReikosWeather.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAIL = simple("hail");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SLEET = simple("sleet");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DOWNPOUR = simple("downpour");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNOWFLAKE = simple("snowflake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLIZZARD = simple("blizzard");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SAND = simple("sand");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = simple("dust");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LEAF = simple("leaf");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FUNNEL = simple("funnel");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = simple("mist");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIPPLE = simple("ripple");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = simple("ember");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        return REGISTER.register(name, () -> new SimpleParticleType(false));
    }

    private ModParticles() {}
}
