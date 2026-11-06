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
import com.reiko.weather.block.LightningBatteryBlock;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightning extras: fulgurite (sand fused into glass) and ball lightning. */
public final class LightningEvents {
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LightningBolt bolt)
                || ((LightningBoltAccessor) bolt).reikos_weather$isVisualOnly() || !WeatherConfig.SPEC.isLoaded()) {
            return;
        }
        BlockPos strike = BlockPos.containing(bolt.getX(), bolt.getY() - 0.5, bolt.getZ());
        chargeBatteries(level, strike);
        if (WeatherConfig.FULGURITE.get() && fulgurite(level, strike) > 0) {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(strike).inflate(32))) {
                WeatherAdvancements.award(player, "fulgurite");
            }
        }
        if (WeatherConfig.DRY_LIGHTNING.get() && level.dimension() == Level.OVERWORLD && !level.isRainingAt(strike.above())) {
            dryLightningFire(level, strike);
        }
    }

    /** Lightning batteries within a few blocks of a strike (for example under a lightning rod) soak it up. */
    private static void chargeBatteries(ServerLevel level, BlockPos strike) {
        for (BlockPos pos : BlockPos.betweenClosed(strike.offset(-3, -4, -3), strike.offset(3, 2, 3))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.LIGHTNING_BATTERY.get())) {
                LightningBatteryBlock.charge(level, pos.immutable(), state);
            }
        }
    }

    /** With no rain to put it out, a strike sets the dry vegetation around it alight. */
    private static void dryLightningFire(ServerLevel level, BlockPos strike) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        int lit = 0;
        for (int attempt = 0; attempt < 40 && lit < 5; attempt++) {
            BlockPos pos = strike.offset(level.random.nextInt(9) - 4, level.random.nextInt(9) - 3, level.random.nextInt(9) - 4);
            BlockState state = level.getBlockState(pos);
            boolean open = state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
            if (open && nextToFuel(level, pos) && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                lit++;
            }
        }
    }

    /** Dry grass, leaves, wood... anything flammable touching this spot. */
    private static boolean nextToFuel(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isFlammable(level, pos, Direction.UP)) {
            return true; // grass plants and the like burn where they stand
        }
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockState(n).isFlammable(level, n, d.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    /** Summer heatwaves bring the odd dry thunderstorm strike, out over the forests. */
    static void maybeDryStrike(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.DRY_LIGHTNING.get() || level.random.nextInt(9000) != 0
                || WeatherManager.seasonInfo().season() != Season.SUMMER) {
            return;
        }
        float angle = level.random.nextFloat() * Mth.TWO_PI;
        double distance = 24 + level.random.nextDouble() * 72;
        BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance);
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        LightningBolt strike = EntityType.LIGHTNING_BOLT.create(level);
        if (strike != null) {
            strike.moveTo(Vec3.atBottomCenterOf(top));
            level.addFreshEntity(strike);
        }
    }

    /** A strike on sand melts a small, branching lump of glass into the ground. Returns how many blocks fused. */
    private static int fulgurite(ServerLevel level, BlockPos strike) {
        int fused = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos pos = strike.offset(dx, dy, dz);
                    boolean core = dx == 0 && dz == 0;
                    if ((core || level.random.nextFloat() < 0.45F - dy * -0.1F) && fuse(level, pos)) {
                        fused++;
                        continue;
                    }
                    if (!core) break;
                }
            }
        }
        return fused;
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
