package net.krypton.utility.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Small 2D drawing toolkit on top of GuiGraphics: rounded rectangles, gradients, shadows, scaled text. */
public final class Draw {
    /** Global alpha multiplier used for fade-in animations. */
    public static float alphaMul = 1f;

    private Draw() {
    }

    public static int col(int argb) {
        if (alphaMul >= 0.999f) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alphaMul, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a = (int) Mth.lerp(t, (c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, c1 & 0xFF, c2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, col(color));
    }

    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        int c = col(color);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    /** 1px outline following the rounded shape. */
    public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        int c = col(color);
        g.fill(x + r, y, x + w - r, y + 1, c);
        g.fill(x + r, y + h - 1, x + w - r, y + h, c);
        g.fill(x, y + r, x + 1, y + h - r, c);
        g.fill(x + w - 1, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, c);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, c);
            g.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r, int spread) {
        for (int i = spread; i >= 1; i--) {
            float a = 0.05f * (spread - i + 1) / spread;
            round(g, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, withAlpha(0x000000, a));
        }
    }

    public static void gradientV(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, col(top), col(bottom));
    }

    /** Horizontal gradient drawn as thin strips. */
    public static void gradientH(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int step = Math.max(1, w / 48);
        for (int i = 0; i < w; i += step) {
            int sw = Math.min(step, w - i);
            int c = lerp(left, right, (i + sw / 2f) / w);
            g.fill(x + i, y, x + i + sw, y + h, col(c));
        }
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, s, x, y, col(color), false);
    }

    public static void textShadow(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, s, x, y, col(color), true);
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
        text(g, s, cx - width(s) / 2, y, color);
    }

    public static void textRight(GuiGraphics g, String s, int right, int y, int color) {
        text(g, s, right - width(s), y, color);
    }

    public static int width(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    public static int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    public static void textScaled(GuiGraphics g, String s, int x, int y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale);
        g.drawString(Minecraft.getInstance().font, s, 0, 0, col(color), false);
        g.pose().popMatrix();
    }

    /** Text with a left-to-right accent gradient, one glyph at a time. */
    public static void textGradient(GuiGraphics g, String s, int x, int y, float scale, int c1, int c2) {
        float cx = x;
        int n = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            textScaled(g, ch, Math.round(cx), y, scale, lerp(c1, c2, i / (float) n));
            cx += width(ch) * scale;
        }
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static float approach(float current, float target, float dt, float speed) {
        return current + (target - current) * (1f - (float) Math.exp(-dt * speed));
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
