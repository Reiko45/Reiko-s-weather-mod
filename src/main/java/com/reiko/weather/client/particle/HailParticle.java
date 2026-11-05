package com.reiko.weather.client.particle;

import com.reiko.weather.client.ClientWeather;
import com.reiko.weather.weather.Wind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.phys.Vec3;

/** A hailstone: falls fast, lands, then melts away. */
public class HailParticle extends TextureSheetParticle {
    private int landedFor = -1;
    private final float startSize;

    public HailParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.pickSprite(sprites);
        this.quadSize = 0.08F + this.random.nextFloat() * 0.07F;
        this.startSize = this.quadSize;
        this.lifetime = 80;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.setSize(0.05F, 0.05F);
        float shade = 0.88F + this.random.nextFloat() * 0.12F;
        this.setColor(shade, shade, Math.min(1.0F, shade + 0.06F));
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
        if (landedFor >= 0) {
            landedFor++;
            this.quadSize = startSize * Math.max(0.0F, 1.0F - landedFor / 25.0F);
            if (landedFor > 25) {
                this.remove();
            }
            return;
        }
        Vec3 wind = Wind.direction(ClientWeather.windAngle()).scale(ClientWeather.wind() * 0.35);
        this.xd += (wind.x - this.xd) * 0.05;
        this.zd += (wind.z - this.zd) * 0.05;
        this.move(this.xd, this.yd, this.zd);
        if (this.onGround) {
            landedFor = 0;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
}
