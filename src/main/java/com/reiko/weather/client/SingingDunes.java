package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;

/**
 * Singing dunes: when dry sand avalanches down the steep lee face of a big dune, the grains shearing past each other
 * in step set up a deep, droning hum, loud enough to be heard a long way off, like a low organ note or a distant
 * propeller plane. Slide down one in dry weather and you'll set it going.
 */
public final class SingingDunes {
    private static final RandomSource RANDOM = RandomSource.create();
    private static long quietUntil;
    private static double lastY = Double.NaN;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !ClientConfig.SINGING_DUNES.get() || ClientWeather.rain() > 0.02F || ClientWeather.wetness() > 0.2F) {
            lastY = Double.NaN;
            return;
        }
        double y = player.getY();
        double drop = Double.isNaN(lastY) ? 0.0 : lastY - y;
        lastY = y;
        long now = level.getGameTime();
        if (now < quietUntil || !player.onGround() || player.isSpectator() || drop < 0.18) return;
        BlockPos under = player.blockPosition().below();
        if (!level.getBlockState(under).is(BlockTags.SAND)) return;
        var biome = level.getBiome(under);
        if (!biome.is(Biomes.DESERT) && !biome.is(BiomeTags.IS_BADLANDS) && !biome.is(BiomeTags.IS_BEACH)) return;
        // only on a big dune's steep face: sand piled well above the ground behind you
        double vx = player.getDeltaMovement().x, vz = player.getDeltaMovement().z, speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < 0.03) return;
        int behind = 0;
        for (int d = 2; d <= 8; d += 2) {
            int hx = net.minecraft.util.Mth.floor(player.getX() - vx / speed * d), hz = net.minecraft.util.Mth.floor(player.getZ() - vz / speed * d);
            if (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, hx, hz) > y + d * 0.5) behind++;
        }
        if (behind < 2) return;
        quietUntil = now + 160 + RANDOM.nextInt(120);
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.DUNE_SONG.get(), SoundSource.AMBIENT, 0.9F, 0.85F + RANDOM.nextFloat() * 0.3F,
                RANDOM, player.getX(), y, player.getZ()));
    }

    private SingingDunes() {}
}
