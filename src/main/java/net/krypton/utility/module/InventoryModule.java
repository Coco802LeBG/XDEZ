package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.krypton.utility.inventory.InventoryActions;

/**
 * Settings of the container tools. Sorting and bulk moves are replayed as ordinary container clicks, exactly like
 * clicking by hand, so this is the one module whose actions are visible to the server.
 */
public final class InventoryModule extends Module {
    // Sorting
    public final ModeSetting sortBy;
    public final BoolSetting reverse;
    public final BoolSetting includeHotbar;
    // Speed
    public final NumberSetting clicksPerTick;
    public final NumberSetting delay;
    // Buttons
    public final BoolSetting showButtons;
    public final ModeSetting buttonSide;
    public final BoolSetting showSortContainer;
    public final BoolSetting showSortInventory;
    public final BoolSetting showTakeAll;
    public final BoolSetting showStoreAll;
    public final BoolSetting showStoreSimilar;

    public InventoryModule() {
        super("inventory_tools", "Inventory Tools", "Sort containers and move items in bulk.", Category.INVENTORY, true);
        serverNotice("Sorting and bulk moves send the same container click packets as doing it by hand, but many in a short "
                + "time. A server can notice, rate-limit or reject this. Keep the speed low on servers you do not control.");

        group("Sorting");
        sortBy = add(new ModeSetting("sort_by", "Sort by", "Name", "Name", "Count"));
        reverse = add(new BoolSetting("reverse", "Reverse order", false));
        includeHotbar = add(new BoolSetting("include_hotbar", "Include the hotbar", false));

        group("Speed");
        clicksPerTick = add(risky(new NumberSetting("clicks_per_tick", "Clicks per tick", 1, 1, 8, 1),
                "More clicks per tick means a bigger burst of packets, which is easier for a server to notice."));
        delay = add(new NumberSetting("delay", "Delay between batches (ticks)", 1, 0, 10, 1));

        group("Buttons");
        showButtons = add(new BoolSetting("show_buttons", "Show the button panel", true));
        buttonSide = add(new ModeSetting("button_side", "Panel side", "Right", "Right", "Left"), showButtons::get);
        showSortContainer = add(new BoolSetting("show_sort_container", "Button: sort container", true), showButtons::get);
        showSortInventory = add(new BoolSetting("show_sort_inventory", "Button: sort inventory", true), showButtons::get);
        showTakeAll = add(new BoolSetting("show_take_all", "Button: take all", true), showButtons::get);
        showStoreAll = add(new BoolSetting("show_store_all", "Button: store all", true), showButtons::get);
        showStoreSimilar = add(new BoolSetting("show_store_similar", "Button: store similar", true), showButtons::get);
    }

    @Override
    protected void onDisable() {
        InventoryActions.cancel();
    }
}
