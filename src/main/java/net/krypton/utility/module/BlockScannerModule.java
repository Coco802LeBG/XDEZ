package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BlockListSetting;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.krypton.utility.render.EspRenderer;
import net.krypton.utility.render.HighlightSource;
import net.krypton.utility.render.Target;
import net.krypton.utility.ui.Theme;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;
import net.minecraft.class_638;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights the blocks chosen by the user in the chunks the client has already received. It only reads local world
 * data (it cannot reveal anything the server did not send) and sends nothing.
 *
 * <p>Work is bounded: a few chunks per tick, empty sections are skipped, block lookup is a hash map on the Block
 * instance, and every chunk result is cached until it expires or the selection changes.
 */
public final class BlockScannerModule extends Module implements HighlightSource {
    private record Hit(long pos, class_2248 block) {
    }

    private record CachedChunk(List<Hit> hits, int scannedAt) {
    }

    public final BlockListSetting blocks;
    public final NumberSetting radius;
    public final NumberSetting rangeUp;
    public final NumberSetting rangeDown;
    public final BoolSetting exposedOnly;
    public final NumberSetting scanInterval;
    public final NumberSetting chunksPerTick;
    public final ModeSetting style;
    public final NumberSetting opacity;
    public final NumberSetting maxBoxes;
    public final NumberSetting maxDistance;
    public final ModeSetting colorMode;
    public final BoolSetting labels;
    public final BoolSetting showDistance;

    private final Map<Long, CachedChunk> cache = new HashMap<>();
    private int tickCounter;
    private int seenVersion = -1;

    public BlockScannerModule() {
        super("block_scanner", "Block Scanner", "Highlights the blocks you choose in loaded chunks.", Category.WORLD, false);

        group("Blocks");
        blocks = add(new BlockListSetting("blocks", "Blocks to highlight", List.of(
                "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris",
                "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:spawner")));

        group("Scan area");
        radius = add(new NumberSetting("radius", "Horizontal radius (chunks)", 4, 1, 12, 1));
        rangeUp = add(new NumberSetting("range_up", "Range above you (blocks)", 48, 8, 128, 8));
        rangeDown = add(new NumberSetting("range_down", "Range below you (blocks)", 48, 8, 128, 8));
        exposedOnly = add(new BoolSetting("exposed_only", "Only blocks touching air or a transparent block", false));

        group("Performance");
        scanInterval = add(new NumberSetting("scan_interval", "Rescan interval (ticks)", 100, 20, 600, 20));
        chunksPerTick = add(new NumberSetting("chunks_per_tick", "Chunks scanned per tick", 2, 1, 6, 1));

        group("Display");
        style = add(new ModeSetting("style", "Style", "Filled", EspRenderer.STYLES));
        opacity = add(new NumberSetting("opacity", "Fill opacity", 70, 10, 200, 10));
        maxBoxes = add(new NumberSetting("max_boxes", "Max boxes", 192, 16, 1024, 16));
        maxDistance = add(new NumberSetting("max_distance", "Max distance (blocks)", 128, 16, 256, 8));
        colorMode = add(new ModeSetting("color_mode", "Colours", "Per block", "Per block", "Accent"));
        labels = add(new BoolSetting("labels", "Labels", false));
        showDistance = add(new BoolSetting("show_distance", "Show distance in labels", true), labels::get);
    }

    @Override
    protected void onEnable() {
        cache.clear();
    }

    @Override
    protected void onDisable() {
        cache.clear();
    }

    @Override
    public void onDisconnect() {
        cache.clear();
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @Override
    public void onTick(class_310 mc) {
        class_638 level = mc.field_1687;
        if (level == null || mc.field_1724 == null) {
            return;
        }
        tickCounter++;
        if (blocks.version() != seenVersion) {
            seenVersion = blocks.version();
            cache.clear();
        }
        Map<class_2248, Integer> targets = blocks.resolved();
        if (targets.isEmpty()) {
            cache.clear();
            return;
        }
        int r = radius.asInt();
        class_243 center = FreecamModule.viewCenter(mc);
        int pcx = ((int) Math.floor(center.field_1352)) >> 4;
        int pcz = ((int) Math.floor(center.field_1350)) >> 4;

        cache.keySet().removeIf(k -> {
            int cx = (int) (k >> 32);
            int cz = (int) (long) k;
            return Math.abs(cx - pcx) > r + 1 || Math.abs(cz - pcz) > r + 1;
        });

        int budget = chunksPerTick.asInt();
        int interval = scanInterval.asInt();
        int py = (int) Math.floor(center.field_1351);
        for (int ring = 0; ring <= r && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    CachedChunk cached = cache.get(key(cx, cz));
                    if (cached != null && tickCounter - cached.scannedAt() < interval) {
                        continue;
                    }
                    class_2818 chunk = level.method_2935().method_21730(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    cache.put(key(cx, cz), scan(level, chunk, cx, cz, py, targets));
                    budget--;
                }
            }
        }
    }

    private CachedChunk scan(class_638 level, class_2818 chunk, int cx, int cz, int py, Map<class_2248, Integer> targets) {
        int minY = level.method_8597().comp_651();
        int maxY = minY + level.method_8597().comp_652() - 1;
        int y0 = Math.max(minY, py - rangeDown.asInt());
        int y1 = Math.min(maxY, py + rangeUp.asInt());
        List<Hit> hits = new ArrayList<>();
        if (y0 > y1) {
            return new CachedChunk(hits, tickCounter);
        }
        boolean exposed = exposedOnly.get();
        class_2826[] sections = chunk.method_12006();
        int firstSection = chunk.method_31602(y0);
        int lastSection = chunk.method_31602(y1);
        class_2338.class_2339 pos = new class_2338.class_2339();
        class_2338.class_2339 neighbour = new class_2338.class_2339();
        for (int si = firstSection; si <= lastSection && si < sections.length; si++) {
            class_2826 section = sections[si];
            if (section == null || section.method_38292()) {
                continue;
            }
            int sectionMinY = minY + si * 16;
            int ly0 = Math.max(y0, sectionMinY) - sectionMinY;
            int ly1 = Math.min(y1, sectionMinY + 15) - sectionMinY;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int ly = ly0; ly <= ly1; ly++) {
                        class_2680 state = section.method_12254(x, ly, z);
                        class_2248 block = state.method_26204();
                        if (!targets.containsKey(block)) {
                            continue;
                        }
                        pos.method_10103((cx << 4) + x, sectionMinY + ly, (cz << 4) + z);
                        if (!exposed || isExposed(level, pos, neighbour)) {
                            hits.add(new Hit(pos.method_10063(), block));
                        }
                    }
                }
            }
        }
        return new CachedChunk(hits, tickCounter);
    }

    private static boolean isExposed(class_638 level, class_2338 pos, class_2338.class_2339 tmp) {
        for (class_2350 d : class_2350.values()) {
            tmp.method_10101(pos).method_10098(d);
            if (!level.method_8320(tmp).method_26225()) {
                return true;
            }
        }
        return false;
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
    public void collect(float pt, List<Target> out) {
        class_310 mc = class_310.method_1551();
        if (mc.field_1724 == null) {
            return;
        }
        class_243 cam = mc.field_1773.method_19418().method_71156();
        double maxSq = maxDistance.asDouble() * maxDistance.asDouble();
        Map<class_2248, Integer> colors = blocks.resolved();
        boolean accent = colorMode.is("Accent");
        List<Target> all = new ArrayList<>();
        class_2338.class_2339 p = new class_2338.class_2339();
        for (CachedChunk c : cache.values()) {
            for (Hit h : c.hits()) {
                p.method_16363(h.pos());
                double d = class_243.method_24953(p).method_1025(cam);
                if (d > maxSq) {
                    continue;
                }
                Integer color = colors.get(h.block());
                if (color == null) {
                    continue;
                }
                String label = null;
                if (labels.get()) {
                    label = h.block().method_9518().getString();
                    if (showDistance.get()) {
                        label = label + "  " + (int) Math.sqrt(d) + "m";
                    }
                }
                all.add(new Target(new class_238(p).method_1011(0.02), accent ? Theme.accent() : color, label, d));
            }
        }
        all.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
        int limit = Math.min(all.size(), maxBoxes.asInt());
        for (int i = 0; i < limit; i++) {
            out.add(all.get(i));
        }
    }
}
