package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.BallLightningEntity;
import com.reiko.weather.mixin.LightningBoltAccessor;
import com.reiko.weather.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Lightning extras: fulgurite (sand fused into glass) and ball lightning. */
public final class LightningEvents {
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LightningBolt bolt
                && !((LightningBoltAccessor) bolt).reikos_weather$isVisualOnly()
                && WeatherConfig.SPEC.isLoaded() && WeatherConfig.FULGURITE.get()) {
            fulgurite(level, BlockPos.containing(bolt.getX(), bolt.getY() - 0.5, bolt.getZ()));
        }
    }

    /** A strike on sand melts a small, branching lump of glass into the ground. */
    private static void fulgurite(ServerLevel level, BlockPos strike) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos pos = strike.offset(dx, dy, dz);
                    boolean core = dx == 0 && dz == 0;
                    if ((core || level.random.nextFloat() < 0.45F - dy * -0.1F) && fuse(level, pos)) {
                        continue;
                    }
                    if (!core) break;
                }
            }
        }
    }

    private static boolean fuse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.RED_SAND)) {
            level.setBlockAndUpdate(pos, Blocks.ORANGE_STAINED_GLASS.defaultBlockState());
            return true;
        }
        if (state.is(BlockTags.SAND)) {
            level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
            return true;
        }
        return false;
    }

    /** Rare ball lightning during thunderstorms, a few blocks off the ground near the player. */
    static void maybeSpawnBallLightning(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!local.type().vanillaThunder || local.intensity() < 0.8F || !WeatherConfig.BALL_LIGHTNING.get()
                || level.random.nextInt(4000) != 0 || !level.getEntities(ModEntities.BALL_LIGHTNING.get(), e -> true).isEmpty()) {
            return;
        }
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        double distance = 8 + level.random.nextDouble() * 12;
        BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        spawnBallLightning(level, ground.getX() + 0.5, ground.getY() + 1.2, ground.getZ() + 0.5);
    }

    public static BallLightningEntity spawnBallLightning(ServerLevel level, double x, double y, double z) {
        BallLightningEntity ball = new BallLightningEntity(ModEntities.BALL_LIGHTNING.get(), level);
        ball.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(ball);
        return ball;
    }

    private LightningEvents() {}
}
