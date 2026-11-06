package com.reiko.weather.compat;

import java.lang.reflect.Method;

/**
 * Whether an Iris (or Oculus) shader pack is active. Shader packs take over the post-processing
 * pipeline, so effects like the heat shimmer step aside while one is on. Looked up reflectively: no
 * compile-time dependency.
 */
public final class Shaders {
    private static boolean looked;
    private static Object api;
    private static Method inUse;

    public static boolean packInUse() {
        if (!looked) {
            looked = true;
            for (String name : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> type = Class.forName(name);
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                    break;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // not installed
                }
            }
        }
        if (api == null) {
            return false;
        }
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Shaders() {}
}
