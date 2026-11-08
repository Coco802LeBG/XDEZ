package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.ModeSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Customisable HUD: info card + active-module list. Widget positions are stored as screen fractions. Client-only. */
public final class HudModule extends Module {
    // Info card
    public final BoolSetting watermark;
    public final BoolSetting coords;
    public final BoolSetting converted;
    public final BoolSetting facing;
    public final BoolSetting biome;
    public final BoolSetting dimension;
    public final BoolSetting light;
    public final BoolSetting fps;
    public final BoolSetting speed;
    public final BoolSetting clock;
    public final NumberSetting decimals;
    // Module list
    public final BoolSetting moduleList;
    public final ModeSetting listSort;
    public final BoolSetting listAccentBar;
    // Appearance
    public final NumberSetting opacity;
    public final NumberSetting cornerRadius;
    public final BoolSetting accentBar;
    public final BoolSetting textShadow;
    // Hidden positions (fractions of the screen)
    public final NumberSetting infoX;
    public final NumberSetting infoY;
    public final NumberSetting listX;
    public final NumberSetting listY;

    private double prevX;
    private double prevZ;
    private double blocksPerSecond;

    public HudModule() {
        super("hud", "HUD", "Coordinates and status widgets. Drag them in the HUD editor.", Category.INTERFACE, true);

        group("Info card");
        watermark = add(new BoolSetting("watermark", "Title row", true));
        coords = add(new BoolSetting("coords", "Coordinates", true));
        converted = add(new BoolSetting("converted", "Nether / Overworld coordinates", true), coords::get);
        facing = add(new BoolSetting("facing", "Facing direction", true));
        biome = add(new BoolSetting("biome", "Biome", true));
        dimension = add(new BoolSetting("dimension", "Dimension", false));
        light = add(new BoolSetting("light", "Light level", false));
        fps = add(new BoolSetting("fps", "FPS", true));
        speed = add(new BoolSetting("speed", "Speed (blocks/s)", true));
        clock = add(new BoolSetting("clock", "Local clock", false));
        decimals = add(new NumberSetting("decimals", "Coordinate decimals", 1, 0, 3, 1), coords::get);

        group("Module list");
        moduleList = add(new BoolSetting("module_list", "Show active modules", true));
        listSort = add(new ModeSetting("list_sort", "Sort by", "Length", "Length", "Alphabetical"), moduleList::get);
        listAccentBar = add(new BoolSetting("list_accent_bar", "Accent bar", true), moduleList::get);

        group("Appearance");
        opacity = add(new NumberSetting("opacity", "Background opacity", 0.72, 0.1, 1.0, 0.02));
        cornerRadius = add(new NumberSetting("corner_radius", "Corner radius", 6, 0, 10, 1));
        accentBar = add(new BoolSetting("accent_bar", "Accent bar on the info card", true));
        textShadow = add(new BoolSetting("text_shadow", "Text shadow", false));

        infoX = add(NumberSetting.hiddenFraction("info_x", 0.0));
        infoY = add(NumberSetting.hiddenFraction("info_y", 0.0));
        listX = add(NumberSetting.hiddenFraction("list_x", 1.0));
        listY = add(NumberSetting.hiddenFraction("list_y", 0.0));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        double dx = mc.player.getX() - prevX;
        double dz = mc.player.getZ() - prevZ;
        blocksPerSecond = Math.sqrt(dx * dx + dz * dz) * 20.0;
        prevX = mc.player.getX();
        prevZ = mc.player.getZ();
    }

    public double blocksPerSecond() {
        return blocksPerSecond;
    }
}
