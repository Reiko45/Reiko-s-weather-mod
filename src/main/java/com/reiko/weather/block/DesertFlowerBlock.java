package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A desert wildflower: an annual whose seeds lie in the sand for years until a wet winter wakes them. They flower
 * together in spring, set seed, and are gone again in the summer heat.
 */
public class DesertFlowerBlock extends FlowerBlock {
    public static final MapCodec<FlowerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(EFFECTS_FIELD.forGetter(FlowerBlock::getSuspiciousEffects),
            propertiesCodec()).apply(i, DesertFlowerBlock::new));

    public DesertFlowerBlock(Holder<MobEffect> effect, float seconds, Properties properties) {
        super(effect, seconds, properties);
    }

    public DesertFlowerBlock(SuspiciousStewEffects effects, Properties properties) {
        super(effects, properties);
    }

    @Override
    public MapCodec<? extends FlowerBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.GRAVEL);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // the heat of summer finishes them off (a pot or a garden keeps one going)
        Season season = WeatherManager.seasonInfo().season();
        if ((season == Season.SUMMER || season == Season.AUTUMN) && random.nextInt(8) == 0 && level.getBlockState(pos.below()).is(BlockTags.SAND)) {
            level.removeBlock(pos, false);
        }
    }
}
