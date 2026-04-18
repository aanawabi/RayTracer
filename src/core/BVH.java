package core;

import common.*;
import java.util.List;
import java.util.ArrayList;

public class BVH {

    public static class AABB {
        public final Vec3 min, max;

        public AABB(Vec3 min, Vec3 max) {
            this.min = min;
            this.max = max;
        }

        public boolean intersect(Ray ray) {
            double tMin = 0.0001;
            double tMax = Double.MAX_VALUE;

            double[] origin = {ray.origin.x, ray.origin.y, ray.origin.z};
            double[] dir    = {ray.direction.x, ray.direction.y, ray.direction.z};
            double[] bMin   = {min.x, min.y, min.z};
            double[] bMax   = {max.x, max.y, max.z};

            for (int i = 0; i < 3; i++) {
                if (Math.abs(dir[i]) < 1e-8) {
                    if (origin[i] < bMin[i] || origin[i] > bMax[i]) return false;
                } else {
                    double t1 = (bMin[i] - origin[i]) / dir[i];
                    double t2 = (bMax[i] - origin[i]) / dir[i];
                    if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                    tMin = Math.max(tMin, t1);
                    tMax = Math.min(tMax, t2);
                    if (tMin > tMax) return false;
                }
            }
            return true;
        }

        public static AABB merge(AABB a, AABB b) {
            return new AABB(
                new Vec3(Math.min(a.min.x, b.min.x),
                         Math.min(a.min.y, b.min.y),
                         Math.min(a.min.z, b.min.z)),
                new Vec3(Math.max(a.max.x, b.max.x),
                         Math.max(a.max.y, b.max.y),
                         Math.max(a.max.z, b.max.z))
            );
        }
    }

    private static class BVHNode {
        AABB        bounds;
        BVHNode     left, right;
        SceneObject object;

        BVHNode(SceneObject obj, AABB bounds) {
            this.object = obj;
            this.bounds = bounds;
        }

        BVHNode(BVHNode left, BVHNode right) {
            this.left   = left;
            this.right  = right;
            this.bounds = AABB.merge(left.bounds, right.bounds);
        }

        boolean isLeaf() { return object != null; }
    }

    private final BVHNode            root;
    private final List<SceneObject>  infiniteObjects; // planes — always tested

    public BVH(List<SceneObject> objects) {
        infiniteObjects = new ArrayList<>();
        List<BVHNode> nodes = new ArrayList<>();

        for (SceneObject obj : objects) {
            if (obj instanceof Sphere) {
                Sphere s    = (Sphere) obj;
                Vec3   c    = s.getCenter();
                double r    = s.getRadius();
                AABB   box  = new AABB(
                    new Vec3(c.x-r, c.y-r, c.z-r),
                    new Vec3(c.x+r, c.y+r, c.z+r)
                );
                nodes.add(new BVHNode(obj, box));
            } else {
                // Infinite objects (planes) bypass the tree
                infiniteObjects.add(obj);
            }
        }

        if (nodes.isEmpty()) {
            root = null;
        } else if (nodes.size() == 1) {
            root = nodes.get(0);
        } else {
            root = build(nodes, 0);
        }
    }

    private BVHNode build(List<BVHNode> nodes, int axis) {
        if (nodes.size() == 1) return nodes.get(0);
        if (nodes.size() == 2) return new BVHNode(nodes.get(0), nodes.get(1));

        final int sortAxis = axis;
        nodes.sort((a, b) -> {
            double ca = centroid(a.bounds, sortAxis);
            double cb = centroid(b.bounds, sortAxis);
            return Double.compare(ca, cb);
        });

        int mid = nodes.size() / 2;
        BVHNode left  = build(new ArrayList<>(nodes.subList(0, mid)),        (axis+1)%3);
        BVHNode right = build(new ArrayList<>(nodes.subList(mid, nodes.size())), (axis+1)%3);
        return new BVHNode(left, right);
    }

    private double centroid(AABB box, int axis) {
        if (axis == 0) return (box.min.x + box.max.x) / 2.0;
        if (axis == 1) return (box.min.y + box.max.y) / 2.0;
        return              (box.min.z + box.max.z) / 2.0;
    }

    /**
     * Find closest intersection across BVH spheres + infinite objects.
     * Returns {t, hitObject} or {-1.0, null}.
     */
    public Object[] intersect(Ray ray) {
        double      tMin   = Double.MAX_VALUE;
        SceneObject hitObj = null;

        // Always test infinite objects (planes)
        for (SceneObject obj : infiniteObjects) {
            double t = obj.intersect(ray);
            if (t > 0 && t < tMin) { tMin = t; hitObj = obj; }
        }

        // BVH traversal for finite objects
        if (root != null) {
            Object[] hit = traverse(root, ray, tMin);
            double t = (double) hit[0];
            if (t > 0 && t < tMin) { tMin = t; hitObj = (SceneObject) hit[1]; }
        }

        return hitObj != null ? new Object[]{tMin, hitObj}
                              : new Object[]{-1.0, null};
    }

    /**
     * Shadow test — just needs any hit closer than lightDist.
     */
    public boolean intersectAny(Ray ray, double maxDist) {
        // Check infinite objects first
        for (SceneObject obj : infiniteObjects) {
            double t = obj.intersect(ray);
            if (t > 1e-4 && t < maxDist) return true;
        }
        // BVH traversal
        if (root != null) return traverseAny(root, ray, maxDist);
        return false;
    }

    private Object[] traverse(BVHNode node, Ray ray, double tMax) {
        if (!node.bounds.intersect(ray)) return new Object[]{-1.0, null};

        if (node.isLeaf()) {
            double t = node.object.intersect(ray);
            if (t > 0 && t < tMax) return new Object[]{t, node.object};
            return new Object[]{-1.0, null};
        }

        Object[] leftHit  = traverse(node.left,  ray, tMax);
        double   tLeft    = (double) leftHit[0];

        // If left hit, use it as new tMax to prune right side
        double newTMax = tLeft > 0 ? Math.min(tMax, tLeft) : tMax;

        Object[] rightHit = traverse(node.right, ray, newTMax);
        double   tRight   = (double) rightHit[0];

        if (tLeft > 0 && tRight > 0) return tLeft < tRight ? leftHit : rightHit;
        if (tLeft  > 0) return leftHit;
        if (tRight > 0) return rightHit;
        return new Object[]{-1.0, null};
    }

    private boolean traverseAny(BVHNode node, Ray ray, double maxDist) {
        if (!node.bounds.intersect(ray)) return false;
        if (node.isLeaf()) {
            double t = node.object.intersect(ray);
            return t > 1e-4 && t < maxDist;
        }
        return traverseAny(node.left, ray, maxDist)
            || traverseAny(node.right, ray, maxDist);
    }
}