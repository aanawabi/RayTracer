package common;

import java.io.Serializable;

public interface SceneObject extends Serializable {
    /**
     * Returns the distance t along the ray to the intersection,
     * or -1 if no intersection.
     */
    double intersect(Ray ray);

    Vec3 getNormal(Vec3 hitPoint);
    Vec3 getColor();
    double getReflectivity(); // 0.0 = matte, 1.0 = mirror
}