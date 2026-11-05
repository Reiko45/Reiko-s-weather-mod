package com.reiko.weather.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a tornado tell vanilla's anti-fly check that the players it is carrying aren't flying. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
    @Accessor("aboveGroundTickCount")
    void reikos_weather$setAboveGroundTickCount(int ticks);
}
