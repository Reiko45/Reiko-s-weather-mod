package com.reiko.weather.client;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.SkyPhenomena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** On hot, sunny days the distant ground shimmers (a small post-processing pass). */
public final class HeatShimmer {
    private static PostChain chain;
    private static boolean failed;
    private static int width, height;
    private static float strength, target;

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !ClientConfig.HEAT_SHIMMER.get() || mc.level.dimension() != Level.OVERWORLD) {
            target = 0.0F;
        } else if (mc.level.getGameTime() % 20 == 0) {
            float sun = SkyPhenomena.sunHeight(mc.level, 1.0F);
            BlockPos eyes = BlockPos.containing(mc.player.getEyePosition());
            float celsius = Temperature.ambient(mc.level, eyes, ClientWeather.local());
            boolean sunny = sun > 0.3F && ClientWeather.rain() < 0.1F && ClientWeather.sunOcclusion() < 0.4F && ClientWeather.exposure() > 0.5F;
            target = sunny ? Mth.clamp((celsius - 28.0F) / 12.0F, 0.0F, 1.0F) : 0.0F;
        }
        strength = Mth.approach(strength, target, 0.01F);
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || strength < 0.02F || failed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                        ReikosWeather.id("shaders/post/heat_shimmer.json"));
                chain.resize(w, h);
                width = w;
                height = h;
            } catch (Exception e) {
                failed = true;
                ReikosWeather.LOGGER.warn("Couldn't load the heat shimmer shader; turning it off", e);
                return;
            }
        }
        if (w != width || h != height) {
            chain.resize(w, h);
            width = w;
            height = h;
        }
        chain.setUniform("Strength", strength);
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        mc.getMainRenderTarget().bindWrite(false);
    }

    public static void reset() {
        strength = target = 0.0F;
    }

    private HeatShimmer() {}
}
