package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A dry lake bed in the desert: dead flat, crusted white with salt. When a storm passes over, it floods into
 * a shallow, mirror-like lake, which dries back out to salt afterwards.
 */
public record SaltFlat(int cx, int cz, int y, int rx, int rz, float angle, long seed) implements Landform {
    private static final float RIM = 1.6F;

    @Nullable
    static Landform plan(PlanContext c) {
        int rx = 34 + c.random().nextInt(31), rz = 22 + c.random().nextInt(21);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || !c.fits(ground, 10, 30)) {
            return null;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            sum += c.groundAt(c.x() + (int) Math.round(Math.cos(a) * rz * 0.6), c.z() + (int) Math.round(Math.sin(a) * rz * 0.6));
        }
        int y = Math.min(ground, sum / 8) - 1;
        return new SaltFlat(c.x(), c.z(), Math.max(y, c.seaLevel() + 1), rx, rz, c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SaltFlat(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("rx"), tag.getInt("rz"), tag.getFloat("angle"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("rx", rx);
        tag.putInt("rz", rz);
        tag.putFloat("angle", angle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SALT_FLAT;
    }

    @Override
    public BoundingBox bounds() {
        int r = (int) Math.ceil(Math.max(rx, rz) * Math.sqrt(RIM)) + 1;
        return new BoundingBox(cx - r, y - 8, cz - r, cx + r, y + 30, cz + r);
    }

    /** (u/rx)^2 + (v/rz)^2: under 1 on the flat itself. */
    public float ellipse(double x, double z) {
        double dx = x - cx, dz = z - cz;
        double c = Math.cos(angle), s = Math.sin(angle);
        double u = dx * c + dz * s, v = -dx * s + dz * c;
        return (float) (u * u / (rx * rx) + v * v / (rz * rz));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float e = ellipse(x + 0.5, z + 0.5);
                if (e > RIM) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (e <= 1.0F) {
                    Terrain.clearAbove(level, pos, x, Math.min(ground, y), z, 30);
                    for (int h = y + 1; h <= ground; h++) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState()); // the dunes are planed away
                    }
                    for (int h = Math.min(ground, y) - 2; h < y; h++) {
                        Terrain.set(level, pos, x, h, z, h < y - 1 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    }
                    boolean sandy = e > 0.85F && Terrain.noise(seed, x, 0, z) < (e - 0.85F) * 4.0F;
                    Terrain.set(level, pos, x, y, z, sandy ? Blocks.SAND.defaultBlockState() : ModBlocks.SALT_CRUST.get().defaultBlockState());
                } else if (ground < y + 1) {
                    // a low lip all the way round holds the flood water in
                    Terrain.clearAbove(level, pos, x, ground, z, 6);
                    for (int h = ground + 1; h <= y + 1; h++) {
                        Terrain.set(level, pos, x, h, z, Blocks.SAND.defaultBlockState());
                    }
                }
            }
        }
    }

    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.CHANGING_LANDFORMS.get()) {
            return;
        }
        BlockPos center = new BlockPos(cx, y + 1, cz);
        if (!level.isLoaded(center)) {
            return;
        }
        LocalWeather local = WeatherManager.local(level, center);
        // a storm overhead (it can rain here even when the desert's vanilla rain doesn't fall)
        boolean wet = local != null && local.type().vanillaRain && local.intensity() > 0.4F;
        boolean hot = local != null && local.type() == WeatherType.HEATWAVE;
        RandomSource random = level.random;
        int samples = wet ? 48 : hot ? 16 : 8;
        for (int i = 0; i < samples; i++) {
            double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble());
            double c = Math.cos(angle), s = Math.sin(angle);
            double u = Math.cos(a) * r * rx, v = Math.sin(a) * r * rz;
            int x = Mth.floor(cx + u * c - v * s), z = Mth.floor(cz + u * s + v * c);
            BlockPos crust = new BlockPos(x, y, z), above = crust.above();
            if (!level.isLoaded(crust)) continue;
            if (wet) {
                // sources kept apart (flowing water fills between them), so vanilla never joins them into a
                // permanent lake: once the sources dry up, the rest drains away by itself
                if (level.getBlockState(crust).is(ModBlocks.SALT_CRUST.get()) && level.getBlockState(above).isAir() && level.canSeeSky(above)
                        && BlockPos.betweenClosedStream(above.offset(-2, 0, -2), above.offset(2, 0, 2)).noneMatch(p -> level.getFluidState(p).isSource())) {
                    level.setBlock(above, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                }
            } else if (level.getFluidState(above).is(FluidTags.WATER)) {
                // evaporating in the sun: take away the source feeding this stretch, and the rest drains off
                BlockPos.betweenClosedStream(above.offset(-2, 0, -2), above.offset(2, 0, 2))
                        .filter(p -> level.getFluidState(p).isSource() && level.getBlockState(p.below()).is(ModBlocks.SALT_CRUST.get()))
                        .findFirst().ifPresent(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            }
        }
    }
}
