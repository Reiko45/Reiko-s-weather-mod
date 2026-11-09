package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.IceFloeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The sea working on the coast: storm waves wearing a notch into the foot of the cliffs until the rock above
 * gives way, and dragging sand off the beach to drop it again just offshore; and icebergs drifting in from the
 * cold seas.
 */
public final class Coasts {
    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local) {
        RandomSource random = level.random;
        float wind = local.profile().wind * local.intensity();
        if (WeatherConfig.COASTAL_EROSION.get() && wind >= 0.65F && random.nextInt(60) == 0
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            erode(level, player, random);
        }
        if (WeatherConfig.ICEBERGS.get() && player.tickCount % 200 == 0 && random.nextInt(8) == 0) {
            driftBerg(level, player, random);
        }
    }

    /** Natural ground the sea can wear away (never anything built). */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.CALCITE) || s.is(Blocks.CLAY);
    }

    private static void erode(ServerLevel level, ServerPlayer player, RandomSource random) {
        int sea = level.getSeaLevel();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = player.getBlockX() + random.nextInt(97) - 48, z = player.getBlockZ() + random.nextInt(97) - 48;
            BlockPos foot = new BlockPos(x, sea, z);
            if (!level.isLoaded(foot) || !natural(level.getBlockState(foot))) continue;
            // facing the open sea?
            Direction seaward = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos w = foot.relative(d);
                if (level.getFluidState(w).is(FluidTags.WATER) && level.getBiome(foot.relative(d, 8)).is(BiomeTags.IS_OCEAN)) {
                    seaward = d;
                    break;
                }
            }
            if (seaward == null) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockState s = level.getBlockState(foot);
            if (top <= sea + 2 && s.is(BlockTags.SAND)) {
                // a beach: the backwash drags the sand off and drops it a little way out, as a bar
                level.setBlockAndUpdate(foot, Blocks.WATER.defaultBlockState());
                if (level.getBlockState(foot.above()).is(BlockTags.SAND)) level.setBlockAndUpdate(foot.above(), Blocks.AIR.defaultBlockState());
                BlockPos bar = foot.relative(seaward, 4 + random.nextInt(4)).below(2);
                if (level.getFluidState(bar).is(FluidTags.WATER) && level.getFluidState(bar.above()).is(FluidTags.WATER)
                        && !level.getBlockState(bar.below()).isAir()) {
                    level.setBlockAndUpdate(bar, Blocks.SAND.defaultBlockState());
                }
                return;
            }
            if (top >= sea + 4) {
                // a cliff: the waves cut a notch at its foot...
                for (BlockPos p : new BlockPos[]{foot, foot.above()}) {
                    if (natural(level.getBlockState(p))) level.setBlockAndUpdate(p, p.getY() <= sea ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                // ...until the rock above has nothing under it and comes down
                if (random.nextInt(3) == 0) {
                    for (int y = sea + 2; y <= Math.min(top, sea + 12); y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState rock = level.getBlockState(p);
                        if (!natural(rock)) break;
                        FallingBlockEntity fall = FallingBlockEntity.fall(level, p, rock.is(BlockTags.DIRT) ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                        fall.setDeltaMovement(seaward.getStepX() * 0.1, 0.0, seaward.getStepZ() * 0.1);
                        fall.hurtMarked = true;
                    }
                }
                return;
            }
        }
    }

    /** Now and then an iceberg drifts by in the cold seas, out in open water. */
    private static void driftBerg(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (!level.getEntitiesOfClass(IceFloeEntity.class, new AABB(player.blockPosition()).inflate(200), IceFloeEntity::berg).isEmpty()) {
            return;
        }
        float a = random.nextFloat() * Mth.TWO_PI;
        double d = 90 + random.nextDouble() * 60;
        int x = Mth.floor(player.getX() + Mth.cos(a) * d), z = Mth.floor(player.getZ() + Mth.sin(a) * d);
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) return;
        var biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));
        if (!biome.is(BiomeTags.IS_OCEAN) || biome.value().getBaseTemperature() >= 0.4F && !biome.is(net.minecraft.world.level.biome.Biomes.COLD_OCEAN)
                && !biome.is(net.minecraft.world.level.biome.Biomes.DEEP_COLD_OCEAN)) {
            return;
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
        if (!level.getFluidState(surface).is(FluidTags.WATER) || !level.getFluidState(surface.below(5)).is(FluidTags.WATER)) return;
        IceFloeEntity.spawnBerg(level, surface, 2.0F + random.nextFloat() * 2.5F);
    }

    // ------------------------------------------------------------------ fertile floods

    /** Flood water draining away leaves a layer of silt on the ground it covered. */
    static void leaveSilt(ServerLevel level, WeatherState state, BlockPos flood) {
        if (!WeatherConfig.FERTILE_FLOODS.get() || state.silt.size() > 8192) return;
        for (BlockPos p : BlockPos.betweenClosed(flood.offset(-3, -2, -3), flood.offset(3, 0, 3))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MUD)) {
                state.silt.add(p.asLong());
                if (s.is(Blocks.COARSE_DIRT)) level.setBlockAndUpdate(p, Blocks.DIRT.defaultBlockState()); // fine silt over the stones
            }
        }
        state.setDirty();
    }

    /** The silt slowly gets worked into the soil and the boost wears off (a bit of it each day). */
    static void weatherSilt(ServerLevel level, WeatherState state) {
        if (state.silt.isEmpty()) return;
        state.silt.removeIf(p -> level.random.nextInt(3) == 0);
        state.setDirty();
    }

    private Coasts() {}
}
