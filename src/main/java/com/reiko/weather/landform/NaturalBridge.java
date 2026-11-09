package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A natural bridge: a thick span of rock left standing over a stream that has cut its way right through it. The stream
 * once looped round a narrow neck of rock in its gully; floods undercut the neck from both sides until they broke
 * through, and the water took the short way under, leaving the top of the neck as a bridge with turf still growing on
 * it. (An arch is different: it's carved by frost and wind, not by running water.)
 *
 * @param angle the way the gully runs
 * @param water the stream's level, the same all along it
 * @param deck  the top of the bridge
 */
public record NaturalBridge(int cx, int cz, float angle, int length, int water, int deck, long seed) implements Landform {
    private static final float SIDES = 0.45F; // how far the gully's walls lean back for each block they rise
    private static final int ENDS = 10;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8 || !c.fits(ground, 26, 16)) {
            return null;
        }
        int length = 56 + c.random().nextInt(25), half = length / 2;
        // the gully runs along the lie of the land, the way the ground changes least from one end to the other
        float angle = 0.0F;
        int best = Integer.MAX_VALUE, ends = 0, high = ground;
        for (int i = 0; i < 6; i++) {
            float a = i * Mth.PI / 6.0F;
            int h1 = c.groundAt(c.x() + Math.round(Mth.cos(a) * half), c.z() + Math.round(Mth.sin(a) * half));
            int h2 = c.groundAt(c.x() - Math.round(Mth.cos(a) * half), c.z() - Math.round(Mth.sin(a) * half));
            if (Math.abs(h1 - h2) < best) {
                best = Math.abs(h1 - h2);
                angle = a;
                ends = Math.min(h1, h2);
            }
        }
        float ux = Mth.cos(angle), uz = Mth.sin(angle);
        int low = Math.min(ends, ground);
        for (int s = -1; s <= 1; s += 2) {
            int h = c.groundAt(c.x() + Math.round(ux * half * 0.5F * s), c.z() + Math.round(uz * half * 0.5F * s));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        // the abutments either side of the bridge
        int left = c.groundAt(c.x() - Math.round(uz * 13), c.z() + Math.round(ux * 13));
        int right = c.groundAt(c.x() + Math.round(uz * 13), c.z() - Math.round(ux * 13));
        high = Math.max(high, Math.max(left, right));
        int deck = Math.min(ground, Math.min(left, right));
        int water = Math.min(low - 4, deck - 10);
        if (water < c.seaLevel() - 4 || low < c.seaLevel() + 2 || high - water > 26) {
            return null;
        }
        return new NaturalBridge(c.x(), c.z(), angle, length, water, deck, c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new NaturalBridge(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("water"), tag.getInt("deck"),
                tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("water", water);
        tag.putInt("deck", deck);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.NATURAL_BRIDGE;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 20;
        return new BoundingBox(cx - r, water - 4, cz - r, cx + r, water + 48, cz + r);
    }

    /** The gully's wanderings from side to side (square to the bridge where it passes under it). */
    private float meander(float u) {
        return (Terrain.hash(seed, 1) < 0.5F ? 2.5F : -2.5F) * Mth.cos(u * 0.09F);
    }

    /** The gully floor: a block over the water in the middle, rising toward each end where the gully peters out. */
    private float floorAt(float u) {
        return water + 1 + Math.max(0.0F, Math.abs(u) - (length / 2.0F - ENDS)) * 2.2F;
    }

    private float halfWidth(float u) {
        return 3.0F + 0.8F * Terrain.field(seed, u, 0.0, 9.0);
    }

    /** The stream's half-width, narrowing to nothing a little short of each end, where it rises from (and sinks into) its gravels. */
    private float stream(float u) {
        return (1.3F + 0.4F * Terrain.field(seed + 1, u, 0.0, 6.0)) * Mth.sqrt(Mth.clamp((length / 2.0F - ENDS - 2.0F - Math.abs(u)) / 4.0F, 0.0F, 1.0F));
    }

    private BlockState rock(int x, int h, int z, boolean red) {
        if (red) {
            return switch (Math.floorMod(h + Math.round(Terrain.field(seed + 4, x, z, 9.0) * 3.0F), 7)) {
                case 0, 1 -> Blocks.RED_SANDSTONE.defaultBlockState();
                case 2 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
                case 3 -> Blocks.TERRACOTTA.defaultBlockState();
                case 4 -> Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
                case 5 -> Blocks.RED_TERRACOTTA.defaultBlockState();
                default -> Blocks.RED_SANDSTONE.defaultBlockState();
            };
        }
        float n = Terrain.noise(seed, x, h, z), band = Terrain.field(seed + 5, x * 0.3, h * 1.5, 3.0);
        if (band > 0.7F) return ModBlocks.LIMESTONE.get().defaultBlockState();
        return n < 0.75F ? Blocks.STONE.defaultBlockState() : n < 0.9F ? Blocks.ANDESITE.defaultBlockState()
                : h < water + 4 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        float aw = halfWidth(0.0F) + 1.0F;
        int ah = Mth.clamp(Math.round((deck - water) * 0.55F), 3, deck - water - 5);
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = Terrain.uv(x + 0.5, z + 0.5, cx, cz, angle);
                float u = l[0], vm = l[1] - meander(l[0]), off = Math.abs(vm);
                if (Math.abs(u) > half || off > 18.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 12) continue;
                int surface = ground;
                for (pos.set(x, ground + 1, z); surface < ground + 8 && !level.getFluidState(pos).isEmpty(); pos.move(net.minecraft.core.Direction.UP)) surface++;
                boolean red = level.getBiome(pos.set(x, ground + 1, z)).is(BiomeTags.IS_BADLANDS);
                float floor = floorAt(u), hw = halfWidth(u);
                boolean middle = Math.abs(u) < half - ENDS;
                float cw = stream(u);
                // the bridge: a band of rock left standing across the gully, with the arch worn through under it
                float bt = 2.5F + 1.5F * Terrain.field(seed + 3, x, z, 4.0);
                float span = hw + (deck - floor) * SIDES + 2.0F;
                boolean band = Math.abs(u) < bt && off < span;
                // the gully: its walls lean back as they rise, ragged where blocks have fallen out of them
                float cut = floor + Math.max(0.0F, off - hw) / SIDES + (Terrain.field(seed + 2, x, z, 3.0) - 0.5F) * 3.0F;
                int top = off <= hw ? Math.round(floor) : Math.max(Math.round(floor), Math.round(cut));
                boolean bed = middle && off < cw;
                if (surface > ground) {
                    // water already lying where the gully would go is walled up in rock, so it can't run into it
                    if (band || top < surface || middle && off <= hw + 0.5F) {
                        for (int h = ground + 1; h <= surface; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z, red));
                    }
                    continue;
                }
                if (band) {
                    bridge(level, b, pos, x, z, ground, off, cw, aw, ah, red);
                    continue;
                }
                if (top >= ground && !(middle && off <= hw + 0.5F)) continue;
                Terrain.clearAbove(level, pos, x, ground, z, Math.min(16, b.maxY() - ground));
                if (bed) {
                    // the stream: flat water over a stony bed, a little deeper in the middle
                    int depth = off < cw * 0.5F ? 2 : 1;
                    for (int h = water - depth - 1; h <= Math.max(ground, water); h++) {
                        BlockState s = h > water ? Blocks.AIR.defaultBlockState() : h > water - depth ? Blocks.WATER.defaultBlockState()
                                : h == water - depth && Terrain.noise(seed, x, h, z) < 0.6F ? Blocks.GRAVEL.defaultBlockState() : rock(x, h, z, red);
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    continue;
                }
                for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                float n = Terrain.noise(seed, x, 7, z);
                if (off <= hw + 0.5F) {
                    // the gully floor and the stream's banks, never lower than a block over the water
                    int from = middle ? water - 1 : Math.min(top, ground);
                    for (int h = from; h <= top; h++) {
                        BlockState s = h < top ? rock(x, h, z, red) : red ? (n < 0.6F ? Blocks.RED_SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState())
                                : n < 0.55F ? Blocks.GRASS_BLOCK.defaultBlockState() : n < 0.75F ? Blocks.MOSS_BLOCK.defaultBlockState()
                                : n < 0.9F ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                        Terrain.set(level, pos, x, h, z, s);
                    }
                    float p = Terrain.noise(seed, x, 8, z);
                    if (red && p < 0.06F && n < 0.6F) {
                        Terrain.set(level, pos, x, top + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                    } else if (!red && n < 0.75F && p < 0.45F) {
                        Terrain.set(level, pos, x, top + 1, z, p < 0.15F ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
                    }
                } else if (top < ground - 1) {
                    // the bare rock of the walls
                    for (int h = top - 2; h <= top; h++) Terrain.set(level, pos, x, h, z, rock(x, h, z, red));
                }
            }
        }
    }

    /** A column of the bridge: rock from the gully floor to the deck, with the arch cut through under it and turf on top. */
    private void bridge(WorldGenLevel level, BoundingBox b, BlockPos.MutableBlockPos pos, int x, int z, int ground, float off, float cw, float aw, int ah, boolean red) {
        int top = Math.max(ground, deck);
        Terrain.clearAbove(level, pos, x, top, z, Math.min(12, b.maxY() - top));
        int floor = water + 1;
        float n = Terrain.noise(seed, x, 7, z);
        for (int h = water - 3; h <= top; h++) {
            float t = (h - floor) / (float) ah;
            boolean arch = h > floor && Mth.square(off / aw) + t * t < 1.0F;
            BlockState s;
            if (off < cw && h <= water && h > water - (off < cw * 0.5F ? 2 : 1)) {
                s = Blocks.WATER.defaultBlockState(); // the stream running on under the bridge
            } else if (arch || off < cw && h > water && h <= floor) {
                s = Blocks.AIR.defaultBlockState();
            } else if (h == top) {
                s = red ? (n < 0.5F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.RED_SAND.defaultBlockState()) : Blocks.GRASS_BLOCK.defaultBlockState();
            } else if (h == top - 1) {
                s = Blocks.DIRT.defaultBlockState();
            } else {
                s = rock(x, h, z, red); // solid rock right through, wherever the gully shows its sides
            }
            Terrain.set(level, pos, x, h, z, s);
        }
        if (n < 0.35F) {
            Terrain.set(level, pos, x, top + 1, z, red ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState());
        }
    }
}
