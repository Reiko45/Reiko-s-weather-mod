package com.reiko.weather.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.reiko.weather.client.SeasonalColors;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Seasonal grass and leaf colours. */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @ModifyReturnValue(method = "getAverageGrassColor", at = @At("RETURN"))
    private static int reikos_weather$seasonGrass(int color, @Local(argsOnly = true) BlockPos pos) {
        return SeasonalColors.grass(color, pos);
    }

    @ModifyReturnValue(method = "getAverageWaterColor", at = @At("RETURN"))
    private static int reikos_weather$whiting(int color, @Local(argsOnly = true) BlockPos pos) {
        return com.reiko.weather.client.LakeWhitings.water(color, pos);
    }

    @ModifyReturnValue(method = "getAverageFoliageColor", at = @At("RETURN"))
    private static int reikos_weather$seasonLeaves(int color, @Local(argsOnly = true) BlockPos pos) {
        return SeasonalColors.foliage(color, pos);
    }
}
