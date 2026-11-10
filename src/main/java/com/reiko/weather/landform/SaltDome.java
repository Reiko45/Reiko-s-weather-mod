package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A salt dome broken through to the surface (like those of the Zagros mountains in Iran). A thick bed of salt left
 * by an ancient sea lies buried deep under younger rock, and salt is lighter than rock and creeps like putty under
 * the weight, so it rises in a great plug, shouldering the beds above it up and aside into a ring of tilted ridges.
 * Where it reaches the open air it swells into a rounded hill of rock salt, banded grey, pink and red with clay and
 * iron, and in the rare rains it flows downhill in a slow glacier of salt (a namakier) that spreads out at its foot.
 * Rain dissolves the salt into sinkholes on top, and leaves the flats round about crusted white.
 *
 * @param base   the level of the land round about
 * @param angle  the way the salt glacier flows (downhill)
 * @param length how far beyond the dome's edge the glacier reaches
 */
public record SaltDome(int cx, int cz, int base, int radius, int height, float angle, int length, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 25 + c.random().nextInt(16), height = 10 + c.random().nextInt(16);
        if (ground < c.seaLevel() + 2 || !c.fits(ground, 12, 44)) {
            return null;
        }
        int low = ground, high = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * radius), c.z() + Math.round(Mth.sin(a) * radius));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        if (high - low > 14 || low < c.seaLevel() + 1) {
            return null;
        }
        float[] down = c.downhill(radius);
        float angle = down[1] > 0 ? down[0] : c.random().nextFloat() * Mth.TWO_PI;
        int length = Math.round(radius * (0.5F + 0.3F * c.random().nextFloat()));
        return new SaltDome(c.x(), c.z(), low, radius, height, angle, length, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SaltDome(tag.getInt("x"), tag.getInt("z"), tag.getInt("base"), tag.getInt("r"), tag.getInt("h"), tag.getFloat("angle"),
                tag.getInt("length"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("base", base);
        tag.putInt("r", radius);
        tag.putInt("h", height);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SALT_DOME;
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(radius * 1.6F * 1.1F) + 2;
        int minX = cx - r, maxX = cx + r, minZ = cz - r, maxZ = cz + r;
        // and the glacier's foot (and the salt crust round it), out past the ring
        float foot = radius * 0.4F + 6.0F;
        for (float v : new float[]{-1.0F, 1.0F}) {
            double[] p = Terrain.xz(radius + length * 0.75F + foot + 2.0F, v * (foot * 1.3F + 2.0F), cx, cz, angle);
            minX = Math.min(minX, Mth.floor(p[0]));
            maxX = Math.max(maxX, Mth.floor(p[0]) + 1);
            minZ = Math.min(minZ, Mth.floor(p[1]));
            maxZ = Math.max(maxZ, Mth.floor(p[1]) + 1);
        }
        return new BoundingBox(minX, base - 20, minZ, maxX, base + height + 24, maxZ);
    }

    /** How far out from the middle of the dome a point is: under 1 on the salt itself. */
    private float dome(double x, double z) {
        return (float) Mth.length(x - cx, z - cz) / (radius * (0.9F + 0.2F * Terrain.field(seed, x, z, 12.0)));
    }

    /** The top of the dome at a point. */
    private float domeTop(double x, double z, float d) {
        return base + height * (float) Math.pow(Math.max(0.0F, 1.0F - d * d), 0.8) + 1.5F * (Terrain.field(seed + 1, x, z, 6.0) - 0.5F);
    }

    /** How deep the sinkholes the rain has dissolved into the top of the salt are at a point. */
    private float sinkholes(double x, double z) {
        float pit = 0.0F;
        for (int i = 0; i < 3; i++) {
            float a = Terrain.hash(seed, 30 + i) * Mth.TWO_PI, r = Terrain.hash(seed, 33 + i) * radius * 0.45F;
            float s = (float) Mth.length(x - (cx + Mth.cos(a) * r), z - (cz + Mth.sin(a) * r)) / (2.0F + 2.0F * Terrain.hash(seed, 36 + i));
            if (s < 1.0F) pit = Math.max(pit, (2.0F + Terrain.hash(seed, 39 + i) * 2.0F) * (1.0F - s * s));
        }
        return pit;
    }

    /** Where salt washed off the glacier's foot has dried into a crust: under 1 there. */
    private float foot(float u, float v) {
        float r = radius * 0.4F + 6.0F;
        return (float) Mth.length(u - radius - length * 0.75F, v / 1.3F) / r;
    }

    /**
     * How thick the salt glacier lies over the ground at a point, flowing out from the dome and spreading into a broad,
     * round-fronted lobe at its foot (0 off it).
     */
    private float glacier(float u, float v) {
        float end = radius + length;
        if (u < radius * 0.5F || u > end) return 0.0F;
        float t = (u - radius * 0.5F) / (end - radius * 0.5F);
        float half = radius * (0.28F + 0.3F * t * t) + 1.5F * Mth.sin(u * 0.2F + Terrain.hash(seed, 4) * 6.0F);
        float across = 1.0F - Mth.square(v / half);
        if (across <= 0.0F) return 0.0F;
        // thick where it pours off the dome, thinning down the slope, with a steep, rounded snout
        float thick = Mth.lerp(t, 6.0F, 3.0F) * Mth.sqrt(Math.min(1.0F, (end - u) / 4.0F));
        return thick * Mth.sqrt(across);
    }

    /** The upturned beds round the dome: how high the ridge stands over the ground (a steep face toward the salt). */
    private float ring(float d) {
        float crest = 3.0F + 3.0F * Terrain.hash(seed, 5);
        if (d < 1.0F || d > 1.4F) return 0.0F;
        return d < 1.08F ? crest * Terrain.smooth((d - 1.0F) / 0.08F) : crest * (1.0F - Terrain.smooth((d - 1.08F) / 0.32F));
    }

    /** Rock salt in contorted bands of grey, pink and red, squeezed and folded as it rose. */
    private BlockState salt(int x, int y, int z, float flow) {
        float s = y * 0.6F + 5.0F * Terrain.field(seed + 2, x, z, 7.0) + 2.5F * Terrain.field(seed + 3, x * 1.7, z * 1.7, 3.0) + flow;
        return switch (Math.floorMod(Mth.floor(s), 11)) {
            case 1 -> Blocks.CALCITE.defaultBlockState();
            case 3 -> Blocks.PINK_TERRACOTTA.defaultBlockState();
            case 5 -> Blocks.WHITE_TERRACOTTA.defaultBlockState();
            case 7 -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case 8 -> Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
            default -> ModBlocks.ROCK_SALT.get().defaultBlockState();
        };
    }

    /** The beds the salt pushed up, tilted to dip away from it all round. */
    private static BlockState stratum(int y, float d, int radius) {
        return switch (Math.floorMod(Mth.floor((y + (d - 1.0F) * radius * 0.8F) / 2.0F), 6)) {
            case 0 -> Blocks.RED_SANDSTONE.defaultBlockState();
            case 1 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case 2 -> Blocks.TERRACOTTA.defaultBlockState();
            case 3 -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case 4 -> Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
            default -> Blocks.BROWN_TERRACOTTA.defaultBlockState();
        };
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState crust = ModBlocks.SALT_CRUST.get().defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = dome(x + 0.5, z + 0.5);
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float g = glacier(l[0], l[1]);
                // salt crust on the flats round about, and spreading round the glacier's foot
                float foot = foot(l[0], l[1]);
                if (d > 1.6F && g <= 0.0F && foot > 1.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 8 || ground > b.maxY() - 16) continue;
                if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) continue;
                float n = Terrain.noise(seed, x, 0, z);
                int top = ground;
                int kind = 0; // 0 the plain, 1 the salt, 2 the ring of upturned beds
                if (d < 1.0F) {
                    // (where the land stands higher than the dome, the salt just breaks out through it; the sinkholes only
                    // eat into the salt that's risen above the ground, so nothing round about is left draining into them)
                    top = Math.max(ground, Math.round(domeTop(x + 0.5, z + 0.5, d) - sinkholes(x + 0.5, z + 0.5)));
                    kind = 1;
                }
                if (g > 0.5F) {
                    // the glacier pours off the dome and drapes itself over the slope below
                    int over = ground + Math.round(g);
                    if (d >= 1.0F || over > top) {
                        top = Math.max(top, over);
                        kind = 1;
                    }
                } else if (d >= 1.0F) {
                    float rise = ring(d);
                    if (rise > 0.5F) {
                        top = ground + Math.round(rise * (0.8F + 0.4F * Terrain.field(seed + 6, x, z, 5.0)));
                        kind = 2;
                    }
                }
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(12 + Math.max(0, top - ground), b.maxY() - ground));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                if (kind == 1) {
                    // the salt, right down through the ground it broke out of
                    float flow = g > 0.0F ? l[1] * 0.7F + 2.0F * Mth.sin(l[0] * 0.15F) : 0.0F;
                    for (int h = Math.min(ground, top) - 3; h <= top; h++) Terrain.set(level, pos, x, h, z, salt(x, h, z, flow));
                    if (d >= 0.95F && n < 0.3F) Terrain.set(level, pos, x, top, z, crust); // crust where it spreads thin
                    continue;
                }
                if (kind == 2) {
                    for (int h = Math.min(ground, top) - 3; h <= top; h++) Terrain.set(level, pos, x, h, z, stratum(h, d, radius));
                    continue;
                }
                // the flats: salt washed off the dome dries into a white crust, thinning out further away
                float reach = Math.max(1.0F - Terrain.smooth((d - 1.3F) / 0.3F), 1.0F - foot);
                if (Terrain.field(seed + 7, x, z, 4.0) < reach * 0.9F) Terrain.set(level, pos, x, ground, z, crust);
            }
        }
    }
}
