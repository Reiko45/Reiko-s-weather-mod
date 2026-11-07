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
        return c;
    }

    public void tick() {
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
        return t * t * (3.0F - 2.0F * t) * strength();
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
        return tag;
    }

    public static StormCell load(CompoundTag tag) {
        WeatherType type = WeatherType.byId(tag.getString("type"));
        StormCell cell = new StormCell(tag.getInt("id"), type == null ? WeatherType.RAIN : type, tag.getDouble("x"), tag.getDouble("z"),
                tag.getFloat("radius"), tag.getDouble("vx"), tag.getDouble("vz"), tag.getInt("maxAge"));
        cell.age = tag.getInt("age");
        cell.grow = tag.contains("grow") ? tag.getInt("grow") : FADE_TICKS;
        cell.steer = !tag.contains("steer") || tag.getBoolean("steer");
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
        return cell;
    }
}
