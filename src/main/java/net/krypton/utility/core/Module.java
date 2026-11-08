package net.krypton.utility.core;

import net.krypton.utility.core.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Base class of every feature. A module owns its settings and reacts to enable/disable/tick. */
public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean defaultEnabled;
    private final List<Setting<?>> settings = new ArrayList<>();
    private String currentGroup = "";
    private String serverNotice;
    private boolean enabled;

    // ClickGUI animation state
    public float toggleAnim;
    public float expandAnim;
    public boolean expanded;

    protected Module(String id, String name, String description, Category category, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
    }

    /** Subsequent {@link #add} calls are filed under this section header in the ClickGUI. */
    protected void group(String name) {
        this.currentGroup = name;
    }

    protected <S extends Setting<?>> S add(S setting) {
        return add(setting, () -> true);
    }

    protected <S extends Setting<?>> S add(S setting, BooleanSupplier visibleWhen) {
        setting.assign(currentGroup, visibleWhen);
        settings.add(setting);
        return setting;
    }

    /** Marks a setting as able to change what the server sees; the note is shown in the ClickGUI. */
    protected static <S extends Setting<?>> S risky(S setting, String note) {
        setting.setRiskNote(note);
        return setting;
    }

    /**
     * Declares that this module can have an effect visible to the server. The text is displayed as a warning banner in
     * the ClickGUI. Modules that never call this are labelled "client-only".
     */
    protected void serverNotice(String text) {
        this.serverNotice = text;
    }

    /** Warning text for modules that can affect what the server sees, or null for purely client-side modules. */
    public String serverNotice() {
        return serverNotice;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    /** Settings that should currently be listed in the ClickGUI (honours hidden flag and visibility conditions). */
    public List<Setting<?>> visibleSettings() {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : settings) {
            if (s.visible()) {
                out.add(s);
            }
        }
        return out;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Whether the enabled state is written to the config file. */
    public boolean persistEnabled() {
        return true;
    }

    protected boolean canEnable() {
        return true;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean value) {
        if (value == enabled) {
            return;
        }
        if (value && !canEnable()) {
            return;
        }
        enabled = value;
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** Restores every setting and the enabled state to their defaults. */
    public final void resetToDefaults() {
        for (Setting<?> s : settings) {
            s.reset();
        }
        setEnabled(defaultEnabled);
        toggleAnim = enabled ? 1f : 0f;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public void onTick(Minecraft mc) {
    }

    /** Called when the player leaves a world / server. */
    public void onDisconnect() {
    }
}
