package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The vent of a geyser. Every few minutes it erupts: a roaring column of steam and boiling water shoots up
 * for several seconds, throwing anything standing over it high into the air (and giving a paraglider a big
 * lift). A comparator reads 15 while it erupts.
 */
public class GeyserBlock extends Block {
    public static final MapCodec<GeyserBlock> CODEC = simpleCodec(GeyserBlock::new);
    public static final BooleanProperty ERUPTING = BooleanProperty.create("erupting");
    public static final IntegerProperty TIME = IntegerProperty.create("time", 0, 40);
    /** Ticks between updates while erupting (so an eruption lasts 40 x 4 = 160 ticks). */
    private static final int STEP = 4;

    public GeyserBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ERUPTING, false).setValue(TIME, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ERUPTING, TIME);
    }

    /** Ticks until the next eruption: every few minutes. */
    public static int restLength(RandomSource random) {
        return 1800 + random.nextInt(3000);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 200 + level.random.nextInt(800));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ERUPTING)) {
            if (!level.getBlockState(pos.above()).isAir()) {
                level.scheduleTick(pos, this, 600); // blocked up: try again later
                return;
            }
            level.setBlock(pos, state.setValue(ERUPTING, true).setValue(TIME, 40), Block.UPDATE_ALL);
            level.playSound(null, pos, ModSounds.GEYSER.get(), SoundSource.BLOCKS, 4.0F, 0.9F + random.nextFloat() * 0.2F);
            level.scheduleTick(pos, this, STEP);
            return;
        }
        int time = state.getValue(TIME);
        erupt(level, pos, time, random);
        if (time <= 0) {
            level.setBlock(pos, state.setValue(ERUPTING, false).setValue(TIME, 0), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, restLength(random));
        } else {
            level.setBlock(pos, state.setValue(TIME, time - 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, STEP);
        }
    }

    /** How high the column reaches at this point in the eruption: it shoots up, holds, then dies back. */
    public static float height(int time) {
        float rise = Mth.clamp((40 - time) / 5.0F, 0.0F, 1.0F), fall = Mth.clamp(time / 12.0F, 0.0F, 1.0F);
        return 20.0F * Math.min(rise, fall);
    }

    private static void erupt(ServerLevel level, BlockPos pos, int time, RandomSource random) {
        float h = height(time);
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) > 192 * 192) continue;
            for (float k = 0; k < h; k += 2.0F) {
                double spread = 0.2 + k * 0.02;
                level.sendParticles(player, ParticleTypes.CLOUD, true, x, y + k, z, 3, spread, 0.8, spread, 0.03);
                level.sendParticles(player, ParticleTypes.SPLASH, true, x, y + k, z, 4, spread, 0.8, spread, 0.0);
            }
            level.sendParticles(player, ParticleTypes.FALLING_WATER, true, x, y + h, z, 10, 2.0, 0.5, 2.0, 0.0);
        }
        if (h < 1.0F) {
            return;
        }
        AABB column = new AABB(pos.above()).inflate(0.8, 0.0, 0.8).expandTowards(0.0, h, 0.0);
        for (Entity entity : level.getEntities((Entity) null, column, e -> !e.isSpectator())) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x * 0.5, Math.max(motion.y, 0.9 + 0.3 * random.nextDouble()), motion.z * 0.5);
            entity.hurtMarked = true;
            entity.resetFallDistance();
            entity.clearFire();
            if (entity instanceof ServerPlayer player) {
                WeatherAdvancements.award(player, "old_faithful");
            }
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(ERUPTING) ? 15 : 0;
    }
}
