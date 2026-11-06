package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server to client: the datapack weather definitions, sent as their JSON source. */
public record CustomWeatherPayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(String id, String json) {}

    public static final Type<CustomWeatherPayload> TYPE = new Type<>(ReikosWeather.id("custom_weathers"));
    public static final StreamCodec<FriendlyByteBuf, CustomWeatherPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeCollection(payload.entries(), (b, e) -> {
                b.writeUtf(e.id());
                b.writeUtf(e.json(), 65536);
            }),
            buf -> new CustomWeatherPayload(buf.readList(b -> new Entry(b.readUtf(), b.readUtf(65536)))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
