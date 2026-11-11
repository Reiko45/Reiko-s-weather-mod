package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A quaking mat: a raft of living sphagnum moss and roots grown out over the edge of a bog pool, floating on deep
 * water. It sags and wobbles underfoot and slows you down; run, jump or ride onto it and you go straight through into
 * the cold water below. The hole knits back over in time.
 */
public class QuakingMatBlock extends Block implements SimpleWaterloggedBlock {
    /** Torn open: just a fringe of moss round a hole of open water. */
    public static final BooleanProperty TORN = BooleanProperty.create("torn");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape MAT = Block.box(0, 0, 0, 16, 14, 16);
    private static final VoxelShape FRINGE = Block.box(0, 13, 0, 16, 16, 16);

    public QuakingMatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TORN, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(QuakingMatBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TORN, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TORN) ? FRINGE : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // you sink a little way into it
        return state.getValue(TORN) ? Shapes.empty() : MAT;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!state.getValue(TORN) && entity.getBbWidth() >= 0.5F) {
            if (!level.isClientSide) {
                // it quakes: the whole mat ripples and squelches as you cross it
                if (level.random.nextInt(8) == 0) {
                    level.playSound(null, pos, SoundEvents.MUD_STEP, SoundSource.BLOCKS, 0.7F, 0.6F + level.random.nextFloat() * 0.2F);
                    ((ServerLevel) level).sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.6, 0.0, 0.6, 0.0);
                }
                // a running weight, or anything as big as a horse, tears straight through
                if (entity.isSprinting() && level.random.nextInt(6) == 0 || entity.getBbWidth() > 1.0F) tear(level, entity.getBoundingBox());
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!level.isClientSide && !state.getValue(TORN) && fallDistance > 1.2F && entity.getBbWidth() >= 0.5F) {
            tear(level, entity.getBoundingBox());
        } else {
            super.fallOn(level, state, pos, entity, fallDistance * 0.3F);
        }
    }

    @Override
    public float getJumpFactor() {
        return 0.75F;
    }

    /** Tears open every bit of mat under a footprint, dropping whatever stood there into the water. */
    public static void tear(Level level, AABB footprint) {
        BlockPos.betweenClosedStream(new AABB(footprint.minX, footprint.minY - 0.6, footprint.minZ, footprint.maxX, footprint.minY - 0.1, footprint.maxZ)).forEach(p -> {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof QuakingMatBlock && !s.getValue(TORN)) {
                level.setBlockAndUpdate(p.immutable(), s.setValue(TORN, true).setValue(WATERLOGGED, true));
                level.playSound(null, p, SoundEvents.PLAYER_SPLASH, SoundSource.BLOCKS, 0.8F, 0.8F);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 12, 0.4, 0.1, 0.4, 0.05);
                }
            }
        });
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(TORN);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // the moss grows back over the hole from the edges, if nothing is swimming in it
        if (random.nextInt(4) != 0 || !level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0.2)).isEmpty()) return;
        int edges = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof QuakingMatBlock && !n.getValue(TORN) || n.isFaceSturdy(level, pos.relative(d), d.getOpposite())) edges++;
        }
        if (edges >= 2) level.setBlockAndUpdate(pos, state.setValue(TORN, false).setValue(WATERLOGGED, false));
    }
}
