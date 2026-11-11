package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's settings page (Mods > Reiko's Weather > Config): one-click presets for the visual
 * settings, plus the full client and server settings.
 */
public class ConfigHomeScreen extends Screen {
    private final ModContainer mod;
    private final Screen parent;
    private Component status = Component.empty();

    public ConfigHomeScreen(ModContainer mod, Screen parent) {
        super(Component.translatable("screen.reikos_weather.config"));
        this.mod = mod;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 4 + 10;
        addRenderableWidget(Button.builder(Component.translatable("screen.reikos_weather.preset.performance"), b -> apply(Preset.PERFORMANCE))
                .bounds(cx - 155, y, 100, 20).tooltip(Tooltip.create(Component.translatable("screen.reikos_weather.preset.performance.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.reikos_weather.preset.default"), b -> apply(Preset.DEFAULT))
                .bounds(cx - 50, y, 100, 20).tooltip(Tooltip.create(Component.translatable("screen.reikos_weather.preset.default.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.reikos_weather.preset.cinematic"), b -> apply(Preset.CINEMATIC))
                .bounds(cx + 55, y, 100, 20).tooltip(Tooltip.create(Component.translatable("screen.reikos_weather.preset.cinematic.tooltip"))).build());
        y += 48;
        addRenderableWidget(Button.builder(Component.translatable("screen.reikos_weather.config.all"), b -> minecraft.setScreen(new ConfigurationScreen(mod, this)))
                .bounds(cx - 100, y, 200, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx - 100, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 4 - 24, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.reikos_weather.preset.hint").withStyle(ChatFormatting.GRAY), width / 2, height / 4 - 8, 0xFFFFFF);
        g.drawCenteredString(font, status, width / 2, height / 4 + 38, 0x80FF80);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void apply(Preset preset) {
        preset.apply();
        ClientConfig.SPEC.save();
        status = Component.translatable("screen.reikos_weather.preset.applied", Component.translatable("screen.reikos_weather.preset." + preset.id));
    }

    public enum Preset {
        PERFORMANCE("performance"), DEFAULT("default"), CINEMATIC("cinematic");

        final String id;

        Preset(String id) {
            this.id = id;
        }

        public void apply() {
            // start from the defaults, then adjust
            for (ModConfigSpec.BooleanValue v : new ModConfigSpec.BooleanValue[]{ClientConfig.FOG, ClientConfig.SKY_EFFECTS, ClientConfig.RAINBOWS,
                    ClientConfig.AURORAS, ClientConfig.STORM_CLOUDS, ClientConfig.WET_GROUND, ClientConfig.MIST, ClientConfig.HEAT_LIGHTNING,
                    ClientConfig.SEASONAL_COLORS, ClientConfig.FALLING_LEAVES, ClientConfig.BREATH, ClientConfig.FOOTPRINTS, ClientConfig.SCREEN_RAINDROPS,
                    ClientConfig.HEAT_SHIMMER, ClientConfig.DRIPPING_LEAVES, ClientConfig.SHOOTING_STARS, ClientConfig.SKY_OPTICS, ClientConfig.HOARFROST,
                    ClientConfig.FIREFLIES, ClientConfig.BLOSSOMS, ClientConfig.BIRD_FLOCKS, ClientConfig.WHITECAPS,
                    ClientConfig.TOWERING_CLOUDS, ClientConfig.FOG_BANKS, ClientConfig.SMOKE_COLUMNS, ClientConfig.DIAMOND_DUST,
                    ClientConfig.STATIC_SPARKS, ClientConfig.MIRAGES, ClientConfig.STEAM_FOG, ClientConfig.WATERFALL_SPRAY, ClientConfig.DUSTY_RAIN,
                    ClientConfig.RED_SPRITES, ClientConfig.NOCTILUCENT_CLOUDS, ClientConfig.STORM_CLOUD_SHAPES, ClientConfig.MOONBOWS, ClientConfig.FOGBOWS,
                    ClientConfig.VIRGA, ClientConfig.INVERSION_HAZE, ClientConfig.SINGING_ICE, ClientConfig.SEA_SPARKLE, ClientConfig.DOUBLE_RAINBOWS, ClientConfig.GLORIES, ClientConfig.LENTICULAR_CLOUDS, ClientConfig.GREEN_FLASH, ClientConfig.TWILIGHT_BANDS, ClientConfig.LIGHT_PILLARS, ClientConfig.IRIDESCENT_CLOUDS,
                    ClientConfig.DEW, ClientConfig.POLLEN, ClientConfig.MILKY_WAY, ClientConfig.ANTICREPUSCULAR_RAYS, ClientConfig.SKY_ARCS, ClientConfig.CLOUD_FORMS, ClientConfig.NACREOUS_CLOUDS, ClientConfig.SPIDER_LIGHTNING, ClientConfig.RED_SKY, ClientConfig.ALGAL_BLOOM_TINT, ClientConfig.AIRGLOW, ClientConfig.FUNNEL_CLOUDS, ClientConfig.SNOW_GLARE, ClientConfig.GROUND_STEAM, ClientConfig.FATA_MORGANA, ClientConfig.UPPER_LIGHTNING, ClientConfig.STEVE, ClientConfig.GEGENSCHEIN, ClientConfig.PLANETS, ClientConfig.EARTHSHINE, ClientConfig.HARVEST_MOON, ClientConfig.DEWBOWS, ClientConfig.SPRAY_BOWS, ClientConfig.BANNER_CLOUDS, ClientConfig.BLUE_HAZE, ClientConfig.WOODSMOKE, ClientConfig.ALTITUDE_SEASONS}) {
                v.set(v.getDefault());
            }
            ClientConfig.PARTICLE_DENSITY.set(ClientConfig.PARTICLE_DENSITY.getDefault());
            ClientConfig.STORM_CLOUD_RANGE.set(ClientConfig.STORM_CLOUD_RANGE.getDefault());
            switch (this) {
                case PERFORMANCE -> {
                    ClientConfig.PARTICLE_DENSITY.set(0.5);
                    ClientConfig.STORM_CLOUD_RANGE.set(480);
                    for (ModConfigSpec.BooleanValue v : new ModConfigSpec.BooleanValue[]{ClientConfig.WET_GROUND, ClientConfig.FOOTPRINTS,
                            ClientConfig.SCREEN_RAINDROPS, ClientConfig.HEAT_SHIMMER, ClientConfig.DRIPPING_LEAVES, ClientConfig.HOARFROST,
                            ClientConfig.BLOSSOMS, ClientConfig.WHITECAPS, ClientConfig.FALLING_LEAVES, ClientConfig.BREATH,
                            ClientConfig.STEAM_FOG, ClientConfig.WATERFALL_SPRAY, ClientConfig.STORM_CLOUD_SHAPES, ClientConfig.LIGHT_PILLARS,
                            ClientConfig.SEA_SPARKLE, ClientConfig.INVERSION_HAZE, ClientConfig.DEW, ClientConfig.POLLEN,
                            ClientConfig.CLOUD_FORMS, ClientConfig.SPIDER_LIGHTNING}) {
                        v.set(false);
                    }
                }
                case CINEMATIC -> {
                    ClientConfig.PARTICLE_DENSITY.set(1.6);
                    ClientConfig.STORM_CLOUD_RANGE.set(2048);
                }
                case DEFAULT -> {}
            }
        }
    }
}
