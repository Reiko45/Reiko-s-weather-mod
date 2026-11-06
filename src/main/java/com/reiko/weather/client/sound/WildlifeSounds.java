package com.reiko.weather.client.sound;

import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Birds singing in the trees on spring and summer days (loudest at dawn), crickets chirping on warm
 * nights, and frogs croaking from the water after rain. Quiet in bad weather, and silent in winter.
 */
public final class WildlifeSounds {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float crickets;
    private static AmbientLoop cricketLoop;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ClientConfig.WILDLIFE_SOUNDS.get() || !ClientConfig.SOUNDS.get()
                || level.dimension() != Level.OVERWORLD || mc.isPaused()) {
            crickets = 0.0F;
            return;
        }
        Season season = SyncedWeather.season();
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        boolean calm = ClientWeather.rain() < 0.1F && ClientWeather.wind() < 0.5F;
        boolean temperate = ClientWeather.climate() == Climate.TEMPERATE;
        float outdoors = ClientWeather.exposure();

        // birdsong: spring and summer days, a dawn chorus, fewer in autumn, none in winter
        float birds = switch (season == null ? Season.SUMMER : season) {
            case SPRING -> 1.0F;
            case SUMMER -> 0.8F;
            case AUTUMN -> 0.3F;
            case WINTER -> 0.0F;
        };
        float dawn = Mth.clamp(1.0F - Math.abs(sun - 0.25F) * 3.0F, 0.0F, 1.0F);
        birds *= (sun > 0.05F ? 0.4F + 0.6F * dawn : 0.0F) * (calm ? 1.0F : 0.15F) * outdoors * (temperate ? 1.0F : 0.4F);
        if (birds > 0.02F && RANDOM.nextFloat() < birds / 30.0F) {
            singFromATree(level, mc.gameRenderer.getMainCamera().getPosition());
        }

        // frogs: croaking from the water after rain, and on spring and summer nights in swamps
        float wetAfterRain = ClientWeather.rain() < 0.3F ? ClientWeather.wetness() : 0.0F;
        boolean swampy = level.getBiome(mc.player.blockPosition()).is(net.minecraft.world.level.biome.Biomes.SWAMP)
                || level.getBiome(mc.player.blockPosition()).is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP);
        float frogNight = Mth.clamp((-sun + 0.1F) * 3.0F, 0.0F, 1.0F) * (season == Season.WINTER ? 0.0F : 1.0F) * (swampy ? 1.0F : 0.25F);
        float frogs = Math.max(wetAfterRain, frogNight) * outdoors * (season == Season.WINTER ? 0.1F : 1.0F) * (ClientWeather.climate() == Climate.COLD ? 0.2F : 1.0F);
        if (frogs > 0.05F && RANDOM.nextFloat() < frogs / 25.0F) {
            croakFromWater(level, mc.gameRenderer.getMainCamera().getPosition());
        }

        // crickets: warm nights in the grass
        float night = Mth.clamp((-sun - 0.05F) * 4.0F, 0.0F, 1.0F);
        float warmSeason = season == null ? 1.0F : season == Season.SUMMER ? 1.0F : season == Season.SPRING || season == Season.AUTUMN ? 0.4F : 0.0F;
        BlockPos below = mc.player.blockPosition().below();
        boolean grassy = level.getBlockState(below).is(Blocks.GRASS_BLOCK) || level.getBlockState(mc.player.blockPosition()).is(BlockTags.REPLACEABLE);
        float target = night * warmSeason * (calm ? 1.0F : 0.1F) * outdoors * (temperate ? 1.0F : 0.3F) * (grassy ? 0.35F : 0.15F);
        crickets = Mth.approach(crickets, target, 0.004F);
        if (crickets > 0.02F && (cricketLoop == null || cricketLoop.isStopped() || !mc.getSoundManager().isActive(cricketLoop))) {
            cricketLoop = new AmbientLoop(ModSounds.CRICKETS.get(), () -> crickets, () -> 1.0F);
            mc.getSoundManager().play(cricketLoop);
        }
    }

    private static void singFromATree(ClientLevel level, Vec3 cam) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 24; i++) {
            pos.set(cam.x + RANDOM.nextInt(33) - 16, cam.y + RANDOM.nextInt(14) - 2, cam.z + RANDOM.nextInt(33) - 16);
            if (level.getBlockState(pos).is(BlockTags.LEAVES) && level.getBlockState(pos.above()).isAir()) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, ModSounds.BIRDSONG.get(), SoundSource.AMBIENT,
                        0.5F + RANDOM.nextFloat() * 0.4F, 0.85F + RANDOM.nextFloat() * 0.35F, false);
                return;
            }
        }
    }

    private static void croakFromWater(ClientLevel level, Vec3 cam) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 24; i++) {
            pos.set(cam.x + RANDOM.nextInt(41) - 20, cam.y + RANDOM.nextInt(10) - 6, cam.z + RANDOM.nextInt(41) - 20);
            if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER) && level.getBlockState(pos.above()).isAir()) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, net.minecraft.sounds.SoundEvents.FROG_AMBIENT,
                        SoundSource.AMBIENT, 0.5F + RANDOM.nextFloat() * 0.5F, 0.8F + RANDOM.nextFloat() * 0.4F, false);
                return;
            }
        }
    }

    public static void reset() {
        crickets = 0.0F;
        cricketLoop = null;
    }

    private WildlifeSounds() {}
}
