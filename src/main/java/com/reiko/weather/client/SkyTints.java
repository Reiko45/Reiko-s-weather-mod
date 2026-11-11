package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.weather.SeasonalSpells;
import com.reiko.weather.weather.SkyPhenomena;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Two colours the light takes on. Under a big hailstorm the daylight turns an eerie green: sunlight reddened low in
 * the sky shines through a towering cloud full of water and ice, which soaks up the red and passes the blue-green.
 * In an Indian summer the still, warm air fills with a soft golden haze.
 */
public final class SkyTints {
    private static float green, golden;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !SyncedWeather.managed()) {
            green = golden = 0.0F;
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float sun = SkyPhenomena.sunHeight(level, 1.0F);
        float target = 0.0F;
        if (ClientConfig.GREEN_STORM_SKIES.get() && sun > 0.0F) {
            for (StormCell cell : SyncedWeather.cells()) {
                if (cell.type != WeatherType.HAIL && cell.type != WeatherType.SUPERCELL) continue;
                // strongest under the core, and best in the afternoon light
                target = Math.max(target, cell.weightAt(cam.x, cam.z)); // (already scaled by how far the storm has grown)
            }
            target *= Mth.clamp(sun * 4.0F, 0.0F, 1.0F) * (0.75F + 0.25F * Mth.clamp((0.7F - sun) * 2.0F, 0.0F, 1.0F));
        }
        green = Mth.lerp(0.01F, green, target);
        float haze = 0.0F;
        if (ClientConfig.GOLDEN_HAZE.get() && sun > -0.05F) {
            haze = SeasonalSpells.indianSummer(level) * Mth.clamp(1.0F - ClientWeather.overcast() * 2.0F, 0.0F, 1.0F) * Mth.clamp((sun + 0.05F) * 6.0F, 0.0F, 1.0F);
        }
        golden = Mth.lerp(0.005F, golden, haze);
    }

    /** How green the storm light is, 0 to 1. */
    public static float green() {
        return green;
    }

    /** How super tonight's full moon is, 0 to 1 (bigger and brighter). */
    public static float supermoon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.SPEC.isLoaded() || !ClientConfig.SUPERMOON_LOOK.get() || !SyncedWeather.managed()) return 0.0F;
        return com.reiko.weather.weather.Supermoons.strength(mc.level);
    }

    /** How strong the golden Indian-summer haze is, 0 to 1. */
    public static float golden() {
        return golden;
    }

    public static void reset() {
        green = golden = 0.0F;
    }

    private SkyTints() {}
}
