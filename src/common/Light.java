package common;

import java.io.Serializable;

public class Light implements Serializable {
    public final Vec3   position;
    public final Vec3   color;    // RGB intensity, e.g. (1,1,1) = white
    public final double intensity;

    public Light(Vec3 position, Vec3 color, double intensity) {
        this.position  = position;
        this.color     = color;
        this.intensity = intensity;
    }
}