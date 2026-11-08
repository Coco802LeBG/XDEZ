package net.krypton.utility.inventory;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.mixin.AbstractContainerScreenAccessor;
import net.krypton.utility.module.InventoryModule;
import net.krypton.utility.ui.Draw;
import net.krypton.utility.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

import java.util.ArrayList;
import java.util.List;

/** Draws the styled action buttons next to storage screens and handles their clicks. */
public final class InventoryUi {
    private static final int W = 88;
    private static final int H = 20;
    private static final int GAP = 5;

    private enum Action {
        SORT_CONTAINER("Sort container"),
        SORT_INVENTORY("Sort inventory"),
        TAKE_ALL("Take all"),
        STORE_ALL("Store all"),
        STORE_SIMILAR("Store similar");

        final String label;

        Action(String label) {
            this.label = label;
        }
    }

    private InventoryUi() {
    }

    private static boolean hasContainer(Screen s) {
        return s instanceof ContainerScreen || s instanceof ShulkerBoxScreen || s instanceof HopperScreen
                || s instanceof DispenserScreen;
    }

    private static boolean supported(Screen s) {
        return hasContainer(s) || s instanceof InventoryScreen;
    }

    private static boolean enabled(Action action) {
        InventoryModule m = ModuleManager.get(InventoryModule.class);
        return switch (action) {
            case SORT_CONTAINER -> m.showSortContainer.get();
            case SORT_INVENTORY -> m.showSortInventory.get();
            case TAKE_ALL -> m.showTakeAll.get();
            case STORE_ALL -> m.showStoreAll.get();
            case STORE_SIMILAR -> m.showStoreSimilar.get();
        };
    }

    private static List<Action> actionsFor(Screen s) {
        List<Action> list = new ArrayList<>();
        for (Action a : Action.values()) {
            boolean containerOnly = a != Action.SORT_INVENTORY;
            if ((!containerOnly || hasContainer(s)) && enabled(a)) {
                list.add(a);
            }
        }
        return list;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs) || !supported(screen)) {
                return;
            }
            ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> render(cs, g, mx, my));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (event.button() != 0 || !visible()) {
                    return true;
                }
                Action hit = hit(cs, event.x(), event.y());
                if (hit == null) {
                    return true;
                }
                run(hit);
                return false;
            });
        });
    }

    private static boolean visible() {
        InventoryModule m = ModuleManager.get(InventoryModule.class);
        return m.isEnabled() && m.showButtons.get();
    }

    private static int originX(AbstractContainerScreen<?> cs) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) (Object) cs;
        if (ModuleManager.get(InventoryModule.class).buttonSide.is("Left")) {
            return a.krypton$leftPos() - W - 8;
        }
        return a.krypton$leftPos() + a.krypton$imageWidth() + 8;
    }

    private static int originY(AbstractContainerScreen<?> cs) {
        return ((AbstractContainerScreenAccessor) (Object) cs).krypton$topPos();
    }

    private static Action hit(AbstractContainerScreen<?> cs, double mx, double my) {
        int x = originX(cs);
        int y = originY(cs);
        for (Action a : actionsFor(cs)) {
            if (Draw.inside(mx, my, x, y, W, H)) {
                return a;
            }
            y += H + GAP;
        }
        return null;
    }

    private static void render(AbstractContainerScreen<?> cs, GuiGraphics g, int mx, int my) {
        if (!visible()) {
            return;
        }
        int x = originX(cs);
        int y = originY(cs);
        List<Action> actions = actionsFor(cs);
        if (actions.isEmpty()) {
            return;
        }
        int total = actions.size() * (H + GAP) - GAP;
        Draw.shadow(g, x - 6, y - 6, W + 12, total + 12, 8, 5);
        Draw.round(g, x - 6, y - 6, W + 12, total + 12, 8, Theme.WINDOW);
        boolean busy = InventoryActions.busy();
        for (Action a : actions) {
            boolean hover = Draw.inside(mx, my, x, y, W, H) && !busy;
            int bg = hover ? Draw.lerp(Theme.CARD_HOVER, Theme.accent(), 0.35f) : Theme.CARD;
            Draw.round(g, x, y, W, H, 5, bg);
            Draw.rect(g, x + 3, y + 5, 2, H - 10, hover ? Theme.accent2() : Theme.accent());
            Draw.text(g, a.label, x + 11, y + (H - Draw.lineHeight()) / 2 + 1, busy ? Theme.MUTED : Theme.TEXT);
            y += H + GAP;
        }
    }

    private static void run(Action action) {
        Minecraft mc = Minecraft.getInstance();
        switch (action) {
            case SORT_CONTAINER -> InventoryActions.sort(mc, InventoryActions.Region.CONTAINER);
            case SORT_INVENTORY -> InventoryActions.sort(mc, InventoryActions.Region.PLAYER);
            case TAKE_ALL -> InventoryActions.moveAll(mc, InventoryActions.Region.CONTAINER);
            case STORE_ALL -> InventoryActions.moveAll(mc, InventoryActions.Region.PLAYER);
            case STORE_SIMILAR -> InventoryActions.moveSimilar(mc);
        }
    }
}
