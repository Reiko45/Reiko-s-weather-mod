package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A horn (like the Matterhorn): a pyramid-shaped peak with three or four steep, hollowed faces meeting in sharp
 * ridges at a pointed summit. Each face is the back wall of a cirque: glaciers on every side of the mountain
 * plucked and froze their way back into it, and where two of them cut back towards each other they left only a
 * knife-edged ridge (an arete) between them. When all of them met at the top, a horn was what was left. Snow
 * clings to its ledges all year round.
 *
 * @param y      the summit
 * @param depth  how far the faces fall below the summit at their foot
 * @param twist  the direction of the first face
 */
public record Horn(int cx, int cz, int y, int depth, int radius, int faces, float twist, long seed) implements Landform {
    /** The most a face is ever cut down into the mountain below its natural slope. */
    private static final float MAX_CUT = 16.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int x = c.x(), z = c.z(), h = c.groundAt(x, z);
        if (h < c.seaLevel() + 40) {
            return null;
        }
        // climb to the summit: big steps, then a small one to settle on the top
        for (int step = 0; step < 4; step++) {
            int dist = step < 3 ? 14 : 6, dirs = step < 3 ? 6 : 4;
            int bx = x, bz = z, bh = h;
            for (int i = 0; i < dirs; i++) {
                float a = i * Mth.TWO_PI / dirs + step * 0.4F;
                int px = x + Math.round(Mth.cos(a) * dist), pz = z + Math.round(Mth.sin(a) * dist);
                int ph = c.groundAt(px, pz);
                if (ph > bh) {
                    bx = px;
                    bz = pz;
                    bh = ph;
                }
            }
            if (bh == h && step < 3) step = 2; // already at the top: just settle
            x = bx;
            z = bz;
            h = bh;
        }
        // a real peak, standing well up above everything round it
        int low = h, high = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int g = c.groundAt(x + Math.round(Mth.cos(a) * 30), z + Math.round(Mth.sin(a) * 30));
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high > h || h - low < 18 || !c.fits(h, 60, 4)) {
            return null;
        }
        int depth = Math.min(48, Math.round((h - low) * 1.2F) + 8);
        return new Horn(x, z, h, depth, 26 + c.random().nextInt(9), 3 + c.random().nextInt(2), c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Horn(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("depth"), tag.getInt("r"), tag.getInt("faces"), tag.getFloat("twist"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("depth", depth);
        tag.putInt("r", radius);
        tag.putInt("faces", faces);
        tag.putFloat("twist", twist);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.HORN;
    }

    @Override
    public BoundingBox bounds() {
        int r = radius + 4;
        return new BoundingBox(cx - r, y - depth - 6, cz - r, cx + r, y + 8, cz + r);
    }

    private float faceAngle(int k) {
        return twist + (k + (Terrain.hash(seed, 10 + k) - 0.5F) * 0.3F) * Mth.TWO_PI / faces;
    }

    /**
     * The carved surface at a point: {height, how far from the middle of its face towards an arete (0-1)}. The faces
     * are hollowed (steep near the top, easing off at the foot) and the aretes between them fall away more gently.
     */
    private float[] surface(double x, double z) {
        float dx = (float) (x - cx - 0.5), dz = (float) (z - cz - 0.5);
        float r = Mth.sqrt(dx * dx + dz * dz);
        float theta = (float) Mth.atan2(dz, dx);
        // which face this is on, and where between its aretes
        float w = 1.0F;
        for (int k = 0; k < faces; k++) {
            float at = faceAngle(k), prev = faceAngle((k + faces - 1) % faces), next = faceAngle((k + 1) % faces);
            float off = Mth.wrapDegrees((theta - at) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            float toNext = Math.abs(Mth.wrapDegrees((next - at) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD / 2.0F;
            float toPrev = Math.abs(Mth.wrapDegrees((at - prev) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD / 2.0F;
            float half = off >= 0.0F ? toNext : toPrev;
            if (Math.abs(off) <= half) w = Math.min(w, Math.abs(off) / half);
        }
        float t = Math.min(1.0F, r / radius);
        float face = y - depth * (1.0F - (1.0F - t) * (1.0F - t));
        float ridge = y - r * 0.95F;
        float h = Mth.lerp(w * w * w, face, ridge);
        // rough, shattered rock on the faces; the ridges are cleaner
        h += (1.0F - w) * 2.2F * (Terrain.field(seed, x, z, 5.0) - 0.5F);
        if (r > radius) h += (r - radius) * 2.5F; // rising back to the untouched mountainside beyond
        if (r < 1.5F) h = y;
        return new float[]{h, w};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int offset = Math.round(Terrain.hash(seed, 1) * 7.0F);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float r = Mth.sqrt(Mth.square(x - cx) + Mth.square(z - cz));
                if (r > radius + 3) continue;
                float[] s = surface(x + 0.5, z + 0.5);
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 4 || ground > b.maxY() - 2) continue;
                // carve only: never cut deeper than the faces would go, nor much below the mountain's own slope
                float limit = MAX_CUT * Terrain.smooth(1.0F - r / radius) * (1.0F - 0.6F * s[1] * s[1]);
                int top = Math.round(Math.max(s[0], ground - limit));
                if (top >= ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(4, b.maxY() - ground));
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // fresh rock on everything the cut has laid open, down to the lowest of the neighbouring faces
                int open = top;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    open = Math.min(open, Math.round(surface(x + 0.5 + d[0], z + 0.5 + d[1])[0]));
                }
                open = Math.max(Math.max(open, top - 10), b.minY() + 2);
                for (int h = open - 1; h <= top; h++) {
                    // banded with darker andesite along the bedding
                    boolean band = Math.floorMod(h + offset + Math.round(Terrain.field(seed + 3, x, z, 9.0) * 2.0F), 7) < 2;
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState rock = n < 0.06F ? Blocks.COBBLESTONE.defaultBlockState() : band ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, rock);
                }
                // snow lying on the ledges: wherever the cut surface is gentle enough to hold it
                float slope = Math.abs(s[0] - surface(x + 1.5, z + 0.5)[0]) + Math.abs(s[0] - surface(x + 0.5, z + 1.5)[0]);
                float n = Terrain.noise(seed + 1, x, top, z);
                boolean ledge = slope < 2.6F || Math.floorMod(top + offset, 5) == 0 && n < 0.6F;
                if (ledge && top + 1 <= ground && n < 0.85F) {
                    pos.set(x, top, z);
                    boolean cold = level.getBiome(pos).value().coldEnoughToSnow(pos);
                    if (cold) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(n * 9.0F) % 3));
                    } else if (n < 0.3F) {
                        Terrain.set(level, pos, x, top, z, Blocks.GRAVEL.defaultBlockState());
                    }
                }
            }
        }
    }
}
