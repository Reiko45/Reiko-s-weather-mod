package com.reiko.weather.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC,
                (payload, context) -> SyncedWeather.accept(payload));
        event.registrar("1").playToClient(CustomWeatherPayload.TYPE, CustomWeatherPayload.STREAM_CODEC,
                (payload, context) -> com.reiko.weather.weather.CustomWeathers.accept(payload));
        event.registrar("1").playToClient(OtherworldPayload.TYPE, OtherworldPayload.STREAM_CODEC,
                (payload, context) -> OtherworldPayload.accept(payload));
    }

    private ModNetwork() {}
}
