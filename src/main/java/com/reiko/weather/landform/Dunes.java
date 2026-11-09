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
 * A field of sand dunes, shaped by the wind that built them. Where sand is plentiful and the wind steady, it piles
 * up in tall rows across the wind (transverse dunes), each with a long, gentle windward slope and a steep lee face.
 * Where there's less sand, it gathers in separate crescents (barchans) with their horns trailing downwind over a thin
 * sheet of sand. Where the wind swings between two directions, the dunes stretch out along it in long, knife-edged
 * ridges (seif dunes); and where it blows from every side in turn, they heap up into great star dunes, a tall peak
 * with arms reaching out in all directions. In sandstorms the wind lifts sand off the crests and drops it beyond, so
 * the dunes slowly creep downwind.
 *
 * @param type what kind of dunes they are (missing from fields saved before there was a choice: those are transverse)
 */
public record Dunes(int cx, int cz, int radius, float angle, int wavelength, int height, int type, long seed) implements Landform {
    public static final int TRANSVERSE = 0, BARCHAN = 1, STAR = 2, SEIF = 3;

    public Dunes(int cx, int cz, int radius, float angle, int wavelength, int height, long seed) {
        this(cx, cz, radius, angle, wavelength, height, TRANSVERSE, seed);
    }

    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 48 + c.random().nextInt(33);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(Math.round(radius * 0.7F), 8) > 12 || !c.fits(ground, 5, 30)) {
            return null;
        }
        float angle = c.random().nextFloat() * Mth.TWO_PI;
        int wavelength = 26 + c.random().nextInt(13), height = 6 + c.random().nextInt(6);
        long seed = c.random().nextLong();
        float pick = c.random().nextFloat();
        int type = pick < 0.4F ? TRANSVERSE : pick < 0.65F ? BARCHAN : pick < 0.85F ? SEIF : STAR;
        return new Dunes(c.x(), c.z(), radius, angle, wavelength, height, type, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Dunes(tag.getInt("x"), tag.getInt("z"), tag.getInt("r"), tag.getFloat("angle"), tag.getInt("wave"), tag.getInt("h"), tag.getInt("type"),
                tag.getLong("seed"));
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
        tag.putInt("type", type);
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

    /** How much sand the dunes pile on the ground at a column. */
    public float duneHeight(double x, double z) {
        double dx = x - cx, dz = z - cz;
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        if (r > radius) return 0.0F;
        double c = Math.cos(angle), s = Math.sin(angle);
        // u downwind, v across the wind
        float u = (float) (dx * c + dz * s), v = (float) (-dx * s + dz * c);
        float edge = Terrain.smooth((radius - r) / (radius * 0.25F));
        return switch (type) {
            case BARCHAN -> barchans(u, v, edge);
            case STAR -> stars(u, v, edge);
            case SEIF -> seifs(u, v, edge);
            default -> transverse(u, v, edge);
        };
    }

    /** Rows across the wind: a gentle rise to the crest, then a steep drop. */
    private float transverse(float u, float v, float edge) {
        float wavy = u + 6.0F * Mth.sin(v * 0.045F + seed % 97) + 3.0F * Mth.sin(v * 0.13F);
        float p = Mth.positiveModulo(wavy / wavelength, 1.0F);
        float profile = p < 0.75F ? p / 0.75F : (1.0F - p) / 0.25F;
        float swell = 0.75F + 0.25F * Mth.sin(v * 0.03F + seed % 13);
        return height * profile * edge * swell;
    }

    /** A thin, patchy sheet of sand between the dunes. */
    private float sheet(float u, float v, float edge) {
        return edge * (0.4F + 1.0F * Terrain.field(seed + 9, u, v, 11.0));
    }

    /** Barchans scattered over the field, one at most in each cell of a loose grid. */
    private float barchans(float u, float v, float edge) {
        float cell = wavelength * 1.7F, best = 0.0F;
        int iu = Mth.floor(u / cell), iv = Mth.floor(v / cell);
        for (int a = iu - 1; a <= iu + 1; a++) {
            for (int b = iv - 1; b <= iv + 1; b++) {
                long cs = seed + a * 341873128712L + b * 132897987541L;
                if (Terrain.hash(cs, 0) < 0.25F) continue; // a gap between them
                float bu = (a + 0.2F + 0.6F * Terrain.hash(cs, 1)) * cell, bv = (b + 0.2F + 0.6F * Terrain.hash(cs, 2)) * cell;
                float size = cell * (0.22F + 0.12F * Terrain.hash(cs, 3));
                best = Math.max(best, barchan(u - bu, v - bv, size));
            }
        }
        return Math.max(best * edge, sheet(u, v, edge));
    }

    /**
     * One barchan: a disc of sand with a bite taken out of its downwind side. What's left is a crescent, its gentle
     * back to the wind, its steep slip face inside the curve, and its horns trailing downwind.
     */
    private float barchan(float a, float b, float r) {
        float outer = r - Mth.sqrt(a * a + b * b);
        if (outer <= 0.0F) return 0.0F;
        float inner = Mth.sqrt(Mth.square(a - 0.6F * r) + b * b) - 0.75F * r;
        if (inner <= 0.0F) return 0.0F;
        float peak = Math.min(height, r * 0.45F);
        float h = Math.min(Terrain.smooth(outer / (r * 0.85F)), Terrain.smooth(inner / Math.max(2.5F, peak * 0.8F)));
        return peak * h * (1.0F - 0.5F * Mth.square(b / r));
    }

    /** One great star dune in the middle of the field, with up to two smaller ones beside it. */
    private float stars(float u, float v, float edge) {
        float best = 0.0F;
        int count = 1 + Math.round(Terrain.hash(seed, 40) * 2.0F);
        for (int i = 0; i < count; i++) {
            float su = 0.0F, sv = 0.0F, size = radius * 0.6F;
            if (i > 0) {
                float a = Terrain.hash(seed, 41 + i) * Mth.TWO_PI, d = radius * (0.5F + 0.15F * Terrain.hash(seed, 44 + i));
                su = Mth.cos(a) * d;
                sv = Mth.sin(a) * d;
                size = radius * (0.25F + 0.1F * Terrain.hash(seed, 47 + i));
            }
            best = Math.max(best, star(u - su, v - sv, size, i));
        }
        return Math.max(best * edge, sheet(u, v, edge));
    }

    /** A star dune: a tall, steep central peak with 3-5 sharp-crested arms winding out from it. */
    private float star(float a, float b, float size, int i) {
        float d = Mth.sqrt(a * a + b * b);
        if (d > size) return 0.0F;
        float top = height * 1.8F * Math.min(1.0F, size / (radius * 0.6F)) + 2.0F;
        float peak = top * (float) Math.pow(Math.max(0.0F, 1.0F - d / (size * 0.4F)), 1.4);
        float theta = (float) Math.atan2(b, a), t = d / size, arms = 0.0F;
        int n = 3 + Math.round(Terrain.hash(seed, 50 + i) * 2.0F);
        float start = Terrain.hash(seed, 55 + i) * Mth.TWO_PI;
        for (int k = 0; k < n; k++) {
            float centre = start + k * Mth.TWO_PI / n + (Terrain.hash(seed, 60 + i * 8 + k) - 0.5F) * 0.6F
                    + (Terrain.hash(seed, 70 + i * 8 + k) - 0.5F) * 1.4F * t;
            float off = d * (float) Math.abs(Math.IEEEremainder(theta - centre, Math.PI * 2.0));
            float h = top * 0.62F * (float) Math.pow(1.0F - t, 0.9);
            arms = Math.max(arms, h * Math.max(0.0F, 1.0F - off / (h * 1.5F + 1.0F)));
        }
        return Math.max(peak, arms);
    }

    /** Seif dunes: long, sinuous, knife-edged ridges running with the wind, with bare corridors between them. */
    private float seifs(float u, float v, float edge) {
        float spacing = wavelength * 1.1F;
        float w = v + 5.0F * Mth.sin(u * 0.035F + v * 0.01F + Terrain.hash(seed, 80) * 6.0F) + 2.0F * Mth.sin(u * 0.09F + v * 0.03F);
        int ridge = Mth.floor(w / spacing + 0.5F);
        float off = Math.abs(w - ridge * spacing);
        // the crest rises and falls in a chain of peaks and saddles along each ridge
        float crest = height * (0.7F + 0.3F * Mth.sin(u * 0.05F + ridge * 1.7F + Terrain.hash(seed, 81) * 6.0F));
        float h = crest * (float) Math.pow(Math.max(0.0F, 1.0F - off / (crest * 1.7F)), 1.3);
        return Math.max(h * edge, sheet(u, v, edge) * 0.6F);
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
