package com.reiko.weather.weather;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The weather actually experienced at one spot: the background weather, possibly overridden by a
 * storm cell passing overhead.
 *
 * @param type       the dominant weather (the cell's once you're past its fuzzy edge)
 * @param profile    the blended profile; drives visuals and continuous effects
 * @param intensity  0 to 1, how established the dominant weather is here
 * @param cell       the storm cell influencing this spot, if any
 * @param cellWeight how strongly that cell applies (0 at its edge, 1 in its core)
 */
public record LocalWeather(WeatherType type, WeatherProfile profile, float intensity, @Nullable StormCell cell, float cellWeight) {

    public static LocalWeather resolve(WeatherType background, @Nullable CustomWeather custom, float backgroundIntensity,
                                       List<StormCell> cells, Climate climate, double x, double z) {
        StormCell best = null;
        float bestWeight = 0.0F, bestRank = 0.0F;
        for (StormCell cell : cells) {
            float w = cell.weightAt(x, z);
            // a dust haze is high up: any real weather underneath it takes over (and rain falling through it comes down dusty)
            float rank = cell.type == WeatherType.DUST_HAZE || w <= 0.3F ? w : w + 1.0F;
            if (rank > bestRank) {
                best = cell;
                bestWeight = w;
                bestRank = rank;
            }
        }
        WeatherProfile backgroundProfile = CustomWeathers.backgroundProfile(background, custom, climate);
        if (best == null) {
            return new LocalWeather(background, backgroundProfile, backgroundIntensity, null, 0.0F);
        }
        WeatherProfile blended = WeatherProfile.blend(backgroundProfile, WeatherProfiles.get(best.type, climate), bestWeight);
        if (best.type == WeatherType.DUST_HAZE && background.vanillaRain) {
            // the haze is high up: the rain keeps falling underneath it (and brings the dust down with it)
            WeatherProfile.Builder b = WeatherProfile.Builder.from(blended);
            b.rain = Math.max(b.rain, backgroundProfile.rain);
            b.thunder = Math.max(b.thunder, backgroundProfile.thunder);
            b.cropBoost = backgroundProfile.cropBoost;
            b.puddles = backgroundProfile.puddles;
            b.floods = backgroundProfile.floods;
            b.dustLayers = 0;
            for (Precip p : Precip.values()) {
                b.precip[p.ordinal()] = Math.max(b.precip[p.ordinal()], backgroundProfile.rateOf(p));
            }
            return new LocalWeather(background, b.build(), backgroundIntensity, best, bestWeight);
        }
        boolean cellDominant = bestWeight >= 0.5F;
        return new LocalWeather(cellDominant ? best.type : background, blended,
                cellDominant ? bestWeight : backgroundIntensity, best, bestWeight);
    }

    /** The nearest cell that is not overhead, for "storm approaching" reports. */
    @Nullable
    public static StormCell nearestAround(List<StormCell> cells, double x, double z, double maxDistance) {
        StormCell nearest = null;
        double best = maxDistance;
        for (StormCell cell : cells) {
            double edge = cell.distanceTo(x, z) - cell.radius;
            if (edge > 0 && edge < best) {
                best = edge;
                nearest = cell;
            }
        }
        return nearest;
    }
}
