package com.reiko.weather.weather;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.CustomWeatherPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datapack weathers: loaded on the server from {@code reikos_weather/weather}, and mirrored to clients. */
public final class CustomWeathers {
    public static final String DIRECTORY = "reikos_weather/weather";

    private static volatile Map<String, CustomWeather> server = Map.of();
    private static volatile Map<String, CustomWeather> client = Map.of();

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, CustomWeather> loaded = new LinkedHashMap<>();
            files.forEach((id, json) -> {
                try {
                    loaded.put(id.toString(), CustomWeather.parse(id, json.getAsJsonObject()));
                } catch (RuntimeException e) {
                    ReikosWeather.LOGGER.error("Couldn't load datapack weather {}: {}", id, e.getMessage());
                }
            });
            server = Map.copyOf(loaded);
            ReikosWeather.LOGGER.info("Loaded {} datapack weather type(s)", loaded.size());
        }
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        CustomWeatherPayload payload = payload();
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static CustomWeatherPayload payload() {
        List<CustomWeatherPayload.Entry> entries = new ArrayList<>();
        for (CustomWeather weather : enabled() ? server.values() : List.<CustomWeather>of()) {
            entries.add(new CustomWeatherPayload.Entry(weather.id().toString(), weather.json()));
        }
        return new CustomWeatherPayload(entries);
    }

    public static boolean enabled() {
        return !WeatherConfig.SPEC.isLoaded() || WeatherConfig.CUSTOM_WEATHERS.get();
    }

    /** Every datapack weather on the server (empty when disabled in the config). */
    public static Collection<CustomWeather> all() {
        return enabled() ? server.values() : List.of();
    }

    @Nullable
    public static CustomWeather get(@Nullable String id) {
        return id == null || !enabled() ? null : server.get(id);
    }

    /** The datapack weather active in a state, if it still exists and matches the background weather. */
    @Nullable
    public static CustomWeather of(WeatherState state) {
        CustomWeather custom = get(state.custom);
        return custom != null && custom.base() == state.type ? custom : null;
    }

    @Nullable
    public static CustomWeather client(@Nullable String id) {
        return id == null ? null : client.get(id);
    }

    /** Client: definitions received from the server. */
    public static void accept(CustomWeatherPayload payload) {
        Map<String, CustomWeather> parsed = new LinkedHashMap<>();
        for (CustomWeatherPayload.Entry entry : payload.entries()) {
            try {
                ResourceLocation id = ResourceLocation.parse(entry.id());
                parsed.put(entry.id(), CustomWeather.parse(id, JsonParser.parseString(entry.json()).getAsJsonObject()));
            } catch (RuntimeException e) {
                ReikosWeather.LOGGER.warn("Couldn't read datapack weather {} from the server: {}", entry.id(), e.getMessage());
            }
        }
        client = Map.copyOf(parsed);
    }

    public static void resetClient() {
        client = Map.of();
    }

    /** The profile of the background weather, with a datapack weather's overrides if one is active. */
    public static WeatherProfile backgroundProfile(WeatherType type, @Nullable CustomWeather custom, Climate climate) {
        return custom != null && custom.base() == type ? custom.profile(climate) : WeatherProfiles.get(type, climate);
    }

    private CustomWeathers() {}
}
