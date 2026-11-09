package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A tongue of ice flowing down a mountainside: packed ice with veins of blue ice, snow-covered higher up and
 * bare lower down. Crevasses cut across it, many of them hidden under a bridge of powder snow. Where its bed
 * steepens, the ice is stretched until it cracks apart into an icefall, a chaos of toppling ice towers (seracs)
 * and gaping crevasses. Lower down, meltwater streams on its surface have drilled moulins, shafts straight down
 * through the ice to the rock. In summer the lower end melts back a little, meltwater runs off it, and pours
 * roaring down the moulins.
 *
 * @param icefall where along the glacier its icefall is ({@link #NO_ICEFALL} if it hasn't one, as on glaciers from
 *                before there were any)
 * @param moulins whether it has moulins (not on glaciers from before there were any)
 */
public record Glacier(int cx, int cz, float angle, int length, int width, int thickness, int icefall, boolean moulins, long seed) implements Landform {
    public static final int NO_ICEFALL = Integer.MIN_VALUE;

    public Glacier(int cx, int cz, float angle, int length, int width, int thickness, long seed) {
        this(cx, cz, angle, length, width, thickness, NO_ICEFALL, false, seed);
    }

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 8 || !c.fits(ground, 30, 40)) {
            return null;
        }
        // flow downhill: find the steepest way down
        float best = 0.0F;
        int drop = 0;
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8.0F;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(a) * 30), c.z() + Math.round(Mth.sin(a) * 30));
            if (ground - h > drop) {
                drop = ground - h;
                best = a;
            }
        }
        if (drop < 6) {
            return null;
        }
        int length = 70 + c.random().nextInt(41), width = 24 + c.random().nextInt(13), thickness = 5 + c.random().nextInt(5);
        long seed = c.random().nextLong();
        // where the bed steepens most (if it steepens enough), the ice breaks up into an icefall
        int icefall = NO_ICEFALL, steepest = 5, previous = 0;
        float step = length * 0.8F / 6.0F;
        for (int i = 0; i <= 6; i++) {
            float u = -length * 0.4F + i * step;
            int h = c.groundAt(c.x() + Math.round(Mth.cos(best) * u), c.z() + Math.round(Mth.sin(best) * u));
            if (i > 0 && previous - h > steepest) {
                steepest = previous - h;
                icefall = Math.round(u - step / 2.0F);
            }
            previous = h;
        }
        return new Glacier(c.x(), c.z(), best, length, width, thickness, icefall, true, seed);
    }

    static Landform load(CompoundTag tag) {
        return new Glacier(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getInt("thick"), tag.contains("icefall") ? tag.getInt("icefall") : NO_ICEFALL, tag.getBoolean("moulins"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putInt("thick", thickness);
        tag.putInt("icefall", icefall);
        tag.putBoolean("moulins", moulins);
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GLACIER;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + width / 2 + 3;
        return new BoundingBox(cx - r, -64, cz - r, cx + r, 319, cz + r);
    }

    /** Along the glacier (downhill, 0 in the middle) and across it. */
    private float[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Mth.cos(angle), s = Mth.sin(angle);
        return new float[]{(float) (dx * c + dz * s), (float) (-dx * s + dz * c)};
    }

    private float halfWidth(float u) {
        float half = length / 2.0F;
        return width / 2.0F * (1.0F - 0.45F * Math.max(0.0F, u) / half) * (1.0F + 0.1F * Mth.sin(u * 0.08F + seed % 100));
    }

    /** How thick the ice is here (0 outside the glacier). */
    public float thicknessAt(double x, double z) {
        float[] l = local(x, z);
        float half = length / 2.0F, hw = halfWidth(l[0]);
        if (Math.abs(l[0]) > half || Math.abs(l[1]) > hw) return 0.0F;
        float across = Mth.sqrt(Math.max(0.0F, 1.0F - (l[1] / hw) * (l[1] / hw)));
        float ends = Terrain.smooth((half - l[0]) / (length * 0.12F)) * Terrain.smooth((l[0] + half) / (length * 0.08F));
        return thickness * across * ends;
    }

    /** How far (u, v) is from the line of the ice cave (a big number if this glacier hasn't got one). */
    private float caveDistance(float u, float v) {
        float half = length / 2.0F;
        if (Terrain.hash(seed, 11) > 0.65F || u > half - 2.0F || u < half - 34.0F) return 99.0F;
        float centre = 3.0F * Mth.sin(u * 0.15F + Terrain.hash(seed, 12) * 6.0F);
        return Math.abs(v - centre);
    }

    /** Whether (u, v) is in a crevasse, and which one (or -1). */
    private int crevasse(float u, float v) {
        float half = length / 2.0F, spacing = 13.0F + 5.0F * Terrain.hash(seed, 7);
        for (int k = 0; ; k++) {
            float uk = -half + length * 0.15F + k * spacing;
            if (uk > length * 0.25F) return -1;
            if (Math.abs(u - uk - 2.0F * Mth.sin(v * 0.18F + k)) < 0.9F && Math.abs(v) < halfWidth(u) * 0.8F) return k;
        }
    }

    /** How much a point is in the icefall (0-1). */
    private float icefallAt(float u) {
        return icefall == NO_ICEFALL ? 0.0F : Terrain.smooth((10.0F - Math.abs(u - icefall)) / 4.0F);
    }

    /**
     * The moulins, {x, z, size, lip x, lip z}: each a shaft 1 or 2 blocks across, and the notch on one side of it
     * where the meltwater pours in. They're only where the ice is thick and whole, clear of crevasses, the ice cave
     * and the icefall, and above the snout that melts back in summer.
     */
    private int[][] moulinList() {
        if (!moulins) return new int[0][];
        int n = 2 + Math.round(Terrain.hash(seed, 70) * 2.0F);
        java.util.List<int[]> list = new java.util.ArrayList<>();
        double c = Mth.cos(angle), s = Mth.sin(angle);
        for (int i = 0; i < 16 && list.size() < n; i++) {
            float u = length * (-0.1F + 0.33F * Terrain.hash(seed, 71 + i)), v = (Terrain.hash(seed, 90 + i) - 0.5F) * halfWidth(u) * 0.9F;
            int x = Mth.floor(cx + u * c - v * s), z = Mth.floor(cz + u * s + v * c);
            int size = Terrain.hash(seed, 110 + i) < 0.5F ? 1 : 2;
            int side = Math.min(3, (int) (Terrain.hash(seed, 130 + i) * 4.0F));
            int lx = side == 0 ? x - 1 : side == 1 ? x + size : x, lz = side == 2 ? z - 1 : side == 3 ? z + size : z;
            boolean ok = true;
            for (int dx = -2; dx <= size + 1 && ok; dx++) {
                for (int dz = -2; dz <= size + 1 && ok; dz++) {
                    float[] l = local(x + dx + 0.5, z + dz + 0.5);
                    ok = thicknessAt(x + dx + 0.5, z + dz + 0.5) >= 4.0F && crevasse(l[0], l[1]) < 0 && caveDistance(l[0], l[1]) > 4.5F && icefallAt(l[0]) == 0.0F;
                }
            }
            if (ok) list.add(new int[]{x, z, size, lx, lz});
        }
        return list.toArray(new int[0][]);
    }

    private static BlockState iceMix(float n) {
        return n < 0.35F ? Blocks.BLUE_ICE.defaultBlockState() : n < 0.75F ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
    }

    /** The icefall: the ice cracked into blocks that lean and topple into towers of bare ice, with crevasses gaping between. */
    private void icefall(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float[] l, int ground, int top, float fall) {
        float cu = (l[0] - icefall) / 4.0F;
        int iu = Mth.floor(cu);
        float cv = l[1] / 4.0F + 0.5F * iu; // each row of blocks staggered against the last
        int iv = Mth.floor(cv);
        float fu = (cu - iu) * 4.0F, fv = (cv - iv) * 4.0F;
        long cell = seed + iu * 73428767L + iv * 912931L;
        if (fu < 1.0F || fv < 0.9F) {
            int floor = Math.max(ground + 1, top - 3 - Math.round(Terrain.hash(cell, 1) * 3.0F * fall));
            for (int h = floor + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
            Terrain.set(level, pos, x, floor, z, Terrain.noise(seed, x, floor, z) < 0.5F ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState());
            return;
        }
        // a serac, its shoulders broken off
        int rise = Math.round(fall * (1.0F + 4.0F * Terrain.hash(cell, 2)));
        if (fu < 1.6F || fu > 3.4F || fv < 1.5F || fv > 3.4F) rise = Math.max(0, rise - 1 - Math.round(Terrain.hash(cell, 3)));
        for (int h = Math.max(ground + 1, top - 1); h <= top + rise; h++) Terrain.set(level, pos, x, h, z, iceMix(Terrain.noise(seed + 1, x, h, z)));
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] shafts = moulinList();
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                int thick = Math.round(thicknessAt(x + 0.5, z + 0.5));
                float[] l = local(x + 0.5, z + 0.5);
                if (thick < 1) {
                    if (WeatherConfig.SPEC.isLoaded() && WeatherConfig.ERRATICS.get()) moraine(level, pos, x, z, l);
                    continue;
                }
                int ground = Terrain.ground(level, pos, x, z);
                Terrain.clearAbove(level, pos, x, ground, z, 24);
                int top = ground + thick;
                float fall = icefallAt(l[0]);
                int crev = thick >= 3 && fall < 0.05F ? crevasse(l[0], l[1]) : -1;
                boolean wall = crev < 0 && thick >= 3 && fall < 0.05F && (crevasse(l[0] + 1.2F, l[1]) >= 0 || crevasse(l[0] - 1.2F, l[1]) >= 0);
                for (int h = ground + 1; h <= top; h++) {
                    float n = Terrain.noise(seed, x, h, z);
                    BlockState ice = wall || n > 0.88F && h < top - 1 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    if (h == top) {
                        ice = l[0] < 0.0F ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    }
                    Terrain.set(level, pos, x, h, z, ice);
                }
                // an ice cave: a blue tunnel melted up into the glacier from its foot
                float cave = caveDistance(l[0], l[1]);
                if (cave < 2.8F && thick >= 5) {
                    int roof = Math.round(Mth.sqrt(Math.max(0.0F, 2.6F * 2.6F - cave * cave)) * 1.3F) + 1;
                    for (int h = ground + 1; h <= ground + Math.min(roof, thick - 2); h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                } else if (cave < 4.0F && thick >= 5) {
                    for (int h = ground + 1; h <= ground + Math.min(4, thick - 1); h++) Terrain.set(level, pos, x, h, z, Blocks.BLUE_ICE.defaultBlockState());
                }
                if (fall >= 0.05F && thick >= 2) {
                    icefall(level, pos, x, z, l, ground, top, fall);
                    continue;
                }
                if (moulin(level, pos, shafts, x, z, ground, top)) continue;
                if (crev >= 0) {
                    int depth = Mth.clamp(thick + 2, 5, 8);
                    for (int h = top - depth + 1; h <= top; h++) {
                        Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    }
                    Terrain.set(level, pos, x, top - depth, z, Blocks.SNOW_BLOCK.defaultBlockState());
                    // most of each crevasse hides under a thin bridge of powder snow
                    if (Terrain.hash(seed, crev * 31 + Mth.floor(l[1] / 4.0F)) < 0.7F) {
                        Terrain.set(level, pos, x, top, z, Blocks.POWDER_SNOW.defaultBlockState());
                    }
                } else if (l[0] < -length * 0.1F && Terrain.noise(seed, x, 0, z) > 0.3F) {
                    Terrain.set(level, pos, x, top + 1, z, Blocks.SNOW.defaultBlockState());
                }
            }
        }
    }

    /** A moulin's shaft, its lip, or the blue ice lining it, if this column is one; whether it was. */
    private static boolean moulin(WorldGenLevel level, BlockPos.MutableBlockPos pos, int[][] shafts, int x, int z, int ground, int top) {
        for (int[] m : shafts) {
            if (x >= m[0] && x < m[0] + m[2] && z >= m[1] && z < m[1] + m[2]) {
                // the shaft, straight down to the rock it has bored through to
                for (int h = ground + 1; h <= top; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                return true;
            }
            if (x == m[3] && z == m[4]) {
                // the notch at its lip the meltwater runs in by, frozen up until summer
                Terrain.set(level, pos, x, top, z, Blocks.AIR.defaultBlockState());
                Terrain.set(level, pos, x, top - 1, z, Blocks.ICE.defaultBlockState());
                return true;
            }
            if (x >= m[0] - 1 && x <= m[0] + m[2] && z >= m[1] - 1 && z <= m[1] + m[2]) {
                // its walls, scoured clean to blue ice
                for (int h = ground + 1; h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.BLUE_ICE.defaultBlockState());
                return false;
            }
        }
        return false;
    }

    /** Meltwater pours into the moulins in summer; the rest of the year their lips are frozen up. */
    private void moulinWater(ServerLevel level, boolean summer) {
        int[][] shafts = moulinList();
        if (shafts.length == 0) return;
        int[] m = shafts[level.random.nextInt(shafts.length)];
        BlockPos column = new BlockPos(m[3], 0, m[4]);
        if (!level.isLoaded(column)) return;
        BlockPos lip = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
        // the shaft column beside the lip
        int sx = Mth.clamp(m[3], m[0], m[0] + m[2] - 1), sz = Mth.clamp(m[4], m[1], m[1] + m[2] - 1);
        BlockPos shaft = new BlockPos(sx, lip.getY(), sz);
        BlockState state = level.getBlockState(lip);
        if (summer && state.is(Blocks.ICE) && level.getBlockState(shaft).isAir()) {
            // only if the water can go nowhere but down the shaft
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos side = lip.relative(d);
                if (!side.equals(shaft) && !level.getBlockState(side).isFaceSturdy(level, side, d.getOpposite())) return;
            }
            level.setBlock(lip, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        } else if (!summer && state.is(Blocks.WATER) && level.getFluidState(lip).isSource()) {
            level.setBlock(lip, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The terminal moraine: a horseshoe-shaped ridge of rubble round the glacier's foot, bulldozed up by the ice
     * when it reached a little further than it does now.
     */
    private void moraine(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, float[] l) {
        float half = length / 2.0F;
        float mu = l[0] - (half + 3.5F), side = halfWidth(half) + 6.0F;
        float lateral = Math.abs(l[1]) - (halfWidth(l[0]) + 2.5F);
        float rise;
        if (Math.abs(mu) < 4.5F && Math.abs(l[1]) < side) {
            float p = Mth.cos(mu / 4.5F * Mth.HALF_PI);
            rise = 3.5F * p * p * Terrain.smooth((side - Math.abs(l[1])) / 5.0F);
        } else if (l[0] > half * 0.2F && l[0] < half + 2.0F && Math.abs(lateral) < 2.5F) {
            // and lower ridges along its sides
            float p = Mth.cos(lateral / 2.5F * Mth.HALF_PI);
            rise = 2.0F * p * p * Terrain.smooth((l[0] - half * 0.2F) / (half * 0.3F));
        } else {
            return;
        }
        int r = Math.round(rise * (0.7F + 0.6F * Terrain.field(seed, x, z, 6.0)));
        if (r < 1) return;
        int ground = Terrain.ground(level, pos, x, z);
        Terrain.clearAbove(level, pos, x, ground, z, 8);
        for (int h = ground + 1; h <= ground + r; h++) {
            float n = Terrain.noise(seed, x, h, z);
            Terrain.set(level, pos, x, h, z, n < 0.45F ? Blocks.GRAVEL.defaultBlockState() : n < 0.8F ? Blocks.COBBLESTONE.defaultBlockState()
                    : n < 0.93F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
        }
    }

    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        // fallen into a crevasse? (well below the ice on every side)
        int px = player.getBlockX(), pz = player.getBlockZ();
        if (thicknessAt(px + 0.5, pz + 0.5) >= 3.0F) {
            int around = Integer.MIN_VALUE;
            for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                around = Math.max(around, level.getHeight(Heightmap.Types.MOTION_BLOCKING, px + d[0], pz + d[1]));
            }
            if (around - player.getY() >= 3.5 && player.onGround()) {
                WeatherAdvancements.award(player, "mind_the_gap");
            }
        }
        calve(level);
        if (moulins && WeatherConfig.CHANGING_LANDFORMS.get()) {
            moulinWater(level, WeatherManager.seasonInfo().season() == Season.SUMMER);
        }
        if (!WeatherConfig.CHANGING_LANDFORMS.get() || WeatherManager.seasonInfo().season() != Season.SUMMER) {
            return;
        }
        // summer: the lower end melts back, and meltwater runs off it
        RandomSource random = level.random;
        float half = length / 2.0F;
        for (int i = 0; i < 6; i++) {
            float u = half - random.nextFloat() * length * 0.25F, v = (random.nextFloat() * 2.0F - 1.0F) * halfWidth(u);
            double c = Mth.cos(angle), s = Mth.sin(angle);
            int x = Mth.floor(cx + u * c - v * s), z = Mth.floor(cz + u * s + v * c);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            BlockState state = level.getBlockState(top);
            boolean ice = state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW);
            // never melt right down to the rock: keep two blocks of ice
            if (ice && isIce(level.getBlockState(top.below())) && isIce(level.getBlockState(top.below(2)))) {
                boolean snout = u > half - length * 0.08F;
                level.setBlock(top, snout && random.nextInt(4) == 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** A glacier that ends in the sea (or a lake) breaks off icebergs at its front now and then. */
    private void calve(ServerLevel level) {
        if (!WeatherConfig.ICEBERGS.get() || level.random.nextInt(150) != 0) return;
        float half = length / 2.0F;
        for (int attempt = 0; attempt < 6; attempt++) {
            float u = half + 2.0F + level.random.nextFloat() * 6.0F, v = (level.random.nextFloat() * 2.0F - 1.0F) * halfWidth(half);
            double c = Mth.cos(angle), s = Mth.sin(angle);
            BlockPos column = BlockPos.containing(cx + u * c - v * s, 0, cz + u * s + v * c);
            if (!level.isLoaded(column)) continue;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            if (!level.getFluidState(surface).is(net.minecraft.tags.FluidTags.WATER) || !level.getFluidState(surface.below(3)).is(net.minecraft.tags.FluidTags.WATER)) continue;
            com.reiko.weather.entity.IceFloeEntity.spawnBerg(level, surface, 1.6F + level.random.nextFloat() * 1.6F);
            level.playSound(null, surface, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.AMBIENT, 4.0F, 0.4F);
            level.playSound(null, surface, com.reiko.weather.registry.ModSounds.AVALANCHE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 3.0F, 0.8F);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(surface.getX(), surface.getY(), surface.getZ()) < 128 * 128) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.reikos_weather.calving")
                            .withStyle(st -> st.withColor(0xBFE6FF)), true);
                }
            }
            return;
        }
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK);
    }
}
