package net.krypton.utility.ui;

/** Central palette. Accent pairs are (start, end) gradient colours. */
public final class Theme {
    public static final String[] ACCENT_NAMES = {"Nebula", "Glacier", "Mint", "Sunset", "Amber", "Aurora"};
    private static final int[][] ACCENTS = {
            {0xFF8B5CF6, 0xFFEC4899},
            {0xFF22D3EE, 0xFF3B82F6},
            {0xFF34D399, 0xFF22D3EE},
            {0xFFFB7185, 0xFFF59E0B},
            {0xFFFACC15, 0xFFF97316},
            {0xFF60A5FA, 0xFFA78BFA}
    };

    public static final int WINDOW = 0xF2101018;
    public static final int SIDEBAR = 0xFF14141D;
    public static final int CARD = 0xFF191924;
    public static final int CARD_HOVER = 0xFF1F1F2C;
    public static final int TRACK = 0xFF2A2A3A;
    public static final int TEXT = 0xFFEDEDF5;
    public static final int MUTED = 0xFF8C8CA3;
    public static final int HUD_BG = 0xB8101018;

    private static int accent = 0;

    private Theme() {
    }

    public static int accentIndex() {
        return accent;
    }

    public static void setAccent(int index) {
        accent = Math.floorMod(index, ACCENTS.length);
    }

    public static int accentCount() {
        return ACCENTS.length;
    }

    public static int accent() {
        return ACCENTS[accent][0];
    }

    public static int accent2() {
        return ACCENTS[accent][1];
    }

    public static int accentOf(int index, boolean second) {
        return ACCENTS[Math.floorMod(index, ACCENTS.length)][second ? 1 : 0];
    }
}
