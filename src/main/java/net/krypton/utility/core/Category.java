package net.krypton.utility.core;

public enum Category {
    VISUALS("Visuals", "Camera, lighting & overlays", 0xFF8B5CF6),
    WORLD("World", "Environment analysis", 0xFF34D399),
    INVENTORY("Inventory", "Storage automation", 0xFFFBBF24),
    INTERFACE("Interface", "HUD & layout", 0xFF60A5FA);

    public final String label;
    public final String subtitle;
    public final int color;

    Category(String label, String subtitle, int color) {
        this.label = label;
        this.subtitle = subtitle;
        this.color = color;
    }
}
