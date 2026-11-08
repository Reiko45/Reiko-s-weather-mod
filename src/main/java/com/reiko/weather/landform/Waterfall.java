package com.reiko.weather.landform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * A waterfall: a stream running to the edge of a cliff and pouring off it into a deep plunge pool it has dug
 * out at the foot, in a notch it has cut back into the hillside. (The spray, and the rainbow in it, are the
 * client's job: it finds falling water by itself.)
 *
 * @param lx    the lip, where the water goes over
 * @param top   the height of the ground at the lip
 * @param angle the way the water falls (downhill)
 * @param foot  the height of the ground at the bottom of the drop
 */
public record Waterfall(int lx, int lz, int top, float angle, int foot, int stream, int pool, long seed) implements Landform {
    @Nullable
    static Landform plan(PlanContext c) {
        int start = c.groundAt(c.x(), c.z());
        if (start < c.seaLevel() + 8 || !c.fits(start, 10, 30)) {
            return null;
        }
        // look round for the steepest drop
        float bestScore = 0.0F, bestAngle = 0.0F;
        int bestLip = 0, bestTop = 0, bestFoot = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            float ux = Mth.cos(a), uz = Mth.sin(a);
            int previous = start, lip = -1, lipHeight = 0;
            for (int s = 2; s <= 26; s += 2) {
                int h = c.groundAt(c.x() + Math.round(ux * s), c.z() + Math.round(uz * s));
                if (lip < 0 && previous - h >= 3) {
                    lip = s - 2;
                    lipHeight = previous;
                }
                if (lip >= 0 && previous - h <= 1) {
                    int drop = lipHeight - previous, run = s - 2 - lip;
                    float score = drop / (float) Math.max(2, run);
                    if (drop >= 8 && run <= 10 && previous > c.seaLevel() && score > bestScore) {
                        bestScore = score;
                        bestAngle = a;
                        bestLip = lip;
                        bestTop = lipHeight;
                        bestFoot = previous;
                    }
                    break;
                }
                previous = h;
            }
        }
        if (bestScore <= 0.0F) {
            return null;
        }
        int lx = c.x() + Math.round(Mth.cos(bestAngle) * bestLip), lz = c.z() + Math.round(Mth.sin(bestAngle) * bestLip);
        return new Waterfall(lx, lz, bestTop, bestAngle, bestFoot, 10 + c.random().nextInt(8), 3 + c.random().nextInt(3), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Waterfall(tag.getInt("x"), tag.getInt("z"), tag.getInt("top"), tag.getFloat("angle"), tag.getInt("foot"),
                tag.getInt("stream"), tag.getInt("pool"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", lx);
        tag.putInt("z", lz);
        tag.putInt("top", top);
        tag.putFloat("angle", angle);
        tag.putInt("foot", foot);
        tag.putInt("stream", stream);
        tag.putInt("pool", pool);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.WATERFALL;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.max(stream, pool * 2 + 6) + 8;
        return new BoundingBox(lx - r, foot - 6, lz - r, lx + r, top + 30, lz + r);
    }

    private float[] local(double x, double z) {
        double dx = x - lx, dz = z - lz, c = Mth.cos(angle), s = Mth.sin(angle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    /** The stream's meanders above the falls (none right at the lip). */
    private float meander(float u) {
        return u > -3.0F ? 0.0F : 1.6F * Mth.sin(u * 0.35F + Terrain.hash(seed, 1) * 6.0F) * Mth.clamp((-u - 3.0F) / 4.0F, 0.0F, 1.0F);
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float poolCentre = pool + 0.5F;
        int surface = foot - 1;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = local(x + 0.5, z + 0.5);
                float u = l[0], v = l[1];
                int ground = Terrain.ground(level, pos, x, z);
                if (u <= 0.5F && u >= -stream) {
                    // the stream: a channel cut into the cliff top, a block of water over a stony bed
                    float off = Math.abs(v - meander(u));
                    if (ground > top + 4) continue; // where the land rises steeply the stream starts, from a spring
                    if (off < 1.3F) {
                        Terrain.clearAbove(level, pos, x, ground, z, 12);
                        for (int h = top; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                        Terrain.set(level, pos, x, top - 2, z, Terrain.noise(seed, x, 0, z) < 0.5F ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState());
                        Terrain.set(level, pos, x, top - 1, z, Blocks.WATER.defaultBlockState());
                        if (u > -0.5F) {
                            level.scheduleTick(pos.set(x, top - 1, z), Fluids.WATER, 1); // set it pouring over the edge
                        }
                    } else if (off < 2.6F && ground < top) {
                        // banks to keep it in its channel
                        for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    }
                    continue;
                }
                if (u <= 0.5F) continue;
                // below the lip: the notch the falls have cut back into the hillside, and the pool at the bottom
                float gorge = 2.0F + u * 0.35F;
                float dp = (float) Math.sqrt((u - poolCentre) * (u - poolCentre) + v * v);
                boolean inPool = dp < pool;
                if (!inPool && dp < pool + 1.6F && ground < foot) {
                    // a lip of rock round the pool, so it doesn't drain away downhill
                    for (int h = ground + 1; h <= foot; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                    ground = foot;
                }
                if (!inPool && (Math.abs(v) > gorge || u > poolCentre + pool + 3)) continue;
                Terrain.clearAbove(level, pos, x, ground, z, 30);
                int floor = inPool ? surface - (dp < pool * 0.5F ? 3 : 2) : foot;
                for (int h = floor + 1; h <= Math.max(ground, top + 2); h++) {
                    BlockState air = Blocks.AIR.defaultBlockState();
                    Terrain.set(level, pos, x, h, z, inPool && h <= surface ? Blocks.WATER.defaultBlockState() : air);
                }
                if (floor < ground) {
                    Terrain.set(level, pos, x, floor, z, inPool ? (Terrain.noise(seed, x, 1, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState()
                            : Blocks.MOSSY_COBBLESTONE.defaultBlockState()) : Blocks.STONE.defaultBlockState());
                } else {
                    // build the rim of the pool up so the water stays in
                    for (int h = ground + 1; h <= floor; h++) Terrain.set(level, pos, x, h, z, Blocks.STONE.defaultBlockState());
                }
                if (!inPool && Terrain.noise(seed, x, 2, z) < 0.3F) {
                    Terrain.set(level, pos, x, floor, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                }
            }
        }
    }
}
