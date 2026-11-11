package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * Water in the bitter cold: freezing spray glazing the shore, brinicles growing down from the sea ice, polynyas
 * that never freeze over, and slushy frazil ice choking the rivers.
 */
public final class IcyWaters {
    private static final int AREA = 32;

    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        float air = Temperature.ambient(level, player.blockPosition(), local);
        RandomSource random = level.random;
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (WeatherConfig.POLYNYAS.get()) {
            for (int i = 0; i < 3; i++) openPolynya(level, column(level, player, random));
        }
        if (air > -2.0F) return;
        if (WeatherConfig.FREEZING_SPRAY.get() && griefing && local.profile().wind > 0.6F) {
            for (int i = 0; i < 3; i++) freezingSpray(level, column(level, player, random));
        }
        if (WeatherConfig.BRINICLES.get() && air < -8.0F && random.nextInt(600) == 0) {
            brinicle(level, player, column(level, player, random));
        }
        if (WeatherConfig.FRAZIL_ICE.get() && air < -6.0F) {
            for (int i = 0; i < 2; i++) frazil(level, column(level, player, random));
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /**
     * Freezing spray: a gale over a sea near freezing whips spray off the waves, and it freezes the moment it lands,
     * coating the shore, docks and anything left out in a thick, growing glaze of ice.
     */
    private static void freezingSpray(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.getBlockState(top).isAir()) return;
        int sea = level.getSeaLevel();
        if (top.getY() < sea || top.getY() > sea + 4) return;
        BlockPos below = top.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return;
        // only right by open sea
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int r = 1; r <= 4; r++) {
                BlockPos w = new BlockPos(top.getX() + d.getStepX() * r, sea - 1, top.getZ() + d.getStepZ() * r);
                if (level.getFluidState(w).is(FluidTags.WATER) && level.getBiome(w).is(BiomeTags.IS_OCEAN)) {
                    level.setBlockAndUpdate(top, ModBlocks.ICE_GLAZE.get().defaultBlockState());
                    return;
                }
            }
        }
    }

    /** Starts a brinicle under the sea ice (it grows down to the bottom by itself while the cold lasts). */
    private static void brinicle(ServerLevel level, ServerPlayer player, @Nullable BlockPos top) {
        if (top == null) return;
        int sea = level.getSeaLevel();
        BlockPos ice = new BlockPos(top.getX(), sea - 1, top.getZ());
        BlockPos under = ice.below();
        if (top.getY() != sea || !level.getBlockState(ice).is(Blocks.ICE) || !level.getBlockState(under).is(Blocks.WATER)
                || !level.getFluidState(under).isSource() || !level.getBiome(under).is(BiomeTags.IS_OCEAN)) return;
        level.setBlockAndUpdate(under, ModBlocks.BRINICLE.get().defaultBlockState());
        if (player.isUnderWater() && player.distanceToSqr(under.getX(), under.getY(), under.getZ()) < 24 * 24) {
            WeatherAdvancements.award(player, "brinicle");
        }
    }

    /**
     * Whether a polynya lies at (x, z): an opening in the sea ice kept clear all winter by upwelling warmer water or
     * by winds pushing the ice away. A few are scattered through every icy sea, always in the same places.
     */
    public static boolean polynya(int x, int z) {
        int cx = Math.floorDiv(x, 128), cz = Math.floorDiv(z, 128);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int h = Mth.murmurHash3Mixer((cx + dx) * 73428767 ^ (cz + dz) * 912931 ^ 0x5F3759DF);
                if (Math.floorMod(h, 3) != 0) continue;
                float px = (cx + dx) * 128 + 24 + ((h >>> 4) & 63) * 1.25F, pz = (cz + dz) * 128 + 24 + ((h >>> 10) & 63) * 1.25F;
                float radius = 7.0F + ((h >>> 16) & 15);
                float stretch = 1.0F + ((h >>> 20) & 7) * 0.15F, angle = ((h >>> 23) & 31) * 0.2F;
                float ox = x - px, oz = z - pz;
                float u = ox * Mth.cos(angle) + oz * Mth.sin(angle), v = -ox * Mth.sin(angle) + oz * Mth.cos(angle);
                float edge = radius * (1.0F + 0.15F * Mth.sin((float) Mth.atan2(v, u) * 3.0F + h));
                if ((u / stretch) * (u / stretch) + v * v < edge * edge) return true;
            }
        }
        return false;
    }

    /** Whether sea ice should stay open here (so the water never freezes). */
    public static boolean keepsOpen(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.POLYNYAS.get() && polynya(pos.getX(), pos.getZ())
                && pos.getY() == level.getSeaLevel() - 1 && level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    private static void openPolynya(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockPos ice = new BlockPos(top.getX(), level.getSeaLevel() - 1, top.getZ());
        if (level.getBlockState(ice).is(Blocks.ICE) && keepsOpen(level, ice)) {
            level.setBlockAndUpdate(ice, Blocks.WATER.defaultBlockState());
        }
    }

    /**
     * Frazil ice: a river too quick to freeze over, chilled a shade below freezing, fills with drifting needles of ice
     * that clump into slush on the surface and stick to the stones on the bottom as anchor ice.
     */
    private static void frazil(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockPos surface = top.below();
        BlockState s = level.getBlockState(surface);
        if (!s.is(Blocks.WATER) || !level.getFluidState(surface).isSource() || !level.getBiome(surface).is(BiomeTags.IS_RIVER)) return;
        if (level.random.nextBoolean()) {
            level.setBlockAndUpdate(surface, ModBlocks.FRAZIL_ICE.get().defaultBlockState());
            return;
        }
        // anchor ice on the bed
        BlockPos.MutableBlockPos p = surface.mutable();
        for (int i = 0; i < 12; i++) {
            p.move(Direction.DOWN);
            BlockState here = level.getBlockState(p);
            if (!here.is(Blocks.WATER)) {
                BlockPos bed = p.above();
                if (here.isFaceSturdy(level, p, Direction.UP) && !bed.equals(surface) && level.getFluidState(bed).isSource()) {
                    level.setBlockAndUpdate(bed, ModBlocks.FRAZIL_ICE.get().defaultBlockState());
                }
                return;
            }
        }
    }

    private IcyWaters() {}
}
