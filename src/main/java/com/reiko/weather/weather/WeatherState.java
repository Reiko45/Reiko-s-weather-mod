package com.reiko.weather.weather;

import net.minecraft.core.HolderLookup;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
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

    /** Regional storms drifting across the map. */
    public final List<StormCell> cells = new ArrayList<>();
    public int nextCellId = 1;
    public int cellCooldown = 6000;
    /** Shifts our own season calendar (set with /rweather season). */
    public int seasonOffsetDays;
    /** Water sources placed by flash floods, removed again once the rain stops. */
    public final LongArrayList floodBlocks = new LongArrayList();

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
        ListTag cellList = new ListTag();
        for (StormCell cell : cells) {
            cellList.add(cell.save());
        }
        tag.put("cells", cellList);
        tag.putInt("nextCellId", nextCellId);
        tag.putInt("cellCooldown", cellCooldown);
        tag.putInt("seasonOffsetDays", seasonOffsetDays);
        tag.put("floodBlocks", new LongArrayTag(floodBlocks.toLongArray()));
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
        ListTag cellList = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            state.cells.add(StormCell.load(cellList.getCompound(i)));
        }
        state.nextCellId = Math.max(1, tag.getInt("nextCellId"));
        state.cellCooldown = tag.contains("cellCooldown") ? tag.getInt("cellCooldown") : 6000;
        state.seasonOffsetDays = tag.getInt("seasonOffsetDays");
        for (long pos : tag.getLongArray("floodBlocks")) {
            state.floodBlocks.add(pos);
        }
        return state;
    }
}
