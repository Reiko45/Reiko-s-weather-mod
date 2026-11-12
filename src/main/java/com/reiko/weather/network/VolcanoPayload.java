package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the volcanoes near the player and what each is doing. */
public record VolcanoPayload(List<Entry> volcanoes, long lastEruption) implements CustomPacketPayload {
    public static final Type<VolcanoPayload> TYPE = new Type<>(ReikosWeather.id("volcanoes"));
    public static final StreamCodec<FriendlyByteBuf, VolcanoPayload> STREAM_CODEC = StreamCodec.of(VolcanoPayload::write, VolcanoPayload::read);

    /**
     * @param phase    a {@link com.reiko.weather.volcano.Volcano.Phase} ordinal
     * @param progress 0 to 1 through the phase, when sent
     * @param length   the phase's length in ticks, so clients can keep the progress moving between updates
     */
    public record Entry(int x, int z, int lavaY, int peakY, int radius, int craterRadius, int phase, float progress, int length) {}

    /** The client's copy (common code, read only on the client). */
    public static volatile List<Entry> client = List.of();
    /** Client game time when {@link #client} arrived. */
    public static volatile long receivedAt;
    /** Day time a volcano last began erupting anywhere (for the twilight glow of its haze round the world). */
    public static volatile long clientLastEruption = Long.MIN_VALUE / 2;

    private static void write(FriendlyByteBuf buf, VolcanoPayload payload) {
        buf.writeLong(payload.lastEruption);
        buf.writeVarInt(payload.volcanoes.size());
        for (Entry e : payload.volcanoes) {
            buf.writeInt(e.x);
            buf.writeInt(e.z);
            buf.writeVarInt(e.lavaY);
            buf.writeVarInt(e.peakY);
            buf.writeVarInt(e.radius);
            buf.writeVarInt(e.craterRadius);
            buf.writeVarInt(e.phase);
            buf.writeFloat(e.progress);
            buf.writeVarInt(e.length);
        }
    }

    private static VolcanoPayload read(FriendlyByteBuf buf) {
        long last = buf.readLong();
        int n = Math.min(buf.readVarInt(), 256);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt()));
        }
        return new VolcanoPayload(list, last);
    }

    public static void accept(VolcanoPayload payload, long gameTime) {
        client = List.copyOf(payload.volcanoes);
        receivedAt = gameTime;
        clientLastEruption = payload.lastEruption;
    }

    public static void reset() {
        client = List.of();
        receivedAt = 0L;
        clientLastEruption = Long.MIN_VALUE / 2;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
