package com.reiko.weather.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Lets the sun hat and storm goggles be worn in a Curios head slot. Only loaded with Curios. */
public final class CuriosIntegration {
    public static boolean equipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(inventory -> inventory.isEquipped(item)).orElse(false);
    }

    private CuriosIntegration() {}
}
