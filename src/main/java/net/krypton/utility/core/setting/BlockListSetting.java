package net.krypton.utility.core.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.krypton.utility.block.BlockCatalog;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A per-module list of blocks chosen by the user, each with an optional custom colour.
 * Blocks are stored by registry id ("minecraft:diamond_ore") so the config survives mod/version changes;
 * {@link #resolved()} turns them into a Block-to-colour lookup that is cached until the list changes.
 */
public final class BlockListSetting extends Setting<Set<String>> {
    private final LinkedHashSet<String> ids = new LinkedHashSet<>();
    private final Map<String, Integer> colors = new HashMap<>();
    private int version;
    private int resolvedVersion = -1;
    private Map<Block, Integer> resolved = Map.of();

    public BlockListSetting(String key, String name, Collection<String> defaults) {
        super(key, name, null);
        this.value = Collections.unmodifiableSet(ids);
        ids.addAll(defaults);
    }

    public int size() {
        return ids.size();
    }

    public boolean contains(String id) {
        return ids.contains(id);
    }

    /** Incremented on every change; lets consumers invalidate their caches cheaply. */
    public int version() {
        return version;
    }

    public void toggle(String id) {
        if (!ids.remove(id)) {
            ids.add(id);
        }
        version++;
    }

    public void add(String id) {
        if (ids.add(id)) {
            version++;
        }
    }

    public void remove(String id) {
        if (ids.remove(id)) {
            version++;
        }
    }

    public void addAll(Collection<String> items) {
        if (ids.addAll(items)) {
            version++;
        }
    }

    public void removeAll(Collection<String> items) {
        if (ids.removeAll(items)) {
            version++;
        }
    }

    public void clear() {
        if (!ids.isEmpty()) {
            ids.clear();
            version++;
        }
    }

    public void replaceWith(Collection<String> items) {
        ids.clear();
        ids.addAll(items);
        version++;
    }

    /** Selects every id of {@code universe} that is currently unselected and deselects the others. */
    public void invert(Collection<String> universe) {
        for (String id : universe) {
            if (!ids.remove(id)) {
                ids.add(id);
            }
        }
        version++;
    }

    public int color(String id) {
        Integer custom = colors.get(id);
        return custom != null ? custom : BlockCatalog.defaultColor(id);
    }

    public boolean hasCustomColor(String id) {
        return colors.containsKey(id);
    }

    public void setColor(String id, int rgb) {
        colors.put(id, rgb | 0xFF000000);
        version++;
    }

    public void resetColor(String id) {
        if (colors.remove(id) != null) {
            version++;
        }
    }

    public Map<String, Integer> customColors() {
        return Collections.unmodifiableMap(colors);
    }

    /** Block-to-ARGB lookup of every selected block that exists in this game version. */
    public Map<Block, Integer> resolved() {
        if (resolvedVersion != version) {
            Map<Block, Integer> map = new HashMap<>();
            for (String id : ids) {
                Block block = BlockCatalog.block(id);
                if (block != null) {
                    map.put(block, color(id));
                }
            }
            resolved = map;
            resolvedVersion = version;
        }
        return resolved;
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        JsonArray blocks = new JsonArray();
        for (String id : ids) {
            blocks.add(id);
        }
        o.add("blocks", blocks);
        JsonObject c = new JsonObject();
        for (Map.Entry<String, Integer> e : colors.entrySet()) {
            c.addProperty(e.getKey(), e.getValue());
        }
        o.add("colors", c);
        return o;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null) {
            return;
        }
        JsonArray blocks = null;
        JsonObject colorObject = null;
        if (element.isJsonArray()) {
            blocks = element.getAsJsonArray();
        } else if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            if (o.has("blocks") && o.get("blocks").isJsonArray()) {
                blocks = o.getAsJsonArray("blocks");
            }
            if (o.has("colors") && o.get("colors").isJsonObject()) {
                colorObject = o.getAsJsonObject("colors");
            }
        }
        if (blocks == null) {
            return;
        }
        ids.clear();
        colors.clear();
        for (JsonElement e : blocks) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                ids.add(e.getAsString());
            }
        }
        if (colorObject != null) {
            for (Map.Entry<String, JsonElement> e : colorObject.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    colors.put(e.getKey(), e.getValue().getAsInt() | 0xFF000000);
                }
            }
        }
        version++;
    }
}
