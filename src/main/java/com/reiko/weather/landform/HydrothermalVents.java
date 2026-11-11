package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * A hydrothermal vent field: black smokers on the deep sea floor. Sea water seeps down through cracks in young volcanic
 * rock until the magma below heats it far past boiling (the weight of the sea keeps it from boiling), then gushes back
 * up loaded with dissolved metals. Where it meets the near-freezing water of the deep, the metal sulphides come out of
 * it in billows of black "smoke", and build chimneys round the vents, some as tall as a house and branching like
 * candlesticks, on mounds of their own fallen crust stained yellow and orange with sulphur and rust. No sunlight
 * reaches this far down, but bacteria living on the chemicals in the vent water feed a ring of life round the field:
 * thickets of giant tube worms, white tubes tipped with blood-red plumes, and glowing sea pickles.
 *
 * @param floor    the sea bed at the middle
 * @param chimneys {x, z, foot y, height, branches} for each chimney
 */
public record HydrothermalVents(int cx, int cz, int sea, int floor, int radius, int[] chimneys, long seed) implements Landform {
    private static final int[] DX = {1, 0, -1, 0}, DZ = {0, 1, 0, -1};
    /** How high the little cone of crust round each chimney's foot stands, and how far it spreads. */
    private static final float CONE = 1.5F, CONE_R = 3.5F;
    private static final BlockState[] DEAD_FANS = {Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL_FAN.defaultBlockState(), Blocks.DEAD_FIRE_CORAL_FAN.defaultBlockState(), Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState()};
    private static final BlockState[] FANS = {Blocks.TUBE_CORAL_FAN.defaultBlockState(), Blocks.BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.BUBBLE_CORAL_FAN.defaultBlockState(), Blocks.FIRE_CORAL_FAN.defaultBlockState(), Blocks.HORN_CORAL_FAN.defaultBlockState()};
    private static final BlockState[] DEAD_CORAL = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};

    @Nullable
    static Landform plan(PlanContext c) {
        int floor = c.groundAt(c.x(), c.z());
        if (floor > c.seaLevel() - 22 || !c.fits(floor, 12, 20)) {
            return null;
        }
        int radius = 12 + c.random().nextInt(7);
        // a broad, fairly level stretch of deep sea floor
        if (c.relief(radius + 4, 8) > 8) {
            return null;
        }
        long seed = c.random().nextLong();
        HydrothermalVents probe = new HydrothermalVents(c.x(), c.z(), c.seaLevel(), floor, radius, new int[0], seed);
        int n = 4 + c.random().nextInt(6);
        int[] chimneys = new int[n * 5];
        int k = 0;
        for (int attempt = 0; attempt < n * 4 && k < n; attempt++) {
            float a = c.random().nextFloat() * Mth.TWO_PI, d = Mth.sqrt(c.random().nextFloat()) * radius * 0.65F;
            int x = c.x() + Math.round(Mth.cos(a) * d), z = c.z() + Math.round(Mth.sin(a) * d);
            boolean clear = true;
            for (int j = 0; j < k && clear; j++) clear = Mth.square(x - chimneys[j * 5]) + Mth.square(z - chimneys[j * 5 + 1]) >= 36;
            if (!clear) continue;
            // each stands on its own little cone of crust, on the mound; and its mouth stays well under the water
            int foot = c.groundAt(x, z) + Math.round(probe.mound(x + 0.5F, z + 0.5F) + CONE);
            int height = Math.min(3 + c.random().nextInt(10), c.seaLevel() - 9 - foot);
            if (height < 3) continue;
            chimneys[k * 5] = x;
            chimneys[k * 5 + 1] = z;
            chimneys[k * 5 + 2] = foot;
            chimneys[k * 5 + 3] = height;
            chimneys[k * 5 + 4] = height >= 7 ? c.random().nextInt(3) : 0;
            k++;
        }
        if (k < 4) {
            return null;
        }
        return new HydrothermalVents(c.x(), c.z(), c.seaLevel(), floor, radius, Arrays.copyOf(chimneys, k * 5), seed);
    }

    static Landform load(CompoundTag tag) {
        return new HydrothermalVents(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getInt("floor"), tag.getInt("r"),
                tag.getIntArray("chimneys"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putInt("floor", floor);
        tag.putInt("r", radius);
        tag.put("chimneys", new IntArrayTag(chimneys));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.HYDROTHERMAL_VENTS;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 1.35F) + 2;
        return new BoundingBox(cx - r, floor - 14, cz - r, cx + r, sea - 1, cz + r);
    }

    /** How far out from the middle of the field a point is: 1 at the edge of the mound. */
    private float spread(float px, float pz) {
        return Mth.sqrt(Mth.square(px - cx - 0.5F) + Mth.square(pz - cz - 0.5F)) / (radius * (0.8F + 0.3F * Terrain.field(seed + 1, px, pz, 7.0)));
    }

    /** How high the broad mound of crust the chimneys stand on is built up over the old sea bed. */
    private float mound(float px, float pz) {
        float d = spread(px, pz);
        return d >= 1.0F ? 0.0F : 2.6F * Terrain.smooth(1.0F - d) * (0.7F + 0.3F * Terrain.field(seed + 2, px, pz, 4.0));
    }

    /** The nearest chimney to a column: {index, distance}. */
    private float[] nearest(int x, int z) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < chimneys.length / 5; i++) {
            float d = Mth.sqrt(Mth.square(x - chimneys[i * 5]) + Mth.square(z - chimneys[i * 5 + 1]));
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return new float[]{best, bd};
    }

    /** The radius of chimney {@code i}'s stack at a height: broad at the foot, drawing in to a narrow throat at the top. */
    private float stack(int i, int h) {
        int foot = chimneys[i * 5 + 2], height = chimneys[i * 5 + 3];
        if (h < foot - 1 || h > foot + height) return 0.0F;
        float t = Mth.clamp((h - foot) / (float) height, 0.0F, 1.0F);
        float base = Math.min(1.85F, 1.25F + 0.06F * height);
        return 0.62F + (base - 0.62F) * (float) Math.pow(1.0F - t, 1.3) + 0.2F * (Terrain.noise(seed + 9 + i, 0, h, 0) - 0.5F);
    }

    private boolean inStack(int i, int dx, int dz, int h) {
        return Mth.sqrt(dx * dx + dz * dz) < stack(i, h);
    }

    /** Branch {@code j} of chimney {@code i}: {direction, junction y, top y}. */
    private int[] branch(int i, int j) {
        int foot = chimneys[i * 5 + 2], height = chimneys[i * 5 + 3];
        int dir = (Mth.floor(Terrain.hash(seed, 200 + i * 7) * 4.0F) + j * 2) & 3;
        int junction = foot + 2 + Mth.floor(Terrain.hash(seed, 210 + i * 7 + j) * (height / 2.0F - 1.0F));
        int top = Math.min(junction + 2 + Mth.floor(Terrain.hash(seed, 220 + i * 7 + j) * 2.99F), foot + height - 2);
        return new int[]{dir, junction, top};
    }

    /**
     * What chimney {@code i} has in the column {@code dx, dz} from its middle at height {@code h}: 0 nothing, 1 rock,
     * 2 a vent's mouth.
     */
    private int part(int i, int dx, int dz, int h) {
        int foot = chimneys[i * 5 + 2], height = chimneys[i * 5 + 3];
        if (dx == 0 && dz == 0 && h == foot + height) return 2;
        if (inStack(i, dx, dz, h)) return 1;
        for (int j = 0; j < chimneys[i * 5 + 4]; j++) {
            int[] br = branch(i, j);
            if (br[2] <= br[1]) continue;
            int bx = DX[br[0]], bz = DZ[br[0]];
            // an arm out from the side of the stack, and a spire of its own rising from it
            if (dx == bx && dz == bz && h == br[1]) return 1;
            if (dx == 2 * bx && dz == 2 * bz && h >= br[1] && h <= br[2]) return h == br[2] ? 2 : 1;
        }
        return 0;
    }

    /** Whether any chimney stands in (or reaches over) a column. */
    private boolean occupied(int x, int z) {
        for (int i = 0; i < chimneys.length / 5; i++) {
            int dx = x - chimneys[i * 5], dz = z - chimneys[i * 5 + 1];
            if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                for (int h = chimneys[i * 5 + 2] - 1; h <= chimneys[i * 5 + 2] + chimneys[i * 5 + 3]; h++) {
                    if (part(i, dx, dz, h) > 0) return true;
                }
            }
        }
        return false;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                float d = spread(px, pz);
                if (d > 1.2F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                // only on the open sea bed, deep under the water
                if (ground < b.minY() + 2 || ground > sea - 12 || level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float[] near = nearest(x, z);
                int ci = (int) near[0];
                float dc = near[1];
                float cone = dc < CONE_R ? CONE * Terrain.smooth(1.0F - dc / CONE_R) : 0.0F;
                int surface = ground + Math.round(mound(px, pz) + cone);
                int ox = chimneys[ci * 5], oz = chimneys[ci * 5 + 1];
                boolean chimney = Math.abs(x - ox) <= 2 && Math.abs(z - oz) <= 2 && occupied(x, z);
                int high = chimney ? chimneys[ci * 5 + 2] + chimneys[ci * 5 + 3] : surface + 4;
                // weed rooted in the old sea bed would be left buried or hanging, so it goes
                for (int h = ground + 1; h <= Math.min(high, b.maxY()); h++) {
                    pos.set(x, h, z);
                    BlockState was = level.getBlockState(pos);
                    if (was.is(Blocks.KELP) || was.is(Blocks.KELP_PLANT) || was.is(Blocks.SEAGRASS) || was.is(Blocks.TALL_SEAGRASS)) {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Terrain.FLAGS);
                    }
                }
                // the mound of crust, stained hottest round the chimneys' feet (and beyond it, the bare old sea bed)
                if (d < 1.0F || surface > ground) {
                    for (int h = ground; h <= surface; h++) Terrain.set(level, pos, x, h, z, crust(x, h, z, h == surface, dc, d));
                }
                if (chimney) {
                    chimney(level, pos, x, z, ci, surface);
                } else {
                    life(level, pos, x, z, surface, ci, dc, d);
                }
            }
        }
    }

    private BlockState crust(int x, int h, int z, boolean top, float dc, float d) {
        float m = Terrain.noise(seed + 3, x, h, z);
        if (top && dc < 3.0F) {
            return m < 0.4F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState() : m < 0.75F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        if (top) {
            float stain = Terrain.field(seed + 4, x, z, 4.0) * (1.0F - d);
            if (stain > 0.42F) return m < 0.5F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            if (m < 0.14F) return DEAD_CORAL[(int) (m * 100.0F) % DEAD_CORAL.length];
        }
        return m < 0.55F ? Blocks.TUFF.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
    }

    /** One column of a chimney: its stack of dark sulphide rock (with a coal of magma sealed in its root), and the vents. */
    private void chimney(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int i, int surface) {
        int ox = chimneys[i * 5], oz = chimneys[i * 5 + 1], foot = chimneys[i * 5 + 2], height = chimneys[i * 5 + 3];
        int dx = x - ox, dz = z - oz;
        int top = foot + height;
        // where the sea bed lies lower than it was planned, the stack's root reaches down to it
        boolean root = Mth.sqrt(dx * dx + dz * dz) < stack(i, foot);
        for (int h = surface + 1; h <= top; h++) {
            int part = part(i, dx, dz, h);
            if (part == 0 && !(root && h < foot)) continue;
            BlockState s;
            if (part == 2) {
                // the mouth: only where the sea is open over it
                s = level.getFluidState(pos.set(x, h + 1, z)).isEmpty() ? Blocks.BLACKSTONE.defaultBlockState() : ModBlocks.VOLCANIC_VENT.get().defaultBlockState();
            } else if (dx == 0 && dz == 0 && h == foot + 1 && height >= 5 && sealed(i, h)) {
                s = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else {
                float m = Terrain.noise(seed + 5, x, h, z);
                float t = (h - foot) / (float) height;
                s = m < 0.42F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.68F ? Blocks.BASALT.defaultBlockState()
                        : m < 0.82F ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : m < 0.9F || t > 0.6F ? Blocks.SMOOTH_BASALT.defaultBlockState()
                        : Blocks.ORANGE_TERRACOTTA.defaultBlockState(); // rust bleeding out of the lower stack
            }
            Terrain.set(level, pos, x, h, z, s);
        }
    }

    /** Whether the middle of chimney {@code i}'s stack at height {@code h} is shut in by rock on every side (and above and below). */
    private boolean sealed(int i, int h) {
        if (part(i, 0, 0, h + 1) != 1 || part(i, 0, 0, h - 1) != 1) return false;
        for (int k = 0; k < 4; k++) {
            if (part(i, DX[k], DZ[k], h) != 1) return false;
        }
        return true;
    }

    /** Whether a column is in one of the thickets of tube worms (one or two) crowding chimney {@code i}'s foot. */
    private boolean thicket(int i, int x, int z) {
        int clusters = 1 + Mth.floor(Terrain.hash(seed, 600 + i * 5) * 1.8F);
        for (int k = 0; k < clusters; k++) {
            float a = Terrain.hash(seed, 601 + i * 5 + k) * Mth.TWO_PI + k * Mth.PI;
            float d = 2.4F + 1.2F * Terrain.hash(seed, 603 + i * 5 + k), r = 1.1F + 0.5F * Terrain.hash(seed, 700 + i * 5 + k);
            float tx = chimneys[i * 5] + 0.5F + Mth.cos(a) * d, tz = chimneys[i * 5 + 1] + 0.5F + Mth.sin(a) * d;
            if (Mth.square(x + 0.5F - tx) + Mth.square(z + 0.5F - tz) < r * r) return true;
        }
        return false;
    }

    /** The ring of life round the vents: tube worms crowding the chimneys' feet, sea pickles and coral fans further out. */
    private void life(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int surface, int ci, float dc, float d) {
        if (level.getFluidState(pos.set(x, surface + 1, z)).isEmpty()) return;
        float n = Terrain.noise(seed + 6, x, surface, z);
        if (dc < 5.0F && thicket(ci, x, z) && n < 0.6F) {
            // a tube worm: a white tube with its red plume out in the warm water
            int tall = 1 + Mth.floor(Terrain.noise(seed + 8, x, surface, z) * 2.99F);
            for (int h = 1; h <= tall; h++) Terrain.set(level, pos, x, surface + h, z, Blocks.WHITE_TERRACOTTA.defaultBlockState());
            Terrain.set(level, pos, x, surface + tall + 1, z, Blocks.RED_CONCRETE.defaultBlockState());
            return;
        }
        if (dc < 2.5F || d < 0.45F) return;
        if (n > 0.92F) {
            Terrain.set(level, pos, x, surface + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (n * 400.0F) % 4));
        } else if (n > 0.84F && d > 0.6F) {
            // coral only lives where the sea is warm enough for it; elsewhere just the bleached fans of old
            Holder<Biome> biome = level.getBiome(pos.set(x, surface + 1, z));
            boolean warm = biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN);
            int pick = (int) (n * 997.0F) % FANS.length;
            Terrain.set(level, pos, x, surface + 1, z, warm && n > 0.88F ? FANS[pick] : DEAD_FANS[pick]);
        }
    }
}
