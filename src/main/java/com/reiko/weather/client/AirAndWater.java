package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Little things in the air and on the water: dew glinting on the grass at dawn, yellow pollen drifting out of the
 * woods on dry spring days (and lying on still water), and flecks of foam torn off the surf in a gale.
 */
public final class AirAndWater {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float pollen, lightPollution, sampledLight, blueHaze, forest;
    private static final java.util.List<BlockPos> FIRES = new java.util.ArrayList<>();
    private static long fireScan = -1000L;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed()) {
            pollen = 0.0F;
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // light pollution: how much lamplight there is round about (villages, bases), sampled a little at a time
        if (level.getGameTime() % 5L == 0L) {
            int x = Mth.floor(cam.x) + RANDOM.nextInt(97) - 48, z = Mth.floor(cam.z) + RANDOM.nextInt(97) - 48;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            float light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(x, y, z)) / 15.0F;
            sampledLight = Mth.lerp(0.05F, sampledLight, light);
            lightPollution = Mth.clamp((sampledLight - 0.04F) * 6.0F, 0.0F, 0.9F);
        }
        // blue haze: on hot summer days big forests breathe out a mist of plant oils that turns to a bluish haze
        long hour = Math.floorMod(level.getDayTime(), 24000L);
        float hazeTarget = 0.0F;
        if (ClientConfig.BLUE_HAZE.get() && ClientWeather.airTemperature() > 24.0F && ClientWeather.rain() < 0.05F && ClientWeather.overcast() < 0.5F
                && hour > 1500L && hour < 11500L) {
            if (level.getGameTime() % 40L == 0L) forest = forestAround(level, cam);
            hazeTarget = Mth.clamp((forest - 0.35F) * 2.0F, 0.0F, 1.0F) * Mth.clamp((ClientWeather.airTemperature() - 24.0F) / 6.0F, 0.0F, 1.0F);
        }
        blueHaze = Mth.lerp(0.004F, blueHaze, hazeTarget);
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        if (ClientConfig.WOODSMOKE.get()) woodsmoke(level, cam, density);
        long time = Math.floorMod(level.getDayTime(), 24000L);
        float rain = ClientWeather.rain(), wind = ClientWeather.wind();

        // dew: clear, calm, mild dawns
        boolean dawn = time > 23000L || time < 1500L;
        if (ClientConfig.DEW.get() && dawn && rain < 0.05F && ClientWeather.overcast() < 0.4F && wind < 0.3F
                && ClientWeather.airTemperature() > 1.0F && ClientWeather.airTemperature() < 20.0F) {
            for (int i = 0; i < Math.round(6 * density); i++) {
                int x = Mth.floor(cam.x) + RANDOM.nextInt(17) - 8, z = Mth.floor(cam.z) + RANDOM.nextInt(17) - 8;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, y - 1, z);
                var s = level.getBlockState(ground);
                boolean grass = s.is(Blocks.GRASS_BLOCK) || level.getBlockState(ground.above()).is(Blocks.SHORT_GRASS);
                if (!grass || Math.abs(y - cam.y) > 6) continue;
                level.addParticle(ModParticles.DEW.get(), x + RANDOM.nextDouble(), y + 0.05 + RANDOM.nextDouble() * 0.3, z + RANDOM.nextDouble(), 0, 0, 0);
            }
        }

        // pollen: dry, breezy spring days near woodland
        boolean spring = SyncedWeather.season() == Season.SPRING;
        BlockPos at = BlockPos.containing(cam);
        var biome = level.getBiome(at);
        boolean woods = biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA);
        float target = ClientConfig.POLLEN.get() && spring && woods && rain < 0.05F && level.isDay() && wind > 0.08F && wind < 0.65F ? 1.0F : 0.0F;
        pollen = Mth.approach(pollen, target, target > pollen ? 0.004F : 0.02F);
        if (pollen > 0.05F) {
            Vec3 dir = Wind.direction(ClientWeather.windAngle());
            for (int i = 0; i < Math.round(8 * density * pollen); i++) {
                double x = cam.x + (RANDOM.nextDouble() - 0.5) * 20 - dir.x * 4, z = cam.z + (RANDOM.nextDouble() - 0.5) * 20 - dir.z * 4;
                // drifting low, among the trees
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                double y = ground + RANDOM.nextDouble() * 10;
                if (Math.abs(y - cam.y) > 12) continue;
                level.addParticle(ModParticles.POLLEN.get(), x, y, z, dir.x * 0.05 * (0.5 + wind), 0.0, dir.z * 0.05 * (0.5 + wind));
            }
            // a yellow film settling on still water
            if (RANDOM.nextFloat() < pollen * 0.5F) {
                int x = Mth.floor(cam.x) + RANDOM.nextInt(25) - 12, z = Mth.floor(cam.z) + RANDOM.nextInt(25) - 12;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (level.getFluidState(new BlockPos(x, y - 1, z)).is(FluidTags.WATER) && Math.abs(y - cam.y) < 10) {
                    level.addParticle(ModParticles.POLLEN.get(), x + RANDOM.nextDouble(), y + 0.02, z + RANDOM.nextDouble(), 0, 0, 0);
                }
            }
        }

        // foam flecks blowing off the surf onto the beach in a gale
        if (wind > 0.65F && com.reiko.weather.config.WeatherConfig.SPEC.isLoaded() && com.reiko.weather.config.WeatherConfig.SWELL.get()) {
            int sea = level.getSeaLevel();
            Vec3 dir = Wind.direction(ClientWeather.windAngle());
            for (int i = 0; i < Math.round(6 * density * (wind - 0.6F) * 3.0F); i++) {
                int x = Mth.floor(cam.x) + RANDOM.nextInt(33) - 16, z = Mth.floor(cam.z) + RANDOM.nextInt(33) - 16;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (y != sea || !level.getFluidState(new BlockPos(x, sea - 1, z)).is(FluidTags.WATER) || Math.abs(y - cam.y) > 12) continue;
                // only where the surf breaks: water with land just downwind
                BlockPos lee = BlockPos.containing(x + dir.x * 4, sea, z + dir.z * 4);
                if (level.getFluidState(lee.below()).is(FluidTags.WATER)) continue;
                level.addParticle(ModParticles.SEA_FOAM.get(), x + RANDOM.nextDouble(), sea + 0.3, z + RANDOM.nextDouble(),
                        dir.x * 0.25 * wind, 0.05, dir.z * 0.25 * wind);
            }
        }
    }

    /** The share of the country round about that's forest (sampled out to the middle distance, where the haze shows). */
    private static float forestAround(ClientLevel level, Vec3 cam) {
        int forested = 0, seen = 0;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            for (int r = 40; r <= 120; r += 40) {
                BlockPos p = BlockPos.containing(cam.x + Mth.cos(a) * r, cam.y, cam.z + Mth.sin(a) * r);
                if (!SkySights.loaded(level, p)) continue;
                var biome = level.getBiome(p);
                seen++;
                if (biome.is(net.minecraft.tags.BiomeTags.IS_FOREST) || biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)
                        || biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA)) forested++;
            }
        }
        return seen == 0 ? 0.0F : forested / (float) seen;
    }

    /**
     * Woodsmoke on a still, cold morning or evening: the ground has chilled the air next to it, warmer air lies
     * on top like a lid, and the smoke from fires and chimneys rises a little way and then spreads out flat under it
     * in a blue-grey layer.
     */
    private static void woodsmoke(ClientLevel level, Vec3 cam, float density) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean still = ClientWeather.wind() < 0.15F && ClientWeather.rain() < 0.05F;
        boolean hour = time > 22500L || time < 2500L || time > 11500L && time < 14500L;
        if (!still || !hour || ClientWeather.airTemperature() > 8.0F) return;
        long now = level.getGameTime();
        if (now - fireScan > 60L || now < fireScan) {
            fireScan = now;
            FIRES.clear();
            int cx = Mth.floor(cam.x) >> 4, cz = Mth.floor(cam.z) >> 4;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    var chunk = level.getChunk(cx + dx, cz + dz);
                    for (var be : chunk.getBlockEntities().values()) {
                        var state = be.getBlockState();
                        boolean lit = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
                        if (lit && (be instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity
                                || be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)) {
                            FIRES.add(be.getBlockPos());
                            if (FIRES.size() >= 24) return;
                        }
                    }
                }
            }
        }
        for (BlockPos fire : FIRES) {
            if (RANDOM.nextFloat() > 0.35F * density) continue;
            // the smoke rises to the lid and spreads out sideways under it
            double lid = fire.getY() + 7.0 + RANDOM.nextDouble() * 2.5;
            level.addParticle(ModParticles.SMOKE_HAZE.get(), fire.getX() + 0.5 + RANDOM.nextGaussian() * 3.0, lid, fire.getZ() + 0.5 + RANDOM.nextGaussian() * 3.0,
                    RANDOM.nextGaussian() * 0.015, 0.0, RANDOM.nextGaussian() * 0.015);
        }
    }

    /** How thick the summer blue haze over the forests is, 0 to 1. */
    public static float blueHaze() {
        return blueHaze;
    }

    /** How much lamplight washes out the night sky here, 0 (dark) to 0.9 (in a lit village). */
    public static float lightPollution() {
        return lightPollution;
    }

    /** How far the yellow pollen haze has built up, 0 to 1 (it tints the air a little). */
    public static float pollen() {
        return pollen;
    }

    /** How big the swell running ahead of any hurricane is here, 0 to about 0.5. */
    public static float swell(double x, double z) {
        float swell = 0.0F;
        for (StormCell cell : SyncedWeather.cells()) {
            if (cell.type != WeatherType.HURRICANE) continue;
            double d = cell.distanceTo(x, z);
            if (d < 900) swell = Math.max(swell, 0.5F * cell.strength() * (float) (1.0 - d / 900));
        }
        return swell;
    }

    private AirAndWater() {}
}
