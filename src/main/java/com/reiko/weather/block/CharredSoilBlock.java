package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Charred soil: what a wildfire leaves behind, black with ash. It isn't dead: in the growing seasons fireweed is the
 * first thing to come back (its seeds blow in and love the open, ashy ground), then grass closes over it, and here
 * and there a young birch takes root, the first of the new forest.
 */
public class CharredSoilBlock extends Block {
    public static final MapCodec<CharredSoilBlock> CODEC = simpleCodec(CharredSoilBlock::new);
    /** 0 fresh ash, 1 crusted over, 2 greening. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);

    public CharredSoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Season season = WeatherManager.seasonInfo().season();
        // nothing grows back in winter
        if (season == Season.WINTER || random.nextInt(5) != 0 || !level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).is(ModBlocks.FIREWEED.get())) {
            return;
        }
        int stage = state.getValue(STAGE);
        BlockPos above = pos.above();
        if (stage < 2) {
            level.setBlockAndUpdate(pos, state.setValue(STAGE, stage + 1));
            if (stage == 1 && level.getBlockState(above).isAir() && random.nextInt(3) == 0) {
                level.setBlockAndUpdate(above, ModBlocks.FIREWEED.get().defaultBlockState());
            }
        } else {
            level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
            if (level.getBlockState(above).isAir() && random.nextInt(14) == 0) {
                level.setBlockAndUpdate(above, Blocks.BIRCH_SAPLING.defaultBlockState());
            }
        }
    }
}
