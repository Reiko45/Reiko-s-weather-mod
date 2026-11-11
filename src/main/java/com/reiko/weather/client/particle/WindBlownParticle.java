package com.reiko.weather.client.particle;

import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Snowflakes, blizzard snow, sand grains, dust clouds and leaves: anything that rides the wind. */
public class WindBlownParticle extends TextureSheetParticle {
    public enum Kind {
        /** Gently falling flakes. */
        SNOW(0.25, 0.08, 0.9F),
        /** Horizontal, driving snow. */
        BLIZZARD(0.85, 0.06, 0.95F),
        /** Fast, low sand grains. */
        SAND(1.0, 0.0, 0.85F),
        /** Big soft dust or sand clouds. */
        DUST(0.7, 0.0, 0.18F),
        /** Tumbling leaves. */
        LEAF(0.6, 0.04, 1.0F),
        /** Soft morning mist drifting low over water. */
        MIST(0.12, 0.0, 0.2F),
        /** Grey flakes of volcanic ash, sifting down. */
        ASH(0.4, 0.07, 0.9F),
        /** Steam fog: wisps rising off water much warmer than the bitter air over it. */
        STEAM(0.15, -0.025, 0.2F),
        /** Spray billowing up from the foot of a waterfall. */
        SPRAY(0.3, -0.035, 0.32F),
        /** The billowing grey cloud of a pyroclastic flow: huge, dense, rising slowly as it spreads. */
        ASH_CLOUD(0.15, -0.02, 0.8F),
        /** Woodsmoke spread out flat under a lid of warmer air: barely drifting, neither rising nor falling. */
        SMOKE_HAZE(0.05, 0.0, 0.3F);

        final double windFactor;
        final double fallSpeed;
        final float maxAlpha;

        Kind(double windFactor, double fallSpeed, float maxAlpha) {
            this.windFactor = windFactor;
            this.fallSpeed = fallSpeed;
            this.maxAlpha = maxAlpha;
        }
    }

    private final Kind kind;
    private final float spin;
    private final float swayPhase;
    private final float targetAlpha;

    public WindBlownParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.pickSprite(sprites);
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.swayPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.spin = (this.random.nextFloat() - 0.5F) * (kind == Kind.LEAF ? 0.5F : 0.1F);
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.alpha = 0.0F;
        switch (kind) {
            case SNOW -> { quadSize = 0.06F + random.nextFloat() * 0.06F; lifetime = 120 + random.nextInt(60); }
            case BLIZZARD -> { quadSize = 0.07F + random.nextFloat() * 0.08F; lifetime = 60 + random.nextInt(30); }
            case SAND -> { quadSize = 0.03F + random.nextFloat() * 0.05F; lifetime = 40 + random.nextInt(30); setSize(0.05F, 0.05F); }
            case DUST -> { quadSize = 1.2F + random.nextFloat() * 2.0F; lifetime = 70 + random.nextInt(50); hasPhysics = false; }
            case LEAF -> { quadSize = 0.11F + random.nextFloat() * 0.06F; lifetime = 100 + random.nextInt(60); }
            case MIST -> { quadSize = 2.0F + random.nextFloat() * 2.5F; lifetime = 120 + random.nextInt(80); hasPhysics = false; }
            case ASH -> { quadSize = 0.05F + random.nextFloat() * 0.07F; lifetime = 140 + random.nextInt(60); }
            case STEAM -> { quadSize = 0.7F + random.nextFloat() * 0.9F; lifetime = 50 + random.nextInt(40); hasPhysics = false; }
            case SPRAY -> { quadSize = 1.0F + random.nextFloat() * 1.4F; lifetime = 35 + random.nextInt(30); hasPhysics = false; }
            case ASH_CLOUD -> { quadSize = 3.0F + random.nextFloat() * 4.0F; lifetime = 160 + random.nextInt(100); hasPhysics = false; }
            case SMOKE_HAZE -> { quadSize = 2.5F + random.nextFloat() * 2.5F; lifetime = 220 + random.nextInt(120); hasPhysics = false; }
        }
        this.targetAlpha = kind.maxAlpha * (soft() ? 0.6F + random.nextFloat() * 0.4F : 1.0F);
        if (!soft()) {
            this.setSize(0.08F, 0.08F);
        }
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // fade in, fade out
        float life = this.age / (float) this.lifetime;
        float fade = life < 0.15F ? life / 0.15F : life > 0.75F ? (1.0F - life) / 0.25F : 1.0F;
        this.alpha = this.targetAlpha * Mth.clamp(fade, 0.0F, 1.0F);

        float strength = ClientWeather.wind();
        double speed = (0.05 + strength * 0.9) * kind.windFactor;
        Vec3 wind = Wind.direction(ClientWeather.windAngle()).scale(speed);
        float sway = Mth.sin(this.age * 0.15F + swayPhase);
        double turbulence = kind == Kind.LEAF ? 0.08 : kind == Kind.SNOW ? 0.02 : 0.03;
        this.xd += (wind.x + sway * turbulence * -wind.z / Math.max(0.01, speed) - this.xd) * 0.1;
        this.zd += (wind.z + sway * turbulence * wind.x / Math.max(0.01, speed) - this.zd) * 0.1;
        double targetY = -kind.fallSpeed + (kind == Kind.LEAF ? Mth.cos(this.age * 0.2F + swayPhase) * 0.04 : 0.0)
                + (kind == Kind.SAND || kind == Kind.DUST ? sway * 0.01 : 0.0);
        this.yd += (targetY - this.yd) * 0.1;
        this.roll += spin;
        if (soft()) {
            this.quadSize *= kind == Kind.STEAM || kind == Kind.SPRAY ? 1.012F : kind == Kind.ASH_CLOUD ? 1.006F : 1.004F;
        }
        this.move(this.xd, this.yd, this.zd);
        if (this.onGround && kind != Kind.SAND && kind != Kind.LEAF) {
            this.age = Math.max(this.age, (int) (this.lifetime * 0.8F));
        }
        if (this.onGround && kind == Kind.LEAF) {
            this.xd *= 0.5;
            this.zd *= 0.5;
        }
    }

    /** Big, soft clouds rather than little flakes. */
    private boolean soft() {
        return kind == Kind.DUST || kind == Kind.MIST || kind == Kind.STEAM || kind == Kind.SPRAY || kind == Kind.ASH_CLOUD || kind == Kind.SMOKE_HAZE;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
