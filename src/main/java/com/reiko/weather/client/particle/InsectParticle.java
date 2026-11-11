package com.reiko.weather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * A tiny flying insect in a swarm. A midge darts about at random, never straying far from the swarm's centre; a mayfly
 * dances, flying slowly up and then drifting down again, over and over.
 */
public class InsectParticle extends TextureSheetParticle {
    private final boolean mayfly;
    private final double cx, cy, cz;
    private final float phase;

    public InsectParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, boolean mayfly) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.mayfly = mayfly;
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.lifetime = mayfly ? 70 + this.random.nextInt(60) : 40 + this.random.nextInt(40);
        this.quadSize = mayfly ? 0.055F + this.random.nextFloat() * 0.02F : 0.018F + this.random.nextFloat() * 0.01F;
        if (mayfly) {
            this.setColor(0.93F, 0.9F, 0.74F);
        } else {
            float g = 0.08F + this.random.nextFloat() * 0.08F;
            this.setColor(g, g, g);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (mayfly) {
            // the nuptial dance: up and down in a slow column, drifting a little
            double target = cy + 0.9 * Mth.sin(this.age * 0.09F + phase);
            this.yd = (target - this.y) * 0.12;
            this.xd = Mth.clamp(this.xd + (this.random.nextDouble() - 0.5) * 0.004 + (cx - this.x) * 0.002, -0.03, 0.03);
            this.zd = Mth.clamp(this.zd + (this.random.nextDouble() - 0.5) * 0.004 + (cz - this.z) * 0.002, -0.03, 0.03);
        } else {
            // a midge jinks about at random, held to the swarm
            this.xd = (this.xd + (this.random.nextDouble() - 0.5) * 0.06 + (cx - this.x) * 0.05) * 0.8;
            this.yd = (this.yd + (this.random.nextDouble() - 0.5) * 0.05 + (cy - this.y) * 0.05) * 0.8;
            this.zd = (this.zd + (this.random.nextDouble() - 0.5) * 0.06 + (cz - this.z) * 0.05) * 0.8;
        }
        float life = Mth.clamp(Math.min(this.age / 10.0F, (this.lifetime - this.age) / 10.0F), 0.0F, 1.0F);
        this.alpha = life;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
