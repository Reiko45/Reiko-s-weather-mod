package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocks a tornado may rip out of the ground and fling around. */
    public static final TagKey<Block> TORNADO_LIFTABLE = TagKey.create(Registries.BLOCK, ReikosWeather.id("tornado_liftable"));
    /** What an EF2 (and stronger) tornado tears apart, on top of what any tornado lifts; and each stronger rating, more. */
    public static final TagKey<Block> TORNADO_EF2 = TagKey.create(Registries.BLOCK, ReikosWeather.id("tornado_ef2"));
    public static final TagKey<Block> TORNADO_EF3 = TagKey.create(Registries.BLOCK, ReikosWeather.id("tornado_ef3"));
    public static final TagKey<Block> TORNADO_EF4 = TagKey.create(Registries.BLOCK, ReikosWeather.id("tornado_ef4"));
    public static final TagKey<Block> TORNADO_EF5 = TagKey.create(Registries.BLOCK, ReikosWeather.id("tornado_ef5"));

    /** Blocks that carry a lightning strike into a tool lying on them. */
    public static final TagKey<Block> CONDUCTS_CHARGE = TagKey.create(Registries.BLOCK, ReikosWeather.id("conducts_charge"));
    /** Food that spoils in hot weather unless it's kept cool. */
    public static final TagKey<net.minecraft.world.item.Item> PERISHABLE = TagKey.create(Registries.ITEM, ReikosWeather.id("perishable"));

    private ModTags() {}
}
