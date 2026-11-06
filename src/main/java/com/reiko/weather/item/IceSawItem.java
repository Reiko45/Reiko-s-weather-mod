package com.reiko.weather.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cuts blocks of ice out of frozen lakes and rivers (they drop as ice instead of shattering), ready to
 * pack around food in an ice house.
 */
public class IceSawItem extends Item {
    public IceSawItem(Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) ? 10.0F : 1.0F;
    }

    @Override
    public boolean mineBlock(ItemStack stack, net.minecraft.world.level.Level level, BlockState state, net.minecraft.core.BlockPos pos,
                             net.minecraft.world.entity.LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F && (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW))) {
            stack.hurtAndBreak(1, miner, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return true;
    }
}
