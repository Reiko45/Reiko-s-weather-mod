package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.Season;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

/**
 * A whiting: in a hot summer, the water of a hard-water lake (one fed by springs from limestone) warms up, the algae
 * in it bloom and use up its dissolved carbon dioxide, and the lime comes out of solution as a haze of tiny crystals,
 * turning the whole lake a milky turquoise for a few weeks. Only some lakes do it: the ones over limestone.
 */
public final class LakeWhitings {
    private static float strength;
    private static final int MILK = 0x8CD9CC;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        float target = 0.0F;
        if (level != null && level.dimension() == Level.OVERWORLD && SyncedWeather.managed() && ClientConfig.LAKE_WHITINGS.get()
                && SyncedWeather.season() == Season.SUMMER && ClientWeather.climate() != Climate.COLD) {
            int per = Math.max(1, SyncedWeather.daysPerSeason());
            float t = (SyncedWeather.dayOfSeason() + Math.floorMod(level.getDayTime(), 24000L) / 24000.0F) / per;
            // late in the summer, once the water has warmed right through
            target = Mth.clamp((t - 0.35F) * 4.0F, 0.0F, 1.0F);
        }
        // (in steps, so the chunks only have to be redrawn a few times)
        float stepped = Math.round(target * 4.0F) / 4.0F;
        if (stepped != strength) {
            strength = stepped;
            SeasonalColors.requestRebuild();
        }
    }

    /** The colour of the water at a spot, milky where a whiting is on. */
    public static int water(int color, BlockPos pos) {
        if (strength <= 0.0F) return color;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !hardWater(pos.getX(), pos.getZ())) return color;
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.SWAMP)
                || biome.is(Biomes.MANGROVE_SWAMP) || biome.value().getBaseTemperature() < 0.3F) return color;
        float k = 0.7F * strength;
        int r = (int) Mth.lerp(k, color >> 16 & 255, MILK >> 16 & 255);
        int g = (int) Mth.lerp(k, color >> 8 & 255, MILK >> 8 & 255);
        int b = (int) Mth.lerp(k, color & 255, MILK & 255);
        return r << 16 | g << 8 | b;
    }

    /** Whether the lakes round here are hard-water ones (patches a few hundred blocks across, about one place in four). */
    static boolean hardWater(int x, int z) {
        return com.reiko.weather.landform.Terrain.field(0x5A17B1L, x, z, 160.0) > 0.62F;
    }

    static float strength() {
        return strength;
    }

    public static void reset() {
        strength = 0.0F;
    }

    private LakeWhitings() {}
}
