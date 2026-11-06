package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModItems;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Rice: a thirsty crop that races ahead in the rain and the monsoon, and crawls along without it. */
public class RiceCropBlock extends CropBlock {
    public static final MapCodec<RiceCropBlock> CODEC = simpleCodec(RiceCropBlock::new);

    public RiceCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.RICE.get();
    }
}
