package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Pillow lava: what lava does when it erupts under the sea. The cold water chills each blob's skin to glass at once,
 * so the lava inside swells it out into a rounded "pillow" until it splits and buds off the next, and so on, until
 * the pillows lie piled on one another in mounds and long ridges along the cracks they came out of. The glassy rinds
 * shatter into grit (hyaloclastite) that settles in the gaps.
 *
 * @param ridges {across, from, to, height, width} for each ridge, along {@code angle} from the middle
 */
public record PillowLava(int cx, int cz, int sea, int floor, float angle, int radius, int[] ridges, long seed) implements Landform {
    private static final float CW = 3.0F, CH = 2.4F; // the lattice the pillows are piled in

    @Nullable
    static Landform plan(PlanContext c) {
        int floor = c.groundAt(c.x(), c.z());
        if (floor > c.seaLevel() - 14 || !c.fits(floor, 6, 16)) {
            return null;
        }
        int radius = 16 + c.random().nextInt(7);
        // the open sea floor, with no land or shallows near
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            if (c.groundAt(c.x() + Math.round(Mth.cos(a) * (radius + 4)), c.z() + Math.round(Mth.sin(a) * (radius + 4))) > c.seaLevel() - 10) {
                return null;
            }
        }
        int n = 3 + c.random().nextInt(3);
        int[] ridges = new int[n * 5];
        for (int i = 0; i < n; i++) {
            int across = Math.round((c.random().nextFloat() - 0.5F) * radius * 1.2F);
            int len = 14 + c.random().nextInt(17);
            int from = Math.round((c.random().nextFloat() - 0.5F) * radius * 0.8F) - len / 2;
            ridges[i * 5] = across;
            ridges[i * 5 + 1] = from;
            ridges[i * 5 + 2] = from + len;
            ridges[i * 5 + 3] = 4 + c.random().nextInt(6);
            ridges[i * 5 + 4] = 4 + c.random().nextInt(4);
        }
        return new PillowLava(c.x(), c.z(), c.seaLevel(), floor, c.random().nextFloat() * Mth.PI, radius, ridges, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new PillowLava(tag.getInt("x"), tag.getInt("z"), tag.getInt("sea"), tag.getInt("floor"), tag.getFloat("angle"), tag.getInt("r"),
                tag.getIntArray("ridges"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("sea", sea);
        tag.putInt("floor", floor);
        tag.putFloat("angle", angle);
        tag.putInt("r", radius);
        tag.put("ridges", new IntArrayTag(ridges));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.PILLOW_LAVA;
    }

    @Override
    public BoundingBox bounds() {
        int r = radius + 12;
        return new BoundingBox(cx - r, floor - 24, cz - r, cx + r, sea, cz + r);
    }

    /** How high the pillows are piled at a point: ridges along the old cracks, fading out at the field's edge. */
    private float mound(float px, float pz) {
        float[] l = Terrain.uv(px, pz, cx, cz, angle);
        float best = 0.0F;
        for (int i = 0; i < ridges.length / 5; i++) {
            float du = Math.max(0.0F, Math.max(ridges[i * 5 + 1] - l[0], l[0] - ridges[i * 5 + 2]));
            float dv = l[1] - ridges[i * 5] - 2.0F * Mth.sin(l[0] * 0.15F + i);
            float d = Mth.sqrt(du * du + dv * dv) / ridges[i * 5 + 4];
            if (d < 1.0F) best = Math.max(best, ridges[i * 5 + 3] * (1.0F - d * d));
        }
        float e = Mth.sqrt(Mth.square(px - cx) + Mth.square(pz - cz)) / (radius * (0.85F + 0.3F * Terrain.field(seed + 1, px, pz, 9.0)));
        return best * Terrain.smooth((1.0F - e) / 0.3F) * (0.75F + 0.5F * Terrain.field(seed + 2, px, pz, 5.0));
    }

    /**
     * The pillow (if any) a block is part of, among those piled on a column whose sea bed is at {@code ground}:
     * how far into it the block is (under 1 inside, 0 at its heart), and which pillow, as {q, i, j, k}.
     */
    @Nullable
    private float[] pillow(int x, int h, int z, int ground, int roof) {
        float px = x + 0.5F, ph = h + 0.5F, pz = z + 0.5F;
        int gi = Mth.floor(px / CW), gj = Mth.floor(ph / CH), gk = Mth.floor(pz / CW);
        float[] best = null;
        for (int i = gi - 1; i <= gi + 1; i++) {
            for (int j = gj - 1; j <= gj + 1; j++) {
                for (int k = gk - 1; k <= gk + 1; k++) {
                    float ox = (i + 0.2F + 0.6F * Terrain.noise(seed + 1, i, j, k)) * CW;
                    float oy = (j + 0.2F + 0.6F * Terrain.noise(seed + 2, i, j, k)) * CH;
                    float oz = (k + 0.2F + 0.6F * Terrain.noise(seed + 3, i, j, k)) * CW;
                    float rx = 1.2F + 0.7F * Terrain.noise(seed + 4, i, j, k), ry = 0.85F + 0.45F * Terrain.noise(seed + 5, i, j, k);
                    float rz = 1.2F + 0.7F * Terrain.noise(seed + 6, i, j, k);
                    float q = Mth.square((px - ox) / rx) + Mth.square((ph - oy) / ry) + Mth.square((pz - oz) / rz);
                    if (q >= 1.0F || best != null && q >= best[0]) continue;
                    // only the pillows that sit within the pile, and on (not under) the sea bed
                    if (oy < ground - 1.2F || oy + ry > roof + 0.5F || oy > ground + mound(ox, oz) - 0.3F) continue;
                    best = new float[]{q, i, j, k};
                }
            }
        }
        return best;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int roof = sea - 4; // keep it all well under the water
        int tallest = 0;
        for (int i = 0; i < ridges.length / 5; i++) tallest = Math.max(tallest, ridges[i * 5 + 3]);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float px = x + 0.5F, pz = z + 0.5F;
                if (Mth.square(px - cx) + Mth.square(pz - cz) > Mth.square(radius * 1.15F + 3.0F)) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 2 || ground > roof - 2) continue;
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isSource()) continue;
                float m = mound(px, pz);
                int high = Math.min(roof, ground + Mth.ceil(tallest * 1.25F) + 2);
                BlockState[] column = new BlockState[high - ground + 2];
                boolean any = false;
                for (int h = ground - 1; h <= high; h++) {
                    float[] p = pillow(x, h, z, ground, roof);
                    if (p == null) continue;
                    // each pillow has a glassy black rind round a basalt heart; a few have crumbled to tuff
                    float kind = Terrain.noise(seed + 7, (int) p[1], (int) p[2], (int) p[3]);
                    BlockState s;
                    if (kind < 0.08F) s = Blocks.TUFF.defaultBlockState();
                    else if (p[0] > 0.45F) s = kind < 0.6F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
                    else s = kind < 0.5F ? Blocks.BASALT.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
                    column[h - ground + 1] = s;
                    any = any || h > ground;
                }
                if (!any) {
                    // grit from the shattered rinds settles on the bed between them
                    if (m > 0.5F && Terrain.noise(seed, x, 8, z) < 0.4F) {
                        Terrain.set(level, pos, x, ground, z, Terrain.noise(seed, x, 9, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                    }
                    continue;
                }
                // weed growing up from the old sea bed would be left hanging over the gaps, so it goes
                for (int h = ground + 1; h <= sea - 1; h++) {
                    pos.set(x, h, z);
                    BlockState was = level.getBlockState(pos);
                    if (was.is(Blocks.KELP) || was.is(Blocks.KELP_PLANT) || was.is(Blocks.SEAGRASS) || was.is(Blocks.TALL_SEAGRASS)) {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Terrain.FLAGS);
                    }
                }
                for (int h = ground - 1; h <= high; h++) {
                    if (column[h - ground + 1] != null) Terrain.set(level, pos, x, h, z, column[h - ground + 1]);
                }
            }
        }
    }
}
