package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Wind ripples: a steady breeze bounces sand grains along the ground, and they pile up in low, evenly spaced ridges
 * running across the wind, a hand's breadth apart. They re-form whenever the wind swings round, and a shower of
 * rain smooths them away. {@link #AXIS} is the way the crests run.
 */
public class RippledSandBlock extends FallingBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final MapCodec<RippledSandBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ColorRGBA.CODEC.fieldOf("falling_dust_color").forGetter(b -> b.dust), propertiesCodec()).apply(i, RippledSandBlock::new));
    private final ColorRGBA dust;

    public RippledSandBlock(ColorRGBA dust, Properties properties) {
        super(properties);
        this.dust = dust;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return dust.rgba();
    }

    /** The plain sand the ripples are made of. */
    public Block smooth() {
        return this == ModBlocks.RIPPLED_RED_SAND.get() ? Blocks.RED_SAND : Blocks.SAND;
    }

    /** Which way the crests run for a wind blowing toward {@code windAngle}: across it. */
    public static Direction.Axis crests(float windAngle) {
        Vec3 dir = Wind.direction(windAngle);
        return Math.abs(dir.x) > Math.abs(dir.z) ? Direction.Axis.Z : Direction.Axis.X;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.canSeeSky(pos.above())) return;
        var local = WeatherManager.local(level, pos);
        if (local.type().vanillaRain && local.profile().rain > 0.15F && local.intensity() > 0.2F) {
            // the rain beats the ripples flat
            level.setBlockAndUpdate(pos, smooth().defaultBlockState());
            return;
        }
        // a new wind combs them round to lie across it
        var weather = WeatherManager.state(level);
        if (weather != null && local.profile().wind > 0.3F) {
            Direction.Axis axis = crests(weather.windAngle);
            if (axis != state.getValue(AXIS)) level.setBlockAndUpdate(pos, state.setValue(AXIS, axis));
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(smooth());
    }
}
