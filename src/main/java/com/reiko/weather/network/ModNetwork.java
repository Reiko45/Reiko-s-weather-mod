package com.reiko.weather.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC,
                (payload, context) -> SyncedWeather.accept(payload));
    }

    private ModNetwork() {}
}
