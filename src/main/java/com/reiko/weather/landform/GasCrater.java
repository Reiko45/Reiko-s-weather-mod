package com.reiko.weather.landform;

import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A burning gas crater, like Darvaza in Turkmenistan, the "Door to Hell": the ground over a pocket of natural gas gave
 * way and fell in, leaving a round pit with sheer walls of bedded rock, undercut at the foot where the softer beds have
 * crumbled away. The gas pouring out of the rubble on its floor was set alight so it wouldn't poison the country round
 * about, and it has burned ever since: hundreds of little flames flicker out of cracks across the floor and up the
 * heaps of rubble at the foot of the walls, the rock round them is scorched black, and at night the whole pit glows.
 * Nothing grows near the rim; the ground there is bare, baked and strewn with loose stones.
 *
 * @param rim   the lowest ground round the rim (the walls rise higher where the land does)
 * @param depth how far the floor lies below the rim
 * @param top   the highest ground sampled round it (for the bounds)
 */
public record GasCrater(int cx, int cz, int rim, int radius, int depth, int top, long seed) implements Landform {
    private static final int DESERT = 0, BADLANDS = 1, SOIL = 2;
    /** The highest a vent sits above the floor: its flames stay well down in the pit, far below anything that could burn. */
    private static final int VENTS = 4;

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3) {
            return null;
        }
        int radius = 12 + c.random().nextInt(8), depth = 11 + c.random().nextInt(10);
        float turn = c.random().nextFloat() * Mth.TWO_PI;
        long seed = c.random().nextLong();
        // fairly level ground: here if it will do, or a little way off
        for (int k = 0; k < 9; k++) {
            int ox = c.x(), oz = c.z();
            if (k > 0) {
                float a = turn + k * Mth.TWO_PI / 8.0F;
                ox += Math.round(Mth.cos(a) * 16.0F);
                oz += Math.round(Mth.sin(a) * 16.0F);
            }
            Landform crater = at(c, ox, oz, radius, depth, seed);
            if (crater != null) return crater;
        }
        return null;
    }

    @Nullable
    private static Landform at(PlanContext c, int ox, int oz, int radius, int depth, long seed) {
        int here = c.groundAt(ox, oz);
        if (here < c.seaLevel() + 3) {
            return null;
        }
        int low = here, high = here, ring = radius + 4;
        for (int i = 0; i < 12; i++) {
            // the four quarters first, so most spots that won't do are given up after a few samples
            float a = ((i % 4) * 3 + i / 4) * Mth.TWO_PI / 12.0F;
            int h = c.groundAt(ox + Math.round(Mth.cos(a) * ring), oz + Math.round(Mth.sin(a) * ring));
            low = Math.min(low, h);
            high = Math.max(high, h);
            if (high - low > 12) return null;
        }
        if (low < c.seaLevel() + 2 || !c.fits(low, depth + 6, high - low + 24)) {
            return null;
        }
        return new GasCrater(ox, oz, low, radius, depth, high, seed);
    }

    static Landform load(CompoundTag tag) {
        return new GasCrater(tag.getInt("x"), tag.getInt("z"), tag.getInt("rim"), tag.getInt("r"), tag.getInt("depth"), tag.getInt("top"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("rim", rim);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putInt("top", top);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GAS_CRATER;
    }

    /** How far out it reaches: the undercut walls, and the bare, stony ground round the rim. */
    private float reach() {
        return radius * 1.15F + 15.0F;
    }

    /** How far back from the brink the ground is stripped bare: well clear of the flames all round, and ragged at its edge. */
    private float bare(int x, int z) {
        return 8.0F + 4.0F * Terrain.field(seed + 14, x, z, 5.0);
    }

    @Override
    public BoundingBox bounds() {
        int r = Mth.ceil(reach()) + 1;
        return new BoundingBox(cx - r, rim - depth - 4, cz - r, cx + r, top + 18, cz + r);
    }

    private float wobble(float theta) {
        return 1.0F + 0.06F * Mth.sin(3.0F * theta + Terrain.hash(seed, 1) * Mth.TWO_PI) + 0.04F * Mth.sin(5.0F * theta + Terrain.hash(seed, 2) * Mth.TWO_PI)
                + 0.02F * Mth.sin(8.0F * theta + Terrain.hash(seed, 3) * Mth.TWO_PI);
    }

    /** Which bed of rock a height is in: beds two or three blocks thick. */
    private int bed(int h) {
        return Math.floorDiv(h * 2 + Math.round(Terrain.hash(seed, 4) * 5.0F), 5);
    }

    /**
     * How far the wall stands from the middle at a height: sheer, but undercut toward the foot, with the harder beds
     * standing out as ledges and the softer ones worn back between them.
     */
    private float wall(int x, int h, int z, float theta, int ground) {
        int floor = rim - depth;
        float frac = Mth.clamp((h - floor) / (float) depth, 0.0F, 1.0F);
        float hard = (Terrain.hash(seed, 100 + bed(h)) - 0.5F) * 1.6F;
        float rough = (Terrain.field(seed + 7, x * 0.9 + h * 2.0, z * 0.9 - h * 1.3, 4.0) - 0.5F) * 1.4F;
        float lip = h >= ground - 1 ? 0.8F : 0.0F; // the brink itself crumbling back
        return radius * wobble(theta) * (1.0F + 0.12F * (1.0F - frac)) - hard + rough + lip;
    }

    /** The top of the floor: the broken rock of the old ground, heaped up against the foot of the walls. */
    private int floorTop(int x, int z, float d, float rw) {
        float heap = 3.6F * Terrain.smooth((d / rw - 0.62F) / 0.5F);
        float lumps = 2.0F * Terrain.field(seed + 3, x, z, 5.0) - 0.6F;
        return rim - depth + Math.max(0, Math.round(heap + lumps));
    }

    /** Whether a spot is open air down in the pit (under the overhangs a couple of blocks of roof are left). */
    private boolean hollow(int x, int h, int z, float d, float theta, int ft, int ground, boolean open) {
        return h > ft && (open || h <= ground - 2) && d < wall(x, h, z, theta, ground);
    }

    private static int palette(BlockState was) {
        if (was.is(BlockTags.TERRACOTTA) || was.is(Blocks.RED_SAND) || was.is(Blocks.RED_SANDSTONE)) return BADLANDS;
        if (was.is(BlockTags.SAND) || was.is(Blocks.SANDSTONE)) return DESERT;
        return SOIL;
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floor = rim - depth;
        float reach = reach();
        BlockState air = Blocks.AIR.defaultBlockState(), vent = ModBlocks.GAS_VENT.get().defaultBlockState(), fire = Blocks.FIRE.defaultBlockState();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dx = x + 0.5F - cx, dz = z + 0.5F - cz;
                float d = Mth.sqrt(dx * dx + dz * dz);
                if (d > reach) continue;
                float theta = (float) Mth.atan2(dz, dx);
                int ground = Terrain.ground(level, pos, x, z);
                if (ground > b.maxY() - 10) continue;
                // (a gully or old cave that dips into the pit's floor is filled and dug out with the rest)
                ground = Math.max(ground, floor + 4);
                int palette = palette(level.getBlockState(pos.set(x, ground, z)));
                int flood = Terrain.surface(level, pos, x, ground, z);
                float lip = wall(x, ground, z, theta, ground);
                boolean open = d < lip, band = !open && d < lip + 4.0F;
                // (the walls' shell reaches a few blocks back from the undercut foot)
                if (d - lip > Math.max(bare(x, z), 7.5F)) continue;
                // nothing that could burn is left anywhere near the pit: trees, bushes, grass and all
                int high = Math.max(ground, flood);
                if (d - lip < bare(x, z)) Terrain.clearAbove(level, pos, x, high, z, Math.min(16, b.maxY() - high));
                int ft = floorTop(x, z, d, radius * wobble(theta));
                float vf = Terrain.field(seed + 5, x, z, 6.0);
                float pv = 0.04F + 0.32F * Terrain.smooth((vf - 0.42F) / 0.3F);
                boolean below = false, lit = false;
                for (int h = floor - 3; h <= high; h++) {
                    BlockState s = null;
                    if (h > ground) {
                        // water standing on the ground: let out of the pit, and walled in where it's at the brink
                        s = open ? air : band ? strata(palette, x, h, z, ground, vf) : null;
                    } else if (lit) {
                        s = fire;
                    } else if (hollow(x, h, z, d, theta, ft, ground, open)) {
                        s = air;
                    } else if (h <= ft && d < wall(x, h, z, theta, ground)) {
                        // the floor: rubble of the old ground, with gas seeping up through it
                        if (h == ft && h <= floor + VENTS && hollow(x, h + 1, z, d, theta, ft, ground, open) && Terrain.noise(seed + 6, x, h, z) < pv) {
                            s = vent;
                        } else {
                            s = rubble(palette, x, h, z, h == ft ? vf : 0.0F);
                            if (s.getBlock() instanceof FallingBlock && (below || h == floor - 3)) s = Blocks.COBBLESTONE.defaultBlockState();
                        }
                    } else {
                        // the walls: bedded rock, kept sound right round the pit so nothing can leak or fall into it
                        BlockState here = level.getBlockState(pos.set(x, h, z));
                        float into = d - wall(x, h, z, theta, ground);
                        if (into < 1.6F || into < 4.5F && (here.isAir() || !here.getFluidState().isEmpty()) || below && here.getBlock() instanceof FallingBlock) {
                            s = strata(palette, x, h, z, ground, vf);
                            if (into < 1.0F && h <= floor + VENTS && hollow(x, h + 1, z, d, theta, ft, ground, open)
                                    && Terrain.noise(seed + 6, x, h, z) < pv * 0.8F) {
                                s = vent;
                            }
                        }
                    }
                    lit = s == vent;
                    if (s != null) Terrain.set(level, pos, x, h, z, s);
                    below = s != null && !s.blocksMotion();
                }
                if (!open && d - lip < bare(x, z)) rim(level, pos, x, z, d - lip, ground, flood > ground, palette);
            }
        }
    }

    /** The bare ground round the rim: turf stripped back to baked earth, and loose stones from the collapse strewn over it. */
    private void rim(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float out, int ground, boolean wet, int palette) {
        BlockState top = level.getBlockState(pos.set(x, ground, z));
        float n = Terrain.noise(seed + 10, x, ground, z);
        if (top.is(BlockTags.DIRT) || top.is(Blocks.MOSS_BLOCK) || top.is(Blocks.DIRT_PATH)) {
            top = n < 0.65F ? Blocks.COARSE_DIRT.defaultBlockState() : n < 0.85F ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            if (top.is(Blocks.GRAVEL) && !level.getBlockState(pos.set(x, ground - 1, z)).blocksMotion()) top = Blocks.COARSE_DIRT.defaultBlockState();
            Terrain.set(level, pos, x, ground, z, top);
        }
        float m = Terrain.noise(seed + 11, x, ground + 1, z);
        if (!wet && out < 4.0F && m < 0.2F * (1.0F - out / 4.0F) + 0.03F && level.getBlockState(pos.set(x, ground + 1, z)).isAir()) {
            BlockState stone = m < 0.05F ? Blocks.COBBLESTONE.defaultBlockState() : strata(palette, x, ground - 3 - Math.round(m * 30.0F), z, ground, 0.0F);
            Terrain.set(level, pos, x, ground + 1, z, stone);
        }
    }

    /** The beds the pit cuts through: sandstone in the desert, banded clays in the badlands, grey rock elsewhere; blackened low down, round the flames. */
    private BlockState strata(int palette, int x, int h, int z, int ground, float vf) {
        int floor = rim - depth;
        float n = Terrain.noise(seed + 9, x, h, z);
        if (h <= floor + 6 && n < (floor + 7 - h) / 9.0F * (0.5F + vf)) {
            return n < 0.15F ? Blocks.BASALT.defaultBlockState() : n < 0.25F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        if (palette == SOIL && h > ground - 3) {
            return h == ground && n < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        // the odd frost-cracked block
        if (n > 0.95F) return palette == BADLANDS ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        float k = Terrain.hash(seed, 300 + bed(h));
        return switch (palette) {
            case DESERT -> k < 0.4F ? Blocks.SANDSTONE.defaultBlockState() : k < 0.55F ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : k < 0.75F ? Blocks.STONE.defaultBlockState() : k < 0.88F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
            case BADLANDS -> k < 0.22F ? Blocks.TERRACOTTA.defaultBlockState() : k < 0.38F ? Blocks.ORANGE_TERRACOTTA.defaultBlockState()
                    : k < 0.52F ? Blocks.RED_TERRACOTTA.defaultBlockState() : k < 0.64F ? Blocks.YELLOW_TERRACOTTA.defaultBlockState()
                    : k < 0.76F ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : k < 0.88F ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                    : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
            default -> k < 0.35F ? Blocks.STONE.defaultBlockState() : k < 0.5F ? Blocks.ANDESITE.defaultBlockState() : k < 0.65F ? Blocks.TUFF.defaultBlockState()
                    : k < 0.8F ? ModBlocks.LIMESTONE.get().defaultBlockState() : k < 0.9F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.BROWN_TERRACOTTA.defaultBlockState();
        };
    }

    /** The floor's rubble: broken beds and gravel, scorched black and glassy where the gas burns thickest. */
    private BlockState rubble(int palette, int x, int h, int z, float scorch) {
        float n = Terrain.noise(seed + 12, x, h, z);
        if (scorch > 0.42F && n < 0.35F + scorch * 0.5F) {
            float m = Terrain.noise(seed + 13, x, h, z);
            return m < 0.45F ? Blocks.BLACKSTONE.defaultBlockState() : m < 0.75F ? Blocks.BASALT.defaultBlockState()
                    : m < 0.9F ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (n < 0.3F) return Blocks.COBBLESTONE.defaultBlockState();
        if (n < 0.45F) return Blocks.GRAVEL.defaultBlockState();
        if (n < 0.55F) return Blocks.COARSE_DIRT.defaultBlockState();
        if (n < 0.62F) return Blocks.BLACKSTONE.defaultBlockState();
        return strata(palette, x, h - Math.round(n * 12.0F), z, Integer.MAX_VALUE, 0.0F);
    }
}
