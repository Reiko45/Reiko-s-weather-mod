package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.block.WeatherVaneBlockEntity;
import com.reiko.weather.block.WindmillBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReikosWeather.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeatherVaneBlockEntity>> WEATHER_VANE = REGISTER.register("weather_vane",
            () -> BlockEntityType.Builder.of(WeatherVaneBlockEntity::new, ModBlocks.WEATHER_VANE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindmillBlockEntity>> WINDMILL = REGISTER.register("windmill",
            () -> BlockEntityType.Builder.of(WindmillBlockEntity::new, ModBlocks.WINDMILL.get()).build(null));

    private ModBlockEntities() {}
}
