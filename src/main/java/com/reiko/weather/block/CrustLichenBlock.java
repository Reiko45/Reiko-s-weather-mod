package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;

/**
 * Crust lichen growing flat on rocks by the sea, in bands set by how often the spray reaches them: black tar lichen
 * just above the waterline, then a band of bright orange higher up, out of reach of all but the biggest waves.
 */
public class CrustLichenBlock extends MultifaceBlock {
    public static final MapCodec<CrustLichenBlock> CODEC = simpleCodec(CrustLichenBlock::new);
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public CrustLichenBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CrustLichenBlock> codec() {
        return CODEC;
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return spreader;
    }
}
