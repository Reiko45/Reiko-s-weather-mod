package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One creative tab holding everything the mod adds, grouped instruments → gear → food → machines → nature. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReikosWeather.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WEATHER = REGISTER.register("weather", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ReikosWeather.MOD_ID))
            .icon(() -> new ItemStack(ModItems.BAROMETER.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((params, output) -> {
                Set<Item> items = new LinkedHashSet<>();
                for (Supplier<? extends Item> item : order()) items.add(item.get());
                // anything added later that the list above doesn't name yet still shows up, at the end
                ModItems.REGISTER.getEntries().forEach(entry -> items.add(entry.get()));
                items.forEach(output::accept);
            })
            .build());

    private static List<Supplier<? extends Item>> order() {
        // the rest (terrain, plants, lichens …) follow in registration order
        return List.of(
                // instruments and tools
                ModItems.BAROMETER, ModItems.THERMOMETER, ModItems.HANDHELD_RADAR, ModItems.WEATHER_RADIO,
                ModItems.WEATHER_ALMANAC, ModItems.STORM_DATA, ModItems.CLOUD_SEEDING_ROCKET, ModItems.UMBRELLA,
                ModItems.PARAGLIDER, ModItems.RAKE, ModItems.ICE_SAW, ModItems.SALT, ModItems.EARTHWORM,
                // clothing
                ModItems.WINTER_COAT, ModItems.GOGGLES, ModItems.SUN_HAT,
                // food and drink
                ModItems.HOT_COCOA, ModItems.MELON_COOLER, ModItems.RICE_BOWL, ModItems.WINTER_STEW,
                ModItems.STORMFISH, ModItems.COOKED_STORMFISH, ModItems.ICEFISH, ModItems.COOKED_ICEFISH,
                ModItems.KALE, ModItems.RICE,
                // machines and furniture
                ModItems.WEATHER_STATION, ModItems.WEATHER_RADAR, ModItems.WEATHER_VANE, ModItems.WINDMILL,
                ModItems.TORNADO_SIREN, ModItems.LIGHTNING_BATTERY, ModItems.STORM_PROBE, ModItems.SEISMOGRAPH,
                ModItems.FORECAST_BOARD, ModItems.RAIN_BARREL, ModItems.ICEBOX, ModItems.TENT, ModItems.SANDBAG,
                ModItems.WEATHER_ALTAR,
                // volcanic
                ModItems.VOLCANIC_ASH, ModItems.SULFUR, ModItems.SULFUR_CRUST, ModItems.VOLCANIC_VENT,
                ModItems.GEYSER, ModItems.GAS_VENT, ModItems.STEAMING_GROUND, ModItems.SMOULDERING_GROUND,
                ModItems.PUMICE, ModItems.CLINKER, ModItems.BLACK_SAND, ModItems.METEORITE);
    }

    private ModCreativeTabs() {}
}
