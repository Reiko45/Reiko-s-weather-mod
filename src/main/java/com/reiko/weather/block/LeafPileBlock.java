package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pile of fallen leaves, 1 to 4 layers deep. Leaves pile up under trees in autumn; you can wade
 * through them (they rustle), a deep pile softens a fall, and a rake gathers them up. They rot away
 * through the winter.
 */
public class LeafPileBlock extends Block {
    public static final MapCodec<LeafPileBlock> CODEC = simpleCodec(LeafPileBlock::new);
    public static final int MAX_LAYERS = 4;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, MAX_LAYERS);
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

    static {
        for (int i = 1; i <= MAX_LAYERS; i++) {
            SHAPES[i] = box(0, 0, 0, 16, i * 2, 16);
        }
    }

    public LeafPileBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // you wade through leaves
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return !ground.is(this) && ground.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (context.getItemInHand().is(asItem()) && layers < MAX_LAYERS) {
            return true;
        }
        return layers == 1 && !context.getItemInHand().is(asItem());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(LAYERS, Math.min(MAX_LAYERS, existing.getValue(LAYERS) + 1));
        }
        return defaultBlockState();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity && entity.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
            if (level.isClientSide) {
                if (level.random.nextInt(3) == 0) {
                    level.addParticle(com.reiko.weather.registry.ModParticles.LEAF.get(), entity.getX() + (level.random.nextDouble() - 0.5) * 0.6,
                            pos.getY() + 0.1 + state.getValue(LAYERS) * 0.12, entity.getZ() + (level.random.nextDouble() - 0.5) * 0.6,
                            (level.random.nextDouble() - 0.5) * 0.1, 0.12, (level.random.nextDouble() - 0.5) * 0.1);
                }
            } else if (entity.tickCount % 6 == 0) {
                level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_STEP, SoundSource.BLOCKS, 0.4F, 0.8F + level.random.nextFloat() * 0.3F);
            }
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Season season = WeatherManager.seasonInfo().season();
        // leaves stay through autumn and rot down over the winter and spring
        boolean rotting = season == null || season == Season.WINTER || season == Season.SPRING || season == Season.SUMMER;
        int chance = level.isRainingAt(pos.above()) ? 6 : 14;
        if (rotting && random.nextInt(chance) == 0) {
            int layers = state.getValue(LAYERS);
            if (layers <= 1) {
                level.removeBlock(pos, false);
            } else {
                level.setBlock(pos, state.setValue(LAYERS, layers - 1), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** How much of a fall's damage a pile soaks up (a full pile takes off 80%). */
    public static float cushion(BlockState state) {
        return state.getBlock() instanceof LeafPileBlock ? 0.2F * state.getValue(LAYERS) : 0.0F;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }

    /** Adds a layer to the pile at a position (or starts one). Returns false if nothing could be placed there. */
    public static boolean addLayer(Level level, BlockPos pos, Block pile, int maxLayers) {
        BlockState here = level.getBlockState(pos);
        if (here.is(pile)) {
            int layers = here.getValue(LAYERS);
            if (layers >= maxLayers) return false;
            level.setBlock(pos, here.setValue(LAYERS, layers + 1), Block.UPDATE_ALL);
            return true;
        }
        if (!(here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty() && !here.is(net.minecraft.tags.BlockTags.SNOW))) {
            return false;
        }
        BlockState state = pile.defaultBlockState();
        if (!state.canSurvive(level, pos)) return false;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return true;
    }
}
