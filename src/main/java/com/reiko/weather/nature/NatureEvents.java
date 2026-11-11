package com.reiko.weather.nature;

import com.reiko.weather.block.LeafPileBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.mixin.FishingHookAccessor;
import com.reiko.weather.registry.ModItems;
import com.reiko.weather.weather.Climate;
import com.reiko.weather.weather.LocalWeather;
import com.reiko.weather.weather.Season;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/** Wild crops in the grass, earthworms after rain, weather-dependent fishing and soft landings in leaf piles. */
public final class NatureEvents {

    // ------------------------------------------------------------------ drops

    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player) || event.isCanceled()) {
            return;
        }
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        RandomSource random = level.random;
        // the ice saw cuts whole blocks of ice instead of shattering them
        if (event.getTool().getItem() instanceof com.reiko.weather.item.IceSawItem && event.getDrops().isEmpty()
                && (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))) {
            add(event, new ItemStack(state.getBlock()));
        }
        if (WeatherConfig.WILD_CROPS.get() && (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN))
                && !event.getTool().is(Items.SHEARS)) {
            Holder<Biome> biome = level.getBiome(pos);
            if ((biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_SAVANNA) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP))
                    && random.nextFloat() < 0.07F) {
                add(event, new ItemStack(ModItems.RICE.get()));
            } else if (Climate.at(level, pos) == Climate.COLD && random.nextFloat() < 0.07F) {
                add(event, new ItemStack(ModItems.KALE.get()));
            }
        }
        if (WeatherConfig.EARTHWORMS.get() && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MUD) || state.is(Blocks.ROOTED_DIRT))) {
            float chance = wetGround(level, pos) ? 0.3F : 0.04F;
            if (random.nextFloat() < chance) {
                add(event, new ItemStack(ModItems.EARTHWORM.get(), 1 + random.nextInt(2)));
            }
        }
    }

    /** It's raining here, or it rained in the last few minutes (worms come up after rain). */
    public static boolean wetGround(ServerLevel level, BlockPos pos) {
        if (level.isRainingAt(pos.above()) || level.isRainingAt(pos.above(2))) {
            return true;
        }
        var state = WeatherManager.activeState(level);
        return state != null && state.rainbowTicks > 0;
    }

    private static void add(BlockDropsEvent event, ItemStack stack) {
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
    }

    // ------------------------------------------------------------------ fishing

    public static void onItemFished(ItemFishedEvent event) {
        FishingHook hook = event.getHookEntity();
        if (!(hook.level() instanceof ServerLevel level) || !WeatherConfig.WEATHER_FISHING.get()) {
            return;
        }
        Player player = event.getEntity();
        RandomSource random = level.random;
        BlockPos pos = hook.blockPosition();
        LocalWeather local = WeatherManager.local(level, pos);
        boolean storm = local != null && local.type().vanillaThunder && level.canSeeSky(pos.above());
        boolean raining = level.isRainingAt(pos.above());
        boolean heatwave = WeatherConfig.MARINE_HEATWAVES.get() && WeatherManager.state(level).seaHeat > 0.5F && com.reiko.weather.weather.Seas.warmSea(level, pos);
        if ((WeatherConfig.ALGAL_BLOOMS.get() && WeatherManager.state(level).bloom > 0.5F && com.reiko.weather.weather.Seas.blooming(level, pos) || heatwave)
                && random.nextFloat() < 0.6F) {
            // in an algal bloom the fish have gone: only weed comes up
            event.setCanceled(true);
            ItemEntity weed = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), new ItemStack(random.nextBoolean() ? Items.KELP : Items.SEAGRASS));
            double dx = player.getX() - hook.getX(), dy = player.getY() - hook.getY(), dz = player.getZ() - hook.getZ();
            weed.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level.addFreshEntity(weed);
            return;
        }
        List<ItemStack> extra = new ArrayList<>();
        if (storm && random.nextFloat() < 0.4F) {
            extra.add(new ItemStack(ModItems.STORMFISH.get()));
        } else if (iceHole(level, pos) && random.nextFloat() < 0.45F) {
            extra.add(new ItemStack(ModItems.ICEFISH.get()));
        } else if (com.reiko.weather.weather.Seas.upwelling(level, pos) > 0.3F && random.nextFloat() < 0.45F) {
            // the cold, rich water brings the fish in shoals
            ItemStack fish = event.getDrops().stream().filter(s -> s.is(ItemTags.FISHES)).findFirst().orElse(new ItemStack(Items.COD));
            extra.add(fish.copyWithCount(1 + random.nextInt(2)));
            if (player instanceof ServerPlayer serverPlayer) WeatherAdvancements.award(serverPlayer, "upwelling");
        } else if (WeatherConfig.INSECT_HATCHES.get() && level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_RIVER) && local != null
                && com.reiko.weather.weather.InsectHatches.mayflies(WeatherManager.seasonInfo().season(), WeatherManager.seasonInfo().dayOfSeason(),
                        WeatherManager.seasonInfo().daysPerSeason(), level.getDayTime(), local.profile().wind, raining)
                && random.nextFloat() < 0.4F) {
            // a mayfly hatch: the trout are rising to the flies, and take anything
            ItemStack fish = event.getDrops().stream().filter(s -> s.is(ItemTags.FISHES)).findFirst().orElse(new ItemStack(Items.SALMON));
            extra.add(fish.copyWithCount(1));
        } else if (raining && random.nextFloat() < 0.3F) {
            // fish bite better in the rain: sometimes a second fish comes up
            ItemStack fish = event.getDrops().stream().filter(s -> s.is(ItemTags.FISHES)).findFirst().orElse(new ItemStack(Items.COD));
            extra.add(fish.copyWithCount(1));
        }
        for (ItemStack stack : extra) {
            ItemEntity item = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), stack);
            double dx = player.getX() - hook.getX(), dy = player.getY() - hook.getY(), dz = player.getZ() - hook.getZ();
            item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level.addFreshEntity(item);
            if (player instanceof ServerPlayer serverPlayer && (stack.is(ModItems.STORMFISH.get()) || stack.is(ModItems.ICEFISH.get()))) {
                WeatherAdvancements.award(serverPlayer, stack.is(ModItems.STORMFISH.get()) ? "stormfish" : "ice_fishing");
            }
        }
    }

    /** Fishing through a hole in the ice, in the cold. */
    private static boolean iceHole(ServerLevel level, BlockPos pos) {
        boolean cold = Climate.at(level, pos) == Climate.COLD || WeatherManager.seasonInfo().season() == Season.WINTER;
        if (!cold) {
            return false;
        }
        for (int dy = -1; dy <= 1; dy++) {
            int ice = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.offset(0, dy, 0).relative(d)).is(net.minecraft.tags.BlockTags.ICE)) {
                    ice++;
                }
            }
            if (ice >= 2) {
                return true;
            }
        }
        return false;
    }

    /** Casting with earthworms in your off hand baits the hook: fish bite sooner, and better ones. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof FishingHook hook)
                || !(hook.getOwner() instanceof Player player) || !player.getOffhandItem().is(ModItems.EARTHWORM.get())) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.getOffhandItem().shrink(1);
        }
        FishingHookAccessor bait = (FishingHookAccessor) hook;
        bait.reikos_weather$setLureSpeed(bait.reikos_weather$getLureSpeed() + 100);
        bait.reikos_weather$setLuck(bait.reikos_weather$getLuck() + 1);
    }

    // ------------------------------------------------------------------ leaf piles

    public static void onLivingFall(LivingFallEvent event) {
        float cushion = LeafPileBlock.cushion(event.getEntity().level().getBlockState(event.getEntity().blockPosition()));
        if (cushion > 0) {
            event.setDamageMultiplier(event.getDamageMultiplier() * (1.0F - cushion));
        }
    }

    private NatureEvents() {}
}
