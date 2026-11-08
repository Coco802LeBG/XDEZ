package net.krypton.utility.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.krypton.utility.module.BlockScannerModule;
import net.krypton.utility.module.EspModule;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.ui.Draw;
import net.minecraft.class_12249;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared renderer for box highlights.
 * <ul>
 *   <li>Screen-space styles (Outline / Corners / Filled) are drawn from the HUD pass using projected boxes, so
 *       they stay visible through walls.</li>
 *   <li>"World 3D" draws real depth-tested boxes in the level using the debug filled-box render type.</li>
 * </ul>
 */
public final class EspRenderer {
    public static final String[] STYLES = {"Outline", "Corners", "Filled", "World 3D"};
    private static final String WORLD_3D = "World 3D";

    private EspRenderer() {
    }

    private static List<HighlightSource> sources() {
        List<HighlightSource> list = new ArrayList<>(2);
        list.add(ModuleManager.get(EspModule.class));
        list.add(ModuleManager.get(BlockScannerModule.class));
        return list;
    }

    private static float partialTick() {
        return class_310.method_1551().method_61966().method_60637(false);
    }

    // ------------------------------------------------------------------ screen space

    public static void render2D(class_332 g, float pt) {
        if (!Projector.isValid()) {
            return;
        }
        int sw = g.method_51421();
        int sh = g.method_51443();
        for (HighlightSource src : sources()) {
            if (!src.isEnabled() || src.style().equals(WORLD_3D)) {
                continue;
            }
            List<Target> targets = new ArrayList<>();
            src.collect(pt, targets);
            // far to near so that closer boxes sit on top
            targets.sort((a, b) -> Double.compare(b.distSq(), a.distSq()));
            for (Target t : targets) {
                float[] r = Projector.projectBoxClipped(t.box(), sw, sh);
                if (r == null || r[2] < 0 || r[0] > sw || r[3] < 0 || r[1] > sh) {
                    continue;
                }
                // a box the camera is inside of (or very close to) is clamped to the screen instead of being dropped
                float[] c = {Math.max(r[0], -2f), Math.max(r[1], -2f), Math.min(r[2], sw + 2f), Math.min(r[3], sh + 2f)};
                drawBox2D(g, c, t.color(), src.style(), src.fillAlpha());
                if (src.healthBars() && t.health() >= 0f) {
                    drawHealthBar(g, c, t.health());
                }
                if (src.labels() && t.label() != null) {
                    drawLabel(g, t, r);
                }
            }
        }
    }

    private static void drawBox2D(class_332 g, float[] r, int color, String style, int fillAlpha) {
        int x1 = Math.round(r[0]);
        int y1 = Math.round(r[1]);
        int x2 = Math.max(x1 + 2, Math.round(r[2]));
        int y2 = Math.max(y1 + 2, Math.round(r[3]));
        int w = x2 - x1;
        int h = y2 - y1;
        if (w > 4096 || h > 4096) {
            return;
        }
        int solid = color | 0xFF000000;
        int shade = Draw.withAlpha(0x000000, 0.55f);
        if (style.equals("Filled")) {
            g.method_25294(x1, y1, x2, y2, Draw.withAlpha(color, fillAlpha / 255f));
        }
        if (style.equals("Corners")) {
            int len = Math.max(3, Math.min(w, h) / 4);
            corner(g, x1, y1, len, 1, 1, solid, shade);
            corner(g, x2, y1, len, -1, 1, solid, shade);
            corner(g, x1, y2, len, 1, -1, solid, shade);
            corner(g, x2, y2, len, -1, -1, solid, shade);
        } else {
            // dark halo + crisp coloured line
            g.method_25294(x1 - 1, y1 - 1, x2 + 1, y1 + 2, shade);
            g.method_25294(x1 - 1, y2 - 2, x2 + 1, y2 + 1, shade);
            g.method_25294(x1 - 1, y1 + 2, x1 + 2, y2 - 2, shade);
            g.method_25294(x2 - 2, y1 + 2, x2 + 1, y2 - 2, shade);
            g.method_25294(x1, y1, x2, y1 + 1, solid);
            g.method_25294(x1, y2 - 1, x2, y2, solid);
            g.method_25294(x1, y1 + 1, x1 + 1, y2 - 1, solid);
            g.method_25294(x2 - 1, y1 + 1, x2, y2 - 1, solid);
        }
    }

    private static void drawHealthBar(class_332 g, float[] r, float fraction) {
        int h = Math.round(r[3]) - Math.round(r[1]);
        if (h < 14) {
            return;
        }
        int x = Math.round(r[0]) - 5;
        int y1 = Math.round(r[1]);
        int y2 = Math.round(r[3]);
        float f = Math.max(0f, Math.min(1f, fraction));
        int color = 0xFF000000 | Draw.lerp(0xFFEF4444, 0xFF22C55E, f) & 0x00FFFFFF;
        int filled = Math.max(1, Math.round((y2 - y1) * f));
        Draw.rect(g, x - 1, y1 - 1, 4, y2 - y1 + 2, Draw.withAlpha(0x000000, 0.6f));
        Draw.rect(g, x, y2 - filled, 2, filled, color);
    }

    private static void corner(class_332 g, int x, int y, int len, int dx, int dy, int solid, int shade) {
        // horizontal arm
        int hx1 = dx > 0 ? x : x - len;
        int hx2 = dx > 0 ? x + len : x;
        int hy1 = dy > 0 ? y : y - 1;
        g.method_25294(hx1 - 1, hy1 - 1, hx2 + 1, hy1 + 2, shade);
        // vertical arm
        int vy1 = dy > 0 ? y : y - len;
        int vy2 = dy > 0 ? y + len : y;
        int vx1 = dx > 0 ? x : x - 1;
        g.method_25294(vx1 - 1, vy1 - 1, vx1 + 2, vy2 + 1, shade);
        g.method_25294(hx1, hy1, hx2, hy1 + 1, solid);
        g.method_25294(vx1, vy1, vx1 + 1, vy2, solid);
    }

    private static void drawLabel(class_332 g, Target t, float[] r) {
        String text = t.label();
        int w = Draw.width(text);
        int cx = Math.round((r[0] + r[2]) / 2f);
        int ty = Math.round(r[1]) - 14;
        Draw.round(g, cx - w / 2 - 4, ty - 2, w + 8, 12, 4, 0xB00E0E16);
        Draw.rect(g, cx - w / 2 - 4, ty + 8, w + 8, 2, t.color() | 0xFF000000);
        Draw.text(g, text, cx - w / 2, ty, 0xFFEDEDF5);
    }

    // ------------------------------------------------------------------ world space

    public static void render3D(WorldRenderContext context) {
        List<Target> targets = new ArrayList<>();
        int alpha = 0;
        boolean any = false;
        float pt = partialTick();
        for (HighlightSource src : sources()) {
            if (src.isEnabled() && src.style().equals(WORLD_3D)) {
                src.collect(pt, targets);
                alpha = Math.max(alpha, src.fillAlpha());
                any = true;
            }
        }
        if (!any || targets.isEmpty()) {
            return;
        }
        class_243 cam = context.worldState().field_63082.field_63078;
        class_4587 ps = context.matrices();
        ps.method_22903();
        ps.method_22904(-cam.field_1352, -cam.field_1351, -cam.field_1350);
        class_4588 vc = context.consumers().method_73477(class_12249.method_76019());
        Matrix4f pose = ps.method_23760().method_23761();
        for (Target t : targets) {
            int rgb = t.color() & 0x00FFFFFF;
            class_238 b = t.box();
            if (alpha > 0) {
                filledBox(vc, pose, b, (Math.max(24, alpha / 2) << 24) | rgb);
            }
            edges(vc, pose, b, 0xFF000000 | rgb);
        }
        ps.method_22909();
    }

    private static void edges(class_4588 vc, Matrix4f m, class_238 b, int color) {
        double t = 0.018;
        for (int yi = 0; yi < 2; yi++) {
            for (int zi = 0; zi < 2; zi++) {
                double y = yi == 0 ? b.field_1322 : b.field_1325 - t;
                double z = zi == 0 ? b.field_1321 : b.field_1324 - t;
                filledBox(vc, m, new class_238(b.field_1323, y, z, b.field_1320, y + t, z + t), color);
            }
        }
        for (int xi = 0; xi < 2; xi++) {
            for (int zi = 0; zi < 2; zi++) {
                double x = xi == 0 ? b.field_1323 : b.field_1320 - t;
                double z = zi == 0 ? b.field_1321 : b.field_1324 - t;
                filledBox(vc, m, new class_238(x, b.field_1322, z, x + t, b.field_1325, z + t), color);
            }
        }
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                double x = xi == 0 ? b.field_1323 : b.field_1320 - t;
                double y = yi == 0 ? b.field_1322 : b.field_1325 - t;
                filledBox(vc, m, new class_238(x, y, b.field_1321, x + t, y + t, b.field_1324), color);
            }
        }
    }

    /** Quad layout identical to Fabric API's own test renderer for RenderTypes.debugFilledBox(). */
    private static void filledBox(class_4588 vc, Matrix4f m, class_238 b, int color) {
        float x1 = (float) b.field_1323;
        float y1 = (float) b.field_1322;
        float z1 = (float) b.field_1321;
        float x2 = (float) b.field_1320;
        float y2 = (float) b.field_1325;
        float z2 = (float) b.field_1324;
        // front
        vc.method_22918(m, x1, y1, z1).method_39415(color);
        vc.method_22918(m, x2, y1, z1).method_39415(color);
        vc.method_22918(m, x2, y2, z1).method_39415(color);
        vc.method_22918(m, x1, y2, z1).method_39415(color);
        // back
        vc.method_22918(m, x2, y1, z2).method_39415(color);
        vc.method_22918(m, x1, y1, z2).method_39415(color);
        vc.method_22918(m, x1, y2, z2).method_39415(color);
        vc.method_22918(m, x2, y2, z2).method_39415(color);
        // left
        vc.method_22918(m, x1, y1, z2).method_39415(color);
        vc.method_22918(m, x1, y1, z1).method_39415(color);
        vc.method_22918(m, x1, y2, z1).method_39415(color);
        vc.method_22918(m, x1, y2, z2).method_39415(color);
        // right
        vc.method_22918(m, x2, y1, z1).method_39415(color);
        vc.method_22918(m, x2, y1, z2).method_39415(color);
        vc.method_22918(m, x2, y2, z2).method_39415(color);
        vc.method_22918(m, x2, y2, z1).method_39415(color);
        // top
        vc.method_22918(m, x1, y2, z1).method_39415(color);
        vc.method_22918(m, x2, y2, z1).method_39415(color);
        vc.method_22918(m, x2, y2, z2).method_39415(color);
        vc.method_22918(m, x1, y2, z2).method_39415(color);
        // bottom
        vc.method_22918(m, x1, y1, z2).method_39415(color);
        vc.method_22918(m, x2, y1, z2).method_39415(color);
        vc.method_22918(m, x2, y1, z1).method_39415(color);
        vc.method_22918(m, x1, y1, z1).method_39415(color);
    }
}
