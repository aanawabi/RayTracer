package core;

import common.*;

public class RayTracer {

    private final SceneConfig scene;
    private final BVH         bvh;       // null = brute force mode
    private final boolean     useBVH;

    /** Standard constructor — brute force O(n) intersection */
    public RayTracer(SceneConfig scene) {
        this.scene  = scene;
        this.bvh    = null;
        this.useBVH = false;
    }

    /** BVH constructor — O(log n) intersection */
    public RayTracer(SceneConfig scene, BVH bvh) {
        this.scene  = scene;
        this.bvh    = bvh;
        this.useBVH = true;
    }

    public Vec3 traceRay(Ray ray, int depth) {
        if (depth <= 0) return new Vec3(0, 0, 0);

        double      tMin   = Double.MAX_VALUE;
        SceneObject hitObj = null;

        if (useBVH) {
            Object[] hit = bvh.intersect(ray);
            double t = (double) hit[0];
            if (t > 0) { tMin = t; hitObj = (SceneObject) hit[1]; }
        } else {
            for (SceneObject obj : scene.objects) {
                double t = obj.intersect(ray);
                if (t > 0 && t < tMin) { tMin = t; hitObj = obj; }
            }
        }

        // Background sky gradient
        if (hitObj == null) {
            double t = 0.5 * (ray.direction.y + 1.0);
            return new Vec3(1,1,1).mul(1.0 - t).add(new Vec3(0.5,0.7,1.0).mul(t));
        }

        Vec3 hitPoint = ray.at(tMin);
        Vec3 normal   = hitObj.getNormal(hitPoint);
        Vec3 objColor = hitObj.getColor();

        // Ambient
        Vec3 color = objColor.mul(scene.ambientStrength);

        for (Light light : scene.lights) {
            Vec3   toLight   = light.position.sub(hitPoint).normalize();
            double lightDist = light.position.sub(hitPoint).length();

            if (!isInShadow(hitPoint, toLight, lightDist)) {
                // Diffuse
                double diff = Math.max(0, normal.dot(toLight));
                color = color.add(objColor.mul(diff * scene.diffuseStrength * light.intensity));

                // Specular
                Vec3   viewDir  = ray.direction.negate().normalize();
                Vec3   halfVec  = toLight.add(viewDir).normalize();
                double spec     = Math.pow(Math.max(0, normal.dot(halfVec)), scene.shininess);
                color = color.add(light.color.mul(spec * scene.specularStrength * light.intensity));
            }
        }

        color = clamp(color);

        // Reflection
        double reflectivity = hitObj.getReflectivity();
        if (reflectivity > 0 && depth > 1) {
            Vec3 reflectDir   = ray.direction.reflect(normal);
            Vec3 reflectColor = traceRay(new Ray(hitPoint, reflectDir), depth - 1);
            color = color.mul(1.0 - reflectivity).add(reflectColor.mul(reflectivity));
        }

        return clamp(color);
    }

    private boolean isInShadow(Vec3 point, Vec3 toLight, double lightDist) {
        Ray shadowRay = new Ray(point, toLight);
        if (useBVH) {
            return bvh.intersectAny(shadowRay, lightDist);
        }
        for (SceneObject obj : scene.objects) {
            double t = obj.intersect(shadowRay);
            if (t > 1e-4 && t < lightDist) return true;
        }
        return false;
    }


    private Vec3 clamp(Vec3 v) {
        return new Vec3(Math.min(1,Math.max(0,v.x)),
                        Math.min(1,Math.max(0,v.y)),
                        Math.min(1,Math.max(0,v.z)));
    }
}