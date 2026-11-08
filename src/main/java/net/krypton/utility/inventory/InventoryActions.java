package net.krypton.utility.inventory;

import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.module.InventoryModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plans and executes inventory operations. Plans are computed against a simulated copy of the slots, then replayed as
 * a rate-limited queue of click packets through MultiPlayerGameMode#handleInventoryMouseClick, which keeps the
 * container revision and local prediction consistent with the server.
 */
public final class InventoryActions {
    public enum Region { CONTAINER, PLAYER }

    public record Op(int slot, ClickType type) {
    }

    private static final ArrayDeque<Op> QUEUE = new ArrayDeque<>();
    private static int syncId = -1;
    private static int wait;

    private InventoryActions() {
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    public static void cancel() {
        QUEUE.clear();
    }

    private static InventoryModule cfg() {
        return ModuleManager.get(InventoryModule.class);
    }

    // ------------------------------------------------------------------ queue execution

    public static void tick(Minecraft mc) {
        if (QUEUE.isEmpty()) {
            return;
        }
        if (mc.player == null || mc.gameMode == null || !(mc.screen instanceof AbstractContainerScreen<?>)
                || mc.player.containerMenu.containerId != syncId) {
            QUEUE.clear();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        int n = cfg().clicksPerTick.asInt();
        for (int i = 0; i < n && !QUEUE.isEmpty(); i++) {
            Op op = QUEUE.poll();
            mc.gameMode.handleInventoryMouseClick(syncId, op.slot(), 0, op.type(), mc.player);
        }
        wait = cfg().delay.asInt();
    }

    private static void submit(Minecraft mc, List<Op> ops) {
        if (ops.isEmpty() || mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()) {
            return; // never start while an item is held on the cursor
        }
        QUEUE.clear();
        syncId = mc.player.containerMenu.containerId;
        QUEUE.addAll(ops);
        wait = 0;
    }

    // ------------------------------------------------------------------ regions

    public static List<Slot> region(Player player, Region region) {
        AbstractContainerMenu menu = player.containerMenu;
        boolean hotbar = cfg().includeHotbar.get();
        List<Slot> out = new ArrayList<>();
        for (Slot s : menu.slots) {
            boolean mine = s.container instanceof Inventory;
            if (region == Region.CONTAINER) {
                if (!mine) {
                    out.add(s);
                }
            } else if (mine) {
                int i = s.getContainerSlot();
                if ((i >= 9 && i < 36) || (hotbar && i >= 0 && i < 9)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ public actions

    public static void sort(Minecraft mc, Region region) {
        if (mc.player == null || busy()) {
            return;
        }
        List<Slot> slots = region(mc.player, region);
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot s : slots) {
            stacks.add(s.getItem().copy());
        }
        List<int[]> plan = planSort(stacks, comparator());
        List<Op> ops = new ArrayList<>();
        for (int[] step : plan) {
            // step = {slotIndexA, slotIndexB, kind}; see planSort for the click sequences
            int a = slots.get(step[0]).index;
            int b = slots.get(step[1]).index;
            switch (step[2]) {
                case KIND_MERGE -> {
                    // pick up B, drop on A (merge), put the remainder back on B
                    ops.add(new Op(b, ClickType.PICKUP));
                    ops.add(new Op(a, ClickType.PICKUP));
                    if (step[3] == 1) {
                        ops.add(new Op(b, ClickType.PICKUP));
                    }
                }
                case KIND_SWAP_EMPTY_A -> {
                    ops.add(new Op(b, ClickType.PICKUP));
                    ops.add(new Op(a, ClickType.PICKUP));
                }
                case KIND_SWAP_EMPTY_B -> {
                    ops.add(new Op(a, ClickType.PICKUP));
                    ops.add(new Op(b, ClickType.PICKUP));
                }
                default -> {
                    ops.add(new Op(a, ClickType.PICKUP));
                    ops.add(new Op(b, ClickType.PICKUP));
                    ops.add(new Op(a, ClickType.PICKUP));
                }
            }
        }
        submit(mc, ops);
    }

    /** Shift-click everything from one side to the other. */
    public static void moveAll(Minecraft mc, Region from) {
        if (mc.player == null || busy()) {
            return;
        }
        List<Op> ops = new ArrayList<>();
        for (Slot s : region(mc.player, from)) {
            if (s.hasItem()) {
                ops.add(new Op(s.index, ClickType.QUICK_MOVE));
            }
        }
        submit(mc, ops);
    }

    /** Shift-click only the stacks of the player's inventory that already exist in the open container. */
    public static void moveSimilar(Minecraft mc) {
        if (mc.player == null || busy()) {
            return;
        }
        List<ItemStack> inContainer = new ArrayList<>();
        for (Slot s : region(mc.player, Region.CONTAINER)) {
            if (s.hasItem()) {
                inContainer.add(s.getItem());
            }
        }
        List<Op> ops = new ArrayList<>();
        for (Slot s : region(mc.player, Region.PLAYER)) {
            if (!s.hasItem()) {
                continue;
            }
            for (ItemStack c : inContainer) {
                if (ItemStack.isSameItemSameComponents(c, s.getItem())) {
                    ops.add(new Op(s.index, ClickType.QUICK_MOVE));
                    break;
                }
            }
        }
        submit(mc, ops);
    }

    // ------------------------------------------------------------------ planning (pure logic, no Minecraft state)

    static final int KIND_MERGE = 0;
    static final int KIND_SWAP_EMPTY_A = 1;
    static final int KIND_SWAP_EMPTY_B = 2;
    static final int KIND_SWAP = 3;

    private static Comparator<ItemStack> comparator() {
        Comparator<ItemStack> byName = Comparator.comparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        Comparator<ItemStack> order = cfg().sortBy.is("Count")
                ? Comparator.<ItemStack>comparingInt(s -> -s.getCount()).thenComparing(byName)
                : byName;
        return cfg().reverse.get() ? order.reversed() : order;
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }

    /**
     * Produces steps {a, b, kind, extra}. Phase 1 merges partial stacks, phase 2 is a selection sort where stacks of the
     * same kind are interchangeable (so a swap never has to merge two stacks, which vanilla would do instead of swap).
     */
    static List<int[]> planSort(List<ItemStack> input, Comparator<ItemStack> cmp) {
        int n = input.size();
        ItemStack[] st = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            st[i] = input.get(i).copy();
        }
        List<int[]> steps = new ArrayList<>();

        // phase 1: merge partial stacks
        for (int i = 0; i < n; i++) {
            if (st[i].isEmpty() || st[i].getCount() >= st[i].getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < n && st[i].getCount() < st[i].getMaxStackSize(); j++) {
                if (st[j].isEmpty() || !ItemStack.isSameItemSameComponents(st[i], st[j])) {
                    continue;
                }
                int space = st[i].getMaxStackSize() - st[i].getCount();
                int moved = Math.min(space, st[j].getCount());
                boolean remainder = moved < st[j].getCount();
                steps.add(new int[]{i, j, KIND_MERGE, remainder ? 1 : 0});
                st[i].grow(moved);
                st[j].shrink(moved);
            }
        }

        // phase 2: selection sort towards the target order (empties last)
        List<ItemStack> target = new ArrayList<>();
        for (ItemStack s : st) {
            if (!s.isEmpty()) {
                target.add(s.copy());
            }
        }
        target.sort(cmp);
        while (target.size() < n) {
            target.add(ItemStack.EMPTY);
        }
        for (int p = 0; p < n; p++) {
            ItemStack want = target.get(p);
            if (sameKind(st[p], want)) {
                continue;
            }
            int q = -1;
            for (int k = p + 1; k < n; k++) {
                if (sameKind(st[k], want)) {
                    q = k;
                    break;
                }
            }
            if (q < 0) {
                continue;
            }
            int kind;
            if (st[p].isEmpty()) {
                kind = KIND_SWAP_EMPTY_A;
            } else if (st[q].isEmpty()) {
                kind = KIND_SWAP_EMPTY_B;
            } else {
                kind = KIND_SWAP;
            }
            steps.add(new int[]{p, q, kind, 0});
            ItemStack tmp = st[p];
            st[p] = st[q];
            st[q] = tmp;
        }
        return steps;
    }
}
