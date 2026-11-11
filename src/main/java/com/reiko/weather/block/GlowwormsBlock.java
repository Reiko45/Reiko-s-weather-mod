package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Glowworms: fungus-gnat larvae living on the roof of a damp cave, each hanging a curtain of sticky silk threads and
 * glowing a cold blue to lure in the midges that hatch from the stream below. In their thousands they look like a
 * night sky underground.
 */
public class GlowwormsBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 4, 1, 15, 16, 15);

    public GlowwormsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(GlowwormsBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // a faint twinkle now and then, brightest where it's darkest
        if (random.nextInt(10) == 0 && level.getBrightness(LightLayer.SKY, pos) < 4) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW, pos.getX() + random.nextDouble(), pos.getY() + 0.3 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0.0, -0.002, 0.0);
        }
    }
}
