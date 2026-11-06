package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: your body temperature and what it is heading toward. */
public record TemperaturePayload(float body, float target) implements CustomPacketPayload {
    public static final Type<TemperaturePayload> TYPE = new Type<>(ReikosWeather.id("temperature"));
    public static final StreamCodec<ByteBuf, TemperaturePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, TemperaturePayload::body, ByteBufCodecs.FLOAT, TemperaturePayload::target, TemperaturePayload::new);

    /** The client's copy (NaN until the server has sent one). */
    public static volatile float clientBody = Float.NaN;
    public static volatile float clientTarget = Float.NaN;

    public static void accept(TemperaturePayload payload) {
        clientBody = payload.body();
        clientTarget = payload.target();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
