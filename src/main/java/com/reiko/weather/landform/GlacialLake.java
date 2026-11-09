package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * A glacial lake: meltwater from a retreating glacier, pooled in a high valley behind the moraine the ice left
 * across it, a ridge of rubble it bulldozed up when it reached further than it does now. The water is a milky
 * turquoise from rock flour, stone ground to dust under the ice, which settles in pale silt on the bed and the
 * beaches. Moraine dams are only loose rubble, though, and now and then, in the height of summer, one gives way and
 * the lake bursts out in a sudden flood down the valley below.
 *
 * @param water  the lake's surface
 * @param angle  the way the valley falls, through the dam
 * @param length how far the flood channel runs down the valley below the dam
 * @param floor  the flood channel's bed, at evenly spaced points from the dam down
 */
public record GlacialLake(int cx, int cz, int water, int radius, int depth, float angle, int length, int[] floor, long seed)
        implements Landform, FloodChannel {
    private static final int CONTROL = 8;
    /** When each lake's dam may next give way (it seldom does, and never twice running). */
    private static final Long2LongOpenHashMap COOLDOWNS = new Long2LongOpenHashMap();

    @Nullable
    static Landform plan(PlanContext c) {
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 24 || !c.fits(ground, 16, 34)) {
            return null;
        }
        int radius = 12 + c.random().nextInt(7);
        float[] down = c.downhill(radius);
        if (down[1] < 2) {
            return null; // nowhere for a moraine to dam it
        }
        float angle = down[0];
        // the water stands a block under the lowest point of the rim, leaving out the downhill side the moraine closes
        int rim = Integer.MAX_VALUE, high = Integer.MIN_VALUE, low = Integer.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            float a = i * (float) Math.PI * 2.0F / 12.0F; // the same points downhill() looked at
            int h = c.groundAt(c.x() + (int) Math.round(Math.cos(a) * radius), c.z() + (int) Math.round(Math.sin(a) * radius));
            high = Math.max(high, h);
            low = Math.min(low, h);
            if (Math.abs(Math.IEEEremainder(a - angle, Math.PI * 2.0)) >= 0.9) rim = Math.min(rim, h);
        }
        if (high - low > 18) {
            return null; // a mountainside, not a valley floor
        }
        // (and never far above the valley floor: in a deep hollow it lies sunk below the rim)
        int water = Math.min(rim - 1, ground + 2);
        if (water < c.seaLevel() + 16 || water - (ground - Math.round(down[1])) > 10) {
            return null; // too low, or needing too tall a dam
        }
        int depth = 4 + c.random().nextInt(5);
        long seed = c.random().nextLong();
        // the way the flood would go: down the valley below the dam, never climbing
        int length = Math.min(70, 104 - start(radius));
        int[] floor = new int[CONTROL + 1];
        GlacialLake probe = new GlacialLake(c.x(), c.z(), water, radius, depth, angle, length, floor, seed);
        int previous = water - 2;
        for (int i = 0; i <= CONTROL; i++) {
            double[] p = probe.point((i / (float) CONTROL - 0.5F) * length);
            previous = Math.max(c.seaLevel(), Math.min(previous, c.groundAt(Mth.floor(p[0]), Mth.floor(p[1])) - 1));
            floor[i] = previous;
        }
        return probe;
    }

    /** How far below the lake's middle the flood channel starts: just past the foot of the moraine. */
    private static int start(int radius) {
        return Math.round(radius * 2.3F) + 2;
    }

    static Landform load(CompoundTag tag) {
        return new GlacialLake(tag.getInt("x"), tag.getInt("z"), tag.getInt("water"), tag.getInt("r"), tag.getInt("depth"), tag.getFloat("angle"),
                tag.getInt("length"), tag.getIntArray("floor"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putInt("water", water);
        tag.putInt("r", radius);
        tag.putInt("depth", depth);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("floor", new IntArrayTag(floor));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.GLACIAL_LAKE;
    }

    @Override
    public BoundingBox bounds() {
        int r = Math.round(radius * 2.3F) + 2;
        int minX = cx - r, maxX = cx + r, minZ = cz - r, maxZ = cz + r, low = water - depth - 4;
        for (float u = -length / 2.0F; u <= length / 2.0F; u += 5.0F) {
            double[] p = point(u);
            minX = Math.min(minX, Mth.floor(p[0]) - 7);
            maxX = Math.max(maxX, Mth.floor(p[0]) + 7);
            minZ = Math.min(minZ, Mth.floor(p[1]) - 7);
            maxZ = Math.max(maxZ, Mth.floor(p[1]) + 7);
        }
        for (int f : floor) low = Math.min(low, f - 3);
        return new BoundingBox(minX, low, minZ, maxX, water + 32, maxZ);
    }

    // ---- the lake ----

    /** How far out from the middle of the lake a column is: below 1 is the lake itself. */
    private float lake(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return (float) Math.sqrt(dx * dx + dz * dz) / (radius * (0.9F + 0.2F * Terrain.field(seed, x, z, 9.0)));
    }

    /** How squarely a column lies on the downhill side, where the moraine is (0-1). */
    private float outlet(double x, double z) {
        double a = Math.atan2(z - cz, x - cx);
        return Terrain.smooth((1.25F - (float) Math.abs(Math.IEEEremainder(a - angle, Math.PI * 2.0))) / 0.45F);
    }

    // ---- the flood channel down the valley below the dam ----

    private float wiggle(float u) {
        return 3.0F * Mth.sin(u * 0.06F + Terrain.hash(seed, 1) * 6.0F) * Terrain.smooth((u + length / 2.0F) / 10.0F);
    }

    @Override
    public double[] point(float u) {
        return Terrain.xz(start(radius) + length / 2.0F + u, wiggle(u), cx, cz, angle);
    }

    @Override
    public float floorAt(float u) {
        float t = Mth.clamp((u / length + 0.5F) * CONTROL, 0.0F, CONTROL);
        int i = Math.min(CONTROL - 1, (int) t);
        return Mth.lerp(t - i, floor[i], floor[i + 1]);
    }

    @Override
    public float halfWidth(float u) {
        return 2.5F + 0.8F * Mth.sin(u * 0.08F + Terrain.hash(seed, 2) * 6.0F);
    }

    @Override
    public float[] local(double x, double z) {
        float[] l = Terrain.uv(x, z, cx, cz, angle);
        float u = l[0] - start(radius) - length / 2.0F;
        return new float[]{u, l[1] - wiggle(u)};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        boolean wet = false;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float dl = lake(x + 0.5, z + 0.5), out = outlet(x + 0.5, z + 0.5);
                float reach = 1.6F + 0.5F * out;
                if (dl < reach) {
                    wet |= dl < 1.0F;
                    shore(level, pos, b, x, z, dl, out, reach);
                    continue;
                }
                float[] l = local(x + 0.5, z + 0.5);
                float u = l[0], v = Math.abs(l[1]);
                if (Math.abs(u) > half) continue;
                float hw = halfWidth(u);
                if (v > hw + 2.5F) continue;
                channel(level, pos, b, x, z, u, v, hw);
            }
        }
        // rock flour in the water turns it a milky turquoise
        if (wet) Terrain.setBiome(level, area, water - depth - 2, water + 2, (x, z) -> lake(x + 0.5, z + 0.5) < 1.0F, Terrain.GLACIAL_LAKE_BIOME);
    }

    /** The lake, its pale beaches, and the moraine across its outlet. */
    private void shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float dl, float out, float reach) {
        int ground = Terrain.ground(level, pos, x, z);
        if (ground > b.maxY() - 8) return;
        float n = Terrain.noise(seed, x, water, z);
        if (dl < 1.0F) {
            int bed = water - 1 - Math.round(depth * (float) Math.pow(1.0F - dl * dl, 0.7));
            Terrain.clearAbove(level, pos, x, Math.max(ground, water), z, 8);
            for (int h = water + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
            for (int h = Math.max(b.minY(), Math.min(ground + 1, bed - 1)); h < bed; h++) Terrain.set(level, pos, x, h, z, Blocks.GRAVEL.defaultBlockState());
            // a bed of pale silt and gravel
            Terrain.set(level, pos, x, bed, z, n < 0.5F ? Blocks.CLAY.defaultBlockState() : n < 0.85F ? Blocks.GRAVEL.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
            for (int h = bed + 1; h <= water; h++) Terrain.set(level, pos, x, h, z, Blocks.WATER.defaultBlockState());
            if (dl < 0.8F && n > 0.993F) Terrain.set(level, pos, x, water, z, Blocks.ICE.defaultBlockState()); // a little floe
            return;
        }
        // the shore, never lower than a block over the water (and never cut down more than a few blocks)
        int shore = water + 1;
        if (dl < 1.6F) {
            shore = Math.max(water + 1, Math.max(ground - 6, Math.round(Mth.lerp(Terrain.smooth((dl - 1.0F) / 0.6F), water + 1, ground))));
        } else {
            shore = ground;
        }
        int top = shore;
        boolean dam = false;
        if (out > 0.0F) {
            // the moraine: a ridge of loose rubble across the outlet, its outer face falling away down the valley
            int crest = water + 2 + Math.round((1.0F + 2.0F * Terrain.hash(seed, 3)) * (0.7F + 0.6F * Terrain.field(seed + 3, x, z, 5.0)));
            float ridge = dl < 1.3F ? Mth.lerp(Terrain.smooth((dl - 1.0F) / 0.3F), water + 1, crest)
                    : Mth.lerp(Terrain.smooth((dl - 1.3F) / (reach - 1.3F)), crest, ground);
            top = Math.round(Mth.lerp(out, shore, ridge));
            dam = top > shore || out > 0.5F;
        }
        if (top == ground && !dam && dl > 1.25F || top < b.minY()) return;
        pos.set(x, ground, z);
        BlockState was = level.getBlockState(pos);
        Terrain.clearAbove(level, pos, x, ground, z, Math.max(0, top - ground) + 8);
        for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        for (int h = Math.max(b.minY(), ground + 1); h <= top; h++) Terrain.set(level, pos, x, h, z, rubble(x, h, z));
        if (dam) {
            Terrain.set(level, pos, x, top, z, rubble(x, top, z));
            if (n > 0.95F && dl > 1.15F && dl < 1.45F) Terrain.set(level, pos, x, top + 1, z, n > 0.98F ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        } else if (top <= water + 2 && dl < 1.25F) {
            // a beach of rock flour and gravel at the water's edge
            Terrain.set(level, pos, x, top, z, n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.7F ? Blocks.CLAY.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
        } else if (top < ground) {
            Terrain.set(level, pos, x, top, z, was.isAir() || !was.getFluidState().isEmpty() ? Blocks.GRAVEL.defaultBlockState() : was);
        } else if (top > ground) {
            Terrain.set(level, pos, x, top, z, Blocks.GRAVEL.defaultBlockState());
        }
    }

    /** Moraine: gravel and cobbles with the odd boulder of bare rock. */
    private BlockState rubble(int x, int y, int z) {
        float n = Terrain.noise(seed + 5, x, y, z);
        return n < 0.4F ? Blocks.GRAVEL.defaultBlockState() : n < 0.65F ? Blocks.COBBLESTONE.defaultBlockState() : n < 0.8F ? Blocks.STONE.defaultBlockState()
                : n < 0.9F ? Blocks.ANDESITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    /** The boulder-strewn channel earlier floods have scoured down the valley. */
    private void channel(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox b, int x, int z, float u, float v, float hw) {
        int ground = Terrain.ground(level, pos, x, z);
        if (ground > b.maxY() - 8 || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) return;
        int bed = Math.round(floorAt(u));
        int top = v < hw ? bed : Math.min(ground, Math.round(Mth.lerp((v - hw) / 2.5F, bed + 1, ground)));
        if (v >= hw && top >= ground) return;
        Terrain.clearAbove(level, pos, x, ground, z, 8);
        for (int h = top + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
        for (int h = Math.max(b.minY(), ground + 1); h < top; h++) Terrain.set(level, pos, x, h, z, Blocks.GRAVEL.defaultBlockState());
        float n = Terrain.noise(seed + 7, x, top, z);
        Terrain.set(level, pos, x, top, z, n < 0.5F ? Blocks.GRAVEL.defaultBlockState() : n < 0.75F ? Blocks.COBBLESTONE.defaultBlockState()
                : n < 0.9F ? Blocks.STONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState());
        if (v < hw - 0.5F && n > 0.985F) Terrain.set(level, pos, x, top + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState()); // a boulder the last flood left
    }

    private long key() {
        return BlockPos.asLong(cx, 2, cz);
    }

    /**
     * About once a second while a player is near. Very rarely, in the heat of a summer's day, the meltwater behind the
     * moraine finds a way through it: the dam gives way, and the lake bursts out in a flood down the valley.
     */
    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        long key = key();
        if (Arroyo.flooding(key)) {
            Arroyo.flood(level, player, this, key, length / 2.0F, bounds(), 1);
            return;
        }
        if (!WeatherConfig.GLACIAL_OUTBURSTS.get() || !WeatherConfig.CHANGING_LANDFORMS.get()
                || WeatherManager.seasonInfo().season() != Season.SUMMER || !level.isDay()) {
            return;
        }
        long now = level.getGameTime();
        if (now < COOLDOWNS.getOrDefault(key, Long.MIN_VALUE) || level.random.nextInt(1500) != 0) {
            return;
        }
        COOLDOWNS.put(key, now + 72000L);
        BlockPos notch = burst(level);
        if (notch == null) {
            return;
        }
        Arroyo.startFlood(level, key);
        level.playSound(null, notch, com.reiko.weather.registry.ModSounds.AVALANCHE.get(), SoundSource.WEATHER, 3.0F, 0.6F);
        BoundingBox bounds = bounds();
        for (ServerPlayer p : level.players()) {
            if (bounds.isInside(p.blockPosition())) {
                p.displayClientMessage(Component.translatable("message.reikos_weather.glacial_outburst").withStyle(s -> s.withColor(0xD6F1FF).withBold(true)), true);
                WeatherAdvancements.award(p, "outburst");
            }
        }
    }

    /** Cuts a notch through the moraine down to the water, so the lake pours out through it; null if it's already breached. */
    @Nullable
    private BlockPos burst(ServerLevel level) {
        // find the crest on the way out through the dam
        BlockPos crest = null;
        for (float s = radius * 0.9F; s < radius * 2.2F && crest == null; s += 0.5F) {
            double[] p = Terrain.xz(s, 0.0, cx, cz, angle);
            if (lake(Mth.floor(p[0]) + 0.5, Mth.floor(p[1]) + 0.5) >= 1.3F) crest = BlockPos.containing(p[0], water + 1, p[1]);
        }
        if (crest == null || !level.isLoaded(crest) || level.getBlockState(crest).isAir() || !level.getFluidState(crest).isEmpty()) {
            return null;
        }
        for (float s = radius * 0.85F; s < radius * 2.2F; s += 0.5F) {
            for (float v = -1.0F; v <= 1.0F; v += 1.0F) {
                double[] p = Terrain.xz(s, v, cx, cz, angle);
                int x = Mth.floor(p[0]), z = Mth.floor(p[1]);
                if (!level.isLoaded(new BlockPos(x, water, z))) continue;
                for (int h = water; h <= water + 8; h++) {
                    BlockPos at = new BlockPos(x, h, z);
                    BlockState state = level.getBlockState(at);
                    if (!state.isAir() && state.getFluidState().isEmpty()) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        return crest;
    }
}
