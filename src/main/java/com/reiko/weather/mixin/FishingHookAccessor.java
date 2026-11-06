package com.reiko.weather.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets earthworm bait improve a cast (vanilla fixes lure and luck when the hook is thrown). */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("lureSpeed")
    int reikos_weather$getLureSpeed();

    @Mutable
    @Accessor("lureSpeed")
    void reikos_weather$setLureSpeed(int lureSpeed);

    @Accessor("luck")
    int reikos_weather$getLuck();

    @Mutable
    @Accessor("luck")
    void reikos_weather$setLuck(int luck);
}
