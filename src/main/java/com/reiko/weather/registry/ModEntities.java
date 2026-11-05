package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.TornadoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, ReikosWeather.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoEntity>> TORNADO = REGISTER.register("tornado",
            () -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(3.0F, 12.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build(ReikosWeather.id("tornado").toString()));

    private ModEntities() {}
}
