package com.reiko.weather.registry;

import com.google.common.collect.ImmutableSet;
import com.reiko.weather.ReikosWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The meteorologist (works at a weather station) and the storm probe's point of interest. */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, ReikosWeather.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, ReikosWeather.MOD_ID);

    public static final ResourceKey<PoiType> METEOROLOGIST_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ReikosWeather.id("meteorologist"));
    public static final ResourceKey<PoiType> STORM_PROBE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ReikosWeather.id("storm_probe"));

    public static final DeferredHolder<PoiType, PoiType> METEOROLOGIST_POI_TYPE = POI_TYPES.register("meteorologist",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.WEATHER_STATION.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final DeferredHolder<PoiType, PoiType> STORM_PROBE_POI_TYPE = POI_TYPES.register("storm_probe",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.STORM_PROBE.get().getStateDefinition().getPossibleStates()), 0, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> METEOROLOGIST = PROFESSIONS.register("meteorologist",
            () -> new VillagerProfession("meteorologist", holder -> holder.is(METEOROLOGIST_POI), holder -> holder.is(METEOROLOGIST_POI),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    private ModVillagers() {}
}
