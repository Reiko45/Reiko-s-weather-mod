package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import net.minecraft.client.Minecraft;

/** Accessibility settings shared by the mod's visual effects. */
public final class Accessibility {
    /** Skip bright flashes: our own option, or vanilla's "Hide Lightning Flashes". */
    public static boolean reduceFlashing() {
        Minecraft mc = Minecraft.getInstance();
        return ClientConfig.SPEC.isLoaded() && ClientConfig.REDUCE_FLASHING.get() || mc.options != null && mc.options.hideLightningFlash().get();
    }

    /** 0 to 1: how strong screen effects (raindrops, shimmer, camera roll) should be. */
    public static float screenEffects() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.SCREEN_EFFECTS.get().floatValue() : 1.0F;
    }

    private Accessibility() {}
}
