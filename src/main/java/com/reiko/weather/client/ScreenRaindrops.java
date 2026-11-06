package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Raindrops landing on the "lens" when you look up into the rain, then trickling down and drying. */
public final class ScreenRaindrops {
    public static final ResourceLocation ID = ReikosWeather.id("raindrops");
    private static final ResourceLocation TEXTURE = ReikosWeather.id("textures/gui/raindrop.png");
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Drop> DROPS = new ArrayList<>();

    private static final class Drop {
        float x, y, size, slide;
        int age, life;
    }

    public static void tick(Minecraft mc) {
        DROPS.removeIf(d -> ++d.age > d.life);
        for (Drop d : DROPS) {
            d.y += d.slide;
            d.slide *= 0.97F;
        }
        if (mc.player == null || mc.level == null || mc.isPaused() || !ClientConfig.SCREEN_RAINDROPS.get() || Accessibility.screenEffects() <= 0.0F
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float rain = ClientWeather.rain();
        float up = Mth.clamp((-mc.player.getXRot() - 15.0F) / 45.0F, 0.0F, 1.0F);
        BlockPos eyes = BlockPos.containing(mc.player.getEyePosition());
        if (rain < 0.1F || up <= 0 || !mc.level.canSeeSky(eyes)
                || mc.level.getBiome(eyes).value().getPrecipitationAt(eyes) != Biome.Precipitation.RAIN) {
            return;
        }
        float wanted = rain * up * 0.8F * Accessibility.screenEffects();
        while (wanted > 0 && DROPS.size() < 80) {
            if (RANDOM.nextFloat() < wanted) {
                Drop d = new Drop();
                d.x = RANDOM.nextFloat();
                d.y = RANDOM.nextFloat();
                d.size = 6 + RANDOM.nextFloat() * 10;
                d.slide = RANDOM.nextFloat() < 0.3F ? 0.002F + RANDOM.nextFloat() * 0.004F : 0.0F;
                d.life = 40 + RANDOM.nextInt(60);
                DROPS.add(d);
            }
            wanted -= 1.0F;
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (DROPS.isEmpty()) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Drop d : DROPS) {
            float life = d.age / (float) d.life;
            float alpha = 0.55F * Mth.clamp(Math.min(life * 8.0F, (1.0F - life) * 2.5F), 0.0F, 1.0F);
            int size = Math.round(d.size);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(TEXTURE, Math.round(d.x * w - size / 2.0F), Math.round(d.y * h - size / 2.0F), size, size, 0, 0, 16, 16, 16, 16);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    public static void reset() {
        DROPS.clear();
    }

    private ScreenRaindrops() {}
}
