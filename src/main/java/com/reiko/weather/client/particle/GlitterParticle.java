package com.reiko.weather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** Diamond dust: a tiny ice crystal hanging in the air, glinting now and then as it turns in the sun. */
public class GlitterParticle extends TextureSheetParticle {
    private final float phase;
    private final float speed;

    public GlitterParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.pickSprite(sprites);
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.quadSize = 0.02F + random.nextFloat() * 0.025F;
        this.lifetime = 60 + random.nextInt(60);
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.speed = 0.2F + random.nextFloat() * 0.3F;
        this.setColor(0.92F, 0.97F, 1.0F);
        this.alpha = 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        float life = age / (float) lifetime;
        float fade = Math.min(1.0F, Math.min(life / 0.15F, (1.0F - life) / 0.2F));
        // mostly faint, with a sharp glint when a facet catches the sun
        float glint = (float) Math.pow(Math.abs(Mth.sin(age * speed + phase)), 12.0);
        this.alpha = Mth.clamp(fade * (0.25F + 0.75F * glint), 0.0F, 1.0F);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
