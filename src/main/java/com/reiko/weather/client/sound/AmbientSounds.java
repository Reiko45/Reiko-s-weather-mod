package com.reiko.weather.client.sound;

import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModSounds;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Sound atmosphere: thunder that arrives after the flash, weather that sounds muffled from indoors,
 * rain pattering on the roof over your head, wind whistling around buildings and the far-off rumble
 * of storms on the horizon.
 */
public final class AmbientSounds {
    /** Blocks per tick. Slower than real sound so the gap between flash and bang is easy to notice. */
    private static final double SOUND_SPEED = 4.0;
    private static final int MAX_DELAY = 140;
    private static final ResourceLocation THUNDER = SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation();
    private static final ResourceLocation IMPACT = SoundEvents.LIGHTNING_BOLT_IMPACT.getLocation();
    private static final RandomSource RANDOM = RandomSource.create();

    private record Pending(SoundInstance sound, long at) {}

    /** Sounds we are replaying ourselves; the event handler lets them through untouched. */
    private static final Set<SoundInstance> PASS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Pending> PENDING = new ArrayList<>();
    private static long ticks;

    private static float indoors;
    private static float roofRain;
    private static float whistle;
    private static AmbientLoop roofLoop;
    private static AmbientLoop whistleLoop;

    // ------------------------------------------------------------------ events

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || PASS.remove(sound) || !ClientConfig.SPEC.isLoaded()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ResourceLocation id = sound.getLocation();
        if (!ClientConfig.WEATHER_ALERT_SOUNDS.get() && id.equals(com.reiko.weather.ReikosWeather.id("item.weather_radio.alert"))) {
            event.setSound(null);
            return;
        }
        boolean thunder = id.equals(THUNDER);
        if (thunder && !sound.isRelative() && ClientConfig.THUNDER_ECHOES.get()) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double distance = Math.sqrt(cam.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
            echo(mc.level, cam, ClientConfig.THUNDER_DELAY.get() ? (int) Math.min(MAX_DELAY, distance / SOUND_SPEED) : 0);
        }
        if (ClientConfig.THUNDER_DELAY.get() && (thunder || id.equals(IMPACT)) && !sound.isRelative()) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double distance = Math.sqrt(cam.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()));
            int delay = (int) Math.min(MAX_DELAY, distance / SOUND_SPEED);
            if (delay >= 2) {
                event.setSound(null);
                PENDING.add(new Pending(sound, ticks + delay));
                return;
            }
        }
        SoundInstance muffled = muffle(sound, thunder);
        if (muffled != sound) {
            event.setSound(muffled);
        }
    }

    private static SoundInstance muffle(SoundInstance sound, boolean global) {
        if (!ClientConfig.MUFFLE_INDOORS.get() || indoors < 0.05F || sound.getSource() != SoundSource.WEATHER
                || sound instanceof TickableSoundInstance || sound instanceof MuffledSound) {
            return sound;
        }
        return new MuffledSound(sound, 1.0F - 0.6F * indoors, 1.0F - 0.15F * indoors, global);
    }

    /**
     * Thunder echoing back off the mountains: where high ground stands round you, the thunder comes rolling back off
     * each slope in turn, later and fainter the further away it is. Out on open plains there's nothing to send it back.
     */
    private static void echo(net.minecraft.client.multiplayer.ClientLevel level, Vec3 cam, int delay) {
        List<double[]> walls = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12.0F;
            for (int r = 40; r <= 160; r += 40) {
                int x = Mth.floor(cam.x + Mth.cos(a) * r), z = Mth.floor(cam.z + Mth.sin(a) * r);
                if (!com.reiko.weather.client.SkySights.loaded(level, new BlockPos(x, 0, z))) break;
                int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                if (h > cam.y + 14) {
                    walls.add(new double[]{r, h - cam.y});
                    break;
                }
            }
        }
        if (walls.size() < 3) return;
        // the highest slopes answer loudest
        walls.sort((p, q) -> Double.compare(q[1], p[1]));
        float enclosed = Math.min(1.0F, walls.size() / 8.0F);
        for (int i = 0; i < Math.min(3, walls.size()); i++) {
            double[] w = walls.get(i);
            float volume = (0.42F - 0.11F * i) * enclosed;
            int back = delay + 12 + (int) (2.0 * w[0] / SOUND_SPEED) + i * 9;
            SoundInstance echo = new SimpleSoundInstance(THUNDER, SoundSource.WEATHER, volume, 0.8F + RANDOM.nextFloat() * 0.1F, RANDOM, false, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            PENDING.add(new Pending(echo, ticks + Math.min(back, MAX_DELAY + 60)));
        }
    }

    /** A low rumble from a storm on the horizon, heard a while after its flash. */
    public static void distantRumble(double distance) {
        if (!ClientConfig.SOUNDS.get()) {
            return;
        }
        float volume = (float) Mth.clamp(0.4 - distance / 4000.0, 0.1, 0.4);
        SoundInstance rumble = new SimpleSoundInstance(THUNDER, SoundSource.WEATHER, volume,
                0.5F + RANDOM.nextFloat() * 0.15F, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        int delay = ClientConfig.THUNDER_DELAY.get() ? (int) Math.min(MAX_DELAY, 20 + distance / SOUND_SPEED) : 0;
        PENDING.add(new Pending(rumble, ticks + delay));
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(Minecraft mc) {
        ticks++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            reset();
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        List<SoundInstance> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (ticks >= p.at()) {
                it.remove();
                due.add(p.sound());
            }
        }
        for (SoundInstance sound : due) {
            SoundInstance play = muffle(sound, sound.getLocation().equals(THUNDER) && !sound.isRelative());
            PASS.add(play);
            mc.getSoundManager().play(play);
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (ticks % 5 == 0) {
            updateSurroundings(level, cam);
        }
        if (!ClientConfig.SOUNDS.get() || !ClientConfig.AMBIENT_LOOPS.get()) {
            return;
        }
        if (roofRain > 0.02F && (roofLoop == null || roofLoop.isStopped() || !mc.getSoundManager().isActive(roofLoop))) {
            roofLoop = new AmbientLoop(ModSounds.RAIN_ROOF.get(), () -> roofRain, () -> 0.9F + ClientWeather.rain() * 0.15F);
            mc.getSoundManager().play(roofLoop);
        }
        if (whistle > 0.02F && (whistleLoop == null || whistleLoop.isStopped() || !mc.getSoundManager().isActive(whistleLoop))) {
            whistleLoop = new AmbientLoop(ModSounds.WIND_WHISTLE.get(), () -> whistle,
                    () -> 0.8F + 0.35F * Wind.gust(ticks, 0.0F));
            mc.getSoundManager().play(whistleLoop);
        }
    }

    /** Works out how covered the camera is, whether rain is drumming on a roof and how much the wind catches on walls. */
    private static void updateSurroundings(ClientLevel level, Vec3 cam) {
        int roofed = 0, rainRoof = 0;
        int camY = Mth.floor(cam.y);
        int[][] offsets = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] o : offsets) {
            int x = Mth.floor(cam.x) + o[0], z = Mth.floor(cam.z) + o[1];
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (top > camY + 1) {
                roofed++;
                BlockPos roof = new BlockPos(x, top, z);
                if (top - camY <= 12 && level.getBiome(roof).value().getPrecipitationAt(roof) == Biome.Precipitation.RAIN) {
                    rainRoof++;
                }
            }
        }
        boolean weather = ClientWeather.active();
        indoors = Mth.lerp(0.5F, indoors, roofed / 5.0F);
        roofRain = weather ? ClientWeather.rain() * (rainRoof / 5.0F) * 0.55F : 0.0F;

        float wind = Mth.clamp((ClientWeather.wind() - 0.4F) / 0.6F, 0.0F, 1.0F);
        if (!weather || wind <= 0.0F) {
            whistle = 0.0F;
            return;
        }
        // wind whistles where it catches on edges: corners, doorways, gaps between buildings
        boolean[] blocked = new boolean[8];
        int open = 0;
        BlockPos eye = BlockPos.containing(cam);
        for (int i = 0; i < 8; i++) {
            float angle = i * Mth.TWO_PI / 8.0F;
            for (int d = 1; d <= 4 && !blocked[i]; d++) {
                BlockPos p = eye.offset(Math.round(Mth.cos(angle) * d), 0, Math.round(Mth.sin(angle) * d));
                blocked[i] = level.getBlockState(p).blocksMotion();
            }
            if (!blocked[i]) open++;
        }
        int edges = 0;
        for (int i = 0; i < 8; i++) {
            if (blocked[i] != blocked[(i + 1) % 8]) edges++;
        }
        float structure = open >= 2 ? Math.min(1.0F, edges / 4.0F) : 0.0F;
        whistle = wind * structure * Wind.gust(ticks, 0.0F) * 0.55F;
    }

    public static float indoors() {
        return indoors;
    }

    public static void reset() {
        PENDING.clear();
        PASS.clear();
        indoors = roofRain = whistle = 0.0F;
        roofLoop = whistleLoop = null;
    }

    private AmbientSounds() {}
}
