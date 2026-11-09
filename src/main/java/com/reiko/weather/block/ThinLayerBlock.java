package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A short-lived skin on the ground left by the weather: sea foam blown up the beach in a storm, needle ice pushed
 * up out of frozen soil on a frosty morning, or frost flowers on new ice. Each goes again before long: foam dries
 * up, and needle ice and frost flowers melt once the sun's on them (or it warms up).
 */
public class ThinLayerBlock extends Block {
    public enum Kind { SEA_FOAM, NEEDLE_ICE, FROST_FLOWERS }

    private final Kind kind;
    private final VoxelShape shape;

    public ThinLayerBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.shape = Block.box(0, 0, 0, 16, kind == Kind.NEEDLE_ICE ? 3 : kind == Kind.SEA_FOAM ? 2 : 4, 16);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new ThinLayerBlock(kind, p));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return switch (kind) {
            case SEA_FOAM -> below.is(BlockTags.SAND) || below.is(Blocks.GRAVEL) || below.isFaceSturdy(level, pos.below(), Direction.UP);
            case NEEDLE_ICE -> below.is(BlockTags.DIRT) || below.is(Blocks.FARMLAND);
            case FROST_FLOWERS -> below.is(BlockTags.ICE);
        };
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean warm = !level.getBiome(pos).value().coldEnoughToSnow(pos) && com.reiko.weather.weather.WeatherManager.seasonInfo().season() != com.reiko.weather.weather.Season.WINTER;
        boolean sunny = level.isDay() && level.getBrightness(LightLayer.SKY, pos) >= 12;
        boolean gone = switch (kind) {
            case SEA_FOAM -> random.nextInt(3) == 0; // it dries and blows away within a few minutes
            case NEEDLE_ICE, FROST_FLOWERS -> sunny && level.getDayTime() % 24000L > 1500L && random.nextInt(2) == 0
                    || warm && random.nextInt(4) == 0 || level.getBrightness(LightLayer.BLOCK, pos) > 11;
        };
        if (gone) level.removeBlock(pos, false);
    }
}
