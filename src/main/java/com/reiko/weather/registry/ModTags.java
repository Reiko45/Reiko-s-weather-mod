package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocks a tornado may rip out of the ground and fling around. */
    public static final TagKey<Block> TORNADO_LIFTABLE = TagKey.create(Registries.BLOCK, ReikosWeather.id("tornado_liftable"));

    private ModTags() {}
}
