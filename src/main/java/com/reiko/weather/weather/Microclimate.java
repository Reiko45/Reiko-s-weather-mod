package com.reiko.weather.weather;

import com.reiko.weather.config.WeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * How the lie of the land changes the weather. Worked out from the terrain around a spot (cached per
 * chunk and wind direction), on both sides so the server's gameplay matches what clients see. It uses the
 * MOTION_BLOCKING heightmap, the one that clients also receive:
 * <ul>
 *   <li><b>valleys</b> are sheltered from the wind (and collect fog at dawn, on the client)</li>
 *   <li><b>peaks</b> and high ground are windier</li>
 *   <li><b>rain shadow</b>: behind a mountain range, downwind, it rains much less</li>
 *   <li><b>lake-effect snow</b>: cold wind over a big stretch of open water dumps snow downwind of it</li>
 * </ul>
 */
public final class Microclimate {
    public record Info(float valley, float peak, float rainShadow, float lakeEffect) {
        public static final Info NONE = new Info(0, 0, 0, 0);
    }

    private record Cached(Info info, long time, boolean complete) {}

    private static final Map<Level, Map<Long, Cached>> CACHE = new WeakHashMap<>();
    private static final long MAX_AGE = 6000L;
    /** Results worked out while some of the terrain wasn't loaded yet are only trusted briefly. */
    private static final long MAX_AGE_INCOMPLETE = 40L;

    public static boolean enabled() {
        return WeatherConfig.SPEC.isLoaded() && WeatherConfig.MICROCLIMATES.get();
    }

    /** The terrain influences at a column, for wind blowing toward {@code windAngle}. */
    public static Info at(Level level, int x, int z, float windAngle) {
        if (!enabled() || level.dimension() != Level.OVERWORLD) {
            return Info.NONE;
        }
        int octant = Math.floorMod(Math.round(windAngle / (Mth.TWO_PI / 8.0F)), 8);
        long key = ((long) (x >> 4) & 0x3FFFFFL) | ((long) (z >> 4) & 0x3FFFFFL) << 22 | (long) octant << 44;
        long now = level.getGameTime();
        Map<Long, Cached> cache;
        synchronized (CACHE) {
            cache = CACHE.computeIfAbsent(level, l -> new HashMap<>());
            Cached cached = cache.get(key);
            if (cached != null && now >= cached.time() && now - cached.time() < (cached.complete() ? MAX_AGE : MAX_AGE_INCOMPLETE)) {
                return cached.info();
            }
        }
        int[] missing = new int[1];
        Info info = compute(level, (x & ~15) + 8, (z & ~15) + 8, octant * Mth.TWO_PI / 8.0F, missing);
        synchronized (CACHE) {
            if (cache.size() > 4096) cache.clear();
            cache.put(key, new Cached(info, now, missing[0] == 0));
        }
        return info;
    }

    private static Info compute(Level level, int x, int z, float windAngle, int[] missing) {
        int sea = level.getSeaLevel();
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            missing[0]++;
            return Info.NONE;
        }
        int h0 = height(level, x, z, sea, missing);
        // valley or peak: compare with a ring of terrain 48 blocks out
        float ring = 0;
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            ring += height(level, x + Math.round(Mth.cos(a) * 48), z + Math.round(Mth.sin(a) * 48), h0, missing);
        }
        ring /= 12.0F;
        float valley = Mth.clamp((ring - h0 - 6.0F) / 14.0F, 0.0F, 1.0F);
        float peak = Math.max(Mth.clamp((h0 - ring - 10.0F) / 20.0F, 0.0F, 1.0F), Mth.clamp((h0 - (sea + 50.0F)) / 40.0F, 0.0F, 1.0F));

        // look upwind: mountains (rain shadow) and open water (lake-effect snow)
        float ux = -Mth.cos(windAngle), uz = -Mth.sin(windAngle);
        int highest = h0, water = 0, samples = 0;
        for (int d = 16; d <= 192; d += 16) {
            int sx = x + Math.round(ux * d), sz = z + Math.round(uz * d);
            if (!level.isLoaded(new BlockPos(sx, 0, sz))) {
                missing[0]++;
                continue;
            }
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            if (d >= 48) highest = Math.max(highest, h);
            if (d <= 160) {
                samples++;
                if (level.getFluidState(new BlockPos(sx, h - 1, sz)).is(FluidTags.WATER)) water++;
            }
        }
        float rainShadow = highest > sea + 50 ? Mth.clamp((highest - h0 - 30.0F) / 40.0F, 0.0F, 1.0F) : 0.0F;
        float waterFraction = samples == 0 ? 0.0F : water / (float) samples;
        float lake = level.getFluidState(new BlockPos(x, h0 - 1, z)).is(FluidTags.WATER) ? 0.0F
                : Mth.clamp((waterFraction - 0.4F) / 0.4F, 0.0F, 1.0F);
        return new Info(valley, peak, rainShadow, lake);
    }

    private static int height(Level level, int x, int z, int fallback, int[] missing) {
        BlockPos p = new BlockPos(x, 0, z);
        if (level.isLoaded(p)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
        missing[0]++;
        return fallback;
    }

    /** The local weather with the terrain's influence applied. */
    public static LocalWeather apply(Level level, Climate climate, int x, int z, float windAngle, LocalWeather local) {
        Info info = at(level, x, z, windAngle);
        if (info == Info.NONE || info.valley() + info.peak() + info.rainShadow() + info.lakeEffect() <= 0.01F) {
            return local;
        }
        WeatherProfile p = local.profile();
        WeatherProfile.Builder b = WeatherProfile.Builder.from(p);
        b.wind = Mth.clamp(p.wind * (1.0F - 0.4F * info.valley()) * (1.0F + 0.5F * info.peak()) + 0.15F * info.peak(), 0.0F, 1.0F);
        if (info.rainShadow() > 0) {
            float dry = 1.0F - 0.6F * info.rainShadow();
            b.rain = p.rain * dry;
            b.thunder = p.thunder * (1.0F - 0.5F * info.rainShadow());
            b.clouds = p.clouds * (1.0F - 0.4F * info.rainShadow());
            for (int i = 0; i < b.precip.length; i++) b.precip[i] *= dry;
        }
        // a föhn (chinook) wind: strong wind coming down off high ground upwind has dropped its rain on the far side,
        // and warms as it sinks, so it arrives warm and bone-dry, and the snow melts away fast
        float foehn = foehn(info, p);
        if (foehn > 0.0F) {
            b.tempOffset += 9.0F * foehn;
            b.rain *= 1.0F - 0.5F * foehn;
            b.overcast *= 1.0F - 0.6F * foehn;
            b.fog *= 1.0F - 0.7F * foehn;
            b.snowLayers = 0;
        }
        // a frost hollow: on clear, still nights cold air drains downhill and pools in the valley bottom
        float hollow = frostHollow(level, info, p);
        if (hollow > 0.0F) {
            b.tempOffset -= 6.0F * hollow;
        }
        WeatherType type = local.type();
        if (info.lakeEffect() > 0 && climate == Climate.COLD
                && (type.isCalm() || type == WeatherType.WINDY || type == WeatherType.SNOW || type == WeatherType.FOG)) {
            float le = info.lakeEffect();
            b.precip[Precip.SNOW.ordinal()] = Math.max(b.precip[Precip.SNOW.ordinal()], 0.55F * le);
            b.rain = Math.max(b.rain, 0.5F * le);
            b.overcast = Math.max(b.overcast, 0.6F * le);
            b.clouds = Math.max(b.clouds, 0.6F * le);
            if (le > 0.5F) b.snowLayers = Math.max(b.snowLayers, 2);
        }
        return new LocalWeather(type, b.build(), local.intensity(), local.cell(), local.cellWeight());
    }

    /** 0 to 1: how strongly a föhn wind is blowing here (rain shadow plus a strong wind over the mountains). */
    public static float foehn(Info info, WeatherProfile p) {
        if (info.rainShadow() <= 0.3F || p.wind < 0.45F) return 0.0F;
        return Mth.clamp((info.rainShadow() - 0.3F) / 0.4F, 0.0F, 1.0F) * Mth.clamp((p.wind - 0.45F) / 0.25F, 0.0F, 1.0F);
    }

    /** 0 to 1: how much cold air is pooling here (a valley floor on a clear, calm night). */
    public static float frostHollow(Level level, Info info, WeatherProfile p) {
        if (info.valley() <= 0.2F || p.overcast > 0.4F || p.rain > 0.1F || p.wind > 0.35F || p.fog > 0.6F) return 0.0F;
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        float night = Mth.clamp((0.05F - sun) * 5.0F, 0.0F, 1.0F);
        return night * Mth.clamp((info.valley() - 0.2F) / 0.5F, 0.0F, 1.0F) * (1.0F - p.overcast * 2.0F);
    }

    private Microclimate() {}
}
