package com.reiko.weather.block;

import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collects rainwater far faster than a cauldron. Take water out with a bucket (when full) or bottles. */
public class RainBarrelBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Block.box(2, 2, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

    public RainBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 100);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int water = state.getValue(LEVEL);
        if (water < 3 && level.isRainingAt(pos.above())) {
            LocalWeather local = WeatherManager.local(level, pos);
            float rain = local != null ? local.profile().rain : 1.0F;
            if (random.nextFloat() < 0.5F * rain) {
                level.setBlockAndUpdate(pos, state.setValue(LEVEL, water + 1));
            }
        }
        level.scheduleTick(pos, this, 100);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        int water = state.getValue(LEVEL);
        if (stack.is(Items.BUCKET) && water == 3) {
            return fill(level, pos, player, hand, stack, new ItemStack(Items.WATER_BUCKET), state.setValue(LEVEL, 0), SoundEvents.BUCKET_FILL);
        }
        if (stack.is(Items.GLASS_BOTTLE) && water > 0) {
            return fill(level, pos, player, hand, stack, PotionContents.createItemStack(Items.POTION, Potions.WATER),
                    state.setValue(LEVEL, water - 1), SoundEvents.BOTTLE_FILL);
        }
        if (stack.is(Items.WATER_BUCKET) && water < 3) {
            return fill(level, pos, player, hand, stack, new ItemStack(Items.BUCKET), state.setValue(LEVEL, 3), SoundEvents.BUCKET_EMPTY);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult fill(Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack used,
                                              ItemStack result, BlockState newState, SoundEvent sound) {
        if (!level.isClientSide) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(used, player, result));
            level.setBlockAndUpdate(pos, newState);
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(LEVEL);
    }
}
