package com.reiko.weather.registry;

import com.mojang.serialization.Codec;
import com.reiko.weather.ReikosWeather;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents REGISTER = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReikosWeather.MOD_ID);

    /** Game time until which a lightning-charged tool keeps its charge. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CHARGED_UNTIL = REGISTER.registerComponentType("charged_until",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Whether a weather radio is switched on. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RADIO_ON = REGISTER.registerComponentType("radio_on",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private ModDataComponents() {}
}
