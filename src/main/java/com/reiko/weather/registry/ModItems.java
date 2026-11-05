package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.item.BarometerItem;
import com.reiko.weather.item.UmbrellaItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(ReikosWeather.MOD_ID);

    public static final DeferredItem<BarometerItem> BAROMETER = REGISTER.register("barometer",
            () -> new BarometerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<UmbrellaItem> UMBRELLA = REGISTER.register("umbrella",
            () -> new UmbrellaItem(new Item.Properties().stacksTo(1).durability(160)));

    public static final DeferredItem<ArmorItem> WINTER_COAT = REGISTER.register("winter_coat",
            () -> new ArmorItem(ModArmorMaterials.WINTER_COAT, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(12))));

    public static final DeferredItem<ArmorItem> GOGGLES = REGISTER.register("goggles",
            () -> new ArmorItem(ModArmorMaterials.GOGGLES, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(10))));

    public static final DeferredItem<BlockItem> WEATHER_STATION = REGISTER.registerSimpleBlockItem(ModBlocks.WEATHER_STATION);
    public static final DeferredItem<BlockItem> TORNADO_SIREN = REGISTER.registerSimpleBlockItem(ModBlocks.TORNADO_SIREN);
    public static final DeferredItem<BlockItem> RAIN_BARREL = REGISTER.registerSimpleBlockItem(ModBlocks.RAIN_BARREL);

    private ModItems() {}
}
