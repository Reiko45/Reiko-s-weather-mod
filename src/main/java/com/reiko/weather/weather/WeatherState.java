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

    /** @param custom id of a datapack weather built on {@code type}, or null */
    public record Forecast(WeatherType type, int duration, @javax.annotation.Nullable String custom) {
        public Forecast(WeatherType type, int duration) {
            this(type, duration, null);
        }
    }

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
    /** Game time when the weather altar can be used again. */
    public long altarReadyAt;
    /** Id of the datapack-defined weather that is active, or null for a built-in one. */
    @javax.annotation.Nullable
    public String custom;
    /** The last day an afternoon thunderstorm and a morning sea fog were considered (one chance a day each). */
    public long convectionDay = -1, seaFogDay = -1, riverDay = -1;
    /** Water sources placed by flash floods, removed again once the rain stops. */
    public final LongArrayList floodBlocks = new LongArrayList();
    /** Ticks of rain so far today, and the day that is (for droughts). */
    public int rainToday;
    public long droughtCheckDay = -1;
    /** Days in a row without proper rain. */
    public int dryDays;
    /** Days the drought still has to run (0 when there's none), and how long it was set to last. */
    public int droughtDaysLeft, droughtLength;
    /** 0 to 1, how far an algal bloom (red tide) has spread through the warm seas. */
    public float bloom;
    /** 0 to 1, how far a marine heatwave has warmed the sea. */
    public float seaHeat;
    /** Shallow pond water a drought dried up (the bed turned to cracked mud), refilled when the rain comes back. */
    public final LongArrayList driedPonds = new LongArrayList();
    /** Water a cold snap froze where it normally wouldn't, thawed again when it warms up. */
    public final LongArrayList snapIce = new LongArrayList();
    /** Water a tsunami washed up over the land, and when it starts draining away. */
    public final LongArrayList tsunamiBlocks = new LongArrayList();
    public long tsunamiUntil;
    /** Ground that floods have left silt on (crops on it grow faster for a while). */
    public final it.unimi.dsi.fastutil.longs.LongOpenHashSet silt = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    /** Water a flash flood sent down an arroyo, drained again once the rain stops. */
    public final LongArrayList flashFlood = new LongArrayList();

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
            if (f.custom() != null) entry.putString("custom", f.custom());
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
        tag.put("driedPonds", new LongArrayTag(driedPonds.toLongArray()));
        tag.put("snapIce", new LongArrayTag(snapIce.toLongArray()));
        tag.put("flashFlood", new LongArrayTag(flashFlood.toLongArray()));
        tag.put("tsunami", new LongArrayTag(tsunamiBlocks.toLongArray()));
        tag.putLong("tsunamiUntil", tsunamiUntil);
        tag.put("silt", new LongArrayTag(silt.toLongArray()));
        tag.putInt("rainToday", rainToday);
        tag.putLong("droughtCheckDay", droughtCheckDay);
        tag.putInt("dryDays", dryDays);
        tag.putInt("droughtDaysLeft", droughtDaysLeft);
        tag.putInt("droughtLength", droughtLength);
        tag.putFloat("bloom", bloom);
        tag.putFloat("seaHeat", seaHeat);
        tag.putLong("altarReadyAt", altarReadyAt);
        tag.putLong("convectionDay", convectionDay);
        tag.putLong("seaFogDay", seaFogDay);
        tag.putLong("riverDay", riverDay);
        if (custom != null) tag.putString("custom", custom);
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
                state.forecast.add(new Forecast(ft, entry.getInt("duration"), entry.contains("custom") ? entry.getString("custom") : null));
            }
        }
        ListTag cellList = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            state.cells.add(StormCell.load(cellList.getCompound(i)));
        }
        state.nextCellId = Math.max(1, tag.getInt("nextCellId"));
        state.cellCooldown = tag.contains("cellCooldown") ? tag.getInt("cellCooldown") : 6000;
        state.seasonOffsetDays = tag.getInt("seasonOffsetDays");
        state.altarReadyAt = tag.getLong("altarReadyAt");
        state.convectionDay = tag.contains("convectionDay") ? tag.getLong("convectionDay") : -1;
        state.seaFogDay = tag.contains("seaFogDay") ? tag.getLong("seaFogDay") : -1;
        state.riverDay = tag.contains("riverDay") ? tag.getLong("riverDay") : -1;
        state.custom = tag.contains("custom") ? tag.getString("custom") : null;
        for (long pos : tag.getLongArray("floodBlocks")) {
            state.floodBlocks.add(pos);
        }
        for (long pos : tag.getLongArray("driedPonds")) {
            state.driedPonds.add(pos);
        }
        for (long pos : tag.getLongArray("snapIce")) {
            state.snapIce.add(pos);
        }
        for (long pos : tag.getLongArray("flashFlood")) {
            state.flashFlood.add(pos);
        }
        for (long pos : tag.getLongArray("tsunami")) {
            state.tsunamiBlocks.add(pos);
        }
        state.tsunamiUntil = tag.getLong("tsunamiUntil");
        for (long pos : tag.getLongArray("silt")) {
            state.silt.add(pos);
        }
        state.rainToday = tag.getInt("rainToday");
        state.droughtCheckDay = tag.contains("droughtCheckDay") ? tag.getLong("droughtCheckDay") : -1;
        state.dryDays = tag.getInt("dryDays");
        state.droughtDaysLeft = tag.getInt("droughtDaysLeft");
        state.droughtLength = tag.getInt("droughtLength");
        state.bloom = tag.getFloat("bloom");
        state.seaHeat = tag.getFloat("seaHeat");
        return state;
    }
}
