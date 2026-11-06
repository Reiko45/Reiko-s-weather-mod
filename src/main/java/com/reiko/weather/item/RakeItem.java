package com.reiko.weather.item;

import com.reiko.weather.block.LeafPileBlock;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Rakes up fallen leaves (and storm-fallen branches) in a 5x5 area, giving you the leaves to compost or build with. */
public class RakeItem extends Item {
    public RakeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        Player player = context.getPlayer();
        int leaves = 0, branches = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 1, 2))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LeafPileBlock) {
                leaves += state.getValue(LeafPileBlock.LAYERS);
                if (!level.isClientSide) level.removeBlock(pos, false);
            } else if (state.getBlock() instanceof com.reiko.weather.block.FallenBranchBlock) {
                branches++;
                if (!level.isClientSide) Block.dropResources(state, level, pos);
                if (!level.isClientSide) level.removeBlock(pos, false);
            }
        }
        if (leaves == 0 && branches == 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.playSound(null, center, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
            if (leaves > 0) {
                ItemStack pile = new ItemStack(ModItems.LEAF_PILE.get(), leaves);
                if (player == null || !player.getInventory().add(pile)) {
                    Block.popResource(level, center.above(), pile);
                }
            }
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                if (player instanceof ServerPlayer serverPlayer && leaves >= 8) {
                    WeatherAdvancements.award(serverPlayer, "raking_it_in");
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
