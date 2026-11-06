package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the weather in the player's current Nether/End dimension. */
public record OtherworldPayload(int weather, int elapsed) implements CustomPacketPayload {
    public static final Type<OtherworldPayload> TYPE = new Type<>(ReikosWeather.id("otherworld_sync"));
    public static final StreamCodec<ByteBuf, OtherworldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OtherworldPayload::weather, ByteBufCodecs.VAR_INT, OtherworldPayload::elapsed, OtherworldPayload::new);

    /** The client's copy (common code, read only on the client). */
    public static volatile int clientWeather;
    public static volatile int clientElapsed;

    public static void accept(OtherworldPayload payload) {
        clientWeather = payload.weather();
        clientElapsed = payload.elapsed();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
