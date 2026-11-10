package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground giving way: sinkholes opening up over caves after a long downpour, and rocks breaking off cliffs
 * in the freeze-thaw of spring, piling up as scree below.
 */
public final class GroundEvents {
    static void tick(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate, boolean soaked) {
        RandomSource random = level.random;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        if (WeatherConfig.SINKHOLES.get() && soaked && local.profile().rain > 0.8F && local.intensity() > 0.85F && random.nextInt(9000) == 0) {
            sinkhole(level, player, random);
        }
        if (WeatherConfig.ROCKFALL.get() && level.isDay() && local.profile().rain < 0.5F && random.nextInt(1500) == 0 && thawing(level, player, local)) {
            rockfall(level, player, random);
        }
    }

    /** Spring, or a thaw: water that got into the cracks overnight froze, and now it's melting again. */
    private static boolean thawing(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (WeatherManager.seasonInfo().season() == Season.SPRING) return true;
        float t = com.reiko.weather.temperature.Temperature.ambient(level, player.blockPosition(), local);
        return t > -2.0F && t < 5.0F;
    }

    /**
     * Rain soaking down through the ground over a cave eats away at the rock, until one day the roof caves in.
     * Only where there's a cave close under the surface, and well away from anything anyone's built.
     */
    private static void sinkhole(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = 16 + random.nextDouble() * 32;
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance), z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos top = new BlockPos(x, surface, z);
            BlockState ground = level.getBlockState(top);
            if (!ground.is(BlockTags.DIRT) && !ground.is(BlockTags.SAND) || !level.getFluidState(top).isEmpty()) continue;
            // a cave a few blocks down, big enough to swallow the roof
            int cave = -1;
            for (int dy = 3; dy <= 14; dy++) {
                BlockPos p = top.below(dy);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isAir() && level.getBlockState(p.below(2)).isAir()) {
                    cave = p.getY();
                    break;
                }
            }
            if (cave < 0) continue;
            int radius = 2 + random.nextInt(2);
            if (built(level, top, radius + 4)) continue;
            int moved = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    int cx = x + dx, cz = z + dz;
                    int columnTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
                    for (int y = columnTop; y > cave; y--) {
                        BlockPos p = new BlockPos(cx, y, cz);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !level.getFluidState(p).isEmpty() || s.getDestroySpeed(level, p) < 0) continue;
                        if (y >= columnTop - 1) {
                            // the turf on top falls in with the rest
                            FallingBlockEntity falling = FallingBlockEntity.fall(level, p, s.is(BlockTags.LEAVES) || !s.isCollisionShapeFullBlock(level, p)
                                    ? Blocks.DIRT.defaultBlockState() : s);
                            falling.setHurtsEntities(1.0F, 10);
                            moved++;
                        } else {
                            level.removeBlock(p, false);
                        }
                    }
                }
            }
            if (moved < 6) continue;
            level.playSound(null, x, surface, z, ModSounds.AVALANCHE.get(), SoundSource.BLOCKS, 3.0F, 0.5F);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(x, surface, z) < 64 * 64) {
                    p.displayClientMessage(Component.translatable("message.reikos_weather.sinkhole").withStyle(s -> s.withColor(0xC08040).withBold(true)), true);
                }
            }
            return;
        }
    }

    /** Signs that people live here: anything with a block entity (chests, furnaces, beds...), or planks, bricks and glass. */
    static boolean built(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -6, -r), center.offset(r, 4, r))) {
            BlockState s = level.getBlockState(p);
            if (s.hasBlockEntity() || s.is(BlockTags.PLANKS) || s.is(Blocks.BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.GLASS) || s.is(BlockTags.IMPERMEABLE) || s.is(BlockTags.WOOL)
                    || s.is(BlockTags.DOORS) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)) {
                return true;
            }
        }
        return false;
    }

    /** A cliff face nearby sheds a few rocks, which tumble down and pile up at the bottom as scree. */
    private static void rockfall(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = 10 + random.nextDouble() * 30;
            int x = Mth.floor(player.getX() + Mth.cos(angle) * distance), z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Direction out = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (height - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + d.getStepX(), z + d.getStepZ()) >= 6) {
                    out = d;
                    break;
                }
            }
            if (out == null) continue;
            int fell = 0;
            for (int k = 1; k <= 4 && fell < 3; k++) {
                BlockPos p = new BlockPos(x, height - k, z);
                BlockState s = level.getBlockState(p);
                if (!s.is(BlockTags.BASE_STONE_OVERWORLD) && !s.is(Blocks.COBBLESTONE) && !s.is(Blocks.GRAVEL)) continue;
                if (!level.getBlockState(p.relative(out)).isAir() || random.nextInt(3) == 0) continue;
                BlockState rock = random.nextInt(5) < 3 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                FallingBlockEntity falling = FallingBlockEntity.fall(level, p, rock);
                falling.setDeltaMovement(out.getStepX() * 0.15, 0.0, out.getStepZ() * 0.15);
                falling.setHurtsEntities(2.0F, 20);
                falling.hurtMarked = true;
                fell++;
            }
            if (fell == 0) continue;
            level.playSound(null, x, height, z, ModSounds.AVALANCHE.get(), SoundSource.BLOCKS, 1.5F, 1.4F + random.nextFloat() * 0.3F);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(x, height, z) < 24 * 24) {
                    p.displayClientMessage(Component.translatable("message.reikos_weather.rockfall").withStyle(s -> s.withColor(0xAAAAAA)), true);
                }
            }
            return;
        }
    }

    private GroundEvents() {}
}
