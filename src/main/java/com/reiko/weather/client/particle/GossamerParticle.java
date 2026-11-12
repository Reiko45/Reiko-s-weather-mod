package com.reiko.weather.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Gossamer: the silk of thousands of tiny spiders. A loose thread drifts on the faint air with a glint now and then;
 * a sheet lies flat over the grass, dew-beaded, catching the low sun as you move.
 */
public class GossamerParticle extends TextureSheetParticle {
    private final boolean sheet;
    private final float phase, maxAlpha, angle;

    public GossamerParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, boolean sheet) {
        super(level, x, y, z);
        this.sheet = sheet;
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.angle = this.random.nextFloat() * Mth.TWO_PI;
        if (sheet) {
            this.lifetime = 140 + this.random.nextInt(100);
            this.quadSize = 0.35F + this.random.nextFloat() * 0.3F;
            this.maxAlpha = 0.6F;
            this.xd = this.yd = this.zd = 0.0;
        } else {
            this.lifetime = 160 + this.random.nextInt(120);
            this.quadSize = 0.25F + this.random.nextFloat() * 0.25F;
            this.maxAlpha = 0.7F;
            this.xd = xd;
            this.yd = 0.002 + this.random.nextDouble() * 0.004;
            this.zd = zd;
        }
        this.alpha = 0.0F;
        this.setColor(0.97F, 0.97F, 0.95F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float life = this.age / (float) this.lifetime;
        float fade = Math.min(1.0F, Math.min(life * 6.0F, (1.0F - life) * 4.0F));
        // the silk only shows where it catches the light, so it glints on and off as it drifts
        float glint = 0.55F + 0.45F * Mth.sin(this.age * (this.sheet ? 0.05F : 0.11F) + this.phase);
        this.alpha = this.maxAlpha * fade * glint;
        if (!this.sheet) {
            this.yd += (this.random.nextDouble() - 0.5) * 0.0006;
            this.move(this.xd, this.yd, this.zd);
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (!this.sheet) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z);
        float s = this.quadSize, c = Mth.cos(this.angle) * s, n = Mth.sin(this.angle) * s;
        int light = this.getLightColor(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        // lying flat over the grass, turned any which way; both windings so it shows from either side
        vertex(buffer, x - c + n, y, z - n - c, u0, v0, light);
        vertex(buffer, x - c - n, y, z - n + c, u0, v1, light);
        vertex(buffer, x + c - n, y, z + n + c, u1, v1, light);
        vertex(buffer, x + c + n, y, z + n - c, u1, v0, light);
        vertex(buffer, x + c + n, y, z + n - c, u1, v0, light);
        vertex(buffer, x + c - n, y, z + n + c, u1, v1, light);
        vertex(buffer, x - c - n, y, z - n + c, u0, v1, light);
        vertex(buffer, x - c + n, y, z - n - c, u0, v0, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
