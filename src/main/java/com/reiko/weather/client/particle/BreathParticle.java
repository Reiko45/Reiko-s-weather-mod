package com.reiko.weather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** A small puff of breath that swells and fades in cold air. */
public class BreathParticle extends TextureSheetParticle {
    private final float maxSize;

    public BreathParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.88F;
        this.lifetime = 16 + random.nextInt(10);
        this.maxSize = 0.16F + random.nextFloat() * 0.08F;
        this.quadSize = maxSize * 0.3F;
        this.alpha = 0.0F;
        this.setColor(0.95F, 0.96F, 1.0F);
        this.hasPhysics = false;
    }

    @Override
    public void tick() {
        super.tick();
        float life = this.age / (float) this.lifetime;
        this.quadSize = Mth.lerp(Math.min(1.0F, life * 2.0F), maxSize * 0.3F, maxSize);
        this.alpha = 0.32F * (life < 0.2F ? life / 0.2F : (1.0F - life) / 0.8F);
        this.yd += 0.002;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
