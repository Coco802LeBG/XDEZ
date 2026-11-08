package net.krypton.utility.ui;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.ConfigManager;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.core.setting.BlockListSetting;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ColorSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.krypton.utility.core.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ClickGUI. Layout is recomputed every frame; every interactive element registers a Hit rectangle while it is
 * drawn, and input handlers resolve clicks against that list (topmost last).
 */
public final class ClickGuiScreen extends Screen {
    private enum HitType { TAB, TOGGLE, HEADER, BOOL, MODE, SLIDER, COLOR, BLOCKS, ACCENT, EDIT_HUD, RESET, ERASE }

    private record Hit(HitType type, int x, int y, int w, int h, Module module, Setting<?> setting, int index) {
    }

    private static final int SIDEBAR_W = 156;
    private static final int CARD_H = 38;
    private static final int TAB_H = 30;
    private static final int GROUP_H = 22;
    private static final int CONFIRM_WINDOW_MS = 4000;

    private static final int COLOR_LOCAL = 0xFF34D399;
    private static final int COLOR_WARN = 0xFFF59E0B;
    private static final int COLOR_DANGER = 0xFFF43F5E;

    private final List<Hit> hits = new ArrayList<>();
    private final Category[] categories = Category.values();
    private Hit dragging;
    private int selected;
    private float tabIndicator;
    private float open;
    private float scroll;
    private float targetScroll;
    private int contentHeight;
    private int viewHeight;
    private long last = System.nanoTime();
    private int hoverX;
    private int hoverY;
    private String pendingTooltip;

    // two-step confirmation of the destructive buttons
    private HitType pending;
    private long pendingUntil;
    private String status = "";
    private long statusUntil;

    // clip rectangle of the module list (set during render)
    private int clipX;
    private int clipY;
    private int clipW;
    private int clipH;

    public ClickGuiScreen() {
        super(Component.literal("Krypton Utility"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0E9f);
        last = now;
        hoverX = mouseX;
        hoverY = mouseY;
        open = Math.min(1f, open + dt * 6f);
        float e = Draw.easeOut(open);
        Draw.alphaMul = e;
        hits.clear();

        int ww = Math.min(680, width - 40);
        int wh = Math.min(440, height - 40);
        int wx = (width - ww) / 2;
        int wy = (height - wh) / 2 + Math.round((1f - e) * 12f);

        g.fillGradient(0, 0, width, height, Draw.col(0x70050510), Draw.col(0xB4080818));
        Draw.shadow(g, wx, wy, ww, wh, 12, 8);
        Draw.round(g, wx, wy, ww, wh, 12, Theme.WINDOW);
        Draw.roundOutline(g, wx, wy, ww, wh, 12, 0x20FFFFFF);

        renderSidebar(g, mouseX, mouseY, dt, wx + 8, wy + 8, SIDEBAR_W, wh - 16);
        int px = wx + 8 + SIDEBAR_W + 16;
        int pw = wx + ww - 16 - px;
        renderContent(g, mouseX, mouseY, dt, px, wy, pw, wh);
        Draw.alphaMul = 1f;
        if (pendingTooltip != null) {
            g.setTooltipForNextFrame(this.font, Component.literal(pendingTooltip), mouseX, mouseY);
            pendingTooltip = null;
        }
    }

    private void renderSidebar(GuiGraphics g, int mx, int my, float dt, int sx, int sy, int sw, int sh) {
        Draw.round(g, sx, sy, sw, sh, 9, Theme.SIDEBAR);
        Draw.textGradient(g, "KRYPTON", sx + 16, sy + 16, 1.6f, Theme.accent(), Theme.accent2());
        Draw.text(g, "U T I L I T Y", sx + 17, sy + 33, Theme.MUTED);
        Draw.rect(g, sx + 16, sy + 49, sw - 32, 1, 0x18FFFFFF);

        tabIndicator = Draw.approach(tabIndicator, selected, dt, 16f);
        int ty0 = sy + 60;
        int indY = ty0 + Math.round(tabIndicator * TAB_H);
        Draw.round(g, sx + 8, indY, sw - 16, 26, 7, Draw.withAlpha(Theme.accent(), 0.16f));
        Draw.gradientV(g, sx + 8, indY + 6, 3, 14, Theme.accent(), Theme.accent2());

        for (int i = 0; i < categories.length; i++) {
            Category c = categories[i];
            int ry = ty0 + i * TAB_H;
            boolean hover = Draw.inside(mx, my, sx + 8, ry, sw - 16, 26);
            if (hover && i != selected) {
                Draw.round(g, sx + 8, ry, sw - 16, 26, 7, 0x10FFFFFF);
            }
            Draw.round(g, sx + 22, ry + 9, 8, 8, 4, c.color);
            Draw.text(g, c.label, sx + 38, ry + 9, i == selected ? Theme.TEXT : Theme.MUTED);
            List<Module> mods = ModuleManager.in(c);
            int on = 0;
            for (Module m : mods) {
                if (m.isEnabled()) {
                    on++;
                }
            }
            Draw.textRight(g, on + "/" + mods.size(), sx + sw - 18, ry + 9, Theme.MUTED);
            hits.add(new Hit(HitType.TAB, sx + 8, ry, sw - 16, 26, null, null, i));
        }

        // data buttons (local config only)
        int by = sy + sh - 118;
        Draw.text(g, "LOCAL DATA", sx + 16, by - 12, Theme.MUTED);
        dataButton(g, mx, my, sx + 12, by, sw - 24, "Reset settings", "Click again to reset", HitType.RESET, COLOR_WARN);
        dataButton(g, mx, my, sx + 12, by + 24, sw - 24, "Erase all data", "Click again to erase", HitType.ERASE, COLOR_DANGER);

        int ay = sy + sh - 40;
        Draw.text(g, "ACCENT", sx + 16, ay - 12, Theme.MUTED);
        for (int i = 0; i < Theme.accentCount(); i++) {
            int cx = sx + 16 + i * 20;
            int mid = Draw.lerp(Theme.accentOf(i, false), Theme.accentOf(i, true), 0.5f);
            Draw.round(g, cx, ay, 14, 14, 7, mid);
            if (i == Theme.accentIndex()) {
                Draw.roundOutline(g, cx - 2, ay - 2, 18, 18, 9, Theme.TEXT);
            }
            hits.add(new Hit(HitType.ACCENT, cx - 2, ay - 2, 18, 18, null, null, i));
        }
    }

    private void dataButton(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String confirmLabel, HitType type, int color) {
        boolean confirming = pending == type && System.currentTimeMillis() < pendingUntil;
        boolean hover = Draw.inside(mx, my, x, y, w, 20);
        int bg = confirming ? Draw.withAlpha(color, 0.30f) : hover ? Draw.lerp(Theme.CARD_HOVER, color, 0.2f) : Theme.CARD;
        Draw.round(g, x, y, w, 20, 6, bg);
        if (confirming) {
            Draw.roundOutline(g, x, y, w, 20, 6, color);
        }
        Draw.textCentered(g, confirming ? confirmLabel : label, x + w / 2, y + 6, confirming ? Theme.TEXT : 0xFFC4C4D6);
        hits.add(new Hit(type, x, y, w, 20, null, null, 0));
    }

    private void renderContent(GuiGraphics g, int mx, int my, float dt, int px, int wy, int pw, int wh) {
        Category cat = categories[selected];
        Draw.textScaled(g, cat.label, px, wy + 20, 1.4f, Theme.TEXT);
        Draw.text(g, cat.subtitle, px + 1, wy + 36, Theme.MUTED);

        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int w = Draw.width(status) + 20;
            Draw.round(g, px + pw - w, wy + 19, w, 20, 8, Draw.withAlpha(Theme.accent(), 0.85f));
            Draw.text(g, status, px + pw - w + 10, wy + 25, 0xFFFFFFFF);
        } else if (cat == Category.INTERFACE) {
            int bw = 92;
            int bx = px + pw - bw;
            boolean hover = Draw.inside(mx, my, bx, wy + 18, bw, 22);
            Draw.round(g, bx, wy + 18, bw, 22, 7, hover ? Draw.lerp(Theme.CARD_HOVER, Theme.accent(), 0.35f) : Theme.CARD);
            Draw.textCentered(g, "Edit HUD", bx + bw / 2, wy + 25, Theme.TEXT);
            hits.add(new Hit(HitType.EDIT_HUD, bx, wy + 18, bw, 22, null, null, 0));
        }

        clipX = px;
        clipY = wy + 56;
        clipW = pw;
        clipH = wh - 56 - 14;
        viewHeight = clipH;
        scroll = Draw.approach(scroll, targetScroll, dt, 16f);

        g.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
        int y = clipY - Math.round(scroll);
        int startY = y;
        for (Module m : ModuleManager.in(cat)) {
            y += renderModule(g, m, mx, my, dt, px, y, pw) + 8;
        }
        g.disableScissor();
        contentHeight = y - startY;
        float max = Math.max(0, contentHeight - viewHeight);
        targetScroll = Mth.clamp(targetScroll, 0f, max);

        if (max > 0) {
            int trackH = clipH;
            int thumbH = Math.max(24, Math.round(trackH * (viewHeight / (float) contentHeight)));
            int thumbY = clipY + Math.round((trackH - thumbH) * (scroll / max));
            Draw.round(g, px + pw + 6, thumbY, 3, thumbH, 1, Draw.withAlpha(Theme.accent(), 0.7f));
        }
    }

    // ------------------------------------------------------------------ settings layout

    private static int rowHeight(Setting<?> s) {
        if (s instanceof NumberSetting) {
            return 32;
        }
        if (s instanceof ColorSetting) {
            return 44;
        }
        if (s instanceof BlockListSetting) {
            return 30;
        }
        return 24;
    }

    /** Word-wraps a text to the given pixel width. */
    private static List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Draw.width(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static int noticeHeight(Module m, int innerWidth) {
        if (m.serverNotice() == null) {
            return 22;
        }
        return 14 + wrap(m.serverNotice(), innerWidth - 20).size() * 11 + 4;
    }

    private int settingsHeight(Module m, List<Setting<?>> settings, int innerWidth) {
        int h = noticeHeight(m, innerWidth) + 6;
        String lastGroup = null;
        for (Setting<?> s : settings) {
            if (!s.group().isEmpty() && !s.group().equals(lastGroup)) {
                h += GROUP_H;
                lastGroup = s.group();
            }
            h += rowHeight(s);
        }
        return h;
    }

    private String fit(String s, int maxWidth) {
        if (Draw.width(s) <= maxWidth) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && Draw.width(t + "...") > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    private void addHit(Hit h) {
        // keep only the part of the rectangle that is inside the visible list area
        int y1 = Math.max(h.y(), clipY);
        int y2 = Math.min(h.y() + h.h(), clipY + clipH);
        if (y2 > y1) {
            hits.add(new Hit(h.type(), h.x(), y1, h.w(), y2 - y1, h.module(), h.setting(), h.index()));
        }
    }

    private void badge(GuiGraphics g, int x, int y, String label, int color) {
        int w = Math.round(Draw.width(label) * 0.75f) + 12;
        Draw.round(g, x, y, w, 11, 5, Draw.withAlpha(color, 0.18f));
        Draw.round(g, x + 4, y + 4, 3, 3, 1, color);
        Draw.textScaled(g, label, x + 9, y + 3, 0.75f, color);
    }

    private int badgeWidth(String label) {
        return Math.round(Draw.width(label) * 0.75f) + 12;
    }

    private int renderModule(GuiGraphics g, Module m, int mx, int my, float dt, int x, int y, int w) {
        m.toggleAnim = Draw.approach(m.toggleAnim, m.isEnabled() ? 1f : 0f, dt, 14f);
        m.expandAnim = Draw.approach(m.expandAnim, m.expanded ? 1f : 0f, dt, 14f);
        List<Setting<?>> settings = m.visibleSettings();
        int extra = settingsHeight(m, settings, w - 32) + 10;
        int h = CARD_H + Math.round(extra * m.expandAnim);
        if (y + h < clipY || y > clipY + clipH) {
            return h;
        }

        boolean hover = Draw.inside(mx, my, x, Math.max(y, clipY), w, Math.min(CARD_H, clipY + clipH - y)) && my >= clipY && my < clipY + clipH;
        Draw.round(g, x, y, w, h, 8, hover ? Theme.CARD_HOVER : Theme.CARD);
        if (m.toggleAnim > 0.02f) {
            Draw.gradientV(g, x + 1, y + 9, 3, 20, Draw.withAlpha(Theme.accent(), m.toggleAnim), Draw.withAlpha(Theme.accent2(), m.toggleAnim));
        }

        int tw = 32;
        int th = 16;
        int tx = x + w - tw - 14;
        int ty = y + (CARD_H - th) / 2;
        boolean serverVisible = m.serverNotice() != null;
        String badgeLabel = serverVisible ? "SERVER-VISIBLE" : "CLIENT-ONLY";
        int badgeX = x + 14 + Draw.width(m.name()) + 8;
        int textMax = w - 14 - (tw + 28) - 66;
        Draw.text(g, m.name(), x + 14, y + 9, Theme.TEXT);
        badge(g, badgeX, y + 8, badgeLabel, serverVisible ? COLOR_WARN : COLOR_LOCAL);
        Draw.text(g, fit(m.description(), textMax), x + 14, y + 21, Theme.MUTED);
        Draw.textRight(g, settings.size() + " options", tx - 12, y + 15, m.expanded ? Theme.accent() : Theme.MUTED);

        Draw.round(g, tx, ty, tw, th, 8, Draw.lerp(Theme.TRACK, Theme.accent(), m.toggleAnim));
        int kx = tx + 2 + Math.round((tw - th) * m.toggleAnim);
        Draw.round(g, kx, ty + 2, th - 4, th - 4, 6, 0xFFFFFFFF);

        addHit(new Hit(HitType.HEADER, x, y, w, CARD_H, m, null, 0));
        addHit(new Hit(HitType.TOGGLE, tx - 4, ty - 4, tw + 8, th + 8, m, null, 0));

        if (m.expandAnim > 0.01f) {
            g.enableScissor(x, Math.max(y + CARD_H, clipY), x + w, Math.min(y + h, clipY + clipH));
            Draw.rect(g, x + 14, y + CARD_H - 2, w - 28, 1, 0x14FFFFFF);
            int sy = y + CARD_H + 6;
            sy += renderNotice(g, m, x + 16, sy, w - 32);
            sy += 6;
            boolean interactive = m.expandAnim > 0.9f;
            String lastGroup = null;
            for (Setting<?> s : settings) {
                if (!s.group().isEmpty() && !s.group().equals(lastGroup)) {
                    lastGroup = s.group();
                    Draw.text(g, s.group().toUpperCase(Locale.ROOT), x + 16, sy + 8, Draw.withAlpha(Theme.accent(), 0.9f));
                    Draw.rect(g, x + 22 + Draw.width(s.group()), sy + 12, w - 40 - Draw.width(s.group()), 1, 0x14FFFFFF);
                    sy += GROUP_H;
                }
                sy += renderSetting(g, s, m, dt, x + 16, sy, w - 32, interactive);
            }
            g.disableScissor();
        }
        return h;
    }

    /** Green "client-only" line, or an amber banner explaining exactly what the server can see. */
    private int renderNotice(GuiGraphics g, Module m, int x, int y, int w) {
        int h = noticeHeight(m, w);
        if (m.serverNotice() == null) {
            Draw.round(g, x, y, w, h - 4, 6, Draw.withAlpha(COLOR_LOCAL, 0.10f));
            Draw.round(g, x + 8, y + 7, 4, 4, 2, COLOR_LOCAL);
            Draw.text(g, "Client-side only: nothing is sent to the server.", x + 18, y + 5, COLOR_LOCAL);
            return h;
        }
        Draw.round(g, x, y, w, h - 4, 6, Draw.withAlpha(COLOR_WARN, 0.12f));
        Draw.roundOutline(g, x, y, w, h - 4, 6, Draw.withAlpha(COLOR_WARN, 0.6f));
        Draw.text(g, "! SERVER-VISIBLE", x + 10, y + 5, COLOR_WARN);
        int ly = y + 17;
        for (String line : wrap(m.serverNotice(), w - 20)) {
            Draw.text(g, line, x + 10, ly, 0xFFE5C07B);
            ly += 11;
        }
        return h;
    }

    private void riskMarker(GuiGraphics g, Setting<?> s, int x, int y, int w, int h) {
        Draw.round(g, x, y + 5, 11, 12, 4, Draw.withAlpha(COLOR_WARN, 0.25f));
        Draw.textCentered(g, "!", x + 5, y + 7, COLOR_WARN);
        if (Draw.inside(hoverX, hoverY, x, y, w, h) && hoverY >= clipY && hoverY < clipY + clipH) {
            tooltip("Server-visible: " + s.riskNote());
        }
    }

    private void tooltip(String text) {
        pendingTooltip = text;
    }

    private int labelX(Setting<?> s, int x) {
        return s.riskNote() == null ? x : x + 15;
    }

    private int renderSetting(GuiGraphics g, Setting<?> s, Module m, float dt, int x, int y, int w, boolean interactive) {
        int lx = labelX(s, x);
        int rowH = rowHeight(s);
        if (s.riskNote() != null) {
            riskMarker(g, s, x, y, w, rowH);
        }
        if (s instanceof BoolSetting b) {
            b.anim = Draw.approach(b.anim, b.get() ? 1f : 0f, dt, 16f);
            Draw.text(g, b.name, lx, y + 8, b.get() ? Theme.TEXT : Theme.MUTED);
            int tw = 26;
            int th = 14;
            int tx = x + w - tw;
            Draw.round(g, tx, y + 5, tw, th, 7, Draw.lerp(Theme.TRACK, Theme.accent(), b.anim));
            Draw.round(g, tx + 2 + Math.round((tw - th) * b.anim), y + 7, th - 4, th - 4, 5, 0xFFFFFFFF);
            if (interactive) {
                addHit(new Hit(HitType.BOOL, x, y, w, rowH, m, b, 0));
            }
            return rowH;
        }
        if (s instanceof ModeSetting ms) {
            Draw.text(g, ms.name, lx, y + 8, Theme.MUTED);
            String label = "<  " + ms.get() + "  >";
            int pw = Draw.width(label) + 14;
            int px = x + w - pw;
            Draw.round(g, px, y + 3, pw, 18, 6, Theme.TRACK);
            Draw.textCentered(g, label, px + pw / 2, y + 8, Theme.accent());
            if (interactive) {
                addHit(new Hit(HitType.MODE, x, y, w, rowH, m, ms, 0));
            }
            return rowH;
        }
        if (s instanceof NumberSetting ns) {
            Draw.text(g, ns.name, lx, y + 5, Theme.MUTED);
            Draw.textRight(g, ns.display(), x + w, y + 5, Theme.TEXT);
            int ty = y + 21;
            int fill = Math.round(ns.fraction() * w);
            Draw.round(g, x, ty, w, 4, 2, Theme.TRACK);
            if (fill > 0) {
                Draw.gradientH(g, x, ty, fill, 4, Theme.accent(), Theme.accent2());
            }
            Draw.round(g, x + fill - 5, ty - 3, 10, 10, 5, 0xFFFFFFFF);
            if (interactive) {
                addHit(new Hit(HitType.SLIDER, x, y + 14, w, 18, m, ns, 0));
            }
            return rowH;
        }
        if (s instanceof ColorSetting cs) {
            Draw.text(g, cs.name, lx, y + 4, Theme.MUTED);
            Draw.round(g, x + w - 18, y + 3, 18, 10, 4, cs.argb());
            int size = Math.min(18, (w - 15 * 3) / 16);
            int idx = cs.paletteIndex();
            for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
                int sx = x + i * (size + 3);
                Draw.round(g, sx, y + 20, size, 18, 5, ColorSetting.PALETTE[i]);
                if (i == idx) {
                    Draw.roundOutline(g, sx - 2, y + 18, size + 4, 22, 7, Theme.TEXT);
                }
                if (interactive) {
                    addHit(new Hit(HitType.COLOR, sx, y + 20, size, 18, m, cs, i));
                }
            }
            return rowH;
        }
        if (s instanceof BlockListSetting bl) {
            Draw.text(g, bl.name, lx, y + 9, Theme.MUTED);
            String label = "Edit  (" + bl.size() + ")";
            int bw = Draw.width(label) + 20;
            int bx = x + w - bw;
            Draw.round(g, bx, y + 3, bw, 22, 7, Draw.withAlpha(Theme.accent(), 0.28f));
            Draw.roundOutline(g, bx, y + 3, bw, 22, 7, Draw.withAlpha(Theme.accent(), 0.8f));
            Draw.textCentered(g, label, bx + bw / 2, y + 10, Theme.TEXT);
            if (interactive) {
                addHit(new Hit(HitType.BLOCKS, bx, y + 3, bw, 22, m, bl, 0));
            }
            return rowH;
        }
        return 0;
    }

    // ------------------------------------------------------------------ input

    private void updateSlider(Hit h, double mouseX) {
        if (h.setting() instanceof NumberSetting ns) {
            double t = Mth.clamp((mouseX - h.x()) / h.w(), 0.0, 1.0);
            ns.set(ns.min + t * (ns.max - ns.min));
        }
    }

    private void announce(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    private void confirmed(HitType type) {
        long now = System.currentTimeMillis();
        if (pending == type && now < pendingUntil) {
            pending = null;
            if (type == HitType.RESET) {
                ConfigManager.resetSettings();
                announce("Settings reset");
            } else {
                ConfigManager.eraseAll();
                announce("All local data erased");
            }
        } else {
            pending = type;
            pendingUntil = now + CONFIRM_WINDOW_MS;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!Draw.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                continue;
            }
            switch (h.type()) {
                case TAB -> {
                    selected = h.index();
                    targetScroll = 0;
                    scroll = 0;
                }
                case TOGGLE -> h.module().toggle();
                case HEADER -> {
                    if (button == 1) {
                        h.module().toggle();
                    } else {
                        h.module().expanded = !h.module().expanded;
                    }
                }
                case BOOL -> ((BoolSetting) h.setting()).toggle();
                case MODE -> ((ModeSetting) h.setting()).cycle(button == 1 ? -1 : 1);
                case SLIDER -> {
                    dragging = h;
                    updateSlider(h, mx);
                }
                case COLOR -> ((ColorSetting) h.setting()).set(ColorSetting.PALETTE[h.index()]);
                case BLOCKS -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(new BlockPickerScreen(this, (BlockListSetting) h.setting(), h.module().name()));
                }
                case ACCENT -> Theme.setAccent(h.index());
                case EDIT_HUD -> {
                    ConfigManager.save();
                    Minecraft.getInstance().setScreen(new HudEditorScreen(this));
                }
                case RESET, ERASE -> confirmed(h.type());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            updateSlider(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float max = Math.max(0, contentHeight - viewHeight);
        targetScroll = Mth.clamp(targetScroll - (float) scrollY * 32f, 0f, max);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        super.onClose();
    }
}
