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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** Lightning extras: fulgurite (sand fused into glass), ball lightning, bolts from the blue and trees split by strikes. */
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
        if (WeatherConfig.BOLTS_FROM_THE_BLUE.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            struckTree(level, strike);
        }
        if (WeatherConfig.PEAT_FIRES.get()) {
            // a strike on a bog can set the peat under it smouldering
            int lit = 0;
            for (BlockPos pos : BlockPos.betweenClosed(strike.offset(-1, -3, -1), strike.offset(1, 0, 1))) {
                if (lit < 3 && com.reiko.weather.block.PeatBlock.ignite(level, pos.immutable())) lit++;
            }
        }
    }

    /**
     * A tree hit by lightning: the current races down the sap under the bark and boils it, blasting a long strip of
     * bark off the trunk all the way to the ground, and a hard strike splits the crown apart and throws the top down.
     */
    private static void struckTree(ServerLevel level, BlockPos strike) {
        BlockPos trunk = null;
        for (int dy = 1; dy >= -6 && trunk == null; dy--) {
            for (BlockPos p : BlockPos.betweenClosed(strike.offset(-1, dy, -1), strike.offset(1, dy, 1))) {
                if (level.getBlockState(p).is(BlockTags.LOGS)) {
                    trunk = p.immutable();
                    break;
                }
            }
        }
        if (trunk == null || GroundEvents.built(level, trunk, 4)) return; // a log cabin isn't a tree
        BlockPos foot = trunk;
        for (int i = 0; i < 30 && level.getBlockState(foot.below()).is(BlockTags.LOGS); i++) foot = foot.below();
        if (!level.getBlockState(foot.below()).is(BlockTags.DIRT)) return; // only a tree growing out of the ground
        BlockPos top = trunk;
        for (int i = 0; i < 12 && level.getBlockState(top.above()).is(BlockTags.LOGS); i++) top = top.above();
        int scarred = 0;
        BlockPos.MutableBlockPos pos = top.mutable();
        for (int i = 0; i < 30 && level.getBlockState(pos).is(BlockTags.LOGS); i++, pos.move(Direction.DOWN)) {
            BlockState bare = stripped(level.getBlockState(pos));
            if (bare != null) {
                level.setBlock(pos, bare, Block.UPDATE_ALL);
                scarred++;
            }
        }
        if (scarred == 0) return;
        level.playSound(null, top, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
        // a hard strike splits the top of the trunk apart and brings branches down round the foot of the tree
        if (scarred >= 4 && level.random.nextFloat() < 0.35F) {
            for (int i = 0; i < 2; i++) level.destroyBlock(top.below(i), true);
            for (int k = 0; k < 3; k++) {
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        foot.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3));
                BlockState branch = ModBlocks.FALLEN_BRANCH.get().defaultBlockState()
                        .setValue(com.reiko.weather.block.FallenBranchBlock.AXIS, level.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
                if (level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty() && branch.canSurvive(level, at)) {
                    level.setBlockAndUpdate(at, branch);
                }
            }
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(top).inflate(40))) {
            WeatherAdvancements.award(player, "struck_tree");
        }
    }

    /** The bark-stripped form of a log (oak log to stripped oak log, and so on), or null if there isn't one. */
    @javax.annotation.Nullable
    public static BlockState stripped(BlockState log) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(log.getBlock());
        if (id.getPath().startsWith("stripped_")) return null;
        Block bare = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id.withPath("stripped_" + id.getPath())).orElse(null);
        if (bare == null || bare == Blocks.AIR) return null;
        BlockState out = bare.defaultBlockState();
        if (log.hasProperty(RotatedPillarBlock.AXIS) && out.hasProperty(RotatedPillarBlock.AXIS)) {
            out = out.setValue(RotatedPillarBlock.AXIS, log.getValue(RotatedPillarBlock.AXIS));
        }
        return out;
    }

    /**
     * A bolt from the blue: lightning that leaps out sideways from the top of a thunderstorm and comes down miles
     * away under a clear sky, where nobody expects it. Only near a storm, never under it.
     */
    static void maybeBoltFromTheBlue(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!WeatherConfig.BOLTS_FROM_THE_BLUE.get() || local.profile().rain > 0.05F || level.random.nextInt(3000) != 0) return;
        StormCell storm = null;
        for (StormCell cell : WeatherManager.state(level).cells) {
            if (!cell.type.vanillaThunder || cell.strength() < 0.5F) continue;
            double edge = cell.distanceTo(player.getX(), player.getZ()) - cell.radius;
            if (edge > 30 && edge < 400) {
                storm = cell;
                break;
            }
        }
        if (storm == null) return;
        // somewhere round about the player, more often on the side away from the storm
        double away = Math.atan2(player.getZ() - storm.z, player.getX() - storm.x) + (level.random.nextDouble() - 0.5) * 2.2;
        double distance = 12 + level.random.nextDouble() * 60;
        BlockPos column = BlockPos.containing(player.getX() + Math.cos(away) * distance, 0, player.getZ() + Math.sin(away) * distance);
        if (!level.isLoaded(column)) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column)));
        level.addFreshEntity(bolt);
        if (distance < 48) WeatherAdvancements.award(player, "bolt_from_the_blue");
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
