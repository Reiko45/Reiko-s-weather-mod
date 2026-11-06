package com.reiko.weather;

import com.reiko.weather.client.BallLightningClient;
import com.reiko.weather.client.ClientEvents;
import com.reiko.weather.client.RadarScreen;
import com.reiko.weather.client.sound.AmbientSounds;
import com.reiko.weather.entity.BallLightningEntity;
import net.minecraft.client.Minecraft;
import com.reiko.weather.client.SkyEffectsRenderer;
import com.reiko.weather.client.TornadoClient;
import com.reiko.weather.entity.TornadoEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ReikosWeather.MOD_ID, dist = Dist.CLIENT)
public class ReikosWeatherClient {
    public ReikosWeatherClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(ClientEvents::registerParticles);
        modBus.addListener(ClientEvents::registerRenderers);
        modBus.addListener(ClientEvents::registerBlockColors);
        modBus.addListener(ClientEvents::registerGuiLayers);
        modBus.addListener(ClientEvents::registerModels);

        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderFog);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onFogColor);
        NeoForge.EVENT_BUS.addListener(SkyEffectsRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderStage);
        NeoForge.EVENT_BUS.addListener(AmbientSounds::onPlaySound);

        TornadoEntity.clientTicker = TornadoClient::tick;
        BallLightningEntity.clientTicker = BallLightningClient::tick;
        ClientActions.Holder.instance = (center, follow) -> Minecraft.getInstance().setScreen(new RadarScreen(center, follow));
    }
}
