package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the current overworld weather.
 *
 * @param weather   weather ordinal, or -1 when the weather system is disabled (client leaves vanilla alone)
 * @param elapsed   ticks since this weather started
 * @param windAngle direction the wind blows toward, radians
 * @param rainbow   ticks left on a post-rain rainbow
 */
public record WeatherSyncPayload(int weather, int elapsed, float windAngle, int rainbow) implements CustomPacketPayload {
    public static final Type<WeatherSyncPayload> TYPE = new Type<>(ReikosWeather.id("weather_sync"));

    public static final StreamCodec<ByteBuf, WeatherSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeatherSyncPayload::weather,
            ByteBufCodecs.VAR_INT, WeatherSyncPayload::elapsed,
            ByteBufCodecs.FLOAT, WeatherSyncPayload::windAngle,
            ByteBufCodecs.VAR_INT, WeatherSyncPayload::rainbow,
            WeatherSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
