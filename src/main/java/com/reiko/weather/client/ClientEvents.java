package com.reiko.weather.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.reiko.weather.client.particle.EmberParticle;
import com.reiko.weather.client.particle.FunnelParticle;
import com.reiko.weather.client.render.BallLightningRenderer;
import com.reiko.weather.client.render.WeatherVaneRenderer;
import com.reiko.weather.client.render.WindmillRenderer;
import com.reiko.weather.client.sound.AmbientSounds;
import com.reiko.weather.network.OtherworldPayload;
import com.reiko.weather.registry.ModBlockEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.reiko.weather.client.particle.HailParticle;
import com.reiko.weather.client.particle.RippleParticle;
import com.reiko.weather.client.particle.StreakParticle;
import com.reiko.weather.client.particle.WindBlownParticle;
import com.reiko.weather.client.sound.WindSoundInstance;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.network.SyncedWeather;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ClientEvents {
    private static final float MIN_FOG_DISTANCE = 10.0F;
    private static WindSoundInstance windSound;

    // ------------------------------------------------------------------ mod bus

    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.HAIL.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new HailParticle(level, x, y, z, xd, yd, zd, sprites));
        // graupel: hail's soft little cousin, smaller and whiter
        event.registerSpriteSet(ModParticles.GRAUPEL.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    var p = new HailParticle(level, x, y, z, xd, yd, zd, sprites);
                    p.scale(0.55F);
                    p.setColor(1.0F, 1.0F, 1.0F);
                    return p;
                });
        // dew glinting on the grass at dawn
        event.registerSpriteSet(ModParticles.DEW.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    var p = new com.reiko.weather.client.particle.GlitterParticle(level, x, y, z, 0, 0, 0, sprites);
                    p.setColor(0.85F, 0.95F, 1.0F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.SLEET.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    StreakParticle p = new StreakParticle(level, x, y, z, xd, yd, zd, sprites, 0.45F, 0.025F, false);
                    p.setColor(0.85F, 0.92F, 1.0F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.DOWNPOUR.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    StreakParticle p = new StreakParticle(level, x, y, z, xd, yd, zd, sprites, 1.1F, 0.02F, true);
                    p.setColor(0.62F, 0.7F, 0.85F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.SNOWFLAKE.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.SNOW));
        event.registerSpriteSet(ModParticles.BLIZZARD.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.BLIZZARD));
        event.registerSpriteSet(ModParticles.SAND.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.SAND);
                    float shade = 0.85F + level.random.nextFloat() * 0.15F;
                    p.setColor(0.86F * shade, 0.72F * shade, 0.5F * shade);
                    return p;
                });
        // pine pollen: fine yellow motes drifting on the breeze
        event.registerSpriteSet(ModParticles.POLLEN.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.DUST);
                    float shade = 0.9F + level.random.nextFloat() * 0.1F;
                    p.setColor(0.95F * shade, 0.85F * shade, 0.35F * shade);
                    p.scale(0.1F); // fine dust, not blobs: the haze comes from there being a lot of it
                    return p;
                });
        // windblown sea foam: white flecks torn off the waves
        event.registerSpriteSet(ModParticles.SEA_FOAM.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.SPRAY);
                    p.setColor(0.97F, 0.97F, 0.94F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.DUST.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.DUST);
                    int color = ClientWeather.fogAutoGray() ? 0xB8A890 : rgb(ClientWeather.fogR(), ClientWeather.fogG(), ClientWeather.fogB());
                    float shade = 0.85F + level.random.nextFloat() * 0.15F;
                    p.setColor((color >> 16 & 255) / 255.0F * shade, (color >> 8 & 255) / 255.0F * shade, (color & 255) / 255.0F * shade);
                    return p;
                });
        event.registerSpriteSet(ModParticles.SEA_SPARKLE.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    var p = new com.reiko.weather.client.particle.GlitterParticle(level, x, y, z, xd, yd, zd, sprites);
                    p.setColor(0.25F + level.random.nextFloat() * 0.15F, 0.72F + level.random.nextFloat() * 0.2F, 1.0F);
                    p.scale(2.6F);
                    p.setLifetime(18 + level.random.nextInt(24));
                    return p;
                });
        event.registerSpriteSet(ModParticles.DIAMOND_DUST.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new com.reiko.weather.client.particle.GlitterParticle(level, x, y, z, xd, yd, zd, sprites));
        event.registerSpriteSet(ModParticles.ASH.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.ASH);
                    float shade = 0.35F + level.random.nextFloat() * 0.3F;
                    p.setColor(shade, shade * 0.97F, shade * 0.94F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.LEAF.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.LEAF);
                    int color = BiomeColors.getAverageFoliageColor(level, BlockPos.containing(x, y, z));
                    float shade = 0.75F + level.random.nextFloat() * 0.35F;
                    p.setColor(Math.min(1, (color >> 16 & 255) / 255.0F * shade), Math.min(1, (color >> 8 & 255) / 255.0F * shade), Math.min(1, (color & 255) / 255.0F * shade));
                    return p;
                });
        event.registerSpriteSet(ModParticles.SMOKE_HAZE.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.SMOKE_HAZE);
                    float shade = 0.62F + level.random.nextFloat() * 0.08F;
                    p.setColor(shade, shade * 1.02F, shade * 1.08F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.ASH_CLOUD.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.ASH_CLOUD);
                    float shade = 0.42F + level.random.nextFloat() * 0.2F;
                    p.setColor(shade, shade * 0.97F, shade * 0.93F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.MIST.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.MIST);
                    p.setColor(0.94F, 0.95F, 0.97F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.STEAM_FOG.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.STEAM);
                    p.setColor(0.95F, 0.96F, 0.98F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.SPRAY.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    WindBlownParticle p = new WindBlownParticle(level, x, y, z, xd, yd, zd, sprites, WindBlownParticle.Kind.SPRAY);
                    p.setColor(0.97F, 0.98F, 1.0F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.RIPPLE.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new RippleParticle(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.EMBER.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new EmberParticle(level, x, y, z, xd, yd, zd, sprites));
        event.registerSpriteSet(ModParticles.BREATH.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new com.reiko.weather.client.particle.BreathParticle(level, x, y, z, xd, yd, zd, sprites));
        event.registerSpriteSet(ModParticles.MIDGE.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new com.reiko.weather.client.particle.InsectParticle(level, x, y, z, sprites, false));
        event.registerSpriteSet(ModParticles.MAYFLY.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new com.reiko.weather.client.particle.InsectParticle(level, x, y, z, sprites, true));
        event.registerSpriteSet(ModParticles.SWALLOW.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new com.reiko.weather.client.particle.SwallowParticle(level, x, y, z, xd, zd, sprites));
        event.registerSpriteSet(ModParticles.FIREFLY.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new com.reiko.weather.client.particle.FireflyParticle(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.WORM.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> {
                    var p = new com.reiko.weather.client.particle.WormParticle(level, x, y, z, sprites);
                    p.setColor(0.86F, 0.55F, 0.55F);
                    return p;
                });
        event.registerSpriteSet(ModParticles.FUNNEL.get(), sprites -> {
            FunnelParticle.sprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> null;
        });
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TORNADO.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.BALL_LIGHTNING.get(), BallLightningRenderer::new);
        event.registerEntityRenderer(ModEntities.CLOUD_SEEDING_ROCKET.get(), ThrownItemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WEATHER_VANE.get(), WeatherVaneRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WINDMILL.get(), WindmillRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FORECAST_BOARD.get(), com.reiko.weather.client.render.ForecastBoardRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), com.reiko.weather.client.render.MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.VOLCANIC_BOMB.get(), com.reiko.weather.client.render.VolcanicBombRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_FLOE.get(), com.reiko.weather.client.render.IceFloeRenderer::new);
        event.registerEntityRenderer(ModEntities.TUMBLEWEED.get(), com.reiko.weather.client.render.TumbleweedRenderer::new);
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(WeatherVaneRenderer.ARROW);
        event.register(WindmillRenderer.HEAD);
        event.register(WindmillRenderer.BLADES);
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : 0x3F76E4,
                ModBlocks.PUDDLE.get(), ModBlocks.RAIN_BARREL.get());
        event.register((state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos)
                : net.minecraft.world.level.FoliageColor.getDefaultColor(), ModBlocks.FALLEN_BRANCH.get());
        event.register((state, level, pos, tintIndex) -> leafPileColor(pos), ModBlocks.LEAF_PILE.get());
    }

    private static final int[] LEAF_PILE_COLORS = {0xD9772A, 0xB8432A, 0xD8B23A, 0xA8652E, 0x9C5A2E, 0xC98A30};

    /** Fallen leaves in a patchwork of autumn colours. */
    private static int leafPileColor(BlockPos pos) {
        if (pos == null) {
            return LEAF_PILE_COLORS[0];
        }
        int hash = Mth.murmurHash3Mixer(pos.getX() * 73856093 ^ pos.getZ() * 19349663);
        return LEAF_PILE_COLORS[Math.floorMod(hash, LEAF_PILE_COLORS.length)];
    }

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ForecastHud.ID, ForecastHud::render);
        event.registerAbove(ForecastHud.ID, TemperatureHud.ID, TemperatureHud::render);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ScreenRaindrops.ID, ScreenRaindrops::render);
        event.registerAbove(ScreenRaindrops.ID, SnowGlare.ID, SnowGlare::render);
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Vec3 cam = event.getCamera().getPosition();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            WetGroundRenderer.render(cam);
            Footprints.render(cam, partialTick);
            CloudDeckRenderer.render(cam, partialTick);
            Mirage.render(event);
            VolcanoClient.collectPlumes(event.getCamera(), partialTick);
            CloudPuffs.collect(event.getCamera(), partialTick);
            CloudPuffs.inversion(event.getCamera());
            CloudPuffs.lenticular(event.getCamera());
            CloudPuffs.banner(event.getCamera());
            DriftingClouds.collect(event.getCamera(), partialTick);
            Cumulus.collect(event.getCamera(), partialTick);
            CloudWaterfalls.collect(event.getCamera(), partialTick);
            Scud.collect(event.getCamera(), partialTick);
            Castellanus.collect(event.getCamera(), partialTick);
            SnowPlumes.collect(event.getCamera(), partialTick);
            StormSkies.collect(event.getCamera(), partialTick);
            CloudForms.collect(event.getCamera(), partialTick);
            SkySights.collectSprites(event.getCamera(), partialTick);
            SkySights.collectUpper(event.getCamera(), partialTick);
            PuffRenderer.render(event.getCamera());
            WaterMist.renderRainbows(event);
            GroundOptics.render(event);
            SkyEffectsRenderer.renderSunbeams(event);
            NatureEffects.renderFlocks(event.getCamera(), partialTick);
        }
    }

    // ------------------------------------------------------------------ game bus

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        ClientWeather.tick(mc);
        WeatherParticleSpawner.tick(mc);
        SeasonalColors.tick(mc);
        OtherworldClient.tick(mc);
        AmbientSounds.tick(mc);
        TemperatureHud.tick(mc);
        ShootingStars.tick(mc);
        Atmosphere.tick(mc);
        Footprints.tick(mc);
        ScreenRaindrops.tick(mc);
        SnowGlare.tick(mc);
        GroundAir.tick(mc);
        HeatShimmer.tick(mc);
        com.reiko.weather.client.sound.WildlifeSounds.tick(mc);
        RoughSeas.tick(mc);
        NatureEffects.tick(mc);
        ParagliderClient.tick(mc);
        VolcanoClient.tick(mc);
        StaticElectricity.tick(mc);
        DiamondDust.tick(mc);
        Mirage.tick(mc);
        WaterMist.tick(mc);
        SkySights.tick(mc);
        ShoreAndIce.tick(mc);
        AirAndWater.tick(mc);
        DriftingClouds.tick(mc);
        Cumulus.tick(mc);
        StormSkies.tick(mc);
        DampAir.tick(mc);
        InsectSwarms.tick(mc);
        SkyTints.tick(mc);
        SingingDunes.tick(mc);
        CloudWaterfalls.tick(mc);
        HighClouds.tick(mc);
        Scud.tick(mc);
        HailFog.tick(mc);
        Castellanus.tick(mc);
        SnowPlumes.tick(mc);
        LakeWhitings.tick(mc);
        Swallows.tick(mc);
        ItemTooltips.tick(mc);
        pushLocalPlayer(mc);
        updateWindSound(mc);
    }

    private static void pushLocalPlayer(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !ClientWeather.active() || player.isSpectator()
                || !WeatherConfig.SPEC.isLoaded() || !WeatherConfig.WIND_PUSHES_ENTITIES.get()) {
            return;
        }
        if (Wind.isExposed(mc.level, BlockPos.containing(player.getEyePosition()))) {
            Wind.push(player, ClientWeather.windAngle(), ClientWeather.wind(), mc.level.getGameTime());
        }
    }

    private static void updateWindSound(Minecraft mc) {
        if (mc.level == null) {
            windSound = null;
            return;
        }
        if (WindSoundInstance.targetVolume() > 0.02F && (windSound == null || windSound.isStopped()
                || !mc.getSoundManager().isActive(windSound))) {
            windSound = new WindSoundInstance();
            mc.getSoundManager().play(windSound);
        }
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        com.reiko.weather.weather.Monsoon.collect(event.getPlayer().level().registryAccess());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncedWeather.reset();
        ClientWeather.reset();
        AmbientSounds.reset();
        ShootingStars.reset();
        Atmosphere.reset();
        Footprints.reset();
        ScreenRaindrops.reset();
        HeatShimmer.reset();
        com.reiko.weather.client.sound.WildlifeSounds.reset();
        RoughSeas.reset();
        NatureEffects.reset();
        VolcanoClient.reset();
        Mirage.reset();
        Cumulus.reset();
        DriftingClouds.reset();
        StormSkies.reset();
        DampAir.reset();
        InsectSwarms.reset();
        SkyTints.reset();
        CloudWaterfalls.reset();
        HighClouds.reset();
        Scud.reset();
        HailFog.reset();
        Castellanus.reset();
        SnowPlumes.reset();
        LakeWhitings.reset();
        com.reiko.weather.network.TemperaturePayload.clientBody = Float.NaN;
        com.reiko.weather.network.TemperaturePayload.clientTarget = Float.NaN;
        com.reiko.weather.weather.CustomWeathers.resetClient();
        OtherworldPayload.clientWeather = 0;
        windSound = null;
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() == FogType.NONE && !blinded(event.getCamera().getEntity()) && OtherworldClient.applyFog(event)) {
            return;
        }
        float density = ClientWeather.fogDensity();
        if (density <= 0.01F || event.getType() != FogType.NONE || blinded(event.getCamera().getEntity())) {
            return;
        }
        float far = event.getFarPlaneDistance();
        // visibility in blocks: ~160 in rain, ~55 in a downpour, ~24 in fog, ~14 in a whiteout
        float visibility = Math.max(MIN_FOG_DISTANCE, 400.0F * (float) Math.exp(-3.6F * density));
        float newFar = Math.min(far * (1.0F - 0.5F * density), visibility);
        if (newFar >= far) {
            return;
        }
        float newNear = event.getMode() == FogRenderer.FogMode.FOG_SKY ? 0.0F : newFar * (0.12F + 0.7F * (1.0F - density));
        event.setFarPlaneDistance(newFar);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), newNear));
        if (density > 0.4F) {
            event.setFogShape(FogShape.SPHERE);
        }
        event.setCanceled(true);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() == FogType.NONE && !blinded(event.getCamera().getEntity())
                && OtherworldClient.applyFogColor(event)) {
            return;
        }
        float bloom = com.reiko.weather.network.SyncedWeather.bloom();
        if (bloom > 0.05F && event.getCamera().getFluidInCamera() == FogType.WATER && com.reiko.weather.config.ClientConfig.ALGAL_BLOOM_TINT.get() && Minecraft.getInstance().level != null) {
            // under an algal bloom the water goes a murky rust-red
            var biome = Minecraft.getInstance().level.getBiome(event.getCamera().getBlockPosition());
            if (biome.is(net.minecraft.world.level.biome.Biomes.WARM_OCEAN) || biome.is(net.minecraft.world.level.biome.Biomes.LUKEWARM_OCEAN)
                    || biome.is(net.minecraft.world.level.biome.Biomes.DEEP_LUKEWARM_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) {
                float k = Math.min(0.75F, bloom);
                event.setRed(Mth.lerp(k, event.getRed(), 0.42F));
                event.setGreen(Mth.lerp(k, event.getGreen(), 0.22F));
                event.setBlue(Mth.lerp(k, event.getBlue(), 0.12F));
            }
        }
        float pollen = AirAndWater.pollen();
        if (pollen > 0.02F && event.getCamera().getFluidInCamera() == FogType.NONE) {
            // a faint yellow haze of pollen in the air
            float k = pollen * 0.12F;
            event.setRed(Mth.lerp(k, event.getRed(), 0.95F));
            event.setGreen(Mth.lerp(k, event.getGreen(), 0.88F));
            event.setBlue(Mth.lerp(k, event.getBlue(), 0.5F));
        }
        float greenLight = SkyTints.green(), goldenHaze = SkyTints.golden();
        float cloudMist = DampAir.cloudMist();
        if (cloudMist > 0.02F && event.getCamera().getFluidInCamera() == FogType.NONE && Minecraft.getInstance().level != null) {
            // the cloud forest's mist: soft, pale grey, as bright as the day
            float light = Minecraft.getInstance().level.getSkyDarken(1.0F);
            float k = cloudMist * 0.7F;
            event.setRed(Mth.lerp(k, event.getRed(), 0.8F * light));
            event.setGreen(Mth.lerp(k, event.getGreen(), 0.83F * light));
            event.setBlue(Mth.lerp(k, event.getBlue(), 0.85F * light));
        }
        float haze = AirAndWater.blueHaze();
        if (haze > 0.02F && event.getCamera().getFluidInCamera() == FogType.NONE) {
            // the blue haze over hot forests (the Blue Ridge, the Blue Mountains)
            float k = haze * 0.32F;
            event.setRed(Mth.lerp(k, event.getRed(), 0.55F));
            event.setGreen(Mth.lerp(k, event.getGreen(), 0.67F));
            event.setBlue(Mth.lerp(k, event.getBlue(), 0.86F));
        }
        float eclipse = ClientWeather.eclipseLoss();
        if (eclipse > 0.01F && event.getCamera().getFluidInCamera() == FogType.NONE) {
            // the moon's shadow darkens the haze too, to a dusky glow (lit from beyond the shadow, all round)
            event.setRed(Mth.lerp(eclipse * 0.88F, event.getRed(), 0.17F));
            event.setGreen(Mth.lerp(eclipse * 0.88F, event.getGreen(), 0.13F));
            event.setBlue(Mth.lerp(eclipse * 0.88F, event.getBlue(), 0.16F));
        }
        float blood = ClientWeather.bloodMoon();
        if (blood > 0.01F && event.getCamera().getFluidInCamera() == FogType.NONE) {
            // a blood moon stains the night air red
            event.setRed(Mth.lerp(blood * 0.5F, event.getRed(), Math.max(event.getRed(), 0.12F)));
            event.setGreen(event.getGreen() * (1.0F - 0.45F * blood));
            event.setBlue(event.getBlue() * (1.0F - 0.45F * blood));
        }
        float weight = ClientWeather.fogColorWeight();
        Minecraft mc = Minecraft.getInstance();
        if (weight <= 0.01F || mc.level == null || event.getCamera().getFluidInCamera() != FogType.NONE
                || blinded(event.getCamera().getEntity())) {
            tintedLight(event, greenLight, goldenHaze);
            return;
        }
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        float tr, tg, tb;
        float daylight = (float) Math.sqrt(mc.level.getSkyDarken((float) event.getPartialTick()));
        if (ClientWeather.mistDominates()) {
            tr = 0.9F * daylight;
            tg = 0.91F * daylight;
            tb = 0.93F * daylight;
        } else if (ClientWeather.fogAutoGray()) {
            float lum = r * 0.3F + g * 0.59F + b * 0.11F;
            tr = tg = lum;
            tb = lum * 1.04F;
        } else {
            tr = ClientWeather.fogR() * daylight;
            tg = ClientWeather.fogG() * daylight;
            tb = ClientWeather.fogB() * daylight;
        }
        event.setRed(Mth.lerp(weight, r, tr));
        event.setGreen(Mth.lerp(weight, g, tg));
        event.setBlue(Mth.lerp(weight, b, Math.min(1.0F, tb)));
        tintedLight(event, greenLight, goldenHaze);
    }

    /**
     * Colours laid over whatever the haze would otherwise be: the eerie green light under a big hailstorm, and the
     * soft golden haze of an Indian summer. (Last, so the weather's own fog colour doesn't wash them out.)
     */
    private static void tintedLight(ViewportEvent.ComputeFogColor event, float green, float golden) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float hail = HailFog.fog();
        if (hail > 0.02F) {
            // the hail fog is a pale, cold grey-white
            float lum = event.getRed() * 0.3F + event.getGreen() * 0.59F + event.getBlue() * 0.11F;
            float k = hail * 0.5F;
            event.setRed(Mth.lerp(k, event.getRed(), lum * 0.98F));
            event.setGreen(Mth.lerp(k, event.getGreen(), lum));
            event.setBlue(Mth.lerp(k, event.getBlue(), Math.min(1.0F, lum * 1.04F)));
        }
        if (golden > 0.02F) {
            float lum = event.getRed() * 0.3F + event.getGreen() * 0.59F + event.getBlue() * 0.11F;
            float k = golden * 0.5F;
            event.setRed(Mth.lerp(k, event.getRed(), Math.min(1.0F, lum * 1.14F)));
            event.setGreen(Mth.lerp(k, event.getGreen(), lum * 0.99F));
            event.setBlue(Mth.lerp(k, event.getBlue(), lum * 0.72F));
        }
        if (green > 0.02F) {
            float lum = event.getRed() * 0.3F + event.getGreen() * 0.59F + event.getBlue() * 0.11F;
            float k = green * 0.6F;
            event.setRed(Mth.lerp(k, event.getRed(), lum * 0.62F));
            event.setGreen(Mth.lerp(k, event.getGreen(), Math.min(1.0F, lum * 1.12F)));
            event.setBlue(Mth.lerp(k, event.getBlue(), lum * 0.88F));
        }
    }

    private static boolean blinded(Entity entity) {
        return entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private static int rgb(float r, float g, float b) {
        return ((int) (r * 255) & 255) << 16 | ((int) (g * 255) & 255) << 8 | ((int) (b * 255) & 255);
    }

    private ClientEvents() {}
}
