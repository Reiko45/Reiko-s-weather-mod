package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What comes down out of thunderstorms: microbursts (a violent downdraft that hits the ground and spreads out,
 * flattening everything in a circle), haboobs (a desert storm's outflow, pushing a wall of dust ahead of it) and
 * heat bursts (a dying storm's collapse at night, bringing down hot, dry air in sudden gusts).
 */
public final class Downdrafts {
    /** Storm cells that have already thrown out a haboob or a heat burst. */
    private static final IntOpenHashSet DONE = new IntOpenHashSet();

    /** Per player, every tick. */
    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local) {
        if (!WeatherConfig.MICROBURSTS.get() || !local.type().vanillaThunder || local.intensity() < 0.8F) {
            return;
        }
        RandomSource random = level.random;
        boolean severe = local.type() == WeatherType.SUPERCELL || local.type() == WeatherType.DERECHO;
        if (random.nextInt(severe ? 2500 : 5000) == 0) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double distance = 8 + random.nextDouble() * 34;
            microburst(level, BlockPos.containing(player.getX() + Mth.cos(angle) * distance, 0, player.getZ() + Mth.sin(angle) * distance),
                    10 + random.nextInt(7));
        }
    }

    /** A microburst comes down at a column: everything within {@code radius} is flattened outward from the middle. */
    public static void microburst(ServerLevel level, BlockPos column, int radius) {
        if (!level.isLoaded(column)) return;
        RandomSource random = level.random;
        BlockPos center = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        level.playSound(null, center, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.WEATHER, 6.0F, 0.5F);
        level.playSound(null, center, com.reiko.weather.registry.ModSounds.AVALANCHE.get(), SoundSource.WEATHER, 4.0F, 0.7F);
        for (int i = 0; i < 24; i++) {
            float a = i * Mth.TWO_PI / 24.0F;
            level.sendParticles(ParticleTypes.GUST, center.getX() + Mth.cos(a) * radius * 0.6, center.getY() + 0.5,
                    center.getZ() + Mth.sin(a) * radius * 0.6, 1, 0.5, 0.2, 0.5, 0.0);
            level.sendParticles(ParticleTypes.SPLASH, center.getX() + Mth.cos(a) * radius * 0.4, center.getY() + 0.3,
                    center.getZ() + Mth.sin(a) * radius * 0.4, 6, 1.0, 0.1, 1.0, 0.2);
        }
        // everything out in the open is slammed down and outward
        for (Entity entity : level.getEntities((Entity) null, new AABB(center).inflate(radius * 1.4, 12, radius * 1.4), e -> !e.isSpectator())) {
            double dx = entity.getX() - center.getX() - 0.5, dz = entity.getZ() - center.getZ() - 0.5;
            double d = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            if (d > radius * 1.4 || !level.canSeeSky(entity.blockPosition().above())) continue;
            double push = 1.6 * (1.0 - d / (radius * 1.5));
            entity.setDeltaMovement(entity.getDeltaMovement().add(dx / d * push, -0.4, dz / d * push));
            entity.hurtMarked = true;
            if (entity instanceof ServerPlayer p && d < radius) WeatherAdvancements.award(p, "downburst");
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 64 * 64) {
                p.displayClientMessage(Component.translatable("message.reikos_weather.microburst").withStyle(s -> s.withColor(0x9FB8D0).withBold(true)), true);
            }
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || random.nextFloat() < 0.25F) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                // the canopy: leaves stripped off the treetops
                pos.set(x, top - 1, z);
                if (level.getBlockState(pos).is(BlockTags.LEAVES) && random.nextFloat() < 0.35F) {
                    level.destroyBlock(pos, false);
                    continue;
                }
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                pos.set(x, ground, z);
                BlockState plant = level.getBlockState(pos);
                if (plant.getBlock() instanceof CropBlock crop) {
                    // laid flat: the crop is knocked back hard
                    level.setBlockAndUpdate(pos, crop.getStateForAge(Math.max(0, crop.getAge(plant) - 3)));
                } else if (plant.is(Blocks.TALL_GRASS) || plant.is(Blocks.LARGE_FERN) || plant.is(BlockTags.FLOWERS) || plant.is(Blocks.SHORT_GRASS)) {
                    if (random.nextFloat() < 0.6F) level.destroyBlock(pos, random.nextFloat() < 0.3F);
                }
            }
        }
        if (WeatherConfig.STORM_DEBRIS.get()) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 48 * 48) StormDebris.drop(level, p, random);
            }
        }
    }

    /** Every few seconds: haboobs out ahead of desert storms, and heat bursts as storms die at night. */
    static void tick(ServerLevel level, WeatherState state) {
        RandomSource random = level.random;
        DONE.removeIf(id -> state.cells.stream().noneMatch(c -> c.id == id));
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        for (StormCell cell : new java.util.ArrayList<>(state.cells)) {
            if (!cell.type.vanillaThunder || cell.type == WeatherType.PYROCUMULUS || DONE.contains(cell.id)) continue;
            Climate climate = Climate.at(level, BlockPos.containing(cell.x, level.getSeaLevel(), cell.z));
            if (WeatherConfig.HABOOBS.get() && climate == Climate.ARID && cell.strength() > 0.7F && random.nextInt(5) == 0) {
                // the outflow races out ahead of the storm, pushing a wall of dust
                double speed = Math.sqrt(cell.vx * cell.vx + cell.vz * cell.vz);
                double ux = speed > 1.0E-6 ? cell.vx / speed : Wind.direction(state.windAngle).x, uz = speed > 1.0E-6 ? cell.vz / speed : Wind.direction(state.windAngle).z;
                double out = Math.max(0.18, speed * 2.5);
                WeatherManager.spawnCell(level, state, WeatherType.HABOOB, cell.x + ux * (cell.radius + 40), cell.z + uz * (cell.radius + 40),
                        60.0F + random.nextFloat() * 25.0F, ux * out, uz * out, 2000 + random.nextInt(1600), 200, false);
                DONE.add(cell.id);
                continue;
            }
            if (WeatherConfig.HEAT_BURSTS.get() && sun < -0.1F && cell.maxAge - cell.age < 2400 && random.nextInt(20) == 0) {
                WeatherManager.spawnCell(level, state, WeatherType.HEAT_BURST, cell.x, cell.z, 90.0F + random.nextFloat() * 50.0F,
                        cell.vx * 0.5, cell.vz * 0.5, 1600 + random.nextInt(1000), 160, false);
                DONE.add(cell.id);
                for (ServerPlayer p : level.players()) {
                    if (cell.distanceTo(p.getX(), p.getZ()) < cell.radius + 150) {
                        p.displayClientMessage(Component.translatable("message.reikos_weather.heat_burst").withStyle(s -> s.withColor(0xFF9955)), true);
                    }
                }
            }
        }
        // a desert-wide thunderstorm throws out haboobs toward whoever is in the desert
        if (WeatherConfig.HABOOBS.get() && state.type.vanillaThunder && state.elapsed >= 1200 && state.elapsed < 1300) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || Climate.at(level, player.blockPosition()) != Climate.ARID) continue;
                Vec3 wind = Wind.direction(state.windAngle);
                WeatherManager.spawnCell(level, state, WeatherType.HABOOB, player.getX() - wind.x * 300, player.getZ() - wind.z * 300,
                        70.0F, wind.x * 0.2, wind.z * 0.2, 3600, 200, false);
            }
        }
    }

    public static void reset() {
        DONE.clear();
    }

    private Downdrafts() {}
}
