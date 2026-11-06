package com.reiko.weather;

import net.minecraft.world.phys.Vec3;

/** Things common code asks the client to do. The client setup replaces {@link #INSTANCE}. */
public interface ClientActions {
    ClientActions NOOP = (center, followPlayer) -> {};

    void openRadar(Vec3 center, boolean followPlayer);

    final class Holder {
        public static ClientActions instance = NOOP;

        private Holder() {}
    }
}
