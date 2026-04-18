package common;

public class Ray {
    public final Vec3 origin;
    public final Vec3 direction; // always normalized

    public Ray(Vec3 origin, Vec3 direction) {
        this.origin    = origin;
        this.direction = direction.normalize();
    }

    public Vec3 at(double t) {
        return origin.add(direction.mul(t));
    }
}