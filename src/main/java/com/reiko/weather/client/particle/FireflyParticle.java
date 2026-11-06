package com.reiko.weather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** A firefly: a tiny light drifting lazily over the grass, glowing and fading in slow pulses. */
public class FireflyParticle extends TextureSheetParticle {
    private final float phase;
    private final float speed;

    public FireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.lifetime = 120 + this.random.nextInt(160);
        this.hasPhysics = true;
        this.gravity = 0.0F;
        this.quadSize = 0.075F + this.random.nextFloat() * 0.035F;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.speed = 0.06F + this.random.nextFloat() * 0.05F;
        this.setColor(0.85F + this.random.nextFloat() * 0.15F, 1.0F, 0.35F);
        this.xd = (this.random.nextDouble() - 0.5) * 0.02;
        this.zd = (this.random.nextDouble() - 0.5) * 0.02;
    }

    @Override
    public void tick() {
        super.tick();
        // meander
        this.xd = Mth.clamp(this.xd + (this.random.nextDouble() - 0.5) * 0.004, -0.03, 0.03);
        this.zd = Mth.clamp(this.zd + (this.random.nextDouble() - 0.5) * 0.004, -0.03, 0.03);
        this.yd = Mth.clamp(this.yd + (this.random.nextDouble() - 0.5) * 0.003, -0.015, 0.015);
        float glow = 0.5F + 0.5F * Mth.sin(this.age * this.speed + this.phase);
        float life = Mth.clamp(Math.min(this.age / 20.0F, (this.lifetime - this.age) / 20.0F), 0.0F, 1.0F);
        this.alpha = Mth.clamp(glow * glow * 1.2F, 0.0F, 1.0F) * life;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // glows in the dark
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
