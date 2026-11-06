package com.reiko.weather.item;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Road salt: scatter it to melt ice glaze, snow, icicles and thin ice around where you use it. */
public class SaltItem extends Item {
    public SaltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int melted = melt(level, center);
        if (melted == 0) {
            return InteractionResult.PASS;
        }
        ((ServerLevel) level).sendParticles(ParticleTypes.WHITE_ASH, center.getX() + 0.5, center.getY() + 1.1, center.getZ() + 0.5,
                25, 1.0, 0.2, 1.0, 0.01);
        level.playSound(null, center, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8F, 1.4F);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Melts frozen things in a 3x3x3 area around a position. Returns how many blocks changed. */
    public static int melt(Level level, BlockPos center) {
        int melted = 0;
        boolean warmEnough = !level.dimensionType().ultraWarm();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.ICE_GLAZE.get()) || state.is(Blocks.SNOW) || state.is(ModBlocks.ICICLE.get()) || state.is(Blocks.POWDER_SNOW)) {
                level.removeBlock(pos, false);
                melted++;
            } else if (warmEnough && (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE))) {
                level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                melted++;
            }
        }
        return melted;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.reikos_weather.salt.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
