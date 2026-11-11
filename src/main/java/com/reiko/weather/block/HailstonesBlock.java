package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hailstones lying on the ground after a hailstorm, drifted deeper against walls and in hollows. Ice in summer
 * doesn't last: it melts away within a few minutes in the sun (faster on warm days), and while it does, the air
 * over it is chilled into a low, drifting hail fog.
 */
public class HailstonesBlock extends SnowLayerBlock {
    public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(HailstonesBlock::new);

    public HailstonesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SnowLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean warm = !level.getBiome(pos).value().coldEnoughToSnow(pos);
        boolean sunny = level.isDay() && level.getBrightness(LightLayer.SKY, pos) >= 12;
        if (!warm && !sunny && random.nextInt(4) != 0) return;
        int layers = state.getValue(LAYERS);
        if (layers > 1) level.setBlock(pos, state.setValue(LAYERS, layers - 1), 2);
        else level.removeBlock(pos, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // hail fog: warm, damp air chilled over the ice
        if (level.isDay() && random.nextInt(6) == 0 && !level.getBiome(pos).value().coldEnoughToSnow(pos)) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 0.3 + random.nextDouble() * 0.5,
                    pos.getZ() + random.nextDouble(), 0.0, 0.004, 0.0);
        }
    }

    /**
     * Only a thin single layer gets swept aside by a block landing on it. (This has no item of its own, and vanilla's
     * snow check, which compares the item in hand with this block's item, would call itself forever when a falling
     * block landed on it.)
     */
    @Override
    protected boolean canBeReplaced(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return state.getValue(LAYERS) == 1;
    }
}
