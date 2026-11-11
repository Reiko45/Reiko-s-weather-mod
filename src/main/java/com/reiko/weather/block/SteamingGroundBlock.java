package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steaming ground: soil baked warm by hot rock not far below, stained orange and yellow by the gases seeping up
 * through it. Wisps of steam rise off it, thickest on cold mornings, and snow never lies on it for long.
 */
public class SteamingGroundBlock extends Block {
    public static final MapCodec<SteamingGroundBlock> CODEC = simpleCodec(SteamingGroundBlock::new);

    public SteamingGroundBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // the warmth melts any snow or ice lying on it
        BlockPos above = pos.above();
        BlockState on = level.getBlockState(above);
        if (on.is(Blocks.SNOW) || on.is(Blocks.POWDER_SNOW) || on.is(BlockTags.ICE) && !on.is(Blocks.BLUE_ICE)) {
            level.removeBlock(above, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        // steam shows best in cold air, and hardly at all on a hot afternoon
        boolean cold = level.getBiome(pos).value().coldEnoughToSnow(pos) || !level.isDay();
        if (random.nextInt(cold ? 2 : 6) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.01, 0.03 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.01);
        }
    }
}
