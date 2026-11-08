package net.krypton.utility.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Captures the camera matrices at the end of render-state extraction and projects world coordinates to
 * scaled GUI coordinates. This is what lets the ESP draw boxes that remain visible through terrain
 * without needing a custom render pipeline.
 */
public final class Projector {
    private static final Matrix4f COMBINED = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean valid;

    private Projector() {
    }

    public static void capture(WorldExtractionContext context) {
        Matrix4f projection = new Matrix4f(context.cullProjectionMatrix());
        Matrix4f view = new Matrix4f(context.viewMatrix());
        COMBINED.set(projection).mul(view);
        camera = context.worldState().cameraRenderState.pos;
        valid = true;
    }

    public static boolean isValid() {
        return valid;
    }

    public static Vec3 camera() {
        return camera;
    }

    public static void invalidate() {
        valid = false;
    }

    /** Projects a point; returns false when it is behind the camera. out = {x, y} in scaled GUI pixels. */
    public static boolean project(double x, double y, double z, int guiW, int guiH, float[] out) {
        if (!valid) {
            return false;
        }
        TMP.set((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
        COMBINED.transform(TMP);
        if (TMP.w <= 0.05f) {
            return false;
        }
        float nx = TMP.x / TMP.w;
        float ny = TMP.y / TMP.w;
        out[0] = (nx * 0.5f + 0.5f) * guiW;
        out[1] = (0.5f - ny * 0.5f) * guiH;
        return true;
    }

    /** Screen-space bounding rectangle of a box: {minX, minY, maxX, maxY}, or null if any corner is behind the camera. */
    public static float[] projectBox(AABB b, int guiW, int guiH) {
        float[] p = new float[2];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!project(x, y, z, guiW, guiH, p)) {
                return null;
            }
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        return new float[]{minX, minY, maxX, maxY};
    }
}
