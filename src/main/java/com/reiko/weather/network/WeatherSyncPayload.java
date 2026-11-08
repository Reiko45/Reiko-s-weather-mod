package com.reiko.weather.network;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.weather.StormCell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: the overworld's weather.
 *
 * @param weather          background weather ordinal, or -1 when the weather system is disabled
 * @param elapsed          ticks since the background weather started
 * @param remaining        ticks until it changes
 * @param windAngle        direction the wind blows toward, radians
 * @param rainbow          ticks left on a post-rain rainbow
 * @param season           season ordinal, or -1 without seasons
 * @param dayOfSeason      0-based day within the season (-1 if unknown)
 * @param daysPerSeason    season length in days (-1 if unknown)
 * @param temperatureShift biome temperature shift for the season
 * @param forecast         upcoming background weathers
 * @param cells            regional storm cells
 * @param custom           id of the active datapack weather, or "" for none
 * @param drought          0 to 1, how bad the drought is (0 when there's none)
 */
public record WeatherSyncPayload(int weather, int elapsed, int remaining, float windAngle, int rainbow,
                                 int season, int dayOfSeason, int daysPerSeason, float temperatureShift,
                                 int[] forecast, List<StormCell> cells, String custom, float drought) implements CustomPacketPayload {
    public static final Type<WeatherSyncPayload> TYPE = new Type<>(ReikosWeather.id("weather_sync"));
    public static final StreamCodec<FriendlyByteBuf, WeatherSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(WeatherSyncPayload::write, WeatherSyncPayload::read);

    public static WeatherSyncPayload unmanaged() {
        return new WeatherSyncPayload(-1, 0, 0, 0.0F, 0, -1, -1, -1, 0.0F, new int[0], List.of(), "", 0.0F);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(weather);
        buf.writeVarInt(elapsed);
        buf.writeVarInt(remaining);
        buf.writeFloat(windAngle);
        buf.writeVarInt(rainbow);
        buf.writeVarInt(season);
        buf.writeVarInt(dayOfSeason);
        buf.writeVarInt(daysPerSeason);
        buf.writeFloat(temperatureShift);
        buf.writeVarIntArray(forecast);
        buf.writeVarInt(cells.size());
        for (StormCell cell : cells) {
            cell.write(buf);
        }
        buf.writeUtf(custom);
        buf.writeFloat(drought);
    }

    private static WeatherSyncPayload read(FriendlyByteBuf buf) {
        int weather = buf.readVarInt();
        int elapsed = buf.readVarInt();
        int remaining = buf.readVarInt();
        float windAngle = buf.readFloat();
        int rainbow = buf.readVarInt();
        int season = buf.readVarInt();
        int dayOfSeason = buf.readVarInt();
        int daysPerSeason = buf.readVarInt();
        float shift = buf.readFloat();
        int[] forecast = buf.readVarIntArray(16);
        int count = Math.min(buf.readVarInt(), 64);
        List<StormCell> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cells.add(StormCell.read(buf));
        }
        String custom = buf.readUtf();
        float drought = buf.readFloat();
        return new WeatherSyncPayload(weather, elapsed, remaining, windAngle, rainbow, season, dayOfSeason, daysPerSeason, shift, forecast, cells, custom, drought);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
