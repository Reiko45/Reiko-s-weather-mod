package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: an earthquake is shaking the ground where you are.
 *
 * @param strength how hard it shakes here, 0 to 1
 * @param ticks    how long it lasts
 */
public record QuakePayload(float strength, int ticks) implements CustomPacketPayload {
    public static final Type<QuakePayload> TYPE = new Type<>(ReikosWeather.id("quake"));
    public static final StreamCodec<FriendlyByteBuf, QuakePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeFloat(p.strength); buf.writeVarInt(p.ticks); },
            buf -> new QuakePayload(buf.readFloat(), buf.readVarInt()));

    /** Set by the client setup. */
    public static java.util.function.BiConsumer<Float, Integer> clientHandler = (s, t) -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
