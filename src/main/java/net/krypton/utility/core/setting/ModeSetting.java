package net.krypton.utility.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ModeSetting extends Setting<String> {
    public final String[] modes;
    private int index;

    public ModeSetting(String key, String name, String def, String... modes) {
        super(key, name, def);
        this.modes = modes;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(def)) {
                index = i;
            }
        }
        this.value = modes[index];
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public void cycle(int direction) {
        index = Math.floorMod(index + direction, modes.length);
        value = modes[index];
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(s)) {
                    index = i;
                    value = s;
                }
            }
        }
    }
}
