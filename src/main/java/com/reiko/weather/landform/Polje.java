package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A polje: a great flat-floored basin sunk in a limestone plateau, walled all round by steep grey cliffs. It grew as
 * the rock dissolved: rainwater, soured by the soil, ate away at the limestone, and where the water table stood close
 * under the surface the land was worn down flat to it while the rock round about stood firm. With no river running out
 * of it, its only drain is a ponor, a swallow hole at one end, where the stream that crosses the floor sinks into the
 * rock. In the wet months the ponor can't swallow all the water coming in (and sometimes spouts water back up), and the
 * polje floods into a shallow lake; in summer it drains away again, and the rich silt left behind makes its floor the
 * best meadow for miles.
 *
 * @param floor the level of the flat floor
 * @param water the level the seasonal lake rises to (always well under the lowest point of the walls)
 * @param walls how high the walls stand above the floor at their lowest
 * @param hums  {u, v, radius, height} for each small limestone hill standing on the floor
 * @param wet   whether the stream is running (or its bed and the ponor are dry)
 */
public record Polje(int cx, int cz, float angle, int halfLength, int halfWidth, int floor, int water, int walls, int[] hums, boolean wet, long seed)
        implements Landform {
    private static final float FACE = 2.5F, BAND = 4.5F, SLOPE = 12.0F;
    /** Water placed or taken away without nudging the water beside it (so the lake doesn't pour back into what's drained). */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8 || !c.fits(ground, 20, 40)) {
            return null;
        }
        int a = 30 + c.random().nextInt(21), b = 18 + c.random().nextInt(13);
        float angle = c.random().nextFloat() * Mth.PI;
        // the plateau round it has to be fairly level, or the basin's walls would be all one side
        int low = ground, high = ground;
        for (int i = 0; i < 10; i++) {
            float t = i * Mth.TWO_PI / 10.0F;
            double[] p = Terrain.xz(Mth.cos(t) * a * 1.1F, Mth.sin(t) * b * 1.1F, c.x(), c.z(), angle);
            int g = c.groundAt(Mth.floor(p[0]), Mth.floor(p[1]));
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high - low > 16 || low < c.seaLevel() + 4) {
            return null;
        }
        int floor = Math.max(c.seaLevel() + 3, low - 5);
        int walls = 8 + c.random().nextInt(5);
        // the lake never comes within four blocks of the top of the lowest wall
        int water = floor + Math.min(2 + c.random().nextInt(3), walls - 4);
        int n = c.random().nextInt(3);
        int[] hums = new int[n * 4];
        for (int i = 0; i < n; i++) {
            hums[i * 4] = Math.round((c.random().nextFloat() - 0.5F) * a);
            hums[i * 4 + 1] = Math.round((c.random().nextBoolean() ? 1 : -1) * b * (0.4F + c.random().nextFloat() * 0.15F));
            hums[i * 4 + 2] = 3 + c.random().nextInt(3);
            hums[i * 4 + 3] = 5 + c.random().nextInt(4);
        }
        return new Polje(c.x(), c.z(), angle, a, b, floor, water, walls, hums, c.random().nextFloat() < 0.65F, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Polje(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("a"), tag.getInt("b"), tag.getInt("floor"), tag.getInt("water"),
                tag.getInt("walls"), tag.getIntArray("hums"), tag.getBoolean("wet"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("a", halfLength);
        tag.putInt("b", halfWidth);
        tag.putInt("floor", floor);
        tag.putInt("water", water);
        tag.putInt("walls", walls);
        tag.put("hums", new IntArrayTag(hums));
        tag.putBoolean("wet", wet);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.POLJE;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        float ru = halfLength * 1.05F + BAND + SLOPE + 2.0F, rv = halfWidth * 1.05F + BAND + SLOPE + 2.0F;
        for (float u : new float[]{-ru, ru}) {
            for (float v : new float[]{-rv, rv}) {
                double[] p = Terrain.xz(u, v, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        return new BoundingBox(minX, floor - 14, minZ, maxX, floor + 44, maxZ);
    }

    /** How far the basin's edge is from the middle in the direction {@code phi}: an ellipse, its outline gently wavering. */
    private float radius(float phi) {
        float c = Mth.cos(phi) / halfLength, s = Mth.sin(phi) / halfWidth;
        float wobble = 1.0F + 0.05F * Mth.sin(phi * 3.0F + Terrain.hash(seed, 1) * 6.0F) + 0.03F * Mth.sin(phi * 7.0F + Terrain.hash(seed, 2) * 6.0F);
        return wobble / Mth.sqrt(c * c + s * s);
    }

    /** How far out past the floor's edge a point is (below 0 on the floor), in blocks. */
    private float out(float u, float v) {
        return Mth.sqrt(u * u + v * v) - radius((float) Mth.atan2(v, u));
    }

    /** How high the walls stand above the floor in the direction {@code phi}: never under {@link #walls}, at most 18. */
    private int wallHeight(float phi) {
        float t = 0.5F + 0.3F * Mth.sin(phi * 2.0F + Terrain.hash(seed, 3) * 6.0F) + 0.2F * Mth.sin(phi * 5.0F + Terrain.hash(seed, 4) * 6.0F);
        return walls + Math.round(Mth.clamp(t, 0.0F, 1.0F) * (18 - walls));
    }

    private float wiggle(float u) {
        return 5.0F * Mth.sin(u * 0.06F + Terrain.hash(seed, 5) * 6.0F) + 2.5F * Mth.sin(u * 0.15F + Terrain.hash(seed, 6) * 6.0F);
    }

    /** Where the stream rises, at the foot of the walls at one end, and where it sinks, in the ponor at the other. */
    private float spring() {
        return -halfLength * 0.95F + 1.0F;
    }

    private float ponor() {
        return halfLength * 0.95F - 7.0F;
    }

    private static final float PONOR_R = 2.2F, PONOR_DEPTH = 8.0F;

    private float ponorDistance(float u, float v) {
        float p = ponor();
        return Mth.sqrt(Mth.square(u - p) + Mth.square(v - wiggle(p)));
    }

    /** How far from the stream's middle a point on the floor is, or a big number off its course. */
    private float stream(float u, float v) {
        if (u < spring() - 3.0F || u > ponor()) return Float.MAX_VALUE;
        return Math.abs(v - wiggle(u));
    }

    private float streamWidth(float u) {
        // a wider pool where it wells up at the spring
        return 1.1F + 0.4F * Mth.sin(u * 0.2F + Terrain.hash(seed, 7) * 6.0F) + 1.4F * Terrain.smooth((spring() + 4.0F - u) / 4.0F);
    }

    /** How high the hums stand over the floor here (0 off them). */
    private int hum(float u, float v) {
        int best = 0;
        for (int i = 0; i < hums.length / 4; i++) {
            // the stream has long since worn away any that stood in its way
            if (stream(hums[i * 4], hums[i * 4 + 1]) < hums[i * 4 + 2] + 3.0F || ponorDistance(hums[i * 4], hums[i * 4 + 1]) < hums[i * 4 + 2] + 5.0F) continue;
            float d = Mth.sqrt(Mth.square(u - hums[i * 4]) + Mth.square(v - hums[i * 4 + 1])) / hums[i * 4 + 2];
            if (d < 1.0F) best = Math.max(best, Math.round(hums[i * 4 + 3] * Mth.sqrt(1.0F - d * d)));
        }
        return best;
    }

    /** Whether water stands at this height just outside the polje (where it never changes anything), next to this column. */
    private boolean pondBeside(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int h, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            float[] l = Terrain.uv(nx + 0.5, nz + 0.5, cx, cz, angle);
            if (out(l[0], l[1]) > BAND + SLOPE && !level.getFluidState(pos.set(nx, h, nz)).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState limestone = ModBlocks.LIMESTONE.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1], s = out(u, v);
                if (s > BAND + SLOPE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 6) continue;
                // any water lying here already goes (the basin keeps its own)
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(Direction.UP)) surface++;
                float n = Terrain.noise(seed, x, floor, z), m = Terrain.noise(seed + 1, x, floor, z);
                float phi = (float) Mth.atan2(v, u);
                int crest = floor + wallHeight(phi);
                if (s >= 0.0F) {
                    int top;
                    if (s < FACE) {
                        top = Math.round(Mth.lerp(Terrain.smooth(s / FACE), floor + 1, Math.max(crest, ground))); // the cliff
                    } else if (s < BAND) {
                        top = Math.max(crest, ground);
                    } else {
                        top = Math.max(ground, Math.round(Mth.lerp(Terrain.smooth((s - BAND) / SLOPE), crest, ground))); // back down to the plateau
                    }
                    if (s >= BAND && top == ground && surface == ground) continue;
                    Terrain.clearAbove(level, pos, x, Math.max(surface, top), z, Math.min(12, b.maxY() - Math.max(surface, top)));
                    for (int h = top + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, pondBeside(level, pos, x, h, z) ? limestone : Blocks.AIR.defaultBlockState());
                    // solid limestone right through the walls, so nothing (a cave, say) can let the lake out
                    boolean face = s < FACE && top - floor > 2;
                    int from = s < BAND ? Math.min(ground, floor) - 3 : ground + 1;
                    for (int h = Math.max(b.minY(), from); h <= top; h++) {
                        float streak = Terrain.field(seed + 8, x * 2.0 + z * 2.0, h * 0.3, 4.0);
                        BlockState rock = streak > 0.8F ? Blocks.STONE.defaultBlockState() : face && Terrain.noise(seed, x, h, z) < 0.05F
                                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : limestone;
                        if (!face && h == top) rock = Blocks.GRASS_BLOCK.defaultBlockState();
                        else if (!face && h == top - 1 && s >= FACE) rock = Blocks.DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, rock);
                    }
                    if (!face && n > 0.55F) Terrain.set(level, pos, x, top + 1, z, n > 0.9F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    continue;
                }
                // the floor: sealed with a few blocks of limestone under the soil
                int hum = hum(u, v);
                int top = floor + hum;
                Terrain.clearAbove(level, pos, x, Math.max(surface, top), z, Math.min(12, b.maxY() - Math.max(surface, top)));
                for (int h = top + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, pondBeside(level, pos, x, h, z) ? limestone : Blocks.AIR.defaultBlockState());
                for (int h = Math.min(ground + 1, floor - 3); h <= top; h++) {
                    BlockState rock = h == top ? Blocks.GRASS_BLOCK.defaultBlockState() : h == top - 1 && hum == 0 ? Blocks.DIRT.defaultBlockState() : limestone;
                    if (hum > 0 && h == top && hum > 3 && Terrain.noise(seed + 2, x, h, z) < 0.4F) rock = limestone; // bare rock on the hums' tops
                    Terrain.set(level, pos, x, h, z, rock);
                }
                float pd = ponorDistance(u, v), off = stream(u, v);
                if (pd < PONOR_R + 1.6F) {
                    // the ponor: a round shaft in a collar of bare rock, closed at the bottom (the water seeps away through cracks)
                    for (int h = floor - (int) PONOR_DEPTH - 2; h <= floor; h++) Terrain.set(level, pos, x, h, z, limestone);
                    if (pd < PONOR_R) {
                        for (int h = floor - (int) PONOR_DEPTH; h <= floor; h++) {
                            Terrain.set(level, pos, x, h, z, wet && h < floor ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                        continue;
                    }
                    if (off >= streamWidth(u)) continue;
                }
                if (off < streamWidth(u)) {
                    // the stream, a block down in the meadow, running from the spring to the ponor
                    Terrain.set(level, pos, x, floor, z, Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, floor - 1, z, wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    Terrain.set(level, pos, x, floor - 2, z, n < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    if (wet && m > 0.85F) Terrain.set(level, pos, x, floor - 1, z, Blocks.SEAGRASS.defaultBlockState());
                    continue;
                }
                if (hum > 3) continue;
                // the meadow: deep, rich grass, with flowers, and ferns along the stream
                pos.set(x, top + 2, z);
                boolean room = level.getBlockState(pos).isAir();
                if (off < streamWidth(u) + 1.5F && m < 0.5F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.FERN.defaultBlockState());
                } else if (m > 0.93F && room) {
                    BlockState tall = Blocks.TALL_GRASS.defaultBlockState();
                    Terrain.set(level, pos, x, top + 1, z, tall.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                    Terrain.set(level, pos, x, top + 2, z, tall.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                } else if (m > 0.87F) {
                    BlockState flower = n < 0.3F ? Blocks.DANDELION.defaultBlockState() : n < 0.55F ? Blocks.POPPY.defaultBlockState()
                            : n < 0.8F ? Blocks.OXEYE_DAISY.defaultBlockState() : Blocks.CORNFLOWER.defaultBlockState();
                    Terrain.set(level, pos, x, top + 1, z, flower);
                } else if (m > 0.35F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ the seasonal lake

    /** Whether a column is part of the basin the lake can fill: its floor, and the foot of its walls. */
    private boolean basin(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
        return out(l[0], l[1]) < FACE;
    }

    /** The lowest block of a column (in the basin) that water can stand in. */
    private int bottom(int x, int z) {
        float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
        float u = l[0], v = l[1];
        if (out(u, v) >= 0.0F) return floor + 1;
        if (ponorDistance(u, v) < PONOR_R) return floor - (int) PONOR_DEPTH;
        return stream(u, v) < streamWidth(u) ? floor - 1 : floor + 1;
    }

    private static boolean holds(BlockState state) {
        return !state.getFluidState().isEmpty() || state.blocksMotion() && !(state.getBlock() instanceof LiquidBlockContainer);
    }

    /**
     * Whether the walls are still whole at the level of the lake: a ring of samples a little way into them, all solid
     * from the floor up to the flood level. A hole dug through them (or a chunk not loaded) and the lake stays away.
     */
    private boolean sealed(ServerLevel level) {
        int steps = Mth.ceil(Mth.TWO_PI * (halfLength + 6) * 1.1F);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < steps; k++) {
            float phi = k * Mth.TWO_PI / steps, r = radius(phi) + 3.5F;
            double[] p = Terrain.xz(Mth.cos(phi) * r, Mth.sin(phi) * r, cx, cz, angle);
            int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
            pos.set(x, water, z);
            if (!level.isLoaded(pos)) return false;
            for (int h = floor + 1; h <= water; h++) {
                if (!holds(level.getBlockState(pos.set(x, h, z)))) return false;
            }
        }
        return true;
    }

    /**
     * About once a second while a player is near. In the wet seasons (and any downpour outside summer) the ponor backs
     * up and the floor floods, a few columns at a time, up to the lake's level; in summer it drains away again.
     */
    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        if (!WeatherConfig.CHANGING_LANDFORMS.get()) {
            return;
        }
        BlockPos centre = new BlockPos(cx, floor + 1, cz);
        if (!level.isLoaded(centre)) {
            return;
        }
        Season season = WeatherManager.seasonInfo().season();
        LocalWeather local = WeatherManager.local(level, centre);
        boolean rain = local != null && local.type().vanillaRain && local.profile().rain > 0.3F;
        boolean downpour = rain && (local.profile().rain > 0.75F || local.profile().floods);
        boolean hot = local != null && (local.profile().heat || local.type() == WeatherType.HEATWAVE);
        boolean flooding, draining;
        if (season == null) {
            flooding = downpour;
            draining = !rain;
        } else {
            flooding = switch (season) {
                case AUTUMN, SPRING -> true;
                case WINTER -> downpour;
                case SUMMER -> false;
            };
            draining = season == Season.SUMMER && !downpour;
        }
        RandomSource random = level.random;
        if (flooding) {
            if (sealed(level)) {
                int columns = downpour ? 12 : rain ? 7 : 3;
                for (int k = 0; k < columns; k++) flood(level, random);
            }
        } else if (draining) {
            int columns = hot ? 12 : rain ? 2 : 6;
            for (int k = 0; k < columns; k++) drain(level, random);
        }
    }

    /** A random column in the basin: {x, z}. */
    private int[] pick(RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * 1.08;
        double[] p = Terrain.xz((float) (Math.cos(a) * r * halfLength), (float) (Math.sin(a) * r * halfWidth), cx, cz, angle);
        return new int[]{Mth.floor(p[0]), Mth.floor(p[1])};
    }

    /** Raises the water in one column by a block, if it's not yet at the lake's level and the water would stay in the basin. */
    private void flood(ServerLevel level, RandomSource random) {
        int[] c = pick(random);
        int x = c[0], z = c[1];
        if (!basin(x, z)) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int h = bottom(x, z); h <= water; h++) {
            pos.set(x, h, z);
            if (!level.isLoaded(pos)) return;
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().isSourceOfType(Fluids.WATER) || state.blocksMotion()) continue;
            if (!state.isAir() && !state.is(Blocks.WATER) && !state.canBeReplaced()) return;
            // only on something that holds water, and only beside the basin's own walls, water, or more of the basin
            if (!holds(level.getBlockState(pos.set(x, h - 1, z)))) return;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ();
                pos.set(nx, h, nz);
                if (!level.isLoaded(pos) || !holds(level.getBlockState(pos)) && !basin(nx, nz)) return;
            }
            level.setBlock(pos.set(x, h, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
    }

    /** Takes the flood water out of one column (leaving the stream, if it's running, and the ponor's pool). */
    private void drain(ServerLevel level, RandomSource random) {
        int[] c = pick(random);
        int x = c[0], z = c[1];
        if (!basin(x, z)) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lowest = wet ? floor : bottom(x, z);
        for (int h = water; h >= lowest; h--) {
            pos.set(x, h, z);
            if (!level.isLoaded(pos)) return;
            if (level.getBlockState(pos).is(Blocks.WATER)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
        }
    }
}
