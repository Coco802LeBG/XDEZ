package net.krypton.utility.render;

import net.minecraft.world.phys.AABB;

/** One thing to highlight this frame: a world-space box, a colour and an optional label. */
public record Target(AABB box, int color, String label, double distSq) {
}
