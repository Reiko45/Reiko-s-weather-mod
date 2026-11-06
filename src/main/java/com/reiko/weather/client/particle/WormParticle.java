package com.reiko.weather.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** An earthworm that came up after the rain, crawling slowly across the grass. Drawn flat on the ground. */
public class WormParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float heading;

    public WormParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 200 + this.random.nextInt(200);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 0.14F;
        this.heading = this.random.nextFloat() * Mth.TWO_PI;
        this.alpha = 0.0F;
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
        // wriggle forward in fits and starts
        float step = Mth.sin(this.age * 0.4F) > 0.3F ? 0.004F : 0.0F;
        this.x += Mth.cos(this.heading) * step;
        this.z += Mth.sin(this.heading) * step;
        this.alpha = Mth.clamp(Math.min(this.age / 20.0F, (this.lifetime - this.age) / 20.0F), 0.0F, 1.0F);
        // two frames: stretched and bunched up
        this.setSprite(this.sprites.get(this.age / 6 % 2, 1));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z);
        float s = this.quadSize;
        float c = Mth.cos(this.heading) * s, n = Mth.sin(this.heading) * s;
        int light = this.getLightColor(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        // a square rotated to the worm's heading, lying on the ground
        vertex(buffer, x - c + n, y, z - n - c, u0, v0, light);
        vertex(buffer, x - c - n, y, z - n + c, u0, v1, light);
        vertex(buffer, x + c - n, y, z + n + c, u1, v1, light);
        vertex(buffer, x + c + n, y, z + n - c, u1, v0, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
