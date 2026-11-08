package net.krypton.utility.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The user's own block presets. They are global (shared by every module) and stored in the config file. */
public final class PresetManager {
    public static final class Preset {
        private String name;
        private final LinkedHashSet<String> ids = new LinkedHashSet<>();
        private final Map<String, Integer> colors = new HashMap<>();

        private Preset(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public java.util.Set<String> ids() {
            return Collections.unmodifiableSet(ids);
        }

        public Map<String, Integer> colors() {
            return Collections.unmodifiableMap(colors);
        }
    }

    private static final List<Preset> USER = new ArrayList<>();

    private PresetManager() {
    }

    public static List<Preset> list() {
        return Collections.unmodifiableList(USER);
    }

    /** Forgets every user preset. */
    public static void clear() {
        USER.clear();
    }

    public static Preset find(String name) {
        for (Preset p : USER) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.trim().length() <= 32;
    }

    /** Creates a preset; returns null when the name is invalid or already used (case-insensitive). */
    public static Preset create(String name, Collection<String> ids, Map<String, Integer> colors) {
        if (!isValidName(name) || find(name.trim()) != null) {
            return null;
        }
        Preset p = new Preset(name.trim());
        p.ids.addAll(ids);
        for (Map.Entry<String, Integer> e : colors.entrySet()) {
            if (p.ids.contains(e.getKey())) {
                p.colors.put(e.getKey(), e.getValue());
            }
        }
        USER.add(p);
        return p;
    }

    public static boolean rename(Preset preset, String newName) {
        if (!isValidName(newName)) {
            return false;
        }
        Preset other = find(newName.trim());
        if (other != null && other != preset) {
            return false;
        }
        preset.name = newName.trim();
        return true;
    }

    public static void delete(Preset preset) {
        USER.remove(preset);
    }

    /** Suggests a free name such as "My preset 2". */
    public static String suggestName() {
        String base = "My preset";
        if (find(base) == null) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + " " + i;
            if (find(candidate.toLowerCase(Locale.ROOT)) == null) {
                return candidate;
            }
        }
        return base;
    }

    // ------------------------------------------------------------------ persistence

    public static JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Preset p : USER) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name);
            JsonArray ids = new JsonArray();
            for (String id : p.ids) {
                ids.add(id);
            }
            o.add("blocks", ids);
            JsonObject colors = new JsonObject();
            for (Map.Entry<String, Integer> e : p.colors.entrySet()) {
                colors.addProperty(e.getKey(), e.getValue());
            }
            o.add("colors", colors);
            array.add(o);
        }
        return array;
    }

    public static void fromJson(JsonElement element) {
        USER.clear();
        if (element == null || !element.isJsonArray()) {
            return;
        }
        for (JsonElement e : element.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            if (!o.has("name") || !o.get("name").isJsonPrimitive()) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            if (o.has("blocks") && o.get("blocks").isJsonArray()) {
                for (JsonElement b : o.getAsJsonArray("blocks")) {
                    if (b.isJsonPrimitive()) {
                        ids.add(b.getAsString());
                    }
                }
            }
            Map<String, Integer> colors = new HashMap<>();
            if (o.has("colors") && o.get("colors").isJsonObject()) {
                for (Map.Entry<String, JsonElement> c : o.getAsJsonObject("colors").entrySet()) {
                    if (c.getValue().isJsonPrimitive() && c.getValue().getAsJsonPrimitive().isNumber()) {
                        colors.put(c.getKey(), c.getValue().getAsInt());
                    }
                }
            }
            create(o.get("name").getAsString(), ids, colors);
        }
    }
}
