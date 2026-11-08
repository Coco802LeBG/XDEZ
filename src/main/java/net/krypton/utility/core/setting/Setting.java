package net.krypton.utility.core.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * A typed, serialisable module option. Settings drive both the module logic and the ClickGUI widgets.
 * Every setting belongs to a named group (used for section headers), can be shown conditionally, can be reset to the
 * value it had at construction time and can carry a server-visibility warning.
 */
public abstract class Setting<T> {
    public final String key;
    public final String name;
    protected T value;
    protected boolean hidden;
    private String group = "";
    private BooleanSupplier visibleWhen = () -> true;
    private JsonElement defaultJson;
    private String riskNote;

    protected Setting(String key, String name, T def) {
        this.key = key;
        this.name = name;
        this.value = def;
    }

    public T get() {
        return value;
    }

    /** Hidden settings are persisted but never shown in the ClickGUI (e.g. HUD widget positions). */
    public boolean hidden() {
        return hidden;
    }

    /** Whether the ClickGUI should currently list this setting. */
    public boolean visible() {
        return !hidden && visibleWhen.getAsBoolean();
    }

    public String group() {
        return group;
    }

    /** Non-null when changing this setting can make the server see something different (shown as a warning). */
    public String riskNote() {
        return riskNote;
    }

    public void setRiskNote(String note) {
        this.riskNote = note;
    }

    /** Called by {@code Module#add}; records the section, the visibility rule and the default value. */
    public void assign(String group, BooleanSupplier visibleWhen) {
        this.group = group;
        this.visibleWhen = visibleWhen;
        this.defaultJson = toJson();
    }

    /** Restores the value the setting had when it was registered. */
    public void reset() {
        if (defaultJson != null) {
            fromJson(defaultJson.deepCopy());
        }
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
