package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rime ice: feathers of ice that freezing fog builds on the windward side of trees, rocks and walls, growing
 * into the wind droplet by frozen droplet. It falls away as soon as the air warms above freezing.
 */
public class RimeBlock extends MultifaceBlock {
    public static final MapCodec<RimeBlock> CODEC = simpleCodec(RimeBlock::new);
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public RimeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RimeBlock> codec() {
        return CODEC;
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return spreader;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean warm = level.getBrightness(LightLayer.BLOCK, pos) > 11
                || Temperature.ambient(level, pos, WeatherManager.local(level, pos)) > 0.5F;
        if (warm) {
            level.removeBlock(pos, false);
        }
    }

    /** Coats the face of {@code support} that looks out into {@code air}. */
    public static boolean coat(ServerLevel level, BlockPos air, net.minecraft.core.Direction towardSupport, Block rime) {
        BlockState at = level.getBlockState(air);
        BlockState base = at.is(rime) ? at : at.isAir() ? rime.defaultBlockState() : null;
        if (base == null || base.getValue(getFaceProperty(towardSupport))) {
            return false;
        }
        if (!canAttachTo(level, towardSupport, air.relative(towardSupport), level.getBlockState(air.relative(towardSupport)))) {
            return false;
        }
        level.setBlock(air, base.setValue(getFaceProperty(towardSupport), true), Block.UPDATE_ALL);
        return true;
    }
}
