package common;

public class Sphere implements SceneObject {
    private final Vec3   center;
    private final double radius;
    private final Vec3   color;
    private final double reflectivity;

    public Sphere(Vec3 center, double radius, Vec3 color, double reflectivity) {
        this.center       = center;
        this.radius       = radius;
        this.color        = color;
        this.reflectivity = reflectivity;
    }

    @Override
    public double intersect(Ray ray) {
        Vec3   oc = ray.origin.sub(center);
        double a  = ray.direction.dot(ray.direction);
        double b  = 2.0 * oc.dot(ray.direction);
        double c  = oc.dot(oc) - radius * radius;
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return -1;
        double t = (-b - Math.sqrt(discriminant)) / (2.0 * a);
        if (t > 1e-4) return t;
        t = (-b + Math.sqrt(discriminant)) / (2.0 * a);
        return (t > 1e-4) ? t : -1;
    }

    @Override
    public Vec3 getNormal(Vec3 hitPoint) {
        return hitPoint.sub(center).normalize();
    }

    @Override public Vec3   getColor()        { return color; }
    @Override public double getReflectivity() { return reflectivity; }
    public Vec3   getCenter() { return center; }
    public double getRadius() { return radius; }
}