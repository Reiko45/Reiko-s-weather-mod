package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Summer is the rainy season in the tropics: savannas (which never see rain in vanilla) get rain, and
 * when players are in jungles or savannas the forecast turns much wetter. Like seasonal temperatures,
 * the savanna rain only applies on the game threads, never to world generation.
 */
public final class Monsoon {
    /** Savanna biome instances, from both the server's and the client's registries. */
    private static final Set<Biome> SAVANNAS = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static volatile boolean serverActive;
    private static volatile boolean clientActive;

    public static void collect(RegistryAccess registries) {
        registries.registry(Registries.BIOME).ifPresent(registry -> registry.getTag(BiomeTags.IS_SAVANNA)
                .ifPresent(tag -> tag.forEach(holder -> SAVANNAS.add(holder.value()))));
    }

    public static void setServerActive(boolean active) {
        serverActive = active;
    }

    public static void setClientActive(boolean active) {
        clientActive = active;
    }

    /** Whether a biome gets rain it normally wouldn't (called from the biome mixin). */
    public static boolean addsRain(Biome biome) {
        if (!serverActive && !clientActive || !SAVANNAS.contains(biome)) {
            return false;
        }
        return Seasons.onServerThread() ? serverActive : Seasons.onClientThread() && clientActive;
    }

    public static boolean isTropical(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_SAVANNA);
    }

    public static boolean enabled() {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.MONSOON.get();
    }

    private Monsoon() {}
}
