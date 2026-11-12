package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.temperature.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frost ferns: on a bitter night, the warm, damp air of a room meets the freezing glass of its windows, and the
 * moisture freezes on the inside of the pane in feathery, branching crystals, growing from the corners and edges.
 * They clear when the air outside warms up, or in the morning sun. Only windows of plain glass frost, between
 * open sky on one side and a roof on the other.
 */
public final class WindowFrost {
    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!WeatherConfig.WINDOW_FROST.get()) return;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time > 2000L && time < 12500L) return;
        RandomSource random = level.random;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++) {
            pos.set(player.getBlockX() + random.nextInt(33) - 16, player.getBlockY() + random.nextInt(17) - 8, player.getBlockZ() + random.nextInt(33) - 16);
            BlockState state = level.getBlockState(pos);
            boolean glass = state.is(Blocks.GLASS), pane = state.is(Blocks.GLASS_PANE);
            if (!glass && !pane || !window(level, pos)) continue;
            // (it's the cold outside the glass that matters)
            if (Temperature.ambient(level, outside(level, pos), local) > -8.0F) return;
            level.setBlockAndUpdate(pos, glass ? ModBlocks.FROSTED_GLASS.get().defaultBlockState()
                    : copyPane(state, ModBlocks.FROSTED_GLASS_PANE.get().defaultBlockState()));
        }
    }

    /** Whether glass here is a window: open sky outside it on one side, a roof over the other. */
    public static boolean window(ServerLevel level, BlockPos pos) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos a = pos.relative(axis, 1), b = pos.relative(axis, -1);
            if (!level.getBlockState(a).isAir() || !level.getBlockState(b).isAir()) continue;
            boolean skyA = level.canSeeSky(a), skyB = level.canSeeSky(b);
            if (skyA != skyB) return true;
        }
        return false;
    }

    /** The open-air side of a window (or the glass itself, if neither side is open to the sky). */
    static BlockPos outside(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(d);
            if (level.getBlockState(side).isAir() && level.canSeeSky(side)) return side;
        }
        return pos;
    }

    /** Whether frosted glass thaws on this random tick: as soon as the air outside rises toward freezing, or in the morning sun. */
    public static boolean thaws(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!WeatherConfig.WINDOW_FROST.get()) return true;
        BlockPos out = outside(level, pos);
        float air = Temperature.ambient(level, out, WeatherManager.local(level, out));
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean day = time > 1000L && time < 12000L;
        return air > -3.0F && random.nextInt(2) == 0 || day && air > -12.0F && random.nextInt(4) == 0;
    }

    /** {@code to} with the pane connections and waterlogging of {@code from}. */
    public static BlockState copyPane(BlockState from, BlockState to) {
        return to.setValue(CrossCollisionBlock.NORTH, from.getValue(CrossCollisionBlock.NORTH))
                .setValue(CrossCollisionBlock.EAST, from.getValue(CrossCollisionBlock.EAST))
                .setValue(CrossCollisionBlock.SOUTH, from.getValue(CrossCollisionBlock.SOUTH))
                .setValue(CrossCollisionBlock.WEST, from.getValue(CrossCollisionBlock.WEST))
                .setValue(CrossCollisionBlock.WATERLOGGED, from.getValue(CrossCollisionBlock.WATERLOGGED));
    }

    private WindowFrost() {}
}
