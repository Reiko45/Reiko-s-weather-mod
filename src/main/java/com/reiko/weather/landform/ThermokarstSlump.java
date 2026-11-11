package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A retrogressive thaw slump: a raw, horseshoe-shaped scar bitten into a hillside where the permafrost is thawing.
 * The frozen ground here is packed with ice, in lenses and layers and in wedges driven down by thousands of winters of
 * frost cracking, and once a bank gives way or a summer runs too warm, the ice bared in its face starts to melt and the
 * ground slumps off it as mud. Every summer the icy headwall melts back further, so the scar eats its way uphill, while
 * below it the mud flows off down the slope in a lobe, carrying the spruce trees that stood on it and leaving them
 * tilted every which way, or lying flat, stripped of their needles: a "drunken forest".
 *
 * @param y      the ground at the middle of the scar
 * @param angle  the way the land falls
 * @param drop   how far the land falls across the scar
 * @param radius the scar's radius
 * @param wall   how high its headwall stands
 * @param lobe   how far the mud runs out below it
 */
public record ThermokarstSlump(int cx, int cz, int y, int sea, float angle, int drop, int radius, int wall, int lobe, long seed) implements Landform {
    private static final BlockState LOG = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 4 || !c.fits(ground, 40, 50)) {
            return null;
        }
        int radius = 15 + c.random().nextInt(16);
        // a hillside or the edge of a terrace: falling away one way, and level or still rising behind
        float[] down = c.downhill(radius);
        if (down[1] < 3) {
            return null;
        }
        float a = down[0];
        int behind = c.groundAt(c.x() - Math.round(Mth.cos(a) * radius), c.z() - Math.round(Mth.sin(a) * radius));
        if (behind < ground - 2) {
            return null;
        }
        return new ThermokarstSlump(c.x(), c.z(), ground, c.seaLevel(), a, Math.round(down[1]), radius, 3 + c.random().nextInt(6), 10 + c.random().nextInt(13),
                c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new ThermokarstSlump(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("sea"), tag.getFloat("angle"), tag.getInt("drop"),
                tag.getInt("r"), tag.getInt("wall"), tag.getInt("lobe"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("sea", sea);
        tag.putFloat("angle", angle);
        tag.putInt("drop", drop);
        tag.putInt("r", radius);
        tag.putInt("wall", wall);
        tag.putInt("lobe", lobe);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.THERMOKARST_SLUMP;
    }

    private float mouth() {
        return radius * 0.9F;
    }

    @Override
    public BoundingBox bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        float side = radius * 1.4F + 3.0F, front = mouth() + lobe + 3.0F, back = radius * 1.15F + 4.0F;
        for (float u : new float[]{-back, front}) {
            for (float s : new float[]{-side, side}) {
                double[] p = Terrain.xz(u, s, cx, cz, angle);
                minX = Math.min(minX, Mth.floor(p[0]));
                maxX = Math.max(maxX, Mth.ceil(p[0]));
                minZ = Math.min(minZ, Mth.floor(p[1]));
                maxZ = Math.max(maxZ, Mth.ceil(p[1]));
            }
        }
        float slope = Math.max(drop, 3) / (float) radius;
        return new BoundingBox(minX, Mth.floor(y - slope * front * 1.5F) - wall - 14, minZ, maxX, Mth.ceil(y + slope * back * 1.5F) + 36, maxZ);
    }

    /** How far into the scar a column is (under 1 is inside): round at the back, open downhill to its mouth. */
    private float scar(float u, float v, int x, int z) {
        float r = radius * (0.9F + 0.2F * Terrain.field(seed, x, z, 8.0));
        // the headwall is scalloped, where it has slumped back a bite at a time
        r *= 1.0F + 0.06F * Mth.sin((float) Mth.atan2(v, -u) * 7.0F + Terrain.hash(seed, 1) * 6.0F);
        if (u < 0.0F) return Mth.sqrt(Mth.square(u / (r * 0.8F)) + Mth.square(v / r));
        if (u > mouth()) return 9.0F;
        return Math.abs(v) / (r * (1.0F - 0.2F * u / radius));
    }

    /** How deep the scar is cut, {@code u} downhill: the full height of the headwall in its back half, nothing at its mouth. */
    private float cut(float u) {
        return wall * Terrain.smooth((mouth() - u) / (mouth() * 1.2F));
    }

    /** How thick the mud lies over a column (0 off the lobe). */
    private float mud(float u, float v, int x, int z) {
        float s = (u - mouth() * 0.4F) / (lobe + mouth() * 0.6F);
        if (s <= 0.0F || s >= 1.0F) return 0.0F;
        float spread = radius * 0.7F * (1.0F + 0.35F * s) * (0.9F + 0.2F * Terrain.field(seed + 2, x, z, 6.0));
        float q = Math.abs(v) / spread;
        if (q >= 1.0F) return 0.0F;
        // thickest well down the lobe, ending in a steep, rounded toe
        float t = (1.5F + 1.5F * Terrain.hash(seed, 3)) * Terrain.smooth(s / 0.35F) * Terrain.smooth((1.0F - s) / 0.12F) * Mth.sqrt(1.0F - q * q);
        return t + 1.6F * (Terrain.field(seed + 3, x, z, 3.0) - 0.5F) * Terrain.smooth(t);
    }

    /** The trees carried down on the mud: {u, v, direction (0-3), length, leaning (1) or lying flat (0)}. */
    private float[][] trees() {
        int count = 6 + Math.round(Terrain.hash(seed, 4) * 7.0F);
        float[][] trees = new float[count][];
        for (int i = 0; i < count; i++) {
            float u = mouth() * 0.3F + Terrain.hash(seed, 10 + i) * (mouth() * 0.6F + lobe * 0.8F);
            float v = (Terrain.hash(seed, 20 + i) - 0.5F) * radius * 1.1F;
            trees[i] = new float[]{u, v, Math.floorMod((int) (Terrain.hash(seed, 30 + i) * 4.0F), 4), 2 + Math.round(Terrain.hash(seed, 40 + i) * 2.0F),
                    Terrain.hash(seed, 50 + i) < 0.6F ? 1 : 0};
        }
        return trees;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[][] planned = trees();
        int[][] trees = new int[planned.length][];
        for (int i = 0; i < planned.length; i++) {
            double[] p = Terrain.xz(planned[i][0], planned[i][1], cx, cz, angle);
            trees[i] = new int[]{Mth.floor(p[0]), Mth.floor(p[1]), (int) planned[i][2], (int) planned[i][3], (int) planned[i][4]};
        }
        float rim = 2.5F / radius;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                float e = scar(u, v, x, z), mud = mud(u, v, x, z);
                boolean inScar = e < 1.0F, onLobe = mud >= 0.5F, face = !inScar && !onLobe && e < 1.0F + rim;
                if (!inScar && !onLobe && !face) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + wall + 6 || ground > b.maxY() - 34) continue;
                int surface = Terrain.surface(level, pos, x, ground, z);
                float n = Terrain.noise(seed, x, ground, z);
                float depth = cut(u);
                if (face) {
                    // the headwall: frozen ground bared in a sheer face, banded with lenses of ice and split by ice wedges
                    int bottom = ground - Math.round(depth) - 2;
                    boolean wedge = Terrain.noise(seed + 4, x, 0, z) > 0.8F;
                    int wedgeDepth = 2 + Math.round(Terrain.noise(seed + 5, x, 0, z) * Math.max(1, depth - 1));
                    for (int h = bottom; h < ground; h++) {
                        BlockState s;
                        float m = Terrain.noise(seed + 6, x, h, z);
                        if (h == ground - 1) {
                            s = Blocks.DIRT.defaultBlockState(); // the thin layer that thaws every summer
                        } else if (wedge && h >= ground - 1 - wedgeDepth) {
                            s = Blocks.ICE.defaultBlockState();
                        } else {
                            int band = Math.floorMod(h + Math.round(Terrain.hash(seed, 5) * 4.0F), 4);
                            s = band == 0 ? (m < 0.6F ? Blocks.ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState())
                                    : band == 2 ? (m < 0.5F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MUD.defaultBlockState())
                                    : m < 0.2F ? Blocks.PACKED_ICE.defaultBlockState() : m < 0.7F ? Blocks.DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                        }
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    continue;
                }
                if (!inScar && surface > ground) continue; // mud never runs into a pond: it just stops at the bank
                int top = ground;
                if (inScar) {
                    // the floor of the scar, cut down below the hillside, hummocky with slumped mud
                    int hummock = Math.round(1.6F * (Terrain.field(seed + 1, x, z, 3.0) - 0.5F));
                    top = Math.min(ground, ground - Math.round(depth) + hummock);
                    top = Math.max(top, Math.min(ground, sea + 1));
                }
                int fill = onLobe ? Math.round(mud) : 0;
                int finalTop = top + fill;
                // the slump took the trees with it
                Terrain.clearAbove(level, pos, x, Math.max(ground, finalTop), z, Math.min(28, b.maxY() - Math.max(ground, finalTop)));
                if (inScar) {
                    for (int h = finalTop + 1; h <= Math.max(ground, surface); h++) {
                        Terrain.set(level, pos, x, h, z, Terrain.pondBeside(level, pos, x, h, z, (nx, nz) -> {
                            float[] o = Terrain.uv(nx + 0.5, nz + 0.5, cx, cz, angle);
                            return scar(o[0], o[1], nx, nz) >= 1.0F;
                        }) ? Blocks.MUD.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
                for (int h = Math.min(ground, top) - 1; h <= finalTop; h++) {
                    float m = Terrain.noise(seed + 7, x, h, z);
                    BlockState s;
                    if (h < finalTop) {
                        s = m < 0.45F ? Blocks.MUD.defaultBlockState() : m < 0.7F ? Blocks.DIRT.defaultBlockState()
                                : m < 0.88F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
                    } else if (inScar && fill == 0 && e > 0.8F && n < 0.3F) {
                        s = n < 0.12F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState(); // ground ice still melting out at the foot of the headwall
                    } else {
                        // churned, wet mud, with rafts of torn-up moss and turf ridden down on the lobe
                        s = n < 0.5F ? Blocks.MUD.defaultBlockState() : n < 0.68F ? Blocks.COARSE_DIRT.defaultBlockState()
                                : n < 0.8F ? Blocks.PACKED_MUD.defaultBlockState() : n < 0.9F ? Blocks.DIRT.defaultBlockState()
                                : onLobe && n < 0.96F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
                // the drunken forest: spruce trunks, stripped bare, leaning every which way or lying in the mud
                for (int[] t : trees) {
                    Direction dir = Direction.from2DDataValue(t[2]);
                    int k = dir.getAxis() == Direction.Axis.X ? (x - t[0]) * dir.getStepX() : (z - t[1]) * dir.getStepZ();
                    boolean on = dir.getAxis() == Direction.Axis.X ? z == t[1] : x == t[0];
                    if (!on || k < 0 || k >= t[3]) continue;
                    boolean leaning = t[4] == 1;
                    BlockState log = LOG.setValue(RotatedPillarBlock.AXIS, leaning && k == 0 ? Direction.Axis.Y : dir.getAxis());
                    Terrain.set(level, pos, x, finalTop + 1 + (leaning ? k : 0), z, log);
                }
            }
        }
    }
}
