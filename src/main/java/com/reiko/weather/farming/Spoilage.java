package com.reiko.weather.farming;

import com.reiko.weather.block.IceboxBlockEntity;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModTags;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw meat and fish (tag {@code reikos_weather:perishable}) go off in the heat, a piece at a time, in
 * your inventory and in containers. Iceboxes never let food spoil; nor does a container with ice,
 * packed ice, blue ice or snow next to it (an ice house), or an inventory with ice in it.
 */
public final class Spoilage {
    /** Above this air temperature food starts to go off. */
    public static final float SPOILS_ABOVE = 27.0F;

    public static void tickPlayer(ServerPlayer player) {
        if (!WeatherConfig.FOOD_SPOILAGE.get() || player.tickCount % 600 != 0 || player.isCreative() || player.isSpectator()
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(player.getEyePosition());
        float celsius = Temperature.ambient(level, pos, WeatherManager.local(level, pos));
        if (celsius <= SPOILS_ABOVE || carriesIce(player)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            stacks.add(player.getInventory().getItem(i));
        }
        int spoiled = spoil(stacks, level.random, celsius, rotten -> {
            if (!player.getInventory().add(rotten)) {
                player.drop(rotten, false);
            }
        });
        if (spoiled > 0) {
            player.displayClientMessage(Component.translatable("message.reikos_weather.food_spoiled").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Every minute, containers near players in hot places spoil some of their food. */
    public static void tickLevel(ServerLevel level) {
        if (!WeatherConfig.FOOD_SPOILAGE.get() || level.getGameTime() % 1200L != 0L || level.dimension() != Level.OVERWORLD) {
            return;
        }
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int cx = center.x + dx, cz = center.z + dz;
                    if (!done.add(ChunkPos.asLong(cx, cz)) || !level.hasChunk(cx, cz)) {
                        continue;
                    }
                    for (BlockEntity be : new ArrayList<>(level.getChunk(cx, cz).getBlockEntities().values())) {
                        if (be instanceof Container container && !(be instanceof IceboxBlockEntity)) {
                            spoilContainer(level, be.getBlockPos(), container);
                        }
                    }
                }
            }
        }
    }

    private static void spoilContainer(ServerLevel level, BlockPos pos, Container container) {
        if (iceNextTo(level, pos)) {
            return;
        }
        float celsius = Temperature.ambient(level, pos, WeatherManager.local(level, pos));
        if (celsius <= SPOILS_ABOVE) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            stacks.add(container.getItem(i));
        }
        List<ItemStack> rotten = new ArrayList<>();
        if (spoil(stacks, level.random, celsius, rotten::add) > 0) {
            for (ItemStack r : rotten) {
                for (int i = 0; i < container.getContainerSize() && !r.isEmpty(); i++) {
                    ItemStack slot = container.getItem(i);
                    if (slot.isEmpty()) {
                        container.setItem(i, r.copy());
                        r.setCount(0);
                    } else if (ItemStack.isSameItemSameComponents(slot, r) && slot.getCount() < slot.getMaxStackSize()) {
                        int move = Math.min(r.getCount(), slot.getMaxStackSize() - slot.getCount());
                        slot.grow(move);
                        r.shrink(move);
                    }
                }
            }
            container.setChanged();
        }
    }

    /**
     * Each perishable stack has a chance (higher the hotter it is) to lose one item to rot. The rotten
     * flesh is handed to {@code out}. Returns how many items went off.
     */
    public static int spoil(List<ItemStack> stacks, RandomSource random, float celsius, java.util.function.Consumer<ItemStack> out) {
        float chance = Mth.clamp(0.04F + (celsius - SPOILS_ABOVE) * 0.02F, 0.04F, 0.35F);
        int spoiled = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.is(ModTags.PERISHABLE) && random.nextFloat() < chance) {
                stack.shrink(1);
                out.accept(new ItemStack(Items.ROTTEN_FLESH));
                spoiled++;
            }
        }
        return spoiled;
    }

    /** Ice, packed ice, blue ice or snow touching the container (an ice house). */
    public static boolean iceNextTo(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                return true;
            }
        }
        return false;
    }

    private static boolean carriesIce(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.ICE) || stack.is(Items.PACKED_ICE) || stack.is(Items.BLUE_ICE) || stack.is(Items.SNOW_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    /** Award for storing food in an icebox during a heatwave (checked when the player opens one). */
    public static void onOpenIcebox(ServerPlayer player, IceboxBlockEntity box) {
        BlockPos pos = box.getBlockPos();
        if (Temperature.ambient(player.serverLevel(), pos, WeatherManager.local(player.serverLevel(), pos)) > SPOILS_ABOVE) {
            for (int i = 0; i < box.getContainerSize(); i++) {
                if (box.getItem(i).is(ModTags.PERISHABLE)) {
                    WeatherAdvancements.award(player, "cool_storage");
                    return;
                }
            }
        }
    }

    private Spoilage() {}
}
