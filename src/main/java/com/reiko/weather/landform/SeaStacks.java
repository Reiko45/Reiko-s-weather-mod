package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Sea stacks: pillars of layered rock standing in the sea off a rocky coast, all that's left of headlands the
 * waves have cut away, notched at the waterline and capped with a scrap of turf. One is still an arch, with the
 * sea running through it.
 */
public record SeaStacks(int cx, int cz, float seaAngle, long seed) implements Landform {
    /** The rock, in layers: grey stone, white chalk, or golden limestone. */
    private static final BlockState[][] ROCK = {
            {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.STONE.defaultBlockState()},
            {Blocks.CALCITE.defaultBlockState(), Blocks.DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState()},
            {Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()},
    };

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() - 3 || ground > c.seaLevel() + 12 || !c.fits(ground, 25, 40)) {
            return null;
        }
        float sea = c.seaward(30, 3);
        if (Float.isNaN(sea)) {
            return null;
        }
        // the stacks need water to stand in, not too deep
        int out = c.groundAt(c.x() + Math.round(Mth.cos(sea) * 22), c.z() + Math.round(Mth.sin(sea) * 22));
        if (out > c.seaLevel() - 2 || out < c.seaLevel() - 22) {
            return null;
        }
        return new SeaStacks(c.x(), c.z(), sea, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new SeaStacks(tag.getInt("x"), tag.getInt("z"), tag.getFloat("sea"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("sea", seaAngle);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.SEA_STACKS;
    }

    @Override
    public BoundingBox bounds() {
        return new BoundingBox(cx - 48, -64, cz - 48, cx + 48, 240, cz + 48);
    }

    /** {u (out to sea), v (along the coast), radius, height above the sea} for each stack. */
    private float[][] stacks() {
        int count = 4 + (int) (Terrain.hash(seed, 0) * 3);
        float[][] list = new float[count][];
        for (int i = 0; i < count; i++) {
            float u = 11.0F + Terrain.hash(seed, 10 + i) * 22.0F;
            float v = (Terrain.hash(seed, 20 + i) - 0.5F) * 70.0F;
            float r = 2.0F + Terrain.hash(seed, 30 + i) * 2.6F;
            // taller nearer the shore, where they've had less time to wear down
            float height = (9.0F + Terrain.hash(seed, 40 + i) * 17.0F) * (1.15F - (u - 11.0F) / 40.0F);
            list[i] = new float[]{u, v, r, height};
        }
        return list;
    }

    private float[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Mth.cos(seaAngle), s = Mth.sin(seaAngle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sea = level.getSeaLevel();
        BlockState[] rock = ROCK[(int) (Terrain.hash(seed, 1) * ROCK.length)];
        float[][] stacks = stacks();
        // the arch: a fin of rock along the coast with a tunnel through it
        float archU = 14.0F + Terrain.hash(seed, 2) * 8.0F, archV = (Terrain.hash(seed, 3) - 0.5F) * 20.0F;
        float archHeight = 12.0F + Terrain.hash(seed, 4) * 6.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = local(x + 0.5, z + 0.5);
                int ground = -1000;
                for (float[] s : stacks) {
                    float du = l[0] - s[0], dv = l[1] - s[1];
                    float d = Mth.sqrt(du * du + dv * dv);
                    if (d > s[2] + 2.5F) continue;
                    if (ground == -1000) ground = Terrain.ground(level, pos, x, z);
                    float wobble = 0.85F + 0.3F * Terrain.field(seed, Math.atan2(dv, du) * 4.0 + s[1], s[0], 1.0);
                    int top = sea + Math.round(s[3]);
                    if (d < s[2] * wobble) {
                        for (int h = ground; h <= top; h++) {
                            float rh = s[2] * wobble * (1.0F - 0.18F * Math.max(0, h - sea) / s[3]) - (Math.abs(h - sea) <= 1 ? 0.8F : 0.0F);
                            if (d > rh) continue; // the waves have cut a notch at the waterline
                            Terrain.set(level, pos, x, h, z, h == top ? (d < rh - 0.7F ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState())
                                    : h == top - 1 ? Blocks.DIRT.defaultBlockState() : layer(rock, h, x, z));
                        }
                        if (d < s[2] * wobble - 1.0F && Terrain.noise(seed, x, top, z) < 0.3F) {
                            Terrain.set(level, pos, x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                        }
                    } else if (ground < sea - 1 && Terrain.noise(seed, x, 0, z) < 0.5F) {
                        // rubble fallen from the stack, on the sea bed round its foot
                        Terrain.set(level, pos, x, ground + 1, z, Terrain.noise(seed, x, 1, z) < 0.5F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    }
                }
                // the arch
                float au = l[0] - archU, av = l[1] - archV;
                if (Math.abs(au) < 2.2F && Math.abs(av) < 7.0F) {
                    if (ground == -1000) ground = Terrain.ground(level, pos, x, z);
                    float end = Mth.sqrt(Math.max(0.0F, 1.0F - (av / 7.0F) * (av / 7.0F)));
                    int top = sea + Math.round(archHeight * (0.75F + 0.25F * end));
                    for (int h = ground; h <= top; h++) {
                        float hole = (av / 4.2F) * (av / 4.2F) + ((h - sea + 2.0F) / (archHeight * 0.62F)) * ((h - sea + 2.0F) / (archHeight * 0.62F));
                        if (hole < 1.0F) continue;
                        Terrain.set(level, pos, x, h, z, h == top ? Blocks.GRASS_BLOCK.defaultBlockState() : layer(rock, h, x, z));
                    }
                }
            }
        }
    }

    /** Layered rock: bands a few blocks thick, gently tilted. */
    private BlockState layer(BlockState[] rock, int h, int x, int z) {
        int band = Math.floorDiv(h + Math.round((x + z) * 0.08F) + (int) (Terrain.hash(seed, 5) * 9), 3);
        return rock[Math.floorMod(band, rock.length)];
    }
}
