package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.item.BarometerItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(ReikosWeather.MOD_ID);

    public static final DeferredItem<BarometerItem> BAROMETER = REGISTER.register("barometer",
            () -> new BarometerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
