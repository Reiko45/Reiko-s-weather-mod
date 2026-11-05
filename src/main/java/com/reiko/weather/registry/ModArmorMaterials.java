package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> REGISTER = DeferredRegister.create(Registries.ARMOR_MATERIAL, ReikosWeather.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WINTER_COAT = REGISTER.register("winter_coat", () -> new ArmorMaterial(
            defense(1, 2, 3, 1), 15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(ItemTags.WOOL),
            List.of(new ArmorMaterial.Layer(ReikosWeather.id("winter_coat"))), 0.0F, 0.0F));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GOGGLES = REGISTER.register("goggles", () -> new ArmorMaterial(
            defense(1, 1, 1, 1), 12, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER),
            List.of(new ArmorMaterial.Layer(ReikosWeather.id("goggles"))), 0.0F, 0.0F));

    private static EnumMap<ArmorItem.Type, Integer> defense(int boots, int legs, int chest, int helmet) {
        return Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, boots);
            map.put(ArmorItem.Type.LEGGINGS, legs);
            map.put(ArmorItem.Type.CHESTPLATE, chest);
            map.put(ArmorItem.Type.HELMET, helmet);
            map.put(ArmorItem.Type.BODY, chest);
        });
    }

    private ModArmorMaterials() {}
}
