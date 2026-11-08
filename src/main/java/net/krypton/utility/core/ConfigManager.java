package net.krypton.utility.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.krypton.utility.SafeLog;
import net.krypton.utility.block.PresetManager;
import net.krypton.utility.core.setting.Setting;
import net.krypton.utility.ui.Theme;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local-only persistence in {@code <game dir>/config/krypton-utility.json}: module states, every setting, block
 * selections with their colours, user presets and the accent colour. Nothing is ever sent anywhere.
 * Key bindings are stored by Minecraft itself in options.txt.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA = 2;
    private static final String FILE_NAME = "krypton-utility.json";

    /** Serialised factory state; used to avoid re-creating the file when nothing differs from the defaults. */
    private static String defaultSnapshot = "";

    private ConfigManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** Must be called once after the modules are created and before {@link #load()}. */
    public static void captureDefaults() {
        defaultSnapshot = serialise();
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.has("accent")) {
                Theme.setAccent(root.get("accent").getAsInt());
            }
            PresetManager.fromJson(root.get("presets"));
            JsonObject modules = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : ModuleManager.all()) {
                if (!modules.has(m.id())) {
                    continue;
                }
                JsonObject o = modules.getAsJsonObject(m.id());
                if (o.has("settings")) {
                    JsonObject s = o.getAsJsonObject("settings");
                    for (Setting<?> setting : m.settings()) {
                        JsonElement e = s.get(setting.key);
                        if (e != null) {
                            setting.fromJson(e);
                        }
                    }
                }
                if (m.persistEnabled() && o.has("enabled")) {
                    m.setEnabled(o.get("enabled").getAsBoolean());
                    m.toggleAnim = m.isEnabled() ? 1f : 0f;
                }
            }
        } catch (IOException | RuntimeException e) {
            SafeLog.warn("The config file could not be read; defaults are used", e);
        }
    }

    private static String serialise() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA);
        root.addProperty("accent", Theme.accentIndex());
        root.add("presets", PresetManager.toJson());
        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            if (m.persistEnabled()) {
                o.addProperty("enabled", m.isEnabled());
            }
            JsonObject s = new JsonObject();
            for (Setting<?> setting : m.settings()) {
                s.add(setting.key, setting.toJson());
            }
            o.add("settings", s);
            modules.add(m.id(), o);
        }
        root.add("modules", modules);
        return GSON.toJson(root);
    }

    public static void save() {
        String json = serialise();
        Path path = file();
        if (!Files.exists(path) && json.equals(defaultSnapshot)) {
            return; // nothing to remember: do not create a file
        }
        Path temp = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.writeString(temp, json);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            SafeLog.warn("The config file could not be written", e);
        }
    }

    /** Every module and setting back to its default; user presets and block selections are reset too. */
    public static void resetSettings() {
        ModuleManager.resetAll();
        Theme.setAccent(0);
        save();
    }

    /** Resets everything, forgets all user presets and deletes the config file from disk. */
    public static void eraseAll() {
        ModuleManager.resetAll();
        Theme.setAccent(0);
        PresetManager.clear();
        Path path = file();
        try {
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.resolveSibling(FILE_NAME + ".tmp"));
        } catch (IOException e) {
            SafeLog.warn("The config file could not be deleted", e);
        }
    }
}
