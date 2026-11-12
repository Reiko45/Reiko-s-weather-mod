package com.reiko.weather.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/** A swallow hawking for insects: fast, low over the ground, swooping and banking in long curves. */
public class SwallowParticle extends TextureSheetParticle {
    private float heading, turn;
    private final float height, speed;

    public SwallowParticle(ClientLevel level, double x, double y, double z, double dx, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.pickSprite(sprites);
        this.heading = (float) Mth.atan2(dz, dx);
        this.turn = (this.random.nextFloat() - 0.5F) * 0.12F;
        this.height = 1.2F + this.random.nextFloat() * 1.8F;
        this.speed = 0.45F + this.random.nextFloat() * 0.2F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.lifetime = 80 + this.random.nextInt(80);
        this.quadSize = 0.2F + this.random.nextFloat() * 0.05F;
        this.setColor(0.1F, 0.11F, 0.18F);
    }

    @Override
    public void tick() {
        super.tick();
        // long sweeping curves, now and then a sharp turn
        this.turn = Mth.clamp(this.turn + (this.random.nextFloat() - 0.5F) * 0.05F, -0.14F, 0.14F);
        if (this.random.nextInt(40) == 0) this.turn = -this.turn * 1.5F;
        this.heading += this.turn;
        this.xd = Mth.cos(this.heading) * this.speed;
        this.zd = Mth.sin(this.heading) * this.speed;
        // skimming just over the ground (or the water)
        int ground = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.x), Mth.floor(this.z));
        double target = ground + this.height + 0.4 * Mth.sin(this.age * 0.3F);
        this.yd = (target - this.y) * 0.25;
        float life = Mth.clamp(Math.min(this.age / 6.0F, (this.lifetime - this.age) / 6.0F), 0.0F, 1.0F);
        this.alpha = life;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
