package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
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
 * Foxfire: the threads of a honey fungus spreading through a damp, rotting log glow a faint, cold green. You'd never
 * notice it by day; on a dark night in a wet wood, the old logs shine. It fades as the wood dries out.
 */
public class FoxfireBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public FoxfireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(FoxfireBlock::new);
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
        return level.getBlockState(pos.below()).is(BlockTags.LOGS);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // it goes out as the log dries out in a dry spell, or as the frost gets into it
        var local = WeatherManager.local(level, pos);
        boolean wet = level.isRainingAt(pos) || local != null && (local.profile().rain > 0.1F || local.profile().fog > 0.3F);
        float air = Temperature.ambient(level, pos, local);
        if (air < 0.0F || !wet && random.nextInt(6) == 0 && Temperature.humidity(level, pos, local) < 0.5F) {
            level.removeBlock(pos, false);
        }
    }

    /** Whether it's dark enough here to see it glowing (for the client's faint shimmer). */
    public static boolean dark(Level level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos) < 6;
    }
}
