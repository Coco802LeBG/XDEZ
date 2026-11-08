package net.krypton.utility.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An opaque RGB colour chosen from a fixed palette (stored as 0xFFRRGGBB). */
public final class ColorSetting extends Setting<Integer> {
    public static final int[] PALETTE = {
            0xFFF43F5E, 0xFFF97316, 0xFFF59E0B, 0xFFFACC15, 0xFF84CC16, 0xFF22C55E, 0xFF34D399, 0xFF22D3EE,
            0xFF38BDF8, 0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFEC4899, 0xFFF8FAFC, 0xFF94A3B8
    };

    public ColorSetting(String key, String name, int rgb) {
        super(key, name, rgb | 0xFF000000);
    }

    public int argb() {
        return value;
    }

    public void set(int rgb) {
        value = rgb | 0xFF000000;
    }

    /** Index of the palette entry equal to the current value, or -1 when the colour is custom. */
    public int paletteIndex() {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == value) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            value = e.getAsInt() | 0xFF000000;
        }
    }
}
