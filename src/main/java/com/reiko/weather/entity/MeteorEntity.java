package com.reiko.weather.entity;

import com.reiko.weather.network.QuakePayload;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.sky.Meteors.Size;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A blazing meteor on its way down. How big it is (see {@link Size}) decides how bright it burns and what it does
 * when it lands: a pebble leaves a lump of meteorite in a scorch mark, the classic meteor blows a small crater, a
 * large fireball a bigger one with a few lumps of meteorite in it, and a huge one blasts a great crater with a
 * glowing core of meteorite, lights fires all around and shakes the ground for a long way.
 */
public class MeteorEntity extends Entity {
    private static final int MAX_AGE = 400;
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1.0F);
    }

    /** 1 is the classic meteor; from about 0.45 (a pebble) to 3 (a huge fireball). */
    public float size() {
        return entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        entityData.set(DATA_SIZE, size);
    }

    public Size sizeClass() {
        return Size.of(size());
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        if (level().isClientSide) {
            float size = size();
            int n = Math.round(2 + 5 * size * size);
            double spread = 0.25 * size;
            for (int i = 0; i < n; i++) {
                double t = random.nextDouble();
                level().addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME,
                        Mth(from.x, to.x, t) + random.nextGaussian() * spread, Mth(from.y, to.y, t) + random.nextGaussian() * spread,
                        Mth(from.z, to.z, t) + random.nextGaussian() * spread, 0.0, 0.02, 0.0);
            }
            if (random.nextFloat() < 0.3F * size) level().addParticle(ParticleTypes.LAVA, from.x, from.y, from.z, 0.0, 0.0, 0.0);
            if (size > 2.2F) {
                // a long, thick smoke trail hangs in the sky behind the big ones
                for (int i = 0; i < 3; i++) {
                    double t = random.nextDouble();
                    level().addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, Mth(from.x, to.x, t), Mth(from.y, to.y, t),
                            Mth(from.z, to.z, t), random.nextGaussian() * 0.02, 0.01, random.nextGaussian() * 0.02);
                }
                // so bright it lights up the sky as it comes down
                if (random.nextInt(3) == 0) level().setSkyFlashTime(2);
            }
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
        Size size = sizeClass();
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        boolean fire = griefing && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        // a pebble only scorches the ground; the bigger ones blast craters
        float power = switch (size) { case SMALL -> 1.5F; case MEDIUM -> 3.0F; case LARGE -> 5.0F; case HUGE -> 7.5F; };
        Level.ExplosionInteraction blast = griefing && size != Size.SMALL ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        level.explode(this, at.x, at.y, at.z, power, false, blast);
        // the rock ends up in the floor of its crater (or on the bottom, if it came down in water)
        BlockPos rock = BlockPos.containing(at);
        for (int i = 0; i < 24 && rock.getY() > level.getMinBuildHeight(); i++) {
            BlockState st = level.getBlockState(rock);
            if (!st.isAir() && st.getFluidState().isEmpty() && st.blocksMotion()) break;
            rock = rock.below();
        }
        if (level.getBlockState(rock).getDestroySpeed(level, rock) < 0) rock = rock.above(); // (never into bedrock)
        level.setBlockAndUpdate(rock, ModBlocks.METEORITE.get().defaultBlockState());
        if (size == Size.LARGE || size == Size.HUGE) {
            // the big ones break up on impact: a lumpy core of meteorite around and under the main one
            double r = size == Size.HUGE ? 2.3 : 1.5;
            float chance = size == Size.HUGE ? 0.3F : 0.18F;
            int ri = (int) Math.ceil(r);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r || (dx == 0 && dy == 0 && dz == 0)) continue;
                        BlockPos p = rock.offset(dx, dy, dz);
                        BlockState there = level.getBlockState(p);
                        if (there.getDestroySpeed(level, p) < 0 || there.hasBlockEntity()) continue;
                        // into the ground, or piled on top of a lump already placed
                        boolean ground = there.isSolidRender(level, p);
                        boolean perch = there.isAir() && level.getBlockState(p.below()).is(ModBlocks.METEORITE.get());
                        if ((ground || perch) && random.nextFloat() < chance) {
                            level.setBlockAndUpdate(p, ModBlocks.METEORITE.get().defaultBlockState());
                        }
                    }
                }
            }
        }
        if (griefing) {
            int tries = switch (size) { case SMALL -> 1; case MEDIUM -> 4; case LARGE -> 10; case HUGE -> 28; };
            int reach = switch (size) { case SMALL -> 1; case MEDIUM -> 2; case LARGE -> 3; case HUGE -> 6; };
            for (int i = 0; i < tries; i++) {
                if (size != Size.SMALL) {
                    BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            rock.offset(random.nextInt(2 * reach + 1) - reach, 0, random.nextInt(2 * reach + 1) - reach)).below();
                    if (level.getBlockState(p).isSolidRender(level, p) && !level.getBlockState(p).is(ModBlocks.METEORITE.get())) {
                        level.setBlockAndUpdate(p, Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                }
                int fr = reach + 1 + (size == Size.HUGE ? 4 : 0);
                BlockPos f = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        rock.offset(random.nextInt(2 * fr + 1) - fr, 0, random.nextInt(2 * fr + 1) - fr));
                if (fire && level.getBlockState(f).isAir() && BaseFireBlock.canBePlacedAt(level, f, net.minecraft.core.Direction.UP)) {
                    level.setBlockAndUpdate(f, BaseFireBlock.getState(level, f));
                }
            }
        }
        level.sendParticles(size == Size.SMALL ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z,
                size == Size.HUGE ? 6 : 1, size.ordinal() * 1.5, size.ordinal() * 0.5, size.ordinal() * 1.5, 0);
        if (size == Size.LARGE || size == Size.HUGE) {
            // dust and smoke billowing up out of the crater
            int puffs = size == Size.HUGE ? 60 : 20;
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, puffs, size == Size.HUGE ? 3 : 1.5, 1, size == Size.HUGE ? 3 : 1.5, 0.03);
            // the ground shakes, and the boom carries a long way
            double felt = size == Size.HUGE ? 160 : 48;
            for (ServerPlayer player : level.players()) {
                double d = Math.sqrt(player.distanceToSqr(at));
                if (d < felt) {
                    float k = (float) (1.0 - d / felt);
                    PacketDistributor.sendToPlayer(player, new QuakePayload((size == Size.HUGE ? 0.8F : 0.35F) * k, size == Size.HUGE ? 80 : 30));
                }
                if (size == Size.HUGE && d > 48 && d < 600) {
                    player.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 1.0F, 0.5F);
                }
            }
        }
        if (size == Size.HUGE) {
            // the shock wave throws everything nearby away from the blast
            for (Entity e : level.getEntities(this, new AABB(at, at).inflate(24), e -> !e.isSpectator())) {
                Vec3 away = e.position().subtract(at);
                double d = away.length();
                if (d < 0.5) continue;
                double push = 1.6 * (1.0 - d / 24);
                e.push(away.x / d * push, 0.35 * push, away.z / d * push);
                e.hurtMarked = true;
            }
        }
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
