package net.krypton.utility.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Index of every block in the game (read once from the block registry on first use): id, display name, icon stack,
 * fast text search, default colours and the built-in presets.
 */
public final class BlockCatalog {
    public record Entry(String id, Block block, String name, String lowerName, ItemStack icon) {
        /** Registry path without the namespace, e.g. "deepslate_diamond_ore". */
        public String path() {
            int i = id.indexOf(':');
            return i < 0 ? id : id.substring(i + 1);
        }
    }

    /** A read-only preset defined by a rule over registry paths, so it also covers modded/new blocks. */
    public record BuiltinPreset(String name, Predicate<String> matcher) {
    }

    private static List<Entry> entries = List.of();
    private static Map<String, Entry> byId = Map.of();
    private static boolean loaded;

    private BlockCatalog() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        List<Entry> list = new ArrayList<>();
        Map<String, Entry> map = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().isAir()) {
                continue; // air variants cannot be targeted by any module
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String name = block.getName().getString();
            Entry entry = new Entry(id, block, name, name.toLowerCase(Locale.ROOT), new ItemStack(block));
            list.add(entry);
            map.put(id, entry);
        }
        list.sort((a, b) -> {
            int c = a.lowerName().compareTo(b.lowerName());
            return c != 0 ? c : a.id().compareTo(b.id());
        });
        entries = Collections.unmodifiableList(list);
        byId = map;
        loaded = true;
    }

    public static List<Entry> all() {
        ensureLoaded();
        return entries;
    }

    public static Entry entry(String id) {
        ensureLoaded();
        return byId.get(id);
    }

    public static Block block(String id) {
        Entry e = entry(id);
        return e == null ? null : e.block();
    }

    /** Every entry whose name or id contains all whitespace-separated words of the query (case-insensitive). */
    public static List<Entry> search(String query) {
        ensureLoaded();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return entries;
        }
        String[] words = q.split("\\s+");
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            boolean all = true;
            for (String w : words) {
                if (!e.lowerName().contains(w) && !e.id().contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                out.add(e);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ colours

    private static final String[][] KEYWORD_COLORS = {
            {"diamond", "22D3EE"}, {"emerald", "22C55E"}, {"gold", "FACC15"}, {"iron", "E5C9B0"},
            {"copper", "FB923C"}, {"redstone", "EF4444"}, {"lapis", "3B82F6"}, {"coal", "9CA3AF"},
            {"quartz", "F3F4F6"}, {"debris", "B45309"}, {"netherite", "7C5A4A"}, {"spawner", "A855F7"},
            {"vault", "A855F7"}, {"shulker", "EC4899"}, {"ender_chest", "A855F7"}, {"chest", "F59E0B"},
            {"barrel", "D97706"}, {"amethyst", "C084FC"}, {"beacon", "67E8F9"}
    };

    /** Colour used for a block that has no custom colour: known keywords first, otherwise a stable hash-based hue. */
    public static int defaultColor(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        for (String[] kw : KEYWORD_COLORS) {
            if (path.contains(kw[0])) {
                return 0xFF000000 | Integer.parseInt(kw[1], 16);
            }
        }
        float hue = (Math.floorMod(id.hashCode() * 31, 360)) / 360f;
        return 0xFF000000 | hsvToRgb(hue, 0.6f, 1.0f);
    }

    public static int hsvToRgb(float h, float s, float v) {
        float r;
        float g;
        float b;
        float hh = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) hh;
        float f = hh - sector;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    // ------------------------------------------------------------------ built-in presets

    private static boolean endsWithAny(String s, String... suffixes) {
        for (String x : suffixes) {
            if (s.endsWith(x)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyOf(String s, String... values) {
        for (String x : values) {
            if (s.equals(x)) {
                return true;
            }
        }
        return false;
    }

    private static final List<BuiltinPreset> BUILTIN = List.of(
            new BuiltinPreset("Ores", p -> p.endsWith("_ore") || p.equals("ancient_debris")),
            new BuiltinPreset("Overworld ores", p -> p.endsWith("_ore") && !p.startsWith("nether_")),
            new BuiltinPreset("Nether ores", p -> p.startsWith("nether_") && p.endsWith("_ore") || p.equals("ancient_debris")),
            new BuiltinPreset("Valuable blocks", p -> anyOf(p, "diamond_block", "emerald_block", "gold_block", "netherite_block",
                    "beacon", "ancient_debris", "lapis_block", "raw_gold_block", "raw_iron_block", "raw_copper_block")),
            new BuiltinPreset("Containers", p -> anyOf(p, "chest", "trapped_chest", "ender_chest", "barrel", "hopper",
                    "dispenser", "dropper", "furnace", "blast_furnace", "smoker", "brewing_stand", "crafter",
                    "chiseled_bookshelf", "decorated_pot") || p.endsWith("shulker_box")),
            new BuiltinPreset("Spawners & vaults", p -> anyOf(p, "spawner", "trial_spawner", "vault")),
            new BuiltinPreset("Building blocks", p -> anyOf(p, "stone", "cobblestone", "smooth_stone", "glass", "obsidian",
                    "bricks", "sandstone", "quartz_block")
                    || endsWithAny(p, "_planks", "_bricks", "_stairs", "_slab", "_wall", "_concrete", "_terracotta",
                    "_wool", "_log", "_wood", "_glass")),
            new BuiltinPreset("Redstone", p -> anyOf(p, "redstone_block", "redstone_lamp", "repeater", "comparator", "observer",
                    "piston", "sticky_piston", "lever", "target", "tripwire_hook", "daylight_detector", "note_block",
                    "tnt", "slime_block", "honey_block", "hopper", "dispenser", "dropper", "redstone_wire", "redstone_torch")
                    || endsWithAny(p, "_button", "_pressure_plate", "_rail")),
            new BuiltinPreset("Liquids & hazards", p -> anyOf(p, "water", "lava", "fire", "soul_fire", "magma_block",
                    "cactus", "sweet_berry_bush", "wither_rose", "cobweb", "powder_snow", "campfire", "soul_campfire")),
            new BuiltinPreset("Natural terrain", p -> anyOf(p, "dirt", "grass_block", "sand", "red_sand", "gravel", "clay",
                    "mud", "snow_block", "ice", "packed_ice", "blue_ice", "netherrack", "end_stone", "deepslate", "tuff",
                    "granite", "diorite", "andesite", "calcite", "dripstone_block", "moss_block", "podzol", "mycelium"))
    );

    public static List<BuiltinPreset> builtinPresets() {
        return BUILTIN;
    }

    /** Registry ids matching a built-in preset in the current game. */
    public static List<String> idsOf(BuiltinPreset preset) {
        List<String> out = new ArrayList<>();
        for (Entry e : all()) {
            if (preset.matcher().test(e.path())) {
                out.add(e.id());
            }
        }
        return out;
    }
}
