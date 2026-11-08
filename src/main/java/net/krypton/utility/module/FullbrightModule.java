package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * Forces a very high gamma for the lightmap. The saved option value is never modified; the renderer is simply fed a
 * different number, optionally faded in and out. Purely visual.
 */
public final class FullbrightModule extends Module {
    public final NumberSetting gamma;
    public final BoolSetting smooth;
    public final NumberSetting fadeSpeed;

    private static FullbrightModule instance;
    /** Gamma currently fed to the renderer; 0 means "no override". */
    private static double current;
    private static boolean reading;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Maximum brightness everywhere, caves included.", Category.VISUALS, false);
        instance = this;
        group("Brightness");
        gamma = add(new NumberSetting("gamma", "Gamma", 12.0, 2.0, 16.0, 0.5));
        smooth = add(new BoolSetting("smooth", "Smooth transition", true));
        fadeSpeed = add(new NumberSetting("fade_speed", "Transition speed", 0.15, 0.03, 0.6, 0.01), smooth::get);
    }

    public static boolean isActive() {
        return current > 0.0 && !reading;
    }

    public static double gamma() {
        return current;
    }

    /** Called every client tick (also while the module is off, so that the fade-out can finish). */
    public static void tickFade(Minecraft mc) {
        FullbrightModule m = instance;
        if (m == null || mc.options == null || (!m.isEnabled() && current <= 0.0)) {
            return;
        }
        double real;
        reading = true;
        try {
            real = mc.options.gamma().get();
        } finally {
            reading = false;
        }
        if (current <= 0.0) {
            current = real;
        }
        double target = m.isEnabled() ? m.gamma.asDouble() : real;
        current = m.smooth.get() ? current + (target - current) * m.fadeSpeed.asDouble() : target;
        if (!m.isEnabled() && Math.abs(current - real) < 0.02) {
            current = 0.0;
        }
    }
}
