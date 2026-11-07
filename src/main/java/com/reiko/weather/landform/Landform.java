package com.reiko.weather.landform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** One planned landform: its shape, how to build it into the world, and (for some) how it changes with the weather. */
public interface Landform {
    LandformKind kind();

    BoundingBox bounds();

    /** Builds whatever part of the landform falls in {@code area} (a chunk, during world generation). */
    void build(WorldGenLevel level, BoundingBox area);

    CompoundTag save();

    /** Called about once a second while a player is within the landform's bounds. */
    default void tick(ServerLevel level, ServerPlayer player) {}
}
