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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRAUPEL = simple("graupel");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POLLEN = simple("pollen");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEW = simple("dew");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEA_FOAM = simple("sea_foam");
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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BREATH = simple("breath");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREFLY = simple("firefly");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WORM = simple("worm");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH = simple("ash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DIAMOND_DUST = simple("diamond_dust");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM_FOG = simple("steam_fog");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRAY = simple("spray");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEA_SPARKLE = simple("sea_sparkle");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        return REGISTER.register(name, () -> new SimpleParticleType(false));
    }

    private ModParticles() {}
}
