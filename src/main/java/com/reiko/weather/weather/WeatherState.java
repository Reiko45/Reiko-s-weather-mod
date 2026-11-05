package com.reiko.weather.weather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Persistent weather state for a level, saved in data/reikos_weather.dat. */
public class WeatherState extends SavedData {
    public static final String FILE_ID = "reikos_weather";
    public static final int RAMP_TICKS = 600;

    public static final Factory<WeatherState> FACTORY = new Factory<>(WeatherState::new, WeatherState::load);

    public record Forecast(WeatherType type, int duration) {}

    public boolean initialized;
    public WeatherType type = WeatherType.CLEAR;
    public int remaining = 12000;
    public int elapsed;
    public final List<Forecast> forecast = new ArrayList<>();
    public float windAngle;
    public float windDrift;
    public int rainbowTicks;

    /** 0 to 1, how established the current weather is. Hazards scale with this so storms build up. */
    public float intensity() {
        return Mth.clamp(elapsed / (float) RAMP_TICKS, 0.0F, 1.0F);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("initialized", initialized);
        tag.putString("type", type.id());
        tag.putInt("remaining", remaining);
        tag.putInt("elapsed", elapsed);
        tag.putFloat("windAngle", windAngle);
        tag.putFloat("windDrift", windDrift);
        tag.putInt("rainbow", rainbowTicks);
        ListTag list = new ListTag();
        for (Forecast f : forecast) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", f.type().id());
            entry.putInt("duration", f.duration());
            list.add(entry);
        }
        tag.put("forecast", list);
        return tag;
    }

    public static WeatherState load(CompoundTag tag, HolderLookup.Provider registries) {
        WeatherState state = new WeatherState();
        state.initialized = tag.getBoolean("initialized");
        WeatherType type = WeatherType.byId(tag.getString("type"));
        state.type = type != null ? type : WeatherType.CLEAR;
        state.remaining = tag.getInt("remaining");
        state.elapsed = tag.getInt("elapsed");
        state.windAngle = tag.getFloat("windAngle");
        state.windDrift = tag.getFloat("windDrift");
        state.rainbowTicks = tag.getInt("rainbow");
        ListTag list = tag.getList("forecast", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            WeatherType ft = WeatherType.byId(entry.getString("type"));
            if (ft != null) {
                state.forecast.add(new Forecast(ft, entry.getInt("duration")));
            }
        }
        return state;
    }
}
