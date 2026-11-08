package net.krypton.utility.render;

import java.util.List;

/** Implemented by modules that contribute boxes to the shared highlight renderer. */
public interface HighlightSource {
    boolean isEnabled();

    /** One of {@link EspRenderer#STYLES}. */
    String style();

    /** 0..255 */
    int fillAlpha();

    boolean labels();

    void collect(float partialTick, List<Target> out);
}
