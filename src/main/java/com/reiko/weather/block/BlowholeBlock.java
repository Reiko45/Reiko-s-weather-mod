package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.Tides;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The mouth of a blowhole: the top of a shaft running down into a sea cave. When a big wave surges into the
 * cave it squeezes the air and water up the shaft in a booming plume of spray, more often (and higher) when
 * the sea is rough or the tide is in. It only works with sea water at the bottom of the shaft.
 */
public class BlowholeBlock extends Block {
    public static final MapCodec<BlowholeBlock> CODEC = simpleCodec(BlowholeBlock::new);

    public BlowholeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 100 + level.random.nextInt(200));
        }
    }

    /** How rough the sea is here, 0 to 1. */
    private static float swell(ServerLevel level, BlockPos pos) {
        LocalWeather local = WeatherManager.local(level, pos);
        float wind = local == null ? 0.2F : local.profile().wind * local.intensity();
        return Math.min(1.0F, 0.2F + wind + (Tides.flooding(level) ? 0.25F : 0.0F));
    }

    /** The water at the bottom of the shaft, or -1 if there isn't any (or the shaft is blocked). */
    private static int depthToWater(Level level, BlockPos pos) {
        for (int d = 1; d <= 16; d++) {
            BlockPos p = pos.below(d);
            if (level.getFluidState(p).is(FluidTags.WATER)) return d;
            if (!level.getBlockState(p).isAir()) return -1;
        }
        return -1;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        float swell = swell(level, pos);
        level.scheduleTick(pos, this, (int) (60 + random.nextInt(160) * (1.2F - swell)));
        if (depthToWater(level, pos) < 0 || !level.getBlockState(pos.above()).isAir() || random.nextFloat() > 0.3F + swell) {
            return;
        }
        float height = 4.0F + 14.0F * swell * (0.6F + 0.4F * random.nextFloat());
        level.playSound(null, pos, ModSounds.BLOWHOLE.get(), SoundSource.BLOCKS, 1.5F + swell * 2.0F, 0.8F + random.nextFloat() * 0.3F);
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) > 160 * 160) continue;
            for (float k = 0; k < height; k += 1.5F) {
                double spread = 0.15 + k * 0.05;
                level.sendParticles(player, ParticleTypes.CLOUD, true, x, y + k, z, 2, spread, 0.4, spread, 0.02);
                level.sendParticles(player, ParticleTypes.SPLASH, true, x, y + k, z, 6, spread, 0.5, spread, 0.0);
            }
            level.sendParticles(player, ParticleTypes.FALLING_WATER, true, x, y + height, z, 20, 2.5, 0.5, 2.5, 0.0);
        }
        for (Entity entity : level.getEntities((Entity) null, new AABB(pos.above()).inflate(0.6, 0.0, 0.6).expandTowards(0, height * 0.6, 0),
                e -> !e.isSpectator())) {
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(v.x * 0.5, Math.max(v.y, 0.5 + 0.6 * swell), v.z * 0.5);
            entity.hurtMarked = true;
            entity.resetFallDistance();
            if (entity instanceof ServerPlayer player) WeatherAdvancements.award(player, "blowhole");
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0 && depthToWater(level, pos) > 0) {
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
        }
    }
}
