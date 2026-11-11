package com.reiko.weather.client;

import com.reiko.weather.weather.SkyCalendar;
import com.reiko.weather.weather.SkyPhenomena;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.weather.WeatherType;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws rainbows, auroras, halos, sun dogs, sunbeams and the blood moon onto the sky dome, right after vanilla's sky. */
public final class SkyEffectsRenderer {
    private static final float DOME = 100.0F;

    /** Rainbow colors from the inside (violet) to the outside (red). */
    private static final float[][] RAINBOW = {
            {0.55F, 0.20F, 0.85F},
            {0.25F, 0.30F, 1.00F},
            {0.15F, 0.75F, 1.00F},
            {0.20F, 0.95F, 0.30F},
            {1.00F, 0.95F, 0.20F},
            {1.00F, 0.55F, 0.10F},
            {1.00F, 0.15F, 0.15F},
    };

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float rainbow = ClientWeather.rainbow();
        float aurora = ClientWeather.aurora();
        float halo = ClientWeather.halo();
        float blood = ClientWeather.bloodMoon();
        float smoke = ClientWeather.smoke(), dust = ClientWeather.diamondDust();
        float nlc = SkySights.noctilucent(), moonbow = SkySights.moonbow(), fogbow = SkySights.fogbow();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        float clearSky = (1.0F - level.getRainLevel(partialTick) * (1.0F - 0.9F * ClientWeather.sunShower()))
                * (1.0F - Math.min(1.0F, ClientWeather.fogDensity() * 1.5F));
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD && com.reiko.weather.network.SyncedWeather.managed();
        float overcast = ClientWeather.overcast();
        // twilight: Earth's shadow rising opposite the sun, with the pink belt above it
        float twilight = overworld && ClientConfig.TWILIGHT_BANDS.get()
                ? clearSky * (1.0F - overcast) * Mth.clamp(1.0F - Math.abs(sun.y + 0.05F) / 0.1F, 0.0F, 1.0F) : 0.0F;
        // iridescence: thin cloud near a high sun shows pastel colours
        float iridescent = overworld && ClientConfig.IRIDESCENT_CLOUDS.get() && sun.y > 0.1F
                ? Mth.clamp(1.0F - Math.abs(overcast - 0.3F) / 0.22F, 0.0F, 1.0F) * (1.0F - ClientWeather.rain()) : 0.0F;
        // the green flash: the last sliver of the sun over a clear sea horizon
        float flash = overworld && ClientConfig.GREEN_FLASH.get() && SkySights.seaHorizon() && overcast < 0.2F
                ? clearSky * Mth.clamp(1.0F - Math.abs(sun.y + 0.002F) / 0.006F, 0.0F, 1.0F) : 0.0F;
        // the Milky Way and zodiacal light: dark, clear, moonless nights well away from villages' lights
        int phase = level.getMoonPhase();
        float moonlight = phase == 4 ? 0.0F : phase == 3 || phase == 5 ? 0.25F : phase == 2 || phase == 6 ? 0.55F : phase == 0 ? 1.0F : 0.8F;
        float moonUp = Mth.clamp(-sun.y * 3.0F + 0.2F, 0.0F, 1.0F); // the moon is opposite the sun
        float dark = clearSky * (1.0F - overcast) * (1.0F - AirAndWater.lightPollution()) * (1.0F - 0.85F * moonlight * moonUp);
        float milky = overworld && ClientConfig.MILKY_WAY.get() ? dark * Mth.clamp((-sun.y - 0.2F) * 5.0F, 0.0F, 1.0F) : 0.0F;
        float zodiacal = overworld && ClientConfig.MILKY_WAY.get() ? dark * Mth.clamp(1.0F - Math.abs(sun.y + 0.3F) / 0.1F, 0.0F, 1.0F) : 0.0F;
        // the circumhorizontal arc under a high sun, the circumzenithal arc over a low one, in thin ice cloud
        float iceCloud = Math.max(halo, Mth.clamp(1.0F - Math.abs(overcast - 0.3F) / 0.2F, 0.0F, 1.0F) * (1.0F - ClientWeather.rain()));
        float cha = overworld && ClientConfig.SKY_ARCS.get() && sun.y > 0.85F ? iceCloud * Mth.clamp((sun.y - 0.85F) * 20.0F, 0.0F, 1.0F) : 0.0F;
        float cza = overworld && ClientConfig.SKY_ARCS.get() && sun.y > 0.03F && sun.y < 0.53F
                ? iceCloud * Mth.clamp((0.53F - sun.y) * 8.0F, 0.0F, 1.0F) * Mth.clamp((sun.y - 0.03F) * 15.0F, 0.0F, 1.0F) : 0.0F;
        // nacreous clouds: polar winter twilight, when the stratosphere is bitterly cold
        float nacreous = overworld && ClientConfig.NACREOUS_CLOUDS.get() && ClientWeather.climate() == com.reiko.weather.weather.Climate.COLD
                && com.reiko.weather.network.SyncedWeather.season() == com.reiko.weather.weather.Season.WINTER
                ? clearSky * (1.0F - overcast) * Mth.clamp(1.0F - Math.abs(sun.y + 0.07F) / 0.08F, 0.0F, 1.0F) : 0.0F;
        float redSky = overworld && ClientConfig.RED_SKY.get() ? redSky(level, sun) * clearSky : 0.0F;
        // eclipses, comets and airglow
        SkyCalendar.Eclipse eclipse = overworld ? Eclipses.today(level) : SkyCalendar.Eclipse.NONE;
        float eclipseProgress = Eclipses.progress(level, eclipse, partialTick);
        boolean eclipsing = eclipse != SkyCalendar.Eclipse.NONE && Math.abs(eclipseProgress) < 1.0F;
        long day = level.getDayTime() / 24000L;
        float comet = overworld ? SkyCalendar.cometBrightness(day) * Mth.clamp((-sun.y - 0.02F) * 7.0F, 0.0F, 1.0F) * clearSky
                * (1.0F - overcast) * (1.0F - 0.5F * AirAndWater.lightPollution()) : 0.0F;
        float airglow = overworld && ClientConfig.AIRGLOW.get() ? dark * Mth.clamp((-sun.y - 0.25F) * 5.0F, 0.0F, 1.0F) : 0.0F;
        // STEVE on geomagnetic storm nights (the first part of the night), the gegenschein on the darkest ones, mirages over a cold sea
        long nightTime = Math.floorMod(level.getDayTime(), 24000L);
        float steve = overworld && ClientConfig.STEVE.get() && SkyPhenomena.isGeomagneticStormNight(level.getDayTime()) && nightTime > 13800L && nightTime < 17500L
                ? clearSky * (1.0F - overcast) * Mth.clamp((-sun.y - 0.2F) * 4.0F, 0.0F, 1.0F) * Mth.clamp(Math.min(nightTime - 13800L, 17500L - nightTime) / 600.0F, 0.0F, 1.0F) : 0.0F;
        float gegenschein = overworld && ClientConfig.GEGENSCHEIN.get() ? dark * Mth.clamp((-sun.y - 0.5F) * 3.0F, 0.0F, 1.0F) : 0.0F;
        float fata = overworld ? SkySights.fataMorgana() : 0.0F;
        // planets after dusk and before dawn (Venus shows in the twilight), earthshine on a crescent moon,
        // an orange moon low in the sky, and rainbows in the spray off a rough sea
        float sunDown = Mth.clamp((0.02F - sun.y) * 8.0F, 0.0F, 1.0F);
        float planets = overworld && ClientConfig.PLANETS.get() ? clearSky * (1.0F - overcast) * sunDown : 0.0F;
        Vector3f moonDir = new Vector3f(sun).negate();
        float earthshine = overworld && ClientConfig.EARTHSHINE.get() && moonDir.y > 0.0F
                ? clearSky * (1.0F - overcast) * (phase == 3 || phase == 5 ? 1.0F : phase == 2 || phase == 6 ? 0.45F : 0.0F)
                * Mth.clamp((-sun.y - 0.05F) * 5.0F, 0.0F, 1.0F) : 0.0F;
        boolean harvest = com.reiko.weather.network.SyncedWeather.season() == com.reiko.weather.weather.Season.AUTUMN && phase == 0;
        float moonTint = overworld && ClientConfig.HARVEST_MOON.get() && phase != 4 && moonDir.y > -0.05F
                ? clearSky * Mth.clamp(1.0F - moonDir.y / (harvest ? 0.6F : 0.35F), 0.0F, 1.0F) * (harvest ? 1.0F : 0.65F) : 0.0F;
        // the high ice clouds: lit white by day, and catching the sunset's colours after the ground is in shadow
        float highLit = overworld ? clearSky * Mth.clamp((sun.y + 0.12F) * 4.0F, 0.0F, 1.0F) : 0.0F;
        float cirrus = HighClouds.cirrus() * highLit, mackerel = HighClouds.mackerel() * highLit, veil = HighClouds.veil() * highLit;
        float lowSun = Mth.clamp(1.0F - sun.y / 0.25F, 0.0F, 1.0F);
        float[] highTint = {1.0F, Mth.lerp(lowSun, 0.97F, 0.55F), Mth.lerp(lowSun, 1.0F, 0.47F)};
        // a supermoon: bigger (see LevelRendererMixin) and brighter, with a broader glow round it
        float supermoon = overworld && moonDir.y > -0.05F ? SkyTints.supermoon() * clearSky * (1.0F - overcast) : 0.0F;
        float sprayBow = overworld && ClientConfig.SPRAY_BOWS.get() && sun.y > 0.02F && sun.y < 0.65F
                ? clearSky * (1.0F - overcast) * Mth.clamp((RoughSeas.roughness() - 0.2F) * 2.5F, 0.0F, 1.0F) : 0.0F;
        // the Chinook arch over the mountains upwind, in a föhn
        float chinook = overworld && ClientConfig.CHINOOK_ARCH.get() ? StormSkies.chinook() : 0.0F;
        if (!eclipsing && cirrus <= 0.005F && mackerel <= 0.005F && veil <= 0.005F && supermoon <= 0.005F && chinook <= 0.005F && planets <= 0.005F && earthshine <= 0.005F && moonTint <= 0.005F && sprayBow <= 0.005F && comet <= 0.005F && airglow <= 0.005F && rainbow <= 0.005F && steve <= 0.005F && gegenschein <= 0.005F && fata <= 0.005F && aurora <= 0.005F && halo <= 0.005F && blood <= 0.005F && smoke <= 0.005F && dust <= 0.005F && !ShootingStars.any()
                && nlc <= 0.005F && moonbow <= 0.005F && fogbow <= 0.005F && twilight <= 0.005F && iridescent <= 0.005F && flash <= 0.005F
                && milky <= 0.005F && zodiacal <= 0.005F && cha <= 0.005F && cza <= 0.005F && nacreous <= 0.005F && redSky <= 0.005F) {
            return;
        }

        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (eclipsing && eclipse.solar() && sun.y > -0.1F) {
            float sunAlpha = 1.0F - Math.max(level.getRainLevel(partialTick) * (1.0F - 0.9F * ClientWeather.sunShower()), ClientWeather.sunOcclusion());
            drawSolarEclipse(pose, sun, eclipse, eclipseProgress, sunAlpha);
        } else if (eclipsing && eclipse.lunar()) {
            drawLunarEclipse(pose, new Vector3f(sun).negate(), eclipse, eclipseProgress);
        }
        if (chinook > 0.005F) {
            // a grey deck of cloud, so blended normally rather than added as light
            RenderSystem.defaultBlendFunc();
            drawChinookArch(pose, StormSkies.chinookAzimuth(), level.getSkyDarken(partialTick), chinook);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        if (airglow > 0.005F) {
            drawAirglow(pose, airglow);
        }
        if (gegenschein > 0.005F) {
            drawGegenschein(pose, sun, gegenschein);
        }
        if (steve > 0.005F) {
            drawSteve(pose, level.getGameTime() + partialTick, steve);
        }
        if (fata > 0.005F) {
            drawFataMorgana(pose, level.getGameTime() + partialTick, fata);
        }
        if (moonTint > 0.005F && !(eclipsing && eclipse.lunar())) {
            drawMoonTint(pose, moonDir, moonTint, harvest);
        }
        if (earthshine > 0.005F && !(eclipsing && eclipse.lunar())) {
            drawEarthshine(pose, moonDir, earthshine);
        }
        if (planets > 0.005F) {
            drawPlanets(pose, theta, level.getDayTime() + partialTick, sun, planets);
        }
        if (sprayBow > 0.005F) {
            // blended normally: added light can't show colour against the bright sky low over the sea
            RenderSystem.defaultBlendFunc();
            drawSprayBow(pose, sun, sprayBow);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        if (comet > 0.005F) {
            drawComet(pose, sun, theta, SkyCalendar.cometShape(day), comet);
        }

        if (rainbow > 0.005F) {
            // the rainbow sits 42 degrees from the anti-solar point, so it needs a low sun
            float alpha = rainbow * clearSky * 0.55F
                    * Mth.clamp((0.68F - sun.y) * 4.0F, 0.0F, 1.0F)
                    * Mth.clamp((sun.y + 0.02F) * 10.0F, 0.0F, 1.0F);
            if (alpha > 0.005F) {
                drawRainbow(pose, sun, alpha);
            }
        }
        if (moonbow > 0.005F) {
            // the same bow, opposite a low moon: faint, and almost white to the eye
            Vector3f moon = new Vector3f(sun).negate();
            float alpha = moonbow * clearSky * 0.22F * Mth.clamp((0.62F - moon.y) * 4.0F, 0.0F, 1.0F) * Mth.clamp((moon.y + 0.02F) * 10.0F, 0.0F, 1.0F);
            if (alpha > 0.005F) {
                drawBow(pose, moon, 39.5F, 42.5F, 0.7F, alpha);
            }
        }
        if (fogbow > 0.005F) {
            // a fogbow: the droplets are so small the colours smear into one broad white band
            float alpha = fogbow * 0.3F * Mth.clamp((0.6F - sun.y) * 4.0F, 0.0F, 1.0F) * Mth.clamp(sun.y * 10.0F, 0.0F, 1.0F);
            if (alpha > 0.005F) {
                drawBow(pose, sun, 33.0F, 42.0F, 0.85F, alpha);
            }
        }
        if (nlc > 0.005F) {
            drawNoctilucent(pose, level.getGameTime() + partialTick, nlc * 0.5F);
        }
        if (veil > 0.005F) {
            drawVeil(pose, highTint, veil * 0.16F);
        }
        if (mackerel > 0.005F) {
            drawMackerel(pose, level.getGameTime() + partialTick, HighClouds.windAzimuth(), highTint, mackerel);
        }
        if (cirrus > 0.005F) {
            drawCirrus(pose, level.getGameTime() + partialTick, HighClouds.windAzimuth(), highTint, cirrus);
        }
        if (supermoon > 0.005F && !(eclipsing && eclipse.lunar())) {
            Vector3f[] around = basis(moonDir);
            drawGlow(pose, new Vector3f(moonDir).normalize(), around[0], around[1], 0.0F, 9.0F, new float[]{0.85F, 0.9F, 1.0F}, 0.12F * supermoon);
        }
        if (milky > 0.005F) {
            drawMilkyWay(pose, theta, milky);
        }
        if (zodiacal > 0.005F) {
            drawZodiacal(pose, theta, zodiacal);
        }
        if (cha > 0.005F) {
            drawArc(pose, sun, (float) Math.asin(Mth.clamp(sun.y, -1.0F, 1.0F)) - 46.0F * Mth.DEG_TO_RAD, 55.0F, false, cha * 0.35F);
        }
        if (cza > 0.005F) {
            drawArc(pose, sun, (float) Math.asin(Mth.clamp(sun.y, -1.0F, 1.0F)) + 46.0F * Mth.DEG_TO_RAD, 45.0F, true, cza * 0.3F);
        }
        if (nacreous > 0.005F) {
            drawNacreous(pose, sun, level.getGameTime() + partialTick, nacreous);
        }
        if (redSky > 0.005F) {
            drawRedSky(pose, sun, redSky);
        }
        if (twilight > 0.005F) {
            drawTwilightBands(pose, sun, twilight);
        }
        if (iridescent > 0.005F && sun.y > 0.0F) {
            drawIridescence(pose, sun, level.getGameTime() + partialTick, iridescent);
        }
        if (flash > 0.005F) {
            drawGreenFlash(pose, sun, flash);
        }
        if (aurora > 0.005F) {
            float night = Mth.clamp((-sun.y - 0.05F) * 4.0F, 0.0F, 1.0F);
            float alpha = aurora * night * clearSky;
            if (alpha > 0.005F) {
                drawAurora(pose, level.getGameTime() + partialTick, alpha);
            }
        }

        float night = Mth.clamp((-sun.y - 0.1F) * 4.0F, 0.0F, 1.0F);
        ShootingStars.render(pose, partialTick, night * clearSky);

        if (halo > 0.005F) {
            // around the sun by day, the (fainter) moon by night
            Vector3f moon = new Vector3f(sun).negate();
            if (sun.y > -0.05F) {
                drawHalo(pose, sun, halo * 0.22F * Mth.clamp((sun.y + 0.05F) * 6.0F, 0.0F, 1.0F));
                if (sun.y < 0.6F) {
                    float dogs = halo * Mth.clamp((0.6F - sun.y) * 4.0F, 0.0F, 1.0F) * Mth.clamp((sun.y + 0.05F) * 8.0F, 0.0F, 1.0F);
                    drawSunDogs(pose, sun, dogs);
                }
            }
            if (moon.y > -0.05F) {
                drawHalo(pose, moon, halo * 0.12F * Mth.clamp((moon.y + 0.05F) * 6.0F, 0.0F, 1.0F) * night);
            }
        }
        if (blood > 0.005F) {
            drawBloodMoon(pose, new Vector3f(sun).negate(), blood);
        }
        if (dust > 0.005F && sun.y > -0.05F && sun.y < 0.4F) {
            drawSunPillar(pose, sun, dust * Mth.clamp((0.4F - sun.y) * 5.0F, 0.0F, 1.0F) * Mth.clamp((sun.y + 0.05F) * 10.0F, 0.0F, 1.0F));
        }
        if (smoke > 0.005F && sun.y > -0.1F) {
            drawRedSun(pose, sun, smoke);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void drawRainbow(Matrix4f pose, Vector3f sun, float alpha) {
        Vector3f anti = new Vector3f(sun).negate().normalize();
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f u = new Vector3f(up).sub(new Vector3f(anti).mul(anti.dot(up)));
        if (u.lengthSquared() < 1.0E-5F) {
            u.set(1.0F, 0.0F, 0.0F);
        }
        u.normalize();
        Vector3f v = new Vector3f(anti).cross(u).normalize();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 96;
        float inner = 39.5F * Mth.DEG_TO_RAD;
        float outer = 42.5F * Mth.DEG_TO_RAD;
        int bands = RAINBOW.length + 1;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI;
            float phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int b = 0; b < bands; b++) {
                float rho0 = Mth.lerp(b / (float) bands, inner, outer);
                float rho1 = Mth.lerp((b + 1) / (float) bands, inner, outer);
                Vector3f p00 = ringPoint(anti, u, v, phi0, rho0);
                Vector3f p10 = ringPoint(anti, u, v, phi1, rho0);
                Vector3f p11 = ringPoint(anti, u, v, phi1, rho1);
                Vector3f p01 = ringPoint(anti, u, v, phi0, rho1);
                if (p00.y < -0.03F && p10.y < -0.03F) {
                    continue; // below the horizon
                }
                float[] c0 = bandColor(b - 1);
                float[] c1 = bandColor(b);
                float a0 = b == 0 ? 0.0F : alpha;
                float a1 = b == bands - 1 ? 0.0F : alpha;
                vertex(buffer, pose, p00, c0, a0 * horizonFade(p00));
                vertex(buffer, pose, p10, c0, a0 * horizonFade(p10));
                vertex(buffer, pose, p11, c1, a1 * horizonFade(p11));
                vertex(buffer, pose, p01, c1, a1 * horizonFade(p01));
            }
        }
        draw(buffer);
        if (ClientConfig.DOUBLE_RAINBOWS.get()) {
            // the secondary bow: fainter, wider, its colours the other way round (light reflected twice inside the drops)
            drawBand(pose, anti, u, v, 50.5F, 53.8F, alpha * 0.4F, true);
            // the sky inside the primary bow is a little brighter...
            drawGlow(pose, anti, u, v, 0.0F, 39.5F, new float[]{1.0F, 1.0F, 1.0F}, alpha * 0.06F);
            // ...and between the two bows, darker: Alexander's dark band
            RenderSystem.defaultBlendFunc();
            drawGlow(pose, anti, u, v, 42.5F, 50.5F, new float[]{0.08F, 0.1F, 0.16F}, alpha * 0.22F);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
    }

    /** A rainbow band between two angles round the anti-solar point; {@code reversed} puts red on the inside. */
    private static void drawBand(Matrix4f pose, Vector3f anti, Vector3f u, Vector3f v, float innerDeg, float outerDeg, float alpha, boolean reversed) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 96, bands = RAINBOW.length + 1;
        float inner = innerDeg * Mth.DEG_TO_RAD, outer = outerDeg * Mth.DEG_TO_RAD;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI, phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int b = 0; b < bands; b++) {
                float rho0 = Mth.lerp(b / (float) bands, inner, outer), rho1 = Mth.lerp((b + 1) / (float) bands, inner, outer);
                Vector3f p00 = ringPoint(anti, u, v, phi0, rho0), p10 = ringPoint(anti, u, v, phi1, rho0);
                Vector3f p11 = ringPoint(anti, u, v, phi1, rho1), p01 = ringPoint(anti, u, v, phi0, rho1);
                if (p00.y < -0.03F && p10.y < -0.03F) continue;
                float[] c0 = bandColor(reversed ? RAINBOW.length - b : b - 1), c1 = bandColor(reversed ? RAINBOW.length - 1 - b : b);
                float a0 = b == 0 ? 0.0F : alpha, a1 = b == bands - 1 ? 0.0F : alpha;
                vertex(buffer, pose, p00, c0, a0 * horizonFade(p00));
                vertex(buffer, pose, p10, c0, a0 * horizonFade(p10));
                vertex(buffer, pose, p11, c1, a1 * horizonFade(p11));
                vertex(buffer, pose, p01, c1, a1 * horizonFade(p01));
            }
        }
        draw(buffer);
    }

    /** An even wash of colour over a ring of sky round a point, fading at its edges. */
    private static void drawGlow(Matrix4f pose, Vector3f center, Vector3f u, Vector3f v, float innerDeg, float outerDeg, float[] color, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 64, rings = 4;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI, phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int r = 0; r < rings; r++) {
                float t0 = r / (float) rings, t1 = (r + 1) / (float) rings;
                float rho0 = Mth.lerp(t0, innerDeg, outerDeg) * Mth.DEG_TO_RAD, rho1 = Mth.lerp(t1, innerDeg, outerDeg) * Mth.DEG_TO_RAD;
                // a filled disc keeps its middle; a ring fades in and out at both edges
                float a0 = innerDeg == 0.0F ? alpha * (1.0F - t0 * t0) : alpha * Mth.sin(t0 * Mth.PI);
                float a1 = innerDeg == 0.0F ? alpha * (1.0F - t1 * t1) : alpha * Mth.sin(t1 * Mth.PI);
                Vector3f p00 = ringPoint(center, u, v, phi0, rho0), p10 = ringPoint(center, u, v, phi1, rho0);
                Vector3f p11 = ringPoint(center, u, v, phi1, rho1), p01 = ringPoint(center, u, v, phi0, rho1);
                vertex(buffer, pose, p00, color, a0 * horizonFade(p00));
                vertex(buffer, pose, p10, color, a0 * horizonFade(p10));
                vertex(buffer, pose, p11, color, a1 * horizonFade(p11));
                vertex(buffer, pose, p01, color, a1 * horizonFade(p01));
            }
        }
        draw(buffer);
    }

    /**
     * After sunset (and before sunrise), opposite the sun: the dark blue-grey shadow of the Earth itself, rising
     * from the horizon, under a band of pink (the Belt of Venus) still lit by the low sun.
     */
    private static void drawTwilightBands(Matrix4f pose, Vector3f sun, float strength) {
        float antiAz = (float) Math.atan2(-sun.x, sun.z); // azimuth of the point opposite the sun (sky() measures from -Z)
        float rise = Mth.clamp(-sun.y * 40.0F, 0.0F, 6.0F); // the shadow climbs as the sun sinks
        int steps = 40;
        float span = 1.6F;
        BufferBuilder shadow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] dark = {0.18F, 0.22F, 0.34F}, pink = {1.0F, 0.55F, 0.62F};
        float top = (2.0F + rise) * Mth.DEG_TO_RAD, beltTop = (9.0F + rise) * Mth.DEG_TO_RAD;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
            float a0 = antiAz + (t0 - 0.5F) * span * 2.0F, a1 = antiAz + (t1 - 0.5F) * span * 2.0F;
            float e0 = Mth.sin(t0 * Mth.PI), e1 = Mth.sin(t1 * Mth.PI), sa = strength * 0.35F;
            vertexSky(shadow, pose, a0, 0.0F, dark, sa * e0);
            vertexSky(shadow, pose, a1, 0.0F, dark, sa * e1);
            vertexSky(shadow, pose, a1, top, dark, sa * e1 * 0.6F);
            vertexSky(shadow, pose, a0, top, dark, sa * e0 * 0.6F);
        }
        RenderSystem.defaultBlendFunc();
        draw(shadow);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder belt = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
            float a0 = antiAz + (t0 - 0.5F) * span * 2.0F, a1 = antiAz + (t1 - 0.5F) * span * 2.0F;
            float e0 = Mth.sin(t0 * Mth.PI), e1 = Mth.sin(t1 * Mth.PI), ba = strength * 0.22F;
            vertexSky(belt, pose, a0, top, pink, ba * e0);
            vertexSky(belt, pose, a1, top, pink, ba * e1);
            vertexSky(belt, pose, a1, beltTop, pink, 0.0F);
            vertexSky(belt, pose, a0, beltTop, pink, 0.0F);
        }
        draw(belt);
    }

    private static void vertexSky(BufferBuilder buffer, Matrix4f pose, float azimuth, float elevation, float[] color, float alpha) {
        Vector3f p = sky(azimuth, elevation);
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(color[0], color[1], color[2], alpha);
    }

    /** Patches of pastel pink and green in thin cloud a little way from the sun, shifting as the cloud drifts. */
    private static void drawIridescence(Matrix4f pose, Vector3f sun, float time, float strength) {
        Vector3f[] b = basis(sun);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 48, rings = 5;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI, phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int r = 0; r < rings; r++) {
                float rho0 = (6.0F + r * 2.2F) * Mth.DEG_TO_RAD, rho1 = (6.0F + (r + 1) * 2.2F) * Mth.DEG_TO_RAD;
                float patch0 = Math.max(0.0F, Mth.sin(phi0 * 3.0F + time * 0.003F) * Mth.sin(rho0 * 40.0F - time * 0.002F));
                float patch1 = Math.max(0.0F, Mth.sin(phi1 * 3.0F + time * 0.003F) * Mth.sin(rho1 * 40.0F - time * 0.002F));
                float hue = (r + Mth.sin(phi0 * 2.0F)) * 0.33F;
                float[] c = {0.75F + 0.25F * Mth.sin(hue * 6.0F), 0.75F + 0.25F * Mth.sin(hue * 6.0F + 2.1F), 0.75F + 0.25F * Mth.sin(hue * 6.0F + 4.2F)};
                float a0 = strength * 0.1F * patch0, a1 = strength * 0.1F * patch1;
                vertex(buffer, pose, ringPoint(sun, b[0], b[1], phi0, rho0), c, a0);
                vertex(buffer, pose, ringPoint(sun, b[0], b[1], phi1, rho0), c, a1);
                vertex(buffer, pose, ringPoint(sun, b[0], b[1], phi1, rho1), c, a1);
                vertex(buffer, pose, ringPoint(sun, b[0], b[1], phi0, rho1), c, a0);
            }
        }
        draw(buffer);
    }

    /** A brief green spark on the top edge of the sun as the last of it slips below a clear sea horizon. */
    private static void drawGreenFlash(Matrix4f pose, Vector3f sun, float strength) {
        Vector3f center = new Vector3f(sun).normalize();
        Vector3f side = new Vector3f(center).cross(new Vector3f(0.0F, 1.0F, 0.0F));
        if (side.lengthSquared() < 1.0E-5F) return;
        side.normalize();
        Vector3f lift = new Vector3f(side).cross(center).normalize();
        if (lift.y < 0) lift.negate();
        Vector3f top = new Vector3f(center).add(new Vector3f(lift).mul(0.075F)).normalize().mul(DOME);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] green = {0.35F, 1.0F, 0.45F};
        float w = 4.0F, h = 1.0F;
        vertex(buffer, pose, new Vector3f(top).add(new Vector3f(side).mul(-w)).add(new Vector3f(lift).mul(-h)), green, strength * 0.9F);
        vertex(buffer, pose, new Vector3f(top).add(new Vector3f(side).mul(w)).add(new Vector3f(lift).mul(-h)), green, strength * 0.9F);
        vertex(buffer, pose, new Vector3f(top).add(new Vector3f(side).mul(w)).add(new Vector3f(lift).mul(h)), green, 0.0F);
        vertex(buffer, pose, new Vector3f(top).add(new Vector3f(side).mul(-w)).add(new Vector3f(lift).mul(h)), green, 0.0F);
        draw(buffer);
    }

    /** A bow round the point opposite {@code light}, between two angles, its colours washed out toward white. */
    private static void drawBow(Matrix4f pose, Vector3f light, float innerDeg, float outerDeg, float whiteness, float alpha) {
        Vector3f anti = new Vector3f(light).negate().normalize();
        Vector3f[] b = basis(anti);
        Vector3f u = b[0], v = b[1];
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 96;
        int bands = RAINBOW.length + 1;
        float inner = innerDeg * Mth.DEG_TO_RAD, outer = outerDeg * Mth.DEG_TO_RAD;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI, phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int k = 0; k < bands; k++) {
                float rho0 = Mth.lerp(k / (float) bands, inner, outer), rho1 = Mth.lerp((k + 1) / (float) bands, inner, outer);
                Vector3f p00 = ringPoint(anti, u, v, phi0, rho0), p10 = ringPoint(anti, u, v, phi1, rho0);
                Vector3f p11 = ringPoint(anti, u, v, phi1, rho1), p01 = ringPoint(anti, u, v, phi0, rho1);
                if (p00.y < -0.03F && p10.y < -0.03F) continue;
                float[] c0 = washed(bandColor(k - 1), whiteness), c1 = washed(bandColor(k), whiteness);
                float a0 = k == 0 ? 0.0F : alpha, a1 = k == bands - 1 ? 0.0F : alpha;
                vertex(buffer, pose, p00, c0, a0 * horizonFade(p00));
                vertex(buffer, pose, p10, c0, a0 * horizonFade(p10));
                vertex(buffer, pose, p11, c1, a1 * horizonFade(p11));
                vertex(buffer, pose, p01, c1, a1 * horizonFade(p01));
            }
        }
        draw(buffer);
    }

    private static float[] washed(float[] c, float white) {
        return new float[]{Mth.lerp(white, c[0], 1.0F), Mth.lerp(white, c[1], 1.0F), Mth.lerp(white, c[2], 1.0F)};
    }

    /**
     * Noctilucent clouds: thin, rippled sheets of ice cloud so high they're still lit by the sun long after it
     * has set, glowing electric blue-white low in the northern sky.
     */
    private static void drawNoctilucent(Matrix4f pose, float time, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 72, rows = 6;
        float span = 1.5F;
        for (int i = 0; i < steps; i++) {
            float a0 = (i / (float) steps - 0.5F) * span, a1 = ((i + 1) / (float) steps - 0.5F) * span;
            float edge0 = Mth.sin((i / (float) steps) * Mth.PI), edge1 = Mth.sin(((i + 1) / (float) steps) * Mth.PI);
            for (int r = 0; r < rows; r++) {
                float e0 = (3.0F + r * 2.0F) * Mth.DEG_TO_RAD, e1 = (3.0F + (r + 1) * 2.0F) * Mth.DEG_TO_RAD;
                float g00 = nlcGlow(a0, e0, time) * edge0, g10 = nlcGlow(a1, e0, time) * edge1;
                float g11 = nlcGlow(a1, e1, time) * edge1, g01 = nlcGlow(a0, e1, time) * edge0;
                // brightest low down, fading out higher up
                float fade0 = 1.0F - r / (float) rows, fade1 = 1.0F - (r + 1) / (float) rows;
                float[] c = {0.55F, 0.82F, 1.0F};
                vertex(buffer, pose, sky(a0, e0), c, alpha * g00 * fade0);
                vertex(buffer, pose, sky(a1, e0), c, alpha * g10 * fade0);
                vertex(buffer, pose, sky(a1, e1), c, alpha * g11 * fade1);
                vertex(buffer, pose, sky(a0, e1), c, alpha * g01 * fade1);
            }
        }
        draw(buffer);
    }

    /** The fine ripples and broad bands of a noctilucent cloud sheet. */
    private static float nlcGlow(float azimuth, float elevation, float time) {
        float ripple = 0.55F + 0.45F * Mth.sin(azimuth * 55.0F + elevation * 90.0F + time * 0.0015F);
        float bands = 0.5F + 0.5F * Mth.sin(azimuth * 7.0F - elevation * 25.0F + time * 0.0006F);
        float patches = 0.5F + 0.5F * Mth.sin(azimuth * 3.1F + 1.7F) * Mth.cos(elevation * 14.0F - azimuth * 2.0F);
        return Mth.clamp(ripple * bands * (0.4F + 0.6F * patches) * 1.4F, 0.0F, 1.0F);
    }

    private static float[] bandColor(int index) {
        return RAINBOW[Mth.clamp(index, 0, RAINBOW.length - 1)];
    }

    private static float horizonFade(Vector3f p) {
        return Mth.clamp((p.y / DOME + 0.03F) * 8.0F, 0.0F, 1.0F);
    }

    private static Vector3f ringPoint(Vector3f center, Vector3f u, Vector3f v, float phi, float rho) {
        float s = Mth.sin(rho);
        return new Vector3f(center).mul(Mth.cos(rho))
                .add(new Vector3f(u).mul(Mth.cos(phi) * s))
                .add(new Vector3f(v).mul(Mth.sin(phi) * s))
                .mul(DOME);
    }

    /** A 22-degree ring of light around the sun or moon, faintly red on the inside. */
    private static void drawHalo(Matrix4f pose, Vector3f center, float alpha) {
        if (alpha <= 0.003F) {
            return;
        }
        Vector3f[] basis = basis(center);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][] bands = {{20.6F, 0.0F}, {21.6F, 1.0F}, {22.6F, 0.7F}, {24.5F, 0.0F}};
        float[][] colors = {{1.0F, 0.6F, 0.45F}, {1.0F, 0.85F, 0.75F}, {0.9F, 0.93F, 1.0F}, {0.85F, 0.9F, 1.0F}};
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            float phi0 = i / (float) segments * Mth.TWO_PI, phi1 = (i + 1) / (float) segments * Mth.TWO_PI;
            for (int b = 0; b < bands.length - 1; b++) {
                float rho0 = bands[b][0] * Mth.DEG_TO_RAD, rho1 = bands[b + 1][0] * Mth.DEG_TO_RAD;
                Vector3f p00 = ringPoint(center, basis[0], basis[1], phi0, rho0), p10 = ringPoint(center, basis[0], basis[1], phi1, rho0);
                Vector3f p11 = ringPoint(center, basis[0], basis[1], phi1, rho1), p01 = ringPoint(center, basis[0], basis[1], phi0, rho1);
                if (p00.y < -3.0F && p10.y < -3.0F && p11.y < -3.0F) {
                    continue;
                }
                float a0 = alpha * bands[b][1], a1 = alpha * bands[b + 1][1];
                vertex(buffer, pose, p00, colors[b], a0 * horizonFade(p00));
                vertex(buffer, pose, p10, colors[b], a0 * horizonFade(p10));
                vertex(buffer, pose, p11, colors[b + 1], a1 * horizonFade(p11));
                vertex(buffer, pose, p01, colors[b + 1], a1 * horizonFade(p01));
            }
        }
        draw(buffer);
    }

    /** Two bright, rainbow-edged spots level with a low sun, one on each side of it. */
    private static void drawSunDogs(Matrix4f pose, Vector3f sun, float strength) {
        if (strength <= 0.005F) {
            return;
        }
        float elevation = (float) Math.asin(Mth.clamp(sun.y, -1.0F, 1.0F));
        float cosE = Mth.cos(elevation);
        // keep the dogs 22 degrees from the sun: they swing outward as the sun climbs
        float cosD = (Mth.cos(22.0F * Mth.DEG_TO_RAD) - sun.y * sun.y) / Math.max(0.05F, cosE * cosE);
        float delta = (float) Math.acos(Mth.clamp(cosD, -1.0F, 1.0F));
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            Vector3f dog = new Vector3f(sun).rotateY(side * delta).normalize();
            // the horizontal axis that points away from the sun
            Vector3f out = new Vector3f(dog).sub(sun);
            out.sub(new Vector3f(dog).mul(out.dot(dog))).normalize();
            Vector3f up = new Vector3f(dog).cross(out).normalize();
            if (up.y < 0) up.negate();
            float w = 2.0F * Mth.DEG_TO_RAD, h = 2.8F * Mth.DEG_TO_RAD, tail = 9.0F * Mth.DEG_TO_RAD;
            float a = strength * 0.8F;
            // red inner edge, white core, bluish outer edge, then a faint tail away from the sun
            spot(buffer, pose, dog, out, up, -w, 0.0F, h, 1.0F, 0.45F, 0.3F, 0.0F, a);
            spot(buffer, pose, dog, out, up, 0.0F, w, h, 1.0F, 0.95F, 0.85F, a, a * 0.6F);
            spot(buffer, pose, dog, out, up, w, tail, h * 0.4F, 0.85F, 0.9F, 1.0F, a * 0.6F, 0.0F);
        }
        draw(buffer);
    }

    private static void spot(BufferBuilder buffer, Matrix4f pose, Vector3f center, Vector3f out, Vector3f up,
                             float from, float to, float halfHeight, float r, float g, float b, float alphaFrom, float alphaTo) {
        Vector3f c0 = new Vector3f(center).add(new Vector3f(out).mul(from)).normalize().mul(DOME);
        Vector3f c1 = new Vector3f(center).add(new Vector3f(out).mul(to)).normalize().mul(DOME);
        Vector3f dy = new Vector3f(up).mul(halfHeight * DOME);
        float[] color = {r, g, b};
        vertex(buffer, pose, new Vector3f(c0).sub(dy), color, 0.0F);
        vertex(buffer, pose, new Vector3f(c1).sub(dy), color, 0.0F);
        vertex(buffer, pose, c1, color, alphaTo);
        vertex(buffer, pose, c0, color, alphaFrom);
        vertex(buffer, pose, c0, color, alphaFrom);
        vertex(buffer, pose, c1, color, alphaTo);
        vertex(buffer, pose, new Vector3f(c1).add(dy), color, 0.0F);
        vertex(buffer, pose, new Vector3f(c0).add(dy), color, 0.0F);
    }

    /**
     * Sunbeams hang in the air below the clouds, so they're drawn after the storm cloud deck (from the
     * particle stage), still on a dome around the camera so nearby terrain hides them.
     */
    public static void renderSunbeams(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        float beams = ClientWeather.sunbeams();
        if (level == null || beams <= 0.005F || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float theta = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        Vector3f sun = new Vector3f(-Mth.sin(theta), Mth.cos(theta), 0.0F);
        if (sun.y <= 0.0F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        drawSunbeams(new Matrix4f(), sun, level.getGameTime() + partialTick, beams * Mth.clamp(sun.y * 5.0F, 0.0F, 1.0F));
        if (ClientConfig.ANTICREPUSCULAR_RAYS.get() && sun.y < 0.35F) {
            drawAntiBeams(new Matrix4f(), sun, level.getGameTime() + partialTick, beams * Mth.clamp((0.35F - sun.y) * 5.0F, 0.0F, 1.0F) * Mth.clamp(sun.y * 8.0F, 0.0F, 1.0F));
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Crepuscular rays fanning out from the sun through gaps in the cloud. */
    private static void drawSunbeams(Matrix4f pose, Vector3f sun, float time, float strength) {
        Vector3f[] basis = basis(sun);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] warm = {1.0F, 0.94F, 0.78F};
        int rays = 14;
        for (int i = 0; i < rays; i++) {
            float seed = i * 12.9898F;
            float phi = (i + 0.5F * Mth.sin(seed)) / rays * Mth.TWO_PI + 0.05F * Mth.sin(time * 0.0007F + seed);
            float width = (1.2F + 1.4F * (0.5F + 0.5F * Mth.sin(seed * 3.1F))) * Mth.DEG_TO_RAD;
            float length = (45.0F + 30.0F * (0.5F + 0.5F * Mth.sin(seed * 1.7F))) * Mth.DEG_TO_RAD;
            // each ray brightens and fades as clouds drift across the sun
            float flicker = Mth.clamp(0.5F + 0.6F * Mth.sin(time * 0.004F + seed * 2.3F), 0.0F, 1.0F);
            float a = strength * 0.4F * flicker;
            if (a <= 0.002F) continue;
            Vector3f start = ringPoint(sun, basis[0], basis[1], phi, 3.0F * Mth.DEG_TO_RAD);
            Vector3f left = ringPoint(sun, basis[0], basis[1], phi - width / Mth.sin(length), length);
            Vector3f right = ringPoint(sun, basis[0], basis[1], phi + width / Mth.sin(length), length);
            vertex(buffer, pose, start, warm, a * horizonFade(start));
            vertex(buffer, pose, left, warm, 0.0F);
            vertex(buffer, pose, right, warm, 0.0F);
        }
        draw(buffer);
    }

    /** Converging rays: the same sunbeams, meeting again at the point opposite the sun on the far horizon. */
    private static void drawAntiBeams(Matrix4f pose, Vector3f sun, float time, float strength) {
        Vector3f anti = new Vector3f(sun).negate();
        Vector3f[] basis = basis(anti);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] warm = {1.0F, 0.9F, 0.8F};
        for (int i = 0; i < 12; i++) {
            float seed = i * 7.31F + 3.0F;
            float phi = (i + 0.5F * Mth.sin(seed)) / 12.0F * Mth.TWO_PI;
            float width = (1.5F + 1.5F * (0.5F + 0.5F * Mth.sin(seed * 2.7F))) * Mth.DEG_TO_RAD;
            float length = (40.0F + 25.0F * (0.5F + 0.5F * Mth.sin(seed * 1.3F))) * Mth.DEG_TO_RAD;
            float flicker = Mth.clamp(0.5F + 0.6F * Mth.sin(time * 0.004F + seed * 2.3F), 0.0F, 1.0F);
            float a = strength * 0.18F * flicker;
            if (a <= 0.002F) continue;
            Vector3f tip = ringPoint(anti, basis[0], basis[1], phi, 2.0F * Mth.DEG_TO_RAD);
            Vector3f left = ringPoint(anti, basis[0], basis[1], phi - width / Mth.sin(length), length);
            Vector3f right = ringPoint(anti, basis[0], basis[1], phi + width / Mth.sin(length), length);
            vertex(buffer, pose, tip, warm, 0.0F);
            vertex(buffer, pose, left, warm, a * horizonFade(left));
            vertex(buffer, pose, right, warm, a * horizonFade(right));
        }
        draw(buffer);
    }

    /** Turns a direction fixed among the stars into where it is in the sky now (the stars wheel round with the sun). */
    private static Vector3f celestial(Vector3f v, float theta) {
        float c = Mth.cos(theta), s = Mth.sin(theta);
        return new Vector3f(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
    }

    /**
     * The Milky Way: a faint, mottled band of light right across the sky, brighter and broader toward the core,
     * split by dark dust lanes, wheeling round with the stars.
     */
    private static void drawMilkyWay(Matrix4f pose, float theta, float strength) {
        Vector3f n = new Vector3f(0.35F, 0.25F, 0.9F).normalize();
        Vector3f u = new Vector3f(n).cross(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 120, rows = 6;
        for (int s = 0; s < segments; s++) {
            float p0 = s / (float) segments * Mth.TWO_PI, p1 = (s + 1) / (float) segments * Mth.TWO_PI;
            float core0 = 0.5F + 0.5F * Mth.cos(p0 - 1.2F), core1 = 0.5F + 0.5F * Mth.cos(p1 - 1.2F);
            float width0 = (7.0F + 9.0F * core0 * core0) * Mth.DEG_TO_RAD, width1 = (7.0F + 9.0F * core1 * core1) * Mth.DEG_TO_RAD;
            for (int r = 0; r < rows; r++) {
                float w0 = (r / (float) rows - 0.5F) * 2.0F, w1 = ((r + 1) / (float) rows - 0.5F) * 2.0F;
                Vector3f[] corners = {band(u, v, n, p0, w0 * width0), band(u, v, n, p1, w0 * width1), band(u, v, n, p1, w1 * width1), band(u, v, n, p0, w1 * width0)};
                float[] ws = {w0, w0, w1, w1};
                float[] ps = {p0, p1, p1, p0};
                for (int k = 0; k < 4; k++) {
                    Vector3f p = celestial(corners[k], theta).normalize().mul(DOME);
                    float core = 0.5F + 0.5F * Mth.cos(ps[k] - 1.2F);
                    // mottled star clouds, with a dark rift down the middle near the core
                    float mottle = 0.6F + 0.4F * Mth.sin(ps[k] * 17.0F + ws[k] * 5.0F) * Mth.sin(ps[k] * 7.0F - ws[k] * 3.0F);
                    float rift = 1.0F - 0.6F * core * Math.max(0.0F, 1.0F - Math.abs(ws[k] + 0.1F * Mth.sin(ps[k] * 9.0F)) * 4.0F);
                    float edge = 1.0F - ws[k] * ws[k];
                    float[] c = {0.85F + 0.15F * core, 0.86F + 0.08F * core, 0.95F - 0.1F * core};
                    vertex(buffer, pose, p, c, strength * 0.17F * (0.35F + 0.65F * core) * mottle * rift * edge * horizonFade(p));
                }
            }
        }
        draw(buffer);
    }

    private static Vector3f band(Vector3f u, Vector3f v, Vector3f n, float phi, float w) {
        float c = Mth.cos(w);
        return new Vector3f(u).mul(Mth.cos(phi) * c).add(new Vector3f(v).mul(Mth.sin(phi) * c)).add(new Vector3f(n).mul(Mth.sin(w)));
    }

    /** Zodiacal light: a faint, tilted cone of glow rising from where the sun went down, along the sun's path. */
    private static void drawZodiacal(Matrix4f pose, float theta, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] c = {0.95F, 0.92F, 0.85F};
        // the sun's own path across the sky, starting a little way from the (set) sun and climbing toward the zenith
        float side = Mth.sin(theta) > 0 ? 1.0F : -1.0F; // which way along its path is up from the sun at dusk or dawn
        for (int i = 0; i < 14; i++) {
            float d0 = (18.0F + i * 4.0F) * Mth.DEG_TO_RAD, d1 = (18.0F + (i + 1) * 4.0F) * Mth.DEG_TO_RAD;
            float w0 = (13.0F - i * 0.8F) * Mth.DEG_TO_RAD, w1 = (13.0F - (i + 1) * 0.8F) * Mth.DEG_TO_RAD;
            Vector3f a = path(theta - side * d0, -w0), b = path(theta - side * d1, -w1), e = path(theta - side * d1, w1), f = path(theta - side * d0, w0);
            float fade0 = strength * 0.16F * (1.0F - i / 14.0F), fade1 = strength * 0.16F * (1.0F - (i + 1) / 14.0F);
            vertex(buffer, pose, a, c, 0.0F);
            vertex(buffer, pose, b, c, 0.0F);
            vertex(buffer, pose, path(theta - side * d1, 0.0F), c, fade1 * horizonFade(path(theta - side * d1, 0.0F)));
            vertex(buffer, pose, path(theta - side * d0, 0.0F), c, fade0 * horizonFade(path(theta - side * d0, 0.0F)));
            vertex(buffer, pose, path(theta - side * d0, 0.0F), c, fade0 * horizonFade(path(theta - side * d0, 0.0F)));
            vertex(buffer, pose, path(theta - side * d1, 0.0F), c, fade1 * horizonFade(path(theta - side * d1, 0.0F)));
            vertex(buffer, pose, e, c, 0.0F);
            vertex(buffer, pose, f, c, 0.0F);
        }
        draw(buffer);
    }

    /** A point on (or {@code off} radians to the side of) the sun's path, where the sun would be at angle {@code t}. */
    private static Vector3f path(float t, float off) {
        return new Vector3f(-Mth.sin(t) * Mth.cos(off), Mth.cos(t) * Mth.cos(off), Mth.sin(off)).mul(DOME);
    }

    /**
     * A spectral arc of light at one elevation, centred on the sun's direction: the circumhorizontal arc (low, red
     * on top) or circumzenithal arc (high, red on the bottom), both made by sunlight through flat ice crystals.
     */
    private static void drawArc(Matrix4f pose, Vector3f sun, float elevation, float spanDeg, boolean zenith, float strength) {
        if (elevation <= 0.02F || elevation >= Mth.HALF_PI - 0.02F) return;
        float sunAz = (float) Math.atan2(sun.x, -sun.z);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][] colors = {{1.0F, 0.2F, 0.15F}, {1.0F, 0.6F, 0.1F}, {1.0F, 1.0F, 0.2F}, {0.25F, 1.0F, 0.35F}, {0.2F, 0.5F, 1.0F}, {0.55F, 0.25F, 1.0F}};
        float thick = 0.45F * Mth.DEG_TO_RAD;
        int steps = 30;
        for (int band = 0; band < colors.length; band++) {
            // red at the top of the circumhorizontal arc, at the bottom (nearest the sun) of the circumzenithal
            float e0 = elevation + (zenith ? band : -band) * thick, e1 = e0 + (zenith ? thick : -thick);
            for (int i = 0; i < steps; i++) {
                float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
                float a0 = sunAz + (t0 - 0.5F) * spanDeg * 2.0F * Mth.DEG_TO_RAD, a1 = sunAz + (t1 - 0.5F) * spanDeg * 2.0F * Mth.DEG_TO_RAD;
                float f0 = Mth.sin(t0 * Mth.PI) * strength, f1 = Mth.sin(t1 * Mth.PI) * strength;
                vertexSky(buffer, pose, a0, e0, colors[band], f0);
                vertexSky(buffer, pose, a1, e0, colors[band], f1);
                vertexSky(buffer, pose, a1, e1, colors[band], f1);
                vertexSky(buffer, pose, a0, e1, colors[band], f0);
            }
        }
        draw(buffer);
    }

    /** Nacreous (mother-of-pearl) clouds: brilliant pastel sheets high in the polar twilight sky, toward the sun. */
    private static void drawNacreous(Matrix4f pose, Vector3f sun, float time, float strength) {
        float sunAz = (float) Math.atan2(sun.x, -sun.z);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int cols = 36, rows = 6;
        for (int i = 0; i < cols; i++) {
            float a0 = sunAz + (i / (float) cols - 0.5F) * 1.6F, a1 = sunAz + ((i + 1) / (float) cols - 0.5F) * 1.6F;
            for (int r = 0; r < rows; r++) {
                float e0 = (6.0F + r * 3.0F) * Mth.DEG_TO_RAD, e1 = (6.0F + (r + 1) * 3.0F) * Mth.DEG_TO_RAD;
                float wisp0 = Math.max(0.0F, Mth.sin(a0 * 9.0F + r * 1.3F + time * 0.0015F) * Mth.sin(a0 * 3.0F - r * 0.7F));
                float wisp1 = Math.max(0.0F, Mth.sin(a1 * 9.0F + r * 1.3F + time * 0.0015F) * Mth.sin(a1 * 3.0F - r * 0.7F));
                float hue = r * 0.9F + a0 * 2.0F;
                float[] c = {0.8F + 0.2F * Mth.sin(hue), 0.8F + 0.2F * Mth.sin(hue + 2.1F), 0.85F + 0.15F * Mth.sin(hue + 4.2F)};
                float edge = Mth.sin((i + 0.5F) / cols * Mth.PI) * Mth.sin((r + 0.5F) / rows * Mth.PI);
                vertexSky(buffer, pose, a0, e0, c, strength * 0.3F * wisp0 * edge);
                vertexSky(buffer, pose, a1, e0, c, strength * 0.3F * wisp1 * edge);
                vertexSky(buffer, pose, a1, e1, c, strength * 0.3F * wisp1 * edge);
                vertexSky(buffer, pose, a0, e1, c, strength * 0.3F * wisp0 * edge);
            }
        }
        draw(buffer);
    }

    /**
     * Red sky at night, shepherd's delight; red sky in the morning, shepherd's warning. A fiery sunset glow means the
     * clear sky to the west (where the weather comes from) is coming this way; a red sunrise means the clear sky
     * has passed and cloud is on its way. Here, how red it gets depends on what the forecast holds.
     */
    private static float redSky(ClientLevel level, Vector3f sun) {
        WeatherType[] forecast = com.reiko.weather.network.SyncedWeather.forecast();
        if (forecast.length == 0 || Math.abs(sun.y) > 0.12F) return 0.0F;
        WeatherType next = forecast[0];
        boolean evening = sun.x < 0; // the sun sets in the west (-X)
        boolean fair = next.isCalm() || next == WeatherType.WINDY || next == WeatherType.COLD_SNAP;
        boolean wet = next.vanillaRain;
        float near = Mth.clamp(1.0F - Math.abs(sun.y - 0.01F) / 0.11F, 0.0F, 1.0F);
        return (evening ? (fair ? 1.0F : 0.0F) : (wet ? 1.0F : 0.0F)) * near * (1.0F - ClientWeather.overcast() * 0.7F);
    }

    /**
     * The Chinook arch: a deck of grey cloud over the whole sky except for a clear band over the mountains upwind,
     * bounded by the deck's sharp, bowed edge.
     */
    private static void drawChinookArch(Matrix4f pose, float upAz, float daylight, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float lit = 0.3F + 0.7F * daylight;
        float[] deck = {0.46F * lit, 0.48F * lit, 0.53F * lit}, rim = {0.86F * lit, 0.84F * lit, 0.8F * lit};
        float opaque = Math.min(1.0F, strength * 1.4F) * 0.93F;
        int around = 96, rows = 12;
        for (int i = 0; i < around; i++) {
            float a0 = i * Mth.TWO_PI / around, a1 = (i + 1) * Mth.TWO_PI / around;
            float e0 = archEdge(a0, upAz), e1 = archEdge(a1, upAz);
            for (int j = 0; j < rows; j++) {
                // rows crowd together at the edge, so it stays crisp
                float q0 = Mth.square(j / (float) rows), q1 = Mth.square((j + 1) / (float) rows);
                float b0 = e0 + (Mth.HALF_PI - e0) * q0, b1 = e1 + (Mth.HALF_PI - e1) * q0;
                float c0 = e0 + (Mth.HALF_PI - e0) * q1, c1 = e1 + (Mth.HALF_PI - e1) * q1;
                float f0 = j == 0 ? 0.0F : opaque, f1 = opaque;
                float[] lower = j <= 1 ? rim : deck, upper = j == 0 ? rim : deck;
                vertexSky(buffer, pose, a0, b0, lower, f0);
                vertexSky(buffer, pose, a1, b1, lower, f0);
                vertexSky(buffer, pose, a1, c1, upper, f1);
                vertexSky(buffer, pose, a0, c0, upper, f1);
            }
        }
        draw(buffer);
    }

    /** The elevation of the arch's edge at an azimuth: highest over the mountains, down near the horizon elsewhere. */
    private static float archEdge(float azimuth, float upAz) {
        float d = Mth.wrapDegrees((azimuth - upAz) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float c = Mth.cos(d);
        return (2.0F + 22.0F * (float) Math.pow(Math.max(0.0F, c), 0.8F)) * Mth.DEG_TO_RAD;
    }

    private static void drawRedSky(Matrix4f pose, Vector3f sun, float strength) {
        float sunAz = (float) Math.atan2(sun.x, -sun.z);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] red = {1.0F, 0.32F, 0.12F}, orange = {1.0F, 0.55F, 0.2F};
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
            float a0 = sunAz + (t0 - 0.5F) * 2.6F, a1 = sunAz + (t1 - 0.5F) * 2.6F;
            float f0 = Mth.sin(t0 * Mth.PI), f1 = Mth.sin(t1 * Mth.PI);
            vertexSky(buffer, pose, a0, -0.02F, red, strength * 0.3F * f0);
            vertexSky(buffer, pose, a1, -0.02F, red, strength * 0.3F * f1);
            vertexSky(buffer, pose, a1, 14.0F * Mth.DEG_TO_RAD, orange, 0.0F);
            vertexSky(buffer, pose, a0, 14.0F * Mth.DEG_TO_RAD, orange, 0.0F);
        }
        draw(buffer);
    }

    /** A blood moon: the moon dyed deep red, with a red glow around it. */
    private static void drawBloodMoon(Matrix4f pose, Vector3f moon, float strength) {
        if (moon.y < -0.1F) {
            return;
        }
        Vector3f[] basis = basis(moon);
        // multiply the moon (and the dark sky right behind it) toward red
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // just the moon's disc (vanilla's moon quad is mostly empty texture around it)
        float half = 3.4F * Mth.DEG_TO_RAD;
        float[] tint = {1.0F, Mth.lerp(strength, 1.0F, 0.32F), Mth.lerp(strength, 1.0F, 0.22F)};
        for (int k = 0; k < 4; k++) {
            float phi = (k * 90 + 45) * Mth.DEG_TO_RAD;
            vertex(buffer, pose, ringPoint(moon, basis[0], basis[1], phi, half * Mth.SQRT_OF_TWO), tint, 1.0F);
        }
        draw(buffer);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] red = {0.9F, 0.12F, 0.08F};
        int segments = 32;
        Vector3f c = new Vector3f(moon).normalize().mul(DOME);
        for (int i = 0; i < segments; i++) {
            float phi0 = i / (float) segments * Mth.TWO_PI, phi1 = (i + 1) / (float) segments * Mth.TWO_PI;
            vertex(buffer, pose, c, red, 0.35F * strength);
            vertex(buffer, pose, ringPoint(moon, basis[0], basis[1], phi0, 26.0F * Mth.DEG_TO_RAD), red, 0.0F);
            vertex(buffer, pose, ringPoint(moon, basis[0], basis[1], phi1, 26.0F * Mth.DEG_TO_RAD), red, 0.0F);
        }
        draw(buffer);
    }

    /** Through wildfire smoke the sun is a dull red ball. */
    private static void drawRedSun(Matrix4f pose, Vector3f sun, float strength) {
        Vector3f[] basis = basis(sun);
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] red = {0.92F, 0.28F, 0.12F};
        int segments = 32;
        Vector3f c = new Vector3f(sun).normalize().mul(DOME);
        float disc = 5.5F * Mth.DEG_TO_RAD, edge = 8.5F * Mth.DEG_TO_RAD;
        float a = 0.88F * strength;
        for (int i = 0; i < segments; i++) {
            float phi0 = i / (float) segments * Mth.TWO_PI, phi1 = (i + 1) / (float) segments * Mth.TWO_PI;
            Vector3f d0 = ringPoint(sun, basis[0], basis[1], phi0, disc), d1 = ringPoint(sun, basis[0], basis[1], phi1, disc);
            Vector3f e0 = ringPoint(sun, basis[0], basis[1], phi0, edge), e1 = ringPoint(sun, basis[0], basis[1], phi1, edge);
            vertex(buffer, pose, c, red, a);
            vertex(buffer, pose, d0, red, a);
            vertex(buffer, pose, d1, red, a);
            vertex(buffer, pose, d0, red, a);
            vertex(buffer, pose, e0, red, 0.0F);
            vertex(buffer, pose, e1, red, 0.0F);
            vertex(buffer, pose, d0, red, a);
            vertex(buffer, pose, e1, red, 0.0F);
            vertex(buffer, pose, d1, red, a);
        }
        draw(buffer);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    /** A sun pillar: a column of light standing straight up from the low sun, lit by the ice crystals in the air. */
    private static void drawSunPillar(Matrix4f pose, Vector3f sun, float strength) {
        Vector3f s = new Vector3f(sun).normalize();
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).sub(new Vector3f(s).mul(s.y)).normalize();
        Vector3f side = new Vector3f(s).cross(up).normalize();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] warm = {1.0F, 0.9F, 0.75F};
        float half = 0.9F * Mth.DEG_TO_RAD, top = 16.0F * Mth.DEG_TO_RAD, bottom = -5.0F * Mth.DEG_TO_RAD;
        float a = 0.55F * strength;
        for (float[] span : new float[][]{{0.0F, top}, {0.0F, bottom}}) {
            Vector3f p0l = new Vector3f(s).add(new Vector3f(up).mul(span[0])).add(new Vector3f(side).mul(-half)).normalize().mul(DOME);
            Vector3f p0r = new Vector3f(s).add(new Vector3f(up).mul(span[0])).add(new Vector3f(side).mul(half)).normalize().mul(DOME);
            Vector3f p1l = new Vector3f(s).add(new Vector3f(up).mul(span[1])).add(new Vector3f(side).mul(-half)).normalize().mul(DOME);
            Vector3f p1r = new Vector3f(s).add(new Vector3f(up).mul(span[1])).add(new Vector3f(side).mul(half)).normalize().mul(DOME);
            vertex(buffer, pose, p0l, warm, a);
            vertex(buffer, pose, p0r, warm, a);
            vertex(buffer, pose, p1r, warm, 0.0F);
            vertex(buffer, pose, p1l, warm, 0.0F);
        }
        draw(buffer);
    }

    /** Two unit vectors perpendicular to a direction (and to each other). */
    private static Vector3f[] basis(Vector3f dir) {
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f ref = Math.abs(d.y) < 0.95F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f u = new Vector3f(ref).sub(new Vector3f(d).mul(d.dot(ref))).normalize();
        Vector3f v = new Vector3f(d).cross(u).normalize();
        return new Vector3f[]{u, v};
    }

    /** Three shimmering curtains hanging over the northern sky. */
    private static void drawAurora(Matrix4f pose, float time, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 64;
        for (int curtain = 0; curtain < 3; curtain++) {
            float offset = curtain * 2.1F;
            float centre = (curtain - 1) * 0.55F;
            float span = 0.9F + curtain * 0.15F;
            for (int i = 0; i < steps; i++) {
                float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
                float[] a = curtainPoint(t0, time, offset, centre, span);
                float[] b = curtainPoint(t1, time, offset, centre, span);
                float edge0 = Mth.sin(t0 * Mth.PI), edge1 = Mth.sin(t1 * Mth.PI);
                float glow0 = alpha * edge0 * a[4], glow1 = alpha * edge1 * b[4];
                // lower band: bright green
                quad(buffer, pose, a, b, a[2], b[2], a[3], b[3], 0.25F, 1.0F, 0.55F, glow0 * 0.9F, glow1 * 0.9F, 0.35F, 0.9F, 0.75F, glow0 * 0.45F, glow1 * 0.45F);
                // upper band: fading into violet
                float topA = a[3] + (a[3] - a[2]) * 0.8F, topB = b[3] + (b[3] - b[2]) * 0.8F;
                quad(buffer, pose, a, b, a[3], b[3], topA, topB, 0.35F, 0.9F, 0.75F, glow0 * 0.45F, glow1 * 0.45F, 0.6F, 0.25F, 0.9F, 0.0F, 0.0F);
            }
        }
        draw(buffer);
    }

    /** Returns {azimuth, unused, bottomElevation, topElevation, brightness} for a point along a curtain. */
    private static float[] curtainPoint(float t, float time, float offset, float centre, float span) {
        float azimuth = centre + (t - 0.5F) * span * 2.2F + 0.06F * Mth.sin(t * 9.0F + time * 0.011F + offset);
        float bottom = (16.0F + 5.0F * Mth.sin(t * 5.0F + time * 0.007F + offset)) * Mth.DEG_TO_RAD;
        float height = (14.0F + 9.0F * (0.5F + 0.5F * Mth.sin(t * 7.0F - time * 0.013F + offset * 1.7F))) * Mth.DEG_TO_RAD;
        float brightness = 0.55F + 0.45F * Mth.sin(t * 23.0F + time * 0.05F + offset * 3.0F) * Mth.sin(t * 3.0F + time * 0.004F);
        return new float[]{azimuth, 0.0F, bottom, bottom + height, Mth.clamp(brightness, 0.15F, 1.0F)};
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, float[] a, float[] b,
                             float aLow, float bLow, float aHigh, float bHigh,
                             float r0, float g0, float b0, float alphaA0, float alphaB0,
                             float r1, float g1, float b1, float alphaA1, float alphaB1) {
        Vector3f p0 = sky(a[0], aLow), p1 = sky(b[0], bLow), p2 = sky(b[0], bHigh), p3 = sky(a[0], aHigh);
        buffer.addVertex(pose, p0.x, p0.y, p0.z).setColor(r0, g0, b0, alphaA0);
        buffer.addVertex(pose, p1.x, p1.y, p1.z).setColor(r0, g0, b0, alphaB0);
        buffer.addVertex(pose, p2.x, p2.y, p2.z).setColor(r1, g1, b1, alphaB1);
        buffer.addVertex(pose, p3.x, p3.y, p3.z).setColor(r1, g1, b1, alphaA1);
    }

    // ------------------------------------------------------------------ eclipses, comets and airglow

    /** Vanilla's sun and moon are square faces: one side runs north-south, the other along their path. */
    private static Vector3f[] faceBasis(Vector3f dir) {
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f across = new Vector3f(0.0F, 0.0F, 1.0F);
        Vector3f along = new Vector3f(d).cross(across).normalize();
        return new Vector3f[]{along, across};
    }

    /** A point on a sun or moon face, {@code x} degrees along its path and {@code y} across it. */
    private static Vector3f onFace(Vector3f dir, Vector3f[] face, float x, float y) {
        return new Vector3f(dir).normalize()
                .add(new Vector3f(face[0]).mul((float) Math.tan(x * Mth.DEG_TO_RAD)))
                .add(new Vector3f(face[1]).mul((float) Math.tan(y * Mth.DEG_TO_RAD)))
                .normalize().mul(DOME);
    }

    /**
     * An eclipse of the sun: the new moon's black disc slides across the sun's face. At totality the pearly corona
     * springs out round it, with pink prominences at the rim and a last dazzling "diamond ring" bead of sunlight
     * either side of it, and a sunset glow all round the horizon; in a ring eclipse a thin ring of fire is left.
     */
    private static void drawSolarEclipse(Matrix4f pose, Vector3f sun, SkyCalendar.Eclipse e, float progress, float sunAlpha) {
        Vector3f[] face = faceBasis(sun);
        float[] o = Eclipses.offset(e, progress);
        float rm = Eclipses.moonRadius(e);
        // the moon only shows where it's in front of the sun (against the bright sky the new moon can't be seen)
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] black = {0.012F, 0.014F, 0.025F};
        // just over the sun's square face, so the moon's disc never shows against the sky beside it
        int n = 30;
        float half = 4.45F, cell = 2.0F * half / n;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -half + i * cell, y0 = -half + j * cell, x1 = x0 + cell, y1 = y0 + cell;
                float a00 = moonCover(x0, y0, o, rm), a10 = moonCover(x1, y0, o, rm), a11 = moonCover(x1, y1, o, rm), a01 = moonCover(x0, y1, o, rm);
                if (a00 + a10 + a11 + a01 <= 0.0F) continue;
                any = true;
                vertex(buffer, pose, onFace(sun, face, x0, y0), black, a00 * sunAlpha);
                vertex(buffer, pose, onFace(sun, face, x1, y0), black, a10 * sunAlpha);
                vertex(buffer, pose, onFace(sun, face, x1, y1), black, a11 * sunAlpha);
                vertex(buffer, pose, onFace(sun, face, x0, y1), black, a01 * sunAlpha);
            }
        }
        if (any) draw(buffer);
        else buffer.build();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        float d = Mth.sqrt(o[0] * o[0] + o[1] * o[1]);
        Vector3f moon = onFace(sun, face, o[0], o[1]).normalize();
        Vector3f[] around = basis(moon);
        if (e == SkyCalendar.Eclipse.TOTAL) {
            float total = Mth.clamp((Eclipses.coverage(e, progress) - 0.985F) / 0.015F, 0.0F, 1.0F) * sunAlpha;
            if (total > 0.005F) {
                float[] pearl = {0.88F, 0.92F, 1.0F};
                drawGlow(pose, moon, around[0], around[1], rm - 0.3F, 15.0F, pearl, 0.5F * total);
                drawGlow(pose, moon, around[0], around[1], rm - 0.3F, 30.0F, pearl, 0.16F * total);
                drawStreamers(pose, moon, around, rm, pearl, 0.22F * total);
                float[] pink = {1.0F, 0.32F, 0.5F};
                for (int k = 0; k < 4; k++) {
                    Vector3f spot = ringPoint(moon, around[0], around[1], (k * 97.0F + 20.0F) * Mth.DEG_TO_RAD, (rm + 0.15F) * Mth.DEG_TO_RAD).normalize();
                    Vector3f[] sb = basis(spot);
                    drawGlow(pose, spot, sb[0], sb[1], 0.0F, 0.55F + 0.25F * (k % 2), pink, 0.55F * total);
                }
                drawHorizonGlow(pose, total);
            }
            // the diamond ring: the last bead of sun as totality starts, and the first as it ends
            float contact = rm - Eclipses.SUN;
            float bead = Mth.clamp(1.0F - Math.abs(d - contact) / 0.8F, 0.0F, 1.0F) * sunAlpha;
            if (bead > 0.01F && d > 0.01F) {
                Vector3f spot = onFace(sun, face, -o[0] / d * Eclipses.SUN * 0.97F, -o[1] / d * Eclipses.SUN * 0.97F).normalize();
                Vector3f[] sb = basis(spot);
                float[] white = {1.0F, 0.98F, 0.92F};
                drawGlow(pose, spot, sb[0], sb[1], 0.0F, 1.6F, white, 0.95F * bead);
                drawGlow(pose, spot, sb[0], sb[1], 0.0F, 7.0F, white, 0.3F * bead);
            }
        } else if (e == SkyCalendar.Eclipse.ANNULAR) {
            float ring = Mth.clamp(1.0F - (d - (Eclipses.SUN - rm)) / 0.6F, 0.0F, 1.0F) * sunAlpha;
            if (ring > 0.01F) {
                drawGlow(pose, moon, around[0], around[1], rm - 0.1F, Eclipses.SUN + 0.5F, new float[]{1.0F, 0.86F, 0.55F}, 0.65F * ring);
            }
        }
    }

    /** How much of a point on the sun's face the moon covers (soft-edged by a fraction of a degree). */
    private static float moonCover(float x, float y, float[] moon, float radius) {
        float dx = x - moon[0], dy = y - moon[1];
        return Mth.clamp((radius - Mth.sqrt(dx * dx + dy * dy)) / 0.3F + 0.5F, 0.0F, 1.0F);
    }

    /** Long, faint rays of the corona, streaming out mostly either side of the sun's equator. */
    private static void drawStreamers(Matrix4f pose, Vector3f centre, Vector3f[] around, float radius, float[] color, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[][] rays = {{8, 26, 2.4F}, {-14, 20, 1.8F}, {172, 24, 2.2F}, {196, 18, 1.6F}, {70, 13, 1.2F}, {250, 12, 1.1F}, {120, 10, 1.0F}};
        for (float[] ray : rays) {
            float phi = ray[0] * Mth.DEG_TO_RAD, spread = ray[2] / ray[1];
            Vector3f base0 = ringPoint(centre, around[0], around[1], phi - spread, radius * Mth.DEG_TO_RAD);
            Vector3f base1 = ringPoint(centre, around[0], around[1], phi + spread, radius * Mth.DEG_TO_RAD);
            Vector3f tip = ringPoint(centre, around[0], around[1], phi, ray[1] * Mth.DEG_TO_RAD);
            vertex(buffer, pose, base0, color, alpha);
            vertex(buffer, pose, base1, color, alpha);
            vertex(buffer, pose, tip, color, 0.0F);
        }
        draw(buffer);
    }

    /** In totality the sky all round the horizon glows like sunset: that's sunlit air beyond the moon's shadow. */
    private static void drawHorizonGlow(Matrix4f pose, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] low = {1.0F, 0.55F, 0.25F}, high = {0.9F, 0.6F, 0.45F};
        int segments = 48;
        float e0 = -2.0F * Mth.DEG_TO_RAD, e1 = 4.0F * Mth.DEG_TO_RAD, e2 = 12.0F * Mth.DEG_TO_RAD;
        for (int i = 0; i < segments; i++) {
            float a0 = i / (float) segments * Mth.TWO_PI, a1 = (i + 1) / (float) segments * Mth.TWO_PI;
            vertex(buffer, pose, sky(a0, e0), low, 0.0F);
            vertex(buffer, pose, sky(a1, e0), low, 0.0F);
            vertex(buffer, pose, sky(a1, e1), low, 0.3F * strength);
            vertex(buffer, pose, sky(a0, e1), low, 0.3F * strength);
            vertex(buffer, pose, sky(a0, e1), high, 0.3F * strength);
            vertex(buffer, pose, sky(a1, e1), high, 0.3F * strength);
            vertex(buffer, pose, sky(a1, e2), high, 0.0F);
            vertex(buffer, pose, sky(a0, e2), high, 0.0F);
        }
        draw(buffer);
    }

    /**
     * An eclipse of the moon: the Earth's shadow creeps across the full moon, dark at first beside the bright
     * part, until the whole moon is in it and glows a dim copper red (lit only by every sunrise and sunset on
     * Earth, bent round through the air).
     */
    private static void drawLunarEclipse(Matrix4f pose, Vector3f moon, SkyCalendar.Eclipse e, float progress) {
        if (moon.y < -0.1F) return;
        Vector3f[] face = faceBasis(moon);
        float[] o = Eclipses.offset(e, progress);
        float c = Eclipses.coverage(e, progress);
        float[] shadow = {Mth.lerp(c, 0.2F, 0.7F), Mth.lerp(c, 0.1F, 0.3F), Mth.lerp(c, 0.08F, 0.16F)};
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int n = 20;
        float half = 5.4F, cell = 2.0F * half / n;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -half + i * cell, y0 = -half + j * cell, x1 = x0 + cell, y1 = y0 + cell;
                umbraVertex(buffer, pose, moon, face, x0, y0, o, shadow);
                umbraVertex(buffer, pose, moon, face, x1, y0, o, shadow);
                umbraVertex(buffer, pose, moon, face, x1, y1, o, shadow);
                umbraVertex(buffer, pose, moon, face, x0, y1, o, shadow);
            }
        }
        draw(buffer);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        float glow = c * c * c * c;
        if (glow > 0.01F) {
            Vector3f[] around = basis(moon);
            drawGlow(pose, new Vector3f(moon).normalize(), around[0], around[1], 0.0F, 10.0F, new float[]{0.8F, 0.26F, 0.12F}, 0.12F * glow);
        }
    }

    private static void umbraVertex(BufferBuilder buffer, Matrix4f pose, Vector3f moon, Vector3f[] face, float x, float y, float[] shadow, float[] color) {
        float dx = x + shadow[0], dy = y + shadow[1]; // the shadow's centre sits at minus the offset from the moon
        float dist = Mth.sqrt(dx * dx + dy * dy);
        // only on the moon's round face, not the square of sky round it
        float onMoon = Mth.clamp((Eclipses.MOON + 0.3F - Mth.sqrt(x * x + y * y)) / 0.6F, 0.0F, 1.0F);
        float inside = Mth.clamp((Eclipses.UMBRA - dist) / 0.9F + 0.5F, 0.0F, 1.0F) * onMoon;
        // the faint outer shadow (penumbra) dims it a little first
        float penumbra = 1.0F - 0.25F * Mth.clamp((Eclipses.UMBRA + 4.0F - dist) / 4.0F, 0.0F, 1.0F) * onMoon;
        Vector3f p = onFace(moon, face, x, y);
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(Mth.lerp(inside, penumbra, color[0]), Mth.lerp(inside, penumbra, color[1]),
                Mth.lerp(inside, penumbra, color[2]), 1.0F);
    }

    /**
     * A great comet low in the twilight: a bright fuzzy head and two tails streaming away from the sun, a broad,
     * gently curved, creamy dust tail and a thin, straight, blue gas tail.
     */
    private static void drawComet(Matrix4f pose, Vector3f sun, float theta, float[] shape, float strength) {
        boolean evening = shape[0] > 0.5F;
        float tc = theta + (evening ? -1.0F : 1.0F) * shape[1] * Mth.DEG_TO_RAD;
        float dec = shape[2] * Mth.DEG_TO_RAD;
        Vector3f head = new Vector3f(-Mth.sin(tc) * Mth.cos(dec), Mth.cos(tc) * Mth.cos(dec), Mth.sin(dec)).normalize();
        if (head.y < -0.05F) return;
        Vector3f s = new Vector3f(sun).normalize();
        Vector3f away = new Vector3f(head).mul(head.dot(s)).sub(s);
        if (away.lengthSquared() < 1.0E-6F) return;
        away.normalize();
        Vector3f side = new Vector3f(head).cross(away).normalize();
        float length = shape[3] * Mth.DEG_TO_RAD;
        cometTail(pose, head, away, side, length, shape[4], 0.4F, 3.2F, new float[]{1.0F, 0.95F, 0.82F}, 0.38F * strength);
        cometTail(pose, head, away, side, length * 1.25F, 0.0F, 0.2F, 0.7F, new float[]{0.5F, 0.72F, 1.0F}, 0.3F * strength);
        Vector3f[] around = basis(head);
        drawGlow(pose, head, around[0], around[1], 0.0F, 1.5F, new float[]{0.82F, 1.0F, 0.9F}, 0.55F * strength);
        drawGlow(pose, head, around[0], around[1], 0.0F, 0.45F, new float[]{1.0F, 1.0F, 1.0F}, 0.9F * strength);
    }

    private static void cometTail(Matrix4f pose, Vector3f head, Vector3f away, Vector3f side, float length, float curve,
                                  float widthStart, float widthEnd, float[] color, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 24;
        Vector3f[] left = new Vector3f[steps + 1], right = new Vector3f[steps + 1];
        float[] fade = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps, a = length * t;
            Vector3f p = new Vector3f(head).mul(Mth.cos(a)).add(new Vector3f(away).mul(Mth.sin(a)))
                    .add(new Vector3f(side).mul(curve * t * t * length)).normalize();
            float w = Mth.lerp(t, widthStart, widthEnd) * Mth.DEG_TO_RAD;
            left[i] = new Vector3f(p).add(new Vector3f(side).mul(w)).normalize().mul(DOME);
            right[i] = new Vector3f(p).sub(new Vector3f(side).mul(w)).normalize().mul(DOME);
            fade[i] = alpha * (float) Math.pow(1.0F - t, 1.6) * Mth.clamp(t * 12.0F, 0.3F, 1.0F);
        }
        for (int i = 0; i < steps; i++) {
            vertex(buffer, pose, left[i], color, fade[i] * horizonFade(left[i]));
            vertex(buffer, pose, right[i], color, fade[i] * horizonFade(right[i]));
            vertex(buffer, pose, right[i + 1], color, fade[i + 1] * horizonFade(right[i + 1]));
            vertex(buffer, pose, left[i + 1], color, fade[i + 1] * horizonFade(left[i + 1]));
        }
        draw(buffer);
    }

    /**
     * Airglow: on the darkest nights the high air itself glows faintly, a green band (oxygen, about 100 km up)
     * hanging a little above the horizon all the way round, with a dimmer red layer above it.
     */
    private static void drawAirglow(Matrix4f pose, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 64;
        float[] green = {0.35F, 1.0F, 0.45F}, red = {1.0F, 0.3F, 0.25F};
        float[][] layers = {{2.0F, 9.0F, 18.0F, 0.09F}, {14.0F, 22.0F, 34.0F, 0.035F}};
        for (int l = 0; l < layers.length; l++) {
            float[] layer = layers[l];
            float[] color = l == 0 ? green : red;
            float lo = layer[0] * Mth.DEG_TO_RAD, mid = layer[1] * Mth.DEG_TO_RAD, hi = layer[2] * Mth.DEG_TO_RAD;
            for (int i = 0; i < segments; i++) {
                float a0 = i / (float) segments * Mth.TWO_PI, a1 = (i + 1) / (float) segments * Mth.TWO_PI;
                // patchy, like the real thing: it drifts in slow waves
                float k0 = 0.7F + 0.3F * Mth.sin(a0 * 5.0F + l), k1 = 0.7F + 0.3F * Mth.sin(a1 * 5.0F + l);
                float p0 = layer[3] * strength * k0, p1 = layer[3] * strength * k1;
                vertex(buffer, pose, sky(a0, lo), color, 0.0F);
                vertex(buffer, pose, sky(a1, lo), color, 0.0F);
                vertex(buffer, pose, sky(a1, mid), color, p1);
                vertex(buffer, pose, sky(a0, mid), color, p0);
                vertex(buffer, pose, sky(a0, mid), color, p0);
                vertex(buffer, pose, sky(a1, mid), color, p1);
                vertex(buffer, pose, sky(a1, hi), color, 0.0F);
                vertex(buffer, pose, sky(a0, hi), color, 0.0F);
            }
        }
        draw(buffer);
    }

    /**
     * STEVE (a "strong thermal emission velocity enhancement"): on some nights of a geomagnetic storm, a narrow
     * ribbon of mauve light arcs right across the sky from east to west, south of the aurora, sometimes with green
     * streaks hanging under it like a picket fence. It lasts an hour or so.
     */
    private static void drawSteve(Matrix4f pose, float time, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f east = new Vector3f(1.0F, 0.0F, 0.0F);
        float top = 72.0F * Mth.DEG_TO_RAD;
        Vector3f high = new Vector3f(0.0F, Mth.sin(top), Mth.cos(top)); // its highest point, a little to the south of overhead
        Vector3f normal = new Vector3f(east).cross(high).normalize();
        float[] mauve = {0.78F, 0.38F, 0.86F}, green = {0.35F, 1.0F, 0.5F};
        int steps = 72;
        float half = 1.1F * Mth.DEG_TO_RAD;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps * Mth.PI, t1 = (i + 1) / (float) steps * Mth.PI;
            Vector3f c0 = new Vector3f(east).mul(Mth.cos(t0)).add(new Vector3f(high).mul(Mth.sin(t0)));
            Vector3f c1 = new Vector3f(east).mul(Mth.cos(t1)).add(new Vector3f(high).mul(Mth.sin(t1)));
            float b0 = strength * (0.55F + 0.45F * Mth.sin(t0 * 7.0F + time * 0.01F)), b1 = strength * (0.55F + 0.45F * Mth.sin(t1 * 7.0F + time * 0.01F));
            Vector3f a0 = new Vector3f(c0).add(new Vector3f(normal).mul(half)).normalize().mul(DOME), d0 = new Vector3f(c0).sub(new Vector3f(normal).mul(half)).normalize().mul(DOME);
            Vector3f a1 = new Vector3f(c1).add(new Vector3f(normal).mul(half)).normalize().mul(DOME), d1 = new Vector3f(c1).sub(new Vector3f(normal).mul(half)).normalize().mul(DOME);
            vertex(buffer, pose, a0, mauve, 0.0F);
            vertex(buffer, pose, a1, mauve, 0.0F);
            vertex(buffer, pose, c1.normalize(new Vector3f()).mul(DOME), mauve, 0.32F * b1 * horizonFade(c1.mul(DOME, new Vector3f())));
            vertex(buffer, pose, c0.normalize(new Vector3f()).mul(DOME), mauve, 0.32F * b0 * horizonFade(c0.mul(DOME, new Vector3f())));
            vertex(buffer, pose, c0.normalize(new Vector3f()).mul(DOME), mauve, 0.32F * b0 * horizonFade(c0.mul(DOME, new Vector3f())));
            vertex(buffer, pose, c1.normalize(new Vector3f()).mul(DOME), mauve, 0.32F * b1 * horizonFade(c1.mul(DOME, new Vector3f())));
            vertex(buffer, pose, d1, mauve, 0.0F);
            vertex(buffer, pose, d0, mauve, 0.0F);
            // the picket fence: green streaks hanging below part of it
            if (i % 3 == 0 && Mth.sin(i * 0.35F + time * 0.003F) > 0.3F) {
                Vector3f below0 = new Vector3f(c0).normalize().sub(new Vector3f(normal).mul(-6.0F * Mth.DEG_TO_RAD)).normalize();
                Vector3f s0 = new Vector3f(c0).normalize().add(new Vector3f(normal).mul(1.5F * Mth.DEG_TO_RAD)).normalize();
                Vector3f side = new Vector3f(c1).sub(c0).normalize().mul(0.25F * Mth.DEG_TO_RAD);
                vertex(buffer, pose, new Vector3f(s0).sub(side).mul(DOME), green, 0.18F * strength);
                vertex(buffer, pose, new Vector3f(s0).add(side).mul(DOME), green, 0.18F * strength);
                vertex(buffer, pose, new Vector3f(below0).add(new Vector3f(normal).mul(4.0F * Mth.DEG_TO_RAD)).normalize().add(side).mul(DOME), green, 0.0F);
                vertex(buffer, pose, new Vector3f(below0).add(new Vector3f(normal).mul(4.0F * Mth.DEG_TO_RAD)).normalize().sub(side).mul(DOME), green, 0.0F);
            }
        }
        draw(buffer);
    }

    /** The gegenschein: sunlight scattered back off interplanetary dust, a faint oval glow exactly opposite the sun. */
    private static void drawGegenschein(Matrix4f pose, Vector3f sun, float strength) {
        Vector3f anti = new Vector3f(sun).negate().normalize();
        if (anti.y < 0.0F) return;
        Vector3f[] around = basis(anti);
        drawGlow(pose, anti, around[0], around[1], 0.0F, 11.0F, new float[]{1.0F, 0.95F, 0.85F}, 0.06F * strength);
    }

    /**
     * A Fata Morgana: over a cold sea, a layer of warm air bends light so the far horizon stretches up into towering
     * walls and pillars (ships and distant coasts become castles in the air) that grow, merge and shrink as you watch.
     */
    private static void drawFataMorgana(Matrix4f pose, float time, float strength) {
        RenderSystem.defaultBlendFunc();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] wall = {0.72F, 0.77F, 0.82F}, glint = {0.92F, 0.95F, 0.98F};
        int columns = 240;
        for (int i = 0; i < columns; i++) {
            float a0 = i / (float) columns * Mth.TWO_PI, a1 = (i + 1) / (float) columns * Mth.TWO_PI;
            float sector = a0 / Mth.TWO_PI * 16.0F;
            int s0 = (int) sector;
            float sea = Mth.lerp(sector - s0, SkySights.sea(s0), SkySights.sea(s0 + 1));
            if (sea < 0.5F) continue;
            // a skyline of stretched shapes along the horizon, rising and falling slowly
            float h = Mth.sin(i * 0.37F + time * 0.004F) * Mth.sin(i * 0.11F - time * 0.0023F) + 0.35F * Mth.sin(i * 1.3F + time * 0.011F);
            if (h < 0.15F) continue;
            float top = (0.25F + 2.4F * h) * Mth.DEG_TO_RAD, bottom = -0.3F * Mth.DEG_TO_RAD;
            float alpha = 0.5F * strength * (sea - 0.5F) * 2.0F;
            vertex(buffer, pose, sky(a0, bottom), wall, alpha);
            vertex(buffer, pose, sky(a1, bottom), wall, alpha);
            vertex(buffer, pose, sky(a1, top), glint, alpha * 0.6F);
            vertex(buffer, pose, sky(a0, top), glint, alpha * 0.6F);
        }
        draw(buffer);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    /**
     * The planets, wandering along the sun's path: Venus never far from the sun, the evening or the morning star by
     * turns and the brightest of them; Jupiter bright and creamy; Mars ruddy, brightest when it's opposite the sun
     * (where the moon is, so the two sometimes meet); Saturn a steady pale gold. They don't twinkle, which is how
     * you tell a planet from a star.
     */
    private static void drawPlanets(Matrix4f pose, float theta, float dayTime, Vector3f sun, float strength) {
        float days = dayTime / 24000.0F;
        // {elongation from the sun (degrees), how far off the sun's path, brightness, size, r, g, b, how dark the sky must be}
        float venus = 44.0F * Mth.sin(days / 48.0F * Mth.TWO_PI + 0.7F);
        float mars = 360.0F * frac(days / 52.0F + 0.63F), jupiter = 360.0F * frac(days / 36.0F + 0.21F), saturn = 360.0F * frac(days / 31.0F + 0.87F);
        float marsNear = Mth.square(Mth.sin(mars * 0.5F * Mth.DEG_TO_RAD)); // 1 opposite the sun
        float[][] planets = {
                {venus, 1.6F * Mth.sin(days * 0.37F), 1.0F, 0.42F, 1.0F, 0.98F, 0.9F, 0.0F},
                {jupiter, 1.1F, 0.75F, 0.34F, 1.0F, 0.96F, 0.86F, 0.12F},
                {mars, -1.3F, 0.3F + 0.5F * marsNear, 0.24F + 0.08F * marsNear, 1.0F, 0.55F, 0.33F, 0.15F},
                {saturn, 0.6F, 0.45F, 0.27F, 0.98F, 0.88F, 0.66F, 0.18F}};
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (float[] p : planets) {
            float along = p[0] * Mth.DEG_TO_RAD;
            if (Math.abs(Mth.wrapDegrees(p[0])) < 14.0F) continue; // lost in the sun's glare
            Vector3f dir = new Vector3f(-Mth.sin(theta + along), Mth.cos(theta + along), p[1] * Mth.DEG_TO_RAD).normalize();
            float dark = Mth.clamp((-sun.y - p[7]) * 8.0F + 1.0F, 0.0F, 1.0F);
            float alpha = strength * p[2] * dark * Mth.clamp(dir.y * 20.0F, 0.0F, 1.0F);
            if (alpha < 0.01F) continue;
            Vector3f[] b = basis(dir);
            float size = (float) Math.tan(p[3] * Mth.DEG_TO_RAD) * DOME;
            Vector3f c = new Vector3f(dir).mul(DOME);
            float[] col = {p[4], p[5], p[6]};
            vertex(buffer, pose, new Vector3f(c).add(new Vector3f(b[0]).mul(-size)).add(new Vector3f(b[1]).mul(-size)), col, alpha);
            vertex(buffer, pose, new Vector3f(c).add(new Vector3f(b[0]).mul(size)).add(new Vector3f(b[1]).mul(-size)), col, alpha);
            vertex(buffer, pose, new Vector3f(c).add(new Vector3f(b[0]).mul(size)).add(new Vector3f(b[1]).mul(size)), col, alpha);
            vertex(buffer, pose, new Vector3f(c).add(new Vector3f(b[0]).mul(-size)).add(new Vector3f(b[1]).mul(size)), col, alpha);
            any = true;
        }
        if (any) draw(buffer);
        else buffer.build();
        // a soft glow round the brighter ones
        for (float[] p : planets) {
            if (p[2] < 0.6F || Math.abs(Mth.wrapDegrees(p[0])) < 14.0F) continue;
            float along = p[0] * Mth.DEG_TO_RAD;
            Vector3f dir = new Vector3f(-Mth.sin(theta + along), Mth.cos(theta + along), p[1] * Mth.DEG_TO_RAD).normalize();
            float alpha = strength * p[2] * Mth.clamp((-sun.y - p[7]) * 8.0F + 1.0F, 0.0F, 1.0F) * Mth.clamp(dir.y * 20.0F, 0.0F, 1.0F);
            if (alpha < 0.01F) continue;
            Vector3f[] b = basis(dir);
            drawGlow(pose, dir, b[0], b[1], 0.0F, 1.6F, new float[]{p[4], p[5], p[6]}, 0.12F * alpha);
        }
    }

    private static float frac(float f) {
        return f - Mth.floor(f);
    }

    /** Earthshine: sunlight bounced off the world faintly lights the dark part of a crescent moon, grey-blue. */
    private static void drawEarthshine(Matrix4f pose, Vector3f moon, float strength) {
        Vector3f[] face = faceBasis(moon);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int n = 16;
        float half = Eclipses.MOON + 0.3F, cell = 2.0F * half / n;
        float[] col = {0.26F, 0.3F, 0.4F};
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -half + i * cell, y0 = -half + j * cell, x1 = x0 + cell, y1 = y0 + cell;
                vertex(buffer, pose, onFace(moon, face, x0, y0), col, 0.16F * strength * onMoon(x0, y0));
                vertex(buffer, pose, onFace(moon, face, x1, y0), col, 0.16F * strength * onMoon(x1, y0));
                vertex(buffer, pose, onFace(moon, face, x1, y1), col, 0.16F * strength * onMoon(x1, y1));
                vertex(buffer, pose, onFace(moon, face, x0, y1), col, 0.16F * strength * onMoon(x0, y1));
            }
        }
        draw(buffer);
    }

    private static float onMoon(float x, float y) {
        return Mth.clamp((Eclipses.MOON - 0.2F - Mth.sqrt(x * x + y * y)) / 0.6F, 0.0F, 1.0F);
    }

    /**
     * The moon low in the sky looks orange: its light comes through so much air that the blue is scattered out of
     * it. The harvest moon, the full moon nearest the autumn equinox, rises big and deep orange-gold.
     */
    private static void drawMoonTint(Matrix4f pose, Vector3f moon, float strength, boolean harvest) {
        Vector3f[] face = faceBasis(moon);
        float[] tint = harvest ? new float[]{1.0F, 0.66F, 0.32F} : new float[]{1.0F, 0.78F, 0.55F};
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int n = 16;
        float half = Eclipses.MOON + 0.3F, cell = 2.0F * half / n;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -half + i * cell, y0 = -half + j * cell, x1 = x0 + cell, y1 = y0 + cell;
                tintVertex(buffer, pose, moon, face, x0, y0, tint, strength);
                tintVertex(buffer, pose, moon, face, x1, y0, tint, strength);
                tintVertex(buffer, pose, moon, face, x1, y1, tint, strength);
                tintVertex(buffer, pose, moon, face, x0, y1, tint, strength);
            }
        }
        draw(buffer);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (harvest) {
            Vector3f[] around = basis(moon);
            drawGlow(pose, new Vector3f(moon).normalize(), around[0], around[1], 0.0F, 11.0F, new float[]{1.0F, 0.62F, 0.3F}, 0.1F * strength);
        }
    }

    private static void tintVertex(BufferBuilder buffer, Matrix4f pose, Vector3f moon, Vector3f[] face, float x, float y, float[] tint, float strength) {
        float k = strength * Mth.clamp((Eclipses.MOON + 0.4F - Mth.sqrt(x * x + y * y)) / 0.6F, 0.0F, 1.0F);
        Vector3f p = onFace(moon, face, x, y);
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(Mth.lerp(k, 1.0F, tint[0]), Mth.lerp(k, 1.0F, tint[1]), Mth.lerp(k, 1.0F, tint[2]), 1.0F);
    }

    /**
     * A spray bow: on a bright, rough day the spray blown off breaking waves catches the sun behind you, and the
     * low ends of a rainbow stand in it just over the sea, vivid where the spray is thickest.
     */
    private static void drawSprayBow(Matrix4f pose, Vector3f sun, float alpha) {
        Vector3f anti = new Vector3f(sun).negate().normalize();
        Vector3f[] b = basis(anti);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 128, bands = RAINBOW.length + 1;
        float inner = 40.0F * Mth.DEG_TO_RAD, outer = 42.5F * Mth.DEG_TO_RAD;
        for (int s = 0; s < segments; s++) {
            float phi0 = s / (float) segments * Mth.TWO_PI, phi1 = (s + 1) / (float) segments * Mth.TWO_PI;
            for (int k = 0; k < bands; k++) {
                float rho0 = Mth.lerp(k / (float) bands, inner, outer), rho1 = Mth.lerp((k + 1) / (float) bands, inner, outer);
                Vector3f p00 = ringPoint(anti, b[0], b[1], phi0, rho0), p10 = ringPoint(anti, b[0], b[1], phi1, rho0);
                Vector3f p11 = ringPoint(anti, b[0], b[1], phi1, rho1), p01 = ringPoint(anti, b[0], b[1], phi0, rho1);
                float z00 = sprayZone(p00), z10 = sprayZone(p10), z11 = sprayZone(p11), z01 = sprayZone(p01);
                if (z00 + z10 + z11 + z01 <= 0.0F) continue;
                // vivid: the spray drops are big, and the bow stands against the darker sea and spray
                float[] c0 = bandColor(k - 1), c1 = bandColor(k);
                float a0 = k == 0 ? 0.0F : alpha * 0.45F, a1 = k == bands - 1 ? 0.0F : alpha * 0.45F;
                vertex(buffer, pose, p00, c0, a0 * z00);
                vertex(buffer, pose, p10, c0, a0 * z10);
                vertex(buffer, pose, p11, c1, a1 * z11);
                vertex(buffer, pose, p01, c1, a1 * z01);
            }
        }
        draw(buffer);
    }

    /** How much spray hangs in this direction: only just over the horizon, and only over open sea. */
    private static float sprayZone(Vector3f p) {
        Vector3f d = new Vector3f(p).normalize();
        float elevation = (float) Math.asin(Mth.clamp(d.y, -1.0F, 1.0F)) * Mth.RAD_TO_DEG;
        if (elevation < -0.5F || elevation > 7.0F) return 0.0F;
        float azimuth = (float) Math.atan2(d.x, -d.z);
        if (azimuth < 0.0F) azimuth += Mth.TWO_PI;
        float sector = azimuth / Mth.TWO_PI * 16.0F;
        int s0 = (int) sector;
        float sea = Mth.lerp(sector - s0, SkySights.sea(s0), SkySights.sea(s0 + 1));
        return Mth.clamp((sea - 0.4F) * 2.5F, 0.0F, 1.0F) * Mth.clamp(1.0F - elevation / 7.0F, 0.0F, 1.0F) * Mth.clamp(elevation + 0.5F, 0.0F, 1.0F);
    }

    /** A point on the sky dome; azimuth 0 is due north (-Z). */
    private static Vector3f sky(float azimuth, float elevation) {
        float c = Mth.cos(elevation);
        return new Vector3f(Mth.sin(azimuth) * c, Mth.sin(elevation), -Mth.cos(azimuth) * c).mul(DOME);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vector3f p, float[] color, float alpha) {
        buffer.addVertex(pose, p.x, p.y, p.z).setColor(color[0], color[1], color[2], alpha);
    }

    /** A point on the dome, offset from a centre direction along a streak (along) and across it. */
    private static Vector3f streakPoint(Vector3f centre, Vector3f along, Vector3f across, float x, float y) {
        return new Vector3f(centre).add(new Vector3f(along).mul(x)).add(new Vector3f(across).mul(y)).normalize().mul(DOME);
    }

    /** The direction along the sky at a point that runs the way the wind does, and the one across it. */
    private static Vector3f[] windFrame(Vector3f centre, float windAzimuth) {
        Vector3f w = new Vector3f(Mth.sin(windAzimuth), 0.0F, -Mth.cos(windAzimuth));
        Vector3f along = new Vector3f(w).sub(new Vector3f(centre).mul(centre.dot(w)));
        if (along.lengthSquared() < 1.0E-4F) along.set(1.0F, 0.0F, 0.0F);
        along.normalize();
        return new Vector3f[]{along, new Vector3f(centre).cross(along).normalize()};
    }

    private static float skyHash(int seed, int salt) {
        return (Mth.murmurHash3Mixer(seed * 0x2545F491 + salt * 0x9E3779B9) >>> 8) / (float) (1 << 24);
    }

    /**
     * Cirrus: long, fine streaks of ice crystals combed out by the wind high up, many ending in a hooked tuft where
     * the crystals fall out of the fast air above into the slower air below ("mares' tails").
     */
    private static void drawCirrus(Matrix4f pose, float time, float windAzimuth, float[] tint, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 14;
        for (int s = 0; s < 26; s++) {
            float h = skyHash(s, 1);
            if (h > strength * 1.15F) continue;
            float show = Mth.clamp((strength * 1.15F - h) * 5.0F, 0.0F, 1.0F);
            // spread over the sky, drifting slowly downwind
            float az = skyHash(s, 2) * Mth.TWO_PI + time * 0.000004F, el = (10.0F + 62.0F * skyHash(s, 3)) * Mth.DEG_TO_RAD;
            Vector3f centre = new Vector3f(Mth.sin(az) * Mth.cos(el), Mth.sin(el), -Mth.cos(az) * Mth.cos(el));
            Vector3f[] frame = windFrame(centre, windAzimuth + (skyHash(s, 4) - 0.5F) * 0.5F);
            float length = 0.3F + 0.45F * skyHash(s, 5), width = 0.03F + 0.04F * skyHash(s, 6);
            float hook = (skyHash(s, 7) - 0.5F) * 0.5F * length;
            float alpha = 0.16F * show * horizonFade(new Vector3f(centre).mul(DOME));
            // a soft band of overlapping fibres, not one hard line
            for (int strand = 0; strand < 5; strand++) {
                float offset = (skyHash(s * 5 + strand, 8) - 0.5F) * width * 1.4F;
                float strandAlpha = alpha * (0.35F + 0.4F * skyHash(s * 5 + strand, 9));
                float w = width * (0.5F + 0.6F * skyHash(s * 5 + strand, 10));
                for (int i = 0; i < segments; i++) {
                    float t0 = i / (float) segments, t1 = (i + 1) / (float) segments;
                    // fine at the tail, thickening to a tuft at the hooked head
                    float x0 = (t0 - 0.5F) * length, x1 = (t1 - 0.5F) * length;
                    float y0 = offset + hook * t0 * t0 * t0, y1 = offset + hook * t1 * t1 * t1;
                    float a0 = strandAlpha * profile(t0), a1 = strandAlpha * profile(t1);
                    float w0 = w * (0.4F + 1.2F * t0), w1 = w * (0.4F + 1.2F * t1);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x0, y0 - w0), tint, 0.0F);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x1, y1 - w1), tint, 0.0F);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x1, y1), tint, a1);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x0, y0), tint, a0);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x0, y0), tint, a0);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x1, y1), tint, a1);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x1, y1 + w1), tint, 0.0F);
                    vertex(buffer, pose, streakPoint(centre, frame[0], frame[1], x0, y0 + w0), tint, 0.0F);
                }
            }
        }
        draw(buffer);
    }

    /** How bright a cirrus streak is along its length: faint at the tail, brightest towards the head. */
    private static float profile(float t) {
        return Mth.clamp(Mth.sin(t * Mth.PI) * (0.5F + 0.7F * t), 0.0F, 1.0F);
    }

    /**
     * A mackerel sky (cirrocumulus): a patch of sky covered in tiny cloudlets set out in neat rows, like the
     * scales on a mackerel's back, where waves in the high air ripple the cloud layer.
     */
    private static void drawMackerel(Matrix4f pose, float time, float windAzimuth, float[] tint, float strength) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // the patch sits up the sky, upwind of overhead, creeping along with the wind
        float az = windAzimuth + Mth.PI + 0.4F * Mth.sin(time * 0.00003F), el = 48.0F * Mth.DEG_TO_RAD;
        Vector3f centre = new Vector3f(Mth.sin(az) * Mth.cos(el), Mth.sin(el), -Mth.cos(az) * Mth.cos(el));
        Vector3f[] frame = windFrame(centre, windAzimuth);
        float drift = time * 0.00004F;
        int rows = 20, cols = 30;
        float rowGap = 0.036F, colGap = 0.032F, size = 0.019F;
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) {
                int seed = r * 131 + k;
                // the ripples run across the wind, in rows one behind another
                float x = (r - rows / 2.0F) * rowGap + ((drift / rowGap) % 1.0F) * rowGap + (skyHash(seed, 11) - 0.5F) * rowGap * 0.35F;
                float y = (k - cols / 2.0F) * colGap + (skyHash(seed, 12) - 0.5F) * colGap * 0.5F + 0.02F * Mth.sin(r * 0.9F);
                // a ragged-edged patch, thinning out towards its edges
                float ex = x / (rows * rowGap * 0.5F), ey = y / (cols * colGap * 0.5F);
                float edge = 1.0F - Mth.sqrt(ex * ex + ey * ey) - 0.25F * (skyHash(seed, 13) - 0.5F);
                if (edge <= 0.0F || skyHash(seed, 14) > 0.35F + strength) continue;
                float a = 0.26F * strength * Mth.clamp(edge * 3.0F, 0.0F, 1.0F);
                float sx = size * (0.7F + 0.6F * skyHash(seed, 15)), sy = sx * 1.3F;
                Vector3f mid = streakPoint(centre, frame[0], frame[1], x, y);
                if (mid.y < 2.0F) continue;
                // a soft little puff: bright in the middle, fading to nothing at its edges
                Vector3f[] ring = new Vector3f[8];
                for (int q = 0; q < 8; q++) {
                    float phi = q * Mth.TWO_PI / 8.0F;
                    ring[q] = streakPoint(centre, frame[0], frame[1], x + Mth.cos(phi) * sx, y + Mth.sin(phi) * sy);
                }
                for (int q = 0; q < 8; q += 2) {
                    vertex(buffer, pose, mid, tint, a);
                    vertex(buffer, pose, ring[q], tint, 0.0F);
                    vertex(buffer, pose, ring[(q + 1) % 8], tint, 0.0F);
                    vertex(buffer, pose, ring[(q + 2) % 8], tint, 0.0F);
                }
            }
        }
        draw(buffer);
    }

    /** Cirrostratus: a thin, milky veil over the whole sky, turning the blue to white ahead of the rain. */
    private static void drawVeil(Matrix4f pose, float[] tint, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 36;
        for (int i = 0; i < steps; i++) {
            float a0 = i * Mth.TWO_PI / steps, a1 = (i + 1) * Mth.TWO_PI / steps;
            for (int r = 0; r < 9; r++) {
                float e0 = (r * 10.0F) * Mth.DEG_TO_RAD, e1 = ((r + 1) * 10.0F) * Mth.DEG_TO_RAD;
                float f0 = r == 0 ? 0.0F : 1.0F;
                vertexSky(buffer, pose, a0, e0, tint, alpha * f0);
                vertexSky(buffer, pose, a1, e0, tint, alpha * f0);
                vertexSky(buffer, pose, a1, e1, tint, alpha);
                vertexSky(buffer, pose, a0, e1, tint, alpha);
            }
        }
        draw(buffer);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private SkyEffectsRenderer() {}
}
