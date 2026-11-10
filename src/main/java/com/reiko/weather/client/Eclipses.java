package com.reiko.weather.client;

import com.reiko.weather.weather.SkyCalendar;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * Where the moon is against the sun (or in the Earth's shadow) during an eclipse, how much is covered, and how
 * dark that makes the day. Angles are in degrees on the sky.
 */
public final class Eclipses {
    /** The sun's bright disc, and the moon's for each kind of eclipse (a little bigger for a total one, smaller for a ring). */
    static final float SUN = 4.8F;
    /** The full moon's disc, and the Earth's dark shadow (umbra) at the moon's distance. */
    static final float MOON = 5.0F, UMBRA = 13.0F;

    static float moonRadius(SkyCalendar.Eclipse e) {
        return e == SkyCalendar.Eclipse.TOTAL ? 6.4F : e == SkyCalendar.Eclipse.ANNULAR ? 3.9F : 5.4F;
    }

    /**
     * The moon's centre measured from the sun's (or the shadow's from the moon's): {along the sun's path, across it}.
     * In a total eclipse it lingers dead centre for a while, so there's a proper spell of totality.
     */
    static float[] offset(SkyCalendar.Eclipse e, float progress) {
        boolean lunar = e.lunar();
        float reach = lunar ? UMBRA + MOON : SUN + moonRadius(e);
        boolean central = e == SkyCalendar.Eclipse.TOTAL || e == SkyCalendar.Eclipse.ANNULAR || e == SkyCalendar.Eclipse.LUNAR;
        float hold = central ? SkyCalendar.TOTALITY : 0.0F;
        float a = Math.abs(progress);
        float s = a < hold ? 0.0F : Math.signum(progress) * (a - hold) / (1.0F - hold);
        float across = central ? 0.0F : lunar ? UMBRA + MOON * 0.15F : (SUN + moonRadius(e)) * 0.55F;
        // the path is a chord, so the near side passes a little closer than the ends
        return new float[]{s * reach * Mth.sqrt(Math.max(0.0F, 1.0F - (across / reach) * (across / reach))), across};
    }

    /** How much of a disc of radius {@code r} is covered by one of radius {@code cover}, their centres {@code d} apart. */
    static float covered(float d, float r, float cover) {
        if (d >= r + cover) return 0.0F;
        if (d <= Math.abs(cover - r)) return cover >= r ? 1.0F : cover * cover / (r * r);
        double r2 = r * r, c2 = cover * cover;
        double a1 = r2 * Math.acos((d * d + r2 - c2) / (2 * d * r));
        double a2 = c2 * Math.acos((d * d + c2 - r2) / (2 * d * cover));
        double a3 = 0.5 * Math.sqrt(Math.max(0.0, (-d + r + cover) * (d + r - cover) * (d - r + cover) * (d + r + cover)));
        return (float) Mth.clamp((a1 + a2 - a3) / (Math.PI * r2), 0.0, 1.0);
    }

    static SkyCalendar.Eclipse today(ClientLevel level) {
        return com.reiko.weather.network.SyncedWeather.managed() ? SkyCalendar.eclipseToday(level) : SkyCalendar.Eclipse.NONE;
    }

    static float progress(ClientLevel level, SkyCalendar.Eclipse e, float partialTick) {
        return SkyCalendar.eclipseProgress(e, Math.floorMod(level.getDayTime(), 24000L) + partialTick);
    }

    /** How much of the sun (or of the moon, in the Earth's shadow) is covered right now: 0-1. */
    static float coverage(SkyCalendar.Eclipse e, float progress) {
        if (e == SkyCalendar.Eclipse.NONE || Math.abs(progress) >= 1.0F) return 0.0F;
        float[] o = offset(e, progress);
        float d = Mth.sqrt(o[0] * o[0] + o[1] * o[1]);
        return e.lunar() ? covered(d, MOON, UMBRA) : covered(d, SUN, moonRadius(e));
    }

    /**
     * How dark the day goes: hardly at all until the sun is nearly gone, then a deep twilight in seconds once the
     * last sliver vanishes (a ring of sun round the moon still lights the land quite well).
     */
    public static float daylightLoss(ClientLevel level, float partialTick) {
        SkyCalendar.Eclipse e = today(level);
        if (!e.solar()) return 0.0F;
        float c = coverage(e, progress(level, e, partialTick));
        if (c <= 0.0F) return 0.0F;
        float loss = 0.45F * c * c * c * c;
        if (e == SkyCalendar.Eclipse.TOTAL) loss += 0.55F * Mth.clamp((c - 0.985F) / 0.015F, 0.0F, 1.0F);
        return loss;
    }

    private Eclipses() {}
}
