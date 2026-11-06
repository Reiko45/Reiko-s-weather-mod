package com.reiko.weather.compat.jade;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.compat.BlockInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade tooltips: battery charge, probe readings, rain barrel level, station mode and so on. Only loaded by Jade. */
@WailaPlugin
public class ReikosWeatherJadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID = ReikosWeather.id("block_info");

    private enum Provider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            for (Component line : BlockInfo.lines(accessor.getBlockState())) {
                tooltip.add(line);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        for (Class<? extends Block> block : BlockInfo.BLOCKS) {
            registration.registerBlockComponent(Provider.INSTANCE, block);
        }
    }
}
