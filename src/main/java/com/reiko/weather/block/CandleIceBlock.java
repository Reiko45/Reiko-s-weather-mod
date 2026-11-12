package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Candle ice: in the spring thaw, the ice on a lake rots along the boundaries between its crystals until it falls
 * apart into long, pencil-thin candles, standing on end. A breeze pushes them ashore in glittering heaps that tinkle
 * and chime like a wind chime as they jostle, and melt away within a day or two.
 */
public class CandleIceBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);

    public CandleIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(CandleIceBlock::new);
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
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // wading through a heap of candles: they clink and chime underfoot
        if (level.isClientSide && entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3 && level.random.nextInt(6) == 0) {
            level.playLocalSound(pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.4F + level.random.nextFloat() * 0.5F, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // and the breeze jostles them now and then
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.35F, 1.5F + random.nextFloat() * 0.5F, false);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // they're already half melted: gone in a day or two of thaw (sooner in the sun)
        float air = Temperature.ambient(level, pos, WeatherManager.local(level, pos));
        if (air > 0.5F && random.nextInt(level.isDay() ? 4 : 8) == 0) level.removeBlock(pos, false);
    }
}
