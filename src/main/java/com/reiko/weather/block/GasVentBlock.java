package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gas vent: cracked, scorched rock where natural gas seeps up out of the ground. Once lit, the flames over it never
 * go out (the gas keeps feeding them, rain or shine), and if they're put out they catch again from the glowing rock.
 */
public class GasVentBlock extends Block {
    public static final MapCodec<GasVentBlock> CODEC = simpleCodec(GasVentBlock::new);

    public GasVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) && level.getBlockState(above).isAir() && random.nextInt(3) == 0) {
            level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
        }
    }
}
