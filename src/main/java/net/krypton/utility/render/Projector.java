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

    /**
     * Screen-space bounding rectangle of a box, clipped against the near plane: a box that is partly behind the
     * camera (or that the camera is inside of) still gets a rectangle instead of vanishing. Returns
     * {minX, minY, maxX, maxY}, or null when the whole box is behind the camera.
     */
    public static float[] projectBoxClipped(AABB b, int guiW, int guiH) {
        if (!valid) {
            return null;
        }
        final float near = 0.05f;
        float[] cx = new float[8];
        float[] cy = new float[8];
        float[] cw = new float[8];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            TMP.set((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
            COMBINED.transform(TMP);
            cx[i] = TMP.x;
            cy[i] = TMP.y;
            cw[i] = TMP.w;
        }
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            if (cw[i] > near) {
                grow(bounds, cx[i] / cw[i], cy[i] / cw[i], guiW, guiH);
                any = true;
            }
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) {
                    continue;
                }
                int j = i | bit;
                if ((cw[i] > near) == (cw[j] > near)) {
                    continue;
                }
                float t = (near - cw[i]) / (cw[j] - cw[i]);
                float x = cx[i] + (cx[j] - cx[i]) * t;
                float y = cy[i] + (cy[j] - cy[i]) * t;
                grow(bounds, x / near, y / near, guiW, guiH);
                any = true;
            }
        }
        return any ? bounds : null;
    }

    private static void grow(float[] bounds, float nx, float ny, int guiW, int guiH) {
        float sx = (nx * 0.5f + 0.5f) * guiW;
        float sy = (0.5f - ny * 0.5f) * guiH;
        bounds[0] = Math.min(bounds[0], sx);
        bounds[1] = Math.min(bounds[1], sy);
        bounds[2] = Math.max(bounds[2], sx);
        bounds[3] = Math.max(bounds[3], sy);
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
