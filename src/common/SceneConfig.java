package common;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class SceneConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int    width;
    public final int    height;
    public final int    maxDepth;
    public final Camera camera;
    public final List<SceneObject> objects;
    public final List<Light>       lights;

    // Phong shading coefficients
    public final double ambientStrength;
    public final double diffuseStrength;
    public final double specularStrength;
    public final int    shininess;

    public SceneConfig(int width, int height, int maxDepth,
                       Camera camera,
                       List<SceneObject> objects,
                       List<Light> lights) {
        this.width           = width;
        this.height          = height;
        this.maxDepth        = maxDepth;
        this.camera          = camera;
        this.objects         = objects;
        this.lights          = lights;
        this.ambientStrength  = 0.15;
        this.diffuseStrength  = 0.85;
        this.specularStrength = 0.5;
        this.shininess        = 32;
    }

    /** Factory: builds the default demo scene used by all tests */
    public static SceneConfig buildDefaultScene(int width, int height, int maxDepth) {
        Camera camera = new Camera(
            new Vec3(0, 1, -5),
            new Vec3(0, 0,  0),
            new Vec3(0, 1,  0),
            60.0
        );

        List<SceneObject> objects = new ArrayList<>();
        // Floor plane
        objects.add(new Plane(new Vec3(0,-1,0), new Vec3(0,1,0),
                              new Vec3(0.8,0.8,0.8), 0.2));
        // Back wall
        objects.add(new Plane(new Vec3(0,0,8), new Vec3(0,0,-1),
                              new Vec3(0.7,0.7,0.9), 0.05));

        // Original spheres
        objects.add(new Sphere(new Vec3( 0.0, 0.0, 0), 1.0, new Vec3(0.9,0.2,0.2), 0.4));
        objects.add(new Sphere(new Vec3( 2.5, 0.0, 1), 1.0, new Vec3(0.2,0.5,0.9), 0.3));
        objects.add(new Sphere(new Vec3(-2.5, 0.0, 1), 1.0, new Vec3(0.2,0.9,0.3), 0.3));
        objects.add(new Sphere(new Vec3( 1.2,-0.4,-1), 0.6, new Vec3(0.9,0.8,0.1), 0.6));
        objects.add(new Sphere(new Vec3(-1.2,-0.4,-1), 0.6, new Vec3(0.7,0.2,0.9), 0.5));
        objects.add(new Sphere(new Vec3( 0.0, 3.5, 2), 1.2, new Vec3(0.95,0.95,0.95), 0.8));
        objects.add(new Sphere(new Vec3( 3.5,-0.3, 3), 0.7, new Vec3(0.9,0.5,0.1), 0.2));
        objects.add(new Sphere(new Vec3(-3.5,-0.3, 3), 0.7, new Vec3(0.1,0.8,0.8), 0.2));

        // Extra spheres — grid of 50 small spheres in the background
        // This is what makes BVH worthwhile
        java.util.Random rng = new java.util.Random(42); // fixed seed = reproducible
        for (int i = 0; i < 50; i++) {
            double x = (rng.nextDouble() - 0.5) * 14.0;
            double y = -0.7 + rng.nextDouble() * 3.0;
            double z =  2.0 + rng.nextDouble() * 8.0;
            double r = 0.2 + rng.nextDouble() * 0.4;
            Vec3 col = new Vec3(rng.nextDouble(), rng.nextDouble(), rng.nextDouble());
            double refl = rng.nextDouble() * 0.5;
            objects.add(new Sphere(new Vec3(x, y, z), r, col, refl));
        }
        
        List<Light> lights = new ArrayList<>();
        lights.add(new Light(new Vec3( 3, 6, -3), new Vec3(1,1,1),   1.0));
        lights.add(new Light(new Vec3(-4, 4,  1), new Vec3(0.8,0.8,1), 0.6));

        return new SceneConfig(width, height, maxDepth, camera, objects, lights);
    }
}