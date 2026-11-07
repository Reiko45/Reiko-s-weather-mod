package com.reiko.weather.client;

import com.reiko.weather.config.ClientConfig;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Small client effects on blocks and items: St. Elmo's fire crackling on lightning rods and metal tools in
 * thunderstorms, and tall columns of smoke over fires burning out in the open.
 */
public final class StaticElectricity {
    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean on(java.util.function.BooleanSupplier toggle) {
        return ClientConfig.SPEC.isLoaded() && toggle.getAsBoolean();
    }

    /** Lightning rods spark under the mod's thunderstorms too (vanilla only does it in a world-wide one). */
    public static void rodSparks(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!on(ClientConfig.STATIC_SPARKS::get) || level.isThundering() || ClientWeather.thunder() < 0.4F || random.nextInt(3) != 0
                || pos.getY() != level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1) {
            return;
        }
        ParticleUtils.spawnParticlesAlongAxis(state.getValue(LightningRodBlock.FACING).getAxis(), level, pos, 0.125,
                ParticleTypes.ELECTRIC_SPARK, UniformInt.of(1, 2));
    }

    /** A wildfire (not a fireplace) sends up a tall column of smoke. */
    public static void fireSmoke(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!on(ClientConfig.SMOKE_COLUMNS::get) || !(state.getBlock() instanceof FireBlock) || random.nextInt(6) != 0
                || level.getBlockState(pos.below()).is(BlockTags.INFINIBURN_OVERWORLD) || !level.canSeeSky(pos.above())) {
            return;
        }
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                pos.getY() + 0.4, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.07, 0.0);
    }

    /** Metal tools held up in a thunderstorm crackle with sparks. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !on(ClientConfig.STATIC_SPARKS::get) || ClientWeather.thunder() < 0.5F
                || player.tickCount % 5 != 0 || RANDOM.nextFloat() > 0.3F * ClientWeather.thunder()
                || !metal(player.getMainHandItem()) || !mc.level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.35);
        Vec3 at = player.getEyePosition().add(look.scale(0.6)).add(side).add(0, -0.3, 0);
        for (int i = 0; i < 2; i++) {
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + (RANDOM.nextDouble() - 0.5) * 0.3, at.y + RANDOM.nextDouble() * 0.3,
                    at.z + (RANDOM.nextDouble() - 0.5) * 0.3, 0.0, 0.02, 0.0);
        }
        if (RANDOM.nextInt(6) == 0 && ClientConfig.SOUNDS.get()) {
            mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.STATIC_CRACKLE.get(), SoundSource.PLAYERS, 0.15F, 1.4F + RANDOM.nextFloat() * 0.3F, false);
        }
    }

    private static boolean metal(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tiered) {
            return tiered.getTier() == Tiers.IRON || tiered.getTier() == Tiers.GOLD || tiered.getTier() == Tiers.NETHERITE;
        }
        return stack.is(Items.TRIDENT) || stack.is(Items.SHEARS) || stack.is(Items.LIGHTNING_ROD) || stack.is(Items.IRON_BARS);
    }

    private StaticElectricity() {}
}
