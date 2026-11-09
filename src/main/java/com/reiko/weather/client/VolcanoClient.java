package com.reiko.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.client.sound.AmbientLoop;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.network.VolcanoPayload;
import com.reiko.weather.registry.ModParticles;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.volcano.Volcano;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What volcanoes look, sound and feel like: the eruption column (a tower of ash puffs leaning downwind,
 * glowing at the base, visible from far beyond render distance), lava fountains and smoke over the crater,
 * the rumble of a waking volcano, eruption blasts that arrive late with distance, and the ground shaking.
 */
public final class VolcanoClient {
    private static final ResourceLocation PUFF = ReikosWeather.id("textures/environment/volcano_plume.png");
    private static final RandomSource RANDOM = RandomSource.create();
    /** Sound crosses about 17 blocks a tick. */
    private static final double SOUND_SPEED = 17.0;

    private record Blast(long due, float volume, float kick) {}

    private static final List<Blast> blasts = new ArrayList<>();
    private static float shake, prevShake, kick;
    private static float tremor;
    private static int tremorTicks;
    /** An earthquake (not a volcano's): how hard, and how long it has left of how long. */
    private static float quakeStrength;
    private static int quakeTicks, quakeLength;

    /** An earthquake starts shaking the ground here. */
    public static void quake(float strength, int ticks) {
        quakeStrength = Math.max(strength, quakeTicks > 0 ? quakeStrength : 0.0F);
        quakeTicks = quakeLength = Math.max(1, ticks);
        Minecraft mc = Minecraft.getInstance();
        if (ClientConfig.SOUNDS.get() && mc.player != null) {
            mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.EARTHQUAKE.get().getLocation(), SoundSource.WEATHER,
                    Math.min(1.0F, 0.4F + strength), 0.8F + RANDOM.nextFloat() * 0.2F, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        }
    }
    private static float rumbleVolume;
    private static AmbientLoop rumble;
    /** Phase of each volcano last tick, to catch an eruption starting (keyed by position). */
    private static final java.util.Map<Long, Integer> lastPhase = new java.util.HashMap<>();

    /** The current state of a synced volcano, with its progress moved on since the last update. */
    private record Live(VolcanoPayload.Entry entry, Volcano.Phase phase, float progress, float activity) {}

    private static List<Live> live(ClientLevel level) {
        List<VolcanoPayload.Entry> entries = VolcanoPayload.client;
        List<Live> list = new ArrayList<>(entries.size());
        long since = Math.max(0L, level.getGameTime() - VolcanoPayload.receivedAt);
        for (VolcanoPayload.Entry e : entries) {
            Volcano.Phase phase = Volcano.Phase.byOrdinal(e.phase());
            float progress = Mth.clamp(e.progress() + since / (float) Math.max(1, e.length()), 0.0F, 1.0F);
            list.add(new Live(e, phase, progress, Volcano.activity(phase, progress)));
        }
        return list;
    }

    private static float falloff(double distance, double range) {
        float f = Mth.clamp(1.0F - (float) (distance / range), 0.0F, 1.0F);
        return f * f;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        prevShake = shake;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD) {
            shake = kick = tremor = rumbleVolume = 0.0F;
            blasts.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long time = level.getGameTime();
        float targetTremor = 0.0F, steady = 0.0F, volume = 0.0F;
        boolean sounds = ClientConfig.SOUNDS.get();
        for (Live v : live(level)) {
            VolcanoPayload.Entry e = v.entry();
            double dx = e.x() + 0.5 - cam.x, dz = e.z() + 0.5 - cam.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            long key = (long) e.x() << 32 ^ e.z() & 0xFFFFFFFFL;
            Integer before = lastPhase.put(key, e.phase());
            if (before != null && before != Volcano.Phase.ERUPTING.ordinal() && v.phase() == Volcano.Phase.ERUPTING) {
                blast(time, d, 1.0F, 3.0F); // the eruption announces itself
            }
            switch (v.phase()) {
                case RUMBLING -> {
                    // earthquakes come and go, more often as the eruption nears
                    if (tremorTicks <= 0 && RANDOM.nextFloat() < 0.008F + 0.03F * v.progress()) {
                        tremorTicks = 30 + RANDOM.nextInt(50);
                    }
                    if (tremorTicks > 0) {
                        targetTremor = Math.max(targetTremor, (0.35F + 0.6F * v.progress()) * falloff(d, 700));
                    }
                    volume = Math.max(volume, (0.25F + 0.5F * v.progress() + (tremorTicks > 0 ? 0.3F : 0.0F)) * falloff(d, 800));
                }
                case ERUPTING -> {
                    steady = Math.max(steady, 0.35F * v.activity() * falloff(d, 900));
                    volume = Math.max(volume, (0.45F + 0.55F * v.activity()) * falloff(d, 1300));
                    if (RANDOM.nextFloat() < 0.012F * v.activity()) {
                        blast(time, d, 0.4F + 0.5F * RANDOM.nextFloat(), 1.4F);
                    }
                }
                case COOLING -> volume = Math.max(volume, 0.3F * (1.0F - v.progress()) * falloff(d, 700));
                default -> {}
            }
            if (d < 192) {
                craterParticles(level, v, d);
            }
        }
        if (quakeTicks > 0) {
            float env = Mth.sin(Mth.PI * (1.0F - quakeTicks / (float) quakeLength));
            targetTremor = Math.max(targetTremor, quakeStrength * (0.3F + 0.7F * env));
            volume = Math.max(volume, quakeStrength * 0.8F * env);
            quakeTicks--;
        }
        if (tremorTicks > 0) tremorTicks--;
        tremor = Mth.lerp(0.08F, tremor, targetTremor);

        // blasts arrive late, the further away you are
        for (Iterator<Blast> it = blasts.iterator(); it.hasNext(); ) {
            Blast b = it.next();
            if (time >= b.due()) {
                it.remove();
                kick = Math.max(kick, b.kick());
                if (sounds && b.volume() > 0.02F) {
                    // a deep boom from all around (relative to the listener)
                    mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.VOLCANO_ERUPTION.get().getLocation(), SoundSource.WEATHER,
                            b.volume(), 0.8F + RANDOM.nextFloat() * 0.3F, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
                }
            }
        }
        kick *= 0.9F;
        boolean grounded = mc.player.onGround() || mc.player.isPassenger();
        shake = (grounded ? tremor + steady : steady * 0.5F) + kick;

        rumbleVolume = sounds ? volume : 0.0F;
        if (rumbleVolume > 0.02F && (rumble == null || rumble.isStopped() || !mc.getSoundManager().isActive(rumble))) {
            rumble = new AmbientLoop(ModSounds.VOLCANO_RUMBLE.get(), () -> rumbleVolume, () -> 0.9 + tremor * 0.2);
            mc.getSoundManager().play(rumble);
        }
    }

    private static void blast(long now, double distance, float strength, float kickStrength) {
        float volume = strength * falloff(distance, 1600) * 1.6F;
        float k = kickStrength * strength * falloff(distance, 1200);
        if (volume > 0.02F || k > 0.02F) {
            blasts.add(new Blast(now + (long) (distance / SOUND_SPEED), Math.min(1.0F, volume), k));
        }
    }

    /** Fountains of lava, smoke and steam over the crater, for anyone close enough to see them. */
    private static void craterParticles(ClientLevel level, Live v, double distance) {
        VolcanoPayload.Entry e = v.entry();
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        float a = v.activity();
        double r = e.craterRadius() * 0.7;
        double lava = e.lavaY() + 0.9;
        switch (v.phase()) {
            case ERUPTING -> {
                for (int i = 0; i < Math.round(6 * a * density); i++) {
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, e.x() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2 * r, lava,
                            e.z() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2 * r, 0.0, 0.0, 0.0);
                }
                for (int i = 0; i < Math.round(4 * a * density); i++) {
                    level.addAlwaysVisibleParticle(ModParticles.EMBER.get(), true, e.x() + 0.5 + RANDOM.nextGaussian() * r * 0.5, lava + RANDOM.nextDouble() * 6,
                            e.z() + 0.5 + RANDOM.nextGaussian() * r * 0.5, RANDOM.nextGaussian() * 0.12, 0.35 + RANDOM.nextDouble() * 0.5, RANDOM.nextGaussian() * 0.12);
                }
                if (RANDOM.nextFloat() < 0.6F * a * density) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, e.x() + 0.5 + RANDOM.nextGaussian() * r * 0.4, lava + 2,
                            e.z() + 0.5 + RANDOM.nextGaussian() * r * 0.4, 0.0, 0.25 + RANDOM.nextDouble() * 0.15, 0.0);
                }
            }
            case RUMBLING, COOLING -> {
                if (RANDOM.nextFloat() < 0.5F * density) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, e.x() + 0.5 + RANDOM.nextGaussian() * r * 0.4, lava,
                            e.z() + 0.5 + RANDOM.nextGaussian() * r * 0.4, 0.0, 0.08, 0.0);
                }
                if (RANDOM.nextFloat() < 0.25F * density) {
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, e.x() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2 * r, lava,
                            e.z() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2 * r, 0.0, 0.0, 0.0);
                }
            }
            case DORMANT -> {
                if (distance < 96 && RANDOM.nextFloat() < 0.2F * density) {
                    level.addParticle(ParticleTypes.CLOUD, e.x() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2 * r, lava + 0.2,
                            e.z() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2 * r, 0.0, 0.05, 0.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ camera

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = Mth.lerp((float) event.getPartialTick(), prevShake, shake) * Accessibility.screenEffects();
        if (s < 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + s * wobble(t, 0.0F) * 0.7F);
        event.setYaw(event.getYaw() + s * wobble(t, 3.1F) * 0.5F);
        event.setRoll(event.getRoll() + s * wobble(t, 7.3F) * 0.9F);
    }

    private static float wobble(float t, float phase) {
        return (Mth.sin(t * 1.9F + phase) + 0.6F * Mth.sin(t * 3.7F + phase * 1.7F) + 0.35F * Mth.sin(t * 6.3F + phase * 2.3F)) / 1.95F;
    }

    // ------------------------------------------------------------------ the eruption column

    /** Adds this frame's eruption columns to the shared puff renderer. */
    public static void collectPlumes(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || VolcanoPayload.client.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        float time = level.getGameTime() + partialTick;
        float maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        float daylight = level.getSkyDarken(partialTick);
        float night = 1.0F - Mth.clamp((daylight - 0.2F) / 0.8F, 0.0F, 1.0F);
        float fog = 1.0F - 0.55F * ClientWeather.fogDensity();
        Vec3 wind = Wind.direction(ClientWeather.windAngle());
        float windStrength = ClientWeather.wind();
        for (Live v : live(level)) {
            if (v.activity() <= 0.0F) continue;
            plume(v, cam, time, wind, windStrength, daylight, night, fog, maxDistance);
        }
    }

    private static float hash(int i, int salt) {
        return PuffRenderer.hash(i, salt);
    }

    /** Builds the puffs of one volcano's plume: a smoky wisp while it rumbles, a towering ash column while it erupts. */
    private static void plume(Live v, Vec3 cam, float time, Vec3 wind, float windStrength, float daylight, float night,
                              float fog, float maxDistance) {
        VolcanoPayload.Entry e = v.entry();
        boolean erupting = v.phase() == Volcano.Phase.ERUPTING;
        float a = v.activity();
        // column height above the crater, width at the base and top, and how far the wind bends it over
        float height = erupting ? 110.0F + 190.0F * a : 40.0F + 120.0F * a;
        float base = erupting ? e.craterRadius() * 1.1F : e.craterRadius() * 0.6F;
        float top = erupting ? 35.0F + 55.0F * a : 14.0F + 24.0F * a;
        float lean = (erupting ? 50.0F : 25.0F) + 220.0F * windStrength;
        int count = erupting ? 56 : 22;
        float rise = erupting ? 0.0035F + 0.004F * a : 0.0025F;
        float grey = erupting ? 0.2F + 0.16F * daylight : 0.55F + 0.35F * daylight; // dark ash, or whitish steam and smoke
        // (a waking or cooling volcano is barely active, but its smoke should still show)
        float alpha = (erupting ? 0.85F : 0.55F) * Mth.clamp(a * 3.0F, erupting ? 0.0F : 0.6F, 1.0F) * fog;
        int salt = e.x() * 31 + e.z();
        double ox = e.x() + 0.5, oy = e.lavaY() + 2.0, oz = e.z() + 0.5;
        for (int i = 0; i < count; i++) {
            float s = (i / (float) count + time * rise + hash(i, salt) * 0.3F) % 1.0F;
            float spread = (hash(i, salt + 1) - 0.5F) * 2.0F;
            float angle = hash(i, salt + 2) * Mth.TWO_PI;
            float bend = s * s * lean;
            float width = base + (top - base) * (float) Math.pow(s, 0.7);
            double px = ox + wind.x * bend + Mth.cos(angle) * spread * width * 0.6;
            double py = oy + s * height + (hash(i, salt + 3) - 0.5F) * width * 0.4;
            double pz = oz + wind.z * bend + Mth.sin(angle) * spread * width * 0.6;
            float fade = Mth.clamp(s / 0.06F, 0.0F, 1.0F) * Mth.clamp((1.0F - s) / 0.25F, 0.0F, 1.0F);
            float shade = grey * (0.85F + 0.3F * hash(i, salt + 4));
            // the base of an eruption column glows with the lava under it, most of all at night
            float glow = erupting ? Mth.clamp(1.0F - s / 0.18F, 0.0F, 1.0F) * (0.35F + 0.65F * night) * a : 0.0F;
            float r = Mth.lerp(glow, shade, 0.85F), g = Mth.lerp(glow, shade, 0.32F), b = Mth.lerp(glow, shade * 1.02F, 0.12F);
            add(px, py, pz, width, r, g, b, alpha * fade, false, cam, maxDistance);
        }
        if (erupting) {
            // the umbrella of ash spreading out downwind at the top of the column
            int canopy = 18;
            for (int i = 0; i < canopy; i++) {
                float t = hash(i, salt + 9);
                float across = (hash(i, salt + 10) - 0.5F) * 2.0F;
                float along = lean + t * (120.0F + 200.0F * a);
                float width = top * (1.1F + 0.6F * t);
                double px = ox + wind.x * along - wind.z * across * width * (0.6F + t);
                double pz = oz + wind.z * along + wind.x * across * width * (0.6F + t);
                double py = oy + height * (0.92F - 0.12F * t) + (hash(i, salt + 11) - 0.5F) * 18.0F;
                float wobble = 0.85F + 0.15F * Mth.sin(time * 0.01F + i);
                add(px, py, pz, width * wobble, grey, grey, grey * 1.02F, alpha * (0.8F - 0.4F * t), false, cam, maxDistance);
            }
            // lava fountaining out of the crater
            for (int i = 0; i < 8; i++) {
                float flicker = 0.6F + 0.4F * Mth.sin(time * 0.6F + i * 1.7F);
                double px = ox + (hash(i, salt + 20) - 0.5F) * e.craterRadius();
                double pz = oz + (hash(i, salt + 21) - 0.5F) * e.craterRadius();
                double py = oy + 2.0 + hash(i, salt + 22) * 14.0 * a;
                add(px, py, pz, 3.0F + 5.0F * a, 1.0F, 0.45F, 0.12F, 0.55F * a * flicker * fog, true, cam, maxDistance);
            }
        }
    }

    private static void add(double wx, double wy, double wz, float size, float r, float g, float b, float a, boolean glow,
                            Vec3 cam, float maxDistance) {
        PuffRenderer.add(wx, wy, wz, size, r, g, b, a, glow, cam, maxDistance);
    }

    // ------------------------------------------------------------------ misc

    /** How hard the ground is shaking (for other effects), 0 to ~3. */
    public static float shake() {
        return shake;
    }

    public static void reset() {
        blasts.clear();
        lastPhase.clear();
        shake = prevShake = kick = tremor = rumbleVolume = quakeStrength = 0.0F;
        quakeTicks = 0;
        tremorTicks = 0;
        rumble = null;
        VolcanoPayload.reset();
    }

    private VolcanoClient() {}
}
