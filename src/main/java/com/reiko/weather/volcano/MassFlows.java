package com.reiko.weather.volcano;

import com.reiko.weather.block.VolcanicAshBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Volcanic mass flows. A lahar is a volcanic mudflow: heavy rain soaks the loose ash on a volcano's slopes until it
 * gives way and pours down the valleys like wet concrete, flattening trees and burying everything in mud. A
 * pyroclastic flow is far worse: a scalding avalanche of ash, rock and gas collapsing off an erupting volcano at
 * the speed of a car, charring and flattening everything in its path and leaving it buried in grey ash. Both follow
 * the lie of the land downhill, the way water would.
 */
public final class MassFlows {
    /** A volcanic mudflow, a scalding avalanche of ash and gas, or a bog burst (a waterlogged peat bog sliding downhill). */
    public enum Kind { LAHAR, PYROCLASTIC, BOG }

    private static final class Front {
        final Kind kind;
        double x, z, dx, dz;
        int steps, flat, maxSteps;
        float width;
        double progress;

        Front(Kind kind, double x, double z, double dx, double dz, float width, int maxSteps) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.dx = dx;
            this.dz = dz;
            this.width = width;
            this.maxSteps = maxSteps;
        }
    }

    private static final List<Front> FRONTS = new ArrayList<>();

    /** Every tick for the overworld: rain on the volcanoes, and the flows moving. */
    static void tick(ServerLevel level, VolcanoData data) {
        if (level.getGameTime() % 20L == 0L && WeatherConfig.LAHARS.get()) {
            for (Volcano v : data.all()) maybeLahar(level, v);
        }
        if (FRONTS.isEmpty()) return;
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        for (Iterator<Front> it = FRONTS.iterator(); it.hasNext(); ) {
            Front f = it.next();
            f.progress += f.kind == Kind.PYROCLASTIC ? 1.1 : f.kind == Kind.BOG ? 0.22 : 0.35;
            boolean alive = true;
            while (f.progress >= 1.0 && alive) {
                f.progress -= 1.0;
                alive = step(level, f, griefing);
            }
            if (!alive) it.remove();
        }
    }

    public static void reset() {
        FRONTS.clear();
    }

    /** Heavy rain on a volcano now and then sets a lahar off from its upper slopes. */
    private static void maybeLahar(ServerLevel level, Volcano v) {
        VolcanoShape s = v.shape;
        BlockPos peak = new BlockPos(s.cx(), s.peakY(), s.cz());
        if (!level.isLoaded(peak) || level.getNearestPlayer(s.cx(), s.peakY(), s.cz(), 400, false) == null) return;
        LocalWeather local = WeatherManager.local(level, peak);
        if (local == null || local.profile().rain < 0.9F || local.intensity() < 0.6F || !local.type().vanillaRain) return;
        // a freshly erupted volcano, deep in loose ash, lets go far more readily
        float chance = v.phase == Volcano.Phase.ERUPTING || v.phase == Volcano.Phase.COOLING ? 1.0F / 60.0F : 1.0F / 240.0F;
        RandomSource random = level.random;
        if (random.nextFloat() > chance) return;
        float a = random.nextFloat() * Mth.TWO_PI;
        double r = s.radius() * (0.3 + random.nextDouble() * 0.2);
        start(level, Kind.LAHAR, s.cx() + Math.cos(a) * r, s.cz() + Math.sin(a) * r, Math.cos(a), Math.sin(a));
    }

    /** A column of the eruption collapses and pours down one flank. */
    static void maybePyroclastic(ServerLevel level, Volcano v, RandomSource random) {
        if (!WeatherConfig.PYROCLASTIC_FLOWS.get() || v.activity() < 0.7F || random.nextFloat() > 1.0F / 2400.0F) return;
        VolcanoShape s = v.shape;
        float a = random.nextFloat() * Mth.TWO_PI;
        double r = s.craterRadius() + 3;
        start(level, Kind.PYROCLASTIC, s.cx() + Math.cos(a) * r, s.cz() + Math.sin(a) * r, Math.cos(a), Math.sin(a));
    }

    public static void start(ServerLevel level, Kind kind, double x, double z, double dx, double dz) {
        float width = kind == Kind.LAHAR ? 2.5F + level.random.nextFloat() * 1.5F : kind == Kind.BOG ? 2.0F + level.random.nextFloat() * 1.5F
                : 4.5F + level.random.nextFloat() * 2.0F;
        FRONTS.add(new Front(kind, x, z, dx, dz, width, kind == Kind.LAHAR ? 420 : kind == Kind.BOG ? 150 : 260));
        BlockPos at = BlockPos.containing(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z), z);
        level.playSound(null, at, kind == Kind.PYROCLASTIC ? ModSounds.VOLCANO_ERUPTION.get() : ModSounds.AVALANCHE.get(), SoundSource.WEATHER,
                kind == Kind.BOG ? 4.0F : 8.0F, kind == Kind.BOG ? 0.45F : 0.6F);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, p.getY(), z) < 320 * 320) {
                p.displayClientMessage(Component.translatable(kind == Kind.LAHAR ? "message.reikos_weather.lahar" : kind == Kind.BOG ? "message.reikos_weather.bog_burst"
                                : "message.reikos_weather.pyroclastic")
                        .withStyle(st -> st.withColor(kind == Kind.LAHAR ? 0xB08A5A : kind == Kind.BOG ? 0x8A6440 : 0xD8D0C8).withBold(true)), true);
            }
        }
    }

    /** Moves a front one block downhill and does its damage there. False when it has run out. */
    private static boolean step(ServerLevel level, Front f, boolean griefing) {
        if (!level.isLoaded(BlockPos.containing(f.x, 0, f.z)) || ++f.steps > f.maxSteps) return false;
        int here = height(level, f.x, f.z);
        // the steepest way down within 60 degrees of the way it's going (it doesn't turn on a sixpence)
        double bestX = f.dx, bestZ = f.dz;
        int best = Integer.MAX_VALUE;
        double heading = Math.atan2(f.dz, f.dx);
        for (int k = -2; k <= 2; k++) {
            double a = heading + k * Math.PI / 6.0;
            int h = height(level, f.x + Math.cos(a) * 2.0, f.z + Math.sin(a) * 2.0);
            int score = h * 4 + Math.abs(k);
            if (score < best) {
                best = score;
                bestX = Math.cos(a);
                bestZ = Math.sin(a);
            }
        }
        int ahead = best / 4;
        f.flat = ahead >= here ? f.flat + 1 : Math.max(0, f.flat - 1);
        // it runs out once the ground levels off for long enough (a pyroclastic flow carries further on its own momentum)
        if (f.flat > (f.kind == Kind.PYROCLASTIC ? 20 : 12)) return false;
        f.dx = Mth.lerp(0.5, f.dx, bestX);
        f.dz = Mth.lerp(0.5, f.dz, bestZ);
        double len = Math.sqrt(f.dx * f.dx + f.dz * f.dz);
        f.dx /= len;
        f.dz /= len;
        f.x += f.dx;
        f.z += f.dz;
        BlockPos surface = BlockPos.containing(f.x, height(level, f.x, f.z), f.z);
        // a lahar that reaches a river or the sea is swallowed by it
        if (f.kind != Kind.PYROCLASTIC && level.getFluidState(surface.below()).is(FluidTags.WATER)) return false;
        if (griefing) {
            int r = Mth.ceil(f.width);
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (ox * ox + oz * oz > f.width * f.width) continue;
                    if (level.random.nextFloat() < 0.35F) bury(level, f, Mth.floor(f.x) + ox, Mth.floor(f.z) + oz);
                }
            }
        }
        hurt(level, f, surface);
        if (f.steps % 3 == 0) effects(level, f, surface);
        return true;
    }

    private static int height(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** What a front does to one column: knocks down trees and plants, and buries the ground in mud or ash. */
    private static void bury(ServerLevel level, Front f, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, height(level, x, z), z);
        if (!level.isLoaded(pos)) return;
        // trees snapped off and swept away (or charred where they stand)
        for (int h = 0; h < 8; h++) {
            BlockPos p = pos.above(h);
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE) && s.getFluidState().isEmpty() && !s.isAir() || s.is(BlockTags.FLOWERS)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            } else if (s.is(BlockTags.LOGS)) {
                if (f.kind == Kind.PYROCLASTIC) {
                    level.setBlock(p, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                            s.hasProperty(RotatedPillarBlock.AXIS) ? s.getValue(RotatedPillarBlock.AXIS) : net.minecraft.core.Direction.Axis.Y), 2);
                } else if (h > 0) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            } else if (!s.isAir() && h > 0) {
                break; // something built, or rock: leave it
            }
        }
        BlockPos top = pos.below();
        BlockState ground = level.getBlockState(top);
        if (ground.hasBlockEntity() || ground.is(BlockTags.PLANKS) || ground.is(BlockTags.DOORS) || !ground.getFluidState().isEmpty()) return;
        if (f.kind == Kind.BOG) {
            // a black slurry of peat over everything, a block deep in places where it slowed
            if (ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL)) {
                level.setBlock(top, level.random.nextInt(3) == 0 ? Blocks.MUD.defaultBlockState() : ModBlocks.PEAT.get().defaultBlockState(), 2);
            }
            if (f.steps > 50 && level.random.nextInt(5) == 0 && level.getBlockState(pos).isAir()) level.setBlock(pos, ModBlocks.PEAT.get().defaultBlockState(), 2);
        } else if (f.kind == Kind.LAHAR) {
            // mud over everything, thicker as it slows down further down the valley
            if (ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL) || ground.is(ModBlocks.VOLCANIC_ASH.get())) {
                level.setBlock(top, Blocks.MUD.defaultBlockState(), 2);
            }
            if (f.steps > 60 && level.random.nextInt(4) == 0 && level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.MUD.defaultBlockState(), 2);
        } else {
            BlockState there = level.getBlockState(pos);
            BlockState ash = ModBlocks.VOLCANIC_ASH.get().defaultBlockState();
            if (there.is(ModBlocks.VOLCANIC_ASH.get())) {
                int layers = there.getValue(VolcanicAshBlock.LAYERS);
                if (layers < 4) level.setBlock(pos, there.setValue(VolcanicAshBlock.LAYERS, layers + 1), 2);
            } else if (there.isAir() && ash.canSurvive(level, pos)) {
                level.setBlock(pos, ash.setValue(VolcanicAshBlock.LAYERS, 1 + level.random.nextInt(2)), 2);
            }
        }
    }

    private static void hurt(ServerLevel level, Front f, BlockPos at) {
        double r = f.width + 1.5;
        AABB box = new AABB(f.x - r, at.getY() - 3, f.z - r, f.x + r, at.getY() + (f.kind == Kind.PYROCLASTIC ? 12 : 4), f.z + r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator())) {
            if (f.kind != Kind.PYROCLASTIC) {
                e.hurt(level.damageSources().inWall(), f.kind == Kind.BOG ? 1.0F : 2.0F);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                e.push(f.dx * 0.25, 0.05, f.dz * 0.25);
            } else {
                e.igniteForSeconds(6.0F);
                e.hurt(level.damageSources().hotFloor(), 6.0F);
                e.push(f.dx * 0.6, 0.15, f.dz * 0.6);
            }
            e.hurtMarked = true;
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box.inflate(40, 20, 40))) {
            if (p.isAlive() && p.distanceToSqr(Vec3.atCenterOf(at)) > (f.width + 4) * (f.width + 4)) {
                WeatherAdvancements.award(p, f.kind == Kind.LAHAR ? "lahar" : f.kind == Kind.BOG ? "bog_burst" : "pyroclastic_flow");
            }
        }
    }

    private static void effects(ServerLevel level, Front f, BlockPos at) {
        if (f.kind != Kind.PYROCLASTIC) {
            far(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            f.kind == Kind.BOG ? ModBlocks.PEAT.get().defaultBlockState() : Blocks.MUD.defaultBlockState()),
                    f.x, at.getY() + 0.5, f.z, 30, f.width * 0.6, 0.4, f.width * 0.6, 0.15);
            if (f.steps % 15 == 0) level.playSound(null, at, ModSounds.AVALANCHE.get(), SoundSource.WEATHER, 3.0F, 0.5F);
        } else {
            // the billowing grey cloud rolling over the ground, with the dense, glowing avalanche underneath
            far(level, ModParticles.ASH_CLOUD.get(), f.x, at.getY() + 3.0, f.z, 7, f.width * 0.8, 2.5, f.width * 0.8, 0.06);
            far(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, f.x, at.getY() + 2.0, f.z, 6, f.width * 0.7, 2.0, f.width * 0.7, 0.03);
            far(level, ParticleTypes.LARGE_SMOKE, f.x, at.getY() + 1.0, f.z, 30, f.width, 1.5, f.width, 0.08);
            far(level, ModParticles.ASH.get(), f.x, at.getY() + 3.0, f.z, 40, f.width, 3.0, f.width, 0.05);
            if (f.steps % 6 == 0) far(level, ModParticles.EMBER.get(), f.x, at.getY() + 0.5, f.z, 10, f.width * 0.5, 0.5, f.width * 0.5, 0.02);
            if (f.steps % 20 == 0) level.playSound(null, at, ModSounds.VOLCANO_RUMBLE.get(), SoundSource.WEATHER, 6.0F, 0.7F);
        }
    }

    /** Sends particles that can be seen from far off (a flow is watched from a safe distance), not just within 32 blocks. */
    private static <T extends net.minecraft.core.particles.ParticleOptions> void far(ServerLevel level, T type, double x, double y, double z,
                                                                                int count, double dx, double dy, double dz, double speed) {
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private MassFlows() {}
}
