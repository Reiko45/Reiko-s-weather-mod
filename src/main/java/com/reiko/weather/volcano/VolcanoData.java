package com.reiko.weather.volcano;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;

/** Every volcano found so far in a level, saved in data/reikos_weather_volcanoes.dat. */
public class VolcanoData extends SavedData {
    public static final String FILE_ID = "reikos_weather_volcanoes";
    public static final Factory<VolcanoData> FACTORY = new Factory<>(VolcanoData::new, VolcanoData::load);

    private final Long2ObjectLinkedOpenHashMap<Volcano> volcanoes = new Long2ObjectLinkedOpenHashMap<>();
    /** When (day time) a volcano last began erupting, anywhere: its haze spreads round the world for a few weeks. */
    public long lastEruption = Long.MIN_VALUE / 2;

    public Collection<Volcano> all() {
        return volcanoes.values();
    }

    public boolean contains(long key) {
        return volcanoes.containsKey(key);
    }

    @Nullable
    public Volcano get(long key) {
        return volcanoes.get(key);
    }

    public void add(Volcano volcano) {
        volcanoes.put(volcano.shape.key(), volcano);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Volcano v : volcanoes.values()) {
            list.add(v.save());
        }
        tag.put("volcanoes", list);
        tag.putLong("lastEruption", lastEruption);
        return tag;
    }

    public static VolcanoData load(CompoundTag tag, HolderLookup.Provider registries) {
        VolcanoData data = new VolcanoData();
        if (tag.contains("lastEruption")) data.lastEruption = tag.getLong("lastEruption");
        for (Tag t : tag.getList("volcanoes", Tag.TAG_COMPOUND)) {
            Volcano v = Volcano.load((CompoundTag) t);
            data.volcanoes.put(v.shape.key(), v);
        }
        return data;
    }
}
