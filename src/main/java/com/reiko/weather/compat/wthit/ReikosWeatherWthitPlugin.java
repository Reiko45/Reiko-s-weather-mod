package com.reiko.weather.compat.wthit;

import com.reiko.weather.compat.BlockInfo;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** WTHIT tooltips (declared in waila_plugins.json, so only WTHIT loads it). */
public class ReikosWeatherWthitPlugin implements IWailaClientPlugin {
    private enum Provider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            for (Component line : BlockInfo.lines(accessor.getBlockState())) {
                tooltip.addLine(line);
            }
        }
    }

    @Override
    public void register(IClientRegistrar registrar) {
        for (Class<? extends Block> block : BlockInfo.BLOCKS) {
            registrar.body(Provider.INSTANCE, block);
        }
    }
}
