package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.StormSigns;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Swallows flying low: in fine weather they hunt high up, out of sight, but as a storm comes on, the damp, heavy air
 * brings the insects down, and the swallows follow them, skimming the grass and the water.
 */
public final class Swallows {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float approach;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed() || !ClientConfig.SWALLOWS.get()
                || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            approach = 0.0F;
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (level.getGameTime() % 20 == 0) {
            WeatherType[] f = SyncedWeather.forecast();
            approach = StormSigns.approach(SyncedWeather.cells(), cam.x, cam.z, SyncedWeather.type(), f.length > 0 ? f[0] : null, SyncedWeather.remaining());
        }
        Season season = SyncedWeather.season();
        boolean fit = approach > 0.3F && season != Season.WINTER && ClientWeather.climate() != Climate.COLD && ClientWeather.rain() < 0.1F
                && SkyPhenomena.sunHeight(level, 1.0F) > 0.1F;
        if (!fit || RANDOM.nextFloat() > 0.05F * approach) return;
        // over open fields and water
        for (int i = 0; i < 4; i++) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(41) - 20, z = Mth.floor(cam.z) + RANDOM.nextInt(41) - 20;
            BlockPos column = new BlockPos(x, 0, z);
            if (!SkySights.loaded(level, column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            var ground = level.getBlockState(top.below());
            if (ground.is(BlockTags.LEAVES) || !level.canSeeSky(top) || Math.abs(top.getY() - cam.y) > 24) continue;
            float a = RANDOM.nextFloat() * Mth.TWO_PI;
            level.addParticle(ModParticles.SWALLOW.get(), x + 0.5, top.getY() + 2.0, z + 0.5, Mth.cos(a), 0.0, Mth.sin(a));
            break;
        }
    }

    static float approach() {
        return approach;
    }

    private Swallows() {}
}
