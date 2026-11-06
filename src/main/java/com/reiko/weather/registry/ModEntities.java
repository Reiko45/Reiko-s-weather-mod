package com.reiko.weather.registry;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.entity.BallLightningEntity;
import com.reiko.weather.entity.CloudSeedingRocketEntity;
import com.reiko.weather.entity.TornadoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, ReikosWeather.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoEntity>> TORNADO = REGISTER.register("tornado",
            () -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(3.0F, 12.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build(ReikosWeather.id("tornado").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<BallLightningEntity>> BALL_LIGHTNING = REGISTER.register("ball_lightning",
            () -> EntityType.Builder.<BallLightningEntity>of(BallLightningEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).fireImmune().clientTrackingRange(8).updateInterval(2)
                    .build(ReikosWeather.id("ball_lightning").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<CloudSeedingRocketEntity>> CLOUD_SEEDING_ROCKET = REGISTER.register("cloud_seeding_rocket",
            () -> EntityType.Builder.<CloudSeedingRocketEntity>of(CloudSeedingRocketEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1)
                    .build(ReikosWeather.id("cloud_seeding_rocket").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<com.reiko.weather.entity.MeteorEntity>> METEOR = REGISTER.register("meteor",
            () -> EntityType.Builder.<com.reiko.weather.entity.MeteorEntity>of(com.reiko.weather.entity.MeteorEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(16).updateInterval(1)
                    .build(ReikosWeather.id("meteor").toString()));

    private ModEntities() {}
}
