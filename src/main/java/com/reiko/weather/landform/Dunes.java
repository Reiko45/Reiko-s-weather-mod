package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A field of tall sand dunes in rows across the prevailing wind, each with a long, gentle windward slope and
 * a steep lee face. In sandstorms the wind lifts sand off the crests and drops it beyond, so the dunes
 * slowly creep downwind.
 */
public record Dunes(int cx, int cz, int radius, float angle, int wavelength, int height, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 48 + c.random().nextInt(33);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(Math.round(radius * 0.7F), 8) > 12 || !c.fits(ground, 5, 30)) {
            return null;
        }
        return new Dunes(c.x(), c.z(), radius, c.random().nextFloat() * Mth.TWO_PI, 26 + c.random().nextInt(13), 6 + c.random().nextInt(6), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Dunes(tag.getInt("x"), tag.getInt("z"), tag.getInt("r"), tag.getFloat("angle"), tag.getInt("wave"), tag.getInt("h"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("r", radius);
        tag.putFloat("angle", angle);
        tag.putInt("wave", wavelength);
        tag.putInt("h", height);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.DUNES;
    }

    @Override
    public BoundingBox bounds() {
        return new BoundingBox(cx - radius - 1, -64, cz - radius - 1, cx + radius + 1, 319, cz + radius + 1);
    }

    /** How much sand the dunes pile on the ground at a column: a gentle rise to the crest, then a steep drop. */
    public float duneHeight(double x, double z) {
        double dx = x - cx, dz = z - cz;
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        if (r > radius) return 0.0F;
        double c = Math.cos(angle), s = Math.sin(angle);
        float u = (float) (dx * c + dz * s), v = (float) (-dx * s + dz * c);
        float wavy = u + 6.0F * Mth.sin(v * 0.045F + seed % 97) + 3.0F * Mth.sin(v * 0.13F);
        float p = Mth.positiveModulo(wavy / wavelength, 1.0F);
        float profile = p < 0.75F ? p / 0.75F : (1.0F - p) / 0.25F;
        float edge = Terrain.smooth((radius - r) / (radius * 0.25F));
        float swell = 0.75F + 0.25F * Mth.sin(v * 0.03F + seed % 13);
        return height * profile * edge * swell;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BoundingBox b = bounds();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int h = Math.round(duneHeight(x + 0.5, z + 0.5));
                if (h < 1) continue;
                int ground = Terrain.ground(level, pos, x, z);
                Terrain.clearAbove(level, pos, x, ground, z, h + 4);
                for (int k = 1; k <= h; k++) {
                    Terrain.set(level, pos, x, ground + k, z, h - k > 3 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.SAND.defaultBlockState());
                }
            }
        }
    }

    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.CHANGING_LANDFORMS.get()) {
            return;
        }
        LocalWeather local = WeatherManager.local(level, player.blockPosition());
        if (local == null || local.type() != WeatherType.SANDSTORM || local.intensity() < 0.5F) {
            return;
        }
        // the wind lifts sand off the windward slopes and crests and drops it just beyond
        Vec3 wind = Wind.direction(WeatherManager.state(level).windAngle);
        RandomSource random = level.random;
        for (int i = 0; i < 24; i++) {
            double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * radius;
            int x = Mth.floor(cx + Math.cos(a) * r), z = Mth.floor(cz + Math.sin(a) * r);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            BlockPos from = new BlockPos(x, top, z);
            if (!level.getBlockState(from).is(Blocks.SAND)) continue;
            int nx = x + Math.round((float) wind.x * 2.0F), nz = z + Math.round((float) wind.z * 2.0F);
            int ntop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz) - 1;
            BlockPos to = new BlockPos(nx, ntop, nz);
            BlockState land = level.getBlockState(to);
            if (ntop > top || !(land.is(BlockTags.SAND) || land.is(Blocks.SANDSTONE)) || !level.getBlockState(to.above()).isAir()) continue;
            level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(to.above(), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
