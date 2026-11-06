package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A two-block tent you can sleep in. It works like a bed for skipping the night but never sets your
 * spawn point, and while you're inside it you are sheltered from the weather.
 */
public class TentBlock extends BedBlock {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final MapCodec<BedBlock> CODEC = (MapCodec) simpleCodec(TentBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 3, 16);

    public TentBlock(Properties properties) {
        super(DyeColor.WHITE, properties);
    }

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
    }

    /** True while an entity is lying in or standing inside a tent. */
    public static boolean isInside(Entity entity) {
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        return level.getBlockState(pos).getBlock() instanceof TentBlock
                || entity instanceof net.minecraft.world.entity.LivingEntity living && living.isSleeping()
                && living.getSleepingPos().map(p -> level.getBlockState(p).getBlock() instanceof TentBlock).orElse(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && !canSetSpawn(level)) {
            // a bed would explode here; a tent just isn't comfortable enough
            player.displayClientMessage(Component.translatable("block.reikos_weather.tent.no_sleep"), true);
            return InteractionResult.SUCCESS;
        }
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hitResult);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && serverPlayer.isSleeping()) {
            com.reiko.weather.weather.WeatherAdvancements.award(serverPlayer, "roughing_it");
        }
        return result;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
