package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
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
 * An obsidian flow: a thick tongue of volcanic glass. Lava rich in silica is so stiff that it barely flows at all; it
 * oozes out of its vent and creeps downhill a few yards a day, chilling to obsidian, its skin wrinkling into ridges
 * that arch across it (ogives) as the middle runs on faster than the edges. It stops with a high, steep front of
 * broken glass, and its surface is a wilderness of jagged blocks, patched with pale, frothy pumice where gas bubbled
 * out of it, and here and there a vein of glass that seems to weep.
 *
 * @param base the ground under the flow's middle line, at even steps down it (never rising)
 */
public record ObsidianFlow(int cx, int cz, float angle, int length, int width, int thick, int[] base, long seed) implements Landform {
    private static final int CONTROL = 6;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 8, 34)) {
            return null;
        }
        int length = 50 + c.random().nextInt(31);
        // it crept down a gentle slope
        float[] down = c.downhill(length / 2);
        if (down[1] < 3 || down[1] > 24) {
            return null;
        }
        float angle = down[0];
        int[] base = new int[CONTROL + 1];
        for (int i = 0; i <= CONTROL; i++) {
            float u = (i / (float) CONTROL - 0.5F) * length;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(angle) * u), c.z() + Math.round(Mth.sin(angle) * u));
            if (h < c.seaLevel()) return null;
            base[i] = i == 0 ? h : Math.min(h, base[i - 1]);
        }
        return new ObsidianFlow(c.x(), c.z(), angle, length, 16 + c.random().nextInt(11), 6 + c.random().nextInt(7), base, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new ObsidianFlow(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"), tag.getInt("thick"),
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
        return LandformKind.OBSIDIAN_FLOW;
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

    /** The flow's half-width: narrow by the vent, spreading as it went. */
    private float halfWidth(float u) {
        float t = Terrain.smooth((u + length / 2.0F) / (length * 0.4F));
        return width / 2.0F * (0.6F + 0.4F * t) * (0.9F + 0.2F * Terrain.field(seed, u, 0.0, 10.0));
    }

    /** How far inside the flow's lobed outline a point is, in blocks (negative outside). */
    private float inside(float u, float v, int x, int z) {
        float half = length / 2.0F, hw = halfWidth(u);
        float front = Math.max(0.0F, u - (half - hw)) / hw;
        float back = Math.max(0.0F, -half + hw * 0.7F - u) / (hw * 0.7F);
        float e = Mth.sqrt(Mth.square(v / hw) + front * front + back * back);
        return (1.0F - e + (Terrain.field(seed + 1, x, z, 6.0) - 0.5F) * 0.25F) * hw;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F, margin = thick * 0.55F;
        float wave = 7.0F + 2.0F * Terrain.hash(seed, 2);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                if (Math.abs(u) > half + 2.0F) continue;
                float in = inside(u, v, x, z);
                if (in <= 0.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground < b.minY() + 1 || ground > b.maxY() - 14) continue;
                pos.set(x, ground + 1, z);
                if (!level.getFluidState(pos).isEmpty()) continue;
                boolean edge = in < margin;
                // flat-topped and thick, with steep sides and a steep front
                float th = thick * Math.min(1.0F, in / margin);
                // crust ridges arching across it, bowed downstream
                float ridge = edge ? 0.0F : 1.6F * (float) Math.pow(Math.max(0.0F, Mth.sin(Mth.TWO_PI * (u - 0.6F * v * v / halfWidth(u)) / wave)), 2.0);
                // swelling up over the vent at its head
                float dome = 2.5F * Terrain.smooth(1.0F - (u + half) / 12.0F);
                float jag = (Terrain.noise(seed, x, 0, z) - 0.5F) * (edge ? 2.0F : 0.6F);
                int top = Math.round(baseAt(u) + th + ridge + dome + jag);
                top = Math.min(top, ground + thick + 5);
                if (top <= ground) {
                    if (in < 2.0F) continue;
                    top = ground + 1; // a thin skin over a hump in the ground
                }
                Terrain.clearAbove(level, pos, x, top, z, Math.min(16, b.maxY() - top));
                float pumice = Terrain.field(seed + 3, x, z, 5.0), tuff = Terrain.field(seed + 4, x, z, 4.0);
                for (int h = ground + 1; h <= top; h++) {
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState s;
                    if (edge && (h > top - 3 || n < 0.3F)) {
                        // the rubbly margin: blocks of glass broken off the front as it crept on, pumice and grit among them
                        s = n < 0.45F ? Blocks.OBSIDIAN.defaultBlockState() : n < 0.6F ? ModBlocks.PUMICE.get().defaultBlockState()
                                : n < 0.72F ? Blocks.TUFF.defaultBlockState() : n < 0.88F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    } else if (h == top) {
                        if (n > 0.994F) s = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                        else if (pumice > 0.66F || ridge > 1.0F && n < 0.4F) s = ModBlocks.PUMICE.get().defaultBlockState();
                        else if (tuff > 0.74F) s = Blocks.TUFF.defaultBlockState();
                        else s = Blocks.OBSIDIAN.defaultBlockState();
                    } else if (h == top - 1 && pumice > 0.72F) {
                        s = ModBlocks.PUMICE.get().defaultBlockState();
                    } else {
                        s = n > 0.998F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, s);
                }
            }
        }
    }
}
