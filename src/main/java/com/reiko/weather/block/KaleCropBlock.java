package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModItems;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Kale: a hardy winter green. Frost doesn't hurt it and it keeps growing in the cold, but it sulks in the heat. */
public class KaleCropBlock extends CropBlock {
    public static final MapCodec<KaleCropBlock> CODEC = simpleCodec(KaleCropBlock::new);

    public KaleCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.KALE.get();
    }
}
