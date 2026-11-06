package com.reiko.weather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** A glowing ember that drifts down, sways and flickers out. Always full-bright. */
public class EmberParticle extends TextureSheetParticle {
    private final float swayPhase;
    private final float flickerSpeed;

    public EmberParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.98F;
        this.quadSize = 0.035F + random.nextFloat() * 0.05F;
        this.lifetime = 80 + random.nextInt(80);
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.flickerSpeed = 0.3F + random.nextFloat() * 0.4F;
        this.setColor(1.0F, 0.55F + random.nextFloat() * 0.3F, 0.15F);
        this.setSize(0.05F, 0.05F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime || this.onGround && random.nextInt(8) == 0) {
            this.remove();
            return;
        }
        float life = this.age / (float) this.lifetime;
        float flicker = 0.75F + 0.25F * Mth.sin(this.age * flickerSpeed + swayPhase);
        this.alpha = Mth.clamp(life < 0.1F ? life / 0.1F : (1.0F - life) / 0.9F, 0.0F, 1.0F) * flicker;
        // cool from yellow-orange to deep red as it burns out
        this.gCol = Mth.lerp(0.02F, this.gCol, 0.25F);
        float sway = Mth.sin(this.age * 0.12F + swayPhase) * 0.01F;
        this.xd += sway;
        this.zd += Mth.cos(this.age * 0.1F + swayPhase) * 0.01F;
        this.yd += (-0.03 - this.yd) * 0.05;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.zd *= this.friction;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
