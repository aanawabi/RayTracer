package common;

public class Plane implements SceneObject {
    private final Vec3   point;  // a point on the plane
    private final Vec3   normal; // plane normal (unit vector)
    private final Vec3   color;
    private final double reflectivity;

    public Plane(Vec3 point, Vec3 normal, Vec3 color, double reflectivity) {
        this.point        = point;
        this.normal       = normal.normalize();
        this.color        = color;
        this.reflectivity = reflectivity;
    }

    @Override
    public double intersect(Ray ray) {
        double denom = normal.dot(ray.direction);
        if (Math.abs(denom) < 1e-6) return -1; // parallel
        double t = point.sub(ray.origin).dot(normal) / denom;
        return (t > 1e-4) ? t : -1;
    }

    @Override
    public Vec3 getNormal(Vec3 hitPoint) { return normal; }

    @Override public Vec3   getColor()        { return color; }
    @Override public double getReflectivity() { return reflectivity; }
}