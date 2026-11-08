package net.krypton.utility.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
    public float anim; // ClickGUI switch animation, 0..1

    public BoolSetting(String key, String name, boolean def) {
        super(key, name, def);
        this.anim = def ? 1f : 0f;
    }

    public void toggle() {
        value = !value;
    }

    public void set(boolean v) {
        value = v;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            value = e.getAsBoolean();
            anim = value ? 1f : 0f;
        }
    }
}
