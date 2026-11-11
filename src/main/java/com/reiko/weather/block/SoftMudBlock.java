package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.temperature.Temperature;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mud season: ground churned to sticky mud by the spring thaw and long rains. You sink into it a little and it drags
 * at your feet, and it holds every footprint. Once the rain stops and the weather warms it dries out again, back to
 * plain earth (or a dirt path, if that's what it was).
 */
public class SoftMudBlock extends Block {
    private static final VoxelShape SINK = Block.box(0, 0, 0, 16, 14, 16);
    private final boolean path;

    public SoftMudBlock(boolean path, Properties properties) {
        super(properties);
        this.path = path;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new SoftMudBlock(path, p));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SINK;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above())) return;
        Season season = WeatherManager.seasonInfo().season();
        float air = Temperature.ambient(level, pos.above(), WeatherManager.local(level, pos.above()));
        // it dries out slowly in a cool spring, fast in summer heat, and not at all while it's freezing
        int chance = air < 1.0F ? 0 : season == Season.SPRING && air < 14.0F ? 30 : air > 22.0F ? 3 : 8;
        if (chance > 0 && random.nextInt(chance) == 0) {
            level.setBlockAndUpdate(pos, (path ? Blocks.DIRT_PATH : Blocks.DIRT).defaultBlockState());
        }
    }

    @Override
    public float getSpeedFactor() {
        return 0.75F;
    }
}
