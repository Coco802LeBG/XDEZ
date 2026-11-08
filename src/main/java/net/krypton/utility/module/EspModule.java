package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ColorSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.krypton.utility.render.EspRenderer;
import net.krypton.utility.render.HighlightSource;
import net.krypton.utility.render.Target;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1542;
import net.minecraft.class_1569;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2601;
import net.minecraft.class_2611;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2646;
import net.minecraft.class_2818;
import net.minecraft.class_310;
import net.minecraft.class_3719;
import net.minecraft.class_638;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Collision-box overlay for entities and storage blocks. Reads only data the client already has; sends nothing. */
public final class EspModule extends Module implements HighlightSource {
    private enum ContainerKind {
        CHEST("Chest"), TRAPPED("Trapped chest"), ENDER("Ender chest"), SHULKER("Shulker box"),
        BARREL("Barrel"), HOPPER("Hopper"), DISPENSER("Dispenser / dropper");

        final String label;

        ContainerKind(String label) {
            this.label = label;
        }
    }

    private record CachedContainer(class_238 box, ContainerKind kind) {
    }

    // Targets
    public final BoolSetting players;
    public final BoolSetting hostile;
    public final BoolSetting passive;
    public final BoolSetting items;
    public final BoolSetting containers;
    public final BoolSetting showOwnBody;
    // Container types
    public final BoolSetting chests;
    public final BoolSetting trappedChests;
    public final BoolSetting enderChests;
    public final BoolSetting shulkers;
    public final BoolSetting barrels;
    public final BoolSetting hoppers;
    public final BoolSetting dispensers;
    // Appearance
    public final ModeSetting style;
    public final NumberSetting opacity;
    public final NumberSetting padding;
    public final BoolSetting labels;
    public final BoolSetting showDistance;
    public final BoolSetting showHealth;
    public final BoolSetting healthBars;
    // Colours
    public final ColorSetting playerColor;
    public final ColorSetting hostileColor;
    public final ColorSetting passiveColor;
    public final ColorSetting itemColor;
    public final ColorSetting chestColor;
    public final ColorSetting trappedColor;
    public final ColorSetting enderColor;
    public final ColorSetting shulkerColor;
    public final ColorSetting barrelColor;
    public final ColorSetting otherColor;
    // Range & performance
    public final NumberSetting range;
    public final NumberSetting minRange;
    public final NumberSetting maxTargets;
    public final NumberSetting refreshTicks;

    private final List<CachedContainer> containerCache = new ArrayList<>();
    private int refreshTimer;
    private class_243 lastScanCenter = class_243.field_1353;

    public EspModule() {
        super("esp", "ESP Boxes", "Bounding boxes around entities and storage blocks.", Category.VISUALS, false);

        group("Targets");
        players = add(new BoolSetting("players", "Players", true));
        hostile = add(new BoolSetting("hostile", "Hostile mobs", true));
        passive = add(new BoolSetting("passive", "Passive mobs", false));
        items = add(new BoolSetting("items", "Dropped items", false));
        showOwnBody = add(new BoolSetting("show_own_body", "Show my own body while in Freecam", true), players::get);
        containers = add(new BoolSetting("containers", "Storage blocks", true));

        group("Storage types");
        chests = add(new BoolSetting("chests", "Chests", true), containers::get);
        trappedChests = add(new BoolSetting("trapped_chests", "Trapped chests", true), containers::get);
        enderChests = add(new BoolSetting("ender_chests", "Ender chests", true), containers::get);
        shulkers = add(new BoolSetting("shulkers", "Shulker boxes", true), containers::get);
        barrels = add(new BoolSetting("barrels", "Barrels", true), containers::get);
        hoppers = add(new BoolSetting("hoppers", "Hoppers", false), containers::get);
        dispensers = add(new BoolSetting("dispensers", "Dispensers & droppers", false), containers::get);

        group("Appearance");
        style = add(new ModeSetting("style", "Style", "Corners", EspRenderer.STYLES));
        opacity = add(new NumberSetting("opacity", "Fill opacity", 60, 10, 200, 10));
        padding = add(new NumberSetting("padding", "Box padding", 0.0, 0.0, 0.4, 0.02));
        labels = add(new BoolSetting("labels", "Labels", true));
        showDistance = add(new BoolSetting("show_distance", "Show distance in labels", true), labels::get);
        showHealth = add(new BoolSetting("show_health", "Show health in labels", false), labels::get);
        healthBars = add(new BoolSetting("health_bars", "Health bars next to boxes", true));

        group("Colours");
        playerColor = add(new ColorSetting("color_player", "Players", 0x60A5FA));
        hostileColor = add(new ColorSetting("color_hostile", "Hostile mobs", 0xF43F5E));
        passiveColor = add(new ColorSetting("color_passive", "Passive mobs", 0x34D399));
        itemColor = add(new ColorSetting("color_item", "Dropped items", 0xFACC15));
        chestColor = add(new ColorSetting("color_chest", "Chests", 0xF59E0B), containers::get);
        trappedColor = add(new ColorSetting("color_trapped", "Trapped chests", 0xF43F5E), containers::get);
        enderColor = add(new ColorSetting("color_ender", "Ender chests", 0xA855F7), containers::get);
        shulkerColor = add(new ColorSetting("color_shulker", "Shulker boxes", 0xEC4899), containers::get);
        barrelColor = add(new ColorSetting("color_barrel", "Barrels", 0xF97316), containers::get);
        otherColor = add(new ColorSetting("color_other", "Hoppers & dispensers", 0x94A3B8), containers::get);

        group("Range & performance");
        range = add(new NumberSetting("range", "Range", 96, 16, 256, 8));
        minRange = add(new NumberSetting("min_range", "Hide closer than", 0, 0, 32, 1));
        maxTargets = add(new NumberSetting("max_targets", "Max boxes", 256, 32, 512, 32));
        refreshTicks = add(new NumberSetting("refresh_ticks", "Storage rescan interval (ticks)", 20, 5, 100, 5), containers::get);
    }

    @Override
    protected void onEnable() {
        refreshTimer = 0;
    }

    @Override
    protected void onDisable() {
        containerCache.clear();
    }

    @Override
    public void onDisconnect() {
        containerCache.clear();
    }

    // ------------------------------------------------------------------ storage scan (loaded chunks only)

    @Override
    public void onTick(class_310 mc) {
        if (!containers.get() || mc.field_1687 == null || mc.field_1724 == null) {
            containerCache.clear();
            return;
        }
        class_243 center = FreecamModule.viewCenter(mc);
        // rescan right away when the viewpoint (free camera) has travelled far since the last scan
        boolean moved = center.method_1025(lastScanCenter) > 64.0;
        if (refreshTimer-- > 0 && !moved) {
            return;
        }
        refreshTimer = refreshTicks.asInt();
        lastScanCenter = center;
        scanContainers(mc, center);
    }

    private boolean enabledKind(ContainerKind kind) {
        return switch (kind) {
            case CHEST -> chests.get();
            case TRAPPED -> trappedChests.get();
            case ENDER -> enderChests.get();
            case SHULKER -> shulkers.get();
            case BARREL -> barrels.get();
            case HOPPER -> hoppers.get();
            case DISPENSER -> dispensers.get();
        };
    }

    private int colorOf(ContainerKind kind) {
        return switch (kind) {
            case CHEST -> chestColor.argb();
            case TRAPPED -> trappedColor.argb();
            case ENDER -> enderColor.argb();
            case SHULKER -> shulkerColor.argb();
            case BARREL -> barrelColor.argb();
            case HOPPER, DISPENSER -> otherColor.argb();
        };
    }

    private static ContainerKind kindOf(class_2586 be) {
        if (be instanceof class_2646) {
            return ContainerKind.TRAPPED;
        }
        if (be instanceof class_2595) {
            return ContainerKind.CHEST;
        }
        if (be instanceof class_2611) {
            return ContainerKind.ENDER;
        }
        if (be instanceof class_2627) {
            return ContainerKind.SHULKER;
        }
        if (be instanceof class_3719) {
            return ContainerKind.BARREL;
        }
        if (be instanceof class_2614) {
            return ContainerKind.HOPPER;
        }
        if (be instanceof class_2601) {
            return ContainerKind.DISPENSER;
        }
        return null;
    }

    private void scanContainers(class_310 mc, class_243 center) {
        class_638 level = mc.field_1687;
        List<CachedContainer> found = new ArrayList<>();
        int radius = range.asInt() / 16 + 1;
        int pcx = ((int) Math.floor(center.field_1352)) >> 4;
        int pcz = ((int) Math.floor(center.field_1350)) >> 4;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                class_2818 chunk = level.method_2935().method_21730(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (class_2586 be : chunk.method_12214().values()) {
                    ContainerKind kind = kindOf(be);
                    if (kind == null) {
                        continue;
                    }
                    class_238 box = new class_238(be.method_11016());
                    if (kind == ContainerKind.CHEST || kind == ContainerKind.TRAPPED || kind == ContainerKind.ENDER) {
                        // the real chest model: 14/16 wide, 14/16 tall, resting on the floor of the block
                        double x = be.method_11016().method_10263();
                        double y = be.method_11016().method_10264();
                        double z = be.method_11016().method_10260();
                        box = new class_238(x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.875, z + 0.9375);
                    }
                    found.add(new CachedContainer(box, kind));
                }
            }
        }
        containerCache.clear();
        containerCache.addAll(found);
    }

    // ------------------------------------------------------------------ HighlightSource

    @Override
    public String style() {
        return style.get();
    }

    @Override
    public int fillAlpha() {
        return opacity.asInt();
    }

    @Override
    public boolean labels() {
        return labels.get();
    }

    @Override
    public boolean healthBars() {
        return healthBars.get();
    }

    private String label(String name, double distSq, class_1309 living) {
        StringBuilder sb = new StringBuilder(name);
        if (showHealth.get() && living != null) {
            sb.append("  ").append(String.format(Locale.ROOT, "%.0f/%.0f", living.method_6032(), living.method_6063()));
        }
        if (showDistance.get()) {
            sb.append("  ").append((int) Math.sqrt(distSq)).append('m');
        }
        return sb.toString();
    }

    @Override
    public void collect(float pt, List<Target> out) {
        class_310 mc = class_310.method_1551();
        if (mc.field_1687 == null || mc.field_1724 == null) {
            return;
        }
        class_243 cam = mc.field_1773.method_19418().method_71156();
        double maxSq = range.asDouble() * range.asDouble();
        double minSq = minRange.asDouble() * minRange.asDouble();
        double pad = padding.asDouble();
        List<Target> found = new ArrayList<>();

        for (class_1297 e : mc.field_1687.method_18112()) {
            if (e == mc.field_1724 && !(FreecamModule.isActive() && showOwnBody.get())) {
                continue;
            }
            int color;
            if (e instanceof class_1657) {
                if (!players.get()) {
                    continue;
                }
                color = playerColor.argb();
            } else if (e instanceof class_1569) {
                if (!hostile.get()) {
                    continue;
                }
                color = hostileColor.argb();
            } else if (e instanceof class_1542) {
                if (!items.get()) {
                    continue;
                }
                color = itemColor.argb();
            } else if (e instanceof class_1309) {
                if (!passive.get()) {
                    continue;
                }
                color = passiveColor.argb();
            } else {
                continue;
            }
            class_243 lp = e.method_30950(pt);
            double d = lp.method_1025(cam);
            if (d > maxSq || d < minSq) {
                continue;
            }
            class_238 box = e.method_5829().method_989(lp.field_1352 - e.method_23317(), lp.field_1351 - e.method_23318(), lp.field_1350 - e.method_23321()).method_1014(pad);
            String name = e instanceof class_1542 ie ? ie.method_6983().method_7964().getString() : e.method_5477().getString();
            float health = -1f;
            if (e instanceof class_1309 le && le.method_6063() > 0f) {
                health = le.method_6032() / le.method_6063();
            }
            found.add(new Target(box, color, labels.get() ? label(name, d, e instanceof class_1309 le2 ? le2 : null) : null, d, health));
        }

        if (containers.get()) {
            for (CachedContainer c : containerCache) {
                if (!enabledKind(c.kind())) {
                    continue;
                }
                double d = c.box().method_1005().method_1025(cam);
                if (d > maxSq || d < minSq) {
                    continue;
                }
                found.add(new Target(c.box().method_1014(pad), colorOf(c.kind()), labels.get() ? label(c.kind().label, d, null) : null, d));
            }
        }

        found.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
        int limit = Math.min(found.size(), maxTargets.asInt());
        for (int i = 0; i < limit; i++) {
            out.add(found.get(i));
        }
    }
}
