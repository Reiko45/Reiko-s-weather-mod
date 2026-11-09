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
 * bare lower down. Crevasses cut across it, many of them hidden under a bridge of powder snow. In summer the
 * lower end melts back a little and meltwater runs off it.
 */
public record Glacier(int cx, int cz, float angle, int length, int width, int thickness, long seed) implements Landform {
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
        return new Glacier(c.x(), c.z(), best, 70 + c.random().nextInt(41), 24 + c.random().nextInt(13), 5 + c.random().nextInt(5), c.random().nextLong());
    }

    static Landform load(CompoundTag tag) {
        return new Glacier(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getInt("width"),
                tag.getInt("thick"), tag.getLong("seed"));
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

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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
                int crev = thick >= 3 ? crevasse(l[0], l[1]) : -1;
                boolean wall = crev < 0 && thick >= 3 && (crevasse(l[0] + 1.2F, l[1]) >= 0 || crevasse(l[0] - 1.2F, l[1]) >= 0);
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
