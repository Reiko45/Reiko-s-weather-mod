package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The cracked white crust of a salt flat. Mine it for salt. */
public class SaltCrustBlock extends Block {
    public static final MapCodec<SaltCrustBlock> CODEC = simpleCodec(SaltCrustBlock::new);

    public SaltCrustBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer serverPlayer && !player.isCreative()) {
            WeatherAdvancements.award(serverPlayer, "salt_of_the_earth");
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
