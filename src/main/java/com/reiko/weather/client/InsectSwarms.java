package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.InsectHatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Swarms over the water: midges dancing in little clouds over ponds, streams and damp ground on still, warm evenings,
 * and on spring and early-summer evenings the mayfly hatch over the rivers, with the fish rising to them.
 */
public final class InsectSwarms {
    private static final RandomSource RANDOM = RandomSource.create();
    private record Swarm(Vec3 at, boolean mayflies, double water, long until) {}
    private static final List<Swarm> SWARMS = new ArrayList<>();

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed()
                || !ClientConfig.INSECT_SWARMS.get() || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            SWARMS.clear();
            return;
        }
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean raining = ClientWeather.rain() > 0.05F;
        boolean mayflies = InsectHatches.mayflies(SyncedWeather.season(), SyncedWeather.dayOfSeason(), SyncedWeather.daysPerSeason(),
                level.getDayTime(), ClientWeather.wind(), raining);
        boolean midges = InsectHatches.midges(SyncedWeather.season(), level.getDayTime(), ClientWeather.wind(), raining, ClientWeather.airTemperature());
        SWARMS.removeIf(s -> s.until() < now || s.at().distanceToSqr(cam) > 40 * 40 || (s.mayflies() ? !mayflies : !midges));
        if ((mayflies || midges) && now % 10 == 0 && SWARMS.size() < 10) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 10; i++) {
                p.set(cam.x + RANDOM.nextInt(41) - 20, cam.y + RANDOM.nextInt(13) - 8, cam.z + RANDOM.nextInt(41) - 20);
                if (!SkySights.loaded(level, p) || !level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) continue;
                boolean river = level.getBiome(p).is(BiomeTags.IS_RIVER);
                boolean hatch = mayflies && (river || RANDOM.nextInt(3) == 0);
                if (!hatch && !midges) continue;
                double y = p.getY() + 1.0 + (hatch ? 1.2 : 0.8) + RANDOM.nextDouble() * 1.2;
                SWARMS.add(new Swarm(new Vec3(p.getX() + RANDOM.nextDouble(), y, p.getZ() + RANDOM.nextDouble()), hatch, p.getY() + 0.9, now + 300 + RANDOM.nextInt(400)));
                break;
            }
        }
        for (Swarm s : SWARMS) {
            Vec3 a = s.at();
            int n = s.mayflies() ? 2 : 3;
            for (int i = 0; i < n; i++) {
                double r = s.mayflies() ? 1.8 : 0.7;
                level.addParticle(s.mayflies() ? ModParticles.MAYFLY.get() : ModParticles.MIDGE.get(),
                        a.x + (RANDOM.nextDouble() - 0.5) * r, a.y + (RANDOM.nextDouble() - 0.5) * r * 0.6, a.z + (RANDOM.nextDouble() - 0.5) * r, 0, 0, 0);
            }
            // under a mayfly hatch, the fish rise: a ring spreading on the water where one took a fly
            if (s.mayflies() && RANDOM.nextInt(30) == 0) {
                double x = a.x + (RANDOM.nextDouble() - 0.5) * 3, z = a.z + (RANDOM.nextDouble() - 0.5) * 3;
                if (level.getFluidState(BlockPos.containing(x, s.water() - 0.5, z)).is(FluidTags.WATER)) {
                    level.addParticle(ModParticles.RIPPLE.get(), x, s.water() + 0.02, z, 0, 0, 0);
                    level.addParticle(ParticleTypes.SPLASH, x, s.water() + 0.05, z, 0, 0.05, 0);
                }
            }
        }
    }

    public static void reset() {
        SWARMS.clear();
    }

    /** For the F3 screen: how many swarms there are, and of which kind. */
    static String debug() {
        long hatch = SWARMS.stream().filter(Swarm::mayflies).count();
        return (SWARMS.size() - hatch) + " midge " + hatch + " mayfly";
    }

    private InsectSwarms() {}
}
