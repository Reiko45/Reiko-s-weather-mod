package com.reiko.weather.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** An expanding ring where a raindrop hits the water, drawn flat on the surface. */
public class RippleParticle extends TextureSheetParticle {
    public RippleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.lifetime = 12 + this.random.nextInt(8);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 0.05F;
        this.alpha = 0.6F;
        this.setColor(0.85F, 0.9F, 1.0F);
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
        this.quadSize = 0.05F + life * 0.4F;
        this.alpha = 0.6F * (1.0F - life);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z);
        float s = this.quadSize;
        int light = this.getLightColor(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        // both windings so it shows from above and below
        vertex(buffer, x - s, y, z - s, u0, v0, light);
        vertex(buffer, x - s, y, z + s, u0, v1, light);
        vertex(buffer, x + s, y, z + s, u1, v1, light);
        vertex(buffer, x + s, y, z - s, u1, v0, light);
        vertex(buffer, x + s, y, z - s, u1, v0, light);
        vertex(buffer, x + s, y, z + s, u1, v1, light);
        vertex(buffer, x - s, y, z + s, u0, v1, light);
        vertex(buffer, x - s, y, z - s, u0, v0, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
