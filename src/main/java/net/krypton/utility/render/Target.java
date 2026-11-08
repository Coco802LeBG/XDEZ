package net.krypton.utility.render;

import net.minecraft.world.phys.AABB;

/**
 * One thing to highlight this frame: a world-space box, a colour and an optional label.
 * {@code health} is 0..1 for living entities and -1 when there is nothing to show.
 */
public record Target(AABB box, int color, String label, double distSq, float health) {
    public Target(AABB box, int color, String label, double distSq) {
        this(box, color, label, distSq, -1f);
    }
}
