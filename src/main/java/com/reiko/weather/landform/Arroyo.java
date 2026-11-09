package com.reiko.weather.landform;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
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
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * An arroyo (a wash, or wadi): a dry, sandy riverbed winding across the desert, with steep banks cut by old
 * floods. Bone-dry almost all the time, but when a storm breaks over it, a flash flood comes racing down the
 * channel, and drains away again soon after the rain stops.
 */
public record Arroyo(int cx, int cz, float angle, int length, int[] floor, long seed) implements Landform, FloodChannel {
    private static final int CONTROL = 16;
    /** Where each arroyo's flood has got to (upstream end first), by arroyo; and when it started. */
    private static final Long2LongOpenHashMap FLOODS = new Long2LongOpenHashMap();

    @Nullable
    static Landform plan(PlanContext c) {
        int length = 120 + c.random().nextInt(61);
        float start = c.random().nextFloat() * Mth.TWO_PI;
        long seed = c.random().nextLong();
        int ground = c.groundAt(c.x(), c.z());
        if (ground < c.seaLevel() + 3 || !c.fits(ground, 20, 20)) {
            return null;
        }
        // try a few ways across the land and keep the one that runs downhill best (the shallowest cut)
        Arroyo best = null;
        int bestCut = Integer.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            float angle = start + k * Mth.PI / 3.0F;
            int[] floor = new int[CONTROL + 1];
            Arroyo probe = new Arroyo(c.x(), c.z(), angle, length, floor, seed);
            int previous = Integer.MAX_VALUE, cut = 0;
            boolean ok = true;
            int[] heights = new int[CONTROL + 1];
            for (int i = 0; i <= CONTROL && ok; i++) {
                float u = (i / (float) CONTROL - 0.5F) * length;
                double[] p = probe.point(u);
                heights[i] = c.groundAt((int) p[0], (int) p[1]);
                // the middle has to be dry land; the ends can run out to the sea or a lake, like real washes
                if (i >= CONTROL / 4 && i <= CONTROL * 3 / 4 && heights[i] < c.seaLevel() + 3) ok = false;
                int h = Math.max(heights[i] - 3, c.seaLevel() + 1);
                previous = Math.min(previous, h); // never climbing as it goes downstream
                floor[i] = previous;
            }
            if (!ok) continue;
            for (int i = 0; i <= CONTROL; i++) cut = Math.max(cut, heights[i] - floor[i]);
            if (floor[0] - floor[CONTROL] < 2) cut += 6; // a wash that hardly falls at all is a poor one
            if (cut < bestCut) {
                bestCut = cut;
                best = probe;
            }
        }
        // don't cut a canyon through a mountain
        return bestCut <= 16 ? best : null;
    }

    static Landform load(CompoundTag tag) {
        return new Arroyo(tag.getInt("x"), tag.getInt("z"), tag.getFloat("angle"), tag.getInt("length"), tag.getIntArray("floor"), tag.getLong("seed"));
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", cx);
        tag.putInt("z", cz);
        tag.putFloat("angle", angle);
        tag.putInt("length", length);
        tag.put("floor", new IntArrayTag(floor));
        tag.putLong("seed", seed);
        return tag;
    }

    @Override
    public LandformKind kind() {
        return LandformKind.ARROYO;
    }

    @Override
    public BoundingBox bounds() {
        int r = length / 2 + 24;
        int min = Integer.MAX_VALUE;
        for (int f : floor) min = Math.min(min, f);
        return new BoundingBox(cx - r, min - 2, cz - r, cx + r, min + 40, cz + r);
    }

    /** The point on the channel's centre line {@code u} blocks downstream of the middle. */
    @Override
    public double[] point(float u) {
        double c = Mth.cos(angle), s = Mth.sin(angle);
        double v = wiggle(u);
        return new double[]{cx + u * c - v * s, cz + u * s + v * c};
    }

    /** How far the meanders swing the channel to one side. */
    private double wiggle(float u) {
        float p1 = Terrain.hash(seed, 1) * Mth.TWO_PI, p2 = Terrain.hash(seed, 2) * Mth.TWO_PI;
        return 9.0 * Mth.sin(u * 0.045F + p1) + 4.0 * Mth.sin(u * 0.11F + p2);
    }

    @Override
    public float halfWidth(float u) {
        return 2.6F + 1.4F * Mth.sin(u * 0.07F + Terrain.hash(seed, 3) * 6.0F) + Math.max(0.0F, u / length) * 1.5F;
    }

    @Override
    public float floorAt(float u) {
        float t = Mth.clamp((u / length + 0.5F) * CONTROL, 0.0F, CONTROL);
        int i = Math.min(CONTROL - 1, (int) t);
        return Mth.lerp(t - i, floor[i], floor[i + 1]);
    }

    @Override
    public float[] local(double x, double z) {
        double dx = x - cx, dz = z - cz, c = Mth.cos(angle), s = Mth.sin(angle);
        float u = (float) (dx * c + dz * s);
        return new float[]{u, (float) (-dx * s + dz * c - wiggle(u))};
    }

    @Override
    public void build(WorldGenLevel level, BoundingBox area) {
        BoundingBox b = bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        float half = length / 2.0F;
        for (int x = Math.max(area.minX(), b.minX()); x <= Math.min(area.maxX(), b.maxX()); x++) {
            for (int z = Math.max(area.minZ(), b.minZ()); z <= Math.min(area.maxZ(), b.maxZ()); z++) {
                float[] l = local(x + 0.5, z + 0.5);
                float u = l[0], v = Math.abs(l[1]);
                if (Math.abs(u) > half) continue;
                float hw = halfWidth(u);
                // it fades out at both ends: it starts as a gully and spreads out onto the flats
                float ends = Terrain.smooth((half - Math.abs(u)) / 14.0F);
                if (v > hw + 3.0F) continue;
                int ground = Terrain.ground(level, pos, x, z);
                int bed = Math.round(Mth.lerp(ends, ground - 1, floorAt(u)));
                if (v > hw) {
                    // the steep, crumbling bank
                    float t = (v - hw) / 3.0F;
                    int bank = Math.round(Mth.lerp(t * t, bed + 1, ground));
                    if (bank >= ground) continue;
                    Terrain.clearAbove(level, pos, x, ground, z, 8);
                    for (int h = bank + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                    continue;
                }
                if (bed >= ground) continue;
                Terrain.clearAbove(level, pos, x, ground, z, 8);
                for (int h = bed + 1; h <= ground; h++) Terrain.set(level, pos, x, h, z, Blocks.AIR.defaultBlockState());
                var biome = level.getBiome(pos.set(x, bed, z));
                boolean red = biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS);
                float n = Terrain.field(seed, x, z, 5.0);
                BlockState floorBlock = n > 0.72F ? Blocks.GRAVEL.defaultBlockState()
                        : red ? Blocks.RED_SAND.defaultBlockState()
                        : biome.is(net.minecraft.tags.BiomeTags.IS_SAVANNA) ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState();
                Terrain.set(level, pos, x, bed, z, floorBlock);
                // pockets of quicksand where water lingers under the sand after a flood
                if (Terrain.quicksand() && !floorBlock.is(Blocks.GRAVEL) && v < hw - 1.0F && Terrain.field(seed + 9, x, z, 3.0) > 0.85F) {
                    Terrain.set(level, pos, x, bed, z, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                    Terrain.set(level, pos, x, bed - 1, z, com.reiko.weather.registry.ModBlocks.QUICKSAND.get().defaultBlockState());
                    continue;
                }
                Terrain.set(level, pos, x, bed - 1, z, red ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
                // the odd tuft of dead brush caught on the bed
                if (Terrain.noise(seed, x, 7, z) > 0.97F && v < hw - 0.5F) {
                    Terrain.set(level, pos, x, bed + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    private long key() {
        return BlockPos.asLong(cx, 0, cz);
    }

    /** About once a second while a player is near: a downpour here (or upstream) sends a flash flood down the channel. */
    @Override
    public void tick(ServerLevel level, ServerPlayer player) {
        flood(level, player, this, key(), length / 2.0F, bounds(), 1);
    }

    /**
     * Runs a flash flood down a channel: once a real storm has been raining over it for a while, a flood front
     * races down from the top end, {@code depth} blocks deep, and the water stays as long as the rain does.
     */
    static void flood(ServerLevel level, ServerPlayer player, FloodChannel channel, long key, float half, BoundingBox bounds, int depth) {
        if (!WeatherConfig.CHANGING_LANDFORMS.get()) {
            return;
        }
        WeatherState state = WeatherManager.activeState(level);
        if (state == null) {
            return;
        }
        long now = level.getGameTime();
        long flood = FLOODS.getOrDefault(key, Long.MIN_VALUE);
        if (flood == Long.MIN_VALUE) {
            // it takes a real storm, raining for a while, to send a flood down a dry wash
            boolean storm = false;
            for (float u : new float[]{-half * 0.8F, 0.0F}) {
                double[] p = channel.point(u);
                LocalWeather local = WeatherManager.local(level, BlockPos.containing(p[0], channel.floorAt(u) + 1, p[1]));
                if (local != null && local.type().vanillaRain && local.profile().rain > 0.6F && local.intensity() > 0.8F
                        && (local.cell() == null || local.cell().age > 1200)) storm = true;
            }
            if (storm && level.random.nextInt(45) == 0) {
                FLOODS.put(key, now);
                level.playSound(null, player.blockPosition(), com.reiko.weather.registry.ModSounds.AVALANCHE.get(), SoundSource.WEATHER, 2.0F, 0.5F);
                for (ServerPlayer p : level.players()) {
                    if (bounds.isInside(p.blockPosition())) {
                        p.displayClientMessage(Component.translatable("message.reikos_weather.flash_flood").withStyle(s -> s.withColor(0x55AAFF).withBold(true)), true);
                    }
                }
            }
            return;
        }
        // the flood front races downstream, ~10 blocks a second
        float front = -half + (now - flood) / 2.0F;
        float back = front - 12.0F;
        for (float u = Math.max(-half, back); u <= Math.min(half, front); u += 3.0F) {
            double[] p = channel.point(u);
            for (int k = 0; k < depth; k++) {
                BlockPos at = BlockPos.containing(p[0], Math.round(channel.floorAt(u)) + 1 + k, p[1]);
                if (level.isLoaded(at) && level.getBlockState(at).isAir() && (k > 0 || level.getBlockState(at.below()).isSolid())
                        && BlockPos.betweenClosedStream(at.offset(-2, 0, -2), at.offset(2, 0, 2)).noneMatch(q -> level.getFluidState(q).isSource())) {
                    level.setBlock(at, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                    state.flashFlood.add(at.asLong());
                    state.setDirty();
                }
            }
        }
        float[] l = channel.local(player.getX(), player.getZ());
        if (Math.abs(l[1]) < channel.halfWidth(l[0]) + 1.0F && player.isInWater()) {
            WeatherAdvancements.award(player, "flash_flood");
        }
        if (front > half + 20.0F || now - flood > 2400) {
            FLOODS.remove(key); // the flood's passed (the water stays as long as the rain keeps coming)
        }
    }

    /** Flash-flood water drains away fast once the rain stops. */
    public static void drain(ServerLevel level, WeatherState state) {
        if (state.flashFlood.isEmpty() || level.getGameTime() % 4L != 1L) {
            return;
        }
        for (int i = 0; i < 6 && !state.flashFlood.isEmpty(); i++) {
            int index = level.random.nextInt(state.flashFlood.size());
            BlockPos pos = BlockPos.of(state.flashFlood.getLong(index));
            if (!level.isLoaded(pos)) continue;
            LocalWeather local = WeatherManager.local(level, pos);
            if (local != null && local.type().vanillaRain && local.profile().rain > 0.3F && level.random.nextInt(8) != 0) {
                continue; // still raining (it trickles away slowly even so)
            }
            if (level.getFluidState(pos).isSourceOfType(Fluids.WATER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            state.flashFlood.removeLong(index);
            state.setDirty();
        }
    }

    public static void reset() {
        FLOODS.clear();
    }
}
