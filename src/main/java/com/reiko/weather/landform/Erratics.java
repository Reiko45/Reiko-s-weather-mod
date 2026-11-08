package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * What the ice sheets left behind: a long, curving moraine ridge of rubble the glacier pushed ahead of it, and
 * great erratic boulders, carried for miles on the ice and dropped where it melted, made of rock that doesn't
 * belong anywhere near here.
 */
public record Erratics(int cx, int cz, float angle, long seed) implements Landform {
    private static final BlockState[] BOULDER = {Blocks.GRANITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()};
    private static final float ARC = 75.0F;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 1 || c.relief(40, 8) > 20 || !c.fits(ground, 10, 30)) {
            return null;
        }
        return new Erratics(c.x(), c.z(), c.random().nextFloat() * Mth.TWO_PI, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Erratics(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ERRATICS;
    }

    @Override
    public BoundingBox bounds() {
        return new BoundingBox(cx - 64, -64, cz - 64, cx + 64, 320, cz + 64);
    }

    /** {x, z, radius} for each boulder. */
    private float[][] boulders() {
        int count = 5 + (int) (Terrain.hash(seed, 0) * 5);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float a = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, r = 8.0F + Terrain.hash(seed, 20 + i) * 46.0F;
            list[i] = new float[]{cx + Mth.cos(a) * r, cz + Mth.sin(a) * r, 1.4F + Terrain.hash(seed, 30 + i) * 2.4F};
        }
        return list;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the moraine: an arc of a circle centred out to one side
        double ox = cx - Mth.cos(angle) * ARC, oz = cz - Mth.sin(angle) * ARC;
        float span = 0.75F;
        float[][] boulders = boulders();
        BlockState rock = BOULDER[(int) (Terrain.hash(seed, 1) * BOULDER.length)];
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                double dx = x + 0.5 - ox, dz = z + 0.5 - oz;
                float a = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx) - angle)) * Mth.DEG_TO_RAD;
                float across = (float) Math.sqrt(dx * dx + dz * dz) - ARC;
                if (Math.abs(a) < span && Math.abs(across) < 9.0F) {
                    // a low, rounded ridge, tapering off at its ends
                    float ends = Terrain.smooth((span - Math.abs(a)) / 0.2F);
                    float height = (4.0F + 2.5F * Terrain.field(seed, x, z, 12.0)) * ends;
                    float profile = Mth.cos(across / 9.0F * Mth.HALF_PI);
                    int rise = Math.round(height * profile * profile);
                    if (rise > 0) moraine(level, pos, x, z, rise);
                }
                for (float[] bo : boulders) {
                    float d = (float) Math.sqrt((x + 0.5 - bo[0]) * (x + 0.5 - bo[0]) + (z + 0.5 - bo[1]) * (z + 0.5 - bo[1]));
                    if (d < bo[2]) boulder(level, pos, x, z, bo, d, rock);
                }
            }
        }
    }

    /** A column of the moraine: a rubbly mix of gravel, cobble and soil, grassed over on top. */
    private void moraine(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int rise) {
        int ground = Terrain.ground(level, pos, x, z);
        pos.set(x, ground, z);
        BlockState top = level.getBlockState(pos);
        boolean grassy = top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.SNOW_BLOCK);
        Terrain.clearAbove(level, pos, x, ground, z, 10);
        for (int h = ground; h <= ground + rise; h++) {
            float n = Terrain.noise(seed, x, h, z);
            BlockState fill = n < 0.35F ? Blocks.GRAVEL.defaultBlockState() : n < 0.6F ? Blocks.COBBLESTONE.defaultBlockState()
                    : n < 0.7F ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            if (h == ground + rise) {
                fill = n < 0.15F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : n < 0.25F ? Blocks.GRAVEL.defaultBlockState()
                        : grassy || top.is(BlockTags.DIRT) ? top.is(Blocks.SNOW_BLOCK) ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState()
                        : Blocks.COARSE_DIRT.defaultBlockState();
            } else if (h == ground + rise - 1 && n >= 0.25F) {
                fill = Blocks.DIRT.defaultBlockState();
            }
            Terrain.set(level, pos, x, h, z, fill);
        }
    }

    /** A rough, rounded boulder sitting half buried in the ground, with moss on its shady side. */
    private void boulder(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float[] bo, float d, BlockState rock) {
        int ground = Terrain.ground(level, pos, x, z);
        float r = bo[2];
        int bottom = ground - Math.round(r * 0.4F);
        float half = Mth.sqrt(Math.max(0.0F, r * r - d * d)) * 0.85F;
        int top = Math.round(bottom + r * 0.6F + half);
        Terrain.clearAbove(level, pos, x, ground, z, 8);
        for (int h = bottom; h <= top; h++) {
            float n = Terrain.noise(seed, x, h, z);
            Terrain.set(level, pos, x, h, z, h == top && n < 0.35F ? Blocks.MOSS_BLOCK.defaultBlockState()
                    : n < 0.12F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock);
        }
    }
}
