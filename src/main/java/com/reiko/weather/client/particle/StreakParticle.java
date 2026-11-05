package com.reiko.weather.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A fast falling streak (sleet pellets, torrential rain) rendered as a quad stretched along its velocity,
 * so it slants with the wind.
 */
public class StreakParticle extends TextureSheetParticle {
    private final float length;
    private final float width;
    private final boolean splash;

    public StreakParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
                          float length, float width, boolean splash) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.pickSprite(sprites);
        this.length = length * (0.8F + this.random.nextFloat() * 0.4F);
        this.width = width;
        this.splash = splash;
        this.lifetime = 40;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.setSize(0.05F, 0.05F);
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
        this.alpha = Math.min(splash ? 0.55F : 0.85F, this.alpha + 0.2F);
        Vec3 wind = Wind.direction(ClientWeather.windAngle()).scale(ClientWeather.wind() * 0.6);
        this.xd += (wind.x - this.xd) * 0.1;
        this.zd += (wind.z - this.zd) * 0.1;
        this.move(this.xd, this.yd, this.zd);
        if (this.onGround) {
            if (splash) {
                this.level.addParticle(ParticleTypes.RAIN, this.x, this.y + 0.05, this.z, 0.0, 0.0, 0.0);
            }
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z);
        Vector3f axis = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
        if (axis.lengthSquared() < 1.0E-6F) {
            axis.set(0.0F, -1.0F, 0.0F);
        }
        axis.normalize();
        Vector3f toCamera = new Vector3f(-px, -py, -pz);
        if (toCamera.lengthSquared() < 1.0E-4F) {
            return;
        }
        toCamera.normalize();
        Vector3f right = new Vector3f(axis).cross(toCamera);
        if (right.lengthSquared() < 1.0E-6F) {
            return;
        }
        right.normalize().mul(this.width);
        Vector3f half = new Vector3f(axis).mul(this.length * 0.5F);
        int light = this.getLightColor(partialTicks);
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        vertex(buffer, px + right.x - half.x, py + right.y - half.y, pz + right.z - half.z, u1, v1, light);
        vertex(buffer, px + right.x + half.x, py + right.y + half.y, pz + right.z + half.z, u1, v0, light);
        vertex(buffer, px - right.x + half.x, py - right.y + half.y, pz - right.z + half.z, u0, v0, light);
        vertex(buffer, px - right.x - half.x, py - right.y - half.y, pz - right.z - half.z, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
