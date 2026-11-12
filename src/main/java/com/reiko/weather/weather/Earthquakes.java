package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.QuakePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Earthquakes (off by default): now and then the ground shakes, from a light tremor to a quake that brings rocks
 * down off cliffs, sets loose sand and gravel sliding and breaks windows. A big one out under the sea sends a
 * tsunami (also off by default) onto the nearest coasts a minute or two later: the sea surges far up over the
 * low ground for a while, then drains away.
 */
public final class Earthquakes {
    private record Tsunami(double x, double z, long arrives, float height) {}

    @Nullable
    private static Tsunami pending;
    private record Aftershock(double x, double z, long at, float magnitude) {}
    private static final java.util.List<Aftershock> AFTERSHOCKS = new java.util.ArrayList<>();

    static void tick(ServerLevel level, WeatherState state) {
        tickTsunami(level, state);
        // the aftershocks of an earlier quake, coming due
        if (!AFTERSHOCKS.isEmpty()) {
            long now = level.getGameTime();
            var due = AFTERSHOCKS.stream().filter(a -> a.at() <= now).toList();
            AFTERSHOCKS.removeAll(due);
            for (Aftershock a : due) shake(level, a.x(), a.z(), a.magnitude(), false);
        }
        if (!WeatherConfig.EARTHQUAKES.get() || level.getGameTime() % 20L != 0L || level.players().isEmpty()) {
            return;
        }
        RandomSource random = level.random;
        // about one a fortnight (in game days) near any one player
        if (random.nextInt(14 * 1200) != 0) {
            return;
        }
        ServerPlayer near = level.players().get(random.nextInt(level.players().size()));
        float angle = random.nextFloat() * Mth.TWO_PI;
        double distance = 100 + random.nextDouble() * 500;
        float magnitude = Mth.clamp(0.25F - 0.25F * (float) Math.log(1.0 - random.nextDouble() * 0.95), 0.25F, 1.0F);
        quake(level, near.getX() + Mth.cos(angle) * distance, near.getZ() + Mth.sin(angle) * distance, magnitude);
    }

    /** An earthquake with its epicentre at (x, z); {@code magnitude} 0 to 1. */
    public static void quake(ServerLevel level, double x, double z, float magnitude) {
        shake(level, x, z, magnitude, true);
    }

    /** How many aftershocks are still to come (for the gametests). */
    public static int pendingAftershocks() {
        return AFTERSHOCKS.size();
    }

    private static void shake(ServerLevel level, double x, double z, float magnitude, boolean main) {
        RandomSource random = level.random;
        if (main && magnitude > 0.4F && WeatherConfig.AFTERSHOCKS.get()) {
            // aftershocks: the fault keeps settling for a few days, the shocks coming less and less often (Omori's law),
            // the biggest of them a good deal weaker than the main quake (Båth's law)
            int n = 3 + Math.round(5 * magnitude);
            long now = level.getGameTime();
            for (int k = 1; k <= n; k++) {
                long at = now + (long) (900 * Math.pow(k, 1.8) * (0.6 + random.nextDouble()));
                float m = magnitude * (0.65F - 0.04F * k) * (0.7F + 0.3F * random.nextFloat());
                AFTERSHOCKS.add(new Aftershock(x + random.nextGaussian() * 50, z + random.nextGaussian() * 50, at, Math.max(0.1F, m)));
            }
        }
        double reach = 200 + 700 * magnitude;
        int ticks = 60 + Math.round(140 * magnitude);
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean torn = false; // (one rupture per quake)
        for (ServerPlayer player : level.players()) {
            double d = Math.sqrt(player.distanceToSqr(x, player.getY(), z));
            if (d > reach) continue;
            float felt = magnitude * (float) (1.0 - d / reach);
            PacketDistributor.sendToPlayer(player, new QuakePayload(Math.min(1.0F, felt * 1.4F), ticks));
            if (felt > 0.15F) {
                player.displayClientMessage(Component.translatable(felt > 0.45F ? "message.reikos_weather.earthquake_strong"
                        : "message.reikos_weather.earthquake").withStyle(s -> s.withColor(0xC8A070).withBold(felt > 0.45F)), true);
                WeatherAdvancements.award(player, "shaken");
            }
            if (griefing && felt > 0.3F) shakeLoose(level, player, felt, random);
            if (main && !torn && felt > 0.55F) {
                FaultScarps.rupture(level, player, felt, random);
                torn = true;
            }
            if (griefing && felt > 0.35F && WeatherConfig.LIQUEFACTION.get()) liquefy(level, player, felt, random);
        }
        boolean underSea = level.getBiome(BlockPos.containing(x, level.getSeaLevel(), z)).is(BiomeTags.IS_OCEAN);
        if (underSea && magnitude > 0.45F) {
            // a big quake under the sea can set off an undersea eruption, and its pumice floats up in rafts
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, player.getY(), z) < 600 * 600) Seas.pumiceRafts(level, player, random);
            }
        }
        if (underSea && magnitude > 0.55F && WeatherConfig.TSUNAMIS.get() && griefing) {
            pending = new Tsunami(x, z, level.getGameTime() + 1200 + random.nextInt(1200), 2.0F + 3.0F * magnitude);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(Component.translatable("message.reikos_weather.tsunami_warning").withStyle(s -> s.withColor(0xFF5555).withBold(true)), false);
            }
        }
    }

    /**
     * Liquefaction: shaking turns waterlogged sand into a slurry. The ground near water goes soft as quicksand for a
     * few minutes, and the water squeezed out of it spouts up through the surface in sand boils, little cones of
     * sand with a spring in the middle.
     */
    private static void liquefy(ServerLevel level, ServerPlayer player, float felt, RandomSource random) {
        int soft = 0, boils = 0;
        for (int i = 0; i < Math.round(120 * felt) && soft < 40; i++) {
            int x = player.getBlockX() + random.nextInt(49) - 24, z = player.getBlockZ() + random.nextInt(49) - 24;
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
            BlockState s = level.getBlockState(top);
            if (!(s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.MUD)) || top.getY() > level.getSeaLevel() + 4 || !nearWater(level, top)) continue;
            if (GroundEvents.built(level, top, 3)) continue;
            // a patch of soft ground, settling again in a few minutes
            for (BlockPos p : BlockPos.betweenClosed(top.offset(-1, -1, -1), top.offset(1, 0, 1))) {
                BlockState there = level.getBlockState(p);
                if ((there.is(BlockTags.SAND) || there.is(Blocks.GRAVEL) || there.is(Blocks.MUD)) && random.nextFloat() < 0.6F
                        && level.getBlockState(p.above()).getFluidState().isEmpty()) {
                    level.setBlockAndUpdate(p, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                    level.scheduleTick(p.immutable(), com.reiko.weather.registry.ModBlocks.QUICKSAND.get(), 2400 + random.nextInt(2400));
                    soft++;
                }
            }
            if (boils < 6 && random.nextInt(3) == 0) {
                // a sand boil: a low cone of sand thrown up round a spout of muddy water
                BlockPos vent = top.above();
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos rim = vent.relative(d);
                    if (level.getBlockState(rim).canBeReplaced() && level.getBlockState(rim).getFluidState().isEmpty()
                            && level.getBlockState(rim.below()).isFaceSturdy(level, rim.below(), net.minecraft.core.Direction.UP)) {
                        level.setBlockAndUpdate(rim, Blocks.SAND.defaultBlockState());
                    }
                }
                level.sendParticles(ParticleTypes.SPLASH, vent.getX() + 0.5, vent.getY() + 0.3, vent.getZ() + 0.5, 40, 0.15, 0.8, 0.15, 0.4);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                        vent.getX() + 0.5, vent.getY() + 0.5, vent.getZ() + 0.5, 20, 0.2, 0.6, 0.2, 0.2);
                boils++;
            }
        }
        if (soft > 0) {
            player.displayClientMessage(Component.translatable("message.reikos_weather.liquefaction").withStyle(st -> st.withColor(0xC8A070)), false);
            WeatherAdvancements.award(player, "liquefaction");
        }
    }

    private static boolean nearWater(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 1, 3))) {
            if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Rocks off the cliffs, loose sand and gravel sliding, windows breaking. */
    private static void shakeLoose(ServerLevel level, ServerPlayer player, float felt, RandomSource random) {
        int tries = Math.round(60 * felt);
        for (int i = 0; i < tries; i++) {
            int x = player.getBlockX() + random.nextInt(65) - 32, z = player.getBlockZ() + random.nextInt(65) - 32;
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
            BlockState s = level.getBlockState(top);
            if (s.getBlock() instanceof FallingBlock && level.getBlockState(top.below()).isAir()) {
                FallingBlockEntity.fall(level, top, s);
            } else if ((s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(BlockTags.DIRT)) && random.nextFloat() < 0.3F) {
                // over the edge of a cliff or a steep bank
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + d.getStepX(), z + d.getStepZ()) < top.getY() - 3) {
                        FallingBlockEntity rock = FallingBlockEntity.fall(level, top, s.is(BlockTags.DIRT) ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                        rock.setDeltaMovement(d.getStepX() * 0.15, 0.0, d.getStepZ() * 0.15);
                        rock.setHurtsEntities(2.0F, 20);
                        rock.hurtMarked = true;
                        break;
                    }
                }
            }
        }
        if (felt > 0.5F) {
            // windows rattle and some give way
            for (BlockPos p : BlockPos.randomBetweenClosed(random, Math.round(80 * felt), player.getBlockX() - 16, player.getBlockY() - 6,
                    player.getBlockZ() - 16, player.getBlockX() + 16, player.getBlockY() + 10, player.getBlockZ() + 16)) {
                BlockState s = level.getBlockState(p);
                if ((s.is(Blocks.GLASS_PANE) || s.is(Blocks.GLASS) || s.is(com.reiko.weather.registry.ModBlocks.FROSTED_GLASS.get())
                        || s.is(com.reiko.weather.registry.ModBlocks.FROSTED_GLASS_PANE.get())) && random.nextFloat() < 0.25F) {
                    level.destroyBlock(p, false);
                }
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
    }

    // ------------------------------------------------------------------ tsunamis

    private static void tickTsunami(ServerLevel level, WeatherState state) {
        long now = level.getGameTime();
        if (pending != null && now >= pending.arrives()) {
            Tsunami t = pending;
            pending = null;
            state.tsunamiUntil = now + 900;
            state.setDirty();
            for (ServerPlayer player : level.players()) {
                if (Math.sqrt(player.distanceToSqr(t.x(), player.getY(), t.z())) < 2500) {
                    player.displayClientMessage(Component.translatable("message.reikos_weather.tsunami").withStyle(s -> s.withColor(0x3399FF).withBold(true)), true);
                    inundate(level, state, player, t.height());
                }
            }
        }
        // the wave drains back out after a while
        if (now > state.tsunamiUntil && !state.tsunamiBlocks.isEmpty() && now % 2L == 0L) {
            for (int i = 0; i < 12 && !state.tsunamiBlocks.isEmpty(); i++) {
                BlockPos p = BlockPos.of(state.tsunamiBlocks.removeLong(state.tsunamiBlocks.size() - 1));
                state.setDirty();
                if (level.isLoaded(p) && level.getFluidState(p).isSource() && level.getFluidState(p).is(FluidTags.WATER)) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** The sea surges up over all the low ground near the coast round a player, up to {@code height} blocks above sea level. */
    private static void inundate(ServerLevel level, WeatherState state, ServerPlayer player, float height) {
        int sea = level.getSeaLevel(), top = sea + Math.round(height);
        for (int dx = -64; dx <= 64; dx += 3) {
            for (int dz = -64; dz <= 64; dz += 3) {
                int x = player.getBlockX() + dx, z = player.getBlockZ() + dz;
                if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (ground <= sea || ground > top) continue;
                // only within reach of the sea
                boolean coastal = false;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (level.getBiome(new BlockPos(x + d.getStepX() * 40, sea, z + d.getStepZ() * 40)).is(BiomeTags.IS_OCEAN)
                            || level.getBiome(new BlockPos(x + d.getStepX() * 20, sea, z + d.getStepZ() * 20)).is(BiomeTags.IS_BEACH)) {
                        coastal = true;
                        break;
                    }
                }
                BlockPos p = new BlockPos(x, ground, z);
                if (!coastal || !level.getBlockState(p).canBeReplaced() || WeatherHazards.protectedBySandbags(level, p)) continue;
                level.destroyBlock(p, true);
                level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
                state.tsunamiBlocks.add(p.asLong());
                state.setDirty();
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.WEATHER, 4.0F, 0.4F);
    }

    public static void reset() {
        pending = null;
    }

    private Earthquakes() {}
}
