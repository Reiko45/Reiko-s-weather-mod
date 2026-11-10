package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Snow glare: out on a sunlit snowfield the light comes up off the snow as well as down from the sky, and after
 * a while without eye protection everything goes washed-out and dazzling (the start of snow blindness). It
 * builds up over half a minute or so and fades once you're in shade, under cloud, or wearing goggles.
 */
public final class SnowGlare {
    public static final ResourceLocation ID = ReikosWeather.id("snow_glare");
    private static final RandomSource RANDOM = RandomSource.create();
    private static float snowAround, glare, previous;

    public static void tick(Minecraft mc) {
        previous = glare;
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        float target = 0.0F;
        if (ClientConfig.SNOW_GLARE.get() && com.reiko.weather.network.SyncedWeather.managed()
                && !com.reiko.weather.compat.Compat.wearing(mc.player, EquipmentSlot.HEAD, ModItems.GOGGLES.get())) {
            // how much of the ground round about is snow or ice, sampled a few columns a tick
            BlockPos feet = mc.player.blockPosition();
            int snowy = 0;
            for (int i = 0; i < 6; i++) {
                int x = feet.getX() + RANDOM.nextInt(17) - 8, z = feet.getZ() + RANDOM.nextInt(17) - 8;
                int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockState s = mc.level.getBlockState(new BlockPos(x, y - 1, z));
                if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(BlockTags.ICE)) snowy++;
            }
            snowAround = Mth.lerp(0.05F, snowAround, snowy / 6.0F);
            float sun = com.reiko.weather.weather.SkyPhenomena.sunHeight(mc.level, 1.0F);
            float sky = (1.0F - ClientWeather.overcast()) * (1.0F - ClientWeather.rain()) * (1.0F - Mth.clamp(ClientWeather.fogDensity() * 2.0F, 0.0F, 1.0F));
            boolean open = mc.level.canSeeSky(BlockPos.containing(mc.player.getEyePosition()));
            // worst with the sun well up, looking out across the snow rather than up at the sky
            float looking = Mth.clamp((mc.player.getXRot() + 25.0F) / 40.0F, 0.0F, 1.0F);
            target = open ? Mth.clamp((sun - 0.2F) * 2.5F, 0.0F, 1.0F) * sky * Mth.clamp((snowAround - 0.4F) * 2.0F, 0.0F, 1.0F) * (0.5F + 0.5F * looking) : 0.0F;
        }
        // the eyes take a while to be overwhelmed, and a while to recover
        glare = target > glare ? Math.min(target, glare + 0.0015F) : Math.max(target, glare - 0.006F);
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float g = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), previous, glare) * Accessibility.screenEffects();
        if (g <= 0.01F || mc.options.hideGui && !mc.options.getCameraType().isFirstPerson()) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        RenderSystem.enableBlend();
        int a = Math.round(g * 0.42F * 255.0F);
        graphics.fill(0, 0, w, h, a << 24 | 0xFFFCF4);
        // brighter still toward the bottom of the view, where the light comes up off the snow
        int a2 = Math.round(g * 0.25F * 255.0F);
        graphics.fillGradient(0, h / 2, w, h, 0x00FFFFFF, a2 << 24 | 0xFFFFFF);
        RenderSystem.disableBlend();
    }

    private SnowGlare() {}
}
