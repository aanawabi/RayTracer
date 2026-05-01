package common;

import java.io.Serializable;

public class Vec3 implements Serializable {
    private static final long serialVersionUID = 1L;
    public final double x, y, z;

    public Vec3(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public Vec3 add(Vec3 v)      { return new Vec3(x+v.x, y+v.y, z+v.z); }
    public Vec3 sub(Vec3 v)      { return new Vec3(x-v.x, y-v.y, z-v.z); }
    public Vec3 mul(double t)    { return new Vec3(x*t, y*t, z*t); }
    public Vec3 mul(Vec3 v)      { return new Vec3(x*v.x, y*v.y, z*v.z); }
    public Vec3 div(double t)    { return new Vec3(x/t, y/t, z/t); }
    public double dot(Vec3 v)    { return x*v.x + y*v.y + z*v.z; }
    public Vec3 cross(Vec3 v)    {
        return new Vec3(y*v.z - z*v.y, z*v.x - x*v.z, x*v.y - y*v.x);
    }
    public double length()       { return Math.sqrt(x*x + y*y + z*z); }
    public Vec3 normalize()      { double l = length(); return new Vec3(x/l, y/l, z/l); }
    public Vec3 negate()         { return new Vec3(-x, -y, -z); }
    public Vec3 reflect(Vec3 n)  { return this.sub(n.mul(2 * this.dot(n))); }

    public int toRGB_R() { return (int) Math.min(255, Math.max(0, x * 255)); }
    public int toRGB_G() { return (int) Math.min(255, Math.max(0, y * 255)); }
    public int toRGB_B() { return (int) Math.min(255, Math.max(0, z * 255)); }

    @Override
    public String toString() { return String.format("Vec3(%.3f, %.3f, %.3f)", x, y, z); }
}