package net.krypton.utility.hud;

import net.krypton.utility.core.Module;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.module.FreecamModule;
import net.krypton.utility.module.HudModule;
import net.krypton.utility.render.EspRenderer;
import net.krypton.utility.ui.Draw;
import net.krypton.utility.ui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws the ESP overlay, the info card, the module list and the Freecam badge. Nothing here leaves the client. */
public final class HudRenderer {
    public static final int MARGIN = 4;

    /** Last drawn rectangles {x, y, w, h}; used by the HUD editor for hit-testing. */
    public static int[] infoRect = {0, 0, 0, 0};
    public static int[] listRect = {0, 0, 0, 0};

    private HudRenderer() {
    }

    public static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        float pt = tracker.getGameTimeDeltaPartialTick(false);
        Draw.alphaMul = 1f;
        EspRenderer.render2D(g, pt);
        drawFreecamBadge(g);
        HudModule hud = ModuleManager.get(HudModule.class);
        if (hud.isEnabled()) {
            drawInfo(g, hud, false);
            if (hud.moduleList.get()) {
                drawList(g, hud, false);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void text(GuiGraphics g, HudModule hud, String s, int x, int y, int color) {
        if (hud.textShadow.get()) {
            Draw.textShadow(g, s, x, y, color);
        } else {
            Draw.text(g, s, x, y, color);
        }
    }

    private static int background(HudModule hud) {
        return Draw.withAlpha(0x101018, hud.opacity.asFloat());
    }

    private static String[] row(String label, String value) {
        return new String[]{label, value};
    }

    private static String facing(float yawDegrees) {
        float yaw = Mth.wrapDegrees(yawDegrees);
        if (yaw >= -45 && yaw < 45) {
            return "South (+Z)";
        }
        if (yaw >= 45 && yaw < 135) {
            return "West (-X)";
        }
        if (yaw >= -135 && yaw < -45) {
            return "East (+X)";
        }
        return "North (-Z)";
    }

    private static String biomeName(Minecraft mc) {
        try {
            Holder<?> holder = mc.level.getBiome(mc.player.blockPosition());
            String raw = holder.unwrapKey().map(Object::toString).orElse("unknown");
            int slash = raw.lastIndexOf('/');
            String name = (slash >= 0 ? raw.substring(slash + 1) : raw).replace("]", "").trim();
            if (name.startsWith("minecraft:")) {
                name = name.substring("minecraft:".length());
            }
            StringBuilder sb = new StringBuilder();
            for (String part : name.split("[_:]")) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
                }
            }
            return sb.toString().trim();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static String dimensionName(Minecraft mc) {
        if (mc.level.dimension() == Level.OVERWORLD) {
            return "Overworld";
        }
        if (mc.level.dimension() == Level.NETHER) {
            return "Nether";
        }
        if (mc.level.dimension() == Level.END) {
            return "The End";
        }
        return "Custom";
    }

    private static List<String[]> infoRows(HudModule hud, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        List<String[]> rows = new ArrayList<>();
        if (mc.player == null || mc.level == null) {
            if (preview) {
                rows.add(row("XYZ", "128.0 / 72.0 / -44.0"));
                rows.add(row("Nether", "16.0 / -5.5"));
                rows.add(row("Facing", "North (-Z)"));
                rows.add(row("Biome", "Plains"));
                rows.add(row("FPS", "144"));
                rows.add(row("Speed", "4.3 b/s"));
            }
            return rows;
        }
        String f = "%." + hud.decimals.asInt() + "f";
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        if (hud.coords.get()) {
            rows.add(row("XYZ", String.format(Locale.ROOT, f + " / " + f + " / " + f, x, y, z)));
            if (hud.converted.get()) {
                if (mc.level.dimension() == Level.OVERWORLD) {
                    rows.add(row("Nether", String.format(Locale.ROOT, f + " / " + f, x / 8.0, z / 8.0)));
                } else if (mc.level.dimension() == Level.NETHER) {
                    rows.add(row("Overworld", String.format(Locale.ROOT, f + " / " + f, x * 8.0, z * 8.0)));
                }
            }
        }
        if (FreecamModule.isActive()) {
            Vec3 c = FreecamModule.instance().cameraPosition();
            rows.add(row("Camera", String.format(Locale.ROOT, f + " / " + f + " / " + f, c.x, c.y, c.z)));
        }
        if (hud.facing.get()) {
            rows.add(row("Facing", facing(mc.player.getYRot())));
        }
        if (hud.biome.get()) {
            rows.add(row("Biome", biomeName(mc)));
        }
        if (hud.dimension.get()) {
            rows.add(row("Dimension", dimensionName(mc)));
        }
        if (hud.light.get()) {
            rows.add(row("Light", Integer.toString(mc.level.getMaxLocalRawBrightness(mc.player.blockPosition()))));
        }
        if (hud.fps.get()) {
            rows.add(row("FPS", Integer.toString(mc.getFps())));
        }
        if (hud.speed.get()) {
            rows.add(row("Speed", String.format(Locale.ROOT, "%.1f b/s", hud.blocksPerSecond())));
        }
        if (hud.clock.get()) {
            LocalTime now = LocalTime.now();
            rows.add(row("Time", String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute())));
        }
        return rows;
    }

    // ------------------------------------------------------------------ info card

    /** Draws the info card and returns its rectangle {x, y, w, h}. */
    public static int[] drawInfo(GuiGraphics g, HudModule hud, boolean preview) {
        List<String[]> rows = infoRows(hud, preview);
        boolean title = hud.watermark.get();
        if (rows.isEmpty() && !title) {
            infoRect = new int[]{0, 0, 0, 0};
            return infoRect;
        }
        int pad = 6;
        int line = Draw.lineHeight() + 3;
        int labelW = 0;
        int valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, Draw.width(r[0]));
            valueW = Math.max(valueW, Draw.width(r[1]));
        }
        int barW = hud.accentBar.get() ? 4 : 0;
        int titleW = title ? Draw.width("KRYPTON") + Draw.width("  UTILITY") : 0;
        int w = Math.max(pad * 2 + barW + labelW + 10 + valueW, pad * 2 + barW + titleW);
        int h = pad * 2 + rows.size() * line + (title ? line + 2 : 0) - 2;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int x = MARGIN + Math.round(hud.infoX.asFloat() * Math.max(0, sw - w - MARGIN * 2));
        int y = MARGIN + Math.round(hud.infoY.asFloat() * Math.max(0, sh - h - MARGIN * 2));
        int radius = hud.cornerRadius.asInt();

        Draw.shadow(g, x, y, w, h, radius, 4);
        Draw.round(g, x, y, w, h, radius, background(hud));
        if (hud.accentBar.get()) {
            Draw.gradientV(g, x + 1, y + 5, 2, h - 10, Theme.accent(), Theme.accent2());
        }

        int cy = y + pad;
        int tx = x + pad + barW;
        if (title) {
            Draw.textGradient(g, "KRYPTON", tx, cy, 1f, Theme.accent(), Theme.accent2());
            text(g, hud, "UTILITY", tx + Draw.width("KRYPTON ") + 2, cy, Theme.MUTED);
            cy += line + 2;
        }
        for (String[] r : rows) {
            text(g, hud, r[0], tx, cy, Theme.MUTED);
            text(g, hud, r[1], tx + labelW + 10, cy, Theme.TEXT);
            cy += line;
        }
        infoRect = new int[]{x, y, w, h};
        return infoRect;
    }

    // ------------------------------------------------------------------ module list

    /** Draws the active-module list and returns its rectangle. */
    public static int[] drawList(GuiGraphics g, HudModule hud, boolean preview) {
        List<String> names = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.isEnabled() && m != hud) {
                names.add(m.name());
            }
        }
        if (names.isEmpty() && preview) {
            names.add("Freecam");
            names.add("ESP Boxes");
            names.add("Block Scanner");
        }
        if (names.isEmpty()) {
            listRect = new int[]{0, 0, 0, 0};
            return listRect;
        }
        if (hud.listSort.is("Alphabetical")) {
            names.sort(String.CASE_INSENSITIVE_ORDER);
        } else {
            names.sort((a, b) -> Integer.compare(Draw.width(b), Draw.width(a)));
        }
        int rowH = Draw.lineHeight() + 4;
        int w = 0;
        for (String n : names) {
            w = Math.max(w, Draw.width(n) + 12);
        }
        int h = names.size() * rowH;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int x = MARGIN + Math.round(hud.listX.asFloat() * Math.max(0, sw - w - MARGIN * 2));
        int y = MARGIN + Math.round(hud.listY.asFloat() * Math.max(0, sh - h - MARGIN * 2));
        int i = 0;
        for (String n : names) {
            int rw = Draw.width(n) + 12;
            int rx = x + w - rw;
            int ry = y + i * rowH;
            int c = Draw.lerp(Theme.accent(), Theme.accent2(), names.size() <= 1 ? 0f : i / (float) (names.size() - 1));
            Draw.rect(g, rx, ry, rw, rowH, background(hud));
            if (hud.listAccentBar.get()) {
                Draw.rect(g, rx + rw - 2, ry, 2, rowH, c);
            }
            text(g, hud, n, rx + 5, ry + 2, c);
            i++;
        }
        listRect = new int[]{x, y, w, h};
        return listRect;
    }

    // ------------------------------------------------------------------ freecam badge

    private static void drawFreecamBadge(GuiGraphics g) {
        FreecamModule fc = FreecamModule.instance();
        if (!FreecamModule.isActive() || fc == null || !fc.badge.get()) {
            return;
        }
        boolean serverVisible = fc.allowInteraction.get() || fc.allowBodyTeleport.get();
        String text = "FREECAM";
        String sub = fc.modeLabel() + "  |  speed " + String.format(Locale.ROOT, "%.2f", fc.speed.asDouble());
        int w = Draw.width(text) + Draw.width(sub) + 34 + (serverVisible ? 12 : 0);
        int x = (g.guiWidth() - w) / 2;
        int y = 8;
        Draw.shadow(g, x, y, w, 16, 8, 4);
        Draw.round(g, x, y, w, 16, 8, Theme.HUD_BG);
        Draw.round(g, x + 6, y + 6, 4, 4, 2, serverVisible ? 0xFFF59E0B : Theme.accent());
        Draw.textGradient(g, text, x + 14, y + 4, 1f, Theme.accent(), Theme.accent2());
        Draw.text(g, sub, x + 22 + Draw.width(text), y + 4, Theme.MUTED);
        if (serverVisible) {
            Draw.text(g, "!", x + w - 12, y + 4, 0xFFF59E0B);
        }
    }

    /** Position helper shared with the editor: converts a dragged top-left corner back to a 0..1 fraction. */
    public static double toFraction(int pos, int size, int screen) {
        int range = Math.max(1, screen - size - MARGIN * 2);
        return Mth.clamp((pos - MARGIN) / (double) range, 0.0, 1.0);
    }
}
