package net.krypton.utility.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class NumberSetting extends Setting<Double> {
    public final double min;
    public final double max;
    public final double step;

    public NumberSetting(String key, String name, double def, double min, double max, double step) {
        super(key, name, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    private NumberSetting(String key, double def) {
        super(key, key, def);
        this.min = 0;
        this.max = 1;
        this.step = 0;
        this.hidden = true;
    }

    /** A persisted 0..1 value that is not exposed in the ClickGUI. */
    public static NumberSetting hiddenFraction(String key, double def) {
        return new NumberSetting(key, def);
    }

    public double asDouble() {
        return value;
    }

    public float asFloat() {
        return value.floatValue();
    }

    public int asInt() {
        return (int) Math.round(value);
    }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) {
            v = Math.round((v - min) / step) * step + min;
        }
        value = Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    /** Position of the current value inside [min, max], 0..1. */
    public float fraction() {
        return (float) ((value - min) / (max - min));
    }

    public String display() {
        if (step >= 1.0) {
            return Integer.toString(asInt());
        }
        return String.format(Locale.ROOT, step < 0.1 ? "%.2f" : "%.1f", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            if (hidden) {
                value = Math.max(0.0, Math.min(1.0, e.getAsDouble()));
            } else {
                set(e.getAsDouble());
            }
        }
    }
}
