package common;

import java.io.Serializable;

public class Camera implements Serializable {
    public final Vec3   position;
    public final Vec3   forward;
    public final Vec3   right;
    public final Vec3   up;
    public final double fovScale; // precomputed from FOV angle

    /**
     * @param position  camera position in world space
     * @param target    point the camera looks at
     * @param worldUp   world up vector (usually 0,1,0)
     * @param fovDeg    horizontal field of view in degrees
     */
    public Camera(Vec3 position, Vec3 target, Vec3 worldUp, double fovDeg) {
        this.position = position;
        this.forward  = target.sub(position).normalize();
        this.right    = this.forward.cross(worldUp).normalize();
        this.up       = this.right.cross(this.forward).normalize();
        this.fovScale = Math.tan(Math.toRadians(fovDeg / 2.0));
    }

    /**
     * Returns a ray through pixel (col, row) of an image of size (width x height).
     */
    public Ray getRay(int col, int row, int width, int height) {
        double aspectRatio = (double) width / height;
        double px = (2.0 * (col + 0.5) / width  - 1.0) * fovScale * aspectRatio;
        double py = (1.0 - 2.0 * (row + 0.5) / height) * fovScale;
        Vec3 dir = forward.add(right.mul(px)).add(up.mul(py)).normalize();
        return new Ray(position, dir);
    }
}