package com.reiko.weather.weather;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * A regional storm: a circle of weather (or, for a derecho, a long band) that drifts across the map
 * with the wind, on top of the dimension-wide background weather.
 */
public final class StormCell {
    private static final int FADE_TICKS = 900;

    public final int id;
    public WeatherType type;
    public double x;
    public double z;
    public float radius;
    public double vx;
    public double vz;
    public int age;
    public int maxAge;
    /** Ticks the cell takes to build up to full strength (afternoon storms tower up slowly). */
    public int grow = FADE_TICKS;
    /** Whether it turns with the wind (a sea fog bank keeps rolling in toward the land). */
    public boolean steer = true;
    /** Hurricanes: how strong it is, 0 to 1. It strengthens over warm sea and weakens over land. */
    public float power = 1.0F;
    /** Radians a tick its track curves by (hurricanes curve round as they go). */
    public float turn;

    public StormCell(int id, WeatherType type, double x, double z, float radius, double vx, double vz, int maxAge) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.z = z;
        this.radius = radius;
        this.vx = vx;
        this.vz = vz;
        this.maxAge = maxAge;
    }

    /** An independent copy (payloads aren't serialized in singleplayer, so never share live cells). */
    public StormCell copy() {
        StormCell c = new StormCell(id, type, x, z, radius, vx, vz, maxAge);
        c.age = age;
        c.grow = grow;
        c.steer = steer;
        c.power = power;
        c.turn = turn;
        return c;
    }

    public void tick() {
        if (turn != 0.0F) {
            double c = Math.cos(turn), s = Math.sin(turn);
            double nx = vx * c - vz * s;
            vz = vx * s + vz * c;
            vx = nx;
        }
        x += vx;
        z += vz;
        age++;
    }

    public boolean expired() {
        return age >= maxAge;
    }

    /** 0 to 1: the storm builds up after forming and dies down before it ends. */
    public float strength() {
        float up = Mth.clamp(age / (float) Math.max(1, grow), 0.0F, 1.0F);
        float fade = Mth.clamp((maxAge - age) / (float) FADE_TICKS, 0.0F, 1.0F);
        return Math.min(up, fade);
    }

    /** How many times longer than deep the cell is: a derecho is a long line of storms side by side. */
    public float elongation() {
        return switch (type) {
            case DERECHO -> 3.5F;
            case COLD_FRONT -> 9.0F;
            case WARM_FRONT -> 3.0F;
            default -> 1.0F;
        };
    }

    /**
     * Distance to the centre, scaled so the cell's outline is at {@code radius}: a circle for most storms,
     * a band stretched across the direction of travel for a derecho.
     */
    public double distanceTo(double px, double pz) {
        double dx = px - x, dz = pz - z;
        float e = elongation();
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (e != 1.0F && speed > 1.0E-6) {
            double ux = vx / speed, uz = vz / speed;
            double along = dx * ux + dz * uz, across = (-dx * uz + dz * ux) / e;
            return Math.sqrt(along * along + across * across);
        }
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How strongly this cell's weather applies at a position: 1 in the core, fading out over the edge. */
    public float weightAt(double px, double pz) {
        double d = distanceTo(px, pz);
        if (d >= radius) {
            return 0.0F;
        }
        float t = Mth.clamp((float) ((radius - d) / (radius * 0.3)), 0.0F, 1.0F);
        float w = t * t * (3.0F - 2.0F * t) * strength();
        return type == WeatherType.HURRICANE ? w * hurricaneStructure(px, pz, d / radius) : w;
    }

    /** The calm eye of a hurricane, as a fraction of its radius (0 while it's too weak to have one). */
    public float eyeFraction() {
        return type == WeatherType.HURRICANE && power >= 0.45F ? 0.05F + 0.03F * power : 0.0F;
    }

    /** 1 to 5, the hurricane's category. */
    public int category() {
        return Mth.clamp(1 + (int) (power * 5.0F), 1, 5);
    }

    /**
     * A hurricane is not a uniform disc: there's a calm, clear eye in the middle, the worst of the wind in the
     * eyewall around it, and spiral rain bands sweeping round (anticlockwise) farther out, with lulls between them.
     */
    private float hurricaneStructure(double px, double pz, double r) {
        float eye = eyeFraction();
        float k = 1.0F;
        if (eye > 0.0F) {
            float wall = Mth.clamp((float) ((r - eye) / (eye * 0.7)), 0.0F, 1.0F);
            k *= wall * wall * (3.0F - 2.0F * wall);
        }
        double inner = Math.max(eye, 0.05F) * 3.0;
        if (r > inner) {
            double angle = Math.atan2(pz - z, px - x);
            float band = 0.5F + 0.5F * Mth.sin((float) (angle * 2.0 - Math.log(Math.max(r, 0.01)) * 9.0 + age * 0.0015));
            float outer = Mth.clamp((float) ((r - inner) / 0.25), 0.0F, 1.0F);
            k *= 1.0F - outer * 0.42F * (1.0F - band);
        }
        return k * (0.55F + 0.45F * Mth.clamp(power, 0.0F, 1.0F));
    }

    /** Whether a point is in the hurricane's eye. */
    public boolean inEye(double px, double pz) {
        float eye = eyeFraction();
        return eye > 0.0F && distanceTo(px, pz) < radius * eye;
    }

    /** Whether the cell is moving toward a point (used for "approaching" reports). */
    public boolean approaching(double px, double pz) {
        return (px - x) * vx + (pz - z) * vz > 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("type", type.id());
        tag.putDouble("x", x);
        tag.putDouble("z", z);
        tag.putFloat("radius", radius);
        tag.putDouble("vx", vx);
        tag.putDouble("vz", vz);
        tag.putInt("age", age);
        tag.putInt("maxAge", maxAge);
        tag.putInt("grow", grow);
        tag.putBoolean("steer", steer);
        tag.putFloat("power", power);
        tag.putFloat("turn", turn);
        return tag;
    }

    public static StormCell load(CompoundTag tag) {
        WeatherType type = WeatherType.byId(tag.getString("type"));
        StormCell cell = new StormCell(tag.getInt("id"), type == null ? WeatherType.RAIN : type, tag.getDouble("x"), tag.getDouble("z"),
                tag.getFloat("radius"), tag.getDouble("vx"), tag.getDouble("vz"), tag.getInt("maxAge"));
        cell.age = tag.getInt("age");
        cell.grow = tag.contains("grow") ? tag.getInt("grow") : FADE_TICKS;
        cell.steer = !tag.contains("steer") || tag.getBoolean("steer");
        cell.power = tag.contains("power") ? tag.getFloat("power") : 1.0F;
        cell.turn = tag.getFloat("turn");
        return cell;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeVarInt(type.ordinal());
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeFloat(radius);
        buf.writeFloat((float) vx);
        buf.writeFloat((float) vz);
        buf.writeVarInt(age);
        buf.writeVarInt(maxAge);
        buf.writeVarInt(grow);
        buf.writeBoolean(steer);
        buf.writeFloat(power);
        buf.writeFloat(turn);
    }

    public static StormCell read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        WeatherType type = WeatherType.byOrdinal(buf.readVarInt());
        double x = buf.readDouble(), z = buf.readDouble();
        float radius = buf.readFloat();
        double vx = buf.readFloat(), vz = buf.readFloat();
        int age = buf.readVarInt(), maxAge = buf.readVarInt();
        StormCell cell = new StormCell(id, type, x, z, radius, vx, vz, maxAge);
        cell.age = age;
        cell.grow = buf.readVarInt();
        cell.steer = buf.readBoolean();
        cell.power = buf.readFloat();
        cell.turn = buf.readFloat();
        return cell;
    }
}
