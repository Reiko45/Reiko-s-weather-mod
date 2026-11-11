package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The high country: snow caps on the mountains (the air cools with height, so the snowline creeps down the slopes
 * in autumn and climbs back up in spring), watermelon snow on old summer snowfields, and katabatic winds, the
 * bitter gales of cold, heavy air that pour down off glaciers and ice fields at night.
 */
public final class Highlands {
    /** Where the extra cold of height starts, and how fast it comes on (biome temperature units per block). */
    private static final int LAPSE_FROM = 100;
    private static final float LAPSE = 0.003F;
    private static final int AREA = 40;
    private static final Map<UUID, Float> KATABATIC = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    /** How much colder high ground is than vanilla makes it: only while playing, never in world generation. */
    public static float lapse(BlockPos pos) {
        if (pos.getY() <= LAPSE_FROM || !WeatherConfig.SPEC.isLoaded() || !WeatherConfig.SNOW_CAPS.get()) return 0.0F;
        if (!Seasons.onServerThread() && !Seasons.onClientThread()) return 0.0F;
        return (pos.getY() - LAPSE_FROM) * LAPSE;
    }

    static void tickPlayer(ServerLevel level, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        Season season = WeatherManager.seasonInfo().season();
        if (WeatherConfig.SNOW_ALGAE.get() && season == Season.SUMMER && level.isDay() && random.nextInt(4) == 0) {
            snowAlgae(level, column(level, player, random));
        }
        if (WeatherConfig.KATABATIC_WINDS.get() && player.tickCount % 10 == 0) {
            katabatic(level, player, local);
        }
    }

    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    /** Snow algae colour old, wet summer snow pink, high up where it lies all year. */
    private static void snowAlgae(ServerLevel level, BlockPos top) {
        if (top == null || top.getY() < level.getSeaLevel() + 60 || !level.canSeeSky(top)) return;
        BlockState s = level.getBlockState(top);
        if (s.is(Blocks.SNOW) && s.getValue(SnowLayerBlock.LAYERS) >= 2) {
            level.setBlockAndUpdate(top, ModBlocks.PINK_SNOW.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, s.getValue(SnowLayerBlock.LAYERS)));
        }
    }

    /**
     * A katabatic wind: on a clear night the air over a glacier or ice field chills, grows heavy and slides off
     * down the slope, gathering speed: an icy gale out of a still sky that blows downhill wherever the ice is.
     */
    private static void katabatic(ServerLevel level, ServerPlayer player, LocalWeather local) {
        float strength = 0.0F;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean night = time > 13000L && time < 23500L;
        double gx = 0, gz = 0;
        if (night && local.profile().rain < 0.3F && local.profile().overcast < 0.5F && level.canSeeSky(player.blockPosition())) {
            // the way the land falls round about, and whether there's ice up the slope
            int x = player.getBlockX(), z = player.getBlockZ();
            int e = height(level, x + 8, z), w = height(level, x - 8, z), s = height(level, x, z + 8), n = height(level, x, z - 8);
            gx = (w - e) / 16.0;
            gz = (n - s) / 16.0;
            double slope = Math.sqrt(gx * gx + gz * gz);
            if (slope > 0.2) {
                gx /= slope;
                gz /= slope;
                int ice = 0;
                for (int k = 1; k <= 4; k++) {
                    int ux = Mth.floor(x - gx * 10 * k), uz = Mth.floor(z - gz * 10 * k);
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(ux, 0, uz)).below();
                    if (!level.isLoaded(top)) continue;
                    BlockState b = level.getBlockState(top);
                    if (b.is(BlockTags.ICE) || b.is(Blocks.SNOW_BLOCK) || b.is(Blocks.POWDER_SNOW)) ice++;
                }
                strength = Mth.clamp((ice - 1) / 3.0F, 0.0F, 1.0F) * (float) Mth.clamp(slope / 0.6, 0.0, 1.0);
            }
        }
        float before = KATABATIC.getOrDefault(player.getUUID(), 0.0F);
        float now = Mth.lerp(0.2F, before, strength);
        if (now < 0.02F) KATABATIC.remove(player.getUUID());
        else KATABATIC.put(player.getUUID(), now);
        if (now > 0.25F && !player.isCreative() && !player.isSpectator() && !player.isShiftKeyDown()) {
            player.push(gx * 0.06 * now, 0.0, gz * 0.06 * now);
            player.hurtMarked = true;
        }
        if (now > 0.5F) {
            long last = WARNED.getOrDefault(player.getUUID(), -100000L);
            if (level.getGameTime() - last > 12000) {
                WARNED.put(player.getUUID(), level.getGameTime());
                player.displayClientMessage(Component.translatable("message.reikos_weather.katabatic").withStyle(st -> st.withColor(0xBBDDFF)), true);
                WeatherAdvancements.award(player, "katabatic");
            }
        }
    }

    private static int height(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** How hard a katabatic wind is blowing on a player: up to about 10 degrees of chill on top of everything else. */
    public static float katabaticChill(ServerPlayer player) {
        return 10.0F * KATABATIC.getOrDefault(player.getUUID(), 0.0F);
    }

    public static void reset() {
        KATABATIC.clear();
        WARNED.clear();
    }

    private Highlands() {}
}
