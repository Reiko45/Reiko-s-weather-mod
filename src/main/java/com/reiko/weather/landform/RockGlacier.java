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
 * A rock glacier: a tongue of frost-shattered rubble creeping down a mountain valley like a glacier, which in a way it
 * is. Scree falling from the cliffs above buried the ice there (or ice formed in the gaps between the stones), and the
 * frozen mass slowly flows, its surface wrinkled into arcs of ridges and furrows bowed downhill, until it ends in a
 * steep, loose front where boulders keep toppling off. Dig in a few blocks and the core is solid ice.
 *
 * @param base the ground under its middle line, at even steps down it (never rising)
 */
public record RockGlacier(int cx, int cz, float angle, int length, int width, int thick, int[] base, long seed) implements Landform {
    private static final int CONTROL = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 16 || !c.fits(ground, 10, 30)) {
            return null;
        }
        int length = 44 + c.random().nextInt(30);
        // it needs a mountainside to creep down
        float[] down = c.downhill(length / 2);
        if (down[1] < 6) {
            return null;
        }
        float angle = down[0];
        int[] base = new int[CONTROL + 1];
        for (int i = 0; i <= CONTROL; i++) {
            float u = (i / (float) CONTROL - 0.5F) * length;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(angle) * u), c.z() + Math.round(Mth.sin(angle) * u));
            if (h < c.seaLevel() + 2) return null;
            base[i] = i == 0 ? h : Math.min(h, base[i - 1]);
        }
        return new RockGlacier(c.x(), c.z(), angle, length, 14 + c.random().nextInt(9), 5 + c.random().nextInt(5), base, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new RockGlacier(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getInt("thick"),
                tag.getIntArray("base"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("thick", thick);
        tag.put("base", new IntArrayTag(base));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ROCK_GLACIER;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + width / 2 + 6;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int h : base) {
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return new BoundingBox(cx - r, min - thick - 8, cz - r, cx + r, max + thick + 16, cz + r);
    }

    private float baseAt(float u) {
        float t = Mth.clamp((u / length + 0.5F) * CONTROL, 0.0F, CONTROL);
        int i = Math.min(CONTROL - 1, (int) t);
        return Mth.lerp(t - i, base[i], base[i + 1]);
    }

    private float halfWidth(float u) {
        float t = Terrain.smooth((u + length / 2.0F) / (length * 0.3F));
        return width / 2.0F * (0.75F + 0.25F * t) * (0.9F + 0.2F * Terrain.field(seed, u, 0.0, 9.0));
    }

    /** How far inside the tongue's outline a point is, in blocks (negative outside): rounded at its front. */
    private float inside(float u, float v, int x, int z) {
        float half = length / 2.0F, hw = halfWidth(u);
        float front = Math.max(0.0F, u - (half - hw)) / hw;
        float e = Mth.sqrt(Mth.square(v / hw) + front * front);
        return (1.0F - e + (Terrain.field(seed + 1, x, z, 5.0) - 0.5F) * 0.2F) * hw;
    }

    private BlockState rubble(float n) {
        return n < 0.3F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.52F ? Blocks.ANDESITE.defaultBlockState() : n < 0.7F ? Blocks.GRAVEL.defaultBlockState()
                : n < 0.86F ? Blocks.STONE.defaultBlockState() : n < 0.93F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F, margin = 2.5F;
        float wave = 5.5F + 2.5F * Terrain.hash(seed, 2);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (Math.abs(u) > half + 1.0F) continue;
                float in = inside(u, v, x, z);
                if (in <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 1 || ground > b.maxY() - 14) continue;
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isEmpty()) continue;
                // its root under the cliffs is just a thickening of the scree; it's thickest toward its front
                float root = Terrain.smooth((u + half) / 12.0F);
                float th = thick * Math.min(1.0F, in / margin) * root;
                // concentric ridges and furrows, bowed downhill
                float ridge = in < margin ? 0.0F : 1.3F * Mth.sin(Mth.TWO_PI * (u - 0.8F * v * v / halfWidth(u)) / wave);
                float n0 = Terrain.noise(seed, x, 0, z);
                int top = Math.round(baseAt(u) + th + ridge + (n0 - 0.5F) * (in < margin ? 1.6F : 0.8F));
                top = Math.min(top, ground + thick + 4);
                if (top <= ground) {
                    if (in < 2.0F || root < 0.5F) continue;
                    top = ground + 1;
                }
                Terrain.clearAbove(level, pos, x, top, z, Math.min(16, b.maxY() - top));
                for (int h = ground + 1; h <= top; h++) {
                    float n = Terrain.noise(seed, x, h, z);
                    // ice hidden in the heart of it, under a few blocks of loose stone
                    boolean core = in > margin + 1.0F && h > ground + 1 && h < top - 2;
                    Terrain.set(level, pos, x, h, z, core ? Blocks.PACKED_ICE.defaultBlockState() : rubble(n));
                }
                // boulders perched on top
                if (n0 > 0.93F && top + 1 <= b.maxY()) {
                    Terrain.set(level, pos, x, top + 1, z, n0 > 0.97F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
    }
}
