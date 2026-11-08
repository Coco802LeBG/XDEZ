package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.render.Projector;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ColorSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.krypton.utility.render.EspRenderer;
import net.krypton.utility.render.HighlightSource;
import net.krypton.utility.render.Target;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
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

    private record CachedContainer(AABB box, ContainerKind kind) {
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
    private Vec3 lastScanCenter = Vec3.ZERO;

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
    public void onTick(Minecraft mc) {
        if (!containers.get() || mc.level == null || mc.player == null) {
            containerCache.clear();
            return;
        }
        Vec3 center = FreecamModule.viewCenter(mc);
        // rescan right away when the viewpoint (free camera) has travelled far since the last scan
        boolean moved = center.distanceToSqr(lastScanCenter) > 64.0;
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

    private static ContainerKind kindOf(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) {
            return ContainerKind.TRAPPED;
        }
        if (be instanceof ChestBlockEntity) {
            return ContainerKind.CHEST;
        }
        if (be instanceof EnderChestBlockEntity) {
            return ContainerKind.ENDER;
        }
        if (be instanceof ShulkerBoxBlockEntity) {
            return ContainerKind.SHULKER;
        }
        if (be instanceof BarrelBlockEntity) {
            return ContainerKind.BARREL;
        }
        if (be instanceof HopperBlockEntity) {
            return ContainerKind.HOPPER;
        }
        if (be instanceof DispenserBlockEntity) {
            return ContainerKind.DISPENSER;
        }
        return null;
    }

    private void scanContainers(Minecraft mc, Vec3 center) {
        ClientLevel level = mc.level;
        List<CachedContainer> found = new ArrayList<>();
        int radius = range.asInt() / 16 + 1;
        int pcx = ((int) Math.floor(center.x)) >> 4;
        int pcz = ((int) Math.floor(center.z)) >> 4;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    ContainerKind kind = kindOf(be);
                    if (kind == null) {
                        continue;
                    }
                    AABB box = new AABB(be.getBlockPos());
                    if (kind == ContainerKind.CHEST || kind == ContainerKind.TRAPPED || kind == ContainerKind.ENDER) {
                        // the real chest model: 14/16 wide, 14/16 tall, resting on the floor of the block
                        double x = be.getBlockPos().getX();
                        double y = be.getBlockPos().getY();
                        double z = be.getBlockPos().getZ();
                        box = new AABB(x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.875, z + 0.9375);
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

    private String label(String name, double distSq, LivingEntity living) {
        StringBuilder sb = new StringBuilder(name);
        if (showHealth.get() && living != null) {
            sb.append("  ").append(String.format(Locale.ROOT, "%.0f/%.0f", living.getHealth(), living.getMaxHealth()));
        }
        if (showDistance.get()) {
            sb.append("  ").append((int) Math.sqrt(distSq)).append('m');
        }
        return sb.toString();
    }

    @Override
    public void collect(float pt, List<Target> out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cam = Projector.camera();
        double maxSq = range.asDouble() * range.asDouble();
        double minSq = minRange.asDouble() * minRange.asDouble();
        double pad = padding.asDouble();
        List<Target> found = new ArrayList<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player && !(FreecamModule.isActive() && showOwnBody.get())) {
                continue;
            }
            int color;
            if (e instanceof Player) {
                if (!players.get()) {
                    continue;
                }
                color = playerColor.argb();
            } else if (e instanceof Enemy) {
                if (!hostile.get()) {
                    continue;
                }
                color = hostileColor.argb();
            } else if (e instanceof ItemEntity) {
                if (!items.get()) {
                    continue;
                }
                color = itemColor.argb();
            } else if (e instanceof LivingEntity) {
                if (!passive.get()) {
                    continue;
                }
                color = passiveColor.argb();
            } else {
                continue;
            }
            Vec3 lp = e.getPosition(pt);
            double d = lp.distanceToSqr(cam);
            if (d > maxSq || d < minSq) {
                continue;
            }
            AABB box = e.getBoundingBox().move(lp.x - e.getX(), lp.y - e.getY(), lp.z - e.getZ()).inflate(pad);
            String name = e instanceof ItemEntity ie ? ie.getItem().getHoverName().getString() : e.getName().getString();
            float health = -1f;
            if (e instanceof LivingEntity le && le.getMaxHealth() > 0f) {
                health = le.getHealth() / le.getMaxHealth();
            }
            found.add(new Target(box, color, labels.get() ? label(name, d, e instanceof LivingEntity le2 ? le2 : null) : null, d, health));
        }

        if (containers.get()) {
            for (CachedContainer c : containerCache) {
                if (!enabledKind(c.kind())) {
                    continue;
                }
                double d = c.box().getCenter().distanceToSqr(cam);
                if (d > maxSq || d < minSq) {
                    continue;
                }
                found.add(new Target(c.box().inflate(pad), colorOf(c.kind()), labels.get() ? label(c.kind().label, d, null) : null, d));
            }
        }

        found.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
        int limit = Math.min(found.size(), maxTargets.asInt());
        for (int i = 0; i < limit; i++) {
            out.add(found.get(i));
        }
    }
}
