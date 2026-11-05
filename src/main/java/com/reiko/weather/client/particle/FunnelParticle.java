package com.reiko.weather.client.particle;

import com.reiko.weather.entity.TornadoEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** One puff of a tornado's funnel cloud, orbiting the tornado's (moving) axis as it rises. */
public class FunnelParticle extends TextureSheetParticle {
    /** Captured from the particle provider so tornadoes can create these directly. */
    public static SpriteSet sprites;

    private final TornadoEntity tornado;
    private float angle;
    private float height;
    private final float radiusScale;
    private final float riseSpeed;
    private final float maxAlpha;

    public FunnelParticle(ClientLevel level, TornadoEntity tornado, float angle, float height, SpriteSet sprites) {
        super(level, tornado.getX(), tornado.getY() + height, tornado.getZ());
        this.tornado = tornado;
        this.angle = angle;
        this.height = height;
        this.radiusScale = 0.75F + this.random.nextFloat() * 0.45F;
        this.riseSpeed = (0.08F + this.random.nextFloat() * 0.15F) * Math.max(0.3F, tornado.height() / 70.0F);
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.lifetime = 50 + this.random.nextInt(40);
        float scale = Math.max(0.3F, tornado.height() / 70.0F);
        this.quadSize = (1.8F + this.random.nextFloat() * 1.8F + height / tornado.height() * 4.0F) * scale;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        float shade;
        switch (tornado.kind()) {
            case WATERSPOUT -> { shade = 0.78F + this.random.nextFloat() * 0.17F; this.setColor(shade * 0.96F, shade, Math.min(1.0F, shade * 1.05F)); }
            case DUST_DEVIL -> { shade = 0.8F + this.random.nextFloat() * 0.2F; this.setColor(0.86F * shade, 0.72F * shade, 0.52F * shade); }
            default -> { shade = 0.5F + this.random.nextFloat() * 0.25F; this.setColor(shade, shade * 1.02F, shade * 1.06F); }
        }
        this.maxAlpha = (0.7F + this.random.nextFloat() * 0.25F) * (tornado.kind() == TornadoEntity.Kind.DUST_DEVIL ? 0.55F : 1.0F);
        this.alpha = 0.0F;
        updatePosition();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    private void updatePosition() {
        float radius = tornado.radiusAt(height) * radiusScale;
        this.setPos(tornado.getX() + Mth.cos(angle) * radius, tornado.getY() + height, tornado.getZ() + Mth.sin(angle) * radius);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime || tornado.isRemoved()) {
            this.remove();
            return;
        }
        float life = this.age / (float) this.lifetime;
        float fade = life < 0.2F ? life / 0.2F : life > 0.7F ? (1.0F - life) / 0.3F : 1.0F;
        this.alpha = this.maxAlpha * Mth.clamp(fade, 0.0F, 1.0F) * (0.3F + 0.7F * tornado.strength());
        float radius = Math.max(1.0F, tornado.radiusAt(height));
        this.angle += 0.9F / radius + 0.05F;
        this.height += riseSpeed;
        this.roll += 0.03F;
        updatePosition();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
