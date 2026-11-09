package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A cirque (a corrie, or cwm): the armchair-shaped hollow a small glacier gouged into a mountainside, with a
 * sheer headwall behind, scree fanning down from it, and a round tarn lying in the floor behind a lip of
 * rock and moraine. Snow lingers in its shade long into the summer.
 */
public record Cirque(int cx, int cz, int y, float angle, int radius, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        int radius = 16 + c.random().nextInt(9);
        float[] down = c.downhill(radius);
        if (ground < c.seaLevel() + 30 || down[1] < 8 || !c.fits(ground, 12, 70)) {
            return null;
        }
        // the floor is cut a few blocks into the slope, with the mountain rising behind
        int behind = c.groundAt(c.x() - Math.round(Mth.cos(down[0]) * radius), c.z() - Math.round(Mth.sin(down[0]) * radius));
        if (behind < ground + 8) {
            return null;
        }
        return new Cirque(c.x(), c.z(), ground - 3, down[0], radius, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Cirque(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getFloat("angle"), tag.getInt("r"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putFloat("angle", angle);
        tag.putInt("r", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.CIRQUE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 2.1F) + 4;
        return new BoundingBox(cx - r, y - 8, cz - r, cx + r, y + 80, cz + r);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], v = l[1];
                // the bowl is round at the back and opens out downhill
                float wobble = 0.9F + 0.2F * Terrain.field(seed, x, z, 9.0);
                float d = Mth.sqrt(Mth.square(u / (radius * (u < 0 ? 1.0F : 1.6F))) + Mth.square(v / (radius * 0.9F))) / wobble;
                if (d > 1.15F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, y, z);
                boolean front = u > 0;
                // the tarn in the floor
                float tarn = Mth.sqrt(Mth.square((u - radius * 0.1F) / (radius * 0.42F)) + Mth.square(v / (radius * 0.36F)))
                        / (0.85F + 0.3F * Terrain.field(seed + 1, x, z, 5.0));
                int top;
                if (d < (front ? 0.6F : 0.55F)) {
                    top = y; // the floor
                } else if (front) {
                    if (ground >= y) {
                        top = Math.min(ground, y + Math.round((d - 0.6F) * 25.0F)); // the bowl's sides opening out downhill
                    } else {
                        // the lip of moraine that dams the tarn, its outer face falling away down the mountainside
                        top = Math.max(ground, y - Math.round((d - 0.6F) * radius * 2.6F));
                    }
                    if (top == ground) continue;
                } else {
                    // the headwall: steep, then sheer near the top
                    float t = (d - 0.55F) / 0.45F;
                    top = y + Math.round(t * t * 60.0F);
                    if (top >= ground) continue;
                }
                Terrain.clearAbove(level, pos, x, Math.max(ground, top), z, 12);
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                // the lip of rock and old moraine that dams the tarn
                for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState());
                if (tarn < 1.0F) {
                    int depth = 1 + Math.round(4.0F * (1.0F - tarn * tarn));
                    for (int h = Math.min(ground + 1, y - depth - 1); h <= y - depth; h++) Terrain.set(level, pos, x, h, z, n < 0.6F ? Blocks.STONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    for (int h = y - depth + 1; h <= y; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                pos.set(x, top, z);
                BlockState was = level.getBlockState(pos);
                boolean grassy = level.getBiome(pos).value().getBaseTemperature() > 0.15F && !was.is(BlockTags.SNOW);
                BlockState surface;
                if (d >= 0.55F && !front) {
                    surface = Terrain.field(seed + 2, x, top * 0.5, 5.0) > 0.6F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                } else if (d > 0.42F && !front || n > 0.8F) {
                    surface = Blocks.GRAVEL.defaultBlockState(); // scree fanning down from the headwall, and stones on the floor
                } else {
                    surface = grassy && n < 0.55F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.35F ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
                }
                Terrain.set(level, pos, x, top, z, surface);
                if (top > y) {
                    // the wall: a few blocks of rock behind its face
                    for (int h = top - 3; h < top; h++) {
                        pos.set(x, h, z);
                        if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), Terrain.FLAGS);
                    }
                } else if (!front && d > 0.3F && n < 0.3F || n > 0.97F) {
                    // old snow lying in the shade at the back of the bowl
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.round(n * 9) % 4));
                } else if (surface.is(Blocks.GRASS_BLOCK) && n < 0.15F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }
}
