package com.reiko.weather.compat;

import com.reiko.weather.config.WeatherConfig;
import net.neoforged.fml.ModList;

/** Which optional mods are installed, and the hooks that only run when they are. */
public final class Compat {
    private static Boolean toughAsNails, curios;

    public static boolean toughAsNailsLoaded() {
        if (toughAsNails == null) toughAsNails = ModList.get().isLoaded("toughasnails");
        return toughAsNails;
    }

    public static boolean curiosLoaded() {
        if (curios == null) curios = ModList.get().isLoaded("curios");
        return curios;
    }

    /** True when Tough As Nails runs body temperature and ours should stand aside (its weather still feeds in). */
    public static boolean toughAsNailsHandlesTemperature() {
        return toughAsNailsLoaded() && WeatherConfig.SPEC.isLoaded() && WeatherConfig.TOUGH_AS_NAILS.get()
                && com.reiko.weather.compat.toughasnails.TanIntegration.temperatureEnabled();
    }

    /** Whether an entity wears an item in its armor slot, or (with Curios) in a curio slot. */
    public static boolean wearing(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.Item item) {
        if (entity.getItemBySlot(slot).is(item)) {
            return true;
        }
        return curiosLoaded() && com.reiko.weather.compat.curios.CuriosIntegration.equipped(entity, item);
    }

    /** Called once during common setup. */
    public static void setup() {
        if (toughAsNailsLoaded()) {
            com.reiko.weather.compat.toughasnails.TanIntegration.register();
        }
    }

    private Compat() {}
}
