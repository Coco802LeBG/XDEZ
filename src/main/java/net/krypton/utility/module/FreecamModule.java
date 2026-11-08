package net.krypton.utility.module;

import net.krypton.utility.core.Category;
import net.krypton.utility.core.Module;
import net.krypton.utility.core.setting.BoolSetting;
import net.krypton.utility.core.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_315;

/**
 * Detached camera. Everything is rendered locally: the player stays where it is and no packet is produced by the
 * camera itself. Options that would change what the server sees are grouped at the bottom and are off by default.
 */
public final class FreecamModule extends Module {
    // Movement
    public final NumberSetting speed;
    public final NumberSetting boostMultiplier;
    public final NumberSetting slowMultiplier;
    public final NumberSetting acceleration;
    public final NumberSetting deceleration;
    public final NumberSetting verticalFactor;
    public final BoolSetting scrollSpeed;
    public final NumberSetting scrollStep;
    // Camera
    public final NumberSetting sensitivity;
    public final BoolSetting invertY;
    public final BoolSetting overrideFov;
    public final NumberSetting fov;
    // Player
    public final BoolSetting freezeBody;
    public final BoolSetting disableOnDamage;
    // Collision & limits
    public final BoolSetting collisions;
    public final NumberSetting collisionSize;
    public final NumberSetting maxDistance;
    // Follow
    public final NumberSetting followDistance;
    public final NumberSetting followHeight;
    public final NumberSetting followSmoothing;
    public final NumberSetting followRange;
    public final BoolSetting preferPlayers;
    // HUD
    public final BoolSetting badge;
    // Server-visible
    public final BoolSetting allowInteraction;
    public final BoolSetting allowBodyTeleport;
    public final BoolSetting exitAfterTeleport;

    private static FreecamModule instance;
    private static boolean active;
    private static boolean bypass;

    private final FreecamController controller = new FreecamController(this);

    public FreecamModule() {
        super("freecam", "Freecam", "Detach the camera from your body and fly around.", Category.VISUALS, false);
        instance = this;
        serverNotice("The camera is rendered locally: your real player never moves and no extra packet is sent. "
                + "Only the two options in 'Server-visible' (both off by default) change what the server sees.");

        group("Movement");
        speed = add(new NumberSetting("speed", "Speed", 1.0, 0.05, 20.0, 0.05));
        boostMultiplier = add(new NumberSetting("boost_multiplier", "Boost multiplier (boost key)", 3.0, 1.5, 8.0, 0.5));
        slowMultiplier = add(new NumberSetting("slow_multiplier", "Slow multiplier (slow key)", 0.25, 0.05, 0.9, 0.05));
        acceleration = add(new NumberSetting("acceleration", "Acceleration", 0.35, 0.05, 1.0, 0.05));
        deceleration = add(new NumberSetting("deceleration", "Deceleration", 0.45, 0.05, 1.0, 0.05));
        verticalFactor = add(new NumberSetting("vertical_factor", "Vertical speed factor", 1.0, 0.25, 2.0, 0.05));
        scrollSpeed = add(new BoolSetting("scroll_speed", "Mouse wheel changes speed", true));
        scrollStep = add(new NumberSetting("scroll_step", "Wheel step (%)", 15, 5, 50, 5), scrollSpeed::get);

        group("Camera");
        sensitivity = add(new NumberSetting("sensitivity", "Mouse sensitivity", 1.0, 0.2, 3.0, 0.1));
        invertY = add(new BoolSetting("invert_y", "Invert vertical look", false));
        overrideFov = add(new BoolSetting("override_fov", "Override FOV", false));
        fov = add(new NumberSetting("fov", "FOV", 70, 30, 110, 1), overrideFov::get);

        group("Player");
        freezeBody = add(new BoolSetting("freeze_body", "Control the camera on start", true));
        disableOnDamage = add(new BoolSetting("disable_on_damage", "Stop when I take damage", true));

        group("Collision & limits");
        collisions = add(new BoolSetting("collisions", "Camera collides with blocks", false));
        collisionSize = add(new NumberSetting("collision_size", "Collision size", 0.3, 0.1, 0.8, 0.05), collisions::get);
        maxDistance = add(new NumberSetting("max_distance", "Max distance from body (0 = unlimited)", 0, 0, 512, 8));

        group("Follow");
        followDistance = add(new NumberSetting("follow_distance", "Follow distance", 4.0, 0.0, 16.0, 0.5));
        followHeight = add(new NumberSetting("follow_height", "Follow height offset", 0.3, -2.0, 4.0, 0.1));
        followSmoothing = add(new NumberSetting("follow_smoothing", "Follow smoothing", 0.25, 0.05, 1.0, 0.05));
        followRange = add(new NumberSetting("follow_range", "Target search range", 64, 8, 128, 8));
        preferPlayers = add(new BoolSetting("prefer_players", "Cycle through players first", true));

        group("HUD");
        badge = add(new BoolSetting("badge", "Show status badge", true));

        group("Server-visible (off by default)");
        allowInteraction = add(risky(new BoolSetting("allow_interaction", "Allow attacking / using from the camera", false),
                "Clicks would act on what the camera sees, far from your real position. Servers can notice this."));
        allowBodyTeleport = add(risky(new BoolSetting("allow_body_teleport", "Allow 'move body to camera'", false),
                "Moves your real player. The server sees the movement and may reject it or flag it."));
        exitAfterTeleport = add(new BoolSetting("exit_after_teleport", "Leave freecam after moving the body", true), allowBodyTeleport::get);
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected boolean canEnable() {
        class_310 mc = class_310.method_1551();
        return mc.field_1724 != null && mc.field_1687 != null && !mc.field_1724.method_29504();
    }

    @Override
    protected void onEnable() {
        controller.start(class_310.method_1551());
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
        controller.stop(class_310.method_1551());
    }

    @Override
    public void onDisconnect() {
        if (isEnabled()) {
            setEnabled(false);
        }
    }

    @Override
    public void onTick(class_310 mc) {
        controller.tick(mc);
    }

    // ------------------------------------------------------------------ actions bound to keys (see Keybinds)

    public void returnToStart(class_310 mc) {
        if (controller.isRunning()) {
            controller.returnToStart(mc);
        }
    }

    public void snapToBody(class_310 mc) {
        if (controller.isRunning()) {
            controller.snapToBody(mc);
        }
    }

    public void toggleControl(class_310 mc) {
        if (controller.isRunning()) {
            controller.toggleControl(mc);
        }
    }

    public void teleportBody(class_310 mc) {
        if (controller.isRunning()) {
            controller.teleportBody(mc);
        }
    }

    public void cycleFollow(class_310 mc) {
        if (controller.isRunning() && !controller.controlBody()) {
            controller.cycleFollow(mc);
        }
    }

    public void followLookedAt(class_310 mc) {
        if (controller.isRunning() && !controller.controlBody()) {
            controller.followLookedAt(mc);
        }
    }

    public void adjustSpeed(class_310 mc, int direction) {
        if (controller.isRunning() && !controller.controlBody()) {
            controller.adjustSpeed(mc, direction);
        }
    }

    /** Where the ESP scanners look from: the free camera while Freecam is on, otherwise the player. */
    public static class_243 viewCenter(class_310 mc) {
        if (active && instance != null && instance.controller.isRunning()) {
            return instance.cameraPosition();
        }
        return mc.field_1724 != null ? mc.field_1724.method_73189() : class_243.field_1353;
    }

    /** Called once per rendered frame (not per tick) so the camera moves smoothly at any frame rate. */
    public static void frame() {
        if (active && instance != null) {
            instance.controller.frame(class_310.method_1551());
        }
    }

    public class_243 cameraPosition() {
        return controller.eyePosition();
    }

    public String modeLabel() {
        return controller.modeLabel();
    }

    // ------------------------------------------------------------------ static hooks used by the mixins and the HUD

    public static boolean isActive() {
        return active;
    }

    public static FreecamModule instance() {
        return instance;
    }

    static void setBypass(boolean value) {
        bypass = value;
    }

    /** Mouse look: rotates the camera instead of the player. Returns true when the event was consumed. */
    public static boolean handleTurn(class_1297 self, double yawDelta, double pitchDelta) {
        if (!active || instance == null || self != class_310.method_1551().field_1724) {
            return false;
        }
        if (!instance.controller.isRunning() || instance.controller.controlBody()) {
            return false;
        }
        instance.controller.look(yawDelta, pitchDelta);
        return true;
    }

    /** Mouse wheel: changes the camera speed (or the follow distance). Returns true when consumed. */
    public static boolean handleScroll(double verticalAmount) {
        if (!active || instance == null || verticalAmount == 0) {
            return false;
        }
        class_310 mc = class_310.method_1551();
        if (mc.field_1755 != null || !instance.scrollSpeed.get() || instance.controller.controlBody()) {
            return false;
        }
        instance.controller.adjustSpeed(mc, verticalAmount > 0 ? 1 : -1);
        return true;
    }

    /** Hides vanilla movement/interaction keys while the camera is being flown. */
    public static boolean shouldBlock(class_304 key) {
        if (!active || bypass || instance == null) {
            return false;
        }
        class_315 o = class_310.method_1551().field_1690;
        if (o == null) {
            return false;
        }
        if (key == o.field_1894 || key == o.field_1881 || key == o.field_1913 || key == o.field_1849
                || key == o.field_1903 || key == o.field_1832 || key == o.field_1867) {
            return !instance.controller.controlBody();
        }
        if (key == o.field_1886 || key == o.field_1904 || key == o.field_1871) {
            return !instance.allowInteraction.get();
        }
        return false;
    }

    /** FOV requested by the module, or -1 when the vanilla value must be used. */
    public static int fovOverride() {
        if (!active || instance == null || !instance.overrideFov.get()) {
            return -1;
        }
        return instance.fov.asInt();
    }
}
