package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.render.Projector;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BlockListSetting;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.krypton.utility.render.EspRenderer;
import net.krypton.utility.render.HighlightSource;
import net.krypton.utility.render.Target;
import net.krypton.utility.ui.Theme;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    private record Hit(long pos, Block block) {
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
    public void onTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        tickCounter++;
        if (blocks.version() != seenVersion) {
            seenVersion = blocks.version();
            cache.clear();
        }
        Map<Block, Integer> targets = blocks.resolved();
        if (targets.isEmpty()) {
            cache.clear();
            return;
        }
        int r = radius.asInt();
        Vec3 center = FreecamModule.viewCenter(mc);
        int pcx = ((int) Math.floor(center.x)) >> 4;
        int pcz = ((int) Math.floor(center.z)) >> 4;

        cache.keySet().removeIf(k -> {
            int cx = (int) (k >> 32);
            int cz = (int) (long) k;
            return Math.abs(cx - pcx) > r + 1 || Math.abs(cz - pcz) > r + 1;
        });

        int budget = chunksPerTick.asInt();
        int interval = scanInterval.asInt();
        int py = (int) Math.floor(center.y);
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
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    cache.put(key(cx, cz), scan(level, chunk, cx, cz, py, targets));
                    budget--;
                }
            }
        }
    }

    private CachedChunk scan(ClientLevel level, LevelChunk chunk, int cx, int cz, int py, Map<Block, Integer> targets) {
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height() - 1;
        int y0 = Math.max(minY, py - rangeDown.asInt());
        int y1 = Math.min(maxY, py + rangeUp.asInt());
        List<Hit> hits = new ArrayList<>();
        if (y0 > y1) {
            return new CachedChunk(hits, tickCounter);
        }
        boolean exposed = exposedOnly.get();
        LevelChunkSection[] sections = chunk.getSections();
        int firstSection = chunk.getSectionIndex(y0);
        int lastSection = chunk.getSectionIndex(y1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (int si = firstSection; si <= lastSection && si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            int sectionMinY = minY + si * 16;
            int ly0 = Math.max(y0, sectionMinY) - sectionMinY;
            int ly1 = Math.min(y1, sectionMinY + 15) - sectionMinY;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int ly = ly0; ly <= ly1; ly++) {
                        BlockState state = section.getBlockState(x, ly, z);
                        Block block = state.getBlock();
                        if (!targets.containsKey(block)) {
                            continue;
                        }
                        pos.set((cx << 4) + x, sectionMinY + ly, (cz << 4) + z);
                        if (!exposed || isExposed(level, pos, neighbour)) {
                            hits.add(new Hit(pos.asLong(), block));
                        }
                    }
                }
            }
        }
        return new CachedChunk(hits, tickCounter);
    }

    private static boolean isExposed(ClientLevel level, BlockPos pos, BlockPos.MutableBlockPos tmp) {
        for (Direction d : Direction.values()) {
            tmp.set(pos).move(d);
            if (!level.getBlockState(tmp).isSolidRender()) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 cam = Projector.camera();
        double maxSq = maxDistance.asDouble() * maxDistance.asDouble();
        Map<Block, Integer> colors = blocks.resolved();
        boolean accent = colorMode.is("Accent");
        List<Target> all = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (CachedChunk c : cache.values()) {
            for (Hit h : c.hits()) {
                p.set(h.pos());
                double d = Vec3.atCenterOf(p).distanceToSqr(cam);
                if (d > maxSq) {
                    continue;
                }
                Integer color = colors.get(h.block());
                if (color == null) {
                    continue;
                }
                String label = null;
                if (labels.get()) {
                    label = h.block().getName().getString();
                    if (showDistance.get()) {
                        label = label + "  " + (int) Math.sqrt(d) + "m";
                    }
                }
                all.add(new Target(new AABB(p).deflate(0.02), accent ? Theme.accent() : color, label, d));
            }
        }
        all.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
        int limit = Math.min(all.size(), maxBoxes.asInt());
        for (int i = 0; i < limit; i++) {
            out.add(all.get(i));
        }
    }
}
