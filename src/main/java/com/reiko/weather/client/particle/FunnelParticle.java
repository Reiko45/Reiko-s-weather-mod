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
    /** Which suction vortex this puff whirls in (-1: the main funnel). */
    private final int vortex;

    public FunnelParticle(ClientLevel level, TornadoEntity tornado, float angle, float height, SpriteSet sprites) {
        this(level, tornado, angle, height, sprites, -1);
    }

    public FunnelParticle(ClientLevel level, TornadoEntity tornado, float angle, float height, SpriteSet sprites, int vortex) {
        super(level, tornado.getX(), tornado.getY() + height, tornado.getZ());
        this.tornado = tornado;
        this.vortex = vortex;
        this.angle = angle;
        this.height = height;
        this.radiusScale = 0.75F + this.random.nextFloat() * 0.45F;
        this.riseSpeed = (0.08F + this.random.nextFloat() * 0.15F) * Math.max(0.3F, tornado.height() / 70.0F);
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.lifetime = 50 + this.random.nextInt(40);
        float scale = Math.max(0.3F, tornado.height() / 70.0F);
        // (the puffs swell toward the cloud base, less so in a straight-sided stovepipe or a narrow trunk)
        float swell = tornado.kind() == TornadoEntity.Kind.TORNADO && (tornado.shape() == TornadoEntity.Shape.STOVEPIPE
                || tornado.shape() == TornadoEntity.Shape.TRUNK) ? 1.2F : 4.0F;
        this.quadSize = (1.8F + this.random.nextFloat() * 1.8F + height / tornado.height() * swell) * scale * (vortex >= 0 ? 0.55F : 1.0F);
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        float shade;
        switch (tornado.kind()) {
            case WATERSPOUT -> { shade = 0.78F + this.random.nextFloat() * 0.17F; this.setColor(shade * 0.96F, shade, Math.min(1.0F, shade * 1.05F)); }
            case DUST_DEVIL -> { shade = 0.8F + this.random.nextFloat() * 0.2F; this.setColor(0.86F * shade, 0.72F * shade, 0.52F * shade); }
            case GUSTNADO -> { shade = 0.65F + this.random.nextFloat() * 0.2F; this.setColor(0.8F * shade, 0.72F * shade, 0.6F * shade); }
            case STEAM_DEVIL, SNOW_DEVIL -> { shade = 0.92F + this.random.nextFloat() * 0.08F; this.setColor(shade, shade, Math.min(1.0F, shade * 1.03F)); }
            case LANDSPOUT -> {
                // a pale condensation rope above, dust swirling at its foot
                boolean dust = height < tornado.height() * 0.12F;
                shade = 0.7F + this.random.nextFloat() * 0.2F;
                if (dust) this.setColor(0.78F * shade, 0.68F * shade, 0.55F * shade);
                else this.setColor(shade, shade, shade * 1.04F);
            }
            case FIRE_WHIRL -> {
                // flame low down, dark smoke higher up
                boolean flame = height < tornado.height() * 0.35F && this.random.nextFloat() < 0.7F;
                shade = 0.8F + this.random.nextFloat() * 0.2F;
                if (flame) this.setColor(1.0F, 0.55F * shade, 0.15F * shade);
                else this.setColor(0.25F * shade, 0.22F * shade, 0.2F * shade);
            }
            default -> { shade = 0.5F + this.random.nextFloat() * 0.25F; this.setColor(shade, shade * 1.02F, shade * 1.06F); }
        }
        this.maxAlpha = (0.7F + this.random.nextFloat() * 0.25F) * switch (tornado.kind()) {
            case DUST_DEVIL, GUSTNADO -> 0.55F;
            case STEAM_DEVIL, SNOW_DEVIL -> 0.4F;
            default -> 1.0F;
        };
        if (vortex >= 0) {
            // the little vortices are full of dust and debris churned up off the ground
            shade = 0.55F + this.random.nextFloat() * 0.2F;
            this.setColor(0.72F * shade, 0.64F * shade, 0.54F * shade);
        } else if (tornado.rainWrapped()) {
            // seen through the rain, a dark grey ghost
            shade = 0.42F + this.random.nextFloat() * 0.12F;
            this.setColor(shade, shade * 1.02F, shade * 1.08F);
        }
        this.alpha = 0.0F;
        updatePosition();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    private void updatePosition() {
        if (vortex >= 0) {
            // whirling tight round its vortex, which races round the funnel's foot
            double[] centre = tornado.vortexAt(vortex, 0.0F);
            float radius = (0.8F + height * 0.08F) * radiusScale;
            this.setPos(centre[0] + Mth.cos(angle) * radius, tornado.getY() + height, centre[1] + Mth.sin(angle) * radius);
            return;
        }
        float radius = tornado.radiusAt(height) * radiusScale;
        float[] bend = tornado.axisOffset(height, 0.0F);
        this.setPos(tornado.getX() + bend[0] + Mth.cos(angle) * radius, tornado.getY() + height, tornado.getZ() + bend[1] + Mth.sin(angle) * radius);
    }

    /** How much of a rain-wrapped tornado shows through the rain at this distance (all of it, close up). */
    private float throughRain() {
        if (!tornado.rainWrapped()) return 1.0F;
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - tornado.getX(), dz = cam.z - tornado.getZ();
        return Mth.clamp(1.5F - (float) Math.sqrt(dx * dx + dz * dz) / 40.0F, 0.12F, 1.0F);
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
        this.alpha = this.maxAlpha * Mth.clamp(fade, 0.0F, 1.0F) * (0.3F + 0.7F * tornado.strength()) * throughRain();
        if (vortex >= 0) {
            // a vortex dies away if the tornado weakens
            if (tornado.activeVortices() <= vortex) this.alpha *= 0.3F;
            this.angle += 0.45F;
            this.height += riseSpeed * 0.6F;
            if (this.height > tornado.height() * 0.3F) this.remove();
            updatePosition();
            return;
        }
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
