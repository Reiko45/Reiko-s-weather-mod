package com.reiko.weather.weather;

import com.reiko.weather.block.IcicleBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/** Droughts, wildfires, icicles, frozen rivers and avalanches: the more extreme side of the weather. */
public final class ExtremeWeather {
    private static final int AREA = 48;
    private static final int HEAT_AREA = 32;

    static void tick(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherProfile profile = local.profile();
        float intensity = local.intensity();

        if (profile.heat && intensity >= 0.8F) {
            if (WeatherConfig.WILDFIRES.get() && climate == Climate.ARID && random.nextInt(2400) == 0) {
                wildfire(level, player);
            }
            // a few columns near the player each tick: unwatered crops lose a stage every few minutes and
            // a full cauldron in the sun dries out over one heatwave
            boolean wilting = WeatherConfig.CROP_WILTING.get(), evaporating = WeatherConfig.CAULDRON_EVAPORATION.get();
            for (int i = 0; i < 4 && (wilting || evaporating); i++) {
                BlockPos top = column(level, player, random, HEAT_AREA);
                if (wilting) wilt(level, top);
                if (evaporating) evaporate(level, top);
            }
        }
        boolean freezing = climate == Climate.COLD;
        if (freezing && WeatherConfig.FREEZE_RIVERS.get()) {
            for (int i = 0; i < 3; i++) {
                freezeWater(level, column(level, player, random, AREA));
            }
        }
        if (freezing && WeatherConfig.ICICLES.get()) {
            if (random.nextFloat() < 0.15F) {
                growIcicle(level, player, random);
            }
            if (player.tickCount % 5 == 0) {
                dropIcicleAbove(level, player, random);
            }
        }
        if (local.type() == WeatherType.BLIZZARD && freezing && intensity >= 0.9F && WeatherConfig.AVALANCHES.get()
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && random.nextInt(3000) == 0) {
            avalanche(level, player, random);
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random, int area) {
        int x = player.getBlockX() + random.nextInt(area * 2 + 1) - area;
        int z = player.getBlockZ() + random.nextInt(area * 2 + 1) - area;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    // ------------------------------------------------------------------ heatwaves

    /** Dry grass catches fire somewhere 16-48 blocks away; vanilla fire spread does the rest. */
    private static void wildfire(ServerLevel level, ServerPlayer player) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        RandomSource random = level.random;
        for (int attempt = 0; attempt < 10; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = 16 + random.nextDouble() * 32;
            BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            BlockState plant = level.getBlockState(top);
            if (plant.is(Blocks.SHORT_GRASS) || plant.is(Blocks.TALL_GRASS) || plant.is(Blocks.DEAD_BUSH) || plant.is(Blocks.FERN)) {
                level.setBlockAndUpdate(top, BaseFireBlock.getState(level, top));
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(top.getX(), top.getY(), top.getZ()) < 96 * 96) {
                        p.displayClientMessage(Component.translatable("message.reikos_weather.wildfire").withStyle(s -> s.withColor(0xFF8833)), true);
                    }
                }
                return;
            }
        }
    }

    /** Crops on dry farmland lose growth in the heat. */
    private static void wilt(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return;
        BlockState crop = level.getBlockState(pos);
        BlockState soil = level.getBlockState(pos.below());
        if (crop.getBlock() instanceof CropBlock cropBlock && soil.is(Blocks.FARMLAND) && soil.getValue(FarmBlock.MOISTURE) < 7
                && level.random.nextFloat() < 0.3F) {
            int age = cropBlock.getAge(crop);
            if (age > 0) level.setBlockAndUpdate(pos, cropBlock.getStateForAge(age - 1));
        }
    }

    /** Water cauldrons out in the sun slowly evaporate. */
    private static void evaporate(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockPos pos = top.below();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON) && level.random.nextFloat() < 0.5F) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }
    }

    // ------------------------------------------------------------------ winter

    /** Freezes still water much faster than vanilla, so rivers and lakes ice over in winter. */
    private static void freezeWater(ServerLevel level, @Nullable BlockPos top) {
        if (top == null) return;
        BlockPos pos = top.below();
        Biome biome = level.getBiome(pos).value();
        if (level.getFluidState(pos).isSourceOfType(Fluids.WATER) && biome.shouldFreeze(level, pos)) {
            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        }
    }

    /** Icicles hang under the edges of roofs and overhangs. */
    private static void growIcicle(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(33) - 16;
        int z = player.getBlockZ() + random.nextInt(33) - 16;
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) return;
        // the roof: the topmost solid block of the column (a snow layer may sit on top)
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos roof = new BlockPos(x, top, z);
        while (level.getBlockState(roof).is(Blocks.SNOW) && roof.getY() > level.getMinBuildHeight()) {
            roof = roof.below(); // look under the snow piled on the roof
        }
        BlockPos below = roof.below();
        if (!level.getBlockState(roof).isFaceSturdy(level, roof, Direction.DOWN) || !level.getBlockState(below).isAir()
                || !level.getBlockState(below.below()).isAir() || level.getBrightness(LightLayer.BLOCK, below) > 10) {
            return;
        }
        // only at an edge: something open beside the roof block
        boolean edge = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(roof.relative(d)).isAir()) edge = true;
        }
        if (edge && level.getBiome(below).value().coldEnoughToSnow(below)) {
            level.setBlockAndUpdate(below, ModBlocks.ICICLE.get().defaultBlockState());
        }
    }

    /** Icicles over a player's head can break loose. */
    private static void dropIcicleAbove(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (player.isSpectator() || player.getAbilities().invulnerable) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy <= 9; dy++) {
            pos.set(player.getBlockX(), player.getBlockY() + dy, player.getBlockZ());
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.ICICLE.get())) {
                if (random.nextInt(4) == 0) {
                    IcicleBlock.drop(level, pos.immutable(), state);
                }
                return;
            }
            if (!state.isAir()) return;
        }
    }

    /** Snow on a steep slope breaks loose and slides downhill. */
    private static void avalanche(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = 12 + random.nextDouble() * 28;
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance), z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            Direction downhill = null;
            int drop = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d.getStepX() * 6, z + d.getStepZ() * 6);
                if (height - h > drop) {
                    drop = height - h;
                    downhill = d;
                }
            }
            if (downhill == null || drop < 5) continue;
            int moved = 0;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz > 16 || random.nextFloat() < 0.3F) continue;
                    int sx = x + dx, sz = z + dz;
                    BlockPos top = new BlockPos(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz), sz);
                    for (BlockPos p : new BlockPos[]{top, top.below()}) {
                        BlockState s = level.getBlockState(p);
                        if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
                            FallingBlockEntity snow = FallingBlockEntity.fall(level, p, s);
                            snow.setDeltaMovement(downhill.getStepX() * (0.4 + random.nextDouble() * 0.3), 0.15, downhill.getStepZ() * (0.4 + random.nextDouble() * 0.3));
                            snow.setHurtsEntities(1.0F, 6);
                            snow.hurtMarked = true;
                            moved++;
                            break;
                        }
                    }
                }
            }
            if (moved >= 6) {
                level.playSound(null, x, height, z, ModSounds.AVALANCHE.get(), SoundSource.WEATHER, 4.0F, 0.9F + random.nextFloat() * 0.2F);
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(x, height, z) < 64 * 64) {
                        p.displayClientMessage(Component.translatable("message.reikos_weather.avalanche").withStyle(s -> s.withColor(0xBBDDFF).withBold(true)), true);
                    }
                }
                return;
            }
        }
    }

    private ExtremeWeather() {}
}
