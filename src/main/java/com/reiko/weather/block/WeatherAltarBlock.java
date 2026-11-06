package com.reiko.weather.block;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** End-game weather control: offer an item and the sky changes to match. */
public class WeatherAltarBlock extends Block {
    /** What each offering calls down. */
    public static final Map<Item, WeatherType> OFFERINGS = new LinkedHashMap<>();

    static {
        OFFERINGS.put(Items.SUNFLOWER, WeatherType.CLEAR);
        OFFERINGS.put(Items.WATER_BUCKET, WeatherType.RAIN);
        OFFERINGS.put(Items.LIGHTNING_ROD, WeatherType.THUNDERSTORM);
        OFFERINGS.put(Items.SNOW_BLOCK, WeatherType.SNOW);
        OFFERINGS.put(Items.PACKED_ICE, WeatherType.BLIZZARD);
        OFFERINGS.put(Items.BLAZE_POWDER, WeatherType.HEATWAVE);
        OFFERINGS.put(Items.FEATHER, WeatherType.WINDY);
        OFFERINGS.put(Items.SAND, WeatherType.SANDSTORM);
        OFFERINGS.put(Items.COBWEB, WeatherType.FOG);
        OFFERINGS.put(Items.ICE, WeatherType.ICE_STORM);
        OFFERINGS.put(Items.GHAST_TEAR, WeatherType.SUPERCELL);
        OFFERINGS.put(Items.HEART_OF_THE_SEA, WeatherType.HURRICANE);
    }

    public WeatherAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        WeatherType wanted = OFFERINGS.get(stack.getItem());
        if (wanted == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            WeatherState weather = WeatherManager.activeState(serverLevel);
            if (weather == null) {
                player.displayClientMessage(Component.translatable("block.reikos_weather.weather_altar.silent").withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.sidedSuccess(false);
            }
            long now = serverLevel.getGameTime();
            if (now < weather.altarReadyAt) {
                player.displayClientMessage(Component.translatable("block.reikos_weather.weather_altar.charging",
                        (weather.altarReadyAt - now) / 20 + 1).withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.sidedSuccess(false);
            }
            WeatherManager.setWeather(serverLevel, weather, wanted, 6000 + serverLevel.random.nextInt(6000));
            weather.altarReadyAt = now + WeatherConfig.ALTAR_COOLDOWN.get() * 20L;
            weather.setDirty();
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, stack.is(Items.WATER_BUCKET) ? ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)) : consume(stack));
            }
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(pos.above()));
                bolt.setVisualOnly(true);
                serverLevel.addFreshEntity(bolt);
            }
            serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.08);
            serverLevel.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            for (var p : serverLevel.players()) {
                p.displayClientMessage(Component.translatable("block.reikos_weather.weather_altar.answered", wanted.displayName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemStack consume(ItemStack stack) {
        stack.shrink(1);
        return stack;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("block.reikos_weather.weather_altar.hint").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
