package com.reiko.weather.item;

import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

/**
 * Volcanic ash: place it in layers like snow, or work it into the soil around a crop, where it makes a
 * fine fertilizer (a crop grows as if bone-mealed).
 */
public class VolcanicAshItem extends BlockItem {
    public VolcanicAshItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SweetBerryBushBlock) {
            if (BoneMealItem.growCrop(context.getItemInHand(), level, pos)) {
                if (!level.isClientSide) {
                    level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        WeatherAdvancements.award(player, "fertile_ground");
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
