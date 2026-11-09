package com.reiko.weather.block;

import com.mojang.serialization.MapCodec;
import com.reiko.weather.weather.Tides;
import com.reiko.weather.weather.WeatherAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The deep hole in the sea bed under a tidal whirlpool. When the tide is running hard through the strait (in the
 * hours between high and low water, strongest at spring tides) the water above it spins into a whirlpool that
 * drags boats and swimmers round and down. At slack water it's calm.
 */
public class WhirlpoolBlock extends Block {
    public static final MapCodec<WhirlpoolBlock> CODEC = simpleCodec(WhirlpoolBlock::new);
    private static final int RADIUS = 7;

    public WhirlpoolBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    /** How hard the tide is running, 0 (slack water) to 1 (mid-tide at springs). */
    public static float strength(Level level) {
        // the tide runs fastest halfway between high and low water
        float flow = Math.abs(Mth.sin((float) ((level.getDayTime() - 6000L) / 12000.0 * Math.PI * 2.0)));
        return Mth.clamp((flow - 0.35F) / 0.65F, 0.0F, 1.0F) * (0.5F + 0.5F * Tides.springness(level));
    }

    /** The water surface above the hole, or -1 if the hole isn't under deep enough water. */
    private static int surface(Level level, BlockPos pos) {
        int top = -1;
        for (int d = 1; d <= 40; d++) {
            if (level.getFluidState(pos.above(d)).is(FluidTags.WATER)) top = pos.getY() + d;
            else break;
        }
        return top - pos.getY() >= 5 ? top : -1;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 2);
        float s = strength(level);
        if (s <= 0.05F) return;
        int top = surface(level, pos);
        if (top < 0) return;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        AABB box = new AABB(cx - RADIUS, pos.getY() + 1, cz - RADIUS, cx + RADIUS, top + 1.5, cz + RADIUS);
        for (Entity e : level.getEntities((Entity) null, box, e -> !e.isSpectator() && (e.isInWater() || e instanceof net.minecraft.world.entity.vehicle.Boat))) {
            double dx = e.getX() - cx, dz = e.getZ() - cz, r = Math.sqrt(dx * dx + dz * dz);
            if (r > RADIUS || r < 0.01) continue;
            // round and round (anticlockwise), drawn inward, and dragged under near the middle
            double spin = 0.09 * s * (1.0 - r / RADIUS * 0.5), in = 0.03 * s;
            Vec3 v = e.getDeltaMovement();
            double down = r < 2.5 ? -0.06 * s : 0.0;
            e.setDeltaMovement(v.x + (-dz / r) * spin - dx / r * in, v.y + down, v.z + (dx / r) * spin - dz / r * in);
            e.hurtMarked = true;
            if (e instanceof ServerPlayer player && r < 3.0) WeatherAdvancements.award(player, "maelstrom");
        }
        if (random.nextInt(60) == 0) {
            level.playSound(null, cx, top, cz, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.AMBIENT, 2.0F * s, 0.6F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        float s = strength(level);
        if (s <= 0.05F) return;
        int top = surface(level, pos);
        if (top < 0) return;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        float time = (level.getGameTime() % 100000L) * 0.15F * s;
        for (int i = 0; i < 6; i++) {
            // foam spiralling in round the surface
            float r = 1.0F + random.nextFloat() * (RADIUS - 1), a = time + r * 0.9F + random.nextFloat() * 0.6F + i * Mth.TWO_PI / 6.0F;
            level.addParticle(ParticleTypes.BUBBLE_POP, cx + Mth.cos(a) * r, top + 0.95, cz + Mth.sin(a) * r, -Mth.sin(a) * 0.05, 0.0, Mth.cos(a) * 0.05);
            if (r < 2.5F) level.addParticle(ParticleTypes.CURRENT_DOWN, cx + Mth.cos(a) * r, top - random.nextFloat() * 3.0, cz + Mth.sin(a) * r, 0, 0, 0);
        }
    }
}
