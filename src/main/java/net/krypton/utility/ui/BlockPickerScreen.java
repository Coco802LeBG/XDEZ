package net.krypton.utility.ui;

import net.krypton.utility.block.BlockCatalog;
import net.krypton.utility.block.PresetManager;
import net.krypton.utility.core.ConfigManager;
import net.krypton.utility.core.setting.BlockListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Block selection screen for a {@link BlockListSetting}: text search, icon grid of every block, bulk actions,
 * built-in and user presets, and a per-block colour editor.
 */
public final class BlockPickerScreen extends Screen {
    private static final int CELL = 30;
    private static final int ROW_H = 22;
    private static final int HEADER_H = 18;

    private enum Prompt { NONE, CREATE, RENAME }

    private record Btn(int x, int y, int w, int h, Runnable action) {
    }

    private record Row(String label, boolean header, boolean builtin, String key, int count) {
    }

    private final Screen parent;
    private final BlockListSetting setting;
    private final String owner;

    private EditBox search;
    private EditBox nameBox;
    private String searchText = "";
    private List<BlockCatalog.Entry> filtered = List.of();
    private boolean selectedOnly;

    private float gridScroll;
    private float gridTarget;
    private float listScroll;
    private float listTarget;
    private int gridContentH;
    private int listContentH;

    private Prompt prompt = Prompt.NONE;
    private String selectedPreset;
    private boolean selectedBuiltin;
    private String colorTarget;
    private boolean draggingHue;

    private String message = "";
    private long messageUntil;
    private long last = System.nanoTime();

    private final List<Btn> buttons = new ArrayList<>();
    private final Map<String, List<String>> builtinIds = new HashMap<>();

    // geometry (computed in layout())
    private int wx;
    private int wy;
    private int ww;
    private int wh;
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int searchY;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int listY;
    private int listH;
    private int hueX;
    private int hueY;
    private int hueW;

    public BlockPickerScreen(Screen parent, BlockListSetting setting, String owner) {
        super(Component.literal("Block selection"));
        this.parent = parent;
        this.setting = setting;
        this.owner = owner;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout & widgets

    private void layout() {
        ww = Math.min(860, width - 30);
        wh = Math.min(500, height - 30);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        rightW = 220;
        leftX = wx + 16;
        leftW = ww - 32 - rightW - 16;
        rightX = leftX + leftW + 16;
        searchY = wy + 42;
        gridX = leftX;
        gridY = wy + 108;
        gridW = leftW;
        gridH = wy + wh - 16 - gridY;
        listY = wy + 40;
        listH = Math.max(90, (wh - 40) / 3 + 10);
        hueX = rightX;
        hueW = rightW;
    }

    @Override
    protected void init() {
        layout();
        BlockCatalog.ensureLoaded();
        search = new EditBox(this.font, leftX + 30, searchY + 8, leftW - 44, 12, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setValue(searchText);
        search.setResponder(s -> {
            searchText = s;
            refilter();
        });
        addWidget(search);

        nameBox = new EditBox(this.font, rightX + 10, 0, rightW - 20, 12, Component.literal("Preset name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(32);
        addWidget(nameBox);

        setFocused(search);
        refilter();
    }

    private void refilter() {
        List<BlockCatalog.Entry> base = BlockCatalog.search(searchText);
        if (selectedOnly) {
            List<BlockCatalog.Entry> out = new ArrayList<>();
            for (BlockCatalog.Entry e : base) {
                if (setting.contains(e.id())) {
                    out.add(e);
                }
            }
            base = out;
        }
        filtered = base;
        gridTarget = 0;
        gridScroll = 0;
    }

    private List<String> filteredIds() {
        List<String> ids = new ArrayList<>(filtered.size());
        for (BlockCatalog.Entry e : filtered) {
            ids.add(e.id());
        }
        return ids;
    }

    private void flash(String text) {
        message = text;
        messageUntil = System.currentTimeMillis() + 3000;
    }

    private List<String> builtinIds(String name) {
        return builtinIds.computeIfAbsent(name, n -> {
            for (BlockCatalog.BuiltinPreset p : BlockCatalog.builtinPresets()) {
                if (p.name().equals(n)) {
                    return BlockCatalog.idsOf(p);
                }
            }
            return List.of();
        });
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0E9f);
        last = now;
        Draw.alphaMul = 1f;
        buttons.clear();

        g.fillGradient(0, 0, width, height, 0x80050510, 0xC0080818);
        Draw.shadow(g, wx, wy, ww, wh, 12, 8);
        Draw.round(g, wx, wy, ww, wh, 12, Theme.WINDOW);
        Draw.roundOutline(g, wx, wy, ww, wh, 12, 0x20FFFFFF);

        renderLeft(g, mouseX, mouseY, dt);
        renderRight(g, mouseX, mouseY, dt);
        if (!message.isEmpty() && System.currentTimeMillis() < messageUntil) {
            int w = Draw.width(message) + 20;
            int x = wx + (ww - w) / 2;
            Draw.round(g, x, wy + wh + 6, w, 18, 8, Draw.withAlpha(Theme.accent(), 0.9f));
            Draw.text(g, message, x + 10, wy + wh + 11, 0xFFFFFFFF);
        }
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, boolean enabled, boolean active, Runnable action) {
        boolean hover = enabled && Draw.inside(mx, my, x, y, w, h);
        int bg = active ? Draw.withAlpha(Theme.accent(), 0.35f) : hover ? Draw.lerp(Theme.CARD_HOVER, Theme.accent(), 0.3f) : Theme.CARD;
        Draw.round(g, x, y, w, h, 6, bg);
        if (active) {
            Draw.roundOutline(g, x, y, w, h, 6, Theme.accent());
        }
        Draw.textCentered(g, label, x + w / 2, y + (h - Draw.lineHeight()) / 2 + 1, enabled ? Theme.TEXT : 0xFF55556A);
        if (enabled) {
            buttons.add(new Btn(x, y, w, h, action));
        }
    }

    private void renderLeft(GuiGraphics g, int mx, int my, float dt) {
        Draw.textScaled(g, "Blocks", leftX, wy + 16, 1.4f, Theme.TEXT);
        Draw.text(g, owner, leftX + 70, wy + 20, Theme.MUTED);
        String counts = setting.size() + " selected   |   " + filtered.size() + " shown";
        Draw.textRight(g, counts, leftX + leftW, wy + 20, Theme.MUTED);

        // search field
        boolean focused = getFocused() == search;
        Draw.round(g, leftX, searchY, leftW, 28, 7, Theme.TRACK);
        if (focused) {
            Draw.roundOutline(g, leftX, searchY, leftW, 28, 7, Theme.accent());
        }
        if (searchText.isEmpty() && !focused) {
            Draw.text(g, "Search a block by name or id...", leftX + 30, searchY + 10, 0xFF5E5E78);
        }
        search.render(g, mx, my, 0f);

        // bulk actions
        int by = wy + 78;
        int x = leftX;
        button(g, mx, my, x, by, 92, 22, "Select shown", !filtered.isEmpty(), false, () -> {
            setting.addAll(filteredIds());
            refilterKeepScroll();
        });
        x += 98;
        button(g, mx, my, x, by, 104, 22, "Deselect shown", !filtered.isEmpty(), false, () -> {
            setting.removeAll(filteredIds());
            refilterKeepScroll();
        });
        x += 110;
        button(g, mx, my, x, by, 66, 22, "Invert", !filtered.isEmpty(), false, () -> {
            setting.invert(filteredIds());
            refilterKeepScroll();
        });
        x += 72;
        button(g, mx, my, x, by, 108, 22, "Selected only", true, selectedOnly, () -> {
            selectedOnly = !selectedOnly;
            refilter();
        });

        renderGrid(g, mx, my, dt);
    }

    private void refilterKeepScroll() {
        float s = gridTarget;
        refilter();
        gridTarget = s;
        gridScroll = s;
    }

    private void renderGrid(GuiGraphics g, int mx, int my, float dt) {
        int cols = Math.max(1, (gridW - 10) / CELL);
        int rows = (filtered.size() + cols - 1) / cols;
        gridContentH = rows * CELL;
        float max = Math.max(0, gridContentH - gridH);
        gridTarget = Mth.clamp(gridTarget, 0f, max);
        gridScroll = Draw.approach(gridScroll, gridTarget, dt, 18f);

        Draw.round(g, gridX - 4, gridY - 4, gridW + 8, gridH + 8, 8, 0xFF0E0E16);
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        BlockCatalog.Entry hovered = null;
        int firstRow = Math.max(0, (int) (gridScroll / CELL));
        int lastRow = Math.min(rows - 1, (int) ((gridScroll + gridH) / CELL));
        int gridPad = (gridW - 10 - cols * CELL) / 2;
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= filtered.size()) {
                    break;
                }
                BlockCatalog.Entry e = filtered.get(index);
                int cx = gridX + gridPad + col * CELL;
                int cy = gridY + row * CELL - Math.round(gridScroll);
                boolean selected = setting.contains(e.id());
                boolean hover = Draw.inside(mx, my, cx, cy, CELL, CELL) && Draw.inside(mx, my, gridX, gridY, gridW, gridH);
                int color = setting.color(e.id());
                if (selected) {
                    Draw.round(g, cx + 1, cy + 1, CELL - 2, CELL - 2, 5, Draw.withAlpha(color, 0.22f));
                    Draw.roundOutline(g, cx + 1, cy + 1, CELL - 2, CELL - 2, 5, color);
                } else if (hover) {
                    Draw.round(g, cx + 1, cy + 1, CELL - 2, CELL - 2, 5, 0x22FFFFFF);
                }
                ItemStack icon = e.icon();
                if (!icon.isEmpty()) {
                    g.renderItem(icon, cx + (CELL - 16) / 2, cy + (CELL - 16) / 2);
                } else {
                    Draw.round(g, cx + 8, cy + 8, 14, 14, 4, Draw.withAlpha(color, 0.7f));
                    Draw.textCentered(g, e.name().substring(0, 1).toUpperCase(Locale.ROOT), cx + 15, cy + 11, 0xFFFFFFFF);
                }
                if (selected && setting.hasCustomColor(e.id())) {
                    Draw.round(g, cx + CELL - 9, cy + CELL - 9, 6, 6, 3, color);
                }
                if (hover) {
                    hovered = e;
                }
            }
        }
        if (filtered.isEmpty()) {
            Draw.textCentered(g, "No block matches your search", gridX + gridW / 2, gridY + gridH / 2 - 4, Theme.MUTED);
        }
        g.disableScissor();

        if (max > 0) {
            int thumbH = Math.max(24, Math.round(gridH * (gridH / (float) gridContentH)));
            int thumbY = gridY + Math.round((gridH - thumbH) * (gridScroll / max));
            Draw.round(g, gridX + gridW - 5, thumbY, 3, thumbH, 1, Draw.withAlpha(Theme.accent(), 0.7f));
        }
        if (hovered != null) {
            g.setTooltipForNextFrame(this.font, Component.literal(hovered.name() + "  (" + hovered.id() + ")"), mx, my);
        }
    }

    // ------------------------------------------------------------------ right panel

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("BUILT-IN", true, true, "", 0));
        for (BlockCatalog.BuiltinPreset p : BlockCatalog.builtinPresets()) {
            rows.add(new Row(p.name(), false, true, p.name(), builtinIds(p.name()).size()));
        }
        rows.add(new Row("MY PRESETS", true, false, "", 0));
        if (PresetManager.list().isEmpty()) {
            rows.add(new Row("No preset yet", true, false, "", 0));
        }
        for (PresetManager.Preset p : PresetManager.list()) {
            rows.add(new Row(p.name(), false, false, p.name(), p.ids().size()));
        }
        return rows;
    }

    private void renderRight(GuiGraphics g, int mx, int my, float dt) {
        Draw.round(g, rightX - 8, wy + 10, rightW + 16, wh - 20, 9, Theme.SIDEBAR);
        Draw.text(g, "PRESETS", rightX, wy + 20, Theme.MUTED);

        List<Row> rows = buildRows();
        int contentH = 0;
        for (Row r : rows) {
            contentH += r.header() ? HEADER_H : ROW_H;
        }
        listContentH = contentH;
        float max = Math.max(0, contentH - listH);
        listTarget = Mth.clamp(listTarget, 0f, max);
        listScroll = Draw.approach(listScroll, listTarget, dt, 18f);

        Draw.round(g, rightX - 2, listY - 2, rightW + 4, listH + 4, 7, 0xFF0E0E16);
        g.enableScissor(rightX, listY, rightX + rightW, listY + listH);
        int y = listY - Math.round(listScroll);
        for (Row r : rows) {
            int h = r.header() ? HEADER_H : ROW_H;
            if (y + h >= listY && y <= listY + listH) {
                if (r.header()) {
                    Draw.text(g, r.label(), rightX + 8, y + 5, r.label().startsWith("No ") ? 0xFF5E5E78 : Theme.MUTED);
                } else {
                    boolean sel = r.key().equals(selectedPreset) && r.builtin() == selectedBuiltin;
                    boolean hover = Draw.inside(mx, my, rightX, y, rightW, ROW_H) && Draw.inside(mx, my, rightX, listY, rightW, listH);
                    if (sel) {
                        Draw.round(g, rightX + 2, y + 1, rightW - 4, ROW_H - 2, 6, Draw.withAlpha(Theme.accent(), 0.25f));
                    } else if (hover) {
                        Draw.round(g, rightX + 2, y + 1, rightW - 4, ROW_H - 2, 6, 0x14FFFFFF);
                    }
                    Draw.text(g, r.label(), rightX + 10, y + 7, sel ? Theme.TEXT : 0xFFC4C4D6);
                    Draw.textRight(g, Integer.toString(r.count()), rightX + rightW - 10, y + 7, Theme.MUTED);
                    int top = Math.max(y, listY);
                    int bottom = Math.min(y + ROW_H, listY + listH);
                    if (bottom > top) {
                        String key = r.key();
                        boolean builtin = r.builtin();
                        buttons.add(new Btn(rightX, top, rightW, bottom - top, () -> {
                            selectedPreset = key;
                            selectedBuiltin = builtin;
                        }));
                    }
                }
            }
            y += h;
        }
        g.disableScissor();
        if (max > 0) {
            int thumbH = Math.max(18, Math.round(listH * (listH / (float) listContentH)));
            int thumbY = listY + Math.round((listH - thumbH) * (listScroll / max));
            Draw.round(g, rightX + rightW - 4, thumbY, 3, thumbH, 1, Draw.withAlpha(Theme.accent(), 0.7f));
        }

        int by = listY + listH + 12;
        int third = (rightW - 12) / 3;
        boolean hasPreset = selectedPreset != null;
        button(g, mx, my, rightX, by, third, 22, "Replace", hasPreset, false, () -> applyPreset(0));
        button(g, mx, my, rightX + third + 6, by, third, 22, "Add", hasPreset, false, () -> applyPreset(1));
        button(g, mx, my, rightX + (third + 6) * 2, by, third, 22, "Remove", hasPreset, false, () -> applyPreset(2));
        by += 28;

        if (prompt == Prompt.NONE) {
            int half = (rightW - 6) / 2;
            if (selectedBuiltin || selectedPreset == null) {
                button(g, mx, my, rightX, by, rightW, 22, "Copy to my presets", hasPreset && selectedBuiltin, false, this::copyBuiltin);
            } else {
                button(g, mx, my, rightX, by, half, 22, "Rename", true, false, () -> startPrompt(Prompt.RENAME, selectedPreset));
                button(g, mx, my, rightX + half + 6, by, half, 22, "Delete", true, false, this::deleteSelected);
            }
            by += 28;
            button(g, mx, my, rightX, by, rightW, 22, "Save selection as preset", setting.size() > 0, false,
                    () -> startPrompt(Prompt.CREATE, PresetManager.suggestName()));
            by += 28;
        } else {
            Draw.text(g, prompt == Prompt.CREATE ? "New preset name" : "Rename preset", rightX, by + 2, Theme.MUTED);
            by += 14;
            Draw.round(g, rightX, by, rightW, 26, 7, Theme.TRACK);
            Draw.roundOutline(g, rightX, by, rightW, 26, 7, Theme.accent());
            nameBox.setY(by + 7);
            nameBox.render(g, mx, my, 0f);
            by += 32;
            int half = (rightW - 6) / 2;
            button(g, mx, my, rightX, by, half, 22, "OK", true, false, this::confirmPrompt);
            button(g, mx, my, rightX + half + 6, by, half, 22, "Cancel", true, false, this::cancelPrompt);
            by += 28;
        }

        renderColorEditor(g, mx, my, by);
    }

    private void renderColorEditor(GuiGraphics g, int mx, int my, int top) {
        Draw.rect(g, rightX, top, rightW, 1, 0x18FFFFFF);
        top += 8;
        if (colorTarget == null || !setting.contains(colorTarget)) {
            Draw.text(g, "Right-click a block to", rightX, top + 4, Theme.MUTED);
            Draw.text(g, "choose its colour.", rightX, top + 16, Theme.MUTED);
            hueY = -1000;
            return;
        }
        BlockCatalog.Entry e = BlockCatalog.entry(colorTarget);
        if (e != null && !e.icon().isEmpty()) {
            g.renderItem(e.icon(), rightX, top);
        }
        String name = e == null ? colorTarget : e.name();
        Draw.text(g, name, rightX + 22, top + 4, Theme.TEXT);
        top += 24;
        int current = setting.color(colorTarget);
        for (int i = 0; i < 16; i++) {
            int col = i % 8;
            int row = i / 8;
            int sx = rightX + col * 27;
            int sy = top + row * 24;
            int color = net.krypton.utility.core.setting.ColorSetting.PALETTE[i];
            Draw.round(g, sx, sy, 20, 18, 5, color);
            if (color == current) {
                Draw.roundOutline(g, sx - 2, sy - 2, 24, 22, 7, Theme.TEXT);
            }
            final int picked = color;
            final String target = colorTarget;
            buttons.add(new Btn(sx, sy, 20, 18, () -> setting.setColor(target, picked)));
        }
        top += 52;
        hueY = top;
        for (int i = 0; i < hueW; i += 4) {
            Draw.rect(g, hueX + i, hueY, Math.min(4, hueW - i), 10, 0xFF000000 | BlockCatalog.hsvToRgb(i / (float) hueW, 0.65f, 1.0f));
        }
        Draw.roundOutline(g, hueX - 1, hueY - 1, hueW + 2, 12, 3, 0x40FFFFFF);
        top += 18;
        boolean custom = setting.hasCustomColor(colorTarget);
        final String target = colorTarget;
        button(g, mx, my, rightX, top, rightW, 20, "Reset to default colour", custom, false, () -> setting.resetColor(target));
    }

    // ------------------------------------------------------------------ preset actions

    private void applyPreset(int mode) {
        if (selectedPreset == null) {
            return;
        }
        List<String> ids;
        Map<String, Integer> colors = Map.of();
        if (selectedBuiltin) {
            ids = builtinIds(selectedPreset);
        } else {
            PresetManager.Preset p = PresetManager.find(selectedPreset);
            if (p == null) {
                return;
            }
            ids = new ArrayList<>(p.ids());
            colors = p.colors();
        }
        switch (mode) {
            case 0 -> setting.replaceWith(ids);
            case 1 -> setting.addAll(ids);
            default -> setting.removeAll(ids);
        }
        if (mode != 2) {
            for (Map.Entry<String, Integer> c : colors.entrySet()) {
                if (setting.contains(c.getKey())) {
                    setting.setColor(c.getKey(), c.getValue());
                }
            }
        }
        refilterKeepScroll();
        flash((mode == 0 ? "Selection replaced by " : mode == 1 ? "Added " : "Removed ") + selectedPreset);
        ConfigManager.save();
    }

    private void copyBuiltin() {
        if (selectedPreset == null || !selectedBuiltin) {
            return;
        }
        String base = selectedPreset;
        String name = base;
        int n = 2;
        while (PresetManager.find(name) != null) {
            name = base + " " + n++;
        }
        PresetManager.Preset p = PresetManager.create(name, builtinIds(base), Map.of());
        if (p != null) {
            selectedPreset = p.name();
            selectedBuiltin = false;
            flash("Created preset " + p.name());
            ConfigManager.save();
        }
    }

    private void deleteSelected() {
        PresetManager.Preset p = selectedPreset == null ? null : PresetManager.find(selectedPreset);
        if (p != null && !selectedBuiltin) {
            PresetManager.delete(p);
            flash("Deleted preset " + p.name());
            selectedPreset = null;
            ConfigManager.save();
        }
    }

    private void startPrompt(Prompt mode, String initial) {
        prompt = mode;
        nameBox.setValue(initial);
        setFocused(nameBox);
    }

    private void cancelPrompt() {
        prompt = Prompt.NONE;
        setFocused(search);
    }

    private void confirmPrompt() {
        String name = nameBox.getValue();
        if (prompt == Prompt.CREATE) {
            PresetManager.Preset p = PresetManager.create(name, setting.get(), setting.customColors());
            if (p == null) {
                flash("Name is empty, too long or already used");
                return;
            }
            selectedPreset = p.name();
            selectedBuiltin = false;
            flash("Saved preset " + p.name());
        } else if (prompt == Prompt.RENAME) {
            PresetManager.Preset p = selectedPreset == null ? null : PresetManager.find(selectedPreset);
            if (p == null || !PresetManager.rename(p, name)) {
                flash("Name is empty, too long or already used");
                return;
            }
            selectedPreset = p.name();
            flash("Renamed to " + p.name());
        }
        prompt = Prompt.NONE;
        setFocused(search);
        ConfigManager.save();
    }

    // ------------------------------------------------------------------ input

    private void setHue(double mouseX) {
        if (colorTarget == null) {
            return;
        }
        float h = (float) Mth.clamp((mouseX - hueX) / hueW, 0.0, 0.999);
        setting.setColor(colorTarget, BlockCatalog.hsvToRgb(h, 0.65f, 1.0f));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        if (Draw.inside(mx, my, leftX, searchY, leftW, 28)) {
            setFocused(search);
            return search.mouseClicked(event, doubleClick) || true;
        }
        if (prompt != Prompt.NONE && nameBox != null && Draw.inside(mx, my, rightX, nameBox.getY() - 7, rightW, 26)) {
            setFocused(nameBox);
            return nameBox.mouseClicked(event, doubleClick) || true;
        }
        if (button == 0) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Btn b = buttons.get(i);
                if (Draw.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
                    b.action().run();
                    return true;
                }
            }
            if (colorTarget != null && Draw.inside(mx, my, hueX, hueY - 2, hueW, 14)) {
                draggingHue = true;
                setHue(mx);
                return true;
            }
        }
        if (Draw.inside(mx, my, gridX, gridY, gridW, gridH)) {
            int cols = Math.max(1, (gridW - 10) / CELL);
            int gridPad = (gridW - 10 - cols * CELL) / 2;
            int col = (int) ((mx - gridX - gridPad) / CELL);
            int row = (int) ((my - gridY + gridScroll) / CELL);
            if (col >= 0 && col < cols && row >= 0) {
                int index = row * cols + col;
                if (index < filtered.size()) {
                    String id = filtered.get(index).id();
                    if (button == 0) {
                        setting.toggle(id);
                        if (!setting.contains(id) && id.equals(colorTarget)) {
                            colorTarget = null;
                        }
                        if (selectedOnly) {
                            refilterKeepScroll();
                        }
                    } else if (button == 1) {
                        setting.add(id);
                        colorTarget = id;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingHue) {
            setHue(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingHue) {
            draggingHue = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Draw.inside(mouseX, mouseY, gridX, gridY, gridW, gridH)) {
            gridTarget -= (float) scrollY * CELL * 2;
            return true;
        }
        if (Draw.inside(mouseX, mouseY, rightX, listY, rightW, listH)) {
            listTarget -= (float) scrollY * ROW_H * 2;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (prompt != Prompt.NONE) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmPrompt();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelPrompt();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
