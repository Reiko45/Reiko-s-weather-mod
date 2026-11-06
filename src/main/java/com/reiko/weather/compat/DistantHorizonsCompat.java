package com.reiko.weather.compat;

import com.reiko.weather.ReikosWeather;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Reads Distant Horizons' LOD render distance (through its public API, by reflection so there is no
 * hard dependency) so the storm cloud deck can reach as far as the distant terrain.
 */
public final class DistantHorizonsCompat {
    private static Boolean loaded;
    private static long lastCheck;
    private static int cachedBlocks;
    private static boolean failed;

    /** LOD render distance in blocks, or 0 when Distant Horizons isn't installed or is turned off. */
    public static int renderDistanceBlocks() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("distanthorizons");
        }
        if (!loaded || failed) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck < 5000) {
            return cachedBlocks;
        }
        lastCheck = now;
        try {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayed.getField("configs").get(null);
            if (configs == null) {
                return cachedBlocks = 0; // API not ready yet
            }
            Object graphics = method(configs, "graphics").invoke(configs);
            Object enabled = method(graphics, "renderingEnabled").invoke(graphics);
            if (Boolean.FALSE.equals(method(enabled, "getValue").invoke(enabled))) {
                return cachedBlocks = 0;
            }
            Object distance = method(graphics, "chunkRenderDistance").invoke(graphics);
            Object chunks = method(distance, "getValue").invoke(distance);
            cachedBlocks = chunks instanceof Integer i ? i * 16 : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            cachedBlocks = 0;
            ReikosWeather.LOGGER.warn("Couldn't read Distant Horizons' render distance: {}", e.toString());
        }
        return cachedBlocks;
    }

    private static Method method(Object target, String name) throws NoSuchMethodException {
        // look the method up on the public API interface, since implementation classes may not be accessible
        for (Class<?> iface : allInterfaces(target.getClass())) {
            try {
                return iface.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return target.getClass().getMethod(name);
    }

    private static java.util.List<Class<?>> allInterfaces(Class<?> type) {
        java.util.List<Class<?>> result = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                collect(i, result);
            }
        }
        return result;
    }

    private static void collect(Class<?> iface, java.util.List<Class<?>> result) {
        if (!result.contains(iface)) {
            result.add(iface);
            for (Class<?> parent : iface.getInterfaces()) {
                collect(parent, result);
            }
        }
    }

    private DistantHorizonsCompat() {}
}
