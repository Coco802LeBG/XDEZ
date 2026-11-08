package net.krypton.utility;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.module.FreecamModule;
import net.krypton.utility.ui.ClickGuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Every key binding of the mod. They appear under "Krypton Utility" in Options > Controls, where they can be rebound;
 * Minecraft stores them in options.txt. Camera movement uses the vanilla movement keys (also rebindable there).
 */
public final class Keybinds {
    private static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    public static KeyMapping openGui;
    public static KeyMapping toggleFreecam;
    public static KeyMapping boost;
    public static KeyMapping slow;
    public static KeyMapping speedUp;
    public static KeyMapping speedDown;
    public static KeyMapping returnToStart;
    public static KeyMapping snapToBody;
    public static KeyMapping toggleControl;
    public static KeyMapping followNext;
    public static KeyMapping followLooked;
    public static KeyMapping toggleCollisions;
    public static KeyMapping teleportBody;

    private Keybinds() {
    }

    private static KeyMapping bind(String name, int key, KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.krypton_utility." + name, InputConstants.Type.KEYSYM, key, category));
    }

    public static void register(KeyMapping.Category category) {
        openGui = bind("open_gui", GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        toggleFreecam = bind("toggle_freecam", GLFW.GLFW_KEY_F6, category);
        boost = bind("freecam_boost", GLFW.GLFW_KEY_LEFT_CONTROL, category);
        slow = bind("freecam_slow", GLFW.GLFW_KEY_LEFT_ALT, category);
        speedUp = bind("freecam_speed_up", UNBOUND, category);
        speedDown = bind("freecam_speed_down", UNBOUND, category);
        returnToStart = bind("freecam_return_start", UNBOUND, category);
        snapToBody = bind("freecam_snap_body", UNBOUND, category);
        toggleControl = bind("freecam_toggle_control", UNBOUND, category);
        followNext = bind("freecam_follow_next", UNBOUND, category);
        followLooked = bind("freecam_follow_looked", UNBOUND, category);
        toggleCollisions = bind("freecam_toggle_collisions", UNBOUND, category);
        teleportBody = bind("freecam_teleport_body", UNBOUND, category);
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // discard presses made while the action is unavailable, so they do not fire later
        }
    }

    public static void tick(Minecraft mc) {
        while (openGui.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new ClickGuiScreen());
            }
        }

        FreecamModule freecam = ModuleManager.get(FreecamModule.class);
        while (toggleFreecam.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                freecam.toggle();
            }
        }

        boolean usable = freecam.isEnabled() && mc.screen == null && mc.player != null;
        if (!usable) {
            drain(speedUp);
            drain(speedDown);
            drain(returnToStart);
            drain(snapToBody);
            drain(toggleControl);
            drain(followNext);
            drain(followLooked);
            drain(toggleCollisions);
            drain(teleportBody);
            return;
        }
        while (speedUp.consumeClick()) {
            freecam.adjustSpeed(mc, 1);
        }
        while (speedDown.consumeClick()) {
            freecam.adjustSpeed(mc, -1);
        }
        while (returnToStart.consumeClick()) {
            freecam.returnToStart(mc);
        }
        while (snapToBody.consumeClick()) {
            freecam.snapToBody(mc);
        }
        while (toggleControl.consumeClick()) {
            freecam.toggleControl(mc);
        }
        while (followNext.consumeClick()) {
            freecam.cycleFollow(mc);
        }
        while (followLooked.consumeClick()) {
            freecam.followLookedAt(mc);
        }
        while (toggleCollisions.consumeClick()) {
            freecam.collisions.set(!freecam.collisions.get());
        }
        while (teleportBody.consumeClick()) {
            freecam.teleportBody(mc);
        }
    }
}
