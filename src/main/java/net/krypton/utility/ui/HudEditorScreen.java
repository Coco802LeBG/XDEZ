package net.krypton.utility.ui;

import net.krypton.utility.core.ConfigManager;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.hud.HudRenderer;
import net.krypton.utility.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-and-drop editor for the HUD widgets. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private int dragging; // 0 none, 1 info, 2 list
    private double grabX;
    private double grabY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Draw.alphaMul = 1f;
        g.fill(0, 0, width, height, 0x99080810);
        HudModule hud = ModuleManager.get(HudModule.class);
        int[] info = HudRenderer.drawInfo(g, hud, true);
        int[] list = hud.moduleList.get() ? HudRenderer.drawList(g, hud, true) : new int[]{0, 0, 0, 0};
        frame(g, info, mouseX, mouseY, dragging == 1);
        frame(g, list, mouseX, mouseY, dragging == 2);

        int w = 250;
        Draw.shadow(g, (width - w) / 2, 10, w, 28, 8, 5);
        Draw.round(g, (width - w) / 2, 10, w, 28, 8, Theme.WINDOW);
        Draw.textGradient(g, "HUD EDITOR", (width - w) / 2 + 12, 16, 1f, Theme.accent(), Theme.accent2());
        Draw.text(g, "Drag a widget  -  ESC to go back", (width - w) / 2 + 12, 27, Theme.MUTED);
    }

    private void frame(GuiGraphics g, int[] r, int mx, int my, boolean active) {
        if (r[2] <= 0) {
            return;
        }
        boolean hover = Draw.inside(mx, my, r[0], r[1], r[2], r[3]);
        int c = active ? Theme.accent2() : hover ? Theme.accent() : Draw.withAlpha(Theme.accent(), 0.45f);
        Draw.roundOutline(g, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, 7, c);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (Draw.inside(event.x(), event.y(), HudRenderer.infoRect[0], HudRenderer.infoRect[1], HudRenderer.infoRect[2], HudRenderer.infoRect[3])) {
                dragging = 1;
                grabX = event.x() - HudRenderer.infoRect[0];
                grabY = event.y() - HudRenderer.infoRect[1];
                return true;
            }
            if (Draw.inside(event.x(), event.y(), HudRenderer.listRect[0], HudRenderer.listRect[1], HudRenderer.listRect[2], HudRenderer.listRect[3])) {
                dragging = 2;
                grabX = event.x() - HudRenderer.listRect[0];
                grabY = event.y() - HudRenderer.listRect[1];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == 0) {
            return super.mouseDragged(event, dx, dy);
        }
        HudModule hud = ModuleManager.get(HudModule.class);
        int[] r = dragging == 1 ? HudRenderer.infoRect : HudRenderer.listRect;
        int nx = (int) Math.round(event.x() - grabX);
        int ny = (int) Math.round(event.y() - grabY);
        double fx = HudRenderer.toFraction(nx, r[2], width);
        double fy = HudRenderer.toFraction(ny, r[3], height);
        if (dragging == 1) {
            hud.infoX.fromJson(new com.google.gson.JsonPrimitive(fx));
            hud.infoY.fromJson(new com.google.gson.JsonPrimitive(fy));
        } else {
            hud.listX.fromJson(new com.google.gson.JsonPrimitive(fx));
            hud.listY.fromJson(new com.google.gson.JsonPrimitive(fy));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
