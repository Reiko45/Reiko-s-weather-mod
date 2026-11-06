package com.reiko.weather.entity;

import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A blazing meteor on its way down. It blows a small crater where it lands and leaves a meteorite behind. */
public class MeteorEntity extends Entity {
    private static final int MAX_AGE = 400;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        if (level().isClientSide) {
            for (int i = 0; i < 6; i++) {
                double t = random.nextDouble();
                level().addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME,
                        Mth(from.x, to.x, t) + random.nextGaussian() * 0.25, Mth(from.y, to.y, t) + random.nextGaussian() * 0.25,
                        Mth(from.z, to.z, t) + random.nextGaussian() * 0.25, 0.0, 0.02, 0.0);
            }
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.LAVA, from.x, from.y, from.z, 0.0, 0.0, 0.0);
            setPos(to);
            return;
        }
        if (tickCount > MAX_AGE || to.y < level().getMinBuildHeight()) {
            discard();
            return;
        }
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.MISS) {
            impact((ServerLevel) level(), hit.getLocation());
            return;
        }
        setPos(to);
    }

    private static double Mth(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void impact(ServerLevel level, Vec3 at) {
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        level.explode(this, at.x, at.y, at.z, 3.0F, false, griefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        BlockPos column = BlockPos.containing(at);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos rock = top.below();
        BlockState below = level.getBlockState(rock);
        if (!below.getFluidState().isEmpty() || below.isAir()) {
            rock = top; // landed in water or the crater floor is open: rest on top
        }
        level.setBlockAndUpdate(rock, ModBlocks.METEORITE.get().defaultBlockState());
        if (griefing) {
            for (int i = 0; i < 4; i++) {
                BlockPos p = rock.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                if (level.getBlockState(p).isSolidRender(level, p) && !p.equals(rock)) {
                    level.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
                }
                BlockPos f = rock.offset(random.nextInt(7) - 3, 1, random.nextInt(7) - 3);
                if (level.getBlockState(f).isAir() && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
                    level.setBlockAndUpdate(f, BaseFireBlock.getState(level, f));
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(rock).inflate(128))) {
            WeatherAdvancements.award(player, "make_a_wish");
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Never written to disk: a meteor stuck in an unloaded chunk shouldn't crash down days later. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
