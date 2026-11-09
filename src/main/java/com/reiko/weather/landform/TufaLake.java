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
 * A salty desert lake with tufa towers: knobbly pale spires of limestone that grew underwater where springs
 * bubbled up through the alkaline lake bed, left standing in the shallows and on the shore as the lake shrank.
 */
public record TufaLake(int cx, int cz, int y, int radius, long seed) implements Landform {
    private static final float SHORE = 1.45F;

    @Nullable
    static Landform plan(PlanContext c) {
        int radius = 16 + c.random().nextInt(11);
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 2 || c.relief(radius, 8) > 9 || !c.fits(ground, 10, 20)) {
            return null;
        }
        int low = ground;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            low = Math.min(low, c.groundAt(c.x() + Math.round(Mth.cos(a) * radius * SHORE), c.z() + Math.round(Mth.sin(a) * radius * SHORE)));
        }
        return new TufaLake(c.x(), c.z(), Math.max(c.seaLevel() + 1, low - 1), radius, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new TufaLake(tag.getInt("x"), tag.getInt("z"), tag.getInt("y"), tag.getInt("r"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("y", y);
        tag.putInt("r", radius);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.TUFA_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * SHORE) + 4;
        return new BoundingBox(cx - r, y - 8, cz - r, cx + r, y + 20, cz + r);
    }

    private float distance(double x, double z) {
        double dx = x - cx, dz = z - cz;
        float a = (float) Math.atan2(dz, dx);
        return (float) Math.sqrt(dx * dx + dz * dz) / (radius * (1.0F + 0.15F * Mth.sin(a * 3.0F + Terrain.hash(seed, 1) * 6.0F)));
    }

    /** {x, z, radius, height} for each tower. */
    private float[][] towers() {
        int count = 8 + (int) (Terrain.hash(seed, 2) * 8);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float a = Terrain.hash(seed, 10 + i) * Mth.TWO_PI, r = (0.2F + Terrain.hash(seed, 20 + i) * 1.05F) * radius;
            list[i] = new float[]{cx + Mth.cos(a) * r, cz + Mth.sin(a) * r, 0.9F + Terrain.hash(seed, 30 + i) * 1.4F, 3.0F + Terrain.hash(seed, 40 + i) * 7.0F};
        }
        return list;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float[][] towers = towers();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float d = distance(x + 0.5, z + 0.5);
                if (d > SHORE) continue;
                int ground = Terrain.ground(level, pos, x, z);
                float n = Terrain.noise(seed, x, 0, z);
                int surface;
                if (d < 1.0F) {
                    int depth = d < 0.4F ? 3 : d < 0.75F ? 2 : 1;
                    surface = y - depth;
                    Terrain.clearAbove(level, pos, x, Math.max(ground, y), z, 20);
                    for (int h = surface + 1; h <= Math.max(ground, y); h++) Terrain.set(level, pos, x, h, z, h <= y ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground, surface) - 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, n < 0.6F ? Blocks.SAND.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                } else {
                    // a white crust of salts round the shore, where the lake has dried back
                    float t = Terrain.smooth((d - 1.0F) / (SHORE - 1.0F));
                    surface = Math.round(Mth.lerp(t * t, y, Math.max(ground, y)));
                    Terrain.clearAbove(level, pos, x, Math.min(ground, surface), z, 10);
                    for (int h = surface + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    for (int h = Math.min(ground, surface) - 1; h < surface; h++) Terrain.set(level, pos, x, h, z, Blocks.SAND.defaultBlockState());
                    Terrain.set(level, pos, x, surface, z, d < 1.2F && n < 0.7F ? ModBlocks.SALT_CRUST.get().defaultBlockState()
                            : n < 0.2F ? Blocks.CALCITE.defaultBlockState() : Blocks.SAND.defaultBlockState());
                }
                for (float[] t : towers) {
                    float dt = (float) Math.sqrt((x + 0.5 - t[0]) * (x + 0.5 - t[0]) + (z + 0.5 - t[1]) * (z + 0.5 - t[1]));
                    if (dt > t[2] + 1.0F) continue;
                    int top = y + Math.round(t[3]);
                    for (int h = surface + 1; h <= top; h++) {
                        // knobbly: the radius swells and pinches up the tower
                        float r = t[2] * (1.0F - 0.35F * (h - y) / t[3]) + 0.5F * (Terrain.noise(seed, x, h, z) - 0.5F)
                                + 0.4F * Mth.sin(h * 1.3F + t[0]);
                        if (dt > r) continue;
                        float m = Terrain.noise(seed + 7, x, h, z);
                        BlockState rock = m < 0.45F ? ModBlocks.LIMESTONE.get().defaultBlockState() : m < 0.7F ? Blocks.TUFF.defaultBlockState()
                                : m < 0.88F ? Blocks.CALCITE.defaultBlockState() : Blocks.DIORITE.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, rock);
                    }
                }
            }
        }
    }
}
